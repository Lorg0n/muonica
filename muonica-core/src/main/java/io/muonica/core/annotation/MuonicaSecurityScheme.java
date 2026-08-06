package io.muonica.core.annotation;

import io.muonica.core.model.ApiParameter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(MuonicaSecuritySchemes.class)
public @interface MuonicaSecurityScheme {
    String name();
    Type type();
    String scheme() default "bearer";
    String bearerFormat() default "";
    String parameterName() default "X-API-Key";
    ApiParameter.ParameterLocation parameterLocation() default ApiParameter.ParameterLocation.HEADER;
    enum Type { HTTP, API_KEY }
}
