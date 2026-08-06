package io.muonica.spring.documentation;

import io.muonica.core.model.DocumentationBlock;
import io.muonica.core.model.DocumentationOrigin;
import io.muonica.core.model.DocumentationWarning;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Performs position-aware inheritance and generated slot fallback. */
public final class DocumentationComposer {
    public static final Set<String> SLOTS = Set.of("request", "responses", "parameters", "security");
    private static final List<String> GENERATED_ORDER = List.of("request", "responses", "parameters", "security");

    public DocumentationResolution compose(DocumentationResolution project, DocumentationResolution group,
            DocumentationResolution endpoint) {
        List<Scope> scopes = List.of(new Scope(project.blocks(), 0), new Scope(group.blocks(), 1), new Scope(endpoint.blocks(), 2));
        Map<String, SlotPosition> owners = new LinkedHashMap<>();
        for (Scope scope : scopes) {
            Map<String, Integer> localSlots = new LinkedHashMap<>();
            for (int index = 0; index < scope.blocks().size(); index++) {
                DocumentationBlock block = scope.blocks().get(index);
                if (!block.type().equals("slot")) continue;
                String name = String.valueOf(block.attributes().get("name"));
                if (localSlots.putIfAbsent(name, index) != null) {
                    throw new DocumentationException("DUPLICATE_SLOT", source(block), line(block), "Duplicate slot '" + name + "'");
                }
                owners.put(name, new SlotPosition(scope.level(), index, block));
            }
        }

        List<DocumentationBlock> result = new ArrayList<>();
        for (Scope scope : scopes) {
            for (int index = 0; index < scope.blocks().size(); index++) {
                DocumentationBlock block = scope.blocks().get(index);
                if (block.type().equals("slot")) {
                    SlotPosition owner = owners.get(String.valueOf(block.attributes().get("name")));
                    if (owner.level() != scope.level() || owner.index() != index) continue;
                }
                result.add(scope.level() == 2 ? withOrigin(block, DocumentationOrigin.USER) : withOrigin(block, DocumentationOrigin.INHERITED));
            }
        }

        for (String slot : GENERATED_ORDER) {
            if (!owners.containsKey(slot)) {
                result.add(new DocumentationBlock("slot", "", Map.of("name", slot, "generated", true), DocumentationOrigin.GENERATED));
            }
        }
        List<DocumentationWarning> warnings = new ArrayList<>();
        warnings.addAll(project.warnings());
        warnings.addAll(group.warnings());
        warnings.addAll(endpoint.warnings());
        return new DocumentationResolution(result, warnings);
    }

    private static DocumentationBlock withOrigin(DocumentationBlock block, DocumentationOrigin origin) {
        return new DocumentationBlock(block.type(), block.content(), block.attributes(), origin);
    }

    private static String source(DocumentationBlock block) {
        return String.valueOf(block.attributes().getOrDefault("source", "documentation"));
    }

    private static Integer line(DocumentationBlock block) {
        Object line = block.attributes().get("line");
        return line instanceof Integer value ? value : null;
    }

    private record Scope(List<DocumentationBlock> blocks, int level) { }
    private record SlotPosition(int level, int index, DocumentationBlock block) { }
}
