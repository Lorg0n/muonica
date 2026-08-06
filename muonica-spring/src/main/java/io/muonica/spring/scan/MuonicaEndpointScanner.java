package io.muonica.spring.scan;

import io.muonica.core.model.ApiEndpoint;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Adapts the mappings registered by Spring MVC to Muonica's neutral model. */
public final class MuonicaEndpointScanner {
    private static final String MUONICA_PACKAGE = "io.muonica.spring.web";
    private final RequestMappingHandlerMapping handlerMapping;

    public MuonicaEndpointScanner(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    public List<ApiEndpoint> scan() {
        return handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> !entry.getValue().getBeanType().getPackageName().startsWith(MUONICA_PACKAGE))
                .flatMap(this::toEndpoints)
                .sorted(Comparator.comparing(ApiEndpoint::path).thenComparing(ApiEndpoint::method))
                .toList();
    }

    private Stream<ApiEndpoint> toEndpoints(Map.Entry<RequestMappingInfo, HandlerMethod> entry) {
        RequestMappingInfo mapping = entry.getKey();
        HandlerMethod handler = entry.getValue();
        List<String> paths = mapping.getPatternValues().stream().sorted().toList();
        List<String> methods = mapping.getMethodsCondition().getMethods().stream()
                .map(Enum::name)
                .sorted()
                .toList();
        if (methods.isEmpty()) {
            methods = List.of("ANY");
        }
        return paths.stream().flatMap(path -> methods.stream().map(method -> new ApiEndpoint(
                method,
                path,
                handler.getBeanType().getSimpleName(),
                handler.getMethod().getName()
        )));
    }
}
