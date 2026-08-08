package io.muonica.spring.scan;

import io.muonica.core.annotation.api.MuonicaAllowedSort;
import io.muonica.core.annotation.api.MuonicaBadge;
import io.muonica.core.annotation.api.MuonicaGroup;
import io.muonica.core.annotation.api.MuonicaHidden;
import io.muonica.core.annotation.api.MuonicaOperation;
import io.muonica.core.annotation.api.MuonicaProject;
import io.muonica.core.annotation.api.MuonicaResponse;
import io.muonica.core.annotation.api.MuonicaResponseHeader;
import io.muonica.core.annotation.api.MuonicaServer;
import io.muonica.core.annotation.documentation.MuonicaDocumentation;
import io.muonica.core.annotation.security.MuonicaBearerAuth;
import io.muonica.core.annotation.security.MuonicaSecurityRequirement;
import io.muonica.core.annotation.security.MuonicaSecurityScheme;
import io.muonica.core.model.api.ApiEndpoint;
import io.muonica.core.model.api.ApiGroup;
import io.muonica.core.model.api.ApiHeader;
import io.muonica.core.model.api.ApiParameter;
import io.muonica.core.model.api.ApiProject;
import io.muonica.core.model.api.ApiRequest;
import io.muonica.core.model.api.ApiResponse;
import io.muonica.core.model.api.ApiSchema;
import io.muonica.core.model.api.ApiServer;
import io.muonica.core.model.security.ApiSecurityScheme;
import io.muonica.spring.documentation.DocumentationComposer;
import io.muonica.spring.documentation.DocumentationFileLoader;
import io.muonica.spring.documentation.DocumentationResolution;
import io.muonica.spring.documentation.DocumentationResolver;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.core.env.Environment;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Adapts Spring MVC handler mappings to the neutral Muonica model. */
public final class MuonicaEndpointScanner implements SmartInitializingSingleton {
    private static final String MUONICA_PACKAGE = "io.muonica.spring.web";
    private final RequestMappingHandlerMapping handlerMapping;
    private final ApplicationContext applicationContext;
    private final Environment environment;
    private final Function<AnnotatedElement, DocumentationResolution> documentationResolver;
    private final DocumentationComposer documentationComposer;
    private volatile ApiProject cachedProject;

    public MuonicaEndpointScanner(RequestMappingHandlerMapping handlerMapping, ApplicationContext applicationContext, Environment environment) {
        this(handlerMapping, applicationContext, environment,
                new DocumentationResolver(new DocumentationFileLoader(applicationContext), new io.muonica.spring.documentation.DocumentationParser(), environment)::resolve);
    }

    MuonicaEndpointScanner(RequestMappingHandlerMapping handlerMapping, ApplicationContext applicationContext, Environment environment,
            Function<AnnotatedElement, DocumentationResolution> documentationResolver) {
        this.handlerMapping = handlerMapping;
        this.applicationContext = applicationContext;
        this.environment = environment;
        this.documentationResolver = documentationResolver;
        this.documentationComposer = new DocumentationComposer(environment.getProperty("muonica.documentation.strict", Boolean.class, true));
    }

    public ApiProject scan() {
        ApiProject result = cachedProject;
        if (result != null) return result;
        synchronized (this) {
            if (cachedProject == null) cachedProject = buildProject();
            return cachedProject;
        }
    }

    @Override
    public void afterSingletonsInstantiated() {
        scan();
    }

    private ApiProject buildProject() {
        SchemaResolver schemas = new SchemaResolver();
        List<ApiResponse> globalResponses = globalResponses(schemas);
        DocumentationResolution projectDocumentation = projectDocumentation();
        Map<Class<?>, List<ApiEndpoint>> endpoints = new LinkedHashMap<>();
        Map<Class<?>, DocumentationResolution> groupDocumentation = new LinkedHashMap<>();
        handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> shouldDocument(entry.getValue()))
                .sorted(Comparator.comparing(entry -> entry.getValue().getBeanType().getName()))
                .forEach(entry -> {
                    Class<?> controller = entry.getValue().getBeanType();
                    DocumentationResolution documentation = groupDocumentation.computeIfAbsent(controller, documentationResolver);
                    endpoints.computeIfAbsent(controller, ignored -> new ArrayList<>())
                            .addAll(toEndpoints(entry, schemas, globalResponses, projectDocumentation, documentation).toList());
                });
        List<ApiGroup> groups = endpoints.entrySet().stream().map(entry -> group(entry.getKey(), entry.getValue(), groupDocumentation.get(entry.getKey())))
                .sorted(Comparator.comparing(ApiGroup::name)).toList();
        MuonicaProject metadata = projectMetadata();
        String defaultName = environment.getProperty("spring.application.name", "application");
        String name = metadata != null && !metadata.title().isBlank() ? metadata.title() : defaultName;
        String version = metadata != null && !metadata.version().isBlank() ? metadata.version() : "0.0.0";
        String description = metadata != null && !metadata.description().isBlank() ? metadata.description() : null;
        return new ApiProject(name, version, description, groups, projectDocumentation.blocks(), projectDocumentation.warnings(),
                securitySchemes(projectClass()), servers(projectClass()), schemas.components());
    }

    private static boolean shouldDocument(HandlerMethod handler) {
        Class<?> controller = handler.getBeanType();
        return !controller.getPackageName().startsWith(MUONICA_PACKAGE)
                && !ErrorController.class.isAssignableFrom(controller)
                && !controller.isAnnotationPresent(MuonicaHidden.class)
                && !handler.getMethod().isAnnotationPresent(MuonicaHidden.class);
    }

    private ApiGroup group(Class<?> controller, List<ApiEndpoint> endpoints, DocumentationResolution documentation) {
        MuonicaGroup annotation = controller.getAnnotation(MuonicaGroup.class);
        String name = annotation != null && !annotation.name().isBlank() ? annotation.name() : controller.getSimpleName();
        String description = annotation != null && !annotation.description().isBlank() ? annotation.description() : null;
        return new ApiGroup(name, description, endpoints.stream().sorted(Comparator.comparing(ApiEndpoint::path).thenComparing(ApiEndpoint::method)).toList(),
                documentation.blocks(), documentation.warnings());
    }

    private java.util.stream.Stream<ApiEndpoint> toEndpoints(Map.Entry<RequestMappingInfo, HandlerMethod> entry, SchemaResolver schemas,
            List<ApiResponse> globalResponses,
            DocumentationResolution projectDocumentation, DocumentationResolution groupDocumentation) {
        RequestMappingInfo mapping = entry.getKey();
        HandlerMethod handler = entry.getValue();
        List<String> paths = mapping.getPatternValues().stream().sorted().toList();
        List<String> detectedMethods = mapping.getMethodsCondition().getMethods().stream().map(Enum::name).sorted().toList();
        List<String> methods = detectedMethods.isEmpty() ? List.of("ANY") : detectedMethods;
        return paths.stream().flatMap(path -> methods.stream().map(method -> endpoint(method, path, handler, mapping, schemas,
                globalResponses, projectDocumentation, groupDocumentation)));
    }

    private ApiEndpoint endpoint(String method, String path, HandlerMethod handler, RequestMappingInfo mapping, SchemaResolver schemas,
            List<ApiResponse> globalResponses,
            DocumentationResolution projectDocumentation, DocumentationResolution groupDocumentation) {
        Method javaMethod = handler.getMethod();
        MuonicaOperation operation = javaMethod.getAnnotation(MuonicaOperation.class);
        List<ApiParameter> parameters = new ArrayList<>();
        ApiRequest request = null;
        for (Parameter parameter : javaMethod.getParameters()) {
            if (isPageable(parameter)) {
                parameters.addAll(pageableParameters(parameter));
                continue;
            }
            if (isAuthenticationPrincipal(parameter)) continue;
            ApiParameter apiParameter = parameter(parameter, schemas);
            if (apiParameter != null) parameters.add(apiParameter);
            if (parameter.isAnnotationPresent(RequestBody.class)) request = requestBody(parameter, mapping, schemas);
            if (parameter.isAnnotationPresent(RequestPart.class)) request = multipartRequest(parameter, request, schemas);
        }
        Map<String, ApiResponse> responses = new LinkedHashMap<>();
        String status = statusCode(javaMethod);
        ApiSchema returnSchema = schemas.resolve(javaMethod.getGenericReturnType());
        MuonicaResponse[] explicitResponses = javaMethod.getAnnotationsByType(MuonicaResponse.class);
        if (!isResponseEntity(javaMethod.getGenericReturnType()) || explicitResponses.length == 0) {
            if (returnSchema.type() != null || returnSchema.ref() != null) {
                Map<String, ApiSchema> content = new LinkedHashMap<>();
                responseContentTypes(mapping, returnSchema).forEach(contentType -> content.put(contentType, returnSchema));
                responses.put(status, new ApiResponse(status, "Success", content));
            } else responses.put(status, new ApiResponse(status, "Success", Map.of()));
        }
        for (MuonicaResponse response : explicitResponses) {
            ApiResponse apiResponse = apiResponse(response, schemas, mapping);
            responses.put(apiResponse.statusCode(), apiResponse);
        }
        globalResponses.forEach(response -> responses.putIfAbsent(response.statusCode(), response));
        DocumentationResolution endpointDocumentation = documentationResolver.apply(javaMethod);
        boolean inheritsDocumentation = java.util.stream.Stream.of(javaMethod.getAnnotationsByType(MuonicaDocumentation.class))
                .allMatch(MuonicaDocumentation::inherit);
        DocumentationResolution composedDocumentation = documentationComposer.compose(
                inheritsDocumentation ? projectDocumentation : DocumentationResolution.empty(),
                inheritsDocumentation ? groupDocumentation : DocumentationResolution.empty(), endpointDocumentation);
        return new ApiEndpoint(method, path, handler.getBeanType().getSimpleName(), javaMethod.getName(),
                blankToNull(operation == null ? null : operation.summary()), blankToNull(operation == null ? null : operation.description()),
                parameters, request, List.copyOf(responses.values()), composedDocumentation.blocks(),
                securityRequirements(handler.getBeanType(), javaMethod),
                java.util.stream.Stream.of(javaMethod.getAnnotationsByType(MuonicaBadge.class)).map(MuonicaBadge::value)
                        .filter(value -> !value.isBlank()).distinct().toList(),
                composedDocumentation.warnings());
    }

    private List<ApiResponse> globalResponses(SchemaResolver schemas) {
        Map<String, Object> adviceBeans = new LinkedHashMap<>();
        addAdviceBeans(adviceBeans, RestControllerAdvice.class);
        addAdviceBeans(adviceBeans, ControllerAdvice.class);

        Map<String, ApiResponse> responses = new LinkedHashMap<>();
        adviceBeans.values().stream()
                .map(bean -> ClassUtils.getUserClass(bean))
                .flatMap(type -> Arrays.stream(type.getMethods()))
                .filter(method -> method.isAnnotationPresent(ExceptionHandler.class))
                .flatMap(method -> Arrays.stream(method.getAnnotationsByType(MuonicaResponse.class)))
                .map(response -> apiResponse(response, schemas, null))
                .forEach(response -> responses.putIfAbsent(response.statusCode(), response));
        return List.copyOf(responses.values());
    }

    private <A extends Annotation> void addAdviceBeans(Map<String, Object> adviceBeans, Class<A> annotationType) {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(annotationType);
        if (beans != null) adviceBeans.putAll(beans);
    }

    private static ApiResponse apiResponse(MuonicaResponse response, SchemaResolver schemas, RequestMappingInfo mapping) {
        String code = Integer.toString(response.status());
        ApiSchema body = response.body() == Void.class ? null : schemas.resolve(response.body());
        Map<String, ApiSchema> content = body == null ? Map.of() : Map.of(responseContentType(response, body, mapping), body);
        Map<String, ApiHeader> headers = new LinkedHashMap<>();
        for (MuonicaResponseHeader header : response.headers()) {
            headers.put(header.name(), new ApiHeader(blankToNull(header.description()), schemas.resolve(header.schema())));
        }
        return new ApiResponse(code, response.description(), content, headers);
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
        ApiSchema schema = schemas.resolve(source.getParameterizedType(), source);
        return new ApiParameter(name, location, required || location == ApiParameter.ParameterLocation.PATH, schema.description(), schema);
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
                : new LinkedHashMap<>(current.content().getOrDefault("multipart/form-data", ApiSchema.object()).properties());
        properties.put(name, schemas.resolve(parameter.getParameterizedType(), parameter));
        ApiSchema schema = ApiSchema.object(properties, List.of(name));
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

    private static List<String> responseContentTypes(RequestMappingInfo mapping, ApiSchema schema) {
        List<String> types = mapping == null ? List.of() : mapping.getProducesCondition().getProducibleMediaTypes().stream()
                .map(Object::toString).sorted().toList();
        if (!types.isEmpty()) return types;
        return isBinary(schema) ? List.of("application/octet-stream") : List.of("application/json");
    }

    private static String responseContentType(MuonicaResponse response, ApiSchema schema, RequestMappingInfo mapping) {
        if (!response.contentType().isBlank()) return response.contentType();
        return responseContentTypes(mapping, schema).get(0);
    }

    private static String name(String name, String value, Parameter parameter) {
        if (name != null && !name.isBlank()) return name;
        if (value != null && !value.isBlank()) return value;
        return parameter.getName();
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }

    private static List<ApiSecurityScheme> securitySchemes(Class<?> source) {
        if (source == null) return List.of();
        java.util.stream.Stream<ApiSecurityScheme> configured = java.util.stream.Stream.of(source.getAnnotationsByType(MuonicaSecurityScheme.class))
                .map(annotation -> new ApiSecurityScheme(
                        annotation.name(), ApiSecurityScheme.Type.valueOf(annotation.type().name()), annotation.scheme(), annotation.bearerFormat(),
                        parameterName(annotation), annotation.parameterLocation()));
        java.util.stream.Stream<ApiSecurityScheme> bearer = java.util.stream.Stream.of(source.getAnnotationsByType(MuonicaBearerAuth.class))
                .map(annotation -> new ApiSecurityScheme(
                        annotation.name(), ApiSecurityScheme.Type.HTTP, "bearer", annotation.bearerFormat(), annotation.parameterName(),
                        ApiParameter.ParameterLocation.HEADER));
        return java.util.stream.Stream.concat(configured, bearer).distinct().toList();
    }

    private static List<ApiServer> servers(Class<?> source) {
        if (source == null) return List.of();
        return java.util.stream.Stream.of(source.getAnnotationsByType(MuonicaServer.class))
                .filter(annotation -> !annotation.url().isBlank())
                .map(annotation -> new ApiServer(annotation.url(), blankToNull(annotation.description()))).toList();
    }

    private static List<List<String>> securityRequirements(Class<?> controller, Method method) {
        List<List<String>> controllerGroups = securityGroups(controller);
        List<List<String>> methodGroups = securityGroups(method);
        if (controllerGroups.isEmpty()) return methodGroups;
        if (methodGroups.isEmpty()) return controllerGroups;
        List<List<String>> combined = new ArrayList<>();
        for (List<String> controllerGroup : controllerGroups) {
            for (List<String> methodGroup : methodGroups) {
                List<String> group = new ArrayList<>(controllerGroup);
                methodGroup.stream().filter(name -> !group.contains(name)).forEach(group::add);
                combined.add(List.copyOf(group));
            }
        }
        return List.copyOf(combined);
    }

    private static List<List<String>> securityGroups(AnnotatedElement source) {
        return java.util.stream.Stream.of(source.getAnnotationsByType(MuonicaSecurityRequirement.class))
                .map(annotation -> java.util.stream.Stream.of(annotation.value()).filter(value -> !value.isBlank()).distinct().toList())
                .filter(group -> !group.isEmpty()).toList();
    }

    private static boolean isPageable(Parameter parameter) {
        return parameter.getType().getName().equals("org.springframework.data.domain.Pageable")
                && isPresent("org.springframework.data.domain.Pageable", parameter.getDeclaringExecutable().getDeclaringClass().getClassLoader());
    }

    private static List<ApiParameter> pageableParameters(Parameter parameter) {
        Annotation pageableDefault = annotation(parameter, "org.springframework.data.web.PageableDefault");
        int page = intAttribute(pageableDefault, "page", 0);
        int size = intAttributeAlias(pageableDefault, "size", "value", 20);
        List<String> sorts = sortDefaults(parameter, pageableDefault);
        ApiSchema pageSchema = ApiSchema.scalar("integer", "int32").withMetadata(null, null, Integer.toString(page));
        ApiSchema sizeSchema = ApiSchema.scalar("integer", "int32").withMetadata(null, null, Integer.toString(size));
        ApiSchema sortSchema = ApiSchema.scalar("string", null).withMetadata(null,
                sorts.isEmpty() ? null : String.join(",", sorts), sorts.isEmpty() ? null : String.join(",", sorts));
        MuonicaAllowedSort allowedSort = parameter.getAnnotation(MuonicaAllowedSort.class);
        if (allowedSort != null && allowedSort.value().length > 0) {
            List<String> values = java.util.stream.Stream.of(allowedSort.value()).filter(value -> !value.isBlank())
                    .flatMap(value -> java.util.stream.Stream.of(value + ",ASC", value + ",DESC")).toList();
            sortSchema = ApiSchema.enumeration("string", values).withMetadata(null, sortSchema.example(), sortSchema.defaultValue());
        }
        return List.of(
                new ApiParameter("page", ApiParameter.ParameterLocation.QUERY, false, "Zero-based page index.", pageSchema),
                new ApiParameter("size", ApiParameter.ParameterLocation.QUERY, false, "Maximum number of results per page.", sizeSchema),
                new ApiParameter("sort", ApiParameter.ParameterLocation.QUERY, false, "Sort property and direction, for example name,ASC.", sortSchema));
    }

    private static List<String> sortDefaults(Parameter parameter, Annotation pageableDefault) {
        List<String> result = new ArrayList<>();
        String direction = enumAttribute(pageableDefault, "direction", "ASC");
        for (String property : stringArrayAttribute(pageableDefault, "sort")) result.add(property + "," + direction);
        for (Annotation candidate : sortDefaultAnnotations(parameter)) {
            String sortDirection = enumAttribute(candidate, "direction", "ASC");
            List<String> properties = stringArrayAttribute(candidate, "sort");
            if (properties.isEmpty()) properties = stringArrayAttribute(candidate, "value");
            for (String property : properties) result.add(property + "," + sortDirection);
        }
        return result.stream().distinct().toList();
    }

    private static Annotation annotation(AnnotatedElement source, String typeName) {
        for (Annotation candidate : source.getAnnotations()) if (candidate.annotationType().getName().equals(typeName)) return candidate;
        return null;
    }

    private static int intAttribute(Annotation annotation, String name, int fallback) {
        Object value = attribute(annotation, name); return value instanceof Integer integer ? integer : fallback;
    }

    private static int intAttributeAlias(Annotation annotation, String primary, String alias, int fallback) {
        int primaryValue = intAttribute(annotation, primary, fallback);
        int aliasValue = intAttribute(annotation, alias, fallback);
        Object primaryDefault = annotationDefault(annotation, primary);
        return primaryDefault instanceof Integer defaultValue && primaryValue == defaultValue && aliasValue != defaultValue
                ? aliasValue : primaryValue;
    }

    private static Object annotationDefault(Annotation annotation, String name) {
        if (annotation == null) return null;
        try { return annotation.annotationType().getMethod(name).getDefaultValue(); }
        catch (ReflectiveOperationException ignored) { return null; }
    }

    private static List<Annotation> sortDefaultAnnotations(Parameter parameter) {
        List<Annotation> result = new ArrayList<>();
        for (Annotation candidate : parameter.getAnnotations()) {
            String typeName = candidate.annotationType().getName();
            if (typeName.equals("org.springframework.data.web.SortDefault")) result.add(candidate);
            if (typeName.equals("org.springframework.data.web.SortDefaults")) {
                Object values = attribute(candidate, "value");
                if (values instanceof Annotation[] annotations) result.addAll(List.of(annotations));
            }
        }
        return result;
    }

    private static String enumAttribute(Annotation annotation, String name, String fallback) {
        Object value = attribute(annotation, name); return value instanceof Enum<?> enumValue ? enumValue.name() : fallback;
    }

    private static List<String> stringArrayAttribute(Annotation annotation, String name) {
        Object value = attribute(annotation, name);
        return value instanceof String[] values ? List.of(values) : List.of();
    }

    private static Object attribute(Annotation annotation, String name) {
        if (annotation == null) return null;
        try { return annotation.annotationType().getMethod(name).invoke(annotation); }
        catch (ReflectiveOperationException ignored) { return null; }
    }

    private static boolean isAuthenticationPrincipal(Parameter parameter) {
        String name = parameter.getType().getName();
        if (name.equals("java.security.Principal") || name.equals("org.springframework.security.core.Authentication")
                || name.equals("org.springframework.security.core.context.SecurityContext")) return true;
        return java.util.stream.Stream.of(parameter.getAnnotations()).map(annotation -> annotation.annotationType().getName())
                .anyMatch(annotation -> annotation.equals("org.springframework.security.core.annotation.AuthenticationPrincipal")
                        || annotation.equals("org.springframework.security.core.annotation.CurrentSecurityContext"));
    }

    private static boolean isResponseEntity(Type type) {
        if (type instanceof java.lang.reflect.ParameterizedType parameterized) return parameterized.getRawType() == org.springframework.http.ResponseEntity.class;
        return type == org.springframework.http.ResponseEntity.class;
    }

    private static boolean isBinary(ApiSchema schema) { return "binary".equals(schema.format()); }

    private static boolean isPresent(String className, ClassLoader classLoader) {
        try { Class.forName(className, false, classLoader); return true; }
        catch (ClassNotFoundException ignored) { return false; }
    }

    private static String parameterName(MuonicaSecurityScheme annotation) {
        if (!annotation.parameterName().isBlank()) return annotation.parameterName();
        return annotation.type() == MuonicaSecurityScheme.Type.HTTP ? "Authorization" : "X-API-Key";
    }

    private DocumentationResolution projectDocumentation() {
        return documentationResolver.apply(projectClass());
    }

    private Class<?> projectClass() {
        String[] projectBeans = applicationContext.getBeanNamesForAnnotation(MuonicaProject.class);
        if (projectBeans.length > 0) return userClass(projectBeans[0]);
        String[] documentationBeans = applicationContext.getBeanNamesForAnnotation(MuonicaDocumentation.class);
        for (String bean : documentationBeans) {
            Class<?> type = userClass(bean);
            if (type != null && AnnotatedElementUtils.hasAnnotation(type, SpringBootConfiguration.class)) return type;
        }
        return null;
    }

    private Class<?> userClass(String beanName) {
        Class<?> type = applicationContext.getType(beanName);
        return type == null ? null : ClassUtils.getUserClass(type);
    }

    private MuonicaProject projectMetadata() {
        String[] beans = applicationContext.getBeanNamesForAnnotation(MuonicaProject.class);
        return beans.length == 0 ? null : applicationContext.findAnnotationOnBean(beans[0], MuonicaProject.class);
    }
}
