package io.muonica.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(MuonicaResponses.class)
public @interface MuonicaResponse {
    int status();
    String description();
    String contentType() default "application/json";
    Class<?> body() default Void.class;
}
