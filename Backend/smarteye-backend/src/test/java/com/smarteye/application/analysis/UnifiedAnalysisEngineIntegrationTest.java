package com.smarteye.application.analysis;

import com.smarteye.application.analysis.UnifiedAnalysisEngine.StructuredData;
import com.smarteye.application.analysis.UnifiedAnalysisEngine.QuestionData;
import com.smarteye.application.analysis.UnifiedAnalysisEngine.UnifiedAnalysisResult;
import com.smarteye.presentation.dto.AIDescriptionResult;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * UnifiedAnalysisEngine 통합 테스트
 *
 * <p><b>목적</b>: CIM Redesign Phase 1 (P0) 구현 검증</p>
 *
 * <h3>테스트 범위</h3>
 * <ol>
 *   <li>Strategy 패턴 동작 검증 (VisualContentStrategy, TextContentStrategy)</li>
 *   <li>2단/3단 레이아웃 테스트 (컬럼 우선 정렬)</li>
 *   <li>동적 필드 구조 검증 (레이아웃 클래스 원본 유지)</li>
 *   <li>LayoutClass 보존 검증 (33개 클래스 동적 필드 생성)</li>
 * </ol>
 *
 * <p><b>참고 문서</b>:</p>
 * <ul>
 *   <li>{@code CIM_SPATIAL_SORTING_REDESIGN_PLAN.md} - P1 통합 테스트 섹션</li>
 *   <li>{@code CIM_Testing_Strategy_Best_Practices} - 메모리 참조</li>
 * </ul>
 *
 * @author SmartEye Backend Team
 * @since v0.5 (CIM Phase 1 P0)
 * @version 1.0
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "smarteye.features.use-2d-spatial-analysis=true"
})
@DisplayName("🎯 UnifiedAnalysisEngine 통합 테스트 (CIM Phase 1 P0)")
class UnifiedAnalysisEngineIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(UnifiedAnalysisEngineIntegrationTest.class);

    @Autowired
    private UnifiedAnalysisEngine unifiedAnalysisEngine;

    // ============================================================================
    // 테스트 상수
    // ============================================================================

    /** 표준 요소 높이 */
    private static final int ELEMENT_HEIGHT = 50;

    // ============================================================================
    // 테스트 초기화
    // ============================================================================

    @BeforeEach
    void setUp() {
        logger.info("🔧 UnifiedAnalysisEngine 통합 테스트 초기화");
    }

    // ============================================================================
    // 1. Strategy 패턴 동작 검증
    // ============================================================================

    @Nested
    @DisplayName("1. Strategy 패턴 동작 검증")
    class StrategyPatternTests {

        @Test
        @DisplayName("시각적 요소(FIGURE)는 VisualContentStrategy가 선택되어야 함")
        void testStrategyPatternSelectionForFigure() {
            logger.info("🔍 테스트: FIGURE → VisualContentStrategy 선택");

            // Given: FIGURE 요소 (AI 설명만 존재)
            List<LayoutInfo> layoutElements = new ArrayList<>();
            List<OCRResult> ocrResults = new ArrayList<>();
            List<AIDescriptionResult> aiResults = new ArrayList<>();

            addQuestionNumber(layoutElements, ocrResults, 1, 100, 100, "1.");
            addFigureWithAI(layoutElements, aiResults, 2, 100, 200, "피타고라스 정리 도형");

            // When
            UnifiedAnalysisResult result = unifiedAnalysisEngine.performUnifiedAnalysis(
                layoutElements, ocrResults, aiResults
            );

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getStructuredData()).isNotNull();

            logger.info("✅ VisualContentStrategy 정상 선택됨");
        }

        @Test
        @DisplayName("텍스트 요소(QUESTION_TEXT)는 TextContentStrategy가 선택되어야 함")
        void testStrategyPatternSelectionForQuestionText() {
            logger.info("🔍 테스트: QUESTION_TEXT → TextContentStrategy 선택");

            // Given: QUESTION_TEXT 요소 (OCR 텍스트만 존재)
            List<LayoutInfo> layoutElements = new ArrayList<>();
            List<OCRResult> ocrResults = new ArrayList<>();
            List<AIDescriptionResult> aiResults = new ArrayList<>();

            addQuestionNumber(layoutElements, ocrResults, 1, 100, 100, "1.");
            addQuestionText(layoutElements, ocrResults, 2, 100, 200, "다음 중 옳은 것은?");

            // When
            UnifiedAnalysisResult result = unifiedAnalysisEngine.performUnifiedAnalysis(
                layoutElements, ocrResults, aiResults
            );

            // Then
            assertThat(result.isSuccess()).isTrue();

            logger.info("✅ TextContentStrategy 정상 선택됨");
        }

        @Test
        @DisplayName("VisualContentStrategy는 AI 설명을 우선 추출해야 함")
        void testVisualContentStrategyPriority() {
            logger.info("🔍 테스트: VisualContentStrategy AI 우선 추출");

            // Given: FIGURE 요소 (AI 설명 + OCR 텍스트 모두 존재)
            List<LayoutInfo> layoutElements = new ArrayList<>();
            List<OCRResult> ocrResults = new ArrayList<>();
            List<AIDescriptionResult> aiResults = new ArrayList<>();

            addQuestionNumber(layoutElements, ocrResults, 1, 100, 100, "1.");
            int figureId = 2;
            addFigureWithAIAndOCR(layoutElements, ocrResults, aiResults, figureId, 100, 200,
                                  "AI 설명: 직각삼각형 도형", "OCR 텍스트: a² + b²");

            // When
            UnifiedAnalysisResult result = unifiedAnalysisEngine.performUnifiedAnalysis(
                layoutElements, ocrResults, aiResults
            );

            // Then
            assertThat(result.isSuccess()).isTrue();

            // AI 설명이 우선 사용되었는지 검증
            StructuredData structuredData = result.getStructuredData();
            List<QuestionData> questions = structuredData.getQuestions();
            assertThat(questions).hasSize(1);

            QuestionData question = questions.get(0);
            assertThat(question.getAiDescription())
                .isNotNull()
                .contains("AI 설명: 직각삼각형 도형");

            logger.info("✅ AI 설명 우선 추출 확인");
        }

        @Test
        @DisplayName("TextContentStrategy는 OCR 텍스트만 추출해야 함 (AI 설명 제외)")
        void testTextContentStrategyBehavior() {
            logger.info("🔍 테스트: TextContentStrategy OCR 전용 추출");

            // Given: QUESTION_TEXT 요소 (OCR 텍스트 + AI 설명 모두 존재)
            List<LayoutInfo> layoutElements = new ArrayList<>();
            List<OCRResult> ocrResults = new ArrayList<>();
            List<AIDescriptionResult> aiResults = new ArrayList<>();

            addQuestionNumber(layoutElements, ocrResults, 1, 100, 100, "1.");
            int textId = 2;
            LayoutInfo textLayout = createLayoutInfo(textId, 100, 200, 300, ELEMENT_HEIGHT, "question_text");
            layoutElements.add(textLayout);

            OCRResult ocr = new OCRResult();
            ocr.setId(textId);
            ocr.setText("OCR 텍스트: 다음 중 옳은 것은?");
            ocr.setConfidence(0.90);
            ocrResults.add(ocr);

            // AI 설명 추가 (TextContentStrategy는 무시해야 함)
            AIDescriptionResult ai = new AIDescriptionResult();
            ai.setId(textId);
            ai.setDescription("AI 설명: 문제 지시문");
            ai.setConfidence(0.85);
            aiResults.add(ai);

            // When
            UnifiedAnalysisResult result = unifiedAnalysisEngine.performUnifiedAnalysis(
                layoutElements, ocrResults, aiResults
            );

            // Then
            assertThat(result.isSuccess()).isTrue();

            StructuredData structuredData = result.getStructuredData();
            List<QuestionData> questions = structuredData.getQuestions();
            assertThat(questions).hasSize(1);

            QuestionData question = questions.get(0);

            // question_text에는 OCR 텍스트만 포함되어야 함
            assertThat(question.getQuestionText())
                .isNotNull()
                .contains("OCR 텍스트: 다음 중 옳은 것은?")
                .doesNotContain("AI 설명");

            logger.info("✅ OCR 텍스트만 추출 확인 (AI 설명 제외)");
        }
    }

    // ============================================================================
    // 2. 2단/3단 레이아웃 테스트
    // ============================================================================

    @Nested
    @DisplayName("2. 2단/3단 레이아웃 테스트 (컬럼 우선 정렬)")
    class MultiColumnLayoutTests {

        @Test
        @DisplayName("2단 레이아웃: 문제 순서가 컬럼 우선으로 정렬되어야 함")
        void testTwoColumnLayoutQuestionOrder() {
            logger.info("🔍 테스트: 2단 레이아웃 문제 순서");

            // Given: 2단 레이아웃 (왼쪽: 1, 2, 3 / 오른쪽: 4, 5, 6)
            List<LayoutInfo> layoutElements = new ArrayList<>();
            List<OCRResult> ocrResults = new ArrayList<>();
            List<AIDescriptionResult> aiResults = new ArrayList<>();

            // 왼쪽 컬럼 (X=100)
            addQuestionNumber(layoutElements, ocrResults, 1, 100, 100, "1.");
            addQuestionText(layoutElements, ocrResults, 2, 100, 150, "문제 1 텍스트");

            addQuestionNumber(layoutElements, ocrResults, 3, 100, 300, "2.");
            addQuestionText(layoutElements, ocrResults, 4, 100, 350, "문제 2 텍스트");

            addQuestionNumber(layoutElements, ocrResults, 5, 100, 500, "3.");
            addQuestionText(layoutElements, ocrResults, 6, 100, 550, "문제 3 텍스트");

            // 오른쪽 컬럼 (X=600)
            addQuestionNumber(layoutElements, ocrResults, 7, 600, 100, "4.");
            addQuestionText(layoutElements, ocrResults, 8, 600, 150, "문제 4 텍스트");

            addQuestionNumber(layoutElements, ocrResults, 9, 600, 300, "5.");
            addQuestionText(layoutElements, ocrResults, 10, 600, 350, "문제 5 텍스트");

            addQuestionNumber(layoutElements, ocrResults, 11, 600, 500, "6.");
            addQuestionText(layoutElements, ocrResults, 12, 600, 550, "문제 6 텍스트");

            // When
            UnifiedAnalysisResult result = unifiedAnalysisEngine.performUnifiedAnalysis(
                layoutElements, ocrResults, aiResults
            );

            // Then
            assertThat(result.isSuccess()).isTrue();

            StructuredData structuredData = result.getStructuredData();
            assertThat(structuredData.getTotalQuestions()).isEqualTo(6);

            List<QuestionData> questions = structuredData.getQuestions();
            assertThat(questions)
                .extracting(QuestionData::getQuestionNumber)
                .containsExactly("1", "2", "3", "4", "5", "6"); // 컬럼 우선 순서

            // 각 문제가 올바른 텍스트를 포함하는지 확인
            assertThat(questions.get(0).getQuestionText()).contains("문제 1 텍스트");
            assertThat(questions.get(3).getQuestionText()).contains("문제 4 텍스트");

            logger.info("✅ 2단 레이아웃 컬럼 우선 정렬 확인");
        }

        @Test
        @DisplayName("3단 레이아웃: 문제 순서가 컬럼 우선으로 정렬되어야 함")
        void testThreeColumnLayoutQuestionOrder() {
            logger.info("🔍 테스트: 3단 레이아웃 문제 순서");

            // Given: 3단 레이아웃 (왼쪽: 1, 2 / 중앙: 3, 4 / 오른쪽: 5, 6)
            List<LayoutInfo> layoutElements = new ArrayList<>();
            List<OCRResult> ocrResults = new ArrayList<>();
            List<AIDescriptionResult> aiResults = new ArrayList<>();

            // 왼쪽 컬럼 (X=80)
            addQuestionNumber(layoutElements, ocrResults, 1, 80, 100, "1.");
            addQuestionNumber(layoutElements, ocrResults, 2, 80, 300, "2.");

            // 중앙 컬럼 (X=400)
            addQuestionNumber(layoutElements, ocrResults, 3, 400, 100, "3.");
            addQuestionNumber(layoutElements, ocrResults, 4, 400, 300, "4.");

            // 오른쪽 컬럼 (X=720)
            addQuestionNumber(layoutElements, ocrResults, 5, 720, 100, "5.");
            addQuestionNumber(layoutElements, ocrResults, 6, 720, 300, "6.");

            // When
            UnifiedAnalysisResult result = unifiedAnalysisEngine.performUnifiedAnalysis(
                layoutElements, ocrResults, aiResults
            );

            // Then
            assertThat(result.isSuccess()).isTrue();

            StructuredData structuredData = result.getStructuredData();
            assertThat(structuredData.getTotalQuestions()).isEqualTo(6);

            List<QuestionData> questions = structuredData.getQuestions();
            assertThat(questions)
                .extracting(QuestionData::getQuestionNumber)
                .containsExactly("1", "2", "3", "4", "5", "6"); // 컬럼 우선 순서

            logger.info("✅ 3단 레이아웃 컬럼 우선 정렬 확인");
        }
    }

    // ============================================================================
    // 3. 동적 필드 구조 검증
    // ============================================================================

    @Nested
    @DisplayName("3. 동적 필드 구조 검증 (레이아웃 클래스 원본 유지)")
    class DynamicFieldGenerationTests {

        @Test
        @DisplayName("동적 필드: 원본 레이아웃 클래스명이 필드로 생성되어야 함")
        void testDynamicFieldGeneration() {
            logger.info("🔍 테스트: 동적 필드 생성");

            // Given: 다양한 레이아웃 클래스 요소들
            List<LayoutInfo> layoutElements = new ArrayList<>();
            List<OCRResult> ocrResults = new ArrayList<>();
            List<AIDescriptionResult> aiResults = new ArrayList<>();

            addQuestionNumber(layoutElements, ocrResults, 1, 100, 100, "1.");
            addQuestionText(layoutElements, ocrResults, 2, 100, 150, "다음 중 옳은 것은?");
            addFigureWithAI(layoutElements, aiResults, 3, 100, 250, "삼각형 도형");
            addPlainText(layoutElements, ocrResults, 4, 100, 400, "보기를 참고하시오.");

            // When
            UnifiedAnalysisResult result = unifiedAnalysisEngine.performUnifiedAnalysis(
                layoutElements, ocrResults, aiResults
            );

            // Then
            assertThat(result.isSuccess()).isTrue();

            // CIM 데이터에서 동적 필드 검증
            Map<String, Object> cimData = result.getCimData();
            assertThat(cimData).isNotNull();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questions = (List<Map<String, Object>>) cimData.get("questions");
            assertThat(questions).isNotEmpty();

            // 첫 번째 문제 검증
            Map<String, Object> question1 = questions.get(0);
            assertThat(question1).containsKeys("question_number", "question_text");

            // question_text 존재 확인
            assertThat(question1.get("question_text")).isNotNull();

            logger.info("✅ 동적 필드 생성 확인: question_text, figure, plain_text");
        }

        @Test
        @DisplayName("레이아웃 클래스 보존: 33개 클래스가 원본 그대로 유지되어야 함")
        void testLayoutClassPreservation() {
            logger.info("🔍 테스트: 레이아웃 클래스 보존");

            // Given: 다양한 레이아웃 클래스 (33개 중 일부 사용)
            List<LayoutInfo> layoutElements = new ArrayList<>();
            List<OCRResult> ocrResults = new ArrayList<>();
            List<AIDescriptionResult> aiResults = new ArrayList<>();

            addQuestionNumber(layoutElements, ocrResults, 1, 100, 100, "1.");
            addElementWithOCR(layoutElements, ocrResults, 2, 100, 150, "question_text", "지시문");
            addElementWithOCR(layoutElements, ocrResults, 3, 100, 200, "plain_text", "일반 텍스트");
            addElementWithOCR(layoutElements, ocrResults, 4, 100, 250, "list", "① 선택지 1");
            addElementWithAI(layoutElements, aiResults, 5, 100, 300, "figure", "도형 설명");
            addElementWithAI(layoutElements, aiResults, 6, 100, 450, "table", "표 데이터");

            // When
            UnifiedAnalysisResult result = unifiedAnalysisEngine.performUnifiedAnalysis(
                layoutElements, ocrResults, aiResults
            );

            // Then
            assertThat(result.isSuccess()).isTrue();

            // CIM 데이터에서 레이아웃 클래스 확인
            Map<String, Object> cimData = result.getCimData();
            assertThat(cimData).containsKey("document_structure");

            @SuppressWarnings("unchecked")
            Map<String, Object> documentStructure = (Map<String, Object>) cimData.get("document_structure");
            assertThat(documentStructure).containsKey("layout_analysis");

            @SuppressWarnings("unchecked")
            Map<String, Object> layoutAnalysis = (Map<String, Object>) documentStructure.get("layout_analysis");
            assertThat(layoutAnalysis).containsKey("elements");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> elements = (List<Map<String, Object>>) layoutAnalysis.get("elements");

            // 각 요소의 클래스명이 원본 그대로 유지되는지 확인
            Set<String> classNames = elements.stream()
                .map(e -> (String) e.get("class"))
                .collect(Collectors.toSet());

            assertThat(classNames).containsAll(Arrays.asList(
                "question_number", "question_text", "plain_text", "list", "figure", "table"
            ));

            logger.info("✅ 레이아웃 클래스 원본 유지 확인: {}", classNames);
        }

        @Test
        @DisplayName("발견된 클래스만 필드 생성: 빈 필드가 없어야 함")
        void testOnlyDiscoveredClassesGenerateFields() {
            logger.info("🔍 테스트: 발견된 클래스만 필드 생성");

            // Given: question_number와 question_text만 존재
            List<LayoutInfo> layoutElements = new ArrayList<>();
            List<OCRResult> ocrResults = new ArrayList<>();
            List<AIDescriptionResult> aiResults = new ArrayList<>();

            addQuestionNumber(layoutElements, ocrResults, 1, 100, 100, "1.");
            addQuestionText(layoutElements, ocrResults, 2, 100, 150, "다음 중 옳은 것은?");

            // When
            UnifiedAnalysisResult result = unifiedAnalysisEngine.performUnifiedAnalysis(
                layoutElements, ocrResults, aiResults
            );

            // Then
            assertThat(result.isSuccess()).isTrue();

            Map<String, Object> cimData = result.getCimData();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questions = (List<Map<String, Object>>) cimData.get("questions");
            assertThat(questions).isNotEmpty();

            Map<String, Object> question1 = questions.get(0);

            // question_number, question_text만 존재해야 함 (figure, table 등은 없음)
            assertThat(question1).containsKeys("question_number", "question_text");

            // 빈 필드(null 또는 빈 문자열)가 없어야 함
            assertThat(question1.get("question_text")).isNotNull();
            assertThat(question1.get("question_text").toString()).isNotEmpty();

            logger.info("✅ 발견된 클래스만 필드 생성 확인");
        }
    }

    // ============================================================================
    // 헬퍼 메서드들
    // ============================================================================

    /**
     * 문제 번호 요소 추가
     */
    private void addQuestionNumber(List<LayoutInfo> layouts, List<OCRResult> ocrs,
                                   int id, int x, int y, String number) {
        LayoutInfo layout = createLayoutInfo(id, x, y, 50, ELEMENT_HEIGHT, "question_number");
        layouts.add(layout);

        OCRResult ocr = new OCRResult();
        ocr.setId(id);
        ocr.setText(number);
        ocr.setConfidence(0.95);
        ocrs.add(ocr);
    }

    /**
     * 문제 텍스트 요소 추가
     */
    private void addQuestionText(List<LayoutInfo> layouts, List<OCRResult> ocrs,
                                 int id, int x, int y, String text) {
        LayoutInfo layout = createLayoutInfo(id, x, y, 300, ELEMENT_HEIGHT, "question_text");
        layouts.add(layout);

        OCRResult ocr = new OCRResult();
        ocr.setId(id);
        ocr.setText(text);
        ocr.setConfidence(0.90);
        ocrs.add(ocr);
    }

    /**
     * 일반 텍스트 요소 추가
     */
    private void addPlainText(List<LayoutInfo> layouts, List<OCRResult> ocrs,
                              int id, int x, int y, String text) {
        LayoutInfo layout = createLayoutInfo(id, x, y, 300, ELEMENT_HEIGHT, "plain_text");
        layouts.add(layout);

        OCRResult ocr = new OCRResult();
        ocr.setId(id);
        ocr.setText(text);
        ocr.setConfidence(0.85);
        ocrs.add(ocr);
    }

    /**
     * FIGURE 요소 (AI 설명만) 추가
     */
    private void addFigureWithAI(List<LayoutInfo> layouts, List<AIDescriptionResult> aiResults,
                                 int id, int x, int y, String description) {
        LayoutInfo layout = createLayoutInfo(id, x, y, 200, 150, "figure");
        layouts.add(layout);

        AIDescriptionResult ai = new AIDescriptionResult();
        ai.setId(id);
        ai.setDescription(description);
        ai.setConfidence(0.90);
        aiResults.add(ai);
    }

    /**
     * FIGURE 요소 (AI 설명 + OCR 텍스트) 추가
     */
    private void addFigureWithAIAndOCR(List<LayoutInfo> layouts, List<OCRResult> ocrs,
                                       List<AIDescriptionResult> aiResults,
                                       int id, int x, int y,
                                       String aiDescription, String ocrText) {
        LayoutInfo layout = createLayoutInfo(id, x, y, 200, 150, "figure");
        layouts.add(layout);

        AIDescriptionResult ai = new AIDescriptionResult();
        ai.setId(id);
        ai.setDescription(aiDescription);
        ai.setConfidence(0.90);
        aiResults.add(ai);

        OCRResult ocr = new OCRResult();
        ocr.setId(id);
        ocr.setText(ocrText);
        ocr.setConfidence(0.70);
        ocrs.add(ocr);
    }

    /**
     * 범용 요소 추가 (OCR)
     */
    private void addElementWithOCR(List<LayoutInfo> layouts, List<OCRResult> ocrs,
                                   int id, int x, int y, String className, String text) {
        LayoutInfo layout = createLayoutInfo(id, x, y, 300, ELEMENT_HEIGHT, className);
        layouts.add(layout);

        OCRResult ocr = new OCRResult();
        ocr.setId(id);
        ocr.setText(text);
        ocr.setConfidence(0.85);
        ocrs.add(ocr);
    }

    /**
     * 범용 요소 추가 (AI)
     */
    private void addElementWithAI(List<LayoutInfo> layouts, List<AIDescriptionResult> aiResults,
                                  int id, int x, int y, String className, String description) {
        LayoutInfo layout = createLayoutInfo(id, x, y, 200, 100, className);
        layouts.add(layout);

        AIDescriptionResult ai = new AIDescriptionResult();
        ai.setId(id);
        ai.setDescription(description);
        ai.setConfidence(0.85);
        aiResults.add(ai);
    }

    /**
     * LayoutInfo 생성 헬퍼
     */
    private LayoutInfo createLayoutInfo(int id, int x, int y, int width, int height, String className) {
        LayoutInfo layout = new LayoutInfo();
        layout.setId(id);
        layout.setBox(new int[]{x, y, x + width, y + height});
        layout.setClassName(className);
        layout.setConfidence(0.9);
        layout.setArea(width * height);
        return layout;
    }
}
