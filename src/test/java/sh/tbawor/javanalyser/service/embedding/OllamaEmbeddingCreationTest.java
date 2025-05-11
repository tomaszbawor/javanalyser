package sh.tbawor.javanalyser.service.embedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.model.DependencyGraph;
import sh.tbawor.javanalyser.model.VectorEmbedding;
import sh.tbawor.javanalyser.repository.VectorEmbeddingRepository;
import sh.tbawor.javanalyser.util.VectorUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Tests for the OllamaEmbeddingCreation class.
 */
@ExtendWith(MockitoExtension.class)
public class OllamaEmbeddingCreationTest {

    @Mock
    private VectorEmbeddingRepository repository;

    @Mock
    private OllamaEmbeddingStrategy embeddingStrategy;

    @Mock
    private VectorUtil vectorUtil;

    private OllamaEmbeddingCreation embeddingCreation;

    @BeforeEach
    void setUp() {
        embeddingCreation = new OllamaEmbeddingCreation(
                repository,
                embeddingStrategy,
                vectorUtil,
                10, // batchSize
                100, // maxEmbeddings
                5 // progressLogInterval
        );
    }

    @Test
    void testCreateEmbeddings() {
        // Given
        DependencyGraph graph = new DependencyGraph();
        List<AstNode> nodes = createTestNodes(15); // Create 15 test nodes
        nodes.forEach(graph::addNode);

        // Mock the embedding strategy
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingStrategy.generateEmbedding(anyString())).thenReturn(embedding);

        // Mock the vector util
        byte[] embeddingBytes = new byte[]{1, 2, 3};
        when(vectorUtil.floatArrayToByteArray(embedding)).thenReturn(embeddingBytes);

        // When
        embeddingCreation.createEmbeddings(graph);

        // Then
        // Verify repository was cleared
        verify(repository).deleteAll();

        // Verify embeddings were created for each node
        verify(embeddingStrategy, times(15)).generateEmbedding(anyString());
        verify(vectorUtil, times(15)).floatArrayToByteArray(embedding);
        verify(repository, times(15)).save(any(VectorEmbedding.class));
    }

    @Test
    void testCreateEmbeddingsWithMaxLimit() {
        // Given
        DependencyGraph graph = new DependencyGraph();
        List<AstNode> nodes = createTestNodes(150); // Create 150 test nodes, but max is 100
        nodes.forEach(graph::addNode);

        // Mock the embedding strategy
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingStrategy.generateEmbedding(anyString())).thenReturn(embedding);

        // Mock the vector util
        byte[] embeddingBytes = new byte[]{1, 2, 3};
        when(vectorUtil.floatArrayToByteArray(embedding)).thenReturn(embeddingBytes);

        // When
        embeddingCreation.createEmbeddings(graph);

        // Then
        // Verify repository was cleared
        verify(repository).deleteAll();

        // Verify embeddings were created up to the max limit
        verify(embeddingStrategy, times(100)).generateEmbedding(anyString());
        verify(vectorUtil, times(100)).floatArrayToByteArray(embedding);
        verify(repository, times(100)).save(any(VectorEmbedding.class));
    }

    @Test
    void testCreateEmbeddingForNode() {
        // Given
        AstNode node = createTestNode("TestClass", "test.package", "class");

        // Mock the embedding strategy
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingStrategy.generateEmbedding(anyString())).thenReturn(embedding);

        // Mock the vector util
        byte[] embeddingBytes = new byte[]{1, 2, 3};
        when(vectorUtil.floatArrayToByteArray(embedding)).thenReturn(embeddingBytes);

        // When
        boolean result = embeddingCreation.createEmbeddingForNode(node);

        // Then
        verify(embeddingStrategy).generateEmbedding(anyString());
        verify(vectorUtil).floatArrayToByteArray(embedding);
        verify(repository).save(any(VectorEmbedding.class));
        assert(result);
    }

    @Test
    void testCreateEmbeddingForNodeHandlesException() {
        // Given
        AstNode node = createTestNode("TestClass", "test.package", "class");

        // Mock the embedding strategy to throw an exception
        when(embeddingStrategy.generateEmbedding(anyString())).thenThrow(new RuntimeException("Test exception"));

        // When
        boolean result = embeddingCreation.createEmbeddingForNode(node);

        // Then
        verify(embeddingStrategy).generateEmbedding(anyString());
        verify(repository, never()).save(any(VectorEmbedding.class));
        assert(!result);
    }

    private List<AstNode> createTestNodes(int count) {
        List<AstNode> nodes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            nodes.add(createTestNode("Class" + i, "test.package", "class"));
        }
        return nodes;
    }

    private AstNode createTestNode(String name, String packageName, String type) {
        return AstNode.builder()
                .name(name)
                .packageName(packageName)
                .type(type)
                .filePath("path/to/" + name + ".java")
                .sourceCode("public class " + name + " {}")
                .build();
    }
}