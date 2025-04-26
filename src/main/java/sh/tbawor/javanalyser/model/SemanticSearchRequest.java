package sh.tbawor.javanalyser.model;

import lombok.Data;

@Data
public class SemanticSearchRequest {
  private String query;
  private int maxResults = 5;
  private boolean includeCode = true;
  private String filterPackage; // Optional package filter
}
