package sh.tbawor.javanalyser.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocalLlmService {

  private final OllamaChatModel ollamaChatModel;

  public String query(String prompt, int maxTokens) {
    try {
      log.debug("Sending prompt to LLM: {}", prompt);

      UserMessage userMessage = new UserMessage(prompt);
      var response = ollamaChatModel.call(new Prompt(List.of(userMessage))).getResult();

      log.debug("Received response from LLM: {}", response.getOutput().getText());
      return response.getOutput().getText();
    } catch (Exception e) {
      log.error("Error querying LLM", e);
      return "Error occurred while querying the LLM: " + e.getMessage();
    }
  }
}
