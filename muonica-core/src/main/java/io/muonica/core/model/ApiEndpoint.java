package io.muonica.core.model;

import java.util.List;

/** A single HTTP operation. */
public record ApiEndpoint(
        String method,
        String path,
        String controller,
        String handler,
        String summary,
        String description,
        List<ApiParameter> parameters,
        ApiRequest request,
        List<ApiResponse> responses,
        List<DocumentationBlock> documentationBlocks,
        List<String> securityRequirements
) {
    public ApiEndpoint {
        parameters = List.copyOf(parameters);
        responses = List.copyOf(responses);
        documentationBlocks = List.copyOf(documentationBlocks);
        securityRequirements = List.copyOf(securityRequirements);
    }

    public ApiEndpoint(String method, String path, String controller, String handler) {
        this(method, path, controller, handler, null, null, List.of(), null, List.of(), List.of(), List.of());
    }
}
