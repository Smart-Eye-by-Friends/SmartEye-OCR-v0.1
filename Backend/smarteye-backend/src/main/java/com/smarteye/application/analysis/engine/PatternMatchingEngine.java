package com.smarteye.application.analysis.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
import com.smarteye.application.analysis.AnalysisJobService;
import com.smarteye.application.user.UserService;
import com.smarteye.domain.document.entity.DocumentPage;
import com.smarteye.infrastructure.external.*;
import com.smarteye.application.file.*;
 * 통합 패턴 매칭 엔진
 *
 * TSPM 관련 모든 서비스의 중복된 패턴 매칭 로직을 통합
 * Strategy Pattern을 사용하여 다양한 패턴 매칭 전략 지원
 */
@Component
public class PatternMatchingEngine {

    private static final Logger logger = LoggerFactory.getLogger(PatternMatchingEngine.class);

    // ============================================================================
    // 통합된 패턴 정의 (단일 소스 관리)
    // ============================================================================

    /**
     * 문제 번호 패턴들 (모든 TSPM 서비스에서 공통 사용)
     */
    private static final List<Pattern> QUESTION_NUMBER_PATTERNS = Arrays.asList(
        // 🎯 고정밀도 패턴 (1순위)
        Pattern.compile("^\\s*(\\d+)번\\s*"),      // "1번", "2번" (라인 시작)
        Pattern.compile("^\\s*(\\d+)\\.\\s*"),      // "1.", "2." (라인 시작)
        Pattern.compile("^\\s*Q\\s*(\\d+)\\s*"),    // "Q1", "Q2" (라인 시작)
        Pattern.compile("^\\s*문제\\s*(\\d+)\\s*"), // "문제 1", "문제 2" (라인 시작)

        // 중간밀도 패턴 (2순위)
        Pattern.compile("(\\d+)\\s*[)）]\\s*"),  // "1)", "2)", "전각 괄호"
        Pattern.compile("[(（]\\s*(\\d+)\\s*[)）]"), // "(1)", "(2)", "전각 괄호"

        // 저밀도 패턴 (3순위 - 신중히 사용)
        Pattern.compile("\\b(\\d{1,3})\\b"),        // 단순 숫자 (단어 경계에서만)
        Pattern.compile("([1-9]\\d{2,3})")           // 3-4자리 문제번호 (100-9999)
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
            // 🔄 직접 처리 (스태틱 컴텍스트 문제 해결)
            if (text == null || text.trim().isEmpty()) {
                return null;
            }

            String cleanText = text.trim();

            // 고정밀도 패턴 우선 매칭
            for (int i = 0; i < Math.min(4, QUESTION_NUMBER_PATTERNS.size()); i++) {
                Pattern pattern = QUESTION_NUMBER_PATTERNS.get(i);
                Matcher matcher = pattern.matcher(cleanText);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }

            // 중간밀도 패턴
            if (cleanText.length() <= 10) {
                for (int i = 4; i < Math.min(6, QUESTION_NUMBER_PATTERNS.size()); i++) {
                    Pattern pattern = QUESTION_NUMBER_PATTERNS.get(i);
                    Matcher matcher = pattern.matcher(cleanText);
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                }
            }

            // 저밀도 패턴
            if (cleanText.length() <= 5 || cleanText.matches("\\d+")) {
                for (int i = 6; i < QUESTION_NUMBER_PATTERNS.size(); i++) {
                    Pattern pattern = QUESTION_NUMBER_PATTERNS.get(i);
                    Matcher matcher = pattern.matcher(cleanText);
                    if (matcher.find()) {
                        String result = matcher.group(1);
                        int number = Integer.parseInt(result);
                        if (number >= 1 && number <= 999) {
                            return result;
                        }
                    }
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
     * 🔍 강화된 문제 번호 추출 (우선순위 기반 매칭)
     */
    public String extractQuestionNumber(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        String cleanText = text.trim();

        // 🎯 고정밀도 패턴 우선 매칭
        for (int i = 0; i < Math.min(4, QUESTION_NUMBER_PATTERNS.size()); i++) {
            Pattern pattern = QUESTION_NUMBER_PATTERNS.get(i);
            Matcher matcher = pattern.matcher(cleanText);
            if (matcher.find()) {
                String result = matcher.group(1);
                logger.trace("✅ 고정밀도 패턴 매칭: '{}' → '{}' (패턴 {})",
                            cleanText, result, i + 1);
                return result;
            }
        }

        // 📊 중간밀도 패턴 (짧은 텍스트에서만)
        if (cleanText.length() <= 10) {
            for (int i = 4; i < Math.min(6, QUESTION_NUMBER_PATTERNS.size()); i++) {
                Pattern pattern = QUESTION_NUMBER_PATTERNS.get(i);
                Matcher matcher = pattern.matcher(cleanText);
                if (matcher.find()) {
                    String result = matcher.group(1);
                    logger.trace("✅ 중간밀도 패턴 매칭: '{}' → '{}' (패턴 {})",
                                cleanText, result, i + 1);
                    return result;
                }
            }
        }

        // 🔄 저밀도 패턴 (매우 짧은 텍스트 또는 숫자만 있는 경우)
        if (cleanText.length() <= 5 || cleanText.matches("\\d+")) {
            for (int i = 6; i < QUESTION_NUMBER_PATTERNS.size(); i++) {
                Pattern pattern = QUESTION_NUMBER_PATTERNS.get(i);
                Matcher matcher = pattern.matcher(cleanText);
                if (matcher.find()) {
                    String result = matcher.group(1);
                    int number = Integer.parseInt(result);

                    // 합리성 검증: 1-999 범위만 허용
                    if (number >= 1 && number <= 999) {
                        logger.trace("⚠️ 저밀도 패턴 매칭: '{}' → '{}' (패턴 {})",
                                    cleanText, result, i + 1);
                        return result;
                    }
                }
            }
        }

        logger.trace("❌ 문제번호 추출 실패: '{}'", cleanText);
        return null;
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