package io.muonica.spring.scan;

import io.muonica.core.annotation.api.MuonicaResponse;
import io.muonica.core.annotation.api.MuonicaResponseHeader;
import io.muonica.core.model.api.ApiHeader;
import io.muonica.core.model.api.ApiResponse;
import io.muonica.core.model.api.ApiSchema;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

/** Resolves inferred, declared, and global Spring MVC responses. */
final class HandlerResponseResolver {
    private final SchemaResolver schemas;
    private final List<ApiResponse> globalResponses;

    HandlerResponseResolver(ApplicationContext applicationContext, SchemaResolver schemas) {
        this.schemas = schemas;
        this.globalResponses = globalResponses(applicationContext);
    }

    List<ApiResponse> resolve(Method method, RequestMappingInfo mapping) {
        Map<String, ApiResponse> responses = new LinkedHashMap<>();
        String status = statusCode(method);
        ApiSchema returnSchema = schemas.resolve(method.getGenericReturnType());
        MuonicaResponse[] explicitResponses = method.getAnnotationsByType(MuonicaResponse.class);

        if (!isResponseEntity(method.getGenericReturnType()) || explicitResponses.length == 0) {
            responses.put(status, inferredResponse(status, returnSchema, mapping));
        }
        for (MuonicaResponse response : explicitResponses) {
            ApiResponse resolved = declaredResponse(response, mapping);
            responses.put(resolved.statusCode(), resolved);
        }
        globalResponses.forEach(response -> responses.putIfAbsent(response.statusCode(), response));
        return List.copyOf(responses.values());
    }

    private ApiResponse inferredResponse(String status, ApiSchema schema, RequestMappingInfo mapping) {
        if (schema.type() == null && schema.ref() == null) {
            return new ApiResponse(status, "Success", Map.of());
        }
        Map<String, ApiSchema> content = new LinkedHashMap<>();
        responseContentTypes(mapping, schema).forEach(contentType -> content.put(contentType, schema));
        return new ApiResponse(status, "Success", content);
    }

    private List<ApiResponse> globalResponses(ApplicationContext applicationContext) {
        Map<String, Object> adviceBeans = new LinkedHashMap<>();
        addAdviceBeans(applicationContext, adviceBeans, RestControllerAdvice.class);
        addAdviceBeans(applicationContext, adviceBeans, ControllerAdvice.class);

        Map<String, ApiResponse> responses = new LinkedHashMap<>();
        adviceBeans.values().stream()
                .map(ClassUtils::getUserClass)
                .flatMap(type -> Arrays.stream(type.getMethods()))
                .filter(method -> method.isAnnotationPresent(ExceptionHandler.class))
                .flatMap(method -> Arrays.stream(method.getAnnotationsByType(MuonicaResponse.class)))
                .map(response -> declaredResponse(response, null))
                .forEach(response -> responses.putIfAbsent(response.statusCode(), response));
        return List.copyOf(responses.values());
    }

    private static <A extends Annotation> void addAdviceBeans(ApplicationContext applicationContext,
            Map<String, Object> adviceBeans, Class<A> annotationType) {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(annotationType);
        if (beans != null) {
            adviceBeans.putAll(beans);
        }
    }

    private ApiResponse declaredResponse(MuonicaResponse response, RequestMappingInfo mapping) {
        String code = Integer.toString(response.status());
        ApiSchema body = response.body() == Void.class ? null : schemas.resolve(response.body());
        Map<String, ApiSchema> content = body == null
                ? Map.of()
                : Map.of(responseContentType(response, body, mapping), body);
        Map<String, ApiHeader> headers = new LinkedHashMap<>();
        for (MuonicaResponseHeader header : response.headers()) {
            headers.put(header.name(), new ApiHeader(blankToNull(header.description()), schemas.resolve(header.schema())));
        }
        return new ApiResponse(code, response.description(), content, headers);
    }

    private static String statusCode(Method method) {
        ResponseStatus status = method.getAnnotation(ResponseStatus.class);
        if (status == null) {
            return "200";
        }
        HttpStatus code = status.code() != HttpStatus.INTERNAL_SERVER_ERROR ? status.code() : status.value();
        return Integer.toString(code.value());
    }

    private static List<String> responseContentTypes(RequestMappingInfo mapping, ApiSchema schema) {
        List<String> types = mapping == null
                ? List.of()
                : mapping.getProducesCondition().getProducibleMediaTypes().stream()
                        .map(Object::toString)
                        .sorted()
                        .toList();
        if (!types.isEmpty()) {
            return types;
        }
        return "binary".equals(schema.format()) ? List.of("application/octet-stream") : List.of("application/json");
    }

    private static String responseContentType(MuonicaResponse response, ApiSchema schema, RequestMappingInfo mapping) {
        if (!response.contentType().isBlank()) {
            return response.contentType();
        }
        return responseContentTypes(mapping, schema).get(0);
    }

    private static boolean isResponseEntity(Type type) {
        if (type instanceof java.lang.reflect.ParameterizedType parameterized) {
            return parameterized.getRawType() == org.springframework.http.ResponseEntity.class;
        }
        return type == org.springframework.http.ResponseEntity.class;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
