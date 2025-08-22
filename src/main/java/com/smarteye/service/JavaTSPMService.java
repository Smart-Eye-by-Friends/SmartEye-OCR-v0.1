package com.smarteye.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.InitializingBean;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Base64;

/**
 * Java 네이티브 TSPM (Text & Semantic Processing Module) 서비스
 * Python 스크립트 대신 Java를 사용하여 이미지 처리 및 Vision API 호출
 */
@Service("javaTSPMService")
@RequiredArgsConstructor
@Slf4j
public class JavaTSPMService implements InitializingBean {

    @Value("${smarteye.openai.api-key:}")
    private String openaiApiKey;
    
    @Value("${smarteye.tesseract.data-path:/usr/share/tesseract-ocr/5/tessdata}")
    private String tesseractDataPath;
    
    @Value("${smarteye.tesseract.language:kor+eng}")
    private String tesseractLanguage;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Tesseract tesseract;
    
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    
    @Override
    public void afterPropertiesSet() throws Exception {
        initializeTesseract();
        log.info("Java TSPM Service 초기화 완료");
        if (openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
            log.warn("OpenAI API 키가 설정되지 않았습니다. Vision API 기능이 제한됩니다.");
        }
    }
    
    /**
     * Tesseract OCR 초기화
     */
    private void initializeTesseract() {
        try {
            tesseract = new Tesseract();
            tesseract.setDatapath(tesseractDataPath);
            tesseract.setLanguage(tesseractLanguage);
            tesseract.setPageSegMode(6); // 균등한 텍스트 블록
            tesseract.setOcrEngineMode(3); // LSTM + Legacy
            
            log.info("Tesseract OCR 초기화 완료 - 언어: {}, 데이터 경로: {}", 
                     tesseractLanguage, tesseractDataPath);
        } catch (Exception e) {
            log.error("Tesseract 초기화 실패", e);
            throw new RuntimeException("OCR 서비스 초기화 실패", e);
        }
    }
    
    /**
     * Java 기반 Tesseract OCR 처리
     */
    public Map<String, Object> performOCR(String imagePath) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                throw new IllegalArgumentException("이미지 파일이 존재하지 않습니다: " + imagePath);
            }
            
            log.info("OCR 처리 시작 - 파일: {}", imagePath);
            
            // Tesseract OCR 실행
            String extractedText = tesseract.doOCR(imageFile);
            double confidence = calculateOCRConfidence(extractedText);
            
            // 결과 구성
            String cleanText = extractedText.trim();
            result.put("text", cleanText);
            result.put("confidence", confidence);
            result.put("word_count", cleanText.isEmpty() ? 0 : cleanText.split("\\s+").length);
            result.put("character_count", cleanText.length());
            result.put("processing_method", "java_tesseract");
            result.put("timestamp", System.currentTimeMillis());
            
            log.info("OCR 처리 완료 - 파일: {}, 신뢰도: {:.2f}, 글자수: {}", 
                     imagePath, confidence, cleanText.length());
            
        } catch (TesseractException e) {
            log.error("Tesseract OCR 처리 실패 - 파일: {}", imagePath, e);
            result.put("text", "");
            result.put("confidence", 0.0);
            result.put("error", "OCR 처리 중 오류 발생: " + e.getMessage());
        } catch (Exception e) {
            log.error("예상치 못한 오류 - 파일: {}", imagePath, e);
            result.put("text", "");
            result.put("confidence", 0.0);
            result.put("error", "처리 중 오류 발생: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Java 기반 OpenAI Vision API 처리
     */
    public Map<String, Object> performVisionAnalysis(String imagePath) {
        Map<String, Object> result = new HashMap<>();
        
        if (openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
            log.warn("OpenAI API 키가 설정되지 않았습니다. 기본 응답을 반환합니다.");
            result.put("description", "OpenAI API 키가 설정되지 않음");
            result.put("confidence", 0.1);
            result.put("error", "OpenAI API 키가 설정되지 않음");
            return result;
        }
        
        try {
            // 이미지를 Base64로 인코딩
            String base64Image = encodeImageToBase64(imagePath);
            
            // OpenAI API 요청 구성
            Map<String, Object> requestBody = createVisionAPIRequest(base64Image);
            
            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + openaiApiKey);
            headers.set("Content-Type", "application/json");
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            // API 호출
            ResponseEntity<String> response = restTemplate.exchange(
                OPENAI_API_URL, 
                HttpMethod.POST, 
                entity, 
                String.class
            );
            
            // 응답 파싱
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            String description = jsonResponse.path("choices").get(0)
                .path("message").path("content").asText();
            int tokensUsed = jsonResponse.path("usage").path("total_tokens").asInt();
            
            // 결과 구성
            result.put("description", description);
            result.put("confidence", 0.85);
            result.put("model", "gpt-4-vision-preview");
            result.put("tokens_used", tokensUsed);
            result.put("processing_method", "java_rest_api");
            result.put("timestamp", System.currentTimeMillis());
            
            log.info("Vision API 분석 완료 - 파일: {}, 토큰 사용량: {}", imagePath, tokensUsed);
            
        } catch (Exception e) {
            log.error("Vision API 처리 실패 - 파일: {}", imagePath, e);
            result.put("description", "");
            result.put("confidence", 0.0);
            result.put("error", "Vision API 처리 중 오류 발생: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 통합 텍스트 및 시각적 분석
     */
    public Map<String, Object> performCombinedAnalysis(String imagePath) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("통합 분석 시작 - 파일: {}", imagePath);
            
            // OCR과 Vision API 순차 실행
            Map<String, Object> ocrResult = performOCR(imagePath);
            Map<String, Object> visionResult = performVisionAnalysis(imagePath);
            
            // 결과 통합
            result.put("ocr", ocrResult);
            result.put("vision", visionResult);
            
            // 통합 신뢰도 계산
            double ocrConfidence = (Double) ocrResult.getOrDefault("confidence", 0.0);
            double visionConfidence = (Double) visionResult.getOrDefault("confidence", 0.0);
            double combinedConfidence = (ocrConfidence + visionConfidence) / 2.0;
            
            result.put("combined_confidence", combinedConfidence);
            result.put("processing_method", "java_native_combined");
            result.put("timestamp", System.currentTimeMillis());
            
            log.info("통합 분석 완료 - 파일: {}, 통합 신뢰도: {:.2f}", imagePath, combinedConfidence);
            
        } catch (Exception e) {
            log.error("통합 분석 처리 실패 - 파일: {}", imagePath, e);
            result.put("error", "통합 분석 중 오류 발생: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 레이아웃 블록 이미지 크롭 (Java 네이티브)
     */
    public String cropLayoutBlock(String originalImagePath, int x1, int y1, int x2, int y2, String outputPath) throws IOException {
        log.debug("이미지 크롭 시작 - 원본: {}, 좌표: ({},{}) to ({},{})", originalImagePath, x1, y1, x2, y2);
        
        // 이미지 로드
        BufferedImage originalImage = ImageIO.read(new File(originalImagePath));
        if (originalImage == null) {
            throw new IOException("이미지를 로드할 수 없습니다: " + originalImagePath);
        }
        
        // 좌표 보정
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        
        x1 = Math.max(0, Math.min(x1, width - 1));
        y1 = Math.max(0, Math.min(y1, height - 1));
        x2 = Math.max(x1 + 1, Math.min(x2, width));
        y2 = Math.max(y1 + 1, Math.min(y2, height));
        
        // 크롭 실행
        BufferedImage croppedImage = originalImage.getSubimage(x1, y1, x2 - x1, y2 - y1);
        
        // 출력 디렉토리 생성
        File outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs();
        
        // 이미지 저장
        ImageIO.write(croppedImage, "jpg", outputFile);
        
        log.debug("이미지 크롭 완료 - 출력: {}", outputPath);
        return outputPath;
    }
    
    /**
     * OpenAI Vision API 요청 구성
     */
    private Map<String, Object> createVisionAPIRequest(String base64Image) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", "gpt-4-vision-preview");
        request.put("max_tokens", 1000);
        request.put("temperature", 0.1);
        
        // 메시지 구성
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        
        // 컨텐츠 (텍스트 + 이미지)
        List<Map<String, Object>> content = new ArrayList<>();
        
        // 텍스트 부분
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", "이 이미지를 자세히 분석하고 한국어로 설명해주세요. 텍스트가 있다면 추출하고, 전체적인 레이아웃과 구성 요소를 설명해주세요.");
        content.add(textContent);
        
        // 이미지 부분
        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("type", "image_url");
        Map<String, Object> imageUrl = new HashMap<>();
        imageUrl.put("url", "data:image/jpeg;base64," + base64Image);
        imageContent.put("image_url", imageUrl);
        content.add(imageContent);
        
        message.put("content", content);
        messages.add(message);
        request.put("messages", messages);
        
        return request;
    }
    
    /**
     * 이미지를 Base64로 인코딩
     */
    private String encodeImageToBase64(String imagePath) throws IOException {
        BufferedImage image = ImageIO.read(new File(imagePath));
        if (image == null) {
            throw new IOException("이미지를 로드할 수 없습니다: " + imagePath);
        }
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }
    
    /**
     * OCR 신뢰도 계산 (간단한 휴리스틱) - 테스트용 public 접근
     */
    public double calculateOCRConfidence(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0.0;
        }
        
        // 텍스트 품질 기반 신뢰도 계산
        String cleanText = text.trim();
        int totalChars = cleanText.length();
        
        if (totalChars == 0) {
            return 0.0;
        }
        
        // 유효한 문자 (한글, 영문, 숫자, 공백, 기본 구두점) 비율 계산
        int validChars = cleanText.replaceAll("[^\\p{L}\\p{N}\\s.,!?:;\"'()\\-]", "").length();
        double validRatio = (double) validChars / totalChars;
        
        // 길이에 따른 보정 (너무 짧거나 긴 텍스트는 신뢰도 감소)
        double lengthFactor = 1.0;
        if (totalChars < 3) {
            lengthFactor = 0.5;
        } else if (totalChars > 1000) {
            lengthFactor = 0.8;
        }
        
        return Math.min(1.0, validRatio * lengthFactor);
    }
    
    /**
     * Tesseract 사용 가능 여부 확인
     */
    public boolean isTesseractAvailable() {
        return tesseract != null;
    }
    
    /**
     * 서비스 상태 확인
     */
    public Map<String, Object> getServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("tesseract_available", tesseract != null);
        status.put("openai_available", openaiApiKey != null && !openaiApiKey.trim().isEmpty());
        status.put("tesseract_language", tesseractLanguage);
        status.put("tesseract_data_path", tesseractDataPath);
        status.put("processing_method", "java_native");
        status.put("ocr_status", "tesseract_enabled");
        status.put("vision_api_status", openaiApiKey != null ? "available" : "api_key_missing");
        return status;
    }
}
