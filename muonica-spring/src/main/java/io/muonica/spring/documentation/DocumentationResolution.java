package io.muonica.spring.documentation;

import io.muonica.core.model.documentation.DocumentationBlock;
import io.muonica.core.model.documentation.DocumentationWarning;
import java.util.List;

/** Raw documentation blocks and non-fatal diagnostics for one annotated source. */
public record DocumentationResolution(List<DocumentationBlock> blocks, List<DocumentationWarning> warnings) {
    public DocumentationResolution {
        blocks = List.copyOf(blocks);
        warnings = List.copyOf(warnings);
    }

    public static DocumentationResolution empty() {
        return new DocumentationResolution(List.of(), List.of());
    }
}
