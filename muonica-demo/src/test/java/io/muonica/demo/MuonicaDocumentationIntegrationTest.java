package io.muonica.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.muonica.core.model.api.ApiEndpoint;
import io.muonica.core.model.api.ApiProject;
import io.muonica.core.model.documentation.DocumentationBlock;
import java.util.List;
import io.muonica.spring.web.MuonicaDocumentationController;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class MuonicaDocumentationIntegrationTest {
    @Autowired
    private MuonicaDocumentationController documentationController;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void exposesMuonicaModelWithSchemasAndDocumentation() {
        ApiProject project = documentationController.api();

        assertEquals("Muonica demo API", project.name());
        assertTrue(project.groups().stream().anyMatch(group -> group.name().equals("Users")));
        assertTrue(project.groups().stream().anyMatch(group -> group.name().equals("Orders")));
        assertTrue(project.groups().stream().anyMatch(group -> group.name().equals("Reports")));
        assertEquals("Authorization", project.securitySchemes().stream()
                .filter(scheme -> scheme.name().equals("bearerAuth"))
                .findFirst().orElseThrow().parameterName());
        assertTrue(project.groups().stream().flatMap(group -> group.endpoints().stream()).count() >= 16);
        assertNotNull(project.schemas().get("UserResponse"));
        assertTrue(project.schemas().get("UserResponse").properties().containsKey("role"));
        assertEquals("markdown", project.documentationBlocks().get(0).type());
        ApiEndpoint getUser = project.groups().stream().flatMap(group -> group.endpoints().stream())
                .filter(endpoint -> endpoint.method().equals("GET") && endpoint.path().equals("/users/{id}"))
                .findFirst().orElseThrow();
        assertEquals(List.of("markdown", "markdown", "notice", "slot", "markdown", "notice", "slot", "markdown", "slot", "slot"),
                getUser.documentationBlocks().stream().map(DocumentationBlock::type).toList());
        assertEquals(List.of("security", "request", "responses", "parameters"),
                getUser.documentationBlocks().stream().filter(block -> block.type().equals("slot"))
                        .map(block -> block.attributes().get("name").toString()).toList());
        assertTrue(getUser.documentationBlocks().stream().anyMatch(block -> block.origin().name().equals("INHERITED")));
        assertTrue(getUser.documentationBlocks().stream().anyMatch(block -> block.type().equals("slot")
                && block.attributes().get("name").equals("parameters")
                && block.attributes().get("generated").equals(true)));
        assertFalse(project.groups().stream().flatMap(group -> group.endpoints().stream())
                .anyMatch(endpoint -> endpoint.path().equals("/users/{userId}/preview")));
        ApiEndpoint orders = project.groups().stream().flatMap(group -> group.endpoints().stream())
                .filter(endpoint -> endpoint.path().equals("/orders") && endpoint.method().equals("GET")).findFirst().orElseThrow();
        assertEquals(List.of("page", "size", "sort"), orders.parameters().stream()
                .filter(parameter -> parameter.name().equals("page") || parameter.name().equals("size") || parameter.name().equals("sort"))
                .map(parameter -> parameter.name()).toList());
        assertEquals("25", orders.parameters().stream().filter(parameter -> parameter.name().equals("size")).findFirst().orElseThrow().schema().defaultValue());
        ApiEndpoint createUser = project.groups().stream().flatMap(group -> group.endpoints().stream())
                .filter(endpoint -> endpoint.path().equals("/users") && endpoint.method().equals("POST")).findFirst().orElseThrow();
        assertEquals(List.of("ADMIN"), createUser.badges());
        assertTrue(createUser.responses().stream().filter(response -> response.statusCode().equals("201")).findFirst().orElseThrow()
                .headers().containsKey("Location"));
        assertEquals(2, project.servers().size());
    }

    @Test
    void exportsOpenApiAndServesDocumentationUi() throws Exception {
        Map<String, Object> document = documentationController.openapi();

        assertEquals("3.1.1", document.get("openapi"));
        assertTrue(((Map<?, ?>) document.get("paths")).containsKey("/users/{id}"));
        assertTrue(((Map<?, ?>) document.get("components")).containsKey("securitySchemes"));
        assertEquals(2, ((List<?>) document.get("servers")).size());
        Map<?, ?> users = (Map<?, ?>) ((Map<?, ?>) document.get("paths")).get("/users");
        assertEquals(List.of("ADMIN"), ((Map<?, ?>) users.get("post")).get("x-muonica-badges"));
        mockMvc.perform(get("/docs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/docs/"));
        mockMvc.perform(get("/docs/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("tailwindcss.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("./js/app.js")));
        mockMvc.perform(get("/docs/js/api.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("const API_URL = \"./api\"")));
        mockMvc.perform(get("/docs/js/render.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"./openapi.json\"")));
        mockMvc.perform(get("/docs/api")).andExpect(status().isOk());
        mockMvc.perform(get("/docs/openapi.json")).andExpect(status().isOk());
        mockMvc.perform(get("/users/1/preview")).andExpect(status().isOk());
        mockMvc.perform(get("/muonica")).andExpect(status().isNotFound());
    }
}
