package io.muonica.spring.documentation;

import io.muonica.core.annotation.MuonicaDocumentation;
import io.muonica.core.model.DocumentationBlock;
import io.muonica.core.model.DocumentationWarning;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.core.env.Environment;

/** Resolves inline annotations and cached Markdown resources. */
public final class DocumentationResolver {
    private final DocumentationFileLoader fileLoader;
    private final DocumentationParser parser;
    private final boolean strict;
    private final ConcurrentMap<String, List<DocumentationBlock>> parsedFiles = new ConcurrentHashMap<>();

    public DocumentationResolver(DocumentationFileLoader fileLoader, DocumentationParser parser, Environment environment) {
        this.fileLoader = fileLoader;
        this.parser = parser;
        this.strict = environment.getProperty("muonica.documentation.strict", Boolean.class, true);
    }

    public DocumentationResolution resolve(AnnotatedElement source) {
        if (source == null) return DocumentationResolution.empty();
        List<DocumentationBlock> blocks = new ArrayList<>();
        List<DocumentationWarning> warnings = new ArrayList<>();
        for (MuonicaDocumentation annotation : source.getAnnotationsByType(MuonicaDocumentation.class)) {
            try {
                List<DocumentationBlock> resolved = resolveAnnotation(annotation, source);
                List<DocumentationBlock> pending = new ArrayList<>();
                for (DocumentationBlock block : resolved) {
                    if (block.type().equals("slot") && (blocks.stream().anyMatch(existing -> isSameSlot(existing, block))
                            || pending.stream().anyMatch(existing -> isSameSlot(existing, block)))) {
                        throw new DocumentationException("DUPLICATE_SLOT", source(block, sourceName(source)), line(block),
                                "Duplicate slot '" + block.attributes().get("name") + "'");
                    }
                    pending.add(block);
                }
                blocks.addAll(pending);
            } catch (DocumentationException exception) {
                if (strict) throw exception;
                warnings.add(new DocumentationWarning(exception.type(), exception.resource(), exception.line(), exception.getMessage()));
            }
        }
        return new DocumentationResolution(blocks, warnings);
    }

    private List<DocumentationBlock> resolveAnnotation(MuonicaDocumentation annotation, AnnotatedElement source) {
        boolean hasContent = !annotation.content().isBlank();
        boolean hasFile = !annotation.file().isBlank();
        String sourceName = sourceName(source);
        if (hasContent == hasFile) {
            throw new DocumentationException("INVALID_SOURCE", sourceName, null,
                    "Exactly one of MuonicaDocumentation.content or file must be specified");
        }
        if (hasFile) {
            if (annotation.type() != MuonicaDocumentation.Type.MARKDOWN) {
                throw new DocumentationException("INVALID_FILE_TYPE", annotation.file(), null,
                        "Documentation files must use type MARKDOWN");
            }
            return parsedFiles.computeIfAbsent(annotation.file(), location -> parser.parse(fileLoader.load(location), location));
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("source", sourceName);
        if (!annotation.title().isBlank()) attributes.put("title", annotation.title());
        if (!annotation.language().isBlank()) attributes.put("language", annotation.language());
        if (annotation.type() == MuonicaDocumentation.Type.NOTICE) attributes.put("level", annotation.noticeLevel().name().toLowerCase());
        if (annotation.type() == MuonicaDocumentation.Type.MERMAID) {
            attributes.put("renderer", "mermaid");
            return List.of(new DocumentationBlock("diagram", annotation.content(), attributes));
        }
        return List.of(new DocumentationBlock(annotation.type().name().toLowerCase(), annotation.content(), attributes));
    }

    private static boolean isSameSlot(DocumentationBlock left, DocumentationBlock right) {
        return left.type().equals("slot") && right.type().equals("slot")
                && left.attributes().get("name").equals(right.attributes().get("name"));
    }

    private static Integer line(DocumentationBlock block) {
        Object line = block.attributes().get("line");
        return line instanceof Integer value ? value : null;
    }

    private static String source(DocumentationBlock block, String fallback) {
        Object source = block.attributes().get("source");
        return source == null ? fallback : source.toString();
    }

    private static String sourceName(AnnotatedElement source) {
        if (source instanceof Class<?> type) return type.getName();
        if (source instanceof Method method) return method.toGenericString();
        return source.toString();
    }
}
