package sh.tbawor.javanalyser.service.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.model.VectorEmbedding;
import sh.tbawor.javanalyser.repository.VectorEmbeddingRepository;
import sh.tbawor.javanalyser.util.VectorUtil;

/**
 * Concrete implementation of the EmbeddingCreationTemplate for Ollama embeddings.
 * This is part of the Template Method pattern implementation.
 * Uses the Strategy pattern via EmbeddingModelStrategy.
 */
@Component
@Slf4j
public class OllamaEmbeddingCreation extends EmbeddingCreationTemplate {

    private final EmbeddingModelStrategy embeddingStrategy;
    private final VectorUtil vectorUtil;

    public OllamaEmbeddingCreation(
            VectorEmbeddingRepository repository,
            OllamaEmbeddingStrategy embeddingStrategy,
            VectorUtil vectorUtil,
            @Value("${embedding.batch.size:50}") int batchSize,
            @Value("${embedding.max.embeddings:5000}") int maxEmbeddings,
            @Value("${embedding.progress.log.interval:5}") int progressLogInterval) {
        super(repository, batchSize, maxEmbeddings, progressLogInterval);
        this.embeddingStrategy = embeddingStrategy;
        this.vectorUtil = vectorUtil;
    }

    @Override
    protected boolean createEmbeddingForNode(AstNode node) {
        // Create text to embed
        String textToEmbed = buildEmbeddingText(node);

        // Generate embedding using the strategy
        float[] embedding = embeddingStrategy.generateEmbedding(textToEmbed);

        // Convert float[] to byte[] for storage
        byte[] embeddingBytes = vectorUtil.floatArrayToByteArray(embedding);

        // Build source code snippet
        String sourceCodeSnippet = (node.getSourceCode() != null && node.getSourceCode().length() > 10000)
                ? node.getSourceCode().substring(0, 10000) + "..."
                : node.getSourceCode();

        // Create and save embedding
        VectorEmbedding vectorEmbedding = VectorEmbedding.builder()
                .nodeKey(node.getPackageName() + "." + node.getName())
                .filePath(node.getFilePath())
                .type(node.getType())
                .name(node.getName())
                .packageName(node.getPackageName())
                .sourceCodeSnippet(sourceCodeSnippet)
                .description(buildNodeDescription(node))
                .embedding(embeddingBytes)
                .build();

        repository.save(vectorEmbedding);
        return true;
    }

    /**
     * Builds the text representation of a node for embedding generation.
     * 
     * @param node The node to build text for
     * @return The text representation
     */
    private String buildEmbeddingText(AstNode node) {
        StringBuilder sb = new StringBuilder();

        sb.append("Type: ").append(node.getType()).append("\n");
        sb.append("Name: ").append(node.getName()).append("\n");
        sb.append("Package: ").append(node.getPackageName()).append("\n");

        if (node.getVisibility() != null) {
            sb.append("Visibility: ").append(node.getVisibility()).append("\n");
        }

        if (node.isInterface()) {
            sb.append("Is Interface: true\n");
        }

        if (node.isAbstract()) {
            sb.append("Is Abstract: true\n");
        }

        if (node.getReturnType() != null) {
            sb.append("Return Type: ").append(node.getReturnType()).append("\n");
        }

        // Add dependencies
        if (!node.getDependencies().isEmpty()) {
            sb.append("Dependencies:\n");
            node.getDependencies()
                .forEach(dep -> sb.append("- ").append(dep.getType()).append(": ").append(dep.getTargetNode()).append("\n"));
        }

        // Add child elements summary
        if (!node.getChildren().isEmpty()) {
            sb.append("Contains:\n");
            node.getChildren()
                .forEach(child -> sb.append("- ").append(child.getType()).append(": ").append(child.getName()).append("\n"));
        }

        // Add source code if available
        if (node.getSourceCode() != null && !node.getSourceCode().isEmpty()) {
            sb.append("Source code:\n").append(node.getSourceCode()).append("\n");
        }

        return sb.toString();
    }

    /**
     * Creates a short description of the node for display in search results.
     * 
     * @param node The node to describe
     * @return The description
     */
    private String buildNodeDescription(AstNode node) {
        StringBuilder sb = new StringBuilder();

        sb.append(node.getType()).append(" in ").append(node.getPackageName());

        if (node.getVisibility() != null) {
            sb.append(", ").append(node.getVisibility());
        }

        if (node.isInterface()) {
            sb.append(", interface");
        } else if (node.isAbstract()) {
            sb.append(", abstract");
        }

        if (!node.getDependencies().isEmpty()) {
            sb.append(", has ").append(node.getDependencies().size()).append(" dependencies");
        }

        if (!node.getChildren().isEmpty()) {
            sb.append(", contains ").append(node.getChildren().size()).append(" members");
        }

        return sb.toString();
    }
}
