package io.muonica.core.model.documentation;

/** A non-fatal documentation configuration warning. */
public record DocumentationWarning(String type, String resource, Integer line, String message) { }
