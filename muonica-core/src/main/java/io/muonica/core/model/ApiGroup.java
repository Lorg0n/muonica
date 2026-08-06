package io.muonica.core.model;

import java.util.List;

public record ApiGroup(String name, String description, List<ApiEndpoint> endpoints, List<DocumentationBlock> documentationBlocks,
        List<DocumentationWarning> documentationWarnings) {
    public ApiGroup {
        endpoints = List.copyOf(endpoints);
        documentationBlocks = List.copyOf(documentationBlocks);
        documentationWarnings = List.copyOf(documentationWarnings);
    }

    public ApiGroup(String name, String description, List<ApiEndpoint> endpoints, List<DocumentationBlock> documentationBlocks) {
        this(name, description, endpoints, documentationBlocks, List.of());
    }

    public ApiGroup(String name, List<ApiEndpoint> endpoints, List<DocumentationBlock> documentationBlocks) {
        this(name, null, endpoints, documentationBlocks);
    }
}
