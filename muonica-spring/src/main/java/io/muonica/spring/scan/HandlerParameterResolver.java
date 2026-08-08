package io.muonica.spring.scan;

import io.muonica.core.model.api.ApiParameter;
import io.muonica.core.model.api.ApiRequest;
import io.muonica.core.model.api.ApiSchema;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

/** Resolves Spring MVC handler parameters to Muonica parameters and request bodies. */
final class HandlerParameterResolver {
    private final SchemaResolver schemas;

    HandlerParameterResolver(SchemaResolver schemas) {
        this.schemas = schemas;
    }

    Resolution resolve(Method method, RequestMappingInfo mapping) {
        List<ApiParameter> parameters = new ArrayList<>();
        ApiRequest request = null;
        for (Parameter parameter : method.getParameters()) {
            if (PageableParameterResolver.supports(parameter)) {
                parameters.addAll(PageableParameterResolver.resolve(parameter));
                continue;
            }
            if (isAuthenticationPrincipal(parameter)) {
                continue;
            }

            ApiParameter resolvedParameter = parameter(parameter);
            if (resolvedParameter != null) {
                parameters.add(resolvedParameter);
            }
            if (parameter.isAnnotationPresent(RequestBody.class)) {
                request = requestBody(parameter, mapping);
            }
            if (parameter.isAnnotationPresent(RequestPart.class)) {
                request = multipartRequest(parameter, request);
            }
        }
        return new Resolution(parameters, request);
    }

    private ApiParameter parameter(Parameter parameter) {
        PathVariable path = parameter.getAnnotation(PathVariable.class);
        if (path != null) {
            return parameter(name(path.name(), path.value(), parameter), ApiParameter.ParameterLocation.PATH, true, parameter);
        }
        RequestParam query = parameter.getAnnotation(RequestParam.class);
        if (query != null) {
            return parameter(name(query.name(), query.value(), parameter), ApiParameter.ParameterLocation.QUERY, query.required(), parameter);
        }
        RequestHeader header = parameter.getAnnotation(RequestHeader.class);
        if (header != null) {
            return parameter(name(header.name(), header.value(), parameter), ApiParameter.ParameterLocation.HEADER, header.required(), parameter);
        }
        CookieValue cookie = parameter.getAnnotation(CookieValue.class);
        if (cookie != null) {
            return parameter(name(cookie.name(), cookie.value(), parameter), ApiParameter.ParameterLocation.COOKIE, cookie.required(), parameter);
        }
        return null;
    }

    private ApiParameter parameter(String name, ApiParameter.ParameterLocation location, boolean required, Parameter source) {
        ApiSchema schema = schemas.resolve(source.getParameterizedType(), source);
        return new ApiParameter(name, location, required || location == ApiParameter.ParameterLocation.PATH, schema.description(), schema);
    }

    private ApiRequest requestBody(Parameter parameter, RequestMappingInfo mapping) {
        RequestBody annotation = parameter.getAnnotation(RequestBody.class);
        Map<String, ApiSchema> content = new LinkedHashMap<>();
        requestContentTypes(mapping).forEach(type -> content.put(type, schemas.resolve(parameter.getParameterizedType(), parameter)));
        return new ApiRequest(annotation.required(), null, content);
    }

    private ApiRequest multipartRequest(Parameter parameter, ApiRequest current) {
        RequestPart annotation = parameter.getAnnotation(RequestPart.class);
        String name = name(annotation.name(), annotation.value(), parameter);
        ApiSchema currentSchema = current == null ? null : current.content().get("multipart/form-data");
        Map<String, ApiSchema> properties = currentSchema == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(currentSchema.properties());
        List<String> requiredProperties = currentSchema == null
                ? new ArrayList<>()
                : new ArrayList<>(currentSchema.requiredProperties());

        properties.put(name, schemas.resolve(parameter.getParameterizedType(), parameter));
        if (annotation.required() && !requiredProperties.contains(name)) {
            requiredProperties.add(name);
        }

        boolean required = annotation.required() || current != null && current.required();
        ApiSchema schema = ApiSchema.object(properties, requiredProperties);
        return new ApiRequest(required, null, Map.of("multipart/form-data", schema));
    }

    private static List<String> requestContentTypes(RequestMappingInfo mapping) {
        List<String> types = mapping.getConsumesCondition().getConsumableMediaTypes().stream()
                .map(Object::toString)
                .sorted()
                .toList();
        return types.isEmpty() ? List.of("application/json") : types;
    }

    private static String name(String name, String value, Parameter parameter) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        if (value != null && !value.isBlank()) {
            return value;
        }
        return parameter.getName();
    }

    private static boolean isAuthenticationPrincipal(Parameter parameter) {
        String name = parameter.getType().getName();
        if (name.equals("java.security.Principal")
                || name.equals("org.springframework.security.core.Authentication")
                || name.equals("org.springframework.security.core.context.SecurityContext")) {
            return true;
        }
        return java.util.stream.Stream.of(parameter.getAnnotations())
                .map(annotation -> annotation.annotationType().getName())
                .anyMatch(annotation -> annotation.equals("org.springframework.security.core.annotation.AuthenticationPrincipal")
                        || annotation.equals("org.springframework.security.core.annotation.CurrentSecurityContext"));
    }

    record Resolution(List<ApiParameter> parameters, ApiRequest request) {
        Resolution {
            parameters = List.copyOf(parameters);
        }
    }
}
