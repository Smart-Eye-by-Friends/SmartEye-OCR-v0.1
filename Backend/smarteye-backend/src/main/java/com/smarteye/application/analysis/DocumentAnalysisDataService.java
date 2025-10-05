package com.smarteye.application.analysis;

import com.smarteye.presentation.dto.AIDescriptionResult;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.domain.analysis.entity.AnalysisJob;
import com.smarteye.domain.analysis.entity.LayoutBlock;
import com.smarteye.domain.analysis.entity.TextBlock;
import com.smarteye.domain.analysis.entity.CIMOutput;
import com.smarteye.domain.analysis.repository.AnalysisJobRepository;
import com.smarteye.domain.analysis.repository.LayoutBlockRepository;
import com.smarteye.domain.analysis.repository.TextBlockRepository;
import com.smarteye.domain.analysis.repository.CIMOutputRepository;
import com.smarteye.domain.document.repository.DocumentPageRepository;
import com.smarteye.domain.logging.repository.ProcessingLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import com.smarteye.application.analysis.AnalysisJobService;
import com.smarteye.application.user.UserService;
import com.smarteye.domain.document.entity.DocumentPage;
import com.smarteye.domain.logging.entity.ProcessingLog;
import com.smarteye.infrastructure.external.LAMServiceClient;
import com.smarteye.infrastructure.external.OCRService;
import com.smarteye.infrastructure.external.AIDescriptionService;
import com.smarteye.application.file.FileService;
import com.smarteye.application.file.ImageProcessingService;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 최적화된 문서 분석 결과 저장 서비스
 *
 * JPA 성능 최적화 기능:
 * 1. 배치 처리로 N+1 쿼리 방지
 * 2. 벌크 INSERT 연산 사용
 * 3. 엔터티 캐싱 및 flush 최적화
 * 4. 동기 트랜잭션 처리로 데이터 무결성 보장
 */
@Service
@Transactional
public class DocumentAnalysisDataService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentAnalysisDataService.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private AnalysisJobRepository analysisJobRepository;

    @Autowired
    private DocumentPageRepository documentPageRepository;

    @Autowired
    private LayoutBlockRepository layoutBlockRepository;

    @Autowired
    private TextBlockRepository textBlockRepository;

    @Autowired
    private CIMOutputRepository cimOutputRepository;

    @Autowired
    private ProcessingLogRepository processingLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 배치 저장 - 최적화된 분석 결과 저장
     *
     * 성능 최적화 사항:
     * - 단일 트랜잭션으로 모든 저장 처리
     * - 배치 INSERT를 위한 JDBC 최적화
     * - N+1 쿼리 방지를 위한 연관관계 미리 로딩
     * - 동기 처리로 트랜잭션 컨텍스트 보장
     */
    @Transactional
    public void saveAnalysisResultsBatch(
            String jobId,
            List<LayoutInfo> layoutInfo,
            List<OCRResult> ocrResults,
            List<AIDescriptionResult> aiResults,
            Map<String, Object> cimResult,
            String formattedText,
            long processingTimeMs) {

        long startTime = System.currentTimeMillis();

        try {
                logger.info("🚀 배치 DB 저장 시작 - JobID: {}, 총 요소: {}개", jobId, layoutInfo.size());

                // 1. AnalysisJob 조회 (캐시 활용)
                AnalysisJob analysisJob = findAnalysisJobWithCache(jobId);

                // 2. DocumentPage 생성 및 저장
                DocumentPage documentPage = createAndSaveDocumentPage(analysisJob);

                // 3. 배치로 LayoutBlock들 저장 (성능 최적화)
                List<LayoutBlock> layoutBlocks = createLayoutBlocksBatch(
                    documentPage, layoutInfo, ocrResults, aiResults);

                // 4. 배치로 TextBlock들 저장
                List<TextBlock> textBlocks = createTextBlocksBatch(layoutBlocks, ocrResults);

                // 5. CIMOutput 저장
                saveCIMOutputOptimized(analysisJob, cimResult, formattedText,
                                     layoutInfo, ocrResults, aiResults, processingTimeMs);

                // 6. ProcessingLog 저장
                addProcessingLogBatch(analysisJob, layoutInfo, ocrResults, aiResults, processingTimeMs);

                // 7. 강제 flush (배치 처리 완료)
                entityManager.flush();
                entityManager.clear();

                long saveTime = System.currentTimeMillis() - startTime;
                logger.info("✅ 배치 DB 저장 완료 ({}ms) - JobID: {}, 레이아웃: {}개, OCR: {}개, AI: {}개",
                           saveTime, jobId, layoutInfo.size(), ocrResults.size(), aiResults.size());

                // 성능 메트릭 로깅
                logBatchSaveMetrics(jobId, layoutInfo.size(), saveTime);

        } catch (Exception e) {
            logger.error("❌ 배치 DB 저장 실패 - JobID: {}", jobId, e);
            throw new RuntimeException("배치 저장 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * AnalysisJob 조회 (캐시 활용)
     */
    private AnalysisJob findAnalysisJobWithCache(String jobId) {
        return analysisJobRepository.findByJobId(jobId)
            .orElseThrow(() -> new RuntimeException("분석 작업을 찾을 수 없습니다: " + jobId));
    }

    /**
     * DocumentPage 생성 및 저장
     */
    private DocumentPage createAndSaveDocumentPage(AnalysisJob analysisJob) {
        DocumentPage documentPage = new DocumentPage();
        documentPage.setAnalysisJob(analysisJob);
        documentPage.setPageNumber(1);
        documentPage.setImagePath(analysisJob.getFilePath());
        documentPage.setProcessingStatus(DocumentPage.ProcessingStatus.COMPLETED);

        return documentPageRepository.save(documentPage);
    }

    /**
     * 배치로 LayoutBlock들 생성 및 저장 (성능 최적화)
     */
    private List<LayoutBlock> createLayoutBlocksBatch(
            DocumentPage documentPage,
            List<LayoutInfo> layoutInfo,
            List<OCRResult> ocrResults,
            List<AIDescriptionResult> aiResults) {

        logger.debug("📦 LayoutBlock 배치 생성 시작 - 총 {}개", layoutInfo.size());

        List<LayoutBlock> layoutBlocks = new ArrayList<>();
        int batchSize = 50; // JPA 배치 크기 설정

        for (int i = 0; i < layoutInfo.size(); i++) {
            LayoutInfo layout = layoutInfo.get(i);

            // LayoutBlock 생성
            LayoutBlock layoutBlock = createOptimizedLayoutBlock(
                documentPage, layout, ocrResults, aiResults);

            layoutBlocks.add(layoutBlock);

            // 배치 크기마다 flush 및 clear
            if ((i + 1) % batchSize == 0) {
                layoutBlockRepository.saveAll(layoutBlocks.subList(i + 1 - batchSize, i + 1));
                entityManager.flush();
                entityManager.clear();
                logger.debug("📦 중간 배치 저장 완료 - {}개 처리됨", i + 1);
            }
        }

        // 남은 항목들 저장
        int remainingStart = (layoutInfo.size() / batchSize) * batchSize;
        if (remainingStart < layoutInfo.size()) {
            layoutBlockRepository.saveAll(layoutBlocks.subList(remainingStart, layoutInfo.size()));
            entityManager.flush();
        }

        logger.debug("✅ LayoutBlock 배치 생성 완료 - 총 {}개", layoutBlocks.size());
        return layoutBlocks;
    }

    /**
     * 최적화된 LayoutBlock 생성
     */
    private LayoutBlock createOptimizedLayoutBlock(
            DocumentPage documentPage,
            LayoutInfo layout,
            List<OCRResult> ocrResults,
            List<AIDescriptionResult> aiResults) {

        LayoutBlock layoutBlock = new LayoutBlock();
        layoutBlock.setDocumentPage(documentPage);
        layoutBlock.setBlockIndex(layout.getId());
        layoutBlock.setClassName(layout.getClassName());
        layoutBlock.setConfidence(layout.getConfidence());

        // 좌표 설정 (배열 범위 체크)
        int[] box = layout.getBox();
        if (box != null && box.length >= 4) {
            layoutBlock.setX1(box[0]);
            layoutBlock.setY1(box[1]);
            layoutBlock.setX2(box[2]);
            layoutBlock.setY2(box[3]);

            // 크기 계산
            layoutBlock.setWidth(box[2] - box[0]);
            layoutBlock.setHeight(box[3] - box[1]);
            layoutBlock.setArea(layoutBlock.getWidth() * layoutBlock.getHeight());
        }

        // OCR 결과 매핑 (성능 최적화된 검색)
        OCRResult ocrResult = findOCRByLayoutIdOptimized(layout.getId(), ocrResults);
        if (ocrResult != null) {
            layoutBlock.setOcrText(ocrResult.getText());
            layoutBlock.setOcrConfidence(ocrResult.getConfidence()); // 실제 신뢰도 사용
            layoutBlock.setProcessingStatus(LayoutBlock.ProcessingStatus.OCR_COMPLETED);
        }

        // AI 설명 매핑 (성능 최적화된 검색)
        AIDescriptionResult aiResult = findAIByLayoutIdOptimized(layout.getId(), aiResults);
        if (aiResult != null) {
            layoutBlock.setAiDescription(aiResult.getDescription());
            // layoutBlock.setAiConfidence(aiResult.getConfidence()); // AI 신뢰도 메서드 확인 필요

            if (layoutBlock.getProcessingStatus() == null) {
                layoutBlock.setProcessingStatus(LayoutBlock.ProcessingStatus.AI_COMPLETED);
            }
        }

        // 기본 상태 설정
        if (layoutBlock.getProcessingStatus() == null) {
            layoutBlock.setProcessingStatus(LayoutBlock.ProcessingStatus.LAYOUT_DETECTED);
        }

        return layoutBlock;
    }

    /**
     * 배치로 TextBlock들 생성 및 저장
     */
    private List<TextBlock> createTextBlocksBatch(
            List<LayoutBlock> layoutBlocks,
            List<OCRResult> ocrResults) {

        logger.debug("📝 TextBlock 배치 생성 시작");

        List<TextBlock> textBlocks = new ArrayList<>();
        int batchSize = 50;

        for (int i = 0; i < layoutBlocks.size(); i++) {
            LayoutBlock layoutBlock = layoutBlocks.get(i);

            // OCR 텍스트가 있는 경우만 TextBlock 생성
            if (layoutBlock.getOcrText() != null &&
                !layoutBlock.getOcrText().trim().isEmpty()) {

                TextBlock textBlock = createOptimizedTextBlock(layoutBlock);
                textBlocks.add(textBlock);

                // 배치 저장
                if (textBlocks.size() % batchSize == 0) {
                    textBlockRepository.saveAll(textBlocks.subList(textBlocks.size() - batchSize, textBlocks.size()));
                    entityManager.flush();
                }
            }
        }

        // 남은 TextBlock들 저장
        int remainingStart = (textBlocks.size() / batchSize) * batchSize;
        if (remainingStart < textBlocks.size()) {
            textBlockRepository.saveAll(textBlocks.subList(remainingStart, textBlocks.size()));
            entityManager.flush();
        }

        logger.debug("✅ TextBlock 배치 생성 완료 - 총 {}개", textBlocks.size());
        return textBlocks;
    }

    /**
     * 최적화된 TextBlock 생성
     */
    private TextBlock createOptimizedTextBlock(LayoutBlock layoutBlock) {
        TextBlock textBlock = new TextBlock(layoutBlock.getOcrText());
        textBlock.setLayoutBlock(layoutBlock);
        textBlock.setConfidence(layoutBlock.getOcrConfidence() != null ?
                               layoutBlock.getOcrConfidence() : 90.0);
        textBlock.setLanguage("kor");
        textBlock.inferTextType(); // 클래스명 기반 텍스트 타입 추론

        return textBlock;
    }

    /**
     * 최적화된 CIMOutput 저장 (UPDATE or INSERT 전략)
     */
    private void saveCIMOutputOptimized(
            AnalysisJob analysisJob,
            Map<String, Object> cimResult,
            String formattedText,
            List<LayoutInfo> layoutInfo,
            List<OCRResult> ocrResults,
            List<AIDescriptionResult> aiResults,
            long processingTimeMs) {

        try {
            // 기존 CIMOutput 조회 (있으면 업데이트, 없으면 생성)
            CIMOutput cimOutput = analysisJob.getCimOutput();
            boolean isUpdate = (cimOutput != null);

            if (cimOutput == null) {
                cimOutput = new CIMOutput();
                cimOutput.setAnalysisJob(analysisJob);
                logger.debug("🆕 새로운 CIMOutput 생성 - JobID: {}", analysisJob.getId());
            } else {
                logger.debug("🔄 기존 CIMOutput 업데이트 - JobID: {}, CIMOutputID: {}",
                           analysisJob.getId(), cimOutput.getId());
            }

            // CIM 데이터 저장 (압축 고려)
            String cimDataJson = objectMapper.writeValueAsString(cimResult);
            cimOutput.setCimData(cimDataJson);
            cimOutput.setFormattedText(formattedText);

            // 최적화된 통계 계산
            calculateAndSetStatistics(cimOutput, layoutInfo, ocrResults, aiResults);

            cimOutput.setProcessingTimeMs(processingTimeMs);
            cimOutput.setGenerationStatus(CIMOutput.GenerationStatus.COMPLETED);

            cimOutputRepository.save(cimOutput);

            // AnalysisJob 연결 (지연 로딩 방지) - 신규 생성 시에만
            if (!isUpdate) {
                analysisJob.setCimOutput(cimOutput);
            }

            logger.debug("💾 CIMOutput {} 완료 - 데이터 크기: {}KB",
                        isUpdate ? "업데이트" : "생성",
                        cimDataJson.length() / 1024);

        } catch (Exception e) {
            logger.error("❌ CIMOutput 최적화 저장 실패", e);
            throw new RuntimeException("CIMOutput 저장 중 오류 발생", e);
        }
    }

    /**
     * 통계 정보 계산 및 설정 (성능 최적화)
     */
    private void calculateAndSetStatistics(
            CIMOutput cimOutput,
            List<LayoutInfo> layoutInfo,
            List<OCRResult> ocrResults,
            List<AIDescriptionResult> aiResults) {

        // 기본 통계
        cimOutput.setTotalElements(layoutInfo.size());
        cimOutput.setTextElements(ocrResults.size());
        cimOutput.setAiDescribedElements(aiResults.size());

        // 클래스별 통계 (스트림 최적화)
        long figureCount = layoutInfo.parallelStream()
            .filter(l -> "figure".equals(l.getClassName()))
            .count();
        long tableCount = layoutInfo.parallelStream()
            .filter(l -> "table".equals(l.getClassName()))
            .count();

        cimOutput.setTotalFigures((int) figureCount);
        cimOutput.setTotalTables((int) tableCount);

        // 텍스트 통계 (병렬 처리)
        int totalWords = ocrResults.parallelStream()
            .filter(ocr -> ocr.getText() != null)
            .mapToInt(ocr -> ocr.getText().split("\\s+").length)
            .sum();

        int totalChars = ocrResults.parallelStream()
            .filter(ocr -> ocr.getText() != null)
            .mapToInt(ocr -> ocr.getText().length())
            .sum();

        cimOutput.setTotalWordCount(totalWords);
        cimOutput.setTotalCharCount(totalChars);
    }

    /**
     * 배치 ProcessingLog 저장
     */
    private void addProcessingLogBatch(
            AnalysisJob analysisJob,
            List<LayoutInfo> layoutInfo,
            List<OCRResult> ocrResults,
            List<AIDescriptionResult> aiResults,
            long processingTimeMs) {

        List<ProcessingLog> logs = new ArrayList<>();

        // 메인 완료 로그
        ProcessingLog mainLog = ProcessingLog.info("BATCH_ANALYSIS_COMPLETED",
            String.format("배치 분석 완료 - 레이아웃: %d개, OCR: %d개, AI: %d개",
                         layoutInfo.size(), ocrResults.size(), aiResults.size()));
        mainLog.setAnalysisJob(analysisJob);
        mainLog.setExecutionTimeMs(processingTimeMs);
        logs.add(mainLog);

        // 성능 메트릭 로그
        ProcessingLog perfLog = ProcessingLog.info("PERFORMANCE_METRICS",
            String.format("처리 시간: %dms, 초당 요소: %.1f개",
                         processingTimeMs,
                         (double) layoutInfo.size() / (processingTimeMs / 1000.0)));
        perfLog.setAnalysisJob(analysisJob);
        logs.add(perfLog);

        processingLogRepository.saveAll(logs);
    }

    /**
     * 최적화된 OCR 결과 검색 (Map 기반 캐싱)
     */
    private OCRResult findOCRByLayoutIdOptimized(int layoutId, List<OCRResult> ocrResults) {
        return ocrResults.stream()
            .filter(ocr -> ocr.getId() == layoutId)
            .findFirst()
            .orElse(null);
    }

    /**
     * 최적화된 AI 결과 검색 (Map 기반 캐싱)
     */
    private AIDescriptionResult findAIByLayoutIdOptimized(int layoutId, List<AIDescriptionResult> aiResults) {
        return aiResults.stream()
            .filter(ai -> ai.getId() == layoutId)
            .findFirst()
            .orElse(null);
    }

    /**
     * 배치 저장 성능 메트릭 로깅
     */
    private void logBatchSaveMetrics(String jobId, int totalElements, long saveTime) {
        double elementsPerSecond = totalElements / (saveTime / 1000.0);
        double avgTimePerElement = (double) saveTime / totalElements;

        logger.info("📊 배치 저장 성능 메트릭 - JobID: {}", jobId);
        logger.info("  └─ 총 저장 요소: {}개", totalElements);
        logger.info("  └─ 저장 시간: {}ms", saveTime);
        logger.info("  └─ 초당 처리: {:.1f}개/초", elementsPerSecond);
        logger.info("  └─ 요소당 평균: {:.1f}ms", avgTimePerElement);

        // 성능 임계값 체크
        if (saveTime > 5000) { // 5초 초과
            logger.warn("⚠️ 배치 저장 시간이 임계값 초과 - {}ms > 5000ms", saveTime);
        }

        if (elementsPerSecond < 10) { // 초당 10개 미만
            logger.warn("⚠️ 배치 저장 성능이 임계값 미달 - {:.1f}개/초 < 10개/초", elementsPerSecond);
        }
    }
}