package sh.tbawor.javanalyser.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.model.CodeDependency;
import sh.tbawor.javanalyser.model.DependencyGraph;
import sh.tbawor.javanalyser.model.SourceCodeContext;
import sh.tbawor.javanalyser.parser.JavaAstParser;
import sh.tbawor.javanalyser.parser.SourceCodeExtractor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class AstService {

  private final JavaAstParser astParser;
  private final DependencyExtractor dependencyExtractor;
  private final SourceCodeExtractor sourceCodeExtractor;
  private final VectorEmbeddingService vectorEmbeddingService;

  @Value("${code.project.path}")
  private String projectPath;

  @Value("${parser.batch.size:100}")
  private int batchSize;

  @Value("${parser.max.nodes:10000}")
  private int maxNodes;

  @Value("${parser.progress.log.interval:5}")
  private int progressLogInterval;

  @Getter
  private DependencyGraph dependencyGraph = new DependencyGraph();

  @PostConstruct
  public void init() {
    parseProjectAndUpdateGraph();
  }

  @Async
  public CompletableFuture<Void> parseProjectAsync() {
    parseProjectAndUpdateGraph();
    return CompletableFuture.completedFuture(null);
  }

  public void parseProjectAndUpdateGraph() {
    try {
      long startTime = System.currentTimeMillis();
      log.info("Starting to parse project at {}", projectPath);
      dependencyGraph = new DependencyGraph();
      Path rootPath = Paths.get(projectPath);

      // Parse all Java files in the project
      try (Stream<Path> paths = Files.walk(rootPath)) {
        List<Path> javaFiles = paths
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());

        int totalFiles = javaFiles.size();
        log.info("Found {} Java files to parse", totalFiles);

        // Create batches of files to process
        List<List<Path>> batches = createBatches(javaFiles, batchSize);
        log.info("Created {} batches with max size of {}", batches.size(), batchSize);

        AtomicInteger processedFiles = new AtomicInteger(0);
        AtomicInteger nodeCount = new AtomicInteger(0);
        int lastLoggedPercentage = 0;

        // First pass: parse files for AST in batches
        log.info("Starting first pass: parsing files for AST nodes");
        for (List<Path> batch : batches) {
          // Process each file in the batch
          for (Path filePath : batch) {
            // Check if we've reached the maximum number of nodes
            if (nodeCount.get() >= maxNodes) {
              log.warn("Reached maximum node count ({}). Stopping parsing.", maxNodes);
              break;
            }

            // Process the file
            int nodesAdded = processJavaFile(filePath);
            nodeCount.addAndGet(nodesAdded);

            // Update progress
            int currentProcessed = processedFiles.incrementAndGet();
            int percentage = (currentProcessed * 100) / totalFiles;

            // Log progress at intervals
            if (percentage >= lastLoggedPercentage + progressLogInterval) {
                log.info("First pass progress: {}% ({}/{} files, {} nodes)", 
                    percentage, currentProcessed, totalFiles, nodeCount.get());
                lastLoggedPercentage = percentage;
            }
          }

          // Check if we've reached the maximum number of nodes
          if (nodeCount.get() >= maxNodes) {
            break;
          }
        }

        log.info("First pass completed: parsed {} files, created {} nodes", 
            processedFiles.get(), nodeCount.get());

        // Reset counters for second pass
        processedFiles.set(0);
        lastLoggedPercentage = 0;

        // Second pass: extract source code in batches
        log.info("Starting second pass: enriching nodes with source code");
        for (List<Path> batch : batches) {
          for (Path filePath : batch) {
            enrichWithSourceCode(filePath);

            // Update progress
            int currentProcessed = processedFiles.incrementAndGet();
            int percentage = (currentProcessed * 100) / totalFiles;

            // Log progress at intervals
            if (percentage >= lastLoggedPercentage + progressLogInterval) {
                log.info("Second pass progress: {}% ({}/{} files)", 
                    percentage, currentProcessed, totalFiles);
                lastLoggedPercentage = percentage;
            }

            // Stop if we've processed all files from the first pass
            if (currentProcessed >= nodeCount.get()) {
                break;
            }
          }
        }

        log.info("Second pass completed: enriched {} files with source code", processedFiles.get());

        // Third pass: extract cross-file dependencies
        log.info("Starting third pass: extracting cross-file dependencies");
        dependencyExtractor.extractCrossFileDependencies(dependencyGraph);
        log.info("Third pass completed: extracted cross-file dependencies");
      }

      // Generate embeddings for all nodes with database bounds
      log.info("Starting to generate embeddings for nodes");
      vectorEmbeddingService.createEmbeddingsFromGraph(dependencyGraph);

      long duration = System.currentTimeMillis() - startTime;
      log.info("Dependency graph built successfully in {}ms with {} nodes and {} edges",
          duration, dependencyGraph.getNodes().size(), dependencyGraph.getEdges().size());
    } catch (IOException e) {
      log.error("Error parsing project files", e);
    } catch (Exception e) {
      log.error("Unexpected error during project parsing", e);
    }
  }

  /**
   * Creates batches of files to process
   */
  private List<List<Path>> createBatches(List<Path> files, int batchSize) {
    List<List<Path>> batches = new ArrayList<>();
    for (int i = 0; i < files.size(); i += batchSize) {
      int end = Math.min(i + batchSize, files.size());
      batches.add(new ArrayList<>(files.subList(i, end)));
    }
    return batches;
  }

  /**
   * Process a Java file and add its nodes to the dependency graph
   * 
   * @param filePath Path to the Java file
   * @return Number of nodes added to the graph
   */
  private int processJavaFile(Path filePath) {
    try {
      log.debug("Processing file: {}", filePath);

      // Parse AST
      List<AstNode> fileNodes = astParser.parseFile(filePath);
      int nodeCount = fileNodes.size();

      // Add nodes to the graph
      fileNodes.forEach(dependencyGraph::addNode);

      // Extract dependencies within the file
      List<CodeDependency> dependencies = dependencyExtractor.extractDependencies(filePath, fileNodes);
      dependencies.forEach(dependencyGraph::addDependency);

      return nodeCount;
    } catch (Exception e) {
      log.error("Error processing file: {}", filePath, e);
      return 0;
    }
  }

  private void enrichWithSourceCode(Path filePath) {
    try {
      log.debug("Enriching file with source code: {}", filePath);

      // Get source code for each node in the file
      sourceCodeExtractor.extractSourceCode(filePath, dependencyGraph);

    } catch (Exception e) {
      log.error("Error enriching file with source code: {}", filePath, e);
    }
  }

  public List<AstNode> getNodesForPackage(String packageName) {
    return dependencyGraph.getNodes().stream()
        .filter(node -> node.getPackageName().startsWith(packageName))
        .toList();
  }

  public List<SourceCodeContext> getSourceCodeForPackage(String packageName) {
    return dependencyGraph.getNodes().stream()
        .filter(node -> node.getPackageName().startsWith(packageName))
        .filter(node -> node.getSourceCode() != null && !node.getSourceCode().isEmpty())
        .map(node -> SourceCodeContext.builder()
            .packageName(node.getPackageName())
            .className(node.getName())
            .filePath(node.getFilePath())
            .sourceCode(node.getSourceCode())
            .type(node.getType())
            .startLine(node.getStartLine())
            .endLine(node.getEndLine())
            .build())
        .toList();
  }
}
