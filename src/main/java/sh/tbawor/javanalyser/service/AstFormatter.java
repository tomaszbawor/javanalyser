package sh.tbawor.javanalyser.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.model.CodeDependency;
import sh.tbawor.javanalyser.model.DependencyGraph;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Formats AST data into structured representations suitable for LLM consumption
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AstFormatter {

  private final ObjectMapper objectMapper;

  /**
   * Format the entire dependency graph for LLM consumption
   */
  public String formatGraph(DependencyGraph graph) {
    try {
      ObjectNode rootNode = objectMapper.createObjectNode();

      // Add summary information
      rootNode.put("nodeCount", graph.getNodes().size());
      rootNode.put("edgeCount", graph.getEdges().size());

      // Create summary of packages
      ArrayNode packagesArray = rootNode.putArray("packages");
      graph.getNodes().stream()
          .map(AstNode::getPackageName)
          .distinct()
          .sorted()
          .forEach(packagesArray::add);

      // Add high-level nodes (classes and interfaces only, not methods or fields)
      ArrayNode nodesArray = rootNode.putArray("mainComponents");
      graph.getNodes().stream()
          .filter(node -> "class".equals(node.getType()) || "interface".equals(node.getType()))
          .forEach(node -> nodesArray.add(formatNode(node)));

      // Add key dependencies
      ArrayNode edgesArray = rootNode.putArray("keyDependencies");
      graph.getEdges().stream()
          .filter(edge -> "extends".equals(edge.getType()) ||
              "implements".equals(edge.getType()) ||
              "import".equals(edge.getType()))
          .limit(50) // Limit to keep the output reasonable
          .forEach(edge -> edgesArray.add(formatDependency(edge)));

      // Return formatted JSON
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
    } catch (Exception e) {
      log.error("Error formatting graph", e);
      return "Error formatting graph: " + e.getMessage();
    }
  }

  /**
   * Format a filtered graph based on a context (package or class name)
   */
  public String formatFilteredGraph(DependencyGraph graph, String context) {
    try {
      // Filter nodes to those in the given context
      List<AstNode> filteredNodes = graph.getNodes().stream()
          .filter(node -> {
            String fullyQualifiedName = node.getPackageName() + "." + node.getName();
            return node.getPackageName().startsWith(context) ||
                fullyQualifiedName.equals(context) ||
                node.getName().equals(context);
          })
          .collect(Collectors.toList());

      // Get the fully qualified names of all filtered nodes
      List<String> nodeNames = filteredNodes.stream()
          .map(node -> node.getPackageName() + "." + node.getName())
          .collect(Collectors.toList());

      // Filter edges to those connecting filtered nodes
      List<CodeDependency> filteredEdges = graph.getEdges().stream()
          .filter(edge -> nodeNames.contains(edge.getSourceNode()) ||
              nodeNames.contains(edge.getTargetNode()))
          .collect(Collectors.toList());

      // Create a new object node for the filtered graph
      ObjectNode rootNode = objectMapper.createObjectNode();

      // Add summary information
      rootNode.put("context", context);
      rootNode.put("nodeCount", filteredNodes.size());
      rootNode.put("edgeCount", filteredEdges.size());

      // Add nodes
      ArrayNode nodesArray = rootNode.putArray("nodes");
      for (AstNode node : filteredNodes) {
        nodesArray.add(formatNode(node));
      }

      // Add edges
      ArrayNode edgesArray = rootNode.putArray("dependencies");
      for (CodeDependency edge : filteredEdges) {
        edgesArray.add(formatDependency(edge));
      }

      // Return formatted JSON
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
    } catch (Exception e) {
      log.error("Error formatting filtered graph", e);
      return "Error formatting filtered graph: " + e.getMessage();
    }
  }

  /**
   * Format a node for JSON representation
   */
  private ObjectNode formatNode(AstNode node) {
    ObjectNode nodeObj = objectMapper.createObjectNode();

    nodeObj.put("type", node.getType());
    nodeObj.put("name", node.getName());
    nodeObj.put("packageName", node.getPackageName());
    nodeObj.put("fqn", node.getPackageName() + "." + node.getName());
    nodeObj.put("filePath", node.getFilePath());
    nodeObj.put("lineNumber", node.getLineNumber());

    // Add optional properties if they exist
    if (node.getVisibility() != null) {
      nodeObj.put("visibility", node.getVisibility());
    }

    if (node.getReturnType() != null) {
      nodeObj.put("returnType", node.getReturnType());
    }

    nodeObj.put("isStatic", node.isStatic());
    nodeObj.put("isInterface", node.isInterface());
    nodeObj.put("isAbstract", node.isAbstract());

    // Add children summary if there are children
    if (!node.getChildren().isEmpty()) {
      ArrayNode childrenArray = nodeObj.putArray("children");
      node.getChildren().forEach(child -> {
        ObjectNode childObj = objectMapper.createObjectNode();
        childObj.put("type", child.getType());
        childObj.put("name", child.getName());
        if (child.getVisibility() != null) {
          childObj.put("visibility", child.getVisibility());
        }
        if (child.getReturnType() != null) {
          childObj.put("returnType", child.getReturnType());
        }
        childrenArray.add(childObj);
      });
    }

    // Add dependency summary
    if (!node.getDependencies().isEmpty()) {
      ArrayNode dependenciesArray = nodeObj.putArray("dependencies");
      node.getDependencies().stream()
          .limit(10) // Limit dependencies to keep the output manageable
          .forEach(dep -> {
            ObjectNode depObj = objectMapper.createObjectNode();
            depObj.put("type", dep.getType());
            depObj.put("target", dep.getTargetNode());
            dependenciesArray.add(depObj);
          });
    }

    // Add source code snippet if available
    if (node.getSourceCode() != null && !node.getSourceCode().isEmpty()) {
      // Only include a brief snippet in the node representation
      String snippet = node.getSourceCode();
      if (snippet.length() > 300) {
        snippet = snippet.substring(0, 300) + "...";
      }
      nodeObj.put("codeSnippet", snippet);
    }

    return nodeObj;
  }

  /**
   * Format a dependency for JSON representation
   */
  private ObjectNode formatDependency(CodeDependency dependency) {
    ObjectNode edgeObj = objectMapper.createObjectNode();

    edgeObj.put("type", dependency.getType());
    edgeObj.put("source", dependency.getSourceNode());
    edgeObj.put("target", dependency.getTargetNode());
    edgeObj.put("sourceLine", dependency.getSourceLine());
    edgeObj.put("description", dependency.getDescription());

    return edgeObj;
  }

  /**
   * Format a node with detailed information including all source code
   */
  public String formatDetailedNode(AstNode node) {
    try {
      ObjectNode nodeObj = objectMapper.createObjectNode();

      // Basic information
      nodeObj.put("type", node.getType());
      nodeObj.put("name", node.getName());
      nodeObj.put("packageName", node.getPackageName());
      nodeObj.put("fqn", node.getPackageName() + "." + node.getName());
      nodeObj.put("filePath", node.getFilePath());
      nodeObj.put("lineNumber", node.getLineNumber());

      // Add optional properties if they exist
      if (node.getVisibility() != null) {
        nodeObj.put("visibility", node.getVisibility());
      }

      if (node.getReturnType() != null) {
        nodeObj.put("returnType", node.getReturnType());
      }

      nodeObj.put("isStatic", node.isStatic());
      nodeObj.put("isInterface", node.isInterface());
      nodeObj.put("isAbstract", node.isAbstract());

      // Add complete source code
      if (node.getSourceCode() != null && !node.getSourceCode().isEmpty()) {
        nodeObj.put("sourceCode", node.getSourceCode());
      }

      // Add all children with their source code
      if (!node.getChildren().isEmpty()) {
        ArrayNode childrenArray = nodeObj.putArray("children");
        node.getChildren().forEach(child -> {
          ObjectNode childObj = objectMapper.createObjectNode();
          childObj.put("type", child.getType());
          childObj.put("name", child.getName());
          if (child.getVisibility() != null) {
            childObj.put("visibility", child.getVisibility());
          }
          if (child.getReturnType() != null) {
            childObj.put("returnType", child.getReturnType());
          }
          if (child.getSourceCode() != null && !child.getSourceCode().isEmpty()) {
            childObj.put("sourceCode", child.getSourceCode());
          }
          childrenArray.add(childObj);
        });
      }

      // Add all dependencies
      if (!node.getDependencies().isEmpty()) {
        ArrayNode dependenciesArray = nodeObj.putArray("dependencies");
        node.getDependencies().forEach(dep -> {
          ObjectNode depObj = objectMapper.createObjectNode();
          depObj.put("type", dep.getType());
          depObj.put("target", dep.getTargetNode());
          depObj.put("description", dep.getDescription());
          dependenciesArray.add(depObj);
        });
      }

      // Return formatted JSON
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(nodeObj);
    } catch (Exception e) {
      log.error("Error formatting detailed node", e);
      return "Error formatting detailed node: " + e.getMessage();
    }
  }

  /**
   * Format a summary view of multiple nodes for quick overview
   */
  public String formatNodesSummary(List<AstNode> nodes) {
    try {
      ObjectNode rootNode = objectMapper.createObjectNode();

      rootNode.put("count", nodes.size());

      // Group by type
      ObjectNode typeCountNode = rootNode.putObject("typeCount");
      nodes.stream()
          .collect(Collectors.groupingBy(AstNode::getType, Collectors.counting()))
          .forEach(typeCountNode::put);

      // Group by package
      ObjectNode packageCountNode = rootNode.putObject("packageCount");
      nodes.stream()
          .collect(Collectors.groupingBy(AstNode::getPackageName, Collectors.counting()))
          .forEach(packageCountNode::put);

      // Add node summaries
      ArrayNode nodesArray = rootNode.putArray("nodes");
      nodes.forEach(node -> {
        ObjectNode nodeObj = objectMapper.createObjectNode();
        nodeObj.put("type", node.getType());
        nodeObj.put("name", node.getName());
        nodeObj.put("packageName", node.getPackageName());
        nodesArray.add(nodeObj);
      });

      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
    } catch (Exception e) {
      log.error("Error formatting nodes summary", e);
      return "Error formatting nodes summary: " + e.getMessage();
    }
  }
}
