package com.textellent.mcp.tools;

import com.textellent.maestro.annotations.ManagedTool;
import com.textellent.mcp.core.SpringAiToolEngine;
import com.textellent.mcp.tools.dto.ToolRequests.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TagsTools {

    private final SpringAiToolEngine engine;

    public TagsTools(SpringAiToolEngine engine) {
        this.engine = engine;
    }

    @ManagedTool
    @Tool(name = "tags_create", description = "Create tags")
    public Map<String, Object> tagsCreate(@ToolParam(required = true, description = "Tool arguments") TagsCreateRequest request) throws Exception { return engine.execute("tags_create", request); }

    @ManagedTool
    @Tool(name = "tags_update", description = "Update tag")
    public Map<String, Object> tagsUpdate(@ToolParam(required = true, description = "Tool arguments") TagsUpdateRequest request) throws Exception { return engine.execute("tags_update", request); }

    @ManagedTool
    @Tool(name = "tags_get", description = "Get tag")
    public Map<String, Object> tagsGet(@ToolParam(required = true, description = "Tool arguments") TagsGetRequest request) throws Exception { return engine.execute("tags_get", request); }

    @ManagedTool
    @Tool(name = "tags_get_all", description = "List tags")
    public Map<String, Object> tagsGetAll(@ToolParam(required = true, description = "Tool arguments") TagsGetAllRequest request) throws Exception { return engine.execute("tags_get_all", request); }

    @ManagedTool
    @Tool(name = "tags_get_summary", description = "Get tag summary")
    public Map<String, Object> tagsGetSummary(@ToolParam(required = true, description = "Tool arguments") TagsGetSummaryRequest request) throws Exception { return engine.execute("tags_get_summary", request); }

    @ManagedTool
    @Tool(name = "tags_assign_contacts", description = "Assign contacts to tag")
    public Map<String, Object> tagsAssignContacts(@ToolParam(required = true, description = "Tool arguments") TagsAssignContactsRequest request) throws Exception { return engine.execute("tags_assign_contacts", request); }

    @ManagedTool
    @Tool(name = "tags_delete", description = "Delete tag")
    public Map<String, Object> tagsDelete(@ToolParam(required = true, description = "Tool arguments") TagsDeleteRequest request) throws Exception { return engine.execute("tags_delete", request); }

    @ManagedTool
    @Tool(name = "tags_remove_contacts", description = "Remove contacts from tag")
    public Map<String, Object> tagsRemoveContacts(@ToolParam(required = true, description = "Tool arguments") TagsRemoveContactsRequest request) throws Exception { return engine.execute("tags_remove_contacts", request); }
}
