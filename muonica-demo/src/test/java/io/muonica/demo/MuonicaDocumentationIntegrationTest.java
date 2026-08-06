package io.muonica.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.muonica.core.model.ApiProject;
import io.muonica.core.model.ApiEndpoint;
import io.muonica.core.model.DocumentationBlock;
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
        assertNotNull(project.schemas().get("UserResponse"));
        assertTrue(project.schemas().get("UserResponse").properties().containsKey("role"));
        assertEquals("markdown", project.documentationBlocks().get(0).type());
        ApiEndpoint getUser = project.groups().stream().flatMap(group -> group.endpoints().stream())
                .filter(endpoint -> endpoint.path().equals("/users/{id}"))
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
    }

    @Test
    void exportsOpenApiAndServesDocumentationUi() throws Exception {
        Map<String, Object> document = documentationController.openapi();

        assertEquals("3.1.1", document.get("openapi"));
        assertTrue(((Map<?, ?>) document.get("paths")).containsKey("/users/{id}"));
        assertTrue(((Map<?, ?>) document.get("components")).containsKey("securitySchemes"));
        mockMvc.perform(get("/muonica"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/muonica/index.html"));
        mockMvc.perform(get("/muonica/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("tailwindcss.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("./js/app.js")));
        mockMvc.perform(get("/muonica/js/api.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("const API_URL = \"./api\"")));
        mockMvc.perform(get("/muonica/js/render.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"./openapi.json\"")));
    }
}
