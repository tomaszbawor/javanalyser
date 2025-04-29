package sh.tbawor.javanalyser.service.embedding;

import lombok.extern.slf4j.Slf4j;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.model.DependencyGraph;
import sh.tbawor.javanalyser.repository.VectorEmbeddingRepository;

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
     */
    public final void createEmbeddings(DependencyGraph graph) {
        long startTime = System.currentTimeMillis();
        List<AstNode> nodes = graph.getNodes();
        int totalNodes = nodes.size();

        log.info("Starting to create embeddings for {} nodes with batch size {} and max embeddings {}", 
                totalNodes, batchSize, maxEmbeddings);

        // Step 1: Clear existing embeddings
        clearExistingEmbeddings();

        // Step 2: Create batches of nodes to process
        List<List<AstNode>> batches = createBatches(nodes, batchSize);
        log.info("Created {} batches with max size of {}", batches.size(), batchSize);

        // Step 3: Process batches
        AtomicInteger processedNodes = new AtomicInteger(0);
        AtomicInteger successfulEmbeddings = new AtomicInteger(0);
        int lastLoggedPercentage = 0;

        processBatches(batches, processedNodes, successfulEmbeddings, lastLoggedPercentage, totalNodes);

        // Step 4: Log completion
        long duration = System.currentTimeMillis() - startTime;
        log.info("Completed creating {} embeddings for {} nodes in {}ms", 
                successfulEmbeddings.get(), processedNodes.get(), duration);
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
     * Creates batches of nodes to process.
     * This is a hook method that can be overridden by subclasses.
     * 
     * @param items The items to batch
     * @param batchSize The size of each batch
     * @return A list of batches
     */
    protected <T> List<List<T>> createBatches(List<T> items, int batchSize) {
        List<List<T>> batches = new java.util.ArrayList<>();
        for (int i = 0; i < items.size(); i += batchSize) {
            int end = Math.min(i + batchSize, items.size());
            batches.add(new java.util.ArrayList<>(items.subList(i, end)));
        }
        return batches;
    }

    /**
     * Processes batches of nodes.
     * This is a hook method that can be overridden by subclasses.
     * 
     * @param batches The batches to process
     * @param processedNodes Counter for processed nodes
     * @param successfulEmbeddings Counter for successful embeddings
     * @param lastLoggedPercentage Last logged percentage
     * @param totalNodes Total number of nodes
     */
    protected void processBatches(
            List<List<AstNode>> batches,
            AtomicInteger processedNodes,
            AtomicInteger successfulEmbeddings,
            int lastLoggedPercentage,
            int totalNodes) {
        
        // Process each batch
        for (List<AstNode> batch : batches) {
            // Process each node in the batch
            for (AstNode node : batch) {
                // Check if we've reached the maximum number of embeddings
                if (successfulEmbeddings.get() >= maxEmbeddings) {
                    log.warn("Reached maximum embedding count ({}). Stopping embedding generation.", maxEmbeddings);
                    break;
                }

                try {
                    // Create embedding for the node - this is the abstract step
                    boolean success = createEmbeddingForNode(node);
                    if (success) {
                        successfulEmbeddings.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("Error creating embedding for node: {} in {}", node.getName(), node.getFilePath(), e);
                }

                // Update progress
                int currentProcessed = processedNodes.incrementAndGet();
                int percentage = (currentProcessed * 100) / totalNodes;

                // Log progress at intervals
                if (percentage >= lastLoggedPercentage + progressLogInterval) {
                    log.info("Embedding progress: {}% ({}/{} nodes, {} embeddings created)", 
                            percentage, currentProcessed, totalNodes, successfulEmbeddings.get());
                    lastLoggedPercentage = percentage;
                }
            }

            // Check if we've reached the maximum number of embeddings
            if (successfulEmbeddings.get() >= maxEmbeddings) {
                break;
            }
        }
    }

    /**
     * Creates an embedding for a single node.
     * This is an abstract method that must be implemented by subclasses.
     * 
     * @param node The node to create an embedding for
     * @return true if the embedding was created successfully, false otherwise
     */
    protected abstract boolean createEmbeddingForNode(AstNode node);
}