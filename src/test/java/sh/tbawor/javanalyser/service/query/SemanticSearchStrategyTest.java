package sh.tbawor.javanalyser.service.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.model.CodeQueryRequest;
import sh.tbawor.javanalyser.model.DependencyGraph;
import sh.tbawor.javanalyser.model.VectorEmbedding;
import sh.tbawor.javanalyser.service.VectorEmbeddingService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for the SemanticSearchStrategy class.
 */
@ExtendWith(MockitoExtension.class)
public class SemanticSearchStrategyTest {

    @Mock
    private VectorEmbeddingService vectorEmbeddingService;

    private SemanticSearchStrategy strategy;
    private DependencyGraph graph;

    @BeforeEach
    void setUp() {
        strategy = new SemanticSearchStrategy(vectorEmbeddingService);
        graph = new DependencyGraph();

        // Add a node to the graph that will match one of our test embeddings
        AstNode node = AstNode.builder()
            .name("TestClass")
            .packageName("test.package")
            .type("class")
            .sourceCode("public class TestClass {}")
            .build();
        graph.addNode(node);
    }

    @Test
    void testCanHandle() {
        // Given
        CodeQueryRequest request1 = new CodeQueryRequest();
        request1.setUseSemanticSearch(true);

        CodeQueryRequest request2 = new CodeQueryRequest();
        request2.setUseSemanticSearch(false);

        // When & Then
        assertThat(strategy.canHandle(request1)).isTrue();
        assertThat(strategy.canHandle(request2)).isFalse();
    }

    @Test
    void testExecuteWithNoResults() {
        // Given
        CodeQueryRequest request = new CodeQueryRequest();
        request.setQuery("test query");
        request.setMaxResults(5);

        when(vectorEmbeddingService.semanticSearch("test query", 5, null))
            .thenReturn(Collections.emptyList());

        // When
        QueryResult result = strategy.execute(request, graph);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFormattedGraph()).contains("Semantic search results for the query");
        assertThat(result.getSourceCodeContext()).isEqualTo("Source code snippets from relevant components:\n\n");

        verify(vectorEmbeddingService).semanticSearch("test query", 5, null);
    }

    @Test
    void testExecuteWithResultsNoSourceCode() {
        // Given
        CodeQueryRequest request = new CodeQueryRequest();
        request.setQuery("test query");
        request.setMaxResults(5);
        request.setIncludeSourceCode(false);

        List<VectorEmbedding> embeddings = Arrays.asList(
            createEmbedding("test.package.TestClass", "class", "TestClass", "test.package", "Test class description"),
            createEmbedding("test.package.OtherClass", "class", "OtherClass", "test.package", "Other class description")
        );

        when(vectorEmbeddingService.semanticSearch("test query", 5, null))
            .thenReturn(embeddings);

        // When
        QueryResult result = strategy.execute(request, graph);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFormattedGraph()).contains("Semantic search results for the query");
        assertThat(result.getFormattedGraph()).contains("TestClass");
        assertThat(result.getFormattedGraph()).contains("OtherClass");
        assertThat(result.getFormattedGraph()).contains("Test class description");
        assertThat(result.getFormattedGraph()).contains("Other class description");
        assertThat(result.getSourceCodeContext()).isEmpty();

        verify(vectorEmbeddingService).semanticSearch("test query", 5, null);
    }

    @Test
    void testExecuteWithResultsAndSourceCode() {
        // Given
        CodeQueryRequest request = new CodeQueryRequest();
        request.setQuery("test query");
        request.setMaxResults(5);
        request.setIncludeSourceCode(true);

        List<VectorEmbedding> embeddings = Arrays.asList(
            createEmbedding("test.package.TestClass", "class", "TestClass", "test.package", "Test class description"),
            createEmbedding("test.package.OtherClass", "class", "OtherClass", "test.package", "Other class description")
        );

        when(vectorEmbeddingService.semanticSearch("test query", 5, null))
            .thenReturn(embeddings);

        // When
        QueryResult result = strategy.execute(request, graph);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFormattedGraph()).contains("Semantic search results for the query");
        assertThat(result.getSourceCodeContext()).contains("Source code snippets from relevant components");
        assertThat(result.getSourceCodeContext()).contains("TestClass");
        assertThat(result.getSourceCodeContext()).contains("OtherClass");
        assertThat(result.getSourceCodeContext()).contains("public class TestClass {}");
        assertThat(result.getSourceCodeContext()).contains("(Source code not available)");

        verify(vectorEmbeddingService).semanticSearch("test query", 5, null);
    }

    @Test
    void testExecuteWithContextFilter() {
        // Given
        CodeQueryRequest request = new CodeQueryRequest();
        request.setQuery("test query");
        request.setMaxResults(5);
        request.setContext("test.package");

        when(vectorEmbeddingService.semanticSearch("test query", 5, "test.package"))
            .thenReturn(Collections.emptyList());

        // When
        QueryResult result = strategy.execute(request, graph);

        // Then
        assertThat(result).isNotNull();
        verify(vectorEmbeddingService).semanticSearch("test query", 5, "test.package");
    }

    private VectorEmbedding createEmbedding(String nodeKey, String type, String name, String packageName, String description) {
        return VectorEmbedding.builder()
            .nodeKey(nodeKey)
            .type(type)
            .name(name)
            .packageName(packageName)
            .description(description)
            .filePath("path/to/" + name + ".java")
            .sourceCodeSnippet(type.equals("class") && name.equals("TestClass") ? "public class TestClass {}" : null)
            .embedding(new byte[]{1, 2, 3})
            .build();
    }
}
