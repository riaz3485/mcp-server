package com.textellent.mcp.tools;

import com.textellent.mcp.core.SpringAiToolEngine;
import com.textellent.mcp.tools.dto.ToolRequests.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ContactsTools {

    private final SpringAiToolEngine engine;

    public ContactsTools(SpringAiToolEngine engine) {
        this.engine = engine;
    }

    @Tool(name = "contacts_add", description = "Add contacts")
    public Map<String, Object> contactsAdd(@ToolParam(required = true, description = "Tool arguments") ContactsAddRequest request) throws Exception { return engine.execute("contacts_add", request); }

    @Tool(name = "contacts_update", description = "Update contact")
    public Map<String, Object> contactsUpdate(@ToolParam(required = true, description = "Tool arguments") ContactsUpdateRequest request) throws Exception { return engine.execute("contacts_update", request); }

    @Tool(name = "contacts_get_all", description = "List contacts")
    public Map<String, Object> contactsGetAll(@ToolParam(required = true, description = "Tool arguments") ContactsGetAllRequest request) throws Exception { return engine.execute("contacts_get_all", request); }

    @Tool(name = "contacts_get_summary", description = "Get contact summary")
    public Map<String, Object> contactsGetSummary(@ToolParam(required = true, description = "Tool arguments") ContactsGetSummaryRequest request) throws Exception { return engine.execute("contacts_get_summary", request); }

    @Tool(name = "contacts_get", description = "Get contact by ID")
    public Map<String, Object> contactsGet(@ToolParam(required = true, description = "Tool arguments") ContactsGetRequest request) throws Exception { return engine.execute("contacts_get", request); }

    @Tool(name = "contacts_delete", description = "Delete contact")
    public Map<String, Object> contactsDelete(@ToolParam(required = true, description = "Tool arguments") ContactsDeleteRequest request) throws Exception { return engine.execute("contacts_delete", request); }

    @Tool(name = "contacts_find_multiple_phones", description = "Find contact by multiple phones")
    public Map<String, Object> contactsFindMultiplePhones(@ToolParam(required = true, description = "Tool arguments") ContactsFindMultiplePhonesRequest request) throws Exception { return engine.execute("contacts_find_multiple_phones", request); }

    @Tool(name = "contacts_find", description = "Find contact")
    public Map<String, Object> contactsFind(@ToolParam(required = true, description = "Tool arguments") ContactsFindRequest request) throws Exception { return engine.execute("contacts_find", request); }
}
