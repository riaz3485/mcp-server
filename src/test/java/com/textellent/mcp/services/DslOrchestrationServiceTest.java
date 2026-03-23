package com.textellent.mcp.services;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DslOrchestrationServiceTest {

    @Test
    void rejectsUnsupportedVersion() {
        DslOrchestrationService service = new DslOrchestrationService();
        Map<String, Object> args = new LinkedHashMap<>();
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("version", "2.0");
        Map<String, Object> simplePlan = new LinkedHashMap<>();
        simplePlan.put("steps", new ArrayList<Object>());
        plan.put("simplePlan", simplePlan);
        args.put("plan", plan);

        Object resultObj = service.executePlan(args, "a", "b");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resultObj;

        assertEquals("failed", result.get("status"));
        assertTrue(String.valueOf(((java.util.List<?>) result.get("validationErrors")).get(0)).contains("Unsupported plan.version"));
    }
}
