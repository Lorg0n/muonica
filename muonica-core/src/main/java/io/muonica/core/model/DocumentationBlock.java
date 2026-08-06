package io.muonica.core.model;

import java.util.Map;

/** Extensible content attached to a project, group, or endpoint. */
public record DocumentationBlock(String type, String content, Map<String, Object> attributes, DocumentationOrigin origin) {
    public DocumentationBlock {
        content = content == null ? "" : content;
        attributes = Map.copyOf(attributes);
        origin = origin == null ? DocumentationOrigin.USER : origin;
    }

    public DocumentationBlock(String type, String content, Map<String, Object> attributes) {
        this(type, content, attributes, DocumentationOrigin.USER);
    }
}
