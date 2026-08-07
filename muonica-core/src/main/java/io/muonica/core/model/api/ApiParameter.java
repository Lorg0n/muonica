package io.muonica.core.model.api;

public record ApiParameter(String name, ParameterLocation location, boolean required, String description, ApiSchema schema) {
    public enum ParameterLocation { PATH, QUERY, HEADER, COOKIE }

    public ApiParameter(String name, ParameterLocation location, boolean required, ApiSchema schema) {
        this(name, location, required, null, schema);
    }
}
