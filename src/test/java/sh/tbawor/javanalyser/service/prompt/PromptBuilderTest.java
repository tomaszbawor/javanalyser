package sh.tbawor.javanalyser.service.prompt;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the PromptBuilder class.
 */
public class PromptBuilderTest {

    @Test
    void testBuildEmptyPrompt() {
        // Given
        PromptBuilder builder = new PromptBuilder();

        // When
        String prompt = builder.build();

        // Then
        assertThat(prompt).isEmpty();
    }

    @Test
    void testBuildWithPreamble() {
        // Given
        PromptBuilder builder = new PromptBuilder();

        // When
        String prompt = builder.withPreamble("This is a test preamble").build();

        // Then
        assertThat(prompt).isEqualTo("This is a test preamble");
    }

    @Test
    void testBuildWithDependencyInfo() {
        // Given
        PromptBuilder builder = new PromptBuilder();

        // When
        String prompt = builder.withDependencyInfo("Class A depends on Class B").build();

        // Then
        assertThat(prompt).isEqualTo("\n\nCode Dependency Information:\nClass A depends on Class B");
    }

    @Test
    void testBuildWithSourceCode() {
        // Given
        PromptBuilder builder = new PromptBuilder();

        // When
        String prompt = builder.withSourceCode("public class Test {}").build();

        // Then
        assertThat(prompt).isEqualTo("\n\nRelevant Source Code:\npublic class Test {}");
    }

    @Test
    void testBuildWithEmptySourceCode() {
        // Given
        PromptBuilder builder = new PromptBuilder();

        // When
        String prompt = builder.withSourceCode("").build();

        // Then
        assertThat(prompt).isEmpty();
    }

    @Test
    void testBuildWithNullSourceCode() {
        // Given
        PromptBuilder builder = new PromptBuilder();

        // When
        String prompt = builder.withSourceCode(null).build();

        // Then
        assertThat(prompt).isEmpty();
    }

    @Test
    void testBuildWithQuery() {
        // Given
        PromptBuilder builder = new PromptBuilder();

        // When
        String prompt = builder.withQuery("How does this code work?").build();

        // Then
        assertThat(prompt).isEqualTo("\n\nUser Query: How does this code work?");
    }

    @Test
    void testBuildCompletePrompt() {
        // Given
        PromptBuilder builder = new PromptBuilder();

        // When
        String prompt = builder
            .withPreamble("You are an expert Java developer assistant.")
            .withDependencyInfo("Class A depends on Class B")
            .withSourceCode("public class Test {}")
            .withQuery("How does this code work?")
            .build();

        // Then
        assertThat(prompt).isEqualTo(
            "You are an expert Java developer assistant." +
            "\n\nCode Dependency Information:\nClass A depends on Class B" +
            "\n\nRelevant Source Code:\npublic class Test {}" +
            "\n\nUser Query: How does this code work?"
        );
    }

    @Test
    void testMethodChaining() {
        // Given
        PromptBuilder builder = new PromptBuilder();

        // When
        String prompt = builder
            .withPreamble("Preamble")
            .withQuery("Query")
            .withPreamble("New Preamble") // Appends to the existing content
            .build();

        // Then
        assertThat(prompt).isEqualTo("Preamble\n\nUser Query: QueryNew Preamble");
    }
}
