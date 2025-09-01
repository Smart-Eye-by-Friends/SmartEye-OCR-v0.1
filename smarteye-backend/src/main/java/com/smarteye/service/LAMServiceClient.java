package com.smarteye.service;

import com.smarteye.dto.LayoutAnalysisResult;
import com.smarteye.dto.common.LayoutInfo;
import com.smarteye.exception.LAMServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LAM (Layout Analysis Module) 마이크로서비스 클라이언트
 * Python api_server.py의 analyze_layout() 메서드를 마이크로서비스로 호출
 */
@Service
public class LAMServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(LAMServiceClient.class);
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    @Value("${smarteye.services.lam.base-url:http://localhost:8001}")
    private String lamServiceBaseUrl;
    
    @Value("${smarteye.services.lam.timeout:60}")
    private int timeoutSeconds;
    
    @Value("${smarteye.services.lam.enabled:true}")
    private boolean lamServiceEnabled;
    
    public LAMServiceClient(@Qualifier("lamWebClientBuilder") WebClient.Builder webClientBuilder, 
                           ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.build();
    }
    
    /**
     * 레이아웃 분석 수행
     * Python api_server.py의 analyze_layout() 메서드와 동일한 기능
     * @param image 분석할 이미지
     * @param modelChoice 모델 선택 ("SmartEyeSsen", "docsynth300k", etc.)
     * @return 레이아웃 분석 결과
     */
    @CircuitBreaker(name = "lam-service", fallbackMethod = "analyzeLayoutFallback")
    @Retry(name = "lam-service")
    public CompletableFuture<LayoutAnalysisResult> analyzeLayout(BufferedImage image, String modelChoice) {
        if (!lamServiceEnabled) {
            logger.warn("LAM 서비스가 비활성화되어 있습니다. 빈 결과를 반환합니다.");
            return CompletableFuture.completedFuture(createEmptyResult());
        }
        
        logger.info("LAM 레이아웃 분석 시작 - 모델: {}, 이미지 크기: {}x{}", 
                   modelChoice, image.getWidth(), image.getHeight());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 이미지를 바이트 배열로 변환
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "jpg", baos);
                byte[] imageBytes = baos.toByteArray();
                
                // Multipart 요청 구성
                MultipartBodyBuilder builder = new MultipartBodyBuilder();
                builder.part("image", new ByteArrayResource(imageBytes))
                       .header("Content-Disposition", "form-data; name=\"image\"; filename=\"temp_image.jpg\"")
                       .contentType(MediaType.IMAGE_JPEG);
                builder.part("model_choice", modelChoice);
                
                // LAM 서비스 호출
                String response = webClient
                    .post()
                    .uri(lamServiceBaseUrl + "/analyze-layout")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
                
                // 응답 파싱
                LayoutAnalysisResult result = parseLayoutResponse(response);
                
                logger.info("LAM 레이아웃 분석 완료 - 감지된 요소: {}개", result.getLayoutInfo().size());
                return result;
                
            } catch (WebClientResponseException e) {
                logger.error("LAM 서비스 HTTP 오류: {} - {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
                throw new LAMServiceException("LAM 서비스 HTTP 오류: " + e.getStatusCode(), e);
                
            } catch (IOException e) {
                logger.error("이미지 변환 실패: {}", e.getMessage(), e);
                throw new LAMServiceException("이미지 변환에 실패했습니다: " + e.getMessage(), e);
                
            } catch (Exception e) {
                logger.error("LAM 서비스 호출 실패: {}", e.getMessage(), e);
                throw new LAMServiceException("LAM 서비스 호출에 실패했습니다: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * LAM 서비스 헬스 체크
     * @return 서비스 상태
     */
    public boolean isHealthy() {
        try {
            String response = webClient
                .get()
                .uri(lamServiceBaseUrl + "/health")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .block();
            
            logger.debug("LAM 서비스 헬스 체크 성공: {}", response);
            return true;
            
        } catch (Exception e) {
            logger.warn("LAM 서비스 헬스 체크 실패: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Circuit Breaker Fallback 메서드
     */
    public CompletableFuture<LayoutAnalysisResult> analyzeLayoutFallback(BufferedImage image, String modelChoice, Exception ex) {
        logger.error("LAM 서비스 Circuit Breaker 작동 - Fallback 실행: {}", ex.getMessage());
        
        // 기본 결과 반환 (전체 이미지를 하나의 plain_text 영역으로 처리)
        LayoutAnalysisResult fallbackResult = createFallbackResult(image);
        return CompletableFuture.completedFuture(fallbackResult);
    }
    
    /**
     * LAM 서비스 응답 파싱
     * Python 응답 형식을 Java 객체로 변환
     */
    private LayoutAnalysisResult parseLayoutResponse(String response) {
        try {
            // Python LAM 서비스의 응답 형식에 맞춰 파싱
            @SuppressWarnings("unchecked")
            var responseMap = objectMapper.readValue(response, java.util.Map.class);
            
            @SuppressWarnings("unchecked")
            List<java.util.Map<String, Object>> layoutList = 
                (List<java.util.Map<String, Object>>) responseMap.get("layout_info");
            
            List<LayoutInfo> layoutInfoList = new ArrayList<>();
            
            for (var layoutMap : layoutList) {
                int id = (Integer) layoutMap.get("id");
                String className = (String) layoutMap.get("class_name");
                double confidence = ((Number) layoutMap.get("confidence")).doubleValue();
                
                @SuppressWarnings("unchecked")
                List<Integer> boxList = (List<Integer>) layoutMap.get("box");
                int[] box = boxList.stream().mapToInt(Integer::intValue).toArray();
                
                int width = (Integer) layoutMap.get("width");
                int height = (Integer) layoutMap.get("height");
                int area = (Integer) layoutMap.get("area");
                
                LayoutInfo layoutInfo = new LayoutInfo(id, className, confidence, box, width, height, area);
                layoutInfoList.add(layoutInfo);
            }
            
            return new LayoutAnalysisResult(layoutInfoList);
            
        } catch (Exception e) {
            logger.error("LAM 서비스 응답 파싱 실패: {}", e.getMessage(), e);
            throw new LAMServiceException("LAM 서비스 응답 파싱에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 빈 결과 생성
     */
    private LayoutAnalysisResult createEmptyResult() {
        return new LayoutAnalysisResult(new ArrayList<>());
    }
    
    /**
     * Fallback 결과 생성
     * LAM 서비스가 실패했을 때 전체 이미지를 하나의 텍스트 영역으로 처리
     */
    private LayoutAnalysisResult createFallbackResult(BufferedImage image) {
        List<LayoutInfo> fallbackLayout = new ArrayList<>();
        
        // 전체 이미지를 하나의 plain_text 영역으로 처리
        int[] box = {0, 0, image.getWidth(), image.getHeight()};
        LayoutInfo fallbackInfo = new LayoutInfo(
            0, 
            "plain_text", 
            0.5, 
            box, 
            image.getWidth(), 
            image.getHeight(), 
            image.getWidth() * image.getHeight()
        );
        
        fallbackLayout.add(fallbackInfo);
        
        logger.info("LAM 서비스 실패 - Fallback 결과 생성: 전체 이미지를 plain_text로 처리");
        return new LayoutAnalysisResult(fallbackLayout);
    }
    
}