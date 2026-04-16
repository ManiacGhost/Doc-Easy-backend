package panscience.chatapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Service for processing images (vision) and audio/video (transcription) using OpenAI APIs.
 */
@Service
public class MediaProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(MediaProcessingService.class);

    private final String apiKey;
    private final String visionModel;
    private final String nvidiaApiKey;
    private final String nvidiaVisionModel;
    private final String deepgramApiKey;
    private final String deepgramModel;
    private final String geminiApiKey;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final NvidiaAiService nvidiaAiService;
    private final GeminiService geminiService;

    public MediaProcessingService(
            @Value("${app.ai.openai.api-key:}") String apiKey,
            @Value("${app.ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${app.ai.openai.vision-model:gpt-4o}") String visionModel,
            @Value("${app.ai.nvidia.api-key:}") String nvidiaApiKey,
            @Value("${app.ai.nvidia.vision-model:meta/llama-3.2-11b-vision-instruct}") String nvidiaVisionModel,
            @Value("${app.ai.deepgram.api-key:}") String deepgramApiKey,
            @Value("${app.ai.deepgram.model:nova-2}") String deepgramModel,
            @Value("${app.ai.gemini.api-key:}") String geminiApiKey,
            ObjectMapper objectMapper,
            NvidiaAiService nvidiaAiService,
            GeminiService geminiService
    ) {
        this.apiKey = apiKey;
        this.visionModel = visionModel;
        this.nvidiaApiKey = nvidiaApiKey;
        this.nvidiaVisionModel = nvidiaVisionModel;
        this.deepgramApiKey = deepgramApiKey;
        this.deepgramModel = deepgramModel;
        this.geminiApiKey = geminiApiKey;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.nvidiaAiService = nvidiaAiService;
        this.geminiService = geminiService;

        logger.info("MediaProcessingService initialized:");
        logger.info("  - OpenAI API Key configured: {}", apiKey != null && !apiKey.isBlank());
        logger.info("  - OpenAI Vision Model: {}", visionModel);
        logger.info("  - NVIDIA API Key configured: {}", nvidiaApiKey != null && !nvidiaApiKey.isBlank());
        logger.info("  - NVIDIA Vision Model: {}", nvidiaVisionModel);
        logger.info("  - Gemini API Key configured: {}", geminiApiKey != null && !geminiApiKey.isBlank());
        logger.info("  - Base URL: {}", baseUrl);
    }

    public boolean isConfigured() {
        return (apiKey != null && !apiKey.isBlank()) ||
               (nvidiaApiKey != null && !nvidiaApiKey.isBlank()) ||
               (geminiApiKey != null && !geminiApiKey.isBlank());
    }

    /**
     * Describe an image using available vision APIs (NVIDIA first, then Gemini, then OpenAI).
     * @param imageBytes The image file bytes
     * @param mimeType The MIME type of the image (e.g., "image/jpeg", "image/png")
     * @return Description of the image content
     */
    public String describeImage(byte[] imageBytes, String mimeType) {
        StringBuilder errors = new StringBuilder();
        
        logger.info("=== Starting image description extraction ===");
        logger.info("Image bytes size: {}, MIME type: {}", imageBytes.length, mimeType);
        
        // Try NVIDIA first with base64 encoding
        if (nvidiaAiService.isConfigured()) {
            try {
                logger.info("NVIDIA API configured. Attempting image description with NVIDIA API");
                return describeImageWithNvidia(imageBytes, mimeType);
            } catch (Exception ex) {
                String msg = "NVIDIA: " + ex.getMessage();
                errors.append(msg).append(" | ");
                logger.error("NVIDIA image description failed: {}", ex.getMessage());
            }
        } else {
            logger.debug("NVIDIA API not configured, skipping");
        }

        // Try Gemini second
        if (geminiService.isConfigured()) {
            try {
                logger.info("Gemini API configured. Attempting image description with Gemini API");
                return describeImageWithGemini(imageBytes, mimeType);
            } catch (Exception ex) {
                String msg = "Gemini: " + ex.getMessage();
                errors.append(msg).append(" | ");
                logger.error("Gemini image description failed: {}", ex.getMessage());
            }
        } else {
            logger.debug("Gemini API not configured, skipping");
        }

        // Fall back to OpenAI
        try {
            logger.info("Attempting image description with OpenAI API");
            return describeImageWithOpenAI(imageBytes, mimeType);
        } catch (Exception ex) {
            String msg = "OpenAI: " + ex.getMessage();
            errors.append(msg);
            logger.error("OpenAI image description failed: {}", ex.getMessage());
        }
        
        // All vision APIs failed - provide a generic fallback description
        logger.warn("=== All vision providers failed. Using fallback description. ===");
        logger.warn("Error details: {}", errors.toString());
        return "Image uploaded successfully. Vision analysis unavailable due to API limitations. "
               + "Please try again later. Error details: " + errors.toString();
    }

    private String describeImageWithNvidia(byte[] imageBytes, String mimeType) {
        if (nvidiaApiKey == null || nvidiaApiKey.isBlank()) {
            throw new IllegalStateException("NVIDIA API key not configured");
        }
        
        logger.info("NVIDIA Vision: Starting image description with API key configured");
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String mediaType = mimeType != null ? mimeType : "image/jpeg";
        logger.info("NVIDIA Vision: Image size {} bytes, media type: {}", imageBytes.length, mediaType);

        // NVIDIA available vision models
        String[] nvidiaVisionModels = {
            "meta/llama-3.2-11b-vision-instruct",
            "meta/llama-3.2-90b-vision-instruct"
        };

        logger.info("NVIDIA Vision: Will try models in order: {}", String.join(", ", nvidiaVisionModels));

        StringBuilder modelErrors = new StringBuilder();
        
        for (String visionModel : nvidiaVisionModels) {
            try {
                logger.info("NVIDIA Vision: Attempting with model: {}", visionModel);

                Map<String, Object> payload = Map.of(
                        "model", visionModel,
                        "messages", List.of(
                                Map.of(
                                        "role", "user",
                                        "content", List.of(
                                                Map.of("type", "text", "text", "Describe this image briefly."),
                                                Map.of(
                                                        "type", "image_url",
                                                        "image_url", Map.of("url", "data:" + mediaType + ";base64," + base64Image)
                                                )
                                        )
                                )
                        ),
                        "max_tokens", 512
                );

                logger.info("NVIDIA Vision: Calling API endpoint with model: {}", visionModel);
                String response = RestClient.builder()
                        .baseUrl("https://integrate.api.nvidia.com")
                        .build()
                        .post()
                        .uri("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + nvidiaApiKey)
                        .body(payload)
                        .retrieve()
                        .body(String.class);

                logger.info("NVIDIA Vision: Received response {} bytes", response != null ? response.length() : 0);
                JsonNode root = objectMapper.readTree(response);

                if (root.has("error")) {
                    String errorMsg = root.path("error").path("message").asText("Unknown error");
                    modelErrors.append(visionModel).append(": ").append(errorMsg).append(" | ");
                    logger.warn("NVIDIA Vision: Model {} returned error: {}", visionModel, errorMsg);
                    continue;
                }

                String result = root.path("choices").path(0).path("message").path("content").asText();
                if (!result.isEmpty()) {
                    logger.info("NVIDIA Vision: SUCCESS with model {} - got {} chars", visionModel, result.length());
                    return result;
                } else {
                    logger.warn("NVIDIA Vision: Model {} returned empty result", visionModel);
                    modelErrors.append(visionModel).append(": Empty result | ");
                }
            } catch (HttpClientErrorException ex) {
                String errMsg = visionModel + ": HTTP " + ex.getStatusCode() + " " + ex.getStatusText();
                String responseBody = ex.getResponseBodyAsString();
                modelErrors.append(errMsg).append(" | ");
                logger.warn("NVIDIA Vision: HTTP error with model {}: {} - Response: {}", visionModel, ex.getStatusCode(), responseBody);
            } catch (Exception ex) {
                String errMsg = visionModel + ": " + ex.getMessage();
                modelErrors.append(errMsg).append(" | ");
                logger.error("NVIDIA Vision: Exception with model {}: {}", visionModel, ex.getMessage(), ex);
            }
        }
        
        logger.error("NVIDIA Vision: ALL MODELS FAILED - {}", modelErrors.toString());
        throw new IllegalStateException("NVIDIA vision not available: " + modelErrors.toString());
    }

    private String describeImageWithGemini(byte[] imageBytes, String mimeType) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key not configured");
        }
        
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String mediaType = mimeType != null ? mimeType : "image/jpeg";

        Map<String, Object> payload = Map.of(
                "contents", List.of(
                        Map.of(
                                "parts", List.of(
                                        Map.of("text", "Describe this image briefly."),
                                        Map.of(
                                                "inlineData", Map.of(
                                                        "mimeType", mediaType,
                                                        "data", base64Image
                                                )
                                        )
                                )
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "maxOutputTokens", 512
                )
        );

        logger.info("Calling Gemini Vision API for image processing");
        try {
            String response = RestClient.builder()
                    .baseUrl("https://generativelanguage.googleapis.com/v1")
                    .build()
                    .post()
                    .uri("/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            
            // Check for errors
            if (root.has("error")) {
                int errorCode = root.path("error").path("code").asInt(0);
                String errorMsg = root.path("error").path("message").asText("Unknown error");

                // Handle rate limiting - skip Gemini if rate limited
                if (errorCode == 429) {
                    logger.warn("Gemini API rate limited (429): {}", errorMsg);
                    throw new IllegalStateException("Gemini rate limited - quota exceeded");
                }

                logger.error("Gemini API error: {}", errorMsg);
                throw new IllegalStateException("Gemini API error: " + errorMsg);
            }
            
            String result = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
            if (result.isEmpty()) {
                logger.warn("Empty response from Gemini API");
                throw new IllegalStateException("Empty response from Gemini API");
            }
            
            logger.info("Successfully described image with Gemini");
            return result;
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 429) {
                logger.warn("Gemini API rate limited (HTTP 429)");
                throw new IllegalStateException("Gemini rate limited - quota exceeded");
            }
            logger.error("HTTP error from Gemini: {}", ex.getStatusCode());
            throw new IllegalStateException("Gemini HTTP error: " + ex.getStatusCode(), ex);
        } catch (Exception ex) {
            logger.error("Failed to describe image with Gemini: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to parse Gemini API response: " + ex.getMessage(), ex);
        }
    }

    private String describeImageWithOpenAI(byte[] imageBytes, String mimeType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key not configured for image vision");
        }

        try {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String mediaType = mimeType != null ? mimeType : "image/jpeg";

            Map<String, Object> payload = Map.of(
                    "model", visionModel,
                    "messages", List.of(
                            Map.of(
                                    "role", "user",
                                    "content", List.of(
                                            Map.of("type", "text", "text", "Please describe this image in detail. What do you see? What are the main subjects, colors, activities, and key details?"),
                                            Map.of(
                                                    "type", "image_url",
                                                    "image_url", Map.of("url", "data:" + mediaType + ";base64," + base64Image)
                                            )
                                    )
                            )
                    ),
                    "max_tokens", 1024
            );

            logger.info("Calling OpenAI Vision API with model: {} for image processing", visionModel);

            String response = restClient.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            logger.info("Vision API Response status: received {} bytes", response != null ? response.length() : 0);

            JsonNode root = objectMapper.readTree(response);

            // Check for error in response
            if (root.has("error")) {
                String errorMsg = root.path("error").path("message").asText("Unknown error");
                logger.error("OpenAI API Error: {}", errorMsg);
                throw new IllegalStateException("OpenAI API Error: " + errorMsg);
            }

            String description = root.path("choices").path(0).path("message").path("content").asText();
            if (description.isEmpty()) {
                logger.warn("Empty response from Vision API");
                throw new IllegalStateException("No description generated for image");
            }

            logger.info("Image description extracted successfully: {} chars", description.length());
            return description;
        } catch (HttpClientErrorException ex) {
            logger.error("HTTP Error from OpenAI Vision API: {} {}", ex.getStatusCode(), ex.getStatusText(), ex);
            logger.error("Error Response Body: {}", ex.getResponseBodyAsString());
            throw new IllegalStateException("OpenAI API Error: " + ex.getStatusCode() + " - " + ex.getStatusText(), ex);
        } catch (Exception ex) {
            logger.error("Failed to describe image using Vision API: {}", ex.getMessage(), ex);
            throw new IllegalStateException("Failed to process image: " + ex.getMessage(), ex);
        }
    }

    /**
     * Transcribe audio/video using appropriate NVIDIA models, then Gemini as fallback.
     * - Audio files → NVIDIA ASR model (parakeet)
     * - Video files → NVIDIA Vision model (llama-3.2-11b-vision-instruct)
     * - Fallback → Gemini
     * Returns null if all providers fail.
     */
    public String transcribeAudio(byte[] audioBytes, String filename) {
        StringBuilder errors = new StringBuilder();
        String mimeType = guessMediaMimeType(filename);
        boolean isVideo = mimeType.startsWith("video/");
        logger.info("Transcribing file: {} (mime: {}, isVideo: {})", filename, mimeType, isVideo);

        if (nvidiaApiKey != null && !nvidiaApiKey.isBlank()) {
            if (isVideo) {
                // VIDEO → Use NVIDIA Vision model
                try {
                    logger.info("VIDEO: Using NVIDIA Vision model ({}) for: {}", nvidiaVisionModel, filename);
                    String description = describeVideoWithNvidiaVision(audioBytes, mimeType);
                    if (description != null && !description.isBlank()) {
                        logger.info("✅ NVIDIA Vision video transcription successful: {} chars", description.length());
                        return description;
                    }
                } catch (Exception ex) {
                    errors.append("NVIDIA Vision: ").append(ex.getMessage()).append(" | ");
                    logger.warn("NVIDIA Vision failed for video: {}", ex.getMessage());
                }
            } else {
                // AUDIO → Use Deepgram ASR
                if (deepgramApiKey != null && !deepgramApiKey.isBlank() && !deepgramApiKey.startsWith("YOUR_")) {
                    try {
                        logger.info("AUDIO: Using Deepgram ASR model ({}) for: {}", deepgramModel, filename);
                        String transcription = transcribeWithDeepgram(audioBytes, filename);
                        if (transcription != null && !transcription.isBlank()) {
                            logger.info("✅ Deepgram ASR transcription successful: {} chars", transcription.length());
                            return transcription;
                        }
                    } catch (Exception ex) {
                        errors.append("Deepgram: ").append(ex.getMessage()).append(" | ");
                        logger.warn("Deepgram ASR failed: {}", ex.getMessage());
                    }
                }
            }
        }

        // Fallback: Gemini (native audio/video understanding)
        if (geminiService.isConfigured()) {
            try {
                logger.info("Fallback: Using Gemini for: {}", filename);
                return transcribeWithGemini(audioBytes, mimeType);
            } catch (Exception ex) {
                errors.append("Gemini: ").append(ex.getMessage()).append(" | ");
                logger.warn("Gemini transcription failed: {}", ex.getMessage());
            }
        }

        logger.error("All transcription providers failed for {}: {}", filename, errors);
        return null;
    }

    private String describeVideoWithNvidiaVision(byte[] videoBytes, String mimeType) throws Exception {
        // Extract frames from video at intervals, send each to NVIDIA vision
        logger.info("Extracting frames from video for NVIDIA vision analysis...");

        // Write video to temp file for JCodec
        File tempVideo = Files.createTempFile("chatapp_video_", ".mp4").toFile();
        try (FileOutputStream fos = new FileOutputStream(tempVideo)) {
            fos.write(videoBytes);
        }

        try {
            FrameGrab grab = FrameGrab.createFrameGrab(org.jcodec.common.io.NIOUtils.readableChannel(tempVideo));

            // Get video duration estimate: extract frames at intervals
            double intervalSeconds = 5.0; // Extract a frame every 5 seconds
            int maxFrames = 10; // Cap at 10 frames to avoid API overload
            int frameCount = 0;
            StringBuilder fullTranscript = new StringBuilder();
            String previousContext = "";

            for (int i = 0; i < maxFrames; i++) {
                double targetTime = i * intervalSeconds;

                try {
                    // Seek to target time
                    grab.seekToSecondPrecise(targetTime);
                    Picture picture = grab.getNativeFrame();
                    if (picture == null) {
                        logger.info("No more frames at {}s, video likely ended", targetTime);
                        break;
                    }

                    // Convert to BufferedImage then to base64 JPEG
                    BufferedImage bufferedImage = AWTUtil.toBufferedImage(picture);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(bufferedImage, "jpg", baos);
                    byte[] frameBytes = baos.toByteArray();
                    String base64Frame = Base64.getEncoder().encodeToString(frameBytes);

                    frameCount++;
                    int timestamp = (int) targetTime;
                    logger.info("Analyzing frame {} at {}s ({} bytes)", frameCount, timestamp, frameBytes.length);

                    // Build prompt with context from previous frame
                    String prompt = "You are analyzing frame " + frameCount + " of a video at timestamp " + timestamp + " seconds. ";
                    if (!previousContext.isEmpty()) {
                        prompt += "Previous frame context: " + previousContext + ". ";
                    }
                    prompt += "Describe what you see in detail: any text, people, actions, scenes, objects. " +
                              "If there are subtitles or text on screen, transcribe them exactly. Be concise but thorough.";

                    // Send frame to NVIDIA vision
                    String description = callNvidiaVisionForFrame(base64Frame, prompt);
                    if (description != null && !description.isBlank()) {
                        String entry = "[" + formatTimestamp(timestamp) + "] " + description.trim();
                        fullTranscript.append(entry).append("\n\n");
                        previousContext = description.trim();
                        if (previousContext.length() > 200) {
                            previousContext = previousContext.substring(0, 200);
                        }
                    }

                } catch (Exception frameEx) {
                    logger.warn("Failed to process frame at {}s: {}", targetTime, frameEx.getMessage());
                    if (frameCount == 0 && i > 0) break; // Video is shorter than expected
                }
            }

            if (fullTranscript.length() == 0) {
                throw new IllegalStateException("Could not extract any frames from video");
            }

            logger.info("✅ Video analysis complete: {} frames analyzed, {} chars total", frameCount, fullTranscript.length());
            return fullTranscript.toString().trim();

        } finally {
            tempVideo.delete();
        }
    }

    private String callNvidiaVisionForFrame(String base64Frame, String prompt) throws Exception {
        Map<String, Object> request = Map.of(
                "model", nvidiaVisionModel,
                "messages", List.of(
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", prompt),
                                Map.of("type", "image_url", "image_url",
                                        Map.of("url", "data:image/jpeg;base64," + base64Frame))
                        ))
                ),
                "max_tokens", 512,
                "temperature", 0.2
        );

        String response = RestClient.create()
                .post()
                .uri("https://integrate.api.nvidia.com/v1/chat/completions")
                .header("Authorization", "Bearer " + nvidiaApiKey)
                .header("Content-Type", "application/json")
                .body(request)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(response);
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    private String formatTimestamp(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String transcribeWithDeepgram(byte[] audioBytes, String filename) throws Exception {
        String mimeType = guessMediaMimeType(filename);
        logger.info("Calling Deepgram API: model={}, file={}, size={} bytes", deepgramModel, filename, audioBytes.length);

        // Request word-level timestamps with utterances for segment timestamps
        String response = RestClient.create()
                .post()
                .uri("https://api.deepgram.com/v1/listen?model=" + deepgramModel
                        + "&smart_format=true&punctuate=true&utterances=true&words=true")
                .header("Authorization", "Token " + deepgramApiKey)
                .header("Content-Type", mimeType)
                .body(audioBytes)
                .retrieve()
                .body(String.class);

        logger.info("Deepgram response received: {} bytes", response != null ? response.length() : 0);

        JsonNode root = objectMapper.readTree(response);

        if (root.has("err_code")) {
            throw new IllegalStateException("Deepgram error: " + root.path("err_msg").asText());
        }

        // Build timestamped transcript from utterances (sentence-level segments)
        JsonNode utterances = root.path("results").path("utterances");
        if (!utterances.isMissingNode() && utterances.isArray() && utterances.size() > 0) {
            StringBuilder timestamped = new StringBuilder();
            for (JsonNode utterance : utterances) {
                double start = utterance.path("start").asDouble(0);
                String text = utterance.path("transcript").asText("").trim();
                if (!text.isEmpty()) {
                    int minutes = (int)(start / 60);
                    int seconds = (int)(start % 60);
                    timestamped.append(String.format("[%02d:%02d] %s\n", minutes, seconds, text));
                }
            }
            if (timestamped.length() > 0) {
                logger.info("✅ Deepgram timestamped transcript: {} chars", timestamped.length());
                return timestamped.toString().trim();
            }
        }

        // Fallback to plain transcript
        String transcript = root.path("results")
                .path("channels").path(0)
                .path("alternatives").path(0)
                .path("transcript").asText("");

        if (transcript.isBlank()) {
            throw new IllegalStateException("Empty transcript from Deepgram");
        }

        logger.info("✅ Deepgram plain transcript: {} chars", transcript.length());
        return transcript;
    }

    private String transcribeWithGemini(byte[] mediaBytes, String mimeType) {
        String base64Data = Base64.getEncoder().encodeToString(mediaBytes);

        Map<String, Object> payload = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", "Transcribe and describe the content of this media file in detail. " +
                                        "If it contains speech, provide the full transcription. " +
                                        "If it contains visual content, describe what happens."),
                                Map.of("inlineData", Map.of(
                                        "mimeType", mimeType,
                                        "data", base64Data
                                ))
                        ))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "maxOutputTokens", 2048
                )
        );

        String response = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1")
                .build()
                .post()
                .uri("/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response);
            if (root.has("error")) {
                throw new IllegalStateException("Gemini error: " + root.path("error").path("message").asText());
            }
            String result = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
            if (result.isEmpty()) throw new IllegalStateException("Empty response from Gemini");
            logger.info("Successfully transcribed media with Gemini: {} chars", result.length());
            return result;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to parse Gemini response: " + ex.getMessage(), ex);
        }
    }

    private String guessMediaMimeType(String filename) {
        if (filename == null) return "video/mp4";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".avi")) return "video/x-msvideo";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".flac")) return "audio/flac";
        return "video/mp4";
    }

    private String sanitizeFilename(String filename) {
        return filename == null ? "audio.mp3" : filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}


