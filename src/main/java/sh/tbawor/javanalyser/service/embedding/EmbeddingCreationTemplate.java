package sh.tbawor.javanalyser.service.embedding;

import lombok.extern.slf4j.Slf4j;
import sh.tbawor.javanalyser.exception.EmbeddingException;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.model.DependencyGraph;
import sh.tbawor.javanalyser.repository.VectorEmbeddingRepository;
import sh.tbawor.javanalyser.util.BatchProcessor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Template for the embedding creation process.
 * This is part of the Template Method pattern implementation.
 */
@Slf4j
public abstract class EmbeddingCreationTemplate {

    protected final VectorEmbeddingRepository repository;
    protected final int batchSize;
    protected final int maxEmbeddings;
    protected final int progressLogInterval;

    /**
     * Constructor for the embedding creation template
     * 
     * @param repository Repository for storing embeddings
     * @param batchSize Size of batches to process
     * @param maxEmbeddings Maximum number of embeddings to create
     * @param progressLogInterval Interval for logging progress (in percentage)
     */
    public EmbeddingCreationTemplate(
            VectorEmbeddingRepository repository,
            int batchSize,
            int maxEmbeddings,
            int progressLogInterval) {
        this.repository = repository;
        this.batchSize = batchSize;
        this.maxEmbeddings = maxEmbeddings;
        this.progressLogInterval = progressLogInterval;
    }

    /**
     * Template method that defines the skeleton of the embedding creation algorithm.
     * 
     * @param graph The dependency graph
     * @throws EmbeddingException if an error occurs during embedding creation
     */
    public final void createEmbeddings(DependencyGraph graph) {
        long startTime = System.currentTimeMillis();
        List<AstNode> nodes = graph.getNodes();
        
        if (nodes.isEmpty()) {
            log.warn("No nodes found in graph. Skipping embedding creation.");
            return;
        }

        log.info("Starting to create embeddings for {} nodes with batch size {} and max embeddings {}", 
                nodes.size(), batchSize, maxEmbeddings);

        try {
            // Step 1: Clear existing embeddings
            clearExistingEmbeddings();

            // Step 2: Process nodes and create embeddings
            AtomicInteger successfulEmbeddings = new AtomicInteger(0);
            
            BatchProcessor.processBatchesWithProgress(
                nodes,
                batchSize,
                maxEmbeddings,
                progressLogInterval,
                "Embedding creation",
                node -> {
                    try {
                        // Create embedding for the node - this is the abstract step
                        boolean success = createEmbeddingForNode(node);
                        if (success) {
                            successfulEmbeddings.incrementAndGet();
                        }
                    } catch (Exception e) {
                        log.error("Error creating embedding for node: {} in {}", node.getName(), node.getFilePath(), e);
                        throw new EmbeddingException("Failed to create embedding for node: " + node.getName(), e);
                    }
                }
            );

            // Log completion
            long duration = System.currentTimeMillis() - startTime;
            log.info("Completed creating {} embeddings in {}ms", successfulEmbeddings.get(), duration);
        } catch (Exception e) {
            log.error("Error during embedding creation process", e);
            throw new EmbeddingException("Failed to create embeddings", e);
        }
    }

    /**
     * Clears existing embeddings from the repository.
     * This is a hook method that can be overridden by subclasses.
     */
    protected void clearExistingEmbeddings() {
        log.info("Clearing existing embeddings from database");
        repository.deleteAll();
    }

    /**
     * Creates an embedding for a single node.
     * This is an abstract method that must be implemented by subclasses.
     * 
     * @param node The node to create an embedding for
     * @return true if the embedding was created successfully, false otherwise
     * @throws EmbeddingException if an error occurs during embedding creation
     */
    protected abstract boolean createEmbeddingForNode(AstNode node);
}