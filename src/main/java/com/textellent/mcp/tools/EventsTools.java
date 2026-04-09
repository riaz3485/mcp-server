package com.textellent.mcp.tools;

import com.textellent.maestro.annotations.ManagedTool;
import com.textellent.mcp.core.SpringAiToolEngine;
import com.textellent.mcp.tools.dto.ToolRequests.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EventsTools {

    private final SpringAiToolEngine engine;

    public EventsTools(SpringAiToolEngine engine) {
        this.engine = engine;
    }

    @ManagedTool
    @Tool(name = "events_phone_added_wrong_number", description = "Get wrong number events")
    public Map<String, Object> eventsPhoneAddedWrongNumber(@ToolParam(required = true, description = "Tool arguments") EventsPhoneAddedWrongNumberRequest request) throws Exception { return engine.execute("events_phone_added_wrong_number", request); }

    @ManagedTool
    @Tool(name = "events_outgoing_delivery_status", description = "Get delivery status events")
    public Map<String, Object> eventsOutgoingDeliveryStatus(@ToolParam(required = true, description = "Tool arguments") EventsOutgoingDeliveryStatusRequest request) throws Exception { return engine.execute("events_outgoing_delivery_status", request); }

    @ManagedTool
    @Tool(name = "events_new_contact_details", description = "Get new contact events")
    public Map<String, Object> eventsNewContactDetails(@ToolParam(required = true, description = "Tool arguments") EventsNewContactDetailsRequest request) throws Exception { return engine.execute("events_new_contact_details", request); }

    @ManagedTool
    @Tool(name = "events_disassociate_contact_tag", description = "Get disassociate tag events")
    public Map<String, Object> eventsDisassociateContactTag(@ToolParam(required = true, description = "Tool arguments") EventsDisassociateContactTagRequest request) throws Exception { return engine.execute("events_disassociate_contact_tag", request); }

    @ManagedTool
    @Tool(name = "events_incoming_message", description = "Get incoming message events")
    public Map<String, Object> eventsIncomingMessage(@ToolParam(required = true, description = "Tool arguments") EventsIncomingMessageRequest request) throws Exception { return engine.execute("events_incoming_message", request); }

    @ManagedTool
    @Tool(name = "events_phone_added_dnt", description = "Get DNT add events")
    public Map<String, Object> eventsPhoneAddedDnt(@ToolParam(required = true, description = "Tool arguments") EventsPhoneAddedDntRequest request) throws Exception { return engine.execute("events_phone_added_dnt", request); }

    @ManagedTool
    @Tool(name = "events_associate_contact_tag", description = "Get associate tag events")
    public Map<String, Object> eventsAssociateContactTag(@ToolParam(required = true, description = "Tool arguments") EventsAssociateContactTagRequest request) throws Exception { return engine.execute("events_associate_contact_tag", request); }

    @ManagedTool
    @Tool(name = "events_phone_removed_dnt", description = "Get DNT remove events")
    public Map<String, Object> eventsPhoneRemovedDnt(@ToolParam(required = true, description = "Tool arguments") EventsPhoneRemovedDntRequest request) throws Exception { return engine.execute("events_phone_removed_dnt", request); }
}
