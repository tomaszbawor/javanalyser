package sh.tbawor.javanalyser.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.model.DependencyGraph;
import sh.tbawor.javanalyser.model.SourceCodeContext;
import sh.tbawor.javanalyser.service.parsing.JavaAstParsingTemplate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for parsing Java files and building a dependency graph.
 * Uses the Template Method pattern via JavaAstParsingTemplate.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AstService {

  private final JavaAstParsingTemplate parsingTemplate;

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

  /**
   * Parses the project and updates the dependency graph.
   * Uses the Template Method pattern via JavaAstParsingTemplate.
   */
  public void parseProjectAndUpdateGraph() {
    try {
      log.info("Starting to parse project using template");
      dependencyGraph = parsingTemplate.parseProject(projectPath);
      log.info("Dependency graph built successfully with {} nodes and {} edges",
          dependencyGraph.getNodes().size(), dependencyGraph.getEdges().size());
    } catch (Exception e) {
      log.error("Unexpected error during project parsing", e);
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
