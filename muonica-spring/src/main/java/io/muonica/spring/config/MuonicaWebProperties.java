package io.muonica.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Web endpoints under which Muonica documentation is exposed. */
@ConfigurationProperties(prefix = "muonica.web")
public record MuonicaWebProperties(@DefaultValue("/docs") String path) {
    public MuonicaWebProperties {
        path = normalize(path);
    }

    public boolean isRootPath() {
        return path.equals("/");
    }

    public String childPath(String child) {
        return isRootPath() ? child : path + child;
    }

    private static String normalize(String configuredPath) {
        String normalized = configuredPath == null ? "/docs" : configuredPath.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("muonica.web.path must not be blank");
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        normalized = normalized.replaceAll("/{2,}", "/");
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
