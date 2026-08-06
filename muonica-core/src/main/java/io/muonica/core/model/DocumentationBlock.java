package io.muonica.core.model;

import java.util.Map;

/** Extensible content attached to a project, group, or endpoint. */
public record DocumentationBlock(String type, String content, Map<String, Object> attributes) {
    public DocumentationBlock {
        attributes = Map.copyOf(attributes);
    }
}
