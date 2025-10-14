package com.smarteye.shared.util;

import com.smarteye.application.analysis.UnifiedAnalysisEngine.AnalysisElement;
import com.smarteye.application.analysis.UnifiedAnalysisEngine.QuestionData;
import com.smarteye.application.analysis.UnifiedAnalysisEngine.StructuredData;
import com.smarteye.application.analysis.UnifiedAnalysisEngine.DocumentInfo;
import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.AIDescriptionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * FormattedTextFormatter 단위 테스트
 *
 * <p>테스트 범위:</p>
 * <ul>
 *   <li>단일 컬럼 레이아웃 처리</li>
 *   <li>2단 레이아웃 처리 (컬럼별 정렬 검증)</li>
 *   <li>XSS 방지 검증 (HTML 이스케이프)</li>
 *   <li>빈 데이터 처리</li>
 *   <li>특수 문자 처리</li>
 * </ul>
 *
 * @author SmartEye QA Team
 * @version 1.0 (Day 2)
 * @since 2025-10-03
 */
class FormattedTextFormatterTest {

    /**
     * 각 테스트 전에 초기화 작업을 수행합니다.
     */
    @BeforeEach
    void setUp() {
        // FormattedTextFormatter는 static 메서드를 사용하므로 별도 초기화 불필요
    }

    /**
     * 테스트 1: 단일 컬럼 문서 정상 처리
     *
     * <p>단일 컬럼 레이아웃에서 문제들이 Y좌표 순으로 정렬되는지 검증합니다.</p>
     */
    @Test
    @DisplayName("단일 컬럼 문서 정상 처리")
    void testSingleColumnLayout() {
        // Given: 단일 컬럼 StructuredData
        StructuredData data = createSingleColumnData();

        // When: FormattedText 생성
        String result = FormattedTextFormatter.format(data);

        // Then: 문제 순서가 올바른지 확인
        assertThat(result).isNotNull();
        assertThat(result).contains("1. ");
        assertThat(result).contains("2. ");

        // 문제 1이 문제 2보다 앞에 나와야 함 (Y좌표 기준)
        int index1 = result.indexOf("1. ");
        int index2 = result.indexOf("2. ");
        assertThat(index1).isLessThan(index2);

        // 문제 텍스트 포함 여부 확인
        assertThat(result).contains("문제 1번 내용");
        assertThat(result).contains("문제 2번 내용");
    }

    /**
     * 테스트 2: 2단 레이아웃 컬럼별 정렬
     *
     * <p>2단 레이아웃에서 왼쪽 컬럼 → 오른쪽 컬럼 순으로 정렬되는지 검증합니다.</p>
     */
    @Test
    @DisplayName("2단 레이아웃 컬럼별 정렬")
    void testTwoColumnLayout() {
        // Given: 2단 레이아웃 StructuredData
        StructuredData data = createTwoColumnData();

        // When: FormattedText 생성
        String result = FormattedTextFormatter.format(data);

        // Then: 컬럼별 순서 확인
        assertThat(result).isNotNull();
        assertThat(result).contains("1. ");
        assertThat(result).contains("2. ");
        assertThat(result).contains("3. ");
        assertThat(result).contains("4. ");

        // 왼쪽 컬럼 (1번, 2번) → 오른쪽 컬럼 (3번, 4번) 순서 검증
        int index1 = result.indexOf("1. ");
        int index2 = result.indexOf("2. ");
        int index3 = result.indexOf("3. ");
        int index4 = result.indexOf("4. ");

        // 왼쪽 컬럼 내부 순서: 1번 → 2번
        assertThat(index1).isLessThan(index2);

        // 컬럼 간 순서: 왼쪽 컬럼 전체 → 오른쪽 컬럼 시작
        assertThat(index2).isLessThan(index3);

        // 오른쪽 컬럼 내부 순서: 3번 → 4번
        assertThat(index3).isLessThan(index4);

        // 문제 텍스트 포함 여부 확인
        assertThat(result).contains("1번 문제");
        assertThat(result).contains("2번 문제");
        assertThat(result).contains("3번 문제");
        assertThat(result).contains("4번 문제");
    }

    /**
     * 테스트 3: XSS 방지 검증
     *
     * <p>악의적인 HTML 태그가 이스케이프 처리되는지 검증합니다.</p>
     */
    @Test
    @DisplayName("XSS 방지 테스트")
    void testXSSPrevention() {
        // Given: 악의적인 HTML 태그 포함
        StructuredData data = createDataWithXSSContent();

        // When: FormattedText 생성
        String result = FormattedTextFormatter.format(data);

        // Then: HTML 태그가 이스케이프됨
        assertThat(result).isNotNull();

        // <script> 태그가 이스케이프되어야 함
        assertThat(result).contains("&lt;script&gt;");
        assertThat(result).contains("&lt;/script&gt;");
        assertThat(result).doesNotContain("<script>");

        // <img> 태그가 이스케이프되어야 함
        assertThat(result).contains("&lt;img");
        assertThat(result).contains("&gt;");
        assertThat(result).doesNotContain("<img");

        // 이벤트 핸들러가 이스케이프되어야 함
        // StringEscapeUtils.escapeHtml4()는 '='를 이스케이프하지 않으므로
        // HTML 태그 자체가 이스케이프되었는지만 확인
        assertThat(result).doesNotContain("<img src=");
    }

    /**
     * 테스트 4: 빈 데이터 처리
     *
     * <p>null 또는 빈 StructuredData 입력 시 예외 처리를 검증합니다.</p>
     */
    @Test
    @DisplayName("빈 데이터 처리")
    void testEmptyData() {
        // Given: null StructuredData
        StructuredData nullData = null;

        // When & Then: IllegalArgumentException 발생
        assertThatThrownBy(() -> FormattedTextFormatter.format(nullData))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("StructuredData는 null일 수 없습니다");

        // Given: 빈 questions 리스트
        StructuredData emptyData = new StructuredData();
        emptyData.setQuestions(new ArrayList<>());

        // When: FormattedText 생성
        String result = FormattedTextFormatter.format(emptyData);

        // Then: 기본 메시지 반환
        assertThat(result).isNotNull();
        assertThat(result).contains("분석된 문제가 없습니다");
    }

    /**
     * 테스트 5: 특수 문자 처리
     *
     * <p>특수 문자가 포함된 텍스트가 올바르게 처리되는지 검증합니다.</p>
     */
    @Test
    @DisplayName("특수 문자 처리 테스트")
    void testSpecialCharacters() {
        // Given: 특수 문자 포함 데이터
        StructuredData data = createDataWithSpecialCharacters();

        // When: FormattedText 생성
        String result = FormattedTextFormatter.format(data);

        // Then: 특수 문자가 이스케이프되어야 함
        assertThat(result).isNotNull();

        // HTML 특수 문자 이스케이프 검증
        assertThat(result).contains("&lt;");  // < 이스케이프
        assertThat(result).contains("&gt;");  // > 이스케이프
        assertThat(result).contains("&amp;"); // & 이스케이프
        assertThat(result).contains("&quot;"); // " 이스케이프

        // 원본 특수 문자는 없어야 함
        assertThat(result).doesNotContain("<div>");
        assertThat(result).doesNotContain("&nbsp;");
    }

    /**
     * 테스트 6: 컬럼 감지 정확도 검증
     *
     * <p>X좌표 Gap Detection 알고리즘이 올바르게 작동하는지 검증합니다.</p>
     */
    @Test
    @DisplayName("컬럼 감지 정확도 테스트")
    void testColumnDetection() {
        // Given: 명확한 Gap이 있는 2단 레이아웃
        StructuredData data = createDataWithClearGap();

        // When: FormattedText 생성
        String result = FormattedTextFormatter.format(data);

        // Then: 2개 컬럼으로 감지됨 (결과 검증)
        assertThat(result).isNotNull();
        assertThat(result).contains("컬럼 1");
        assertThat(result).contains("컬럼 2");

        // 컬럼 구분 마커 확인
        assertThat(result).contains("--- 컬럼");
    }

    // ============================================================================
    // Helper Methods (테스트 데이터 생성)
    // ============================================================================

    /**
     * 단일 컬럼 레이아웃 데이터 생성
     */
    private StructuredData createSingleColumnData() {
        StructuredData data = new StructuredData();
        data.setDocumentInfo(createDocumentInfo(2, 2));

        List<QuestionData> questions = new ArrayList<>();

        // 문제 1 (Y=100)
        QuestionData q1 = createQuestion(1, 100, 100, "문제 1번 내용");
        questions.add(q1);

        // 문제 2 (Y=300)
        QuestionData q2 = createQuestion(2, 100, 300, "문제 2번 내용");
        questions.add(q2);

        data.setQuestions(questions);
        return data;
    }

    /**
     * 2단 레이아웃 데이터 생성
     */
    private StructuredData createTwoColumnData() {
        StructuredData data = new StructuredData();
        data.setDocumentInfo(createDocumentInfo(4, 4));

        List<QuestionData> questions = new ArrayList<>();

        // 왼쪽 컬럼 (X=50)
        questions.add(createQuestion(1, 50, 50, "1번 문제"));
        questions.add(createQuestion(2, 50, 200, "2번 문제"));

        // 오른쪽 컬럼 (X=550) - Gap = 500px
        questions.add(createQuestion(3, 550, 50, "3번 문제"));
        questions.add(createQuestion(4, 550, 200, "4번 문제"));

        data.setQuestions(questions);
        return data;
    }

    /**
     * XSS 공격 문자열 포함 데이터 생성
     */
    private StructuredData createDataWithXSSContent() {
        StructuredData data = new StructuredData();
        data.setDocumentInfo(createDocumentInfo(2, 2));

        List<QuestionData> questions = new ArrayList<>();

        // 악의적인 스크립트 태그
        QuestionData q1 = createQuestion(1, 100, 100, "<script>alert('XSS')</script>");
        questions.add(q1);

        // 악의적인 이미지 태그
        QuestionData q2 = createQuestion(2, 100, 200, "<img src=x onerror=alert('XSS')>");
        questions.add(q2);

        data.setQuestions(questions);
        return data;
    }

    /**
     * 특수 문자 포함 데이터 생성
     */
    private StructuredData createDataWithSpecialCharacters() {
        StructuredData data = new StructuredData();
        data.setDocumentInfo(createDocumentInfo(1, 1));

        List<QuestionData> questions = new ArrayList<>();

        // HTML 특수 문자 포함
        String specialText = "<div class=\"test\">내용 & 기호</div>";
        QuestionData q1 = createQuestion(1, 100, 100, specialText);
        questions.add(q1);

        data.setQuestions(questions);
        return data;
    }

    /**
     * 명확한 Gap이 있는 2단 레이아웃 데이터 생성
     */
    private StructuredData createDataWithClearGap() {
        return createTwoColumnData(); // 2단 레이아웃 재사용
    }

    /**
     * QuestionData 생성 헬퍼 메서드
     *
     * @param number 문제 번호
     * @param x X좌표
     * @param y Y좌표
     * @param text 문제 텍스트
     * @return QuestionData 객체
     */
    private QuestionData createQuestion(int number, int x, int y, String text) {
        QuestionData question = new QuestionData();
        question.setQuestionNumber(String.valueOf(number));
        question.setQuestionText(text);

        // LayoutInfo 생성
        LayoutInfo layout = new LayoutInfo();
        layout.setBox(new int[]{x, y, x + 100, y + 50});
        layout.setClassName("question_text");
        layout.setConfidence(0.95);

        // OCRResult 생성
        OCRResult ocr = new OCRResult();
        ocr.setText(text);
        ocr.setConfidence(0.90);

        // AnalysisElement 생성
        AnalysisElement element = new AnalysisElement();
        element.setLayoutInfo(layout);
        element.setOcrResult(ocr);
        element.setCategory("question_text");

        Map<String, List<AnalysisElement>> elements = new HashMap<>();
        elements.put("question_text", Collections.singletonList(element));
        question.setElements(elements);

        return question;
    }

    /**
     * DocumentInfo 생성 헬퍼 메서드
     *
     * @param totalQuestions 총 문제 수
     * @param totalElements 총 요소 수
     * @return DocumentInfo 객체
     */
    private DocumentInfo createDocumentInfo(int totalQuestions, int totalElements) {
        DocumentInfo info = new DocumentInfo();
        info.setTotalQuestions(totalQuestions);
        info.setTotalElements(totalElements);
        info.setProcessingTimestamp(System.currentTimeMillis());
        return info;
    }
}
