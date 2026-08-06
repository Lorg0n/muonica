package io.muonica.core.model;

import java.util.List;

public record ApiGroup(String name, String description, List<ApiEndpoint> endpoints, List<DocumentationBlock> documentationBlocks) {
    public ApiGroup {
        endpoints = List.copyOf(endpoints);
        documentationBlocks = List.copyOf(documentationBlocks);
    }

    public ApiGroup(String name, List<ApiEndpoint> endpoints, List<DocumentationBlock> documentationBlocks) {
        this(name, null, endpoints, documentationBlocks);
    }
}
