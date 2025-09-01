package com.smarteye.controller;

import com.smarteye.dto.*;
import com.smarteye.dto.common.LayoutInfo;
import com.smarteye.entity.AnalysisJob;
import com.smarteye.service.*;
import com.smarteye.util.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 문서 분석 컨트롤러 - 메인 분석 API
 * Python api_server.py의 analyze_worksheet, analyze-pdf 엔드포인트 변환
 */
@RestController
@RequestMapping("/api/document")
@CrossOrigin(origins = "*")
@Validated
public class DocumentAnalysisController {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentAnalysisController.class);
    
    @Autowired
    private LAMServiceClient lamServiceClient;
    
    @Autowired
    private OCRService ocrService;
    
    @Autowired
    private AIDescriptionService aiDescriptionService;
    
    @Autowired
    private ImageProcessingService imageProcessingService;
    
    @Autowired
    private PDFService pdfService;
    
    @Autowired
    private FileService fileService;
    
    @Autowired
    private AnalysisJobService analysisJobService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${smarteye.upload.directory:./uploads}")
    private String uploadDirectory;
    
    @Value("${smarteye.static.directory:./static}")
    private String staticDirectory;
    
    /**
     * 단일 이미지 분석
     * Python api_server.py의 /analyze 엔드포인트와 동일
     */
    @PostMapping("/analyze")
    public CompletableFuture<ResponseEntity<AnalysisResponse>> analyzeDocument(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "modelChoice", defaultValue = "SmartEyeSsen") String modelChoice,
            @RequestParam(value = "apiKey", required = false) String apiKey) {
        
        logger.info("이미지 분석 요청 시작 - 파일: {}, 모델: {}, API키 존재: {}", 
                   image.getOriginalFilename(), modelChoice, apiKey != null && !apiKey.trim().isEmpty());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. 이미지 검증 및 로드
                BufferedImage bufferedImage = validateAndLoadImage(image);
                
                // 2. 작업 ID 생성
                String jobId = fileService.generateJobId();
                
                // 3. 업로드된 파일 저장
                String savedFilePath = fileService.saveUploadedFile(image, jobId);
                logger.info("파일 저장 완료: {}", savedFilePath);
                
                // 4. 분석 작업 생성 및 DB 저장 (사용자 없이)
                AnalysisJob analysisJob = analysisJobService.createAnalysisJob(
                    null,  // 사용자 ID 없음 (익명 분석)
                    image.getOriginalFilename(),
                    savedFilePath,
                    image.getSize(),
                    image.getContentType(),
                    modelChoice
                );
                
                // 5. LAM 레이아웃 분석
                logger.info("LAM 레이아웃 분석 시작...");
                LayoutAnalysisResult layoutResult = lamServiceClient
                    .analyzeLayout(bufferedImage, modelChoice)
                    .get(); // 동기 처리
                
                if (layoutResult.getLayoutInfo().isEmpty()) {
                    return ResponseEntity.badRequest()
                        .body(new AnalysisResponse(false, "레이아웃 분석에 실패했습니다. 감지된 요소가 없습니다."));
                }
                
                // 3. OCR 처리  
                logger.info("OCR 처리 시작...");
                List<OCRResult> ocrResults = ocrService.performOCR(
                    bufferedImage, 
                    layoutResult.getLayoutInfo()
                );
                
                // 4. AI 설명 생성 (API 키가 있는 경우)
                List<AIDescriptionResult> aiResults;
                if (apiKey != null && !apiKey.trim().isEmpty()) {
                    logger.info("AI 설명 생성 시작...");
                    aiResults = aiDescriptionService.generateDescriptions(
                        bufferedImage,
                        layoutResult.getLayoutInfo(),
                        apiKey
                    ).get(); // 동기 처리
                } else {
                    aiResults = List.of();
                }
                
                // 6. 결과 시각화 및 파일 저장
                String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                
                // 레이아웃 시각화 이미지 생성 및 저장
                BufferedImage visualizedImage = createLayoutVisualization(bufferedImage, layoutResult.getLayoutInfo());
                String layoutImagePath = saveVisualizationImage(visualizedImage, timestamp);
                
                // 7. 분석 작업 상태 업데이트
                logger.info("데이터베이스에 분석 결과 저장 시작...");
                analysisJobService.updateJobStatus(
                    analysisJob.getJobId(), 
                    AnalysisJob.JobStatus.COMPLETED, 
                    100, 
                    null
                );
                
                // 8. CIM 통합 결과 생성
                Map<String, Object> cimResult = createCIMResult(layoutResult.getLayoutInfo(), ocrResults, aiResults);
                String jsonFilePath = saveCIMResultAsJson(cimResult, timestamp);
                
                // 8. 포맷팅된 텍스트 생성
                String formattedText = createFormattedText(cimResult);
                
                // 9. 응답 구성
                AnalysisResponse response = buildAnalysisResponse(
                    layoutImagePath, jsonFilePath, layoutResult.getLayoutInfo(), 
                    ocrResults, aiResults, formattedText, Long.parseLong(timestamp)
                );
                
                // 분석 작업 ID를 응답에 추가
                response.setJobId(analysisJob.getJobId());
                
                logger.info("이미지 분석 완료 - 작업 ID: {}, 레이아웃: {}개, OCR: {}개, AI: {}개", 
                           analysisJob.getJobId(), layoutResult.getLayoutInfo().size(), ocrResults.size(), aiResults.size());
                
                return ResponseEntity.ok(response);
                
            } catch (Exception e) {
                logger.error("이미지 분석 중 오류 발생: {}", e.getMessage(), e);
                return ResponseEntity.internalServerError()
                    .body(new AnalysisResponse(false, "분석 중 오류가 발생했습니다: " + e.getMessage()));
            }
        });
    }
    
    /**
     * PDF 분석
     * Python api_server.py의 /analyze-pdf 엔드포인트와 동일한 기능 (추후 구현)
     */
    @PostMapping("/analyze-pdf")
    public CompletableFuture<ResponseEntity<AnalysisResponse>> analyzePDF(
            @RequestParam("file") MultipartFile pdfFile,
            @RequestParam(value = "modelChoice", defaultValue = "SmartEyeSsen") String modelChoice,
            @RequestParam(value = "apiKey", required = false) String apiKey) {
        
        logger.info("PDF 분석 요청 - 파일: {}, 모델: {}", pdfFile.getOriginalFilename(), modelChoice);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // PDF를 이미지로 변환
                List<BufferedImage> pdfImages = pdfService.convertPDFToImages(pdfFile);
                
                if (pdfImages.isEmpty()) {
                    return ResponseEntity.badRequest()
                        .body(new AnalysisResponse(false, "PDF에서 이미지를 추출할 수 없습니다."));
                }
                
                // 첫 번째 페이지만 분석 (멀티페이지는 추후 구현)
                BufferedImage firstPage = pdfImages.get(0);
                
                // 이미지 분석과 동일한 로직 적용
                LayoutAnalysisResult layoutResult = lamServiceClient
                    .analyzeLayout(firstPage, modelChoice)
                    .get();
                
                if (layoutResult.getLayoutInfo().isEmpty()) {
                    return ResponseEntity.badRequest()
                        .body(new AnalysisResponse(false, "PDF 레이아웃 분석에 실패했습니다."));
                }
                
                List<OCRResult> ocrResults = ocrService.performOCR(
                    firstPage, 
                    layoutResult.getLayoutInfo()
                );
                
                List<AIDescriptionResult> aiResults = List.of();
                if (apiKey != null && !apiKey.trim().isEmpty()) {
                    aiResults = aiDescriptionService.generateDescriptions(
                        firstPage,
                        layoutResult.getLayoutInfo(),
                        apiKey
                    ).get();
                }
                
                // 결과 저장 및 응답 구성 (이미지 분석과 동일)
                String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                BufferedImage visualizedImage = createLayoutVisualization(firstPage, layoutResult.getLayoutInfo());
                String layoutImagePath = saveVisualizationImage(visualizedImage, timestamp);
                
                Map<String, Object> cimResult = createCIMResult(layoutResult.getLayoutInfo(), ocrResults, aiResults);
                String jsonFilePath = saveCIMResultAsJson(cimResult, timestamp);
                String formattedText = createFormattedText(cimResult);
                
                AnalysisResponse response = buildAnalysisResponse(
                    layoutImagePath, jsonFilePath, layoutResult.getLayoutInfo(),
                    ocrResults, aiResults, formattedText, Long.parseLong(timestamp)
                );
                
                logger.info("PDF 분석 완료 - 페이지 수: {}, 레이아웃: {}개", 
                           pdfImages.size(), layoutResult.getLayoutInfo().size());
                
                return ResponseEntity.ok(response);
                
            } catch (Exception e) {
                logger.error("PDF 분석 중 오류 발생: {}", e.getMessage(), e);
                return ResponseEntity.internalServerError()
                    .body(new AnalysisResponse(false, "PDF 분석 중 오류가 발생했습니다: " + e.getMessage()));
            }
        });
    }
    
    // === Private Helper Methods ===
    
    private BufferedImage validateAndLoadImage(MultipartFile image) throws IOException {
        if (image.isEmpty()) {
            throw new IllegalArgumentException("이미지 파일이 비어있습니다.");
        }
        
        // 파일 크기 체크 (50MB 제한)
        if (image.getSize() > 50 * 1024 * 1024) {
            throw new IllegalArgumentException("파일 크기가 너무 큽니다. (최대 50MB)");
        }
        
        // 이미지 형식 체크
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("지원하지 않는 파일 형식입니다. 이미지 파일만 업로드 가능합니다.");
        }
        
        return ImageIO.read(image.getInputStream());
    }
    
    // No longer needed - all services now use the common LayoutInfo DTO
    // private List<LayoutInfo> convertToOCRLayoutInfo(List<LayoutInfo> lamLayoutInfo) {
    //     return lamLayoutInfo; // Same type, no conversion needed
    // }
    
    // private List<LayoutInfo> convertToAILayoutInfo(List<LayoutInfo> lamLayoutInfo) {
    //     return lamLayoutInfo; // Same type, no conversion needed  
    // }
    
    private BufferedImage createLayoutVisualization(BufferedImage image, List<LayoutInfo> layoutInfo) {
        // 간단한 시각화 구현 (추후 VisualizationService로 분리)
        return imageProcessingService.drawLayoutBoxes(image, layoutInfo);
    }
    
    private String saveVisualizationImage(BufferedImage image, String timestamp) throws IOException {
        ensureStaticDirectoryExists();
        
        String filename = "layout_viz_" + timestamp + ".png";
        Path imagePath = Paths.get(staticDirectory, filename);
        
        ImageIO.write(image, "PNG", imagePath.toFile());
        
        return "/static/" + filename;
    }
    
    private Map<String, Object> createCIMResult(
            List<LayoutInfo> layoutInfo,
            List<OCRResult> ocrResults, 
            List<AIDescriptionResult> aiResults) {
        
        // Python의 create_cim_result() 메서드와 동일한 구조 생성
        return JsonUtils.createCIMResult(layoutInfo, ocrResults, aiResults);
    }
    
    private String saveCIMResultAsJson(Map<String, Object> cimResult, String timestamp) throws IOException {
        ensureStaticDirectoryExists();
        
        String filename = "analysis_result_" + 
                         LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".json";
        Path jsonPath = Paths.get(staticDirectory, filename);
        
        objectMapper.writeValue(jsonPath.toFile(), cimResult);
        
        return "/static/" + filename;
    }
    
    private String createFormattedText(Map<String, Object> cimResult) {
        // Python의 create_formatted_text() 메서드와 동일한 로직
        return JsonUtils.createFormattedText(cimResult);
    }
    
    private AnalysisResponse buildAnalysisResponse(
            String layoutImagePath, String jsonFilePath,
            List<LayoutInfo> layoutInfo,
            List<OCRResult> ocrResults, List<AIDescriptionResult> aiResults,
            String formattedText, Long timestamp) {
        
        AnalysisResponse response = new AnalysisResponse(true, "분석이 성공적으로 완료되었습니다.");
        response.setLayoutImageUrl(layoutImagePath);
        response.setJsonUrl(jsonFilePath);
        response.setOcrResults(ocrResults);
        response.setAiResults(aiResults);
        response.setFormattedText(formattedText);
        response.setTimestamp(timestamp);
        
        // OCR 텍스트 통합
        String combinedOcrText = ocrResults.stream()
            .map(result -> "[" + result.getClassName() + "]\n" + result.getText() + "\n\n")
            .collect(Collectors.joining());
        response.setOcrText(combinedOcrText.trim());
        
        // AI 설명 통합
        String combinedAiText = aiResults.stream()
            .map(result -> "[" + result.getClassName() + "]\n" + result.getDescription() + "\n\n")
            .collect(Collectors.joining());
        response.setAiText(combinedAiText.trim());
        
        // 통계 생성
        Map<String, Integer> classCounts = layoutInfo.stream()
            .collect(Collectors.groupingBy(
                LayoutInfo::getClassName,
                Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
            ));
            
        AnalysisResponse.AnalysisStats stats = new AnalysisResponse.AnalysisStats(
            layoutInfo.size(),
            ocrResults.size(), 
            aiResults.size(),
            classCounts
        );
        response.setStats(stats);
        
        return response;
    }
    
    private void ensureStaticDirectoryExists() throws IOException {
        Path staticPath = Paths.get(staticDirectory);
        if (!Files.exists(staticPath)) {
            Files.createDirectories(staticPath);
        }
    }
}