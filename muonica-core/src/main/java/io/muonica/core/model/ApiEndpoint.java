package io.muonica.core.model;

import java.util.List;

/** A single HTTP operation. */
public record ApiEndpoint(
        String method,
        String path,
        String controller,
        String handler,
        List<ApiParameter> parameters,
        ApiRequest request,
        List<ApiResponse> responses,
        List<DocumentationBlock> documentationBlocks
) {
    public ApiEndpoint {
        parameters = List.copyOf(parameters);
        responses = List.copyOf(responses);
        documentationBlocks = List.copyOf(documentationBlocks);
    }

    public ApiEndpoint(String method, String path, String controller, String handler) {
        this(method, path, controller, handler, List.of(), null, List.of(), List.of());
    }
}
