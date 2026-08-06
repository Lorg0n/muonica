package io.muonica.spring.scan;

import io.muonica.core.model.ApiSchema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

/** Resolves the supported Java type subset to reusable Muonica schemas. */
final class SchemaResolver {
    private final Map<String, ApiSchema> components = new LinkedHashMap<>();
    private final Map<Class<?>, String> names = new LinkedHashMap<>();

    ApiSchema resolve(Type type) {
        return resolve(type, null);
    }

    ApiSchema resolve(Type type, AnnotatedElement annotations) {
        ApiSchema schema = baseSchema(type);
        return annotations == null ? schema : withConstraints(schema, annotations);
    }

    Map<String, ApiSchema> components() {
        return Map.copyOf(components);
    }

    private ApiSchema baseSchema(Type type) {
        if (type instanceof ParameterizedType parameterizedType) {
            Type raw = parameterizedType.getRawType();
            if (raw instanceof Class<?> rawClass && (rawClass == ResponseEntity.class || rawClass == Optional.class)) {
                return baseSchema(parameterizedType.getActualTypeArguments()[0]);
            }
            if (raw instanceof Class<?> rawClass && Collection.class.isAssignableFrom(rawClass)) {
                return schema("array", null, null, Map.of(), List.of(), baseSchema(parameterizedType.getActualTypeArguments()[0]), List.of());
            }
            if (raw instanceof Class<?> rawClass && Map.class.isAssignableFrom(rawClass)) {
                return schema("object", null, null, Map.of(), List.of(), null, List.of());
            }
            return baseSchema(raw);
        }
        if (type instanceof GenericArrayType arrayType) {
            return schema("array", null, null, Map.of(), List.of(), baseSchema(arrayType.getGenericComponentType()), List.of());
        }
        if (!(type instanceof Class<?> clazz)) return schema("object", null, null, Map.of(), List.of(), null, List.of());
        if (clazz.isArray()) return schema("array", null, null, Map.of(), List.of(), baseSchema(clazz.getComponentType()), List.of());
        if (clazz == String.class || clazz == Character.class || clazz == char.class) return schema("string", null, null, Map.of(), List.of(), null, List.of());
        if (clazz == UUID.class) return schema("string", "uuid", null, Map.of(), List.of(), null, List.of());
        if (clazz == LocalDate.class) return schema("string", "date", null, Map.of(), List.of(), null, List.of());
        if (clazz == LocalDateTime.class || clazz == OffsetDateTime.class || clazz == java.time.Instant.class) return schema("string", "date-time", null, Map.of(), List.of(), null, List.of());
        if (clazz == boolean.class || clazz == Boolean.class) return schema("boolean", null, null, Map.of(), List.of(), null, List.of());
        if (clazz == byte.class || clazz == Byte.class || clazz == short.class || clazz == Short.class || clazz == int.class || clazz == Integer.class) return schema("integer", "int32", null, Map.of(), List.of(), null, List.of());
        if (clazz == long.class || clazz == Long.class || clazz == java.math.BigInteger.class) return schema("integer", "int64", null, Map.of(), List.of(), null, List.of());
        if (Number.class.isAssignableFrom(clazz) || clazz == float.class || clazz == double.class) return schema("number", null, null, Map.of(), List.of(), null, List.of());
        if (clazz == MultipartFile.class) return schema("string", "binary", null, Map.of(), List.of(), null, List.of());
        if (clazz == Void.class || clazz == void.class) return schema(null, null, null, Map.of(), List.of(), null, List.of());
        if (clazz.isEnum()) return schema("string", null, null, Map.of(), List.of(), null,
                List.of(clazz.getEnumConstants()).stream().map(value -> ((Enum<?>) value).name()).toList());
        return componentReference(clazz);
    }

    private ApiSchema componentReference(Class<?> type) {
        String name = names.computeIfAbsent(type, this::componentName);
        if (!components.containsKey(name)) {
            components.put(name, schema("object", null, null, Map.of(), List.of(), null, List.of()));
            components.put(name, component(type));
        }
        return schema(null, null, name, Map.of(), List.of(), null, List.of());
    }

    private String componentName(Class<?> type) {
        String base = type.getSimpleName();
        String name = base.isBlank() ? type.getName().replace('.', '_') : base;
        if (!components.containsKey(name)) return name;
        return type.getName().replace('.', '_').replace('$', '_');
    }

    private ApiSchema component(Class<?> type) {
        Map<String, ApiSchema> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        if (type.isRecord()) {
            for (RecordComponent field : type.getRecordComponents()) {
                properties.put(field.getName(), resolve(field.getGenericType(), field));
                if (isRequired(field)) required.add(field.getName());
            }
        } else {
            for (Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                properties.put(field.getName(), resolve(field.getGenericType(), field));
                if (isRequired(field)) required.add(field.getName());
            }
        }
        return schema("object", null, null, properties, required, null, List.of());
    }

    private ApiSchema withConstraints(ApiSchema schema, AnnotatedElement source) {
        Integer minLength = null;
        Integer maxLength = null;
        String pattern = null;
        Long minimum = null;
        Long maximum = null;
        Size size = source.getAnnotation(Size.class);
        if (size != null) { minLength = size.min(); maxLength = size.max() == Integer.MAX_VALUE ? null : size.max(); }
        Pattern patternAnnotation = source.getAnnotation(Pattern.class);
        if (patternAnnotation != null) pattern = patternAnnotation.regexp();
        Min min = source.getAnnotation(Min.class);
        if (min != null) minimum = min.value();
        Max max = source.getAnnotation(Max.class);
        if (max != null) maximum = max.value();
        return new ApiSchema(schema.type(), schema.format(), schema.ref(), schema.description(), schema.properties(),
                schema.requiredProperties(), schema.items(), schema.enumValues(), minLength, maxLength, pattern, minimum, maximum);
    }

    private boolean isRequired(AnnotatedElement source) {
        return source.isAnnotationPresent(NotNull.class) || source.isAnnotationPresent(NotBlank.class) || source.isAnnotationPresent(NotEmpty.class);
    }

    private static ApiSchema schema(String type, String format, String ref, Map<String, ApiSchema> properties,
            List<String> required, ApiSchema items, List<String> values) {
        return new ApiSchema(type, format, ref, null, properties, required, items, values, null, null, null, null, null);
    }
}
