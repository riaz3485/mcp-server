package com.textellent.mcp.services.dsl;

import com.textellent.mcp.registry.McpToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Validates DSL plan structure: simplePlan (steps, tool names) and task (phases, step types, tool names).
 * Does not execute; used for dry-run and before execution.
 */
@Component
public class DslPlanValidator {

    private static final Logger logger = LoggerFactory.getLogger(DslPlanValidator.class);
    private static final Set<String> SUPPORTED_COMPUTE_OPERATIONS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList(
                    "length", "literal", "pick", "math", "map", "filter", "uniqueBy", "difference", "normalize", "sort", "parseCsv"
            ))
    );

    /** DSL v2.0 pure operators (minimal basis). */
    private static final Set<String> V2_PURE_OPERATORS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList(
                    "project", "filter", "mapRecords", "rename", "extend", "concat", "distinct",
                    "groupBy", "aggregate", "indexBy", "lookup", "join", "chunk", "batchBy",
                    "validate", "literal"
            ))
    );

    @Lazy
    private final McpToolRegistry toolRegistry;

    public DslPlanValidator(@Lazy McpToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * Validate simplePlan: steps array, unique ids, required tool, tool exists in registry.
     */
    @SuppressWarnings("unchecked")
    public List<String> validateSimplePlan(Map<String, Object> simplePlan) {
        List<String> errors = new ArrayList<>();
        if (simplePlan == null) {
            logger.warn("validateSimplePlan: simplePlan is null");
            errors.add("simplePlan must be an object.");
            return errors;
        }
        Object stepsObj = simplePlan.get("steps");
        if (!(stepsObj instanceof List)) {
            logger.warn("validateSimplePlan: steps is not an array");
            errors.add("simplePlan.steps must be an array.");
            return errors;
        }

        List<Map<String, Object>> steps = (List<Map<String, Object>>) stepsObj;
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            String id = (String) step.get("id");
            String tool = (String) step.get("tool");
            if (id == null || id.trim().isEmpty()) {
                errors.add("simplePlan step at index " + i + " is missing 'id'.");
            } else if (!ids.add(id)) {
                errors.add("Duplicate simplePlan step id: " + id);
            }
            if (tool == null || tool.trim().isEmpty()) {
                errors.add("simplePlan step " + (id != null ? id : ("at index " + i)) + " is missing 'tool'.");
            } else if (!toolRegistry.hasTool(tool)) {
                errors.add("simplePlan step " + (id != null ? id : ("at index " + i)) + " references unknown tool '" + tool + "'.");
            }
        }
        if (!errors.isEmpty()) {
            logger.info("validateSimplePlan: validation failed with {} error(s)", errors.size());
        } else {
            logger.debug("validateSimplePlan: valid, {} steps", steps.size());
        }
        return errors;
    }

    /**
     * Validate task: phases array, phase ids, steps array per phase, step id/type, toolCall tool exists.
     */
    @SuppressWarnings("unchecked")
    public List<String> validateTask(Map<String, Object> task) {
        List<String> errors = new ArrayList<>();
        if (task == null) {
            logger.warn("validateTask: task is null");
            errors.add("task must be an object.");
            return errors;
        }
        Object phasesObj = task.get("phases");
        if (!(phasesObj instanceof List)) {
            logger.warn("validateTask: phases is not an array");
            errors.add("task.phases must be an array.");
            return errors;
        }
        List<Map<String, Object>> phases = (List<Map<String, Object>>) phasesObj;
        Set<String> phaseIds = new HashSet<>();
        for (int i = 0; i < phases.size(); i++) {
            Map<String, Object> phase = phases.get(i);
            String phaseId = (String) phase.get("id");
            if (phaseId == null || phaseId.trim().isEmpty()) {
                errors.add("Phase at index " + i + " is missing 'id'.");
                continue;
            } else if (!phaseIds.add(phaseId)) {
                errors.add("Duplicate phase id: " + phaseId);
            }
            Object stepsObj = phase.get("steps");
            if (!(stepsObj instanceof List)) {
                errors.add("Phase " + phaseId + " must have array 'steps'.");
                continue;
            }
            List<Map<String, Object>> steps = (List<Map<String, Object>>) stepsObj;
            Set<String> stepIds = new HashSet<>();
            for (int j = 0; j < steps.size(); j++) {
                Map<String, Object> step = steps.get(j);
                String stepId = (String) step.get("id");
                String type = (String) step.get("type");
                if (stepId == null || stepId.trim().isEmpty()) {
                    errors.add("Phase " + phaseId + " step at index " + j + " is missing 'id'.");
                } else if (!stepIds.add(stepId)) {
                    errors.add("Phase " + phaseId + " has duplicate step id: " + stepId);
                }
                if (type == null || type.trim().isEmpty()) {
                    errors.add("Phase " + phaseId + " step " + stepId + " is missing 'type'.");
                }
                if ("toolCall".equals(type)) {
                    String tool = (String) step.get("tool");
                    if (tool == null || tool.trim().isEmpty()) {
                        errors.add("Phase " + phaseId + " step " + stepId + " (toolCall) is missing 'tool'.");
                    } else if (!toolRegistry.hasTool(tool)) {
                        errors.add("Phase " + phaseId + " step " + stepId + " (toolCall) references unknown tool '" + tool + "'.");
                    }
                }
                if ("batchLoop".equals(type)) {
                    Map<String, Object> src = step.get("source") instanceof Map
                            ? (Map<String, Object>) step.get("source")
                            : null;
                    if (src == null) {
                        errors.add("Phase " + phaseId + " step " + stepId + " (batchLoop) requires 'source' object.");
                    } else {
                        String fv = (String) src.get("fromVar");
                        Object literal = src.get("literal");
                        boolean hasFromVar = fv != null && !fv.trim().isEmpty();
                        boolean hasLiteral = literal instanceof List;
                        if (!hasFromVar && !hasLiteral) {
                            errors.add("Phase " + phaseId + " step " + stepId + " (batchLoop) requires source.fromVar or source.literal(array).");
                        }
                        if (literal != null && !(literal instanceof List)) {
                            errors.add("Phase " + phaseId + " step " + stepId + " (batchLoop) source.literal must be an array.");
                        }
                    }
                    Map<String, Object> body = step.get("bodyStep") instanceof Map
                            ? (Map<String, Object>) step.get("bodyStep")
                            : null;
                    if (body == null) {
                        errors.add("Phase " + phaseId + " step " + stepId + " (batchLoop) requires 'bodyStep'.");
                    } else {
                        String bt = (String) body.get("tool");
                        if (bt == null || bt.trim().isEmpty()) {
                            errors.add("Phase " + phaseId + " step " + stepId + " (batchLoop) bodyStep is missing 'tool'.");
                        } else if (!toolRegistry.hasTool(bt)) {
                            errors.add("Phase " + phaseId + " step " + stepId + " (batchLoop) bodyStep references unknown tool '" + bt + "'.");
                        }
                    }
                }
                if ("compute".equals(type)) {
                    String operation = (String) step.get("operation");
                    String targetVar = (String) step.get("targetVar");
                    if (operation == null || operation.trim().isEmpty()) {
                        errors.add("Phase " + phaseId + " step " + stepId + " (compute) is missing 'operation'.");
                    } else if (!SUPPORTED_COMPUTE_OPERATIONS.contains(operation)) {
                        errors.add("Phase " + phaseId + " step " + stepId + " (compute) uses unsupported operation '" + operation + "'.");
                    }
                    if (targetVar == null || targetVar.trim().isEmpty()) {
                        errors.add("Phase " + phaseId + " step " + stepId + " (compute) is missing 'targetVar'.");
                    }
                }
            }
        }
        if (!errors.isEmpty()) {
            logger.info("validateTask: validation failed with {} error(s), phases={}", errors.size(), phases.size());
        } else {
            logger.debug("validateTask: valid, {} phases", phases.size());
        }
        return errors;
    }

    /**
     * Validate DSL v2.0 pipeline (A9 static well-formedness).
     */
    @SuppressWarnings("unchecked")
    public List<String> validatePipeline(Map<String, Object> pipeline) {
        List<String> errors = new ArrayList<>();
        if (pipeline == null) {
            errors.add("pipeline must be an object.");
            return errors;
        }
        Object stagesObj = pipeline.get("stages");
        if (!(stagesObj instanceof List)) {
            errors.add("pipeline.stages must be a non-empty array.");
            return errors;
        }
        List<Map<String, Object>> stages = (List<Map<String, Object>>) stagesObj;
        if (stages.isEmpty()) {
            errors.add("pipeline.stages must not be empty.");
        }
        Map<String, Object> perms = pipeline.get("permissions") instanceof Map
                ? (Map<String, Object>) pipeline.get("permissions")
                : null;
        if (perms == null) {
            errors.add("pipeline.permissions is required for v2.0.");
        }
        Set<String> stageIds = new HashSet<>();
        for (int i = 0; i < stages.size(); i++) {
            Map<String, Object> stage = stages.get(i);
            String sid = (String) stage.get("id");
            if (sid == null || sid.trim().isEmpty()) {
                errors.add("pipeline.stages[" + i + "] missing id.");
            } else if (!stageIds.add(sid)) {
                errors.add("Duplicate pipeline stage id: " + sid);
            }
            boolean isPure = !Boolean.FALSE.equals(stage.get("pure"));
            if (isPure) {
                Object opsObj = stage.get("ops");
                if (!(opsObj instanceof List)) {
                    errors.add("Pure stage " + sid + " requires ops array.");
                    continue;
                }
                List<Map<String, Object>> ops = (List<Map<String, Object>>) opsObj;
                for (int j = 0; j < ops.size(); j++) {
                    Map<String, Object> op = ops.get(j);
                    String opName = (String) op.get("op");
                    if (opName == null || opName.trim().isEmpty()) {
                        errors.add("Stage " + sid + " op[" + j + "] missing op.");
                    } else if (!V2_PURE_OPERATORS.contains(opName)) {
                        errors.add("Stage " + sid + " op[" + j + "] unknown op '" + opName + "'.");
                    }
                    if (!"literal".equals(opName) && (op.get("targetVar") == null || String.valueOf(op.get("targetVar")).trim().isEmpty())) {
                        errors.add("Stage " + sid + " op[" + j + "] requires targetVar.");
                    }
                }
            } else {
                Map<String, Object> effect = stage.get("effect") instanceof Map
                        ? (Map<String, Object>) stage.get("effect")
                        : null;
                if (effect == null) {
                    errors.add("Effect stage " + sid + " requires effect object.");
                } else {
                    String tool = (String) effect.get("tool");
                    if (tool == null || tool.trim().isEmpty()) {
                        errors.add("Effect stage " + sid + " missing effect.tool.");
                    } else if (!toolRegistry.hasTool(tool)) {
                        errors.add("Effect stage " + sid + " unknown tool '" + tool + "'.");
                    }
                }
            }
        }
        if (!errors.isEmpty()) {
            logger.info("validatePipeline: validation failed with {} error(s)", errors.size());
        }
        return errors;
    }
}
