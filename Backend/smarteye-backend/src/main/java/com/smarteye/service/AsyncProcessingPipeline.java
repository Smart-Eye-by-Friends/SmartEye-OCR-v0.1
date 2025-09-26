package com.smarteye.service;

import com.smarteye.presentation.dto.*;
import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.entity.AnalysisJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 비동기 처리 파이프라인 서비스
 *
 * Phase 2 성능 최적화: CompletableFuture 기반 병렬 처리
 *
 * 처리 플로우:
 * 1. LAM 분석 (순차)
 * 2. OCR 처리 + OpenAI 설명 생성 (병렬)
 * 3. 결과 취합 및 최종 분석
 *
 * 성능 예상 개선 효과:
 * - 기존: LAM → OCR → AI (순차) = 100% 시간
 * - 개선: LAM → (OCR + AI) 병렬 = 60-70% 시간 단축
 */
@Service
public class AsyncProcessingPipeline {

    private static final Logger logger = LoggerFactory.getLogger(AsyncProcessingPipeline.class);

    @Autowired
    private LAMServiceClient lamServiceClient;

    @Autowired
    private OCRService ocrService;

    @Autowired
    private AIDescriptionService aiDescriptionService;

    @Autowired
    private UnifiedAnalysisEngine unifiedAnalysisEngine;

    @Autowired
    private DocumentAnalysisDataService documentAnalysisDataServiceOptimized;

    // 비동기 처리를 위한 스레드 풀
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    /**
     * 메인 비동기 분석 파이프라인
     *
     * @param analysisJob 분석 작업 정보
     * @param imageFile 분석할 이미지
     * @param modelType LAM 모델 타입
     * @param apiKey OpenAI API 키
     * @return 완전한 분석 결과
     */
    @Async
    public CompletableFuture<PipelineResult> processAsync(
            AnalysisJob analysisJob,
            BufferedImage imageFile,
            String modelType,
            String apiKey) {

        String jobId = analysisJob.getJobId();
        logger.info("🚀 비동기 파이프라인 시작 - JobID: {}, 모델: {}", jobId, modelType);

        long totalStartTime = System.currentTimeMillis();

        return CompletableFuture
            .supplyAsync(() -> performLAMAnalysis(jobId, imageFile, modelType), executorService)
            .thenCompose(lamResult -> performParallelProcessing(jobId, imageFile, lamResult, apiKey))
            .thenCompose(parallelResult -> performUnifiedAnalysis(jobId, parallelResult))
            .thenCompose(analysisResult -> saveResults(jobId, analysisResult))
            .handle((result, throwable) -> {
                long totalTime = System.currentTimeMillis() - totalStartTime;

                if (throwable != null) {
                    logger.error("❌ 비동기 파이프라인 실패 - JobID: {} ({}ms)", jobId, totalTime, throwable);
                    return new PipelineResult(
                        false,
                        "비동기 처리 중 오류 발생: " + throwable.getMessage(),
                        null,
                        null,
                        null,
                        null,
                        totalTime
                    );
                } else {
                    logger.info("✅ 비동기 파이프라인 완료 - JobID: {} ({}ms)", jobId, totalTime);
                    result.setTotalProcessingTimeMs(totalTime);
                    return result;
                }
            });
    }

    /**
     * 1단계: LAM 분석 (순차 처리)
     */
    private LAMAnalysisResult performLAMAnalysis(String jobId, BufferedImage imageFile, String modelType) {
        logger.info("🔍 LAM 분석 시작 - JobID: {}", jobId);
        long startTime = System.currentTimeMillis();

        try {
            LayoutAnalysisResult lamResult = lamServiceClient.analyzeLayout(imageFile, modelType).join();

            long lamTime = System.currentTimeMillis() - startTime;
            logger.info("✅ LAM 분석 완료 - JobID: {} ({}ms), 감지된 요소: {}개",
                       jobId, lamTime, lamResult.getLayoutInfo().size());

            return new LAMAnalysisResult(
                true,
                "LAM 분석 완료",
                lamResult.getLayoutInfo(),
                null, // layoutImageBase64는 현재 LayoutAnalysisResult에 없음
                lamTime
            );

        } catch (Exception e) {
            logger.error("❌ LAM 분석 실패 - JobID: {}", jobId, e);
            return new LAMAnalysisResult(
                false,
                "LAM 분석 실패: " + e.getMessage(),
                null,
                null,
                System.currentTimeMillis() - startTime
            );
        }
    }

    /**
     * 2단계: OCR + AI 병렬 처리
     */
    private CompletableFuture<ParallelProcessingResult> performParallelProcessing(
            String jobId,
            BufferedImage imageFile,
            LAMAnalysisResult lamResult,
            String apiKey) {

        if (!lamResult.isSuccess()) {
            return CompletableFuture.completedFuture(
                new ParallelProcessingResult(false, lamResult.getMessage(), null, null, 0, 0)
            );
        }

        logger.info("🔄 병렬 처리 시작 - JobID: {} (OCR + AI 동시 실행)", jobId);
        long parallelStartTime = System.currentTimeMillis();

        // OCR 처리 (비동기)
        CompletableFuture<OCRProcessingResult> ocrFuture = CompletableFuture
            .supplyAsync(() -> performOCRProcessing(jobId, imageFile, lamResult.getLayoutElements()), executorService);

        // AI 설명 생성 (비동기)
        CompletableFuture<AIProcessingResult> aiFuture = CompletableFuture
            .supplyAsync(() -> performAIProcessing(jobId, imageFile, lamResult.getLayoutElements(), apiKey), executorService);

        // 두 작업이 모두 완료되면 결과 취합
        return ocrFuture.thenCombine(aiFuture, (ocrResult, aiResult) -> {
            long parallelTime = System.currentTimeMillis() - parallelStartTime;

            logger.info("✅ 병렬 처리 완료 - JobID: {} ({}ms)", jobId, parallelTime);
            logger.info("  ├─ OCR: {}개 ({}ms)",
                       ocrResult.getOcrResults() != null ? ocrResult.getOcrResults().size() : 0,
                       ocrResult.getProcessingTimeMs());
            logger.info("  └─ AI: {}개 ({}ms)",
                       aiResult.getAiResults() != null ? aiResult.getAiResults().size() : 0,
                       aiResult.getProcessingTimeMs());

            boolean success = ocrResult.isSuccess() && aiResult.isSuccess();
            String message = success ? "병렬 처리 완료" : "병렬 처리 중 일부 실패";

            return new ParallelProcessingResult(
                success,
                message,
                ocrResult.getOcrResults(),
                aiResult.getAiResults(),
                ocrResult.getProcessingTimeMs(),
                aiResult.getProcessingTimeMs()
            );
        });
    }

    /**
     * OCR 처리 (비동기 실행)
     */
    private OCRProcessingResult performOCRProcessing(String jobId, BufferedImage imageFile, List<LayoutInfo> layoutElements) {
        logger.debug("📝 OCR 처리 시작 - JobID: {}", jobId);
        long startTime = System.currentTimeMillis();

        try {
            List<OCRResult> ocrResults = ocrService.performOCR(imageFile, layoutElements);

            long ocrTime = System.currentTimeMillis() - startTime;
            logger.debug("✅ OCR 처리 완료 - JobID: {} ({}ms)", jobId, ocrTime);

            return new OCRProcessingResult(
                true,
                "OCR 처리 완료",
                ocrResults,
                ocrTime
            );

        } catch (Exception e) {
            logger.error("❌ OCR 처리 실패 - JobID: {}", jobId, e);
            return new OCRProcessingResult(
                false,
                "OCR 처리 실패: " + e.getMessage(),
                null,
                System.currentTimeMillis() - startTime
            );
        }
    }

    /**
     * AI 설명 생성 (비동기 실행)
     */
    private AIProcessingResult performAIProcessing(String jobId, BufferedImage imageFile, List<LayoutInfo> layoutElements, String apiKey) {
        logger.debug("🤖 AI 처리 시작 - JobID: {}", jobId);
        long startTime = System.currentTimeMillis();

        try {
            List<AIDescriptionResult> aiResults = aiDescriptionService.generateDescriptions(imageFile, layoutElements, apiKey).join();

            long aiTime = System.currentTimeMillis() - startTime;
            logger.debug("✅ AI 처리 완료 - JobID: {} ({}ms)", jobId, aiTime);

            return new AIProcessingResult(
                true,
                "AI 처리 완료",
                aiResults,
                aiTime
            );

        } catch (Exception e) {
            logger.error("❌ AI 처리 실패 - JobID: {}", jobId, e);
            return new AIProcessingResult(
                false,
                "AI 처리 실패: " + e.getMessage(),
                null,
                System.currentTimeMillis() - startTime
            );
        }
    }

    /**
     * 3단계: 통합 분석 수행
     */
    private CompletableFuture<UnifiedAnalysisEngine.UnifiedAnalysisResult> performUnifiedAnalysis(
            String jobId,
            ParallelProcessingResult parallelResult) {

        if (!parallelResult.isSuccess()) {
            return CompletableFuture.completedFuture(
                new UnifiedAnalysisEngine.UnifiedAnalysisResult(
                    false, parallelResult.getMessage(), null, null, null, null, 0
                )
            );
        }

        logger.info("🧩 통합 분석 시작 - JobID: {}", jobId);

        return CompletableFuture.supplyAsync(() -> {
            // LAM 결과를 다시 가져와야 함 (개선 필요)
            // 실제 구현에서는 parallelResult에 layoutElements를 포함해야 함
            return unifiedAnalysisEngine.performUnifiedAnalysis(
                null, // layoutElements (실제로는 parallelResult에서 가져와야 함)
                parallelResult.getOcrResults(),
                parallelResult.getAiResults()
            );
        }, executorService);
    }

    /**
     * 4단계: 결과 저장
     */
    private CompletableFuture<PipelineResult> saveResults(
            String jobId,
            UnifiedAnalysisEngine.UnifiedAnalysisResult analysisResult) {

        if (!analysisResult.isSuccess()) {
            return CompletableFuture.completedFuture(
                new PipelineResult(
                    false, analysisResult.getMessage(), null, null, null, null, 0
                )
            );
        }

        logger.info("💾 결과 저장 시작 - JobID: {}", jobId);

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 비동기 DB 저장 (기존 OptimizedService 사용)
                documentAnalysisDataServiceOptimized.saveAnalysisResultsBatch(
                    jobId,
                    null, // layoutInfo (실제로는 analysisResult에서 추출)
                    analysisResult.getClassifiedElements().get("ocr_results") != null ?
                        null : null, // OCR 결과 변환 필요
                    null, // AI 결과 변환 필요
                    analysisResult.getCimData(),
                    "포맷된 텍스트", // 실제로는 생성 필요
                    analysisResult.getProcessingTimeMs()
                ).join(); // 동기적으로 대기

                logger.info("✅ 결과 저장 완료 - JobID: {}", jobId);

                return new PipelineResult(
                    true,
                    "비동기 파이프라인 처리 완료",
                    null, // layoutElements
                    null, // ocrResults
                    null, // aiResults
                    analysisResult.getCimData(),
                    0 // 총 시간은 상위에서 설정
                );

            } catch (Exception e) {
                logger.error("❌ 결과 저장 실패 - JobID: {}", jobId, e);
                return new PipelineResult(
                    false,
                    "결과 저장 실패: " + e.getMessage(),
                    null, null, null, null, 0
                );
            }
        }, executorService);
    }

    // ============================================================================
    // 내부 결과 클래스들
    // ============================================================================

    /**
     * LAM 분석 결과
     */
    public static class LAMAnalysisResult {
        private boolean success;
        private String message;
        private List<LayoutInfo> layoutElements;
        private String layoutImageBase64;
        private long processingTimeMs;

        public LAMAnalysisResult(boolean success, String message, List<LayoutInfo> layoutElements,
                               String layoutImageBase64, long processingTimeMs) {
            this.success = success;
            this.message = message;
            this.layoutElements = layoutElements;
            this.layoutImageBase64 = layoutImageBase64;
            this.processingTimeMs = processingTimeMs;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public List<LayoutInfo> getLayoutElements() { return layoutElements; }
        public String getLayoutImageBase64() { return layoutImageBase64; }
        public long getProcessingTimeMs() { return processingTimeMs; }
    }

    /**
     * 병렬 처리 결과
     */
    public static class ParallelProcessingResult {
        private boolean success;
        private String message;
        private List<OCRResult> ocrResults;
        private List<AIDescriptionResult> aiResults;
        private long ocrProcessingTimeMs;
        private long aiProcessingTimeMs;

        public ParallelProcessingResult(boolean success, String message, List<OCRResult> ocrResults,
                                      List<AIDescriptionResult> aiResults, long ocrProcessingTimeMs, long aiProcessingTimeMs) {
            this.success = success;
            this.message = message;
            this.ocrResults = ocrResults;
            this.aiResults = aiResults;
            this.ocrProcessingTimeMs = ocrProcessingTimeMs;
            this.aiProcessingTimeMs = aiProcessingTimeMs;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public List<OCRResult> getOcrResults() { return ocrResults; }
        public List<AIDescriptionResult> getAiResults() { return aiResults; }
        public long getOcrProcessingTimeMs() { return ocrProcessingTimeMs; }
        public long getAiProcessingTimeMs() { return aiProcessingTimeMs; }
    }

    /**
     * OCR 처리 결과
     */
    public static class OCRProcessingResult {
        private boolean success;
        private String message;
        private List<OCRResult> ocrResults;
        private long processingTimeMs;

        public OCRProcessingResult(boolean success, String message, List<OCRResult> ocrResults, long processingTimeMs) {
            this.success = success;
            this.message = message;
            this.ocrResults = ocrResults;
            this.processingTimeMs = processingTimeMs;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public List<OCRResult> getOcrResults() { return ocrResults; }
        public long getProcessingTimeMs() { return processingTimeMs; }
    }

    /**
     * AI 처리 결과
     */
    public static class AIProcessingResult {
        private boolean success;
        private String message;
        private List<AIDescriptionResult> aiResults;
        private long processingTimeMs;

        public AIProcessingResult(boolean success, String message, List<AIDescriptionResult> aiResults, long processingTimeMs) {
            this.success = success;
            this.message = message;
            this.aiResults = aiResults;
            this.processingTimeMs = processingTimeMs;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public List<AIDescriptionResult> getAiResults() { return aiResults; }
        public long getProcessingTimeMs() { return processingTimeMs; }
    }

    /**
     * 최종 파이프라인 결과
     */
    public static class PipelineResult {
        private boolean success;
        private String message;
        private List<LayoutInfo> layoutElements;
        private List<OCRResult> ocrResults;
        private List<AIDescriptionResult> aiResults;
        private Map<String, Object> cimData;
        private long totalProcessingTimeMs;

        public PipelineResult(boolean success, String message, List<LayoutInfo> layoutElements,
                             List<OCRResult> ocrResults, List<AIDescriptionResult> aiResults,
                             Map<String, Object> cimData, long totalProcessingTimeMs) {
            this.success = success;
            this.message = message;
            this.layoutElements = layoutElements;
            this.ocrResults = ocrResults;
            this.aiResults = aiResults;
            this.cimData = cimData;
            this.totalProcessingTimeMs = totalProcessingTimeMs;
        }

        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<LayoutInfo> getLayoutElements() { return layoutElements; }
        public void setLayoutElements(List<LayoutInfo> layoutElements) { this.layoutElements = layoutElements; }
        public List<OCRResult> getOcrResults() { return ocrResults; }
        public void setOcrResults(List<OCRResult> ocrResults) { this.ocrResults = ocrResults; }
        public List<AIDescriptionResult> getAiResults() { return aiResults; }
        public void setAiResults(List<AIDescriptionResult> aiResults) { this.aiResults = aiResults; }
        public Map<String, Object> getCimData() { return cimData; }
        public void setCimData(Map<String, Object> cimData) { this.cimData = cimData; }
        public long getTotalProcessingTimeMs() { return totalProcessingTimeMs; }
        public void setTotalProcessingTimeMs(long totalProcessingTimeMs) { this.totalProcessingTimeMs = totalProcessingTimeMs; }
    }
}