package com.smarteye.service;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import com.smarteye.model.dto.AnalysisRequest;
import com.smarteye.model.dto.AnalysisResult;
import com.smarteye.model.response.AnalysisResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

    private final LAMService lamService;
    private final TSPMService tspmService;
    private final CIMService cimService;
    private final ProgressTrackingService progressTrackingService;

    // 기존 메서드 (호환성 유지)
    public AnalysisResponse analyzeDocument(AnalysisRequest request) {
        log.info("Starting document analysis for file: {}", request.getFile().getOriginalFilename());

        try {
            // 1. LAM (Layout Analysis Module) - 레이아웃 분석
            log.info("Step 1: Layout Analysis");
            var layoutResult = lamService.analyzeLayout(request.getFile());

            // 2. TSPM (Text & Semantic Processing Module) - 텍스트 추출 및 의미 분석
            log.info("Step 2: Text & Semantic Processing");
            // TODO: 새 파이프라인과 통합 필요
            // var textResult = tspmService.processTextAndSemantic(jobId);
            var textResult = "TSPM processing placeholder";

            // 3. CIM (Content Integration Module) - 결과 통합
            log.info("Step 3: Content Integration");
            var finalResult = cimService.integrateResults(layoutResult, textResult);

            return AnalysisResponse.success(finalResult);

        } catch (Exception e) {
            log.error("Error during document analysis", e);
            return AnalysisResponse.error("분석 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    // 새로운 React용 메서드
    public AnalysisResult analyzeDocumentNew(AnalysisRequest request) {
        log.info("Starting new document analysis for file: {}", request.getFile().getOriginalFilename());

        try {
            // TODO: 실제 분석 로직 구현
            return AnalysisResult.builder()
                    .analysisId(java.util.UUID.randomUUID().toString())
                    .fileName(request.getFile().getOriginalFilename())
                    .fileType(request.getFile().getContentType())
                    .fileSize(request.getFile().getSize())
                    .build();

        } catch (Exception e) {
            log.error("Error during new document analysis", e);
            throw new RuntimeException("분석 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @Async
    public CompletableFuture<AnalysisResponse> analyzeDocumentAsync(AnalysisRequest request) {
        log.info("Starting async document analysis for file: {}", request.getFile().getOriginalFilename());
        
        return CompletableFuture.supplyAsync(() -> analyzeDocument(request));
    }

    @Async
    public CompletableFuture<AnalysisResult> analyzeDocumentAsyncNew(AnalysisRequest request, String taskId) {
        log.info("Starting async new document analysis for file: {} with taskId: {}", 
                request.getFile().getOriginalFilename(), taskId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 진행 상황 업데이트
                progressTrackingService.updateProgress(taskId, 10, "분석 시작");
                
                AnalysisResult result = analyzeDocumentNew(request);
                
                progressTrackingService.updateProgress(taskId, 100, "분석 완료");
                progressTrackingService.completeTask(taskId, result);
                
                return result;
                
            } catch (Exception e) {
                progressTrackingService.failTask(taskId, e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
}
