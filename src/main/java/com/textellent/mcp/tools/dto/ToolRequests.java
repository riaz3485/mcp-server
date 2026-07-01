package com.textellent.mcp.tools.dto;

import com.textellent.mcp.core.SchemaBackedToolRequest;

public final class ToolRequests {

    private ToolRequests() {}

    public static final class MessagesSendRequest extends SchemaBackedToolRequest {}

    public static final class ContactsAddRequest extends SchemaBackedToolRequest {}
    public static final class ContactsUpdateRequest extends SchemaBackedToolRequest {}
    public static final class ContactsGetAllRequest extends SchemaBackedToolRequest {}
    public static final class ContactsGetSummaryRequest extends SchemaBackedToolRequest {}
    public static final class ContactsGetRequest extends SchemaBackedToolRequest {}
    public static final class ContactsDeleteRequest extends SchemaBackedToolRequest {}
    public static final class ContactsFindMultiplePhonesRequest extends SchemaBackedToolRequest {}
    public static final class ContactsFindRequest extends SchemaBackedToolRequest {}

    public static final class TagsCreateRequest extends SchemaBackedToolRequest {}
    public static final class TagsUpdateRequest extends SchemaBackedToolRequest {}
    public static final class TagsGetRequest extends SchemaBackedToolRequest {}
    public static final class TagsGetAllRequest extends SchemaBackedToolRequest {}
    public static final class TagsGetSummaryRequest extends SchemaBackedToolRequest {}
    public static final class TagsAssignContactsRequest extends SchemaBackedToolRequest {}
    public static final class TagsDeleteRequest extends SchemaBackedToolRequest {}
    public static final class TagsRemoveContactsRequest extends SchemaBackedToolRequest {}

    public static final class EventsPhoneAddedWrongNumberRequest extends SchemaBackedToolRequest {}
    public static final class EventsOutgoingDeliveryStatusRequest extends SchemaBackedToolRequest {}
    public static final class EventsNewContactDetailsRequest extends SchemaBackedToolRequest {}
    public static final class EventsDisassociateContactTagRequest extends SchemaBackedToolRequest {}
    public static final class EventsIncomingMessageRequest extends SchemaBackedToolRequest {}
    public static final class EventsPhoneAddedDntRequest extends SchemaBackedToolRequest {}
    public static final class EventsAssociateContactTagRequest extends SchemaBackedToolRequest {}
    public static final class EventsPhoneRemovedDntRequest extends SchemaBackedToolRequest {}

    public static final class WebhookSubscribeRequest extends SchemaBackedToolRequest {}
    public static final class WebhookUnsubscribeRequest extends SchemaBackedToolRequest {}
    public static final class WebhookListSubscriptionsRequest extends SchemaBackedToolRequest {}
}
