package com.smarteye.domain.layout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * LayoutClass Enum 단위 테스트
 *
 * <p>검증 항목:</p>
 * <ul>
 *   <li>33개 클래스 전체 매핑 정확성</li>
 *   <li>문자열 ↔ Enum 변환 (fromString, toString)</li>
 *   <li>카테고리별/우선순위별 필터링</li>
 *   <li>속성 기반 조회 (isVisual, isOcrTarget, isQuestionComponent)</li>
 *   <li>정적 캐시 성능 및 불변성</li>
 *   <li>통계 정보 정확성</li>
 * </ul>
 *
 * @since v0.4
 */
@DisplayName("LayoutClass Enum 테스트")
class LayoutClassTest {

    // ============================================================
    // 1. 기본 매핑 테스트
    // ============================================================

    @Nested
    @DisplayName("1. 기본 매핑 테스트")
    class BasicMappingTests {

        @Test
        @DisplayName("전체 33개 클래스 상수가 정의되어 있어야 함")
        void shouldHave33Classes() {
            // When
            int totalClasses = LayoutClass.values().length;

            // Then
            assertThat(totalClasses).isEqualTo(33);
        }

        @ParameterizedTest
        @MethodSource("com.smarteye.domain.layout.LayoutClassTest#provideEducationalClasses")
        @DisplayName("교육 특화 클래스 5개가 정확히 매핑되어야 함")
        void shouldMapEducationalClasses(String className, LayoutClass expected) {
            // When
            Optional<LayoutClass> actual = LayoutClass.fromString(className);

            // Then
            assertThat(actual)
                .isPresent()
                .contains(expected);
            assertThat(expected.getCategory()).isEqualTo(LayoutClass.Category.EDUCATIONAL);
            assertThat(expected.isQuestionComponent()).isTrue();
            assertThat(expected.getPriority()).isEqualTo(LayoutClass.Priority.P0);
        }

        @ParameterizedTest
        @MethodSource("com.smarteye.domain.layout.LayoutClassTest#provideVisualClasses")
        @DisplayName("시각적 요소 7개가 정확히 매핑되어야 함")
        void shouldMapVisualClasses(String className, LayoutClass expected) {
            // When
            Optional<LayoutClass> actual = LayoutClass.fromString(className);

            // Then
            assertThat(actual)
                .isPresent()
                .contains(expected);
            assertThat(expected.getCategory()).isEqualTo(LayoutClass.Category.VISUAL);
            assertThat(expected.isVisual()).isTrue();
            assertThat(expected.isOcrTarget()).isFalse();
        }
    }

    // ============================================================
    // 2. 문자열 변환 테스트
    // ============================================================

    @Nested
    @DisplayName("2. 문자열 변환 테스트")
    class StringConversionTests {

        @Test
        @DisplayName("fromString: 유효한 클래스명은 Enum으로 변환되어야 함")
        void fromString_ShouldReturnEnum_WhenValidClassName() {
            // When
            Optional<LayoutClass> result = LayoutClass.fromString("question_number");

            // Then
            assertThat(result)
                .isPresent()
                .contains(LayoutClass.QUESTION_NUMBER);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "invalid_class", "QUESTION_NUMBER", "QuestionNumber"})
        @DisplayName("fromString: 유효하지 않은 클래스명은 빈 Optional을 반환해야 함")
        void fromString_ShouldReturnEmpty_WhenInvalidClassName(String invalidClassName) {
            // When
            Optional<LayoutClass> result = LayoutClass.fromString(invalidClassName);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("fromString: null 입력은 빈 Optional을 반환해야 함")
        void fromString_ShouldReturnEmpty_WhenNull() {
            // When
            Optional<LayoutClass> result = LayoutClass.fromString(null);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("fromString: 공백 포함 클래스명은 trim 후 변환되어야 함")
        void fromString_ShouldTrimAndConvert_WhenClassNameHasWhitespace() {
            // When
            Optional<LayoutClass> result = LayoutClass.fromString("  question_number  ");

            // Then
            assertThat(result)
                .isPresent()
                .contains(LayoutClass.QUESTION_NUMBER);
        }

        @Test
        @DisplayName("toString: Enum은 LAM 서비스 호환 문자열로 변환되어야 함")
        void toString_ShouldReturnClassName() {
            // Given
            LayoutClass layoutClass = LayoutClass.QUESTION_NUMBER;

            // When
            String result = layoutClass.toString();

            // Then
            assertThat(result).isEqualTo("question_number");
        }

        @Test
        @DisplayName("isValid: 유효한 클래스명은 true를 반환해야 함")
        void isValid_ShouldReturnTrue_WhenValidClassName() {
            // When
            boolean result = LayoutClass.isValid("question_number");

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isValid: 유효하지 않은 클래스명은 false를 반환해야 함")
        void isValid_ShouldReturnFalse_WhenInvalidClassName() {
            // When
            boolean result = LayoutClass.isValid("invalid_class");

            // Then
            assertThat(result).isFalse();
        }
    }

    // ============================================================
    // 3. 카테고리 필터링 테스트
    // ============================================================

    @Nested
    @DisplayName("3. 카테고리 필터링 테스트")
    class CategoryFilteringTests {

        @Test
        @DisplayName("getByCategory: EDUCATIONAL 카테고리는 5개 클래스를 반환해야 함")
        void getByCategory_ShouldReturn5Classes_WhenEducational() {
            // When
            Set<LayoutClass> result = LayoutClass.getByCategory(LayoutClass.Category.EDUCATIONAL);

            // Then
            assertThat(result)
                .hasSize(5)
                .containsExactlyInAnyOrder(
                    LayoutClass.QUESTION_NUMBER,
                    LayoutClass.QUESTION_TEXT,
                    LayoutClass.CHOICE_TEXT,
                    LayoutClass.ANSWER_TEXT,
                    LayoutClass.EXPLANATION_TEXT
                );
        }

        @Test
        @DisplayName("getByCategory: VISUAL 카테고리는 7개 클래스를 반환해야 함")
        void getByCategory_ShouldReturn7Classes_WhenVisual() {
            // When
            Set<LayoutClass> result = LayoutClass.getByCategory(LayoutClass.Category.VISUAL);

            // Then
            assertThat(result)
                .hasSize(7)
                .containsExactlyInAnyOrder(
                    LayoutClass.FIGURE,
                    LayoutClass.IMAGE,
                    LayoutClass.CHART,
                    LayoutClass.GRAPH,
                    LayoutClass.DIAGRAM,
                    LayoutClass.ILLUSTRATION,
                    LayoutClass.PHOTO
                );
        }

        @Test
        @DisplayName("getByCategory: 반환된 Set은 불변이어야 함")
        void getByCategory_ShouldReturnUnmodifiableSet() {
            // When
            Set<LayoutClass> result = LayoutClass.getByCategory(LayoutClass.Category.EDUCATIONAL);

            // Then
            assertThatThrownBy(() -> result.add(LayoutClass.FIGURE))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ============================================================
    // 4. 우선순위 필터링 테스트
    // ============================================================

    @Nested
    @DisplayName("4. 우선순위 필터링 테스트")
    class PriorityFilteringTests {

        @Test
        @DisplayName("getByPriority: P0 우선순위는 5개 교육 특화 클래스를 반환해야 함")
        void getByPriority_ShouldReturn5Classes_WhenP0() {
            // When
            Set<LayoutClass> result = LayoutClass.getByPriority(LayoutClass.Priority.P0);

            // Then
            assertThat(result)
                .hasSize(5)
                .allMatch(lc -> lc.isQuestionComponent());
        }

        @Test
        @DisplayName("getByPriority: P2 우선순위는 낮은 우선순위 클래스를 반환해야 함")
        void getByPriority_ShouldReturnLowPriorityClasses_WhenP2() {
            // When
            Set<LayoutClass> result = LayoutClass.getByPriority(LayoutClass.Priority.P2);

            // Then
            assertThat(result)
                .isNotEmpty()
                .allMatch(lc -> lc.getPriority() == LayoutClass.Priority.P2);
        }
    }

    // ============================================================
    // 5. 속성 기반 조회 테스트
    // ============================================================

    @Nested
    @DisplayName("5. 속성 기반 조회 테스트")
    class AttributeBasedQueryTests {

        @Test
        @DisplayName("getVisualClasses: 7개 시각적 요소를 반환해야 함")
        void getVisualClasses_ShouldReturn7Classes() {
            // When
            Set<LayoutClass> result = LayoutClass.getVisualClasses();

            // Then
            assertThat(result)
                .hasSize(7)
                .allMatch(LayoutClass::isVisual)
                .allMatch(lc -> !lc.isOcrTarget());
        }

        @Test
        @DisplayName("getOcrTargetClasses: 26개 OCR 대상 클래스를 반환해야 함")
        void getOcrTargetClasses_ShouldReturn26Classes() {
            // When
            Set<LayoutClass> result = LayoutClass.getOcrTargetClasses();

            // Then
            assertThat(result)
                .hasSize(26)
                .allMatch(LayoutClass::isOcrTarget)
                .allMatch(lc -> !lc.isVisual());
        }

        @Test
        @DisplayName("getQuestionComponents: 5개 교육 특화 클래스를 반환해야 함")
        void getQuestionComponents_ShouldReturn5Classes() {
            // When
            Set<LayoutClass> result = LayoutClass.getQuestionComponents();

            // Then
            assertThat(result)
                .hasSize(5)
                .allMatch(LayoutClass::isQuestionComponent)
                .allMatch(lc -> lc.getCategory() == LayoutClass.Category.EDUCATIONAL)
                .allMatch(lc -> lc.getPriority() == LayoutClass.Priority.P0);
        }

        @Test
        @DisplayName("시각적 요소와 OCR 대상은 상호 배타적이어야 함")
        void visualAndOcrTarget_ShouldBeMutuallyExclusive() {
            // When
            Set<LayoutClass> visualClasses = LayoutClass.getVisualClasses();
            Set<LayoutClass> ocrTargetClasses = LayoutClass.getOcrTargetClasses();

            // Then
            assertThat(visualClasses)
                .doesNotContainAnyElementsOf(ocrTargetClasses);
        }
    }

    // ============================================================
    // 6. 통계 정보 테스트
    // ============================================================

    @Nested
    @DisplayName("6. 통계 정보 테스트")
    class StatisticsTests {

        @Test
        @DisplayName("getStatistics: 정확한 통계 정보를 반환해야 함")
        void getStatistics_ShouldReturnCorrectCounts() {
            // When
            Map<String, Integer> stats = LayoutClass.getStatistics();

            // Then
            assertThat(stats)
                .containsEntry("total", 33)
                .containsEntry("educational", 5)
                .containsEntry("structural", 7)
                .containsEntry("textual", 4)
                .containsEntry("visual", 7)
                .containsEntry("table", 3)
                .containsEntry("formula", 2)
                .containsEntry("other", 5)
                .containsEntry("p0", 5)
                .containsEntry("visual_elements", 7)
                .containsEntry("ocr_targets", 26)
                .containsEntry("question_components", 5);
        }

        @Test
        @DisplayName("getAllClassNames: 33개 클래스명을 반환해야 함")
        void getAllClassNames_ShouldReturn33Names() {
            // When
            Set<String> names = LayoutClass.getAllClassNames();

            // Then
            assertThat(names)
                .hasSize(33)
                .contains(
                    "question_number",
                    "question_text",
                    "figure",
                    "image",
                    "table"
                );
        }

        @Test
        @DisplayName("카테고리별 클래스 합계는 총 클래스 수와 일치해야 함")
        void categoryTotals_ShouldMatchTotalClasses() {
            // When
            Map<String, Integer> stats = LayoutClass.getStatistics();
            int categoryTotal = stats.get("educational")
                + stats.get("structural")
                + stats.get("textual")
                + stats.get("visual")
                + stats.get("table")
                + stats.get("formula")
                + stats.get("other");

            // Then
            assertThat(categoryTotal).isEqualTo(33);
        }
    }

    // ============================================================
    // 7. 성능 및 불변성 테스트
    // ============================================================

    @Nested
    @DisplayName("7. 성능 및 불변성 테스트")
    class PerformanceAndImmutabilityTests {

        @Test
        @DisplayName("fromString: 동일한 입력에 대해 항상 같은 인스턴스를 반환해야 함")
        void fromString_ShouldReturnSameInstance_ForSameInput() {
            // When
            Optional<LayoutClass> first = LayoutClass.fromString("question_number");
            Optional<LayoutClass> second = LayoutClass.fromString("question_number");

            // Then
            assertThat(first.get()).isSameAs(second.get());
        }

        @Test
        @DisplayName("getVisualClasses: 반환된 Set은 불변이어야 함")
        void getVisualClasses_ShouldReturnUnmodifiableSet() {
            // When
            Set<LayoutClass> result = LayoutClass.getVisualClasses();

            // Then
            assertThatThrownBy(() -> result.add(LayoutClass.QUESTION_NUMBER))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("getOcrTargetClasses: 반환된 Set은 불변이어야 함")
        void getOcrTargetClasses_ShouldReturnUnmodifiableSet() {
            // When
            Set<LayoutClass> result = LayoutClass.getOcrTargetClasses();

            // Then
            assertThatThrownBy(() -> result.clear())
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("getAllClassNames: 반환된 Set은 불변이어야 함")
        void getAllClassNames_ShouldReturnUnmodifiableSet() {
            // When
            Set<String> result = LayoutClass.getAllClassNames();

            // Then
            assertThatThrownBy(() -> result.add("new_class"))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ============================================================
    // 8. 내부 Enum 테스트
    // ============================================================

    @Nested
    @DisplayName("8. 내부 Enum 테스트")
    class InnerEnumTests {

        @Test
        @DisplayName("Category: 모든 카테고리는 displayName과 koreanName을 가져야 함")
        void category_ShouldHaveDisplayAndKoreanNames() {
            // When & Then
            for (LayoutClass.Category category : LayoutClass.Category.values()) {
                assertThat(category.getDisplayName()).isNotBlank();
                assertThat(category.getKoreanName()).isNotBlank();
            }
        }

        @Test
        @DisplayName("Priority: 우선순위 레벨은 0, 1, 2이어야 함")
        void priority_ShouldHaveLevels0_1_2() {
            // Then
            assertThat(LayoutClass.Priority.P0.getLevel()).isEqualTo(0);
            assertThat(LayoutClass.Priority.P1.getLevel()).isEqualTo(1);
            assertThat(LayoutClass.Priority.P2.getLevel()).isEqualTo(2);
        }
    }

    // ============================================================
    // Test Data Providers
    // ============================================================

    private static Stream<Arguments> provideEducationalClasses() {
        return Stream.of(
            Arguments.of("question_number", LayoutClass.QUESTION_NUMBER),
            Arguments.of("question_text", LayoutClass.QUESTION_TEXT),
            Arguments.of("choice_text", LayoutClass.CHOICE_TEXT),
            Arguments.of("answer_text", LayoutClass.ANSWER_TEXT),
            Arguments.of("explanation_text", LayoutClass.EXPLANATION_TEXT)
        );
    }

    private static Stream<Arguments> provideVisualClasses() {
        return Stream.of(
            Arguments.of("figure", LayoutClass.FIGURE),
            Arguments.of("image", LayoutClass.IMAGE),
            Arguments.of("chart", LayoutClass.CHART),
            Arguments.of("graph", LayoutClass.GRAPH),
            Arguments.of("diagram", LayoutClass.DIAGRAM),
            Arguments.of("illustration", LayoutClass.ILLUSTRATION),
            Arguments.of("photo", LayoutClass.PHOTO)
        );
    }
}
