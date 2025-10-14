package com.smarteye.domain.layout;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 레이아웃 클래스 타입을 정의하는 Enum
 *
 * <p>DocLayout-YOLO 모델에서 감지하는 33개 레이아웃 클래스를 타입 안전하게 관리합니다.
 * 각 클래스는 카테고리, 처리 우선순위, 처리 방식 등의 메타데이터를 포함합니다.</p>
 *
 * <p>설계 원칙:</p>
 * <ul>
 *   <li>불변성: 모든 필드는 final로 선언</li>
 *   <li>타입 안전성: Enum 사용으로 컴파일 타임 검증</li>
 *   <li>확장성: 새 클래스 추가 시 Enum만 수정</li>
 *   <li>성능: 정적 Map 캐싱으로 O(1) 조회</li>
 *   <li>하위 호환성: 문자열 클래스명과 양방향 변환 지원</li>
 * </ul>
 *
 * <p><b>사용 예시:</b></p>
 * <pre>{@code
 * // 문자열 → Enum 변환
 * Optional<LayoutClass> lc = LayoutClass.fromString("question_number");
 *
 * // 속성 조회
 * if (lc.isPresent() && lc.get().isQuestionComponent()) {
 *     // CBHLS 전략 적용
 * }
 *
 * // 카테고리별 필터링
 * Set<LayoutClass> eduClasses = LayoutClass.getByCategory(Category.EDUCATIONAL);
 * }</pre>
 *
 * @see Category
 * @see Priority
 * @since v0.4
 * @version 1.0
 */
public enum LayoutClass {

    // ============================================================
    // 교육 콘텐츠 특화 클래스 (5개) - P0 우선순위
    // ============================================================

    /**
     * 문제 번호 (예: "1.", "[1]", "문제 1")
     * CBHLS 전략의 핵심 앵커 요소
     */
    QUESTION_NUMBER(
        "question_number",
        Category.EDUCATIONAL,
        false,  // isVisual
        true,   // isOcrTarget
        true,   // isQuestionComponent
        Priority.P0
    ),

    /**
     * 소문제 번호 (예: "(1)", "(2)", "①", "②", "(가)", "(나)")
     * LAM v2.0 Fine-tuning 후 추가 예정
     * 메인 문제의 하위 문제 번호를 식별
     * 
     * @since v0.5
     */
    SECOND_QUESTION_NUMBER(
        "second_question_number",
        Category.EDUCATIONAL,
        false,  // isVisual
        true,   // isOcrTarget
        true,   // isQuestionComponent
        Priority.P0
    ),

    /**
     * 문제 텍스트 (예: "다음 중 옳은 것은?")
     */
    QUESTION_TEXT(
        "question_text",
        Category.EDUCATIONAL,
        false,
        true,
        true,
        Priority.P0
    ),

    /**
     * 선택지 텍스트 (예: "① 서울", "② 부산")
     */
    CHOICE_TEXT(
        "choice_text",
        Category.EDUCATIONAL,
        false,
        true,
        true,
        Priority.P0
    ),

    /**
     * 정답 텍스트 (예: "정답: ③")
     */
    ANSWER_TEXT(
        "answer_text",
        Category.EDUCATIONAL,
        false,
        true,
        true,
        Priority.P0
    ),

    /**
     * 해설 텍스트 (예: "해설: 이 문제는...")
     */
    EXPLANATION_TEXT(
        "explanation_text",
        Category.EDUCATIONAL,
        false,
        true,
        true,
        Priority.P0
    ),

    /**
     * 문제 유형 라벨 (예: "유형 번개 [1]", "A형")
     * LAM 모델이 감지하는 question type 클래스에 대응
     */
    QUESTION_TYPE(
        "question_type",
        Category.EDUCATIONAL,
        false,
        true,
        true,
        Priority.P0
    ),

    /**
     * 단원 정보 (예: "단원 1", "B. 수와 연산")
     * LAM 모델이 감지하는 unit 클래스에 대응
     */
    UNIT(
        "unit",
        Category.EDUCATIONAL,
        false,
        true,
        true,
        Priority.P0
    ),

    // ============================================================
    // 구조 요소 (7개) - P1 우선순위
    // ============================================================

    TITLE(
        "title",
        Category.STRUCTURAL,
        false,
        true,
        false,
        Priority.P1
    ),

    HEADING(
        "heading",
        Category.STRUCTURAL,
        false,
        true,
        false,
        Priority.P1
    ),

    CAPTION(
        "caption",
        Category.STRUCTURAL,
        false,
        true,
        false,
        Priority.P1
    ),

    FOOTER(
        "footer",
        Category.STRUCTURAL,
        false,
        true,
        false,
        Priority.P2  // 낮은 우선순위
    ),

    HEADER(
        "header",
        Category.STRUCTURAL,
        false,
        true,
        false,
        Priority.P2  // 낮은 우선순위
    ),

    PAGE_NUMBER(
        "page_number",
        Category.STRUCTURAL,
        false,
        true,
        false,
        Priority.P2  // 낮은 우선순위
    ),

    SECTION_TITLE(
        "section_title",
        Category.STRUCTURAL,
        false,
        true,
        false,
        Priority.P1
    ),

    // ============================================================
    // 텍스트 요소 (4개) - P1 우선순위
    // ============================================================

    TEXT(
        "text",
        Category.TEXTUAL,
        false,
        true,
        false,
        Priority.P1
    ),

    PLAIN_TEXT(
        "plain_text",
        Category.TEXTUAL,
        false,
        true,
        false,
        Priority.P1
    ),

    PARAGRAPH(
        "paragraph",
        Category.TEXTUAL,
        false,
        true,
        false,
        Priority.P1
    ),

    LIST(
        "list",
        Category.TEXTUAL,
        false,
        true,
        false,
        Priority.P1
    ),

    // ============================================================
    // 시각적 요소 (7개) - P1 우선순위, AI 설명 대상
    // ============================================================

    FIGURE(
        "figure",
        Category.VISUAL,
        true,   // isVisual - AI 설명 대상
        false,  // isOcrTarget
        false,
        Priority.P1
    ),

    IMAGE(
        "image",
        Category.VISUAL,
        true,
        false,
        false,
        Priority.P1
    ),

    CHART(
        "chart",
        Category.VISUAL,
        true,
        false,
        false,
        Priority.P1
    ),

    GRAPH(
        "graph",
        Category.VISUAL,
        true,
        false,
        false,
        Priority.P1
    ),

    DIAGRAM(
        "diagram",
        Category.VISUAL,
        true,
        false,
        false,
        Priority.P1
    ),

    ILLUSTRATION(
        "illustration",
        Category.VISUAL,
        true,
        false,
        false,
        Priority.P1
    ),

    PHOTO(
        "photo",
        Category.VISUAL,
        true,
        false,
        false,
        Priority.P1
    ),

    // ============================================================
    // 표 요소 (3개) - P1 우선순위
    // ============================================================

    TABLE(
        "table",
        Category.TABLE,
        false,
        true,   // 표 내부 텍스트 OCR 필요
        false,
        Priority.P1
    ),

    TABLE_CAPTION(
        "table_caption",
        Category.TABLE,
        false,
        true,
        false,
        Priority.P1
    ),

    TABLE_CELL(
        "table_cell",
        Category.TABLE,
        false,
        true,
        false,
        Priority.P1
    ),

    // ============================================================
    // 수식 요소 (2개) - P1 우선순위
    // ============================================================

    FORMULA(
        "formula",
        Category.FORMULA,
        false,
        true,   // 수식 텍스트 OCR 필요
        false,
        Priority.P1
    ),

    EQUATION(
        "equation",
        Category.FORMULA,
        false,
        true,
        false,
        Priority.P1
    ),

    // ============================================================
    // 기타 요소 (5개) - P2 우선순위
    // ============================================================

    CODE_BLOCK(
        "code_block",
        Category.OTHER,
        false,
        true,
        false,
        Priority.P2
    ),

    QUOTE(
        "quote",
        Category.OTHER,
        false,
        true,
        false,
        Priority.P2
    ),

    REFERENCE(
        "reference",
        Category.OTHER,
        false,
        true,
        false,
        Priority.P2
    ),

    FOOTNOTE(
        "footnote",
        Category.OTHER,
        false,
        true,
        false,
        Priority.P2
    ),

    ANNOTATION(
        "annotation",
        Category.OTHER,
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
        /** 교육 콘텐츠 특화 (5개) */
        EDUCATIONAL("Educational Content", "교육 콘텐츠"),

        /** 구조 요소 (7개) */
        STRUCTURAL("Structural Elements", "구조 요소"),

        /** 텍스트 요소 (4개) */
        TEXTUAL("Textual Elements", "텍스트 요소"),

        /** 시각적 요소 (7개) */
        VISUAL("Visual Elements", "시각적 요소"),

        /** 표 요소 (3개) */
        TABLE("Table Elements", "표 요소"),

        /** 수식 요소 (2개) */
        FORMULA("Formula Elements", "수식 요소"),

        /** 기타 요소 (5개) */
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

    /** LAM 서비스에서 사용하는 클래스명 (불변) */
    private final String className;

    /** 카테고리 (불변) */
    private final Category category;

    /** 시각적 요소 여부 (AI 설명 대상) */
    private final boolean isVisual;

    /** OCR 처리 대상 여부 */
    private final boolean isOcrTarget;

    /** 교육 콘텐츠 특화 클래스 여부 (CBHLS 전략 대상) */
    private final boolean isQuestionComponent;

    /** 처리 우선순위 */
    private final Priority priority;

    // ============================================================
    // 정적 캐시 (성능 최적화)
    // ============================================================

    /**
     * 문자열 → Enum 빠른 조회를 위한 정적 Map
     * 초기화 시점: 클래스 로딩 시 (static 블록)
     * 복잡도: O(1) 조회
     * 메모리: 약 2KB (33개 엔트리)
     */
    private static final Map<String, LayoutClass> NAME_TO_ENUM;

    /**
     * 카테고리별 클래스 캐시
     * 초기화 시점: 클래스 로딩 시
     * 복잡도: O(1) 조회
     */
    private static final Map<Category, Set<LayoutClass>> CATEGORY_CACHE;

    /**
     * 우선순위별 클래스 캐시
     */
    private static final Map<Priority, Set<LayoutClass>> PRIORITY_CACHE;

    /**
     * 시각적 요소 캐시 (불변 Set)
     */
    private static final Set<LayoutClass> VISUAL_CLASSES;

    /**
     * OCR 대상 캐시 (불변 Set)
     */
    private static final Set<LayoutClass> OCR_TARGET_CLASSES;

    /**
     * 교육 특화 클래스 캐시 (불변 Set)
     */
    private static final Set<LayoutClass> QUESTION_COMPONENTS;

    static {
        // 문자열 → Enum 매핑 초기화
        NAME_TO_ENUM = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(
                LayoutClass::getClassName,
                e -> e
            ));

        // 카테고리별 캐시 초기화
        CATEGORY_CACHE = Stream.of(values())
            .collect(Collectors.groupingBy(
                LayoutClass::getCategory,
                Collectors.collectingAndThen(
                    Collectors.toSet(),
                    Collections::unmodifiableSet
                )
            ));

        // 우선순위별 캐시 초기화
        PRIORITY_CACHE = Stream.of(values())
            .collect(Collectors.groupingBy(
                LayoutClass::getPriority,
                Collectors.collectingAndThen(
                    Collectors.toSet(),
                    Collections::unmodifiableSet
                )
            ));

        // 시각적 요소 캐시 초기화
        VISUAL_CLASSES = Stream.of(values())
            .filter(LayoutClass::isVisual)
            .collect(Collectors.toUnmodifiableSet());

        // OCR 대상 캐시 초기화
        OCR_TARGET_CLASSES = Stream.of(values())
            .filter(LayoutClass::isOcrTarget)
            .collect(Collectors.toUnmodifiableSet());

        // 교육 특화 클래스 캐시 초기화
        QUESTION_COMPONENTS = Stream.of(values())
            .filter(LayoutClass::isQuestionComponent)
            .collect(Collectors.toUnmodifiableSet());
    }

    // ============================================================
    // 생성자
    // ============================================================

    /**
     * LayoutClass 생성자
     *
     * @param className LAM 서비스 클래스명
     * @param category 카테고리
     * @param isVisual 시각적 요소 여부
     * @param isOcrTarget OCR 처리 대상 여부
     * @param isQuestionComponent 교육 특화 클래스 여부
     * @param priority 처리 우선순위
     * @throws IllegalArgumentException className이 null이거나 빈 문자열인 경우
     */
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

    /**
     * LAM 서비스 클래스명 반환
     *
     * @return 클래스명 (예: "question_number")
     */
    public String getClassName() {
        return className;
    }

    /**
     * 카테고리 반환
     *
     * @return 카테고리
     */
    public Category getCategory() {
        return category;
    }

    /**
     * 시각적 요소 여부 반환
     *
     * @return true: AI 설명 대상, false: 텍스트 처리 대상
     */
    public boolean isVisual() {
        return isVisual;
    }

    /**
     * OCR 처리 대상 여부 반환
     *
     * @return true: OCR 처리 필요, false: OCR 불필요
     */
    public boolean isOcrTarget() {
        return isOcrTarget;
    }

    /**
     * 교육 콘텐츠 특화 클래스 여부 반환
     *
     * @return true: CBHLS 전략 대상, false: 일반 요소
     */
    public boolean isQuestionComponent() {
        return isQuestionComponent;
    }

    /**
     * 처리 우선순위 반환
     *
     * @return 우선순위 (P0, P1, P2)
     */
    public Priority getPriority() {
        return priority;
    }

    // ============================================================
    // 정적 유틸리티 메서드
    // ============================================================

    /**
     * 문자열 클래스명을 LayoutClass Enum으로 변환
     *
     * <p>성능:</p>
     * <ul>
     *   <li>시간 복잡도: O(1) - HashMap 조회</li>
     *   <li>공간 복잡도: O(1) - 추가 메모리 불필요</li>
     * </ul>
     *
     * @param className LAM 서비스 클래스명 (예: "question_number")
     * @return Optional<LayoutClass> - 매칭되는 Enum 또는 빈 Optional
     */
    public static Optional<LayoutClass> fromString(String className) {
        if (className == null || className.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(NAME_TO_ENUM.get(className.trim()));
    }

    /**
     * 유효한 클래스명인지 검증
     *
     * @param className 검증할 클래스명
     * @return true: 유효한 클래스명, false: 유효하지 않음
     */
    public static boolean isValid(String className) {
        return fromString(className).isPresent();
    }

    /**
     * 시각적 요소 목록 반환 (AI 설명 대상)
     *
     * <p>성능: O(1) - 정적 캐시 반환</p>
     *
     * @return 불변 Set<LayoutClass> (7개)
     */
    public static Set<LayoutClass> getVisualClasses() {
        return VISUAL_CLASSES;
    }

    /**
     * OCR 처리 대상 목록 반환
     *
     * <p>성능: O(1) - 정적 캐시 반환</p>
     *
     * @return 불변 Set<LayoutClass> (26개)
     */
    public static Set<LayoutClass> getOcrTargetClasses() {
        return OCR_TARGET_CLASSES;
    }

    /**
     * 교육 콘텐츠 특화 클래스 목록 반환 (CBHLS 전략 대상)
     *
     * <p>성능: O(1) - 정적 캐시 반환</p>
     *
     * @return 불변 Set<LayoutClass> (5개)
     */
    public static Set<LayoutClass> getQuestionComponents() {
        return QUESTION_COMPONENTS;
    }

    /**
     * 특정 카테고리의 클래스 목록 반환
     *
     * <p>성능: O(1) - 정적 캐시 조회</p>
     *
     * @param category 카테고리
     * @return 불변 Set<LayoutClass>
     */
    public static Set<LayoutClass> getByCategory(Category category) {
        return CATEGORY_CACHE.getOrDefault(category, Collections.emptySet());
    }

    /**
     * 특정 우선순위의 클래스 목록 반환
     *
     * <p>성능: O(1) - 정적 캐시 조회</p>
     *
     * @param priority 우선순위
     * @return 불변 Set<LayoutClass>
     */
    public static Set<LayoutClass> getByPriority(Priority priority) {
        return PRIORITY_CACHE.getOrDefault(priority, Collections.emptySet());
    }

    /**
     * 전체 클래스명 목록 반환 (디버깅용)
     *
     * @return 불변 Set<String> (33개)
     */
    public static Set<String> getAllClassNames() {
        return NAME_TO_ENUM.keySet();
    }

    /**
     * 통계 정보 반환 (디버깅/모니터링용)
     *
     * @return Map<String, Integer> - 카테고리별/우선순위별 개수
     */
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

    // ============================================================
    // Object 메서드 오버라이드
    // ============================================================

    /**
     * 문자열 표현 반환 (LAM 서비스 호환)
     *
     * @return className (예: "question_number")
     */
    @Override
    public String toString() {
        return className;
    }
}
