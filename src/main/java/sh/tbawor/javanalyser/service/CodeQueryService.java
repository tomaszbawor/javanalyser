package sh.tbawor.javanalyser.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.model.CodeQueryRequest;
import sh.tbawor.javanalyser.model.DependencyGraph;
import sh.tbawor.javanalyser.model.VectorEmbedding;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeQueryService {

  private final AstService astService;
  private final LocalLlmService llmService;
  private final AstFormatter astFormatter;
  private final VectorEmbeddingService vectorEmbeddingService;
  private final SourceCodeService sourceCodeService;

  public String queryCode(CodeQueryRequest request) {
    DependencyGraph graph = astService.getDependencyGraph();

    // Default context and source code
    String formattedGraph;
    String sourceCodeContext = "";

    // Use semantic search if enabled
    if (request.isUseSemanticSearch()) {
      List<VectorEmbedding> relevantResults = vectorEmbeddingService.semanticSearch(
          request.getQuery(),
          request.getMaxResults(),
          request.getContext());

      // Format results
      formattedGraph = formatSemanticResults(relevantResults);

      // Get source code if requested
      if (request.isIncludeSourceCode()) {
        sourceCodeContext = getSourceCodeFromResults(relevantResults, graph);
      }
    } else {
      // Traditional context-based filtering
      if (request.getContext() != null && !request.getContext().isEmpty()) {
        // If context is provided, filter the graph
        formattedGraph = astFormatter.formatFilteredGraph(graph, request.getContext());

        // Get source code if requested
        if (request.isIncludeSourceCode()) {
          sourceCodeContext = sourceCodeService.getSourceCodeForContext(request.getContext(), graph);
        }
      } else {
        formattedGraph = astFormatter.formatGraph(graph);

        // Get source code for top-level classes if requested
        if (request.isIncludeSourceCode()) {
          sourceCodeContext = sourceCodeService.getSourceCodeHighlights(graph);
        }
      }
    }

    // Generate prompt for LLM
    String prompt = generatePrompt(request, formattedGraph, sourceCodeContext);

    // Query the LLM
    return llmService.query(prompt, request.getMaxTokens());
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

  private String generatePrompt(CodeQueryRequest request, String formattedGraph, String sourceCodeContext) {
    StringBuilder promptBuilder = new StringBuilder();

    promptBuilder.append("You are an expert Java developer assistant. ");
    promptBuilder.append("Analyze the following code dependency information and answer the query.\n\n");

    promptBuilder.append("Code Dependency Information:\n");
    promptBuilder.append(formattedGraph);

    // Add source code if provided
    if (!sourceCodeContext.isEmpty()) {
      promptBuilder.append("\n\nRelevant Source Code:\n");
      promptBuilder.append(sourceCodeContext);
    }

    promptBuilder.append("\n\nUser Query: ").append(request.getQuery());

    return promptBuilder.toString();
  }
}
