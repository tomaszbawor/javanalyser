package sh.tbawor.javanalyser.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.model.CodeDependency;
import sh.tbawor.javanalyser.model.DependencyGraph;
import org.springframework.stereotype.Service;
import com.github.javaparser.JavaParser; // Need JavaParser for detailed body analysis
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExplanationService {

  private final AstService astService;
  private final LocalLlmService llmService;
  private final JavaParser javaParser = new JavaParser(); // For analyzing method bodies
  private final int MAX_DEPTH = 5; // Limit recursion depth

  public String explainFunction(String fullyQualifiedFunctionName) {
    DependencyGraph graph = astService.getDependencyGraph();
    AstNode targetNode = graph.getNodeByKey(fullyQualifiedFunctionName);

    if (targetNode == null) {
      return "Error: Function " + fullyQualifiedFunctionName + " not found in the analyzed codebase.";
    }

    if (!"method".equals(targetNode.getType()) && !"constructor".equals(targetNode.getType())) {
      return "Error: Node " + fullyQualifiedFunctionName + " is not a method or constructor.";
    }

    try {
      return generateExplanationRecursive(targetNode, graph, new HashSet<>(), 0);
    } catch (Exception e) {
      log.error("Error generating explanation for {}", fullyQualifiedFunctionName, e);
      return "Error generating explanation: " + e.getMessage();
    }
  }

  private String generateExplanationRecursive(AstNode currentNode, DependencyGraph graph, Set<String> visitedNodes,
      int depth) {
    String currentNodeKey = graph.getNodeKey(currentNode); // Assuming getNodeKey exists or use FQN
    log.debug("Explaining node: {} at depth {}", currentNodeKey, depth);

    // --- Prevent infinite loops and excessive depth ---
    if (visitedNodes.contains(currentNodeKey) || depth > MAX_DEPTH) {
      log.debug("Stopping recursion for {} (visited or max depth)", currentNodeKey);
      return "[Explanation omitted due to recursion depth or cycle]";
    }
    visitedNodes.add(currentNodeKey);

    // --- Find called methods within the current method's body ---
    Map<String, String> calledFunctionSummaries = new HashMap<>();
    List<String> calledMethodKeys = findCalledMethodKeys(currentNode, graph); // Needs implementation

    log.debug("Node {} calls: {}", currentNodeKey, calledMethodKeys);

    for (String calledMethodKey : calledMethodKeys) {
      AstNode calledNode = graph.getNodeByKey(calledMethodKey);
      if (calledNode != null) {
        // Recursively get summary for the called function
        String summary = generateExplanationRecursive(calledNode, graph, new HashSet<>(visitedNodes), depth + 1); // Pass
                                                                                                                  // copy
                                                                                                                  // of
                                                                                                                  // visited
                                                                                                                  // set
        calledFunctionSummaries.put(calledMethodKey, summary);
      } else {
        calledFunctionSummaries.put(calledMethodKey, "[External or unresolved call]");
      }
    }

    // --- Generate Prompt for LLM ---
    String prompt = buildExplanationPrompt(currentNode, calledFunctionSummaries);

    // --- Call LLM for explanation and summary ---
    // Adjust maxTokens as needed
    String explanation = llmService.query(prompt, 4000);

    // Remove node from visited set for alternative paths (if necessary, depending
    // on exact logic)
    // visitedNodes.remove(currentNodeKey); // Might be needed if exploring
    // different branches

    log.debug("Generated explanation for {}: {}", currentNodeKey,
        explanation.substring(0, Math.min(explanation.length(), 100)) + "...");
    return explanation;
  }

  // **Crucial Implementation Needed:** Find methods called *within* this specific
  // method body.
  // This requires parsing the method's source code and resolving call targets
  // using imports and context.
  // The existing DependencyExtractor might provide a starting point, but likely
  // needs refinement
  // to be precise at the method-body level.
  private List<String> findCalledMethodKeys(AstNode methodNode, DependencyGraph graph) {
    Set<String> calledKeys = new HashSet<>();
    String sourceCode = methodNode.getSourceCode();
    if (sourceCode == null || sourceCode.isEmpty()) {
      log.warn("Source code missing for node: {}", graph.getNodeKey(methodNode));
      return List.copyOf(calledKeys);
    }

    // Attempt to parse just the method's code snippet if possible, or the whole
    // class
    // This is simplified - real resolution is complex
    try {
      // Find the class node containing this method
      String className = methodNode.getPackageName() + "."
          + methodNode.getName().substring(0, methodNode.getName().indexOf('(')); // Heuristic
      AstNode classNode = graph.getNodes().stream()
          .filter(n -> ("class".equals(n.getType()) || "interface".equals(n.getType()))
              && (n.getPackageName() + "." + n.getName()).equals(methodNode.getPackageName() + "." + className))
          .findFirst().orElse(null); // Find the class the method belongs to

      if (classNode != null && classNode.getSourceCode() != null) {
        javaParser.parse(classNode.getSourceCode()) // Parse the containing class
            .ifSuccessful(cu -> {
              cu.findAll(MethodCallExpr.class).forEach(call -> {
                // Find the method declaration containing this call
                call.findAncestor(MethodDeclaration.class).ifPresent(callerDecl -> {
                  // Check if this is the method we are currently analyzing
                  if (callerDecl.getNameAsString().equals(methodNode.getName())) { // Simplified check
                    String methodName = call.getNameAsString();
                    // *** VERY SIMPLIFIED RESOLUTION ***
                    // Need to resolve 'call.getScope()' using imports, local variables, class
                    // hierarchy etc.
                    // For now, check direct dependencies of the class for a matching method name
                    List<CodeDependency> classDeps = graph.getDependenciesForNode(graph.getNodeKey(classNode));
                    for (CodeDependency dep : classDeps) {
                      if (dep.getTargetNode().endsWith("." + methodName)) {
                        calledKeys.add(dep.getTargetNode()); // Add FQN if resolved
                        break; // Simple assumption: first match wins
                      }
                    }
                    // If not resolved via direct deps, might be internal call or unresolved
                    // external
                    if (calledKeys.stream().noneMatch(k -> k.endsWith("." + methodName))) {
                      // Attempt to find method within the same class
                      classNode.getChildren().stream()
                          .filter(child -> "method".equals(child.getType()) && child.getName().equals(methodName))
                          .findFirst()
                          .ifPresent(internalMethod -> calledKeys.add(graph.getNodeKey(internalMethod)));
                    }
                  }
                });
              });
            });
      } else {
        log.warn("Could not find containing class or its source for method: {}", graph.getNodeKey(methodNode));
      }
    } catch (Exception e) {
      log.error("Error parsing method body for calls: {}", graph.getNodeKey(methodNode), e);
    }

    log.debug("Found called keys for {}: {}", graph.getNodeKey(methodNode), calledKeys);
    return List.copyOf(calledKeys);
  }

  private String buildExplanationPrompt(AstNode node, Map<String, String> calledFunctionSummaries) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("You are an expert Java code analyst.\n");
    prompt.append("Analyze the following Java method:\n\n");
    prompt.append("Method: ").append(node.getPackageName()).append(".").append(node.getName()).append("\n");
    prompt.append("Visibility: ").append(node.getVisibility()).append("\n");
    if (node.getReturnType() != null)
      prompt.append("Return Type: ").append(node.getReturnType()).append("\n");
    // Add other relevant metadata (static, abstract, etc.)

    prompt.append("\n--- Source Code ---\n");
    prompt.append("```java\n");
    prompt.append(node.getSourceCode() != null ? node.getSourceCode() : "[Source code not available]");
    prompt.append("\n```\n");

    if (!calledFunctionSummaries.isEmpty()) {
      prompt.append("\n--- Summaries of Called Functions ---\n");
      for (Map.Entry<String, String> entry : calledFunctionSummaries.entrySet()) {
        prompt.append("\nFunction: ").append(entry.getKey()).append("\nSummary:\n");
        prompt.append(entry.getValue()).append("\n");
        prompt.append("--------------------\n");
      }
    } else {
      prompt.append("\nThis function does not appear to call other functions analyzed in this context.\n");
    }

    prompt.append("\n--- Task ---\n");
    prompt.append("1. Provide a step-by-step explanation of the logic within the source code provided above (Method: ")
        .append(node.getName()).append(").\n");
    prompt.append("2. Describe the overall purpose of this method.\n");
    prompt.append(
        "3. Incorporate the summaries of the called functions (listed above) into your explanation to describe the end-to-end flow when this method is executed.\n");
    prompt.append(
        "4. Produce a concise, combined summary paragraph of what this method achieves, considering the actions of the functions it calls.\n");
    prompt.append("Ensure the explanation is detailed and follows the code's execution path.");

    return prompt.toString();
  }
}
