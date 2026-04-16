package panscience.chatapp.service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class FallbackAiService {

    public String summarize(String text) {
        if (text == null || text.isBlank()) {
            return "No extractable text was found for this file.";
        }

        String compact = text.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 280) {
            return compact;
        }

        return Arrays.stream(compact.split("(?<=[.!?])\\s+"))
                .limit(3)
                .collect(Collectors.joining(" "));
    }

    public String answerQuestion(String context, String question) {
        if (context == null || context.isBlank()) {
            return "I could not find enough extracted context in this file yet."
                    + " For audio/video, connect Whisper transcription to improve answers.";
        }

        String compact = context.replaceAll("\\s+", " ").trim();
        String excerpt = compact.length() > 450 ? compact.substring(0, 450) + "..." : compact;
        return "Fallback answer for: '" + question + "'. Based on uploaded content: " + excerpt;
    }
}

