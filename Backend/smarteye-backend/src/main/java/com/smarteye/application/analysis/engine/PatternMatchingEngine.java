package com.smarteye.application.analysis.engine;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
import com.smarteye.application.analysis.AnalysisJobService;
import com.smarteye.application.user.UserService;
import com.smarteye.domain.document.DocumentPage;
import com.smarteye.infrastructure.external.*;
import com.smarteye.application.file.*;
 * 통합 패턴 매칭 엔진
 *
 * TSPM 관련 모든 서비스의 중복된 패턴 매칭 로직을 통합
 * Strategy Pattern을 사용하여 다양한 패턴 매칭 전략 지원
 */
@Component
public class PatternMatchingEngine {

    // ============================================================================
    // 통합된 패턴 정의 (단일 소스 관리)
    // ============================================================================

    /**
     * 문제 번호 패턴들 (모든 TSPM 서비스에서 공통 사용)
     */
    private static final List<Pattern> QUESTION_NUMBER_PATTERNS = Arrays.asList(
        Pattern.compile("(\\d+)번"),           // 1번, 2번 형식
        Pattern.compile("(\\d+)\\."),          // 1., 2. 형식
        Pattern.compile("문제\\s*(\\d+)"),     // 문제 1, 문제 2 형식
        Pattern.compile("(\\d+)\\s*(?:\\)|）)"), // 1), 2) 형식
        Pattern.compile("Q\\s*(\\d+)"),        // Q1, Q2 형식
        Pattern.compile("(\\d{2,3})")          // 593, 594 등 문제번호
    );

    /**
     * 선택지 패턴들 (모든 구조 분석에서 공통 사용)
     */
    private static final List<Pattern> CHOICE_PATTERNS = Arrays.asList(
        Pattern.compile("^[①②③④⑤⑥⑦⑧⑨⑩]"),    // 원문자 선택지
        Pattern.compile("^[(（]\\s*[1-5]\\s*[)）]"),  // (1), (2) 형식
        Pattern.compile("^[1-5]\\s*[.．]")           // 1., 2. 형식
    );

    /**
     * 섹션 패턴들
     */
    private static final List<Pattern> SECTION_PATTERNS = Arrays.asList(
        Pattern.compile("([A-Z])\\s*섹션"),    // A섹션, B섹션
        Pattern.compile("([A-Z])\\s*부분"),    // A부분, B부분
        Pattern.compile("([A-Z])\\s+")         // A, B (단독)
    );

    /**
     * 교육 문서 유형별 패턴들
     */
    private static final Map<String, List<Pattern>> EDUCATIONAL_PATTERNS = Map.of(
        "passage", Arrays.asList(
            Pattern.compile("다음을\\s*읽고"),
            Pattern.compile("아래의?\\s*(그림|표|내용)"),
            Pattern.compile("위의?\\s*(그림|표|문제)")
        ),
        "explanation", Arrays.asList(
            Pattern.compile("(설명|해설|풀이)\\s*:"),
            Pattern.compile("정답\\s*:"),
            Pattern.compile("해답\\s*:")
        )
    );

    // ============================================================================
    // 패턴 매칭 인터페이스
    // ============================================================================

    /**
     * 패턴 매칭 전략 인터페이스
     */
    public interface PatternMatchingStrategy {
        boolean matches(String text);
        String extract(String text);
        int getPriority();
    }

    /**
     * 문제 번호 매칭 전략
     */
    public static class QuestionNumberStrategy implements PatternMatchingStrategy {
        @Override
        public boolean matches(String text) {
            return QUESTION_NUMBER_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(text).find());
        }

        @Override
        public String extract(String text) {
            for (Pattern pattern : QUESTION_NUMBER_PATTERNS) {
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            return null;
        }

        @Override
        public int getPriority() { return 1; }
    }

    /**
     * 선택지 매칭 전략
     */
    public static class ChoicePatternStrategy implements PatternMatchingStrategy {
        @Override
        public boolean matches(String text) {
            return CHOICE_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(text).find());
        }

        @Override
        public String extract(String text) {
            for (Pattern pattern : CHOICE_PATTERNS) {
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    return matcher.group(0);
                }
            }
            return null;
        }

        @Override
        public int getPriority() { return 2; }
    }

    // ============================================================================
    // 통합 매칭 메서드들
    // ============================================================================

    /**
     * 문제 번호 추출 (모든 TSMP 서비스의 공통 로직 통합)
     */
    public String extractQuestionNumber(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        return new QuestionNumberStrategy().extract(text.trim());
    }

    /**
     * 선택지 패턴 확인
     */
    public boolean isChoicePattern(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        return new ChoicePatternStrategy().matches(text.trim());
    }

    /**
     * 문제 번호 패턴 확인
     */
    public boolean isQuestionNumberPattern(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        return new QuestionNumberStrategy().matches(text.trim());
    }

    /**
     * 교육 문서 유형 분류
     */
    public String classifyEducationalContent(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "unknown";
        }

        String trimmedText = text.trim();

        // 우선순위별 패턴 매칭
        for (Map.Entry<String, List<Pattern>> entry : EDUCATIONAL_PATTERNS.entrySet()) {
            String contentType = entry.getKey();
            List<Pattern> patterns = entry.getValue();

            boolean matches = patterns.stream()
                .anyMatch(pattern -> pattern.matcher(trimmedText).find());

            if (matches) {
                return contentType;
            }
        }

        return "plain_text";
    }

    /**
     * 다중 패턴 매칭 (복합 분류)
     */
    public PatternMatchingResult performComprehensiveMatching(String text, String className) {
        if (text == null || text.trim().isEmpty()) {
            return new PatternMatchingResult("unknown", false, false, 0);
        }

        String trimmedText = text.trim();

        // 각 패턴 전략 적용
        boolean isQuestionNumber = isQuestionNumberPattern(trimmedText);
        boolean isChoice = isChoicePattern(trimmedText);
        String educationalType = classifyEducationalContent(trimmedText);

        // 우선순위 결정
        int priority = determinePriority(className, isQuestionNumber, isChoice);

        // 최종 타입 결정
        String finalType = determineFinalType(className, educationalType, isQuestionNumber, isChoice);

        return new PatternMatchingResult(finalType, isQuestionNumber, isChoice, priority);
    }

    /**
     * 우선순위 결정 로직
     */
    private int determinePriority(String className, boolean isQuestionNumber, boolean isChoice) {
        if (isQuestionNumber) return 1;
        if ("question_text".equals(className)) return 2;
        if (isChoice) return 3;
        if ("figure".equals(className)) return 4;
        if ("table".equals(className)) return 5;
        return 6;
    }

    /**
     * 최종 타입 결정 로직
     */
    private String determineFinalType(String className, String educationalType,
                                    boolean isQuestionNumber, boolean isChoice) {
        // 패턴 기반 우선순위
        if (isQuestionNumber) return "question_number";
        if (isChoice) return "choices";

        // 교육 콘텐츠 타입 우선
        if (!"plain_text".equals(educationalType)) return educationalType;

        // 원본 클래스명 유지
        return className != null ? className : "unknown";
    }

    /**
     * 패턴 매칭 결과 클래스
     */
    public static class PatternMatchingResult {
        private final String finalType;
        private final boolean isQuestionNumber;
        private final boolean isChoice;
        private final int priority;

        public PatternMatchingResult(String finalType, boolean isQuestionNumber,
                                   boolean isChoice, int priority) {
            this.finalType = finalType;
            this.isQuestionNumber = isQuestionNumber;
            this.isChoice = isChoice;
            this.priority = priority;
        }

        // Getters
        public String getFinalType() { return finalType; }
        public boolean isQuestionNumber() { return isQuestionNumber; }
        public boolean isChoice() { return isChoice; }
        public int getPriority() { return priority; }
    }
}