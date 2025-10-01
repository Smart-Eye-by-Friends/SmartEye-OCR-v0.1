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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * QuestionNumberExtractor 단위 테스트
 *
 * CBHLS 전략 1단계 검증:
 * - LAM 기반 추출 + OCR 교차 검증
 * - 신뢰도 점수 계산
 * - 임계값 필터링
 * - Fallback 메커니즘
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QuestionNumberExtractor 테스트")
class QuestionNumberExtractorTest {

    @Mock
    private PatternMatchingEngine patternMatchingEngine;

    @InjectMocks
    private QuestionNumberExtractor extractor;

    @BeforeEach
    void setUp() {
        // 기본 패턴 매칭 동작 설정 (lenient mode for unused stubs)
        lenient().when(patternMatchingEngine.extractQuestionNumber("1번")).thenReturn("1");
        lenient().when(patternMatchingEngine.extractQuestionNumber("2번")).thenReturn("2");
        lenient().when(patternMatchingEngine.extractQuestionNumber("Q1")).thenReturn("1");
        lenient().when(patternMatchingEngine.extractQuestionNumber("문제 1")).thenReturn("1");
        lenient().when(patternMatchingEngine.extractQuestionNumber("잘못된 텍스트")).thenReturn(null);
    }

    @Test
    @DisplayName("LAM + OCR 높은 신뢰도 - 성공적으로 문제 번호 추출")
    void testHighConfidenceLAMandOCR() {
        // Given: LAM에서 question_number 분류 + OCR 신뢰도 높음
        List<LayoutInfo> layoutElements = new ArrayList<>();
        LayoutInfo layout1 = createLayout(1, "question_number", 0.9, new int[]{10, 100, 50, 120});
        layoutElements.add(layout1);

        List<OCRResult> ocrResults = new ArrayList<>();
        OCRResult ocr1 = createOCR(1, "question_number", new int[]{10, 100, 50, 120}, "1번", 0.95);
        ocrResults.add(ocr1);

        // When
        Map<String, Integer> result = extractor.extractQuestionPositions(layoutElements, ocrResults);

        // Then: 신뢰도 점수 = 0.9 * 0.95 * 1.0 = 0.855 > 0.65 (통과)
        assertEquals(1, result.size());
        assertTrue(result.containsKey("1"));
        assertEquals(100, result.get("1")); // Y 좌표
    }

    @Test
    @DisplayName("LAM 높음 + OCR 낮음 - 신뢰도 필터링")
    void testHighLAMlowOCR() {
        // Given: LAM 신뢰도 높지만 OCR 신뢰도 낮음
        List<LayoutInfo> layoutElements = new ArrayList<>();
        LayoutInfo layout1 = createLayout(1, "question_number", 0.9, new int[]{10, 100, 50, 120});
        layoutElements.add(layout1);

        List<OCRResult> ocrResults = new ArrayList<>();
        OCRResult ocr1 = createOCR(1, "question_number", new int[]{10, 100, 50, 120}, "1번", 0.4);
        ocrResults.add(ocr1);

        // When
        Map<String, Integer> result = extractor.extractQuestionPositions(layoutElements, ocrResults);

        // Then: 신뢰도 점수 = 0.9 * 0.4 * 1.0 = 0.36 < 0.65 (필터링)
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("LAM 없음 - Fallback 패턴 매칭")
    void testFallbackToPatternMatching() {
        // Given: LAM에 question_number 없음, OCR만 있음
        List<LayoutInfo> layoutElements = new ArrayList<>();
        LayoutInfo layout1 = createLayout(1, "text", 0.9, new int[]{10, 100, 50, 120});
        layoutElements.add(layout1);

        List<OCRResult> ocrResults = new ArrayList<>();
        OCRResult ocr1 = createOCR(1, "text", new int[]{10, 100, 50, 120}, "1번", 0.9);
        ocrResults.add(ocr1);

        // When
        Map<String, Integer> result = extractor.extractQuestionPositions(layoutElements, ocrResults);

        // Then: Fallback으로 패턴 매칭 사용 (OCR 신뢰도 * 패턴 점수)
        // 0.9 * 1.0 = 0.9 > 0.65 (통과)
        assertEquals(1, result.size());
        assertTrue(result.containsKey("1"));
    }

    @Test
    @DisplayName("임계값 경계 테스트 - 0.65 경계값")
    void testConfidenceThreshold() {
        // Given: 신뢰도 점수가 정확히 0.65인 경우
        List<LayoutInfo> layoutElements = new ArrayList<>();
        LayoutInfo layout1 = createLayout(1, "question_number", 0.65, new int[]{10, 100, 50, 120});
        layoutElements.add(layout1);

        List<OCRResult> ocrResults = new ArrayList<>();
        OCRResult ocr1 = createOCR(1, "question_number", new int[]{10, 100, 50, 120}, "1번", 1.0);
        ocrResults.add(ocr1);

        // When
        Map<String, Integer> result = extractor.extractQuestionPositions(layoutElements, ocrResults);

        // Then: 0.65 * 1.0 * 1.0 = 0.65 >= 0.65 (통과)
        assertEquals(1, result.size());
        assertTrue(result.containsKey("1"));
    }

    @Test
    @DisplayName("임계값 경계 테스트 - 0.64 경계값 (실패)")
    void testConfidenceThresholdFail() {
        // Given: 신뢰도 점수가 0.64인 경우
        List<LayoutInfo> layoutElements = new ArrayList<>();
        LayoutInfo layout1 = createLayout(1, "question_number", 0.64, new int[]{10, 100, 50, 120});
        layoutElements.add(layout1);

        List<OCRResult> ocrResults = new ArrayList<>();
        OCRResult ocr1 = createOCR(1, "question_number", new int[]{10, 100, 50, 120}, "1번", 1.0);
        ocrResults.add(ocr1);

        // When
        Map<String, Integer> result = extractor.extractQuestionPositions(layoutElements, ocrResults);

        // Then: 0.64 * 1.0 * 1.0 = 0.64 < 0.65 (필터링)
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("중복 문제 번호 - 최고 신뢰도 선택")
    void testDuplicateQuestionNumbers() {
        // Given: 같은 문제 번호가 여러 번 추출됨
        List<LayoutInfo> layoutElements = new ArrayList<>();
        LayoutInfo layout1 = createLayout(1, "question_number", 0.7, new int[]{10, 100, 50, 120});
        LayoutInfo layout2 = createLayout(2, "question_number", 0.95, new int[]{10, 150, 50, 170});
        layoutElements.add(layout1);
        layoutElements.add(layout2);

        List<OCRResult> ocrResults = new ArrayList<>();
        OCRResult ocr1 = createOCR(1, "question_number", new int[]{10, 100, 50, 120}, "1번", 0.8);
        OCRResult ocr2 = createOCR(2, "question_number", new int[]{10, 150, 50, 170}, "1번", 0.9);
        ocrResults.add(ocr1);
        ocrResults.add(ocr2);

        // When
        Map<String, Integer> result = extractor.extractQuestionPositions(layoutElements, ocrResults);

        // Then: 신뢰도 높은 것 선택
        // layout1: 0.7 * 0.8 * 1.0 = 0.56 < 0.65 (필터링)
        // layout2: 0.95 * 0.9 * 1.0 = 0.855 > 0.65 (선택)
        assertEquals(1, result.size());
        assertEquals(150, result.get("1")); // 신뢰도 높은 Y 좌표
    }

    @Test
    @DisplayName("여러 문제 번호 추출")
    void testMultipleQuestions() {
        // Given: 여러 문제 번호가 있음
        List<LayoutInfo> layoutElements = new ArrayList<>();
        LayoutInfo layout1 = createLayout(1, "question_number", 0.9, new int[]{10, 100, 50, 120});
        LayoutInfo layout2 = createLayout(2, "question_number", 0.85, new int[]{10, 200, 50, 220});
        LayoutInfo layout3 = createLayout(3, "question_number", 0.92, new int[]{10, 300, 50, 320});
        layoutElements.add(layout1);
        layoutElements.add(layout2);
        layoutElements.add(layout3);

        List<OCRResult> ocrResults = new ArrayList<>();
        ocrResults.add(createOCR(1, "question_number", new int[]{10, 100, 50, 120}, "1번", 0.9));
        ocrResults.add(createOCR(2, "question_number", new int[]{10, 200, 50, 220}, "2번", 0.8));
        ocrResults.add(createOCR(3, "question_number", new int[]{10, 300, 50, 320}, "Q1", 0.95));

        // Mock 추가 설정
        when(patternMatchingEngine.extractQuestionNumber("Q1")).thenReturn("1");

        // When
        Map<String, Integer> result = extractor.extractQuestionPositions(layoutElements, ocrResults);

        // Then: 문제 1, 2 추출 성공 (문제 3의 "Q1"은 중복으로 문제 1과 경쟁)
        assertTrue(result.containsKey("1"));
        assertTrue(result.containsKey("2"));
    }

    @Test
    @DisplayName("패턴 매칭 실패 - null 반환")
    void testPatternMatchingFail() {
        // Given: 패턴 매칭이 실패하는 텍스트
        List<LayoutInfo> layoutElements = new ArrayList<>();
        LayoutInfo layout1 = createLayout(1, "question_number", 0.9, new int[]{10, 100, 50, 120});
        layoutElements.add(layout1);

        List<OCRResult> ocrResults = new ArrayList<>();
        OCRResult ocr1 = createOCR(1, "question_number", new int[]{10, 100, 50, 120}, "잘못된 텍스트", 0.9);
        ocrResults.add(ocr1);

        // When
        Map<String, Integer> result = extractor.extractQuestionPositions(layoutElements, ocrResults);

        // Then: 추출 실패
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("빈 입력 - 빈 결과 반환")
    void testEmptyInput() {
        // Given: 빈 입력
        List<LayoutInfo> layoutElements = new ArrayList<>();
        List<OCRResult> ocrResults = new ArrayList<>();

        // When
        Map<String, Integer> result = extractor.extractQuestionPositions(layoutElements, ocrResults);

        // Then: 빈 결과
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("OCR 텍스트 null - 스킵")
    void testNullOCRText() {
        // Given: OCR 텍스트가 null인 경우
        List<LayoutInfo> layoutElements = new ArrayList<>();
        LayoutInfo layout1 = createLayout(1, "question_number", 0.9, new int[]{10, 100, 50, 120});
        layoutElements.add(layout1);

        List<OCRResult> ocrResults = new ArrayList<>();
        OCRResult ocr1 = createOCR(1, "question_number", new int[]{10, 100, 50, 120}, null, 0.9);
        ocrResults.add(ocr1);

        // When
        Map<String, Integer> result = extractor.extractQuestionPositions(layoutElements, ocrResults);

        // Then: null 텍스트는 스킵
        assertEquals(0, result.size());
    }

    // ============================================================================
    // 헬퍼 메서드
    // ============================================================================

    private LayoutInfo createLayout(int id, String className, double confidence, int[] box) {
        LayoutInfo layout = new LayoutInfo();
        layout.setId(id);
        layout.setClassName(className);
        layout.setConfidence(confidence);
        layout.setBox(box);
        return layout;
    }

    private OCRResult createOCR(int id, String className, int[] coordinates, String text, double confidence) {
        OCRResult ocr = new OCRResult();
        ocr.setId(id);
        ocr.setClassName(className);
        ocr.setCoordinates(coordinates);
        ocr.setText(text);
        ocr.setConfidence(confidence);
        return ocr;
    }
}
