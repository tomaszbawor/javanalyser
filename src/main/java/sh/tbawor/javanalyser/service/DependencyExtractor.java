package sh.tbawor.javanalyser.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import lombok.extern.slf4j.Slf4j;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.model.CodeDependency;
import sh.tbawor.javanalyser.model.DependencyGraph;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Extracts and analyzes dependencies between Java code elements
 */
@Component
@Slf4j
public class DependencyExtractor {

  /**
   * Extract dependencies from a Java file and its associated AST nodes
   */
  public List<CodeDependency> extractDependencies(Path filePath, List<AstNode> nodes) {
    List<CodeDependency> dependencies = new ArrayList<>();

    try {
      String sourceContent = Files.readString(filePath);
      JavaParser javaParser = new JavaParser();
      Optional<CompilationUnit> optionalCu = javaParser.parse(sourceContent).getResult();

      if (optionalCu.isPresent()) {
        CompilationUnit cu = optionalCu.get();

        // Extract package name
        String packageName = cu.getPackageDeclaration()
            .map(pd -> pd.getName().asString())
            .orElse("");

        // Find the primary class/interface from nodes
        Optional<AstNode> primaryClass = nodes.stream()
            .filter(n -> "class".equals(n.getType()) || "interface".equals(n.getType()))
            .findFirst();

        if (primaryClass.isPresent()) {
          // Extract import dependencies
          extractImportDependencies(cu, primaryClass.get(), dependencies);

          // Extract inheritance dependencies
          extractInheritanceDependencies(cu, primaryClass.get(), dependencies);

          // Extract method call dependencies (within the same file)
          extractMethodCallDependencies(cu, primaryClass.get(), nodes, dependencies);

          // Extract object creation dependencies
          extractObjectCreationDependencies(cu, primaryClass.get(), dependencies);

          // Extract field usage dependencies
          extractFieldUsageDependencies(cu, primaryClass.get(), nodes, dependencies);
        }
      }
    } catch (IOException e) {
      log.error("Error extracting dependencies from file: {}", filePath, e);
    }

    return dependencies;
  }

  /**
   * Extract import statement dependencies
   */
  private void extractImportDependencies(CompilationUnit cu, AstNode sourceNode, List<CodeDependency> dependencies) {
    for (ImportDeclaration importDecl : cu.getImports()) {
      String importedClass = importDecl.getName().asString();

      CodeDependency dependency = CodeDependency.builder()
          .type("import")
          .sourceNode(sourceNode.getPackageName() + "." + sourceNode.getName())
          .targetNode(importedClass)
          .sourceFilePath(sourceNode.getFilePath())
          .targetFilePath("") // Will be populated during cross-file dependency phase
          .sourceLine(importDecl.getBegin().map(pos -> pos.line).orElse(0))
          .description("Imports " + importedClass)
          .build();

      dependencies.add(dependency);
    }
  }

  /**
   * Extract inheritance (extends/implements) dependencies
   */
  private void extractInheritanceDependencies(CompilationUnit cu, AstNode sourceNode,
      List<CodeDependency> dependencies) {
    cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
      // Extract extends relationships
      for (ClassOrInterfaceType extendedType : classDecl.getExtendedTypes()) {
        CodeDependency dependency = CodeDependency.builder()
            .type("extends")
            .sourceNode(sourceNode.getPackageName() + "." + sourceNode.getName())
            .targetNode(extendedType.getNameAsString()) // Will be resolved later
            .sourceFilePath(sourceNode.getFilePath())
            .targetFilePath("") // Will be populated during cross-file dependency phase
            .sourceLine(extendedType.getBegin().map(pos -> pos.line).orElse(0))
            .description("Extends " + extendedType.getNameAsString())
            .build();

        dependencies.add(dependency);
      }

      // Extract implements relationships
      for (ClassOrInterfaceType implementedType : classDecl.getImplementedTypes()) {
        CodeDependency dependency = CodeDependency.builder()
            .type("implements")
            .sourceNode(sourceNode.getPackageName() + "." + sourceNode.getName())
            .targetNode(implementedType.getNameAsString()) // Will be resolved later
            .sourceFilePath(sourceNode.getFilePath())
            .targetFilePath("") // Will be populated during cross-file dependency phase
            .sourceLine(implementedType.getBegin().map(pos -> pos.line).orElse(0))
            .description("Implements " + implementedType.getNameAsString())
            .build();

        dependencies.add(dependency);
      }
    });
  }

  /**
   * Extract method call dependencies
   */
  private void extractMethodCallDependencies(CompilationUnit cu, AstNode sourceNode,
      List<AstNode> nodes, List<CodeDependency> dependencies) {
    // Create a map of method names to nodes for this file
    cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
      // For now, we'll just capture all method calls as dependencies
      // Later, we can resolve which class/interface they belong to
      String methodName = methodCall.getNameAsString();

      // Find if any node in this file is the target of this method call
      Optional<AstNode> targetMethod = nodes.stream()
          .filter(node -> "method".equals(node.getType()) && methodName.equals(node.getName()))
          .findFirst();

      if (targetMethod.isPresent()) {
        CodeDependency dependency = CodeDependency.builder()
            .type("calls")
            .sourceNode(sourceNode.getPackageName() + "." + sourceNode.getName())
            .targetNode(targetMethod.get().getPackageName() + "." +
                targetMethod.get().getName())
            .sourceFilePath(sourceNode.getFilePath())
            .targetFilePath(targetMethod.get().getFilePath())
            .sourceLine(methodCall.getBegin().map(pos -> pos.line).orElse(0))
            .description("Calls method " + methodName)
            .build();

        dependencies.add(dependency);
      }
    });
  }

  /**
   * Extract object creation dependencies
   */
  private void extractObjectCreationDependencies(CompilationUnit cu, AstNode sourceNode,
      List<CodeDependency> dependencies) {
    cu.findAll(ObjectCreationExpr.class).forEach(objCreation -> {
      String typeName = objCreation.getType().getNameAsString();

      CodeDependency dependency = CodeDependency.builder()
          .type("creates")
          .sourceNode(sourceNode.getPackageName() + "." + sourceNode.getName())
          .targetNode(typeName) // Will be resolved later with imports
          .sourceFilePath(sourceNode.getFilePath())
          .targetFilePath("") // Will be populated during cross-file dependency phase
          .sourceLine(objCreation.getBegin().map(pos -> pos.line).orElse(0))
          .description("Creates instance of " + typeName)
          .build();

      dependencies.add(dependency);
    });
  }

  /**
   * Extract field usage dependencies (optional, more complex to implement fully)
   */
  private void extractFieldUsageDependencies(CompilationUnit cu, AstNode sourceNode,
      List<AstNode> nodes, List<CodeDependency> dependencies) {
    // This is a simplified implementation
    cu.findAll(com.github.javaparser.ast.expr.NameExpr.class).forEach(nameExpr -> {
      String name = nameExpr.getNameAsString();

      // Find if any field in this file matches the name
      Optional<AstNode> targetField = nodes.stream()
          .filter(node -> "field".equals(node.getType()) && name.equals(node.getName()))
          .findFirst();

      if (targetField.isPresent()) {
        CodeDependency dependency = CodeDependency.builder()
            .type("uses")
            .sourceNode(sourceNode.getPackageName() + "." + sourceNode.getName())
            .targetNode(targetField.get().getPackageName() + "." +
                sourceNode.getName() + "." + targetField.get().getName())
            .sourceFilePath(sourceNode.getFilePath())
            .targetFilePath(targetField.get().getFilePath())
            .sourceLine(nameExpr.getBegin().map(pos -> pos.line).orElse(0))
            .description("Uses field " + name)
            .build();

        dependencies.add(dependency);
      }
    });
  }

  /**
   * Process cross-file dependencies after all files have been parsed
   */
  public void extractCrossFileDependencies(DependencyGraph graph) {
    // Resolve import dependencies to actual file paths
    graph.getEdges().stream()
        .filter(dep -> "import".equals(dep.getType()))
        .forEach(dep -> {
          String targetNodeName = dep.getTargetNode();

          // Find the node in the graph that matches this import
          graph.getNodes().stream()
              .filter(node -> (node.getPackageName() + "." + node.getName()).equals(targetNodeName) ||
                  targetNodeName.endsWith("." + node.getName()))
              .findFirst()
              .ifPresent(targetNode -> {
                // Update the target file path
                dep.setTargetFilePath(targetNode.getFilePath());
              });
        });

    // Resolve inheritance dependencies
    graph.getEdges().stream()
        .filter(dep -> "extends".equals(dep.getType()) || "implements".equals(dep.getType()))
        .forEach(dep -> {
          String simpleName = dep.getTargetNode();

          // Find the fully qualified name from imports
          String sourceNode = dep.getSourceNode();
          List<CodeDependency> imports = graph.getEdges().stream()
              .filter(importDep -> "import".equals(importDep.getType()) &&
                  sourceNode.equals(importDep.getSourceNode()))
              .toList();

          // Look for matching import
          Optional<CodeDependency> matchingImport = imports.stream()
              .filter(importDep -> importDep.getTargetNode().endsWith("." + simpleName))
              .findFirst();

          if (matchingImport.isPresent()) {
            String fullyQualifiedName = matchingImport.get().getTargetNode();
            dep.setTargetNode(fullyQualifiedName);

            // Find target node in graph
            graph.getNodes().stream()
                .filter(node -> (node.getPackageName() + "." + node.getName()).equals(fullyQualifiedName))
                .findFirst()
                .ifPresent(targetNode -> {
                  dep.setTargetFilePath(targetNode.getFilePath());
                });
          }
        });

    // Resolve object creation dependencies
    graph.getEdges().stream()
        .filter(dep -> "creates".equals(dep.getType()))
        .forEach(dep -> {
          String simpleName = dep.getTargetNode();

          // Find the fully qualified name from imports
          String sourceNode = dep.getSourceNode();
          List<CodeDependency> imports = graph.getEdges().stream()
              .filter(importDep -> "import".equals(importDep.getType()) &&
                  sourceNode.equals(importDep.getSourceNode()))
              .toList();

          // Look for matching import
          Optional<CodeDependency> matchingImport = imports.stream()
              .filter(importDep -> importDep.getTargetNode().endsWith("." + simpleName))
              .findFirst();

          if (matchingImport.isPresent()) {
            String fullyQualifiedName = matchingImport.get().getTargetNode();
            dep.setTargetNode(fullyQualifiedName);

            // Find target node in graph
            graph.getNodes().stream()
                .filter(node -> (node.getPackageName() + "." + node.getName()).equals(fullyQualifiedName))
                .findFirst()
                .ifPresent(targetNode -> {
                  dep.setTargetFilePath(targetNode.getFilePath());
                });
          }
        });

    // Track method call dependencies across files
    graph.getEdges().stream()
        .filter(dep -> "calls".equals(dep.getType()))
        .forEach(dep -> {
          // Extract method name from target
          String targetNode = dep.getTargetNode();
          String methodName = targetNode.substring(targetNode.lastIndexOf(".") + 1);

          // Look for all methods with this name in the graph
          List<AstNode> methodNodes = graph.getNodes().stream()
              .filter(node -> "method".equals(node.getType()) && methodName.equals(node.getName()))
              .toList();

          // If there's only one, we're done. If there are multiple, we need to resolve
          if (methodNodes.size() == 1) {
            AstNode methodNode = methodNodes.get(0);
            dep.setTargetNode(methodNode.getPackageName() + "." + methodNode.getName());
            dep.setTargetFilePath(methodNode.getFilePath());
          } else if (methodNodes.size() > 1) {
            // Try to resolve based on imports
            // This is simplified - in a real scenario we'd need more sophisticated
            // resolution
            String sourceNode = dep.getSourceNode();
            List<CodeDependency> imports = graph.getEdges().stream()
                .filter(importDep -> "import".equals(importDep.getType()) &&
                    sourceNode.equals(importDep.getSourceNode()))
                .toList();

            for (AstNode methodNode : methodNodes) {
              String methodPackage = methodNode.getPackageName();
              boolean hasImport = imports.stream()
                  .anyMatch(importDep -> importDep.getTargetNode().startsWith(methodPackage));

              if (hasImport) {
                dep.setTargetNode(methodNode.getPackageName() + "." + methodNode.getName());
                dep.setTargetFilePath(methodNode.getFilePath());
                break;
              }
            }
          }
        });
  }
}
