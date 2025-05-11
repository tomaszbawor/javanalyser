package sh.tbawor.javanalyser.service.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import sh.tbawor.javanalyser.exception.QueryException;
import sh.tbawor.javanalyser.model.CodeQueryRequest;
import sh.tbawor.javanalyser.model.DependencyGraph;
import sh.tbawor.javanalyser.model.VectorEmbedding;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.service.VectorEmbeddingService;

import java.util.Collections;
import java.util.List;

/**
 * Strategy implementation for semantic search queries.
 * This is part of the Strategy pattern implementation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SemanticSearchStrategy implements QueryStrategy {

    private final VectorEmbeddingService vectorEmbeddingService;

    /**
     * Determines if this strategy can handle the given request
     *
     * @param request The query request to check
     * @return true if the request has useSemanticSearch set to true
     */
    @Override
    public boolean canHandle(CodeQueryRequest request) {
        return request != null && request.isUseSemanticSearch();
    }

    /**
     * Executes the semantic search strategy for the given request
     *
     * @param request The query request to process
     * @param graph The dependency graph to use for context
     * @return A QueryResult containing formatted results
     * @throws QueryException if an error occurs during query execution
     */
    @Override
    public QueryResult execute(CodeQueryRequest request, DependencyGraph graph) {
        if (request == null) {
            throw new QueryException("Cannot execute semantic search with null request");
        }
        
        if (graph == null) {
            throw new QueryException("Cannot execute semantic search with null dependency graph");
        }
        
        try {
            log.debug("Executing semantic search for query: {}", request.getQuery());
            
            List<VectorEmbedding> relevantResults = vectorEmbeddingService.semanticSearch(
                    request.getQuery(),
                    request.getMaxResults(),
                    request.getContext());

            // Format results
            String formattedGraph = formatSemanticResults(relevantResults);

            // Get source code if requested
            String sourceCodeContext = "";
            if (request.isIncludeSourceCode()) {
                sourceCodeContext = getSourceCodeFromResults(relevantResults, graph);
            }

            return QueryResult.builder()
                    .formattedGraph(formattedGraph)
                    .sourceCodeContext(sourceCodeContext)
                    .build();
        } catch (Exception e) {
            log.error("Error executing semantic search for query: {}", request.getQuery(), e);
            throw new QueryException("Failed to execute semantic search query", e);
        }
    }

    /**
     * Formats semantic search results into a readable string representation
     *
     * @param results The list of vector embeddings to format
     * @return A formatted string representation of the results
     */
    private String formatSemanticResults(List<VectorEmbedding> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("Semantic search results for the query:\n\n");
        
        if (results == null || results.isEmpty()) {
            sb.append("No semantic search results found.");
            return sb.toString();
        }

        for (int i = 0; i < results.size(); i++) {
            VectorEmbedding result = results.get(i);
            sb.append(i + 1).append(". ");
            sb.append("Type: ").append(result.getType()).append("\n");
            sb.append("   Name: ").append(result.getName()).append("\n");
            sb.append("   Package: ").append(result.getPackageName()).append("\n");
            sb.append("   File: ").append(result.getFilePath()).append("\n");
            sb.append("   Description: ").append(StringUtils.defaultString(result.getDescription(), "No description available"))
              .append("\n\n");
        }

        return sb.toString();
    }

    /**
     * Extracts source code from search results
     *
     * @param results The list of vector embeddings to extract source code from
     * @param graph The dependency graph to use for looking up source code
     * @return A formatted string containing source code snippets
     */
    private String getSourceCodeFromResults(List<VectorEmbedding> results, DependencyGraph graph) {
        StringBuilder sb = new StringBuilder("Source code snippets from relevant components:\n\n");
        
        if (results == null || results.isEmpty()) {
            return sb.toString();
        }

        for (int i = 0; i < results.size(); i++) {
            VectorEmbedding result = results.get(i);
            sb.append(i + 1).append(". ");
            sb.append(result.getType()).append(": ").append(result.getName()).append("\n");

            // Add source code if available in the embedding
            if (StringUtils.isNotEmpty(result.getSourceCodeSnippet())) {
                appendSourceCode(sb, result.getSourceCodeSnippet());
            } else {
                // Try to get from graph if embedding doesn't have it
                String nodeKey = result.getNodeKey();
                AstNode node = graph.getNodeByKey(nodeKey);
                if (node != null && StringUtils.isNotEmpty(node.getSourceCode())) {
                    appendSourceCode(sb, node.getSourceCode());
                } else {
                    sb.append("(Source code not available)\n\n");
                }
            }
        }

        return sb.toString();
    }
    
    /**
     * Helper method to append formatted source code to a StringBuilder
     */
    private void appendSourceCode(StringBuilder sb, String sourceCode) {
        sb.append("```java\n");
        sb.append(sourceCode).append("\n");
        sb.append("```\n\n");
    }
}