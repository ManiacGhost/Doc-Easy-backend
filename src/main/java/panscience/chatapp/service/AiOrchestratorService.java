package panscience.chatapp.service;

import org.springframework.stereotype.Service;

@Service
public class AiOrchestratorService implements AiService {

    private final NvidiaAiService nvidiaAiService;
    private final GeminiService geminiService;
    private final OpenAiService openAiService;
    private final FallbackAiService fallbackAiService;

    public AiOrchestratorService(NvidiaAiService nvidiaAiService, GeminiService geminiService, OpenAiService openAiService, FallbackAiService fallbackAiService) {
        this.nvidiaAiService = nvidiaAiService;
        this.geminiService = geminiService;
        this.openAiService = openAiService;
        this.fallbackAiService = fallbackAiService;
    }

    @Override
    public String summarize(String text) {
        // Try NVIDIA first (unlimited rate limit)
        if (nvidiaAiService.isConfigured()) {
            try {
                return nvidiaAiService.summarize(text);
            } catch (Exception ex) {
                // Fall through to next option
            }
        }

        // Try Gemini second (free tier)
        if (geminiService.isConfigured()) {
            try {
                return geminiService.summarize(text);
            } catch (Exception ex) {
                // Fall through to next option
            }
        }

        // Try OpenAI as tertiary option
        if (openAiService.isConfigured()) {
            try {
                return openAiService.summarize(text);
            } catch (Exception ex) {
                // Fall through to fallback
            }
        }

        // Use fallback as last resort
        return fallbackAiService.summarize(text);
    }

    @Override
    public String answerQuestion(String context, String question) {
        // Try NVIDIA first (unlimited rate limit)
        if (nvidiaAiService.isConfigured()) {
            try {
                return nvidiaAiService.answerQuestion(context, question);
            } catch (Exception ex) {
                // Fall through to next option
            }
        }

        // Try Gemini second (free tier)
        if (geminiService.isConfigured()) {
            try {
                return geminiService.answerQuestion(context, question);
            } catch (Exception ex) {
                // Fall through to next option
            }
        }

        // Try OpenAI as tertiary option
        if (openAiService.isConfigured()) {
            try {
                return openAiService.answerQuestion(context, question);
            } catch (Exception ex) {
                // Fall through to fallback
            }
        }

        // Use fallback as last resort
        return fallbackAiService.answerQuestion(context, question);
    }
}

