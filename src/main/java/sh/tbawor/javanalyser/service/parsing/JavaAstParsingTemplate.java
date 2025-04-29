package sh.tbawor.javanalyser.service.parsing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.model.CodeDependency;
import sh.tbawor.javanalyser.model.DependencyGraph;
import sh.tbawor.javanalyser.parser.JavaAstParser;
import sh.tbawor.javanalyser.parser.SourceCodeExtractor;
import sh.tbawor.javanalyser.service.DependencyExtractor;
import sh.tbawor.javanalyser.service.VectorEmbeddingService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Concrete implementation of the ParsingTemplate for Java AST parsing.
 * This is part of the Template Method pattern implementation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JavaAstParsingTemplate extends ParsingTemplate {

    private final JavaAstParser astParser;
    private final DependencyExtractor dependencyExtractor;
    private final SourceCodeExtractor sourceCodeExtractor;
    private final VectorEmbeddingService vectorEmbeddingService;

    @Value("${parser.batch.size:100}")
    private int batchSize;

    @Value("${parser.max.nodes:10000}")
    private int maxNodes;

    @Value("${parser.progress.log.interval:5}")
    private int progressLogInterval;

    @Override
    protected List<Path> findJavaFiles(String projectPath) {
        try {
            Path rootPath = Paths.get(projectPath);
            try (Stream<Path> paths = Files.walk(rootPath)) {
                return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            }
        } catch (IOException e) {
            log.error("Error finding Java files", e);
            return new ArrayList<>();
        }
    }

    @Override
    protected void parseAstNodes(List<Path> javaFiles, DependencyGraph graph) {
        int totalFiles = javaFiles.size();
        
        // Create batches of files to process
        List<List<Path>> batches = createBatches(javaFiles, batchSize);
        log.info("Created {} batches with max size of {}", batches.size(), batchSize);

        AtomicInteger processedFiles = new AtomicInteger(0);
        AtomicInteger nodeCount = new AtomicInteger(0);
        int lastLoggedPercentage = 0;

        // Process each batch
        for (List<Path> batch : batches) {
            // Process each file in the batch
            for (Path filePath : batch) {
                // Check if we've reached the maximum number of nodes
                if (nodeCount.get() >= maxNodes) {
                    log.warn("Reached maximum node count ({}). Stopping parsing.", maxNodes);
                    break;
                }

                // Process the file
                int nodesAdded = processJavaFile(filePath, graph);
                nodeCount.addAndGet(nodesAdded);

                // Update progress
                int currentProcessed = processedFiles.incrementAndGet();
                int percentage = (currentProcessed * 100) / totalFiles;

                // Log progress at intervals
                if (percentage >= lastLoggedPercentage + progressLogInterval) {
                    log.info("First pass progress: {}% ({}/{} files, {} nodes)", 
                        percentage, currentProcessed, totalFiles, nodeCount.get());
                    lastLoggedPercentage = percentage;
                }
            }

            // Check if we've reached the maximum number of nodes
            if (nodeCount.get() >= maxNodes) {
                break;
            }
        }

        log.info("First pass completed: parsed {} files, created {} nodes", 
            processedFiles.get(), nodeCount.get());
    }

    @Override
    protected void enrichWithSourceCode(List<Path> javaFiles, DependencyGraph graph) {
        int totalFiles = javaFiles.size();
        
        // Create batches of files to process
        List<List<Path>> batches = createBatches(javaFiles, batchSize);

        AtomicInteger processedFiles = new AtomicInteger(0);
        int lastLoggedPercentage = 0;

        // Process each batch
        for (List<Path> batch : batches) {
            for (Path filePath : batch) {
                try {
                    log.debug("Enriching file with source code: {}", filePath);
                    sourceCodeExtractor.extractSourceCode(filePath, graph);
                } catch (Exception e) {
                    log.error("Error enriching file with source code: {}", filePath, e);
                }

                // Update progress
                int currentProcessed = processedFiles.incrementAndGet();
                int percentage = (currentProcessed * 100) / totalFiles;

                // Log progress at intervals
                if (percentage >= lastLoggedPercentage + progressLogInterval) {
                    log.info("Second pass progress: {}% ({}/{} files)", 
                        percentage, currentProcessed, totalFiles);
                    lastLoggedPercentage = percentage;
                }
            }
        }

        log.info("Second pass completed: enriched {} files with source code", processedFiles.get());
    }

    @Override
    protected void extractCrossFileDependencies(DependencyGraph graph) {
        log.info("Starting third pass: extracting cross-file dependencies");
        dependencyExtractor.extractCrossFileDependencies(graph);
        log.info("Third pass completed: extracted cross-file dependencies");
    }

    @Override
    protected void generateEmbeddings(DependencyGraph graph) {
        log.info("Starting to generate embeddings for nodes");
        vectorEmbeddingService.createEmbeddingsFromGraph(graph);
    }

    /**
     * Process a Java file and add its nodes to the dependency graph
     * 
     * @param filePath Path to the Java file
     * @param graph The dependency graph to add nodes to
     * @return Number of nodes added to the graph
     */
    private int processJavaFile(Path filePath, DependencyGraph graph) {
        try {
            log.debug("Processing file: {}", filePath);

            // Parse AST
            List<AstNode> fileNodes = astParser.parseFile(filePath);
            int nodeCount = fileNodes.size();

            // Add nodes to the graph
            fileNodes.forEach(graph::addNode);

            // Extract dependencies within the file
            List<CodeDependency> dependencies = dependencyExtractor.extractDependencies(filePath, fileNodes);
            dependencies.forEach(graph::addDependency);

            return nodeCount;
        } catch (Exception e) {
            log.error("Error processing file: {}", filePath, e);
            return 0;
        }
    }

    /**
     * Creates batches of files to process
     */
    private <T> List<List<T>> createBatches(List<T> items, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < items.size(); i += batchSize) {
            int end = Math.min(i + batchSize, items.size());
            batches.add(new ArrayList<>(items.subList(i, end)));
        }
        return batches;
    }
}