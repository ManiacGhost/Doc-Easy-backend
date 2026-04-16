package panscience.chatapp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import panscience.chatapp.dto.AskResponse;
import panscience.chatapp.dto.AssetResponse;
import panscience.chatapp.dto.PlayLinkResponse;
import panscience.chatapp.dto.SummaryResponse;
import panscience.chatapp.dto.TimestampItem;
import panscience.chatapp.dto.TimestampResponse;
import panscience.chatapp.entity.AssetType;
import panscience.chatapp.entity.QaRecord;
import panscience.chatapp.entity.UploadedAsset;
import panscience.chatapp.entity.User;
import panscience.chatapp.exception.BadRequestException;
import panscience.chatapp.exception.NotFoundException;
import panscience.chatapp.repository.QaRecordRepository;
import panscience.chatapp.repository.UploadedAssetRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
public class AssetQaService {
    private static final Logger logger = LoggerFactory.getLogger(AssetQaService.class);

    private final UploadedAssetRepository uploadedAssetRepository;
    private final QaRecordRepository qaRecordRepository;
    private final StorageService storageService;
    private final AssetTypeResolver assetTypeResolver;
    private final TextExtractionService textExtractionService;
    private final AiService aiService;
    private final StorageRouterService storageRouterService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper cacheMapper;

    public AssetQaService(
            UploadedAssetRepository uploadedAssetRepository,
            QaRecordRepository qaRecordRepository,
            StorageService storageService,
            AssetTypeResolver assetTypeResolver,
            TextExtractionService textExtractionService,
            AiService aiService,
            StorageRouterService storageRouterService,
            RedisTemplate<String, String> redisTemplate
    ) {
        this.uploadedAssetRepository = uploadedAssetRepository;
        this.qaRecordRepository = qaRecordRepository;
        this.storageService = storageService;
        this.assetTypeResolver = assetTypeResolver;
        this.textExtractionService = textExtractionService;
        this.aiService = aiService;
        this.storageRouterService = storageRouterService;
        this.redisTemplate = redisTemplate;
        this.cacheMapper = new ObjectMapper();
        this.cacheMapper.registerModule(new JavaTimeModule());
        this.cacheMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @CacheEvict(value = "assets", key = "#user.id.toString()")
    public AssetResponse upload(MultipartFile file, User user) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required for upload");
        }

        logger.info("Starting upload of file: {} for user: {}", file.getOriginalFilename(), user.getEmail());
        AssetType assetType = assetTypeResolver.resolve(file);
        logger.info("Detected asset type: {} for file: {}", assetType, file.getOriginalFilename());

        String cloudUrl;
        try {
            CloudStorageService cloudStorageService = storageRouterService.getStorageService(assetType);
            cloudUrl = cloudStorageService.upload(
                    file.getOriginalFilename(),
                    file.getInputStream(),
                    file.getContentType()
            );
            logger.info("File uploaded to cloud storage: {}", cloudUrl);
        } catch (Exception ex) {
            logger.error("Failed to upload file to cloud storage. Root cause: {}", ex.getMessage(), ex);
            throw new IllegalStateException("Failed to upload file to cloud storage", ex);
        }

        logger.info("Starting text extraction for uploaded file: {}", file.getOriginalFilename());
        String extractedText = textExtractionService.extractText(file, assetType);
        logger.info("Text extraction completed. Extracted text length: {} chars", extractedText != null ? extractedText.length() : 0);

        UploadedAsset asset = new UploadedAsset();
        asset.setOriginalFilename(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        asset.setStoredFilename(cloudUrl);
        asset.setCloudUrl(cloudUrl);
        asset.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        asset.setAssetType(assetType);
        asset.setSizeBytes(file.getSize());
        asset.setExtractedText(extractedText);
        asset.setUploadedAt(OffsetDateTime.now());
        asset.setUser(user);

        UploadedAsset saved = uploadedAssetRepository.save(asset);
        // Also manually evict the assets list cache key
        try { redisTemplate.delete("assets:" + user.getId()); } catch (Exception ignored) {}
        logger.info("Asset saved to database with ID: {} for user: {}", saved.getId(), user.getEmail());
        return toAssetResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> listAssets(User user) {
        String cacheKey = "assets:" + user.getId();
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                logger.info("Cache HIT for assets list of user: {}", user.getId());
                return cacheMapper.readValue(cached, new TypeReference<List<AssetResponse>>() {});
            }
        } catch (Exception e) {
            logger.warn("Cache read failed for assets list: {}", e.getMessage());
        }

        List<AssetResponse> result = uploadedAssetRepository.findByUserOrderByUploadedAtDesc(user).stream()
                .map(this::toAssetResponse)
                .toList();

        try {
            redisTemplate.opsForValue().set(cacheKey, cacheMapper.writeValueAsString(result), 10, TimeUnit.MINUTES);
            logger.info("Cache SET for assets list of user: {}", user.getId());
        } catch (Exception e) {
            logger.warn("Cache write failed for assets list: {}", e.getMessage());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public AssetResponse getAsset(Long id, User user) {
        return toAssetResponse(findAsset(id, user));
    }

    @Transactional(readOnly = true)
    public SummaryResponse summarize(Long assetId, User user) {
        String cacheKey = "summary:" + assetId + ":user:" + user.getId();
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                logger.info("Cache HIT for summary assetId: {}", assetId);
                return cacheMapper.readValue(cached, SummaryResponse.class);
            }
        } catch (Exception e) {
            logger.warn("Cache read failed for summary: {}", e.getMessage());
        }

        logger.info("Requesting summary for asset ID: {} by user: {}", assetId, user.getEmail());
        UploadedAsset asset = findAsset(assetId, user);
        String extractedText = asset.getExtractedText();

        if (extractedText == null || extractedText.isBlank()) {
            String msg = asset.getAssetType() == AssetType.AUDIO || asset.getAssetType() == AssetType.VIDEO
                    ? "Transcription is not available for this file. Please try re-uploading."
                    : "No content could be extracted from this file.";
            return new SummaryResponse(assetId, msg);
        }

        logger.info("Asset found: type={}, extracted text length={}", asset.getAssetType(), extractedText.length());
        String summary = aiService.summarize(extractedText);
        logger.info("Summary generated: {} chars", summary != null ? summary.length() : 0);
        SummaryResponse response = new SummaryResponse(assetId, summary);

        try {
            redisTemplate.opsForValue().set(cacheKey, cacheMapper.writeValueAsString(response), 30, TimeUnit.MINUTES);
            logger.info("Cache SET for summary assetId: {}", assetId);
        } catch (Exception e) {
            logger.warn("Cache write failed for summary: {}", e.getMessage());
        }
        return response;
    }

    public AskResponse ask(Long assetId, String question, User user) {
        UploadedAsset asset = findAsset(assetId, user);
        String extractedText = asset.getExtractedText();

        if (extractedText == null || extractedText.isBlank()) {
            String msg = asset.getAssetType() == AssetType.AUDIO || asset.getAssetType() == AssetType.VIDEO
                    ? "Transcription is not available for this file. Gemini may be rate-limited. Please try re-uploading."
                    : "No content was extracted from this file to answer questions.";
            return new AskResponse(assetId, question, msg, List.of());
        }

        String answer = aiService.answerQuestion(extractedText, question);

        QaRecord qaRecord = new QaRecord();
        qaRecord.setAsset(asset);
        qaRecord.setQuestion(question);
        qaRecord.setAnswer(answer);
        qaRecord.setCreatedAt(OffsetDateTime.now());
        qaRecordRepository.save(qaRecord);

        List<TimestampItem> timestamps = (asset.getAssetType() == AssetType.VIDEO || asset.getAssetType() == AssetType.AUDIO)
                ? buildTimestamps(asset, question)
                : List.of();
        return new AskResponse(assetId, question, answer, timestamps);
    }

    @Transactional(readOnly = true)
    public TimestampResponse findTopicTimestamps(Long assetId, String topic, User user) {
        UploadedAsset asset = findAsset(assetId, user);
        return new TimestampResponse(assetId, topic, buildTimestamps(asset, topic));
    }

    @Transactional(readOnly = true)
    public PlayLinkResponse buildPlayLink(Long assetId, int startSeconds, User user) {
        UploadedAsset asset = findAsset(assetId, user);
        String cloudUrl = asset.getCloudUrl();
        if (cloudUrl == null || cloudUrl.isEmpty()) {
            throw new panscience.chatapp.exception.NotFoundException("No cloud URL found for asset: " + assetId);
        }
        int clampedStart = Math.max(0, startSeconds);
        // Append fragment for video seek; use fl_attachment=false for Cloudinary to allow inline streaming
        String playableUrl = cloudUrl;
        if (cloudUrl.contains("cloudinary.com")) {
            // Ensure Cloudinary delivers inline (not as attachment) and supports range requests
            playableUrl = cloudUrl.replace("/upload/", "/upload/fl_attachment:false/");
        }
        if (clampedStart > 0) {
            playableUrl = playableUrl + "#t=" + clampedStart;
        }
        return new PlayLinkResponse(assetId, clampedStart, playableUrl);
    }

    @Transactional(readOnly = true)
    public UploadedAsset findAsset(Long id, User user) {
        return uploadedAssetRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new NotFoundException("Asset not found for id: " + id));
    }

    private AssetResponse toAssetResponse(UploadedAsset asset) {
        return new AssetResponse(
                asset.getId(),
                asset.getOriginalFilename(),
                asset.getContentType(),
                asset.getAssetType(),
                asset.getSizeBytes(),
                asset.getUploadedAt()
        );
    }

    private List<TimestampItem> buildTimestamps(UploadedAsset asset, String topic) {
        if (asset.getAssetType() != AssetType.AUDIO && asset.getAssetType() != AssetType.VIDEO) {
            return List.of();
        }

        String text = asset.getExtractedText();
        if (text == null || text.isBlank()) {
            return List.of(new TimestampItem(0, "No transcription available."));
        }

        // Check if transcript has inline timestamps like [MM:SS]
        boolean hasTimestamps = text.contains("[") && text.matches("(?s).*\\[\\d{2}:\\d{2}\\].*");

        if (hasTimestamps && topic != null && !topic.isBlank()) {
            List<TimestampItem> result = findKeywordTimestamps(text, topic);
            if (!result.isEmpty()) return deduplicateTimestamps(result);
        }

        // Fall back to AI-generated timestamps — ask AI to summarize distinct visual moments
        try {
            String response = aiService.answerQuestion(text,
                    "You are given a timestamped description of a video (frames analyzed at intervals). " +
                    "The user asked: '" + topic + "'. " +
                    "Based on the content, identify up to 5 DISTINCT and MEANINGFUL moments in the video, each with a UNIQUE short label (max 60 chars) describing what happens at that time. " +
                    "Do NOT repeat the same label. Do NOT copy raw descriptions — summarize briefly. " +
                    "Reply ONLY with a JSON array: [{\"seconds\":0,\"label\":\"Brief unique description\"}]. " +
                    "Use seconds as integers matching the [MM:SS] timestamps in the transcript.");
            List<TimestampItem> parsed = parseTimestampsFromAi(response);
            List<TimestampItem> deduped = deduplicateTimestamps(parsed);
            if (!deduped.isEmpty() && !(deduped.size() == 1 && deduped.get(0).label().equals("Start of recording"))) {
                return deduped;
            }
        } catch (Exception e) {
            logger.warn("AI timestamp generation failed: {}", e.getMessage());
        }

        // Last resort: parse unique frames directly from the transcript
        if (hasTimestamps) {
            return extractUniqueFrameTimestamps(text);
        }

        return List.of(new TimestampItem(0, "Start of recording"));
    }

    /**
     * Parses [MM:SS] entries from transcript and returns unique ones (deduped by label similarity).
     */
    private List<TimestampItem> extractUniqueFrameTimestamps(String transcript) {
        List<TimestampItem> result = new ArrayList<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\[(\\d{2}):(\\d{2})\\]\\s*(.+?)(?=\\[\\d{2}:\\d{2}\\]|$)", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher m = p.matcher(transcript);
        String lastLabel = null;
        while (m.find() && result.size() < 5) {
            int seconds = Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
            String label = m.group(3).replaceAll("\\s+", " ").trim();
            if (label.length() > 80) label = label.substring(0, 80) + "...";
            // Skip if label is too similar to the previous one (first 60 chars match)
            String sig = label.length() > 60 ? label.substring(0, 60) : label;
            if (sig.equals(lastLabel)) continue;
            lastLabel = sig;
            result.add(new TimestampItem(seconds, label));
        }
        return result.isEmpty() ? List.of(new TimestampItem(0, "Start of recording")) : result;
    }

    /**
     * Removes duplicate timestamp entries where the label is identical or near-identical.
     */
    private List<TimestampItem> deduplicateTimestamps(List<TimestampItem> items) {
        List<TimestampItem> result = new ArrayList<>();
        java.util.Set<String> seenLabels = new java.util.LinkedHashSet<>();
        for (TimestampItem item : items) {
            // Normalize: lowercase, strip punctuation, first 60 chars as signature
            String sig = item.label().toLowerCase().replaceAll("[^a-z0-9 ]", "").trim();
            if (sig.length() > 60) sig = sig.substring(0, 60);
            if (seenLabels.add(sig)) {
                result.add(item);
            }
        }
        return result;
    }

    private List<TimestampItem> findKeywordTimestamps(String transcript, String topic) {
        List<TimestampItem> result = new ArrayList<>();
        String lowerTopic = topic.toLowerCase();
        String[] keywords = lowerTopic.split("\\s+");
        String[] lines = transcript.split("\n");

        for (String line : lines) {
            String lowerLine = line.toLowerCase();
            boolean matches = false;
            for (String keyword : keywords) {
                if (keyword.length() > 2 && lowerLine.contains(keyword)) {
                    matches = true;
                    break;
                }
            }
            if (matches) {
                // Parse [MM:SS] from line
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\\[(\\d{2}):(\\d{2})\\]")
                        .matcher(line);
                if (m.find()) {
                    int minutes = Integer.parseInt(m.group(1));
                    int seconds = Integer.parseInt(m.group(2));
                    int totalSeconds = minutes * 60 + seconds;
                    // Remove the timestamp prefix for the label
                    String label = line.replaceAll("\\[\\d{2}:\\d{2}\\]\\s*", "").trim();
                    if (label.length() > 80) label = label.substring(0, 80) + "...";
                    result.add(new TimestampItem(totalSeconds, label));
                    if (result.size() >= 5) break;
                }
            }
        }
        return result;
    }

    private List<TimestampItem> parseTimestampsFromAi(String aiResponse) {
        List<TimestampItem> result = new ArrayList<>();
        try {
            // Find JSON array in response
            int start = aiResponse.indexOf('[');
            int end = aiResponse.lastIndexOf(']');
            if (start >= 0 && end > start) {
                String jsonArray = aiResponse.substring(start, end + 1);
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode nodes = mapper.readTree(jsonArray);
                for (com.fasterxml.jackson.databind.JsonNode node : nodes) {
                    int seconds = node.path("seconds").asInt(0);
                    String label = node.path("label").asText("Section");
                    result.add(new TimestampItem(seconds, label));
                }
            }
        } catch (Exception e) {
            logger.warn("Could not parse AI timestamps: {}", e.getMessage());
        }

        if (result.isEmpty()) {
            result.add(new TimestampItem(0, "Start of recording"));
        }

        return result;
    }
}
