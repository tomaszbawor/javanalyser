package sh.tbawor.javanalyser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AstNode {
  private String type;
  private String name;
  private String filePath;
  private int lineNumber;
  private String packageName;
  @Builder.Default
  private List<AstNode> children = new ArrayList<>();
  @Builder.Default
  private List<CodeDependency> dependencies = new ArrayList<>();

  // Additional metadata depending on node type
  private String visibility; // public, private, protected, etc.
  private boolean isStatic;
  private boolean isInterface;
  private boolean isAbstract;
  private String returnType; // for methods

  // Source code context
  private String sourceCode;
  private int startLine;
  private int endLine;
}
