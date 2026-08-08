package io.muonica.spring.scan;

import io.muonica.core.annotation.api.MuonicaDefault;
import io.muonica.core.annotation.api.MuonicaDescription;
import io.muonica.core.annotation.api.MuonicaExample;
import io.muonica.core.model.api.ApiSchema;
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
import java.io.File;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
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
                return ApiSchema.array(baseSchema(parameterizedType.getActualTypeArguments()[0]));
            }
            if (raw instanceof Class<?> rawClass && Map.class.isAssignableFrom(rawClass)) {
                return ApiSchema.object();
            }
            return baseSchema(raw);
        }
        if (type instanceof GenericArrayType arrayType) {
            return ApiSchema.array(baseSchema(arrayType.getGenericComponentType()));
        }
        if (!(type instanceof Class<?> clazz)) return ApiSchema.object();
        if (clazz == byte[].class || clazz == File.class || Resource.class.isAssignableFrom(clazz)) return ApiSchema.scalar("string", "binary");
        if (clazz.isArray()) return ApiSchema.array(baseSchema(clazz.getComponentType()));
        if (clazz == String.class || clazz == Character.class || clazz == char.class) return ApiSchema.scalar("string", null);
        if (clazz == UUID.class) return ApiSchema.scalar("string", "uuid");
        if (clazz == LocalDate.class) return ApiSchema.scalar("string", "date");
        if (clazz == LocalDateTime.class || clazz == OffsetDateTime.class || clazz == java.time.Instant.class) return ApiSchema.scalar("string", "date-time");
        if (clazz == boolean.class || clazz == Boolean.class) return ApiSchema.scalar("boolean", null);
        if (clazz == byte.class || clazz == Byte.class || clazz == short.class || clazz == Short.class || clazz == int.class || clazz == Integer.class) return ApiSchema.scalar("integer", "int32");
        if (clazz == long.class || clazz == Long.class || clazz == java.math.BigInteger.class) return ApiSchema.scalar("integer", "int64");
        if (Number.class.isAssignableFrom(clazz) || clazz == float.class || clazz == double.class) return ApiSchema.scalar("number", null);
        if (clazz == MultipartFile.class) return ApiSchema.scalar("string", "binary");
        if (clazz == Void.class || clazz == void.class) return ApiSchema.empty();
        if (clazz.isEnum()) return ApiSchema.enumeration("string",
                List.of(clazz.getEnumConstants()).stream().map(value -> ((Enum<?>) value).name()).toList());
        return componentReference(clazz);
    }

    private ApiSchema componentReference(Class<?> type) {
        String name = names.computeIfAbsent(type, this::componentName);
        if (!components.containsKey(name)) {
            components.put(name, ApiSchema.object());
            components.put(name, component(type));
        }
        return ApiSchema.reference(name);
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
        ApiSchema schema = ApiSchema.object(properties, required);
        return withMetadata(schema, type);
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
        return withMetadata(schema.withValidationConstraints(minLength, maxLength, pattern, minimum, maximum), source);
    }

    private ApiSchema withMetadata(ApiSchema schema, AnnotatedElement source) {
        MuonicaDescription description = source.getAnnotation(MuonicaDescription.class);
        MuonicaExample example = source.getAnnotation(MuonicaExample.class);
        MuonicaDefault defaultValue = source.getAnnotation(MuonicaDefault.class);
        String resolvedDescription = description == null || description.value().isBlank() ? schema.description() : description.value();
        String resolvedExample = example == null ? schema.example() : example.value();
        String resolvedDefault = defaultValue == null ? schema.defaultValue() : defaultValue.value();
        return new ApiSchema(schema.type(), schema.format(), schema.ref(), resolvedDescription, resolvedExample, resolvedDefault,
                schema.properties(), schema.requiredProperties(), schema.items(), schema.enumValues(), schema.minLength(), schema.maxLength(),
                schema.pattern(), schema.minimum(), schema.maximum());
    }

    private boolean isRequired(AnnotatedElement source) {
        return source.isAnnotationPresent(NotNull.class) || source.isAnnotationPresent(NotBlank.class) || source.isAnnotationPresent(NotEmpty.class);
    }
}
