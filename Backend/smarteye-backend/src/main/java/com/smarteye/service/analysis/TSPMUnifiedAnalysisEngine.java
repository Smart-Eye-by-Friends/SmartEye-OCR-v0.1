package com.smarteye.service.analysis;

import com.smarteye.dto.TSPMResult;
import com.smarteye.dto.QuestionGroup;
import com.smarteye.dto.EducationalElement;
import com.smarteye.entity.LayoutBlock;
import com.smarteye.entity.TextBlock;
import com.smarteye.repository.LayoutBlockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TSPM 통합 분석 엔진
 *
 * 기존 TSPMEngine, StructuredAnalysisService, CIMService, StructuredJSONService의
 * 중복 로직을 통합하여 단일 엔진으로 구현
 *
 * SOLID 원칙 적용:
 * - 단일 책임: 문서 분석 통합 처리
 * - 개방-폐쇄: 새로운 분석 기능 확장 가능
 * - 리스코프 치환: 기존 TSPMEngine과 호환
 * - 인터페이스 분리: 각 분석 단계별 인터페이스 분리
 * - 의존성 역전: 추상화된 분석 컴포넌트 의존
 */
@Service
public class TSPMUnifiedAnalysisEngine {

    private static final Logger logger = LoggerFactory.getLogger(TSPMUnifiedAnalysisEngine.class);

    @Autowired
    private TextPatternAnalyzer textPatternAnalyzer;

    @Autowired
    private SpatialProximityCalculator spatialCalculator;

    @Autowired
    private ElementClassifier elementClassifier;

    @Autowired
    private LayoutBlockRepository layoutBlockRepository;

    /**
     * 통합 TSPM 분석 수행
     * 기존 TSPMEngine.performTSPMAnalysis()의 리팩토링된 버전
     *
     * @param documentPageId 문서 페이지 ID
     * @return TSPM 분석 결과
     */
    public TSPMResult performUnifiedTSPMAnalysis(Long documentPageId) {
        logger.info("🔧 통합 TSPM 분석 시작 - 페이지 ID: {}", documentPageId);
        long startTime = System.currentTimeMillis();

        try {
            // 1. 데이터 로드
            List<LayoutBlock> layoutsWithText = loadLayoutData(documentPageId);
            logger.info("📊 로드된 데이터: LayoutBlock {} 개", layoutsWithText.size());

            // 2. 문제 위치 추출 (통합된 패턴 분석 활용)
            Map<String, Integer> questionPositions = extractQuestionPositions(layoutsWithText);
            logger.info("🔍 감지된 문제 번호: {} 개 - {}", questionPositions.size(), questionPositions.keySet());

            // 3. 통합 분석 수행
            TSPMResult result = performUnifiedAnalysis(layoutsWithText, questionPositions);

            // 4. 메타데이터 설정
            finalizeAnalysisResult(result, startTime);

            logger.info("✅ 통합 TSPM 분석 완료 - 처리 시간: {}ms, 문제 수: {}",
                       result.getAnalysisMetadata().getProcessingTimeMs(),
                       result.getQuestionGroups().size());

            return result;

        } catch (Exception e) {
            logger.error("❌ 통합 TSMP 분석 실패: {}", e.getMessage(), e);
            throw new RuntimeException("통합 TSPM 분석 중 오류 발생", e);
        }
    }

    /**
     * 레이아웃 데이터 로드
     */
    private List<LayoutBlock> loadLayoutData(Long documentPageId) {
        return layoutBlockRepository
            .findByDocumentPageIdWithTextBlocksOrderByPosition(documentPageId);
    }

    /**
     * 문제 위치 추출 (통합된 패턴 분석 활용)
     */
    private Map<String, Integer> extractQuestionPositions(List<LayoutBlock> layouts) {
        Map<String, Integer> questionPositions = new HashMap<>();

        for (LayoutBlock layout : layouts) {
            // question_number 클래스만 처리
            if (!"question_number".equals(layout.getClassName())) {
                continue;
            }

            String extractedText = getExtractedText(layout);
            if (extractedText == null || extractedText.trim().isEmpty()) {
                continue;
            }

            // 통합된 패턴 분석 활용
            String questionNum = textPatternAnalyzer.extractQuestionNumber(extractedText.trim());
            if (questionNum != null) {
                questionPositions.put(questionNum, layout.getY1());
                logger.debug("📍 문제 번호 감지: {} → Y좌표: {}", questionNum, layout.getY1());
            }
        }

        return questionPositions;
    }

    /**
     * 통합 분석 수행
     */
    private TSPMResult performUnifiedAnalysis(List<LayoutBlock> layouts,
                                            Map<String, Integer> questionPositions) {
        Map<String, QuestionGroup> questionGroups = new HashMap<>();
        List<LayoutBlock> unassignedElements = new ArrayList<>();

        // 문제 그룹 초기화
        initializeQuestionGroups(questionGroups, questionPositions);

        // 각 요소를 문제별로 할당 (통합된 공간 계산 활용)
        assignElementsToQuestions(layouts, questionPositions, questionGroups, unassignedElements);

        logger.info("📊 할당 결과: 할당됨 {} 개, 미할당 {} 개",
                   layouts.size() - unassignedElements.size(), unassignedElements.size());

        // TSPMResult 구성
        return buildUnifiedTSPMResult(questionGroups, unassignedElements.size());
    }

    /**
     * 문제 그룹 초기화
     */
    private void initializeQuestionGroups(Map<String, QuestionGroup> questionGroups,
                                        Map<String, Integer> questionPositions) {
        for (Map.Entry<String, Integer> entry : questionPositions.entrySet()) {
            QuestionGroup group = new QuestionGroup(entry.getKey(), entry.getValue());
            questionGroups.put(entry.getKey(), group);
        }
    }

    /**
     * 요소들을 문제별로 할당 (통합된 로직 활용)
     */
    private void assignElementsToQuestions(List<LayoutBlock> layouts,
                                         Map<String, Integer> questionPositions,
                                         Map<String, QuestionGroup> questionGroups,
                                         List<LayoutBlock> unassignedElements) {
        for (LayoutBlock layout : layouts) {
            // 통합된 공간 계산 활용
            String assignedQuestion = spatialCalculator.assignElementToNearestQuestion(
                layout.getY1(), questionPositions);

            if ("unknown".equals(assignedQuestion)) {
                unassignedElements.add(layout);
                continue;
            }

            // 통합된 요소 분류 활용
            EducationalElement element = createUnifiedEducationalElement(layout);

            // 문제 그룹에 추가
            QuestionGroup group = questionGroups.get(assignedQuestion);
            if (group != null) {
                addElementToGroup(group, element);
            }
        }
    }

    /**
     * 통합된 EducationalElement 생성
     */
    private EducationalElement createUnifiedEducationalElement(LayoutBlock layout) {
        String extractedText = getExtractedText(layout);
        EducationalElement element = new EducationalElement(layout.getId(), layout.getClassName(), extractedText);

        // 위치 정보 설정
        element.setPosition(new EducationalElement.Position(
            layout.getX1(), layout.getY1(), layout.getX2(), layout.getY2()));

        // 신뢰도 정보 설정
        TextBlock textBlock = layout.getTextBlock();
        double ocrConfidence = textBlock != null && textBlock.getConfidence() != null ?
                              textBlock.getConfidence() : 0.0;
        element.setConfidence(new EducationalElement.ConfidenceInfo(
            layout.getConfidence(), ocrConfidence));

        // AI 설명 설정
        element.setAiDescription(layout.getAiDescription());

        // 통합된 분류 로직 활용
        performUnifiedElementClassification(element, extractedText, layout.getClassName());

        return element;
    }

    /**
     * 통합된 요소 분류 수행
     */
    private void performUnifiedElementClassification(EducationalElement element,
                                                   String text,
                                                   String originalClass) {
        // 통합된 분류기 활용
        ElementClassifier.ClassificationResult classification =
            elementClassifier.performComprehensiveClassification(originalClass, text);

        // 텍스트 패턴 설정
        EducationalElement.TextPatterns patterns = element.getTextPatterns();
        patterns.setChoicePattern(classification.isChoicePattern());
        patterns.setQuestionNumberPattern(textPatternAnalyzer.isQuestionNumberPattern(text));
        patterns.setTextClassification(classification.getRefinedType());

        // 세분화된 타입 설정
        element.setRefinedType(classification.getRefinedType());

        logger.debug("🔍 통합 분류: {} → {} (그룹: {}, 우선순위: {})",
                    originalClass, classification.getRefinedType(),
                    classification.getGroup(), classification.getPriority());
    }

    /**
     * QuestionGroup에 요소 추가 (기존 로직 활용)
     */
    private void addElementToGroup(QuestionGroup group, EducationalElement element) {
        QuestionGroup.QuestionElements elements = group.getElements();
        String refinedType = element.getRefinedType();

        switch (refinedType) {
            case "question_text" -> elements.getQuestionText().add(element);
            case "question_type" -> elements.getQuestionType().add(element);
            case "title" -> elements.getTitle().add(element);
            case "plain_text", "passage" -> elements.getPlainText().add(element);
            case "list", "choices" -> elements.getListItems().add(element);
            case "figure" -> elements.getFigures().add(element);
            case "table" -> elements.getTables().add(element);
            case "isolated_formula", "formula_caption" -> elements.getFormulas().add(element);
            default -> elements.getPlainText().add(element); // 기본값
        }

        // AI 분석 정보 추가
        addAIAnalysisInfo(group, element, refinedType);

        // 공간 정보 업데이트
        updateSpatialInfo(group, element);
    }

    /**
     * AI 분석 정보 추가
     */
    private void addAIAnalysisInfo(QuestionGroup group, EducationalElement element, String refinedType) {
        if (element.getAiDescription() != null && !element.getAiDescription().trim().isEmpty()) {
            QuestionGroup.AIAnalysis aiAnalysis = group.getAiAnalysis();
            switch (refinedType) {
                case "figure" -> aiAnalysis.getImageDescriptions().add(element.getAiDescription());
                case "table" -> aiAnalysis.getTableAnalysis().add(element.getAiDescription());
                case "isolated_formula", "formula_caption" ->
                    aiAnalysis.getFormulaDescriptions().add(element.getAiDescription());
            }
        }
    }

    /**
     * 공간 정보 업데이트
     */
    private void updateSpatialInfo(QuestionGroup group, EducationalElement element) {
        QuestionGroup.SpatialInfo spatialInfo = group.getSpatialInfo();
        spatialInfo.setTotalElements(spatialInfo.getTotalElements() + 1);
        spatialInfo.setProximityElements(spatialInfo.getProximityElements() + 1);

        // Y 범위 업데이트
        int elementY = element.getPosition().getY1();
        int[] yRange = spatialInfo.getYRange();
        if (yRange[0] == 0 || elementY < yRange[0]) yRange[0] = elementY;
        if (elementY > yRange[1]) yRange[1] = elementY;
    }

    /**
     * 통합 TSPMResult 구성
     */
    private TSPMResult buildUnifiedTSPMResult(Map<String, QuestionGroup> questionGroups,
                                            int unassignedCount) {
        // QuestionGroup을 번호 순으로 정렬
        List<QuestionGroup> sortedGroups = questionGroups.values().stream()
            .sorted(Comparator.comparing(group -> {
                try {
                    return Integer.parseInt(group.getQuestionNumber());
                } catch (NumberFormatException e) {
                    return 999; // 숫자가 아닌 경우 뒤로
                }
            }))
            .collect(Collectors.toList());

        // DocumentInfo 설정
        TSPMResult.DocumentInfo documentInfo = new TSPMResult.DocumentInfo();
        documentInfo.setTotalQuestions(questionGroups.size());
        documentInfo.setLayoutType(determineLayoutType(questionGroups.size()));

        // TSPMResult 생성
        TSPMResult result = new TSPMResult(documentInfo, sortedGroups);

        logger.info("📋 통합 TSPM 결과 구성: 문제 {} 개, 미할당 요소 {} 개",
                   questionGroups.size(), unassignedCount);

        return result;
    }

    /**
     * 분석 결과 최종화
     */
    private void finalizeAnalysisResult(TSPMResult result, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;

        TSPMResult.AnalysisMetadata metadata = result.getAnalysisMetadata();
        metadata.setAnalysisTimestamp(LocalDateTime.now());
        metadata.setProcessingTimeMs(processingTime);
        metadata.setProximityThreshold(SpatialProximityCalculator.DEFAULT_PROXIMITY_THRESHOLD);
    }

    /**
     * 레이아웃 타입 결정
     */
    private String determineLayoutType(int totalQuestions) {
        if (totalQuestions <= 2) return "simple";
        else if (totalQuestions > 5) return "multiple_choice";
        else return "standard";
    }

    /**
     * LayoutBlock에서 텍스트 추출
     */
    private String getExtractedText(LayoutBlock layout) {
        TextBlock textBlock = layout.getTextBlock();
        if (textBlock != null && textBlock.getExtractedText() != null) {
            return textBlock.getExtractedText();
        }
        // fallback: OCR 텍스트 사용
        return layout.getOcrText();
    }

    /**
     * 통합 분석 결과 통계
     */
    public static class AnalysisStatistics {
        private final int totalElements;
        private final int assignedElements;
        private final int unassignedElements;
        private final Map<String, Integer> elementsByType;
        private final long processingTimeMs;

        public AnalysisStatistics(int totalElements, int assignedElements, int unassignedElements,
                                Map<String, Integer> elementsByType, long processingTimeMs) {
            this.totalElements = totalElements;
            this.assignedElements = assignedElements;
            this.unassignedElements = unassignedElements;
            this.elementsByType = elementsByType;
            this.processingTimeMs = processingTimeMs;
        }

        // Getters
        public int getTotalElements() { return totalElements; }
        public int getAssignedElements() { return assignedElements; }
        public int getUnassignedElements() { return unassignedElements; }
        public Map<String, Integer> getElementsByType() { return elementsByType; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public double getAssignmentRate() {
            return totalElements > 0 ? (double) assignedElements / totalElements : 0.0;
        }
    }
}