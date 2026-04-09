package com.textellent.mcp.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ToolSchemaValidationService {

    private static final Logger logger = LoggerFactory.getLogger(ToolSchemaValidationService.class);

    private final ObjectMapper objectMapper;
    private final Map<String, Schema> inputSchemas = new ConcurrentHashMap<>();

    public ToolSchemaValidationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadSchemas() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:schemas/*.json");
            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    String filename = resource.getFilename();
                    if (filename == null) {
                        continue;
                    }
                    String toolName = filename.replace(".json", "");
                    Map<String, Object> schemaDoc = objectMapper.readValue(is, new TypeReference<Map<String, Object>>() {});
                    @SuppressWarnings("unchecked")
                    Map<String, Object> inputSchema = (Map<String, Object>) schemaDoc.get("inputSchema");
                    if (inputSchema != null) {
                        Schema schema = SchemaLoader.load(new JSONObject(inputSchema));
                        inputSchemas.put(toolName, schema);
                    }
                } catch (Exception inner) {
                    logger.error("Unable to load schema {}", resource.getFilename(), inner);
                }
            }
            logger.info("Loaded {} input schemas for tool validation", inputSchemas.size());
        } catch (Exception e) {
            logger.error("Unable to scan schema resources", e);
        }
    }

    public void validate(String toolName, Map<String, Object> arguments) {
        Schema schema = inputSchemas.get(toolName);
        if (schema == null || arguments == null) {
            return;
        }
        schema.validate(new JSONObject(arguments));
    }
}
