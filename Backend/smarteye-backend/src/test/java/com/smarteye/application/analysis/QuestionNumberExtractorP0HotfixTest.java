package com.smarteye.application.analysis;

import com.smarteye.application.analysis.engine.PatternMatchingEngine;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * QuestionNumberExtractor P0 핫픽스 검증 테스트
 *
 * 검증 항목:
 * 1. OCR 텍스트 정제: "299..." → "299."
 * 2. 패턴 매칭 유연화: 점 여러개 허용 (Tier 시스템)
 * 3. 신뢰도 계산 개선: 곱셈 → 가중 평균
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QuestionNumberExtractor P0 핫픽스 검증")
class QuestionNumberExtractorP0HotfixTest {

    @Mock
    private PatternMatchingEngine patternMatchingEngine;

    @InjectMocks
    private QuestionNumberExtractor extractor;

    @BeforeEach
    void setUp() {
        // 패턴 매칭 기본 동작 설정 (정제 후 텍스트 기준)
        lenient().when(patternMatchingEngine.extractQuestionNumber("299.")).thenReturn("299");
        lenient().when(patternMatchingEngine.extractQuestionNumber("1번")).thenReturn("1");
        lenient().when(patternMatchingEngine.extractQuestionNumber("2번")).thenReturn("2");
        lenient().when(patternMatchingEngine.extractQuestionNumber("1.")).thenReturn("1");
        lenient().when(patternMatchingEngine.extractQuestionNumber("2.")).thenReturn("2");
        lenient().when(patternMatchingEngine.extractQuestionNumber("3번!!")).thenReturn("3");
    }

    // ============================================================================
    // 테스트 1: OCR 텍스트 정제 검증
    // ============================================================================

    @Test
    @DisplayName("P0-1: OCR 텍스트 정제 - 점 여러개 제거 (299... → 299.)")
    void testCleanOCRText_MultipleDotsToSingleDot() {
        // Given: 노이즈가 포함된 OCR 텍스트
        String noisyText = "299...";

        // When: 정제 메서드 호출 (Reflection 사용)
        String cleaned = invokePrivateCleanOCRText(noisyText);

        // Then: 점 하나만 남음
        assertEquals("299.", cleaned,
            "OCR 텍스트 정제 실패: '299...'가 '299.'로 변환되어야 함");
        assertFalse(cleaned.contains("..."),
            "여러 개의 점이 여전히 포함됨");
    }

    @Test
    @DisplayName("P0-1: OCR 텍스트 정제 - 공백과 점 정규화")
    void testCleanOCRText_WhitespaceAndDotNormalization() {
        // Given: 공백과 점이 섞인 OCR 텍스트
        String textWithSpaces = "299 .  .";

        // When: 정제 메서드 호출
        String cleaned = invokePrivateCleanOCRText(textWithSpaces);

        // Then: 정규화됨 ("299.")
        assertEquals("299.", cleaned,
            "공백+점 정규화 실패: '299 .  .'가 '299.'로 변환되어야 함");
    }

    @Test
    @DisplayName("P0-1: OCR 텍스트 정제 - 연속 공백 제거")
    void testCleanOCRText_MultipleSpaces() {
        // Given: 연속 공백이 포함된 텍스트
        String textWithSpaces = "문제   1번";

        // When: 정제 메서드 호출
        String cleaned = invokePrivateCleanOCRText(textWithSpaces);

        // Then: 단일 공백으로 변환
        assertEquals("문제 1번", cleaned,
            "연속 공백 제거 실패: '문제   1번'이 '문제 1번'으로 변환되어야 함");
    }

    @Test
    @DisplayName("P0-1: OCR 텍스트 정제 - Null 입력 안정성")
    void testCleanOCRText_NullInput() {
        // Given: Null 입력
        String nullText = null;

        // When: 정제 메서드 호출
        String cleaned = invokePrivateCleanOCRText(nullText);

        // Then: 빈 문자열 반환
        assertEquals("", cleaned,
            "Null 입력 처리 실패: 빈 문자열을 반환해야 함");
    }

    // ============================================================================
    // 테스트 2: 패턴 매칭 점수 계산 검증
    // ============================================================================

    @Test
    @DisplayName("P0-2: 패턴 매칭 점수 - Tier 3: 점 여러개 허용 (0.8)")
    void testPatternMatchScore_Tier3MultipleDotsAllowed() {
        // Given: 점이 여러개 붙은 텍스트
        String ocrText = "299...";
        String extractedNumber = "299";

        // When: 패턴 점수 계산 (Reflection 사용)
        double score = invokePrivateCalculatePatternMatchScore(ocrText, extractedNumber);

        // Then: Tier 3 점수 (0.8)
        assertEquals(0.8, score, 0.01,
            "점 여러개 패턴의 점수가 0.8이 아님: " + score);
    }

    @Test
    @DisplayName("P0-2: 패턴 매칭 점수 - Tier 1: 완전 일치 (1.0)")
    void testPatternMatchScore_Tier1PerfectMatch() {
        // Given: 완전 일치 패턴
        String ocrText = "299번";
        String extractedNumber = "299";

        // When: 패턴 점수 계산
        double score = invokePrivateCalculatePatternMatchScore(ocrText, extractedNumber);

        // Then: Tier 1 점수 (1.0)
        assertEquals(1.0, score, 0.01,
            "완전 일치 패턴의 점수가 1.0이 아님: " + score);
    }

    @Test
    @DisplayName("P0-2: 패턴 매칭 점수 - Tier 2: 높은 일치 (0.9)")
    void testPatternMatchScore_Tier2HighMatch() {
        // Given: Q1 패턴
        String ocrText = "Q 1";
        String extractedNumber = "1";

        // When: 패턴 점수 계산
        double score = invokePrivateCalculatePatternMatchScore(ocrText, extractedNumber);

        // Then: Tier 2 점수 (0.9)
        assertEquals(0.9, score, 0.01,
            "Q1 패턴의 점수가 0.9가 아님: " + score);
    }

    @Test
    @DisplayName("P0-2: 패턴 매칭 점수 - Tier 5: False Positive 방지 (0.0)")
    void testPatternMatchScore_Tier5FalsePositivePrevention() {
        // Given: 문제 번호가 아닌 문맥 ("정답", "점" 등 포함)
        String ocrText = "정답 299점";
        String extractedNumber = "299";

        // When: 패턴 점수 계산
        double score = invokePrivateCalculatePatternMatchScore(ocrText, extractedNumber);

        // Then: False Positive 방지 (0.0)
        assertEquals(0.0, score, 0.01,
            "잘못된 문맥이 거부되지 않음 (점수: " + score + ")");
    }

    // ============================================================================
    // 테스트 3: 가중 평균 신뢰도 계산 검증
    // ============================================================================

    @Test
    @DisplayName("P0-3: 신뢰도 계산 - 가중 평균 (임계값 0.70 통과)")
    void testWeightedAverageConfidence_PassThreshold() {
        // Given: 균형잡힌 신뢰도 입력
        double lamConfidence = 0.85;
        double ocrConfidence = 0.60;
        double patternScore = 0.80;

        // When: 신뢰도 계산 (Reflection 사용)
        double confidenceScore = invokePrivateCalculateConfidenceScore(
            lamConfidence, ocrConfidence, patternScore
        );

        // Then: 임계값 0.70 통과
        assertTrue(confidenceScore >= 0.70,
            "가중 평균 신뢰도가 임계값 미달: " + confidenceScore + " (임계값: 0.70)");

        // 기존 곱셈 방식과 비교
        double oldScore = lamConfidence * ocrConfidence * patternScore;
        assertTrue(confidenceScore > oldScore,
            String.format("가중 평균(%.3f)이 곱셈 방식(%.3f)보다 낮음",
                         confidenceScore, oldScore));
    }

    @Test
    @DisplayName("P0-3: 신뢰도 계산 - 가중 평균 공식 검증")
    void testWeightedAverageConfidence_FormulaVerification() {
        // Given: 테스트 신뢰도 값
        double lamConfidence = 0.90;
        double ocrConfidence = 0.50;
        double patternScore = 0.75;

        // When: 가중 평균 계산
        double confidenceScore = invokePrivateCalculateConfidenceScore(
            lamConfidence, ocrConfidence, patternScore
        );

        // Then: 수동 계산 검증
        // 가중치: LAM 50%, OCR 30%, Pattern 20%
        double expected = (0.5 * lamConfidence) + (0.3 * ocrConfidence) + (0.2 * patternScore);
        assertEquals(expected, confidenceScore, 0.001,
            String.format("가중 평균 공식 오류: 예상 %.3f, 실제 %.3f",
                         expected, confidenceScore));
    }

    @Test
    @DisplayName("P0-3: 신뢰도 계산 - 극단적 케이스 (LAM 높음, OCR 낮음)")
    void testWeightedAverageConfidence_ExtremeCaseHighLAM() {
        // Given: LAM 매우 높음, OCR 매우 낮음
        double lamConfidence = 0.95;
        double ocrConfidence = 0.30;
        double patternScore = 0.60;

        // When: 가중 평균 계산
        double confidenceScore = invokePrivateCalculateConfidenceScore(
            lamConfidence, ocrConfidence, patternScore
        );

        // Then: 가중 평균은 곱셈보다 관대함
        double oldScore = lamConfidence * ocrConfidence * patternScore; // 0.171
        assertTrue(confidenceScore > oldScore * 3,
            String.format("가중 평균(%.3f)이 곱셈(%.3f)의 3배보다 작음 - 개선 효과 부족",
                         confidenceScore, oldScore));
    }

    @Test
    @DisplayName("P0-3: 신뢰도 계산 - 가중치 균형 테스트")
    void testWeightedAverageConfidence_WeightBalance() {
        // Given: 모든 신뢰도 동일
        double confidence = 0.80;

        // When: 가중 평균 계산
        double confidenceScore = invokePrivateCalculateConfidenceScore(
            confidence, confidence, confidence
        );

        // Then: 가중치 합 = 1.0이므로 원래 값 유지
        assertEquals(confidence, confidenceScore, 0.001,
            "가중치 합이 1.0일 때 평균이 입력값과 다름");
    }

    // ============================================================================
    // 테스트 4: 통합 시나리오 (실제 사용 케이스)
    // ============================================================================

    @Test
    @DisplayName("P0-통합: 노이즈 있는 OCR + 가중 평균 신뢰도 (실제 시나리오)")
    void testIntegratedScenario_NoisyOCRWithWeightedConfidence() {
        // Given: 실제 상황과 유사한 LAM + OCR 결과
        List<LayoutInfo> layoutElements = new ArrayList<>();
        layoutElements.add(createLayoutInfo(1, "question_number", 0.94, new int[]{1830, 3420, 1969, 3513}));

        List<OCRResult> ocrResults = new ArrayList<>();
        ocrResults.add(createOCRResult(1, "question_number", new int[]{1830, 3420, 1969, 3513}, "299...", 0.80));

        // When: 문제 번호 추출
        Map<String, Integer> result = extractor.extractQuestionPositions(
            layoutElements, ocrResults
        );

        // Then: "299" 인식 성공 (가중 평균으로 임계값 통과)
        assertTrue(result.containsKey("299"),
            "문제 번호 299가 인식되지 않음 - 가중 평균 적용 실패 가능성");
        assertEquals(3420, result.get("299").intValue(),
            "문제 번호 299의 Y 좌표가 올바르지 않음");

        // 디버깅 정보 출력
        if (!result.containsKey("299")) {
            fail("통합 테스트 실패: 노이즈 있는 OCR + 가중 평균 조합이 작동하지 않음");
        }
    }

    @Test
    @DisplayName("P0-통합: 여러 노이즈 패턴 동시 처리")
    void testIntegratedScenario_MultipleNoisyPatterns() {
        // Given: 다양한 노이즈 패턴
        List<LayoutInfo> layoutElements = new ArrayList<>();
        layoutElements.add(createLayoutInfo(1, "question_number", 0.90, new int[]{100, 100, 150, 120}));
        layoutElements.add(createLayoutInfo(2, "question_number", 0.88, new int[]{100, 200, 150, 220}));
        layoutElements.add(createLayoutInfo(3, "question_number", 0.92, new int[]{100, 300, 150, 320}));

        List<OCRResult> ocrResults = new ArrayList<>();
        ocrResults.add(createOCRResult(1, "question_number", new int[]{100, 100, 150, 120}, "1...", 0.75));
        ocrResults.add(createOCRResult(2, "question_number", new int[]{100, 200, 150, 220}, " 2. ", 0.82));
        ocrResults.add(createOCRResult(3, "question_number", new int[]{100, 300, 150, 320}, "3번!!", 0.78));

        // When: 문제 번호 추출
        Map<String, Integer> result = extractor.extractQuestionPositions(
            layoutElements, ocrResults
        );

        // Then: 모든 문제 번호 인식 성공
        assertTrue(result.containsKey("1"), "문제 1 인식 실패 (점 여러개)");
        assertTrue(result.containsKey("2"), "문제 2 인식 실패 (공백 포함)");
        assertTrue(result.containsKey("3"), "문제 3 인식 실패 (특수문자)");
        assertEquals(3, result.size(), "인식된 문제 번호 개수가 예상과 다름");
    }

    @Test
    @DisplayName("P0-통합: 곱셈 방식과 가중 평균 비교 (통계)")
    void testIntegratedScenario_CompareMultiplicationVsWeightedAverage() {
        // Given: 곱셈 방식으로는 실패하지만 가중 평균으로는 성공하는 케이스
        double lamConfidence = 0.85;
        double ocrConfidence = 0.60;
        double patternScore = 0.80;

        // When: 두 방식 비교
        double oldScore = lamConfidence * ocrConfidence * patternScore; // 0.408
        double newScore = invokePrivateCalculateConfidenceScore(
            lamConfidence, ocrConfidence, patternScore
        ); // 0.755

        // Then: 가중 평균이 임계값 통과, 곱셈은 실패
        double threshold = 0.70;
        assertTrue(oldScore < threshold,
            "곱셈 방식이 임계값을 통과함 (테스트 케이스 오류): " + oldScore);
        assertTrue(newScore >= threshold,
            "가중 평균이 임계값을 통과하지 못함: " + newScore);

        // 개선 비율 계산
        double improvement = ((newScore - oldScore) / oldScore) * 100;
        assertTrue(improvement > 50,
            String.format("개선 비율이 50%% 미만: %.1f%% (%.3f → %.3f)",
                         improvement, oldScore, newScore));
    }

    @Test
    @DisplayName("P0-통합: 임계값 경계 테스트 (0.70)")
    void testIntegratedScenario_ThresholdBoundary() {
        // Given: 신뢰도가 정확히 임계값 근처인 경우
        List<LayoutInfo> layoutElements = new ArrayList<>();
        layoutElements.add(createLayoutInfo(1, "question_number", 0.70, new int[]{100, 100, 150, 120}));

        List<OCRResult> ocrResults = new ArrayList<>();
        ocrResults.add(createOCRResult(1, "question_number", new int[]{100, 100, 150, 120}, "1번", 1.0));

        // Mock 설정
        when(patternMatchingEngine.extractQuestionNumber("1번")).thenReturn("1");

        // When: 문제 번호 추출
        Map<String, Integer> result = extractor.extractQuestionPositions(
            layoutElements, ocrResults
        );

        // Then: 임계값 0.70 이상이므로 통과
        // 신뢰도 = 0.5*0.70 + 0.3*1.0 + 0.2*1.0 = 0.85 >= 0.70 (통과)
        assertTrue(result.containsKey("1"),
            "임계값 근처의 신뢰도가 통과하지 못함");
    }

    // ============================================================================
    // 헬퍼 메서드 (Reflection을 통한 private 메서드 테스트)
    // ============================================================================

    /**
     * cleanOCRText private 메서드 호출 (Reflection)
     */
    private String invokePrivateCleanOCRText(String text) {
        try {
            Method method = QuestionNumberExtractor.class.getDeclaredMethod("cleanOCRText", String.class);
            method.setAccessible(true);
            return (String) method.invoke(extractor, text);
        } catch (Exception e) {
            fail("cleanOCRText 메서드 호출 실패: " + e.getMessage());
            return null;
        }
    }

    /**
     * calculatePatternMatchScore private 메서드 호출 (Reflection)
     */
    private double invokePrivateCalculatePatternMatchScore(String ocrText, String extractedNumber) {
        try {
            Method method = QuestionNumberExtractor.class.getDeclaredMethod(
                "calculatePatternMatchScore", String.class, String.class
            );
            method.setAccessible(true);
            return (double) method.invoke(extractor, ocrText, extractedNumber);
        } catch (Exception e) {
            fail("calculatePatternMatchScore 메서드 호출 실패: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * calculateConfidenceScore private 메서드 호출 (Reflection)
     */
    private double invokePrivateCalculateConfidenceScore(
            double lamConfidence, double ocrConfidence, double patternScore) {
        try {
            Method method = QuestionNumberExtractor.class.getDeclaredMethod(
                "calculateConfidenceScore", double.class, double.class, double.class
            );
            method.setAccessible(true);
            return (double) method.invoke(extractor, lamConfidence, ocrConfidence, patternScore);
        } catch (Exception e) {
            fail("calculateConfidenceScore 메서드 호출 실패: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * LayoutInfo Mock 생성 헬퍼
     */
    private LayoutInfo createLayoutInfo(int id, String className, double confidence, int[] box) {
        LayoutInfo layout = new LayoutInfo();
        layout.setId(id);
        layout.setClassName(className);
        layout.setConfidence(confidence);
        layout.setBox(box);
        return layout;
    }

    /**
     * OCRResult Mock 생성 헬퍼
     */
    private OCRResult createOCRResult(int id, String className, int[] coordinates, String text, double confidence) {
        OCRResult ocr = new OCRResult();
        ocr.setId(id);
        ocr.setClassName(className);
        ocr.setCoordinates(coordinates);
        ocr.setText(text);
        ocr.setConfidence(confidence);
        return ocr;
    }
}
