package sh.tbawor.javanalyser.service.parsing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sh.tbawor.javanalyser.exception.ParsingException;
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

    /**
     * Finds all Java files in the specified project directory
     * 
     * @param projectPath Path to the project root directory
     * @return List of Java file paths
     */
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
            throw new ParsingException("Error finding Java files in project path: " + projectPath, e);
        }
    }

    /**
     * Parses Java files to extract AST nodes
     * 
     * @param javaFiles List of Java file paths to parse
     * @param graph Dependency graph to populate with nodes
     */
    @Override
    protected void parseAstNodes(List<Path> javaFiles, DependencyGraph graph) {
        // Process only up to maxNodes files
        int count = 0;
        for (Path filePath : javaFiles) {
            if (count >= maxNodes) {
                break;
            }
            
            try {
                // Parse file
                List<AstNode> nodes = astParser.parseFile(filePath);
                
                // Add nodes to graph
                for (AstNode node : nodes) {
                    graph.addNode(node);
                    count++;
                    if (count >= maxNodes) {
                        break;
                    }
                }
                
                // Extract dependencies
                List<CodeDependency> dependencies = dependencyExtractor.extractDependencies(filePath, nodes);
                for (CodeDependency dependency : dependencies) {
                    graph.addDependency(dependency);
                }
            } catch (Exception e) {
                log.error("Error parsing file: {}", filePath, e);
            }
        }
    }

    /**
     * Enriches nodes in the graph with source code
     * 
     * @param javaFiles List of Java file paths
     * @param graph Dependency graph with nodes to enrich
     */
    @Override
    protected void enrichWithSourceCode(List<Path> javaFiles, DependencyGraph graph) {
        for (Path filePath : javaFiles) {
            // For each file, check if it was successfully parsed
            boolean fileHasNodes = false;
            for (AstNode node : graph.getNodes()) {
                if (node.getFilePath() != null && node.getFilePath().equals(filePath.toString())) {
                    fileHasNodes = true;
                    break;
                }
            }
            
            // Only call extractSourceCode for files that were successfully parsed
            if (fileHasNodes) {
                sourceCodeExtractor.extractSourceCode(filePath, graph);
            }
        }
    }

    /**
     * Extracts cross-file dependencies
     * 
     * @param graph Dependency graph to process
     */
    @Override
    protected void extractCrossFileDependencies(DependencyGraph graph) {
        dependencyExtractor.extractCrossFileDependencies(graph);
    }

    /**
     * Generates vector embeddings for nodes in the graph
     * 
     * @param graph Dependency graph with nodes to create embeddings for
     */
    @Override
    protected void generateEmbeddings(DependencyGraph graph) {
        vectorEmbeddingService.createEmbeddingsFromGraph(graph);
    }
}