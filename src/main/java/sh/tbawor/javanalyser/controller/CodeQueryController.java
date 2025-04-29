package sh.tbawor.javanalyser.controller;

import lombok.RequiredArgsConstructor;
import sh.tbawor.javanalyser.model.CodeQueryRequest;
import sh.tbawor.javanalyser.model.DependencyGraph;
import sh.tbawor.javanalyser.model.SemanticSearchRequest;
import sh.tbawor.javanalyser.model.SourceCodeContext;
import sh.tbawor.javanalyser.model.VectorEmbedding;
import sh.tbawor.javanalyser.service.AstService;
import sh.tbawor.javanalyser.service.CodeQueryService;
import sh.tbawor.javanalyser.service.ExplanationService;
import sh.tbawor.javanalyser.service.VectorEmbeddingService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/code")
@RequiredArgsConstructor
public class CodeQueryController {

  private final AstService astService;
  private final CodeQueryService codeQueryService;
  private final VectorEmbeddingService vectorEmbeddingService;
  private final ExplanationService explanationService;

  @PostMapping("/query")
  public ResponseEntity<String> queryCode(@RequestBody CodeQueryRequest request) {
    String response = codeQueryService.queryCode(request);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/dependencies")
  public ResponseEntity<DependencyGraph> getDependencyGraph() {
    DependencyGraph graph = astService.getDependencyGraph();
    return ResponseEntity.ok(graph);
  }

  @GetMapping("/refresh")
  public ResponseEntity<String> refreshCodebase() {
    astService.parseProjectAndUpdateGraph();
    return ResponseEntity.ok("Code dependencies refreshed and embeddings created successfully");
  }

  @PostMapping("/search")
  public ResponseEntity<List<VectorEmbedding>> semanticSearch(@RequestBody SemanticSearchRequest request) {
    List<VectorEmbedding> results = vectorEmbeddingService.semanticSearch(
        request.getQuery(),
        request.getMaxResults(),
        request.getFilterPackage());
    return ResponseEntity.ok(results);
  }

  @GetMapping("/sources/{packageName}")
  public ResponseEntity<List<SourceCodeContext>> getSourcesForPackage(@PathVariable String packageName) {
    List<SourceCodeContext> sources = astService.getSourceCodeForPackage(packageName);
    return ResponseEntity.ok(sources);
  }

  @GetMapping("/explain/{fullyQualifiedFunctionName}")
  public ResponseEntity<String> explainFunction(@PathVariable String fullyQualifiedFunctionName) {
    // Basic validation - you might want more robust FQN validation
    if (fullyQualifiedFunctionName == null || !fullyQualifiedFunctionName.contains(".")) {
      return ResponseEntity.badRequest().body("Invalid fully qualified function name provided.");
    }
    String explanation = explanationService.explainFunction(fullyQualifiedFunctionName);
    if (explanation.startsWith("Error:")) {
      // Return 404 or 500 based on the error type if needed
      return ResponseEntity.status(404).body(explanation);
    }
    return ResponseEntity.ok(explanation);
  }
}
