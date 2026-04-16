package panscience.chatapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;

@Service
public class NvidiaAiService {

    private static final Logger logger = LoggerFactory.getLogger(NvidiaAiService.class);

    private final String apiKey;
    private final String textModel;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public NvidiaAiService(
            @Value("${app.ai.nvidia.api-key:}") String apiKey,
            @Value("${app.ai.nvidia.text-model:meta/llama-2-13b-chat}") String textModel,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.textModel = textModel;
        this.objectMapper = objectMapper;
        this.enabled = apiKey != null && !apiKey.isBlank();
        // NVIDIA endpoint format
        this.restClient = RestClient.builder()
                .baseUrl("https://integrate.api.nvidia.com")
                .build();

        logger.info("NvidiaAiService initialized:");
        logger.info("  - Enabled: {}", enabled);
        logger.info("  - Text Model: {}", textModel);
        if (enabled) {
            logger.info("  - API Key configured: true");
        }
    }

    public boolean isConfigured() {
        return enabled;
    }

    public String summarize(String text) {
        String prompt = "Summarize the following content in 5 concise bullet points:\n\n" + text;
        return chat(prompt);
    }

    public String answerQuestion(String context, String question) {
        String prompt = "Answer the user question using only the provided context. "
                + "If context is insufficient, explicitly mention that.\n\n"
                + "Context:\n" + context + "\n\nQuestion: " + question;
        return chat(prompt);
    }

    public String chat(String prompt) {
        if (!enabled) {
            throw new IllegalStateException("NVIDIA AI API not configured");
        }

        // Try multiple NVIDIA models if the configured one fails
        String[] modelsToTry = {
            textModel,  // Try configured model first
            "meta/llama-3.1-8b-instruct",
            "meta/llama-2-7b",
            "mistralai/mistral-7b-instruct"
        };

        StringBuilder lastError = new StringBuilder();

        for (String currentModel : modelsToTry) {
            try {
                logger.info("Attempting NVIDIA AI API with model: {}", currentModel);

                // NVIDIA API uses OpenAI-compatible format
                Map<String, Object> payload = Map.of(
                        "model", currentModel,
                        "messages", List.of(
                                Map.of("role", "system", "content", "You are a helpful assistant."),
                                Map.of("role", "user", "content", prompt)
                        ),
                        "temperature", 0.2,
                        "max_tokens", 1024
                );

                logger.info("Calling NVIDIA AI API with model: {} and prompt length: {}", currentModel, prompt.length());

                String response = restClient.post()
                        .uri("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + apiKey)
                        .body(payload)
                        .retrieve()
                        .body(String.class);

                logger.info("NVIDIA AI Response received: {} bytes", response != null ? response.length() : 0);

                JsonNode root = objectMapper.readTree(response);

                // Check for error in response
                if (root.has("error")) {
                    String errorMsg = root.path("error").path("message").asText("Unknown error");
                    String errorCode = root.path("error").path("code").asText("unknown");
                    logger.warn("NVIDIA AI API error with model {}: {} - {}", currentModel, errorCode, errorMsg);
                    lastError.append(currentModel).append(": ").append(errorMsg).append(" | ");
                    continue;
                }

                // OpenAI-compatible response format: choices[0].message.content
                String result = root.path("choices").path(0).path("message").path("content").asText();

                if (result.isEmpty()) {
                    logger.warn("Empty response from NVIDIA AI API with model {}", currentModel);
                    lastError.append(currentModel).append(": Empty response | ");
                    continue;
                }

                logger.info("Successfully got response from NVIDIA model {}: {} chars", currentModel, result.length());
                return result;

            } catch (HttpClientErrorException ex) {
                logger.warn("HTTP Error from NVIDIA AI API with model {}: {} {}", currentModel, ex.getStatusCode(), ex.getStatusText());
                lastError.append(currentModel).append(": HTTP ").append(ex.getStatusCode()).append(" | ");
            } catch (Exception ex) {
                logger.debug("Failed with NVIDIA model {}: {}", currentModel, ex.getMessage());
                lastError.append(currentModel).append(": ").append(ex.getMessage()).append(" | ");
            }
        }

        // All models failed
        throw new IllegalStateException("All NVIDIA AI models failed: " + lastError.toString());
    }
}

