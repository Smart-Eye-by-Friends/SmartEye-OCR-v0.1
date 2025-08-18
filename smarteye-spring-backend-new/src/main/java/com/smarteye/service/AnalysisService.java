package com.smarteye.service;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import com.smarteye.model.dto.AnalysisRequest;
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

    public AnalysisResponse analyzeDocument(AnalysisRequest request) {
        log.info("Starting document analysis for file: {}", request.getFile().getOriginalFilename());

        try {
            // 1. LAM (Layout Analysis Module) - 레이아웃 분석
            log.info("Step 1: Layout Analysis");
            var layoutResult = lamService.analyzeLayout(request.getFile());

            // 2. TSPM (Text & Semantic Processing Module) - 텍스트 추출 및 의미 분석
            log.info("Step 2: Text & Semantic Processing");
            var textResult = tspmService.processText(request.getFile(), request.getLanguage());

            // 3. CIM (Content Integration Module) - 결과 통합
            log.info("Step 3: Content Integration");
            var finalResult = cimService.integrateResults(layoutResult, textResult);

            return AnalysisResponse.success(finalResult);

        } catch (Exception e) {
            log.error("Error during document analysis", e);
            return AnalysisResponse.error("분석 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @Async
    public CompletableFuture<AnalysisResponse> analyzeDocumentAsync(AnalysisRequest request) {
        log.info("Starting async document analysis for file: {}", request.getFile().getOriginalFilename());
        
        return CompletableFuture.supplyAsync(() -> analyzeDocument(request));
    }
}
