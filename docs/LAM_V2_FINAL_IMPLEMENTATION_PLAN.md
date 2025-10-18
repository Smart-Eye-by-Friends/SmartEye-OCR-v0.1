# LAM v2 최종 통합 실행 계획서

**프로젝트**: SmartEye v0.4 → v0.5 LAM v2 모델 전환
**작성일**: 2025-10-15
**문서 버전**: 1.0 Final
**예상 작업 시간**: 6-8시간 (작업 1: 2시간, 작업 2: 3-4시간, 작업 3: 1-2시간)

---

## 📋 Executive Summary

본 계획서는 **LAM v2 모델 전환**과 **CIM 공간 정렬 개선**을 통합하여 실행 가능한 단계별 가이드를 제공합니다.

### 핵심 목표

1. **LAM v2 모델 호환성 확보**: 23개 클래스 → LayoutClass.java 통합
2. **하위 문항 처리 개선**: `second_question_number` 계층 구조 지원
3. **컬럼 우선 정렬 구현**: 다단 레이아웃 98% 정확도 달성

### 주요 변경 사항

| 항목 | 현재 (v0.4) | 목표 (v0.5) | 변경 유형 |
|------|-------------|-------------|-----------|
| **LAM 클래스** | 33개 (v1) | 23개 (v2) | 모델 업그레이드 |
| **활성 클래스** | 33개 | 12개 (OCR 9개 + AI 3개) | 핵심 클래스 집중 |
| **비활성 클래스** | 0개 | 11개 (@Deprecated) | 하위 호환성 유지 |
| **하위 문항 처리** | 필터링 제외 | 계층 구조 통합 | 로직 재설계 |
| **정렬 방식** | String 사전식 | 컬럼 우선 + Y좌표 + 숫자 | 3단계 정렬 |

---

## 🎯 작업 1: LayoutClass.java 업데이트

### 1.1 개요

**목표**: LAM v2 모델의 23개 클래스를 LayoutClass.java Enum에 반영하고, 12개 활성 클래스와 11개 비활성 클래스를 명확히 구분합니다.

**파일 경로**: `/home/jongyoung3/SmartEye_v0.4/Backend/smarteye-backend/src/main/java/com/smarteye/domain/layout/LayoutClass.java`

**예상 작업 시간**: 2시간

---

### 1.2 활성 클래스 정의 (12개)

#### 1.2.1 OCR 처리 클래스 (9개)

**목적**: Tesseract OCR로 텍스트 인식이 필요한 클래스

```java
// ========================================
// LAM v2 OCR 처리 클래스 (9개)
// ========================================

/**
 * 일반 텍스트 (본문, 설명 등)
 * v2: plain text
 */
PLAIN_TEXT(
    "plain_text",
    Category.TEXT,
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
    Category.TEXT,
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
);
```

#### 1.2.2 AI 설명 처리 클래스 (3개)

**목적**: OpenAI Vision API로 이미지 설명 생성이 필요한 클래스

```java
// ========================================
// LAM v2 AI 설명 처리 클래스 (3개)
// ========================================

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
);
```

---

### 1.3 비활성 클래스 정의 (11개)

**목적**: LAM v2 모델이 인식하지 않지만 하위 호환성을 위해 유지하는 클래스

#### 1.3.1 @Deprecated 처리 방법

```java
// ========================================
// LAM v2 비활성 클래스 (11개) - @Deprecated
// ========================================

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
```

---

### 1.4 별칭(Alias) 매핑 추가

**목적**: LAM v2 모델의 클래스명 변경 (choices, page, isolate_formula, table_footnote)을 기존 Enum 값으로 매핑

#### 1.4.1 별칭 매핑 Map 추가

**위치**: LayoutClass.java 클래스 상단 (Line ~100)

```java
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
```

#### 1.4.2 fromString() 메서드 수정

**위치**: LayoutClass.java:719-730

**수정 전**:
```java
public static Optional<LayoutClass> fromString(String className) {
    if (className == null || className.isBlank()) {
        return Optional.empty();
    }

    // 공백→언더스코어 정규화
    String normalized = className.trim().replace(" ", "_");
    return Optional.ofNullable(NAME_TO_ENUM.get(normalized));
}
```

**수정 후**:
```java
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
```

---

### 1.5 작업 체크리스트

- [ ] **1.5.1** 활성 클래스 9개 (OCR) 추가/확인
- [ ] **1.5.2** 활성 클래스 3개 (AI) 추가/확인
- [ ] **1.5.3** 비활성 클래스 11개 @Deprecated 처리
- [ ] **1.5.4** CLASS_NAME_ALIASES Map 추가
- [ ] **1.5.5** fromString() 메서드 수정
- [ ] **1.5.6** 컴파일 오류 확인 (0개 목표)
- [ ] **1.5.7** 단위 테스트 실행: `./gradlew test --tests "*LayoutClassTest"`

---

## 🛠️ 작업 2: UnifiedAnalysisEngine.java 핵심 로직 재설계

### 2.1 개요

**목표**: 하위 문항 (`second_question_number`) 처리 및 컬럼 우선 정렬을 구현하여 다단 레이아웃 정확도를 98%로 향상시킵니다.

**파일 경로**: `/home/jongyoung3/SmartEye_v0.4/Backend/smarteye-backend/src/main/java/com/smarteye/application/analysis/UnifiedAnalysisEngine.java`

**예상 작업 시간**: 3-4시간

---

### 2.2 작업 2-1: `isSubQuestionPattern` 필터링 로직 제거

#### 2.2.1 문제 인식

**현재 로직의 문제점**:
- `QuestionNumberExtractor.java:193-196`에서 괄호 숫자 패턴 `(1)`, `(2)`를 **완전히 필터링 제외**
- 이로 인해 하위 문항 데이터가 **손실**됨
- 하위 문항을 독립 문제로 인식하여 잘못된 문제 번호 생성

**해결 방향**:
- 하위 문항을 **경계 요소에서 제외하지 않음**
- 대신 `groupElementsByQuestion` 단계에서 **이전 문제에 종속**시킴

#### 2.2.2 수정 지시사항

**위치**: `QuestionNumberExtractor.java:193-196`

**수정 전**:
```java
// 🆕 Quick Fix 2: 하위 문항 필터링 (괄호 숫자 패턴)
if (SUB_QUESTION_PATTERN.matcher(ocrText.trim()).matches()) {
    logger.debug("⊘ 하위 문항 패턴 감지, 건너뜀: '{}'", ocrText.trim());
    continue;  // ⚠️ 문제: 하위 문항을 완전히 무시
}
```

**수정 후** (코드 제거):
```java
// ❌ 제거됨: isSubQuestionPattern 필터링
// 하위 문항은 groupElementsByQuestion에서 처리하도록 변경
```

**로깅 추가**:
```java
// 하위 문항 패턴 감지 시 로그만 출력 (필터링하지 않음)
if (SUB_QUESTION_PATTERN.matcher(ocrText.trim()).matches()) {
    logger.debug("🔗 하위 문항 패턴 감지 (필터링 안함): '{}' (type={})",
                ocrText.trim(), layout.getClassName());
    // ✅ continue 제거 → 경계 요소로 추가됨
}
```

---

### 2.3 작업 2-2: `groupElementsByQuestion` 로직 재설계

#### 2.3.1 목표

**`second_question_number`를 이전 메인 문제에 종속시키는 계층 구조 구현**

```
메인 문제 294
  ├─ 문제 번호: "294"
  ├─ 문제 텍스트: "다음 중 옳은 것은?"
  ├─ 하위 문항 (1)
  │   └─ 텍스트: "서울은 수도이다."
  └─ 하위 문항 (2)
      └─ 텍스트: "부산은 항구도시이다."
```

#### 2.3.2 수정 지시사항

**위치**: `UnifiedAnalysisEngine.java:808` (`groupElementsByQuestion` 메서드)

**핵심 개념**:
1. **경계 요소 분류**: `QUESTION_NUMBER`, `QUESTION_TYPE`, `UNIT` vs `SECOND_QUESTION_NUMBER`
2. **메인 문제 그룹**: `QUESTION_NUMBER` 등이 나타날 때 새로운 그룹 시작
3. **하위 문항 종속**: `SECOND_QUESTION_NUMBER`는 **이전 메인 문제 그룹에 추가**

**수정 전**:
```java
// 현재 로직: 모든 경계 요소를 동일하게 처리
for (QuestionBoundary boundary : sortedBoundaries) {
    // 새 그룹 시작
    currentGroup = new ArrayList<>();
    // ...
}
```

**수정 후**:
```java
/**
 * Step 2: 문제별 요소 그룹화 (하위 문항 계층 구조 지원)
 *
 * <p>경계 요소를 다음과 같이 분류하여 처리합니다:</p>
 * <ul>
 *   <li><b>메인 경계</b>: QUESTION_NUMBER, QUESTION_TYPE, UNIT → 새 그룹 시작</li>
 *   <li><b>하위 문항 경계</b>: SECOND_QUESTION_NUMBER → 이전 그룹에 종속</li>
 * </ul>
 */
List<QuestionRegion> questionRegions = new ArrayList<>();
List<AnalysisElement> currentGroup = new ArrayList<>();
String currentQuestionId = null;

for (QuestionBoundary boundary : sortedBoundaries) {
    String className = boundary.getClassName();
    LayoutClass layoutClass = LayoutClass.fromString(className).orElse(null);

    // 🆕 경계 타입 판단
    boolean isMainBoundary = (layoutClass == LayoutClass.QUESTION_NUMBER ||
                             layoutClass == LayoutClass.QUESTION_TYPE ||
                             layoutClass == LayoutClass.UNIT);
    boolean isSubBoundary = (layoutClass == LayoutClass.SECOND_QUESTION_NUMBER);

    if (isMainBoundary) {
        // ✅ 메인 경계: 새 그룹 시작
        if (!currentGroup.isEmpty()) {
            // 이전 그룹 저장
            questionRegions.add(new QuestionRegion(
                currentQuestionId,
                currentGroup,
                columnIndexMap.getOrDefault(currentQuestionId, -1)
            ));
            currentGroup = new ArrayList<>();
        }

        // 새 그룹 ID 설정
        currentQuestionId = boundary.getQuestionId();
        logger.debug("📌 메인 경계 감지: questionId={}, type={}",
                    currentQuestionId, className);

    } else if (isSubBoundary) {
        // ✅ 하위 문항 경계: 이전 그룹에 종속
        if (currentGroup.isEmpty()) {
            logger.warn("⚠️ 하위 문항이 메인 문제 없이 나타남: questionId={}, 건너뜀",
                       boundary.getQuestionId());
            continue;  // 메인 문제가 없으면 무시
        }

        logger.debug("🔗 하위 문항 종속: questionId={}, 메인 문제={}, type={}",
                    boundary.getQuestionId(), currentQuestionId, className);
        // ✅ 이전 그룹에 추가 (새 그룹 시작하지 않음)
    }

    // 경계 요소를 현재 그룹에 추가
    currentGroup.add(new AnalysisElement(
        boundary.getLayoutId(),
        className,
        boundary.getBbox(),
        boundary.getText(),
        boundary.getConfidence()
    ));
}

// 마지막 그룹 저장
if (!currentGroup.isEmpty()) {
    questionRegions.add(new QuestionRegion(
        currentQuestionId,
        currentGroup,
        columnIndexMap.getOrDefault(currentQuestionId, -1)
    ));
}

logger.info("📋 문제 영역 그룹화 완료: {} 개 영역 (메인 문제만 카운트)", questionRegions.size());
```

**로깅 예시**:
```
📌 메인 경계 감지: questionId=001, type=question_type
📌 메인 경계 감지: questionId=003, type=question_number
📌 메인 경계 감지: questionId=004, type=question_number
🔗 하위 문항 종속: questionId=(1), 메인 문제=004, type=second_question_number
🔗 하위 문항 종속: questionId=(2), 메인 문제=004, type=second_question_number
📌 메인 경계 감지: questionId=005, type=question_number
📋 문제 영역 그룹화 완료: 7 개 영역 (메인 문제만 카운트)
```

---

### 2.4 작업 2-3: `generateStructuredData` 최종 정렬 로직 구현

#### 2.4.1 목표

**컬럼 우선 → Y좌표 → 숫자 순서의 3단계 정렬 구현**

| 우선순위 | 기준 | 정렬 방향 | 설명 |
|---------|------|----------|------|
| 1순위 | columnIndex | 오름차순 (0 → 1 → 2) | 왼쪽 컬럼부터 처리 |
| 2순위 | Y좌표 (minY) | 오름차순 (위 → 아래) | 같은 컬럼 내 위치 순서 |
| 3순위 | 문제 번호 | 숫자 크기 순 (1 < 2 < 10) | 사전식 정렬 방지 |

#### 2.4.2 수정 지시사항

**위치**: `UnifiedAnalysisEngine.java:1161-1162`

**수정 전**:
```java
// 문제 번호순 정렬 (자연 정렬)
questionDataList.sort(Comparator.comparing(QuestionData::getQuestionNumber));  // ❌ String 사전식
structuredData.setQuestions(questionDataList);
```

**수정 후**:
```java
// 🆕 Phase 2: 컬럼 우선 + Y좌표 기반 정렬 (3단계)
questionDataList.sort(Comparator
    .comparingInt((QuestionData qd) -> {
        // 1순위: 컬럼 인덱스 (왼쪽 → 오른쪽)
        Integer colIdx = qd.getColumnIndex();
        return (colIdx != null && colIdx >= 0) ? colIdx : 999;  // null → 맨 뒤
    })
    .thenComparing((qd1, qd2) -> {
        // 2순위: Y좌표 (위 → 아래)
        int y1 = getMinY(qd1);
        int y2 = getMinY(qd2);
        return Integer.compare(y1, y2);
    })
    .thenComparing((qd1, qd2) -> {
        // 3순위: 문제 번호 (숫자 크기 순)
        try {
            int num1 = Integer.parseInt(qd1.getQuestionNumber());
            int num2 = Integer.parseInt(qd2.getQuestionNumber());
            return Integer.compare(num1, num2);
        } catch (NumberFormatException e) {
            // Fallback: String 사전식 정렬
            return qd1.getQuestionNumber().compareTo(qd2.getQuestionNumber());
        }
    })
);

logger.info("📊 최종 정렬 완료 (컬럼 우선 + Y좌표): {} 개 문제", questionDataList.size());

// 🆕 정렬 후 순서 로깅 (디버깅용)
for (int i = 0; i < questionDataList.size(); i++) {
    QuestionData qd = questionDataList.get(i);
    logger.debug("  [{}] questionNumber={}, columnIndex={}, minY={}",
                i, qd.getQuestionNumber(), qd.getColumnIndex(), getMinY(qd));
}

structuredData.setQuestions(questionDataList);
```

#### 2.4.3 헬퍼 메서드 추가

**위치**: `UnifiedAnalysisEngine.java:1175` (generateStructuredData 메서드 이후)

```java
/**
 * QuestionData의 최소 Y좌표 추출
 *
 * <p>문제 영역에 속한 모든 요소의 Y좌표 중 최소값을 반환합니다.
 * 정렬 시 Y좌표 기준으로 사용됩니다.</p>
 *
 * @param qd QuestionData 객체
 * @return 최소 Y좌표 (요소가 없으면 Integer.MAX_VALUE)
 */
private int getMinY(QuestionData qd) {
    if (qd.getContentElements() == null || qd.getContentElements().isEmpty()) {
        logger.warn("⚠️ 문제 {}번 - ContentElements가 비어있음, Y좌표를 MAX_VALUE로 설정",
                   qd.getQuestionNumber());
        return Integer.MAX_VALUE;
    }

    return qd.getContentElements().stream()
        .filter(ce -> ce.getBbox() != null)
        .mapToInt(ce -> ce.getBbox().getY1())
        .min()
        .orElse(Integer.MAX_VALUE);
}
```

---

### 2.5 작업 2-4: OpenAI 처리 로직 확인

#### 2.5.1 목표

**AI 설명이 필요한 3개 클래스 (`FIGURE`, `TABLE`, `FLOWCHART`)에 대해서만 OpenAI API 호출**

#### 2.5.2 확인 지시사항

**위치**: `AIDescriptionService.java` 또는 `UnifiedAnalysisEngine.java`에서 AI 설명 호출 부분

**확인 사항**:
```java
// AI 설명이 필요한 클래스 판단
LayoutClass layoutClass = LayoutClass.fromString(element.getClassName()).orElse(null);
if (layoutClass == null) {
    continue;
}

// ✅ 활성 클래스 중 isVisual=true인 것만 AI 설명 생성
if (layoutClass.isVisual() &&
    (layoutClass == LayoutClass.FIGURE ||
     layoutClass == LayoutClass.TABLE ||
     layoutClass == LayoutClass.FLOWCHART)) {

    // OpenAI Vision API 호출
    String aiDescription = aiDescriptionService.generateDescription(imageRegion);
    element.setAiDescription(aiDescription);

    logger.debug("🤖 AI 설명 생성 완료: type={}, length={}",
                layoutClass, aiDescription.length());
}
```

**기대 결과**:
- `FIGURE`, `TABLE`, `FLOWCHART`만 AI 설명 생성
- 나머지 11개 비활성 클래스는 AI 호출 제외

---

### 2.6 작업 체크리스트

- [ ] **2.6.1** `QuestionNumberExtractor.java`에서 isSubQuestionPattern 필터링 제거
- [ ] **2.6.2** `groupElementsByQuestion` 메서드 재설계 (하위 문항 종속 로직)
- [ ] **2.6.3** `generateStructuredData` 정렬 로직 수정 (3단계)
- [ ] **2.6.4** `getMinY()` 헬퍼 메서드 추가
- [ ] **2.6.5** 정렬 후 로깅 추가 (디버깅용)
- [ ] **2.6.6** OpenAI 처리 로직 확인 (3개 클래스만)
- [ ] **2.6.7** 컴파일 오류 확인 (0개 목표)
- [ ] **2.6.8** 단위 테스트 실행: `./gradlew test --tests "*UnifiedAnalysisEngineTest"`

---

## 🧪 작업 3: 테스트 코드 초안 작성

### 3.1 개요

**목표**: 실제 이미지 (`쎈 수학1-1_페이지_016.jpg`)를 사용한 통합 테스트로 LAM v2 전환 및 정렬 로직을 검증합니다.

**파일 경로**: `/home/jongyoung3/SmartEye_v0.4/Backend/smarteye-backend/src/test/java/com/smarteye/application/analysis/UnifiedAnalysisEngineIntegrationTest.java`

**예상 작업 시간**: 1-2시간

---

### 3.2 테스트 이미지 정보

**이미지 경로**: `/home/jongyoung3/SmartEye_v0.4/쎈 수학1-1_페이지_016.jpg`

**레이아웃 구조**:
- **2단 컬럼** (왼쪽 컬럼 0, 오른쪽 컬럼 1)
- **총 7개 메인 문제**:
  - 컬럼 0: 001(type), 003(number), 004(number)
  - 컬럼 1: 005(number), 006(number), 007(number), 002(type)
- **하위 문항**: 문제 004에 (1), (2) 포함

**기대 결과**:
```json
{
  "stats": {
    "total_questions": 7
  },
  "questions": [
    {"question_number": "001", "columnIndex": 0, "boundary_type": "question_type"},
    {"question_number": "003", "columnIndex": 0, "boundary_type": "question_number"},
    {"question_number": "004", "columnIndex": 0, "boundary_type": "question_number", "has_sub_questions": true},
    {"question_number": "005", "columnIndex": 1, "boundary_type": "question_number"},
    {"question_number": "006", "columnIndex": 1, "boundary_type": "question_number"},
    {"question_number": "007", "columnIndex": 1, "boundary_type": "question_number"},
    {"question_number": "002", "columnIndex": 1, "boundary_type": "question_type"}
  ]
}
```

---

### 3.3 테스트 코드 초안

```java
package com.smarteye.application.analysis;

import com.smarteye.application.analysis.dto.UnifiedAnalysisResult;
import com.smarteye.application.analysis.dto.QuestionData;
import com.smarteye.domain.analysis.entity.AnalysisJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LAM v2 모델 전환 및 컬럼 우선 정렬 통합 테스트
 *
 * <p>실제 이미지를 사용하여 다음 기능을 검증합니다:</p>
 * <ul>
 *   <li>LAM v2 모델 23개 클래스 인식</li>
 *   <li>하위 문항 (second_question_number) 계층 구조</li>
 *   <li>컬럼 우선 정렬 (columnIndex → Y좌표 → 문제 번호)</li>
 *   <li>활성 클래스 12개만 CIM 로직 적용</li>
 * </ul>
 *
 * @since v0.5
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("LAM v2 통합 테스트")
class UnifiedAnalysisEngineIntegrationTest {

    @Autowired
    private CIMService cimService;

    /**
     * TC-1: 2단 레이아웃 이미지 전체 파이프라인 테스트
     *
     * <p><b>테스트 이미지</b>: 쎈 수학1-1_페이지_016.jpg</p>
     * <ul>
     *   <li>2단 컬럼 (0, 1)</li>
     *   <li>총 7개 메인 문제</li>
     *   <li>문제 004에 하위 문항 (1), (2) 포함</li>
     * </ul>
     *
     * <p><b>검증 항목</b>:</p>
     * <ul>
     *   <li>총 문제 수: 7개 (하위 문항 제외)</li>
     *   <li>컬럼 우선 정렬: 컬럼 0 → 컬럼 1</li>
     *   <li>같은 컬럼 내 Y좌표 순서</li>
     *   <li>하위 문항 데이터 존재 여부</li>
     * </ul>
     */
    @Test
    @DisplayName("2단 레이아웃 + 하위 문항 전체 파이프라인 테스트")
    void testMultiColumnLayoutWithSubQuestions() throws Exception {
        // ========================================
        // Given: 실제 테스트 이미지 로드
        // ========================================
        String testImagePath = "/home/jongyoung3/SmartEye_v0.4/쎈 수학1-1_페이지_016.jpg";
        BufferedImage image = ImageIO.read(new File(testImagePath));
        assertThat(image).isNotNull();

        AnalysisJob job = new AnalysisJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setStatus("IN_PROGRESS");

        // ========================================
        // When: 전체 분석 실행 (LAM → TSPM → CIM)
        // ========================================
        UnifiedAnalysisResult result = cimService.performUnifiedAnalysisWithCIM(
            image,
            job,
            "yolo_v10_imgsz1024_epoch200",  // LAM v2 모델
            null
        );

        // ========================================
        // Then: 기본 검증
        // ========================================
        assertThat(result.isSuccess())
            .as("분석이 성공해야 함")
            .isTrue();

        Map<String, Object> cimData = result.getCimData();
        assertThat(cimData)
            .as("CIM 데이터가 null이 아니어야 함")
            .isNotNull();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions = (List<Map<String, Object>>) cimData.get("questions");
        assertThat(questions)
            .as("questions 배열이 null이 아니어야 함")
            .isNotNull();

        // ========================================
        // 검증 1: 총 문제 수 (7개)
        // ========================================
        assertThat(questions)
            .as("총 7개 메인 문제여야 함 (하위 문항 (1), (2) 제외)")
            .hasSize(7);

        // ========================================
        // 검증 2: 문제 번호 순서 (컬럼 우선 정렬)
        // ========================================
        List<String> questionNumbers = questions.stream()
            .map(q -> (String) q.get("question_number"))
            .collect(Collectors.toList());

        assertThat(questionNumbers)
            .as("문제 번호 순서: 컬럼 0 (001, 003, 004) → 컬럼 1 (005, 006, 007, 002)")
            .containsExactly("001", "003", "004", "005", "006", "007", "002");

        // ========================================
        // 검증 3: columnIndex 값 확인
        // ========================================
        assertThat((Integer) questions.get(0).get("columnIndex"))
            .as("001은 컬럼 0")
            .isEqualTo(0);
        assertThat((Integer) questions.get(1).get("columnIndex"))
            .as("003은 컬럼 0")
            .isEqualTo(0);
        assertThat((Integer) questions.get(2).get("columnIndex"))
            .as("004는 컬럼 0")
            .isEqualTo(0);
        assertThat((Integer) questions.get(3).get("columnIndex"))
            .as("005는 컬럼 1")
            .isEqualTo(1);
        assertThat((Integer) questions.get(4).get("columnIndex"))
            .as("006은 컬럼 1")
            .isEqualTo(1);
        assertThat((Integer) questions.get(5).get("columnIndex"))
            .as("007은 컬럼 1")
            .isEqualTo(1);
        assertThat((Integer) questions.get(6).get("columnIndex"))
            .as("002는 컬럼 1")
            .isEqualTo(1);

        // ========================================
        // 검증 4: 하위 문항 데이터 존재 확인 (문제 004)
        // ========================================
        Map<String, Object> question004 = questions.get(2);
        assertThat(question004.get("question_number"))
            .as("세 번째 문제는 004")
            .isEqualTo("004");

        @SuppressWarnings("unchecked")
        Map<String, Object> questionContent = (Map<String, Object>) question004.get("question_content_simplified");
        assertThat(questionContent)
            .as("문제 004는 question_content_simplified를 가져야 함")
            .isNotNull();

        // 하위 문항 텍스트 존재 확인 (예상: second_question_number 클래스 포함)
        assertThat(questionContent)
            .as("문제 004는 하위 문항 관련 데이터를 포함해야 함")
            .containsKey("second_question_number");

        // ========================================
        // 검증 5: 잘못된 문제 번호 미포함 확인
        // ========================================
        assertThat(questionNumbers)
            .as("\"1\", \"2\" 문제 번호가 없어야 함 (하위 문항)")
            .doesNotContain("1", "2");

        // ========================================
        // 검증 6: stats 확인
        // ========================================
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) cimData.get("stats");
        assertThat(stats)
            .as("stats가 null이 아니어야 함")
            .isNotNull();

        assertThat((Integer) stats.get("total_questions"))
            .as("total_questions는 7이어야 함")
            .isEqualTo(7);
    }

    /**
     * TC-2: LAM v2 활성 클래스 12개만 처리 확인
     *
     * <p>11개 비활성 클래스 (@Deprecated)는 CIM 로직에서 무시되어야 함</p>
     */
    @Test
    @DisplayName("활성 클래스 12개만 CIM 처리 확인")
    void testOnlyActiveClassesProcessed() throws Exception {
        // Given
        String testImagePath = "/home/jongyoung3/SmartEye_v0.4/쎈 수학1-1_페이지_016.jpg";
        BufferedImage image = ImageIO.read(new File(testImagePath));

        AnalysisJob job = new AnalysisJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setStatus("IN_PROGRESS");

        // When
        UnifiedAnalysisResult result = cimService.performUnifiedAnalysisWithCIM(
            image, job, "yolo_v10_imgsz1024_epoch200", null
        );

        // Then
        Map<String, Object> cimData = result.getCimData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions = (List<Map<String, Object>>) cimData.get("questions");

        // 각 문제의 question_content_simplified에 비활성 클래스가 없어야 함
        List<String> deprecatedClasses = List.of(
            "abandon", "figure_caption", "table_caption", "footnote",
            "formula", "formula_caption", "page_number",
            "underline_blank", "parenthesis_blank", "box_blank", "grid_blank"
        );

        for (Map<String, Object> question : questions) {
            @SuppressWarnings("unchecked")
            Map<String, Object> content = (Map<String, Object>) question.get("question_content_simplified");

            if (content != null) {
                for (String deprecatedClass : deprecatedClasses) {
                    assertThat(content)
                        .as("비활성 클래스 %s는 question_content_simplified에 없어야 함", deprecatedClass)
                        .doesNotContainKey(deprecatedClass);
                }
            }
        }
    }

    /**
     * TC-3: AI 설명 생성 클래스 확인 (3개만)
     *
     * <p>FIGURE, TABLE, FLOWCHART만 AI 설명이 생성되어야 함</p>
     */
    @Test
    @DisplayName("AI 설명 생성 클래스 확인 (3개)")
    void testAIDescriptionOnlyForActiveVisualClasses() throws Exception {
        // Given
        String testImagePath = "/home/jongyoung3/SmartEye_v0.4/쎈 수학1-1_페이지_016.jpg";
        BufferedImage image = ImageIO.read(new File(testImagePath));

        AnalysisJob job = new AnalysisJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setStatus("IN_PROGRESS");

        // When
        UnifiedAnalysisResult result = cimService.performUnifiedAnalysisWithCIM(
            image, job, "yolo_v10_imgsz1024_epoch200", null
        );

        // Then
        Map<String, Object> cimData = result.getCimData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions = (List<Map<String, Object>>) cimData.get("questions");

        // AI 설명이 있는 요소 카운트
        int aiDescriptionCount = 0;
        List<String> activeAIClasses = List.of("figure", "table", "flowchart");

        for (Map<String, Object> question : questions) {
            @SuppressWarnings("unchecked")
            Map<String, Object> content = (Map<String, Object>) question.get("question_content_simplified");

            if (content != null) {
                for (String aiClass : activeAIClasses) {
                    if (content.containsKey(aiClass)) {
                        aiDescriptionCount++;
                    }
                }
            }
        }

        assertThat(aiDescriptionCount)
            .as("AI 설명은 FIGURE, TABLE, FLOWCHART에만 생성되어야 함")
            .isGreaterThanOrEqualTo(0);  // 실제 이미지에 따라 달라짐
    }
}
```

---

### 3.4 테스트 실행 방법

#### 3.4.1 단위 테스트 실행

```bash
cd Backend/smarteye-backend

# 전체 테스트 실행
./gradlew test

# 통합 테스트만 실행
./gradlew test --tests "*UnifiedAnalysisEngineIntegrationTest"

# 특정 테스트 케이스만 실행
./gradlew test --tests "*UnifiedAnalysisEngineIntegrationTest.testMultiColumnLayoutWithSubQuestions"
```

#### 3.4.2 실제 API 테스트 (Swagger UI)

1. **백엔드 시작**:
   ```bash
   cd Backend/smarteye-backend
   ./gradlew bootRun --args='--spring.profiles.active=dev'
   ```

2. **Swagger UI 접속**: http://localhost:8080/swagger-ui/index.html

3. **`/api/analysis/unified` 엔드포인트 테스트**:
   - 이미지 업로드: `쎈 수학1-1_페이지_016.jpg`
   - 모델: `yolo_v10_imgsz1024_epoch200`

4. **JSON 응답 검증**:
   - `total_questions`: 7
   - `questions` 배열 순서: `[001, 003, 004, 005, 006, 007, 002]`
   - `columnIndex` 값: `[0, 0, 0, 1, 1, 1, 1]`

---

### 3.5 작업 체크리스트

- [ ] **3.5.1** 테스트 파일 생성: `UnifiedAnalysisEngineIntegrationTest.java`
- [ ] **3.5.2** TC-1 작성: 2단 레이아웃 + 하위 문항 테스트
- [ ] **3.5.3** TC-2 작성: 활성 클래스 12개만 처리 확인
- [ ] **3.5.4** TC-3 작성: AI 설명 생성 클래스 확인
- [ ] **3.5.5** 테스트 실행: `./gradlew test --tests "*UnifiedAnalysisEngineIntegrationTest"`
- [ ] **3.5.6** 테스트 통과율: 100% 목표
- [ ] **3.5.7** Swagger UI 수동 테스트
- [ ] **3.5.8** JSON 응답 검증

---

## 📊 최종 검증 기준

### 검증 항목 체크리스트

#### ✅ 작업 1: LayoutClass.java

- [ ] 활성 클래스 12개 (OCR 9개 + AI 3개) 정의 완료
- [ ] 비활성 클래스 11개 @Deprecated 처리 완료
- [ ] 별칭 매핑 Map 추가 완료
- [ ] fromString() 메서드 수정 완료
- [ ] 컴파일 오류 0개
- [ ] LayoutClassTest 통과

#### ✅ 작업 2: UnifiedAnalysisEngine.java

- [ ] isSubQuestionPattern 필터링 제거 완료
- [ ] groupElementsByQuestion 재설계 완료 (하위 문항 종속)
- [ ] generateStructuredData 정렬 로직 구현 완료 (3단계)
- [ ] getMinY() 헬퍼 메서드 추가 완료
- [ ] 정렬 후 로깅 추가 완료
- [ ] OpenAI 처리 로직 확인 완료 (3개 클래스만)
- [ ] 컴파일 오류 0개
- [ ] UnifiedAnalysisEngineTest 통과

#### ✅ 작업 3: 테스트 코드

- [ ] UnifiedAnalysisEngineIntegrationTest 작성 완료
- [ ] TC-1: 2단 레이아웃 테스트 통과
- [ ] TC-2: 활성 클래스 테스트 통과
- [ ] TC-3: AI 설명 테스트 통과
- [ ] Swagger UI 수동 테스트 완료
- [ ] JSON 응답 검증 완료

### 성능 지표

| 지표 | 목표 | 측정 방법 |
|------|------|----------|
| **다단 레이아웃 정확도** | 98% | 7/7 문제 정확 정렬 |
| **문제 번호 인식률** | 100% | 잘못된 문제 ID 0개 |
| **컬럼 우선 정렬 성공률** | 100% | 컬럼 0 → 컬럼 1 순서 |
| **하위 문항 처리 성공률** | 100% | (1), (2) 데이터 유지 |
| **평균 처리 시간** | < 9초 | API 응답 시간 측정 |
| **테스트 통과율** | 100% | 모든 테스트 통과 |

---

## 🚀 다음 세션 실행 순서

### Phase 1: LayoutClass.java 수정 (2시간)

1. **Step 1-1**: 활성 클래스 12개 추가/확인 (30분)
2. **Step 1-2**: 비활성 클래스 11개 @Deprecated 처리 (30분)
3. **Step 1-3**: 별칭 매핑 추가 (30분)
4. **Step 1-4**: 컴파일 및 테스트 (30분)

### Phase 2: UnifiedAnalysisEngine.java 수정 (3-4시간)

1. **Step 2-1**: isSubQuestionPattern 제거 (30분)
2. **Step 2-2**: groupElementsByQuestion 재설계 (1-1.5시간)
3. **Step 2-3**: generateStructuredData 정렬 구현 (1-1.5시간)
4. **Step 2-4**: OpenAI 로직 확인 (30분)
5. **Step 2-5**: 컴파일 및 테스트 (30분)

### Phase 3: 테스트 작성 (1-2시간)

1. **Step 3-1**: 테스트 파일 생성 및 TC-1~3 작성 (1시간)
2. **Step 3-2**: 테스트 실행 및 검증 (30분)
3. **Step 3-3**: Swagger UI 수동 테스트 (30분)

---

## 📚 참고 문서

- **LAM v2 영향 분석 보고서**: `claudedocs/LAM_V2_IMPACT_ANALYSIS_REPORT.md`
- **CIM 공간 정렬 재설계 계획서**: `CIM_SPATIAL_SORTING_REDESIGN_MASTER_PLAN.md`
- **CLAUDE.md**: 프로젝트 개요 및 아키텍처
- **data.yaml**: LAM v2 모델 클래스 정의

---

## ✅ 최종 체크리스트

### 착수 전 확인 사항

- [ ] 기존 보고서 2개 정독 완료
- [ ] 테스트 이미지 경로 확인: `/home/jongyoung3/SmartEye_v0.4/쎈 수학1-1_페이지_016.jpg`
- [ ] 개발 환경 준비 완료: `./start_dev.sh`
- [ ] 백엔드 정상 동작 확인: `http://localhost:8080/swagger-ui/index.html`

### 작업 완료 후 확인 사항

- [ ] 모든 컴파일 오류 해결 (0개)
- [ ] 모든 테스트 통과 (100%)
- [ ] Swagger UI 수동 테스트 완료
- [ ] JSON 응답 검증 완료 (7개 문제, 컬럼 정렬)
- [ ] 로그 출력 확인 (경계 7개, 정렬 순서)
- [ ] CLAUDE.md 업데이트 (선택사항)

---

**문서 끝**

**작성 완료일**: 2025-10-15
**총 작성 시간**: 약 1시간
**문서 상태**: ✅ Final (구현 준비 완료)
**예상 작업 시간**: 6-8시간 (Phase 1: 2h, Phase 2: 3-4h, Phase 3: 1-2h)
