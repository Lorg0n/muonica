package io.muonica.spring.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.muonica.core.model.DocumentationBlock;
import io.muonica.core.model.DocumentationOrigin;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;

class DocumentationCompositionTest {
    private final DocumentationParser parser = new DocumentationParser();

    @Test
    void preservesMarkdownAndSlotOrder() {
        List<DocumentationBlock> blocks = parser.parse("# User\n\n:::slot request\n:::\n\nDescription after request\n\n:::slot responses\n:::",
                "classpath:/muonica/users/get-user.md");

        assertEquals(List.of("markdown", "slot", "markdown", "slot"), blocks.stream().map(DocumentationBlock::type).toList());
        assertEquals(List.of("request", "responses"), blocks.stream().filter(block -> block.type().equals("slot"))
                .map(block -> block.attributes().get("name")).toList());
        assertEquals(1, blocks.get(0).attributes().get("line"));
        assertEquals(3, blocks.get(1).attributes().get("line"));
    }

    @Test
    void resolvesNearestSlotsAndAppendsOnlyMissingGeneratedSlots() {
        DocumentationResolution project = resolution(
                markdown("Project"), slot("security"));
        DocumentationResolution group = resolution(
                markdown("Group"), slot("responses"));
        DocumentationResolution endpoint = resolution(
                markdown("Endpoint"), slot("request"));

        DocumentationResolution result = new DocumentationComposer().compose(project, group, endpoint);

        assertEquals(List.of("Project", "security", "Group", "responses", "Endpoint", "request", "parameters"),
                result.blocks().stream().filter(block -> block.type().equals("markdown") || block.type().equals("slot"))
                        .map(block -> block.type().equals("markdown") ? block.content() : block.attributes().get("name"))
                        .toList());
        assertEquals(List.of("security", "responses", "request", "parameters"), result.blocks().stream()
                .filter(block -> block.type().equals("slot"))
                .map(block -> block.attributes().get("name")).toList());
        assertEquals(DocumentationOrigin.GENERATED, result.blocks().get(result.blocks().size() - 1).origin());
        assertTrue(result.blocks().get(result.blocks().size() - 1).attributes().get("generated").equals(true));
    }

    @Test
    void endpointSlotOverridesInheritedSlotAtEndpointPosition() {
        DocumentationResolution project = resolution(slot("request"));
        DocumentationResolution group = resolution(slot("request"));
        DocumentationResolution endpoint = resolution(markdown("Before"), slot("request"), markdown("After"));

        DocumentationResolution result = new DocumentationComposer().compose(project, group, endpoint);

        assertEquals(List.of("Before", "request", "After", "responses", "parameters", "security"), result.blocks().stream()
                .map(block -> block.type().equals("slot") ? block.attributes().get("name") : block.content()).toList());
        assertEquals(DocumentationOrigin.USER, result.blocks().get(1).origin());
    }

    @Test
    void duplicateSlotsInOneScopeAreRejected() {
        DocumentationResolution duplicate = resolution(slot("request"), slot("request"));

        assertThrows(DocumentationException.class, () -> new DocumentationComposer().compose(duplicate,
                DocumentationResolution.empty(), DocumentationResolution.empty()));
    }

    @Test
    void nonStrictResolverReturnsWarningForMissingResource() {
        MockEnvironment environment = new MockEnvironment().withProperty("muonica.documentation.strict", "false");
        DocumentationResolver resolver = new DocumentationResolver(new DocumentationFileLoader(new DefaultResourceLoader()), parser, environment);

        DocumentationResolution result = resolver.resolve(MissingDocumentationSource.class);

        assertTrue(result.blocks().isEmpty());
        assertEquals("MISSING_RESOURCE", result.warnings().get(0).type());
        assertEquals("classpath:/missing-doc.md", result.warnings().get(0).resource());
    }

    @Test
    void nonStrictResolverDiscardsAnInvalidSourceAtomically() {
        MockEnvironment environment = new MockEnvironment().withProperty("muonica.documentation.strict", "false");
        DocumentationResolver resolver = new DocumentationResolver(new DocumentationFileLoader(new DefaultResourceLoader()), parser, environment);

        DocumentationResolution result = resolver.resolve(DuplicateSlotDocumentationSource.class);

        assertTrue(result.blocks().isEmpty());
        assertEquals("DUPLICATE_SLOT", result.warnings().get(0).type());
        assertEquals("classpath:/duplicate-slots.md", result.warnings().get(0).resource());
    }

    private static DocumentationResolution resolution(DocumentationBlock... blocks) {
        return new DocumentationResolution(List.of(blocks), List.of());
    }

    private static DocumentationBlock markdown(String content) {
        return new DocumentationBlock("markdown", content, Map.of("source", "test.md"));
    }

    private static DocumentationBlock slot(String name) {
        return new DocumentationBlock("slot", "", Map.of("name", name, "generated", false));
    }

    @io.muonica.core.annotation.MuonicaDocumentation(file = "classpath:/missing-doc.md")
    private static final class MissingDocumentationSource { }

    @io.muonica.core.annotation.MuonicaDocumentation(file = "classpath:/duplicate-slots.md")
    private static final class DuplicateSlotDocumentationSource { }
}
