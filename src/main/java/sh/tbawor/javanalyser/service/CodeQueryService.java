package sh.tbawor.javanalyser.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sh.tbawor.javanalyser.model.CodeQueryRequest;
import sh.tbawor.javanalyser.model.DependencyGraph;
import sh.tbawor.javanalyser.service.prompt.PromptBuilder;
import sh.tbawor.javanalyser.service.query.QueryResult;
import sh.tbawor.javanalyser.service.query.QueryStrategy;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Service for handling code queries.
 * Uses the Strategy pattern to select the appropriate query strategy based on the request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CodeQueryService {

  private final AstService astService;
  private final LocalLlmService llmService;
  private final List<QueryStrategy> queryStrategies;

  /**
   * Processes a code query request and returns the response from the LLM.
   * Uses the Strategy pattern to select the appropriate query strategy.
   * 
   * @param request The query request
   * @return The response from the LLM
   */
  public String queryCode(CodeQueryRequest request) {
    DependencyGraph graph = astService.getDependencyGraph();

    // Select the appropriate strategy based on the request
    QueryStrategy strategy = queryStrategies.stream()
        .filter(s -> s.canHandle(request))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("No strategy found for request"));

    // Execute the strategy
    QueryResult result = strategy.execute(request, graph);

    // Generate prompt for LLM
    String prompt = generatePrompt(request, result.getFormattedGraph(), result.getSourceCodeContext());

    // Query the LLM
    return llmService.query(prompt, request.getMaxTokens());
  }

  /**
   * Generates a prompt for the LLM based on the query request and results.
   * Uses the Builder pattern via PromptBuilder.
   * 
   * @param request The query request
   * @param formattedGraph The formatted graph
   * @param sourceCodeContext The source code context
   * @return The prompt for the LLM
   */
  private String generatePrompt(CodeQueryRequest request, String formattedGraph, String sourceCodeContext) {
    return new PromptBuilder()
        .withPreamble("You are an expert Java developer assistant. " +
                      "Analyze the following code dependency information and answer the query.")
        .withDependencyInfo(formattedGraph)
        .withSourceCode(sourceCodeContext)
        .withQuery(request.getQuery())
        .build();
  }
}
