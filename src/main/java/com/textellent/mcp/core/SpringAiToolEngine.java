package com.textellent.mcp.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.textellent.mcp.audit.AuditLogService;
import com.textellent.mcp.ratelimit.RateLimitService;
import com.textellent.mcp.services.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SpringAiToolEngine {

    @FunctionalInterface
    private interface ToolHandler {
        Object execute(Map<String, Object> arguments, String authCode, String partnerCode) throws Exception;
    }

    private static final Set<String> READ_TOOLS = Set.of(
            "contacts_get_all", "contacts_get_summary", "contacts_get", "contacts_find_multiple_phones", "contacts_find",
            "tags_get", "tags_get_all", "tags_get_summary",
            "events_phone_added_wrong_number", "events_outgoing_delivery_status", "events_new_contact_details",
            "events_disassociate_contact_tag", "events_incoming_message", "events_phone_added_dnt",
            "events_associate_contact_tag", "events_phone_removed_dnt",
            "webhook_list_subscriptions"
    );

    private final ToolSchemaValidationService schemaValidationService;
    private final ToolCredentialResolver credentialResolver;
    private final RateLimitService rateLimitService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final Map<String, ToolHandler> handlers;

    public SpringAiToolEngine(
            ToolSchemaValidationService schemaValidationService,
            ToolCredentialResolver credentialResolver,
            RateLimitService rateLimitService,
            AuditLogService auditLogService,
            MessageApiService messageApiService,
            ContactApiService contactApiService,
            TagApiService tagApiService,
            CallbackEventApiService callbackEventApiService,
            ConfigurationApiService configurationApiService,
            ObjectMapper objectMapper) {
        this.schemaValidationService = schemaValidationService;
        this.credentialResolver = credentialResolver;
        this.rateLimitService = rateLimitService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.handlers = initHandlers(messageApiService, contactApiService, tagApiService, callbackEventApiService, configurationApiService);
    }

    private Map<String, ToolHandler> initHandlers(
            MessageApiService messageApiService,
            ContactApiService contactApiService,
            TagApiService tagApiService,
            CallbackEventApiService callbackEventApiService,
            ConfigurationApiService configurationApiService) {
        Map<String, ToolHandler> map = new HashMap<>();
        map.put("messages_send", messageApiService::sendMessage);

        map.put("contacts_add", contactApiService::addContacts);
        map.put("contacts_update", contactApiService::updateContact);
        map.put("contacts_get_all", contactApiService::getAllContacts);
        map.put("contacts_get_summary", contactApiService::getContactsSummary);
        map.put("contacts_get", contactApiService::getContact);
        map.put("contacts_delete", contactApiService::deleteContact);
        map.put("contacts_find_multiple_phones", contactApiService::findContactWithMultiplePhoneNumbers);
        map.put("contacts_find", contactApiService::findContact);

        map.put("tags_create", tagApiService::createTag);
        map.put("tags_update", tagApiService::updateTag);
        map.put("tags_get", tagApiService::getTag);
        map.put("tags_get_all", tagApiService::getAllTags);
        map.put("tags_get_summary", tagApiService::getTagsSummary);
        map.put("tags_assign_contacts", tagApiService::assignContactsToTag);
        map.put("tags_delete", tagApiService::deleteTag);
        map.put("tags_remove_contacts", tagApiService::removeContactsFromTag);

        map.put("events_phone_added_wrong_number", (args, ac, pc) -> callbackEventApiService.fetchPagedEvents("/api/v1/events/phoneNumberAddedToWrongNumber.json", args, ac, pc));
        map.put("events_outgoing_delivery_status", (args, ac, pc) -> callbackEventApiService.fetchPagedEvents("/api/v1/events/outgoingMessageDeliveryStatus.json", args, ac, pc));
        map.put("events_new_contact_details", (args, ac, pc) -> callbackEventApiService.fetchPagedEvents("/api/v1/events/newContactDetails.json", args, ac, pc));
        map.put("events_disassociate_contact_tag", (args, ac, pc) -> callbackEventApiService.fetchPagedEvents("/api/v1/events/disassociateContactFromTag.json", args, ac, pc));
        map.put("events_incoming_message", (args, ac, pc) -> callbackEventApiService.fetchPagedEvents("/api/v1/events/incomingMessageDetail.json", args, ac, pc));
        map.put("events_phone_added_dnt", (args, ac, pc) -> callbackEventApiService.fetchPagedEvents("/api/v1/events/phoneNumberAddedToDNT.json", args, ac, pc));
        map.put("events_associate_contact_tag", (args, ac, pc) -> callbackEventApiService.fetchPagedEvents("/api/v1/events/associateContactToTag.json", args, ac, pc));
        map.put("events_phone_removed_dnt", (args, ac, pc) -> callbackEventApiService.fetchPagedEvents("/api/v1/events/phoneNumberRemovedFromDNT.json", args, ac, pc));

        map.put("webhook_subscribe", configurationApiService::webhookSubscribe);
        map.put("webhook_unsubscribe", configurationApiService::webhookUnsubscribe);
        map.put("webhook_list_subscriptions", configurationApiService::listSubscriptions);
        return Collections.unmodifiableMap(map);
    }

    public Map<String, Object> execute(String toolName, SchemaBackedToolRequest request) throws Exception {
        Map<String, Object> arguments = request == null ? new HashMap<>() : request.asMap();
        schemaValidationService.validate(toolName, arguments);

        boolean readOnly = READ_TOOLS.contains(toolName);
        checkScope(readOnly ? "read" : "write");
        if (readOnly ? !rateLimitService.allowRead() : !rateLimitService.allowWrite()) {
            auditLogService.logFailure(toolName, arguments, "Rate limit exceeded");
            return errorResult("This connector has reached its rate limit. Please wait about 60 seconds before retrying.");
        }

        ToolExecutionContext credentials = credentialResolver.resolve(arguments);
        if (credentials.authCode() == null || credentials.authCode().isBlank()) {
            auditLogService.logFailure(toolName, arguments, "Missing authCode");
            return errorResult("authCode is required in JWT claims (must be added by OAuth2 server)");
        }

        ToolHandler handler = handlers.get(toolName);
        if (handler == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }

        try {
            Object raw = handler.execute(arguments, credentials.authCode(), credentials.partnerClientCode());
            auditLogService.logSuccess(toolName, arguments);
            return successResult(raw);
        } catch (Exception e) {
            auditLogService.logFailure(toolName, arguments, "Execution error: " + e.getMessage());
            throw e;
        }
    }

    private void checkScope(String requiredScope) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new IllegalArgumentException("Insufficient permissions. Required scope: " + requiredScope);
        }
        Set<String> scopes = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("SCOPE_"))
                .map(a -> a.substring(6))
                .collect(Collectors.toSet());
        if (!scopes.contains(requiredScope)) {
            throw new IllegalArgumentException("Insufficient permissions. Required scope: " + requiredScope);
        }
    }

    private Map<String, Object> successResult(Object rawResult) throws Exception {
        Object parsedResult = rawResult;
        if (rawResult instanceof String text) {
            try {
                parsedResult = objectMapper.readValue(text, Object.class);
            } catch (Exception ignored) {
                parsedResult = rawResult;
            }
        }
        Object dataToReturn = parsedResult;
        if (parsedResult instanceof Map<?, ?> map && map.containsKey("data")) {
            dataToReturn = map.get("data");
        }
        return contentResult(dataToReturn, false);
    }

    private Map<String, Object> errorResult(String text) {
        return contentResult(text, true);
    }

    private Map<String, Object> contentResult(Object payload, boolean isError) {
        Map<String, Object> contentItem = new LinkedHashMap<>();
        contentItem.put("type", "text");
        try {
            contentItem.put("text", payload instanceof String ? payload : objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            contentItem.put("text", String.valueOf(payload));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", List.of(contentItem));
        result.put("isError", isError);
        return result;
    }
}
