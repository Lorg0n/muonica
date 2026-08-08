package io.muonica.spring.scan;

import io.muonica.core.annotation.api.MuonicaServer;
import io.muonica.core.annotation.security.MuonicaBearerAuth;
import io.muonica.core.annotation.security.MuonicaSecurityRequirement;
import io.muonica.core.annotation.security.MuonicaSecurityScheme;
import io.muonica.core.model.api.ApiParameter;
import io.muonica.core.model.api.ApiServer;
import io.muonica.core.model.security.ApiSecurityScheme;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Maps project and operation annotations that do not depend on handler payloads. */
final class AnnotationModelResolver {
    private AnnotationModelResolver() {
    }

    static List<ApiSecurityScheme> securitySchemes(Class<?> source) {
        if (source == null) {
            return List.of();
        }
        Stream<ApiSecurityScheme> configured = Stream.of(source.getAnnotationsByType(MuonicaSecurityScheme.class))
                .map(annotation -> new ApiSecurityScheme(
                        annotation.name(),
                        ApiSecurityScheme.Type.valueOf(annotation.type().name()),
                        annotation.scheme(),
                        annotation.bearerFormat(),
                        parameterName(annotation),
                        annotation.parameterLocation()));
        Stream<ApiSecurityScheme> bearer = Stream.of(source.getAnnotationsByType(MuonicaBearerAuth.class))
                .map(annotation -> new ApiSecurityScheme(
                        annotation.name(),
                        ApiSecurityScheme.Type.HTTP,
                        "bearer",
                        annotation.bearerFormat(),
                        annotation.parameterName(),
                        ApiParameter.ParameterLocation.HEADER));
        return Stream.concat(configured, bearer).distinct().toList();
    }

    static List<ApiServer> servers(Class<?> source) {
        if (source == null) {
            return List.of();
        }
        return Stream.of(source.getAnnotationsByType(MuonicaServer.class))
                .filter(annotation -> !annotation.url().isBlank())
                .map(annotation -> new ApiServer(annotation.url(), blankToNull(annotation.description())))
                .toList();
    }

    static List<List<String>> securityRequirements(Class<?> controller, Method method) {
        List<List<String>> controllerGroups = securityGroups(controller);
        List<List<String>> methodGroups = securityGroups(method);
        if (controllerGroups.isEmpty()) {
            return methodGroups;
        }
        if (methodGroups.isEmpty()) {
            return controllerGroups;
        }

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
        return Stream.of(source.getAnnotationsByType(MuonicaSecurityRequirement.class))
                .map(annotation -> Stream.of(annotation.value())
                        .filter(value -> !value.isBlank())
                        .distinct()
                        .toList())
                .filter(group -> !group.isEmpty())
                .toList();
    }

    private static String parameterName(MuonicaSecurityScheme annotation) {
        if (!annotation.parameterName().isBlank()) {
            return annotation.parameterName();
        }
        return annotation.type() == MuonicaSecurityScheme.Type.HTTP ? "Authorization" : "X-API-Key";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
