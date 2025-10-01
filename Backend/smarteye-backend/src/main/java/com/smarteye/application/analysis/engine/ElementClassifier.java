package com.smarteye.application.analysis.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import com.smarteye.application.analysis.AnalysisJobService;
import com.smarteye.application.user.UserService;
import com.smarteye.domain.document.entity.DocumentPage;
import com.smarteye.infrastructure.external.*;
import com.smarteye.application.file.*;
/**
 * 요소 분류 통합 유틸리티
 *
 * 기존 TSPMEngine과 StructuredAnalysisService에 분산되어 있던
 * 텍스트 요소 분류 로직을 통합
 *
 * SOLID 원칙 적용:
 * - 단일 책임: 요소 분류만 담당
 * - 의존성 역전: TextPatternAnalyzer 추상화 의존
 * - 리스코프 치환: 분류 결과 일관성 보장
 */
@Component
public class ElementClassifier {

    private static final Logger logger = LoggerFactory.getLogger(ElementClassifier.class);

    @Autowired
    private TextPatternAnalyzer textPatternAnalyzer;

    // 실제 LAM 클래스 기반 교육 문서 요소 우선순위
    public static final Map<String, Integer> EDUCATIONAL_PRIORITY = Map.of(
        "question_number", 1,    // 문제 번호 (문제 구분 기준)
        "question_text", 2,      // 문제 내용
        "question_type", 3,      // 문제 유형
        "title", 4,              // 제목/소제목
        "figure", 5,             // 이미지/그림
        "table", 6,              // 표
        "list", 7,               // 선택지 (패턴 매칭 필요)
        "plain_text", 8,         // 지문/설명
        "isolated_formula", 9,   // 수식
        "formula_caption", 10    // 수식 설명
    );

    /**
     * 텍스트 요소 분류
     * 기존 StructuredAnalysisService.classifyTextElement() 통합
     *
     * @param text 분류할 텍스트
     * @return 분류된 요소 타입
     */
    public String classifyTextElement(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "unknown";
        }

        String trimmedText = text.trim();

        // 1. 선택지 패턴 체크
        if (textPatternAnalyzer.isChoicePattern(trimmedText)) {
            return "choices";
        }

        // 2. 지문 패턴 체크
        if (textPatternAnalyzer.isPassagePattern(trimmedText)) {
            return "passage";
        }

        // 3. 설명/해설 패턴 체크
        if (textPatternAnalyzer.isExplanationPattern(trimmedText)) {
            return "explanations";
        }

        // 4. 문제 번호 패턴 체크
        if (textPatternAnalyzer.isQuestionNumberPattern(trimmedText)) {
            return "question_number";
        }

        // 5. 기본은 문제 텍스트
        return "question_text";
    }

    /**
     * 세분화된 타입 결정
     * 기존 TSPMEngine.determineRefinedType() 통합
     *
     * @param originalClass LAM 클래스 원본
     * @param text 텍스트 내용
     * @param isChoicePattern 선택지 패턴 여부
     * @return 세분화된 타입
     */
    public String determineRefinedType(String originalClass, String text, boolean isChoicePattern) {
        if (originalClass == null) {
            return classifyTextElement(text);
        }

        // list 클래스의 경우 텍스트 패턴으로 세분화
        if ("list".equals(originalClass) && isChoicePattern) {
            return "choices";
        }

        // plain_text의 경우 내용으로 세분화
        if ("plain_text".equals(originalClass)) {
            if (textPatternAnalyzer.isPassagePattern(text)) {
                return "passage";
            } else if (textPatternAnalyzer.isExplanationPattern(text)) {
                return "explanation";
            }
        }

        // question_text 계열 통합
        if ("question_text".equals(originalClass) || "question_type".equals(originalClass)) {
            return "question_text";
        }

        // 기본적으로 LAM 원본 클래스 사용
        return originalClass;
    }

    /**
     * 요소 타입별 그룹 결정
     * 문제 구조에서 어느 그룹에 속할지 결정
     *
     * @param refinedType 세분화된 타입
     * @return 그룹명
     */
    public String determineElementGroup(String refinedType) {
        if (refinedType == null) {
            return "others";
        }

        return switch (refinedType) {
            case "question_text", "question_type", "question_number" -> "questionText";
            case "plain_text", "passage" -> "plainText";
            case "list", "choices" -> "listItems";
            case "figure" -> "figures";
            case "table" -> "tables";
            case "isolated_formula", "formula_caption" -> "formulas";
            case "title" -> "title";
            case "explanation", "explanations" -> "explanations";
            default -> "others";
        };
    }

    /**
     * 요소 우선순위 반환
     *
     * @param className 클래스명
     * @return 우선순위 (낮을수록 높은 우선순위)
     */
    public int getElementPriority(String className) {
        return EDUCATIONAL_PRIORITY.getOrDefault(className, 999);
    }

    /**
     * 분류 결과 상세 정보
     */
    public static class ClassificationResult {
        private final String elementType;
        private final String refinedType;
        private final String group;
        private final int priority;
        private final boolean isChoicePattern;
        private final boolean isPassagePattern;
        private final boolean isExplanationPattern;

        public ClassificationResult(String elementType, String refinedType, String group, int priority,
                                  boolean isChoicePattern, boolean isPassagePattern, boolean isExplanationPattern) {
            this.elementType = elementType;
            this.refinedType = refinedType;
            this.group = group;
            this.priority = priority;
            this.isChoicePattern = isChoicePattern;
            this.isPassagePattern = isPassagePattern;
            this.isExplanationPattern = isExplanationPattern;
        }

        // Getters
        public String getElementType() { return elementType; }
        public String getRefinedType() { return refinedType; }
        public String getGroup() { return group; }
        public int getPriority() { return priority; }
        public boolean isChoicePattern() { return isChoicePattern; }
        public boolean isPassagePattern() { return isPassagePattern; }
        public boolean isExplanationPattern() { return isExplanationPattern; }
    }

    /**
     * 종합적인 요소 분류 수행
     *
     * @param originalClass LAM 원본 클래스
     * @param text 텍스트 내용
     * @return 상세 분류 결과
     */
    public ClassificationResult performComprehensiveClassification(String originalClass, String text) {
        // 패턴 분석
        boolean isChoicePattern = textPatternAnalyzer.isChoicePattern(text);
        boolean isPassagePattern = textPatternAnalyzer.isPassagePattern(text);
        boolean isExplanationPattern = textPatternAnalyzer.isExplanationPattern(text);

        // 요소 타입 결정
        String elementType = classifyTextElement(text);

        // 세분화된 타입 결정
        String refinedType = determineRefinedType(originalClass, text, isChoicePattern);

        // 그룹 결정
        String group = determineElementGroup(refinedType);

        // 우선순위 결정
        int priority = getElementPriority(refinedType);

        logger.debug("🔍 종합 분류: {} → {} → {} (우선순위: {}, 선택지: {}, 지문: {}, 설명: {})",
                    originalClass, elementType, refinedType, priority,
                    isChoicePattern, isPassagePattern, isExplanationPattern);

        return new ClassificationResult(elementType, refinedType, group, priority,
                                      isChoicePattern, isPassagePattern, isExplanationPattern);
    }
}