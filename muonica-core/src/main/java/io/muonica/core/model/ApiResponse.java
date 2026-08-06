package io.muonica.core.model;

public record ApiResponse(String statusCode, String description, String contentType, ApiSchema schema) { }
