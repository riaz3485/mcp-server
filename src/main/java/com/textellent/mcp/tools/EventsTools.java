package com.textellent.mcp.tools;

import com.textellent.maestro.annotations.ManagedTool;
import com.textellent.mcp.core.SpringAiToolEngine;
import com.textellent.mcp.tools.dto.ToolRequests.*;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EventsTools {

    private final SpringAiToolEngine engine;

    public EventsTools(SpringAiToolEngine engine) {
        this.engine = engine;
    }

    @ManagedTool
    @McpTool(name = "events_phone_added_wrong_number", description = "Get wrong number events")
    public Map<String, Object> eventsPhoneAddedWrongNumber(@McpToolParam(required = true, description = "Tool arguments") EventsPhoneAddedWrongNumberRequest request) throws Exception { return engine.execute("events_phone_added_wrong_number", request); }

    @ManagedTool
    @McpTool(name = "events_outgoing_delivery_status", description = "Get delivery status events")
    public Map<String, Object> eventsOutgoingDeliveryStatus(@McpToolParam(required = true, description = "Tool arguments") EventsOutgoingDeliveryStatusRequest request) throws Exception { return engine.execute("events_outgoing_delivery_status", request); }

    @ManagedTool
    @McpTool(name = "events_new_contact_details", description = "Get new contact events")
    public Map<String, Object> eventsNewContactDetails(@McpToolParam(required = true, description = "Tool arguments") EventsNewContactDetailsRequest request) throws Exception { return engine.execute("events_new_contact_details", request); }

    @ManagedTool
    @McpTool(name = "events_disassociate_contact_tag", description = "Get disassociate tag events")
    public Map<String, Object> eventsDisassociateContactTag(@McpToolParam(required = true, description = "Tool arguments") EventsDisassociateContactTagRequest request) throws Exception { return engine.execute("events_disassociate_contact_tag", request); }

    @ManagedTool
    @McpTool(name = "events_incoming_message", description = "Get incoming message events")
    public Map<String, Object> eventsIncomingMessage(@McpToolParam(required = true, description = "Tool arguments") EventsIncomingMessageRequest request) throws Exception { return engine.execute("events_incoming_message", request); }

    @ManagedTool
    @McpTool(name = "events_phone_added_dnt", description = "Get DNT add events")
    public Map<String, Object> eventsPhoneAddedDnt(@McpToolParam(required = true, description = "Tool arguments") EventsPhoneAddedDntRequest request) throws Exception { return engine.execute("events_phone_added_dnt", request); }

    @ManagedTool
    @McpTool(name = "events_associate_contact_tag", description = "Get associate tag events")
    public Map<String, Object> eventsAssociateContactTag(@McpToolParam(required = true, description = "Tool arguments") EventsAssociateContactTagRequest request) throws Exception { return engine.execute("events_associate_contact_tag", request); }

    @ManagedTool
    @McpTool(name = "events_phone_removed_dnt", description = "Get DNT remove events")
    public Map<String, Object> eventsPhoneRemovedDnt(@McpToolParam(required = true, description = "Tool arguments") EventsPhoneRemovedDntRequest request) throws Exception { return engine.execute("events_phone_removed_dnt", request); }
}
