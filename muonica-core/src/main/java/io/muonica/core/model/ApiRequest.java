package io.muonica.core.model;

import java.util.Map;

public record ApiRequest(boolean required, String description, Map<String, ApiSchema> content) {
    public ApiRequest {
        content = Map.copyOf(content);
    }

    public ApiRequest(String contentType, boolean required, ApiSchema schema) {
        this(required, null, Map.of(contentType, schema));
    }
}
