package sh.tbawor.javanalyser.service.query;

import sh.tbawor.javanalyser.model.CodeQueryRequest;
import sh.tbawor.javanalyser.model.DependencyGraph;

/**
 * Strategy interface for different query strategies.
 * This is part of the Strategy pattern implementation.
 */
public interface QueryStrategy {
    
    /**
     * Determines if this strategy can handle the given request.
     * 
     * @param request The query request
     * @return true if this strategy can handle the request, false otherwise
     */
    boolean canHandle(CodeQueryRequest request);
    
    /**
     * Executes the query strategy and returns the formatted graph and source code context.
     * 
     * @param request The query request
     * @param graph The dependency graph
     * @return A QueryResult containing the formatted graph and source code context
     */
    QueryResult execute(CodeQueryRequest request, DependencyGraph graph);
}