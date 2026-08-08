package io.muonica.core.model.api;

import io.muonica.core.model.documentation.DocumentationBlock;
import io.muonica.core.model.documentation.DocumentationWarning;
import io.muonica.core.model.documentation.ApiDocumentationPage;
import io.muonica.core.model.security.ApiSecurityScheme;
import java.util.List;
import java.util.Map;

/** Root of a Muonica documentation tree. */
public record ApiProject(
        String name,
        String version,
        String description,
        List<ApiGroup> groups,
        List<DocumentationBlock> documentationBlocks,
        List<DocumentationWarning> documentationWarnings,
        List<ApiDocumentationPage> documentationPages,
        List<ApiSecurityScheme> securitySchemes,
        List<ApiServer> servers,
        Map<String, ApiSchema> schemas
) {
    public ApiProject {
        version = version == null || version.isBlank() ? "0.0.0" : version;
        groups = List.copyOf(groups);
        documentationBlocks = List.copyOf(documentationBlocks);
        documentationWarnings = List.copyOf(documentationWarnings);
        documentationPages = List.copyOf(documentationPages);
        securitySchemes = List.copyOf(securitySchemes);
        servers = List.copyOf(servers);
        schemas = Map.copyOf(schemas);
    }

    public ApiProject(String name, List<ApiGroup> groups, List<DocumentationBlock> documentationBlocks) {
        this(name, "0.0.0", null, groups, documentationBlocks, List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    public ApiProject(String name, String version, String description, List<ApiGroup> groups,
            List<DocumentationBlock> documentationBlocks, List<ApiSecurityScheme> securitySchemes,
            Map<String, ApiSchema> schemas) {
        this(name, version, description, groups, documentationBlocks, List.of(), List.of(), securitySchemes, List.of(), schemas);
    }
}
