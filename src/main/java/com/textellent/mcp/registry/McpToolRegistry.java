package com.textellent.mcp.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.textellent.mcp.models.McpToolDefinition;
import com.textellent.mcp.services.*;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Registry for MCP tools that maps tool names to their handlers and definitions.
 */
@Component
public class McpToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(McpToolRegistry.class);

    /** Prepended to tool description for primitives (also declared per-schema as x-textellent-mcp). */
    private static final String DSL_STEP_ONLY_TOOL_DESCRIPTION_PREFIX =
        "MCP invocation policy (required): Do not call this tool with MCP tools/call. "
            + "It appears in tools/list only for discovery (this tool's name and inputSchema). "
            + "Run it only as a step inside a plan submitted to dsl_execute_plan (use this tool's name as the step `tool` field).\n\n";

    private static final String DIRECT_ORCHESTRATOR_TOOL_DESCRIPTION_PREFIX =
        "MCP invocation policy (required): This is the only tool you may invoke via MCP tools/call. "
            + "Every other tool in tools/list is dsl_step_only: use those names only inside the plan you pass here.\n\n";

    private static final String INPUT_SCHEMA_DSL_STEP_NOTE =
        "MCP (required): These properties are step arguments inside dsl_execute_plan only—not a standalone tools/call payload.";

    private static final String INPUT_SCHEMA_DIRECT_ORCHESTRATOR_NOTE =
        "MCP (required): This object is the arguments map for tools/call when the tool name is dsl_execute_plan.";

    private static final String OUTPUT_SCHEMA_DSL_STEP_NOTE =
        "MCP: Response shape when this primitive is executed as a dsl_execute_plan step (never from tools/call).";

    private final Map<String, McpToolHandler> handlers = new HashMap<>();
    private final Map<String, McpToolDefinition> toolDefinitions = new HashMap<>();
    private final Map<String, Schema> schemas = new HashMap<>();

    @Autowired
    private MessageApiService messageApiService;

    @Autowired
    private com.textellent.mcp.services.dsl.OrchestrationDslEngine orchestrationDslEngine;

    @Autowired
    private ContactApiService contactApiService;

    @Autowired
    private TagApiService tagApiService;

    @Autowired
    private CallbackEventApiService callbackEventApiService;

    @Autowired
    private ConfigurationApiService configurationApiService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        registerAllTools();
        loadToolSchemas();
    }

    /**
     * Register all MCP tools with their handlers.
     */
    private void registerAllTools() {
        // Orchestration: exposed for direct tools/call. Primitives appear in tools/list for DSL discovery but are only invokable inside dsl_execute_plan (see McpController).
        registerTool("dsl_execute_plan", orchestrationDslEngine::executePlan);

        // Message tools (invoked only by orchestrator when executing DSL plans)
        registerTool("messages_send", messageApiService::sendMessage);

        // Contact tools
        registerTool("contacts_add", contactApiService::addContacts);
        registerTool("contacts_update", contactApiService::updateContact);
        registerTool("contacts_get_all", contactApiService::getAllContacts);
        registerTool("contacts_get_summary", contactApiService::getContactsSummary);
        registerTool("contacts_get", contactApiService::getContact);
        registerTool("contacts_delete", contactApiService::deleteContact);
        registerTool("contacts_find_multiple_phones", contactApiService::findContactWithMultiplePhoneNumbers);
        registerTool("contacts_find", contactApiService::findContact);

        // Tag tools
        registerTool("tags_create", tagApiService::createTag);
        registerTool("tags_update", tagApiService::updateTag);
        registerTool("tags_get", tagApiService::getTag);
        registerTool("tags_get_all", tagApiService::getAllTags);
        registerTool("tags_get_summary", tagApiService::getTagsSummary);
        registerTool("tags_assign_contacts", tagApiService::assignContactsToTag);
        registerTool("tags_delete", tagApiService::deleteTag);
        registerTool("tags_remove_contacts", tagApiService::removeContactsFromTag);

        // Callback event tools (single fetchPagedEvents implementation; paths registered here)
        registerTool("events_phone_added_wrong_number", (args, ac, pc) ->
                callbackEventApiService.fetchPagedEvents("/api/v1/events/phoneNumberAddedToWrongNumber.json", args, ac, pc));
        registerTool("events_outgoing_delivery_status", (args, ac, pc) ->
                callbackEventApiService.fetchPagedEvents("/api/v1/events/outgoingMessageDeliveryStatus.json", args, ac, pc));
        registerTool("events_new_contact_details", (args, ac, pc) ->
                callbackEventApiService.fetchPagedEvents("/api/v1/events/newContactDetails.json", args, ac, pc));
        registerTool("events_disassociate_contact_tag", (args, ac, pc) ->
                callbackEventApiService.fetchPagedEvents("/api/v1/events/disassociateContactFromTag.json", args, ac, pc));
        registerTool("events_incoming_message", (args, ac, pc) ->
                callbackEventApiService.fetchPagedEvents("/api/v1/events/incomingMessageDetail.json", args, ac, pc));
        registerTool("events_phone_added_dnt", (args, ac, pc) ->
                callbackEventApiService.fetchPagedEvents("/api/v1/events/phoneNumberAddedToDNT.json", args, ac, pc));
        registerTool("events_associate_contact_tag", (args, ac, pc) ->
                callbackEventApiService.fetchPagedEvents("/api/v1/events/associateContactToTag.json", args, ac, pc));
        registerTool("events_phone_removed_dnt", (args, ac, pc) ->
                callbackEventApiService.fetchPagedEvents("/api/v1/events/phoneNumberRemovedFromDNT.json", args, ac, pc));

        // Configuration tools
        registerTool("webhook_subscribe", configurationApiService::webhookSubscribe);
        registerTool("webhook_unsubscribe", configurationApiService::webhookUnsubscribe);
        registerTool("webhook_list_subscriptions", configurationApiService::listSubscriptions);

        logger.info("Registered {} MCP tools", handlers.size());
    }

    /**
     * Register a tool with its handler.
     */
    private void registerTool(String toolName, McpToolHandler handler) {
        handlers.put(toolName, handler);
    }

    /**
     * Load all tool schemas from resources/schemas directory.
     */
    @SuppressWarnings("unchecked")
    private void loadToolSchemas() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:schemas/*.json");

            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    String filename = resource.getFilename();
                    if (filename != null) {
                        String toolName = filename.replace(".json", "");

                        Map<String, Object> schemaMap = objectMapper.readValue(is, new TypeReference<Map<String, Object>>() {});

                        Map<String, Object> inputSchema = (Map<String, Object>) schemaMap.get("inputSchema");
                        Map<String, Object> outputSchema = (Map<String, Object>) schemaMap.get("outputSchema");

                        boolean mustBeDirect = "dsl_execute_plan".equals(toolName);
                        Map<String, Object> xTextellentMcp = resolveTextellentMcpExtension(toolName, schemaMap, mustBeDirect);

                        boolean directToolsCall = Boolean.TRUE.equals(xTextellentMcp.get("directToolsCall"));
                        if (directToolsCall) {
                            prependRootSchemaDescription(inputSchema, INPUT_SCHEMA_DIRECT_ORCHESTRATOR_NOTE);
                        } else {
                            prependRootSchemaDescription(inputSchema, INPUT_SCHEMA_DSL_STEP_NOTE);
                            prependRootSchemaDescription(outputSchema, OUTPUT_SCHEMA_DSL_STEP_NOTE);
                        }

                        String baseDescription = (String) schemaMap.get("description");
                        String fullDescription = (directToolsCall ? DIRECT_ORCHESTRATOR_TOOL_DESCRIPTION_PREFIX : DSL_STEP_ONLY_TOOL_DESCRIPTION_PREFIX)
                            + (baseDescription != null ? baseDescription : "");

                        McpToolDefinition toolDef = new McpToolDefinition();
                        toolDef.setName(toolName);
                        toolDef.setDescription(fullDescription);
                        toolDef.setInputSchema(inputSchema);
                        toolDef.setOutputSchema(outputSchema);
                        toolDef.setTextellentMcp(xTextellentMcp);

                        configureSafetyMetadata(toolName, toolDef);

                        toolDefinitions.put(toolName, toolDef);

                        if (inputSchema != null) {
                            JSONObject jsonSchema = new JSONObject(inputSchema);
                            Schema schema = SchemaLoader.load(jsonSchema);
                            schemas.put(toolName, schema);
                        }

                        logger.debug("Loaded schema for tool: {}", toolName);
                    }
                } catch (Exception e) {
                    logger.error("Failed to load schema from resource: {}", resource.getFilename(), e);
                }
            }

            logger.info("Loaded {} tool schemas", toolDefinitions.size());
        } catch (IOException e) {
            logger.error("Failed to load tool schemas", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveTextellentMcpExtension(String toolName, Map<String, Object> schemaMap, boolean mustBeDirect) {
        Map<String, Object> xtm = (Map<String, Object>) schemaMap.get("x-textellent-mcp");
        if (xtm == null) {
            logger.warn("Schema file for tool '{}' is missing x-textellent-mcp; add it to the JSON schema for explicit MCP policy.", toolName);
            xtm = new LinkedHashMap<>();
        } else {
            xtm = new LinkedHashMap<>(xtm);
        }

        Object rawDirect = xtm.get("directToolsCall");
        boolean directToolsCall;
        if (rawDirect instanceof Boolean) {
            directToolsCall = (Boolean) rawDirect;
        } else {
            directToolsCall = mustBeDirect;
            if (rawDirect != null) {
                logger.warn("Tool '{}' x-textellent-mcp.directToolsCall must be a boolean; coercing to {}", toolName, mustBeDirect);
            }
        }

        if (directToolsCall != mustBeDirect) {
            logger.warn("Tool '{}' x-textellent-mcp.directToolsCall={} conflicts with server policy (expected {}); enforcing policy.",
                toolName, directToolsCall, mustBeDirect);
            directToolsCall = mustBeDirect;
        }

        xtm.put("directToolsCall", directToolsCall);
        xtm.put("invocation", directToolsCall ? "direct_tools_call" : "dsl_step_only");
        return Collections.unmodifiableMap(xtm);
    }

    private void prependRootSchemaDescription(Map<String, Object> schemaRoot, String mcpNote) {
        if (schemaRoot == null || mcpNote == null) {
            return;
        }
        Object existing = schemaRoot.get("description");
        if (existing instanceof String) {
            String str = (String) existing;
            if (!str.isEmpty()) {
                if (str.startsWith(mcpNote)) {
                    return;
                }
                schemaRoot.put("description", mcpNote + " " + str);
                return;
            }
        }
        schemaRoot.put("description", mcpNote);
    }

    /**
     * Get all registered tool definitions.
     */
    public List<McpToolDefinition> getAllToolDefinitions() {
        return new ArrayList<>(toolDefinitions.values());
    }

    /**
     * Execute a tool with the given name and arguments.
     */
    public Object execute(String toolName, Map<String, Object> arguments, String authCode, String partnerClientCode) throws Exception {
        McpToolHandler handler = handlers.get(toolName);
        if (handler == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }

        // Validate arguments against schema if available
        validateArguments(toolName, arguments);

        // Execute the tool
        return handler.execute(arguments, authCode, partnerClientCode);
    }

    /**
     * Check if a tool exists in the registry.
     */
    public boolean hasTool(String toolName) {
        return handlers.containsKey(toolName);
    }

    /**
     * Get a specific tool definition by name.
     */
    public McpToolDefinition getToolDefinition(String toolName) {
        return toolDefinitions.get(toolName);
    }

    /**
     * Validate arguments against the tool's input schema.
     */
    private void validateArguments(String toolName, Map<String, Object> arguments) throws ValidationException {
        Schema schema = schemas.get(toolName);
        if (schema != null && arguments != null) {
            JSONObject jsonObject = new JSONObject(arguments);
            schema.validate(jsonObject);
        }
    }

    /**
     * Configure safety metadata for a tool based on its name and function.
     */
    private void configureSafetyMetadata(String toolName, McpToolDefinition toolDef) {
        // Read-only tools (GET operations, list operations)
        if (toolName.startsWith("contacts_get") || toolName.startsWith("tags_get") ||
            toolName.startsWith("events_") || toolName.equals("webhook_list_subscriptions") ||
            toolName.equals("contacts_find") || toolName.equals("contacts_find_multiple_phones")) {
            toolDef.setReadOnly(true);
            toolDef.setDestructive(false);
            toolDef.setRequiredScope("read"); // Changed from textellent.read to match OAuth2 scopes
        }
        // Destructive tools (DELETE/UPDATE operations)
        else if (toolName.contains("_delete") || toolName.contains("_cancel") || toolName.contains("_update") ||
                 toolName.equals("webhook_unsubscribe") || toolName.equals("tags_remove_contacts")) {
            toolDef.setReadOnly(false);
            toolDef.setDestructive(true);
            toolDef.setRequiredScope("write"); // Changed from textellent.write to match OAuth2 scopes
        }
        // Write tools (CREATE operations)
        else {
            toolDef.setReadOnly(false);
            toolDef.setDestructive(false);
            toolDef.setRequiredScope("write"); // Changed from textellent.write to match OAuth2 scopes
        }

        // Orchestration tool: no scope required for plan submission
        if ("dsl_execute_plan".equals(toolName)) {
            toolDef.setRequiredScope(null);
        }
    }
}
