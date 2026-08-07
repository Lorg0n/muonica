package io.muonica.core.model.security;

import io.muonica.core.model.api.ApiParameter;

/** A security scheme declared by an API project. */
public record ApiSecurityScheme(
        String name,
        Type type,
        String scheme,
        String bearerFormat,
        String parameterName,
        ApiParameter.ParameterLocation parameterLocation
) {
    public enum Type { HTTP, API_KEY }
}
