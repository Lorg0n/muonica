package io.muonica.core.model.api;

import java.util.Map;

public record ApiResponse(String statusCode, String description, Map<String, ApiSchema> content, Map<String, ApiHeader> headers) {
    public ApiResponse {
        content = Map.copyOf(content);
        headers = Map.copyOf(headers);
    }

    public ApiResponse(String statusCode, String description, Map<String, ApiSchema> content) {
        this(statusCode, description, content, Map.of());
    }

    public ApiResponse(String statusCode, String description, String contentType, ApiSchema schema) {
        this(statusCode, description, schema == null ? Map.of() : Map.of(contentType, schema), Map.of());
    }
}
