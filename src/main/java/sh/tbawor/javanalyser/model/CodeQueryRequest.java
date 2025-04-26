package sh.tbawor.javanalyser.model;

import lombok.Data;

@Data
public class CodeQueryRequest {
  private String query;
  private String context; // Optional, e.g., specific class or package to focus on
  private boolean includeSourceCode = true; // Now default to true
  private boolean useSemanticSearch = true; // Use vector search
  private int maxTokens = 4000;
  private int maxResults = 5; // For semantic search
}
