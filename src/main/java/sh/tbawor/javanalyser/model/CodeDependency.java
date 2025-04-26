package sh.tbawor.javanalyser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeDependency {
  private String type; // import, extends, implements, uses, calls, etc.
  private String sourceNode;
  private String targetNode;
  private String sourceFilePath;
  private String targetFilePath;
  private int sourceLine;
  private String description;
}
