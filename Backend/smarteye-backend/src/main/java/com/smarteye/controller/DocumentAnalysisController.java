package com.smarteye.controller;

import com.smarteye.dto.*;
import com.smarteye.dto.common.LayoutInfo;
import com.smarteye.entity.*;
import com.smarteye.service.*;
import com.smarteye.util.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 문서 분석 컨트롤러 - 메인 분석 API
 * Python api_server.py의 analyze_worksheet, analyze-pdf 엔드포인트 변환
 */
@RestController
@RequestMapping("/api/document")
@Validated
@Tag(name = "Document Analysis", description = "문서 분석 및 OCR 처리 API")
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
    private DocumentAnalysisDataService documentAnalysisDataService;
    
    @Autowired
    private com.smarteye.repository.DocumentPageRepository documentPageRepository;
    
    @Autowired
    private BookService bookService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${smarteye.upload.directory:./uploads}")
    private String uploadDirectory;
    
    @Value("${smarteye.processing.temp-directory:./temp}")
    private String tempDirectory;
    
    @Value("${smarteye.static.directory:./static}")
    private String staticDirectory;
    
    /**
     * 단일 이미지 분석
     * Python api_server.py의 /analyze 엔드포인트와 동일
     */
    @Operation(
        summary = "이미지 문서 분석",
        description = "업로드된 이미지를 분석하여 레이아웃 감지, OCR 텍스트 추출, AI 설명 생성을 수행합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "분석 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AnalysisResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청 (파일 형식 오류, 파일 크기 초과 등)"),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<AnalysisResponse>> analyzeDocument(
            @Parameter(description = "분석할 이미지 파일 (JPG, PNG, JPEG 지원)", required = true)
            @RequestParam("image") MultipartFile image,
            
            @Parameter(description = "분석 모델 선택", example = "SmartEyeSsen")
            @RequestParam(value = "modelChoice", defaultValue = "SmartEyeSsen") String modelChoice,
            
            @Parameter(description = "OpenAI API 키 (AI 설명 생성용, 선택사항)")
            @RequestParam(value = "apiKey", required = false) String apiKey) {
        
        logger.info("이미지 분석 요청 시작 - 파일: {}, 모델: {}, API키 존재: {}", 
                   image.getOriginalFilename(), modelChoice, apiKey != null && !apiKey.trim().isEmpty());
        
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis(); // 시작 시간 기록
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
                
                // 7. 기본 분석 결과 JSON 저장 (시각화용)
                Map<String, Object> basicResult = createBasicAnalysisResult(layoutResult.getLayoutInfo(), ocrResults, aiResults);
                String jsonFilePath = saveCIMResultAsJson(basicResult, timestamp);
                
                // 9. 데이터베이스에 분석 결과 저장
                long processingTimeMs = System.currentTimeMillis() - startTime;
                logger.info("데이터베이스에 분석 결과 저장 시작...");
                documentAnalysisDataService.saveAnalysisResults(
                    analysisJob.getJobId(),
                    layoutResult.getLayoutInfo(),
                    ocrResults,
                    aiResults,
                    jsonFilePath,
                    layoutImagePath,
                    processingTimeMs
                );
                
                // 10. 분석 작업 상태 업데이트
                analysisJobService.updateJobStatus(
                    analysisJob.getJobId(), 
                    AnalysisJob.JobStatus.COMPLETED, 
                    100, 
                    null
                );
                
                // 11. 응답 구성
                AnalysisResponse response = buildAnalysisResponse(
                    layoutImagePath, jsonFilePath, layoutResult.getLayoutInfo(), 
                    ocrResults, aiResults, Long.parseLong(timestamp)
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
    @Operation(
        summary = "PDF 문서 분석",
        description = "업로드된 PDF 파일을 이미지로 변환한 후 분석하여 레이아웃 감지, OCR 텍스트 추출, AI 설명 생성을 수행합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "분석 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AnalysisResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청 (파일 형식 오류, 파일 크기 초과 등)"),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @PostMapping(value = "/analyze-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<AnalysisResponse>> analyzePDF(
            @Parameter(description = "분석할 PDF 파일", required = true)
            @RequestParam("file") MultipartFile pdfFile,
            
            @Parameter(description = "분석 모델 선택", example = "SmartEyeSsen")
            @RequestParam(value = "modelChoice", defaultValue = "SmartEyeSsen") String modelChoice,
            
            @Parameter(description = "OpenAI API 키 (AI 설명 생성용, 선택사항)")
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
                
                logger.info("PDF 페이지 수: {}", pdfImages.size());
                
                // 사용자 조회 또는 익명 사용자 생성
                User user = userService.getOrCreateAnonymousUser();
                
                // Book 생성 (PDF 파일명을 책 제목으로 사용)
                String bookTitle = extractBookTitle(pdfFile.getOriginalFilename());
                CreateBookRequest bookRequest = new CreateBookRequest(bookTitle, 
                    String.format("PDF 문서 (%d 페이지)", pdfImages.size()), user.getId());
                BookDto bookDto = bookService.createBook(bookRequest);
                
                // 각 페이지별로 분석 작업 생성 및 분석 수행
                List<AnalysisJob> analysisJobs = new ArrayList<>();
                List<DocumentPage> allDocumentPages = new ArrayList<>();
                int totalLayoutElements = 0;
                int totalOcrResults = 0;
                int totalAiResults = 0;
                
                String baseTimestamp = String.valueOf(System.currentTimeMillis() / 1000);
                
                for (int pageIndex = 0; pageIndex < pdfImages.size(); pageIndex++) {
                    BufferedImage pageImage = pdfImages.get(pageIndex);
                    int pageNumber = pageIndex + 1;
                    
                    logger.info("페이지 {}/{} 분석 중...", pageNumber, pdfImages.size());
                    
                    // 페이지별 분석 작업 생성
                    String pageTimestamp = baseTimestamp + "_page" + pageNumber;
                    String tempImagePath = savePageImage(pageImage, pageTimestamp);
                    
                    AnalysisJob analysisJob = analysisJobService.createAnalysisJob(
                        user.getId(),
                        String.format("%s_page_%d.png", extractFileName(pdfFile.getOriginalFilename()), pageNumber),
                        tempImagePath,
                        (long) pageImage.getWidth() * pageImage.getHeight() * 4,
                        "image/png",
                        modelChoice
                    );
                    
                    // Book에 분석 작업 추가
                    bookService.addFileToBook(bookDto.getId(), createMultipartFile(pageImage, pageTimestamp), user.getId(), pageNumber);
                    analysisJobs.add(analysisJob);
                    
                    // 페이지 분석 수행
                    LayoutAnalysisResult layoutResult = lamServiceClient
                        .analyzeLayout(pageImage, modelChoice)
                        .get();
                    
                    if (layoutResult.getLayoutInfo().isEmpty()) {
                        logger.warn("페이지 {} 레이아웃 분석 실패", pageNumber);
                        continue;
                    }
                    
                    // OCR 분석
                    List<OCRResult> ocrResults = ocrService.performOCR(
                        pageImage, 
                        layoutResult.getLayoutInfo()
                    );
                    
                    // AI 설명 생성 (선택사항)
                    List<AIDescriptionResult> aiResults = List.of();
                    if (apiKey != null && !apiKey.trim().isEmpty()) {
                        aiResults = aiDescriptionService.generateDescriptions(
                            pageImage,
                            layoutResult.getLayoutInfo(),
                            apiKey
                        ).get();
                    }
                    
                    // 페이지별 시각화 및 결과 저장
                    BufferedImage visualizedImage = createLayoutVisualization(pageImage, layoutResult.getLayoutInfo());
                    String layoutImagePath = saveVisualizationImage(visualizedImage, pageTimestamp);
                    
                    // 페이지별 기본 분석 결과 저장
                    Map<String, Object> basicPageResult = createBasicAnalysisResult(layoutResult.getLayoutInfo(), ocrResults, aiResults);
                    String jsonFilePath = saveCIMResultAsJson(basicPageResult, pageTimestamp);
                    
                    // 데이터베이스에 페이지 분석 결과 저장
                    long processingStartTime = System.currentTimeMillis();
                    DocumentPage documentPage = documentAnalysisDataService.savePageAnalysisResult(
                        analysisJob,
                        pageNumber,
                        tempImagePath,
                        layoutResult.getLayoutInfo(),
                        ocrResults,
                        aiResults,
                        jsonFilePath,
                        layoutImagePath,
                        System.currentTimeMillis() - processingStartTime
                    );
                    
                    allDocumentPages.add(documentPage);
                    
                    // 분석 작업 상태 업데이트
                    analysisJobService.updateJobStatus(
                        analysisJob.getJobId(),
                        AnalysisJob.JobStatus.COMPLETED,
                        100,
                        null
                    );
                    
                    totalLayoutElements += layoutResult.getLayoutInfo().size();
                    totalOcrResults += ocrResults.size();
                    totalAiResults += aiResults.size();
                    
                    logger.info("페이지 {}/{} 분석 완료 - 레이아웃: {}개, OCR: {}개, AI: {}개", 
                               pageNumber, pdfImages.size(), 
                               layoutResult.getLayoutInfo().size(), ocrResults.size(), aiResults.size());
                }
                
                // Book 진행률 업데이트
                bookService.updateBookProgress(bookDto.getId());
                
                // 각 분석 작업별로 페이지 데이터를 로드 (fetch join 사용)
                List<DocumentPage> completeDocumentPages = new ArrayList<>();
                
                for (AnalysisJob job : analysisJobs) {
                    List<DocumentPage> jobPages = documentPageRepository.findByJobIdWithLayoutBlocksAndText(job.getJobId());
                    completeDocumentPages.addAll(jobPages);
                }
                
                logger.info("완전한 DocumentPage 데이터 로드 완료 - 페이지 수: {}, 작업 수: {}", 
                           completeDocumentPages.size(), analysisJobs.size());
                
                // 다중 페이지 데이터 통합
                if (!completeDocumentPages.isEmpty()) {
                    DocumentPage firstPage = completeDocumentPages.get(0);
                    
                    // 모든 페이지의 레이아웃 정보 통합
                    List<LayoutInfo> allLayoutInfo = completeDocumentPages.stream()
                        .flatMap(page -> page.getLayoutBlocks().stream())
                        .map(block -> new LayoutInfo(
                            block.getId().intValue(),
                            block.getClassName(),
                            block.getConfidence() != null ? block.getConfidence() : 0.0,
                            new int[]{block.getX1(), block.getY1(), block.getX2(), block.getY2()},
                            block.getWidth() != null ? block.getWidth() : 0,
                            block.getHeight() != null ? block.getHeight() : 0,
                            block.getArea() != null ? block.getArea() : 0
                        ))
                        .collect(java.util.stream.Collectors.toList());
                    
                    // 모든 페이지의 OCR 결과 통합
                    List<OCRResult> allOcrResults = completeDocumentPages.stream()
                        .flatMap(page -> page.getLayoutBlocks().stream())
                        .filter(block -> block.getOcrText() != null && !block.getOcrText().trim().isEmpty())
                        .map(block -> new OCRResult(
                            block.getId().intValue(),
                            block.getClassName(),
                            new int[]{block.getX1(), block.getY1(), block.getX2(), block.getY2()},
                            block.getOcrText()
                        ))
                        .collect(java.util.stream.Collectors.toList());
                    
                    // 모든 페이지의 AI 결과 통합 (있는 경우)
                    List<AIDescriptionResult> allAiResults = completeDocumentPages.stream()
                        .flatMap(page -> page.getLayoutBlocks().stream())
                        .filter(block -> block.getAiDescription() != null && !block.getAiDescription().trim().isEmpty())
                        .map(block -> new AIDescriptionResult(
                            block.getId().intValue(),
                            block.getClassName(),
                            new int[]{block.getX1(), block.getY1(), block.getX2(), block.getY2()},
                            block.getAiDescription()
                        ))
                        .collect(java.util.stream.Collectors.toList());
                    
                    // 통합된 포맷된 텍스트 생성
                    String combinedFormattedText = completeDocumentPages.stream()
                        .map(page -> page.getAnalysisResult())
                        .filter(text -> text != null && !text.trim().isEmpty())
                        .collect(java.util.stream.Collectors.joining("\n\n--- 페이지 구분 ---\n\n"));
                    
                    // 포맷된 텍스트가 없으면 OCR 텍스트로 생성
                    if (combinedFormattedText.trim().isEmpty()) {
                        combinedFormattedText = String.format("PDF 다중 페이지 분석 완료 (%d 페이지)", pdfImages.size());
                    }
                    
                    AnalysisResponse response = buildAnalysisResponse(
                        firstPage.getLayoutVisualizationPath(),
                        null, // JSON 파일은 페이지별로 분산
                        allLayoutInfo, // 통합된 레이아웃 정보
                        allOcrResults, // 통합된 OCR 결과  
                        allAiResults,  // 통합된 AI 결과
                        Long.parseLong(baseTimestamp)
                    );
                    
                    // 추가 메타데이터
                    response.setJobId(bookDto.getId().toString()); // Book ID를 Job ID로 사용
                    response.setMessage(String.format("PDF 다중 페이지 분석 완료 - 총 %d 페이지, 레이아웃: %d개, OCR: %d개, AI: %d개",
                        pdfImages.size(), totalLayoutElements, totalOcrResults, totalAiResults));
                    
                    logger.info("PDF 다중 페이지 분석 완료 - 책 ID: {}, 페이지 수: {}, 총 레이아웃: {}개", 
                               bookDto.getId(), pdfImages.size(), totalLayoutElements);
                    
                    return ResponseEntity.ok(response);
                } else {
                    return ResponseEntity.badRequest()
                        .body(new AnalysisResponse(false, "PDF 페이지 분석에 실패했습니다."));
                }
                
            } catch (Exception e) {
                logger.error("PDF 분석 중 오류 발생: {}", e.getMessage(), e);
                return ResponseEntity.internalServerError()
                    .body(new AnalysisResponse(false, "PDF 분석 중 오류가 발생했습니다: " + e.getMessage()));
            }
        });
    }
    
    // === Private Helper Methods ===
    
    /**
     * PDF 파일명에서 책 제목을 추출합니다.
     */
    private String extractBookTitle(String originalFilename) {
        if (originalFilename == null) {
            return "PDF 분석 결과";
        }
        
        String nameWithoutExtension = originalFilename.lastIndexOf('.') > 0 
            ? originalFilename.substring(0, originalFilename.lastIndexOf('.'))
            : originalFilename;
            
        return nameWithoutExtension.length() > 100 
            ? nameWithoutExtension.substring(0, 100) + "..."
            : nameWithoutExtension;
    }
    
    /**
     * 파일명에서 확장자를 제거합니다.
     */
    private String extractFileName(String originalFilename) {
        if (originalFilename == null) {
            return "document";
        }
        
        return originalFilename.lastIndexOf('.') > 0 
            ? originalFilename.substring(0, originalFilename.lastIndexOf('.'))
            : originalFilename;
    }
    
    /**
     * 페이지 이미지를 임시 파일로 저장합니다.
     */
    private String savePageImage(BufferedImage image, String timestamp) {
        try {
            String fileName = "page_" + timestamp + ".png";
            String filePath = tempDirectory + "/" + fileName;
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
     * BufferedImage를 MultipartFile로 변환합니다.
     */
    private MultipartFile createMultipartFile(BufferedImage image, String timestamp) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();
            
            return new MultipartFile() {
                @Override
                public String getName() {
                    return "file";
                }
                
                @Override
                public String getOriginalFilename() {
                    return "page_" + timestamp + ".png";
                }
                
                @Override
                public String getContentType() {
                    return "image/png";
                }
                
                @Override
                public boolean isEmpty() {
                    return imageBytes.length == 0;
                }
                
                @Override
                public long getSize() {
                    return imageBytes.length;
                }
                
                @Override
                public byte[] getBytes() {
                    return imageBytes;
                }
                
                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(imageBytes);
                }
                
                @Override
                public void transferTo(File dest) throws IOException, IllegalStateException {
                    try (FileOutputStream fos = new FileOutputStream(dest)) {
                        fos.write(imageBytes);
                    }
                }
            };
        } catch (IOException e) {
            logger.error("MultipartFile 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("MultipartFile 생성에 실패했습니다", e);
        }
    }
    
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
    
    private Map<String, Object> createBasicAnalysisResult(
            List<LayoutInfo> layoutInfo,
            List<OCRResult> ocrResults, 
            List<AIDescriptionResult> aiResults) {
        
        // 기본적인 분석 결과만 포함 (시각화용)
        return JsonUtils.createBasicResult(layoutInfo, ocrResults, aiResults);
    }
    
    private String saveCIMResultAsJson(Map<String, Object> cimResult, String timestamp) throws IOException {
        ensureStaticDirectoryExists();
        
        String filename = "analysis_result_" + 
                         LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".json";
        Path jsonPath = Paths.get(staticDirectory, filename);
        
        objectMapper.writeValue(jsonPath.toFile(), cimResult);
        
        return "/static/" + filename;
    }
    
    
    private AnalysisResponse buildAnalysisResponse(
            String layoutImagePath, String jsonFilePath,
            List<LayoutInfo> layoutInfo,
            List<OCRResult> ocrResults, List<AIDescriptionResult> aiResults,
            Long timestamp) {
        
        AnalysisResponse response = new AnalysisResponse(true, "분석이 성공적으로 완료되었습니다.");
        response.setLayoutImageUrl(layoutImagePath);
        response.setJsonUrl(jsonFilePath);
        response.setOcrResults(ocrResults);
        response.setAiResults(aiResults);
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
    
    /**
     * 여러 이미지 동시 분석
     * PDF 처리 방식을 기반으로 한 다중 이미지 파이프라인 처리
     */
    @Operation(
        summary = "여러 이미지 동시 분석",
        description = "여러 개의 이미지를 업로드하여 일괄 분석하고 하나의 책으로 그룹화하여 관리합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "분석 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AnalysisResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청 (파일 개수 초과, 파일 크기 초과 등)"),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @PostMapping(value = "/analyze-multiple-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<AnalysisResponse>> analyzeMultipleImages(
            @Parameter(description = "분석할 이미지 파일들 (JPG, PNG, JPEG 지원)", required = true)
            @RequestParam("images") MultipartFile[] images,
            
            @Parameter(description = "분석 모델 선택", example = "SmartEyeSsen")
            @RequestParam(value = "modelChoice", defaultValue = "SmartEyeSsen") String modelChoice,
            
            @Parameter(description = "OpenAI API 키 (AI 설명 생성용, 선택사항)")
            @RequestParam(value = "apiKey", required = false) String apiKey,
            
            @Parameter(description = "책 제목 (선택사항, 미지정 시 자동 생성)")
            @RequestParam(value = "bookTitle", required = false) String bookTitle) {
        
        logger.info("여러 이미지 분석 요청 - 이미지 수: {}, 모델: {}, API키 존재: {}", 
                   images.length, modelChoice, apiKey != null && !apiKey.trim().isEmpty());
        
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                // 1. 이미지 배열 검증
                validateMultipleImages(images);
                
                // 2. 사용자 조회 또는 익명 사용자 생성
                User user = userService.getOrCreateAnonymousUser();
                
                // 3. Book 생성 (이미지 집합을 하나의 책으로 그룹화)
                String finalBookTitle = bookTitle != null && !bookTitle.trim().isEmpty() 
                    ? bookTitle.trim() 
                    : generateBookTitleFromImages(images);
                
                CreateBookRequest bookRequest = new CreateBookRequest(
                    finalBookTitle, 
                    String.format("이미지 집합 분석 결과 (%d 장의 이미지)", images.length), 
                    user.getId()
                );
                BookDto bookDto = bookService.createBook(bookRequest);
                
                logger.info("Book 생성 완료 - ID: {}, 제목: '{}', 이미지 수: {}", 
                           bookDto.getId(), finalBookTitle, images.length);
                
                // 4. 각 이미지별로 분석 작업 생성 및 분석 수행
                List<ImageProcessingResult> processingResults = new ArrayList<>();
                
                String baseTimestamp = String.valueOf(System.currentTimeMillis() / 1000);
                
                // 이미지 처리 방식 결정 (병렬 vs 순차)
                boolean useParallelProcessing = shouldUseParallelProcessing(images.length);
                logger.info("이미지 처리 방식: {}", useParallelProcessing ? "병렬 처리" : "순차 처리");
                
                if (useParallelProcessing) {
                    // 병렬 처리 (작은 이미지 개수)
                    processingResults = processImagesInParallel(
                        images, user, bookDto, modelChoice, apiKey, baseTimestamp
                    );
                } else {
                    // 순차 처리 (많은 이미지 개수 또는 메모리 절약)
                    processingResults = processImagesSequentially(
                        images, user, bookDto, modelChoice, apiKey, baseTimestamp
                    );
                }
                
                // 5. 성공한 결과들만 수집
                List<ImageProcessingResult> successfulResults = processingResults.stream()
                    .filter(result -> result.isSuccess())
                    .collect(Collectors.toList());
                
                List<ImageProcessingResult> failedResults = processingResults.stream()
                    .filter(result -> !result.isSuccess())
                    .collect(Collectors.toList());
                
                if (successfulResults.isEmpty()) {
                    return ResponseEntity.badRequest()
                        .body(new AnalysisResponse(false, "모든 이미지 분석에 실패했습니다."));
                }
                
                // 6. Book 진행률 업데이트
                bookService.updateBookProgress(bookDto.getId());
                
                // 7. 성공한 결과들로부터 완전한 DocumentPage 데이터 로드
                List<DocumentPage> completeDocumentPages = new ArrayList<>();
                for (ImageProcessingResult result : successfulResults) {
                    if (result.getAnalysisJob() != null) {
                        List<DocumentPage> jobPages = documentPageRepository
                            .findByJobIdWithLayoutBlocksAndText(result.getAnalysisJob().getJobId());
                        completeDocumentPages.addAll(jobPages);
                    }
                }
                
                logger.info("완전한 DocumentPage 데이터 로드 완료 - 성공: {}개, 실패: {}개, 총 페이지: {}", 
                           successfulResults.size(), failedResults.size(), completeDocumentPages.size());
                
                // 8. 다중 이미지 데이터 통합 및 응답 생성
                if (!completeDocumentPages.isEmpty()) {
                    AnalysisResponse response = buildMultipleImagesResponse(
                        completeDocumentPages, bookDto, successfulResults.size(), 
                        failedResults.size(), baseTimestamp
                    );
                    
                    // 처리 결과 요약 메시지
                    long processingTimeMs = System.currentTimeMillis() - startTime;
                    String resultMessage = String.format(
                        "여러 이미지 분석 완료 - 성공: %d개, 실패: %d개, 처리 시간: %.2f초",
                        successfulResults.size(), failedResults.size(), processingTimeMs / 1000.0
                    );
                    
                    if (!failedResults.isEmpty()) {
                        resultMessage += String.format(" (실패한 이미지: %s)", 
                            failedResults.stream()
                                .map(r -> r.getImageName())
                                .collect(Collectors.joining(", "))
                        );
                    }
                    
                    response.setMessage(resultMessage);
                    response.setJobId(bookDto.getId().toString());
                    
                    logger.info("여러 이미지 분석 완료 - 책 ID: {}, 성공: {}개, 실패: {}개", 
                               bookDto.getId(), successfulResults.size(), failedResults.size());
                    
                    return ResponseEntity.ok(response);
                } else {
                    return ResponseEntity.badRequest()
                        .body(new AnalysisResponse(false, "이미지 분석 결과를 생성할 수 없습니다."));
                }
                
            } catch (Exception e) {
                logger.error("여러 이미지 분석 중 오류 발생: {}", e.getMessage(), e);
                return ResponseEntity.internalServerError()
                    .body(new AnalysisResponse(false, "여러 이미지 분석 중 오류가 발생했습니다: " + e.getMessage()));
            }
        });
    }
    
    // === 여러 이미지 처리를 위한 Helper Methods ===
    
    /**
     * 여러 이미지 파일 검증
     */
    private void validateMultipleImages(MultipartFile[] images) {
        if (images == null || images.length == 0) {
            throw new IllegalArgumentException("이미지 파일이 없습니다.");
        }
        
        // 이미지 개수 제한 (최대 20개)
        if (images.length > 20) {
            throw new IllegalArgumentException("한 번에 처리할 수 있는 이미지는 최대 20개입니다.");
        }
        
        // 총 파일 크기 계산 및 제한 (최대 200MB)
        long totalSize = 0;
        for (MultipartFile image : images) {
            totalSize += image.getSize();
        }
        
        if (totalSize > 200 * 1024 * 1024) { // 200MB
            throw new IllegalArgumentException("총 파일 크기가 너무 큽니다. (최대 200MB)");
        }
        
        // 각 이미지 개별 검증
        for (int i = 0; i < images.length; i++) {
            MultipartFile image = images[i];
            
            if (image.isEmpty()) {
                throw new IllegalArgumentException(String.format("이미지 %d번이 비어있습니다.", i + 1));
            }
            
            // 개별 파일 크기 제한 (50MB)
            if (image.getSize() > 50 * 1024 * 1024) {
                throw new IllegalArgumentException(
                    String.format("이미지 %d번 (%s)의 크기가 너무 큽니다. (최대 50MB)", 
                    i + 1, image.getOriginalFilename())
                );
            }
            
            // 이미지 형식 체크
            String contentType = image.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IllegalArgumentException(
                    String.format("이미지 %d번 (%s)은 지원하지 않는 파일 형식입니다.", 
                    i + 1, image.getOriginalFilename())
                );
            }
        }
        
        logger.info("이미지 배열 검증 완료 - 개수: {}, 총 크기: {:.2f}MB", 
                   images.length, totalSize / (1024.0 * 1024.0));
    }
    
    /**
     * 이미지들로부터 책 제목 자동 생성
     */
    private String generateBookTitleFromImages(MultipartFile[] images) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        
        if (images.length == 1) {
            String filename = extractFileName(images[0].getOriginalFilename());
            return String.format("이미지 분석: %s (%s)", filename, timestamp);
        } else {
            return String.format("이미지 집합 분석 (%d장) - %s", images.length, timestamp);
        }
    }
    
    /**
     * 병렬 처리 사용 여부 결정
     */
    private boolean shouldUseParallelProcessing(int imageCount) {
        // 이미지 개수가 적고 시스템 리소스가 충분한 경우 병렬 처리
        return imageCount <= 5 && Runtime.getRuntime().availableProcessors() >= 4;
    }
    
    /**
     * 이미지들을 병렬로 처리
     */
    private List<ImageProcessingResult> processImagesInParallel(
            MultipartFile[] images, User user, BookDto bookDto, 
            String modelChoice, String apiKey, String baseTimestamp) {
        
        logger.info("병렬 이미지 처리 시작 - 이미지 수: {}", images.length);
        
        return IntStream.range(0, images.length)
            .parallel()
            .mapToObj(i -> {
                try {
                    return processSingleImage(
                        images[i], i + 1, user, bookDto, modelChoice, apiKey, 
                        baseTimestamp + "_img" + (i + 1)
                    );
                } catch (Exception e) {
                    logger.error("이미지 {}번 병렬 처리 실패: {}", i + 1, e.getMessage(), e);
                    return new ImageProcessingResult(false, images[i].getOriginalFilename(), 
                        "처리 실패: " + e.getMessage(), null, null);
                }
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 이미지들을 순차적으로 처리
     */
    private List<ImageProcessingResult> processImagesSequentially(
            MultipartFile[] images, User user, BookDto bookDto, 
            String modelChoice, String apiKey, String baseTimestamp) {
        
        logger.info("순차 이미지 처리 시작 - 이미지 수: {}", images.length);
        
        List<ImageProcessingResult> results = new ArrayList<>();
        
        for (int i = 0; i < images.length; i++) {
            try {
                logger.info("이미지 {}/{} 처리 중: {}", i + 1, images.length, images[i].getOriginalFilename());
                
                ImageProcessingResult result = processSingleImage(
                    images[i], i + 1, user, bookDto, modelChoice, apiKey, 
                    baseTimestamp + "_img" + (i + 1)
                );
                
                results.add(result);
                
                logger.info("이미지 {}/{} 처리 완료: {} (성공: {})", 
                           i + 1, images.length, images[i].getOriginalFilename(), result.isSuccess());
                
            } catch (Exception e) {
                logger.error("이미지 {}/{} 처리 실패: {} - {}", 
                           i + 1, images.length, images[i].getOriginalFilename(), e.getMessage(), e);
                
                results.add(new ImageProcessingResult(false, images[i].getOriginalFilename(), 
                    "처리 실패: " + e.getMessage(), null, null));
            }
        }
        
        return results;
    }
    
    /**
     * 단일 이미지 처리 (PDF 분석 로직 기반)
     */
    private ImageProcessingResult processSingleImage(
            MultipartFile image, int imageNumber, User user, BookDto bookDto,
            String modelChoice, String apiKey, String imageTimestamp) throws Exception {
        
        // 1. 이미지 검증 및 로드
        BufferedImage bufferedImage = validateAndLoadImage(image);
        
        // 2. 이미지 임시 저장
        String tempImagePath = savePageImage(bufferedImage, imageTimestamp);
        
        // 3. 분석 작업 생성
        AnalysisJob analysisJob = analysisJobService.createAnalysisJob(
            user.getId(),
            String.format("image_%d_%s", imageNumber, image.getOriginalFilename()),
            tempImagePath,
            image.getSize(),
            image.getContentType(),
            modelChoice
        );
        
        // 4. Book에 파일 추가
        bookService.addFileToBook(bookDto.getId(), image, user.getId(), imageNumber);
        
        // 5. LAM 레이아웃 분석
        LayoutAnalysisResult layoutResult = lamServiceClient
            .analyzeLayout(bufferedImage, modelChoice)
            .get();
        
        if (layoutResult.getLayoutInfo().isEmpty()) {
            logger.warn("이미지 {} 레이아웃 분석 실패: 감지된 요소 없음", imageNumber);
            return new ImageProcessingResult(false, image.getOriginalFilename(), 
                "레이아웃 분석 실패: 감지된 요소 없음", analysisJob, null);
        }
        
        // 6. OCR 분석
        List<OCRResult> ocrResults = ocrService.performOCR(
            bufferedImage, 
            layoutResult.getLayoutInfo()
        );
        
        // 7. AI 설명 생성 (선택사항)
        List<AIDescriptionResult> aiResults = List.of();
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            aiResults = aiDescriptionService.generateDescriptions(
                bufferedImage,
                layoutResult.getLayoutInfo(),
                apiKey
            ).get();
        }
        
        // 8. 시각화 및 결과 저장
        BufferedImage visualizedImage = createLayoutVisualization(bufferedImage, layoutResult.getLayoutInfo());
        String layoutImagePath = saveVisualizationImage(visualizedImage, imageTimestamp);
        
        Map<String, Object> basicResult = createBasicAnalysisResult(layoutResult.getLayoutInfo(), ocrResults, aiResults);
        String jsonFilePath = saveCIMResultAsJson(basicResult, imageTimestamp);
        
        // 9. 데이터베이스에 페이지 분석 결과 저장
        long processingStartTime = System.currentTimeMillis();
        DocumentPage documentPage = documentAnalysisDataService.savePageAnalysisResult(
            analysisJob,
            imageNumber,
            tempImagePath,
            layoutResult.getLayoutInfo(),
            ocrResults,
            aiResults,
            jsonFilePath,
            layoutImagePath,
            System.currentTimeMillis() - processingStartTime
        );
        
        // 10. 분석 작업 상태 업데이트
        analysisJobService.updateJobStatus(
            analysisJob.getJobId(),
            AnalysisJob.JobStatus.COMPLETED,
            100,
            null
        );
        
        return new ImageProcessingResult(true, image.getOriginalFilename(), 
            "분석 완료", analysisJob, documentPage);
    }
    
    /**
     * 다중 이미지 분석 결과를 통합하여 응답 생성
     */
    private AnalysisResponse buildMultipleImagesResponse(
            List<DocumentPage> completeDocumentPages, BookDto bookDto, 
            int successCount, int failureCount, String baseTimestamp) {
        
        // 모든 페이지의 레이아웃 정보 통합
        List<LayoutInfo> allLayoutInfo = completeDocumentPages.stream()
            .flatMap(page -> page.getLayoutBlocks().stream())
            .map(block -> new LayoutInfo(
                block.getId().intValue(),
                block.getClassName(),
                block.getConfidence() != null ? block.getConfidence() : 0.0,
                new int[]{block.getX1(), block.getY1(), block.getX2(), block.getY2()},
                block.getWidth() != null ? block.getWidth() : 0,
                block.getHeight() != null ? block.getHeight() : 0,
                block.getArea() != null ? block.getArea() : 0
            ))
            .collect(Collectors.toList());
        
        // 모든 페이지의 OCR 결과 통합
        List<OCRResult> allOcrResults = completeDocumentPages.stream()
            .flatMap(page -> page.getLayoutBlocks().stream())
            .filter(block -> block.getOcrText() != null && !block.getOcrText().trim().isEmpty())
            .map(block -> new OCRResult(
                block.getId().intValue(),
                block.getClassName(),
                new int[]{block.getX1(), block.getY1(), block.getX2(), block.getY2()},
                block.getOcrText()
            ))
            .collect(Collectors.toList());
        
        // 모든 페이지의 AI 결과 통합
        List<AIDescriptionResult> allAiResults = completeDocumentPages.stream()
            .flatMap(page -> page.getLayoutBlocks().stream())
            .filter(block -> block.getAiDescription() != null && !block.getAiDescription().trim().isEmpty())
            .map(block -> new AIDescriptionResult(
                block.getId().intValue(),
                block.getClassName(),
                new int[]{block.getX1(), block.getY1(), block.getX2(), block.getY2()},
                block.getAiDescription()
            ))
            .collect(Collectors.toList());
        
        // 첫 번째 페이지의 레이아웃 이미지 사용
        String layoutImagePath = !completeDocumentPages.isEmpty() 
            ? completeDocumentPages.get(0).getLayoutVisualizationPath() 
            : null;
        
        AnalysisResponse response = buildAnalysisResponse(
            layoutImagePath,
            null, // JSON 파일은 페이지별로 분산
            allLayoutInfo,
            allOcrResults,
            allAiResults,
            Long.parseLong(baseTimestamp)
        );
        
        return response;
    }
    
    /**
     * 이미지 처리 결과를 담는 내부 클래스
     */
    private static class ImageProcessingResult {
        private final boolean success;
        private final String imageName;
        private final String message;
        private final AnalysisJob analysisJob;
        private final DocumentPage documentPage;
        
        public ImageProcessingResult(boolean success, String imageName, String message, 
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

    // /analyze-structured 엔드포인트는 제거됨 - 구조화 분석은 Java CIMService와 CIMController로 이식됨
}