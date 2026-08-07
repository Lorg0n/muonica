package io.muonica.core.annotation.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the conventional HTTP bearer scheme for a Muonica project.
 *
 * <p>The generated scheme uses the {@code Authorization} header. Use
 * {@link MuonicaSecurityScheme} when an API needs API keys or a custom HTTP
 * authentication scheme.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MuonicaBearerAuth {
    String name() default "bearerAuth";
    String bearerFormat() default "";
    String parameterName() default "Authorization";
}
