package com.smarteye.application.analysis.engine;

import com.smarteye.presentation.dto.AIDescriptionResult;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.application.analysis.UnifiedAnalysisEngine.StructuredData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.smarteye.application.analysis.AnalysisJobService;
import com.smarteye.application.user.UserService;
import com.smarteye.domain.document.DocumentPage;
import com.smarteye.infrastructure.external.*;
import com.smarteye.application.file.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 구조화된 분석 템플릿
 *
 * Template Method Pattern을 사용하여
 * StructuredAnalysisService, StructuredJSONService, CIMService의
 * 공통 분석 워크플로우를 통합
 *
 * 템플릿 워크플로우:
 * 1. 문제 구조 감지
 * 2. 요소 분류 및 그룹핑
 * 3. AI 결과 매핑
 * 4. 구조화된 데이터 생성
 * 5. 형식 변환
 */
@Component
public abstract class StructuredAnalysisTemplate {

    private static final Logger logger = LoggerFactory.getLogger(StructuredAnalysisTemplate.class);

    @Autowired
    protected PatternMatchingEngine patternMatchingEngine;

    @Autowired
    protected SpatialAnalysisEngine spatialAnalysisEngine;

    // ============================================================================
    // 템플릿 메서드 (공통 워크플로우)
    // ============================================================================

    /**
     * 메인 템플릿 메서드 - 전체 구조화된 분석 워크플로우 정의
     */
    public final StructuredData performStructuredAnalysis(
            List<OCRResult> ocrResults,
            List<AIDescriptionResult> aiResults,
            List<LayoutInfo> layoutElements) {

        logger.info("🔄 구조화된 분석 시작 - OCR: {}개, AI: {}개, Layout: {}개",
                   ocrResults.size(), aiResults.size(), layoutElements.size());

        long startTime = System.currentTimeMillis();

        try {
            // 1. 전처리 (하위 클래스에서 커스터마이징 가능)
            PreprocessingResult preprocessed = preprocessData(ocrResults, aiResults, layoutElements);

            // 2. 문제 구조 감지 (공통 로직)
            QuestionStructureResult questionStructure = detectQuestionStructure(
                preprocessed.ocrResults, preprocessed.layoutElements);

            // 3. 요소 분류 및 그룹핑 (공통 로직)
            ElementClassificationResult classification = classifyAndGroupElements(
                preprocessed.ocrResults, preprocessed.aiResults, preprocessed.layoutElements);

            // 4. AI 결과 매핑 (공통 로직)
            AIResultMapping aiMapping = mapAIResultsToQuestions(
                preprocessed.aiResults, questionStructure);

            // 5. 구조화된 데이터 생성 (하위 클래스에서 커스터마이징)
            StructuredDataResult structuredData = generateStructuredData(
                questionStructure, classification, aiMapping);

            // 6. 최종 형식 변환 (하위 클래스별 구현)
            StructuredData finalResult = convertToFinalFormat(structuredData);

            // 7. 후처리 (하위 클래스에서 커스터마이징 가능)
            postProcessResult(finalResult, startTime);

            logger.info("✅ 구조화된 분석 완료 - 처리시간: {}ms, 문제수: {}개",
                       System.currentTimeMillis() - startTime,
                       finalResult.getQuestions() != null ? finalResult.getQuestions().size() : 0);

            return finalResult;

        } catch (Exception e) {
            logger.error("❌ 구조화된 분석 실패", e);
            return createErrorResult(e.getMessage());
        }
    }

    // ============================================================================
    // 추상 메서드들 (하위 클래스에서 구현)
    // ============================================================================

    /**
     * 전처리 로직 (하위 클래스에서 커스터마이징)
     */
    protected PreprocessingResult preprocessData(List<OCRResult> ocrResults,
                                               List<AIDescriptionResult> aiResults,
                                               List<LayoutInfo> layoutElements) {
        // 기본 구현: 데이터를 그대로 전달
        return new PreprocessingResult(ocrResults, aiResults, layoutElements);
    }

    /**
     * 구조화된 데이터 생성 (하위 클래스별 특화 구현)
     */
    protected abstract StructuredDataResult generateStructuredData(
            QuestionStructureResult questionStructure,
            ElementClassificationResult classification,
            AIResultMapping aiMapping);

    /**
     * 최종 형식 변환 (하위 클래스별 구현)
     */
    protected abstract StructuredData convertToFinalFormat(StructuredDataResult structuredData);

    /**
     * 후처리 로직 (하위 클래스에서 커스터마이징 가능)
     */
    protected void postProcessResult(StructuredData result, long startTime) {
        // 기본 구현: 처리시간 설정
        long processingTime = System.currentTimeMillis() - startTime;
        logger.debug("⏱️ 후처리 완료 - 처리시간: {}ms", processingTime);
    }

    // ============================================================================
    // 공통 구현 메서드들
    // ============================================================================

    /**
     * 문제 구조 감지 (공통 로직 - 통합 엔진 사용)
     */
    protected QuestionStructureResult detectQuestionStructure(List<OCRResult> ocrResults,
                                                            List<LayoutInfo> layoutElements) {
        logger.debug("📝 문제 구조 감지 시작");

        Map<String, QuestionInfo> questions = new HashMap<>();
        Map<String, SectionInfo> sections = new HashMap<>();

        // 1. 통합 패턴 매칭 엔진을 사용한 문제 번호 추출
        for (OCRResult ocr : ocrResults) {
            String text = ocr.getText();
            if (text == null) continue;

            String questionNumber = patternMatchingEngine.extractQuestionNumber(text);
            if (questionNumber != null) {
                QuestionInfo questionInfo = new QuestionInfo();
                questionInfo.questionNumber = questionNumber;
                questionInfo.ocrResult = ocr;
                questionInfo.position = ocr.getCoordinates() != null && ocr.getCoordinates().length > 1
                    ? ocr.getCoordinates()[1] : 0;

                questions.put(questionNumber, questionInfo);
            }
        }

        // 2. 관련 요소들 매핑 (통합 공간 엔진 사용)
        for (String questionNum : questions.keySet()) {
            QuestionInfo question = questions.get(questionNum);
            if (question.ocrResult != null && question.ocrResult.getCoordinates() != null) {
                // 공간 엔진을 사용하여 관련 레이아웃 요소 찾기
                // 실제 구현시 좌표 기반 매핑 로직 추가
            }
        }

        return new QuestionStructureResult(questions, sections, determineLayoutType(questions.size()));
    }

    /**
     * 요소 분류 및 그룹핑 (공통 로직 - 통합 엔진 사용)
     */
    protected ElementClassificationResult classifyAndGroupElements(
            List<OCRResult> ocrResults,
            List<AIDescriptionResult> aiResults,
            List<LayoutInfo> layoutElements) {

        logger.debug("🏷️ 요소 분류 및 그룹핑 시작");

        Map<String, List<ClassifiedElement>> classifiedElements = new HashMap<>();

        // OCR 결과 분류
        for (OCRResult ocr : ocrResults) {
            ClassifiedElement element = classifyOCRElement(ocr);
            classifiedElements.computeIfAbsent(element.category, k -> new ArrayList<>()).add(element);
        }

        // 레이아웃 요소 분류
        for (LayoutInfo layout : layoutElements) {
            ClassifiedElement element = classifyLayoutElement(layout);
            classifiedElements.computeIfAbsent(element.category, k -> new ArrayList<>()).add(element);
        }

        return new ElementClassificationResult(classifiedElements);
    }

    /**
     * AI 결과를 문제별로 매핑 (공통 로직 - 공간 엔진 사용)
     */
    protected AIResultMapping mapAIResultsToQuestions(List<AIDescriptionResult> aiResults,
                                                    QuestionStructureResult questionStructure) {
        logger.debug("🤖 AI 결과 매핑 시작");

        Map<String, List<AIDescriptionResult>> aiByQuestion = new HashMap<>();

        // 각 AI 결과를 가장 가까운 문제에 할당
        for (AIDescriptionResult aiResult : aiResults) {
            String assignedQuestion = findNearestQuestionForAI(aiResult, questionStructure);
            aiByQuestion.computeIfAbsent(assignedQuestion, k -> new ArrayList<>()).add(aiResult);
        }

        return new AIResultMapping(aiByQuestion);
    }

    // ============================================================================
    // 유틸리티 메서드들
    // ============================================================================

    private ClassifiedElement classifyOCRElement(OCRResult ocr) {
        String text = ocr.getText();
        PatternMatchingEngine.PatternMatchingResult matchingResult =
            patternMatchingEngine.performComprehensiveMatching(text, ocr.getClassName());

        ClassifiedElement element = new ClassifiedElement();
        element.ocrResult = ocr;
        element.category = matchingResult.getFinalType();
        element.confidence = 0.9; // OCR 기본 신뢰도
        element.isQuestionNumber = matchingResult.isQuestionNumber();
        element.isChoice = matchingResult.isChoice();

        return element;
    }

    private ClassifiedElement classifyLayoutElement(LayoutInfo layout) {
        ClassifiedElement element = new ClassifiedElement();
        element.layoutInfo = layout;
        element.category = layout.getClassName() != null ? layout.getClassName() : "unknown";
        element.confidence = layout.getConfidence();

        return element;
    }

    private String findNearestQuestionForAI(AIDescriptionResult aiResult,
                                          QuestionStructureResult questionStructure) {
        if (aiResult.getCoordinates() == null || aiResult.getCoordinates().length < 2) {
            return "unknown";
        }

        int aiY = aiResult.getCoordinates()[1];
        Map<String, Integer> questionPositions = new HashMap<>();

        for (QuestionInfo question : questionStructure.questions.values()) {
            questionPositions.put(question.questionNumber, question.position);
        }

        return spatialAnalysisEngine.assignElementToNearestQuestion(aiY, questionPositions);
    }

    private String determineLayoutType(int totalQuestions) {
        if (totalQuestions <= 2) return "simple";
        else if (totalQuestions > 5) return "multiple_choice";
        else return "standard";
    }

    private StructuredData createErrorResult(String errorMessage) {
        StructuredData errorResult = new StructuredData();
        // 오류 결과 구조 생성
        logger.error("구조화된 분석 오류 결과 생성: {}", errorMessage);
        return errorResult;
    }

    // ============================================================================
    // 내부 데이터 클래스들
    // ============================================================================

    protected static class PreprocessingResult {
        public final List<OCRResult> ocrResults;
        public final List<AIDescriptionResult> aiResults;
        public final List<LayoutInfo> layoutElements;

        public PreprocessingResult(List<OCRResult> ocrResults,
                                 List<AIDescriptionResult> aiResults,
                                 List<LayoutInfo> layoutElements) {
            this.ocrResults = ocrResults;
            this.aiResults = aiResults;
            this.layoutElements = layoutElements;
        }
    }

    protected static class QuestionStructureResult {
        public final Map<String, QuestionInfo> questions;
        public final Map<String, SectionInfo> sections;
        public final String layoutType;

        public QuestionStructureResult(Map<String, QuestionInfo> questions,
                                     Map<String, SectionInfo> sections,
                                     String layoutType) {
            this.questions = questions;
            this.sections = sections;
            this.layoutType = layoutType;
        }
    }

    protected static class QuestionInfo {
        public String questionNumber;
        public OCRResult ocrResult;
        public int position;
        public List<LayoutInfo> relatedElements = new ArrayList<>();
    }

    protected static class SectionInfo {
        public String sectionName;
        public int[] coordinates;
        public int yPosition;
    }

    protected static class ElementClassificationResult {
        public final Map<String, List<ClassifiedElement>> classifiedElements;

        public ElementClassificationResult(Map<String, List<ClassifiedElement>> classifiedElements) {
            this.classifiedElements = classifiedElements;
        }
    }

    protected static class ClassifiedElement {
        public OCRResult ocrResult;
        public LayoutInfo layoutInfo;
        public AIDescriptionResult aiResult;
        public String category;
        public double confidence;
        public boolean isQuestionNumber;
        public boolean isChoice;
    }

    protected static class AIResultMapping {
        public final Map<String, List<AIDescriptionResult>> aiByQuestion;

        public AIResultMapping(Map<String, List<AIDescriptionResult>> aiByQuestion) {
            this.aiByQuestion = aiByQuestion;
        }
    }

    protected static class StructuredDataResult {
        public final Map<String, Object> data;

        public StructuredDataResult(Map<String, Object> data) {
            this.data = data;
        }
    }
}