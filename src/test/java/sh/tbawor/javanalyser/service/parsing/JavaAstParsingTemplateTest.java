package sh.tbawor.javanalyser.service.parsing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.model.CodeDependency;
import sh.tbawor.javanalyser.model.DependencyGraph;
import sh.tbawor.javanalyser.parser.JavaAstParser;
import sh.tbawor.javanalyser.parser.SourceCodeExtractor;
import sh.tbawor.javanalyser.service.DependencyExtractor;
import sh.tbawor.javanalyser.service.VectorEmbeddingService;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for the JavaAstParsingTemplate class.
 */
@ExtendWith(MockitoExtension.class)
public class JavaAstParsingTemplateTest {

    @Mock
    private JavaAstParser astParser;

    @Mock
    private DependencyExtractor dependencyExtractor;

    @Mock
    private SourceCodeExtractor sourceCodeExtractor;

    @Mock
    private VectorEmbeddingService vectorEmbeddingService;

    private JavaAstParsingTemplate template;

    @BeforeEach
    void setUp() {
        // Create a test subclass that overrides findJavaFiles to avoid file system access
        template = new JavaAstParsingTemplate(
                astParser,
                dependencyExtractor,
                sourceCodeExtractor,
                vectorEmbeddingService
        ) {
            @Override
            protected List<Path> findJavaFiles(String projectPath) {
                // For testParseProject and testParseProjectHandlesExceptions
                if ("/path/to/project".equals(projectPath)) {
                    Path file1 = Paths.get("/path/to/project/File1.java");
                    Path file2 = Paths.get("/path/to/project/File2.java");
                    return Arrays.asList(file1, file2);
                }
                // For testParseProjectWithMaxNodes
                else {
                    return createTestFiles(150);
                }
            }
        };

        // Set the field values using reflection, specifying the correct class
        setFieldInClass(template, JavaAstParsingTemplate.class, "batchSize", 10);
        setFieldInClass(template, JavaAstParsingTemplate.class, "maxNodes", 100);
        setFieldInClass(template, JavaAstParsingTemplate.class, "progressLogInterval", 5);
    }

    @Test
    void testParseProject() {
        // Given
        String projectPath = "/path/to/project";

        // Mock finding Java files
        Path file1 = Paths.get("/path/to/project/File1.java");
        Path file2 = Paths.get("/path/to/project/File2.java");
        List<Path> javaFiles = Arrays.asList(file1, file2);

        // Mock parsing AST nodes
        AstNode node1 = createTestNode("Class1", "test.package", "class");
        AstNode node2 = createTestNode("Class2", "test.package", "class");
        List<AstNode> file1Nodes = Collections.singletonList(node1);
        List<AstNode> file2Nodes = Collections.singletonList(node2);

        when(astParser.parseFile(file1)).thenReturn(file1Nodes);
        when(astParser.parseFile(file2)).thenReturn(file2Nodes);

        // Mock extracting dependencies
        CodeDependency dependency = CodeDependency.builder()
                .type("USES")
                .sourceNode("test.package.Class1")
                .targetNode("test.package.Class2")
                .sourceFilePath(file1.toString())
                .targetFilePath(file2.toString())
                .sourceLine(1)
                .description("Class1 uses Class2")
                .build();
        when(dependencyExtractor.extractDependencies(eq(file1), anyList())).thenReturn(Collections.singletonList(dependency));
        when(dependencyExtractor.extractDependencies(eq(file2), anyList())).thenReturn(Collections.emptyList());

        // When
        DependencyGraph graph = template.parseProject(projectPath);

        // Then
        assertThat(graph).isNotNull();
        assertThat(graph.getNodes()).hasSize(2);
        assertThat(graph.getEdges()).hasSize(1);

        // Verify interactions
        verify(astParser).parseFile(file1);
        verify(astParser).parseFile(file2);
        verify(dependencyExtractor).extractDependencies(eq(file1), anyList());
        verify(dependencyExtractor).extractDependencies(eq(file2), anyList());
        verify(sourceCodeExtractor).extractSourceCode(file1, graph);
        verify(sourceCodeExtractor).extractSourceCode(file2, graph);
        verify(dependencyExtractor).extractCrossFileDependencies(graph);
        verify(vectorEmbeddingService).createEmbeddingsFromGraph(graph);
    }

    @Test
    void testParseProjectWithMaxNodes() {
        // Given
        String projectPath = "/path/to/project";

        // Create a large number of files
        List<Path> javaFiles = createTestFiles(150); // 150 files, but max is 100 nodes

        // Mock parsing AST nodes - each file has one node
        for (Path file : javaFiles) {
            AstNode node = createTestNode("Class" + file.getFileName(), "test.package", "class");
            when(astParser.parseFile(file)).thenReturn(Collections.singletonList(node));
            when(dependencyExtractor.extractDependencies(eq(file), anyList())).thenReturn(Collections.emptyList());
        }

        // When
        DependencyGraph graph = template.parseProject(projectPath);

        // Then
        assertThat(graph).isNotNull();
        assertThat(graph.getNodes()).hasSize(100); // Should be limited to 100

        // Verify interactions - should only process up to max nodes
        verify(astParser, times(100)).parseFile(any(Path.class));
        verify(dependencyExtractor, times(100)).extractDependencies(any(Path.class), anyList());
        verify(sourceCodeExtractor, times(100)).extractSourceCode(any(Path.class), any(DependencyGraph.class));
        verify(dependencyExtractor).extractCrossFileDependencies(graph);
        verify(vectorEmbeddingService).createEmbeddingsFromGraph(graph);
    }

    @Test
    void testParseProjectHandlesExceptions() {
        // Given
        String projectPath = "/path/to/project";

        // Mock finding Java files
        Path file1 = Paths.get("/path/to/project/File1.java");
        Path file2 = Paths.get("/path/to/project/File2.java");
        List<Path> javaFiles = Arrays.asList(file1, file2);

        // Mock parsing AST nodes - first file throws exception
        when(astParser.parseFile(file1)).thenThrow(new RuntimeException("Test exception"));

        AstNode node2 = createTestNode("Class2", "test.package", "class");
        List<AstNode> file2Nodes = Collections.singletonList(node2);
        when(astParser.parseFile(file2)).thenReturn(file2Nodes);

        when(dependencyExtractor.extractDependencies(eq(file2), anyList())).thenReturn(Collections.emptyList());

        // When
        DependencyGraph graph = template.parseProject(projectPath);

        // Then
        assertThat(graph).isNotNull();
        assertThat(graph.getNodes()).hasSize(1); // Only one file processed successfully

        // Verify interactions
        verify(astParser).parseFile(file1);
        verify(astParser).parseFile(file2);
        verify(dependencyExtractor, never()).extractDependencies(eq(file1), anyList());
        verify(dependencyExtractor).extractDependencies(eq(file2), anyList());
        verify(sourceCodeExtractor, never()).extractSourceCode(eq(file1), any(DependencyGraph.class));
        verify(sourceCodeExtractor).extractSourceCode(file2, graph);
        verify(dependencyExtractor).extractCrossFileDependencies(graph);
        verify(vectorEmbeddingService).createEmbeddingsFromGraph(graph);
    }

    private AstNode createTestNode(String name, String packageName, String type) {
        return AstNode.builder()
                .name(name)
                .packageName(packageName)
                .type(type)
                .filePath("path/to/" + name + ".java")
                .sourceCode("public class " + name + " {}")
                .build();
    }

    private List<Path> createTestFiles(int count) {
        Path[] files = new Path[count];
        for (int i = 0; i < count; i++) {
            files[i] = Paths.get("/path/to/project/File" + i + ".java");
        }
        return Arrays.asList(files);
    }

    private void setField(Object target, String fieldName, Object value) {
        setFieldInClass(target, target.getClass(), fieldName, value);
    }

    private void setFieldInClass(Object target, Class<?> clazz, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName + " in class " + clazz.getName(), e);
        }
    }
}
