package sh.tbawor.javanalyser.service.prompt;

/**
 * Builder for creating prompts for LLMs.
 * This is part of the Builder pattern implementation.
 */
public class PromptBuilder {
    private final StringBuilder promptBuilder = new StringBuilder();

    /**
     * Adds a preamble to the prompt.
     * 
     * @param preamble The preamble text
     * @return This builder for method chaining
     */
    public PromptBuilder withPreamble(String preamble) {
        promptBuilder.append(preamble);
        return this;
    }

    /**
     * Adds code dependency information to the prompt.
     * 
     * @param formattedGraph The formatted dependency graph
     * @return This builder for method chaining
     */
    public PromptBuilder withDependencyInfo(String formattedGraph) {
        promptBuilder.append("\n\nCode Dependency Information:\n");
        promptBuilder.append(formattedGraph);
        return this;
    }

    /**
     * Adds source code context to the prompt if provided.
     * 
     * @param sourceCodeContext The source code context
     * @return This builder for method chaining
     */
    public PromptBuilder withSourceCode(String sourceCodeContext) {
        if (sourceCodeContext != null && !sourceCodeContext.isEmpty()) {
            promptBuilder.append("\n\nRelevant Source Code:\n");
            promptBuilder.append(sourceCodeContext);
        }
        return this;
    }

    /**
     * Adds a user query to the prompt.
     * 
     * @param query The user query
     * @return This builder for method chaining
     */
    public PromptBuilder withQuery(String query) {
        promptBuilder.append("\n\nUser Query: ").append(query);
        return this;
    }

    /**
     * Builds the final prompt string.
     * 
     * @return The complete prompt
     */
    public String build() {
        return promptBuilder.toString();
    }
}