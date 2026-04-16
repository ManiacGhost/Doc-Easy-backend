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
public class OpenAiService {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiService.class);

    private final String apiKey;
    private final String chatEndpoint;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiService(
            @Value("${app.ai.openai.api-key:}") String apiKey,
            @Value("${app.ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${app.ai.openai.chat-endpoint:/v1/chat/completions}") String chatEndpoint,
            @Value("${app.ai.openai.model:gpt-4o-mini}") String model,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.chatEndpoint = chatEndpoint;
        this.model = model;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;

        logger.info("OpenAiService initialized:");
        logger.info("  - API Key configured: {}", apiKey != null && !apiKey.isBlank());
        logger.info("  - Base URL: {}", baseUrl);
        logger.info("  - Chat Endpoint: {}", chatEndpoint);
        logger.info("  - Chat Model: {}", model);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
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

    private String chat(String prompt) {
        Map<String, Object> payload = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a helpful backend assistant."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.2
        );

        logger.info("Calling OpenAI API with model: {} and prompt length: {}", model, prompt.length());
        logger.debug("Request model configuration: {}", model);

        try {
            String response = restClient.post()
                    .uri(chatEndpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            logger.info("OpenAI Response received: {} bytes", response != null ? response.length() : 0);
            logger.debug("OpenAI Response: {}", response);

            JsonNode root = objectMapper.readTree(response);

            // Check for error in response
            if (root.has("error")) {
                String errorMsg = root.path("error").path("message").asText("Unknown error");
                String errorCode = root.path("error").path("code").asText("unknown");
                logger.error("OpenAI API returned error: {} - {}", errorCode, errorMsg);
                throw new IllegalStateException("OpenAI API Error (" + errorCode + "): " + errorMsg);
            }

            String result = root.path("choices").path(0).path("message").path("content").asText();
            logger.info("Extracted response: {} chars", result.length());
            return result;
        } catch (HttpClientErrorException ex) {
            logger.error("HTTP Error from OpenAI API: {} {}", ex.getStatusCode(), ex.getStatusText());
            logger.error("Error Response Body: {}", ex.getResponseBodyAsString());
            String errorBody = ex.getResponseBodyAsString();
            try {
                JsonNode errorNode = objectMapper.readTree(errorBody);
                String errorMsg = errorNode.path("error").path("message").asText("Unknown error");
                throw new IllegalStateException("OpenAI API Error: " + errorMsg, ex);
            } catch (Exception parseEx) {
                throw new IllegalStateException("OpenAI API Error (" + ex.getStatusCode() + "): " + ex.getStatusText(), ex);
            }
        } catch (Exception ex) {
            logger.error("Failed to parse OpenAI response: {}", ex.getMessage(), ex);
            logger.error("Exception class: {}", ex.getClass().getName());
            throw new IllegalStateException("Unable to parse OpenAI response: " + ex.getMessage(), ex);
        }
    }
}
