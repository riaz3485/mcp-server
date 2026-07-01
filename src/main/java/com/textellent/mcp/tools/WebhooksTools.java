package com.textellent.mcp.tools;

import com.textellent.maestro.annotations.ManagedTool;
import com.textellent.mcp.core.SpringAiToolEngine;
import com.textellent.mcp.tools.dto.ToolRequests.WebhookListSubscriptionsRequest;
import com.textellent.mcp.tools.dto.ToolRequests.WebhookSubscribeRequest;
import com.textellent.mcp.tools.dto.ToolRequests.WebhookUnsubscribeRequest;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WebhooksTools {

    private final SpringAiToolEngine engine;

    public WebhooksTools(SpringAiToolEngine engine) {
        this.engine = engine;
    }

    @ManagedTool
    @McpTool(name = "webhook_subscribe", description = "CRITICAL: ALWAYS call this tool - NEVER make up, guess, or fabricate data. Subscribe to a webhook event. Use this tool directly when it matches the user's request.")
    public Map<String, Object> webhookSubscribe(@McpToolParam(required = true, description = "Tool arguments") WebhookSubscribeRequest request) throws Exception { return engine.execute("webhook_subscribe", request); }

    @ManagedTool
    @McpTool(name = "webhook_unsubscribe", description = "CRITICAL: ALWAYS call this tool - NEVER make up, guess, or fabricate data. Unsubscribe from a webhook event. Use this tool directly when it matches the user's request.")
    public Map<String, Object> webhookUnsubscribe(@McpToolParam(required = true, description = "Tool arguments") WebhookUnsubscribeRequest request) throws Exception { return engine.execute("webhook_unsubscribe", request); }

    @ManagedTool
    @McpTool(name = "webhook_list_subscriptions", description = "CRITICAL: ALWAYS call this tool - NEVER make up, guess, or fabricate data. List all webhook subscriptions. Use this tool directly when it matches the user's request.")
    public Map<String, Object> webhookListSubscriptions(@McpToolParam(required = true, description = "Tool arguments") WebhookListSubscriptionsRequest request) throws Exception { return engine.execute("webhook_list_subscriptions", request); }
}
