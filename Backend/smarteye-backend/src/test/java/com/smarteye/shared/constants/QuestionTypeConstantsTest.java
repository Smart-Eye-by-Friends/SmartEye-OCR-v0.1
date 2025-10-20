package com.smarteye.shared.constants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QuestionTypeConstants 단위 테스트.
 *
 * <p>v0.7 question_type 독립 영역 처리를 위한 ID 생성/검증 유틸리티 테스트입니다.</p>
 *
 * @author SmartEye Development Team
 * @version 0.7
 * @since 2025-10-18
 */
@DisplayName("QuestionTypeConstants 테스트")
class QuestionTypeConstantsTest {

    // ========================================
    // generateIdentifier() 테스트
    // ========================================

    @Test
    @DisplayName("generateIdentifier - 정상 케이스: 짧은 텍스트")
    void testGenerateIdentifier_Short() {
        String result = QuestionTypeConstants.generateIdentifier(5, "유형01");
        
        assertTrue(result.startsWith("type_5_"));
        assertTrue(result.contains("유형01"));
        assertEquals("type_5_유형01", result);
    }

    @Test
    @DisplayName("generateIdentifier - 정상 케이스: 공백 포함")
    void testGenerateIdentifier_WithSpaces() {
        String result = QuestionTypeConstants.generateIdentifier(12, "문제 유형 A");
        
        assertEquals("type_12_문제_유형_A", result);
    }

    @Test
    @DisplayName("generateIdentifier - 정상 케이스: 특수문자 제거")
    void testGenerateIdentifier_WithSpecialChars() {
        String result = QuestionTypeConstants.generateIdentifier(7, "유형@#1!");
        
        assertEquals("type_7_유형1", result);
    }

    @Test
    @DisplayName("generateIdentifier - 긴 텍스트: Hash suffix 추가")
    void testGenerateIdentifier_LongText() {
        // 50자를 확실히 넘는 텍스트 생성 (52자)
        String longText = "12345678901234567890123456789012345678901234567890ab";  // 52자
        String result = QuestionTypeConstants.generateIdentifier(10, longText);
        
        assertTrue(result.startsWith("type_10_"));
        // Hash suffix가 추가되었으므로 50자 + hash가 포함되어야 함
        assertTrue(result.length() > 58); // "type_10_" (8) + 50 + "_" (1) + 최소 1자
        assertTrue(result.contains("_")); // Hash suffix 구분자 확인
    }

    @Test
    @DisplayName("generateIdentifier - 예외: null 텍스트")
    void testGenerateIdentifier_NullText() {
        assertThrows(IllegalArgumentException.class, () -> {
            QuestionTypeConstants.generateIdentifier(1, null);
        });
    }

    @Test
    @DisplayName("generateIdentifier - 예외: 빈 텍스트")
    void testGenerateIdentifier_EmptyText() {
        assertThrows(IllegalArgumentException.class, () -> {
            QuestionTypeConstants.generateIdentifier(1, "");
        });
    }

    // ========================================
    // isQuestionTypeIdentifier() 테스트
    // ========================================

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("provideValidIdentifiers")
    @DisplayName("isQuestionTypeIdentifier - 유효한 ID들")
    void testIsQuestionTypeIdentifier_Valid(String identifier, boolean expected) {
        assertEquals(expected, QuestionTypeConstants.isQuestionTypeIdentifier(identifier));
    }

    static Stream<Arguments> provideValidIdentifiers() {
        return Stream.of(
            Arguments.of("type_5_유형01", true),
            Arguments.of("type_12_문제_유형_A", true),
            Arguments.of("type_100_test", true),
            Arguments.of("type_1_a", true),
            
            // 잘못된 형식
            Arguments.of("type_5", false),              // 텍스트 부분 없음
            Arguments.of("type__text", false),           // layoutId 없음
            Arguments.of("question_5_text", false),      // 잘못된 접두사
            Arguments.of("5_text", false),               // 접두사 없음
            Arguments.of(null, false),                   // null
            Arguments.of("", false)                      // 빈 문자열
        );
    }

    // ========================================
    // parseLayoutId() 테스트
    // ========================================

    @Test
    @DisplayName("parseLayoutId - 정상 케이스")
    void testParseLayoutId_Success() {
        assertEquals(5, QuestionTypeConstants.parseLayoutId("type_5_유형01"));
        assertEquals(12, QuestionTypeConstants.parseLayoutId("type_12_문제_유형_A"));
        assertEquals(999, QuestionTypeConstants.parseLayoutId("type_999_test"));
    }

    @Test
    @DisplayName("parseLayoutId - 예외: 잘못된 형식")
    void testParseLayoutId_InvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> {
            QuestionTypeConstants.parseLayoutId("invalid_format");
        });
    }

    @Test
    @DisplayName("parseLayoutId - 예외: null")
    void testParseLayoutId_Null() {
        assertThrows(IllegalArgumentException.class, () -> {
            QuestionTypeConstants.parseLayoutId(null);
        });
    }

    // ========================================
    // extractText() 테스트
    // ========================================

    @Test
    @DisplayName("extractText - 정상 케이스")
    void testExtractText_Success() {
        assertEquals("유형01", QuestionTypeConstants.extractText("type_5_유형01"));
        assertEquals("문제_유형_A", QuestionTypeConstants.extractText("type_12_문제_유형_A"));
    }

    @Test
    @DisplayName("extractText - 예외: 잘못된 형식")
    void testExtractText_InvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> {
            QuestionTypeConstants.extractText("invalid");
        });
    }

    // ========================================
    // toDebugString() 테스트
    // ========================================

    @Test
    @DisplayName("toDebugString - 정상 케이스")
    void testToDebugString_Valid() {
        String result = QuestionTypeConstants.toDebugString("type_5_유형01");
        
        assertTrue(result.contains("question_type"));
        assertTrue(result.contains("Layout ID=5"));
        assertTrue(result.contains("유형01"));
    }

    @Test
    @DisplayName("toDebugString - 잘못된 ID")
    void testToDebugString_Invalid() {
        String result = QuestionTypeConstants.toDebugString("invalid_id");
        
        assertTrue(result.contains("유효하지 않은 ID"));
    }

    // ========================================
    // normalizeFullWidthCharacters() 테스트 (v0.7 P1 Fix)
    // ========================================

    @Test
    @DisplayName("normalizeFullWidthCharacters - 전각 괄호 정규화")
    void testNormalizeFullWidthCharacters_Parentheses() {
        String result = QuestionTypeConstants.normalizeFullWidthCharacters("（１）");
        assertEquals("(1)", result);
    }

    @Test
    @DisplayName("normalizeFullWidthCharacters - 전각 숫자 정규화")
    void testNormalizeFullWidthCharacters_Numbers() {
        String result = QuestionTypeConstants.normalizeFullWidthCharacters("０１２３４５６７８９");
        assertEquals("0123456789", result);
    }

    @Test
    @DisplayName("normalizeFullWidthCharacters - 혼합 문자 정규화")
    void testNormalizeFullWidthCharacters_Mixed() {
        String result = QuestionTypeConstants.normalizeFullWidthCharacters("유형（０１）");
        assertEquals("유형(01)", result);
    }

    @Test
    @DisplayName("normalizeFullWidthCharacters - 한글 유지")
    void testNormalizeFullWidthCharacters_KoreanPreserved() {
        String result = QuestionTypeConstants.normalizeFullWidthCharacters("문제유형");
        assertEquals("문제유형", result);
    }

    @Test
    @DisplayName("normalizeFullWidthCharacters - null 처리")
    void testNormalizeFullWidthCharacters_Null() {
        String result = QuestionTypeConstants.normalizeFullWidthCharacters(null);
        assertNull(result);
    }

    @Test
    @DisplayName("normalizeFullWidthCharacters - 빈 문자열 처리")
    void testNormalizeFullWidthCharacters_Empty() {
        String result = QuestionTypeConstants.normalizeFullWidthCharacters("");
        assertEquals("", result);
    }

    // ========================================
    // 통합 시나리오 테스트
    // ========================================

    @Test
    @DisplayName("통합 시나리오: ID 생성 → 검증 → 파싱")
    void testIntegrationScenario() {
        // 1. ID 생성
        String id = QuestionTypeConstants.generateIdentifier(42, "통합 테스트");
        
        // 2. ID 검증
        assertTrue(QuestionTypeConstants.isQuestionTypeIdentifier(id));
        
        // 3. Layout ID 추출
        int layoutId = QuestionTypeConstants.parseLayoutId(id);
        assertEquals(42, layoutId);
        
        // 4. 텍스트 추출
        String text = QuestionTypeConstants.extractText(id);
        assertEquals("통합_테스트", text);
    }

    @Test
    @DisplayName("통합 시나리오: 전각 문자 정규화 → ID 생성")
    void testIntegrationScenario_FullWidthNormalization() {
        // 1. 전각 문자 정규화
        String normalized = QuestionTypeConstants.normalizeFullWidthCharacters("유형（０１）");
        assertEquals("유형(01)", normalized);
        
        // 2. 정규화된 텍스트로 ID 생성
        String id = QuestionTypeConstants.generateIdentifier(10, normalized);
        assertTrue(id.startsWith("type_10_"));
        assertTrue(id.contains("유형"));
    }
}
