package com.textellent.mcp.services.dsl;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DslPureOperatorsTest {

    @Test
    void projectRetainsOrder() {
        List<Map<String, Object>> d = Arrays.asList(
                row("a", 1),
                row("b", 2)
        );
        List<Map<String, Object>> p = DslPureOperators.project(d, Collections.singletonList("x"));
        assertEquals(2, p.size());
        assertEquals("a", p.get(0).get("x"));
        assertEquals("b", p.get(1).get("x"));
    }

    @Test
    void filterStableOrder() {
        List<Map<String, Object>> d = Arrays.asList(row("a", 1), row("b", 0), row("c", 3));
        List<Map<String, Object>> f = DslPureOperators.filter(d, "v", "notBlank", null);
        assertEquals(3, f.size());
    }

    @Test
    void distinctFirstWin() {
        List<Map<String, Object>> d = Arrays.asList(row("a", 1), row("a", 2));
        List<Map<String, Object>> u = DslPureOperators.distinct(d, Collections.singletonList("x"));
        assertEquals(1, u.size());
        assertEquals(1, u.get(0).get("v"));
    }

    @Test
    void joinFunctionalViolation() {
        List<Map<String, Object>> L = Collections.singletonList(rowKey("k", "1"));
        List<Map<String, Object>> R = Arrays.asList(rowKey("k", "1"), rowKey("k", "1"));
        assertThrows(IllegalArgumentException.class, () ->
                DslPureOperators.join(L, R, "k", "k", "inner", true));
    }

    @Test
    void validatePartitions() {
        List<Map<String, Object>> d = Arrays.asList(
                row("a", 1),
                new LinkedHashMap<>()
        );
        Map<String, Object> v = DslPureOperators.validateRecords(
                d,
                Collections.singletonList("x"),
                null,
                null
        );
        assertTrue(((List<?>) v.get("accepted")).size() >= 1);
        assertFalse(((List<?>) v.get("rejected")).isEmpty());
    }

    private static Map<String, Object> row(String x, int v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", x);
        m.put("v", v);
        return m;
    }

    private static Map<String, Object> rowKey(String field, String value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(field, value);
        return m;
    }
}
