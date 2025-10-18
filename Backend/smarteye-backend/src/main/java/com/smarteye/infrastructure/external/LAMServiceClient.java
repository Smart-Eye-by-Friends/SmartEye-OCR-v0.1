package com.smarteye.infrastructure.external;

import com.smarteye.presentation.dto.LayoutAnalysisResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.shared.exception.LAMServiceException;
import com.smarteye.shared.util.CoordinateScalingUtils;
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
                           ObjectMapper objectMapper,
                           @Value("${smarteye.services.lam.base-url:http://localhost:8001}") String lamServiceBaseUrl) {
        this.objectMapper = objectMapper;
        this.lamServiceBaseUrl = lamServiceBaseUrl;
        this.webClient = webClientBuilder.baseUrl(lamServiceBaseUrl).build();
        logger.info("LAM 서비스 클라이언트 초기화 - 기본 URL: {}", lamServiceBaseUrl);
    }
    
    /**
     * 레이아웃 분석 수행
     * Python api_server.py의 analyze_layout() 메서드와 동일한 기능
     * @param image 분석할 이미지
     * @param modelChoice 모델 선택 ("SmartEye", etc.)
     * @return 레이아웃 분석 결과
     */
    @CircuitBreaker(name = "lam-service", fallbackMethod = "analyzeLayoutFallback")
    @Retry(name = "lam-service")
    public CompletableFuture<LayoutAnalysisResult> analyzeLayout(BufferedImage image, String modelChoice) {
        if (!lamServiceEnabled) {
            logger.warn("LAM 서비스가 비활성화되어 있습니다. 빈 결과를 반환합니다.");
            return CompletableFuture.completedFuture(createEmptyResult());
        }

        // 원본 이미지 크기 저장 (좌표 스케일링을 위해)
        final int originalWidth = image.getWidth();
        final int originalHeight = image.getHeight();

        logger.info("LAM 레이아웃 분석 시작 - 모델: {}, 원본 이미지 크기: {}x{}",
                   modelChoice, originalWidth, originalHeight);
        
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
                
                logger.debug("LAM 서비스 호출 시작 - URL: {}/analyze-layout", lamServiceBaseUrl);
                
                // LAM 서비스 호출 (타임아웃 대폭 완화)
                String response = webClient
                    .post()
                    .uri(lamServiceBaseUrl + "/analyze-layout")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), 
                             clientResponse -> {
                                 logger.error("LAM 서비스 HTTP 오류: {}", clientResponse.statusCode());
                                 return clientResponse.bodyToMono(String.class)
                                     .map(body -> new LAMServiceException("LAM 서비스 오류 [" + 
                                         clientResponse.statusCode() + "]: " + body));
                             })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
                
                if (response == null || response.trim().isEmpty()) {
                    throw new LAMServiceException("LAM 서비스에서 빈 응답을 받았습니다.");
                }
                
                // JSON 유효성 검사
                if (!response.trim().startsWith("{")) {
                    logger.error("LAM 서비스 응답이 JSON이 아닙니다: {}", 
                               response.length() > 200 ? response.substring(0, 200) + "..." : response);
                    throw new LAMServiceException("LAM 서비스 응답 형식 오류: JSON이 아님");
                }
                
                // 응답 파싱 (원본 이미지 크기 정보 포함)
                LayoutAnalysisResult result = parseLayoutResponse(response, originalWidth, originalHeight);

                // 좌표 스케일링 정보 로그
                if (result.needsCoordinateScaling()) {
                    logger.info("LAM 좌표 스케일링 필요 - 원본: {}x{}, 처리됨: {}x{}, 스케일: {:.3f}x{:.3f}",
                               result.getOriginalImageWidth(), result.getOriginalImageHeight(),
                               result.getProcessedImageWidth(), result.getProcessedImageHeight(),
                               result.getScaleX(), result.getScaleY());
                } else {
                    logger.debug("LAM 좌표 스케일링 불필요 - 이미지 크기 동일");
                }

                logger.info("LAM 레이아웃 분석 완료 - 감지된 요소: {}개", result.getLayoutInfo().size());
                for (LayoutInfo info : result.getLayoutInfo()) {
                    logger.debug("감지된 요소: {} (신뢰도: {:.2f}) 좌표: [{}, {}, {}, {}]",
                               info.getClassName(), info.getConfidence(),
                               info.getBox()[0], info.getBox()[1], info.getBox()[2], info.getBox()[3]);
                }
                
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
        logger.warn("모델: {}, 이미지 크기: {}x{}", modelChoice, image.getWidth(), image.getHeight());
        
        // 개선된 기본 결과 반환 (다중 영역 시뮬레이션)
        LayoutAnalysisResult fallbackResult = createFallbackResult(image);
        return CompletableFuture.completedFuture(fallbackResult);
    }
    
    /**
     * LAM 서비스 응답 파싱
     * Python 응답 형식을 Java 객체로 변환
     * @param response LAM 서비스 JSON 응답
     * @param originalWidth 원본 이미지 너비
     * @param originalHeight 원본 이미지 높이
     */
    private LayoutAnalysisResult parseLayoutResponse(String response, int originalWidth, int originalHeight) {
        try {
            logger.debug("LAM 서비스 원시 응답: {}", response);
            
            // Python LAM 서비스의 실제 응답 형식에 맞춰 파싱
            @SuppressWarnings("unchecked")
            var responseMap = objectMapper.readValue(response, java.util.Map.class);
            
            // LAM 서비스의 실제 응답 구조: results.layout_analysis
            @SuppressWarnings("unchecked")
            var resultsMap = (java.util.Map<String, Object>) responseMap.get("results");
            
            if (resultsMap == null) {
                logger.error("LAM 서비스 응답에 'results' 키가 없습니다: {}", response);
                throw new LAMServiceException("LAM 서비스 응답 구조 오류: results 없음");
            }
            
            @SuppressWarnings("unchecked")
            List<java.util.Map<String, Object>> layoutList =
                (List<java.util.Map<String, Object>>) resultsMap.get("layout_analysis");

            if (layoutList == null) {
                logger.warn("LAM 서비스 응답에 layout_analysis가 없습니다. 빈 결과 반환");
                return new LayoutAnalysisResult(new ArrayList<>());
            }

            // LAM 서비스가 처리한 이미지 크기 추출 (선택적)
            int processedWidth = originalWidth;
            int processedHeight = originalHeight;

            // LAM 서비스 응답에 image_info가 있는 경우 사용
            @SuppressWarnings("unchecked")
            var imageInfoMap = (java.util.Map<String, Object>) resultsMap.get("image_info");
            if (imageInfoMap != null) {
                if (imageInfoMap.get("width") != null && imageInfoMap.get("height") != null) {
                    processedWidth = ((Number) imageInfoMap.get("width")).intValue();
                    processedHeight = ((Number) imageInfoMap.get("height")).intValue();
                    logger.debug("LAM 서비스 처리 이미지 크기: {}x{}", processedWidth, processedHeight);
                }
            }
            
            List<LayoutInfo> layoutInfoList = new ArrayList<>();
            
            for (int i = 0; i < layoutList.size(); i++) {
                var layoutMap = layoutList.get(i);
                
                // LAM 서비스의 실제 응답 구조에 맞춤
                String className = (String) layoutMap.get("class");  // "class_name" → "class"
                
                // 🆕 v0.5 Fix (Option A): LAM 클래스명 정규화
                // LAM 모델이 "question type" (공백)을 반환하지만
                // 백엔드 Enum은 "question_type" (언더스코어)로 정의되어 있음
                // 공백을 언더스코어로 변환하여 일관성 유지
                className = normalizeClassName(className);
                
                double confidence = ((Number) layoutMap.get("confidence")).doubleValue();
                
                @SuppressWarnings("unchecked")
                var bboxMap = (java.util.Map<String, Object>) layoutMap.get("bbox");  // "box" → "bbox"
                
                if (bboxMap == null) {
                    logger.warn("레이아웃 요소에 bbox 정보가 없습니다. 건너뜁니다.");
                    continue;
                }
                
                // 좌표 정밀도 개선: double로 파싱 후 반올림
                double x1_double = ((Number) bboxMap.get("x1")).doubleValue();
                double y1_double = ((Number) bboxMap.get("y1")).doubleValue();
                double x2_double = ((Number) bboxMap.get("x2")).doubleValue();
                double y2_double = ((Number) bboxMap.get("y2")).doubleValue();
                
                // 좌표 스케일링 적용 (유틸리티 사용)
                CoordinateScalingUtils.ScalingInfo scalingInfo =
                    CoordinateScalingUtils.calculateScaling(originalWidth, originalHeight, processedWidth, processedHeight);

                if (scalingInfo.needsScaling()) {
                    // double 정밀도로 스케일링 후 반올림
                    x1_double *= scalingInfo.getScaleX();
                    y1_double *= scalingInfo.getScaleY();
                    x2_double *= scalingInfo.getScaleX();
                    y2_double *= scalingInfo.getScaleY();
                    logger.debug("좌표 스케일링 적용: 요소 {}, {}", className, scalingInfo);
                }
                
                // 반올림하여 정수로 변환
                int x1 = (int) Math.round(x1_double);
                int y1 = (int) Math.round(y1_double);
                int x2 = (int) Math.round(x2_double);
                int y2 = (int) Math.round(y2_double);

                int[] box = {x1, y1, x2, y2};
                int width = x2 - x1;
                int height = y2 - y1;
                int area = width * height;

                LayoutInfo layoutInfo = new LayoutInfo(
                    i,  // id는 순서대로 할당
                    className,
                    confidence,
                    box,
                    width,
                    height,
                    area
                );
                layoutInfoList.add(layoutInfo);
                
                logger.debug("파싱된 레이아웃 요소: {} (신뢰도: {:.2f})", className, confidence);
            }
            
            logger.info("LAM 서비스 응답 파싱 완료 - {}개 요소", layoutInfoList.size());
            return new LayoutAnalysisResult(layoutInfoList, originalWidth, originalHeight, processedWidth, processedHeight);
            
        } catch (Exception e) {
            logger.error("LAM 서비스 응답 파싱 실패: {}", e.getMessage(), e);
            logger.error("응답 내용: {}", response);
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
     * Fallback 결과 생성 (개선된 다양한 영역 생성)
     * LAM 서비스가 실패했을 때 이미지를 다양한 레이아웃 영역으로 분할하여 처리
     */
    private LayoutAnalysisResult createFallbackResult(BufferedImage image) {
        List<LayoutInfo> fallbackLayout = new ArrayList<>();
        int width = image.getWidth();
        int height = image.getHeight();
        
        // 상단 제목 영역 (높이의 15%)
        int titleHeight = (int)(height * 0.15);
        int[] titleBox = {0, 0, width, titleHeight};
        LayoutInfo titleInfo = new LayoutInfo(
            0, 
            "title", 
            0.8, 
            titleBox, 
            width, 
            height, 
            width * titleHeight
        );
        fallbackLayout.add(titleInfo);
        
        // 좌측 텍스트 영역 (전체 너비의 60%, 중간 영역)
        int middleY = titleHeight;
        int middleHeight = (int)(height * 0.65);
        int leftWidth = (int)(width * 0.6);
        int[] textBox = {0, middleY, leftWidth, middleY + middleHeight};
        LayoutInfo textInfo = new LayoutInfo(
            1, 
            "text", 
            0.7, 
            textBox, 
            width, 
            height, 
            leftWidth * middleHeight
        );
        fallbackLayout.add(textInfo);
        
        // 우측 그림/표 영역 (전체 너비의 40%, 중간 영역)
        int[] figureBox = {leftWidth, middleY, width, middleY + middleHeight};
        LayoutInfo figureInfo = new LayoutInfo(
            2, 
            "figure", 
            0.6, 
            figureBox, 
            width, 
            height, 
            (width - leftWidth) * middleHeight
        );
        fallbackLayout.add(figureInfo);
        
        // 하단 일반 텍스트 영역 (높이의 20%)
        int bottomY = middleY + middleHeight;
        int bottomHeight = height - bottomY;
        int[] plainTextBox = {0, bottomY, width, height};
        LayoutInfo plainTextInfo = new LayoutInfo(
            3, 
            "plain_text", 
            0.5, 
            plainTextBox, 
            width, 
            height, 
            width * bottomHeight
        );
        fallbackLayout.add(plainTextInfo);
        
        logger.info("LAM 서비스 실패 - 개선된 Fallback 결과 생성: {}개의 다양한 레이아웃 영역 ({}x{})", 
                   fallbackLayout.size(), width, height);
        return new LayoutAnalysisResult(fallbackLayout);
    }
    
    /**
     * 🆕 v0.5: LAM 서비스 클래스명 정규화
     * 
     * <p>LAM 모델이 반환하는 공백 포함 클래스명을 백엔드 Enum 형식으로 변환</p>
     * 
     * <p><b>변환 예시:</b></p>
     * <ul>
     *   <li>"question type" → "question_type"</li>
     *   <li>"question text" → "question_text"</li>
     *   <li>"plain text" → "plain_text"</li>
     *   <li>"question_number" → "question_number" (변경 없음)</li>
     * </ul>
     * 
     * <p><b>목적:</b></p>
     * <ul>
     *   <li>LAM 응답과 백엔드 LayoutClass enum 간의 불일치 해소</li>
     *   <li>데이터 소스에서 정규화하여 일관성 보장</li>
     *   <li>로그에도 정규화된 클래스명 표시</li>
     * </ul>
     * 
     * @param className LAM 서비스가 반환한 원본 클래스명
     * @return 정규화된 클래스명 (공백 → 언더스코어)
     */
    private String normalizeClassName(String className) {
        if (className == null || className.isBlank()) {
            return className;
        }
        
        // 공백을 언더스코어로 변환
        String normalized = className.trim().replace(" ", "_");
        
        // 변환이 발생한 경우 로그 출력 (디버깅용)
        if (!className.equals(normalized)) {
            logger.trace("📝 LAM 클래스명 정규화: '{}' → '{}'", className, normalized);
        }
        
        return normalized;
    }
    
    // 구조화된 분석 기능이 Java CIMService로 이전됨
    
}