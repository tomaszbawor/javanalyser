package sh.tbawor.javanalyser.service.embedding;

import java.util.List;

/**
 * Strategy interface for different embedding models.
 * This is part of the Strategy pattern implementation.
 */
public interface EmbeddingModelStrategy {
    
    /**
     * Generates embeddings for the given text.
     * 
     * @param text The text to generate embeddings for
     * @return The embedding as a float array
     */
    float[] generateEmbedding(String text);
    
    /**
     * Generates embeddings for multiple texts.
     * 
     * @param texts The texts to generate embeddings for
     * @return A list of embeddings as float arrays
     */
    List<float[]> generateEmbeddings(List<String> texts);
}