package io.muonica.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.muonica.core.model.ApiProject;
import io.muonica.spring.web.MuonicaDocumentationController;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MuonicaDocumentationIntegrationTest {
    @Autowired
    private MuonicaDocumentationController documentationController;

    @Test
    void exposesMuonicaModelWithSchemasAndDocumentation() {
        ApiProject project = documentationController.api();

        assertEquals("Muonica demo API", project.name());
        assertTrue(project.groups().stream().anyMatch(group -> group.name().equals("Users")));
        assertNotNull(project.schemas().get("UserResponse"));
        assertTrue(project.schemas().get("UserResponse").properties().containsKey("role"));
    }

    @Test
    void exportsOpenApiAndServesDocumentationUi() throws Exception {
        Map<String, Object> document = documentationController.openapi();

        assertEquals("3.1.1", document.get("openapi"));
        assertTrue(((Map<?, ?>) document.get("paths")).containsKey("/users/{id}"));
        assertTrue(((Map<?, ?>) document.get("components")).containsKey("securitySchemes"));
        assertEquals("redirect:/muonica/index.html", documentationController.home());

        ClassPathResource ui = new ClassPathResource("META-INF/resources/muonica/index.html");
        assertTrue(ui.exists());
        String html = new String(ui.getInputStream().readAllBytes());
        assertTrue(html.contains("tailwindcss.com"));
        assertTrue(html.contains("./js/app.js"));
    }
}
