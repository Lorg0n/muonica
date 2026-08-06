package io.muonica.core.model;

import java.util.Map;

public record ApiSchema(String type, String format, Map<String, ApiSchema> properties, ApiSchema items) {
    public ApiSchema {
        properties = Map.copyOf(properties);
    }
}
