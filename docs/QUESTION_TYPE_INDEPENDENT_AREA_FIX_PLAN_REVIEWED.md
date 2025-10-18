# Question Type 독립 영역 처리 구현 계획 (검토 버전)

## 📋 Executive Summary

**문제:** `question_type`이 현재 문제 경계 감지에만 사용되고, JSON 출력에 독립적인 문제 영역으로 표시되지 않음

**목표:** `question_type`을 `question_number`처럼 독립적인 문제 영역으로 처리하여 주변 요소를 할당받고 JSON에 출력

**핵심 변경사항:**
1. ✅ `QUESTION_TYPE` → 독립 문제 영역 생성 (신규)
2. ⚠️ `UNIT` → **컬럼 감지에는 유지**, 독립 영역 생성 안 함 (기존 유지)
3. ✅ `QUESTION_NUMBER` → 기존 동작 유지

**영향 범위:**
- `QuestionNumberExtractor.java` - question_type 처리 로직 추가
- `SpatialAnalysisEngine.java` - type_* 패턴 인식 확인
- `UnifiedAnalysisEngine.java` - type_* 키 처리 확인
- JSON 출력 - `question_type` 항목 추가

---

## 🔍 현재 구현 분석

### 1. QuestionNumberExtractor.java

#### 1.1 경계 클래스 체크 (Lines 142-158)

**현재 코드:**
```java
// 경계 클래스 체크: QUESTION_NUMBER, QUESTION_TYPE, UNIT 모두 허용
boolean isBoundaryClass = (
    cls == LayoutClass.QUESTION_NUMBER ||
    cls == LayoutClass.QUESTION_TYPE ||
    cls == LayoutClass.UNIT
);
```

**현재 동작:**
- 3가지 클래스 모두 컬럼 감지용으로 사용
- 하지만 Lines 171-182에서 `QUESTION_TYPE`과 `UNIT`은 continue로 스킵

**문제점:**
- `QUESTION_TYPE`이 컬럼 감지에는 사용되지만, 독립 영역으로 생성 안 됨
- `UNIT`도 동일 (이건 의도된 동작으로 유지해야 함)

#### 1.2 특별 처리 로직 (Lines 171-182)

**현재 코드:**
```java
// QUESTION_TYPE 또는 UNIT의 경우 특별 처리
if (cls == LayoutClass.QUESTION_TYPE || cls == LayoutClass.UNIT) {
    logger.debug("📌 {} 감지: '{}' (LAM conf={})", 
               cls == LayoutClass.QUESTION_TYPE ? "문제 유형" : "단원",
               ocrText,
               String.format("%.3f", lamConfidence));
    
    // TODO: 유형/단원 정보를 별도로 저장하는 로직 추가 필요
    continue;  // ❌ 둘 다 스킵
}
```

**문제점:**
- `QUESTION_TYPE`과 `UNIT`을 동일하게 처리
- `QUESTION_TYPE`은 독립 영역으로 만들어야 하는데 스킵됨
- `UNIT`은 스킵이 맞음 (컬럼 감지용으로만 사용)

---

## 🎯 요구사항 정리 (수정됨)

### 변경 사항

| 레이아웃 클래스 | 컬럼 감지 사용 | 독립 문제 영역 생성 | 변경 여부 |
|----------------|---------------|-------------------|----------|
| `QUESTION_NUMBER` | ✅ 사용 | ✅ 생성 | 유지 |
| `QUESTION_TYPE` | ✅ 사용 | ✅ **생성 (신규)** | **변경** |
| `UNIT` | ✅ **사용 (유지)** | ❌ 생성 안 함 | 유지 |

### 기대 동작

1. **`question_type` 처리:**
   - LAM이 `question_type` 감지 시:
     - ✅ `questionPositions`에 추가 (컬럼 감지용)
     - ✅ 독립 문제 영역 생성 (주변 요소 할당받음)
     - ✅ JSON 출력에 표시
   - 예: `"type_1_유형01": { "elements": [...], "text": "유형 01" }`

2. **`UNIT` 처리 (기존 유지):**
   - LAM이 `UNIT` 감지 시:
     - ✅ `questionPositions`에는 **추가 안 함** (경계 클래스 체크만 통과)
     - ✅ 컬럼 감지에 X 좌표 기여 (isBoundaryClass 통과 후 별도 처리)
     - ❌ 독립 문제 영역 생성 안 함
     - ✅ 로깅만 수행

**⚠️ 중요:** UNIT을 완전히 제거하면 컬럼 감지 정확도가 떨어질 수 있으므로, 컬럼 감지용으로는 유지!

---

## 🔧 수정 계획

### Phase 1: QuestionNumberExtractor 수정

#### 1.1 경계 클래스 체크 수정 (Lines 142-158)

**수정 방향:** 주석만 업데이트 (로직 유지)

**Before:**
```java
// 경계 클래스 체크: QUESTION_NUMBER, QUESTION_TYPE, UNIT 모두 허용
boolean isBoundaryClass = (
    cls == LayoutClass.QUESTION_NUMBER ||
    cls == LayoutClass.QUESTION_TYPE ||
    cls == LayoutClass.UNIT
);
```

**After:**
```java
// 문제 경계 클래스 체크
// - QUESTION_NUMBER: 독립 영역 생성 + 컬럼 감지
// - QUESTION_TYPE: 독립 영역 생성 + 컬럼 감지 (v0.7 추가)
// - UNIT: 컬럼 감지만 사용 (독립 영역 생성 안 함)
boolean isBoundaryClass = (
    cls == LayoutClass.QUESTION_NUMBER ||
    cls == LayoutClass.QUESTION_TYPE ||
    cls == LayoutClass.UNIT
);
```

#### 1.2 question_type 처리 로직 수정 (Lines 171-182)

**Before:**
```java
// QUESTION_TYPE 또는 UNIT의 경우 특별 처리
if (cls == LayoutClass.QUESTION_TYPE || cls == LayoutClass.UNIT) {
    logger.debug("📌 {} 감지: '{}' (LAM conf={})", 
               cls == LayoutClass.QUESTION_TYPE ? "문제 유형" : "단원",
               ocrText,
               String.format("%.3f", lamConfidence));
    
    // TODO: 유형/단원 정보를 별도로 저장하는 로직 추가 필요
    continue;  // ❌ 스킵
}
```

**After:**
```java
// UNIT은 컬럼 감지에만 사용 (독립 영역 생성 안 함)
if (cls == LayoutClass.UNIT) {
    logger.debug("📌 단원 정보 감지 (컬럼 감지용): '{}' (LAM conf={})", 
               ocrText, String.format("%.3f", lamConfidence));
    // ✅ UNIT은 isBoundaryClass 통과했지만 독립 영역은 생성 안 함
    continue;
}

// 문제 식별자 결정 (QUESTION_NUMBER 또는 QUESTION_TYPE)
String questionIdentifier;
if (cls == LayoutClass.QUESTION_TYPE) {
    // 🆕 v0.7: question_type도 독립 영역으로 처리
    // ID 생성: Layout ID + OCR 텍스트 조합 (중복 방지)
    String sanitizedText = ocrText.trim()
        .replaceAll("\\s+", "_")           // 띄어쓰기 → 언더스코어
        .replaceAll("[^가-힣a-zA-Z0-9_]", "");  // 특수문자 제거
    questionIdentifier = String.format("type_%d_%s", layout.getId(), sanitizedText);
    
    logger.info("📌 문제 유형 영역 생성: '{}' → ID: '{}' (LAM conf={})",
               ocrText, questionIdentifier, String.format("%.3f", lamConfidence));
} else {
    // QUESTION_NUMBER는 기존 패턴 매칭 사용
    questionIdentifier = patternMatchingEngine.extractQuestionNumber(ocrText);
    if (questionIdentifier == null) {
        logger.debug("⚠️ 패턴 매칭 실패 - OCR 텍스트: '{}'", ocrText);
        continue;
    }
    
    // 하위 문항 필터링
    if (SUB_QUESTION_PATTERN.matcher(ocrText.trim()).matches()) {
        logger.debug("⊘ 하위 문항 패턴 감지, 건너뜀: '{}'", ocrText.trim());
        continue;
    }
}
```

**ID 생성 전략:**
- `type_{layoutId}_{sanitizedText}` 형식
- 예: `type_5_유형01`, `type_12_필수예제`
- Layout ID로 중복 방지 (같은 페이지에 같은 텍스트 여러 개 있어도 구분)

#### 1.3 후보 등록 로직 수정 (Lines 183-220)

**변경사항:**
- 패턴 매칭 점수 계산 부분도 `question_type`에 맞게 수정
- `question_type`은 패턴 매칭 불필요 (OCR 텍스트 그대로 사용)

**Before:**
```java
// 패턴 매칭으로 문제 번호 추출
String questionNum = patternMatchingEngine.extractQuestionNumber(ocrText);
if (questionNum == null) {
    logger.debug("⚠️ 패턴 매칭 실패 - OCR 텍스트: '{}'", ocrText);
    continue;
}

// 하위 문항 필터링
if (SUB_QUESTION_PATTERN.matcher(ocrText.trim()).matches()) {
    logger.debug("⊘ 하위 문항 패턴 감지, 건너뜀: '{}'", ocrText.trim());
    continue;
}

// 패턴 매칭 점수 계산
double patternScore = calculatePatternMatchScore(ocrText, questionNum);
```

**After:**
```java
// questionIdentifier는 위에서 이미 결정됨

// 패턴 매칭 점수 계산 (QUESTION_TYPE은 패턴 매칭 안 함)
double patternScore;
if (cls == LayoutClass.QUESTION_TYPE) {
    // question_type은 패턴 매칭 불필요 → 고정 점수
    patternScore = 1.0;  // 최대 점수 (LAM이 이미 분류함)
} else {
    // QUESTION_NUMBER는 기존 로직
    patternScore = calculatePatternMatchScore(ocrText, questionIdentifier);
}
```

#### 1.4 신뢰도 계산 로직 검토

**현재 코드 (Line 206):**
```java
double confidenceScore = calculateConfidenceScore(lamConfidence, adjustedOCRConfidence, patternScore);
```

**확인 필요:**
- `question_type`은 patternScore=1.0이므로, 가중 평균이 높아질 것
- LAM 신뢰도가 높으면 최종 점수도 높음 (적절함)

---

### Phase 2: 데이터 흐름 검증

#### 2.1 `extractQuestionPositions()` 반환값 확인

**현재:**
```java
public Map<String, Integer> extractQuestionPositions(
    List<LayoutInfo> layoutElements,
    List<OCRResult> ocrResults)
```

**반환 예시 (수정 후):**
```java
{
  "003": 1710,              // QUESTION_NUMBER (Y좌표)
  "004": 3429,              // QUESTION_NUMBER
  "type_5_유형01": 515,     // QUESTION_TYPE ✅ 추가됨
  "005": 1589,              // QUESTION_NUMBER
  "006": 3695               // QUESTION_NUMBER
}
```

**검증 필요:**
- ✅ Map<String, Integer> 타입이므로 `type_*` 키 지원 가능
- ✅ 컬럼 감지는 X 좌표 사용하므로 영향 없음 (Y 좌표만 저장)

**⚠️ 문제 발견:**
`extractQuestionPositions()`는 **Y 좌표만 반환**하는데, `ColumnDetector`는 **X 좌표가 필요**합니다!

**현재 코드 재확인 필요:**
```java
// SpatialAnalysisEngine에서 사용
Map<String, PositionInfo> questionPositions = ...;  // PositionInfo = (X, Y)
columnDetector.detectColumns(questionPositions, pageWidth);
```

**실제로는 `PositionInfo` 타입을 사용하는 것으로 보임!**

#### 2.2 실제 반환 타입 확인 필요

<function_calls>
<invoke name="grep_search">
<parameter name="isRegexp">true