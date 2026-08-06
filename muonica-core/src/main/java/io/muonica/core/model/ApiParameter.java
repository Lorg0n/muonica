package io.muonica.core.model;

public record ApiParameter(String name, ParameterLocation location, boolean required, ApiSchema schema) {
    public enum ParameterLocation { PATH, QUERY, HEADER, COOKIE }
}
