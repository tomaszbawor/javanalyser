package sh.tbawor.javanalyser.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.model.DependencyGraph;
import sh.tbawor.javanalyser.model.VectorEmbedding;
import sh.tbawor.javanalyser.repository.VectorEmbeddingRepository;
import sh.tbawor.javanalyser.service.embedding.EmbeddingCreationTemplate;
import sh.tbawor.javanalyser.util.VectorUtil;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VectorEmbeddingService {

  private final VectorEmbeddingRepository repository;
  private final EmbeddingModel embeddingModel;
  private final VectorUtil vectorUtil;
  private final EmbeddingCreationTemplate embeddingCreationTemplate;

  @Value("${embedding.batch.size:50}")
  private int batchSize;

  @Value("${embedding.max.embeddings:5000}")
  private int maxEmbeddings;

  @Value("${embedding.progress.log.interval:5}")
  private int progressLogInterval;

  /**
   * Creates embeddings for all nodes in the dependency graph and stores them in
   * the repository with database bounds and batch processing.
   * Uses the Template Method pattern via EmbeddingCreationTemplate.
   */
  @Transactional
  public void createEmbeddingsFromGraph(DependencyGraph graph) {
    // Delegate to the template
    embeddingCreationTemplate.createEmbeddings(graph);
  }

  /**
   * Asynchronously creates embeddings for all nodes in the graph
   */
  @Async
  public CompletableFuture<Void> createEmbeddingsAsync(DependencyGraph graph) {
    createEmbeddingsFromGraph(graph);
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Performs semantic search using vector similarity
   */
  @Transactional(readOnly = true)
  public List<VectorEmbedding> semanticSearch(String query, int maxResults, String packageFilter) {
    // Generate embedding for query
    EmbeddingResponse embeddingResponse = embeddingModel.embedForResponse(List.of(query));
    float[] queryEmbedding = embeddingResponse.getResult().getOutput();

    // Get all embeddings from repository
    List<VectorEmbedding> allEmbeddings = repository.findAll();

    // If package filter provided, apply it
    if (packageFilter != null && !packageFilter.isEmpty()) {
      allEmbeddings = allEmbeddings.stream()
          .filter(e -> e.getPackageName().startsWith(packageFilter))
          .collect(Collectors.toList());
    }

    // Calculate similarity scores and sort
    return allEmbeddings.stream()
        .map(embedding -> {
          float[] embeddingVector = vectorUtil.byteArrayToFloatArray(embedding.getEmbedding());
          float similarity = vectorUtil.cosineSimilarity(queryEmbedding, embeddingVector);
          return new Object[] { embedding, similarity };
        })
        .sorted((a, b) -> Float.compare((float) b[1], (float) a[1])) // Sort by similarity descending
        .limit(maxResults)
        .map(arr -> (VectorEmbedding) arr[0])
        .collect(Collectors.toList());
  }

  /**
   * Finds the most semantically similar nodes to a given node
   */
  @Transactional(readOnly = true)
  public List<VectorEmbedding> findSimilarNodes(String nodeKey, int maxResults) {
    // Get the source embedding
    VectorEmbedding sourceEmbedding = repository.findByNodeKey(nodeKey)
        .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeKey));

    float[] sourceVector = vectorUtil.byteArrayToFloatArray(sourceEmbedding.getEmbedding());

    // Get all other embeddings
    List<VectorEmbedding> otherEmbeddings = repository.findAll().stream()
        .filter(e -> !e.getNodeKey().equals(nodeKey))
        .collect(Collectors.toList());

    // Calculate similarity scores and sort
    return otherEmbeddings.stream()
        .map(embedding -> {
          float[] targetVector = vectorUtil.byteArrayToFloatArray(embedding.getEmbedding());
          float similarity = vectorUtil.cosineSimilarity(sourceVector, targetVector);
          return new Object[] { embedding, similarity };
        })
        .sorted((a, b) -> Float.compare((float) b[1], (float) a[1])) // Sort by similarity descending
        .limit(maxResults)
        .map(arr -> (VectorEmbedding) arr[0])
        .collect(Collectors.toList());
  }

  /**
   * Finds all embeddings that match a specific type
   */
  @Transactional(readOnly = true)
  public List<VectorEmbedding> findByType(String type, String packageFilter) {
    if (packageFilter != null && !packageFilter.isEmpty()) {
      return repository.findByTypeAndPackageNameStartingWith(type, packageFilter);
    } else {
      return repository.findByType(type);
    }
  }

  /**
   * Gets the embedding for a specific node
   */
  @Transactional(readOnly = true)
  public VectorEmbedding getEmbeddingForNode(String nodeKey) {
    return repository.findByNodeKey(nodeKey)
        .orElse(null);
  }
}
