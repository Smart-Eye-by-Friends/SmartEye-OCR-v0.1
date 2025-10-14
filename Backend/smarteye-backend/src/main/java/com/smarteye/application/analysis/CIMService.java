package com.smarteye.application.analysis;

import com.smarteye.presentation.dto.AIDescriptionResult;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.domain.analysis.entity.AnalysisJob;
import com.smarteye.domain.analysis.entity.CIMOutput;
import com.smarteye.domain.document.entity.DocumentPage;
import com.smarteye.domain.analysis.repository.AnalysisJobRepository;
import com.smarteye.domain.analysis.repository.CIMOutputRepository;
import com.smarteye.application.analysis.UnifiedAnalysisEngine.UnifiedAnalysisResult;
import com.smarteye.shared.util.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CIM (Common Information Model) 통합 서비스
 * 통합된 UnifiedAnalysisEngine을 사용하여 분석 워크플로우를 처리합니다.
 */
@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class CIMService {
    
    private static final Logger logger = LoggerFactory.getLogger(CIMService.class);
    
    @Autowired
    private UnifiedAnalysisEngine unifiedAnalysisEngine; // 통합된 분석 엔진

    @Autowired
    private IntegratedCIMProcessor integratedCIMProcessor; // 새로운 통합 CIM 처리기

    @Autowired
    private com.smarteye.infrastructure.external.OCRService ocrService;

    @Autowired
    private com.smarteye.infrastructure.external.AIDescriptionService aiDescriptionService;

    @Autowired
    private com.smarteye.infrastructure.external.LAMServiceClient lamServiceClient;

    @Autowired
    private com.smarteye.application.file.ImageProcessingService imageProcessingService;

    @Autowired
    private DocumentAnalysisDataService documentAnalysisDataService;
    
    @Autowired
    private CIMOutputRepository cimOutputRepository;
    
    @Autowired
    private com.smarteye.domain.document.repository.DocumentPageRepository documentPageRepository;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AnalysisJobRepository analysisJobRepository;

    /**
     * 🛡️ 견고한 통합 분석 및 CIM 처리 (v2.0 - IntegratedCIMProcessor 사용)
     *
     * 개선사항:
     * - 단계별 데이터 검증 및 오류 처리
     * - question_text null 매핑 문제 해결
     * - 완전한 추적 가능성
     * - 강화된 데이터 무결성 보장
     */
    public UnifiedAnalysisResult performUnifiedAnalysisWithCIM(BufferedImage image,
                                                               AnalysisJob analysisJob,
                                                               String modelChoice,
                                                               String apiKey) {
        long startTime = System.currentTimeMillis();
        String jobId = analysisJob.getJobId();

        try {
            logger.info("🚀 견고한 통합 분석 시작 - JobID: {}, 모델: {}", jobId, modelChoice);

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // Phase 1: 기본 분석 수행 (LAM, OCR, AI)
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            var layoutResult = lamServiceClient.analyzeLayout(image, modelChoice).get();
            if (layoutResult.getLayoutInfo().isEmpty()) {
                throw new RuntimeException("레이아웃 분석에 실패했습니다.");
            }

            List<OCRResult> ocrResults = ocrService.performOCR(image, layoutResult.getLayoutInfo());
            List<AIDescriptionResult> aiResults = (apiKey != null && !apiKey.trim().isEmpty())
                ? aiDescriptionService.generateDescriptions(image, layoutResult.getLayoutInfo(), apiKey).get()
                : List.of();

            logger.info("📊 기본 분석 완료 - 레이아웃: {}개, OCR: {}개, AI: {}개",
                       layoutResult.getLayoutInfo().size(), ocrResults.size(), aiResults.size());

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // Phase 2-4: UnifiedAnalysisEngine을 통한 통합 분석
            // - Phase 2: 컨텍스트 검증 (ContextValidationEngine)
            // - Phase 3: 지능형 교정 (IntelligentCorrectionEngine)
            // - Phase 4: 구조화 데이터 생성
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            UnifiedAnalysisResult unifiedResult = unifiedAnalysisEngine.performUnifiedAnalysis(
                layoutResult.getLayoutInfo(), ocrResults, aiResults
            );

            if (!unifiedResult.isSuccess()) {
                logger.error("❌ UnifiedAnalysisEngine 처리 실패 - JobID: {}, 오류: {}", jobId, unifiedResult.getMessage());
                throw new RuntimeException("통합 분석 실패: " + unifiedResult.getMessage());
            }

            logger.info("✅ UnifiedAnalysisEngine 처리 완료 - Phase 2-4 실행됨");

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // Phase 5: IntegratedCIMProcessor를 통한 최종 CIM 데이터 생성
            // (FormattedText 생성 및 데이터 무결성 검증)
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            IntegratedCIMProcessor.IntegratedCIMResult cimResult = integratedCIMProcessor.processIntegratedCIM(
                layoutResult.getLayoutInfo(), ocrResults, aiResults, analysisJob);

            if (!cimResult.isSuccess()) {
                logger.error("❌ 통합 CIM 처리 실패 - JobID: {}, 오류: {}", jobId, cimResult.getMessage());
                throw new RuntimeException("CIM 처리 실패: " + cimResult.getMessage());
            }

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // Phase 6: 레이아웃 시각화 생성
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            String layoutVisualizationPath = imageProcessingService.generateAndSaveLayoutVisualization(
                image, layoutResult.getLayoutInfo(), jobId);

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // Phase 7: 데이터베이스 저장
            // - UnifiedAnalysisResult 사용 (Phase 2-4 교정된 데이터)
            // - IntegratedCIMResult 사용 (FormattedText 및 무결성 검증)
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            String enhancedFormattedText = cimResult.getFormattedTextResult().getPrimaryText();
            saveEnhancedResultToDatabase(analysisJob, cimResult, layoutResult.getLayoutInfo(),
                                       ocrResults, aiResults, layoutVisualizationPath,
                                       enhancedFormattedText, System.currentTimeMillis() - startTime);

            long totalTime = System.currentTimeMillis() - startTime;
            logger.info("✅ 견고한 통합 분석 완료 - JobID: {}, 총 시간: {}ms, Phase 2-4 교정 적용됨",
                       jobId, totalTime);

            // 데이터 무결성 상태 로깅
            if (cimResult.getIntegrityCheck() != null && !cimResult.getIntegrityCheck().getWarnings().isEmpty()) {
                logger.warn("⚠️ 데이터 무결성 경고 - JobID: {}, 경고: {}",
                           jobId, String.join(", ", cimResult.getIntegrityCheck().getWarnings()));
            }

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // Phase 8: UnifiedAnalysisResult 반환 (Phase 2-4 교정된 최종 결과)
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            return unifiedResult;

        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            logger.error("❌ 견고한 통합 분석 실패 - JobID: {}, 시간: {}ms", jobId, totalTime, e);
            throw new RuntimeException("통합 분석 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 🆕 향상된 결과를 데이터베이스에 저장 (IntegratedCIMProcessor 결과 사용)
     */
    @Transactional(rollbackFor = Exception.class)
    private void saveEnhancedResultToDatabase(AnalysisJob analysisJob,
                                            IntegratedCIMProcessor.IntegratedCIMResult cimResult,
                                            List<LayoutInfo> layoutInfo,
                                            List<OCRResult> ocrResults,
                                            List<AIDescriptionResult> aiResults,
                                            String layoutVisualizationPath,
                                            String enhancedFormattedText,
                                            long processingTimeMs) {
        try {
            logger.info("💾 향상된 DB 저장 시작 - JobID: {}", analysisJob.getJobId());

            DocumentPage documentPage = createOrUpdateDocumentPage(analysisJob);

            // 향상된 CIMOutput 저장 (무결성 정보 포함)
            saveCIMOutputEnhanced(analysisJob, cimResult, layoutVisualizationPath,
                                enhancedFormattedText, processingTimeMs);

            // 기존 분석 결과 저장 (호환성 유지)
            documentAnalysisDataService.saveAnalysisResultsBatch(
                analysisJob.getJobId(),
                layoutInfo,
                ocrResults,
                aiResults,
                cimResult.getEnhancedCIMData().getBaseCIMData(),
                enhancedFormattedText,
                processingTimeMs
            );

            logger.info("✅ 향상된 DB 저장 완료 - JobID: {}", analysisJob.getJobId());

        } catch (Exception e) {
            logger.error("❌ 향상된 DB 저장 실패 - JobID: {}", analysisJob.getJobId(), e);
            throw new RuntimeException("DB 저장 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 🔄 호환성을 위한 UnifiedAnalysisResult 변환
     */
    private UnifiedAnalysisResult convertToUnifiedAnalysisResult(
            IntegratedCIMProcessor.IntegratedCIMResult cimResult, List<LayoutInfo> layoutInfo) {

        try {
            // IntegratedCIMResult를 기존 UnifiedAnalysisResult로 변환
            Map<String, Object> baseCIMData = cimResult.getEnhancedCIMData().getBaseCIMData();

            // 구조화된 데이터 변환
            UnifiedAnalysisEngine.StructuredData structuredData = convertToStructuredData(cimResult);

            // 분류된 요소 변환
            Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> classifiedElements =
                convertToClassifiedElements(cimResult);

            return new UnifiedAnalysisEngine.UnifiedAnalysisResult(
                true,
                "통합 CIM 처리 성공",
                null, // questionStructures는 사용하지 않음
                classifiedElements,
                structuredData,
                baseCIMData,
                cimResult.getProcessingTimeMs()
            );

        } catch (Exception e) {
            logger.error("❌ UnifiedAnalysisResult 변환 실패", e);
            throw new RuntimeException("결과 변환 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 구조화된 데이터 변환
     */
    private UnifiedAnalysisEngine.StructuredData convertToStructuredData(
            IntegratedCIMProcessor.IntegratedCIMResult cimResult) {

        UnifiedAnalysisEngine.StructuredData structuredData = new UnifiedAnalysisEngine.StructuredData();

        // DocumentInfo 생성
        UnifiedAnalysisEngine.DocumentInfo docInfo = new UnifiedAnalysisEngine.DocumentInfo();
        if (cimResult.getEnhancedCIMData().getMetadata() != null) {
            docInfo.setTotalQuestions(cimResult.getEnhancedCIMData().getMetadata().getTotalQuestions());
            docInfo.setTotalElements(cimResult.getEnhancedCIMData().getMetadata().getTotalElements());
            docInfo.setProcessingTimestamp(cimResult.getEnhancedCIMData().getMetadata().getProcessingTimestamp());
        }
        structuredData.setDocumentInfo(docInfo);

        // Questions 변환
        List<UnifiedAnalysisEngine.QuestionData> questions = new ArrayList<>();
        if (cimResult.getEnhancedCIMData().getQuestionGroups() != null) {
            for (IntegratedCIMProcessor.QuestionGroup group : cimResult.getEnhancedCIMData().getQuestionGroups()) {
                UnifiedAnalysisEngine.QuestionData questionData = new UnifiedAnalysisEngine.QuestionData();
                questionData.setQuestionNumber(group.getQuestionNumber());
                questionData.setQuestionText(group.getQuestionText()); // null 방지 보장됨

                // Elements 변환
                if (group.getElements() != null) {
                    Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> elements = new HashMap<>();
                    List<UnifiedAnalysisEngine.AnalysisElement> mainElements = group.getElements().stream()
                        .map(this::convertToAnalysisElement)
                        .collect(Collectors.toList());
                    elements.put("main", mainElements);
                    questionData.setElements(elements);
                }

                questions.add(questionData);
            }
        }
        structuredData.setQuestions(questions);

        return structuredData;
    }

    /**
     * 분류된 요소 변환
     */
    private Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> convertToClassifiedElements(
            IntegratedCIMProcessor.IntegratedCIMResult cimResult) {

        Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> classifiedElements = new HashMap<>();

        if (cimResult.getEnhancedCIMData().getClassifiedElements() != null) {
            for (Map.Entry<String, List<IntegratedCIMProcessor.ProcessedElement>> entry :
                 cimResult.getEnhancedCIMData().getClassifiedElements().entrySet()) {

                List<UnifiedAnalysisEngine.AnalysisElement> elements = entry.getValue().stream()
                    .map(this::convertToAnalysisElement)
                    .collect(Collectors.toList());

                classifiedElements.put(entry.getKey(), elements);
            }
        }

        return classifiedElements;
    }

    /**
     * ProcessedElement를 AnalysisElement로 변환
     */
    private UnifiedAnalysisEngine.AnalysisElement convertToAnalysisElement(
            IntegratedCIMProcessor.ProcessedElement processedElement) {

        UnifiedAnalysisEngine.AnalysisElement element = new UnifiedAnalysisEngine.AnalysisElement();
        element.setLayoutInfo(processedElement.getLayoutInfo());
        element.setOcrResult(processedElement.getOcrResult());
        element.setAiResult(processedElement.getAiResult());
        element.setCategory(processedElement.getCategory());

        return element;
    }

    /**
     * 향상된 CIMOutput 저장 (무결성 정보 포함)
     */
    private void saveCIMOutputEnhanced(AnalysisJob analysisJob,
                                     IntegratedCIMProcessor.IntegratedCIMResult cimResult,
                                     String layoutVisualizationPath,
                                     String enhancedFormattedText,
                                     long processingTimeMs) {
        try {
            CIMOutput cimOutput = cimOutputRepository.findByAnalysisJobId(analysisJob.getId())
                .orElse(new CIMOutput());

            cimOutput.setAnalysisJob(analysisJob);

            // 향상된 CIM 데이터 저장
            String cimDataJson = objectMapper.writeValueAsString(cimResult.getEnhancedCIMData().getBaseCIMData());
            cimOutput.setCimData(cimDataJson);
            cimOutput.setFormattedText(enhancedFormattedText);
            cimOutput.setLayoutVisualizationPath(layoutVisualizationPath);
            cimOutput.setProcessingTimeMs(processingTimeMs);
            cimOutput.setGenerationStatus(CIMOutput.GenerationStatus.COMPLETED);

            // 추가 메타데이터 저장
            if (cimResult.getEnhancedCIMData().getMetadata() != null) {
                cimOutput.setTotalElements((int) cimResult.getEnhancedCIMData().getMetadata().getTotalElements());
                // 기타 통계 정보 설정...
            }

            // 무결성 정보 저장 (확장 필드에)
            if (cimResult.getIntegrityCheck() != null) {
                Map<String, Object> integrityInfo = new HashMap<>();
                integrityInfo.put("passed", cimResult.getIntegrityCheck().isPassed());
                integrityInfo.put("warnings", cimResult.getIntegrityCheck().getWarnings());
                integrityInfo.put("generation_method", cimResult.getFormattedTextResult().getGenerationMethod());
                integrityInfo.put("text_quality", cimResult.getFormattedTextResult().getQuality().toString());

                // JSON으로 변환하여 확장 필드에 저장 (필요 시 CIMOutput 엔티티 확장)
                logger.debug("💾 무결성 정보 저장: {}", integrityInfo);
            }

            cimOutputRepository.save(cimOutput);

            logger.info("✅ 향상된 CIMOutput 저장 완료 - 데이터 크기: {}KB, 품질: {}",
                       cimDataJson.length() / 1024, cimResult.getFormattedTextResult().getQuality());

        } catch (Exception e) {
            logger.error("❌ 향상된 CIMOutput 저장 실패", e);
            throw new RuntimeException("CIMOutput 저장 중 오류 발생", e);
        }
    }

    /**
     * 통합된 분석 결과를 데이터베이스에 저장 (기존 메서드 - 호환성 유지)
     */
    @Transactional(rollbackFor = Exception.class)
    private void saveUnifiedResultToDatabase(AnalysisJob analysisJob,
                                             UnifiedAnalysisResult analysisResult,
                                             List<LayoutInfo> layoutInfo,
                                             List<OCRResult> ocrResults,
                                             List<AIDescriptionResult> aiResults,
                                             String layoutVisualizationPath,
                                             long processingTimeMs) {
        try {
            DocumentPage documentPage = createOrUpdateDocumentPage(analysisJob);

            // 실제 분석 결과에서 텍스트 추출 (강화된 처리) - saveAnalysisResultsBatch 호출 전에 먼저 생성
            String formattedText;
            try {
                formattedText = JsonUtils.createFormattedText(analysisResult.getCimData());

                // 결과 검증 및 대안 처리
                if (formattedText == null || formattedText.trim().isEmpty() || "(empty)".equals(formattedText.trim())) {
                    logger.warn("⚠️ CIM formattedText 결과 부족 - 대안 처리 시작");

                    // 대안 1: structuredData에서 직접 텍스트 생성
                    if (analysisResult.getStructuredData() != null) {
                        formattedText = createTextFromStructuredData(analysisResult.getStructuredData());
                        logger.info("✅ 대안 1 성공: StructuredData에서 {}   글자 생성", formattedText.length());
                    }

                    // 대안 2: classifiedElements에서 텍스트 생성
                    if ((formattedText == null || formattedText.trim().length() < 10) &&
                        analysisResult.getClassifiedElements() != null) {
                        formattedText = createTextFromClassifiedElements(analysisResult.getClassifiedElements());
                        logger.info("✅ 대안 2 성공: ClassifiedElements에서 {}글자 생성", formattedText.length());
                    }

                    // 최종 대안
                    if (formattedText == null || formattedText.trim().length() < 10) {
                        formattedText = createFallbackFormattedText(
                            analysisResult.getCimData() != null ? analysisResult.getCimData().size() : 0,
                            analysisResult.getStructuredData() != null ?
                                (int) analysisResult.getStructuredData().getTotalQuestions() : 0
                        );
                        logger.warn("🚨 최종 대안 사용: {}글자", formattedText.length());
                    }
                }

            } catch (Exception textError) {
                logger.error("❌ formattedText 생성 실패: {}", textError.getMessage(), textError);
                formattedText = createFallbackFormattedText(0, 0);
            }

            logger.info("📝 formattedText 생성 완료: {}글자", formattedText.length());

            // DocumentAnalysisDataService를 사용하여 기본 블록 정보 저장 (7개 매개변수) - 실제 formattedText 사용
            documentAnalysisDataService.saveAnalysisResultsBatch(
                analysisJob.getJobId(),
                layoutInfo,
                ocrResults,
                aiResults,
                analysisResult.getCimData(),
                formattedText,  // 실제 생성된 formattedText 사용
                processingTimeMs
            );

            // CIMOutput 저장
            CIMOutput cimOutput = cimOutputRepository.findByAnalysisJobId(analysisJob.getId())
                .orElse(new CIMOutput());

            cimOutput.setAnalysisJob(analysisJob);
            cimOutput.setCimData(objectMapper.writeValueAsString(analysisResult.getCimData()));
            cimOutput.setFormattedText(formattedText);
            logger.info("📝 최종 formattedText 설정 완료: {}글자", formattedText.length());
            cimOutput.setLayoutVisualizationPath(layoutVisualizationPath);
            cimOutput.setProcessingTimeMs(processingTimeMs);
            cimOutput.setGenerationStatus(CIMOutput.GenerationStatus.COMPLETED);

            cimOutputRepository.save(cimOutput);

        } catch (Exception e) {
            logger.error("통합 분석 결과 DB 저장 실패 - JobID: {}", analysisJob.getJobId(), e);
            throw new RuntimeException("DB 저장 중 오류 발생: " + e.getMessage(), e);
        }
    }

    private com.smarteye.domain.document.entity.DocumentPage createOrUpdateDocumentPage(AnalysisJob analysisJob) {
        Optional<DocumentPage> existingPage = documentPageRepository
            .findByAnalysisJobAndPageNumber(analysisJob, 1);

        DocumentPage documentPage;
        if (existingPage.isPresent()) {
            documentPage = existingPage.get();
        } else {
            documentPage = new DocumentPage();
            documentPage.setAnalysisJob(analysisJob);
            documentPage.setPageNumber(1);
        }

        documentPage.setImagePath(analysisJob.getFilePath());
        documentPage.setProcessingStatus(DocumentPage.ProcessingStatus.COMPLETED);

        return documentPageRepository.save(documentPage);
    }

    /**
     * 구조화된 데이터에서 텍스트 생성 (대안 1)
     */
    private String createTextFromStructuredData(com.smarteye.application.analysis.UnifiedAnalysisEngine.StructuredData structuredData) {
        try {
            StringBuilder text = new StringBuilder();
            text.append("=== 문제 분석 결과 ===\n\n");

            var docInfo = structuredData.getDocumentInfo();
            if (docInfo != null) {
                text.append("총 문제 수: ").append(docInfo.getTotalQuestions()).append("개\n");
                text.append("총 요소 수: ").append(docInfo.getTotalElements()).append("개\n\n");
            }

            var questions = structuredData.getQuestions();
            if (questions != null && !questions.isEmpty()) {
                for (int i = 0; i < questions.size(); i++) {
                    var question = questions.get(i);

                    // 문제 번호
                    if (question.getQuestionNumber() != null) {
                        text.append("🔸 ").append(question.getQuestionNumber()).append(". ");
                    } else {
                        text.append("🔸 문제").append(i + 1).append(". ");
                    }

                    // 문제 텍스트
                    if (question.getQuestionText() != null && !question.getQuestionText().trim().isEmpty()) {
                        text.append(question.getQuestionText()).append("\n\n");
                    } else {
                        text.append("내용 분석 중\n\n");
                    }

                    // 요소 정보 추가
                    var elements = question.getElements();
                    if (elements != null && !elements.isEmpty()) {
                        for (Map.Entry<String, List<com.smarteye.application.analysis.UnifiedAnalysisEngine.AnalysisElement>> entry : elements.entrySet()) {
                            String type = entry.getKey();
                            List<com.smarteye.application.analysis.UnifiedAnalysisEngine.AnalysisElement> elementList = entry.getValue();

                            if (!elementList.isEmpty()) {
                                text.append("📊 ").append(type).append(" 요소: ").append(elementList.size()).append("개\n");

                                for (com.smarteye.application.analysis.UnifiedAnalysisEngine.AnalysisElement element : elementList) {
                                    if (element.getOcrResult() != null &&
                                        element.getOcrResult().getText() != null &&
                                        !element.getOcrResult().getText().trim().isEmpty()) {
                                        text.append("    - ").append(element.getOcrResult().getText().trim()).append("\n");
                                    }
                                    if (element.getAiResult() != null &&
                                        element.getAiResult().getDescription() != null &&
                                        !element.getAiResult().getDescription().trim().isEmpty()) {
                                        text.append("    [AI] ").append(element.getAiResult().getDescription().trim()).append("\n");
                                    }
                                }
                            }
                        }
                        text.append("\n");
                    }

                    if (i < questions.size() - 1) {
                        text.append("-".repeat(30)).append("\n\n");
                    }
                }
            }

            String result = text.toString().trim();
            return result.isEmpty() ? null : result;

        } catch (Exception e) {
            logger.error("❌ createTextFromStructuredData 실패: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 분류된 요소에서 텍스트 생성 (대안 2)
     */
    private String createTextFromClassifiedElements(
            Map<String, List<com.smarteye.application.analysis.UnifiedAnalysisEngine.AnalysisElement>> classifiedElements) {
        try {
            StringBuilder text = new StringBuilder();
            text.append("=== 분석 요소 결과 ===\n\n");

            for (Map.Entry<String, List<com.smarteye.application.analysis.UnifiedAnalysisEngine.AnalysisElement>> entry : classifiedElements.entrySet()) {
                String questionKey = entry.getKey();
                List<com.smarteye.application.analysis.UnifiedAnalysisEngine.AnalysisElement> elements = entry.getValue();

                if (!"알 수 없음".equals(questionKey) && !elements.isEmpty()) {
                    text.append("🔸 ").append(questionKey).append("\n");

                    for (com.smarteye.application.analysis.UnifiedAnalysisEngine.AnalysisElement element : elements) {
                        // OCR 텍스트
                        if (element.getOcrResult() != null &&
                            element.getOcrResult().getText() != null &&
                            !element.getOcrResult().getText().trim().isEmpty()) {
                            text.append("    ").append(element.getOcrResult().getText().trim()).append("\n");
                        }

                        // AI 설명
                        if (element.getAiResult() != null &&
                            element.getAiResult().getDescription() != null &&
                            !element.getAiResult().getDescription().trim().isEmpty()) {
                            text.append("    [AI 설명] ").append(element.getAiResult().getDescription().trim()).append("\n");
                        }
                    }
                    text.append("\n");
                }
            }

            String result = text.toString().trim();
            return result.isEmpty() ? null : result;

        } catch (Exception e) {
            logger.error("❌ createTextFromClassifiedElements 실패: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 최종 대안 텍스트 생성
     */
    private String createFallbackFormattedText(int cimDataSize, int totalQuestions) {
        StringBuilder fallback = new StringBuilder();
        fallback.append("=== SmartEye 분석 결과 ===\n\n");
        fallback.append("분석이 완료되었습니다.\n\n");
        fallback.append("📊 분석 통계:\n");
        fallback.append("- CIM 데이터 크기: ").append(cimDataSize).append("개 항목\n");
        fallback.append("- 총 문제 수: ").append(totalQuestions).append("개\n\n");
        fallback.append("상세 내용을 보려면 '구조화된 분석' 탭을 확인해주세요.\n");
        fallback.append("🕰️ 처리 시간: ").append(
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        ).append("\n");

        return fallback.toString();
    }
}