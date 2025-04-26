package sh.tbawor.javanalyser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceCodeContext {
  private String filePath;
  private String className;
  private String packageName;
  private String sourceCode;
  private String type; // class, interface, enum, etc.
  private int startLine;
  private int endLine;
}
