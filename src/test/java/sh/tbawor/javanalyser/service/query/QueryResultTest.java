package sh.tbawor.javanalyser.service.query;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the QueryResult class.
 */
public class QueryResultTest {

    @Test
    void testNoArgsConstructor() {
        // Given & When
        QueryResult result = new QueryResult();
        
        // Then
        assertThat(result.getFormattedGraph()).isNull();
        assertThat(result.getSourceCodeContext()).isNull();
    }
    
    @Test
    void testAllArgsConstructor() {
        // Given
        String formattedGraph = "formatted graph";
        String sourceCodeContext = "source code";
        
        // When
        QueryResult result = new QueryResult(formattedGraph, sourceCodeContext);
        
        // Then
        assertThat(result.getFormattedGraph()).isEqualTo(formattedGraph);
        assertThat(result.getSourceCodeContext()).isEqualTo(sourceCodeContext);
    }
    
    @Test
    void testBuilder() {
        // Given
        String formattedGraph = "formatted graph";
        String sourceCodeContext = "source code";
        
        // When
        QueryResult result = QueryResult.builder()
            .formattedGraph(formattedGraph)
            .sourceCodeContext(sourceCodeContext)
            .build();
        
        // Then
        assertThat(result.getFormattedGraph()).isEqualTo(formattedGraph);
        assertThat(result.getSourceCodeContext()).isEqualTo(sourceCodeContext);
    }
    
    @Test
    void testSetters() {
        // Given
        QueryResult result = new QueryResult();
        String formattedGraph = "formatted graph";
        String sourceCodeContext = "source code";
        
        // When
        result.setFormattedGraph(formattedGraph);
        result.setSourceCodeContext(sourceCodeContext);
        
        // Then
        assertThat(result.getFormattedGraph()).isEqualTo(formattedGraph);
        assertThat(result.getSourceCodeContext()).isEqualTo(sourceCodeContext);
    }
    
    @Test
    void testEqualsAndHashCode() {
        // Given
        QueryResult result1 = new QueryResult("graph1", "code1");
        QueryResult result2 = new QueryResult("graph1", "code1");
        QueryResult result3 = new QueryResult("graph2", "code2");
        
        // Then
        assertThat(result1).isEqualTo(result2);
        assertThat(result1).isNotEqualTo(result3);
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        assertThat(result1.hashCode()).isNotEqualTo(result3.hashCode());
    }
    
    @Test
    void testToString() {
        // Given
        QueryResult result = new QueryResult("graph", "code");
        
        // When
        String toString = result.toString();
        
        // Then
        assertThat(toString).contains("formattedGraph=graph");
        assertThat(toString).contains("sourceCodeContext=code");
    }
}