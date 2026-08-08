package io.muonica.core.annotation.documentation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares a standalone Markdown page in a Muonica project's documentation navigation. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(MuonicaPages.class)
public @interface MuonicaPage {
    String title();
    String content() default "";
    String file() default "";
}
