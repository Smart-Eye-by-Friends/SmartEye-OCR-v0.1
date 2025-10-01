package com.smarteye.application.analysis;

import com.smarteye.application.analysis.UnifiedAnalysisEngine.AnalysisElement;
import com.smarteye.application.analysis.UnifiedAnalysisEngine.StructuredData;
import com.smarteye.application.analysis.UnifiedAnalysisEngine.QuestionData;
import com.smarteye.application.analysis.engine.ColumnDetector;
import com.smarteye.application.analysis.engine.ColumnDetector.ColumnRange;
import com.smarteye.application.analysis.engine.ColumnDetector.PositionInfo;
import com.smarteye.application.analysis.engine.Spatial2DAnalyzer;
import com.smarteye.presentation.dto.AIDescriptionResult;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * 2D 공간 분석 통합 테스트
 *
 * <p>CBHLS Phase 2 - 2D spatial analysis 기능 검증</p>
 * <p>Feature Flag: smarteye.features.use-2d-spatial-analysis=true</p>
 *
 * <h3>테스트 시나리오</h3>
 * <ol>
 *   <li>표준 2단 신문 레이아웃 - 교차 컬럼 할당 방지</li>
 *   <li>비대칭 2단 레이아웃 - 높이 차이 처리</li>
 *   <li>3단 레이아웃 (넓은 중앙) - 다중 컬럼 감지</li>
 *   <li>2단 걸친 이미지 - 중심점 기반 할당</li>
 * </ol>
 *
 * @author SmartEye Backend Team
 * @since v0.7
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "smarteye.features.use-2d-spatial-analysis=true"
})
@DisplayName("🎯 2D 공간 분석 통합 테스트")
public class Spatial2DIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(Spatial2DIntegrationTest.class);

    @Autowired
    private UnifiedAnalysisEngine unifiedAnalysisEngine;

    @Autowired
    private ColumnDetector columnDetector;

    @Autowired
    private Spatial2DAnalyzer spatial2DAnalyzer;

    @Autowired
    private QuestionNumberExtractor questionNumberExtractor;

    // ============================================================================
    // 테스트 상수
    // ============================================================================

    /** 표준 페이지 너비 (A4 기준) */
    private static final int PAGE_WIDTH = 1000;

    /** 표준 요소 높이 */
    private static final int ELEMENT_HEIGHT = 50;

    // ============================================================================
    // 테스트 초기화
    // ============================================================================

    @BeforeEach
    void setUp() {
        logger.info("🔧 2D 공간 분석 통합 테스트 초기화");
    }

    // ============================================================================
    // 테스트 1: 표준 2단 신문 레이아웃
    // ============================================================================

    @Test
    @DisplayName("통합: 표준 2단 신문 레이아웃 - 교차 컬럼 할당 방지")
    void testStandard2ColumnNewspaperLayout() {
        logger.info("🔍 테스트 1: 표준 2단 레이아웃 시작");

        // Given: 2단 레이아웃 (왼쪽: 문제 1,2,3 / 오른쪽: 문제 4,5,6)
        List<LayoutInfo> layoutElements = new ArrayList<>();
        List<OCRResult> ocrResults = new ArrayList<>();
        List<AIDescriptionResult> aiResults = new ArrayList<>();

        // 왼쪽 컬럼 문제들 (X=100)
        addQuestionNumber(layoutElements, ocrResults, 1, 100, 100, "1");
        addQuestionNumber(layoutElements, ocrResults, 2, 100, 300, "2");
        addQuestionNumber(layoutElements, ocrResults, 3, 100, 500, "3");

        // 오른쪽 컬럼 문제들 (X=600)
        addQuestionNumber(layoutElements, ocrResults, 4, 600, 100, "4");
        addQuestionNumber(layoutElements, ocrResults, 5, 600, 300, "5");
        addQuestionNumber(layoutElements, ocrResults, 6, 600, 500, "6");

        // 왼쪽 컬럼 요소들 (문제 1, 2, 3에 속함)
        addTextElement(layoutElements, ocrResults, 7, 100, 150, "문제 1의 텍스트");
        addTextElement(layoutElements, ocrResults, 8, 100, 350, "문제 2의 텍스트");
        addTextElement(layoutElements, ocrResults, 9, 100, 550, "문제 3의 텍스트");

        // 오른쪽 컬럼 요소들 (문제 4, 5, 6에 속함)
        addTextElement(layoutElements, ocrResults, 10, 600, 150, "문제 4의 텍스트");
        addTextElement(layoutElements, ocrResults, 11, 600, 350, "문제 5의 텍스트");
        addTextElement(layoutElements, ocrResults, 12, 600, 550, "문제 6의 텍스트");

        // When: 통합 분석 실행
        UnifiedAnalysisEngine.UnifiedAnalysisResult result = unifiedAnalysisEngine.performUnifiedAnalysis(
            layoutElements, ocrResults, aiResults
        );

        // Then: 검증
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStructuredData()).isNotNull();

        StructuredData structuredData = result.getStructuredData();
        assertThat(structuredData.getTotalQuestions()).isEqualTo(6);

        // 각 문제별 요소 확인
        Map<Integer, QuestionData> questionsById = structuredData.getQuestions().stream()
            .collect(Collectors.toMap(QuestionData::getQuestionNumber, q -> q));

        // 왼쪽 컬럼 문제들은 왼쪽 요소만 가져야 함
        assertQuestionHasElement(questionsById, 1, "문제 1의 텍스트");
        assertQuestionHasElement(questionsById, 2, "문제 2의 텍스트");
        assertQuestionHasElement(questionsById, 3, "문제 3의 텍스트");

        // 오른쪽 컬럼 문제들은 오른쪽 요소만 가져야 함
        assertQuestionHasElement(questionsById, 4, "문제 4의 텍스트");
        assertQuestionHasElement(questionsById, 5, "문제 5의 텍스트");
        assertQuestionHasElement(questionsById, 6, "문제 6의 텍스트");

        // 교차 할당 검증 (문제 1이 오른쪽 요소를 가지면 안됨)
        assertQuestionDoesNotHaveElement(questionsById, 1, "문제 4의 텍스트");
        assertQuestionDoesNotHaveElement(questionsById, 4, "문제 1의 텍스트");

        logger.info("✅ 테스트 1 완료: 교차 컬럼 할당이 올바르게 방지됨");
    }

    // ============================================================================
    // 테스트 2: 비대칭 2단 레이아웃
    // ============================================================================

    @Test
    @DisplayName("통합: 비대칭 2단 레이아웃 - 높이 차이 처리")
    void testAsymmetric2ColumnLayout() {
        logger.info("🔍 테스트 2: 비대칭 2단 레이아웃 시작");

        // Given: 왼쪽이 짧고 오른쪽이 긴 레이아웃
        List<LayoutInfo> layoutElements = new ArrayList<>();
        List<OCRResult> ocrResults = new ArrayList<>();
        List<AIDescriptionResult> aiResults = new ArrayList<>();

        // 왼쪽 컬럼 (짧음): 문제 1, 2
        addQuestionNumber(layoutElements, ocrResults, 1, 100, 100, "1");
        addQuestionNumber(layoutElements, ocrResults, 2, 100, 400, "2");

        // 오른쪽 컬럼 (김): 문제 3, 4, 5, 6
        addQuestionNumber(layoutElements, ocrResults, 3, 600, 100, "3");
        addQuestionNumber(layoutElements, ocrResults, 4, 600, 300, "4");
        addQuestionNumber(layoutElements, ocrResults, 5, 600, 500, "5");
        addQuestionNumber(layoutElements, ocrResults, 6, 600, 700, "6");

        // 경계 근처 요소들 (컬럼 경계 테스트)
        addTextElement(layoutElements, ocrResults, 7, 120, 200, "왼쪽 경계 텍스트");
        addTextElement(layoutElements, ocrResults, 8, 580, 200, "오른쪽 경계 텍스트");

        // When
        UnifiedAnalysisEngine.UnifiedAnalysisResult result = unifiedAnalysisEngine.performUnifiedAnalysis(
            layoutElements, ocrResults, aiResults
        );

        // Then
        assertThat(result.isSuccess()).isTrue();
        StructuredData structuredData = result.getStructuredData();
        assertThat(structuredData.getTotalQuestions()).isEqualTo(6);

        Map<Integer, QuestionData> questionsById = structuredData.getQuestions().stream()
            .collect(Collectors.toMap(QuestionData::getQuestionNumber, q -> q));

        // 경계 요소들이 올바른 컬럼에 할당되었는지 확인
        assertQuestionHasElement(questionsById, 1, "왼쪽 경계 텍스트");
        assertQuestionHasElement(questionsById, 3, "오른쪽 경계 텍스트");

        // 높이가 다르지만 각 컬럼의 문제들이 모두 감지되어야 함
        assertThat(questionsById).containsKeys(1, 2, 3, 4, 5, 6);

        logger.info("✅ 테스트 2 완료: 비대칭 레이아웃 처리 성공");
    }

    // ============================================================================
    // 테스트 3: 3단 레이아웃 (넓은 중앙)
    // ============================================================================

    @Test
    @DisplayName("통합: 3단 레이아웃 (넓은 중앙) - 다중 컬럼 감지")
    void testThreeColumnLayoutWithWideCenter() {
        logger.info("🔍 테스트 3: 3단 레이아웃 시작");

        // Given: 왼쪽 좁음 / 중앙 넓음 / 오른쪽 좁음
        List<LayoutInfo> layoutElements = new ArrayList<>();
        List<OCRResult> ocrResults = new ArrayList<>();
        List<AIDescriptionResult> aiResults = new ArrayList<>();

        // 왼쪽 좁은 컬럼: 문제 1
        addQuestionNumber(layoutElements, ocrResults, 1, 80, 200, "1");
        addTextElement(layoutElements, ocrResults, 7, 80, 250, "왼쪽 텍스트");

        // 중앙 넓은 컬럼: 문제 2, 3, 4
        addQuestionNumber(layoutElements, ocrResults, 2, 400, 100, "2");
        addQuestionNumber(layoutElements, ocrResults, 3, 400, 300, "3");
        addQuestionNumber(layoutElements, ocrResults, 4, 400, 500, "4");
        addTextElement(layoutElements, ocrResults, 8, 400, 150, "중앙 문제 2 텍스트");
        addTextElement(layoutElements, ocrResults, 9, 400, 350, "중앙 문제 3 텍스트");

        // 오른쪽 좁은 컬럼: 문제 5
        addQuestionNumber(layoutElements, ocrResults, 5, 720, 200, "5");
        addTextElement(layoutElements, ocrResults, 10, 720, 250, "오른쪽 텍스트");

        // When
        UnifiedAnalysisEngine.UnifiedAnalysisResult result = unifiedAnalysisEngine.performUnifiedAnalysis(
            layoutElements, ocrResults, aiResults
        );

        // Then
        assertThat(result.isSuccess()).isTrue();
        StructuredData structuredData = result.getStructuredData();
        assertThat(structuredData.getTotalQuestions()).isEqualTo(5);

        Map<Integer, QuestionData> questionsById = structuredData.getQuestions().stream()
            .collect(Collectors.toMap(QuestionData::getQuestionNumber, q -> q));

        // 3개 컬럼이 모두 올바르게 분리되었는지 확인
        assertQuestionHasElement(questionsById, 1, "왼쪽 텍스트");
        assertQuestionHasElement(questionsById, 2, "중앙 문제 2 텍스트");
        assertQuestionHasElement(questionsById, 3, "중앙 문제 3 텍스트");
        assertQuestionHasElement(questionsById, 5, "오른쪽 텍스트");

        // 컬럼 간 교차 할당이 없어야 함
        assertQuestionDoesNotHaveElement(questionsById, 1, "중앙 문제 2 텍스트");
        assertQuestionDoesNotHaveElement(questionsById, 5, "중앙 문제 3 텍스트");

        logger.info("✅ 테스트 3 완료: 3단 레이아웃 감지 성공");
    }

    // ============================================================================
    // 테스트 4: 2단 걸친 이미지
    // ============================================================================

    @Test
    @DisplayName("통합: 2단 걸친 이미지 - 중심점 기반 할당")
    void testImageSpanningTwoColumns() {
        logger.info("🔍 테스트 4: 2단 걸친 이미지 시작");

        // Given: 2단 레이아웃 + 넓은 이미지
        List<LayoutInfo> layoutElements = new ArrayList<>();
        List<OCRResult> ocrResults = new ArrayList<>();
        List<AIDescriptionResult> aiResults = new ArrayList<>();

        // 문제들
        addQuestionNumber(layoutElements, ocrResults, 1, 100, 100, "1");
        addQuestionNumber(layoutElements, ocrResults, 2, 600, 100, "2");

        // 넓은 이미지 (중심이 왼쪽에 가까움: X=300, Y=200)
        addImageElement(layoutElements, aiResults, 3, 200, 200, 400, 100, "넓은 이미지");

        // 추가 텍스트 요소들
        addTextElement(layoutElements, ocrResults, 4, 100, 150, "문제 1 텍스트");
        addTextElement(layoutElements, ocrResults, 5, 600, 150, "문제 2 텍스트");

        // When
        UnifiedAnalysisEngine.UnifiedAnalysisResult result = unifiedAnalysisEngine.performUnifiedAnalysis(
            layoutElements, ocrResults, aiResults
        );

        // Then
        assertThat(result.isSuccess()).isTrue();
        StructuredData structuredData = result.getStructuredData();

        Map<Integer, QuestionData> questionsById = structuredData.getQuestions().stream()
            .collect(Collectors.toMap(QuestionData::getQuestionNumber, q -> q));

        // 이미지는 중심점(X=300+200=400, Y=200)을 기준으로 왼쪽 문제(1번)에 더 가까움
        // 하지만 Y좌표(200)는 두 문제(Y=100) 사이에 있으므로 더 가까운 쪽에 할당
        assertThat(questionsById).containsKeys(1, 2);

        // 이미지가 어느 한 문제에 할당되었는지 확인 (중심점 기반)
        boolean imageAssignedToQ1 = hasElementWithDescription(questionsById.get(1), "넓은 이미지");
        boolean imageAssignedToQ2 = hasElementWithDescription(questionsById.get(2), "넓은 이미지");

        assertThat(imageAssignedToQ1 || imageAssignedToQ2)
            .as("이미지가 문제 1 또는 2에 할당되어야 함")
            .isTrue();

        // 텍스트 요소들은 각자 올바른 컬럼에 할당
        assertQuestionHasElement(questionsById, 1, "문제 1 텍스트");
        assertQuestionHasElement(questionsById, 2, "문제 2 텍스트");

        logger.info("✅ 테스트 4 완료: 걸친 이미지 할당 성공");
    }

    // ============================================================================
    // 테스트 5: 엣지 케이스 - 컬럼 감지 실패 시 1D Fallback
    // ============================================================================

    @Test
    @DisplayName("통합: 컬럼 감지 실패 시 1D Fallback 동작 확인")
    void testFallbackTo1DWhenColumnDetectionFails() {
        logger.info("🔍 테스트 5: 1D Fallback 시작");

        // Given: 단일 컬럼으로 보이는 레이아웃 (모든 문제가 같은 X좌표)
        List<LayoutInfo> layoutElements = new ArrayList<>();
        List<OCRResult> ocrResults = new ArrayList<>();
        List<AIDescriptionResult> aiResults = new ArrayList<>();

        addQuestionNumber(layoutElements, ocrResults, 1, 100, 100, "1");
        addQuestionNumber(layoutElements, ocrResults, 2, 100, 300, "2");
        addQuestionNumber(layoutElements, ocrResults, 3, 100, 500, "3");

        addTextElement(layoutElements, ocrResults, 4, 100, 200, "문제 1-2 사이 텍스트");
        addTextElement(layoutElements, ocrResults, 5, 100, 400, "문제 2-3 사이 텍스트");

        // When
        UnifiedAnalysisEngine.UnifiedAnalysisResult result = unifiedAnalysisEngine.performUnifiedAnalysis(
            layoutElements, ocrResults, aiResults
        );

        // Then: 단일 컬럼이므로 Y좌표 기반 할당이 작동해야 함
        assertThat(result.isSuccess()).isTrue();
        StructuredData structuredData = result.getStructuredData();
        assertThat(structuredData.getTotalQuestions()).isEqualTo(3);

        Map<Integer, QuestionData> questionsById = structuredData.getQuestions().stream()
            .collect(Collectors.toMap(QuestionData::getQuestionNumber, q -> q));

        // Y좌표 기반으로 가장 가까운 문제에 할당되어야 함
        assertThat(questionsById).containsKeys(1, 2, 3);

        logger.info("✅ 테스트 5 완료: 1D Fallback 정상 작동");
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
     * 텍스트 요소 추가
     */
    private void addTextElement(List<LayoutInfo> layouts, List<OCRResult> ocrs,
                                int id, int x, int y, String text) {
        LayoutInfo layout = createLayoutInfo(id, x, y, 300, ELEMENT_HEIGHT, "text");
        layouts.add(layout);

        OCRResult ocr = new OCRResult();
        ocr.setId(id);
        ocr.setText(text);
        ocr.setConfidence(0.85);
        ocrs.add(ocr);
    }

    /**
     * 이미지 요소 추가 (AI 설명 포함)
     */
    private void addImageElement(List<LayoutInfo> layouts, List<AIDescriptionResult> aiResults,
                                 int id, int x, int y, int width, int height, String description) {
        LayoutInfo layout = createLayoutInfo(id, x, y, width, height, "figure");
        layouts.add(layout);

        AIDescriptionResult ai = new AIDescriptionResult();
        ai.setId(id);
        ai.setDescription(description);
        ai.setConfidence(0.90);
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

    /**
     * 문제가 특정 텍스트를 가진 요소를 포함하는지 검증
     */
    private void assertQuestionHasElement(Map<Integer, QuestionData> questionsById,
                                         int questionNumber, String expectedText) {
        assertThat(questionsById).containsKey(questionNumber);
        QuestionData question = questionsById.get(questionNumber);

        boolean hasElement = question.getElements().values().stream()
            .flatMap(List::stream)
            .anyMatch(element -> {
                if (element.getOcrResult() != null) {
                    return expectedText.equals(element.getOcrResult().getText());
                }
                return false;
            });

        assertThat(hasElement)
            .as("문제 %d번이 텍스트 '%s'를 포함해야 함", questionNumber, expectedText)
            .isTrue();
    }

    /**
     * 문제가 특정 텍스트를 가진 요소를 포함하지 않는지 검증
     */
    private void assertQuestionDoesNotHaveElement(Map<Integer, QuestionData> questionsById,
                                                  int questionNumber, String unexpectedText) {
        assertThat(questionsById).containsKey(questionNumber);
        QuestionData question = questionsById.get(questionNumber);

        boolean hasElement = question.getElements().values().stream()
            .flatMap(List::stream)
            .anyMatch(element -> {
                if (element.getOcrResult() != null) {
                    return unexpectedText.equals(element.getOcrResult().getText());
                }
                return false;
            });

        assertThat(hasElement)
            .as("문제 %d번이 텍스트 '%s'를 포함하면 안됨", questionNumber, unexpectedText)
            .isFalse();
    }

    /**
     * 문제가 특정 AI 설명을 가진 요소를 포함하는지 확인
     */
    private boolean hasElementWithDescription(QuestionData question, String description) {
        if (question == null || question.getElements() == null) {
            return false;
        }

        return question.getElements().values().stream()
            .flatMap(List::stream)
            .anyMatch(element -> {
                if (element.getAiResult() != null) {
                    return description.equals(element.getAiResult().getDescription());
                }
                return false;
            });
    }

    /**
     * CIM 데이터 검증용 헬퍼
     */
    private Map<String, Object> createTestCIMData(List<LayoutInfo> layouts,
                                                  List<OCRResult> ocrs,
                                                  List<AIDescriptionResult> aiResults) {
        Map<String, Object> cimData = new HashMap<>();

        // Document structure
        Map<String, Object> documentStructure = new HashMap<>();
        Map<String, Object> layoutAnalysis = new HashMap<>();

        List<Map<String, Object>> elements = new ArrayList<>();
        for (int i = 0; i < layouts.size(); i++) {
            LayoutInfo layout = layouts.get(i);
            Map<String, Object> element = new HashMap<>();
            element.put("id", layout.getId());
            element.put("class", layout.getClassName());
            element.put("bbox", Arrays.asList(
                layout.getBox()[0], layout.getBox()[1],
                layout.getBox()[2], layout.getBox()[3]
            ));
            element.put("confidence", layout.getConfidence());

            // OCR 텍스트 추가
            ocrs.stream()
                .filter(ocr -> ocr.getId() == layout.getId())
                .findFirst()
                .ifPresent(ocr -> element.put("text", ocr.getText()));

            // AI 설명 추가
            aiResults.stream()
                .filter(ai -> ai.getId() == layout.getId())
                .findFirst()
                .ifPresent(ai -> element.put("ai_description", ai.getDescription()));

            elements.add(element);
        }

        layoutAnalysis.put("elements", elements);
        layoutAnalysis.put("total_elements", elements.size());
        documentStructure.put("layout_analysis", layoutAnalysis);
        cimData.put("document_structure", documentStructure);

        return cimData;
    }
}
