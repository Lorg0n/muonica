package io.muonica.openapi;

import io.muonica.core.model.api.ApiEndpoint;
import io.muonica.core.model.api.ApiParameter;
import io.muonica.core.model.api.ApiProject;
import io.muonica.core.model.api.ApiResponse;
import io.muonica.core.model.api.ApiSchema;
import io.muonica.core.model.security.ApiSecurityScheme;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Converts the neutral Muonica model to an OpenAPI 3.1.1 JSON-compatible document. */
public final class OpenApiExporter {
    public Map<String, Object> export(ApiProject project) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("openapi", "3.1.1");
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", project.name());
        info.put("version", project.version());
        putIfNotBlank(info, "description", project.description());
        document.put("info", info);

        Map<String, Object> paths = new TreeMap<>();
        project.groups().forEach(group -> group.endpoints().forEach(endpoint -> {
            if ("ANY".equals(endpoint.method())) return;
            @SuppressWarnings("unchecked")
            Map<String, Object> pathItem = (Map<String, Object>) paths.computeIfAbsent(endpoint.path(), ignored -> new LinkedHashMap<>());
            pathItem.put(endpoint.method().toLowerCase(), operation(endpoint, group.name()));
        }));
        document.put("paths", paths);

        Map<String, Object> components = new LinkedHashMap<>();
        Map<String, Object> schemas = new TreeMap<>();
        project.schemas().forEach((name, schema) -> schemas.put(name, schema(schema)));
        if (!schemas.isEmpty()) components.put("schemas", schemas);
        Map<String, Object> securitySchemes = new TreeMap<>();
        project.securitySchemes().forEach(scheme -> securitySchemes.put(scheme.name(), securityScheme(scheme)));
        if (!securitySchemes.isEmpty()) components.put("securitySchemes", securitySchemes);
        if (!components.isEmpty()) document.put("components", components);
        return document;
    }

    private Map<String, Object> operation(ApiEndpoint endpoint, String group) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operationId", endpoint.controller() + "_" + endpoint.handler());
        operation.put("tags", List.of(group));
        putIfNotBlank(operation, "summary", endpoint.summary());
        putIfNotBlank(operation, "description", endpoint.description());
        if (!endpoint.parameters().isEmpty()) {
            operation.put("parameters", endpoint.parameters().stream().map(this::parameter).toList());
        }
        if (endpoint.request() != null) {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("required", endpoint.request().required());
            putIfNotBlank(request, "description", endpoint.request().description());
            request.put("content", content(endpoint.request().content()));
            operation.put("requestBody", request);
        }
        Map<String, Object> responses = new TreeMap<>();
        endpoint.responses().forEach(response -> responses.put(response.statusCode(), response(response)));
        operation.put("responses", responses);
        if (!endpoint.securityRequirements().isEmpty()) {
            operation.put("security", endpoint.securityRequirements().stream().map(name -> Map.of(name, List.of())).toList());
        }
        return operation;
    }

    private Map<String, Object> parameter(ApiParameter parameter) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", parameter.name());
        result.put("in", parameter.location().name().toLowerCase());
        result.put("required", parameter.required());
        putIfNotBlank(result, "description", parameter.description());
        result.put("schema", schema(parameter.schema()));
        return result;
    }

    private Map<String, Object> response(ApiResponse response) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("description", response.description() == null || response.description().isBlank() ? "Response" : response.description());
        if (!response.content().isEmpty()) result.put("content", content(response.content()));
        return result;
    }

    private Map<String, Object> content(Map<String, ApiSchema> schemas) {
        Map<String, Object> content = new TreeMap<>();
        schemas.forEach((contentType, schema) -> content.put(contentType, Map.of("schema", schema(schema))));
        return content;
    }

    private Map<String, Object> schema(ApiSchema schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (schema.ref() != null && !schema.ref().isBlank()) {
            result.put("$ref", "#/components/schemas/" + schema.ref());
            return result;
        }
        putIfNotBlank(result, "type", schema.type());
        putIfNotBlank(result, "format", schema.format());
        putIfNotBlank(result, "description", schema.description());
        if (!schema.properties().isEmpty()) result.put("properties", schema.properties().entrySet().stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getKey(), schema(item.getValue())), LinkedHashMap::putAll));
        if (!schema.requiredProperties().isEmpty()) result.put("required", schema.requiredProperties());
        if (schema.items() != null) result.put("items", schema(schema.items()));
        if (!schema.enumValues().isEmpty()) result.put("enum", schema.enumValues());
        if (schema.minLength() != null) result.put("minLength", schema.minLength());
        if (schema.maxLength() != null) result.put("maxLength", schema.maxLength());
        putIfNotBlank(result, "pattern", schema.pattern());
        if (schema.minimum() != null) result.put("minimum", schema.minimum());
        if (schema.maximum() != null) result.put("maximum", schema.maximum());
        return result;
    }

    private Map<String, Object> securityScheme(ApiSecurityScheme scheme) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (scheme.type() == ApiSecurityScheme.Type.HTTP) {
            result.put("type", "http");
            result.put("scheme", scheme.scheme());
            putIfNotBlank(result, "bearerFormat", scheme.bearerFormat());
        } else {
            result.put("type", "apiKey");
            result.put("name", scheme.parameterName());
            result.put("in", scheme.parameterLocation().name().toLowerCase());
        }
        return result;
    }

    private static void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }
}
