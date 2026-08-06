package io.muonica.spring.scan;

import io.muonica.core.annotation.MuonicaDocumentation;
import io.muonica.core.annotation.MuonicaGroup;
import io.muonica.core.annotation.MuonicaOperation;
import io.muonica.core.annotation.MuonicaProject;
import io.muonica.core.annotation.MuonicaResponse;
import io.muonica.core.annotation.MuonicaSecurityRequirement;
import io.muonica.core.annotation.MuonicaSecurityScheme;
import io.muonica.core.model.ApiEndpoint;
import io.muonica.core.model.ApiGroup;
import io.muonica.core.model.ApiParameter;
import io.muonica.core.model.ApiProject;
import io.muonica.core.model.ApiRequest;
import io.muonica.core.model.ApiResponse;
import io.muonica.core.model.ApiSchema;
import io.muonica.core.model.ApiSecurityScheme;
import io.muonica.core.model.DocumentationBlock;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Adapts Spring MVC handler mappings to the neutral Muonica model. */
public final class MuonicaEndpointScanner {
    private static final String MUONICA_PACKAGE = "io.muonica.spring.web";
    private final RequestMappingHandlerMapping handlerMapping;
    private final ApplicationContext applicationContext;
    private final Environment environment;

    public MuonicaEndpointScanner(RequestMappingHandlerMapping handlerMapping, ApplicationContext applicationContext, Environment environment) {
        this.handlerMapping = handlerMapping;
        this.applicationContext = applicationContext;
        this.environment = environment;
    }

    public ApiProject scan() {
        SchemaResolver schemas = new SchemaResolver();
        Map<Class<?>, List<ApiEndpoint>> endpoints = new LinkedHashMap<>();
        handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> !entry.getValue().getBeanType().getPackageName().startsWith(MUONICA_PACKAGE))
                .sorted(Comparator.comparing(entry -> entry.getValue().getBeanType().getName()))
                .forEach(entry -> endpoints.computeIfAbsent(entry.getValue().getBeanType(), ignored -> new ArrayList<>())
                        .addAll(toEndpoints(entry, schemas).toList()));
        List<ApiGroup> groups = endpoints.entrySet().stream().map(entry -> group(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ApiGroup::name)).toList();
        String projectBean = applicationContext.getBeanNamesForAnnotation(MuonicaProject.class).length == 0 ? null
                : applicationContext.getBeanNamesForAnnotation(MuonicaProject.class)[0];
        Class<?> projectClass = projectBean == null ? null : ClassUtils.getUserClass(applicationContext.getType(projectBean));
        MuonicaProject metadata = projectBean == null ? null : applicationContext.findAnnotationOnBean(projectBean, MuonicaProject.class);
        String defaultName = environment.getProperty("spring.application.name", "application");
        String name = metadata != null && !metadata.title().isBlank() ? metadata.title() : defaultName;
        String version = metadata != null && !metadata.version().isBlank() ? metadata.version() : "0.0.0";
        String description = metadata != null && !metadata.description().isBlank() ? metadata.description() : null;
        return new ApiProject(name, version, description, groups, documentation(projectClass), securitySchemes(projectClass), schemas.components());
    }

    private ApiGroup group(Class<?> controller, List<ApiEndpoint> endpoints) {
        MuonicaGroup annotation = controller.getAnnotation(MuonicaGroup.class);
        String name = annotation != null && !annotation.name().isBlank() ? annotation.name() : controller.getSimpleName();
        String description = annotation != null && !annotation.description().isBlank() ? annotation.description() : null;
        return new ApiGroup(name, description, endpoints.stream().sorted(Comparator.comparing(ApiEndpoint::path).thenComparing(ApiEndpoint::method)).toList(), documentation(controller));
    }

    private Stream<ApiEndpoint> toEndpoints(Map.Entry<RequestMappingInfo, HandlerMethod> entry, SchemaResolver schemas) {
        RequestMappingInfo mapping = entry.getKey();
        HandlerMethod handler = entry.getValue();
        List<String> paths = mapping.getPatternValues().stream().sorted().toList();
        List<String> detectedMethods = mapping.getMethodsCondition().getMethods().stream().map(Enum::name).sorted().toList();
        List<String> methods = detectedMethods.isEmpty() ? List.of("ANY") : detectedMethods;
        return paths.stream().flatMap(path -> methods.stream().map(method -> endpoint(method, path, handler, mapping, schemas)));
    }

    private ApiEndpoint endpoint(String method, String path, HandlerMethod handler, RequestMappingInfo mapping, SchemaResolver schemas) {
        Method javaMethod = handler.getMethod();
        MuonicaOperation operation = javaMethod.getAnnotation(MuonicaOperation.class);
        List<ApiParameter> parameters = new ArrayList<>();
        ApiRequest request = null;
        for (Parameter parameter : javaMethod.getParameters()) {
            ApiParameter apiParameter = parameter(parameter, schemas);
            if (apiParameter != null) parameters.add(apiParameter);
            if (parameter.isAnnotationPresent(RequestBody.class)) request = requestBody(parameter, mapping, schemas);
            if (parameter.isAnnotationPresent(RequestPart.class)) request = multipartRequest(parameter, request, schemas);
        }
        Map<String, ApiResponse> responses = new LinkedHashMap<>();
        String status = statusCode(javaMethod);
        ApiSchema returnSchema = schemas.resolve(javaMethod.getGenericReturnType());
        if (returnSchema.type() != null || returnSchema.ref() != null) {
            Map<String, ApiSchema> content = new LinkedHashMap<>();
            responseContentTypes(mapping).forEach(contentType -> content.put(contentType, returnSchema));
            responses.put(status, new ApiResponse(status, "Success", content));
        } else responses.put(status, new ApiResponse(status, "Success", Map.of()));
        for (MuonicaResponse response : javaMethod.getAnnotationsByType(MuonicaResponse.class)) {
            String code = Integer.toString(response.status());
            responses.put(code, new ApiResponse(code, response.description(), response.body() == Void.class ? Map.of() : Map.of(response.contentType(), schemas.resolve(response.body()))));
        }
        return new ApiEndpoint(method, path, handler.getBeanType().getSimpleName(), javaMethod.getName(),
                blankToNull(operation == null ? null : operation.summary()), blankToNull(operation == null ? null : operation.description()),
                parameters, request, List.copyOf(responses.values()), documentation(javaMethod),
                Stream.of(javaMethod.getAnnotationsByType(MuonicaSecurityRequirement.class)).map(MuonicaSecurityRequirement::value).toList());
    }

    private ApiParameter parameter(Parameter parameter, SchemaResolver schemas) {
        PathVariable path = parameter.getAnnotation(PathVariable.class);
        if (path != null) return parameter(name(path.name(), path.value(), parameter), ApiParameter.ParameterLocation.PATH, true, parameter, schemas);
        RequestParam query = parameter.getAnnotation(RequestParam.class);
        if (query != null) return parameter(name(query.name(), query.value(), parameter), ApiParameter.ParameterLocation.QUERY, query.required(), parameter, schemas);
        RequestHeader header = parameter.getAnnotation(RequestHeader.class);
        if (header != null) return parameter(name(header.name(), header.value(), parameter), ApiParameter.ParameterLocation.HEADER, header.required(), parameter, schemas);
        CookieValue cookie = parameter.getAnnotation(CookieValue.class);
        if (cookie != null) return parameter(name(cookie.name(), cookie.value(), parameter), ApiParameter.ParameterLocation.COOKIE, cookie.required(), parameter, schemas);
        return null;
    }

    private ApiParameter parameter(String name, ApiParameter.ParameterLocation location, boolean required, Parameter source, SchemaResolver schemas) {
        return new ApiParameter(name, location, required || location == ApiParameter.ParameterLocation.PATH, null, schemas.resolve(source.getParameterizedType(), source));
    }

    private ApiRequest requestBody(Parameter parameter, RequestMappingInfo mapping, SchemaResolver schemas) {
        RequestBody annotation = parameter.getAnnotation(RequestBody.class);
        Map<String, ApiSchema> content = new LinkedHashMap<>();
        requestContentTypes(mapping).forEach(type -> content.put(type, schemas.resolve(parameter.getParameterizedType(), parameter)));
        return new ApiRequest(annotation.required(), null, content);
    }

    private ApiRequest multipartRequest(Parameter parameter, ApiRequest current, SchemaResolver schemas) {
        RequestPart annotation = parameter.getAnnotation(RequestPart.class);
        String name = name(annotation.name(), annotation.value(), parameter);
        Map<String, ApiSchema> properties = current == null || current.content().isEmpty() ? new LinkedHashMap<>()
                : new LinkedHashMap<>(current.content().getOrDefault("multipart/form-data", new ApiSchema("object", null, Map.of(), null)).properties());
        properties.put(name, schemas.resolve(parameter.getParameterizedType(), parameter));
        ApiSchema schema = new ApiSchema("object", null, null, null, properties, List.of(name), null, List.of(), null, null, null, null, null);
        return new ApiRequest(annotation.required(), null, Map.of("multipart/form-data", schema));
    }

    private static String statusCode(Method method) {
        ResponseStatus status = method.getAnnotation(ResponseStatus.class);
        if (status == null) return "200";
        HttpStatus code = status.code() != HttpStatus.INTERNAL_SERVER_ERROR ? status.code() : status.value();
        return Integer.toString(code.value());
    }

    private static List<String> requestContentTypes(RequestMappingInfo mapping) {
        List<String> types = mapping.getConsumesCondition().getConsumableMediaTypes().stream().map(Object::toString).sorted().toList();
        return types.isEmpty() ? List.of("application/json") : types;
    }

    private static List<String> responseContentTypes(RequestMappingInfo mapping) {
        List<String> types = mapping.getProducesCondition().getProducibleMediaTypes().stream().map(Object::toString).sorted().toList();
        return types.isEmpty() ? List.of("application/json") : types;
    }

    private static String name(String name, String value, Parameter parameter) {
        if (name != null && !name.isBlank()) return name;
        if (value != null && !value.isBlank()) return value;
        return parameter.getName();
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }

    private static List<DocumentationBlock> documentation(java.lang.reflect.AnnotatedElement source) {
        if (source == null) return List.of();
        return Stream.of(source.getAnnotationsByType(MuonicaDocumentation.class)).map(annotation -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            if (!annotation.title().isBlank()) attributes.put("title", annotation.title());
            if (!annotation.language().isBlank()) attributes.put("language", annotation.language());
            if (annotation.type() == MuonicaDocumentation.Type.NOTICE) attributes.put("level", annotation.noticeLevel().name().toLowerCase());
            return new DocumentationBlock(annotation.type().name().toLowerCase(), annotation.content(), attributes);
        }).toList();
    }

    private static List<ApiSecurityScheme> securitySchemes(Class<?> source) {
        if (source == null) return List.of();
        return Stream.of(source.getAnnotationsByType(MuonicaSecurityScheme.class)).map(annotation -> new ApiSecurityScheme(
                annotation.name(), ApiSecurityScheme.Type.valueOf(annotation.type().name()), annotation.scheme(), annotation.bearerFormat(),
                annotation.parameterName(), annotation.parameterLocation())).toList();
    }
}
