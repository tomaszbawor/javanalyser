package sh.tbawor.javanalyser.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAiConfig {

  @Value("${spring.ai.ollama.base-url}")
  private String baseUrl;

  @Value("${spring.ai.ollama.chat.model}")
  private String chatModel;

  @Value("${spring.ai.ollama.embedding.model}")
  private String embeddingModel;

  @Bean
  public OllamaApi ollamaApi() {
    return new OllamaApi(baseUrl);
  }

  @Bean
  public OllamaChatModel ollamaChatModel(OllamaApi ollamaApi) {
    OllamaOptions options = new OllamaOptions();
    options.setModel(chatModel);
    options.setTemperature(0.3);

    return OllamaChatModel.builder().ollamaApi(ollamaApi).defaultOptions(options).build();
  }

  @Bean
  public EmbeddingModel ollamaEmbeddingModel(OllamaApi ollamaApi) {
    OllamaOptions options = new OllamaOptions();
    options.setModel(embeddingModel);
    return OllamaEmbeddingModel.builder().ollamaApi(ollamaApi).defaultOptions(options).build();
  }
}
