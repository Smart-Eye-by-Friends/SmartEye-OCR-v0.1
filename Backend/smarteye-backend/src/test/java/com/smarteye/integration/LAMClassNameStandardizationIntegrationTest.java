package com.smarteye.integration;

import com.smarteye.domain.layout.LayoutClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LAM 클래스명 표준화 통합 테스트 (Phase 4)
 *
 * <p>검증 항목:</p>
 * <ul>
 *   <li>OCR 대상 15개 + AI 대상 3개 = 18개 활성 클래스</li>
 *   <li>Blank 타입 4개 + ABANDON 1개 = 5개 비활성 클래스</li>
 *   <li>총 23개 클래스 구성 검증</li>
 * </ul>
 *
 * @since v0.5
 */
@DisplayName("LAM 클래스명 표준화 통합 테스트")
class LAMClassNameStandardizationIntegrationTest {

    @Test
    @DisplayName("전체 시스템 클래스 구성 검증: 23개 클래스 = 15개 OCR + 3개 AI + 5개 비활성")
    void shouldHaveCorrectClassComposition() {
        // When
        Set<LayoutClass> allClasses = Set.of(LayoutClass.values());
        Set<LayoutClass> ocrClasses = LayoutClass.getOcrTargetClasses();
        Set<LayoutClass> visualClasses = LayoutClass.getVisualClasses();

        // Then
        assertThat(allClasses).hasSize(23);
        assertThat(ocrClasses).hasSize(15);
        assertThat(visualClasses).hasSize(3);

        // OCR + AI = 18개 (일부 중복 가능하므로 union 사용)
        Set<LayoutClass> activeClasses = ocrClasses.stream()
            .collect(Collectors.toSet());
        activeClasses.addAll(visualClasses);

        // 비활성 클래스 = 전체 - OCR - AI
        Set<LayoutClass> inactiveClasses = allClasses.stream()
            .filter(lc -> !lc.isOcrTarget() && !lc.isVisual())
            .collect(Collectors.toSet());

        assertThat(inactiveClasses).hasSize(5);  // Blank 4개 + ABANDON 1개
    }

    @Test
    @DisplayName("OCR 대상 클래스 분류 검증: 띄어쓰기 6개 + 언더스코어 4개 + 단일단어 5개")
    @SuppressWarnings("deprecation")
    void shouldClassifyOcrTargetsByFormat() {
        // Given
        Set<LayoutClass> ocrClasses = LayoutClass.getOcrTargetClasses();

        // When - 띄어쓰기 방식 (공백 포함)
        Set<LayoutClass> spacedClasses = ocrClasses.stream()
            .filter(lc -> lc.getClassName().contains(" "))
            .collect(Collectors.toSet());

        // When - 언더스코어 방식
        Set<LayoutClass> underscoreClasses = ocrClasses.stream()
            .filter(lc -> lc.getClassName().contains("_") && !lc.getClassName().contains(" "))
            .collect(Collectors.toSet());

        // When - 단일 단어 방식
        Set<LayoutClass> singleWordClasses = ocrClasses.stream()
            .filter(lc -> !lc.getClassName().contains(" ") && !lc.getClassName().contains("_"))
            .collect(Collectors.toSet());

        // Then
        assertThat(spacedClasses).hasSize(6);
        assertThat(underscoreClasses).hasSize(4);
        assertThat(singleWordClasses).hasSize(5);

        // 합계 검증
        assertThat(spacedClasses.size() + underscoreClasses.size() + singleWordClasses.size())
            .isEqualTo(15);
    }

    @Test
    @DisplayName("AI 대상 클래스 검증: figure, table, flowchart")
    void shouldHaveCorrectAITargetClasses() {
        // When
        Set<LayoutClass> visualClasses = LayoutClass.getVisualClasses();

        // Then
        assertThat(visualClasses).hasSize(3);
        
        // AI 대상 클래스는 모두 isVisual=true, isOcrTarget=false
        visualClasses.forEach(lc -> {
            assertThat(lc.isVisual()).isTrue();
            assertThat(lc.isOcrTarget()).isFalse();
        });

        // 클래스명 검증
        Set<String> visualClassNames = visualClasses.stream()
            .map(LayoutClass::getClassName)
            .collect(Collectors.toSet());

        assertThat(visualClassNames).containsExactlyInAnyOrder(
            "figure",
            "table",
            "flowchart"
        );
    }

    @Test
    @DisplayName("비활성 클래스 검증: Blank 4개 + ABANDON 1개는 isOcrTarget=false")
    @SuppressWarnings("deprecation")
    void shouldHaveCorrectInactiveClasses() {
        // When
        Set<LayoutClass> inactiveClasses = Set.of(LayoutClass.values()).stream()
            .filter(lc -> !lc.isOcrTarget() && !lc.isVisual())
            .collect(Collectors.toSet());

        // Then
        assertThat(inactiveClasses).hasSize(5);

        // Blank 타입 4개 검증
        Set<String> blankClassNames = inactiveClasses.stream()
            .map(LayoutClass::getClassName)
            .filter(name -> name.contains("blank"))
            .collect(Collectors.toSet());

        assertThat(blankClassNames).hasSize(4);
        assertThat(blankClassNames).containsExactlyInAnyOrder(
            "underline_blank",
            "parenthesis_blank",
            "box_blank",
            "grid_blank"
        );

        // ABANDON 검증
        assertThat(LayoutClass.ABANDON.isOcrTarget()).isFalse();
        assertThat(LayoutClass.ABANDON.isVisual()).isFalse();
    }

    @Test
    @DisplayName("data.yaml 혼용 형식 일치 검증")
    @SuppressWarnings("deprecation")
    void shouldMatchDataYamlMixedFormat() {
        // When - 띄어쓰기 방식 클래스
        assertThat(LayoutClass.PLAIN_TEXT.getClassName()).isEqualTo("plain text");
        assertThat(LayoutClass.QUESTION_TYPE.getClassName()).isEqualTo("question type");
        assertThat(LayoutClass.TABLE_CAPTION.getClassName()).isEqualTo("table caption");

        // When - 언더스코어 방식 클래스
        assertThat(LayoutClass.FIGURE_CAPTION.getClassName()).isEqualTo("figure_caption");
        assertThat(LayoutClass.FORMULA.getClassName()).isEqualTo("isolate_formula");
        assertThat(LayoutClass.SECOND_QUESTION_NUMBER.getClassName()).isEqualTo("second_question_number");

        // When - 단일 단어 방식 클래스
        assertThat(LayoutClass.TITLE.getClassName()).isEqualTo("title");
        assertThat(LayoutClass.CHOICE_TEXT.getClassName()).isEqualTo("choices");
        assertThat(LayoutClass.FIGURE.getClassName()).isEqualTo("figure");
    }

    @Test
    @DisplayName("문제 구성 요소 검증")
    void shouldHaveCorrectQuestionComponents() {
        // When
        Set<LayoutClass> questionComponents = LayoutClass.getQuestionComponents();

        // Then - 문제 구성 요소는 최소한 다음 클래스들을 포함해야 함
        assertThat(questionComponents).contains(
            LayoutClass.QUESTION_TYPE,
            LayoutClass.QUESTION_TEXT,
            LayoutClass.QUESTION_NUMBER,
            LayoutClass.UNIT,
            LayoutClass.CHOICE_TEXT
        );

        // 모든 문제 구성 요소는 isQuestionComponent=true
        questionComponents.forEach(lc -> 
            assertThat(lc.isQuestionComponent()).isTrue()
        );
    }

    @Test
    @DisplayName("우선순위 검증: P0는 교육 콘텐츠, P1은 텍스트/시각, P2는 저우선순위")
    @SuppressWarnings("deprecation")
    void shouldHaveCorrectPriorityDistribution() {
        // When
        Set<LayoutClass> p0Classes = LayoutClass.getByPriority(LayoutClass.Priority.P0);
        Set<LayoutClass> p1Classes = LayoutClass.getByPriority(LayoutClass.Priority.P1);
        Set<LayoutClass> p2Classes = LayoutClass.getByPriority(LayoutClass.Priority.P2);

        // Then - P0는 주로 교육 콘텐츠
        assertThat(p0Classes).isNotEmpty();
        p0Classes.forEach(lc -> 
            assertThat(lc.getCategory())
                .isIn(LayoutClass.Category.EDUCATIONAL, LayoutClass.Category.VISUAL, LayoutClass.Category.TABLE)
        );

        // P1은 일반 텍스트/시각
        assertThat(p1Classes).isNotEmpty();

        // P2는 저우선순위 클래스 (OCR 대상 6개 + 비활성 5개 = 11개)
        assertThat(p2Classes).hasSize(11);  // figure_caption, table caption, table footnote, isolate_formula, formula_caption, page + Blank 4개 + ABANDON 1개
        
        // P2 중 비활성 클래스만 검증 (5개)
        Set<LayoutClass> inactiveP2Classes = p2Classes.stream()
            .filter(lc -> !lc.isOcrTarget())
            .collect(Collectors.toSet());
        
        assertThat(inactiveP2Classes).hasSize(5);  // Blank 4개 + ABANDON 1개
        inactiveP2Classes.forEach(lc -> {
            assertThat(lc.isOcrTarget()).isFalse();
            assertThat(lc.isVisual()).isFalse();
        });
    }
}
