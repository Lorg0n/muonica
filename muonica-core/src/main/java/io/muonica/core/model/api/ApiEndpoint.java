package io.muonica.core.model.api;

import io.muonica.core.model.documentation.DocumentationBlock;
import io.muonica.core.model.documentation.DocumentationWarning;
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
        List<List<String>> securityRequirements,
        List<String> badges,
        List<DocumentationWarning> documentationWarnings
) {
    public ApiEndpoint {
        parameters = List.copyOf(parameters);
        responses = List.copyOf(responses);
        documentationBlocks = List.copyOf(documentationBlocks);
        securityRequirements = securityRequirements.stream().map(List::copyOf).toList();
        badges = List.copyOf(badges);
        documentationWarnings = List.copyOf(documentationWarnings);
    }

    public ApiEndpoint(String method, String path, String controller, String handler, String summary, String description,
            List<ApiParameter> parameters, ApiRequest request, List<ApiResponse> responses,
            List<DocumentationBlock> documentationBlocks, List<List<String>> securityRequirements) {
        this(method, path, controller, handler, summary, description, parameters, request, responses,
                documentationBlocks, securityRequirements, List.of(), List.of());
    }

    public ApiEndpoint(String method, String path, String controller, String handler) {
        this(method, path, controller, handler, null, null, List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
