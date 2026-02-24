package com.textellent.mcp.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for Textellent Contact API operations.
 */
@Service
public class ContactApiService {

    private static final Logger logger = LoggerFactory.getLogger(ContactApiService.class);

    @Autowired
    private WebClient webClient;

    /**
     * Add contacts using Textellent API.
     * POST /api/v1/contacts.json
     */
    public Object addContacts(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Adding contacts with arguments: {}", arguments);

        try {
            Object contacts = arguments.get("contacts");

            String response = webClient.post()
                    .uri("/api/v1/contacts.json")
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(contacts)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error adding contacts", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to add contacts", e);
            throw new RuntimeException("Failed to add contacts: " + e.getMessage(), e);
        }
    }

    /**
     * Update a contact using Textellent API.
     * PUT /api/v1/contacts.json?contactId={contactId}
     */
    public Object updateContact(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Updating contact with arguments: {}", arguments);

        try {
            String contactId = (String) arguments.get("contactId");
            Map<String, Object> contactData = (Map<String, Object>) arguments.get("contactData");

            String response = webClient.put()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/contacts.json")
                            .queryParam("contactId", contactId)
                            .build())
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(contactData)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error updating contact", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to update contact", e);
            throw new RuntimeException("Failed to update contact: " + e.getMessage(), e);
        }
    }

    /**
     * Get all contacts using Textellent API.
     * GET /api/v1/contacts.json?searchKey={searchKey}&pageSize={pageSize}&pageNum={pageNum}
     */
    public Object getAllContacts(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Getting all contacts with arguments: {}", arguments);

        try {
            String searchKey = (String) arguments.getOrDefault("searchKey", "");
            Integer pageSize = (Integer) arguments.getOrDefault("pageSize", 10);
            Integer pageNum = (Integer) arguments.getOrDefault("pageNum", 1);

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/contacts.json")
                            .queryParam("searchKey", searchKey)
                            .queryParam("pageSize", pageSize)
                            .queryParam("pageNum", pageNum)
                            .build())
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error getting all contacts", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            // Parse response and expose full contact objects (not a simplified summary).
            if (response != null) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode rootNode = mapper.readTree(response);

                    // Check if this is an actual error response
                    if (rootNode.has("error") && rootNode.get("error").isTextual()) {
                        logger.warn("Backend returned error: {}", response);
                        return response;
                    }

                    JsonNode dataNode = rootNode;
                    if (rootNode.has("text")) {
                        logger.info("Response has 'text' field, parsing as JSON");
                        String textContent = rootNode.get("text").asText();
                        dataNode = mapper.readTree(textContent);
                    }

                    if (dataNode.has("data")) {
                        dataNode = dataNode.get("data");
                    }

                    JsonNode contactsNode;
                    JsonNode totalCountNode = null;
                    JsonNode pageSizeNode = null;
                    JsonNode pageNumNode = null;
                    if (dataNode.isArray()) {
                        contactsNode = dataNode;
                    } else {
                        contactsNode = dataNode.get("contacts");
                        totalCountNode = dataNode.get("totalCount");
                        pageSizeNode = dataNode.get("pageSize");
                        pageNumNode = dataNode.get("pageNum");
                    }

                    if (contactsNode != null && contactsNode.isArray()) {
                        Map<String, Object> fullResponse = new HashMap<>();

                        // Keep full contact objects exactly as provided by the backend.
                        Object fullContacts = mapper.convertValue(contactsNode, Object.class);
                        fullResponse.put("contacts", fullContacts);

                        int totalCount = totalCountNode != null && totalCountNode.isInt()
                                ? totalCountNode.asInt()
                                : contactsNode.size();
                        fullResponse.put("totalCount", totalCount);

                        int effectivePageSize = pageSizeNode != null && pageSizeNode.isInt()
                                ? pageSizeNode.asInt()
                                : (pageSize != null ? pageSize : contactsNode.size());
                        int effectivePageNum = pageNumNode != null && pageNumNode.isInt()
                                ? pageNumNode.asInt()
                                : (pageNum != null ? pageNum : 1);

                        fullResponse.put("pageSize", effectivePageSize);
                        fullResponse.put("pageNum", effectivePageNum);

                        logger.info("Returning {} full contacts out of {} total (page {}/{})",
                                contactsNode.size(),
                                totalCount,
                                effectivePageNum,
                                effectivePageSize);

                        return mapper.writeValueAsString(fullResponse);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse contacts_get_all response, returning raw response", e);
                    return response;
                }
            }

            // Should not reach here
            logger.warn("No valid contacts array found in response");
            return response;
        } catch (Exception e) {
            logger.error("Failed to get all contacts", e);
            throw new RuntimeException("Failed to get all contacts: " + e.getMessage(), e);
        }
    }

    /**
     * Get a summary of contacts (count + simplified contact list) to avoid ChatGPT hallucination.
     * This is the DEFAULT tool for listing contacts - returns minimal data.
     * GET /api/v1/contacts.json?searchKey=&pageSize=&pageNum=
     */
    public Object getContactsSummary(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Getting contacts summary with arguments: {}", arguments);

        try {
            String searchKey = (String) arguments.getOrDefault("searchKey", "");
            Integer pageSize = (Integer) arguments.getOrDefault("pageSize", 10);
            Integer pageNum = (Integer) arguments.getOrDefault("pageNum", 1);

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/contacts.json")
                            .queryParam("searchKey", searchKey)
                            .queryParam("pageSize", pageSize)
                            .queryParam("pageNum", pageNum)
                            .build())
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error getting contacts summary", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            // Parse response and extract totalCount + first 10 contacts (simplified)
            if (response != null) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode rootNode = mapper.readTree(response);

                    // Check if this is an actual error response
                    if (rootNode.has("error") && rootNode.get("error").isTextual()) {
                        logger.warn("Backend returned error: {}", response);
                        return response;
                    }

                    JsonNode dataNode = rootNode;
                    if (rootNode.has("text")) {
                        logger.info("Response has 'text' field, parsing as JSON");
                        String textContent = rootNode.get("text").asText();
                        dataNode = mapper.readTree(textContent);
                    }

                    if (dataNode.has("data")) {
                        dataNode = dataNode.get("data");
                    }

                    JsonNode contactsNode;
                    JsonNode totalCountNode = null;
                    if (dataNode.isArray()) {
                        contactsNode = dataNode;
                    } else {
                        contactsNode = dataNode.get("contacts");
                        totalCountNode = dataNode.get("totalCount");
                    }

                    Map<String, Object> summaryResponse = new HashMap<>();

                    List<Map<String, String>> simplifiedContacts = new ArrayList<>();
                    if (contactsNode != null && contactsNode.isArray()) {
                        for (JsonNode contact : contactsNode) {
                            Map<String, String> simpleContact = new HashMap<>();

                            JsonNode firstNameNode = contact.get("firstName");
                            JsonNode lastNameNode = contact.get("lastName");
                            String name = "";
                            if (firstNameNode != null && lastNameNode != null) {
                                name = firstNameNode.asText() + " " + lastNameNode.asText();
                            } else if (firstNameNode != null) {
                                name = firstNameNode.asText();
                            } else if (lastNameNode != null) {
                                name = lastNameNode.asText();
                            }
                            simpleContact.put("name", name.trim());

                            JsonNode phoneNode = contact.get("phoneNumber");
                            if (phoneNode == null) {
                                phoneNode = contact.get("mobile");
                            }
                            if (phoneNode != null) {
                                simpleContact.put("phone", phoneNode.asText());
                            }

                            JsonNode idNode = contact.get("id");
                            if (idNode != null) {
                                simpleContact.put("id", idNode.asText());
                            }

                            simplifiedContacts.add(simpleContact);
                        }
                    }

                    int totalCount = totalCountNode != null ? totalCountNode.asInt() : simplifiedContacts.size();
                    int pSize = pageSize != null ? pageSize : 10;
                    int pNum = pageNum != null ? pageNum : 1;
                    boolean hasMore = (pNum * pSize) < totalCount;
                    summaryResponse.put("totalCount", totalCount);
                    summaryResponse.put("contacts", simplifiedContacts);
                    summaryResponse.put("hasMore", hasMore);
                    summaryResponse.put("pageSize", pageSize);
                    summaryResponse.put("pageNum", pageNum);

                    if (searchKey != null && !searchKey.isEmpty()) {
                        summaryResponse.put("searchKey", searchKey);
                    }

                    logger.info("Returning contacts summary: totalCount={}, page {}/{}, hasMore={}",
                            totalCount, pNum, (totalCount + pSize - 1) / pSize, hasMore);

                    return mapper.writeValueAsString(summaryResponse);
                } catch (Exception e) {
                    logger.warn("Failed to parse contacts summary response, returning raw response", e);
                    return response;
                }
            }

            // Should not reach here
            logger.warn("No valid response received");
            return response;
        } catch (Exception e) {
            logger.error("Failed to get contacts summary", e);
            throw new RuntimeException("Failed to get contacts summary: " + e.getMessage(), e);
        }
    }

    /**
     * Get a specific contact by ID using Textellent API.
     * GET /api/v1/contacts/{contactId}.json
     */
    public Object getContact(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Getting contact with arguments: {}", arguments);

        try {
            String contactId = (String) arguments.get("contactId");

            String response = webClient.get()
                    .uri("/api/v1/contacts/" + contactId + ".json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error getting contact", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to get contact", e);
            throw new RuntimeException("Failed to get contact: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a contact by ID using Textellent API.
     * DELETE /api/v1/contacts/{contactId}.json
     */
    public Object deleteContact(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Deleting contact with arguments: {}", arguments);

        try {
            String contactId = (String) arguments.get("contactId");

            String response = webClient.delete()
                    .uri("/api/v1/contacts/" + contactId + ".json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error deleting contact", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to delete contact", e);
            throw new RuntimeException("Failed to delete contact: " + e.getMessage(), e);
        }
    }

    /**
     * Find contact with multiple phone numbers using Textellent API.
     * GET /api/v1/findContactWithMultiplePhoneNumbers.json?extId={extId}&phoneNumbers={phoneNumbers}
     */
    public Object findContactWithMultiplePhoneNumbers(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Finding contact with multiple phone numbers: {}", arguments);

        try {
            String extId = (String) arguments.getOrDefault("extId", "");
            String phoneNumbers = (String) arguments.get("phoneNumbers");

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/findContactWithMultiplePhoneNumbers.json")
                            .queryParam("extId", extId)
                            .queryParam("phoneNumbers", phoneNumbers)
                            .build())
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error finding contact with multiple phone numbers", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to find contact with multiple phone numbers", e);
            throw new RuntimeException("Failed to find contact with multiple phone numbers: " + e.getMessage(), e);
        }
    }

    /**
     * Find contact using Textellent API.
     * GET /api/v1/findContact.json?extId={extId}&phoneNumber={phoneNumber}&email={email}
     */
    public Object findContact(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Finding contact with arguments: {}", arguments);

        try {
            String extId = (String) arguments.getOrDefault("extId", "");
            String phoneNumber = (String) arguments.getOrDefault("phoneNumber", "");
            String email = (String) arguments.getOrDefault("email", "");

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/findContact.json")
                            .queryParam("extId", extId)
                            .queryParam("phoneNumber", phoneNumber)
                            .queryParam("email", email)
                            .build())
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error finding contact", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to find contact", e);
            throw new RuntimeException("Failed to find contact: " + e.getMessage(), e);
        }
    }
}
