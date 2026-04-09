package com.textellent.mcp.tools;

import com.textellent.maestro.annotations.ManagedTool;
import com.textellent.mcp.core.SpringAiToolEngine;
import com.textellent.mcp.tools.dto.ToolRequests.MessagesSendRequest;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MessagesTools {

    private final SpringAiToolEngine engine;

    public MessagesTools(SpringAiToolEngine engine) {
        this.engine = engine;
    }

    @ManagedTool
    @McpTool(name = "messages_send", description = "CRITICAL: ALWAYS call this tool - NEVER make up, guess, or fabricate data. Send SMS/MMS using Textellent API. Use this tool directly only when a single call to this tool is sufficient to complete the user's request; for any sequence of two or more tool calls, use `dsl_execute_plan` with a multi-step plan instead.")
    public Map<String, Object> messagesSend(@McpToolParam(required = true, description = "Tool arguments") MessagesSendRequest request) throws Exception {
        return engine.execute("messages_send", request);
    }
}
