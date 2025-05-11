package sh.tbawor.javanalyser.service.prompt;

import org.apache.commons.lang3.StringUtils;

/**
 * Builder for creating structured prompts for LLMs when querying code.
 * 
 * <p>This class implements the Builder pattern to provide a fluent interface
 * for constructing prompts that include:
 * <ul>
 *   <li>A preamble with instructions and context</li>
 *   <li>Code dependency information (class relationships, inheritance, etc.)</li>
 *   <li>Relevant source code snippets</li>
 *   <li>The user's specific query</li>
 * </ul>
 * 
 * <p>Usage example:
 * <pre>{@code
 * String prompt = new PromptBuilder()
 *     .withPreamble("You are a Java expert. Please analyze the following code.")
 *     .withDependencyInfo(formattedGraph)
 *     .withSourceCode(sourceSnippets)
 *     .withQuery("How does the authentication system work?")
 *     .build();
 * }</pre>
 * 
 * <p>The resulting prompt will have a consistent structure with clear section
 * separation, making it easier for LLMs to parse and understand the provided
 * information.
 */
public class PromptBuilder {
    private final StringBuilder promptBuilder = new StringBuilder();

    /**
     * Adds a preamble to the prompt that sets context and instructions for the LLM.
     * This should be called first to establish the role and task for the LLM.
     * 
     * @param preamble The preamble text with instructions and context
     * @return This builder for method chaining
     */
    public PromptBuilder withPreamble(String preamble) {
        promptBuilder.append(preamble);
        return this;
    }

    /**
     * Adds code dependency information to the prompt in a dedicated section.
     * This information helps the LLM understand the relationships between code elements.
     * 
     * @param formattedGraph The formatted dependency graph information
     * @return This builder for method chaining
     */
    public PromptBuilder withDependencyInfo(String formattedGraph) {
        if (StringUtils.isNotBlank(formattedGraph)) {
            promptBuilder.append("\n\nCode Dependency Information:\n");
            promptBuilder.append(formattedGraph);
        }
        return this;
    }

    /**
     * Adds source code context to the prompt if provided.
     * This gives the LLM concrete code examples to reference in its response.
     * The section is only added if non-empty content is provided.
     * 
     * @param sourceCodeContext The source code snippets
     * @return This builder for method chaining
     */
    public PromptBuilder withSourceCode(String sourceCodeContext) {
        if (StringUtils.isNotBlank(sourceCodeContext)) {
            promptBuilder.append("\n\nRelevant Source Code:\n");
            promptBuilder.append(sourceCodeContext);
        }
        return this;
    }

    /**
     * Adds a user query to the prompt, which is the specific question 
     * or request that the LLM should address in its response.
     * 
     * @param query The user query text
     * @return This builder for method chaining
     */
    public PromptBuilder withQuery(String query) {
        if (StringUtils.isNotBlank(query)) {
            promptBuilder.append("\n\nUser Query: ").append(query);
        } else {
            promptBuilder.append("\n\nUser Query: Please analyze the provided code.");
        }
        return this;
    }

    /**
     * Adds a custom section to the prompt with the specified title and content.
     * This allows for extending the prompt with additional structured information.
     * 
     * @param sectionTitle The title of the section
     * @param content The content of the section
     * @return This builder for method chaining
     */
    public PromptBuilder withCustomSection(String sectionTitle, String content) {
        if (StringUtils.isNotBlank(content) && StringUtils.isNotBlank(sectionTitle)) {
            promptBuilder.append("\n\n").append(sectionTitle).append(":\n");
            promptBuilder.append(content);
        }
        return this;
    }

    /**
     * Builds the final prompt string.
     * 
     * @return The complete formatted prompt
     */
    public String build() {
        return promptBuilder.toString();
    }
}