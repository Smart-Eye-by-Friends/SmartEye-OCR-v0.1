package com.smarteye.application.analysis;

import com.smarteye.presentation.dto.AIDescriptionResult;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.domain.analysis.entity.AnalysisJob;
import com.smarteye.application.analysis.UnifiedAnalysisEngine.UnifiedAnalysisResult;
import com.smarteye.shared.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 🛡️ 통합 CIM 처리기 - 견고한 데이터 통합 아키텍처
 *
 * 핵심 개선사항:
 * 1. 데이터 무결성 보장 (null-safe, validation)
 * 2. 단일 책임 원칙 적용 (CIM 전용 처리)
 * 3. 명확한 오류 처리 및 복구 메커니즘
 * 4. 성능 최적화된 데이터 변환
 * 5. 완전한 추적 가능성
 */
@Service
@Validated
@Transactional(readOnly = true)
public class IntegratedCIMProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IntegratedCIMProcessor.class);

    @Autowired
    private UnifiedAnalysisEngine unifiedAnalysisEngine;

    // 향후 확장을 위한 서비스 (현재 미사용)
    // @Autowired
    // private DocumentAnalysisDataService documentAnalysisDataService;

    /**
     * 🎯 핵심 메서드: 통합 CIM 처리
     *
     * 개선사항:
     * - 데이터 유효성 검증 강화
     * - 단계별 오류 처리
     * - 성능 모니터링
     * - 추적 가능한 로깅
     */
    @Transactional
    public IntegratedCIMResult processIntegratedCIM(
            @NotNull @Valid List<LayoutInfo> layoutElements,
            @NotNull List<OCRResult> ocrResults,
            @NotNull List<AIDescriptionResult> aiResults,
            @NotNull AnalysisJob analysisJob) {

        String jobId = analysisJob.getJobId();
        long startTime = System.currentTimeMillis();

        logger.info("🚀 [CIM-PROCESSOR] 통합 CIM 처리 시작 - JobID: {}", jobId);
        logger.info("📊 입력 데이터: 레이아웃={}개, OCR={}개, AI={}개",
                   layoutElements.size(), ocrResults.size(), aiResults.size());

        try {
            // Phase 1: 입력 데이터 검증 및 전처리
            ValidationResult validation = validateAndPreprocessInputs(layoutElements, ocrResults, aiResults);
            if (!validation.isValid()) {
                return createFailureResult(jobId, "데이터 검증 실패: " + validation.getErrorMessage());
            }

            // Phase 2: 통합 분석 실행
            UnifiedAnalysisResult analysisResult = executeUnifiedAnalysis(layoutElements, ocrResults, aiResults);
            if (!analysisResult.isSuccess()) {
                return createFailureResult(jobId, "통합 분석 실패: " + analysisResult.getMessage());
            }

            // Phase 3: 강화된 CIM 데이터 생성
            EnhancedCIMData enhancedCIM = generateEnhancedCIMData(analysisResult);

            // Phase 4: 견고한 FormattedText 생성
            FormattedTextResult formattedTextResult = generateRobustFormattedText(enhancedCIM);

            // Phase 5: 데이터 무결성 검증
            IntegrityCheckResult integrityCheck = performDataIntegrityCheck(enhancedCIM, formattedTextResult);
            if (!integrityCheck.isPassed()) {
                logger.warn("⚠️ [CIM-PROCESSOR] 데이터 무결성 경고: {}", integrityCheck.getWarnings());
            }

            // Phase 6: 통합 결과 생성
            long processingTime = System.currentTimeMillis() - startTime;
            IntegratedCIMResult result = createSuccessResult(
                jobId, enhancedCIM, formattedTextResult, integrityCheck, processingTime);

            logger.info("✅ [CIM-PROCESSOR] 통합 CIM 처리 완료 - JobID: {}, 시간: {}ms",
                       jobId, processingTime);

            return result;

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            logger.error("❌ [CIM-PROCESSOR] 통합 CIM 처리 실패 - JobID: {}, 시간: {}ms",
                        jobId, processingTime, e);
            return createFailureResult(jobId, "처리 중 예외 발생: " + e.getMessage());
        }
    }

    /**
     * 🔍 Phase 1: 강화된 입력 데이터 검증
     */
    private ValidationResult validateAndPreprocessInputs(
            List<LayoutInfo> layoutElements,
            List<OCRResult> ocrResults,
            List<AIDescriptionResult> aiResults) {

        logger.debug("🔍 [VALIDATION] 입력 데이터 검증 시작");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 필수 데이터 존재 확인
        if (layoutElements.isEmpty()) {
            errors.add("레이아웃 요소가 없습니다");
        }

        // ID 일관성 검증
        Set<Integer> layoutIds = layoutElements.stream()
            .map(LayoutInfo::getId)
            .collect(Collectors.toSet());

        // ID 매핑 검증
        long unmatchedOCR = ocrResults.stream()
            .filter(ocr -> !layoutIds.contains(ocr.getId()))
            .count();

        long unmatchedAI = aiResults.stream()
            .filter(ai -> !layoutIds.contains(ai.getId()))
            .count();

        if (unmatchedOCR > 0) {
            warnings.add("매핑되지 않은 OCR 결과: " + unmatchedOCR + "개");
        }

        if (unmatchedAI > 0) {
            warnings.add("매핑되지 않은 AI 결과: " + unmatchedAI + "개");
        }

        // 좌표 유효성 검증
        long invalidCoordinates = layoutElements.stream()
            .filter(layout -> !isValidCoordinates(layout.getBox()))
            .count();

        if (invalidCoordinates > 0) {
            warnings.add("유효하지 않은 좌표: " + invalidCoordinates + "개");
        }

        ValidationResult result = new ValidationResult(errors.isEmpty(), errors, warnings);

        if (!result.isValid()) {
            logger.warn("❌ [VALIDATION] 검증 실패: {}", String.join(", ", errors));
        } else if (!warnings.isEmpty()) {
            logger.warn("⚠️ [VALIDATION] 검증 경고: {}", String.join(", ", warnings));
        } else {
            logger.debug("✅ [VALIDATION] 검증 성공");
        }

        return result;
    }

    /**
     * 🧠 Phase 2: 통합 분석 실행
     */
    private UnifiedAnalysisResult executeUnifiedAnalysis(
            List<LayoutInfo> layoutElements,
            List<OCRResult> ocrResults,
            List<AIDescriptionResult> aiResults) {

        logger.debug("🧠 [ANALYSIS] 통합 분석 실행");

        try {
            return unifiedAnalysisEngine.performUnifiedAnalysis(layoutElements, ocrResults, aiResults);
        } catch (Exception e) {
            logger.error("❌ [ANALYSIS] 통합 분석 중 예외", e);
            throw new CIMProcessingException("통합 분석 실행 실패", e);
        }
    }

    /**
     * 🏗️ Phase 3: 강화된 CIM 데이터 생성
     *
     * <p>개선사항 (Day 2):</p>
     * <ul>
     *   <li>StructuredData를 baseCIM에 포함하여 FormattedTextFormatter 연동</li>
     *   <li>다단 레이아웃 지원을 위한 structured_data 키 추가</li>
     * </ul>
     */
    private EnhancedCIMData generateEnhancedCIMData(UnifiedAnalysisResult analysisResult) {
        logger.debug("🏗️ [CIM-GENERATION] 강화된 CIM 데이터 생성");

        EnhancedCIMData enhancedCIM = new EnhancedCIMData();

        try {
            // 기본 CIM 데이터 변환
            Map<String, Object> baseCIM = analysisResult.getCimData();

            // ⭐ Day 2 핵심 수정: StructuredData를 CIM에 포함
            // FormattedTextFormatter가 다단 레이아웃을 처리할 수 있도록 함
            if (analysisResult.getStructuredData() != null) {
                baseCIM.put("structured_data", analysisResult.getStructuredData());
                logger.debug("✅ structured_data를 baseCIM에 추가 완료");
            } else {
                logger.warn("⚠️ StructuredData가 null - FormattedTextFormatter는 Fallback 사용");
            }

            enhancedCIM.setBaseCIMData(baseCIM);

            // 구조화된 데이터 처리
            if (analysisResult.getStructuredData() != null) {
                enhancedCIM.setQuestionGroups(
                    convertToQuestionGroups(analysisResult.getStructuredData()));
            }

            // 분류된 요소 처리
            if (analysisResult.getClassifiedElements() != null) {
                enhancedCIM.setClassifiedElements(
                    processClassifiedElements(analysisResult.getClassifiedElements()));
            }

            // 메타데이터 생성
            enhancedCIM.setMetadata(generateCIMMetadata(analysisResult));

            // 데이터 무결성 태그 추가
            enhancedCIM.setIntegrityTags(generateIntegrityTags(enhancedCIM));

            logger.debug("✅ [CIM-GENERATION] 강화된 CIM 데이터 생성 완료");
            return enhancedCIM;

        } catch (Exception e) {
            logger.error("❌ [CIM-GENERATION] CIM 데이터 생성 실패", e);
            throw new CIMProcessingException("CIM 데이터 생성 실패", e);
        }
    }

    /**
     * 📝 Phase 4: 견고한 FormattedText 생성
     */
    private FormattedTextResult generateRobustFormattedText(EnhancedCIMData enhancedCIM) {
        logger.debug("📝 [TEXT-GENERATION] 견고한 FormattedText 생성");

        try {
            // 주 텍스트 생성 시도
            String primaryText = JsonUtils.createFormattedText(enhancedCIM.getBaseCIMData());

            FormattedTextResult result = new FormattedTextResult();

            if (isValidFormattedText(primaryText)) {
                result.setPrimaryText(primaryText);
                result.setGenerationMethod("PRIMARY_CIM");
                result.setQuality(TextQuality.HIGH);
                logger.debug("✅ [TEXT-GENERATION] 주 텍스트 생성 성공: {}글자", primaryText.length());
            } else {
                // 대안 텍스트 생성
                String alternativeText = generateAlternativeText(enhancedCIM);
                result.setPrimaryText(alternativeText);
                result.setGenerationMethod("ALTERNATIVE_STRUCTURED");
                result.setQuality(TextQuality.MEDIUM);
                logger.warn("⚠️ [TEXT-GENERATION] 대안 텍스트 사용: {}글자", alternativeText.length());
            }

            // 추가 텍스트 변형 생성
            result.setStructuredSummary(generateStructuredSummary(enhancedCIM));
            result.setRawDataExtract(generateRawDataExtract(enhancedCIM));

            return result;

        } catch (Exception e) {
            logger.error("❌ [TEXT-GENERATION] FormattedText 생성 실패", e);
            throw new CIMProcessingException("FormattedText 생성 실패", e);
        }
    }

    /**
     * 🔒 Phase 5: 데이터 무결성 검증
     */
    private IntegrityCheckResult performDataIntegrityCheck(
            EnhancedCIMData enhancedCIM, FormattedTextResult formattedTextResult) {

        logger.debug("🔒 [INTEGRITY] 데이터 무결성 검증");

        IntegrityCheckResult result = new IntegrityCheckResult();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // 1. 텍스트 길이 검증
        if (formattedTextResult.getPrimaryText().length() < 50) {
            warnings.add("생성된 텍스트가 너무 짧습니다: " + formattedTextResult.getPrimaryText().length() + "글자");
        }

        // 2. question_text 무결성 검증
        long nullQuestionTexts = enhancedCIM.getQuestionGroups().stream()
            .filter(q -> q.getQuestionText() == null || q.getQuestionText().trim().isEmpty())
            .count();

        if (nullQuestionTexts > 0) {
            warnings.add("빈 문제 텍스트: " + nullQuestionTexts + "개");
        }

        // 3. 요소 매핑 일관성 검증
        long unmappedElements = enhancedCIM.getClassifiedElements().values().stream()
            .flatMap(List::stream)
            .filter(element -> element.getOcrResult() == null && element.getAiResult() == null)
            .count();

        if (unmappedElements > 0) {
            warnings.add("매핑되지 않은 요소: " + unmappedElements + "개");
        }

        result.setPassed(errors.isEmpty());
        result.setWarnings(warnings);
        result.setErrors(errors);

        if (result.isPassed()) {
            logger.debug("✅ [INTEGRITY] 무결성 검증 통과");
        } else {
            logger.warn("⚠️ [INTEGRITY] 무결성 경고: {}", String.join(", ", warnings));
        }

        return result;
    }

    // 헬퍼 메서드들

    private boolean isValidCoordinates(int[] box) {
        return box != null && box.length >= 4 && box[0] >= 0 && box[1] >= 0 &&
               box[2] > box[0] && box[3] > box[1];
    }

    private boolean isValidFormattedText(String text) {
        return text != null && !text.trim().isEmpty() && text.trim().length() > 20;
    }

    private List<QuestionGroup> convertToQuestionGroups(
            UnifiedAnalysisEngine.StructuredData structuredData) {
        // 구조화된 데이터를 QuestionGroup으로 변환하는 로직
        List<QuestionGroup> questionGroups = new ArrayList<>();

        if (structuredData.getQuestions() != null) {
            for (var question : structuredData.getQuestions()) {
                QuestionGroup group = new QuestionGroup();
                group.setQuestionNumber(question.getQuestionNumber());
                group.setQuestionText(question.getQuestionText() != null ?
                    question.getQuestionText() : "문제 텍스트 추출 중..."); // null 방지

                if (question.getElements() != null) {
                    List<ProcessedElement> elements = question.getElements().entrySet().stream()
                        .flatMap(entry -> entry.getValue().stream())
                        .map(this::convertToProcessedElement)
                        .collect(Collectors.toList());
                    group.setElements(elements);
                }

                questionGroups.add(group);
            }
        }

        return questionGroups;
    }

    private ProcessedElement convertToProcessedElement(UnifiedAnalysisEngine.AnalysisElement element) {
        ProcessedElement processed = new ProcessedElement();
        processed.setLayoutInfo(element.getLayoutInfo());
        processed.setOcrResult(element.getOcrResult());
        processed.setAiResult(element.getAiResult());
        processed.setCategory(element.getCategory());

        // 요소 품질 계산
        ElementQuality quality = calculateElementQuality(element);
        processed.setQuality(quality);

        return processed;
    }

    private ElementQuality calculateElementQuality(UnifiedAnalysisEngine.AnalysisElement element) {
        ElementQuality quality = new ElementQuality();

        // OCR 신뢰도
        if (element.getOcrResult() != null) {
            quality.setOcrConfidence(element.getOcrResult().getConfidence());
        }

        // 레이아웃 신뢰도
        if (element.getLayoutInfo() != null) {
            quality.setLayoutConfidence(element.getLayoutInfo().getConfidence());
        }

        // 종합 품질 점수
        quality.calculateOverallScore();

        return quality;
    }

    private Map<String, List<ProcessedElement>> processClassifiedElements(
            Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> classifiedElements) {

        return classifiedElements.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream()
                    .map(this::convertToProcessedElement)
                    .collect(Collectors.toList())
            ));
    }

    private CIMMetadata generateCIMMetadata(UnifiedAnalysisResult analysisResult) {
        CIMMetadata metadata = new CIMMetadata();
        metadata.setProcessingTime(analysisResult.getProcessingTimeMs());
        metadata.setProcessingTimestamp(System.currentTimeMillis());
        metadata.setVersion("v2.0-enhanced");
        metadata.setProcessorVersion("IntegratedCIMProcessor");

        if (analysisResult.getStructuredData() != null &&
            analysisResult.getStructuredData().getDocumentInfo() != null) {
            metadata.setTotalQuestions(analysisResult.getStructuredData().getDocumentInfo().getTotalQuestions());
            metadata.setTotalElements(analysisResult.getStructuredData().getDocumentInfo().getTotalElements());
        }

        return metadata;
    }

    private Map<String, String> generateIntegrityTags(EnhancedCIMData enhancedCIM) {
        Map<String, String> tags = new HashMap<>();
        tags.put("data_completeness", calculateDataCompleteness(enhancedCIM));
        tags.put("question_text_coverage", calculateQuestionTextCoverage(enhancedCIM));
        tags.put("element_mapping_ratio", calculateElementMappingRatio(enhancedCIM));
        return tags;
    }

    private String calculateDataCompleteness(EnhancedCIMData enhancedCIM) {
        // 데이터 완성도 계산 로직
        int totalFields = 0;
        int populatedFields = 0;

        for (QuestionGroup group : enhancedCIM.getQuestionGroups()) {
            totalFields += 3; // questionNumber, questionText, elements
            if (group.getQuestionNumber() != null) populatedFields++;
            if (group.getQuestionText() != null && !group.getQuestionText().trim().isEmpty()) populatedFields++;
            if (group.getElements() != null && !group.getElements().isEmpty()) populatedFields++;
        }

        double completeness = totalFields > 0 ? (double) populatedFields / totalFields * 100 : 0;
        return String.format("%.1f%%", completeness);
    }

    private String calculateQuestionTextCoverage(EnhancedCIMData enhancedCIM) {
        long totalQuestions = enhancedCIM.getQuestionGroups().size();
        long questionsWithText = enhancedCIM.getQuestionGroups().stream()
            .filter(q -> q.getQuestionText() != null && !q.getQuestionText().trim().isEmpty())
            .count();

        double coverage = totalQuestions > 0 ? (double) questionsWithText / totalQuestions * 100 : 0;
        return String.format("%.1f%%", coverage);
    }

    private String calculateElementMappingRatio(EnhancedCIMData enhancedCIM) {
        long totalElements = enhancedCIM.getClassifiedElements().values().stream()
            .mapToLong(List::size)
            .sum();

        long mappedElements = enhancedCIM.getClassifiedElements().values().stream()
            .flatMap(List::stream)
            .filter(element -> element.getOcrResult() != null || element.getAiResult() != null)
            .count();

        double ratio = totalElements > 0 ? (double) mappedElements / totalElements * 100 : 0;
        return String.format("%.1f%%", ratio);
    }

    private String generateAlternativeText(EnhancedCIMData enhancedCIM) {
        StringBuilder text = new StringBuilder();
        text.append("=== SmartEye 분석 결과 (대안 처리) ===\n\n");

        for (QuestionGroup group : enhancedCIM.getQuestionGroups()) {
            if (group.getQuestionNumber() != null) {
                text.append(group.getQuestionNumber()).append(". ");
            }

            if (group.getQuestionText() != null && !group.getQuestionText().trim().isEmpty()) {
                text.append(group.getQuestionText()).append("\n\n");
            } else {
                text.append("문제 내용을 추출 중입니다...\n\n");
            }

            // 요소별 텍스트 추가
            if (group.getElements() != null) {
                for (ProcessedElement element : group.getElements()) {
                    if (element.getOcrResult() != null &&
                        element.getOcrResult().getText() != null &&
                        !element.getOcrResult().getText().trim().isEmpty()) {
                        text.append("    ").append(element.getOcrResult().getText().trim()).append("\n");
                    }
                }
            }

            text.append("\n");
        }

        return text.toString();
    }

    private String generateStructuredSummary(EnhancedCIMData enhancedCIM) {
        StringBuilder summary = new StringBuilder();
        summary.append("=== 구조화된 요약 ===\n");
        summary.append("총 문제 수: ").append(enhancedCIM.getQuestionGroups().size()).append("\n");
        summary.append("총 요소 수: ").append(
            enhancedCIM.getClassifiedElements().values().stream().mapToLong(List::size).sum()
        ).append("\n");
        return summary.toString();
    }

    private String generateRawDataExtract(EnhancedCIMData enhancedCIM) {
        return "원시 데이터 추출 결과를 여기에 포함...";
    }

    private IntegratedCIMResult createSuccessResult(
            String jobId, EnhancedCIMData enhancedCIM, FormattedTextResult formattedTextResult,
            IntegrityCheckResult integrityCheck, long processingTime) {

        IntegratedCIMResult result = new IntegratedCIMResult();
        result.setJobId(jobId);
        result.setSuccess(true);
        result.setMessage("통합 CIM 처리 성공");
        result.setEnhancedCIMData(enhancedCIM);
        result.setFormattedTextResult(formattedTextResult);
        result.setIntegrityCheck(integrityCheck);
        result.setProcessingTimeMs(processingTime);
        result.setTimestamp(System.currentTimeMillis());

        return result;
    }

    private IntegratedCIMResult createFailureResult(String jobId, String errorMessage) {
        IntegratedCIMResult result = new IntegratedCIMResult();
        result.setJobId(jobId);
        result.setSuccess(false);
        result.setMessage(errorMessage);
        result.setTimestamp(System.currentTimeMillis());

        return result;
    }

    // 데이터 클래스들

    public static class IntegratedCIMResult {
        private String jobId;
        private boolean success;
        private String message;
        private EnhancedCIMData enhancedCIMData;
        private FormattedTextResult formattedTextResult;
        private IntegrityCheckResult integrityCheck;
        private long processingTimeMs;
        private long timestamp;

        // Getters and Setters
        public String getJobId() { return jobId; }
        public void setJobId(String jobId) { this.jobId = jobId; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public EnhancedCIMData getEnhancedCIMData() { return enhancedCIMData; }
        public void setEnhancedCIMData(EnhancedCIMData enhancedCIMData) { this.enhancedCIMData = enhancedCIMData; }
        public FormattedTextResult getFormattedTextResult() { return formattedTextResult; }
        public void setFormattedTextResult(FormattedTextResult formattedTextResult) { this.formattedTextResult = formattedTextResult; }
        public IntegrityCheckResult getIntegrityCheck() { return integrityCheck; }
        public void setIntegrityCheck(IntegrityCheckResult integrityCheck) { this.integrityCheck = integrityCheck; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    public static class EnhancedCIMData {
        private Map<String, Object> baseCIMData;
        private List<QuestionGroup> questionGroups;
        private Map<String, List<ProcessedElement>> classifiedElements;
        private CIMMetadata metadata;
        private Map<String, String> integrityTags;

        // Getters and Setters
        public Map<String, Object> getBaseCIMData() { return baseCIMData; }
        public void setBaseCIMData(Map<String, Object> baseCIMData) { this.baseCIMData = baseCIMData; }
        public List<QuestionGroup> getQuestionGroups() { return questionGroups; }
        public void setQuestionGroups(List<QuestionGroup> questionGroups) { this.questionGroups = questionGroups; }
        public Map<String, List<ProcessedElement>> getClassifiedElements() { return classifiedElements; }
        public void setClassifiedElements(Map<String, List<ProcessedElement>> classifiedElements) { this.classifiedElements = classifiedElements; }
        public CIMMetadata getMetadata() { return metadata; }
        public void setMetadata(CIMMetadata metadata) { this.metadata = metadata; }
        public Map<String, String> getIntegrityTags() { return integrityTags; }
        public void setIntegrityTags(Map<String, String> integrityTags) { this.integrityTags = integrityTags; }
    }

    public static class QuestionGroup {
        private Integer questionNumber;
        private String questionText;
        private List<ProcessedElement> elements;

        // Getters and Setters
        public Integer getQuestionNumber() { return questionNumber; }
        public void setQuestionNumber(Integer questionNumber) { this.questionNumber = questionNumber; }
        public String getQuestionText() { return questionText; }
        public void setQuestionText(String questionText) { this.questionText = questionText; }
        public List<ProcessedElement> getElements() { return elements; }
        public void setElements(List<ProcessedElement> elements) { this.elements = elements; }
    }

    public static class ProcessedElement {
        private LayoutInfo layoutInfo;
        private OCRResult ocrResult;
        private AIDescriptionResult aiResult;
        private String category;
        private ElementQuality quality;

        // Getters and Setters
        public LayoutInfo getLayoutInfo() { return layoutInfo; }
        public void setLayoutInfo(LayoutInfo layoutInfo) { this.layoutInfo = layoutInfo; }
        public OCRResult getOcrResult() { return ocrResult; }
        public void setOcrResult(OCRResult ocrResult) { this.ocrResult = ocrResult; }
        public AIDescriptionResult getAiResult() { return aiResult; }
        public void setAiResult(AIDescriptionResult aiResult) { this.aiResult = aiResult; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public ElementQuality getQuality() { return quality; }
        public void setQuality(ElementQuality quality) { this.quality = quality; }
    }

    public static class ElementQuality {
        private double ocrConfidence;
        private double layoutConfidence;
        private double overallScore;

        public void calculateOverallScore() {
            this.overallScore = (ocrConfidence + layoutConfidence) / 2.0;
        }

        // Getters and Setters
        public double getOcrConfidence() { return ocrConfidence; }
        public void setOcrConfidence(double ocrConfidence) { this.ocrConfidence = ocrConfidence; }
        public double getLayoutConfidence() { return layoutConfidence; }
        public void setLayoutConfidence(double layoutConfidence) { this.layoutConfidence = layoutConfidence; }
        public double getOverallScore() { return overallScore; }
        public void setOverallScore(double overallScore) { this.overallScore = overallScore; }
    }

    public static class FormattedTextResult {
        private String primaryText;
        private String structuredSummary;
        private String rawDataExtract;
        private String generationMethod;
        private TextQuality quality;

        // Getters and Setters
        public String getPrimaryText() { return primaryText; }
        public void setPrimaryText(String primaryText) { this.primaryText = primaryText; }
        public String getStructuredSummary() { return structuredSummary; }
        public void setStructuredSummary(String structuredSummary) { this.structuredSummary = structuredSummary; }
        public String getRawDataExtract() { return rawDataExtract; }
        public void setRawDataExtract(String rawDataExtract) { this.rawDataExtract = rawDataExtract; }
        public String getGenerationMethod() { return generationMethod; }
        public void setGenerationMethod(String generationMethod) { this.generationMethod = generationMethod; }
        public TextQuality getQuality() { return quality; }
        public void setQuality(TextQuality quality) { this.quality = quality; }
    }

    public enum TextQuality {
        HIGH, MEDIUM, LOW
    }

    public static class ValidationResult {
        private boolean valid;
        private List<String> errors;
        private List<String> warnings;

        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
        }

        // Getters
        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public String getErrorMessage() { return String.join(", ", errors); }
    }

    public static class IntegrityCheckResult {
        private boolean passed;
        private List<String> warnings;
        private List<String> errors;

        // Getters and Setters
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
    }

    public static class CIMMetadata {
        private long processingTime;
        private long processingTimestamp;
        private String version;
        private String processorVersion;
        private long totalQuestions;
        private int totalElements;

        // Getters and Setters
        public long getProcessingTime() { return processingTime; }
        public void setProcessingTime(long processingTime) { this.processingTime = processingTime; }
        public long getProcessingTimestamp() { return processingTimestamp; }
        public void setProcessingTimestamp(long processingTimestamp) { this.processingTimestamp = processingTimestamp; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getProcessorVersion() { return processorVersion; }
        public void setProcessorVersion(String processorVersion) { this.processorVersion = processorVersion; }
        public long getTotalQuestions() { return totalQuestions; }
        public void setTotalQuestions(long totalQuestions) { this.totalQuestions = totalQuestions; }
        public int getTotalElements() { return totalElements; }
        public void setTotalElements(int totalElements) { this.totalElements = totalElements; }
    }

    public static class CIMProcessingException extends RuntimeException {
        public CIMProcessingException(String message) {
            super(message);
        }

        public CIMProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}