package sh.tbawor.javanalyser.service.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sh.tbawor.javanalyser.model.CodeQueryRequest;
import sh.tbawor.javanalyser.model.DependencyGraph;
import sh.tbawor.javanalyser.service.AstFormatter;
import sh.tbawor.javanalyser.service.SourceCodeService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for the TraditionalContextStrategy class.
 */
@ExtendWith(MockitoExtension.class)
public class TraditionalContextStrategyTest {

    @Mock
    private AstFormatter astFormatter;

    @Mock
    private SourceCodeService sourceCodeService;

    private TraditionalContextStrategy strategy;
    private DependencyGraph graph;

    @BeforeEach
    void setUp() {
        strategy = new TraditionalContextStrategy(astFormatter, sourceCodeService);
        graph = new DependencyGraph();
    }

    @Test
    void testCanHandle() {
        // Given
        CodeQueryRequest request1 = new CodeQueryRequest();
        request1.setUseSemanticSearch(false);
        
        CodeQueryRequest request2 = new CodeQueryRequest();
        request2.setUseSemanticSearch(true);
        
        // When & Then
        assertThat(strategy.canHandle(request1)).isTrue();
        assertThat(strategy.canHandle(request2)).isFalse();
    }

    @Test
    void testExecuteWithContextAndSourceCode() {
        // Given
        CodeQueryRequest request = new CodeQueryRequest();
        request.setContext("test.package");
        request.setIncludeSourceCode(true);
        
        when(astFormatter.formatFilteredGraph(graph, "test.package"))
            .thenReturn("Filtered graph for test.package");
        
        when(sourceCodeService.getSourceCodeForContext("test.package", graph))
            .thenReturn("Source code for test.package");
        
        // When
        QueryResult result = strategy.execute(request, graph);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFormattedGraph()).isEqualTo("Filtered graph for test.package");
        assertThat(result.getSourceCodeContext()).isEqualTo("Source code for test.package");
        
        verify(astFormatter).formatFilteredGraph(graph, "test.package");
        verify(sourceCodeService).getSourceCodeForContext("test.package", graph);
    }

    @Test
    void testExecuteWithContextNoSourceCode() {
        // Given
        CodeQueryRequest request = new CodeQueryRequest();
        request.setContext("test.package");
        request.setIncludeSourceCode(false);
        
        when(astFormatter.formatFilteredGraph(graph, "test.package"))
            .thenReturn("Filtered graph for test.package");
        
        // When
        QueryResult result = strategy.execute(request, graph);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFormattedGraph()).isEqualTo("Filtered graph for test.package");
        assertThat(result.getSourceCodeContext()).isEmpty();
        
        verify(astFormatter).formatFilteredGraph(graph, "test.package");
        verifyNoInteractions(sourceCodeService);
    }

    @Test
    void testExecuteWithoutContextWithSourceCode() {
        // Given
        CodeQueryRequest request = new CodeQueryRequest();
        request.setIncludeSourceCode(true);
        
        when(astFormatter.formatGraph(graph))
            .thenReturn("Complete graph");
        
        when(sourceCodeService.getSourceCodeHighlights(graph))
            .thenReturn("Source code highlights");
        
        // When
        QueryResult result = strategy.execute(request, graph);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFormattedGraph()).isEqualTo("Complete graph");
        assertThat(result.getSourceCodeContext()).isEqualTo("Source code highlights");
        
        verify(astFormatter).formatGraph(graph);
        verify(sourceCodeService).getSourceCodeHighlights(graph);
    }

    @Test
    void testExecuteWithoutContextNoSourceCode() {
        // Given
        CodeQueryRequest request = new CodeQueryRequest();
        request.setIncludeSourceCode(false);
        
        when(astFormatter.formatGraph(graph))
            .thenReturn("Complete graph");
        
        // When
        QueryResult result = strategy.execute(request, graph);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFormattedGraph()).isEqualTo("Complete graph");
        assertThat(result.getSourceCodeContext()).isEmpty();
        
        verify(astFormatter).formatGraph(graph);
        verifyNoInteractions(sourceCodeService);
    }

    @Test
    void testExecuteWithEmptyContext() {
        // Given
        CodeQueryRequest request = new CodeQueryRequest();
        request.setContext("");
        
        when(astFormatter.formatGraph(graph))
            .thenReturn("Complete graph");
        
        // When
        QueryResult result = strategy.execute(request, graph);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFormattedGraph()).isEqualTo("Complete graph");
        
        verify(astFormatter).formatGraph(graph);
    }
}