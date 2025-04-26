package sh.tbawor.javanalyser.parser;

import lombok.extern.slf4j.Slf4j;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.model.DependencyGraph;

import org.springframework.stereotype.Component;

import com.github.javaparser.JavaParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class SourceCodeExtractor {

  /**
   * Extract source code for all nodes in a file and add it to the nodes in the
   * graph
   */
  public void extractSourceCode(Path filePath, DependencyGraph graph) {
    try {
      // Read all lines from the file
      List<String> fileLines = Files.readAllLines(filePath);

      // Get nodes for this file
      List<AstNode> nodesInFile = graph.getNodes().stream()
          .filter(node -> filePath.toString().equals(node.getFilePath()))
          .collect(Collectors.toList());

      // Extract source code for each node
      for (AstNode node : nodesInFile) {
        extractNodeSourceCode(node, fileLines);
      }
    } catch (IOException e) {
      log.error("Error reading file for source code extraction: {}", filePath, e);
    }
  }

  private void extractNodeSourceCode(AstNode node, List<String> fileLines) {
    // Only extract if we have start and end line information
    if (node.getStartLine() > 0 && node.getEndLine() > 0 &&
        node.getStartLine() <= fileLines.size() && node.getEndLine() <= fileLines.size()) {

      // Extract lines for this node (adjusting for 0-based index in list)
      List<String> nodeLines = fileLines.subList(node.getStartLine() - 1, node.getEndLine());

      // Join lines and set to node
      String sourceCode = String.join("\n", nodeLines);
      node.setSourceCode(sourceCode);
    }
  }

  /**
   * Extract just the method body, excluding the signature
   */
  public String extractMethodBody(AstNode methodNode, List<String> fileLines) {
    if (methodNode.getStartLine() <= 0 || methodNode.getEndLine() <= 0 ||
        methodNode.getStartLine() > fileLines.size() || methodNode.getEndLine() > fileLines.size()) {
      return "";
    }

    // Get all lines for the method
    List<String> methodLines = fileLines.subList(methodNode.getStartLine() - 1, methodNode.getEndLine());

    // Find the opening brace
    int openingBraceIndex = -1;
    for (int i = 0; i < methodLines.size(); i++) {
      if (methodLines.get(i).contains("{")) {
        openingBraceIndex = i;
        break;
      }
    }

    // If we found the opening brace, extract from after it to the end
    if (openingBraceIndex >= 0 && openingBraceIndex < methodLines.size() - 1) {
      return String.join("\n", methodLines.subList(openingBraceIndex + 1, methodLines.size() - 1));
    }

    // Fallback - return everything
    return String.join("\n", methodLines);
  }

  /**
   * Extract the Javadoc comment for a node if it exists
   */
  public String extractJavadoc(AstNode node, List<String> fileLines) {
    if (node.getStartLine() <= 1) {
      return "";
    }

    // Look for Javadoc comment above the node
    int searchStart = Math.max(0, node.getStartLine() - 20); // Look up to 20 lines above
    int searchEnd = node.getStartLine() - 1;

    List<String> searchLines = fileLines.subList(searchStart, searchEnd);
    StringBuilder javadoc = new StringBuilder();
    boolean inJavadoc = false;

    // Scan lines in reverse to find javadoc
    for (int i = searchLines.size() - 1; i >= 0; i--) {
      String line = searchLines.get(i).trim();

      if (inJavadoc) {
        javadoc.insert(0, line + "\n");
        if (line.contains("/**")) {
          break;
        }
      } else if (line.contains("*/")) {
        inJavadoc = true;
        javadoc.insert(0, line + "\n");
      } else if (line.isEmpty() || line.startsWith("@")) {
        // Skip annotations and blank lines
        continue;
      } else {
        // If we hit other code, stop searching
        break;
      }
    }

    return javadoc.toString();
  }

  /**
   * Extracts imports for a specific class file
   */
  public List<String> extractImports(Path filePath) {
    try {
      String content = Files.readString(filePath);
      JavaParser javaParser = new JavaParser();
      return javaParser.parse(content)
          .getResult()
          .map(cu -> cu.getImports().stream()
              .map(importDecl -> importDecl.getName().asString())
              .collect(Collectors.toList()))
          .orElse(List.of());
    } catch (IOException e) {
      log.error("Error extracting imports from file: {}", filePath, e);
      return List.of();
    }
  }

  /**
   * Extracts all the method signatures from a class
   */
  public List<String> extractMethodSignatures(AstNode classNode, List<String> fileLines) {
    return classNode.getChildren().stream()
        .filter(node -> "method".equals(node.getType()))
        .map(methodNode -> {
          if (methodNode.getStartLine() > 0 && methodNode.getStartLine() <= fileLines.size()) {
            // Get the line containing the method signature
            String line = fileLines.get(methodNode.getStartLine() - 1).trim();
            // If the method signature spans multiple lines, get until we find the opening
            // brace
            StringBuilder signature = new StringBuilder(line);
            int currentLine = methodNode.getStartLine();
            while (!signature.toString().contains("{") &&
                currentLine < methodNode.getEndLine() &&
                currentLine < fileLines.size()) {
              signature.append(" ").append(fileLines.get(currentLine).trim());
              currentLine++;
            }
            return signature.toString().split("\\{")[0].trim();
          }
          return methodNode.getVisibility() + " " +
              (methodNode.isStatic() ? "static " : "") +
              methodNode.getReturnType() + " " +
              methodNode.getName() + "()";
        })
        .collect(Collectors.toList());
  }

  /**
   * Create a compact class summary with just the essential information
   */
  public String createClassSummary(AstNode classNode, List<String> fileLines) {
    StringBuilder summary = new StringBuilder();

    // Add package declaration
    summary.append("package ").append(classNode.getPackageName()).append(";\n\n");

    // Add basic class declaration
    summary.append(classNode.getVisibility()).append(" ");
    if (classNode.isAbstract())
      summary.append("abstract ");
    summary.append(classNode.isInterface() ? "interface " : "class ");
    summary.append(classNode.getName()).append(" {\n\n");

    // Add field declarations
    classNode.getChildren().stream()
        .filter(node -> "field".equals(node.getType()))
        .forEach(fieldNode -> {
          summary.append("    ").append(fieldNode.getVisibility()).append(" ");
          if (fieldNode.isStatic())
            summary.append("static ");
          summary.append(fieldNode.getReturnType()).append(" ");
          summary.append(fieldNode.getName()).append(";\n");
        });

    summary.append("\n");

    // Add method signatures
    classNode.getChildren().stream()
        .filter(node -> "method".equals(node.getType()) || "constructor".equals(node.getType()))
        .forEach(methodNode -> {
          if (methodNode.getStartLine() > 0 && methodNode.getStartLine() <= fileLines.size()) {
            String signature = fileLines.get(methodNode.getStartLine() - 1).trim();
            if (signature.contains("{")) {
              signature = signature.split("\\{")[0].trim();
            }
            summary.append("    ").append(signature).append(" {...}\n");
          } else {
            summary.append("    ").append(methodNode.getVisibility()).append(" ");
            if (methodNode.isStatic())
              summary.append("static ");
            if (!"constructor".equals(methodNode.getType())) {
              summary.append(methodNode.getReturnType()).append(" ");
            }
            summary.append(methodNode.getName()).append("() {...}\n");
          }
        });

    summary.append("}\n");

    return summary.toString();
  }
}
