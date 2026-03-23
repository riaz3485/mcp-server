package com.textellent.mcp.services.dsl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.textellent.mcp.registry.McpToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrchestrationDslEngineTest {

    @Test
    void rejectsUnsupportedVersion() {
        StubRegistry registry = new StubRegistry();
        DslPlanValidator validator = new DslPlanValidator(registry);
        OrchestrationDslEngine engine = new OrchestrationDslEngine(
                registry,
                validator,
                new DslTemplateResolver(new ObjectMapper())
        );
        Map<String, Object> args = new LinkedHashMap<>();
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("version", "99.0");
        Map<String, Object> simplePlan = new LinkedHashMap<>();
        simplePlan.put("steps", new ArrayList<Object>());
        plan.put("simplePlan", simplePlan);
        args.put("plan", plan);

        Object resultObj = engine.executePlan(args, "a", "b");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resultObj;

        assertEquals("failed", result.get("status"));
        assertTrue(String.valueOf(((java.util.List<?>) result.get("validationErrors")).get(0)).contains("Unsupported plan.version"));
    }

    /** Minimal registry for tests that construct the engine without Spring. */
    private static class StubRegistry extends McpToolRegistry {
        @Override
        public Object execute(String toolName, Map<String, Object> arguments, String authCode, String partnerClientCode) {
            return "{}";
        }
    }
}
