package com.smarteye.domain.layout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * LayoutClass Enum 단위 테스트 (Phase 4)
 *
 * <p>검증 항목:</p>
 * <ul>
 *   <li>23개 클래스 전체 매핑 정확성</li>
 *   <li>data.yaml 혼용 형식 매핑 (띄어쓰기/언더스코어/단일단어)</li>
 *   <li>OCR 대상 15개 클래스 검증</li>
 *   <li>AI 대상 3개 클래스 검증</li>
 *   <li>Blank 타입 isOcrTarget=false 검증</li>
 *   <li>문자열 ↔ Enum 변환 (fromString)</li>
 * </ul>
 *
 * @since v0.5 - LAM 원본 유지 방식
 */
@DisplayName("LayoutClass Enum 테스트 (Phase 4)")
class LayoutClassTest {

    // ============================================================
    // 1. 기본 매핑 테스트
    // ============================================================

    @Nested
    @DisplayName("1. 기본 매핑 테스트")
    class BasicMappingTests {

        @Test
        @DisplayName("전체 23개 클래스 상수가 정의되어 있어야 함")
        void shouldHave23Classes() {
            // When
            int totalClasses = LayoutClass.values().length;

            // Then
            assertThat(totalClasses).isEqualTo(23);
        }

        @Test
        @DisplayName("OCR 대상 클래스는 15개여야 함")
        void shouldHave15OcrTargetClasses() {
            // When
            Set<LayoutClass> ocrTargets = LayoutClass.getOcrTargetClasses();

            // Then
            assertThat(ocrTargets).hasSize(15);
        }

        @Test
        @DisplayName("AI 대상 클래스는 3개여야 함")
        void shouldHave3VisualClasses() {
            // When
            Set<LayoutClass> visualClasses = LayoutClass.getVisualClasses();

            // Then
            assertThat(visualClasses).hasSize(3);
            assertThat(visualClasses).containsExactlyInAnyOrder(
                LayoutClass.FIGURE,
                LayoutClass.TABLE,
                LayoutClass.FLOWCHART
            );
        }
    }

    // ============================================================
    // 2. data.yaml 혼용 형식 매핑 테스트
    // ============================================================

    @Nested
    @DisplayName("2. data.yaml 혼용 형식 매핑 테스트")
    class DataYamlFormatMappingTests {

        @ParameterizedTest
        @CsvSource({
            "plain text, PLAIN_TEXT",
            "question type, QUESTION_TYPE",
            "question text, QUESTION_TEXT",
            "question number, QUESTION_NUMBER",
            "table caption, TABLE_CAPTION",
            "table footnote, FOOTNOTE"
        })
        @DisplayName("띄어쓰기 방식 클래스명 매핑 테스트 (6개)")
        void shouldMapSpacedClassNames(String className, String expectedEnumName) {
            // When
            Optional<LayoutClass> result = LayoutClass.fromString(className);

            // Then
            assertThat(result)
                .isPresent()
                .hasValueSatisfying(layoutClass -> {
                    assertThat(layoutClass.name()).isEqualTo(expectedEnumName);
                    assertThat(layoutClass.getClassName()).isEqualTo(className);
                });
        }

        @ParameterizedTest
        @CsvSource({
            "figure_caption, FIGURE_CAPTION",
            "isolate_formula, FORMULA",
            "formula_caption, FORMULA_CAPTION",
            "second_question_number, SECOND_QUESTION_NUMBER",
            "underline_blank, UNDERLINE_BLANK",
            "parenthesis_blank, PARENTHESIS_BLANK",
            "box_blank, BOX_BLANK",
            "grid_blank, GRID_BLANK"
        })
        @DisplayName("언더스코어 방식 클래스명 매핑 테스트 (8개)")
        void shouldMapUnderscoreClassNames(String className, String expectedEnumName) {
            // When
            Optional<LayoutClass> result = LayoutClass.fromString(className);

            // Then
            assertThat(result)
                .isPresent()
                .hasValueSatisfying(layoutClass -> {
                    assertThat(layoutClass.name()).isEqualTo(expectedEnumName);
                    assertThat(layoutClass.getClassName()).isEqualTo(className);
                });
        }

        @ParameterizedTest
        @CsvSource({
            "title, TITLE",
            "unit, UNIT",
            "list, LIST",
            "choices, CHOICE_TEXT",
            "page, PAGE_NUMBER",
            "figure, FIGURE",
            "table, TABLE",
            "flowchart, FLOWCHART",
            "abandon, ABANDON"
        })
        @DisplayName("단일 단어 방식 클래스명 매핑 테스트 (9개)")
        void shouldMapSingleWordClassNames(String className, String expectedEnumName) {
            // When
            Optional<LayoutClass> result = LayoutClass.fromString(className);

            // Then
            assertThat(result)
                .isPresent()
                .hasValueSatisfying(layoutClass -> {
                    assertThat(layoutClass.name()).isEqualTo(expectedEnumName);
                    assertThat(layoutClass.getClassName()).isEqualTo(className);
                });
        }

        @Test
        @DisplayName("대소문자 무관 매핑 테스트")
        void shouldBeCaseInsensitive() {
            // When & Then
            assertThat(LayoutClass.fromString("PLAIN TEXT"))
                .isPresent()
                .contains(LayoutClass.PLAIN_TEXT);
            
            assertThat(LayoutClass.fromString("Plain Text"))
                .isPresent()
                .contains(LayoutClass.PLAIN_TEXT);
            
            assertThat(LayoutClass.fromString("plain text"))
                .isPresent()
                .contains(LayoutClass.PLAIN_TEXT);
        }
    }

    // ============================================================
    // 3. OCR 대상 클래스 검증
    // ============================================================

    @Nested
    @DisplayName("3. OCR 대상 클래스 검증 (15개)")
    @SuppressWarnings("deprecation")  // OCR 대상 클래스 일부가 임시 Deprecated 상태
    class OcrTargetClassTests {

        @ParameterizedTest
        @ValueSource(strings = {
            // 띄어쓰기 방식 (6개)
            "plain text",
            "question type",
            "question text",
            "question number",
            "table caption",
            "table footnote",
            // 언더스코어 방식 (4개)
            "figure_caption",
            "isolate_formula",
            "formula_caption",
            "second_question_number",
            // 단일 단어 (5개)
            "title",
            "unit",
            "list",
            "choices",
            "page"
        })
        @DisplayName("OCR 대상 클래스는 isOcrTarget=true여야 함")
        void ocrTargetClassesShouldBeOcrTarget(String className) {
            // When
            Optional<LayoutClass> layoutClass = LayoutClass.fromString(className);

            // Then
            assertThat(layoutClass)
                .isPresent()
                .hasValueSatisfying(lc -> assertThat(lc.isOcrTarget()).isTrue());
        }

        @Test
        @DisplayName("OCR 대상 클래스 15개가 모두 포함되어야 함")
        void shouldContainAll15OcrTargetClasses() {
            // When
            Set<LayoutClass> ocrTargets = LayoutClass.getOcrTargetClasses();

            // Then
            assertThat(ocrTargets).containsExactlyInAnyOrder(
                // 띄어쓰기 방식 (6개)
                LayoutClass.PLAIN_TEXT,
                LayoutClass.QUESTION_TYPE,
                LayoutClass.QUESTION_TEXT,
                LayoutClass.QUESTION_NUMBER,
                LayoutClass.TABLE_CAPTION,
                LayoutClass.FOOTNOTE,
                // 언더스코어 방식 (4개)
                LayoutClass.FIGURE_CAPTION,
                LayoutClass.FORMULA,
                LayoutClass.FORMULA_CAPTION,
                LayoutClass.SECOND_QUESTION_NUMBER,
                // 단일 단어 (5개)
                LayoutClass.TITLE,
                LayoutClass.UNIT,
                LayoutClass.LIST,
                LayoutClass.CHOICE_TEXT,
                LayoutClass.PAGE_NUMBER
            );
        }
    }

    // ============================================================
    // 4. Blank 타입 검증
    // ============================================================

    @Nested
    @DisplayName("4. Blank 타입 검증")
    @SuppressWarnings("deprecation")  // Blank 타입은 Deprecated 상태
    class BlankTypeTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "underline_blank",
            "parenthesis_blank",
            "box_blank",
            "grid_blank"
        })
        @DisplayName("Blank 타입은 isOcrTarget=false여야 함")
        void blankTypesShouldNotBeOcrTarget(String className) {
            // When
            Optional<LayoutClass> layoutClass = LayoutClass.fromString(className);

            // Then
            assertThat(layoutClass)
                .isPresent()
                .hasValueSatisfying(lc -> assertThat(lc.isOcrTarget()).isFalse());
        }

        @Test
        @DisplayName("Blank 타입 4개가 존재해야 함")
        void blankTypesShouldExist() {
            // Then
            assertThat(LayoutClass.UNDERLINE_BLANK).isNotNull();
            assertThat(LayoutClass.PARENTHESIS_BLANK).isNotNull();
            assertThat(LayoutClass.BOX_BLANK).isNotNull();
            assertThat(LayoutClass.GRID_BLANK).isNotNull();
        }
    }

    // ============================================================
    // 5. AI 대상 클래스 검증
    // ============================================================

    @Nested
    @DisplayName("5. AI 대상 클래스 검증 (3개)")
    class AITargetClassTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "figure",
            "table",
            "flowchart"
        })
        @DisplayName("AI 대상 클래스는 isVisual=true여야 함")
        void aiTargetClassesShouldBeVisual(String className) {
            // When
            Optional<LayoutClass> layoutClass = LayoutClass.fromString(className);

            // Then
            assertThat(layoutClass)
                .isPresent()
                .hasValueSatisfying(lc -> assertThat(lc.isVisual()).isTrue());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "figure",
            "table",
            "flowchart"
        })
        @DisplayName("AI 대상 클래스는 isOcrTarget=false여야 함")
        void aiTargetClassesShouldNotBeOcrTarget(String className) {
            // When
            Optional<LayoutClass> layoutClass = LayoutClass.fromString(className);

            // Then
            assertThat(layoutClass)
                .isPresent()
                .hasValueSatisfying(lc -> assertThat(lc.isOcrTarget()).isFalse());
        }
    }

    // ============================================================
    // 6. 문자열 변환 테스트
    // ============================================================

    @Nested
    @DisplayName("6. 문자열 변환 테스트")
    class StringConversionTests {

        @Test
        @DisplayName("fromString: null은 Optional.empty()를 반환해야 함")
        void fromString_ShouldReturnEmpty_WhenNull() {
            // When
            Optional<LayoutClass> result = LayoutClass.fromString(null);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("fromString: 빈 문자열은 Optional.empty()를 반환해야 함")
        void fromString_ShouldReturnEmpty_WhenBlank() {
            // When
            Optional<LayoutClass> result1 = LayoutClass.fromString("");
            Optional<LayoutClass> result2 = LayoutClass.fromString("   ");

            // Then
            assertThat(result1).isEmpty();
            assertThat(result2).isEmpty();
        }

        @Test
        @DisplayName("fromString: 존재하지 않는 클래스명은 Optional.empty()를 반환해야 함")
        void fromString_ShouldReturnEmpty_WhenUnknownClass() {
            // When
            Optional<LayoutClass> result = LayoutClass.fromString("unknown_class");

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("isValid: 유효한 클래스명은 true를 반환해야 함")
        void isValid_ShouldReturnTrue_WhenValidClassName() {
            // When & Then
            assertThat(LayoutClass.isValid("plain text")).isTrue();
            assertThat(LayoutClass.isValid("figure_caption")).isTrue();
            assertThat(LayoutClass.isValid("title")).isTrue();
        }

        @Test
        @DisplayName("isValid: 무효한 클래스명은 false를 반환해야 함")
        void isValid_ShouldReturnFalse_WhenInvalidClassName() {
            // When & Then
            assertThat(LayoutClass.isValid("unknown")).isFalse();
            assertThat(LayoutClass.isValid(null)).isFalse();
            assertThat(LayoutClass.isValid("")).isFalse();
        }
    }

    // ============================================================
    // 7. 카테고리별 필터링 테스트
    // ============================================================

    @Nested
    @DisplayName("7. 카테고리별 필터링 테스트")
    class CategoryFilteringTests {

        @Test
        @DisplayName("교육 콘텐츠 카테고리 필터링")
        void shouldFilterByEducationalCategory() {
            // When
            Set<LayoutClass> educational = LayoutClass.getByCategory(LayoutClass.Category.EDUCATIONAL);

            // Then
            assertThat(educational).isNotEmpty();
            assertThat(educational).allMatch(lc -> lc.getCategory() == LayoutClass.Category.EDUCATIONAL);
        }

        @Test
        @DisplayName("시각적 요소 카테고리 필터링")
        void shouldFilterByVisualCategory() {
            // When
            Set<LayoutClass> visual = LayoutClass.getByCategory(LayoutClass.Category.VISUAL);

            // Then - VISUAL 카테고리는 FIGURE, FLOWCHART만 포함 (TABLE은 Category.TABLE)
            assertThat(visual).hasSize(2);
            assertThat(visual).containsExactlyInAnyOrder(
                LayoutClass.FIGURE,
                LayoutClass.FLOWCHART
            );
        }
    }

    // ============================================================
    // 8. 우선순위별 필터링 테스트
    // ============================================================

    @Nested
    @DisplayName("8. 우선순위별 필터링 테스트")
    class PriorityFilteringTests {

        @Test
        @DisplayName("P0 우선순위 클래스 필터링")
        void shouldFilterByP0Priority() {
            // When
            Set<LayoutClass> p0Classes = LayoutClass.getByPriority(LayoutClass.Priority.P0);

            // Then
            assertThat(p0Classes).isNotEmpty();
            assertThat(p0Classes).allMatch(lc -> lc.getPriority() == LayoutClass.Priority.P0);
        }

        @Test
        @DisplayName("P1 우선순위 클래스 필터링")
        void shouldFilterByP1Priority() {
            // When
            Set<LayoutClass> p1Classes = LayoutClass.getByPriority(LayoutClass.Priority.P1);

            // Then
            assertThat(p1Classes).isNotEmpty();
            assertThat(p1Classes).allMatch(lc -> lc.getPriority() == LayoutClass.Priority.P1);
        }
    }

    // ============================================================
    // 9. 문제 구성 요소 검증
    // ============================================================

    @Nested
    @DisplayName("9. 문제 구성 요소 검증")
    class QuestionComponentTests {

        @Test
        @DisplayName("문제 구성 요소 필터링")
        void shouldFilterQuestionComponents() {
            // When
            Set<LayoutClass> questionComponents = LayoutClass.getQuestionComponents();

            // Then
            assertThat(questionComponents).isNotEmpty();
            assertThat(questionComponents).allMatch(LayoutClass::isQuestionComponent);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "question type",
            "question text",
            "question number",
            "unit",
            "choices"
        })
        @DisplayName("주요 문제 구성 요소는 isQuestionComponent=true여야 함")
        void mainQuestionComponentsShouldBeQuestionComponent(String className) {
            // When
            Optional<LayoutClass> layoutClass = LayoutClass.fromString(className);

            // Then
            assertThat(layoutClass)
                .isPresent()
                .hasValueSatisfying(lc -> assertThat(lc.isQuestionComponent()).isTrue());
        }
    }
}
