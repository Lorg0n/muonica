package io.muonica.core.model.api;

import java.util.Map;

public record ApiResponse(String statusCode, String description, Map<String, ApiSchema> content) {
    public ApiResponse {
        content = Map.copyOf(content);
    }

    public ApiResponse(String statusCode, String description, String contentType, ApiSchema schema) {
        this(statusCode, description, schema == null ? Map.of() : Map.of(contentType, schema));
    }
}
