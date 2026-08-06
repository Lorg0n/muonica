package io.muonica.core.model;

public record ApiRequest(String contentType, boolean required, ApiSchema schema) { }
