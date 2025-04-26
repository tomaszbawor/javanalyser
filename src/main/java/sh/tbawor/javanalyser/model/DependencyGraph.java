package sh.tbawor.javanalyser.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class DependencyGraph {
  private List<AstNode> nodes = new ArrayList<>();
  private List<CodeDependency> edges = new ArrayList<>();
  private Map<String, AstNode> nodeIndex = new HashMap<>();

  public void addNode(AstNode node) {
    nodes.add(node);
    nodeIndex.put(getNodeKey(node), node);
  }

  public void addDependency(CodeDependency dependency) {
    edges.add(dependency);

    // Update source and target nodes with this dependency
    AstNode sourceNode = nodeIndex.get(dependency.getSourceNode());
    if (sourceNode != null) {
      sourceNode.getDependencies().add(dependency);
    }
  }

  private String getNodeKey(AstNode node) {
    return node.getPackageName() + "." + node.getName();
  }

  public List<CodeDependency> getDependenciesForNode(String nodeName) {
    return nodeIndex.getOrDefault(nodeName, new AstNode()).getDependencies();
  }

  public AstNode getNodeByKey(String key) {
    return nodeIndex.get(key);
  }
}
