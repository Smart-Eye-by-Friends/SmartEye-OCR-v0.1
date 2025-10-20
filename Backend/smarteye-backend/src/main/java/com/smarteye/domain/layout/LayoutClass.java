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
 *   <li><b>LAM 원본 유지:</b> data.yaml의 혼용 형식(띄어쓰기/언더스코어/단일단어) 그대로 사용</li>
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
     * data.yaml: plain text (띄어쓰기)
     */
    PLAIN_TEXT(
        "plain text",
        Category.TEXTUAL,
        false,  // isVisual
        true,   // isOcrTarget
        false,  // isQuestionComponent
        Priority.P1
    ),

    /**
     * 제목 (문서 제목, 단원 제목 등)
     * data.yaml: title (단일)
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
     * data.yaml: unit (단일)
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
     * data.yaml: question type (띄어쓰기)
     */
    QUESTION_TYPE(
        "question type",
        Category.EDUCATIONAL,
        false,
        true,
        true,   // ✅ 문제 경계 요소
        Priority.P0
    ),

    /**
     * 문제 본문 텍스트
     * data.yaml: question text (띄어쓰기)
     */
    QUESTION_TEXT(
        "question text",
        Category.EDUCATIONAL,
        false,
        true,
        true,   // ✅ 문제 구성 요소
        Priority.P0
    ),

    /**
     * 문제 번호 (메인 문제)
     * data.yaml: question number (띄어쓰기)
     */
    QUESTION_NUMBER(
        "question number",
        Category.EDUCATIONAL,
        false,
        true,
        true,   // ✅ 문제 경계 요소
        Priority.P0
    ),

    /**
     * 목록 (순서 있는/없는 목록)
     * data.yaml: list (단일)
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
     * data.yaml: choices (단일)
     */
    CHOICE_TEXT(
        "choices",
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
        false,  // ✅ 비활성 클래스 - OCR 대상 아님
        false,
        Priority.P2
    ),

    /**
     * 그림 캡션 (사용자 요구사항: OCR 대상 클래스)
     * data.yaml: figure_caption (언더스코어)
     */
    FIGURE_CAPTION(
        "figure_caption",
        Category.STRUCTURAL,
        false,
        true,  // ✅ OCR 대상 클래스
        false,
        Priority.P2
    ),

    /**
     * 표 캡션 (사용자 요구사항: OCR 대상 클래스)
     * data.yaml: table caption (띄어쓰기)
     */
    TABLE_CAPTION(
        "table caption",
        Category.TABLE,
        false,
        true,  // ✅ OCR 대상 클래스
        false,
        Priority.P2
    ),

    /**
     * 표 각주 (사용자 요구사항: OCR 대상 클래스)
     * data.yaml: table footnote (띄어쓰기)
     */
    FOOTNOTE(
        "table footnote",
        Category.TABLE,
        false,
        true,  // ✅ OCR 대상 클래스
        false,
        Priority.P2
    ),

    /**
     * 독립 수식 (사용자 요구사항: OCR 대상 클래스)
     * data.yaml: isolate_formula (언더스코어)
     */
    FORMULA(
        "isolate_formula",
        Category.FORMULA,
        false,
        true,  // ✅ OCR 대상 클래스
        false,
        Priority.P2
    ),

    /**
     * 수식 캡션 (사용자 요구사항: OCR 대상 클래스)
     * data.yaml: formula_caption (언더스코어)
     */
    FORMULA_CAPTION(
        "formula_caption",
        Category.FORMULA,
        false,
        true,  // ✅ OCR 대상 클래스
        false,
        Priority.P2
    ),

    /**
     * 페이지 번호 (사용자 요구사항: OCR 대상 클래스)
     * data.yaml: page (단일)
     */
    PAGE_NUMBER(
        "page",
        Category.STRUCTURAL,
        false,
        true,  // ✅ OCR 대상 클래스
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
        false,  // ✅ 비활성 클래스 - OCR 대상 아님
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
        false,  // ✅ 비활성 클래스 - OCR 대상 아님
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
        false,  // ✅ 비활성 클래스 - OCR 대상 아님
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
        false,  // ✅ 비활성 클래스 - OCR 대상 아님
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

    static {
        // 대소문자 무관 매핑을 위해 소문자 키로 저장
        NAME_TO_ENUM = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(
                e -> e.getClassName().toLowerCase(), 
                e -> e
            ));

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

    /**
     * 활성 클래스 여부 확인
     * 
     * <p>@Deprecated 어노테이션이 있는 LayoutClass는 비활성 클래스로 간주합니다.
     * v0.5부터 비활성 클래스 11개(ABANDON, FIGURE_CAPTION 등)는 
     * CIM 로직에서 사용하지 않으므로 전략 매핑에서 제외됩니다.</p>
     *
     * @return true: 활성 클래스, false: 비활성(@Deprecated) 클래스
     * @since v0.5
     */
    public boolean isActive() {
        try {
            return !this.getClass().getField(this.name()).isAnnotationPresent(Deprecated.class);
        } catch (NoSuchFieldException e) {
            // 필드를 찾을 수 없으면 활성으로 간주 (정상적으로는 발생하지 않음)
            return true;
        }
    }

    // ============================================================
    // 정적 유틸리티 메서드
    // ============================================================

    /**
     * 문자열로부터 LayoutClass Enum 값을 반환합니다.
     *
     * <p>LAM 원본 클래스명을 그대로 사용하여 조회합니다.
     * data.yaml의 혼용 형식(띄어쓰기/언더스코어/단일단어)을 그대로 지원합니다.</p>
     * <p>대소문자 무관 매핑을 지원합니다.</p>
     *
     * @param className LAM 모델 클래스명 (예: "plain text", "figure_caption", "title")
     * @return LayoutClass Enum 값 (존재하지 않으면 Optional.empty())
     * @since v0.5 - LAM 원본 유지 방식
     */
    public static Optional<LayoutClass> fromString(String className) {
        if (className == null || className.isBlank()) {
            return Optional.empty();
        }

        // 대소문자 무관 매핑: 소문자로 정규화하여 조회
        String normalized = className.trim().toLowerCase();
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