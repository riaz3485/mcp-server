package com.textellent.mcp.services.dsl;

import com.textellent.mcp.registry.McpToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * Pipeline runtime: pure stages (algebra) then effectful stages (audited tool calls).
 * Used by {@link OrchestrationDslEngine}; plan versioning is expressed by {@code plan.version} in the payload.
 */
public final class PipelineRuntime {

    private static final Logger logger = LoggerFactory.getLogger(PipelineRuntime.class);

    private final McpToolRegistry toolRegistry;
    private final DslPlanValidator validator;
    private final DslTemplateResolver templateResolver;

    public PipelineRuntime(
            McpToolRegistry toolRegistry,
            DslPlanValidator validator,
            DslTemplateResolver templateResolver
    ) {
        this.toolRegistry = toolRegistry;
        this.validator = validator;
        this.templateResolver = templateResolver;
    }

    @SuppressWarnings("unchecked")
    public void execute(
            Map<String, Object> pipeline,
            Map<String, Object> vars,
            String authCode,
            String partnerClientCode,
            Map<String, Object> overallResult,
            boolean preview,
            String confirmationToken
    ) throws Exception {
        logger.info("PipelineRuntime: start preview={}", preview);
        List<String> errors = validator.validatePipeline(pipeline);
        if (!errors.isEmpty()) {
            overallResult.put("status", "failed");
            overallResult.put("validationErrors", errors);
            overallResult.put("summary", "Pipeline validation failed.");
            overallResult.put("vars", Collections.emptyMap());
            return;
        }

        Map<String, Object> initial = pipeline.get("initialState") instanceof Map
                ? (Map<String, Object>) pipeline.get("initialState")
                : Collections.emptyMap();
        Object dsObj = initial.get("datasets");
        if (dsObj instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) dsObj).entrySet()) {
                vars.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        Object scObj = initial.get("scalars");
        if (scObj instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) scObj).entrySet()) {
                vars.put(String.valueOf(e.getKey()), e.getValue());
            }
        }

        List<Map<String, Object>> auditLog = new ArrayList<>();
        vars.put("_audit", auditLog);

        List<Map<String, Object>> stages = (List<Map<String, Object>>) pipeline.get("stages");
        Map<String, Object> previewReport = new LinkedHashMap<>();
        int candidateUnits = 0;
        int skippedUnits = 0;

        for (Map<String, Object> stage : stages) {
            String stageId = String.valueOf(stage.get("id"));
            Boolean pure = Boolean.TRUE.equals(stage.get("pure"));
            if (pure == null || pure) {
                List<Map<String, Object>> ops = stage.get("ops") instanceof List
                        ? (List<Map<String, Object>>) stage.get("ops")
                        : Collections.emptyList();
                for (Map<String, Object> op : ops) {
                    executePureOp(op, vars);
                }
            } else {
                Map<String, Object> effect = stage.get("effect") instanceof Map
                        ? (Map<String, Object>) stage.get("effect")
                        : null;
                if (effect == null) {
                    continue;
                }
                boolean confirmable = Boolean.TRUE.equals(effect.get("confirmable"));
                String expected = (String) pipeline.get("confirmationToken");
                if (expected == null) {
                    expected = (String) stage.get("confirmationToken");
                }
                if (confirmable && (confirmationToken == null || !confirmationToken.equals(expected))) {
                    overallResult.put("status", "failed");
                    overallResult.put("validationErrors", Collections.singletonList(
                            "A47: confirmable stage '" + stageId + "' requires confirmationToken."));
                    overallResult.put("vars", Collections.emptyMap());
                    return;
                }
                if (preview) {
                    Map<String, Object> argsTemplate = effect.get("argsTemplate") instanceof Map
                            ? (Map<String, Object>) effect.get("argsTemplate")
                            : Collections.emptyMap();
                    try {
                        templateResolver.resolveTemplatesInArgs(new LinkedHashMap<>(argsTemplate), vars, null);
                        candidateUnits++;
                    } catch (Exception ex) {
                        previewReport.put("stage_" + stageId + "_invalid", ex.getMessage());
                    }
                    continue;
                }
                executeEffect(stageId, effect, vars, authCode, partnerClientCode, auditLog);
                candidateUnits++;
            }
        }

        overallResult.put("status", "succeeded");
        overallResult.put("summary", "DSL v2.0 pipeline completed.");
        overallResult.put("vars", new LinkedHashMap<>(vars));
        overallResult.put("dslVersion", "2.0");
        long auditOk = auditLog.stream().filter(a -> "succeeded".equals(a.get("outcome"))).count();
        long auditFail = auditLog.stream().filter(a -> "failed".equals(a.get("outcome"))).count();
        Map<String, Object> partition = new LinkedHashMap<>();
        partition.put("succeeded", auditOk);
        partition.put("failed", auditFail);
        partition.put("skipped", skippedUnits);
        overallResult.put("executionPartition", partition);
        if (preview) {
            previewReport.put("candidateEffectUnits", candidateUnits);
            previewReport.put("skippedUnits", skippedUnits);
            previewReport.put("pureStagesExecuted", true);
            overallResult.put("previewReport", previewReport);
            overallResult.put("summary", "DSL v2.0 preview completed (no effectful tools invoked).");
        }
    }

    @SuppressWarnings("unchecked")
    private void executePureOp(Map<String, Object> op, Map<String, Object> vars) {
        String opName = (String) op.get("op");
        if (opName == null) {
            throw new IllegalArgumentException("op missing 'op' name");
        }
        String targetVar = (String) op.get("targetVar");
        switch (opName) {
            case "project": {
                List<Map<String, Object>> d = asDataset(vars, op.get("fromVar"));
                List<String> fields = op.get("fields") instanceof List ? (List<String>) op.get("fields") : Collections.emptyList();
                vars.put(targetVar, DslPureOperators.project(d, fields));
                break;
            }
            case "filter": {
                List<Map<String, Object>> d = asDataset(vars, op.get("fromVar"));
                String field = (String) op.get("field");
                String fop = op.get("predicateOp") instanceof String ? (String) op.get("predicateOp") : "notBlank";
                Object val = op.get("value");
                vars.put(targetVar, DslPureOperators.filter(d, field, fop, val));
                break;
            }
            case "mapRecords": {
                List<Map<String, Object>> d = asDataset(vars, op.get("fromVar"));
                Map<String, Object> fm = op.get("fieldMap") instanceof Map ? (Map<String, Object>) op.get("fieldMap") : Collections.emptyMap();
                vars.put(targetVar, DslPureOperators.mapRecords(d, fm));
                break;
            }
            case "rename": {
                List<Map<String, Object>> d = asDataset(vars, op.get("fromVar"));
                Map<String, String> rm = op.get("rename") instanceof Map ? (Map<String, String>) op.get("rename") : Collections.emptyMap();
                vars.put(targetVar, DslPureOperators.rename(d, rm));
                break;
            }
            case "extend": {
                List<Map<String, Object>> d = asDataset(vars, op.get("fromVar"));
                Map<String, Object> ex = op.get("extend") instanceof Map ? (Map<String, Object>) op.get("extend") : Collections.emptyMap();
                vars.put(targetVar, DslPureOperators.extend(d, ex));
                break;
            }
            case "concat": {
                List<Map<String, Object>> a = asDataset(vars, op.get("leftVar"));
                List<Map<String, Object>> b = asDataset(vars, op.get("rightVar"));
                vars.put(targetVar, DslPureOperators.concat(a, b));
                break;
            }
            case "distinct": {
                List<Map<String, Object>> d = asDataset(vars, op.get("fromVar"));
                List<String> fields = op.get("fields") instanceof List ? (List<String>) op.get("fields") : Collections.emptyList();
                vars.put(targetVar, DslPureOperators.distinct(d, fields));
                break;
            }
            case "groupBy": {
                List<Map<String, Object>> d = asDataset(vars, op.get("fromVar"));
                List<String> fields = op.get("fields") instanceof List ? (List<String>) op.get("fields") : Collections.emptyList();
                vars.put(targetVar, DslPureOperators.groupBy(d, fields));
                break;
            }
            case "aggregate": {
                List<Map<String, Object>> g = asDatasetGroups(vars, op.get("fromVar"));
                String aggOp = op.get("aggOp") instanceof String ? (String) op.get("aggOp") : "count";
                String vf = (String) op.get("valueField");
                vars.put(targetVar, DslPureOperators.aggregate(g, aggOp, vf));
                break;
            }
            case "indexBy": {
                List<Map<String, Object>> d = asDataset(vars, op.get("fromVar"));
                List<String> fields = op.get("fields") instanceof List ? (List<String>) op.get("fields") : Collections.emptyList();
                vars.put(targetVar, DslPureOperators.indexBy(d, fields));
                break;
            }
            case "lookup": {
                Map<String, Object> index = op.get("indexVar") instanceof String
                        ? (Map<String, Object>) vars.get(op.get("indexVar"))
                        : null;
                Object keyObj = op.get("key");
                if (keyObj == null && op.get("keyVar") instanceof String) {
                    keyObj = vars.get(op.get("keyVar"));
                }
                String key = String.valueOf(keyObj);
                vars.put(targetVar, DslPureOperators.lookup(index, key));
                break;
            }
            case "join": {
                List<Map<String, Object>> left = asDataset(vars, op.get("leftVar"));
                List<Map<String, Object>> right = asDataset(vars, op.get("rightVar"));
                String lk = (String) op.get("leftKey");
                String rk = (String) op.get("rightKey");
                boolean functional = Boolean.TRUE.equals(op.get("functionalLeft"));
                vars.put(targetVar, DslPureOperators.join(left, right, lk, rk, "left", functional));
                break;
            }
            case "chunk": {
                List<Map<String, Object>> d = asDataset(vars, op.get("fromVar"));
                int size = op.get("size") instanceof Number ? ((Number) op.get("size")).intValue() : 1;
                vars.put(targetVar, DslPureOperators.chunk(d, size));
                break;
            }
            case "batchBy": {
                List<Map<String, Object>> d = asDataset(vars, op.get("fromVar"));
                List<String> fields = op.get("fields") instanceof List ? (List<String>) op.get("fields") : Collections.emptyList();
                int max = op.get("maxPerBatch") instanceof Number ? ((Number) op.get("maxPerBatch")).intValue() : 100;
                vars.put(targetVar, DslPureOperators.batchBy(d, fields, max));
                break;
            }
            case "validate": {
                List<Map<String, Object>> d = asDataset(vars, op.get("fromVar"));
                List<String> req = op.get("requiredFields") instanceof List ? (List<String>) op.get("requiredFields") : null;
                List<String> uniq = op.get("uniqueFields") instanceof List ? (List<String>) op.get("uniqueFields") : null;
                Map<String, List<Object>> allowed = op.get("allowedValues") instanceof Map ? (Map<String, List<Object>>) op.get("allowedValues") : null;
                vars.put(targetVar, DslPureOperators.validateRecords(d, req, uniq, allowed));
                break;
            }
            case "literal": {
                vars.put(targetVar, op.get("value"));
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown pipeline op: " + opName);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asDatasetGroups(Map<String, Object> vars, Object fromVar) {
        Object v = fromVar instanceof String ? vars.get(fromVar) : fromVar;
        if (v instanceof List) {
            return (List<Map<String, Object>>) v;
        }
        throw new IllegalArgumentException("aggregate expects grouped list in fromVar");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asDataset(Map<String, Object> vars, Object fromVar) {
        Object v = fromVar instanceof String ? vars.get(fromVar) : fromVar;
        if (v instanceof List) {
            return (List<Map<String, Object>>) v;
        }
        if (v == null) {
            return new ArrayList<>();
        }
        throw new IllegalArgumentException("Expected dataset (array of records) for var " + fromVar);
    }

    @SuppressWarnings("unchecked")
    private void executeEffect(
            String stageId,
            Map<String, Object> effect,
            Map<String, Object> vars,
            String authCode,
            String partnerClientCode,
            List<Map<String, Object>> auditLog
    ) throws Exception {
        String tool = (String) effect.get("tool");
        Map<String, Object> argsTemplate = effect.get("argsTemplate") instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) effect.get("argsTemplate"))
                : new LinkedHashMap<>();
        Map<String, Object> resolved = templateResolver.resolveTemplatesInArgs(argsTemplate, vars, null);
        Map<String, Object> auditEntry = new LinkedHashMap<>();
        auditEntry.put("stageId", stageId);
        auditEntry.put("operator", tool);
        auditEntry.put("inputBatchIdentity", resolved.hashCode());
        try {
            Object raw = toolRegistry.execute(tool, resolved, authCode, partnerClientCode);
            Object parsed = templateResolver.maybeParseJson(raw);
            auditEntry.put("outcome", "succeeded");
            auditEntry.put("targetIdentity", tool);
            if (effect.get("capture") instanceof String) {
                vars.put((String) effect.get("capture"), parsed);
            }
        } catch (Exception e) {
            auditEntry.put("outcome", "failed");
            auditEntry.put("failure", e.getMessage());
            auditEntry.put("failingInput", resolved);
            auditLog.add(auditEntry);
            throw e;
        }
        auditLog.add(auditEntry);
    }
}
