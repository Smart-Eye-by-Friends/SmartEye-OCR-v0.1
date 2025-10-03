package com.smarteye.application.analysis;

import com.smarteye.application.analysis.IntegratedCIMProcessor.IntegratedCIMResult;
import com.smarteye.domain.analysis.entity.AnalysisJob;
import com.smarteye.presentation.dto.AIDescriptionResult;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.shared.util.FormattedTextFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Day 3: FormattedTextFormatter 통합 테스트
 *
 * <p>통합 테스트 목표:</p>
 * <ul>
 *   <li>2단 레이아웃 이미지 입력 → FormattedText 생성 전체 파이프라인 검증</li>
 *   <li>컬럼 순서(왼쪽→오른쪽) 및 문제 번호 순서(위→아래) 검증</li>
 *   <li>IntegratedCIMProcessor → FormattedTextFormatter 위임 패턴 검증</li>
 * </ul>
 *
 * @author SmartEye QA Team
 * @since Day 3
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "smarteye.features.use-2d-spatial-analysis=true"  // 2D 공간 분석 활성화
})
class FormattedTextFormatterIntegrationTest {

    @Autowired
    private IntegratedCIMProcessor integratedCIMProcessor;

    @Autowired
    private UnifiedAnalysisEngine unifiedAnalysisEngine;

    private AnalysisJob testJob;

    @BeforeEach
    void setUp() {
        testJob = new AnalysisJob();
        testJob.setJobId("test-job-integration-" + System.currentTimeMillis());
    }

    /**
     * 통합 테스트 1: 2단 레이아웃 E2E 파이프라인 검증
     *
     * <p>테스트 시나리오:</p>
     * <ol>
     *   <li>2단 레이아웃 테스트 데이터 생성 (왼쪽 컬럼: 1번, 2번 / 오른쪽 컬럼: 3번, 4번)</li>
     *   <li>IntegratedCIMProcessor.processIntegratedCIM() 호출</li>
     *   <li>FormattedText에서 문제 순서 검증: 1 → 2 → 3 → 4</li>
     * </ol>
     */
    @Test
    @DisplayName("통합 테스트: 2단 레이아웃 E2E 파이프라인 (컬럼 순서 검증)")
    void testTwoColumnLayoutE2EPipeline() {
        // Given: 2단 레이아웃 테스트 데이터 생성
        TestDataSet testData = createTwoColumnLayoutData();

        // When: IntegratedCIMProcessor로 통합 처리
        IntegratedCIMResult result = integratedCIMProcessor.processIntegratedCIM(
            testData.layoutElements,
            testData.ocrResults,
            testData.aiResults,
            testJob
        );

        // Then: 성공 검증
        assertThat(result.isSuccess())
            .as("통합 CIM 처리가 성공해야 함")
            .isTrue();

        assertThat(result.getFormattedTextResult())
            .as("FormattedTextResult가 null이 아니어야 함")
            .isNotNull();

        String formattedText = result.getFormattedTextResult().getPrimaryText();
        assertThat(formattedText)
            .as("FormattedText가 비어있지 않아야 함")
            .isNotNull()
            .isNotEmpty();

        // 디버깅: FormattedText 출력
        System.out.println("=== FormattedText 출력 ===");
        System.out.println(formattedText);
        System.out.println("========================");

        // 핵심 검증: 컬럼별 정렬 순서 (왼쪽 → 오른쪽)
        int index1 = formattedText.indexOf("1.");
        int index2 = formattedText.indexOf("2.");
        int index3 = formattedText.indexOf("3.");
        int index4 = formattedText.indexOf("4.");

        assertThat(index1)
            .as("문제 1번이 먼저 나와야 함")
            .isGreaterThanOrEqualTo(0)
            .isLessThan(index2);

        assertThat(index2)
            .as("문제 2번이 1번 다음에 나와야 함 (왼쪽 컬럼 내 순서)")
            .isGreaterThan(index1)
            .isLessThan(index3);

        assertThat(index3)
            .as("문제 3번이 2번 다음에 나와야 함 (오른쪽 컬럼으로 이동)")
            .isGreaterThan(index2)
            .isLessThan(index4);

        assertThat(index4)
            .as("문제 4번이 3번 다음에 나와야 함 (오른쪽 컬럼 내 순서)")
            .isGreaterThan(index3);

        // 로그 출력 (디버깅용)
        System.out.println("=== 통합 테스트 결과 FormattedText ===");
        System.out.println(formattedText);
        System.out.println("=== 문제 순서 검증 완료 ===");
        System.out.println("1번 위치: " + index1);
        System.out.println("2번 위치: " + index2);
        System.out.println("3번 위치: " + index3);
        System.out.println("4번 위치: " + index4);
    }

    /**
     * 통합 테스트 2: 비대칭 2단 레이아웃 처리
     *
     * <p>왼쪽 컬럼이 더 긴 비대칭 레이아웃에서도 올바른 순서를 유지하는지 검증</p>
     */
    @Test
    @DisplayName("통합 테스트: 비대칭 2단 레이아웃 처리")
    void testAsymmetricTwoColumnLayout() {
        // Given: 왼쪽 컬럼이 더 긴 비대칭 레이아웃
        TestDataSet testData = createAsymmetricTwoColumnLayoutData();

        // When
        IntegratedCIMResult result = integratedCIMProcessor.processIntegratedCIM(
            testData.layoutElements,
            testData.ocrResults,
            testData.aiResults,
            testJob
        );

        // Then
        assertThat(result.isSuccess()).isTrue();

        String formattedText = result.getFormattedTextResult().getPrimaryText();

        int index1 = formattedText.indexOf("1.");
        int index2 = formattedText.indexOf("2.");
        int index3 = formattedText.indexOf("3.");

        assertThat(index1).isLessThan(index2);
        assertThat(index2).isLessThan(index3);

        System.out.println("=== 비대칭 레이아웃 테스트 결과 ===");
        System.out.println(formattedText);
    }

    /**
     * 통합 테스트 3: 단일 컬럼 하위 호환성 검증
     *
     * <p>단일 컬럼 문서도 정상적으로 처리되는지 확인</p>
     */
    @Test
    @DisplayName("통합 테스트: 단일 컬럼 하위 호환성")
    void testSingleColumnCompatibility() {
        // Given: 단일 컬럼 레이아웃
        TestDataSet testData = createSingleColumnLayoutData();

        // When
        IntegratedCIMResult result = integratedCIMProcessor.processIntegratedCIM(
            testData.layoutElements,
            testData.ocrResults,
            testData.aiResults,
            testJob
        );

        // Then
        assertThat(result.isSuccess()).isTrue();

        String formattedText = result.getFormattedTextResult().getPrimaryText();

        int index1 = formattedText.indexOf("1.");
        int index2 = formattedText.indexOf("2.");

        assertThat(index1).isLessThan(index2);

        System.out.println("=== 단일 컬럼 테스트 결과 ===");
        System.out.println(formattedText);
    }

    /**
     * 통합 테스트 4: XSS 방지 검증 (E2E)
     *
     * <p>악의적인 HTML 태그가 전체 파이프라인에서 이스케이프되는지 확인</p>
     */
    @Test
    @DisplayName("통합 테스트: XSS 방지 검증 (E2E)")
    void testXSSPreventionE2E() {
        // Given: XSS 공격 문자열 포함
        TestDataSet testData = createDataWithXSSContent();

        // When
        IntegratedCIMResult result = integratedCIMProcessor.processIntegratedCIM(
            testData.layoutElements,
            testData.ocrResults,
            testData.aiResults,
            testJob
        );

        // Then
        assertThat(result.isSuccess()).isTrue();

        String formattedText = result.getFormattedTextResult().getPrimaryText();

        assertThat(formattedText)
            .as("XSS 태그가 이스케이프되어야 함")
            .contains("&lt;script&gt;")
            .doesNotContain("<script>");

        System.out.println("=== XSS 방지 테스트 결과 ===");
        System.out.println(formattedText);
    }

    // ============================================================================
    // Helper Methods: 테스트 데이터 생성
    // ============================================================================

    /**
     * 2단 레이아웃 테스트 데이터 생성
     *
     * <p>레이아웃:</p>
     * <pre>
     * [1번 문제] (X=50, Y=50)   |   [3번 문제] (X=550, Y=50)
     * 첫 번째 내용 (X=50, Y=80)  |   세 번째 내용 (X=550, Y=80)
     *
     * [2번 문제] (X=50, Y=200)   |   [4번 문제] (X=550, Y=200)
     * 두 번째 내용 (X=50, Y=230) |   네 번째 내용 (X=550, Y=230)
     * </pre>
     */
    private TestDataSet createTwoColumnLayoutData() {
        List<LayoutInfo> layoutElements = new ArrayList<>();
        List<OCRResult> ocrResults = new ArrayList<>();

        // 왼쪽 컬럼 (문제 1, 2)
        int id = 0;

        // 문제 1번
        layoutElements.add(createLayoutInfo(id, "question_number", 0.95, new int[]{50, 50, 70, 70}));
        ocrResults.add(createOCRResult(id++, "1.", 0.98));

        layoutElements.add(createLayoutInfo(id, "question_text", 0.92, new int[]{50, 80, 450, 130}));
        ocrResults.add(createOCRResult(id++, "첫 번째 문제 내용입니다.", 0.95));

        // 문제 2번
        layoutElements.add(createLayoutInfo(id, "question_number", 0.94, new int[]{50, 200, 70, 220}));
        ocrResults.add(createOCRResult(id++, "2.", 0.97));

        layoutElements.add(createLayoutInfo(id, "question_text", 0.91, new int[]{50, 230, 450, 280}));
        ocrResults.add(createOCRResult(id++, "두 번째 문제 내용입니다.", 0.94));

        // 오른쪽 컬럼 (문제 3, 4)
        // 문제 3번
        layoutElements.add(createLayoutInfo(id, "question_number", 0.96, new int[]{550, 50, 570, 70}));
        ocrResults.add(createOCRResult(id++, "3.", 0.99));

        layoutElements.add(createLayoutInfo(id, "question_text", 0.93, new int[]{550, 80, 950, 130}));
        ocrResults.add(createOCRResult(id++, "세 번째 문제 내용입니다.", 0.96));

        // 문제 4번
        layoutElements.add(createLayoutInfo(id, "question_number", 0.95, new int[]{550, 200, 570, 220}));
        ocrResults.add(createOCRResult(id++, "4.", 0.98));

        layoutElements.add(createLayoutInfo(id, "question_text", 0.90, new int[]{550, 230, 950, 280}));
        ocrResults.add(createOCRResult(id++, "네 번째 문제 내용입니다.", 0.93));

        return new TestDataSet(layoutElements, ocrResults, new ArrayList<>());
    }

    /**
     * 비대칭 2단 레이아웃 데이터 생성
     */
    private TestDataSet createAsymmetricTwoColumnLayoutData() {
        List<LayoutInfo> layoutElements = new ArrayList<>();
        List<OCRResult> ocrResults = new ArrayList<>();

        int id = 0;

        // 왼쪽 컬럼 (더 김)
        layoutElements.add(createLayoutInfo(id, "question_number", 0.95, new int[]{50, 50, 70, 70}));
        ocrResults.add(createOCRResult(id++, "1.", 0.98));

        layoutElements.add(createLayoutInfo(id, "question_text", 0.92, new int[]{50, 80, 450, 180}));
        ocrResults.add(createOCRResult(id++, "첫 번째 문제 내용 (긴 텍스트)", 0.95));

        layoutElements.add(createLayoutInfo(id, "question_number", 0.94, new int[]{50, 200, 70, 220}));
        ocrResults.add(createOCRResult(id++, "2.", 0.97));

        layoutElements.add(createLayoutInfo(id, "question_text", 0.91, new int[]{50, 230, 450, 400}));
        ocrResults.add(createOCRResult(id++, "두 번째 문제 내용 (매우 긴 텍스트)", 0.94));

        // 오른쪽 컬럼 (더 짧음)
        layoutElements.add(createLayoutInfo(id, "question_number", 0.96, new int[]{550, 50, 570, 70}));
        ocrResults.add(createOCRResult(id++, "3.", 0.99));

        layoutElements.add(createLayoutInfo(id, "question_text", 0.93, new int[]{550, 80, 950, 180}));
        ocrResults.add(createOCRResult(id++, "세 번째 문제 내용", 0.96));

        return new TestDataSet(layoutElements, ocrResults, new ArrayList<>());
    }

    /**
     * 단일 컬럼 레이아웃 데이터 생성
     */
    private TestDataSet createSingleColumnLayoutData() {
        List<LayoutInfo> layoutElements = new ArrayList<>();
        List<OCRResult> ocrResults = new ArrayList<>();

        int id = 0;

        layoutElements.add(createLayoutInfo(id, "question_number", 0.95, new int[]{100, 50, 120, 70}));
        ocrResults.add(createOCRResult(id++, "1.", 0.98));

        layoutElements.add(createLayoutInfo(id, "question_text", 0.92, new int[]{100, 80, 500, 130}));
        ocrResults.add(createOCRResult(id++, "첫 번째 문제", 0.95));

        layoutElements.add(createLayoutInfo(id, "question_number", 0.94, new int[]{100, 200, 120, 220}));
        ocrResults.add(createOCRResult(id++, "2.", 0.97));

        layoutElements.add(createLayoutInfo(id, "question_text", 0.91, new int[]{100, 230, 500, 280}));
        ocrResults.add(createOCRResult(id++, "두 번째 문제", 0.94));

        return new TestDataSet(layoutElements, ocrResults, new ArrayList<>());
    }

    /**
     * XSS 공격 문자열 포함 데이터 생성
     */
    private TestDataSet createDataWithXSSContent() {
        List<LayoutInfo> layoutElements = new ArrayList<>();
        List<OCRResult> ocrResults = new ArrayList<>();

        int id = 0;

        layoutElements.add(createLayoutInfo(id, "question_number", 0.95, new int[]{100, 50, 120, 70}));
        ocrResults.add(createOCRResult(id++, "1.", 0.98));

        layoutElements.add(createLayoutInfo(id, "question_text", 0.92, new int[]{100, 80, 500, 130}));
        ocrResults.add(createOCRResult(id++, "<script>alert('XSS')</script>", 0.95));

        return new TestDataSet(layoutElements, ocrResults, new ArrayList<>());
    }

    private LayoutInfo createLayoutInfo(int id, String className, double confidence, int[] box) {
        LayoutInfo layout = new LayoutInfo();
        layout.setId(id);
        layout.setClassName(className);
        layout.setConfidence(confidence);
        layout.setBox(box);
        layout.setArea((box[2] - box[0]) * (box[3] - box[1]));
        return layout;
    }

    private OCRResult createOCRResult(int id, String text, double confidence) {
        OCRResult ocr = new OCRResult();
        ocr.setId(id);
        ocr.setText(text);
        ocr.setConfidence(confidence);
        return ocr;
    }

    /**
     * 테스트 데이터 세트 컨테이너
     */
    private static class TestDataSet {
        final List<LayoutInfo> layoutElements;
        final List<OCRResult> ocrResults;
        final List<AIDescriptionResult> aiResults;

        TestDataSet(List<LayoutInfo> layoutElements, List<OCRResult> ocrResults, List<AIDescriptionResult> aiResults) {
            this.layoutElements = layoutElements;
            this.ocrResults = ocrResults;
            this.aiResults = aiResults;
        }
    }
}
