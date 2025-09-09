package com.smarteye.service;

import com.smarteye.dto.AIDescriptionResult;
import com.smarteye.dto.AnalysisResponse;
import com.smarteye.dto.LayoutAnalysisResult;
import com.smarteye.dto.OCRResult;
import com.smarteye.dto.common.LayoutInfo;
import com.smarteye.entity.AnalysisJob;
import com.smarteye.entity.DocumentPage;
import com.smarteye.util.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

/**
 * 단일 이미지 분석을 담당하는 핵심 비즈니스 서비스
 * DocumentAnalysisController에서 분리된 이미지 분석 로직
 */
@Service
@Transactional
public class ImageAnalysisService {
    
    private static final Logger logger = LoggerFactory.getLogger(ImageAnalysisService.class);
    
    @Autowired
    private LAMServiceClient lamServiceClient;
    
    @Autowired
    private OCRService ocrService;
    
    @Autowired
    private AIDescriptionService aiDescriptionService;
    
    @Autowired
    private ImageProcessingService imageProcessingService;
    
    @Autowired
    private FileService fileService;
    
    @Autowired
    private AnalysisJobService analysisJobService;
    
    @Autowired
    private DocumentAnalysisDataService documentAnalysisDataService;
    
    @Autowired
    private AnalysisResponseBuilder responseBuilder;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${smarteye.static.directory:./static}")
    private String staticDirectory;
    
    /**
     * 단일 이미지를 분석하고 AnalysisResponse를 반환
     * 
     * @param image 분석할 이미지 파일
     * @param modelChoice 분석 모델 선택
     * @param apiKey OpenAI API 키 (선택사항)
     * @return AnalysisResponse와 작업 ID가 포함된 결과
     */
    public CompletableFuture<ImageAnalysisResult> analyzeImage(
            MultipartFile image, String modelChoice, String apiKey) {
        
        logger.info("이미지 분석 시작 - 파일: {}, 모델: {}, API키 존재: {}", 
                   image.getOriginalFilename(), modelChoice, apiKey != null && !apiKey.trim().isEmpty());
        
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                // 1. 이미지 검증 및 로드
                BufferedImage bufferedImage = validateAndLoadImage(image);
                
                // 2. 작업 ID 생성 및 파일 저장
                String jobId = fileService.generateJobId();
                String savedFilePath = fileService.saveUploadedFile(image, jobId);
                logger.info("파일 저장 완료: {}", savedFilePath);
                
                // 3. 분석 작업 생성 및 DB 저장 (익명 분석)
                AnalysisJob analysisJob = analysisJobService.createAnalysisJob(
                    null,  // 사용자 ID 없음 (익명 분석)
                    image.getOriginalFilename(),
                    savedFilePath,
                    image.getSize(),
                    image.getContentType(),
                    modelChoice
                );
                
                // 4. 실제 이미지 분석 수행
                SingleImageAnalysisResult analysisResult = performImageAnalysis(
                    bufferedImage, modelChoice, apiKey, startTime
                );
                
                if (!analysisResult.isSuccess()) {
                    return new ImageAnalysisResult(false, analysisResult.getErrorMessage(), null, null);
                }
                
                // 5. 결과 저장 및 시각화
                String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                ImageVisualizationResult visualizationResult = saveAnalysisResults(
                    bufferedImage, analysisResult, timestamp
                );
                
                // 6. 데이터베이스에 분석 결과 저장
                long processingTimeMs = System.currentTimeMillis() - startTime;
                logger.info("데이터베이스에 분석 결과 저장 시작...");
                
                documentAnalysisDataService.saveAnalysisResults(
                    analysisJob.getJobId(),
                    analysisResult.getLayoutInfo(),
                    analysisResult.getOcrResults(),
                    analysisResult.getAiResults(),
                    visualizationResult.getJsonFilePath(),
                    visualizationResult.getLayoutImagePath(),
                    processingTimeMs
                );
                
                // 7. 분석 작업 상태 업데이트
                analysisJobService.updateJobStatus(
                    analysisJob.getJobId(), 
                    AnalysisJob.JobStatus.COMPLETED, 
                    100, 
                    null
                );
                
                // 8. AnalysisResponse 생성
                AnalysisResponse response = responseBuilder.buildAnalysisResponse(
                    visualizationResult.getLayoutImagePath(), 
                    visualizationResult.getJsonFilePath(), 
                    analysisResult.getLayoutInfo(), 
                    analysisResult.getOcrResults(), 
                    analysisResult.getAiResults(), 
                    Long.parseLong(timestamp)
                );
                
                response.setJobId(analysisJob.getJobId());
                
                logger.info("이미지 분석 완료 - 작업 ID: {}, 레이아웃: {}개, OCR: {}개, AI: {}개", 
                           analysisJob.getJobId(), analysisResult.getLayoutInfo().size(), 
                           analysisResult.getOcrResults().size(), analysisResult.getAiResults().size());
                
                return new ImageAnalysisResult(true, null, response, analysisJob);
                
            } catch (Exception e) {
                logger.error("이미지 분석 중 오류 발생: {}", e.getMessage(), e);
                return new ImageAnalysisResult(false, "분석 중 오류가 발생했습니다: " + e.getMessage(), null, null);
            }
        });
    }
    
    /**
     * BufferedImage에 대한 직접 분석 (PDF나 다중 이미지에서 사용)
     * 
     * @param bufferedImage 분석할 버퍼 이미지
     * @param imageNumber 이미지 순서 (페이지 번호)
     * @param userId 사용자 ID
     * @param imageName 이미지 이름
     * @param modelChoice 분석 모델 선택
     * @param apiKey OpenAI API 키
     * @param imageTimestamp 이미지별 타임스탬프
     * @return 단일 이미지 처리 결과
     */
    public SingleImageProcessingResult processBufferedImage(
            BufferedImage bufferedImage, int imageNumber, Long userId, String imageName,
            long imageSize, String contentType, String modelChoice, String apiKey, 
            String imageTimestamp) throws Exception {
        
        // 1. 이미지 임시 저장
        String tempImagePath = savePageImage(bufferedImage, imageTimestamp);
        
        // 2. 분석 작업 생성
        AnalysisJob analysisJob = analysisJobService.createAnalysisJob(
            userId,
            String.format("image_%d_%s", imageNumber, imageName),
            tempImagePath,
            imageSize,
            contentType,
            modelChoice
        );
        
        // 3. 이미지 분석 수행
        SingleImageAnalysisResult analysisResult = performImageAnalysis(
            bufferedImage, modelChoice, apiKey, System.currentTimeMillis()
        );
        
        if (!analysisResult.isSuccess()) {
            logger.warn("이미지 {} 분석 실패: {}", imageNumber, analysisResult.getErrorMessage());
            return new SingleImageProcessingResult(false, imageName, 
                analysisResult.getErrorMessage(), analysisJob, null);
        }
        
        // 4. 시각화 및 결과 저장
        ImageVisualizationResult visualizationResult = saveAnalysisResults(
            bufferedImage, analysisResult, imageTimestamp
        );
        
        // 5. 데이터베이스에 페이지 분석 결과 저장
        long processingStartTime = System.currentTimeMillis();
        DocumentPage documentPage = documentAnalysisDataService.savePageAnalysisResult(
            analysisJob,
            imageNumber,
            tempImagePath,
            analysisResult.getLayoutInfo(),
            analysisResult.getOcrResults(),
            analysisResult.getAiResults(),
            visualizationResult.getJsonFilePath(),
            visualizationResult.getLayoutImagePath(),
            System.currentTimeMillis() - processingStartTime
        );
        
        // 6. 분석 작업 상태 업데이트
        analysisJobService.updateJobStatus(
            analysisJob.getJobId(),
            AnalysisJob.JobStatus.COMPLETED,
            100,
            null
        );
        
        return new SingleImageProcessingResult(true, imageName, 
            "분석 완료", analysisJob, documentPage);
    }
    
    // === Private Helper Methods ===
    
    /**
     * 이미지 파일 검증 및 BufferedImage로 로드
     */
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
    
    /**
     * 실제 이미지 분석 수행 (LAM + OCR + AI)
     */
    private SingleImageAnalysisResult performImageAnalysis(
            BufferedImage bufferedImage, String modelChoice, String apiKey, long startTime) {
        
        try {
            // 1. LAM 레이아웃 분석
            logger.info("LAM 레이아웃 분석 시작...");
            LayoutAnalysisResult layoutResult = lamServiceClient
                .analyzeLayout(bufferedImage, modelChoice)
                .get(); // 동기 처리
            
            if (layoutResult.getLayoutInfo().isEmpty()) {
                return new SingleImageAnalysisResult(false, 
                    "레이아웃 분석에 실패했습니다. 감지된 요소가 없습니다.", null, null, null);
            }
            
            // 2. OCR 처리
            logger.info("OCR 처리 시작...");
            List<OCRResult> ocrResults = ocrService.performOCR(
                bufferedImage, 
                layoutResult.getLayoutInfo()
            );
            
            // 3. AI 설명 생성 (API 키가 있는 경우)
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
            
            return new SingleImageAnalysisResult(true, null, 
                layoutResult.getLayoutInfo(), ocrResults, aiResults);
            
        } catch (Exception e) {
            logger.error("이미지 분석 수행 중 오류: {}", e.getMessage(), e);
            return new SingleImageAnalysisResult(false, 
                "분석 수행 중 오류가 발생했습니다: " + e.getMessage(), null, null, null);
        }
    }
    
    /**
     * 분석 결과 시각화 및 파일 저장
     */
    private ImageVisualizationResult saveAnalysisResults(
            BufferedImage bufferedImage, SingleImageAnalysisResult analysisResult, 
            String timestamp) throws IOException {
        
        // 레이아웃 시각화 이미지 생성 및 저장
        BufferedImage visualizedImage = createLayoutVisualization(
            bufferedImage, analysisResult.getLayoutInfo()
        );
        String layoutImagePath = saveVisualizationImage(visualizedImage, timestamp);
        
        // 기본 분석 결과 JSON 저장 (시각화용)
        Map<String, Object> basicResult = createBasicAnalysisResult(
            analysisResult.getLayoutInfo(), analysisResult.getOcrResults(), analysisResult.getAiResults()
        );
        String jsonFilePath = saveCIMResultAsJson(basicResult, timestamp);
        
        return new ImageVisualizationResult(layoutImagePath, jsonFilePath);
    }
    
    /**
     * 레이아웃 시각화 이미지 생성
     */
    private BufferedImage createLayoutVisualization(BufferedImage image, List<LayoutInfo> layoutInfo) {
        return imageProcessingService.drawLayoutBoxes(image, layoutInfo);
    }
    
    /**
     * 시각화 이미지 저장
     */
    private String saveVisualizationImage(BufferedImage image, String timestamp) throws IOException {
        ensureStaticDirectoryExists();
        
        String filename = "layout_viz_" + timestamp + ".png";
        Path imagePath = Paths.get(staticDirectory, filename);
        
        ImageIO.write(image, "PNG", imagePath.toFile());
        
        return "/static/" + filename;
    }
    
    /**
     * 페이지 이미지를 임시 파일로 저장
     */
    private String savePageImage(BufferedImage image, String timestamp) {
        try {
            String fileName = "page_" + timestamp + ".png";
            String filePath = staticDirectory + "/" + fileName;
            File outputFile = new File(filePath);
            outputFile.getParentFile().mkdirs();
            
            ImageIO.write(image, "PNG", outputFile);
            return filePath;
        } catch (IOException e) {
            logger.error("페이지 이미지 저장 실패: {}", e.getMessage(), e);
            throw new RuntimeException("페이지 이미지 저장에 실패했습니다", e);
        }
    }
    
    /**
     * 기본 분석 결과 생성 (시각화용)
     */
    private Map<String, Object> createBasicAnalysisResult(
            List<LayoutInfo> layoutInfo,
            List<OCRResult> ocrResults, 
            List<AIDescriptionResult> aiResults) {
        
        return JsonUtils.createBasicResult(layoutInfo, ocrResults, aiResults);
    }
    
    /**
     * CIM 결과를 JSON 파일로 저장
     */
    private String saveCIMResultAsJson(Map<String, Object> cimResult, String timestamp) throws IOException {
        ensureStaticDirectoryExists();
        
        String filename = "analysis_result_" + 
                         LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".json";
        Path jsonPath = Paths.get(staticDirectory, filename);
        
        objectMapper.writeValue(jsonPath.toFile(), cimResult);
        
        return "/static/" + filename;
    }
    
    /**
     * static 디렉토리 존재 확인 및 생성
     */
    private void ensureStaticDirectoryExists() throws IOException {
        Path staticPath = Paths.get(staticDirectory);
        if (!Files.exists(staticPath)) {
            Files.createDirectories(staticPath);
        }
    }
    
    // === Result Classes ===
    
    /**
     * 이미지 분석 결과
     */
    public static class ImageAnalysisResult {
        private final boolean success;
        private final String errorMessage;
        private final AnalysisResponse response;
        private final AnalysisJob analysisJob;
        
        public ImageAnalysisResult(boolean success, String errorMessage, 
                                 AnalysisResponse response, AnalysisJob analysisJob) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.response = response;
            this.analysisJob = analysisJob;
        }
        
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public AnalysisResponse getResponse() { return response; }
        public AnalysisJob getAnalysisJob() { return analysisJob; }
    }
    
    /**
     * 단일 이미지 분석 결과 (내부용)
     */
    public static class SingleImageAnalysisResult {
        private final boolean success;
        private final String errorMessage;
        private final List<LayoutInfo> layoutInfo;
        private final List<OCRResult> ocrResults;
        private final List<AIDescriptionResult> aiResults;
        
        public SingleImageAnalysisResult(boolean success, String errorMessage,
                                       List<LayoutInfo> layoutInfo, List<OCRResult> ocrResults,
                                       List<AIDescriptionResult> aiResults) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.layoutInfo = layoutInfo;
            this.ocrResults = ocrResults;
            this.aiResults = aiResults;
        }
        
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public List<LayoutInfo> getLayoutInfo() { return layoutInfo; }
        public List<OCRResult> getOcrResults() { return ocrResults; }
        public List<AIDescriptionResult> getAiResults() { return aiResults; }
    }
    
    /**
     * 단일 이미지 처리 결과 (PDF/다중 이미지용)
     */
    public static class SingleImageProcessingResult {
        private final boolean success;
        private final String imageName;
        private final String message;
        private final AnalysisJob analysisJob;
        private final DocumentPage documentPage;
        
        public SingleImageProcessingResult(boolean success, String imageName, String message, 
                                         AnalysisJob analysisJob, DocumentPage documentPage) {
            this.success = success;
            this.imageName = imageName;
            this.message = message;
            this.analysisJob = analysisJob;
            this.documentPage = documentPage;
        }
        
        public boolean isSuccess() { return success; }
        public String getImageName() { return imageName; }
        public String getMessage() { return message; }
        public AnalysisJob getAnalysisJob() { return analysisJob; }
        public DocumentPage getDocumentPage() { return documentPage; }
    }
    
    /**
     * 이미지 시각화 결과
     */
    private static class ImageVisualizationResult {
        private final String layoutImagePath;
        private final String jsonFilePath;
        
        public ImageVisualizationResult(String layoutImagePath, String jsonFilePath) {
            this.layoutImagePath = layoutImagePath;
            this.jsonFilePath = jsonFilePath;
        }
        
        public String getLayoutImagePath() { return layoutImagePath; }
        public String getJsonFilePath() { return jsonFilePath; }
    }
}