package sh.tbawor.javanalyser.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import lombok.extern.slf4j.Slf4j;
import sh.tbawor.javanalyser.model.AstNode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    try {
      // Check file size to avoid processing extremely large files
      long fileSizeKb = filePath.toFile().length() / 1024;
      if (fileSizeKb > maxFileSizeKb) {
        log.warn("File too large to parse: {} ({}KB > {}KB limit)", filePath, fileSizeKb, maxFileSizeKb);
        return nodes;
      }

      // Parse with timeout to avoid hanging on complex files
      JavaParser parser = new JavaParser();
      ParseResult<CompilationUnit> parseResult = parser.parse(filePath);

      if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
        CompilationUnit cu = parseResult.getResult().get();
        log.debug("Successfully parsed compilation unit for: {}", filePath);

        // Extract package name
        String packageName = cu.getPackageDeclaration()
            .map(pd -> pd.getName().asString())
            .orElse("");

        // Create visitor to extract nodes
        AstVisitor visitor = new AstVisitor(filePath.toString(), packageName);
        cu.accept(visitor, nodes);

        long duration = System.currentTimeMillis() - startTime;
        log.debug("Parsed file {} in {}ms, extracted {} nodes", filePath, duration, nodes.size());
      } else {
        log.warn("Failed to parse file: {}, errors: {}", filePath, parseResult.getProblems());
      }
    } catch (IOException e) {
      log.error("Error reading file: {}", filePath, e);
    } catch (Exception e) {
      log.error("Unexpected error parsing file: {}", filePath, e);
    }

    return nodes;
  }

  private static class AstVisitor extends VoidVisitorAdapter<List<AstNode>> {
    private final String filePath;
    private final String packageName;

    public AstVisitor(String filePath, String packageName) {
      this.filePath = filePath;
      this.packageName = packageName;
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration n, List<AstNode> nodes) {
      // Create AST node for the class/interface
      AstNode classNode = AstNode.builder()
          .type(n.isInterface() ? "interface" : "class")
          .name(n.getNameAsString())
          .filePath(filePath)
          .lineNumber(n.getBegin().map(pos -> pos.line).orElse(0))
          .packageName(packageName)
          .visibility(getVisibility(n))
          .isInterface(n.isInterface())
          .isAbstract(n.isAbstract())
          .build();

      // Store line information for source code extraction
      n.getBegin().ifPresent(begin -> classNode.setStartLine(begin.line));
      n.getEnd().ifPresent(end -> classNode.setEndLine(end.line));

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

    @Override
    public void visit(FieldDeclaration n, List<AstNode> nodes) {
      n.getVariables().forEach(v -> {
        AstNode fieldNode = AstNode.builder()
            .type("field")
            .name(v.getNameAsString())
            .filePath(filePath)
            .lineNumber(n.getBegin().map(pos -> pos.line).orElse(0))
            .packageName(packageName)
            .visibility(getVisibility(n))
            .isStatic(n.isStatic())
            .returnType(n.getElementType().asString())
            .build();

        // Store line information for source code extraction
        n.getBegin().ifPresent(begin -> fieldNode.setStartLine(begin.line));
        n.getEnd().ifPresent(end -> fieldNode.setEndLine(end.line));

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
          .lineNumber(n.getBegin().map(pos -> pos.line).orElse(0))
          .packageName(packageName)
          .visibility(getVisibility(n))
          .isStatic(n.isStatic())
          .isAbstract(n.isAbstract())
          .returnType(n.getType().asString())
          .build();

      // Store line information for source code extraction
      n.getBegin().ifPresent(begin -> methodNode.setStartLine(begin.line));
      n.getEnd().ifPresent(end -> methodNode.setEndLine(end.line));

      nodes.add(methodNode);
      super.visit(n, nodes);
    }

    @Override
    public void visit(ConstructorDeclaration n, List<AstNode> nodes) {
      AstNode constructorNode = AstNode.builder()
          .type("constructor")
          .name(n.getNameAsString())
          .filePath(filePath)
          .lineNumber(n.getBegin().map(pos -> pos.line).orElse(0))
          .packageName(packageName)
          .visibility(getVisibility(n))
          .build();

      // Store line information for source code extraction
      n.getBegin().ifPresent(begin -> constructorNode.setStartLine(begin.line));
      n.getEnd().ifPresent(end -> constructorNode.setEndLine(end.line));

      nodes.add(constructorNode);
      super.visit(n, nodes);
    }

    private String getVisibility(com.github.javaparser.ast.body.BodyDeclaration<?> declaration) {
      if (declaration instanceof com.github.javaparser.ast.nodeTypes.NodeWithModifiers) {
        com.github.javaparser.ast.nodeTypes.NodeWithModifiers<?> nodeWithModifiers = (com.github.javaparser.ast.nodeTypes.NodeWithModifiers<?>) declaration;

        if (nodeWithModifiers.hasModifier(com.github.javaparser.ast.Modifier.Keyword.PUBLIC)) {
          return "public";
        }
        if (nodeWithModifiers.hasModifier(com.github.javaparser.ast.Modifier.Keyword.PRIVATE)) {
          return "private";
        }
        if (nodeWithModifiers.hasModifier(com.github.javaparser.ast.Modifier.Keyword.PROTECTED)) {
          return "protected";
        }
        return "package-private";
      } else {
        // If it doesn't have modifiers, we assume package-private
        return "package-private";
      }

    }
  }
}
