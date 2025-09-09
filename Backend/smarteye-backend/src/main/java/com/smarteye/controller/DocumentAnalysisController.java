package com.smarteye.controller;

import com.smarteye.dto.AnalysisResponse;
import com.smarteye.service.ImageAnalysisService;
import com.smarteye.service.PDFAnalysisService;
import com.smarteye.service.MultipleImageAnalysisService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;

/**
 * 문서 분석 컨트롤러 - 메인 분석 API
 * Python api_server.py의 analyze_worksheet, analyze-pdf 엔드포인트 변환
 * 
 * 리팩토링: 비즈니스 로직을 서비스 레이어로 분리하여 컨트롤러를 슬림화
 * - ImageAnalysisService: 단일 이미지 분석
 * - PDFAnalysisService: PDF 문서 분석
 * - MultipleImageAnalysisService: 다중 이미지 분석
 * 
 * 리팩토링 결과: 1,185라인 → 200라인 (83% 감소)
 */
@RestController
@RequestMapping("/api/document")
@Validated
@Tag(name = "Document Analysis", description = "문서 분석 및 OCR 처리 API")
public class DocumentAnalysisController {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentAnalysisController.class);
    
    @Autowired
    private ImageAnalysisService imageAnalysisService;
    
    @Autowired
    private PDFAnalysisService pdfAnalysisService;
    
    @Autowired
    private MultipleImageAnalysisService multipleImageAnalysisService;
    
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
        
        return imageAnalysisService.analyzeImage(image, modelChoice, apiKey)
            .thenApply(result -> {
                if (result.isSuccess()) {
                    logger.info("이미지 분석 완료 - 작업 ID: {}", result.getResponse().getJobId());
                    return ResponseEntity.ok(result.getResponse());
                } else {
                    logger.error("이미지 분석 실패: {}", result.getErrorMessage());
                    return ResponseEntity.badRequest()
                        .body(new AnalysisResponse(false, result.getErrorMessage()));
                }
            });
    }
    
    /**
     * PDF 분석
     * Python api_server.py의 /analyze-pdf 엔드포인트와 동일한 기능
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
        
        return pdfAnalysisService.analyzePDF(pdfFile, modelChoice, apiKey)
            .thenApply(result -> {
                if (result.isSuccess()) {
                    logger.info("PDF 분석 완료 - 책 ID: {}", result.getResponse().getJobId());
                    return ResponseEntity.ok(result.getResponse());
                } else {
                    logger.error("PDF 분석 실패: {}", result.getErrorMessage());
                    return ResponseEntity.badRequest()
                        .body(new AnalysisResponse(false, result.getErrorMessage()));
                }
            });
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
        
        return multipleImageAnalysisService.analyzeMultipleImages(images, modelChoice, apiKey, bookTitle)
            .thenApply(result -> {
                if (result.isSuccess()) {
                    logger.info("여러 이미지 분석 완료 - 책 ID: {}", result.getResponse().getJobId());
                    return ResponseEntity.ok(result.getResponse());
                } else {
                    logger.error("여러 이미지 분석 실패: {}", result.getErrorMessage());
                    return ResponseEntity.badRequest()
                        .body(new AnalysisResponse(false, result.getErrorMessage()));
                }
            });
    }
    
    // 모든 helper 메서드들과 비즈니스 로직은 각각의 서비스로 이전되었습니다.
    // - ImageAnalysisService: 단일 이미지 분석 로직
    // - PDFAnalysisService: PDF 분석 및 Book 관리 로직  
    // - MultipleImageAnalysisService: 다중 이미지 분석 로직
    // - AnalysisResponseBuilder: 응답 생성 및 통합 로직
    
    // /analyze-structured 엔드포인트는 제거됨 - 구조화 분석은 Java CIMService와 CIMController로 이식됨
}