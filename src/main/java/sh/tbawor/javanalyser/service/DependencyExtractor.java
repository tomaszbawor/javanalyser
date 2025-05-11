package sh.tbawor.javanalyser.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import sh.tbawor.javanalyser.exception.ParsingException;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.model.CodeDependency;
import sh.tbawor.javanalyser.model.DependencyGraph;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Extracts and analyzes dependencies between Java code elements.
 * Identifies relationships between classes, interfaces, methods, and fields.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DependencyExtractor {

  /**
   * Extract dependencies from a Java file and its associated AST nodes
   *
   * @param filePath Path to the Java file
   * @param nodes List of AST nodes from the file
   * @return List of dependencies extracted from the file
   * @throws ParsingException if an error occurs during dependency extraction
   */
  public List<CodeDependency> extractDependencies(Path filePath, List<AstNode> nodes) {
    List<CodeDependency> dependencies = new ArrayList<>();

    try {
      String sourceContent = Files.readString(filePath);
      JavaParser javaParser = new JavaParser();
      Optional<CompilationUnit> optionalCu = javaParser.parse(sourceContent).getResult();

      if (optionalCu.isEmpty()) {
        log.warn("Failed to parse file for dependency extraction: {}", filePath);
        return dependencies;
      }

      CompilationUnit cu = optionalCu.get();
      String packageName = extractPackageName(cu);

      // Find the primary class/interface from nodes
      Optional<AstNode> primaryClassOpt = findPrimaryClassNode(nodes);
      
      if (primaryClassOpt.isEmpty()) {
        log.warn("No primary class/interface found in file: {}", filePath);
        return dependencies;
      }
      
      AstNode primaryClass = primaryClassOpt.get();
      extractAllDependencies(cu, primaryClass, nodes, dependencies);
      
      return dependencies;
    } catch (IOException e) {
      log.error("Error reading file for dependency extraction: {}", filePath, e);
      throw new ParsingException("Failed to read file for dependency extraction", e);
    } catch (Exception e) {
      log.error("Unexpected error extracting dependencies from file: {}", filePath, e);
      throw new ParsingException("Unexpected error during dependency extraction", e);
    }
  }

  /**
   * Extract package name from compilation unit
   */
  private String extractPackageName(CompilationUnit cu) {
    return cu.getPackageDeclaration()
        .map(pd -> pd.getName().asString())
        .orElse("");
  }

  /**
   * Find the primary class or interface node in a file
   */
  private Optional<AstNode> findPrimaryClassNode(List<AstNode> nodes) {
    return nodes.stream()
        .filter(n -> "class".equals(n.getType()) || "interface".equals(n.getType()))
        .findFirst();
  }

  /**
   * Extract all dependency types from a file
   */
  private void extractAllDependencies(CompilationUnit cu, AstNode sourceNode, 
                                     List<AstNode> nodes, List<CodeDependency> dependencies) {
    // Extract import dependencies
    extractImportDependencies(cu, sourceNode, dependencies);

    // Extract inheritance dependencies
    extractInheritanceDependencies(cu, sourceNode, dependencies);

    // Extract method call dependencies (within the same file)
    extractMethodCallDependencies(cu, sourceNode, nodes, dependencies);

    // Extract object creation dependencies
    extractObjectCreationDependencies(cu, sourceNode, dependencies);

    // Extract field usage dependencies
    extractFieldUsageDependencies(cu, sourceNode, nodes, dependencies);
  }

  /**
   * Extract import statement dependencies
   */
  private void extractImportDependencies(CompilationUnit cu, AstNode sourceNode, List<CodeDependency> dependencies) {
    for (ImportDeclaration importDecl : cu.getImports()) {
      String importedClass = importDecl.getName().asString();
      
      dependencies.add(createDependency(
          "import",
          sourceNode,
          importedClass, 
          "",
          importDecl.getBegin().map(pos -> pos.line).orElse(0),
          "Imports " + importedClass
      ));
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
        String typeName = extendedType.getNameAsString();
        
        dependencies.add(createDependency(
            "extends",
            sourceNode,
            typeName,
            "",
            extendedType.getBegin().map(pos -> pos.line).orElse(0),
            "Extends " + typeName
        ));
      }

      // Extract implements relationships
      for (ClassOrInterfaceType implementedType : classDecl.getImplementedTypes()) {
        String typeName = implementedType.getNameAsString();
        
        dependencies.add(createDependency(
            "implements",
            sourceNode,
            typeName,
            "",
            implementedType.getBegin().map(pos -> pos.line).orElse(0),
            "Implements " + typeName
        ));
      }
    });
  }

  /**
   * Extract method call dependencies
   */
  private void extractMethodCallDependencies(CompilationUnit cu, AstNode sourceNode,
      List<AstNode> nodes, List<CodeDependency> dependencies) {
      
    // Create a map of method names to nodes for more efficient lookup
    Map<String, AstNode> methodMap = nodes.stream()
        .filter(node -> "method".equals(node.getType()))
        .collect(Collectors.toMap(
            AstNode::getName,
            Function.identity(),
            (existing, replacement) -> existing // Keep first if duplicate keys
        ));
        
    cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
      String methodName = methodCall.getNameAsString();
      AstNode targetMethod = methodMap.get(methodName);
      
      if (targetMethod != null) {
        String targetNodeName = targetMethod.getPackageName() + "." + targetMethod.getName();
        
        dependencies.add(createDependency(
            "calls",
            sourceNode,
            targetNodeName,
            targetMethod.getFilePath(),
            methodCall.getBegin().map(pos -> pos.line).orElse(0),
            "Calls method " + methodName
        ));
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
      
      dependencies.add(createDependency(
          "creates",
          sourceNode,
          typeName,
          "",
          objCreation.getBegin().map(pos -> pos.line).orElse(0),
          "Creates instance of " + typeName
      ));
    });
  }

  /**
   * Extract field usage dependencies
   */
  private void extractFieldUsageDependencies(CompilationUnit cu, AstNode sourceNode,
      List<AstNode> nodes, List<CodeDependency> dependencies) {
      
    // Create a map of field names to nodes for more efficient lookup
    Map<String, AstNode> fieldMap = nodes.stream()
        .filter(node -> "field".equals(node.getType()))
        .collect(Collectors.toMap(
            AstNode::getName,
            Function.identity(),
            (existing, replacement) -> existing // Keep first if duplicate keys
        ));
        
    cu.findAll(NameExpr.class).forEach(nameExpr -> {
      String name = nameExpr.getNameAsString();
      AstNode targetField = fieldMap.get(name);
      
      if (targetField != null) {
        String targetNodeName = targetField.getPackageName() + "." +
                               sourceNode.getName() + "." + targetField.getName();
                               
        dependencies.add(createDependency(
            "uses",
            sourceNode,
            targetNodeName,
            targetField.getFilePath(),
            nameExpr.getBegin().map(pos -> pos.line).orElse(0),
            "Uses field " + name
        ));
      }
    });
  }

  /**
   * Helper method to create a dependency with consistent structure
   */
  private CodeDependency createDependency(String type, AstNode sourceNode, String targetNode, 
                                         String targetFilePath, int sourceLine, String description) {
    return CodeDependency.builder()
        .type(type)
        .sourceNode(sourceNode.getPackageName() + "." + sourceNode.getName())
        .targetNode(targetNode)
        .sourceFilePath(sourceNode.getFilePath())
        .targetFilePath(targetFilePath)
        .sourceLine(sourceLine)
        .description(description)
        .build();
  }

  /**
   * Process cross-file dependencies after all files have been parsed
   *
   * @param graph The dependency graph to process
   */
  public void extractCrossFileDependencies(DependencyGraph graph) {
    if (graph == null || graph.getEdges().isEmpty()) {
      log.warn("No dependencies to process for cross-file resolution");
      return;
    }
    
    try {
      log.info("Starting cross-file dependency resolution for {} edges", graph.getEdges().size());
      
      // Create lookup map for nodes by fully qualified name
      Map<String, AstNode> nodesByFqn = createNodeLookupMap(graph);
      
      // Create import maps for each source node
      Map<String, List<String>> importsBySource = createImportMaps(graph);
      
      // Resolve dependencies by type
      resolveImportDependencies(graph, nodesByFqn);
      resolveInheritanceDependencies(graph, importsBySource, nodesByFqn);
      resolveObjectCreationDependencies(graph, importsBySource, nodesByFqn);
      resolveMethodCallDependencies(graph, importsBySource, nodesByFqn);
      
      log.info("Completed cross-file dependency resolution");
    } catch (Exception e) {
      log.error("Error during cross-file dependency resolution", e);
      throw new ParsingException("Failed to resolve cross-file dependencies", e);
    }
  }

  /**
   * Create a lookup map for nodes by fully qualified name
   */
  private Map<String, AstNode> createNodeLookupMap(DependencyGraph graph) {
    Map<String, AstNode> nodeMap = new HashMap<>();
    
    for (AstNode node : graph.getNodes()) {
      String fqn = node.getPackageName() + "." + node.getName();
      nodeMap.put(fqn, node);
    }
    
    return nodeMap;
  }

  /**
   * Create import maps for each source node
   */
  private Map<String, List<String>> createImportMaps(DependencyGraph graph) {
    Map<String, List<String>> importsBySource = new HashMap<>();
    
    graph.getEdges().stream()
        .filter(dep -> "import".equals(dep.getType()))
        .forEach(dep -> {
          String sourceNode = dep.getSourceNode();
          String importedClass = dep.getTargetNode();
          
          if (!importsBySource.containsKey(sourceNode)) {
            importsBySource.put(sourceNode, new ArrayList<>());
          }
          
          importsBySource.get(sourceNode).add(importedClass);
        });
        
    return importsBySource;
  }

  /**
   * Resolve import dependencies
   */
  private void resolveImportDependencies(DependencyGraph graph, Map<String, AstNode> nodesByFqn) {
    graph.getEdges().stream()
        .filter(dep -> "import".equals(dep.getType()))
        .forEach(dep -> {
          String targetNodeName = dep.getTargetNode();
          AstNode targetNode = nodesByFqn.get(targetNodeName);
          
          if (targetNode != null) {
            dep.setTargetFilePath(targetNode.getFilePath());
          }
        });
  }

  /**
   * Resolve inheritance dependencies
   */
  private void resolveInheritanceDependencies(DependencyGraph graph, 
                                             Map<String, List<String>> importsBySource,
                                             Map<String, AstNode> nodesByFqn) {
    resolveTypeBasedDependencies(graph, importsBySource, nodesByFqn, 
                               dep -> "extends".equals(dep.getType()) || "implements".equals(dep.getType()));
  }

  /**
   * Resolve object creation dependencies
   */
  private void resolveObjectCreationDependencies(DependencyGraph graph, 
                                               Map<String, List<String>> importsBySource,
                                               Map<String, AstNode> nodesByFqn) {
    resolveTypeBasedDependencies(graph, importsBySource, nodesByFqn, 
                               dep -> "creates".equals(dep.getType()));
  }

  /**
   * Helper method to resolve type-based dependencies (extends, implements, creates)
   */
  private void resolveTypeBasedDependencies(DependencyGraph graph, 
                                          Map<String, List<String>> importsBySource,
                                          Map<String, AstNode> nodesByFqn,
                                          java.util.function.Predicate<CodeDependency> filter) {
    graph.getEdges().stream()
        .filter(filter)
        .forEach(dep -> {
          String simpleName = dep.getTargetNode();
          String sourceNode = dep.getSourceNode();
          List<String> imports = importsBySource.getOrDefault(sourceNode, List.of());
          
          // Find matching import that ends with the simple name
          Optional<String> matchingImport = imports.stream()
              .filter(imp -> imp.endsWith("." + simpleName))
              .findFirst();
              
          if (matchingImport.isPresent()) {
            String fullyQualifiedName = matchingImport.get();
            dep.setTargetNode(fullyQualifiedName);
            
            // Find and set target file path
            AstNode targetNode = nodesByFqn.get(fullyQualifiedName);
            if (targetNode != null) {
              dep.setTargetFilePath(targetNode.getFilePath());
            }
          }
        });
  }

  /**
   * Resolve method call dependencies
   */
  private void resolveMethodCallDependencies(DependencyGraph graph, 
                                           Map<String, List<String>> importsBySource,
                                           Map<String, AstNode> nodesByFqn) {
    graph.getEdges().stream()
        .filter(dep -> "calls".equals(dep.getType()))
        .forEach(dep -> {
          // Skip if the target file path is already set
          if (StringUtils.isNotEmpty(dep.getTargetFilePath())) {
            return;
          }
          
          // Extract method name from target
          String targetNode = dep.getTargetNode();
          int lastDotIndex = targetNode.lastIndexOf(".");
          if (lastDotIndex == -1) {
            return;
          }
          
          String methodName = targetNode.substring(lastDotIndex + 1);
          
          // Find all method nodes with this name
          List<AstNode> methodNodes = graph.getNodes().stream()
              .filter(node -> "method".equals(node.getType()) && methodName.equals(node.getName()))
              .toList();
              
          if (methodNodes.isEmpty()) {
            return;
          }
          
          if (methodNodes.size() == 1) {
            // Only one method with this name exists
            AstNode methodNode = methodNodes.get(0);
            dep.setTargetNode(methodNode.getPackageName() + "." + methodNode.getName());
            dep.setTargetFilePath(methodNode.getFilePath());
          } else {
            // Multiple methods with this name, try to resolve based on imports
            resolveMethodCallByImports(dep, methodNodes, importsBySource);
          }
        });
  }

  /**
   * Resolve method calls with multiple candidate targets using imports
   */
  private void resolveMethodCallByImports(CodeDependency dep, List<AstNode> methodNodes,
                                         Map<String, List<String>> importsBySource) {
    String sourceNode = dep.getSourceNode();
    List<String> imports = importsBySource.getOrDefault(sourceNode, List.of());
    
    for (AstNode methodNode : methodNodes) {
      String methodPackage = methodNode.getPackageName();
      
      // Check if any import references this method's package
      boolean hasMatchingImport = imports.stream()
          .anyMatch(imp -> imp.startsWith(methodPackage));
          
      if (hasMatchingImport) {
        dep.setTargetNode(methodNode.getPackageName() + "." + methodNode.getName());
        dep.setTargetFilePath(methodNode.getFilePath());
        return;
      }
    }
  }
}
