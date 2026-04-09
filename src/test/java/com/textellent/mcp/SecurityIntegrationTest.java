package com.textellent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for security and scope enforcement.
 */
@SpringBootTest(properties = "security.mode=local")
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testHealthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    public void testVersionEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/version"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("textellent-mcp-server"));
    }

    @Test
    public void testMcpEndpointRequiresSessionForStreamablePost() throws Exception {
        Map<String, Object> payload = Map.of(
            "jsonrpc", "2.0",
            "id", 1,
            "method", "tools/list",
            "params", Map.of()
        );

        mockMvc.perform(post("/mcp")
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.jsonRpcError.message").value("Session ID missing"));
    }

    @Test
    public void testMcpEndpointRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/mcp")
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.jsonRpcError").exists());
    }
}
