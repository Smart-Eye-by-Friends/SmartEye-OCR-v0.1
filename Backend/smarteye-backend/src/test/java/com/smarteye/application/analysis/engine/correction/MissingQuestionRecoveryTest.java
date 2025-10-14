package com.smarteye.application.analysis.engine.correction;

import com.smarteye.application.analysis.engine.validation.SequenceGap;
import com.smarteye.application.analysis.engine.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MissingQuestionRecovery 단위 테스트
 *
 * @author Claude Code
 * @since v0.7
 */
class MissingQuestionRecoveryTest {

    private MissingQuestionRecovery recovery;

    @BeforeEach
    void setUp() {
        recovery = new MissingQuestionRecovery();
    }

    @Test
    @DisplayName("OCR 오류 교정: 204 → 294 (2↔9 혼동)")
    void testCorrectOCRError_204to294() {
        // Given: 295 → 204 역순 (실제: 294)
        SequenceGap reverseGap = new SequenceGap(
                295,
                204,
                SequenceGap.Type.REVERSE,
                "역순 감지: 295 → 204 (OCR 오류 가능성)"
        );
        ValidationResult validationResult = new ValidationResult(List.of(reverseGap));

        // When: 교정 수행
        CorrectionResult result = recovery.recover(validationResult);

        // Then: 204 → 294 교정
        assertTrue(result.hasCorrections(), "교정이 수행되어야 함");
        assertEquals(1, result.getOcrCorrections().size(), "1개 OCR 오류 교정");
        assertEquals("294", result.getOcrCorrections().get("204"), "204 → 294 교정");
    }

    @Test
    @DisplayName("OCR 오류 교정: 104 → 194 (1↔9 혼동)")
    void testCorrectOCRError_104to194() {
        // Given: 193 → 104 역순 (실제: 194)
        SequenceGap reverseGap = new SequenceGap(
                193,
                104,
                SequenceGap.Type.REVERSE,
                "역순 감지: 193 → 104 (OCR 오류 가능성)"
        );
        ValidationResult validationResult = new ValidationResult(List.of(reverseGap));

        // When: 교정 수행
        CorrectionResult result = recovery.recover(validationResult);

        // Then: 104 → 194 교정
        assertTrue(result.hasCorrections());
        assertEquals("194", result.getOcrCorrections().get("104"), "104 → 194 교정");
    }

    @Test
    @DisplayName("누락 문제 기록: 295 → 297 (296 누락)")
    void testRecordMissingQuestion() {
        // Given: 295 → 297 정방향 Gap (296 누락)
        SequenceGap forwardGap = new SequenceGap(
                295,
                297,
                1,
                List.of(296)
        );
        ValidationResult validationResult = new ValidationResult(List.of(forwardGap));

        // When: 교정 수행
        CorrectionResult result = recovery.recover(validationResult);

        // Then: 296번 누락 기록
        assertTrue(result.hasCorrections());
        assertEquals(1, result.getRecoveredQuestions().size(), "1개 누락 문제 기록");
        assertTrue(result.getRecoveredQuestions().contains(296), "296번 누락 기록");
    }

    @Test
    @DisplayName("복합 케이스: OCR 오류 + 누락 문제")
    void testComplexCase() {
        // Given: 295 → 204 (역순) + 296 → 298 (297 누락)
        SequenceGap reverseGap = new SequenceGap(
                295,
                204,
                SequenceGap.Type.REVERSE,
                "역순 감지"
        );
        SequenceGap forwardGap = new SequenceGap(
                296,
                298,
                1,
                List.of(297)
        );
        ValidationResult validationResult = new ValidationResult(List.of(reverseGap, forwardGap));

        // When: 교정 수행
        CorrectionResult result = recovery.recover(validationResult);

        // Then: 204 → 294 교정 + 297번 누락 기록
        assertTrue(result.hasCorrections());
        assertEquals(1, result.getOcrCorrections().size(), "1개 OCR 교정");
        assertEquals("294", result.getOcrCorrections().get("204"));
        assertEquals(1, result.getRecoveredQuestions().size(), "1개 누락 문제");
        assertTrue(result.getRecoveredQuestions().contains(297));
    }

    @Test
    @DisplayName("교정 불필요: 정상 시퀀스")
    void testNoCorrection() {
        // Given: 빈 검증 결과 (모든 검사 통과)
        ValidationResult validationResult = new ValidationResult(List.of());

        // When: 교정 수행
        CorrectionResult result = recovery.recover(validationResult);

        // Then: 교정 없음
        assertFalse(result.hasCorrections(), "교정이 수행되지 않아야 함");
        assertEquals(0, result.getOcrCorrections().size());
        assertEquals(0, result.getRecoveredQuestions().size());
    }

    @Test
    @DisplayName("교정 로그 생성 확인")
    void testCorrectionLogs() {
        // Given: OCR 오류
        SequenceGap reverseGap = new SequenceGap(
                295,
                204,
                SequenceGap.Type.REVERSE,
                "역순 감지"
        );
        ValidationResult validationResult = new ValidationResult(List.of(reverseGap));

        // When: 교정 수행
        CorrectionResult result = recovery.recover(validationResult);

        // Then: 교정 로그 생성
        assertFalse(result.getCorrectionLogs().isEmpty(), "교정 로그가 생성되어야 함");
        assertTrue(result.getCorrectionLogs().stream()
                .anyMatch(log -> log.getType().equals("OCR_CORRECTION")),
                "OCR_CORRECTION 타입 로그 존재");
    }
}
