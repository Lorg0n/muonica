package io.muonica.core.model.documentation;

import java.util.List;

/** A standalone authored documentation page displayed alongside endpoint reference groups. */
public record ApiDocumentationPage(String title, List<DocumentationBlock> blocks, List<DocumentationWarning> warnings) {
    public ApiDocumentationPage {
        blocks = List.copyOf(blocks);
        warnings = List.copyOf(warnings);
    }
}
