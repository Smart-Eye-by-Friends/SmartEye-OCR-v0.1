package com.smarteye.infrastructure.external;

import com.smarteye.presentation.dto.AIDescriptionResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.exception.FileProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;

/**
 * AI 설명 서비스 - OpenAI Vision API를 이용한 그림/표 설명 생성
 * Python api_server.py의 call_openai_api() 메서드 변환
 */
@Service
public class AIDescriptionService {
    
    private static final Logger logger = LoggerFactory.getLogger(AIDescriptionService.class);
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    @Value("${smarteye.api.openai.base-url:https://api.openai.com/v1}")
    private String openaiBaseUrl;
    
    @Value("${smarteye.api.openai.timeout:60}")
    private int timeoutSeconds;
    
    @Value("${smarteye.api.openai.model:gpt-4-turbo}")
    private String openaiModel;
    
    @Value("${smarteye.api.openai.max-tokens:600}")
    private int maxTokens;
    
    @Value("${smarteye.api.openai.temperature:0.2}")
    private double temperature;
    
    // Python 코드에서 가져온 AI 설명 대상 클래스
    private static final Set<String> TARGET_CLASSES = Set.of("figure", "table");
    
    // 프롬프트 템플릿 (Python 코드와 동일)
    private static final Map<String, String> PROMPTS = Map.of(
        "figure", "이 그림(figure)의 내용을 간단히 요약해 주세요.",
        "table", "이 표(table)의 주요 내용을 요약해 주세요."
    );
    
    private static final String SYSTEM_PROMPT = 
        "당신은 시각 장애 아동을 위한 학습 AI 비서입니다.\n" +
        "시각 자료의 내용을 한국어로 간결하고 명확하게 설명해주세요.\n" +
        "설명은 음성으로 변환될 수 있도록 직접적이고 이해하기 쉽게 작성해주세요.";
    
    public AIDescriptionService(@Qualifier("openaiWebClientBuilder") WebClient.Builder webClientBuilder, 
                               ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.build();
    }
    
    /**
     * OpenAI Vision API를 이용한 그림/표 설명 생성
     * Python api_server.py의 call_openai_api() 메서드와 동일한 로직
     * @param image 원본 이미지
     * @param layoutInfo 레이아웃 분석 결과
     * @param apiKey OpenAI API 키
     * @return AI 설명 결과 리스트
     */
    public CompletableFuture<List<AIDescriptionResult>> generateDescriptions(
            BufferedImage image, 
            List<LayoutInfo> layoutInfo, 
            String apiKey) {
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("API 키가 제공되지 않아 AI 설명을 건너뜁니다.");
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        
        return CompletableFuture.supplyAsync(() -> {
            List<AIDescriptionResult> apiResults = new ArrayList<>();
            
            try {
                logger.info("OpenAI API 처리 시작...");
                
                for (LayoutInfo layout : layoutInfo) {
                    String className = layout.getClassName().toLowerCase();
                    if (!TARGET_CLASSES.contains(className)) {
                        continue;
                    }
                    
                    logger.debug("AI 설명 생성 중: ID {} - 클래스 '{}'", layout.getId(), className);
                    
                    // 이미지 크롭
                    int[] box = layout.getBox();
                    BufferedImage croppedImg = image.getSubimage(box[0], box[1], box[2] - box[0], box[3] - box[1]);
                    
                    try {
                        // OpenAI API 호출
                        String description = callOpenAIVisionAPI(croppedImg, className, apiKey);

                        // AI 분석 메타데이터 생성
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("api_model", openaiModel);
                        metadata.put("temperature", temperature);
                        metadata.put("max_tokens", maxTokens);
                        metadata.put("image_size", croppedImg.getWidth() + "x" + croppedImg.getHeight());
                        metadata.put("element_area", (box[2] - box[0]) * (box[3] - box[1]));

                        // AI 신뢰도 계산 (설명 길이 기반)
                        double confidence = calculateAIConfidence(description, className);

                        AIDescriptionResult result = new AIDescriptionResult(
                            layout.getId(),
                            className,
                            box,
                            description,
                            className, // elementType
                            confidence,
                            description, // extractedText (AI의 경우 설명이 추출된 텍스트역할)
                            metadata
                        );

                        apiResults.add(result);
                        
                        logger.info("API 응답 완료: ID {} - {}", layout.getId(), className);
                        
                    } catch (Exception e) {
                        logger.error("API 요청 실패: ID {} - {}", layout.getId(), e.getMessage(), e);
                    }
                }
                
                logger.info("OpenAI API 처리 완료: {}개 설명 생성", apiResults.size());
                return apiResults;
                
            } catch (Exception e) {
                logger.error("AI 설명 생성 전체 실패: {}", e.getMessage(), e);
                throw new FileProcessingException("AI 설명 생성에 실패했습니다: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * OpenAI Vision API 호출
     * @param image 분석할 이미지
     * @param elementType 요소 타입 (figure, table)
     * @param apiKey OpenAI API 키
     * @return 생성된 설명
     */
    private String callOpenAIVisionAPI(BufferedImage image, String elementType, String apiKey) {
        try {
            // 이미지를 base64로 인코딩
            String imageBase64 = imageToBase64(image);
            
            // 프롬프트 선택
            String prompt = PROMPTS.getOrDefault(elementType, 
                "이 " + elementType + "의 내용을 간단히 설명해 주세요.");
            
            // 요청 본문 구성
            Map<String, Object> requestBody = Map.of(
                "model", openaiModel,
                "messages", List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", List.of(
                        Map.of("type", "text", "text", prompt),
                        Map.of("type", "image_url", "image_url", 
                            Map.of("url", "data:image/png;base64," + imageBase64))
                    ))
                ),
                "temperature", temperature,
                "max_tokens", maxTokens
            );
            
            // OpenAI API 호출
            String response = webClient
                .post()
                .uri(openaiBaseUrl + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();
            
            // 응답 파싱
            return parseOpenAIResponse(response);
            
        } catch (WebClientResponseException e) {
            logger.error("OpenAI API HTTP 오류: {} - {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new FileProcessingException("OpenAI API 호출 실패: " + e.getStatusCode(), e);
            
        } catch (Exception e) {
            logger.error("OpenAI API 호출 중 오류: {}", e.getMessage(), e);
            throw new FileProcessingException("OpenAI API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 이미지를 Base64로 인코딩
     * @param image BufferedImage
     * @return Base64 인코딩된 문자열
     */
    private String imageToBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }
    
    /**
     * OpenAI API 응답 파싱
     * @param response JSON 응답
     * @return 추출된 설명 텍스트
     */
    private String parseOpenAIResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.get("choices");
            
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode message = firstChoice.get("message");
                JsonNode content = message.get("content");
                
                return content.asText().strip();
            }
            
            throw new FileProcessingException("OpenAI API 응답에서 내용을 찾을 수 없습니다.");
            
        } catch (Exception e) {
            logger.error("OpenAI API 응답 파싱 실패: {}", e.getMessage(), e);
            throw new FileProcessingException("OpenAI API 응답 파싱에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * AI 설명 대상 클래스인지 확인
     * @param className 클래스명
     * @return AI 설명 대상 여부
     */
    public boolean isAITargetClass(String className) {
        return TARGET_CLASSES.contains(className.toLowerCase());
    }

    /**
     * AI 설명 신뢰도 계산
     * 설명의 길이, 키워드 포함 여부 등을 고려하여 신뢰도 계산
     * @param description AI가 생성한 설명
     * @param className 요소 유형
     * @return 0.0~1.0 사이의 신뢰도 값
     */
    private double calculateAIConfidence(String description, String className) {
        if (description == null || description.trim().isEmpty()) {
            return 0.1; // 비어있으면 낮은 신뢰도
        }

        double confidence = 0.5; // 기본 신뢰도

        // 설명 길이에 따른 신뢰도 조정
        int length = description.length();
        if (length > 20 && length < 300) {
            confidence += 0.2; // 적당한 길이면 가점
        } else if (length >= 300) {
            confidence += 0.1; // 너무 길면 약간 감점
        }

        // 클래스별 키워드 포함 여부
        if ("figure".equals(className.toLowerCase())) {
            if (description.contains("그림") || description.contains("이미지") ||
                description.contains("도표") || description.contains("사진")) {
                confidence += 0.2;
            }
        } else if ("table".equals(className.toLowerCase())) {
            if (description.contains("표") || description.contains("데이터") ||
                description.contains("행") || description.contains("열")) {
                confidence += 0.2;
            }
        }

        // 한글 포함 여부 (시스템 프롬프트가 한국어로 설정되어 있음)
        if (description.matches(".*[한글]+.*")) {
            confidence += 0.1;
        }

        // 0.0 ~ 1.0 범위로 제한
        return Math.max(0.0, Math.min(1.0, confidence));
    }
    
    /**
     * OpenAI API 키 유효성 검증
     * @param apiKey API 키
     * @return 유효성 여부
     */
    public boolean validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }
        
        try {
            // 간단한 텍스트 완성 요청으로 API 키 테스트
            Map<String, Object> testRequest = Map.of(
                "model", "gpt-3.5-turbo",
                "messages", List.of(
                    Map.of("role", "user", "content", "Hello")
                ),
                "max_tokens", 1
            );
            
            webClient
                .post()
                .uri(openaiBaseUrl + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(testRequest)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .block();
            
            return true;
            
        } catch (Exception e) {
            logger.warn("OpenAI API 키 검증 실패: {}", e.getMessage());
            return false;
        }
    }
    
}