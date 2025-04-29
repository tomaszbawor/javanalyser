package sh.tbawor.javanalyser.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import sh.tbawor.javanalyser.model.AstNode;
import sh.tbawor.javanalyser.model.DependencyGraph;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public class LocalLlmServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine"))
            .withUsername("test")
            .withPassword("test")
            .withDatabaseName("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public LocalLlmService fakeLocalLlmService() {
            return new FakeLocalLlmService();
        }
    }

    // Fake implementation of LocalLlmService that doesn't use OllamaChatModel
    static class FakeLocalLlmService extends LocalLlmService {
        public FakeLocalLlmService() {
            // Pass null as we won't use the OllamaChatModel
            super(null);
        }

        @Override
        public String query(String prompt, int maxTokens) {
            // Return different responses based on the prompt content for more realistic testing
            if (prompt.contains("test.function")) {
                return "This is a mock response for test.function";
            } else if (prompt.contains("Method:")) {
                // This simulates a response for a method explanation
                return "This method performs the following steps:\n" +
                       "1. Validates input parameters\n" +
                       "2. Processes the data\n" +
                       "3. Returns the result\n\n" +
                       "The purpose of this method is to demonstrate LLM integration.";
            } else {
                return "This is a default mock response from the LLM";
            }
        }
    }

    @Autowired
    private LocalLlmService localLlmService;

    @Test
    void testQueryWithMockOllama() {
        // Given
        String prompt = "Test prompt";
        int maxTokens = 100;

        // When
        String response = localLlmService.query(prompt, maxTokens);

        // Then
        assertThat(response).isEqualTo("This is a default mock response from the LLM");
    }

    @Test
    void testExplanationServiceWithMockLlm_FunctionNotFound() {
        // Given
        AstService mockAstService = mock(AstService.class);
        when(mockAstService.getDependencyGraph()).thenReturn(new DependencyGraph());

        ExplanationService explanationService = new ExplanationService(mockAstService, localLlmService);

        // When - we call a method that uses the LLM service
        // Note: This is a simplified test as we're providing a minimal mock AstService
        String explanation = explanationService.explainFunction("test.function");

        // Then - verify the explanation contains our mock response
        assertThat(explanation).contains("Error: Function test.function not found");
    }

    @Test
    void testExplanationServiceWithMockLlm_SuccessfulExplanation() {
        // Given
        // Create a real AstNode that represents a method (using the builder pattern)
        AstNode methodNode = AstNode.builder()
            .type("method")
            .name("testMethod")
            .packageName("test.package")
            .sourceCode("public void testMethod() { /* code */ }")
            .visibility("public")
            .returnType("void")
            .build();

        // Create a real DependencyGraph
        DependencyGraph graph = new DependencyGraph();
        graph.addNode(methodNode);

        // Create a mock AstService that returns our graph
        AstService mockAstService = mock(AstService.class);
        when(mockAstService.getDependencyGraph()).thenReturn(graph);

        // Create the ExplanationService with our mocks
        ExplanationService explanationService = new ExplanationService(mockAstService, localLlmService);

        // When - we call the method to explain a function
        String explanation = explanationService.explainFunction("test.package.testMethod");

        // Then - verify the explanation contains the expected content from our fake LLM
        assertThat(explanation).contains("This method performs the following steps:");
        assertThat(explanation).contains("1. Validates input parameters");
        assertThat(explanation).contains("2. Processes the data");
        assertThat(explanation).contains("3. Returns the result");
        assertThat(explanation).contains("The purpose of this method is to demonstrate LLM integration");
    }
}
