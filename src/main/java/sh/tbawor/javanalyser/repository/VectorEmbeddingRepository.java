package sh.tbawor.javanalyser.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import sh.tbawor.javanalyser.model.VectorEmbedding;

import java.util.List;
import java.util.Optional;

@Repository
public interface VectorEmbeddingRepository extends JpaRepository<VectorEmbedding, Long> {

  /**
   * Find an embedding by its node key
   */
  Optional<VectorEmbedding> findByNodeKey(String nodeKey);

  /**
   * Find embeddings by package name prefix
   */
  List<VectorEmbedding> findByPackageNameStartingWith(String packagePrefix);

  /**
   * Find embeddings by type
   */
  List<VectorEmbedding> findByType(String type);

  /**
   * Find embeddings by name
   */
  List<VectorEmbedding> findByName(String name);

  /**
   * Find embeddings by type and package prefix
   */
  List<VectorEmbedding> findByTypeAndPackageNameStartingWith(String type, String packagePrefix);

  /**
   * Find embeddings by file path
   */
  List<VectorEmbedding> findByFilePath(String filePath);

  /**
   * Custom query to find embeddings with source code
   */
  @Query("SELECT v FROM VectorEmbedding v WHERE v.sourceCodeSnippet IS NOT NULL AND v.sourceCodeSnippet <> ''")
  List<VectorEmbedding> findAllWithSourceCode();

  /**
   * Custom query to find embeddings with source code in a specific package
   */
  @Query("SELECT v FROM VectorEmbedding v WHERE v.packageName LIKE :packagePrefix% AND v.sourceCodeSnippet IS NOT NULL AND v.sourceCodeSnippet <> ''")
  List<VectorEmbedding> findAllWithSourceCodeInPackage(@Param("packagePrefix") String packagePrefix);
}
