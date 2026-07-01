package com.textellent.mcp.tools;

import com.textellent.maestro.annotations.ManagedTool;
import com.textellent.mcp.core.SpringAiToolEngine;
import com.textellent.mcp.tools.dto.ToolRequests.*;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ContactsTools {

    private final SpringAiToolEngine engine;

    public ContactsTools(SpringAiToolEngine engine) {
        this.engine = engine;
    }

    @ManagedTool
    @McpTool(name = "contacts_add", description = "CRITICAL: ALWAYS call this tool - NEVER make up, guess, or fabricate data. Add one or more contacts to Textellent. IMPORTANT: (1) DO NOT call contacts_find before adding - add contacts DIRECTLY without checking if they exist. The backend handles duplicates automatically. (2) When creating multiple contacts, include ALL contacts in a SINGLE call - DO NOT make separate calls for each contact. (3) After adding, use the phone numbers directly with tags_assign_contacts - no need to look up contacts. Use this tool directly when it matches the user's request.")
    public Map<String, Object> contactsAdd(@McpToolParam(required = true, description = "Tool arguments") ContactsAddRequest request) throws Exception { return engine.execute("contacts_add", request); }

    @ManagedTool
    @McpTool(name = "contacts_update", description = "CRITICAL: ALWAYS call this tool - NEVER make up, guess, or fabricate data. Update an existing contact in Textellent. Use this tool directly when it matches the user's request.")
    public Map<String, Object> contactsUpdate(@McpToolParam(required = true, description = "Tool arguments") ContactsUpdateRequest request) throws Exception { return engine.execute("contacts_update", request); }

    @ManagedTool
    @McpTool(name = "contacts_get_all", description = "Use this tool only when you need data that contacts_get_summary does not provide. Prefer contacts_get_summary whenever possible. This tool returns all matching contacts in a single result. Use this tool directly when it matches the user's request.")
    public Map<String, Object> contactsGetAll(@McpToolParam(required = true, description = "Tool arguments") ContactsGetAllRequest request) throws Exception { return engine.execute("contacts_get_all", request); }

    @ManagedTool
    @McpTool(name = "contacts_get_summary", description = "CRITICAL: Prefer this tool over contacts_get_all. NEVER make up or guess contact data. Use cases: (1) 'list my contacts' or 'show contacts' - returns total count plus a simplified list of contacts (name and phone only). (2) 'find contact named X' or 'search for X' - use searchKey parameter to find specific contacts. (3) 'how many contacts do I have' - returns totalCount. Use contacts_get_all only when you need data that this tool does not provide. Use this tool directly when it matches the user's request.")
    public Map<String, Object> contactsGetSummary(@McpToolParam(required = true, description = "Tool arguments") ContactsGetSummaryRequest request) throws Exception { return engine.execute("contacts_get_summary", request); }

    @ManagedTool
    @McpTool(name = "contacts_get", description = "CRITICAL: ALWAYS call this tool - NEVER make up, guess, or fabricate data. Get a specific contact by ID. Use this tool directly when it matches the user's request.")
    public Map<String, Object> contactsGet(@McpToolParam(required = true, description = "Tool arguments") ContactsGetRequest request) throws Exception { return engine.execute("contacts_get", request); }

    @ManagedTool
    @McpTool(name = "contacts_delete", description = "CRITICAL: ALWAYS call this tool - NEVER make up, guess, or fabricate data. Delete a contact by ID. Use this tool directly when it matches the user's request.")
    public Map<String, Object> contactsDelete(@McpToolParam(required = true, description = "Tool arguments") ContactsDeleteRequest request) throws Exception { return engine.execute("contacts_delete", request); }

    @ManagedTool
    @McpTool(name = "contacts_find_multiple_phones", description = "CRITICAL: ALWAYS call this tool - NEVER make up, guess, or fabricate data. Find contact with multiple phone numbers. Use this tool directly when it matches the user's request.")
    public Map<String, Object> contactsFindMultiplePhones(@McpToolParam(required = true, description = "Tool arguments") ContactsFindMultiplePhonesRequest request) throws Exception { return engine.execute("contacts_find_multiple_phones", request); }

    @ManagedTool
    @McpTool(name = "contacts_find", description = "Find a specific contact by external ID, phone number, or email. IMPORTANT: Only use this tool when the user explicitly asks to FIND or LOOK UP a contact. DO NOT use this tool: (1) Before adding contacts - use contacts_add directly. (2) Before tagging contacts - use phone numbers directly with tags_assign_contacts. (3) For listing contacts - use contacts_get_summary instead. Use this tool directly when it matches the user's request.")
    public Map<String, Object> contactsFind(@McpToolParam(required = true, description = "Tool arguments") ContactsFindRequest request) throws Exception { return engine.execute("contacts_find", request); }
}
