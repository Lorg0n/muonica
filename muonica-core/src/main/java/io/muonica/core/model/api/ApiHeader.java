package io.muonica.core.model.api;

/** A response header and its schema. */
public record ApiHeader(String description, ApiSchema schema) { }
