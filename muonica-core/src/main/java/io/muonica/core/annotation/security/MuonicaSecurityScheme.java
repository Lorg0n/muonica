package io.muonica.core.annotation.security;

import io.muonica.core.model.api.ApiParameter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares a custom HTTP or API-key security scheme for a Muonica project. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(MuonicaSecuritySchemes.class)
public @interface MuonicaSecurityScheme {
    String name();
    Type type();
    String scheme() default "bearer";
    String bearerFormat() default "";
    /** Uses {@code Authorization} for HTTP schemes and {@code X-API-Key} for API keys when blank. */
    String parameterName() default "";
    ApiParameter.ParameterLocation parameterLocation() default ApiParameter.ParameterLocation.HEADER;
    enum Type { HTTP, API_KEY }
}
