package com.textellent.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.textellent.maestro.orchestration_layer.tool_registration.ManagedToolRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "security.mode=local")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ManagedToolExposureIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private List<ToolCallback> toolCallbacks;

    @org.springframework.beans.factory.annotation.Autowired
    private ManagedToolRegistry managedToolRegistry;

    @Test
    void contactsAddToolExposedToClientsUsesManagedWrapperDefinition() {
        ToolCallback exposedContactsAdd = toolCallbacks.stream()
                .filter(tool -> "contacts_add".equals(tool.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();

        assertThat(exposedContactsAdd.getToolDefinition().description())
                .contains("managed by the Maestro DSL framework");
        assertThat(exposedContactsAdd.getToolDefinition().description())
                .contains("dsl_execute_plan");
        assertThat(exposedContactsAdd.getToolDefinition().inputSchema())
                .contains("contactFirstName")
                .contains("phoneMobile");
    }

    @Test
    void exposedToolDefinitionsMatchManagedRegistryDefinitionsForManagedTools() {
        for (ToolCallback managedTool : managedToolRegistry.getManagedTools()) {
            String toolName = managedTool.getToolDefinition().name();
            ToolCallback exposedTool = toolCallbacks.stream()
                    .filter(tool -> toolName.equals(tool.getToolDefinition().name()))
                    .findFirst()
                    .orElseThrow();
            assertThat(exposedTool.getToolDefinition().description())
                    .isEqualTo(managedTool.getToolDefinition().description());
            assertThat(exposedTool.getToolDefinition().inputSchema())
                    .isEqualTo(managedTool.getToolDefinition().inputSchema());
        }
    }
}
