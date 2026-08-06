package io.muonica.spring.documentation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/** Loads and caches classpath documentation resources. */
public final class DocumentationFileLoader {
    private final ResourceLoader resourceLoader;
    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();

    public DocumentationFileLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String load(String location) {
        if (!location.startsWith("classpath:")) {
            throw new DocumentationException("UNSUPPORTED_RESOURCE", location, null,
                    "Only classpath: documentation resources are supported");
        }
        return cache.computeIfAbsent(location, this::read);
    }

    private String read(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new DocumentationException("MISSING_RESOURCE", location, null,
                    "Documentation resource was not found");
        }
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new DocumentationException("RESOURCE_READ_ERROR", location, null,
                    "Documentation resource could not be read: " + exception.getMessage());
        }
    }
}
