package panscience.chatapp.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import panscience.chatapp.entity.AssetType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TextExtractionService {
    private static final Logger logger = LoggerFactory.getLogger(TextExtractionService.class);

    private final MediaProcessingService mediaProcessingService;

    public TextExtractionService(MediaProcessingService mediaProcessingService) {
        this.mediaProcessingService = mediaProcessingService;
    }

    public String extractText(MultipartFile file, AssetType type) {
        try {
            if (type == AssetType.DOCUMENT) {
                logger.info("Extracting text from DOCUMENT: {}", file.getOriginalFilename());
                return extractPdfText(file.getBytes(), file.getOriginalFilename());
            } else if (type == AssetType.IMAGE) {
                // Use Vision API to describe the image
                logger.info("Processing IMAGE with Vision API: {}", file.getOriginalFilename());
                String description = mediaProcessingService.describeImage(file.getBytes(), file.getContentType());
                logger.info("IMAGE description extracted: {} characters", description != null ? description.length() : 0);
                return description;
            } else if (type == AssetType.AUDIO || type == AssetType.VIDEO) {
                logger.info("Processing AUDIO/VIDEO with Transcription API: {}", file.getOriginalFilename());
                String transcription = mediaProcessingService.transcribeAudio(file.getBytes(), file.getOriginalFilename());
                if (transcription != null && !transcription.isBlank()) {
                    logger.info("AUDIO/VIDEO transcription extracted: {} characters", transcription.length());
                    return transcription;
                } else {
                    logger.warn("Transcription returned null/empty for: {}", file.getOriginalFilename());
                    return null;
                }
            } else {
                return "Unknown file type: " + file.getOriginalFilename();
            }
        } catch (Exception ex) {
            logger.error("Extraction failed for file: {}", file.getOriginalFilename(), ex);
            return "Extraction failed for file: " + file.getOriginalFilename() + ". Error: " + ex.getMessage();
        }
    }

    private String extractPdfText(byte[] bytes, String filename) throws IOException {
        try (PDDocument document = PDDocument.load(bytes)) {
            String extractedText = new PDFTextStripper().getText(document);
            
            // If text extraction returned empty or very little text, it might be a scanned PDF
            if (extractedText == null || extractedText.trim().isEmpty()) {
                logger.warn("PDFBox extracted no text from PDF, attempting vision-based OCR: {}", filename);
                // Try to extract using vision API for scanned PDFs
                return extractPdfWithVision(bytes, filename);
            }
            
            logger.info("Successfully extracted {} characters from PDF: {}", extractedText.length(), filename);
            return extractedText;
        } catch (IOException parseEx) {
            logger.warn("Failed to parse PDF with PDFBox, attempting vision-based extraction: {} - {}", filename, parseEx.getMessage());
            // If bytes are not a valid PDF or PDFBox fails, try vision API
            return extractPdfWithVision(bytes, filename);
        }
    }
    
    private String extractPdfWithVision(byte[] bytes, String filename) {
        try {
            logger.info("Using Vision API for PDF text extraction (OCR): {}", filename);
            // Use vision API to describe/read the PDF content
            String result = mediaProcessingService.describeImage(bytes, "application/pdf");
            
            if (result != null && !result.trim().isEmpty() && 
                !result.contains("Image vision not configured") && 
                !result.contains("Failed to process")) {
                logger.info("Successfully extracted text via Vision API from PDF: {}", filename);
                return result;
            }
            
            // Vision API also failed, return fallback
            logger.warn("Vision API also failed for PDF: {}", filename);
            return "Could not extract text from PDF. The document may be encrypted, corrupted, or image-based without OCR support.";
        } catch (Exception ex) {
            logger.error("Vision-based PDF extraction also failed: {}", ex.getMessage());
            return "Could not extract text from PDF: " + ex.getMessage();
        }
    }
}

