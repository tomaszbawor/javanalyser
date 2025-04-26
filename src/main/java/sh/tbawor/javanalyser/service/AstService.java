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
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
      log.info("Starting to parse project at {}", projectPath);
      dependencyGraph = new DependencyGraph();
      Path rootPath = Paths.get(projectPath);

      // Parse all Java files in the project
      try (Stream<Path> paths = Files.walk(rootPath)) {
        List<Path> javaFiles = paths
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());

        log.info("Found {} Java files to parse", javaFiles.size());

        // First pass: parse all files for AST
        for (Path filePath : javaFiles) {
          processJavaFile(filePath);
        }

        // Second pass: extract source code
        for (Path filePath : javaFiles) {
          enrichWithSourceCode(filePath);
        }

        // Third pass: extract cross-file dependencies
        dependencyExtractor.extractCrossFileDependencies(dependencyGraph);
      }

      // Generate embeddings for all nodes
      vectorEmbeddingService.createEmbeddingsFromGraph(dependencyGraph);

      log.info("Dependency graph built successfully with {} nodes and {} edges",
          dependencyGraph.getNodes().size(),
          dependencyGraph.getEdges().size());
    } catch (IOException e) {
      log.error("Error parsing project files", e);
    }
  }

  private void processJavaFile(Path filePath) {
    try {
      log.debug("Processing file: {}", filePath);

      // Parse AST
      List<AstNode> fileNodes = astParser.parseFile(filePath);

      // Add nodes to the graph
      fileNodes.forEach(dependencyGraph::addNode);

      // Extract dependencies within the file
      List<CodeDependency> dependencies = dependencyExtractor.extractDependencies(filePath, fileNodes);
      dependencies.forEach(dependencyGraph::addDependency);

    } catch (Exception e) {
      log.error("Error processing file: {}", filePath, e);
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
