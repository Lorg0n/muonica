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
    Type type();
    String content();
    String title() default "";
    String language() default "";
    NoticeLevel noticeLevel() default NoticeLevel.INFO;

    enum Type { MARKDOWN, NOTICE, EXAMPLE, IMAGE, MERMAID }
    enum NoticeLevel { INFO, WARNING, DANGER }
}
