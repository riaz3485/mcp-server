package com.textellent.mcp.services.dsl;

import com.textellent.mcp.registry.McpToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Orchestration DSL engine: single entry point for parsing plan payloads, validation routing,
 * and execution of simplePlan, task, or pipeline plans. Stateless and synchronous; each
 * {@link #executePlan} call is independent.
 *
 * <p>Runtimes ({@link SimplePlanRuntime}, {@link TaskPlanRuntime}, {@link PipelineRuntime}) are
 * owned by the engine rather than registered as separate application services.</p>
 */
@Component
public class OrchestrationDslEngine {

    private static final Logger logger = LoggerFactory.getLogger(OrchestrationDslEngine.class);
    private static final Set<String> SUPPORTED_DSL_VERSIONS =
            Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("1.0", "1.1", "2.0")));

    private final DslPlanValidator planValidator;
    private final SimplePlanRuntime simplePlanRuntime;
    private final TaskPlanRuntime taskPlanRuntime;
    private final PipelineRuntime pipelineRuntime;

    public OrchestrationDslEngine(
            @Lazy McpToolRegistry toolRegistry,
            DslPlanValidator planValidator,
            DslTemplateResolver templateResolver
    ) {
        this.planValidator = planValidator;
        this.simplePlanRuntime = new SimplePlanRuntime(toolRegistry, planValidator, templateResolver);
        this.taskPlanRuntime = new TaskPlanRuntime(toolRegistry, planValidator, templateResolver);
        this.pipelineRuntime = new PipelineRuntime(toolRegistry, planValidator, templateResolver);
    }

    /**
     * Entry point for the {@code dsl_execute_plan} tool. Execution is always synchronous and stateless.
     *
     * @param arguments         tool arguments (must contain "plan"; optional "dryRun")
     * @param authCode          auth code for primitive tools
     * @param partnerClientCode partner client code for primitive tools
     * @return result map with status, phases, summary, validationErrors, lastError
     */
    @SuppressWarnings("unchecked")
    public Object executePlan(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("OrchestrationDslEngine.executePlan: start dryRun={}", Boolean.TRUE.equals(arguments != null ? arguments.get("dryRun") : null));

        Map<String, Object> result = new LinkedHashMap<>();

        if (arguments == null || !arguments.containsKey("plan")) {
            logger.warn("executePlan: missing plan, aborting");
            result.put("status", "failed");
            result.put("validationErrors", Collections.singletonList("Missing required 'plan' object."));
            result.put("vars", Collections.emptyMap());
            return result;
        }

        Object planObj = arguments.get("plan");
        if (!(planObj instanceof Map)) {
            logger.warn("executePlan: plan is not an object");
            result.put("status", "failed");
            result.put("validationErrors", Collections.singletonList("'plan' must be an object."));
            result.put("vars", Collections.emptyMap());
            return result;
        }

        Map<String, Object> plan = (Map<String, Object>) planObj;
        Object version = plan.get("version");
        if (!(version instanceof String)) {
            logger.warn("executePlan: plan.version missing or not string");
            result.put("status", "failed");
            result.put("validationErrors", Collections.singletonList("plan.version must be a string, e.g. \"1.0\"."));
            result.put("vars", Collections.emptyMap());
            return result;
        }
        String versionString = ((String) version).trim();
        if (!SUPPORTED_DSL_VERSIONS.contains(versionString)) {
            logger.warn("executePlan: unsupported DSL version={}", versionString);
            result.put("status", "failed");
            result.put("validationErrors",
                    Collections.singletonList("Unsupported plan.version '" + versionString + "'. Supported versions: 1.0, 1.1, 2.0."));
            result.put("vars", Collections.emptyMap());
            return result;
        }

        boolean dryRun = Boolean.TRUE.equals(arguments.get("dryRun"));

        if ("2.0".equals(versionString)) {
            Object pipelineObj = plan.get("pipeline");
            if (!(pipelineObj instanceof Map)) {
                result.put("status", "failed");
                result.put("validationErrors", Collections.singletonList("plan.version 2.0 requires plan.pipeline."));
                result.put("vars", Collections.emptyMap());
                return result;
            }
            if (plan.containsKey("task") || plan.containsKey("simplePlan")) {
                result.put("status", "failed");
                result.put("validationErrors", Collections.singletonList(
                        "plan.version 2.0 must not include task or simplePlan; use pipeline only."));
                result.put("vars", Collections.emptyMap());
                return result;
            }
            if (dryRun) {
                List<String> errors = planValidator.validatePipeline((Map<String, Object>) pipelineObj);
                if (!errors.isEmpty()) {
                    result.put("status", "failed");
                    result.put("validationErrors", errors);
                } else {
                    result.put("status", "succeeded");
                    result.put("summary", "DSL v2.0 pipeline validated successfully (dry run).");
                }
                result.put("vars", Collections.emptyMap());
                result.put("dslVersion", "2.0");
                return result;
            }
            boolean preview = Boolean.TRUE.equals(arguments != null ? arguments.get("preview") : null);
            String confirmationToken = arguments != null && arguments.get("confirmationToken") instanceof String
                    ? (String) arguments.get("confirmationToken") : null;
            Map<String, Object> vars = new HashMap<>();
            result.put("phases", new ArrayList<Map<String, Object>>());
            try {
                pipelineRuntime.execute(
                        (Map<String, Object>) pipelineObj,
                        vars,
                        authCode,
                        partnerClientCode,
                        result,
                        preview,
                        confirmationToken
                );
            } catch (Exception e) {
                logger.error("executePlan: pipeline failed", e);
                result.put("status", "failed");
                result.put("lastError", e.getMessage());
                if (!result.containsKey("summary")) {
                    result.put("summary", "DSL v2.0 pipeline failed: " + e.getMessage());
                }
                if (!result.containsKey("vars")) {
                    result.put("vars", vars);
                }
            }
            result.put("dslVersion", "2.0");
            logger.info("executePlan: finished status={}", result.get("status"));
            return result;
        }

        boolean hasTask = plan.containsKey("task");
        boolean hasSimplePlan = plan.containsKey("simplePlan");
        if (!hasTask && !hasSimplePlan) {
            logger.warn("executePlan: plan has neither task nor simplePlan");
            result.put("status", "failed");
            result.put("validationErrors", Collections.singletonList("plan must contain either 'task' or 'simplePlan'."));
            result.put("vars", Collections.emptyMap());
            return result;
        }
        Map<String, Object> vars = new HashMap<>();
        result.put("phases", new ArrayList<Map<String, Object>>());

        try {
            if (hasSimplePlan) {
                Map<String, Object> simplePlan = (Map<String, Object>) plan.get("simplePlan");
                if (dryRun) {
                    List<String> errors = planValidator.validateSimplePlan(simplePlan);
                    if (!errors.isEmpty()) {
                        result.put("status", "failed");
                        result.put("validationErrors", errors);
                    } else {
                        result.put("status", "succeeded");
                        result.put("summary", "Plan validated successfully (dry run).");
                    }
                    result.put("vars", Collections.emptyMap());
                    return result;
                }
                simplePlanRuntime.execute(simplePlan, vars, authCode, partnerClientCode, result);
            } else {
                Map<String, Object> task = (Map<String, Object>) plan.get("task");
                if (dryRun) {
                    List<String> errors = planValidator.validateTask(task);
                    if (!errors.isEmpty()) {
                        result.put("status", "failed");
                        result.put("validationErrors", errors);
                    } else {
                        result.put("status", "succeeded");
                        result.put("summary", "Task plan validated successfully (dry run).");
                    }
                    result.put("vars", Collections.emptyMap());
                    return result;
                }
                taskPlanRuntime.execute(task, vars, authCode, partnerClientCode, result);
            }
            if (vars.containsKey("batchSummary")) {
                result.put("batchSummary", vars.get("batchSummary"));
            }
        } catch (Exception e) {
            logger.error("executePlan: execution failed", e);
            result.put("status", "failed");
            result.put("lastError", e.getMessage());
            if (!result.containsKey("summary")) {
                result.put("summary", "Plan execution failed with an internal error.");
            }
            if (!result.containsKey("vars")) {
                result.put("vars", Collections.emptyMap());
            }
        }

        logger.info("executePlan: finished status={}", result.get("status"));
        return result;
    }
}
