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
public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public GeminiService(
            @Value("${app.ai.gemini.api-key:}") String apiKey,
            @Value("${app.ai.gemini.model:gemini-pro}") String model,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1")
                .build();

        logger.info("GeminiService initialized:");
        logger.info("  - Enabled: {}", enabled);
        logger.info("  - Model: {}", model);
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

    private String chat(String prompt) {
        if (!enabled) {
            throw new IllegalStateException("Gemini API not configured");
        }

        // Gemini API request format
        Map<String, Object> payload = Map.of(
                "contents", List.of(
                        Map.of(
                                "parts", List.of(
                                        Map.of("text", prompt)
                                )
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "maxOutputTokens", 1024
                )
        );

        logger.info("Calling Gemini API with model: {} and prompt length: {}", model, prompt.length());

        try {
            String endpoint = "/models/" + model + ":generateContent?key=" + apiKey;
            String response = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            logger.info("Gemini Response received: {} bytes", response != null ? response.length() : 0);

            JsonNode root = objectMapper.readTree(response);

            // Check for error in response
            if (root.has("error")) {
                String errorMsg = root.path("error").path("message").asText("Unknown error");
                String errorCode = root.path("error").path("code").asText("unknown");
                logger.error("Gemini API returned error: {} - {}", errorCode, errorMsg);
                throw new IllegalStateException("Gemini API Error (" + errorCode + "): " + errorMsg);
            }

            // Gemini response format: candidates[0].content.parts[0].text
            String result = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();

            if (result.isEmpty()) {
                logger.warn("Empty response from Gemini API");
                result = "No response generated";
            }

            logger.info("Extracted response: {} chars", result.length());
            return result;
        } catch (HttpClientErrorException ex) {
            logger.error("HTTP Error from Gemini API: {} {}", ex.getStatusCode(), ex.getStatusText());
            logger.error("Error Response Body: {}", ex.getResponseBodyAsString());
            String errorBody = ex.getResponseBodyAsString();
            try {
                JsonNode errorNode = objectMapper.readTree(errorBody);
                String errorMsg = errorNode.path("error").path("message").asText("Unknown error");
                throw new IllegalStateException("Gemini API Error: " + errorMsg, ex);
            } catch (Exception parseEx) {
                throw new IllegalStateException("Gemini API Error (" + ex.getStatusCode() + "): " + ex.getStatusText(), ex);
            }
        } catch (Exception ex) {
            logger.error("Failed to call Gemini API: {}", ex.getMessage(), ex);
            logger.error("Exception class: {}", ex.getClass().getName());
            throw new IllegalStateException("Unable to call Gemini API: " + ex.getMessage(), ex);
        }
    }
}

