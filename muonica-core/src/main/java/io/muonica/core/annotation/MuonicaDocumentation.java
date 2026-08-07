package io.muonica.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(MuonicaDocumentations.class)
public @interface MuonicaDocumentation {
    Type type() default Type.MARKDOWN;
    String content() default "";
    String file() default "";
    String title() default "";
    String language() default "";
    NoticeLevel noticeLevel() default NoticeLevel.INFO;
    /** Whether documentation inherited from the project and controller should be included. */
    boolean inherit() default true;

    enum Type { MARKDOWN, NOTICE, EXAMPLE, IMAGE, MERMAID }
    enum NoticeLevel { INFO, WARNING, DANGER }
}
