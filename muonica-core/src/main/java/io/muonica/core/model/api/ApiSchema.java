package io.muonica.core.model.api;

import java.util.Map;
import java.util.List;

/**
 * A JSON-schema-like description used by Muonica's neutral API model.
 *
 * <p>Use the factory methods for common schema shapes instead of depending on
 * the record's full constructor. This keeps schema construction readable as
 * new attributes are added.</p>
 */
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

    /** Creates a schema without a type, for example a response with no body. */
    public static ApiSchema empty() {
        return new ApiSchema(null, null, Map.of(), null);
    }

    /** Creates a scalar schema. */
    public static ApiSchema scalar(String type, String format) {
        return new ApiSchema(type, format, Map.of(), null);
    }

    /** Creates an array schema with the supplied item schema. */
    public static ApiSchema array(ApiSchema items) {
        return new ApiSchema("array", null, Map.of(), items);
    }

    /** Creates an object schema with no declared properties. */
    public static ApiSchema object() {
        return object(Map.of(), List.of());
    }

    /** Creates an object schema with its declared properties and required property names. */
    public static ApiSchema object(Map<String, ApiSchema> properties, List<String> requiredProperties) {
        return new ApiSchema("object", null, null, null, properties, requiredProperties, null, List.of(),
                null, null, null, null, null);
    }

    /** Creates a reference to a named project schema component. */
    public static ApiSchema reference(String name) {
        return new ApiSchema(null, null, name, null, Map.of(), List.of(), null, List.of(),
                null, null, null, null, null);
    }

    /** Creates a scalar schema constrained to the given enum values. */
    public static ApiSchema enumeration(String type, List<String> values) {
        return new ApiSchema(type, null, null, null, Map.of(), List.of(), null, values,
                null, null, null, null, null);
    }

    /** Returns this schema with validation constraints from its source field or parameter. */
    public ApiSchema withValidationConstraints(Integer minLength, Integer maxLength, String pattern, Long minimum, Long maximum) {
        return new ApiSchema(type, format, ref, description, properties, requiredProperties, items, enumValues,
                minLength, maxLength, pattern, minimum, maximum);
    }
}
