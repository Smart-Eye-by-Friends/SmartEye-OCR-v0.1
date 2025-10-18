# SmartEye v0.7 테스트 코드 예시

**작성일**: 2025-10-18
**대상**: question_type 독립, UNIT 제거, second_question_number 우선순위 변경
**목적**: 실제 구현 가능한 테스트 코드 제공

---

## 1. QuestionNumberExtractor 단위 테스트

### 파일: QuestionNumberExtractorV07Test.java

```java
package com.smarteye.application.analysis;

import com.smarteye.application.analysis.engine.PatternMatchingEngine;
import com.smarteye.domain.layout.LayoutClass;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * QuestionNumberExtractor v0.7 테스트
 *
 * 테스트 범위:
 * - question_type 독립 영역 생성
 * - UNIT 제거
 * - ID 충돌 처리
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QuestionNumberExtractor v0.7")
class QuestionNumberExtractorV07Test {

    @Mock
    private PatternMatchingEngine patternMatchingEngine;

    @InjectMocks
    private QuestionNumberExtractor extractor;

    // ========== 테스트 헬퍼 메서드 ==========

    private LayoutInfo createLayout(int id, String className, int[] box, double confidence) {
        LayoutInfo layout = new LayoutInfo();
        layout.setId(id);
        layout.setClassName(className);
        layout.setBox(box);
        layout.setConfidence(confidence);
        return layout;
    }

    private OCRResult createOCR(int id, String text, double confidence) {
        OCRResult ocr = new OCRResult();
        ocr.setId(id);
        ocr.setText(text);
        ocr.setConfidence(confidence);
        ocr.setCoordinates(new int[]{100, 200, 300, 250});
        return ocr;
    }

    // ========== Test Suite 1: question_type 독립 영역 ==========

    @Nested
    @DisplayName("question_type 독립 영역 생성")
    class QuestionTypeIndependentAreaTests {

        @Test
        @DisplayName("TC-QNE-001: question_type 정상 추출 (type_* 형식)")
        void testQuestionTypeExtraction_Success() {
            // Given
            LayoutInfo qtLayout = createLayout(
                5, "question_type", new int[]{300, 500, 500, 550}, 0.92
            );
            OCRResult qtOCR = createOCR(5, "유형 01", 0.88);

            // When
            Map<String, Integer> positions = extractor.extractQuestionPositions(
                List.of(qtLayout), List.of(qtOCR)
            );

            // Then
            assertThat(positions)
                .as("questionPositions가 비어있지 않아야 함")
                .isNotEmpty()
                .as("type_* 형식의 키가 포함되어야 함")
                .containsKey("type_5_유형01")
                .as("Y 좌표가 올바르게 저장되어야 함")
                .containsEntry("type_5_유형01", 500);
        }

        @Test
        @DisplayName("TC-QNE-002: question_type ID 중복 방지 (Layout ID 활용)")
        void testQuestionTypeExtraction_DuplicateTextDifferentIds() {
            // Given: 같은 텍스트 "유형A" but 다른 Layout ID
            LayoutInfo qt1 = createLayout(1, "question_type", new int[]{100, 200, 300, 250}, 0.90);
            LayoutInfo qt2 = createLayout(2, "question_type", new int[]{100, 800, 300, 850}, 0.92);
            OCRResult ocr1 = createOCR(1, "유형A", 0.85);
            OCRResult ocr2 = createOCR(2, "유형A", 0.88);

            // When
            Map<String, Integer> positions = extractor.extractQuestionPositions(
                List.of(qt1, qt2), List.of(ocr1, ocr2)
            );

            // Then
            assertThat(positions)
                .as("2개의 항목이 추가되어야 함")
                .hasSize(2)
                .as("Layout ID로 구분되어야 함")
                .containsKeys("type_1_유형A", "type_2_유형A");

            assertThat(positions.get("type_1_유형A"))
                .as("첫 번째 유형의 Y 좌표")
                .isEqualTo(200);

            assertThat(positions.get("type_2_유형A"))
                .as("두 번째 유형의 Y 좌표")
                .isEqualTo(800);
        }

        @Test
        @DisplayName("TC-QNE-003: question_type 특수 문자 sanitization")
        void testQuestionTypeExtraction_SpecialCharacterHandling() {
            // Given: 특수 문자 포함 텍스트
            LayoutInfo layout = createLayout(5, "question_type", new int[]{100, 200, 300, 250}, 0.90);
            OCRResult ocr = createOCR(5, "유형 01 (심화)★", 0.85);

            // When
            Map<String, Integer> positions = extractor.extractQuestionPositions(
                List.of(layout), List.of(ocr)
            );

            // Then: 특수문자 제거, 공백 언더스코어
            String expectedKey = "type_5_유형01심화";  // (심화)★ 제거
            assertThat(positions)
                .as("Sanitized 키가 생성되어야 함")
                .containsKey(expectedKey);
        }

        @Test
        @DisplayName("TC-QNE-004: question_type 패턴 점수 고정값 (1.0)")
        void testQuestionTypeExtraction_MaxPatternScore() {
            // Given: question_type는 패턴 매칭 불필요
            LayoutInfo layout = createLayout(1, "question_type", new int[]{100, 200, 300, 250}, 0.85);
            OCRResult ocr = createOCR(1, "유형A", 0.80);

            // When
            Map<String, Integer> positions = extractor.extractQuestionPositions(
                List.of(layout), List.of(ocr)
            );

            // Then: 신뢰도 계산에서 패턴 점수 1.0 사용 (자동 통과 확률 높음)
            assertThat(positions)
                .as("높은 신뢰도로 추출 성공해야 함")
                .isNotEmpty();
        }

        @Test
        @DisplayName("TC-QNE-005: question_type + question_number 혼재")
        void testQuestionTypeExtraction_Mixed() {
            // Given
            LayoutInfo qt = createLayout(1, "question_type", new int[]{100, 200, 300, 250}, 0.92);
            LayoutInfo qn = createLayout(2, "question_number", new int[]{100, 500, 150, 550}, 0.90);
            OCRResult qtOCR = createOCR(1, "유형A", 0.88);
            OCRResult qnOCR = createOCR(2, "003", 0.85);

            when(patternMatchingEngine.extractQuestionNumber("003")).thenReturn("003");

            // When
            Map<String, Integer> positions = extractor.extractQuestionPositions(
                List.of(qt, qn), List.of(qtOCR, qnOCR)
            );

            // Then: 두 타입 모두 추출
            assertThat(positions)
                .as("2개 항목 추출")
                .hasSize(2)
                .containsKeys("type_1_유형A", "003");
        }

        @Test
        @DisplayName("TC-QNE-006: question_type OCR 없음 시 스킵")
        void testQuestionTypeExtraction_NoOCR() {
            // Given: LAM 감지했지만 OCR 결과 없음
            LayoutInfo layout = createLayout(1, "question_type", new int[]{100, 200, 300, 250}, 0.90);

            // When: OCR 결과 빈 리스트
            Map<String, Integer> positions = extractor.extractQuestionPositions(
                List.of(layout), List.of()
            );

            // Then: 스킵되어 빈 맵
            assertThat(positions)
                .as("OCR 없으면 스킵")
                .isEmpty();
        }
    }

    // ========== Test Suite 2: UNIT 제거 검증 ==========

    @Nested
    @DisplayName("UNIT 제거")
    class UnitRemovalTests {

        @Test
        @DisplayName("TC-QNE-101: UNIT 클래스 완전 제외")
        void testUnitNotIncluded() {
            // Given
            LayoutInfo unitLayout = createLayout(1, "unit", new int[]{100, 200, 300, 250}, 0.95);
            OCRResult ocrResult = createOCR(1, "I. 지수함수와 로그함수", 0.90);

            // When
            Map<String, Integer> positions = extractor.extractQuestionPositions(
                List.of(unitLayout), List.of(ocrResult)
            );

            // Then
            assertThat(positions)
                .as("UNIT은 포함되지 않아야 함")
                .isEmpty();
        }

        @Test
        @DisplayName("TC-QNE-102: UNIT과 QUESTION_NUMBER 혼재 시 필터링")
        void testUnitFilteredWithQuestionNumbers() {
            // Given
            LayoutInfo unit = createLayout(1, "unit", new int[]{100, 100, 300, 150}, 0.95);
            LayoutInfo qn1 = createLayout(2, "question_number", new int[]{100, 500, 150, 550}, 0.90);
            LayoutInfo qn2 = createLayout(3, "question_number", new int[]{100, 1000, 150, 1050}, 0.92);

            OCRResult unitOCR = createOCR(1, "II. 삼각함수", 0.90);
            OCRResult qn1OCR = createOCR(2, "001", 0.88);
            OCRResult qn2OCR = createOCR(3, "002", 0.89);

            when(patternMatchingEngine.extractQuestionNumber("001")).thenReturn("001");
            when(patternMatchingEngine.extractQuestionNumber("002")).thenReturn("002");

            // When
            Map<String, Integer> positions = extractor.extractQuestionPositions(
                List.of(unit, qn1, qn2), List.of(unitOCR, qn1OCR, qn2OCR)
            );

            // Then
            assertThat(positions)
                .as("QUESTION_NUMBER만 2개 추출")
                .hasSize(2)
                .containsKeys("001", "002")
                .doesNotContainKey("II. 삼각함수");
        }

        @Test
        @DisplayName("TC-QNE-103: isBoundaryClass 로직 검증 (UNIT 제외)")
        void testIsBoundaryClass_ExcludesUnit() {
            // Given
            LayoutInfo qn = createLayout(1, "question_number", new int[]{100, 200, 150, 250}, 0.90);
            LayoutInfo qt = createLayout(2, "question_type", new int[]{100, 500, 300, 550}, 0.92);
            LayoutInfo unit = createLayout(3, "unit", new int[]{100, 100, 300, 150}, 0.95);

            OCRResult qnOCR = createOCR(1, "003", 0.85);
            OCRResult qtOCR = createOCR(2, "유형01", 0.88);
            OCRResult unitOCR = createOCR(3, "I. 단원", 0.90);

            when(patternMatchingEngine.extractQuestionNumber("003")).thenReturn("003");

            // When
            Map<String, Integer> positions = extractor.extractQuestionPositions(
                List.of(qn, qt, unit), List.of(qnOCR, qtOCR, unitOCR)
            );

            // Then: QUESTION_NUMBER, QUESTION_TYPE만
            assertThat(positions)
                .as("UNIT 제외, 2개만 추출")
                .hasSize(2)
                .containsKeys("003", "type_2_유형01");
        }
    }

    // ========== Test Suite 3: second_question_number 필터링 ==========

    @Nested
    @DisplayName("second_question_number 필터링")
    class SecondQuestionNumberFilteringTests {

        @Test
        @DisplayName("TC-QNE-201: second_question_number 여전히 필터링")
        void testSecondQuestionNumberStillFiltered() {
            // Given
            LayoutInfo subQN = createLayout(1, "second_question_number", new int[]{200, 600, 250, 650}, 0.88);
            OCRResult ocrResult = createOCR(1, "(1)", 0.85);

            // When
            Map<String, Integer> positions = extractor.extractQuestionPositions(
                List.of(subQN), List.of(ocrResult)
            );

            // Then: questionPositions에는 메인 문제만 (하위 문항 제외)
            assertThat(positions)
                .as("second_question_number는 questionPositions에 미포함")
                .isEmpty();
        }

        @Test
        @DisplayName("TC-QNE-202: question_number 하위 문항 패턴 방어")
        void testQuestionNumberSubQuestionPatternFiltering() {
            // Given: LAM이 question_number로 잘못 분류 (실제로는 하위 문항)
            LayoutInfo qn = createLayout(1, "question_number", new int[]{200, 600, 250, 650}, 0.90);
            OCRResult ocrResult = createOCR(1, "(1)", 0.85);

            when(patternMatchingEngine.extractQuestionNumber("(1)")).thenReturn("1");

            // When
            Map<String, Integer> positions = extractor.extractQuestionPositions(
                List.of(qn), List.of(ocrResult)
            );

            // Then: SUB_QUESTION_PATTERN으로 필터링
            assertThat(positions)
                .as("하위 문항 패턴은 필터링되어야 함")
                .isEmpty();
        }
    }
}
```

---

## 2. UnifiedAnalysisEngine 단위 테스트

### 파일: UnifiedAnalysisEngineV07Test.java

```java
package com.smarteye.application.analysis;

import com.smarteye.application.analysis.engine.*;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * UnifiedAnalysisEngine v0.7 테스트
 *
 * 테스트 범위:
 * - findQuestionBoundaryElement() (QUESTION_TYPE 지원)
 * - groupSubQuestions() (second_question_number 우선순위)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UnifiedAnalysisEngine v0.7")
class UnifiedAnalysisEngineV07Test {

    @InjectMocks
    private UnifiedAnalysisEngine engine;

    // ========== Test Suite 4: findQuestionBoundaryElement() ==========

    @Nested
    @DisplayName("findQuestionBoundaryElement() - QUESTION_TYPE 지원")
    class FindQuestionBoundaryElementTests {

        @Test
        @DisplayName("TC-UAE-001: question_type 요소 찾기 성공")
        void testFindQuestionBoundaryElement_QuestionType_Success() throws Exception {
            // Given
            LayoutInfo questionTypeLayout = createLayout(
                5, "question_type", new int[]{300, 500, 500, 550}
            );
            OCRResult ocr = createOCR(5, "유형01");
            Map<Integer, OCRResult> ocrMap = Map.of(5, ocr);
            List<LayoutInfo> layouts = List.of(questionTypeLayout);

            // When: Reflection으로 private 메서드 호출
            LayoutInfo found = (LayoutInfo) ReflectionTestUtils.invokeMethod(
                engine, "findQuestionBoundaryElement",
                "type_5_유형01", 500, layouts, ocrMap
            );

            // Then
            assertThat(found)
                .as("요소를 찾아야 함")
                .isNotNull()
                .extracting(LayoutInfo::getId)
                .isEqualTo(5);

            assertThat(found.getBox()[0])
                .as("X 좌표 검증")
                .isEqualTo(300);
        }

        @Test
        @DisplayName("TC-UAE-002: question_number 요소 찾기 (기존 동작)")
        void testFindQuestionBoundaryElement_QuestionNumber_Success() throws Exception {
            // Given
            LayoutInfo qnLayout = createLayout(
                10, "question_number", new int[]{100, 1500, 150, 1550}
            );
            OCRResult ocr = createOCR(10, "003.");
            Map<Integer, OCRResult> ocrMap = Map.of(10, ocr);

            // When
            LayoutInfo found = (LayoutInfo) ReflectionTestUtils.invokeMethod(
                engine, "findQuestionBoundaryElement",
                "003", 1500, List.of(qnLayout), ocrMap
            );

            // Then
            assertThat(found)
                .isNotNull()
                .extracting(LayoutInfo::getId)
                .isEqualTo(10);
        }

        @Test
        @DisplayName("TC-UAE-003: Y 좌표 허용 오차 경계값 (+10px)")
        void testFindQuestionBoundaryElement_YTolerance_Boundary() throws Exception {
            // Given: 실제 Y=500, 검색 Y=510 (오차 +10px)
            LayoutInfo layout = createLayout(5, "question_type", new int[]{300, 500, 500, 550});
            OCRResult ocr = createOCR(5, "유형A");

            // When: Y=510으로 검색
            LayoutInfo found = (LayoutInfo) ReflectionTestUtils.invokeMethod(
                engine, "findQuestionBoundaryElement",
                "type_5_유형A", 510, List.of(layout), Map.of(5, ocr)
            );

            // Then: 허용 오차 내이므로 매칭 성공
            assertThat(found)
                .as("Y 허용 오차 ±10px 내 매칭")
                .isNotNull();
        }

        @Test
        @DisplayName("TC-UAE-004: Y 좌표 오차 초과 시 null 반환")
        void testFindQuestionBoundaryElement_YTolerance_Exceeded() throws Exception {
            // Given: 실제 Y=500, 검색 Y=520 (오차 +20px)
            LayoutInfo layout = createLayout(5, "question_type", new int[]{300, 500, 500, 550});
            OCRResult ocr = createOCR(5, "유형A");

            // When: Y=520으로 검색
            LayoutInfo found = (LayoutInfo) ReflectionTestUtils.invokeMethod(
                engine, "findQuestionBoundaryElement",
                "type_5_유형A", 520, List.of(layout), Map.of(5, ocr)
            );

            // Then: 허용 오차 초과로 null
            assertThat(found)
                .as("Y 허용 오차 초과 시 null")
                .isNull();
        }

        @Test
        @DisplayName("TC-UAE-005: question_type + question_number 혼재")
        void testFindQuestionBoundaryElement_Mixed_CorrectMatching() throws Exception {
            // Given
            LayoutInfo qt = createLayout(5, "question_type", new int[]{300, 500, 500, 550});
            LayoutInfo qn = createLayout(10, "question_number", new int[]{100, 1500, 150, 1550});
            OCRResult ocrQt = createOCR(5, "유형A");
            OCRResult ocrQn = createOCR(10, "003");

            List<LayoutInfo> layouts = List.of(qt, qn);
            Map<Integer, OCRResult> ocrMap = Map.of(5, ocrQt, 10, ocrQn);

            // When
            LayoutInfo foundQt = (LayoutInfo) ReflectionTestUtils.invokeMethod(
                engine, "findQuestionBoundaryElement",
                "type_5_유형A", 500, layouts, ocrMap
            );
            LayoutInfo foundQn = (LayoutInfo) ReflectionTestUtils.invokeMethod(
                engine, "findQuestionBoundaryElement",
                "003", 1500, layouts, ocrMap
            );

            // Then
            assertThat(foundQt)
                .as("question_type 정상 매칭")
                .isNotNull()
                .extracting(LayoutInfo::getId)
                .isEqualTo(5);

            assertThat(foundQn)
                .as("question_number 정상 매칭")
                .isNotNull()
                .extracting(LayoutInfo::getId)
                .isEqualTo(10);
        }
    }

    // ========== Test Suite 5: groupSubQuestions() 우선순위 ==========

    @Nested
    @DisplayName("groupSubQuestions() - second_question_number 우선순위")
    class GroupSubQuestionsTests {

        @Test
        @DisplayName("TC-UAE-101: second_question_number 우선 처리")
        void testGroupSubQuestions_SecondQuestionNumber_Priority() throws Exception {
            // Given
            AnalysisElement subQN = createAnalysisElement("second_question_number", "(1)");
            List<AnalysisElement> elements = List.of(subQN);

            // When
            Map<String, Map<String, String>> subQuestions = (Map) ReflectionTestUtils.invokeMethod(
                engine, "groupSubQuestions", "001", elements
            );

            // Then
            assertThat(subQuestions)
                .as("하위 문항 1개 감지")
                .hasSize(1)
                .containsKey("1");

            // 로그 검증 필요: "📌 하위 문항 감지 (second_question_number): 1"
        }

        @Test
        @DisplayName("TC-UAE-102: question_number 패턴 fallback")
        void testGroupSubQuestions_QuestionNumber_FallbackPattern() throws Exception {
            // Given: second_question_number 없고, question_number "(1)" 패턴만
            AnalysisElement qnFallback = createAnalysisElement("question_number", "(1)");
            List<AnalysisElement> elements = List.of(qnFallback);

            // When
            Map<String, Map<String, String>> subQuestions = (Map) ReflectionTestUtils.invokeMethod(
                engine, "groupSubQuestions", "001", elements
            );

            // Then
            assertThat(subQuestions)
                .as("fallback으로 하위 문항 감지")
                .hasSize(1)
                .containsKey("1");

            // 로그 검증 필요: "📌 하위 문항 감지 (fallback-question_number): (1)"
        }

        @Test
        @DisplayName("TC-UAE-103: second_question_number 우선 (혼재 시)")
        void testGroupSubQuestions_SecondQuestionNumber_TakesPrecedence() throws Exception {
            // Given: 둘 다 존재
            AnalysisElement secondQN = createAnalysisElement("second_question_number", "(1)");
            AnalysisElement questionQN = createAnalysisElement("question_number", "(1)");
            List<AnalysisElement> elements = List.of(secondQN, questionQN);

            // When
            Map<String, Map<String, String>> subQuestions = (Map) ReflectionTestUtils.invokeMethod(
                engine, "groupSubQuestions", "001", elements
            );

            // Then: 1개만 추가 (second_question_number 우선)
            assertThat(subQuestions)
                .as("중복 방지, 1개만 추가")
                .hasSize(1)
                .containsKey("1");
        }

        @Test
        @DisplayName("TC-UAE-104: second_question_number 다양한 패턴")
        void testGroupSubQuestions_VariousPatterns() throws Exception {
            // Given: (1), 2), 3. 패턴
            AnalysisElement sub1 = createAnalysisElement("second_question_number", "(1)");
            AnalysisElement sub2 = createAnalysisElement("second_question_number", "2)");
            AnalysisElement sub3 = createAnalysisElement("second_question_number", "3.");
            List<AnalysisElement> elements = List.of(sub1, sub2, sub3);

            // When
            Map<String, Map<String, String>> subQuestions = (Map) ReflectionTestUtils.invokeMethod(
                engine, "groupSubQuestions", "001", elements
            );

            // Then
            assertThat(subQuestions)
                .as("다양한 패턴 모두 인식")
                .hasSize(3)
                .containsKeys("1", "2", "3");
        }
    }

    // ========== 헬퍼 메서드 ==========

    private LayoutInfo createLayout(int id, String className, int[] box) {
        LayoutInfo layout = new LayoutInfo();
        layout.setId(id);
        layout.setClassName(className);
        layout.setBox(box);
        layout.setConfidence(0.90);
        return layout;
    }

    private OCRResult createOCR(int id, String text) {
        OCRResult ocr = new OCRResult();
        ocr.setId(id);
        ocr.setText(text);
        ocr.setConfidence(0.85);
        ocr.setCoordinates(new int[]{100, 200, 300, 250});
        return ocr;
    }

    private AnalysisElement createAnalysisElement(String className, String ocrText) {
        AnalysisElement element = new AnalysisElement();

        LayoutInfo layout = new LayoutInfo();
        layout.setClassName(className);
        layout.setBox(new int[]{100, 200, 150, 250});
        element.setLayoutInfo(layout);

        OCRResult ocr = new OCRResult();
        ocr.setText(ocrText);
        element.setOcrResult(ocr);

        return element;
    }
}
```

---

## 3. 통합 테스트 예시

### 파일: UnifiedAnalysisEngineV07IntegrationTest.java

```java
package com.smarteye.application.analysis;

import com.smarteye.presentation.dto.AIDescriptionResult;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * UnifiedAnalysisEngine v0.7 통합 테스트
 *
 * 테스트 범위:
 * - question_type 포함 전체 분석 파이프라인
 * - UNIT 제외 검증
 * - second_question_number 하위 문항 그룹핑
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("UnifiedAnalysisEngine v0.7 통합 테스트")
class UnifiedAnalysisEngineV07IntegrationTest {

    @Autowired
    private UnifiedAnalysisEngine engine;

    // ========== Test Suite 6: E2E 파이프라인 ==========

    @Nested
    @DisplayName("E2E 분석 파이프라인")
    class EndToEndPipelineTests {

        @Test
        @DisplayName("TC-INT-001: question_type 포함 전체 분석")
        void testFullAnalysis_WithQuestionType() {
            // Given: question_type 포함 레이아웃
            List<LayoutInfo> layouts = createLayoutsWithQuestionType();
            List<OCRResult> ocrs = createOCRsWithQuestionType();
            List<AIDescriptionResult> aiResults = Collections.emptyList();

            // When
            UnifiedAnalysisResult result = engine.performUnifiedAnalysis(layouts, ocrs, aiResults);

            // Then
            assertThat(result.isSuccess())
                .as("분석 성공")
                .isTrue();

            assertThat(result.getStructuredData())
                .as("StructuredData 존재")
                .isNotNull();

            List<QuestionData> questions = result.getStructuredData().getQuestions();
            assertThat(questions)
                .as("question_type 항목 포함")
                .anyMatch(q -> q.getQuestionNumber().startsWith("type_"));

            // Y 좌표 순서 검증 (003, type_5_유형01, 004)
            List<String> questionNumbers = questions.stream()
                .map(QuestionData::getQuestionNumber)
                .toList();

            assertThat(questionNumbers)
                .as("Y 좌표 순서대로 정렬")
                .containsSequence("003", "type_5_유형01", "004");
        }

        @Test
        @DisplayName("TC-INT-002: UNIT 제외 검증")
        void testFullAnalysis_UnitExcluded() {
            // Given: UNIT 포함 레이아웃
            List<LayoutInfo> layouts = createLayoutsWithUnit();
            List<OCRResult> ocrs = createOCRsWithUnit();

            // When
            UnifiedAnalysisResult result = engine.performUnifiedAnalysis(layouts, ocrs, Collections.emptyList());

            // Then
            assertThat(result.isSuccess()).isTrue();

            List<QuestionData> questions = result.getStructuredData().getQuestions();
            assertThat(questions)
                .as("UNIT 제외, 2개만")
                .hasSize(2)
                .noneMatch(q -> q.getQuestionNumber().contains("단원"));
        }

        @Test
        @DisplayName("TC-INT-003: second_question_number 하위 문항 그룹핑")
        void testFullAnalysis_SecondQuestionNumber_Priority() {
            // Given: second_question_number 포함
            List<LayoutInfo> layouts = createLayoutsWithSubQuestions();
            List<OCRResult> ocrs = createOCRsWithSubQuestions();

            // When
            UnifiedAnalysisResult result = engine.performUnifiedAnalysis(layouts, ocrs, Collections.emptyList());

            // Then
            QuestionData mainQuestion = result.getStructuredData().getQuestions().stream()
                .filter(q -> "001".equals(q.getQuestionNumber()))
                .findFirst()
                .orElseThrow();

            assertThat(mainQuestion.getSubQuestions())
                .as("하위 문항 존재")
                .isNotNull()
                .as("2개 하위 문항")
                .hasSize(2)
                .containsKeys("1", "2");
        }

        @Test
        @DisplayName("TC-INT-004: 다단 레이아웃 + question_type")
        void testFullAnalysis_MultiColumn_WithQuestionType() {
            // Given: 2단 레이아웃 + question_type
            List<LayoutInfo> layouts = createTwoColumnLayoutWithQuestionType();
            List<OCRResult> ocrs = createTwoColumnOCRs();

            // When
            UnifiedAnalysisResult result = engine.performUnifiedAnalysis(layouts, ocrs, Collections.emptyList());

            // Then
            assertThat(result.isSuccess()).isTrue();

            List<QuestionData> questions = result.getStructuredData().getQuestions();
            assertThat(questions)
                .as("4개 문제 (003, type_*, 004, 005)")
                .hasSize(4);

            // columnIndex 검증
            questions.forEach(q -> {
                assertThat(q.getColumnIndex())
                    .as("columnIndex 존재")
                    .isNotNull()
                    .as("columnIndex 범위 0-1")
                    .isBetween(0, 1);
            });
        }
    }

    // ========== 헬퍼 메서드 ==========

    private List<LayoutInfo> createLayoutsWithQuestionType() {
        return List.of(
            createLayout(1, "question_number", new int[]{100, 200, 150, 250}, 0.90),
            createLayout(5, "question_type", new int[]{300, 500, 500, 550}, 0.92),
            createLayout(10, "question_number", new int[]{100, 1500, 150, 1550}, 0.88)
        );
    }

    private List<OCRResult> createOCRsWithQuestionType() {
        return List.of(
            createOCR(1, "003", 0.85),
            createOCR(5, "유형01", 0.88),
            createOCR(10, "004", 0.86)
        );
    }

    private List<LayoutInfo> createLayoutsWithUnit() {
        return List.of(
            createLayout(1, "unit", new int[]{100, 100, 300, 150}, 0.95),
            createLayout(2, "question_number", new int[]{100, 500, 150, 550}, 0.90),
            createLayout(3, "question_number", new int[]{100, 1000, 150, 1050}, 0.92)
        );
    }

    private List<OCRResult> createOCRsWithUnit() {
        return List.of(
            createOCR(1, "I. 지수함수와 로그함수", 0.90),
            createOCR(2, "001", 0.88),
            createOCR(3, "002", 0.89)
        );
    }

    private List<LayoutInfo> createLayoutsWithSubQuestions() {
        return List.of(
            createLayout(1, "question_number", new int[]{100, 200, 150, 250}, 0.90),
            createLayout(2, "second_question_number", new int[]{200, 600, 250, 650}, 0.88),
            createLayout(3, "second_question_number", new int[]{200, 1000, 250, 1050}, 0.89)
        );
    }

    private List<OCRResult> createOCRsWithSubQuestions() {
        return List.of(
            createOCR(1, "001", 0.85),
            createOCR(2, "(1)", 0.85),
            createOCR(3, "(2)", 0.86)
        );
    }

    private List<LayoutInfo> createTwoColumnLayoutWithQuestionType() {
        return List.of(
            // 왼쪽 컬럼
            createLayout(1, "question_number", new int[]{100, 500, 150, 550}, 0.90),
            createLayout(2, "question_type", new int[]{100, 1000, 300, 1050}, 0.92),
            // 오른쪽 컬럼
            createLayout(3, "question_number", new int[]{600, 500, 650, 550}, 0.92),
            createLayout(4, "question_number", new int[]{600, 1000, 650, 1050}, 0.88)
        );
    }

    private List<OCRResult> createTwoColumnOCRs() {
        return List.of(
            createOCR(1, "003", 0.85),
            createOCR(2, "유형A", 0.88),
            createOCR(3, "004", 0.87),
            createOCR(4, "005", 0.86)
        );
    }

    private LayoutInfo createLayout(int id, String className, int[] box, double confidence) {
        LayoutInfo layout = new LayoutInfo();
        layout.setId(id);
        layout.setClassName(className);
        layout.setBox(box);
        layout.setConfidence(confidence);
        return layout;
    }

    private OCRResult createOCR(int id, String text, double confidence) {
        OCRResult ocr = new OCRResult();
        ocr.setId(id);
        ocr.setText(text);
        ocr.setConfidence(confidence);
        ocr.setCoordinates(new int[]{100, 200, 300, 250});
        return ocr;
    }
}
```

---

## 4. 성능 테스트 예시

### 파일: PerformanceV07Test.java

```java
package com.smarteye.application.analysis;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * v0.7 성능 테스트
 */
@SpringBootTest
@DisplayName("v0.7 성능 벤치마크")
class PerformanceV07Test {

    @Autowired
    private UnifiedAnalysisEngine engine;

    private static final int WARMUP_ITERATIONS = 3;
    private static final int TEST_ITERATIONS = 10;

    @Test
    @DisplayName("TC-PERF-001: convertToPositionInfoMap() 성능")
    void testPerformance_ConvertToPositionInfoMap() {
        // Given: 20개 questionPositions (question_type 2개 포함)
        Map<String, Integer> questionPositions = createQuestionPositions(20, 2);
        List<LayoutInfo> layouts = createLayoutsForBenchmark(20);
        List<OCRResult> ocrs = createOCRsForBenchmark(20);

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            engine.convertToPositionInfoMap(questionPositions, layouts, ocrs);
        }

        // When: 평균 시간 측정
        long totalTime = 0;
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            engine.convertToPositionInfoMap(questionPositions, layouts, ocrs);
            totalTime += (System.nanoTime() - start);
        }

        // Then: 평균 < 10ms
        long avgTimeMs = totalTime / TEST_ITERATIONS / 1_000_000;
        assertThat(avgTimeMs)
            .as("평균 처리 시간 < 10ms")
            .isLessThan(10);
    }

    @Test
    @DisplayName("TC-PERF-002: 전체 파이프라인 성능")
    void testPerformance_FullPipeline() {
        // Given: A4 페이지 시뮬레이션 (100개 요소)
        List<LayoutInfo> layouts = createRealisticPage(100);
        List<OCRResult> ocrs = createRealisticOCRs(100);
        List<AIDescriptionResult> aiResults = Collections.emptyList();

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            engine.performUnifiedAnalysis(layouts, ocrs, aiResults);
        }

        // When
        long totalTime = 0;
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.currentTimeMillis();
            engine.performUnifiedAnalysis(layouts, ocrs, aiResults);
            totalTime += (System.currentTimeMillis() - start);
        }

        // Then: 평균 < 3초
        long avgTimeMs = totalTime / TEST_ITERATIONS;
        assertThat(avgTimeMs)
            .as("평균 분석 시간 < 3000ms")
            .isLessThan(3000);
    }

    // 헬퍼 메서드 생략...
}
```

---

## 5. 실행 명령어

### Gradle 태스크

```bash
# v0.7 전체 테스트 실행
./gradlew test

# 특정 클래스만 실행
./gradlew test --tests "*QuestionNumberExtractorV07Test"

# 통합 테스트만 실행
./gradlew test --tests "*IntegrationTest"

# 커버리지 리포트
./gradlew jacocoTestReport

# 성능 테스트
./gradlew test --tests "*PerformanceV07Test"
```

---

**문서 버전**: 1.0
**작성일**: 2025-10-18
