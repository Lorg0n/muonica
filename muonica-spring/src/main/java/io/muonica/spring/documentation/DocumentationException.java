package io.muonica.spring.documentation;

final class DocumentationException extends RuntimeException {
    private final String type;
    private final String resource;
    private final Integer line;

    DocumentationException(String type, String resource, Integer line, String message) {
        super(message);
        this.type = type;
        this.resource = resource;
        this.line = line;
    }

    String type() { return type; }
    String resource() { return resource; }
    Integer line() { return line; }
}
