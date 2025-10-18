package com.smarteye.domain.layout;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 레이아웃 클래스 타입을 정의하는 Enum
 *
 * <p>DocLayout-YOLO 모델에서 감지하는 레이아웃 클래스를 타입 안전하게 관리합니다.
 * v0.5부터 LAM v2 모델의 23개 클래스에 맞춰 활성/비활성 클래스를 구분합니다.</p>
 *
 * <p><b>v0.5 변경 사항:</b></p>
 * <ul>
 *   <li><b>활성 클래스 12개:</b> OCR(9), AI(3) 대상 클래스를 명확히 정의</li>
 *   <li><b>비활성 클래스 11개:</b> @Deprecated 처리하여 하위 호환성 유지</li>
 *   <li><b>별칭(Alias) 지원:</b> fromString() 메서드에서 "choices" -> "choice_text" 등 자동 매핑</li>
 *   <li><b>신규 클래스 추가:</b> FLOWCHART, SECOND_QUESTION_NUMBER 등 LAM v2 클래스 반영</li>
 * </ul>
 *
 * @see Category
 * @see Priority
 * @since v0.4
 * @version 1.1
 */
public enum LayoutClass {

    // ============================================================
    // LAM v2 활성 클래스 (12개)
    // ============================================================

    // 1. OCR 처리 클래스 (9개)
    /**
     * 일반 텍스트 (본문, 설명 등)
     * v2: plain text
     */
    PLAIN_TEXT(
        "plain_text",
        Category.TEXTUAL,
        false,  // isVisual
        true,   // isOcrTarget
        false,  // isQuestionComponent
        Priority.P1
    ),

    /**
     * 제목 (문서 제목, 단원 제목 등)
     * v2: title
     */
    TITLE(
        "title",
        Category.STRUCTURAL,
        false,
        true,
        false,
        Priority.P1
    ),

    /**
     * 단원 정보 (예: "1. 함수", "2. 미분")
     * v2: unit
     */
    UNIT(
        "unit",
        Category.EDUCATIONAL,
        false,
        true,
        true,   // ✅ 문제 경계 요소
        Priority.P0
    ),

    /**
     * 문제 유형 (예: "기본", "심화", "응용")
     * v2: question type
     */
    QUESTION_TYPE(
        "question_type",
        Category.EDUCATIONAL,
        false,
        true,
        true,   // ✅ 문제 경계 요소
        Priority.P0
    ),

    /**
     * 문제 본문 텍스트
     * v2: question text
     */
    QUESTION_TEXT(
        "question_text",
        Category.EDUCATIONAL,
        false,
        true,
        true,   // ✅ 문제 구성 요소
        Priority.P0
    ),

    /**
     * 문제 번호 (메인 문제)
     * v2: question number
     */
    QUESTION_NUMBER(
        "question_number",
        Category.EDUCATIONAL,
        false,
        true,
        true,   // ✅ 문제 경계 요소
        Priority.P0
    ),

    /**
     * 목록 (순서 있는/없는 목록)
     * v2: list
     */
    LIST(
        "list",
        Category.TEXTUAL,
        false,
        true,
        false,
        Priority.P1
    ),

    /**
     * 선택지 (객관식 문제의 보기)
     * v2: choices (별칭 필요)
     */
    CHOICE_TEXT(
        "choice_text",
        Category.EDUCATIONAL,
        false,
        true,
        true,   // ✅ 문제 구성 요소
        Priority.P0
    ),

    /**
     * 하위 문항 번호 (예: (1), (2), ①, ②)
     * v2: second_question_number (🆕 LAM v2 신규)
     */
    SECOND_QUESTION_NUMBER(
        "second_question_number",
        Category.EDUCATIONAL,
        false,
        true,
        true,   // ✅ 하위 문항 표시
        Priority.P0
    ),

    // 2. AI 설명 처리 클래스 (3개)
    /**
     * 그림 (이미지, 차트, 그래프 등 시각 자료 통합)
     * v2: figure (IMAGE, CHART, GRAPH 등 통합)
     */
    FIGURE(
        "figure",
        Category.VISUAL,
        true,   // ✅ isVisual
        false,  // isOcrTarget (AI 설명)
        true,   // 문제 구성 요소
        Priority.P0
    ),

    /**
     * 표 (데이터 테이블)
     * v2: table
     */
    TABLE(
        "table",
        Category.TABLE,
        true,   // ✅ isVisual
        false,  // OCR + AI 하이브리드 (구조는 OCR, 시각화는 AI)
        true,   // 문제 구성 요소
        Priority.P0
    ),

    /**
     * 순서도 (플로우차트, 프로세스 다이어그램)
     * v2: flowchart (🆕 LAM v2 신규)
     */
    FLOWCHART(
        "flowchart",
        Category.VISUAL,
        true,   // ✅ isVisual
        false,  // isOcrTarget (AI 설명)
        true,   // 문제 구성 요소
        Priority.P1
    ),

    // ============================================================
    // LAM v2 비활성 클래스 (11개) - @Deprecated
    // ============================================================

    /**
     * 버려진/무효 영역 (LAM v2에서 새로 추가되었으나 CIM 로직에서 사용하지 않음)
     * v2: abandon (🆕 LAM v2 신규, 하지만 비활성)
     * @deprecated v0.5부터 CIM 로직에서 사용하지 않음. 다음 메이저 버전에서 제거 예정.
     */
    @Deprecated(since = "v0.5", forRemoval = true)
    ABANDON(
        "abandon",
        Category.OTHER,
        false,
        true,
        false,
        Priority.P2
    ),

    /**
     * 그림 캡션 (LAM v2에서 새로 추가되었으나 CIM 로직에서 사용하지 않음)
     * v2: figure_caption (🆕 LAM v2 신규, 하지만 비활성)
     * @deprecated v0.5부터 CIM 로직에서 사용하지 않음. 다음 메이저 버전에서 제거 예정.
     */
    @Deprecated(since = "v0.5", forRemoval = true)
    FIGURE_CAPTION(
        "figure_caption",
        Category.STRUCTURAL,
        false,
        true,
        false,
        Priority.P2
    ),

    /**
     * 표 캡션 (LAM v2에서 유지되었으나 CIM 로직에서 사용하지 않음)
     * v2: table caption
     * @deprecated v0.5부터 CIM 로직에서 사용하지 않음. 다음 메이저 버전에서 제거 예정.
     */
    @Deprecated(since = "v0.5", forRemoval = true)
    TABLE_CAPTION(
        "table_caption",
        Category.TABLE,
        false,
        true,
        false,
        Priority.P2
    ),

    /**
     * 표 각주 (LAM v2에서 유지되었으나 CIM 로직에서 사용하지 않음)
     * v2: table footnote (별칭 필요)
     * @deprecated v0.5부터 CIM 로직에서 사용하지 않음. 다음 메이저 버전에서 제거 예정.
     */
    @Deprecated(since = "v0.5", forRemoval = true)
    FOOTNOTE(
        "footnote",
        Category.TABLE,
        false,
        true,
        false,
        Priority.P2
    ),

    /**
     * 독립 수식 (LAM v2에서 유지되었으나 CIM 로직에서 사용하지 않음)
     * v2: isolate_formula (별칭 필요)
     * @deprecated v0.5부터 CIM 로직에서 사용하지 않음. 다음 메이저 버전에서 제거 예정.
     */
    @Deprecated(since = "v0.5", forRemoval = true)
    FORMULA(
        "formula",
        Category.FORMULA,
        false,
        true,
        false,
        Priority.P2
    ),

    /**
     * 수식 캡션 (LAM v2에서 새로 추가되었으나 CIM 로직에서 사용하지 않음)
     * v2: formula_caption (🆕 LAM v2 신규, 하지만 비활성)
     * @deprecated v0.5부터 CIM 로직에서 사용하지 않음. 다음 메이저 버전에서 제거 예정.
     */
    @Deprecated(since = "v0.5", forRemoval = true)
    FORMULA_CAPTION(
        "formula_caption",
        Category.FORMULA,
        false,
        true,
        false,
        Priority.P2
    ),

    /**
     * 페이지 번호 (LAM v2에서 유지되었으나 CIM 로직에서 사용하지 않음)
     * v2: page (별칭 필요)
     * @deprecated v0.5부터 CIM 로직에서 사용하지 않음. 다음 메이저 버전에서 제거 예정.
     */
    @Deprecated(since = "v0.5", forRemoval = true)
    PAGE_NUMBER(
        "page_number",
        Category.STRUCTURAL,
        false,
        true,
        false,
        Priority.P2
    ),

    /**
     * 밑줄 빈칸 (LAM v2에서 새로 추가되었으나 CIM 로직에서 사용하지 않음)
     * v2: underline_blank (🆕 LAM v2 신규, 하지만 비활성)
     * @deprecated v0.5부터 CIM 로직에서 사용하지 않음. 다음 메이저 버전에서 제거 예정.
     */
    @Deprecated(since = "v0.5", forRemoval = true)
    UNDERLINE_BLANK(
        "underline_blank",
        Category.EDUCATIONAL,
        false,
        true,
        false,
        Priority.P2
    ),

    /**
     * 괄호 빈칸 (LAM v2에서 새로 추가되었으나 CIM 로직에서 사용하지 않음)
     * v2: parenthesis_blank (🆕 LAM v2 신규, 하지만 비활성)
     * @deprecated v0.5부터 CIM 로직에서 사용하지 않음. 다음 메이저 버전에서 제거 예정.
     */
    @Deprecated(since = "v0.5", forRemoval = true)
    PARENTHESIS_BLANK(
        "parenthesis_blank",
        Category.EDUCATIONAL,
        false,
        true,
        false,
        Priority.P2
    ),

    /**
     * 박스 빈칸 (LAM v2에서 새로 추가되었으나 CIM 로직에서 사용하지 않음)
     * v2: box_blank (🆕 LAM v2 신규, 하지만 비활성)
     * @deprecated v0.5부터 CIM 로직에서 사용하지 않음. 다음 메이저 버전에서 제거 예정.
     */
    @Deprecated(since = "v0.5", forRemoval = true)
    BOX_BLANK(
        "box_blank",
        Category.EDUCATIONAL,
        false,
        true,
        false,
        Priority.P2
    ),

    /**
     * 격자 빈칸 (LAM v2에서 새로 추가되었으나 CIM 로직에서 사용하지 않음)
     * v2: grid_blank (🆕 LAM v2 신규, 하지만 비활성)
     * @deprecated v0.5부터 CIM 로직에서 사용하지 않음. 다음 메이저 버전에서 제거 예정.
     */
    @Deprecated(since = "v0.5", forRemoval = true)
    GRID_BLANK(
        "grid_blank",
        Category.EDUCATIONAL,
        false,
        true,
        false,
        Priority.P2
    );


    // ============================================================
    // 내부 열거형 정의
    // ============================================================

    /**
     * 레이아웃 클래스 카테고리
     */
    public enum Category {
        /** 교육 콘텐츠 특화 */
        EDUCATIONAL("Educational Content", "교육 콘텐츠"),

        /** 구조 요소 */
        STRUCTURAL("Structural Elements", "구조 요소"),

        /** 텍스트 요소 */
        TEXTUAL("Textual Elements", "텍스트 요소"),

        /** 시각적 요소 */
        VISUAL("Visual Elements", "시각적 요소"),

        /** 표 요소 */
        TABLE("Table Elements", "표 요소"),

        /** 수식 요소 */
        FORMULA("Formula Elements", "수식 요소"),

        /** 기타 요소 */
        OTHER("Other Elements", "기타 요소");

        private final String displayName;
        private final String koreanName;

        Category(String displayName, String koreanName) {
            this.displayName = displayName;
            this.koreanName = koreanName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getKoreanName() {
            return koreanName;
        }
    }

    /**
     * 처리 우선순위
     */
    public enum Priority {
        /** 최우선 - 교육 특화 클래스 */
        P0(0, "Critical"),

        /** 높음 - 주요 콘텐츠 */
        P1(1, "High"),

        /** 보통 - 보조 콘텐츠 */
        P2(2, "Normal");

        private final int level;
        private final String displayName;

        Priority(int level, String displayName) {
            this.level = level;
            this.displayName = displayName;
        }

        public int getLevel() {
            return level;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // ============================================================
    // 필드
    // ============================================================

    private final String className;
    private final Category category;
    private final boolean isVisual;
    private final boolean isOcrTarget;
    private final boolean isQuestionComponent;
    private final Priority priority;

    // ============================================================
    // 정적 캐시 (성능 최적화)
    // ============================================================

    private static final Map<String, LayoutClass> NAME_TO_ENUM;
    private static final Map<Category, Set<LayoutClass>> CATEGORY_CACHE;
    private static final Map<Priority, Set<LayoutClass>> PRIORITY_CACHE;
    private static final Set<LayoutClass> VISUAL_CLASSES;
    private static final Set<LayoutClass> OCR_TARGET_CLASSES;
    private static final Set<LayoutClass> QUESTION_COMPONENTS;

    /**
     * LAM v2 모델 클래스명 별칭 매핑
     *
     * <p>LAM v2 모델은 일부 클래스명을 변경하였으나, 기존 LayoutClass Enum 값과의
     * 호환성을 위해 별칭 매핑을 제공합니다.</p>
     *
     * <ul>
     *   <li>"choices" → "choice_text" (선택지)</li>
     *   <li>"page" → "page_number" (페이지 번호)</li>
     *   <li>"isolate_formula" → "formula" (독립 수식)</li>
     *   <li>"table_footnote" → "footnote" (표 각주)</li>
     * </ul>
     *
     * @since v0.5
     */
    private static final Map<String, String> CLASS_NAME_ALIASES = Map.of(
        "choices", "choice_text",
        "page", "page_number",
        "isolate_formula", "formula",
        "table_footnote", "footnote"
    );

    static {
        NAME_TO_ENUM = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(LayoutClass::getClassName, e -> e));

        CATEGORY_CACHE = Stream.of(values())
            .collect(Collectors.groupingBy(
                LayoutClass::getCategory,
                Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet)
            ));

        PRIORITY_CACHE = Stream.of(values())
            .collect(Collectors.groupingBy(
                LayoutClass::getPriority,
                Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet)
            ));

        VISUAL_CLASSES = Stream.of(values())
            .filter(LayoutClass::isVisual)
            .collect(Collectors.toUnmodifiableSet());

        OCR_TARGET_CLASSES = Stream.of(values())
            .filter(LayoutClass::isOcrTarget)
            .collect(Collectors.toUnmodifiableSet());

        QUESTION_COMPONENTS = Stream.of(values())
            .filter(LayoutClass::isQuestionComponent)
            .collect(Collectors.toUnmodifiableSet());
    }

    // ============================================================
    // 생성자
    // ============================================================

    LayoutClass(
        String className,
        Category category,
        boolean isVisual,
        boolean isOcrTarget,
        boolean isQuestionComponent,
        Priority priority
    ) {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("className must not be null or blank");
        }

        this.className = className;
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.isVisual = isVisual;
        this.isOcrTarget = isOcrTarget;
        this.isQuestionComponent = isQuestionComponent;
        this.priority = Objects.requireNonNull(priority, "priority must not be null");
    }

    // ============================================================
    // Getter 메서드
    // ============================================================

    public String getClassName() { return className; }
    public Category getCategory() { return category; }
    public boolean isVisual() { return isVisual; }
    public boolean isOcrTarget() { return isOcrTarget; }
    public boolean isQuestionComponent() { return isQuestionComponent; }
    public Priority getPriority() { return priority; }

    // ============================================================
    // 정적 유틸리티 메서드
    // ============================================================

    /**
     * 문자열로부터 LayoutClass Enum 값을 반환합니다.
     *
     * <p>LAM v2 모델 호환성을 위해 다음 처리를 수행합니다:</p>
     * <ol>
     *   <li>공백 → 언더스코어 변환 ("plain text" → "plain_text")</li>
     *   <li>별칭 매핑 적용 ("choices" → "choice_text")</li>
     *   <li>NAME_TO_ENUM 조회</li>
     * </ol>
     *
     * @param className LAM 모델 클래스명 (예: "plain text", "choices")
     * @return LayoutClass Enum 값 (존재하지 않으면 Optional.empty())
     * @since v0.5 - LAM v2 별칭 매핑 지원
     */
    public static Optional<LayoutClass> fromString(String className) {
        if (className == null || className.isBlank()) {
            return Optional.empty();
        }

        // Step 1: 공백 → 언더스코어 정규화
        String normalized = className.trim().replace(" ", "_");

        // Step 2: 🆕 별칭 매핑 적용
        normalized = CLASS_NAME_ALIASES.getOrDefault(normalized, normalized);

        // Step 3: Enum 조회
        return Optional.ofNullable(NAME_TO_ENUM.get(normalized));
    }

    public static boolean isValid(String className) {
        return fromString(className).isPresent();
    }

    public static Set<LayoutClass> getVisualClasses() {
        return VISUAL_CLASSES;
    }

    public static Set<LayoutClass> getOcrTargetClasses() {
        return OCR_TARGET_CLASSES;
    }

    public static Set<LayoutClass> getQuestionComponents() {
        return QUESTION_COMPONENTS;
    }

    public static Set<LayoutClass> getByCategory(Category category) {
        return CATEGORY_CACHE.getOrDefault(category, Collections.emptySet());
    }

    public static Set<LayoutClass> getByPriority(Priority priority) {
        return PRIORITY_CACHE.getOrDefault(priority, Collections.emptySet());
    }

    public static Set<String> getAllClassNames() {
        return NAME_TO_ENUM.keySet();
    }

    public static Map<String, Integer> getStatistics() {
        return Map.ofEntries(
            Map.entry("total", values().length),
            Map.entry("educational", getByCategory(Category.EDUCATIONAL).size()),
            Map.entry("structural", getByCategory(Category.STRUCTURAL).size()),
            Map.entry("textual", getByCategory(Category.TEXTUAL).size()),
            Map.entry("visual", getByCategory(Category.VISUAL).size()),
            Map.entry("table", getByCategory(Category.TABLE).size()),
            Map.entry("formula", getByCategory(Category.FORMULA).size()),
            Map.entry("other", getByCategory(Category.OTHER).size()),
            Map.entry("p0", getByPriority(Priority.P0).size()),
            Map.entry("p1", getByPriority(Priority.P1).size()),
            Map.entry("p2", getByPriority(Priority.P2).size()),
            Map.entry("visual_elements", VISUAL_CLASSES.size()),
            Map.entry("ocr_targets", OCR_TARGET_CLASSES.size()),
            Map.entry("question_components", QUESTION_COMPONENTS.size())
        );
    }

    @Override
    public String toString() {
        return className;
    }
}