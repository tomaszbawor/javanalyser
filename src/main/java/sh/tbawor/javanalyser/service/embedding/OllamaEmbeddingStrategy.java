package sh.tbawor.javanalyser.service.embedding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

    @Override
    public float[] generateEmbedding(String text) {
        try {
            EmbeddingResponse embeddingResponse = embeddingModel.embedForResponse(List.of(text));
            return embeddingResponse.getResult().getOutput();
        } catch (Exception e) {
            log.error("Error generating embedding for text", e);
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    @Override
    public List<float[]> generateEmbeddings(List<String> texts) {
        try {
            EmbeddingResponse embeddingResponse = embeddingModel.embedForResponse(texts);
            return embeddingResponse.getResults().stream()
                    .map(result -> result.getOutput())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error generating embeddings for texts", e);
            throw new RuntimeException("Failed to generate embeddings", e);
        }
    }
}