package sh.tbawor.javanalyser.service.embedding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;
import sh.tbawor.javanalyser.exception.EmbeddingException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Concrete implementation of the EmbeddingModelStrategy for Ollama embeddings.
 * This is part of the Strategy pattern implementation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OllamaEmbeddingStrategy implements EmbeddingModelStrategy {

    private final EmbeddingModel embeddingModel;

    /**
     * Generates a single embedding vector for the given text
     *
     * @param text The text to generate an embedding for
     * @return A float array containing the embedding vector
     * @throws EmbeddingException if an error occurs during embedding generation
     */
    @Override
    public float[] generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.warn("Attempted to generate embedding for null or empty text");
            throw new EmbeddingException("Cannot generate embedding for null or empty text");
        }
        
        try {
            log.debug("Generating embedding for text of length {}", text.length());
            EmbeddingResponse embeddingResponse = embeddingModel.embedForResponse(List.of(text));
            return embeddingResponse.getResult().getOutput();
        } catch (Exception e) {
            log.error("Error generating embedding for text of length {}", text.length(), e);
            throw new EmbeddingException("Failed to generate embedding", e);
        }
    }

    /**
     * Generates multiple embedding vectors for the given texts
     *
     * @param texts A list of texts to generate embeddings for
     * @return A list of float arrays containing the embedding vectors
     * @throws EmbeddingException if an error occurs during embeddings generation
     */
    @Override
    public List<float[]> generateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            log.warn("Attempted to generate embeddings for null or empty text list");
            throw new EmbeddingException("Cannot generate embeddings for null or empty text list");
        }
        
        try {
            log.debug("Generating embeddings for {} texts", texts.size());
            EmbeddingResponse embeddingResponse = embeddingModel.embedForResponse(texts);
            return embeddingResponse.getResults().stream()
                    .map(result -> result.getOutput())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error generating embeddings for {} texts", texts.size(), e);
            throw new EmbeddingException("Failed to generate embeddings", e);
        }
    }
}