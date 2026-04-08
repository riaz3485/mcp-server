package com.textellent.mcp.services.dsl;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pure dataset operators for the pipeline DSL (axioms A12–A37, A27–A34).
 * All methods are deterministic and do not mutate inputs; they return new structures.
 */
public final class DslPureOperators {

    private DslPureOperators() {}

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> project(List<Map<String, Object>> d, List<String> fields) {
        if (d == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>(d.size());
        for (Map<String, Object> r : d) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : fields) {
                row.put(f, r != null ? r.get(f) : null);
            }
            out.add(row);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> filter(
            List<Map<String, Object>> d,
            String field,
            String op,
            Object value
    ) {
        if (d == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : d) {
            Object v = r != null ? r.get(field) : null;
            if (matchesPredicate(v, op, value)) {
                out.add(r);
            }
        }
        return out;
    }

    /** A14: preserves length and order — applies per-record field map (literal values). */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> mapRecords(
            List<Map<String, Object>> d,
            Map<String, Object> fieldLiterals
    ) {
        if (d == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>(d.size());
        for (Map<String, Object> r : d) {
            Map<String, Object> copy = new LinkedHashMap<>(r != null ? r : Collections.emptyMap());
            if (fieldLiterals != null) {
                copy.putAll(fieldLiterals);
            }
            out.add(copy);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> rename(
            List<Map<String, Object>> d,
            Map<String, String> renameMap
    ) {
        if (d == null || renameMap == null) {
            return d == null ? new ArrayList<>() : new ArrayList<>(d);
        }
        Set<String> targets = new HashSet<>(renameMap.values());
        if (targets.size() != renameMap.size()) {
            throw new IllegalArgumentException("rename: duplicate target fields (A15).");
        }
        List<Map<String, Object>> out = new ArrayList<>(d.size());
        for (Map<String, Object> r : d) {
            Map<String, Object> row = new LinkedHashMap<>();
            if (r != null) {
                for (Map.Entry<String, Object> e : r.entrySet()) {
                    String nk = renameMap.getOrDefault(e.getKey(), e.getKey());
                    row.put(nk, e.getValue());
                }
            }
            out.add(row);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> extend(
            List<Map<String, Object>> d,
            Map<String, Object> extraFields
    ) {
        if (d == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>(d.size());
        for (Map<String, Object> r : d) {
            Map<String, Object> row = new LinkedHashMap<>(r != null ? r : Collections.emptyMap());
            if (extraFields != null) {
                row.putAll(extraFields);
            }
            out.add(row);
        }
        return out;
    }

    public static List<Map<String, Object>> concat(List<Map<String, Object>> a, List<Map<String, Object>> b) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (a != null) {
            out.addAll(a);
        }
        if (b != null) {
            out.addAll(b);
        }
        return out;
    }

    /** A18 stable distinct on field equality */
    public static List<Map<String, Object>> distinct(List<Map<String, Object>> d, List<String> fields) {
        if (d == null || fields == null || fields.isEmpty()) {
            return d == null ? new ArrayList<>() : new ArrayList<>(d);
        }
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : d) {
            String key = keyTuple(r, fields);
            if (seen.add(key)) {
                out.add(r);
            }
        }
        return out;
    }

    /** A19–A20: returns list of { \"groupKey\": map, \"records\": list } */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> groupBy(List<Map<String, Object>> d, List<String> fields) {
        if (d == null) {
            return new ArrayList<>();
        }
        Map<String, List<Map<String, Object>>> buckets = new LinkedHashMap<>();
        for (Map<String, Object> r : d) {
            String k = keyTuple(r, fields);
            buckets.computeIfAbsent(k, x -> new ArrayList<>()).add(r);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : buckets.entrySet()) {
            Map<String, Object> gk = new LinkedHashMap<>();
            Map<String, Object> first = e.getValue().get(0);
            for (String f : fields) {
                gk.put(f, first != null ? first.get(f) : null);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("groupKey", gk);
            row.put("records", e.getValue());
            out.add(row);
        }
        return out;
    }

    /** A21 simple aggregate: count or sum */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> aggregate(
            List<Map<String, Object>> grouped,
            String aggOp,
            String valueField
    ) {
        if (grouped == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> g : grouped) {
            Object recsObj = g.get("records");
            List<Map<String, Object>> recs = recsObj instanceof List ? (List<Map<String, Object>>) recsObj : Collections.emptyList();
            Map<String, Object> gk = g.get("groupKey") instanceof Map ? (Map<String, Object>) g.get("groupKey") : new LinkedHashMap<>();
            Map<String, Object> row = new LinkedHashMap<>(gk);
            if ("count".equalsIgnoreCase(aggOp)) {
                row.put("count", recs.size());
            } else if ("sum".equalsIgnoreCase(aggOp) && valueField != null) {
                BigDecimal sum = BigDecimal.ZERO;
                for (Map<String, Object> r : recs) {
                    Object v = r != null ? r.get(valueField) : null;
                    if (v instanceof Number) {
                        sum = sum.add(new BigDecimal(v.toString()));
                    }
                }
                row.put("sum", sum);
            } else {
                row.put("result", recs.size());
            }
            out.add(row);
        }
        return out;
    }

    /** A22: returns Map key string -> first record (for simple index) */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> indexBy(List<Map<String, Object>> d, List<String> fields) {
        Map<String, Object> index = new LinkedHashMap<>();
        if (d == null) {
            return index;
        }
        for (Map<String, Object> r : d) {
            String k = keyTuple(r, fields);
            index.putIfAbsent(k, r);
        }
        return index;
    }

    /** A23 */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> lookup(Map<String, Object> index, String key) {
        if (index == null || key == null) {
            return Collections.emptyList();
        }
        Object v = index.get(key);
        if (v == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList((Map<String, Object>) v);
    }

    /**
     * A24–A26 join on equality of leftKey/rightKey fields (single field names).
     * functionalLeft: if true, error when multiple right rows share the same join key.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> join(
            List<Map<String, Object>> left,
            List<Map<String, Object>> right,
            String leftKey,
            String rightKey,
            String joinType,
            boolean functionalLeft
    ) {
        Map<String, List<Map<String, Object>>> rightByKey = new LinkedHashMap<>();
        if (right != null) {
            for (Map<String, Object> r : right) {
                String k = String.valueOf(r != null ? r.get(rightKey) : "");
                rightByKey.computeIfAbsent(k, x -> new ArrayList<>()).add(r);
            }
        }
        for (Map.Entry<String, List<Map<String, Object>>> e : rightByKey.entrySet()) {
            if (functionalLeft && e.getValue().size() > 1) {
                throw new IllegalArgumentException("A26 functional join violated: right key '" + e.getKey() + "' has " + e.getValue().size() + " rows.");
            }
        }
        List<Map<String, Object>> matched = new ArrayList<>();
        List<Map<String, Object>> unmatchedLeft = new ArrayList<>();
        List<Map<String, Object>> multiMatchLeft = new ArrayList<>();
        Set<Map<String, Object>> consumedRightRows = Collections.newSetFromMap(new IdentityHashMap<>());

        if (left != null) {
            for (Map<String, Object> l : left) {
                String lk = String.valueOf(l != null ? l.get(leftKey) : "");
                List<Map<String, Object>> matches = rightByKey.getOrDefault(lk, Collections.emptyList());
                if (matches.isEmpty()) {
                    unmatchedLeft.add(l);
                } else if (matches.size() > 1) {
                    multiMatchLeft.add(l);
                    for (Map<String, Object> rr : matches) {
                        matched.add(mergeJoinRow(l, rr));
                        consumedRightRows.add(rr);
                    }
                } else {
                    Map<String, Object> rr = matches.get(0);
                    matched.add(mergeJoinRow(l, rr));
                    consumedRightRows.add(rr);
                }
            }
        }
        List<Map<String, Object>> unmatchedRight = new ArrayList<>();
        if (right != null) {
            for (Map<String, Object> r : right) {
                if (!consumedRightRows.contains(r)) {
                    unmatchedRight.add(r);
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matched", matched);
        result.put("unmatchedLeft", unmatchedLeft);
        result.put("unmatchedRight", unmatchedRight);
        result.put("multiMatchedLeft", multiMatchLeft);
        return result;
    }

    /** A35 */
    public static List<List<Map<String, Object>>> chunk(List<Map<String, Object>> d, int size) {
        if (d == null || d.isEmpty()) {
            return Collections.emptyList();
        }
        if (size < 1) {
            throw new IllegalArgumentException("chunk size must be positive (A35).");
        }
        List<List<Map<String, Object>>> chunks = new ArrayList<>();
        for (int i = 0; i < d.size(); i += size) {
            chunks.add(new ArrayList<>(d.subList(i, Math.min(i + size, d.size()))));
        }
        return chunks;
    }

    /** A36 */
    public static List<List<Map<String, Object>>> batchBy(List<Map<String, Object>> d, List<String> fields, int maxPerBatch) {
        if (d == null || d.isEmpty()) {
            return Collections.emptyList();
        }
        if (maxPerBatch < 1) {
            throw new IllegalArgumentException("batchBy maxPerBatch must be positive.");
        }
        Map<String, List<Map<String, Object>>> byKey = new LinkedHashMap<>();
        for (Map<String, Object> r : d) {
            String k = keyTuple(r, fields);
            byKey.computeIfAbsent(k, x -> new ArrayList<>()).add(r);
        }
        List<List<Map<String, Object>>> batches = new ArrayList<>();
        for (List<Map<String, Object>> group : byKey.values()) {
            for (int i = 0; i < group.size(); i += maxPerBatch) {
                batches.add(new ArrayList<>(group.subList(i, Math.min(i + maxPerBatch, group.size()))));
            }
        }
        return batches;
    }

    /** A31 validation partition */
    public static Map<String, Object> validateRecords(
            List<Map<String, Object>> d,
            List<String> requiredFields,
            List<String> uniqueFields,
            Map<String, List<Object>> allowedValues
    ) {
        List<Map<String, Object>> accepted = new ArrayList<>();
        List<Map<String, Object>> rejected = new ArrayList<>();
        List<Map<String, Object>> warned = new ArrayList<>();
        Set<String> seenUnique = new HashSet<>();
        for (Map<String, Object> r : d != null ? d : Collections.<Map<String, Object>>emptyList()) {
            String reason = null;
            if (requiredFields != null) {
                for (String f : requiredFields) {
                    Object v = r != null ? r.get(f) : null;
                    if (v == null || (v instanceof String && ((String) v).trim().isEmpty())) {
                        reason = "required:" + f;
                        break;
                    }
                }
            }
            if (reason == null && uniqueFields != null && !uniqueFields.isEmpty()) {
                String uk = keyTuple(r, uniqueFields);
                if (!seenUnique.add(uk)) {
                    reason = "unique:" + uniqueFields;
                }
            }
            if (reason == null && allowedValues != null) {
                for (Map.Entry<String, List<Object>> e : allowedValues.entrySet()) {
                    Object v = r != null ? r.get(e.getKey()) : null;
                    if (v != null && e.getValue() != null && !e.getValue().contains(v)) {
                        reason = "allowedValues:" + e.getKey();
                        break;
                    }
                }
            }
            if (reason != null) {
                Map<String, Object> row = new LinkedHashMap<>(r != null ? r : Collections.emptyMap());
                row.put("_rejectReason", reason);
                rejected.add(row);
            } else {
                accepted.add(r);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accepted", accepted);
        out.put("rejected", rejected);
        out.put("warned", warned);
        return out;
    }

    private static Map<String, Object> mergeJoinRow(Map<String, Object> l, Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("left", l);
        m.put("right", r);
        return m;
    }

    private static String keyTuple(Map<String, Object> r, List<String> fields) {
        if (fields == null) {
            return "";
        }
        return fields.stream()
                .map(f -> String.valueOf(r != null ? r.get(f) : ""))
                .collect(Collectors.joining("\u0001"));
    }

    private static boolean matchesPredicate(Object v, String op, Object value) {
        if ("notBlank".equalsIgnoreCase(op)) {
            return v != null && !String.valueOf(v).trim().isEmpty();
        }
        if ("equals".equalsIgnoreCase(op)) {
            return Objects.equals(v, value);
        }
        if ("notEquals".equalsIgnoreCase(op)) {
            return !Objects.equals(v, value);
        }
        return true;
    }
}
