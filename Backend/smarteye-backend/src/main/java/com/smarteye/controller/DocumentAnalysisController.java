package com.smarteye.controller;

import com.smarteye.dto.*;
import com.smarteye.dto.CIMAnalysisResponse;
import com.smarteye.dto.CIMToTextRequest;
import com.smarteye.dto.TextConversionResponse;
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
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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
@Tag(name = "Document Analysis", description = "문서 분석 및 OCR 처리 API")
public class DocumentAnalysisController {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentAnalysisController.class);
    
    @Autowired
    private LAMServiceClient lamServiceClient;
    
    @Autowired
    private CIMService cimService;
    
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
                
                // 7. CIM 통합 결과 생성
                Map<String, Object> cimResult = createCIMResult(layoutResult.getLayoutInfo(), ocrResults, aiResults);
                String jsonFilePath = saveCIMResultAsJson(cimResult, timestamp);
                
                // 8. 포맷팅된 텍스트 생성
                String formattedText = createFormattedText(cimResult);
                
                // 9. 데이터베이스에 분석 결과 저장
                long processingTimeMs = System.currentTimeMillis() - startTime;
                logger.info("데이터베이스에 분석 결과 저장 시작...");
                documentAnalysisDataService.saveAnalysisResults(
                    analysisJob.getJobId(),
                    layoutResult.getLayoutInfo(),
                    ocrResults,
                    aiResults,
                    cimResult,
                    formattedText,
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
     * CIM 통합 분석 API
     * Phase 1: 프론트엔드 연동을 위한 CIM 통합 분석 엔드포인트
     */
    @Operation(
        summary = "CIM 통합 분석",
        description = "이미지 분석과 CIM 통합 결과를 동시에 제공하는 API입니다. 현재 Java 포맷팅 규칙을 적용한 2차 가공 텍스트를 포함합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "CIM 분석 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CIMAnalysisResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @PostMapping(value = "/analyze-cim", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<CIMAnalysisResponse>> analyzeCIM(
            @Parameter(description = "분석할 이미지 파일", required = true)
            @RequestParam("image") MultipartFile image,

            @Parameter(description = "분석 모델 선택", example = "SmartEyeSsen")
            @RequestParam(value = "modelChoice", defaultValue = "SmartEyeSsen") String modelChoice,

            @Parameter(description = "OpenAI API 키 (선택사항)")
            @RequestParam(value = "apiKey", required = false) String apiKey,

            @Parameter(description = "구조화된 분석 활성화", example = "true")
            @RequestParam(value = "structuredAnalysis", defaultValue = "true") boolean structuredAnalysis) {

        logger.info("CIM 통합 분석 요청 - 파일: {}, 모델: {}, 구조화 분석: {}",
                   image.getOriginalFilename(), modelChoice, structuredAnalysis);

        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            try {
                // 1. 기본 이미지 분석 수행
                BufferedImage bufferedImage = validateAndLoadImage(image);
                String jobId = fileService.generateJobId();
                String savedFilePath = fileService.saveUploadedFile(image, jobId);

                // 2. 분석 작업 생성
                AnalysisJob analysisJob = analysisJobService.createAnalysisJob(
                    null, image.getOriginalFilename(), savedFilePath,
                    image.getSize(), image.getContentType(), modelChoice
                );

                // 3. CIM 통합 분석 수행
                Map<String, Object> cimResult;
                String formattedText;
                String layoutImagePath;

                if (structuredAnalysis) {
                    // 구조화된 CIM 분석
                    com.smarteye.service.StructuredJSONService.StructuredResult structuredResult =
                        cimService.performStructuredAnalysisWithCIM(bufferedImage, analysisJob, modelChoice, apiKey);

                    // CIM 데이터 생성
                    cimResult = JsonUtils.convertStructuredResultToCIM(structuredResult);
                    formattedText = JsonUtils.createFormattedText(cimResult);

                    // 레이아웃 시각화 (구조화된 결과에서 레이아웃 정보 추출)
                    List<LayoutInfo> layoutInfo = extractLayoutInfoFromStructured(structuredResult);
                    BufferedImage visualizedImage = createLayoutVisualization(bufferedImage, layoutInfo);
                    String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                    layoutImagePath = saveVisualizationImage(visualizedImage, timestamp);
                } else {
                    // 기본 CIM 분석
                    LayoutAnalysisResult layoutResult = lamServiceClient
                        .analyzeLayout(bufferedImage, modelChoice).get();
                    List<OCRResult> ocrResults = ocrService.performOCR(bufferedImage, layoutResult.getLayoutInfo());
                    List<AIDescriptionResult> aiResults = (apiKey != null && !apiKey.trim().isEmpty()) ?
                        aiDescriptionService.generateDescriptions(bufferedImage, layoutResult.getLayoutInfo(), apiKey).get() :
                        List.of();

                    cimResult = createCIMResult(layoutResult.getLayoutInfo(), ocrResults, aiResults);
                    formattedText = createFormattedText(cimResult);

                    BufferedImage visualizedImage = createLayoutVisualization(bufferedImage, layoutResult.getLayoutInfo());
                    String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                    layoutImagePath = saveVisualizationImage(visualizedImage, timestamp);
                }

                // 4. 분석 작업 상태 업데이트
                analysisJobService.updateJobStatus(
                    analysisJob.getJobId(),
                    AnalysisJob.JobStatus.COMPLETED,
                    100,
                    null
                );

                // 5. 통계 정보 생성
                Map<String, Object> stats = createCIMStats(cimResult, System.currentTimeMillis() - startTime);

                // 6. 응답 구성
                CIMAnalysisResponse response = new CIMAnalysisResponse(
                    true,
                    "CIM 분석이 성공적으로 완료되었습니다.",
                    analysisJob.getJobId(),
                    layoutImagePath,
                    stats,
                    cimResult,
                    formattedText,
                    System.currentTimeMillis()
                );

                logger.info("CIM 통합 분석 완료 - 작업 ID: {}, 처리 시간: {}ms",
                           analysisJob.getJobId(), System.currentTimeMillis() - startTime);

                return ResponseEntity.ok(response);

            } catch (Exception e) {
                logger.error("CIM 분석 중 오류 발생: {}", e.getMessage(), e);
                return ResponseEntity.internalServerError()
                    .body(new CIMAnalysisResponse(false, "CIM 분석 중 오류가 발생했습니다: " + e.getMessage()));
            }
        });
    }

    /**
     * CIM 데이터를 텍스트로 변환
     * Phase 1: JsonUtils.createFormattedText를 호출하여 텍스트 변환
     */
    @Operation(
        summary = "CIM 데이터 텍스트 변환",
        description = "CIM 데이터를 다양한 형식의 텍스트로 변환합니다. JsonUtils.createFormattedText를 사용하여 현재 Java 포맷팅 규칙을 적용합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "텍스트 변환 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = TextConversionResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @PostMapping(value = "/cim-to-text", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TextConversionResponse> convertCIMToText(
            @Parameter(description = "CIM 텍스트 변환 요청", required = true)
            @RequestBody CIMToTextRequest request) {

        logger.info("CIM 텍스트 변환 요청 - 작업 ID: {}, 출력 형식: {}",
                   request.getJobId(), request.getOutputFormat());

        long startTime = System.currentTimeMillis();
        try {
            // 1. 입력 검증
            if (request.getCimData() == null || request.getCimData().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new TextConversionResponse(false, "CIM 데이터가 비어있습니다."));
            }

            // 2. 작업 ID가 있는 경우 DB에서 데이터 조회 및 병합
            Map<String, Object> finalCimData = request.getCimData();
            if (request.getJobId() != null && !request.getJobId().trim().isEmpty()) {
                // TODO: DB에서 기존 CIM 데이터 조회하여 병합 (옵션)
                logger.info("작업 ID {}의 기존 데이터와 병합 (향후 구현)", request.getJobId());
            }

            // 3. 요청된 형식에 따라 텍스트 변환
            String convertedText;
            switch (request.getOutputFormat()) {
                case FORMATTED:
                    convertedText = JsonUtils.createFormattedText(finalCimData);
                    break;
                case STRUCTURED:
                    convertedText = createStructuredTextFromCIMData(finalCimData);
                    break;
                case RAW:
                    convertedText = extractRawTextFromCIMData(finalCimData);
                    break;
                default:
                    convertedText = JsonUtils.createFormattedText(finalCimData);
            }

            // 4. 섹션 필터링 적용 (선택사항)
            if (request.getSectionFilter() != null && !request.getSectionFilter().trim().isEmpty()) {
                convertedText = applySectionFilter(convertedText, request.getSectionFilter());
            }

            // 5. 통계 계산
            long processingTime = System.currentTimeMillis() - startTime;
            TextConversionResponse.TextConversionStats stats = calculateTextStats(
                convertedText, finalCimData, processingTime
            );

            // 6. 메타데이터 생성 (요청 시)
            Map<String, Object> metadata = null;
            if (request.isIncludeMetadata()) {
                metadata = createConversionMetadata(request, finalCimData, stats);
            }

            // 7. 응답 구성
            TextConversionResponse response = new TextConversionResponse(
                true,
                "텍스트 변환이 성공적으로 완료되었습니다.",
                convertedText,
                stats,
                metadata
            );

            logger.info("CIM 텍스트 변환 완료 - 출력 형식: {}, 문자 수: {}, 처리 시간: {}ms",
                       request.getOutputFormat(), convertedText.length(), processingTime);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("CIM 텍스트 변환 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(new TextConversionResponse(false, "텍스트 변환 중 오류가 발생했습니다: " + e.getMessage()));
        }
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
                    
                    Map<String, Object> pageCimResult = createCIMResult(layoutResult.getLayoutInfo(), ocrResults, aiResults);
                    String jsonFilePath = saveCIMResultAsJson(pageCimResult, pageTimestamp);
                    String formattedText = createFormattedText(pageCimResult);
                    
                    // 데이터베이스에 페이지 분석 결과 저장
                    long processingStartTime = System.currentTimeMillis();
                    DocumentPage documentPage = documentAnalysisDataService.savePageAnalysisResult(
                        analysisJob,
                        pageNumber,
                        tempImagePath,
                        layoutResult.getLayoutInfo(),
                        ocrResults,
                        aiResults,
                        formattedText,
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
                    
                    // 모든 페이지의 OCR 결과 통합 - 실제 신뢰도 포함
                    List<OCRResult> allOcrResults = completeDocumentPages.stream()
                        .flatMap(page -> page.getLayoutBlocks().stream())
                        .filter(block -> block.getOcrText() != null && !block.getOcrText().trim().isEmpty())
                        .map(block -> new OCRResult(
                            block.getId().intValue(),
                            block.getClassName(),
                            new int[]{block.getX1(), block.getY1(), block.getX2(), block.getY2()},
                            block.getOcrText(),
                            block.getOcrConfidence() != null ? block.getOcrConfidence() : 0.8 // OCR 신뢰도 사용
                        ))
                        .collect(java.util.stream.Collectors.toList());
                    
                    // 모든 페이지의 AI 결과 통합 (있는 경우) - 실제 신뢰도 포함
                    List<AIDescriptionResult> allAiResults = new ArrayList<>();
                    for (DocumentPage page : completeDocumentPages) {
                        List<AIDescriptionResult> pageAiResults = page.getLayoutBlocks().stream()
                            .filter(block -> block.getAiDescription() != null && !block.getAiDescription().trim().isEmpty())
                            .map(block -> {
                                // AI 신뢰도 계산을 위한 메타데이터 생성
                                Map<String, Object> metadata = new HashMap<>();
                                metadata.put("source", "pdf_analysis");
                                metadata.put("page_number", page.getPageNumber());
                                
                                // AI 신뢰도 계산 (설명 길이와 키워드 기반)
                                double confidence = calculatePDFAIConfidence(block.getAiDescription(), block.getClassName());
                                
                                return new AIDescriptionResult(
                                    block.getId().intValue(),
                                    block.getClassName(),
                                    new int[]{block.getX1(), block.getY1(), block.getX2(), block.getY2()},
                                    block.getAiDescription(),
                                    block.getClassName(), // elementType
                                    confidence,
                                    block.getAiDescription(), // extractedText
                                    metadata
                                );
                            })
                            .collect(java.util.stream.Collectors.toList());
                        allAiResults.addAll(pageAiResults);
                    }
                    
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
                        combinedFormattedText, // 통합된 포맷된 텍스트
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
        
        // 🔄 전체 데이터 복원 (데이터 손실 없는 해결책 적용)
        response.setOcrResults(ocrResults);    // 전체 OCR 결과 포함
        response.setAiResults(aiResults);      // 전체 AI 결과 포함
        response.setFormattedText(formattedText); // 전체 포맷된 텍스트 포함
        response.setTimestamp(timestamp);

        // OCR 텍스트 통합 (전체)
        String combinedOcrText = ocrResults.stream()
            .map(result -> "[" + result.getClassName() + "]\n" + result.getText() + "\n\n")
            .collect(Collectors.joining());
        response.setOcrText(combinedOcrText.trim());

        // AI 설명 통합 (전체)
        String combinedAiText = aiResults.stream()
            .map(result -> "[" + result.getClassName() + "]\n" + result.getDescription() + "\n\n")
            .collect(Collectors.joining());
        response.setAiText(combinedAiText.trim());
        
        // 통계 생성 - 프론트엔드 호환성을 위한 추가 계산
        Map<String, Integer> classCounts = layoutInfo.stream()
            .collect(Collectors.groupingBy(
                LayoutInfo::getClassName,
                Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
            ));

        // 총 문자 수 계산
        int totalCharacters = ocrResults.stream()
            .mapToInt(result -> result.getText() != null ? result.getText().length() : 0)
            .sum();

        // 평균 신뢰도 계산 (레이아웃 분석 결과 기준)
        double averageConfidence = layoutInfo.stream()
            .mapToDouble(LayoutInfo::getConfidence)
            .average()
            .orElse(0.0);

        // 처리 시간 (밀리초를 초로 변환)
        double processingTimeSeconds = timestamp != null ? (System.currentTimeMillis() - (timestamp * 1000)) / 1000.0 : 0.0;

        AnalysisResponse.AnalysisStats stats = new AnalysisResponse.AnalysisStats(
            layoutInfo.size(),
            ocrResults.size(),
            aiResults.size(),
            classCounts,
            totalCharacters,
            averageConfidence,
            processingTimeSeconds
        );
        response.setStats(stats);
        
        logger.info("응답 생성 완료 - 레이아웃: {}개, OCR: {}개, AI: {}개",
                   layoutInfo.size(), ocrResults.size(), aiResults.size());
        
        return response;
    }
    
    private void ensureStaticDirectoryExists() throws IOException {
        Path staticPath = Paths.get(staticDirectory);
        if (!Files.exists(staticPath)) {
            Files.createDirectories(staticPath);
        }
    }
    
    
    /**
     * CIM에서 생성된 구조화된 결과에서 텍스트 생성
     */
    private String createStructuredTextFromCIM(com.smarteye.service.StructuredJSONService.StructuredResult structuredResult) {
        if (structuredResult == null) {
            return "";
        }
        
        StringBuilder formattedText = new StringBuilder();
        
        // 문서 정보 추가
        var docInfo = structuredResult.documentInfo;
        if (docInfo != null) {
            formattedText.append("📋 문서 분석 결과\n");
            formattedText.append("총 문제 수: ").append(docInfo.totalQuestions).append("개\n");
            formattedText.append("레이아웃 유형: ").append(docInfo.layoutType != null ? docInfo.layoutType : "미확인").append("\n\n");
            formattedText.append("=".repeat(50)).append("\n\n");
        }
        
        // 각 문제별 처리
        var questions = structuredResult.questions;
        for (int i = 0; i < questions.size(); i++) {
            var question = questions.get(i);
            String questionNum = question.questionNumber != null ? question.questionNumber : "문제" + (i + 1);
            String section = question.section;
            
            // 문제 제목
            formattedText.append("🔸 ").append(questionNum);
            if (section != null && !section.trim().isEmpty()) {
                formattedText.append(" (").append(section).append(")");
            }
            formattedText.append("\n\n");
            
            var content = question.questionContent;
            if (content != null) {
                // 지문
                if (content.passage != null && !content.passage.trim().isEmpty()) {
                    formattedText.append("📖 지문:\n").append(content.passage).append("\n\n");
                }
                
                // 주요 문제
                if (content.mainQuestion != null && !content.mainQuestion.trim().isEmpty()) {
                    formattedText.append("❓ 문제:\n").append(content.mainQuestion).append("\n\n");
                }
                
                // 선택지
                if (content.choices != null && !content.choices.isEmpty()) {
                    formattedText.append("📝 선택지:\n");
                    for (var choice : content.choices) {
                        String choiceNum = choice.choiceNumber;
                        String choiceText = choice.choiceText;
                        if (choiceNum != null && choiceText != null) {
                            formattedText.append("   ").append(choiceNum).append(" ").append(choiceText).append("\n");
                        } else if (choiceText != null) {
                            formattedText.append("   • ").append(choiceText).append("\n");
                        }
                    }
                    formattedText.append("\n");
                }
                
                // 이미지 설명
                if (content.images != null && !content.images.isEmpty()) {
                    formattedText.append("🖼️ 이미지 설명:\n");
                    for (var img : content.images) {
                        if (img.description != null && !img.description.trim().isEmpty()) {
                            formattedText.append("   ").append(img.description).append("\n");
                        }
                    }
                    formattedText.append("\n");
                }
                
                // 표 설명
                if (content.tables != null && !content.tables.isEmpty()) {
                    formattedText.append("📊 표 설명:\n");
                    for (var table : content.tables) {
                        if (table.description != null && !table.description.trim().isEmpty()) {
                            formattedText.append("   ").append(table.description).append("\n");
                        }
                    }
                    formattedText.append("\n");
                }
                
                // 해설
                if (content.explanations != null && !content.explanations.trim().isEmpty()) {
                    formattedText.append("💡 해설:\n").append(content.explanations).append("\n\n");
                }
            }
            
            // AI 분석
            var aiAnalysis = question.aiAnalysis;
            if (aiAnalysis != null && 
                (!aiAnalysis.imageDescriptions.isEmpty() || !aiAnalysis.tableAnalysis.isEmpty())) {
                formattedText.append("🤖 AI 분석:\n");
                
                for (var imgDesc : aiAnalysis.imageDescriptions) {
                    if (imgDesc.getDescription() != null && !imgDesc.getDescription().trim().isEmpty()) {
                        formattedText.append("   [이미지] ").append(imgDesc.getDescription()).append("\n");
                    }
                }
                
                for (var tableDesc : aiAnalysis.tableAnalysis) {
                    if (tableDesc.getDescription() != null && !tableDesc.getDescription().trim().isEmpty()) {
                        formattedText.append("   [표] ").append(tableDesc.getDescription()).append("\n");
                    }
                }
                
                formattedText.append("\n");
            }
            
            // 문제 구분선
            if (i < questions.size() - 1) {
                formattedText.append("-".repeat(30)).append("\n\n");
            }
        }
        
        return formattedText.toString().trim();
    }
    
    /**
     * 구조화된 텍스트 생성 (Python의 create_structured_text와 동일)
     */
    private String createStructuredText(StructuredAnalysisResult structuredResult) {
        if (structuredResult == null) {
            return "";
        }
        
        StringBuilder formattedText = new StringBuilder();
        
        // 문서 정보 추가
        DocumentInfo docInfo = structuredResult.getDocumentInfo();
        if (docInfo != null) {
            formattedText.append("📋 문서 분석 결과\n");
            formattedText.append("총 문제 수: ").append(docInfo.getTotalQuestions()).append("개\n");
            formattedText.append("레이아웃 유형: ").append(docInfo.getLayoutType() != null ? docInfo.getLayoutType() : "미확인").append("\n\n");
            formattedText.append("=".repeat(50)).append("\n\n");
        }
        
        // 각 문제별 처리
        List<QuestionResult> questions = structuredResult.getQuestions();
        for (int i = 0; i < questions.size(); i++) {
            QuestionResult question = questions.get(i);
            String questionNum = question.getQuestionNumber() != null ? question.getQuestionNumber() : "문제" + (i + 1);
            String section = question.getSection();
            
            // 문제 제목
            formattedText.append("🔸 ").append(questionNum);
            if (section != null && !section.trim().isEmpty()) {
                formattedText.append(" (").append(section).append(")");
            }
            formattedText.append("\n\n");
            
            QuestionContent content = question.getQuestionContent();
            if (content != null) {
                // 지문
                if (content.getPassage() != null && !content.getPassage().trim().isEmpty()) {
                    formattedText.append("📖 지문:\n").append(content.getPassage()).append("\n\n");
                }
                
                // 주요 문제
                if (content.getMainQuestion() != null && !content.getMainQuestion().trim().isEmpty()) {
                    formattedText.append("❓ 문제:\n").append(content.getMainQuestion()).append("\n\n");
                }
                
                // 선택지
                if (content.getChoices() != null && !content.getChoices().isEmpty()) {
                    formattedText.append("📝 선택지:\n");
                    for (Choice choice : content.getChoices()) {
                        String choiceNum = choice.getChoiceNumber();
                        String choiceText = choice.getChoiceText();
                        if (choiceNum != null && choiceText != null) {
                            formattedText.append("   ").append(choiceNum).append(" ").append(choiceText).append("\n");
                        } else if (choiceText != null) {
                            formattedText.append("   • ").append(choiceText).append("\n");
                        }
                    }
                    formattedText.append("\n");
                }
                
                // 이미지 설명
                if (content.getImages() != null && !content.getImages().isEmpty()) {
                    formattedText.append("🖼️ 이미지 설명:\n");
                    for (ImageDescription img : content.getImages()) {
                        if (img.getDescription() != null && !img.getDescription().trim().isEmpty()) {
                            formattedText.append("   ").append(img.getDescription()).append("\n");
                        }
                    }
                    formattedText.append("\n");
                }
                
                // 표 설명
                if (content.getTables() != null && !content.getTables().isEmpty()) {
                    formattedText.append("📊 표 설명:\n");
                    for (TableDescription table : content.getTables()) {
                        if (table.getDescription() != null && !table.getDescription().trim().isEmpty()) {
                            formattedText.append("   ").append(table.getDescription()).append("\n");
                        }
                    }
                    formattedText.append("\n");
                }
                
                // 해설
                if (content.getExplanations() != null && !content.getExplanations().trim().isEmpty()) {
                    formattedText.append("💡 해설:\n").append(content.getExplanations()).append("\n\n");
                }
            }
            
            // AI 분석
            AIAnalysis aiAnalysis = question.getAiAnalysis();
            if (aiAnalysis != null && 
                (!aiAnalysis.getImageDescriptions().isEmpty() || !aiAnalysis.getTableAnalysis().isEmpty())) {
                formattedText.append("🤖 AI 분석:\n");
                
                for (AIDescriptionResult imgDesc : aiAnalysis.getImageDescriptions()) {
                    if (imgDesc.getDescription() != null && !imgDesc.getDescription().trim().isEmpty()) {
                        formattedText.append("   [이미지] ").append(imgDesc.getDescription()).append("\n");
                    }
                }
                
                for (AIDescriptionResult tableDesc : aiAnalysis.getTableAnalysis()) {
                    if (tableDesc.getDescription() != null && !tableDesc.getDescription().trim().isEmpty()) {
                        formattedText.append("   [표] ").append(tableDesc.getDescription()).append("\n");
                    }
                }
                
                formattedText.append("\n");
            }
            
            // 문제 구분선
            if (i < questions.size() - 1) {
                formattedText.append("-".repeat(30)).append("\n\n");
            }
        }
        
        return formattedText.toString().trim();
    }
    
    /**
     * 구조화된 결과를 JSON 파일로 저장 (CIM 버전)
     */
    private String saveStructuredResultAsJson(com.smarteye.service.StructuredJSONService.StructuredResult structuredResult, String timestamp) throws IOException {
        ensureStaticDirectoryExists();
        
        String filename = "structured_analysis_" + 
                         LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".json";
        Path jsonPath = Paths.get(staticDirectory, filename);
        
        objectMapper.writeValue(jsonPath.toFile(), structuredResult);
        
        return "/static/" + filename;
    }
    
    // 구조화된 분석 DB 저장은 CIMService에서 처리됨
    
    /**
     * 구조화된 분석 응답 구성 (CIM 버전)
     */
    private StructuredAnalysisResponse buildStructuredAnalysisResponseFromCIM(
            com.smarteye.service.StructuredJSONService.StructuredResult structuredResult,
            String structuredText,
            String jsonFilePath,
            Long timestamp) {
        
        StructuredAnalysisResponse response = new StructuredAnalysisResponse(
            true, 
            "구조화된 분석이 성공적으로 완료되었습니다."
        );
        
        // CIM 결과를 기존 StructuredAnalysisResult 형태로 변환해서 설정
        // 임시로 기존 응답 형태 유지를 위해 변환
        response.setStructuredText(structuredText);
        response.setJsonUrl(jsonFilePath);
        response.setTimestamp(timestamp);
        
        // 총 문제 수
        Integer totalQuestions = structuredResult.documentInfo != null ? 
            structuredResult.documentInfo.totalQuestions : 0;
        response.setTotalQuestions(totalQuestions);
        
        // 통계 생성
        AnalysisResponse.AnalysisStats stats = new AnalysisResponse.AnalysisStats(
            structuredResult.questions.size(), // 레이아웃 요소 수 = 문제 수
            structuredResult.questions.size(), // OCR 블록 수 = 문제 수
            0, // AI 설명 수 (향후 확장)
            Map.of("questions", structuredResult.questions.size())
        );
        response.setStats(stats);
        
        return response;
    }
    
    /**
     * PDF 분석에서 AI 신뢰도 계산
     * AIDescriptionService의 계산 로직과 유사한 방식 사용
     * @param description AI가 생성한 설명
     * @param className 요소 유형
     * @return 0.0~1.0 사이의 신뢰도 값
     */
    private double calculatePDFAIConfidence(String description, String className) {
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

        // 한글 포함 여부
        if (description.matches(".*[가-힣]+.*")) {
            confidence += 0.1;
        }

        // 0.0 ~ 1.0 범위로 제한
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    // ═══════════════════════════════════════════════════
    // 🔧 CIM API 헬퍼 메서드들
    // ═══════════════════════════════════════════════════

    /**
     * 구조화된 결과에서 레이아웃 정보 추출
     */
    private List<LayoutInfo> extractLayoutInfoFromStructured(
            com.smarteye.service.StructuredJSONService.StructuredResult structuredResult) {
        // 구조화된 결과에서 레이아웃 정보를 추출하는 로직
        // 실제 구현은 StructuredJSONService의 구조에 따라 조정 필요
        List<LayoutInfo> layoutInfo = new ArrayList<>();

        // TODO: 구조화된 결과에서 실제 레이아웃 정보 추출
        // 현재는 기본 레이아웃 정보 생성
        layoutInfo.add(new LayoutInfo(1, "document", 0.9f, new int[]{0, 0, 800, 600}, 800, 600, 480000));

        return layoutInfo;
    }

    /**
     * CIM 분석 통계 생성
     */
    private Map<String, Object> createCIMStats(Map<String, Object> cimResult, long processingTimeMs) {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 기본 통계
            stats.put("processing_time_ms", processingTimeMs);
            stats.put("analysis_timestamp", System.currentTimeMillis());
            stats.put("cim_data_size", cimResult.size());

            // CIM 데이터에서 추가 통계 추출
            if (cimResult.containsKey("questions")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> questions = (List<Map<String, Object>>) cimResult.get("questions");
                stats.put("total_questions", questions.size());
            }

            if (cimResult.containsKey("layout_info")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> layoutInfo = (List<Map<String, Object>>) cimResult.get("layout_info");
                stats.put("total_layout_elements", layoutInfo.size());
            }

            if (cimResult.containsKey("ocr_results")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> ocrResults = (List<Map<String, Object>>) cimResult.get("ocr_results");
                stats.put("total_ocr_blocks", ocrResults.size());

                // 총 문자 수 계산
                int totalCharacters = ocrResults.stream()
                    .mapToInt(ocr -> {
                        Object text = ocr.get("text");
                        return text != null ? text.toString().length() : 0;
                    })
                    .sum();
                stats.put("total_characters", totalCharacters);
            }

            if (cimResult.containsKey("ai_results")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> aiResults = (List<Map<String, Object>>) cimResult.get("ai_results");
                stats.put("total_ai_descriptions", aiResults.size());
            }

        } catch (Exception e) {
            logger.warn("CIM 통계 생성 중 오류: {}", e.getMessage());
            stats.put("error", "통계 생성 실패");
        }

        return stats;
    }

    /**
     * CIM 데이터에서 구조화된 텍스트 생성
     */
    private String createStructuredTextFromCIMData(Map<String, Object> cimData) {
        StringBuilder structuredText = new StringBuilder();

        try {
            // 문서 정보
            if (cimData.containsKey("document_info")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> docInfo = (Map<String, Object>) cimData.get("document_info");
                structuredText.append("📋 문서 분석 결과\n");
                structuredText.append("총 문제 수: ").append(docInfo.getOrDefault("total_questions", 0)).append("개\n");
                structuredText.append("레이아웃 유형: ").append(docInfo.getOrDefault("layout_type", "미확인")).append("\n\n");
                structuredText.append("=".repeat(50)).append("\n\n");
            }

            // 문제별 정보
            if (cimData.containsKey("questions")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> questions = (List<Map<String, Object>>) cimData.get("questions");

                for (int i = 0; i < questions.size(); i++) {
                    Map<String, Object> question = questions.get(i);
                    String questionNum = (String) question.getOrDefault("question_number", "문제" + (i + 1));

                    structuredText.append("🔸 ").append(questionNum).append("\n\n");

                    // 문제 내용 추가
                    if (question.containsKey("question_content")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> content = (Map<String, Object>) question.get("question_content");

                        // 주요 문제
                        if (content.containsKey("main_question")) {
                            String mainQuestion = (String) content.get("main_question");
                            if (mainQuestion != null && !mainQuestion.trim().isEmpty()) {
                                structuredText.append("❓ 문제:\n").append(mainQuestion).append("\n\n");
                            }
                        }

                        // 선택지
                        if (content.containsKey("choices")) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> choices = (List<Map<String, Object>>) content.get("choices");
                            if (!choices.isEmpty()) {
                                structuredText.append("📝 선택지:\n");
                                for (Map<String, Object> choice : choices) {
                                    String choiceText = (String) choice.get("choice_text");
                                    if (choiceText != null) {
                                        structuredText.append("   • ").append(choiceText).append("\n");
                                    }
                                }
                                structuredText.append("\n");
                            }
                        }
                    }

                    if (i < questions.size() - 1) {
                        structuredText.append("-".repeat(30)).append("\n\n");
                    }
                }
            }

        } catch (Exception e) {
            logger.warn("구조화된 텍스트 생성 중 오류: {}", e.getMessage());
            return "구조화된 텍스트 생성에 실패했습니다.";
        }

        return structuredText.toString().trim();
    }

    /**
     * CIM 데이터에서 원시 텍스트 추출
     */
    private String extractRawTextFromCIMData(Map<String, Object> cimData) {
        StringBuilder rawText = new StringBuilder();

        try {
            // OCR 결과에서 텍스트 추출
            if (cimData.containsKey("ocr_results")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> ocrResults = (List<Map<String, Object>>) cimData.get("ocr_results");

                for (Map<String, Object> ocr : ocrResults) {
                    String text = (String) ocr.get("text");
                    if (text != null && !text.trim().isEmpty()) {
                        rawText.append(text).append(" ");
                    }
                }
            }

        } catch (Exception e) {
            logger.warn("원시 텍스트 추출 중 오류: {}", e.getMessage());
            return "원시 텍스트 추출에 실패했습니다.";
        }

        return rawText.toString().trim();
    }

    /**
     * 섹션 필터링 적용
     */
    private String applySectionFilter(String text, String sectionFilter) {
        // 간단한 섹션 필터링 구현
        // 실제로는 더 정교한 필터링 로직이 필요할 수 있음

        if ("questions".equals(sectionFilter)) {
            // 문제 섹션만 추출
            String[] lines = text.split("\n");
            StringBuilder filteredText = new StringBuilder();

            for (String line : lines) {
                if (line.contains("🔸") || line.contains("❓") || line.contains("📝")) {
                    filteredText.append(line).append("\n");
                }
            }

            return filteredText.toString().trim();
        }

        return text; // 필터링하지 않음
    }

    /**
     * 텍스트 변환 통계 계산
     */
    private TextConversionResponse.TextConversionStats calculateTextStats(
            String convertedText,
            Map<String, Object> originalData,
            long processingTime) {

        int totalCharacters = convertedText.length();
        int totalWords = convertedText.split("\\s+").length;

        // 원본 데이터에서 문제 수 추출
        int totalQuestions = 0;
        if (originalData.containsKey("questions")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questions = (List<Map<String, Object>>) originalData.get("questions");
            totalQuestions = questions.size();
        }

        // 원본 데이터 크기 계산 (대략적)
        long originalDataSize = originalData.toString().length();

        return new TextConversionResponse.TextConversionStats(
            totalCharacters,
            totalWords,
            totalQuestions,
            processingTime,
            originalDataSize
        );
    }

    /**
     * 변환 메타데이터 생성
     */
    private Map<String, Object> createConversionMetadata(
            CIMToTextRequest request,
            Map<String, Object> originalData,
            TextConversionResponse.TextConversionStats stats) {

        Map<String, Object> metadata = new HashMap<>();

        metadata.put("conversion_format", request.getOutputFormat().toString());
        metadata.put("job_id", request.getJobId());
        metadata.put("section_filter", request.getSectionFilter());
        metadata.put("original_data_keys", new ArrayList<>(originalData.keySet()));
        metadata.put("conversion_timestamp", System.currentTimeMillis());
        metadata.put("compression_ratio", stats.getOriginalDataSize() > 0 ?
            (double) stats.getTotalCharacters() / stats.getOriginalDataSize() : 0.0);

        return metadata;
    }
}