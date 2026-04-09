package com.textellent.mcp.core;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight request base that captures arbitrary properties.
 * Canonical validation still comes from JSON schemas in resources/schemas.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class SchemaBackedToolRequest {

    private final Map<String, Object> values = new LinkedHashMap<>();

    @JsonAnySetter
    public void put(String key, Object value) {
        values.put(key, value);
    }

    @JsonIgnore
    public Map<String, Object> asMap() {
        return new LinkedHashMap<>(values);
    }

    @JsonIgnore
    public String partnerClientCode() {
        Object partnerCode = values.get("partnerClientCode");
        return partnerCode instanceof String ? (String) partnerCode : null;
    }
}
