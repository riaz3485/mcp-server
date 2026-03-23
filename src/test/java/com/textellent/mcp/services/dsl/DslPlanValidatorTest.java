package com.textellent.mcp.services.dsl;

import com.textellent.mcp.registry.McpToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DslPlanValidatorTest {

    private final DslPlanValidator validator = new DslPlanValidator(new TestRegistry());

    @Test
    void acceptsBatchLoopLiteralSource() {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", "s1");
        step.put("type", "batchLoop");
        Map<String, Object> source = new LinkedHashMap<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "1");
        source.put("literal", Arrays.asList((Object) item));
        step.put("source", source);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tool", "contacts_add");
        step.put("bodyStep", body);

        Map<String, Object> phase = new LinkedHashMap<>();
        phase.put("id", "p1");
        phase.put("steps", Arrays.asList((Object) step));
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("phases", Arrays.asList((Object) phase));

        List<String> errors = validator.validateTask(task);
        assertTrue(errors.isEmpty(), "Expected no errors, got: " + errors);
    }

    @Test
    void rejectsUnsupportedComputeOperation() {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", "compute1");
        step.put("type", "compute");
        step.put("operation", "unknownOp");
        step.put("targetVar", "x");

        Map<String, Object> phase = new LinkedHashMap<>();
        phase.put("id", "p1");
        phase.put("steps", Arrays.asList((Object) step));
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("phases", Arrays.asList((Object) phase));

        List<String> errors = validator.validateTask(task);
        assertTrue(errors.stream().anyMatch(e -> e.contains("unsupported operation")));
    }

    private static class TestRegistry extends McpToolRegistry {
        @Override
        public boolean hasTool(String toolName) {
            return true;
        }
    }
}
