package sh.tbawor.javanalyser.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import com.github.javaparser.ast.Modifier;
import lombok.extern.slf4j.Slf4j;
import sh.tbawor.javanalyser.model.AstNode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Parser for Java source files that extracts AST nodes using JavaParser library.
 * Converts Java source code constructs into AstNode objects that can be processed
 * by the dependency analyzer.
 */
@Component
@Slf4j
public class JavaAstParser {

  @Value("${parser.timeout.seconds:30}")
  private int parserTimeoutSeconds;

  @Value("${parser.max.file.size.kb:1000}")
  private int maxFileSizeKb;

  @Value("${parser.log.interval:10}")
  private int logInterval;

  /**
   * Parse a Java file and extract AST nodes
   * 
   * @param filePath Path to the Java file
   * @return List of AST nodes extracted from the file
   */
  public List<AstNode> parseFile(Path filePath) {
    List<AstNode> nodes = new ArrayList<>();
    long startTime = System.currentTimeMillis();

    log.debug("Starting to parse file: {}", filePath);

    if (isFileTooLarge(filePath)) {
      return nodes;
    }

    try {
      CompilationUnit compilationUnit = parseCompilationUnit(filePath);
      if (compilationUnit != null) {
        processCompilationUnit(compilationUnit, filePath, nodes);
        logParsingStats(startTime, filePath, nodes.size());
      }
    } catch (IOException e) {
      log.error("Error reading file: {}", filePath, e);
    } catch (Exception e) {
      log.error("Unexpected error parsing file: {}", filePath, e);
    }

    return nodes;
  }

  /**
   * Checks if the file exceeds the maximum size limit
   * 
   * @param filePath Path to check
   * @return true if file is too large, false otherwise
   */
  private boolean isFileTooLarge(Path filePath) {
    try {
      long fileSizeKb = filePath.toFile().length() / 1024;
      if (fileSizeKb > maxFileSizeKb) {
        log.warn("File too large to parse: {} ({}KB > {}KB limit)", filePath, fileSizeKb, maxFileSizeKb);
        return true;
      }
      return false;
    } catch (Exception e) {
      log.error("Error checking file size: {}", filePath, e);
      return true;
    }
  }

  /**
   * Parses a Java file into a CompilationUnit
   * 
   * @param filePath Path to the Java file
   * @return CompilationUnit if parsing was successful, null otherwise
   * @throws IOException if file cannot be read
   */
  private CompilationUnit parseCompilationUnit(Path filePath) throws IOException {
    JavaParser parser = new JavaParser();
    ParseResult<CompilationUnit> parseResult = parser.parse(filePath);

    if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
      CompilationUnit cu = parseResult.getResult().get();
      log.debug("Successfully parsed compilation unit for: {}", filePath);
      return cu;
    } else {
      log.warn("Failed to parse file: {}, errors: {}", filePath, parseResult.getProblems());
      return null;
    }
  }

  /**
   * Process a parsed compilation unit to extract AST nodes
   * 
   * @param cu The parsed compilation unit
   * @param filePath Path to the source file
   * @param nodes List to populate with extracted nodes
   */
  private void processCompilationUnit(CompilationUnit cu, Path filePath, List<AstNode> nodes) {
    String packageName = cu.getPackageDeclaration()
        .map(pd -> pd.getName().asString())
        .orElse("");

    AstVisitor visitor = new AstVisitor(filePath.toString(), packageName);
    cu.accept(visitor, nodes);
  }

  /**
   * Log statistics about the parsing operation
   * 
   * @param startTime Time when parsing started
   * @param filePath Path to the parsed file
   * @param nodeCount Number of nodes extracted
   */
  private void logParsingStats(long startTime, Path filePath, int nodeCount) {
    long duration = System.currentTimeMillis() - startTime;
    log.debug("Parsed file {} in {}ms, extracted {} nodes", filePath, duration, nodeCount);
  }

  /**
   * Visitor implementation that traverses the AST and creates AstNode objects
   */
  private static class AstVisitor extends VoidVisitorAdapter<List<AstNode>> {
    private final String filePath;
    private final String packageName;

    public AstVisitor(String filePath, String packageName) {
      this.filePath = filePath;
      this.packageName = packageName;
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration n, List<AstNode> nodes) {
      AstNode classNode = createClassNode(n);
      nodes.add(classNode);

      // Process class members
      List<AstNode> children = new ArrayList<>();
      n.getFields().forEach(f -> visit(f, children));
      n.getConstructors().forEach(c -> visit(c, children));
      n.getMethods().forEach(m -> visit(m, children));

      // Set children to parent node
      classNode.setChildren(children);

      // Continue with super visit
      super.visit(n, nodes);
    }

    private AstNode createClassNode(ClassOrInterfaceDeclaration n) {
      AstNode classNode = AstNode.builder()
          .type(n.isInterface() ? "interface" : "class")
          .name(n.getNameAsString())
          .filePath(filePath)
          .lineNumber(getNodeLineNumber(n))
          .packageName(packageName)
          .visibility(getVisibility(n))
          .isInterface(n.isInterface())
          .isAbstract(n.isAbstract())
          .build();

      setNodeLineRange(n, classNode);
      return classNode;
    }

    @Override
    public void visit(FieldDeclaration n, List<AstNode> nodes) {
      n.getVariables().forEach(v -> {
        AstNode fieldNode = AstNode.builder()
            .type("field")
            .name(v.getNameAsString())
            .filePath(filePath)
            .lineNumber(getNodeLineNumber(n))
            .packageName(packageName)
            .visibility(getVisibility(n))
            .isStatic(n.isStatic())
            .returnType(n.getElementType().asString())
            .build();

        setNodeLineRange(n, fieldNode);
        nodes.add(fieldNode);
      });

      super.visit(n, nodes);
    }

    @Override
    public void visit(MethodDeclaration n, List<AstNode> nodes) {
      AstNode methodNode = AstNode.builder()
          .type("method")
          .name(n.getNameAsString())
          .filePath(filePath)
          .lineNumber(getNodeLineNumber(n))
          .packageName(packageName)
          .visibility(getVisibility(n))
          .isStatic(n.isStatic())
          .isAbstract(n.isAbstract())
          .returnType(n.getType().asString())
          .build();

      setNodeLineRange(n, methodNode);
      nodes.add(methodNode);
      super.visit(n, nodes);
    }

    @Override
    public void visit(ConstructorDeclaration n, List<AstNode> nodes) {
      AstNode constructorNode = AstNode.builder()
          .type("constructor")
          .name(n.getNameAsString())
          .filePath(filePath)
          .lineNumber(getNodeLineNumber(n))
          .packageName(packageName)
          .visibility(getVisibility(n))
          .build();

      setNodeLineRange(n, constructorNode);
      nodes.add(constructorNode);
      super.visit(n, nodes);
    }

    /**
     * Gets the line number for a node, defaulting to 0 if not available
     */
    private int getNodeLineNumber(com.github.javaparser.ast.Node node) {
      return node.getBegin().map(pos -> pos.line).orElse(0);
    }

    /**
     * Sets the start and end line for a node
     */
    private void setNodeLineRange(com.github.javaparser.ast.Node javaParserNode, AstNode astNode) {
      javaParserNode.getBegin().ifPresent(begin -> astNode.setStartLine(begin.line));
      javaParserNode.getEnd().ifPresent(end -> astNode.setEndLine(end.line));
    }

    /**
     * Determines the visibility modifier of a declaration
     */
    private String getVisibility(com.github.javaparser.ast.body.BodyDeclaration<?> declaration) {
      if (declaration instanceof NodeWithModifiers) {
        NodeWithModifiers<?> nodeWithModifiers = (NodeWithModifiers<?>) declaration;

        if (nodeWithModifiers.hasModifier(Modifier.Keyword.PUBLIC)) {
          return "public";
        }
        if (nodeWithModifiers.hasModifier(Modifier.Keyword.PRIVATE)) {
          return "private";
        }
        if (nodeWithModifiers.hasModifier(Modifier.Keyword.PROTECTED)) {
          return "protected";
        }
        return "package-private";
      } else {
        return "package-private";
      }
    }
  }
}
