package sh.tbawor.javanalyser.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "code_embeddings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorEmbedding {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String nodeKey; // For referencing AST node

  @Column(nullable = false)
  private String filePath;

  @Column(nullable = false)
  private String type; // class, method, field

  @Column(nullable = false, length = 1000)
  private String name;

  @Column(nullable = false)
  private String packageName;

  @Column(length = 10000)
  private String sourceCodeSnippet;

  @Column(length = 1000)
  private String description;

  @Lob
  @Column(columnDefinition = "BLOB")
  private byte[] embedding;
}
