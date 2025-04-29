package sh.tbawor.javanalyser.parser;

import org.junit.jupiter.api.Test;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import sh.tbawor.javanalyser.model.AstNode;

import static org.junit.jupiter.api.Assertions.*;

public class JavaAstParserTest {

    private final JavaAstParser parser = new JavaAstParser();

    @Test
    void testParseSimpleClass() throws Exception {
        URL resource = getClass().getClassLoader().getResource("Hello.java");
        assertNotNull(resource, "Test resource Hello.java not found");
        Path path = Paths.get(resource.toURI());

        List<AstNode> nodes = parser.parseFile(path);
        assertEquals(2, nodes.size(), "Expected 2 AST nodes (class + method)");

        AstNode classNode = nodes.get(0);
        assertEquals("class", classNode.getType());
        assertEquals("Hello", classNode.getName());
        assertEquals("com.example.test", classNode.getPackageName());
        assertEquals(path.toString(), classNode.getFilePath());

        List<AstNode> children = classNode.getChildren();
        assertEquals(1, children.size(), "Expected 1 child (method)");

        AstNode methodNode = children.get(0);
        assertEquals("method", methodNode.getType());
        assertEquals("greet", methodNode.getName());
        assertEquals("String", methodNode.getReturnType());
        assertEquals("public", methodNode.getVisibility());
    }

    @Test
    void testParseFieldAndConstructor() throws Exception {
        URL resource = getClass().getClassLoader().getResource("Sample.java");
        assertNotNull(resource, "Test resource Sample.java not found");
        Path path = Paths.get(resource.toURI());

        List<AstNode> nodes = parser.parseFile(path);
        assertEquals(4, nodes.size(), "Expected 4 AST nodes (class + field + constructor + method)");

        AstNode classNode = nodes.get(0);
        assertEquals("Sample", classNode.getName());

        List<AstNode> children = classNode.getChildren();
        assertEquals(3, children.size(), "Expected 3 children (field, constructor, method)");

        AstNode fieldNode = children.stream()
                .filter(n -> "field".equals(n.getType()))
                .findFirst().orElse(null);
        assertNotNull(fieldNode, "Field node not found");
        assertEquals("x", fieldNode.getName());
        assertEquals("int", fieldNode.getReturnType());
        assertEquals("private", fieldNode.getVisibility());

        AstNode constructorNode = children.stream()
                .filter(n -> "constructor".equals(n.getType()))
                .findFirst().orElse(null);
        assertNotNull(constructorNode, "Constructor node not found");
        assertEquals("Sample", constructorNode.getName());
        assertEquals("public", constructorNode.getVisibility());
    }
}