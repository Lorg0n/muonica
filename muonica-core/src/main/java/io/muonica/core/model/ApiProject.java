package io.muonica.core.model;

import java.util.List;

/** Root of a Muonica documentation tree. */
public record ApiProject(String name, List<ApiGroup> groups, List<DocumentationBlock> documentationBlocks) {
    public ApiProject {
        groups = List.copyOf(groups);
        documentationBlocks = List.copyOf(documentationBlocks);
    }
}
