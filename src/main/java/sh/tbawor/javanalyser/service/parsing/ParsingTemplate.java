package sh.tbawor.javanalyser.service.parsing;

import lombok.extern.slf4j.Slf4j;
import sh.tbawor.javanalyser.exception.ParsingException;
import sh.tbawor.javanalyser.model.DependencyGraph;

import java.nio.file.Path;
import java.util.List;

/**
 * Abstract template class that defines the overall process for parsing Java
 * projects and building a dependency graph.
 * 
 * <p>This class implements the Template Method design pattern, which defines
 * the skeleton of the parsing algorithm in the {@link #parseProject} method,
 * with specific steps delegated to abstract methods that subclasses must implement.
 * This approach allows for varying implementations of each step while maintaining
 * the overall process structure.</p>
 * 
 * <p>The parsing process consists of several distinct phases:</p>
 * <ol>
 *   <li>Initializing the dependency graph</li>
 *   <li>Finding all Java source files in the project</li>
 *   <li>Parsing AST nodes from each file</li>
 *   <li>Enriching nodes with source code</li>
 *   <li>Extracting cross-file dependencies</li>
 *   <li>Generating vector embeddings (optional)</li>
 * </ol>
 * 
 * <p>Concrete subclasses must implement the abstract methods to provide specific
 * parsing behavior. The template also provides hook methods with default
 * implementations that can be overridden if needed.</p>
 */
@Slf4j
public abstract class ParsingTemplate {

    /**
     * Template method that defines the skeleton of the multi-pass parsing algorithm.
     * This method orchestrates the entire parsing process through a series of
     * well-defined steps that are executed in sequence.
     * 
     * <p>The method is marked as final to prevent subclasses from altering
     * the overall algorithm structure, while allowing customization of individual steps.</p>
     * 
     * @param projectPath The absolute path to the project root directory to parse
     * @return The constructed dependency graph of the project
     * @throws ParsingException if any part of the parsing process fails
     */
    public final DependencyGraph parseProject(String projectPath) {
        if (projectPath == null || projectPath.isEmpty()) {
            throw new ParsingException("Project path cannot be null or empty");
        }
        
        try {
            long startTime = System.currentTimeMillis();
            log.info("Starting to parse project at {}", projectPath);
            
            // Step 1: Initialize the dependency graph
            DependencyGraph graph = initializeGraph();
            
            // Step 2: Find all Java files in the project
            List<Path> javaFiles = findJavaFiles(projectPath);
            log.info("Found {} Java files to parse", javaFiles.size());
            
            // Step 3: Parse AST nodes from files
            parseAstNodes(javaFiles, graph);
            
            // Step 4: Enrich nodes with source code
            enrichWithSourceCode(javaFiles, graph);
            
            // Step 5: Extract cross-file dependencies
            extractCrossFileDependencies(graph);
            
            // Step 6: Generate embeddings (optional)
            generateEmbeddings(graph);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Dependency graph built successfully in {}ms with {} nodes and {} edges",
                    duration, graph.getNodes().size(), graph.getEdges().size());
            
            return graph;
        } catch (ParsingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error parsing project at {}", projectPath, e);
            throw new ParsingException("Failed to parse project: " + e.getMessage(), e);
        }
    }
    
    /**
     * Initializes an empty dependency graph.
     * This is a hook method that provides a default implementation but can be
     * overridden by subclasses to provide specialized initialization.
     * 
     * @return A new empty dependency graph
     */
    protected DependencyGraph initializeGraph() {
        return new DependencyGraph();
    }
    
    /**
     * Finds all Java source files in the project.
     * Implementations should handle traversing the directory structure
     * and filtering for Java files.
     * 
     * @param projectPath The path to the project root directory
     * @return A list of paths to Java source files
     * @throws ParsingException if file finding fails
     */
    protected abstract List<Path> findJavaFiles(String projectPath);
    
    /**
     * Parses AST nodes from Java source files and adds them to the graph.
     * This is the first pass of the multi-pass parsing process that extracts
     * the structure of Java classes, methods, and fields.
     * 
     * @param javaFiles The list of Java source files to parse
     * @param graph The dependency graph to populate with nodes
     * @throws ParsingException if AST parsing fails
     */
    protected abstract void parseAstNodes(List<Path> javaFiles, DependencyGraph graph);
    
    /**
     * Enriches AST nodes with their associated source code.
     * This is the second pass of the parsing process that adds the actual
     * source code text to nodes previously created in the first pass.
     * 
     * @param javaFiles The list of Java source files to process
     * @param graph The dependency graph with nodes to enrich
     * @throws ParsingException if source code extraction fails
     */
    protected abstract void enrichWithSourceCode(List<Path> javaFiles, DependencyGraph graph);
    
    /**
     * Extracts and resolves dependencies between nodes across different files.
     * This is the third pass of the parsing process that connects nodes together
     * based on their relationships (imports, inheritance, method calls, etc.).
     * 
     * @param graph The dependency graph to process
     * @throws ParsingException if dependency extraction fails
     */
    protected abstract void extractCrossFileDependencies(DependencyGraph graph);
    
    /**
     * Generates vector embeddings for nodes in the graph.
     * This optional fourth pass creates embeddings for semantic search capabilities.
     * The default implementation does nothing and can be overridden by subclasses.
     * 
     * @param graph The dependency graph with nodes to generate embeddings for
     */
    protected void generateEmbeddings(DependencyGraph graph) {
        log.info("Skipping embedding generation (not implemented in this template)");
    }
}