package sh.tbawor.javanalyser.service.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import sh.tbawor.javanalyser.model.CodeQueryRequest;
import sh.tbawor.javanalyser.model.DependencyGraph;
import sh.tbawor.javanalyser.service.AstFormatter;
import sh.tbawor.javanalyser.service.SourceCodeService;

/**
 * Strategy implementation for traditional context-based filtering queries.
 * This is part of the Strategy pattern implementation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TraditionalContextStrategy implements QueryStrategy {

    private final AstFormatter astFormatter;
    private final SourceCodeService sourceCodeService;

    @Override
    public boolean canHandle(CodeQueryRequest request) {
        return !request.isUseSemanticSearch();
    }

    @Override
    public QueryResult execute(CodeQueryRequest request, DependencyGraph graph) {
        String formattedGraph;
        String sourceCodeContext = "";

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

        return QueryResult.builder()
                .formattedGraph(formattedGraph)
                .sourceCodeContext(sourceCodeContext)
                .build();
    }
}