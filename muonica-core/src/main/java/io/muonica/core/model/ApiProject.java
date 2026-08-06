package io.muonica.core.model;

import java.util.List;
import java.util.Map;

/** Root of a Muonica documentation tree. */
public record ApiProject(
        String name,
        String version,
        String description,
        List<ApiGroup> groups,
        List<DocumentationBlock> documentationBlocks,
        List<ApiSecurityScheme> securitySchemes,
        Map<String, ApiSchema> schemas
) {
    public ApiProject {
        version = version == null || version.isBlank() ? "0.0.0" : version;
        groups = List.copyOf(groups);
        documentationBlocks = List.copyOf(documentationBlocks);
        securitySchemes = List.copyOf(securitySchemes);
        schemas = Map.copyOf(schemas);
    }

    public ApiProject(String name, List<ApiGroup> groups, List<DocumentationBlock> documentationBlocks) {
        this(name, "0.0.0", null, groups, documentationBlocks, List.of(), Map.of());
    }
}
