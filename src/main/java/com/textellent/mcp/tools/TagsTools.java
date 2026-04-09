package com.textellent.mcp.tools;

import com.textellent.maestro.annotations.ManagedTool;
import com.textellent.mcp.core.SpringAiToolEngine;
import com.textellent.mcp.tools.dto.ToolRequests.*;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TagsTools {

    private final SpringAiToolEngine engine;

    public TagsTools(SpringAiToolEngine engine) {
        this.engine = engine;
    }

    @ManagedTool
    @McpTool(name = "tags_create", description = "CRITICAL: ALWAYS call this tool - NEVER make up, guess, or fabricate data. Create new contact tags. IMPORTANT: Before creating a tag, ALWAYS call tags_get_summary with the tagName parameter FIRST to check if the tag already exists. Only create a tag if exists=false. If the tag exists, use tags_assign_contacts instead to add contacts to the existing tag. Use this tool directly when it matches the user's request.")
    public Map<String, Object> tagsCreate(@McpToolParam(required = true, description = "Tool arguments") TagsCreateRequest request) throws Exception { return engine.execute("tags_create", request); }

    @ManagedTool
    @McpTool(name = "tags_update", description = "Update an existing contact tag. Use this tool directly when it matches the user's request.")
    public Map<String, Object> tagsUpdate(@McpToolParam(required = true, description = "Tool arguments") TagsUpdateRequest request) throws Exception { return engine.execute("tags_update", request); }

    @ManagedTool
    @McpTool(name = "tags_get", description = "Get a specific contact tag by ID. Use this tool directly when it matches the user's request.")
    public Map<String, Object> tagsGet(@McpToolParam(required = true, description = "Tool arguments") TagsGetRequest request) throws Exception { return engine.execute("tags_get", request); }

    @ManagedTool
    @McpTool(name = "tags_get_all", description = "DEPRECATED: Use tags_get_summary instead for listing tags. This tool is only needed when you require the full list of tag names in a single result. For 'list my tags' or 'show tags' requests, ALWAYS use tags_get_summary which is more efficient. Use this tool directly when it matches the user's request.")
    public Map<String, Object> tagsGetAll(@McpToolParam(required = true, description = "Tool arguments") TagsGetAllRequest request) throws Exception { return engine.execute("tags_get_all", request); }

    @ManagedTool
    @McpTool(name = "tags_get_summary", description = "CRITICAL: This is the DEFAULT tool for listing tags - use this instead of tags_get_all. NEVER make up or guess tag names. Use cases: (1) 'list my tags' or 'show all tags' - call WITHOUT tagName parameter to get all tag names. (2) 'check if tag X exists' - call WITH tagName parameter to verify a specific tag exists before creating or assigning contacts. Use this tool directly when it matches the user's request.")
    public Map<String, Object> tagsGetSummary(@McpToolParam(required = true, description = "Tool arguments") TagsGetSummaryRequest request) throws Exception { return engine.execute("tags_get_summary", request); }

    @ManagedTool
    @McpTool(name = "tags_assign_contacts", description = "CRITICAL: ALWAYS call this tool - NEVER make up, guess, or fabricate data. Assign one or more contacts to an EXISTING tag. IMPORTANT: (1) Call tags_get_summary with tagName parameter FIRST to verify tag exists. (2) You only need PHONE NUMBERS to assign contacts - DO NOT call contacts_find first. Use the phone numbers directly from user input or from contacts_add response. (3) Include ALL contacts in a SINGLE call - DO NOT make separate calls for each contact. Use this tool directly when it matches the user's request.")
    public Map<String, Object> tagsAssignContacts(@McpToolParam(required = true, description = "Tool arguments") TagsAssignContactsRequest request) throws Exception { return engine.execute("tags_assign_contacts", request); }

    @ManagedTool
    @McpTool(name = "tags_delete", description = "Delete a contact tag by ID. Use this tool directly when it matches the user's request.")
    public Map<String, Object> tagsDelete(@McpToolParam(required = true, description = "Tool arguments") TagsDeleteRequest request) throws Exception { return engine.execute("tags_delete", request); }

    @ManagedTool
    @McpTool(name = "tags_remove_contacts", description = "CRITICAL: ALWAYS call this tool - NEVER make up, guess, or fabricate data. Remove one or more contacts from a tag. IMPORTANT: When removing multiple contacts from the same tag, ALWAYS include ALL phone numbers in a SINGLE call using the phoneNumbers array - DO NOT make separate calls for each contact. Use this tool directly when it matches the user's request.")
    public Map<String, Object> tagsRemoveContacts(@McpToolParam(required = true, description = "Tool arguments") TagsRemoveContactsRequest request) throws Exception { return engine.execute("tags_remove_contacts", request); }
}
