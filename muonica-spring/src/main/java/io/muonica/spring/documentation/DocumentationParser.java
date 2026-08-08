package io.muonica.spring.documentation;

import io.muonica.core.model.documentation.DocumentationBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses Markdown and Muonica block directives into neutral documentation blocks. */
public final class DocumentationParser {
    private static final Pattern DIRECTIVE = Pattern.compile("^\\s*:::[ \\t]*(\\w+)(?:[ \\t]+(.*))?$");

    public List<DocumentationBlock> parse(String source, String resource) {
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        List<DocumentationBlock> blocks = new ArrayList<>();
        List<String> markdown = new ArrayList<>();
        int markdownStart = 1;
        boolean markdownFence = false;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String trimmed = line.trim();
            boolean fenceLine = trimmed.startsWith("```") || trimmed.startsWith("~~~");
            if (markdownFence || fenceLine) {
                if (markdown.isEmpty()) markdownStart = index + 1;
                markdown.add(line);
                if (fenceLine) markdownFence = !markdownFence;
                continue;
            }
            Matcher matcher = DIRECTIVE.matcher(line);
            if (matcher.matches()) {
                flushMarkdown(blocks, markdown, resource, markdownStart);
                String directive = matcher.group(1);
                String argument = matcher.group(2);
                int openingLine = index + 1;
                int closing = findClosing(lines, index + 1, resource, openingLine);
                String body = String.join("\n", java.util.Arrays.copyOfRange(lines, index + 1, closing)).strip();
                blocks.add(block(directive, argument, body, resource, openingLine));
                index = closing;
                markdownStart = index + 2;
            } else if (trimmed.startsWith(":::") && !trimmed.equals(":::")) {
                throw error("UNKNOWN_DIRECTIVE", resource, index + 1, "Unknown documentation directive: " + trimmed);
            } else if (trimmed.equals(":::")) {
                throw error("UNEXPECTED_DIRECTIVE_END", resource, index + 1, "Unexpected directive terminator");
            } else {
                if (markdown.isEmpty()) markdownStart = index + 1;
                markdown.add(line);
            }
        }
        flushMarkdown(blocks, markdown, resource, markdownStart);
        return List.copyOf(blocks);
    }

    private static int findClosing(String[] lines, int start, String resource, int openingLine) {
        for (int index = start; index < lines.length; index++) {
            if (lines[index].trim().equals(":::")) return index;
        }
        throw error("UNCLOSED_DIRECTIVE", resource, openingLine, "Documentation directive is not closed");
    }

    private static void flushMarkdown(List<DocumentationBlock> blocks, List<String> markdown, String resource, int line) {
        if (markdown.isEmpty()) return;
        String content = String.join("\n", markdown).strip();
        markdown.clear();
        if (!content.isBlank()) blocks.add(new DocumentationBlock("markdown", content, sourceAttributes(resource, line)));
    }

    private static DocumentationBlock block(String directive, String argument, String content, String resource, int line) {
        Map<String, Object> attributes = sourceAttributes(resource, line);
        switch (directive) {
            case "notice" -> {
                String level = argument == null ? "info" : argument.toLowerCase();
                if (!List.of("info", "warning", "danger").contains(level)) {
                    throw error("INVALID_NOTICE_LEVEL", resource, line, "Unknown notice level: " + argument);
                }
                attributes.put("level", level);
                return new DocumentationBlock("notice", content, attributes);
            }
            case "diagram" -> {
                if (argument == null || argument.isBlank()) {
                    throw error("MISSING_DIAGRAM_RENDERER", resource, line, "A diagram renderer is required");
                }
                attributes.put("renderer", argument.toLowerCase());
                return new DocumentationBlock("diagram", content, attributes);
            }
            case "slot" -> {
                if (argument == null || !DocumentationComposer.SLOTS.contains(argument)) {
                    throw error("INVALID_SLOT", resource, line, "Unknown or missing documentation slot: " + argument);
                }
                attributes.put("name", argument);
                attributes.put("generated", false);
                return new DocumentationBlock("slot", "", attributes);
            }
            case "endpoint" -> {
                if (!content.isBlank()) {
                    throw error("INVALID_ENDPOINT_REFERENCE", resource, line,
                            "An endpoint directive cannot contain a body");
                }
                String[] parts = argument == null ? new String[0] : argument.trim().split("\\s+", 2);
                if (parts.length != 2 || !parts[0].matches("[A-Za-z]+") || !parts[1].startsWith("/")) {
                    throw error("INVALID_ENDPOINT_REFERENCE", resource, line,
                            "Use an endpoint directive in the form ::: endpoint METHOD /path");
                }
                attributes.put("method", parts[0].toUpperCase());
                attributes.put("path", parts[1]);
                return new DocumentationBlock("endpoint", "", attributes);
            }
            default -> throw error("UNKNOWN_DIRECTIVE", resource, line, "Unknown documentation directive: " + directive);
        }
    }

    private static Map<String, Object> sourceAttributes(String resource, int line) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("source", resource);
        attributes.put("line", line);
        return attributes;
    }

    private static DocumentationException error(String type, String resource, Integer line, String message) {
        return new DocumentationException(type, resource, line, message);
    }
}
