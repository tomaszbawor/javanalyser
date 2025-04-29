package sh.tbawor.javanalyser.service.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a query strategy execution.
 * Contains the formatted graph and source code context.
 * This is part of the Strategy pattern implementation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResult {
    private String formattedGraph;
    private String sourceCodeContext;
}