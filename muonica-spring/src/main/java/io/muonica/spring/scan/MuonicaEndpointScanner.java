package io.muonica.spring.scan;

import io.muonica.core.annotation.api.MuonicaGroup;
import io.muonica.core.annotation.api.MuonicaHidden;
import io.muonica.core.annotation.api.MuonicaProject;
import io.muonica.core.annotation.documentation.MuonicaDocumentation;
import io.muonica.core.annotation.documentation.MuonicaPage;
import io.muonica.core.model.api.ApiEndpoint;
import io.muonica.core.model.api.ApiGroup;
import io.muonica.core.model.api.ApiProject;
import io.muonica.core.model.documentation.ApiDocumentationPage;
import io.muonica.core.model.documentation.DocumentationBlock;
import io.muonica.core.model.documentation.DocumentationWarning;
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
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.BiFunction;
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
    private final BiFunction<MuonicaPage, AnnotatedElement, DocumentationResolution> pageResolver;
    private final DocumentationComposer documentationComposer;
    private final boolean documentationStrict;
    private volatile ApiProject cachedProject;

    public MuonicaEndpointScanner(RequestMappingHandlerMapping handlerMapping, ApplicationContext applicationContext,
            Environment environment) {
        this(handlerMapping, applicationContext, environment, documentationResolver(applicationContext, environment));
    }

    private MuonicaEndpointScanner(RequestMappingHandlerMapping handlerMapping, ApplicationContext applicationContext,
            Environment environment, DocumentationResolver resolver) {
        this(handlerMapping, applicationContext, environment, resolver::resolve, resolver::resolvePage, resolver.strict());
    }

    MuonicaEndpointScanner(RequestMappingHandlerMapping handlerMapping, ApplicationContext applicationContext,
            Environment environment, Function<AnnotatedElement, DocumentationResolution> documentationResolver) {
        this(handlerMapping, applicationContext, environment, documentationResolver,
                (page, source) -> DocumentationResolution.empty(),
                environment.getProperty("muonica.documentation.strict", Boolean.class, true));
    }

    MuonicaEndpointScanner(RequestMappingHandlerMapping handlerMapping, ApplicationContext applicationContext,
            Environment environment, Function<AnnotatedElement, DocumentationResolution> documentationResolver,
            BiFunction<MuonicaPage, AnnotatedElement, DocumentationResolution> pageResolver, boolean documentationStrict) {
        this.handlerMapping = handlerMapping;
        this.applicationContext = applicationContext;
        this.environment = environment;
        this.documentationResolver = documentationResolver;
        this.pageResolver = pageResolver;
        this.documentationComposer = new DocumentationComposer(
                environment.getProperty("muonica.documentation.strict", Boolean.class, true));
        this.documentationStrict = documentationStrict;
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
        DocumentationPages pages = documentationPages(projectClass, groups);
        List<DocumentationWarning> documentationWarnings = new ArrayList<>(projectDocumentation.warnings());
        documentationWarnings.addAll(pages.warnings());
        ProjectDetails project = projectDetails();
        return new ApiProject(
                project.name(),
                project.version(),
                project.description(),
                groups,
                projectDocumentation.blocks(),
                documentationWarnings,
                pages.pages(),
                AnnotationModelResolver.securitySchemes(projectClass),
                AnnotationModelResolver.servers(projectClass),
                schemas.components());
    }

    private DocumentationPages documentationPages(Class<?> projectClass, List<ApiGroup> groups) {
        if (projectClass == null) {
            return new DocumentationPages(List.of(), List.of());
        }
        Set<String> titles = new HashSet<>();
        List<ApiDocumentationPage> pages = new ArrayList<>();
        List<DocumentationWarning> skippedWarnings = new ArrayList<>();
        for (MuonicaPage page : projectClass.getAnnotationsByType(MuonicaPage.class)) {
            DocumentationResolution resolution = pageResolver.apply(page, projectClass);
            List<DocumentationWarning> warnings = new ArrayList<>(resolution.warnings());
            if (resolution.blocks().isEmpty() && !warnings.isEmpty()) {
                skippedWarnings.addAll(warnings);
                continue;
            }
            if (!titles.add(page.title())) {
                pageProblem(warnings, "DUPLICATE_PAGE_TITLE", page.title(), null,
                        "Documentation page titles must be unique: " + page.title());
                skippedWarnings.addAll(warnings);
                continue;
            }
            List<DocumentationBlock> blocks = new ArrayList<>();
            for (DocumentationBlock block : resolution.blocks()) {
                if (block.type().equals("slot")) {
                    pageProblem(warnings, "INVALID_PAGE_SLOT", source(block, page.title()), line(block),
                            "Documentation pages cannot contain endpoint slots");
                    continue;
                }
                if (block.type().equals("endpoint") && !documentedEndpoint(block, groups)) {
                    String reference = block.attributes().get("method") + " " + block.attributes().get("path");
                    pageProblem(warnings, "UNKNOWN_ENDPOINT_REFERENCE", source(block, page.title()), line(block),
                            "No documented endpoint matches " + reference);
                    continue;
                }
                blocks.add(block);
            }
            if (blocks.isEmpty() && !warnings.isEmpty()) {
                skippedWarnings.addAll(warnings);
                continue;
            }
            pages.add(new ApiDocumentationPage(page.title(), blocks, warnings));
        }
        return new DocumentationPages(pages, skippedWarnings);
    }

    private void pageProblem(List<DocumentationWarning> warnings, String type, String resource, Integer line,
            String message) {
        if (documentationStrict) {
            throw new IllegalStateException(message + " (" + resource + (line == null ? "" : ":" + line) + ")");
        }
        warnings.add(new DocumentationWarning(type, resource, line, message));
    }

    private static boolean documentedEndpoint(DocumentationBlock block, List<ApiGroup> groups) {
        String method = String.valueOf(block.attributes().get("method"));
        String path = String.valueOf(block.attributes().get("path"));
        return groups.stream()
                .flatMap(group -> group.endpoints().stream())
                .anyMatch(endpoint -> endpoint.method().equalsIgnoreCase(method) && endpoint.path().equals(path));
    }

    private static String source(DocumentationBlock block, String fallback) {
        Object source = block.attributes().get("source");
        return source == null ? fallback : source.toString();
    }

    private static Integer line(DocumentationBlock block) {
        Object line = block.attributes().get("line");
        return line instanceof Integer value ? value : null;
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
        String[] pageBeans = applicationContext.getBeanNamesForAnnotation(MuonicaPage.class);
        if (pageBeans != null) {
            for (String bean : pageBeans) {
                Class<?> type = userClass(bean);
                if (type != null && AnnotatedElementUtils.hasAnnotation(type, SpringBootConfiguration.class)) {
                    return type;
                }
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

    private record DocumentationPages(List<ApiDocumentationPage> pages, List<DocumentationWarning> warnings) {
    }

    private static DocumentationResolver documentationResolver(ApplicationContext applicationContext, Environment environment) {
        return new DocumentationResolver(new DocumentationFileLoader(applicationContext), new DocumentationParser(), environment);
    }
}
