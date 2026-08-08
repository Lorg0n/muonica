package io.muonica.spring.scan;

import io.muonica.core.annotation.api.MuonicaBadge;
import io.muonica.core.annotation.api.MuonicaOperation;
import io.muonica.core.annotation.documentation.MuonicaDocumentation;
import io.muonica.core.model.api.ApiEndpoint;
import io.muonica.spring.documentation.DocumentationComposer;
import io.muonica.spring.documentation.DocumentationResolution;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.stream.Stream;
import org.springframework.context.ApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

/** Builds one neutral endpoint model from a Spring MVC handler. */
final class EndpointFactory {
    private final HandlerParameterResolver parameterResolver;
    private final HandlerResponseResolver responseResolver;
    private final Function<AnnotatedElement, DocumentationResolution> documentationResolver;
    private final DocumentationComposer documentationComposer;

    EndpointFactory(ApplicationContext applicationContext, SchemaResolver schemas,
            Function<AnnotatedElement, DocumentationResolution> documentationResolver,
            DocumentationComposer documentationComposer) {
        this.parameterResolver = new HandlerParameterResolver(schemas);
        this.responseResolver = new HandlerResponseResolver(applicationContext, schemas);
        this.documentationResolver = documentationResolver;
        this.documentationComposer = documentationComposer;
    }

    ApiEndpoint create(String httpMethod, String path, HandlerMethod handler, RequestMappingInfo mapping,
            DocumentationResolution projectDocumentation, DocumentationResolution groupDocumentation) {
        Method method = handler.getMethod();
        MuonicaOperation operation = method.getAnnotation(MuonicaOperation.class);
        HandlerParameterResolver.Resolution payload = parameterResolver.resolve(method, mapping);
        DocumentationResolution documentation = documentation(method, projectDocumentation, groupDocumentation);

        return new ApiEndpoint(
                httpMethod,
                path,
                handler.getBeanType().getSimpleName(),
                method.getName(),
                blankToNull(operation == null ? null : operation.summary()),
                blankToNull(operation == null ? null : operation.description()),
                payload.parameters(),
                payload.request(),
                responseResolver.resolve(method, mapping),
                documentation.blocks(),
                AnnotationModelResolver.securityRequirements(handler.getBeanType(), method),
                badges(method),
                documentation.warnings());
    }

    private DocumentationResolution documentation(Method method, DocumentationResolution project,
            DocumentationResolution group) {
        DocumentationResolution endpoint = documentationResolver.apply(method);
        boolean inherits = Stream.of(method.getAnnotationsByType(MuonicaDocumentation.class))
                .allMatch(MuonicaDocumentation::inherit);
        return documentationComposer.compose(
                inherits ? project : DocumentationResolution.empty(),
                inherits ? group : DocumentationResolution.empty(),
                endpoint);
    }

    private static java.util.List<String> badges(Method method) {
        return Stream.of(method.getAnnotationsByType(MuonicaBadge.class))
                .map(MuonicaBadge::value)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
