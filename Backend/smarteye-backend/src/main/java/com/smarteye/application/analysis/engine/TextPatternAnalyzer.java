package com.smarteye.application.analysis.engine;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.smarteye.application.analysis.AnalysisJobService;
import com.smarteye.application.user.UserService;
import com.smarteye.domain.document.DocumentPage;
import com.smarteye.infrastructure.external.*;
import com.smarteye.application.file.*;
/**
 * 텍스트 패턴 분석 통합 유틸리티
 *
 * 기존 TSPMEngine, StructuredAnalysisService, StructuredJSONService에
 * 분산되어 있던 정규표현식 패턴과 텍스트 분석 로직을 통합
 *
 * SOLID 원칙 적용:
 * - 단일 책임: 텍스트 패턴 분석만 담당
 * - 개방-폐쇄: 새로운 패턴 추가 가능, 기존 코드 수정 불필요
 */
@Component
public class TextPatternAnalyzer {

    // 문제 번호 패턴 - 통합된 단일 소스
    public static final List<Pattern> QUESTION_NUMBER_PATTERNS = Arrays.asList(
        Pattern.compile("(\\d+)번"),           // 1번, 2번 형식
        Pattern.compile("(\\d+)\\."),          // 1., 2. 형식
        Pattern.compile("문제\\s*(\\d+)"),     // 문제 1, 문제 2 형식
        Pattern.compile("(\\d+)\\s*(?:\\)|）)"), // 1), 2) 형식
        Pattern.compile("Q\\s*(\\d+)"),        // Q1, Q2 형식
        Pattern.compile("(\\d{2,3})")          // 593, 594 등 문제번호
    );

    // 선택지 패턴 - 통합된 단일 소스
    public static final List<Pattern> CHOICE_PATTERNS = Arrays.asList(
        Pattern.compile("^[①②③④⑤⑥⑦⑧⑨⑩]"),    // 원문자 선택지
        Pattern.compile("^[(（]\\s*[1-5]\\s*[)）]"),  // (1), (2) 형식
        Pattern.compile("^[1-5]\\s*[.．]")           // 1., 2. 형식
    );

    // 섹션 패턴 - 확장 가능한 구조
    public static final List<Pattern> SECTION_PATTERNS = Arrays.asList(
        Pattern.compile("([A-Z])\\s*섹션"),    // A섹션, B섹션
        Pattern.compile("([A-Z])\\s*부분"),    // A부분, B부분
        Pattern.compile("([A-Z])\\s+")         // A, B (단독)
    );

    // 선택지 번호 추출 패턴
    private static final List<Pattern> CHOICE_NUMBER_PATTERNS = Arrays.asList(
        Pattern.compile("^([①②③④⑤⑥⑦⑧⑨⑩])"),
        Pattern.compile("^[(（]\\s*([1-5])\\s*[)）]"),
        Pattern.compile("^([1-5])\\s*[.．]")
    );

    /**
     * 텍스트에서 문제 번호 추출
     * 기존 TSPMEngine.extractQuestionNumber() 통합
     *
     * @param text 분석할 텍스트
     * @return 추출된 문제 번호 (없으면 null)
     */
    public String extractQuestionNumber(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        for (Pattern pattern : QUESTION_NUMBER_PATTERNS) {
            Matcher matcher = pattern.matcher(text.trim());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /**
     * 선택지 패턴 여부 확인
     * 기존 여러 서비스의 중복 로직 통합
     *
     * @param text 분석할 텍스트
     * @return 선택지 패턴 여부
     */
    public boolean isChoicePattern(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        return CHOICE_PATTERNS.stream()
            .anyMatch(pattern -> pattern.matcher(text.trim()).find());
    }

    /**
     * 문제 번호 패턴 여부 확인
     *
     * @param text 분석할 텍스트
     * @return 문제 번호 패턴 여부
     */
    public boolean isQuestionNumberPattern(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        return QUESTION_NUMBER_PATTERNS.stream()
            .anyMatch(pattern -> pattern.matcher(text.trim()).find());
    }

    /**
     * 선택지 번호 추출
     * 기존 StructuredJSONService.extractChoiceNumber() 통합
     *
     * @param text 선택지 텍스트
     * @return 추출된 선택지 번호
     */
    public String extractChoiceNumber(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        for (Pattern pattern : CHOICE_NUMBER_PATTERNS) {
            Matcher matcher = pattern.matcher(text.trim());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return "";
    }

    /**
     * 섹션 이름 추출
     *
     * @param text 분석할 텍스트
     * @return 추출된 섹션 이름 (없으면 null)
     */
    public String extractSectionName(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        for (Pattern pattern : SECTION_PATTERNS) {
            Matcher matcher = pattern.matcher(text.trim());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    /**
     * 지문 패턴 확인
     * 기존 여러 서비스의 중복 로직 통합
     *
     * @param text 분석할 텍스트
     * @return 지문 패턴 여부
     */
    public boolean isPassagePattern(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String trimmed = text.trim();
        return trimmed.contains("다음을") || trimmed.contains("아래의") ||
               trimmed.contains("위의") || trimmed.contains("그림을") ||
               trimmed.contains("표를");
    }

    /**
     * 설명/해설 패턴 확인
     * 기존 여러 서비스의 중복 로직 통합
     *
     * @param text 분석할 텍스트
     * @return 설명/해설 패턴 여부
     */
    public boolean isExplanationPattern(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String trimmed = text.trim();
        return trimmed.contains("설명") || trimmed.contains("해설") ||
               trimmed.contains("풀이") || trimmed.contains("답:");
    }
}