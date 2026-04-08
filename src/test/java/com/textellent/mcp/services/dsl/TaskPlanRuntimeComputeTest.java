package com.textellent.mcp.services.dsl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.textellent.mcp.registry.McpToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskPlanRuntimeComputeTest {

    private final TaskPlanRuntime executor = new TaskPlanRuntime(
            new NoopRegistry(),
            new NoopValidator(),
            new DslTemplateResolver(new ObjectMapper())
    );

    @Test
    void computesDifferenceIntoTargetVar() throws Exception {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("leftNames", Arrays.asList((Object) "alpha", "beta", "gamma"));
        vars.put("existingNames", Arrays.asList((Object) "beta"));

        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", "diff1");
        step.put("type", "compute");
        step.put("operation", "difference");
        step.put("leftVar", "leftNames");
        step.put("rightVar", "existingNames");
        step.put("targetVar", "missing");

        Map<String, Object> phase = new LinkedHashMap<>();
        phase.put("id", "phase1");
        phase.put("steps", Arrays.asList((Object) step));
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("description", "test");
        task.put("phases", Arrays.asList((Object) phase));

        Map<String, Object> result = new LinkedHashMap<>();
        executor.execute(task, vars, "a", "b", result);

        assertEquals(Arrays.asList((Object) "alpha", "gamma"), vars.get("missing"));
        assertEquals("succeeded", result.get("status"));
    }

    @Test
    void parsesCsvRows() throws Exception {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("csvRaw", "name,team\nLewis,Mercedes\nMax,Red Bull");

        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", "csv1");
        step.put("type", "compute");
        step.put("operation", "parseCsv");
        step.put("sourceVar", "csvRaw");
        step.put("targetVar", "rows");

        Map<String, Object> phase = new LinkedHashMap<>();
        phase.put("id", "phase1");
        phase.put("steps", Arrays.asList((Object) step));
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("description", "test");
        task.put("phases", Arrays.asList((Object) phase));

        Map<String, Object> result = new LinkedHashMap<>();
        executor.execute(task, vars, "a", "b", result);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) vars.get("rows");
        assertEquals(2, rows.size());
        assertEquals("Lewis", rows.get(0).get("name"));
    }

    private static class NoopRegistry extends McpToolRegistry {
        @Override
        public Object execute(String toolName, Map<String, Object> arguments, String authCode, String partnerClientCode) {
            return "{}";
        }
    }

    private static class NoopValidator extends DslPlanValidator {
        NoopValidator() {
            super(new NoopRegistry());
        }

        @Override
        public List<String> validateTask(Map<String, Object> task) {
            return new ArrayList<String>();
        }
    }
}
