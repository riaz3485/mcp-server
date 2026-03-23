package com.textellent.mcp.services.dsl;

import com.textellent.mcp.registry.McpToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Executes the full task form of the DSL: phases with dependsOn, steps (toolCall and placeholders for batchLoop/wait/compute/checkpoint).
 */
@Component
public class DslTaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(DslTaskExecutor.class);

    @Lazy
    private final McpToolRegistry toolRegistry;
    private final DslPlanValidator validator;
    private final DslTemplateResolver templateResolver;
    private static final int DEFAULT_MAX_BATCH_RETRIES = 2;

    public DslTaskExecutor(
            @Lazy McpToolRegistry toolRegistry,
            DslPlanValidator validator,
            DslTemplateResolver templateResolver
    ) {
        this.toolRegistry = toolRegistry;
        this.validator = validator;
        this.templateResolver = templateResolver;
    }

    /**
     * Validate and execute task; write phases, status, summary into overallResult.
     */
    @SuppressWarnings("unchecked")
    public void execute(
            Map<String, Object> task,
            Map<String, Object> vars,
            String authCode,
            String partnerClientCode,
            Map<String, Object> overallResult
    ) throws Exception {
        logger.info("DslTaskExecutor: starting task execution");
        List<String> validationErrors = validator.validateTask(task);
        if (!validationErrors.isEmpty()) {
            logger.warn("DslTaskExecutor: task validation failed, aborting execution");
            overallResult.put("status", "failed");
            overallResult.put("validationErrors", validationErrors);
            overallResult.put("summary", "Task validation failed.");
            return;
        }

        List<Map<String, Object>> phases = (List<Map<String, Object>>) task.get("phases");
        Map<String, String> phaseStatus = new LinkedHashMap<>();
        List<Map<String, Object>> phaseSummaries = new ArrayList<>();

        for (int p = 0; p < phases.size(); p++) {
            Map<String, Object> phase = phases.get(p);
            String phaseId = (String) phase.get("id");
            List<String> dependsOn = phase.get("dependsOn") instanceof List
                    ? (List<String>) phase.get("dependsOn")
                    : Collections.emptyList();

            boolean dependencyFailed = dependsOn.stream()
                    .anyMatch(dep -> !"succeeded".equals(phaseStatus.get(dep)));
            if (dependencyFailed) {
                logger.info("DslTaskExecutor: phase {} skipped (dependency failed)", phaseId);
                phaseStatus.put(phaseId, "skipped");
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("id", phaseId);
                summary.put("status", "skipped");
                summary.put("error", "Skipped due to failed dependency.");
                phaseSummaries.add(summary);
                continue;
            }

            logger.info("DslTaskExecutor: executing phase {}/{} id={}", p + 1, phases.size(), phaseId);
            Map<String, Object> phaseResult = executePhase(phase, vars, authCode, partnerClientCode);
            String status = (String) phaseResult.getOrDefault("status", "succeeded");
            phaseStatus.put(phaseId, status);

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("id", phaseId);
            summary.put("status", status);
            if (phaseResult.containsKey("error")) {
                summary.put("error", phaseResult.get("error"));
            }
            phaseSummaries.add(summary);
        }

        overallResult.put("phases", phaseSummaries);
        boolean anyFailed = phaseStatus.values().stream().anyMatch(s -> "failed".equals(s));
        overallResult.put("status", anyFailed ? "failed" : "succeeded");
        String taskDescription = (String) task.getOrDefault("description", "Task");
        overallResult.put("summary", taskDescription + " completed with status: " + overallResult.get("status") + ".");
        overallResult.put("vars", new LinkedHashMap<>(vars));
        logger.info("DslTaskExecutor: task finished, status={} varsKeys={}", overallResult.get("status"), vars.keySet());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executePhase(
            Map<String, Object> phase,
            Map<String, Object> vars,
            String authCode,
            String partnerClientCode
    ) throws Exception {
        String phaseId = (String) phase.get("id");
        Map<String, Object> phaseResult = new LinkedHashMap<>();
        List<Map<String, Object>> steps = (List<Map<String, Object>>) phase.get("steps");

        for (int s = 0; s < steps.size(); s++) {
            Map<String, Object> step = steps.get(s);
            String type = (String) step.get("type");
            String stepId = (String) step.get("id");
            logger.debug("DslTaskExecutor: phase {} step {}/{} id={} type={}", phaseId, s + 1, steps.size(), stepId, type);

            try {
                switch (type != null ? type : "") {
                    case "toolCall":
                        executeToolCallStep(step, vars, authCode, partnerClientCode);
                        break;
                    case "batchLoop":
                        executeBatchLoopStep(step, vars, authCode, partnerClientCode);
                        break;
                    case "wait":
                        logger.info("DslTaskExecutor: ignoring wait step {} (MVP)", stepId);
                        break;
                    case "compute":
                        executeComputeStep(step, vars);
                        break;
                    case "checkpoint":
                        logger.info("DslTaskExecutor: checkpoint step {} not yet active; continuing", stepId);
                        break;
                    default:
                        logger.warn("DslTaskExecutor: unknown step type '{}' for id={}, skipping", type, stepId);
                }
            } catch (Exception e) {
                logger.error("DslTaskExecutor: step id={} type={} failed: {}", stepId, type, e.getMessage(), e);
                phaseResult.put("status", "failed");
                phaseResult.put("error", "Step " + stepId + " failed: " + e.getMessage());
                return phaseResult;
            }
        }

        phaseResult.put("status", "succeeded");
        return phaseResult;
    }

    @SuppressWarnings("unchecked")
    private void executeToolCallStep(
            Map<String, Object> step,
            Map<String, Object> vars,
            String authCode,
            String partnerClientCode
    ) throws Exception {
        String tool = (String) step.get("tool");
        Map<String, Object> args = step.get("args") instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) step.get("args"))
                : new LinkedHashMap<>();
        Map<String, Object> resolvedArgs = templateResolver.resolveTemplatesInArgs(args, vars, null);
        Object raw = toolRegistry.execute(tool, resolvedArgs, authCode, partnerClientCode);
        Object parsed = templateResolver.maybeParseJson(raw);
        String capture = (String) step.get("capture");
        if (capture != null && !capture.trim().isEmpty()) {
            vars.put(capture, parsed);
            logger.debug("DslTaskExecutor: captured into vars.{}", capture);
        }
    }

    /**
     * Runs {@code bodyStep} once per batch chunk from {@code source} (a variable, optional JSON path to an array).
     * Batch context for templates: {@code batch.items} (current chunk), {@code batch.item} (set when chunk size is 1),
     * {@code batch.batchIndex}, {@code batch.itemOffset}. See {@code DslTemplateResolver} for {@code batch.items[*].id}.
     */
    @SuppressWarnings("unchecked")
    private void executeBatchLoopStep(
            Map<String, Object> step,
            Map<String, Object> vars,
            String authCode,
            String partnerClientCode
    ) throws Exception {
        Map<String, Object> source = step.get("source") instanceof Map
                ? (Map<String, Object>) step.get("source")
                : null;
        if (source == null) {
            throw new IllegalArgumentException("batchLoop step missing 'source' object.");
        }
        String fromVar = (String) source.get("fromVar");
        String path = source.get("path") instanceof String ? ((String) source.get("path")).trim() : "";
        Object literal = source.get("literal");

        Object listObj;
        if (literal != null) {
            listObj = literal;
        } else {
            if (fromVar == null || fromVar.trim().isEmpty()) {
                throw new IllegalArgumentException("batchLoop source missing 'fromVar' (or provide source.literal).");
            }
            Object root = vars.get(fromVar);
            if (root == null) {
                throw new IllegalArgumentException("batchLoop source variable '" + fromVar + "' is missing or null.");
            }
            listObj = root;
            if (!path.isEmpty()) {
                if (!(root instanceof Map)) {
                    throw new IllegalArgumentException(
                            "batchLoop cannot apply path '" + path + "' because variable '" + fromVar + "' is not an object.");
                }
                listObj = templateResolver.resolvePath((Map<String, Object>) root, path);
            }
        }
        if (!(listObj instanceof List)) {
            String kind = listObj == null ? "null" : listObj.getClass().getSimpleName();
            throw new IllegalArgumentException("batchLoop source must resolve to a JSON array; got: " + kind);
        }

        List<Object> allItems = new ArrayList<>((List<Object>) listObj);

        Map<String, Object> bodyStep = step.get("bodyStep") instanceof Map
                ? (Map<String, Object>) step.get("bodyStep")
                : null;
        if (bodyStep == null) {
            throw new IllegalArgumentException("batchLoop step missing 'bodyStep'.");
        }
        String tool = (String) bodyStep.get("tool");
        if (tool == null || tool.trim().isEmpty()) {
            throw new IllegalArgumentException("batchLoop bodyStep missing 'tool'.");
        }

        Map<String, Object> batchSpec = step.get("batch") instanceof Map
                ? (Map<String, Object>) step.get("batch")
                : Collections.emptyMap();
        int chunkSize = 1;
        if (batchSpec.get("size") instanceof Number) {
            chunkSize = Math.max(1, ((Number) batchSpec.get("size")).intValue());
        }
        int maxBatches = Integer.MAX_VALUE;
        if (batchSpec.get("maxBatches") instanceof Number) {
            maxBatches = Math.max(1, ((Number) batchSpec.get("maxBatches")).intValue());
        }
        long throttleMs = 0;
        if (batchSpec.get("throttleMsBetweenBatches") instanceof Number) {
            throttleMs = Math.max(0, ((Number) batchSpec.get("throttleMsBetweenBatches")).longValue());
        }

        Map<String, Object> onError = step.get("onError") instanceof Map
                ? (Map<String, Object>) step.get("onError")
                : Collections.emptyMap();
        int maxRetries = onError.get("maxRetries") instanceof Number
                ? Math.max(0, ((Number) onError.get("maxRetries")).intValue())
                : DEFAULT_MAX_BATCH_RETRIES;
        int minChunkSize = 1;
        if (batchSpec.get("minSize") instanceof Number) {
            minChunkSize = Math.max(1, ((Number) batchSpec.get("minSize")).intValue());
        }
        int resumeBatchIndex = 0;
        if (batchSpec.get("resumeFromBatchIndex") instanceof Number) {
            resumeBatchIndex = Math.max(0, ((Number) batchSpec.get("resumeFromBatchIndex")).intValue());
        }

        Map<String, Object> argsTemplate = bodyStep.get("argsTemplate") instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) bodyStep.get("argsTemplate"))
                : new LinkedHashMap<>();

        int i = 0;
        int batchIndex = 0;
        int createdCount = 0;
        int skippedCount = 0;
        int duplicateCount = 0;
        List<Object> failedItems = new ArrayList<>();
        int recommendedNextBatchSize = chunkSize;
        while (i < allItems.size() && batchIndex < maxBatches) {
            int end = Math.min(i + chunkSize, allItems.size());
            List<Object> chunk = new ArrayList<>(allItems.subList(i, end));
            if (batchIndex < resumeBatchIndex) {
                skippedCount += chunk.size();
                batchIndex++;
                i = end;
                continue;
            }

            Map<String, Object> batchContext = new LinkedHashMap<>();
            batchContext.put("items", chunk);
            batchContext.put("batchIndex", batchIndex);
            batchContext.put("itemOffset", i);
            if (chunk.size() == 1) {
                batchContext.put("item", chunk.get(0));
            }

            boolean batchSucceeded = false;
            int attempts = 0;
            Exception lastBatchError = null;
            while (!batchSucceeded && attempts <= maxRetries) {
                attempts++;
                try {
                    Map<String, Object> resolvedArgs = templateResolver.resolveTemplatesInArgs(argsTemplate, vars, batchContext);
                    Object raw = toolRegistry.execute(tool, resolvedArgs, authCode, partnerClientCode);
                    Object parsed = templateResolver.maybeParseJson(raw);
                    String capture = (String) bodyStep.get("capture");
                    if (capture != null && !capture.trim().isEmpty()) {
                        vars.put(capture, parsed);
                        logger.debug("DslTaskExecutor: batchLoop captured into vars.{}", capture);
                    }
                    createdCount += chunk.size();
                    extractCountersFromBatchResult(parsed, failedItems);
                    batchSucceeded = true;
                } catch (Exception batchError) {
                    lastBatchError = batchError;
                    boolean shouldDownsize = isPayloadTooLarge(batchError) && chunkSize > minChunkSize;
                    if (shouldDownsize) {
                        chunkSize = Math.max(minChunkSize, chunkSize / 2);
                        recommendedNextBatchSize = chunkSize;
                        end = Math.min(i + chunkSize, allItems.size());
                        chunk = new ArrayList<>(allItems.subList(i, end));
                        batchContext.put("items", chunk);
                        if (chunk.size() == 1) {
                            batchContext.put("item", chunk.get(0));
                        } else {
                            batchContext.remove("item");
                        }
                    } else if (attempts > maxRetries) {
                        failedItems.addAll(chunk);
                        throw batchError;
                    }
                }
            }
            if (!batchSucceeded && lastBatchError != null) {
                throw lastBatchError;
            }

            batchIndex++;
            i = end;
            if (throttleMs > 0 && i < allItems.size() && batchIndex < maxBatches) {
                try {
                    Thread.sleep(throttleMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("batchLoop interrupted during throttle", ie);
                }
            }
        }
        duplicateCount = 0;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("createdCount", createdCount);
        summary.put("skippedCount", skippedCount);
        summary.put("duplicateCount", duplicateCount);
        summary.put("failedItems", failedItems);
        summary.put("nextRecommendedBatchSize", recommendedNextBatchSize);
        summary.put("lastSuccessfulBatchIndex", Math.max(batchIndex - 1, -1));
        summary.put("processedCount", createdCount + skippedCount);
        String summaryVar = step.get("summaryFromVar") instanceof String
                ? (String) step.get("summaryFromVar")
                : step.get("capture") instanceof String ? (String) step.get("capture") : null;
        if (summaryVar != null && !summaryVar.trim().isEmpty()) {
            vars.put(summaryVar, summary);
        }
        vars.put("batchSummary", summary);
        logger.info("DslTaskExecutor: batchLoop finished, {} batch(es), {} item(s) total", batchIndex, allItems.size());
    }

    @SuppressWarnings("unchecked")
    private void executeComputeStep(Map<String, Object> step, Map<String, Object> vars) {
        String operation = (String) step.get("operation");
        String targetVar = (String) step.get("targetVar");
        if (operation == null || targetVar == null) {
            throw new IllegalArgumentException("compute step requires 'operation' and 'targetVar'.");
        }
        Object result;
        switch (operation) {
            case "literal":
                result = step.get("expression");
                break;
            case "length":
                result = computeLength(step, vars);
                break;
            case "pick":
                result = pickFromVars(step, vars);
                break;
            case "math":
                result = computeMath(step, vars);
                break;
            case "map":
                result = computeMap(step, vars);
                break;
            case "filter":
                result = computeFilter(step, vars);
                break;
            case "uniqueBy":
                result = computeUniqueBy(step, vars);
                break;
            case "difference":
                result = computeDifference(step, vars);
                break;
            case "normalize":
                result = computeNormalize(step, vars);
                break;
            case "sort":
                result = computeSort(step, vars);
                break;
            case "parseCsv":
                result = computeParseCsv(step, vars);
                break;
            default:
                throw new IllegalArgumentException("Unsupported compute operation: " + operation);
        }
        vars.put(targetVar, result);
    }

    @SuppressWarnings("unchecked")
    private int computeLength(Map<String, Object> step, Map<String, Object> vars) {
        Object value = resolveOperand(step, vars, "sourceVar", "source");
        if (value instanceof Collection) {
            return ((Collection<?>) value).size();
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).size();
        }
        if (value instanceof String) {
            return ((String) value).length();
        }
        return value == null ? 0 : 1;
    }

    private Object pickFromVars(Map<String, Object> step, Map<String, Object> vars) {
        if (step.get("path") instanceof String) {
            return templateResolver.resolvePath(vars, (String) step.get("path"));
        }
        return resolveOperand(step, vars, "sourceVar", "source");
    }

    private Number computeMath(Map<String, Object> step, Map<String, Object> vars) {
        String operator = step.get("operator") instanceof String ? (String) step.get("operator") : "add";
        BigDecimal left = toDecimal(resolveOperand(step, vars, "leftVar", "left"));
        BigDecimal right = toDecimal(resolveOperand(step, vars, "rightVar", "right"));
        if ("subtract".equals(operator)) {
            return left.subtract(right);
        }
        if ("multiply".equals(operator)) {
            return left.multiply(right);
        }
        if ("divide".equals(operator)) {
            return right.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : left.divide(right, 8, java.math.RoundingMode.HALF_UP);
        }
        return left.add(right);
    }

    @SuppressWarnings("unchecked")
    private List<Object> computeMap(Map<String, Object> step, Map<String, Object> vars) {
        List<Object> source = ensureList(resolveOperand(step, vars, "sourceVar", "source"), "map");
        String field = step.get("field") instanceof String ? (String) step.get("field") : null;
        String asKey = step.get("asKey") instanceof String ? (String) step.get("asKey") : null;
        List<Object> out = new ArrayList<>();
        for (Object item : source) {
            Object value = item;
            if (field != null && item instanceof Map) {
                value = templateResolver.resolvePath((Map<String, Object>) item, field);
            }
            if (asKey != null) {
                Map<String, Object> mapped = new LinkedHashMap<>();
                mapped.put(asKey, value);
                out.add(mapped);
            } else {
                out.add(value);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Object> computeFilter(Map<String, Object> step, Map<String, Object> vars) {
        List<Object> source = ensureList(resolveOperand(step, vars, "sourceVar", "source"), "filter");
        String field = step.get("field") instanceof String ? (String) step.get("field") : null;
        String operator = step.get("operator") instanceof String ? (String) step.get("operator") : "notBlank";
        Object compareValue = resolveOperand(step, vars, "valueVar", "value");
        List<Object> out = new ArrayList<>();
        for (Object item : source) {
            Object val = item;
            if (field != null && item instanceof Map) {
                val = templateResolver.resolvePath((Map<String, Object>) item, field);
            }
            if (matchesFilter(val, operator, compareValue)) {
                out.add(item);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Object> computeUniqueBy(Map<String, Object> step, Map<String, Object> vars) {
        List<Object> source = ensureList(resolveOperand(step, vars, "sourceVar", "source"), "uniqueBy");
        String field = step.get("field") instanceof String ? (String) step.get("field") : null;
        Set<String> seen = new LinkedHashSet<>();
        List<Object> out = new ArrayList<>();
        for (Object item : source) {
            Object keyObj = item;
            if (field != null && item instanceof Map) {
                keyObj = templateResolver.resolvePath((Map<String, Object>) item, field);
            }
            String key = String.valueOf(keyObj);
            if (seen.add(key)) {
                out.add(item);
            }
        }
        return out;
    }

    private List<Object> computeDifference(Map<String, Object> step, Map<String, Object> vars) {
        List<Object> left = ensureList(resolveOperand(step, vars, "leftVar", "left"), "difference.left");
        List<Object> right = ensureList(resolveOperand(step, vars, "rightVar", "right"), "difference.right");
        Set<String> rightSet = right.stream().map(String::valueOf).collect(Collectors.toCollection(LinkedHashSet::new));
        List<Object> out = new ArrayList<>();
        for (Object item : left) {
            if (!rightSet.contains(String.valueOf(item))) {
                out.add(item);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Object computeNormalize(Map<String, Object> step, Map<String, Object> vars) {
        Object value = resolveOperand(step, vars, "sourceVar", "source");
        String mode = step.get("mode") instanceof String ? (String) step.get("mode") : "trimLower";
        if (value instanceof List) {
            List<Object> out = new ArrayList<>();
            for (Object item : (List<Object>) value) {
                out.add(normalizeScalar(item, mode));
            }
            return out;
        }
        return normalizeScalar(value, mode);
    }

    private List<Object> computeSort(Map<String, Object> step, Map<String, Object> vars) {
        List<Object> source = new ArrayList<>(ensureList(resolveOperand(step, vars, "sourceVar", "source"), "sort"));
        String direction = step.get("direction") instanceof String ? (String) step.get("direction") : "asc";
        source.sort(Comparator.comparing(String::valueOf));
        if ("desc".equalsIgnoreCase(direction)) {
            Collections.reverse(source);
        }
        return source;
    }

    private List<Map<String, Object>> computeParseCsv(Map<String, Object> step, Map<String, Object> vars) {
        Object csvObj = resolveOperand(step, vars, "sourceVar", "csv");
        if (!(csvObj instanceof String)) {
            throw new IllegalArgumentException("parseCsv source must be a CSV string.");
        }
        String csv = (String) csvObj;
        String[] lines = csv.split("\\r?\\n");
        if (lines.length == 0 || lines[0].trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> headers = parseCsvLine(lines[0]);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].trim().isEmpty()) {
                continue;
            }
            List<String> values = parseCsvLine(lines[i]);
            Map<String, Object> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                row.put(headers.get(c), c < values.size() ? values.get(c) : "");
            }
            rows.add(row);
        }
        return rows;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private Object normalizeScalar(Object value, String mode) {
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value);
        if ("trim".equals(mode)) {
            return str.trim();
        }
        if ("upper".equals(mode)) {
            return str.trim().toUpperCase(Locale.ROOT);
        }
        if ("lower".equals(mode)) {
            return str.trim().toLowerCase(Locale.ROOT);
        }
        return str.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matchesFilter(Object value, String operator, Object compareValue) {
        if ("equals".equalsIgnoreCase(operator)) {
            return Objects.equals(String.valueOf(value), String.valueOf(compareValue));
        }
        if ("notEquals".equalsIgnoreCase(operator)) {
            return !Objects.equals(String.valueOf(value), String.valueOf(compareValue));
        }
        if ("in".equalsIgnoreCase(operator) && compareValue instanceof Collection) {
            return ((Collection<?>) compareValue).stream().map(String::valueOf).anyMatch(v -> v.equals(String.valueOf(value)));
        }
        String s = value == null ? "" : String.valueOf(value).trim();
        return !s.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private List<Object> ensureList(Object value, String context) {
        if (value instanceof List) {
            return (List<Object>) value;
        }
        throw new IllegalArgumentException(context + " expects an array input.");
    }

    private BigDecimal toDecimal(Object value) {
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value));
        }
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException nfe) {
            return BigDecimal.ZERO;
        }
    }

    private boolean isPayloadTooLarge(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return msg.contains("413") || msg.contains("payload too large") || msg.contains("uri too long");
    }

    @SuppressWarnings("unchecked")
    private Object resolveOperand(Map<String, Object> step, Map<String, Object> vars, String varKey, String literalKey) {
        if (step.get(varKey) instanceof String) {
            return vars.get((String) step.get(varKey));
        }
        if (step.get(literalKey) != null) {
            return step.get(literalKey);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void extractCountersFromBatchResult(Object parsed, List<Object> failedItems) {
        if (!(parsed instanceof Map)) {
            return;
        }
        Map<String, Object> map = (Map<String, Object>) parsed;
        Object failures = map.get("failedItems");
        if (failures instanceof List) {
            failedItems.addAll((List<Object>) failures);
        }
    }
}
