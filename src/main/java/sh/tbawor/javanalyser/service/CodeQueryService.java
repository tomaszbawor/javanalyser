package sh.tbawor.javanalyser.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sh.tbawor.javanalyser.exception.QueryException;
import sh.tbawor.javanalyser.model.CodeQueryRequest;
import sh.tbawor.javanalyser.model.DependencyGraph;
import sh.tbawor.javanalyser.service.prompt.PromptBuilder;
import sh.tbawor.javanalyser.service.query.QueryResult;
import sh.tbawor.javanalyser.service.query.QueryStrategy;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Service for handling code queries and interacting with the LLM.
 * This class implements the Strategy pattern to select the appropriate query execution
 * strategy based on the request type, and the Builder pattern for prompt generation.
 * 
 * <p>The service flow works as follows:
 * <ol>
 *   <li>Obtain the dependency graph from AstService</li>
 *   <li>Select the appropriate query strategy based on request parameters</li>
 *   <li>Execute the strategy to obtain formatted results</li>
 *   <li>Generate a prompt from the results using PromptBuilder</li>
 *   <li>Submit the prompt to the LLM and return its response</li>
 * </ol>
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
   * Uses the Strategy pattern to select the appropriate query strategy based
   * on the request parameters.
   * 
   * @param request The code query request containing query text and parameters
   * @return The natural language response from the LLM
   * @throws QueryException if no suitable strategy is found or query execution fails
   */
  public String queryCode(CodeQueryRequest request) {
    if (request == null) {
      throw new QueryException("Query request cannot be null");
    }
    
    log.debug("Processing code query: {}", request.getQuery());
    
    try {
      // Get the dependency graph
      DependencyGraph graph = astService.getDependencyGraph();
      if (graph == null || graph.getNodes().isEmpty()) {
        log.warn("Dependency graph is empty. Results may be limited.");
      }

      // Select the appropriate strategy based on the request
      QueryStrategy strategy = selectQueryStrategy(request);
      log.debug("Selected query strategy: {}", strategy.getClass().getSimpleName());

      // Execute the strategy
      QueryResult result = strategy.execute(request, graph);

      // Generate prompt for LLM
      String prompt = generatePrompt(request, result.getFormattedGraph(), result.getSourceCodeContext());

      // Query the LLM
      log.debug("Sending prompt to LLM with max tokens: {}", request.getMaxTokens());
      return llmService.query(prompt, request.getMaxTokens());
    } catch (NoSuchElementException e) {
      log.error("No appropriate query strategy found for request", e);
      throw new QueryException("No appropriate query strategy found for this request", e);
    } catch (Exception e) {
      log.error("Error processing code query", e);
      throw new QueryException("Failed to process code query", e);
    }
  }

  /**
   * Selects the appropriate query strategy from the available strategies
   * based on the request parameters.
   * 
   * @param request The code query request
   * @return The selected query strategy
   * @throws NoSuchElementException if no suitable strategy is found
   */
  private QueryStrategy selectQueryStrategy(CodeQueryRequest request) {
    return queryStrategies.stream()
        .filter(strategy -> strategy.canHandle(request))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("No strategy found for request"));
  }

  /**
   * Generates a prompt for the LLM based on the query request and structured results.
   * Uses the Builder pattern via PromptBuilder for clean, modular prompt construction.
   * 
   * @param request The original query request
   * @param formattedGraph The formatted dependency graph information
   * @param sourceCodeContext The relevant source code context
   * @return The assembled prompt for the LLM
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
