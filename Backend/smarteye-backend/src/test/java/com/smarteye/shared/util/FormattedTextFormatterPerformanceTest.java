package com.smarteye.shared.util;

import com.smarteye.application.analysis.UnifiedAnalysisEngine;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Day 3: FormattedTextFormatter 성능 테스트
 *
 * <p>성능 목표: 100문제 < 1초</p>
 *
 * <h3>테스트 시나리오</h3>
 * <ol>
 *   <li>100개의 QuestionData를 포함하는 StructuredData 생성</li>
 *   <li>FormattedTextFormatter.format() 실행 시간 측정</li>
 *   <li>1초 이내 완료 검증</li>
 * </ol>
 *
 * @author SmartEye QA Team
 * @since Day 3
 */
class FormattedTextFormatterPerformanceTest {

    private static final Logger logger = LoggerFactory.getLogger(FormattedTextFormatterPerformanceTest.class);

    /**
     * 성능 테스트 1: 100문제 처리 시간 검증
     *
     * <p>목표: 100문제를 1초 이내에 처리</p>
     */
    @Test
    @DisplayName("성능 테스트: 100문제 < 1초 목표 검증")
    void testPerformanceWith100Questions() {
        // Given: 100개 문제 데이터 생성
        UnifiedAnalysisEngine.StructuredData largeData = createDataWithQuestions(100);

        // When: 실행 시간 측정
        long startTime = System.currentTimeMillis();
        String result = FormattedTextFormatter.format(largeData);
        long endTime = System.currentTimeMillis();

        long processingTime = endTime - startTime;

        // Then: 1초 이내 완료 검증
        assertThat(processingTime)
            .as("100문제 처리가 1초(1000ms) 이내에 완료되어야 함")
            .isLessThan(1000);

        assertThat(result)
            .as("FormattedText가 비어있지 않아야 함")
            .isNotEmpty();

        // 성능 통계 출력
        logger.info("=== 성능 테스트 결과 ===");
        logger.info("문제 수: 100개");
        logger.info("처리 시간: {}ms", processingTime);
        logger.info("문제당 평균 시간: {}ms", processingTime / 100.0);
        logger.info("생성된 텍스트 길이: {}글자", result.length());
        logger.info("======================");

        System.out.println("\n=== 성능 테스트 결과 ===");
        System.out.println("문제 수: 100개");
        System.out.println("처리 시간: " + processingTime + "ms");
        System.out.println("문제당 평균 시간: " + (processingTime / 100.0) + "ms");
        System.out.println("생성된 텍스트 길이: " + result.length() + "글자");
        System.out.println("목표 달성: " + (processingTime < 1000 ? "✅ 성공" : "❌ 실패"));
    }

    /**
     * 성능 테스트 2: 다양한 문제 수에 대한 확장성 검증
     */
    @Test
    @DisplayName("성능 테스트: 다양한 문제 수에 대한 확장성")
    void testScalabilityWithVariousQuestionCounts() {
        int[] questionCounts = {10, 50, 100, 200};
        List<PerformanceResult> results = new ArrayList<>();

        for (int count : questionCounts) {
            // Given
            UnifiedAnalysisEngine.StructuredData data = createDataWithQuestions(count);

            // When
            long startTime = System.currentTimeMillis();
            String result = FormattedTextFormatter.format(data);
            long endTime = System.currentTimeMillis();

            long processingTime = endTime - startTime;

            // Collect results
            results.add(new PerformanceResult(count, processingTime, result.length()));
        }

        // Then: 선형 확장성 검증
        System.out.println("\n=== 확장성 테스트 결과 ===");
        System.out.println("문제 수 | 처리 시간(ms) | 문제당 평균(ms) | 텍스트 길이");
        System.out.println("--------|--------------|----------------|------------");

        for (PerformanceResult result : results) {
            double avgTimePerQuestion = result.processingTime / (double) result.questionCount;
            System.out.printf("%7d | %12d | %14.2f | %11d%n",
                result.questionCount,
                result.processingTime,
                avgTimePerQuestion,
                result.textLength);

            // 성능 임계값 검증
            if (result.questionCount <= 100) {
                assertThat(result.processingTime)
                    .as("%d문제는 1초 이내에 완료되어야 함", result.questionCount)
                    .isLessThan(1000);
            }
        }

        System.out.println("=========================");
    }

    /**
     * 성능 테스트 3: 2단 레이아웃 성능 검증
     */
    @Test
    @DisplayName("성능 테스트: 2단 레이아웃 대용량 처리")
    void testPerformanceWithTwoColumnLayout() {
        // Given: 2단 레이아웃 100문제 (왼쪽 50개, 오른쪽 50개)
        UnifiedAnalysisEngine.StructuredData data = createTwoColumnDataWithQuestions(100);

        // When
        long startTime = System.currentTimeMillis();
        String result = FormattedTextFormatter.format(data);
        long endTime = System.currentTimeMillis();

        long processingTime = endTime - startTime;

        // Then
        assertThat(processingTime)
            .as("2단 레이아웃 100문제도 1초 이내에 완료되어야 함")
            .isLessThan(1000);

        System.out.println("\n=== 2단 레이아웃 성능 테스트 ===");
        System.out.println("레이아웃: 2단 (왼쪽 50개 + 오른쪽 50개)");
        System.out.println("처리 시간: " + processingTime + "ms");
        System.out.println("컬럼 감지 오버헤드 포함");
        System.out.println("=============================");
    }

    /**
     * 성능 테스트 4: 메모리 효율성 검증
     */
    @Test
    @DisplayName("성능 테스트: 메모리 효율성 검증")
    void testMemoryEfficiency() {
        // Given
        Runtime runtime = Runtime.getRuntime();
        runtime.gc(); // 가비지 컬렉션 강제 실행

        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        UnifiedAnalysisEngine.StructuredData data = createDataWithQuestions(100);

        // When
        String result = FormattedTextFormatter.format(data);

        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;

        // Then: 메모리 사용량 로깅
        System.out.println("\n=== 메모리 효율성 테스트 ===");
        System.out.println("처리 전 메모리: " + (memoryBefore / 1024 / 1024) + " MB");
        System.out.println("처리 후 메모리: " + (memoryAfter / 1024 / 1024) + " MB");
        System.out.println("사용된 메모리: " + (memoryUsed / 1024 / 1024) + " MB");
        System.out.println("생성된 텍스트: " + result.length() + " 글자");
        System.out.println("==========================");

        // 메모리 사용량이 합리적인지 검증 (100MB 미만)
        assertThat(memoryUsed / 1024 / 1024)
            .as("메모리 사용량이 100MB 미만이어야 함")
            .isLessThan(100);
    }

    // ============================================================================
    // Helper Methods: 성능 테스트 데이터 생성
    // ============================================================================

    /**
     * 지정된 개수의 문제를 포함하는 StructuredData 생성
     */
    private UnifiedAnalysisEngine.StructuredData createDataWithQuestions(int questionCount) {
        UnifiedAnalysisEngine.StructuredData data = new UnifiedAnalysisEngine.StructuredData();

        // DocumentInfo 설정
        UnifiedAnalysisEngine.DocumentInfo docInfo = new UnifiedAnalysisEngine.DocumentInfo();
        docInfo.setTotalQuestions(questionCount);
        docInfo.setTotalElements(questionCount * 3); // 문제당 평균 3개 요소
        docInfo.setProcessingTimestamp(System.currentTimeMillis());
        data.setDocumentInfo(docInfo);

        // QuestionData 리스트 생성
        List<UnifiedAnalysisEngine.QuestionData> questions = new ArrayList<>();

        for (int i = 1; i <= questionCount; i++) {
            UnifiedAnalysisEngine.QuestionData question = new UnifiedAnalysisEngine.QuestionData();
            question.setQuestionNumber(i);
            question.setQuestionText("문제 " + i + "번 내용");

            // 요소 추가
            Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> elements = new HashMap<>();

            // 문제 텍스트 요소
            UnifiedAnalysisEngine.AnalysisElement textElement = createAnalysisElement(
                "question_text",
                "문제 " + i + "번의 상세 내용입니다.",
                100 + (i * 200),
                50 + ((i - 1) % 10) * 200
            );
            elements.put("question_text", List.of(textElement));

            // 선택지 요소
            UnifiedAnalysisEngine.AnalysisElement choiceElement = createAnalysisElement(
                "choice_1",
                "① 선택지 1",
                120 + (i * 200),
                150 + ((i - 1) % 10) * 200
            );
            elements.put("choice_1", List.of(choiceElement));

            question.setElements(elements);
            questions.add(question);
        }

        data.setQuestions(questions);
        return data;
    }

    /**
     * 2단 레이아웃 데이터 생성
     */
    private UnifiedAnalysisEngine.StructuredData createTwoColumnDataWithQuestions(int totalQuestions) {
        UnifiedAnalysisEngine.StructuredData data = new UnifiedAnalysisEngine.StructuredData();

        UnifiedAnalysisEngine.DocumentInfo docInfo = new UnifiedAnalysisEngine.DocumentInfo();
        docInfo.setTotalQuestions(totalQuestions);
        docInfo.setTotalElements(totalQuestions * 3);
        docInfo.setProcessingTimestamp(System.currentTimeMillis());
        data.setDocumentInfo(docInfo);

        List<UnifiedAnalysisEngine.QuestionData> questions = new ArrayList<>();

        for (int i = 1; i <= totalQuestions; i++) {
            UnifiedAnalysisEngine.QuestionData question = new UnifiedAnalysisEngine.QuestionData();
            question.setQuestionNumber(i);
            question.setQuestionText("문제 " + i + "번");

            Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> elements = new HashMap<>();

            // X좌표를 2단으로 분배 (왼쪽: 50, 오른쪽: 550)
            int xCoord = (i % 2 == 1) ? 50 : 550;
            int yCoord = 50 + ((i - 1) / 2) * 200;

            UnifiedAnalysisEngine.AnalysisElement textElement = createAnalysisElement(
                "question_text",
                "문제 " + i + "번 내용",
                xCoord,
                yCoord
            );
            elements.put("question_text", List.of(textElement));

            question.setElements(elements);
            questions.add(question);
        }

        data.setQuestions(questions);
        return data;
    }

    /**
     * AnalysisElement 생성 헬퍼
     */
    private UnifiedAnalysisEngine.AnalysisElement createAnalysisElement(
            String className, String text, int x, int y) {

        UnifiedAnalysisEngine.AnalysisElement element = new UnifiedAnalysisEngine.AnalysisElement();
        element.setCategory(className);

        // LayoutInfo 생성
        LayoutInfo layout = new LayoutInfo();
        layout.setClassName(className);
        layout.setBox(new int[]{x, y, x + 400, y + 50});
        layout.setConfidence(0.95);
        element.setLayoutInfo(layout);

        // OCRResult 생성
        OCRResult ocr = new OCRResult();
        ocr.setText(text);
        ocr.setConfidence(0.95);
        element.setOcrResult(ocr);

        return element;
    }

    /**
     * 성능 결과 데이터 클래스
     */
    private static class PerformanceResult {
        final int questionCount;
        final long processingTime;
        final int textLength;

        PerformanceResult(int questionCount, long processingTime, int textLength) {
            this.questionCount = questionCount;
            this.processingTime = processingTime;
            this.textLength = textLength;
        }
    }
}
