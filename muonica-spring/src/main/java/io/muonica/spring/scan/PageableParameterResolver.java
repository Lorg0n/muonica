package io.muonica.spring.scan;

import io.muonica.core.annotation.api.MuonicaAllowedSort;
import io.muonica.core.model.api.ApiParameter;
import io.muonica.core.model.api.ApiSchema;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Resolves optional Spring Data pageable annotations without requiring Spring Data at runtime. */
final class PageableParameterResolver {
    private static final String PAGEABLE = "org.springframework.data.domain.Pageable";

    private PageableParameterResolver() {
    }

    static boolean supports(Parameter parameter) {
        return parameter.getType().getName().equals(PAGEABLE)
                && isPresent(PAGEABLE, parameter.getDeclaringExecutable().getDeclaringClass().getClassLoader());
    }

    static List<ApiParameter> resolve(Parameter parameter) {
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
            List<String> values = Stream.of(allowedSort.value())
                    .filter(value -> !value.isBlank())
                    .flatMap(value -> Stream.of(value + ",ASC", value + ",DESC"))
                    .toList();
            sortSchema = ApiSchema.enumeration("string", values)
                    .withMetadata(null, sortSchema.example(), sortSchema.defaultValue());
        }
        return List.of(
                new ApiParameter("page", ApiParameter.ParameterLocation.QUERY, false, "Zero-based page index.", pageSchema),
                new ApiParameter("size", ApiParameter.ParameterLocation.QUERY, false, "Maximum number of results per page.", sizeSchema),
                new ApiParameter("sort", ApiParameter.ParameterLocation.QUERY, false,
                        "Sort property and direction, for example name,ASC.", sortSchema));
    }

    private static List<String> sortDefaults(Parameter parameter, Annotation pageableDefault) {
        List<String> result = new ArrayList<>();
        String direction = enumAttribute(pageableDefault, "direction", "ASC");
        for (String property : stringArrayAttribute(pageableDefault, "sort")) {
            result.add(property + "," + direction);
        }
        for (Annotation candidate : sortDefaultAnnotations(parameter)) {
            String sortDirection = enumAttribute(candidate, "direction", "ASC");
            List<String> properties = stringArrayAttribute(candidate, "sort");
            if (properties.isEmpty()) {
                properties = stringArrayAttribute(candidate, "value");
            }
            for (String property : properties) {
                result.add(property + "," + sortDirection);
            }
        }
        return result.stream().distinct().toList();
    }

    private static Annotation annotation(AnnotatedElement source, String typeName) {
        for (Annotation candidate : source.getAnnotations()) {
            if (candidate.annotationType().getName().equals(typeName)) {
                return candidate;
            }
        }
        return null;
    }

    private static int intAttribute(Annotation annotation, String name, int fallback) {
        Object value = attribute(annotation, name);
        return value instanceof Integer integer ? integer : fallback;
    }

    private static int intAttributeAlias(Annotation annotation, String primary, String alias, int fallback) {
        int primaryValue = intAttribute(annotation, primary, fallback);
        int aliasValue = intAttribute(annotation, alias, fallback);
        Object primaryDefault = annotationDefault(annotation, primary);
        return primaryDefault instanceof Integer defaultValue && primaryValue == defaultValue && aliasValue != defaultValue
                ? aliasValue
                : primaryValue;
    }

    private static Object annotationDefault(Annotation annotation, String name) {
        if (annotation == null) {
            return null;
        }
        try {
            return annotation.annotationType().getMethod(name).getDefaultValue();
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static List<Annotation> sortDefaultAnnotations(Parameter parameter) {
        List<Annotation> result = new ArrayList<>();
        for (Annotation candidate : parameter.getAnnotations()) {
            String typeName = candidate.annotationType().getName();
            if (typeName.equals("org.springframework.data.web.SortDefault")) {
                result.add(candidate);
            }
            if (typeName.equals("org.springframework.data.web.SortDefaults")) {
                Object values = attribute(candidate, "value");
                if (values instanceof Annotation[] annotations) {
                    result.addAll(List.of(annotations));
                }
            }
        }
        return result;
    }

    private static String enumAttribute(Annotation annotation, String name, String fallback) {
        Object value = attribute(annotation, name);
        return value instanceof Enum<?> enumValue ? enumValue.name() : fallback;
    }

    private static List<String> stringArrayAttribute(Annotation annotation, String name) {
        Object value = attribute(annotation, name);
        return value instanceof String[] values ? List.of(values) : List.of();
    }

    private static Object attribute(Annotation annotation, String name) {
        if (annotation == null) {
            return null;
        }
        try {
            return annotation.annotationType().getMethod(name).invoke(annotation);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean isPresent(String className, ClassLoader classLoader) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
