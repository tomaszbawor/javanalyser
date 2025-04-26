package sh.tbawor.javanalyser.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.model.DependencyGraph;
import sh.tbawor.javanalyser.model.VectorEmbedding;
import sh.tbawor.javanalyser.repository.VectorEmbeddingRepository;
import sh.tbawor.javanalyser.util.VectorUtil;

import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VectorEmbeddingService {

  private final VectorEmbeddingRepository repository;
  private final OllamaEmbeddingModel embeddingModel;
  private final VectorUtil vectorUtil;

  /**
   * Creates embeddings for all nodes in the dependency graph and stores them in
   * the repository
   */
  @Transactional
  public void createEmbeddingsFromGraph(DependencyGraph graph) {
    log.info("Starting to create embeddings for {} nodes", graph.getNodes().size());

    // Clear existing embeddings
    repository.deleteAll();

    // Process each node
    int count = 0;
    for (AstNode node : graph.getNodes()) {
      try {
        createEmbeddingForNode(node);
        count++;

        if (count % 100 == 0) {
          log.info("Created embeddings for {} nodes", count);
        }
      } catch (Exception e) {
        log.error("Error creating embedding for node: {}", node.getName(), e);
      }
    }

    log.info("Completed creating embeddings for {} nodes", count);
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
   * Creates a vector embedding for a single AST node
   */
  private void createEmbeddingForNode(AstNode node) {
    // Create text to embed
    String textToEmbed = buildEmbeddingText(node);

    // Generate embedding
    EmbeddingResponse embeddingResponse = embeddingModel.embedForResponse(List.of(textToEmbed));
    float[] embedding = embeddingResponse.getResult().getOutput();

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
  }

  /**
   * Builds the text representation of a node for embedding generation
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
   * Creates a short description of the node for display in search results
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
