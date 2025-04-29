package sh.tbawor.javanalyser.service.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import sh.tbawor.javanalyser.model.CodeQueryRequest;
import sh.tbawor.javanalyser.model.DependencyGraph;
import sh.tbawor.javanalyser.model.VectorEmbedding;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.service.VectorEmbeddingService;

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

    @Override
    public boolean canHandle(CodeQueryRequest request) {
        return request.isUseSemanticSearch();
    }

    @Override
    public QueryResult execute(CodeQueryRequest request, DependencyGraph graph) {
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
    }

    private String formatSemanticResults(List<VectorEmbedding> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("Semantic search results for the query:\n\n");

        for (int i = 0; i < results.size(); i++) {
            VectorEmbedding result = results.get(i);
            sb.append(i + 1).append(". ");
            sb.append("Type: ").append(result.getType()).append("\n");
            sb.append("   Name: ").append(result.getName()).append("\n");
            sb.append("   Package: ").append(result.getPackageName()).append("\n");
            sb.append("   File: ").append(result.getFilePath()).append("\n");
            sb.append("   Description: ").append(result.getDescription()).append("\n\n");
        }

        return sb.toString();
    }

    private String getSourceCodeFromResults(List<VectorEmbedding> results, DependencyGraph graph) {
        StringBuilder sb = new StringBuilder("Source code snippets from relevant components:\n\n");

        for (int i = 0; i < results.size(); i++) {
            VectorEmbedding result = results.get(i);
            sb.append(i + 1).append(". ");
            sb.append(result.getType()).append(": ").append(result.getName()).append("\n");

            // Add source code if available
            if (result.getSourceCodeSnippet() != null && !result.getSourceCodeSnippet().isEmpty()) {
                sb.append("```java\n");
                sb.append(result.getSourceCodeSnippet()).append("\n");
                sb.append("```\n\n");
            } else {
                // Try to get from graph if embedding doesn't have it
                String nodeKey = result.getNodeKey();
                AstNode node = graph.getNodeByKey(nodeKey);
                if (node != null && node.getSourceCode() != null && !node.getSourceCode().isEmpty()) {
                    sb.append("```java\n");
                    sb.append(node.getSourceCode()).append("\n");
                    sb.append("```\n\n");
                } else {
                    sb.append("(Source code not available)\n\n");
                }
            }
        }

        return sb.toString();
    }
}