package sh.tbawor.javanalyser.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import sh.tbawor.javanalyser.model.VectorEmbedding;
import sh.tbawor.javanalyser.repository.VectorEmbeddingRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for VectorEmbeddingRepository using Testcontainers PostgreSQL.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
public class VectorEmbeddingRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine"))
            .withUsername("test")
            .withPassword("test")
            .withDatabaseName("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        // Ensure Hibernate creates the schema for tests (without dropping on shutdown)
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Autowired
    private VectorEmbeddingRepository repository;

    @Test
    void testRepositoryMethods() {
        // Prepare test data
        VectorEmbedding e1 = VectorEmbedding.builder()
                .nodeKey("pkg1.Class1")
                .filePath("path1")
                .type("class")
                .name("Class1")
                .packageName("pkg1")
                .sourceCodeSnippet("code1")
                .description("desc1")
                .embedding(new byte[]{1, 2, 3})
                .build();
        VectorEmbedding e2 = VectorEmbedding.builder()
                .nodeKey("pkg1.Class2")
                .filePath("path2")
                .type("class")
                .name("Class2")
                .packageName("pkg1.extra")
                .sourceCodeSnippet("")
                .description("desc2")
                .embedding(new byte[]{4, 5, 6})
                .build();
        VectorEmbedding e3 = VectorEmbedding.builder()
                .nodeKey("pkg2.Class3")
                .filePath("path3")
                .type("method")
                .name("method1")
                .packageName("pkg2")
                .sourceCodeSnippet("code3")
                .description("desc3")
                .embedding(new byte[]{7, 8, 9})
                .build();

        repository.saveAll(List.of(e1, e2, e3));

        // findByNodeKey
        Optional<VectorEmbedding> foundE1 = repository.findByNodeKey("pkg1.Class1");
        assertThat(foundE1).isPresent();
        assertThat(foundE1.get().getName()).isEqualTo("Class1");

        // findByPackageNameStartingWith
        List<VectorEmbedding> pkg1List = repository.findByPackageNameStartingWith("pkg1");
        assertThat(pkg1List).hasSize(2)
                .extracting(VectorEmbedding::getNodeKey)
                .containsExactlyInAnyOrder("pkg1.Class1", "pkg1.Class2");

        // findByType
        List<VectorEmbedding> classList = repository.findByType("class");
        assertThat(classList).hasSize(2);

        // findByName
        List<VectorEmbedding> nameList = repository.findByName("Class1");
        assertThat(nameList).hasSize(1)
                .extracting(VectorEmbedding::getNodeKey)
                .contains("pkg1.Class1");

        // findByTypeAndPackageNameStartingWith
        List<VectorEmbedding> typePkgList = repository.findByTypeAndPackageNameStartingWith("class", "pkg1");
        assertThat(typePkgList).hasSize(2);

        // findByFilePath
        List<VectorEmbedding> pathList = repository.findByFilePath("path1");
        assertThat(pathList).hasSize(1)
                .extracting(VectorEmbedding::getNodeKey)
                .contains("pkg1.Class1");

        // findAllWithSourceCode
        List<VectorEmbedding> withSrcList = repository.findAllWithSourceCode();
        assertThat(withSrcList).hasSize(2)
                .extracting(VectorEmbedding::getNodeKey)
                .containsExactlyInAnyOrder("pkg1.Class1", "pkg2.Class3");

        // findAllWithSourceCodeInPackage
        List<VectorEmbedding> withSrcPkg1 = repository.findAllWithSourceCodeInPackage("pkg1");
        assertThat(withSrcPkg1).hasSize(1)
                .extracting(VectorEmbedding::getNodeKey)
                .contains("pkg1.Class1");
    }
}