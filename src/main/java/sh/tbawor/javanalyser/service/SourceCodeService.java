package sh.tbawor.javanalyser.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.model.DependencyGraph;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SourceCodeService {

  public String getSourceCodeForContext(String context, DependencyGraph graph) {
    StringBuilder sb = new StringBuilder();

    // Find all nodes that match the context
    List<AstNode> contextNodes = graph.getNodes().stream()
        .filter(node -> {
          String fullyQualifiedName = node.getPackageName() + "." + node.getName();
          return node.getPackageName().startsWith(context) ||
              fullyQualifiedName.equals(context) ||
              node.getName().equals(context);
        })
        .filter(node -> node.getSourceCode() != null && !node.getSourceCode().isEmpty())
        .collect(Collectors.toList());

    sb.append("Source code for context ").append(context).append(":\n\n");

    for (AstNode node : contextNodes) {
      sb.append("// ").append(node.getType()).append(": ").append(node.getPackageName())
          .append(".").append(node.getName()).append("\n");
      sb.append("```java\n");
      sb.append(node.getSourceCode());
      sb.append("\n```\n\n");
    }

    return sb.toString();
  }

  public String getSourceCodeHighlights(DependencyGraph graph) {
    StringBuilder sb = new StringBuilder();
    sb.append("Selected source code highlights:\n\n");

    // Get top-level classes and interfaces (limit to 5 for brevity)
    List<AstNode> topLevelNodes = graph.getNodes().stream()
        .filter(node -> "class".equals(node.getType()) || "interface".equals(node.getType()))
        .filter(node -> node.getSourceCode() != null && !node.getSourceCode().isEmpty())
        .limit(5)
        .collect(Collectors.toList());

    for (AstNode node : topLevelNodes) {
      sb.append("// ").append(node.getType()).append(": ").append(node.getPackageName())
          .append(".").append(node.getName()).append("\n");
      sb.append("```java\n");
      sb.append(node.getSourceCode());
      sb.append("\n```\n\n");
    }

    return sb.toString();
  }
}
