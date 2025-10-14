package com.smarteye.application.formatter;

import com.smarteye.application.analysis.UnifiedAnalysisEngine.AnalysisElement;
import com.smarteye.application.analysis.UnifiedAnalysisEngine.DocumentInfo;
import com.smarteye.application.analysis.UnifiedAnalysisEngine.QuestionData;
import com.smarteye.application.analysis.UnifiedAnalysisEngine.StructuredData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FormattedTextGenerator 단위 테스트 클래스.
 *
 * <p>7개의 핵심 테스트 케이스를 포함합니다:</p>
 * <ul>
 *   <li>TC-1: StructuredData 기반 생성 성공</li>
 *   <li>TC-2: null 입력 예외 처리</li>
 *   <li>TC-3: 빈 questions 처리</li>
 *   <li>TC-4: CIM 데이터 기반 생성</li>
 *   <li>TC-5: structured_data 있을 때 Primary Path 전환</li>
 *   <li>TC-6: null/empty CIM 데이터 처리</li>
 *   <li>TC-7: XSS 방지 확인</li>
 * </ul>
 *
 * @author SmartEye Development Team
 * @version 1.0
 * @since v0.5
 */
@DisplayName("FormattedTextGenerator 테스트")
class FormattedTextGeneratorTest {

    private FormattedTextGenerator formattedTextGenerator;

    @BeforeEach
    void setUp() {
        formattedTextGenerator = new FormattedTextGenerator();
    }

    /**
     * TC-1: StructuredData 기반 FormattedText 생성 성공
     */
    @Test
    @DisplayName("StructuredData 기반 FormattedText 생성 성공")
    void testGenerate_WithStructuredData_Success() {
        // Given
        StructuredData structuredData = createTestStructuredData();

        // When
        String result = formattedTextGenerator.generate(structuredData);

        // Then
        assertThat(result).isNotEmpty();
        assertThat(result).contains("=== 분석 결과 ===");
        assertThat(result).contains("1. "); // 문제 번호 포함
        assertThat(result).doesNotContain("<script>"); // XSS 방지 확인
    }

    /**
     * TC-2: StructuredData null 입력 시 IllegalArgumentException 발생
     */
    @Test
    @DisplayName("StructuredData null 입력 시 IllegalArgumentException 발생")
    void testGenerate_WithNullInput_ThrowsException() {
        // When & Then
        assertThatThrownBy(() -> formattedTextGenerator.generate(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("StructuredData는 null일 수 없습니다.");
    }

    /**
     * TC-3: 빈 questions 리스트 처리
     */
    @Test
    @DisplayName("빈 questions 리스트 처리")
    void testGenerate_WithEmptyQuestions_ReturnsDefaultMessage() {
        // Given
        StructuredData structuredData = createEmptyStructuredData();

        // When
        String result = formattedTextGenerator.generate(structuredData);

        // Then
        assertThat(result).contains("분석된 문제가 없습니다.");
    }

    /**
     * TC-4: CIM 데이터 기반 FormattedText 생성 (questions 배열 사용)
     */
    @Test
    @DisplayName("CIM 데이터 기반 FormattedText 생성 (questions 배열 사용)")
    void testGenerateWithFallback_WithCIMData_Success() {
        // Given
        Map<String, Object> cimData = createTestCIMData();

        // When
        String result = formattedTextGenerator.generateWithFallback(cimData);

        // Then
        assertThat(result).isNotEmpty();
        assertThat(result).contains("1. "); // 문제 번호 포함
        assertThat(result).doesNotContain("<script>"); // XSS 방지 확인
    }

    /**
     * TC-5: structured_data 존재 시 Primary Path 사용
     */
    @Test
    @DisplayName("structured_data 존재 시 Primary Path 사용")
    void testGenerateWithFallback_WithStructuredDataInCIM_UsesPrimaryPath() {
        // Given
        StructuredData structuredData = createTestStructuredData();
        Map<String, Object> cimData = Map.of("structured_data", structuredData);

        // When
        String result = formattedTextGenerator.generateWithFallback(cimData);

        // Then
        assertThat(result).isNotEmpty();
        assertThat(result).contains("=== 분석 결과 ===");
        // Primary Path 사용 확인 (FormattedTextFormatter 호출)
    }

    /**
     * TC-6: null/empty CIM 데이터 처리
     */
    @Test
    @DisplayName("null/empty CIM 데이터 처리")
    void testGenerateWithFallback_WithNullOrEmptyData_ReturnsDefaultMessage() {
        // When
        String result1 = formattedTextGenerator.generateWithFallback(null);
        String result2 = formattedTextGenerator.generateWithFallback(Collections.emptyMap());

        // Then
        assertThat(result1).contains("분석 데이터가 없습니다.");
        assertThat(result2).contains("분석 데이터가 없습니다.");
    }

    /**
     * TC-7: XSS 공격 방지 (HTML 이스케이프)
     */
    @Test
    @DisplayName("XSS 공격 방지 (HTML 이스케이프)")
    void testGenerateFromQuestions_XSSPrevention() {
        // Given
        Map<String, Object> cimData = createXSSTestCIMData(); // "<script>alert('XSS')</script>" 포함

        // When
        String result = formattedTextGenerator.generateWithFallback(cimData);

        // Then
        assertThat(result).doesNotContain("<script>");
        assertThat(result).contains("&lt;script&gt;"); // 이스케이프 확인
    }

    // ═══════════════════════════════════════════════════
    // 테스트 헬퍼 메서드
    // ═══════════════════════════════════════════════════

    /**
     * 테스트용 StructuredData 생성
     */
    private StructuredData createTestStructuredData() {
        StructuredData structuredData = new StructuredData();

        DocumentInfo docInfo = new DocumentInfo();
        docInfo.setTotalQuestions(3);
        docInfo.setTotalElements(10);
        docInfo.setProcessingTimestamp(System.currentTimeMillis());
        structuredData.setDocumentInfo(docInfo);

        List<QuestionData> questions = new ArrayList<>();

        // 문제 1
        QuestionData q1 = new QuestionData();
        q1.setQuestionNumber("1");
        q1.setQuestionText("다음 중 옳은 것은?");
        q1.setElements(createTestElements());
        questions.add(q1);

        // 문제 2
        QuestionData q2 = new QuestionData();
        q2.setQuestionNumber("2");
        q2.setQuestionText("다음 중 틀린 것은?");
        q2.setElements(createTestElements());
        questions.add(q2);

        // 문제 3
        QuestionData q3 = new QuestionData();
        q3.setQuestionNumber("3");
        q3.setQuestionText("다음 중 가장 적절한 것은?");
        q3.setElements(createTestElements());
        questions.add(q3);

        structuredData.setQuestions(questions);
        return structuredData;
    }

    /**
     * 빈 StructuredData 생성 (테스트용)
     */
    private StructuredData createEmptyStructuredData() {
        StructuredData structuredData = new StructuredData();

        DocumentInfo docInfo = new DocumentInfo();
        docInfo.setTotalQuestions(0);
        docInfo.setTotalElements(0);
        docInfo.setProcessingTimestamp(System.currentTimeMillis());
        structuredData.setDocumentInfo(docInfo);

        structuredData.setQuestions(Collections.emptyList());
        return structuredData;
    }

    /**
     * 테스트용 요소 맵 생성
     */
    private Map<String, List<AnalysisElement>> createTestElements() {
        // 간단한 테스트용 빈 요소 맵 반환
        // FormattedTextFormatter가 실제로 요소를 처리하므로 여기서는 빈 맵으로 충분
        return new HashMap<>();
    }

    /**
     * 테스트용 CIM 데이터 생성
     */
    private Map<String, Object> createTestCIMData() {
        Map<String, Object> cimData = new HashMap<>();

        List<Map<String, Object>> questions = new ArrayList<>();

        Map<String, Object> q1 = new HashMap<>();
        q1.put("question_number", 1);
        q1.put("question_text", "다음 중 옳은 것은?");
        questions.add(q1);

        Map<String, Object> q2 = new HashMap<>();
        q2.put("question_number", 2);
        q2.put("question_text", "다음 중 틀린 것은?");
        questions.add(q2);

        cimData.put("questions", questions);
        return cimData;
    }

    /**
     * XSS 공격 테스트용 CIM 데이터 생성
     */
    private Map<String, Object> createXSSTestCIMData() {
        Map<String, Object> cimData = new HashMap<>();

        List<Map<String, Object>> questions = new ArrayList<>();

        Map<String, Object> q1 = new HashMap<>();
        q1.put("question_number", 1);
        q1.put("question_text", "<script>alert('XSS')</script>다음 중 옳은 것은?");
        questions.add(q1);

        cimData.put("questions", questions);
        return cimData;
    }
}
