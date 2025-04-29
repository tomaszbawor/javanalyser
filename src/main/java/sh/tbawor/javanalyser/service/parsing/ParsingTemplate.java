package sh.tbawor.javanalyser.service.parsing;

import lombok.extern.slf4j.Slf4j;
import sh.tbawor.javanalyser.model.DependencyGraph;

import java.nio.file.Path;
import java.util.List;

/**
 * Template for the multi-pass parsing process.
 * This is part of the Template Method pattern implementation.
 */
@Slf4j
public abstract class ParsingTemplate {

    /**
     * Template method that defines the skeleton of the parsing algorithm.
     * 
     * @param projectPath The path to the project to parse
     * @return The dependency graph
     */
    public final DependencyGraph parseProject(String projectPath) {
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
        
        // Step 6: Generate embeddings
        generateEmbeddings(graph);
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("Dependency graph built successfully in {}ms with {} nodes and {} edges",
                duration, graph.getNodes().size(), graph.getEdges().size());
        
        return graph;
    }
    
    /**
     * Initializes the dependency graph.
     * This is a hook method that can be overridden by subclasses.
     * 
     * @return The initialized dependency graph
     */
    protected DependencyGraph initializeGraph() {
        return new DependencyGraph();
    }
    
    /**
     * Finds all Java files in the project.
     * This is an abstract method that must be implemented by subclasses.
     * 
     * @param projectPath The path to the project
     * @return A list of paths to Java files
     */
    protected abstract List<Path> findJavaFiles(String projectPath);
    
    /**
     * Parses AST nodes from files and adds them to the graph.
     * This is an abstract method that must be implemented by subclasses.
     * 
     * @param javaFiles The list of Java files to parse
     * @param graph The dependency graph to add nodes to
     */
    protected abstract void parseAstNodes(List<Path> javaFiles, DependencyGraph graph);
    
    /**
     * Enriches nodes with source code.
     * This is an abstract method that must be implemented by subclasses.
     * 
     * @param javaFiles The list of Java files to parse
     * @param graph The dependency graph to enrich
     */
    protected abstract void enrichWithSourceCode(List<Path> javaFiles, DependencyGraph graph);
    
    /**
     * Extracts cross-file dependencies.
     * This is an abstract method that must be implemented by subclasses.
     * 
     * @param graph The dependency graph to extract dependencies from
     */
    protected abstract void extractCrossFileDependencies(DependencyGraph graph);
    
    /**
     * Generates embeddings for the graph.
     * This is a hook method that can be overridden by subclasses.
     * 
     * @param graph The dependency graph to generate embeddings for
     */
    protected void generateEmbeddings(DependencyGraph graph) {
        // Default implementation does nothing
        log.info("Skipping embedding generation (not implemented in this template)");
    }
}