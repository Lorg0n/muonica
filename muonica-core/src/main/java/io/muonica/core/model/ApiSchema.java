package io.muonica.core.model;

import java.util.Map;
import java.util.List;

public record ApiSchema(
        String type,
        String format,
        String ref,
        String description,
        Map<String, ApiSchema> properties,
        List<String> requiredProperties,
        ApiSchema items,
        List<String> enumValues,
        Integer minLength,
        Integer maxLength,
        String pattern,
        Long minimum,
        Long maximum
) {
    public ApiSchema {
        properties = Map.copyOf(properties);
        requiredProperties = List.copyOf(requiredProperties);
        enumValues = List.copyOf(enumValues);
    }

    public ApiSchema(String type, String format, Map<String, ApiSchema> properties, ApiSchema items) {
        this(type, format, null, null, properties, List.of(), items, List.of(), null, null, null, null, null);
    }
}
