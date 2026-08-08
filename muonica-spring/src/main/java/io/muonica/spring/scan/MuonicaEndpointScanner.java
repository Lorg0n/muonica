package io.muonica.spring.scan;

import io.muonica.core.annotation.api.MuonicaGroup;
import io.muonica.core.annotation.api.MuonicaHidden;
import io.muonica.core.annotation.api.MuonicaProject;
import io.muonica.core.annotation.documentation.MuonicaDocumentation;
import io.muonica.core.model.api.ApiEndpoint;
import io.muonica.core.model.api.ApiGroup;
import io.muonica.core.model.api.ApiProject;
import io.muonica.spring.documentation.DocumentationComposer;
import io.muonica.spring.documentation.DocumentationFileLoader;
import io.muonica.spring.documentation.DocumentationParser;
import io.muonica.spring.documentation.DocumentationResolution;
import io.muonica.spring.documentation.DocumentationResolver;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
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

    public MuonicaEndpointScanner(RequestMappingHandlerMapping handlerMapping, ApplicationContext applicationContext,
            Environment environment) {
        this(handlerMapping, applicationContext, environment,
                new DocumentationResolver(
                        new DocumentationFileLoader(applicationContext),
                        new DocumentationParser(),
                        environment)::resolve);
    }

    MuonicaEndpointScanner(RequestMappingHandlerMapping handlerMapping, ApplicationContext applicationContext,
            Environment environment, Function<AnnotatedElement, DocumentationResolution> documentationResolver) {
        this.handlerMapping = handlerMapping;
        this.applicationContext = applicationContext;
        this.environment = environment;
        this.documentationResolver = documentationResolver;
        this.documentationComposer = new DocumentationComposer(
                environment.getProperty("muonica.documentation.strict", Boolean.class, true));
    }

    public ApiProject scan() {
        ApiProject result = cachedProject;
        if (result != null) {
            return result;
        }
        synchronized (this) {
            if (cachedProject == null) {
                cachedProject = buildProject();
            }
            return cachedProject;
        }
    }

    @Override
    public void afterSingletonsInstantiated() {
        scan();
    }

    private ApiProject buildProject() {
        SchemaResolver schemas = new SchemaResolver();
        EndpointFactory endpointFactory = new EndpointFactory(
                applicationContext, schemas, documentationResolver, documentationComposer);
        Class<?> projectClass = projectClass();
        DocumentationResolution projectDocumentation = documentationResolver.apply(projectClass);
        Map<Class<?>, List<ApiEndpoint>> endpoints = new LinkedHashMap<>();
        Map<Class<?>, DocumentationResolution> groupDocumentation = new LinkedHashMap<>();

        handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> shouldDocument(entry.getValue()))
                .sorted(Comparator.comparing(entry -> entry.getValue().getBeanType().getName()))
                .forEach(entry -> collectEndpoints(
                        entry, endpointFactory, projectDocumentation, endpoints, groupDocumentation));

        List<ApiGroup> groups = endpoints.entrySet().stream()
                .map(entry -> group(entry.getKey(), entry.getValue(), groupDocumentation.get(entry.getKey())))
                .sorted(Comparator.comparing(ApiGroup::name))
                .toList();
        ProjectDetails project = projectDetails();
        return new ApiProject(
                project.name(),
                project.version(),
                project.description(),
                groups,
                projectDocumentation.blocks(),
                projectDocumentation.warnings(),
                AnnotationModelResolver.securitySchemes(projectClass),
                AnnotationModelResolver.servers(projectClass),
                schemas.components());
    }

    private void collectEndpoints(Map.Entry<RequestMappingInfo, HandlerMethod> entry, EndpointFactory endpointFactory,
            DocumentationResolution projectDocumentation, Map<Class<?>, List<ApiEndpoint>> endpoints,
            Map<Class<?>, DocumentationResolution> groupDocumentation) {
        Class<?> controller = entry.getValue().getBeanType();
        DocumentationResolution documentation = groupDocumentation.computeIfAbsent(controller, documentationResolver);
        endpoints.computeIfAbsent(controller, ignored -> new ArrayList<>())
                .addAll(toEndpoints(entry, endpointFactory, projectDocumentation, documentation).toList());
    }

    private Stream<ApiEndpoint> toEndpoints(Map.Entry<RequestMappingInfo, HandlerMethod> entry,
            EndpointFactory endpointFactory, DocumentationResolution projectDocumentation,
            DocumentationResolution groupDocumentation) {
        RequestMappingInfo mapping = entry.getKey();
        HandlerMethod handler = entry.getValue();
        List<String> paths = mapping.getPatternValues().stream().sorted().toList();
        List<String> detectedMethods = mapping.getMethodsCondition().getMethods().stream()
                .map(Enum::name)
                .sorted()
                .toList();
        List<String> methods = detectedMethods.isEmpty() ? List.of("ANY") : detectedMethods;
        return paths.stream().flatMap(path -> methods.stream()
                .map(method -> endpointFactory.create(
                        method, path, handler, mapping, projectDocumentation, groupDocumentation)));
    }

    private static boolean shouldDocument(HandlerMethod handler) {
        Class<?> controller = handler.getBeanType();
        return !controller.getPackageName().startsWith(MUONICA_PACKAGE)
                && !ErrorController.class.isAssignableFrom(controller)
                && !controller.isAnnotationPresent(MuonicaHidden.class)
                && !handler.getMethod().isAnnotationPresent(MuonicaHidden.class);
    }

    private static ApiGroup group(Class<?> controller, List<ApiEndpoint> endpoints,
            DocumentationResolution documentation) {
        MuonicaGroup annotation = controller.getAnnotation(MuonicaGroup.class);
        String name = annotation != null && !annotation.name().isBlank()
                ? annotation.name()
                : controller.getSimpleName();
        String description = annotation != null && !annotation.description().isBlank()
                ? annotation.description()
                : null;
        List<ApiEndpoint> sortedEndpoints = endpoints.stream()
                .sorted(Comparator.comparing(ApiEndpoint::path).thenComparing(ApiEndpoint::method))
                .toList();
        return new ApiGroup(name, description, sortedEndpoints, documentation.blocks(), documentation.warnings());
    }

    private ProjectDetails projectDetails() {
        MuonicaProject metadata = projectMetadata();
        String defaultName = environment.getProperty("spring.application.name", "application");
        String name = metadata != null && !metadata.title().isBlank() ? metadata.title() : defaultName;
        String version = metadata != null && !metadata.version().isBlank() ? metadata.version() : "0.0.0";
        String description = metadata != null && !metadata.description().isBlank() ? metadata.description() : null;
        return new ProjectDetails(name, version, description);
    }

    private Class<?> projectClass() {
        String[] projectBeans = applicationContext.getBeanNamesForAnnotation(MuonicaProject.class);
        if (projectBeans.length > 0) {
            return userClass(projectBeans[0]);
        }
        String[] documentationBeans = applicationContext.getBeanNamesForAnnotation(MuonicaDocumentation.class);
        for (String bean : documentationBeans) {
            Class<?> type = userClass(bean);
            if (type != null && AnnotatedElementUtils.hasAnnotation(type, SpringBootConfiguration.class)) {
                return type;
            }
        }
        return null;
    }

    private Class<?> userClass(String beanName) {
        Class<?> type = applicationContext.getType(beanName);
        return type == null ? null : ClassUtils.getUserClass(type);
    }

    private MuonicaProject projectMetadata() {
        String[] beans = applicationContext.getBeanNamesForAnnotation(MuonicaProject.class);
        return beans.length == 0
                ? null
                : applicationContext.findAnnotationOnBean(beans[0], MuonicaProject.class);
    }

    private record ProjectDetails(String name, String version, String description) {
    }
}
