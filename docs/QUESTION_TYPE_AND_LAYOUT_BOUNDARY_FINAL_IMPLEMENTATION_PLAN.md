# Question Type 독립 영역 및 레이아웃 경계 처리 최종 구현 계획

**작성일**: 2025-10-17  
**버전**: v0.7 Final  
**우선순위**: 🔴 P0 (긴급)  
**영향 범위**: QuestionNumberExtractor, UnifiedAnalysisEngine, CIM 출력

---

## 📋 Executive Summary

### 요구사항

1. **`question_type` 독립 영역 처리** ⭐ 핵심
   - 현재: 컬럼 감지에만 사용, JSON 출력 없음
   - 변경: `question_number`처럼 독립적인 문제 영역 생성, 주변 요소 할당, JSON 출력

2. **`UNIT` 완전 제거** 
   - 이유: LAM 모델 교체로 기준 변경, 사용 시 오히려 정확도 저하
   - 현재: isBoundaryClass에 포함되어 있지만 실제로는 사용 안 됨
   - 변경: 코드에서 완전히 제거

3. **`second_question_number` 처리 수정** ⚠️ 코드 순서 변경 필요
   - 현황: LAM이 `second_question_number` 클래스를 직접 제공
   - 문제: `UnifiedAnalysisEngine.groupSubQuestions()` 메서드에서 `question_number` 패턴을 먼저 체크 (Lines 713-740)
   - 영향: second_question_number 우선순위가 코드에 반영 안 됨
   - 수정: if-else 순서 뒤집기 (second_question_number 먼저, question_number는 fallback)

### 조사 결과 요약

| 항목 | 현재 상태 | 문제점 | 해결 방안 |
|------|----------|--------|----------|
| `question_type` | 컬럼 감지용만 사용 | JSON 출력 없음, X 좌표 찾기 실패 (fallback X=0) | 독립 영역 생성 + findQuestionTypeElement() 추가 |
| `UNIT` | isBoundaryClass 포함 → continue 스킵 | 무의미한 코드 (실제 사용 안 됨) | 완전 제거 |
| `second_question_number` | groupSubQuestions()에서 처리 | ⚠️ 코드 순서 문제 (question_number 먼저 체크) | if-else 순서 변경 (second_question_number 우선) |

### 핵심 변경사항

```diff
# QuestionNumberExtractor.java (Lines 142-158)
- cls == LayoutClass.UNIT                    // ❌ 제거
+ // UNIT 제거 (LAM 모델 변경으로 사용 중단)

# QuestionNumberExtractor.java (Lines 171-182)
- if (cls == LayoutClass.QUESTION_TYPE || cls == LayoutClass.UNIT) {
-     continue;  // 둘 다 스킵
- }
+ if (cls == LayoutClass.UNIT) {
+     continue;  // UNIT만 스킵
+ }
+ // QUESTION_TYPE은 독립 영역으로 처리
+ if (cls == LayoutClass.QUESTION_TYPE) {
+     questionIdentifier = String.format("type_%d_%s", layout.getId(), sanitizedText);
+ }

# UnifiedAnalysisEngine.java (Lines 377)
- if (!LayoutClass.QUESTION_NUMBER.getClassName().equals(...)) {
+ boolean isQuestionBoundary = (
+     LayoutClass.QUESTION_NUMBER.getClassName().equals(...) ||
+     LayoutClass.QUESTION_TYPE.getClassName().equals(...)
+ );
```

---

## 🔍 상세 조사 결과

### 1. second_question_number 처리 현황 ✅

#### 1.1 현재 구현 분석

**파일**: `UnifiedAnalysisEngine.java`  
**메서드**: `groupSubQuestions()` (Lines 695-760)

⚠️ **주의**: 아래는 **수정되어야 할 이상적인 순서**입니다. 현재 실제 코드는 **반대 순서**(question_number 먼저)로 되어 있어 수정이 필요합니다.

```java
/**
 * 🆕 Phase 2: 하위 문항 그룹핑 (LAM 클래스 기반)
 * 
 * ✅ 수정 후 LAM 모델: second_question_number 클래스 우선 인식
 */
private Map<String, Map<String, String>> groupSubQuestions(
    String mainQuestionNumber,
    List<AnalysisElement> elements
) {
    // 1. second_question_number 클래스 직접 지원 (LAM 정상 출력) ⭐ 우선순위 1
    if ("second_question_number".equals(className)) {
        subNumber = ocrText.replaceAll("[^0-9]", "");
        if (!subNumber.isEmpty()) {
            isSubQuestion = true;
        }
    }
    
    // 2. question_number 클래스에서 "(1)", "(2)" 패턴 감지 (Fallback - LAM 오감지 대비) ⭐ 우선순위 2
    else if ("question_number".equals(className)) {
        Matcher matcher = SUB_QUESTION_PATTERN.matcher(ocrText.trim());
        if (matcher.find()) {
            subNumber = matcher.group(1);
            isSubQuestion = true;
        }
    }
    
    // 3. 하위 문항별로 그룹핑하여 QuestionData.subQuestions에 추가
    qd.setSubQuestions(subQuestionList);
}
```

**현재 실제 코드의 문제점 (Lines 713-740)**:
```java
// ❌ 잘못된 순서: question_number를 먼저 체크
if ("question_number".equals(className)) {
    // 주석: "🔧 현재 LAM 모델" ← 잘못된 설명
}
else if ("second_question_number".equals(className)) {
    // 주석: "🆕 미래 LAM 모델" ← 잘못된 설명 (실제로는 현재 모델)
}
```

👉 **Phase 2.3에서 이 순서를 수정합니다.**

#### 1.2 데이터 흐름

```
LAM 감지: second_question_number
  ↓
SpatialAnalysisEngine: 가장 가까운 question_number에 할당
  ↓
UnifiedAnalysisEngine.analyzeQuestion(): 문제별 요소 그룹
  ↓
groupSubQuestions(): 하위 문항 분리 및 그룹핑
  ↓
QuestionData.subQuestions: List<QuestionData>로 저장
  ↓
JSON 출력: "sub_questions": [{"question_number": "1", ...}, ...]
```

#### 1.3 결론

⚠️ **코드 순서 수정 필요**
- **LAM 모델이 second_question_number 클래스를 직접 제공**하여 하위 문항 인식
- second_question_number는 이미 상위 question_number의 하위 문항으로 올바르게 할당됨
- **하지만 현재 코드에서 question_number 패턴을 먼저 체크하고 있어 우선순위가 잘못됨**
- **Phase 2.3에서 if-else 순서를 변경**하여 second_question_number를 우선 처리
- question_number의 "(1)" 패턴 감지는 LAM 오감지 대비 fallback 로직으로 변경
- QuestionNumberExtractor에서 continue로 스킵하는 것은 의도된 동작 (questionPositions는 메인 문제 경계만 표시)
- **수정 후 정상 동작 예상**

---

### 2. UNIT 제거 근거

#### 2.1 현재 UNIT 처리 로직

**파일**: `QuestionNumberExtractor.java`

```java
// Lines 142-158: isBoundaryClass에 포함
boolean isBoundaryClass = (
    cls == LayoutClass.QUESTION_NUMBER ||
    cls == LayoutClass.QUESTION_TYPE ||
    cls == LayoutClass.UNIT  // ✅ 포함됨
);

if (!isBoundaryClass) {
    continue;  // ✅ UNIT은 통과
}

// Lines 171-182: 특별 처리로 스킵
if (cls == LayoutClass.QUESTION_TYPE || cls == LayoutClass.UNIT) {
    // 로깅만 하고 continue
    continue;  // ❌ 결국 스킵되어 questionPositions에 미포함!
}
```

#### 2.2 문제점 분석

1. **무의미한 코드**
   - isBoundaryClass를 통과하지만 바로 continue로 스킵
   - questionPositions 맵에 추가되지 않음
   - `convertToPositionInfoMap()`에서 사용되지 않음
   - **컬럼 감지에 실제로 기여하지 않음**

2. **LAM 모델 변경 영향**
   - 사용자 피드백: "LAM 모델이 교체되면서 기준이 바뀌어서 사용하면 오히려 정확도가 낮아질 수 있음"
   - 새 LAM 모델의 UNIT 감지 기준이 달라졌을 가능성
   - 사용하지도 않는 코드이므로 제거가 안전

#### 2.3 제거 계획

```diff
# QuestionNumberExtractor.java (Lines 142-158)
  // 문제 경계 클래스 체크
- // - QUESTION_NUMBER: 독립 영역 생성 + 컬럼 감지
- // - QUESTION_TYPE: 독립 영역 생성 + 컬럼 감지 (v0.7 추가)
- // - UNIT: 컬럼 감지만 사용 (독립 영역 생성 안 함)
+ // - QUESTION_NUMBER: 독립 영역 생성 + 컬럼 감지
+ // - QUESTION_TYPE: 독립 영역 생성 + 컬럼 감지 (v0.7 추가)
  boolean isBoundaryClass = (
      cls == LayoutClass.QUESTION_NUMBER ||
-     cls == LayoutClass.QUESTION_TYPE ||
-     cls == LayoutClass.UNIT
+     cls == LayoutClass.QUESTION_TYPE
  );

# Lines 171-182: UNIT 처리 로직 수정
- // QUESTION_TYPE 또는 UNIT의 경우 특별 처리
- if (cls == LayoutClass.QUESTION_TYPE || cls == LayoutClass.UNIT) {
-     logger.debug("📌 {} 감지: '{}' (LAM conf={})", 
-                cls == LayoutClass.QUESTION_TYPE ? "문제 유형" : "단원",
-                ocrText, String.format("%.3f", lamConfidence));
-     continue;
- }
+ // UNIT은 더 이상 처리하지 않음 (LAM 모델 변경으로 제거)
+ // (isBoundaryClass에서 이미 제외되므로 이 부분에 도달하지 않음)
```

---

### 3. question_type 독립 영역 처리

#### 3.1 현재 문제점

1. **독립 영역 생성 안 됨**
   - Lines 171-182에서 continue로 스킵
   - questionPositions에 추가되지 않음
   - JSON 출력 없음

2. **X 좌표 찾기 실패**
   - `convertToPositionInfoMap()` 호출 시
   - `findQuestionNumberElement()`가 QUESTION_NUMBER만 찾음 (Line 377)
   - question_type 요소는 찾지 못해 X=0 fallback 사용
   - 컬럼 감지 정확도 저하

#### 3.2 해결 방안

**Step 1**: QuestionNumberExtractor에서 독립 영역 생성

```java
// Lines 171-200: 수정 후
// QUESTION_TYPE은 독립 영역으로 처리
String questionIdentifier;
if (cls == LayoutClass.QUESTION_TYPE) {
    // ID 생성: Layout ID + OCR 텍스트 조합 (중복 방지)
    String sanitizedText = ocrText.trim()
        .replaceAll("\\s+", "_")                    // 띄어쓰기 → 언더스코어
        .replaceAll("[^가-힣a-zA-Z0-9_]", "");      // 특수문자 제거
    
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

// 패턴 매칭 점수 계산
double patternScore;
if (cls == LayoutClass.QUESTION_TYPE) {
    patternScore = 1.0;  // 최대 점수 (LAM이 이미 분류함)
} else {
    patternScore = calculatePatternMatchScore(ocrText, questionIdentifier);
}

// 신뢰도 점수 계산 (기존 로직 유지)
double confidenceScore = calculateConfidenceScore(lamConfidence, adjustedOCRConfidence, patternScore);

// Y 좌표
int yCoordinate = layout.getBox()[1];

// 후보 등록
QuestionCandidate candidate = new QuestionCandidate(
    questionIdentifier, yCoordinate, confidenceScore, "LAM+OCR"
);

candidates.merge(questionIdentifier, candidate, (existing, newCand) ->
    newCand.confidenceScore > existing.confidenceScore ? newCand : existing
);
```

**Step 2**: UnifiedAnalysisEngine에서 X 좌표 찾기 로직 수정

```java
// Lines 355-400: findQuestionNumberElement() 수정
/**
 * 문제 번호 또는 문제 유형 요소 찾기 (Y좌표 + 클래스 매칭)
 * 
 * @param questionIdentifier 문제 식별자 ("003" 또는 "type_5_유형01")
 */
private LayoutInfo findQuestionBoundaryElement(
        String questionIdentifier,
        int questionY,
        List<LayoutInfo> layoutElements,
        Map<Integer, OCRResult> ocrMap) {

    // Y좌표 허용 오차 (±10px)
    final int Y_TOLERANCE = 10;
    
    // question_type 여부 판단
    boolean isQuestionType = questionIdentifier.startsWith("type_");

    for (LayoutInfo layout : layoutElements) {
        // Y좌표 매칭 확인
        if (Math.abs(layout.getBox()[1] - questionY) > Y_TOLERANCE) {
            continue;
        }

        String className = layout.getClassName();
        
        // QUESTION_TYPE 또는 QUESTION_NUMBER 확인
        if (isQuestionType) {
            // question_type 요소 찾기
            if (!LayoutClass.QUESTION_TYPE.getClassName().equals(className)) {
                continue;
            }
            
            // Layout ID로 매칭 (type_{layoutId}_{text} 형식)
            String idPrefix = "type_" + layout.getId() + "_";
            if (questionIdentifier.startsWith(idPrefix)) {
                logger.debug("✅ question_type 요소 발견: ID={}, X={}", 
                           layout.getId(), layout.getBox()[0]);
                return layout;
            }
        } else {
            // question_number 요소 찾기 (기존 로직)
            if (!LayoutClass.QUESTION_NUMBER.getClassName().equals(className)) {
                continue;
            }

            // OCR 텍스트로 검증
            OCRResult ocr = ocrMap.get(layout.getId());
            if (ocr != null && ocr.getText() != null) {
                String text = ocr.getText().trim();
                if (text.matches(".*" + questionIdentifier + "[.번)]?.*")) {
                    logger.debug("✅ question_number 요소 발견: {}, X={}", 
                               questionIdentifier, layout.getBox()[0]);
                    return layout;
                }
            }
        }
    }

    return null;
}
```

**Step 3**: convertToPositionInfoMap() 메서드명 업데이트 (호출부 수정)

```java
// Line 324: 메서드 호출 수정
private Map<String, ColumnDetector.PositionInfo> convertToPositionInfoMap(
        Map<String, Integer> questionPositions,
        List<LayoutInfo> layoutElements,
        List<OCRResult> ocrResults) {

    Map<String, ColumnDetector.PositionInfo> result = new HashMap<>();
    Map<Integer, OCRResult> ocrMap = ocrResults.stream()
        .collect(Collectors.toMap(OCRResult::getId, ocr -> ocr, (a, b) -> a));

    for (Map.Entry<String, Integer> entry : questionPositions.entrySet()) {
        String questionIdentifier = entry.getKey();  // "003" 또는 "type_5_유형01"
        int questionY = entry.getValue();

        // 🆕 v0.7: QUESTION_TYPE도 지원
        LayoutInfo boundaryElement = findQuestionBoundaryElement(
            questionIdentifier, questionY, layoutElements, ocrMap
        );

        if (boundaryElement != null) {
            int questionX = boundaryElement.getBox()[0];
            result.put(questionIdentifier, new ColumnDetector.PositionInfo(questionX, questionY));
            logger.trace("✅ 경계 요소 {} 위치: (X={}, Y={})", 
                       questionIdentifier, questionX, questionY);
        } else {
            // Fallback: X좌표를 0으로 설정
            result.put(questionIdentifier, new ColumnDetector.PositionInfo(0, questionY));
            logger.debug("⚠️ 경계 요소 {}를 찾지 못함 - X=0 fallback", questionIdentifier);
        }
    }

    return result;
}
```

---

## 🔧 구현 계획

### Phase 1: QuestionNumberExtractor 수정 (2-3시간)

#### 1.1 UNIT 제거

**파일**: `QuestionNumberExtractor.java`

**수정 위치 1**: Lines 142-158

```java
// Before
// 경계 클래스 체크: QUESTION_NUMBER, QUESTION_TYPE, UNIT 모두 허용
boolean isBoundaryClass = (
    cls == LayoutClass.QUESTION_NUMBER ||
    cls == LayoutClass.QUESTION_TYPE ||
    cls == LayoutClass.UNIT
);

// After
// 문제 경계 클래스 체크 (v0.7: UNIT 제거)
// - QUESTION_NUMBER: 독립 영역 생성 + 컬럼 감지
// - QUESTION_TYPE: 독립 영역 생성 + 컬럼 감지 (v0.7 추가)
boolean isBoundaryClass = (
    cls == LayoutClass.QUESTION_NUMBER ||
    cls == LayoutClass.QUESTION_TYPE
);
```

#### 1.2 question_type 독립 영역 생성

**수정 위치 2**: Lines 171-220 (대폭 수정)

```java
// Before
// QUESTION_TYPE 또는 UNIT의 경우 특별 처리
if (cls == LayoutClass.QUESTION_TYPE || cls == LayoutClass.UNIT) {
    logger.debug("📌 {} 감지: '{}' (LAM conf={})", 
               cls == LayoutClass.QUESTION_TYPE ? "문제 유형" : "단원",
               ocrText,
               String.format("%.3f", lamConfidence));
    continue;
}

// 패턴 매칭으로 문제 번호 추출
String questionNum = patternMatchingEngine.extractQuestionNumber(ocrText);
if (questionNum == null) {
    logger.debug("⚠️ 패턴 매칭 실패 - OCR 텍스트: '{}'", ocrText);
    continue;
}

// After
// 🆕 v0.7: 문제 식별자 결정 (QUESTION_NUMBER 또는 QUESTION_TYPE)
String questionIdentifier;
if (cls == LayoutClass.QUESTION_TYPE) {
    // question_type은 독립 영역으로 처리
    // ID 생성: Layout ID + OCR 텍스트 조합 (중복 방지)
    String sanitizedText = ocrText.trim()
        .replaceAll("\\s+", "_")                    // 띄어쓰기 → 언더스코어
        .replaceAll("[^가-힣a-zA-Z0-9_]", "");      // 특수문자 제거
    
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

// 패턴 매칭 점수 계산 (QUESTION_TYPE은 고정 점수)
double patternScore;
if (cls == LayoutClass.QUESTION_TYPE) {
    patternScore = 1.0;  // 최대 점수 (LAM이 이미 분류함)
} else {
    patternScore = calculatePatternMatchScore(ocrText, questionIdentifier);
}
```

**수정 위치 3**: Lines 206-220 (변수명 변경)

```java
// Before
QuestionCandidate candidate = new QuestionCandidate(
    questionNum, yCoordinate, confidenceScore, "LAM+OCR"
);

candidates.merge(questionNum, candidate, ...);

// After
QuestionCandidate candidate = new QuestionCandidate(
    questionIdentifier, yCoordinate, confidenceScore, "LAM+OCR"
);

candidates.merge(questionIdentifier, candidate, (existing, newCand) ->
    newCand.confidenceScore > existing.confidenceScore ? newCand : existing
);
```

---

### Phase 2: UnifiedAnalysisEngine 수정 (3-4시간)

#### 2.1 findQuestionBoundaryElement() 메서드 추가/수정

**파일**: `UnifiedAnalysisEngine.java`  
**위치**: Lines 355-400 (findQuestionNumberElement 대체)

```java
/**
 * 🆕 v0.7: 문제 경계 요소 찾기 (QUESTION_NUMBER 또는 QUESTION_TYPE)
 * 
 * @param questionIdentifier 문제 식별자 ("003" 또는 "type_5_유형01")
 * @param questionY Y 좌표
 * @param layoutElements LAM 레이아웃 요소 목록
 * @param ocrMap OCR 결과 맵 (ID → OCRResult)
 * @return 찾은 레이아웃 요소, 없으면 null
 */
private LayoutInfo findQuestionBoundaryElement(
        String questionIdentifier,
        int questionY,
        List<LayoutInfo> layoutElements,
        Map<Integer, OCRResult> ocrMap) {

    // Y좌표 허용 오차 (±10px)
    final int Y_TOLERANCE = 10;
    
    // question_type 여부 판단 (ID 패턴: "type_{layoutId}_{text}")
    boolean isQuestionType = questionIdentifier.startsWith("type_");

    for (LayoutInfo layout : layoutElements) {
        // 1. Y좌표 매칭 확인
        if (Math.abs(layout.getBox()[1] - questionY) > Y_TOLERANCE) {
            continue;
        }

        String className = layout.getClassName();
        
        if (isQuestionType) {
            // 2-A. QUESTION_TYPE 요소 찾기
            if (!LayoutClass.QUESTION_TYPE.getClassName().equals(className)) {
                continue;
            }
            
            // Layout ID로 매칭 (type_{layoutId}_{text} 형식에서 layoutId 추출)
            String idPrefix = "type_" + layout.getId() + "_";
            if (questionIdentifier.startsWith(idPrefix)) {
                logger.debug("✅ question_type 요소 발견: ID={}, OCR='{}', X={}", 
                           layout.getId(), 
                           ocrMap.get(layout.getId()) != null ? 
                               ocrMap.get(layout.getId()).getText() : "N/A",
                           layout.getBox()[0]);
                return layout;
            }
        } else {
            // 2-B. QUESTION_NUMBER 요소 찾기 (기존 로직)
            if (!LayoutClass.QUESTION_NUMBER.getClassName().equals(className)) {
                continue;
            }

            // OCR 텍스트로 검증
            OCRResult ocr = ocrMap.get(layout.getId());
            if (ocr != null && ocr.getText() != null) {
                String text = ocr.getText().trim();
                // 문제 번호 패턴 매칭: "1.", "1번", "Q1" 등
                if (text.matches(".*" + questionIdentifier + "[.번)]?.*")) {
                    logger.debug("✅ question_number 요소 발견: {}, OCR='{}', X={}", 
                               questionIdentifier, text, layout.getBox()[0]);
                    return layout;
                }
            }
        }
    }

    logger.debug("⚠️ 경계 요소 {}를 찾지 못함 (Y={})", questionIdentifier, questionY);
    return null;
}
```

#### 2.2 convertToPositionInfoMap() 메서드 수정

**위치**: Lines 324-354

```java
// Before (메서드명과 호출 변경)
LayoutInfo questionElement = findQuestionNumberElement(
    questionNum, questionY, layoutElements, ocrMap
);

// After
LayoutInfo boundaryElement = findQuestionBoundaryElement(
    questionIdentifier, questionY, layoutElements, ocrMap
);

if (boundaryElement != null) {
    int questionX = boundaryElement.getBox()[0];
    result.put(questionIdentifier, new ColumnDetector.PositionInfo(questionX, questionY));
    logger.trace("✅ 경계 요소 '{}' 위치: (X={}, Y={})", 
               questionIdentifier, questionX, questionY);
} else {
    // Fallback: X좌표를 0으로 설정
    result.put(questionIdentifier, new ColumnDetector.PositionInfo(0, questionY));
    logger.debug("⚠️ 경계 요소 '{}'를 찾지 못함 - X=0 fallback", questionIdentifier);
}
```

---

#### 2.3 groupSubQuestions() 메서드 순서 수정 ⭐ 중요

**파일**: `UnifiedAnalysisEngine.java`  
**위치**: Lines 713-740

**현재 문제점**:
- question_number 패턴을 먼저 체크 (Lines 713-725)
- second_question_number를 나중에 체크 (Lines 728-740)
- 주석이 잘못됨: "현재 LAM" vs "미래 LAM"

**수정 내용**:

```java
// Before (Lines 713-740)
// ❌ 잘못된 순서
for (AnalysisElement element : elements) {
    String className = element.getLayoutInfo() != null ? 
        element.getLayoutInfo().getClassName() : null;
    
    boolean isSubQuestion = false;
    String subNumber = null;
    
    // 🔧 현재 LAM 모델: question_number 클래스에서 (1), (2) 감지
    if ("question_number".equals(className)) {
        String ocrText = element.getOcrResult() != null ? 
            element.getOcrResult().getText() : null;
        
        if (ocrText != null) {
            Matcher matcher = SUB_QUESTION_PATTERN.matcher(ocrText.trim());
            if (matcher.find()) {
                subNumber = matcher.group(1);
                isSubQuestion = true;
                logger.debug("    📌 하위 문항 감지 (question_number): ({})", subNumber);
            }
        }
    }
    
    // 🆕 미래 LAM 모델: second_question_number 클래스 대비
    else if ("second_question_number".equals(className)) {
        String ocrText = element.getOcrResult() != null ? 
            element.getOcrResult().getText() : null;
        
        if (ocrText != null) {
            // (1), 1), 1. 등 다양한 패턴 지원
            subNumber = ocrText.replaceAll("[^0-9]", "");
            if (!subNumber.isEmpty()) {
                isSubQuestion = true;
                logger.debug("    📌 하위 문항 감지 (second_question_number): {}", subNumber);
            }
        }
    }
    // ... 나머지 코드
}

// After (Lines 713-740 수정)
// ✅ 올바른 순서
for (AnalysisElement element : elements) {
    String className = element.getLayoutInfo() != null ? 
        element.getLayoutInfo().getClassName() : null;
    
    boolean isSubQuestion = false;
    String subNumber = null;
    
    // ⭐ 우선순위 1: second_question_number 클래스 직접 지원 (LAM 정상 출력)
    if ("second_question_number".equals(className)) {
        String ocrText = element.getOcrResult() != null ? 
            element.getOcrResult().getText() : null;
        
        if (ocrText != null) {
            // (1), 1), 1. 등 다양한 패턴 지원
            subNumber = ocrText.replaceAll("[^0-9]", "");
            if (!subNumber.isEmpty()) {
                isSubQuestion = true;
                logger.debug("    📌 하위 문항 감지 (second_question_number): {}", subNumber);
            }
        }
    }
    
    // ⭐ 우선순위 2: question_number 패턴 매칭 (Fallback - LAM 오감지 대비)
    else if ("question_number".equals(className)) {
        String ocrText = element.getOcrResult() != null ? 
            element.getOcrResult().getText() : null;
        
        if (ocrText != null) {
            Matcher matcher = SUB_QUESTION_PATTERN.matcher(ocrText.trim());
            if (matcher.find()) {
                subNumber = matcher.group(1);
                isSubQuestion = true;
                logger.debug("    📌 하위 문항 감지 (fallback-question_number): ({})", subNumber);
            }
        }
    }
    // ... 나머지 코드
}
```

**변경 사항 요약**:
1. ✅ if-else 순서 뒤집기: second_question_number 먼저
2. ✅ 주석 수정: "LAM 정상 출력" vs "Fallback"
3. ✅ 로그 메시지 구분: "second_question_number" vs "fallback-question_number"
4. ✅ 우선순위 명시: ⭐ 우선순위 1, ⭐ 우선순위 2

---

### Phase 3: 검증 및 테스트 (2-3시간)

#### 3.1 단위 테스트

**파일**: `QuestionNumberExtractorTest.java`

```java
@Test
void testQuestionTypeExtraction() {
    // Given: LAM에서 question_type 감지
    LayoutInfo questionTypeLayout = new LayoutInfo();
    questionTypeLayout.setId(5);
    questionTypeLayout.setClassName("question_type");
    questionTypeLayout.setBox(new int[]{300, 500, 500, 550});
    questionTypeLayout.setConfidence(0.92);
    
    OCRResult ocrResult = new OCRResult();
    ocrResult.setId(5);
    ocrResult.setText("유형 01");
    ocrResult.setConfidence(0.88);
    
    List<LayoutInfo> layouts = List.of(questionTypeLayout);
    List<OCRResult> ocrs = List.of(ocrResult);
    
    // When: questionPositions 추출
    Map<String, Integer> questionPositions = extractor.extractQuestionPositions(layouts, ocrs);
    
    // Then: type_* 형식으로 추가됨
    assertFalse(questionPositions.isEmpty());
    assertTrue(questionPositions.containsKey("type_5_유형01"));
    assertEquals(500, questionPositions.get("type_5_유형01"));
}

@Test
void testUnitNotIncluded() {
    // Given: LAM에서 UNIT 감지
    LayoutInfo unitLayout = new LayoutInfo();
    unitLayout.setClassName("unit");
    unitLayout.setBox(new int[]{100, 200, 300, 250});
    
    OCRResult ocrResult = new OCRResult();
    ocrResult.setId(1);
    ocrResult.setText("I. 지수함수와 로그함수");
    
    List<LayoutInfo> layouts = List.of(unitLayout);
    List<OCRResult> ocrs = List.of(ocrResult);
    
    // When
    Map<String, Integer> questionPositions = extractor.extractQuestionPositions(layouts, ocrs);
    
    // Then: UNIT은 포함되지 않음
    assertTrue(questionPositions.isEmpty());
}

@Test
void testSecondQuestionNumberFiltered() {
    // Given: question_number에서 "(1)" 패턴 감지 (LAM 오감지 시나리오)
    // 정상적으로는 LAM이 second_question_number로 분류해야 함
    LayoutInfo subQuestionLayout = new LayoutInfo();
    subQuestionLayout.setClassName("question_number");
    
    OCRResult ocrResult = new OCRResult();
    ocrResult.setText("(1)");
    
    List<LayoutInfo> layouts = List.of(subQuestionLayout);
    List<OCRResult> ocrs = List.of(ocrResult);
    
    // When
    Map<String, Integer> questionPositions = extractor.extractQuestionPositions(layouts, ocrs);
    
    // Then: 하위 문항은 제외됨 (방어 로직 정상 동작)
    assertTrue(questionPositions.isEmpty());
}
```

**파일**: `UnifiedAnalysisEngineTest.java`

```java
@Test
void testFindQuestionBoundaryElement_QuestionType() {
    // Given
    LayoutInfo questionTypeLayout = new LayoutInfo();
    questionTypeLayout.setId(5);
    questionTypeLayout.setClassName("question_type");
    questionTypeLayout.setBox(new int[]{300, 500, 500, 550});
    
    OCRResult ocr = new OCRResult();
    ocr.setId(5);
    ocr.setText("유형 01");
    
    Map<Integer, OCRResult> ocrMap = Map.of(5, ocr);
    List<LayoutInfo> layouts = List.of(questionTypeLayout);
    
    // When
    LayoutInfo found = engine.findQuestionBoundaryElement(
        "type_5_유형01", 500, layouts, ocrMap
    );
    
    // Then
    assertNotNull(found);
    assertEquals(5, found.getId());
    assertEquals(300, found.getBox()[0]);  // X 좌표 확인
}

@Test
void testConvertToPositionInfoMap_WithQuestionType() {
    // Given
    Map<String, Integer> questionPositions = Map.of(
        "003", 1500,
        "type_5_유형01", 500,
        "004", 3000
    );
    
    List<LayoutInfo> layouts = createTestLayoutsWithQuestionType();
    List<OCRResult> ocrs = createTestOCRs();
    
    // When
    Map<String, PositionInfo> positionInfoMap = 
        engine.convertToPositionInfoMap(questionPositions, layouts, ocrs);
    
    // Then
    assertEquals(3, positionInfoMap.size());
    
    PositionInfo typePos = positionInfoMap.get("type_5_유형01");
    assertNotNull(typePos);
    assertNotEquals(0, typePos.getX());  // X=0 fallback이 아님!
    assertEquals(500, typePos.getY());
}

@Test
void testGroupSubQuestions_SecondQuestionNumberPriority() {
    // Given: second_question_number와 question_number "(1)" 패턴 모두 존재
    List<AnalysisElement> elements = new ArrayList<>();
    
    // second_question_number 요소 (LAM 정상 출력)
    AnalysisElement secondQN = createAnalysisElement("second_question_number", "(1)");
    elements.add(secondQN);
    
    // question_number 요소 (잘못된 LAM 감지, fallback 대상)
    AnalysisElement questionQN = createAnalysisElement("question_number", "(1)");
    elements.add(questionQN);
    
    // When
    Map<String, Map<String, String>> subQuestions = 
        engine.groupSubQuestions("001", elements);
    
    // Then: second_question_number가 우선 처리됨
    assertEquals(1, subQuestions.size());
    assertTrue(subQuestions.containsKey("1"));
    
    // 로그 확인: second_question_number가 먼저 감지되어야 함
    // "📌 하위 문항 감지 (second_question_number): 1" 로그 출력
}

@Test
void testGroupSubQuestions_FallbackToQuestionNumber() {
    // Given: second_question_number 없고, question_number "(1)" 패턴만 존재
    List<AnalysisElement> elements = new ArrayList<>();
    
    // question_number 요소 (fallback 시나리오)
    AnalysisElement questionQN = createAnalysisElement("question_number", "(1)");
    elements.add(questionQN);
    
    // When
    Map<String, Map<String, String>> subQuestions = 
        engine.groupSubQuestions("001", elements);
    
    // Then: question_number 패턴 매칭이 작동함 (fallback)
    assertEquals(1, subQuestions.size());
    assertTrue(subQuestions.containsKey("1"));
    
    // 로그 확인: fallback 로직 사용 확인
    // "📌 하위 문항 감지 (fallback-question_number): (1)" 로그 출력
}

private AnalysisElement createAnalysisElement(String className, String ocrText) {
    AnalysisElement element = new AnalysisElement();
    
    LayoutInfo layout = new LayoutInfo();
    layout.setClassName(className);
    element.setLayoutInfo(layout);
    
    OCRResult ocr = new OCRResult();
    ocr.setText(ocrText);
    element.setOcrResult(ocr);
    
    return element;
}
```

#### 3.2 통합 테스트

**시나리오**: question_type 포함 페이지 분석

```bash
# 1. 백엔드 빌드
cd Backend/smarteye-backend
./gradlew clean build

# 2. 서비스 시작
cd ../..
./start_dev.sh

# 3. Swagger UI 테스트
# http://localhost:8080/swagger-ui/index.html
# POST /api/analysis/unified-analysis
# - File: 쎈 수학 페이지 (question_type 포함)
# - Model: SmartEyeSsen
# - Analysis Mode: structured

# 4. 응답 JSON 확인
{
  "questions": [
    {
      "question_number": "003",
      "elements": {...}
    },
    {
      "question_number": "type_5_유형01",  // ✅ 추가됨!
      "question_text": "유형 01",
      "elements": {
        "question_type": ["유형 01"],
        "question_text": [...],
        ...
      }
    },
    {
      "question_number": "004",
      "elements": {...}
    }
  ]
}
```

#### 3.3 검증 체크리스트

**QuestionNumberExtractor 검증**
- [ ] UNIT이 questionPositions에 포함되지 않음
- [ ] question_type이 "type_{layoutId}_{text}" 형식으로 추가됨
- [ ] second_question_number가 여전히 필터링됨 (기존 동작 유지)
- [ ] question_number는 기존처럼 정상 추출됨

**UnifiedAnalysisEngine 검증**
- [ ] findQuestionBoundaryElement()가 question_type 요소를 찾음
- [ ] question_type의 X 좌표가 0이 아님 (실제 좌표)
- [ ] convertToPositionInfoMap()에서 type_* 키 처리됨
- [ ] groupSubQuestions()에서 second_question_number를 먼저 체크
- [ ] groupSubQuestions()에서 question_number 패턴은 fallback으로 처리
- [ ] 컬럼 감지가 question_type X 좌표 포함하여 정확하게 동작

**CIM JSON 출력 검증**
- [ ] questions 배열에 "type_*" 항목 포함
- [ ] type_* 항목의 elements가 비어있지 않음
- [ ] type_* 주변 요소들이 올바르게 할당됨
- [ ] question_number와 type_* 순서가 Y 좌표 기반으로 정렬됨

**로그 확인**
```bash
# question_type 감지 로그
grep "문제 유형 영역 생성" logs/application.log

# question_type X 좌표 찾기 로그
grep "question_type 요소 발견" logs/application.log

# UNIT 제외 확인 (로그 없어야 함)
grep "단원.*감지" logs/application.log | wc -l  # 0이어야 함
```

---

## 📊 예상 결과

### Before (현재)

```json
{
  "questions": [
    {
      "question_number": "003",
      "elements": {
        "question_text": ["003"],
        "figure": [...]
      }
    },
    {
      "question_number": "004",
      "elements": {...}
    }
  ]
}
```

### After (수정 후)

```json
{
  "questions": [
    {
      "question_number": "003",
      "elements": {
        "question_text": ["003"],
        "figure": [...]
      }
    },
    {
      "question_number": "type_5_유형01",  // ✅ 추가됨!
      "question_text": "유형 01",
      "question_type": "유형 01",
      "elements": {
        "question_type": ["유형 01"],
        "question_text": ["다음은..."],
        "figure": [...],
        ...
      },
      "metadata": {
        "total_elements": 5,
        "elements_by_type": {
          "question_type": 1,
          "question_text": 2,
          "figure": 2
        }
      }
    },
    {
      "question_number": "004",
      "elements": {...}
    }
  ]
}
```

**주요 차이점:**
1. ✅ `type_5_유형01` 항목 추가 (독립 문제 영역)
2. ✅ question_type 주변 요소들이 해당 영역에 할당됨
3. ✅ 순서는 Y 좌표 기반 (003 → type_5_유형01 → 004)
4. ✅ UNIT은 완전히 제거 (JSON에 없음)

---

## 🚨 리스크 및 완화 방안

### 1. ID 중복 가능성

**리스크**: 같은 페이지에 같은 question_type 텍스트가 여러 번 나타날 경우

**완화**:
```java
// Layout ID 포함으로 중복 방지
questionIdentifier = String.format("type_%d_%s", layout.getId(), sanitizedText);
// 예: type_5_유형01, type_12_유형01 (다른 ID)
```

### 2. X 좌표 찾기 실패

**리스크**: findQuestionBoundaryElement()가 요소를 찾지 못할 경우

**완화**:
```java
// Fallback: X=0으로 설정 (왼쪽 정렬 가정)
if (boundaryElement == null) {
    result.put(questionIdentifier, new ColumnDetector.PositionInfo(0, questionY));
    logger.warn("⚠️ 경계 요소 '{}'를 찾지 못함 - X=0 fallback 사용", questionIdentifier);
}
```

**모니터링**:
```bash
# X=0 fallback 사용 빈도 확인
grep "X=0 fallback" logs/application.log | wc -l
```

### 3. 기존 클라이언트 호환성

**리스크**: 프론트엔드가 "type_*" 형식의 question_number를 처리 못 할 수 있음

**완화**:
1. **필드 추가**: `question_type` 필드로 구분 가능
2. **패턴 매칭**: 프론트엔드에서 `startsWith("type_")` 확인
3. **점진적 롤아웃**: 백엔드 먼저 배포 후 프론트엔드 업데이트

**프론트엔드 수정 가이드**:
```javascript
// 문제 유형 판단
function isQuestionType(questionNumber) {
    return questionNumber.startsWith("type_");
}

// 표시 로직
questions.forEach(q => {
    if (isQuestionType(q.question_number)) {
        // question_type 특별 렌더링
        renderQuestionType(q);
    } else {
        // 일반 question_number 렌더링
        renderQuestionNumber(q);
    }
});
```

### 4. 성능 영향

**리스크**: question_type 추가로 처리 요소 증가

**예상 영향**:
- questionPositions 항목: +10% (페이지당 2-3개 question_type 추가)
- convertToPositionInfoMap() 처리 시간: +5% (추가 반복문)
- 전체 분석 시간: +1% 미만 (전체 파이프라인 대비 미미)

**모니터링**:
```java
long start = System.currentTimeMillis();
Map<String, PositionInfo> positionInfoMap = convertToPositionInfoMap(...);
long elapsed = System.currentTimeMillis() - start;
logger.info("🕐 PositionInfo 변환 시간: {}ms (항목 수: {})", elapsed, positionInfoMap.size());
```

---

## 📝 구현 체크리스트

### Phase 1: QuestionNumberExtractor 수정
- [ ] Lines 142-158: isBoundaryClass에서 UNIT 제거
- [ ] Lines 171-182: UNIT 처리 로직 삭제
- [ ] Lines 171-200: question_type 독립 영역 생성 로직 추가
- [ ] Lines 195-205: 패턴 매칭 점수 조건 분기 추가
- [ ] Lines 206-220: 변수명 questionNum → questionIdentifier 변경
- [ ] 주석 업데이트 (UNIT 제거 이유 명시)

### Phase 2: UnifiedAnalysisEngine 수정
- [ ] Lines 355-400: findQuestionNumberElement → findQuestionBoundaryElement 대체
- [ ] question_type 패턴 인식 로직 추가 (startsWith("type_"))
- [ ] Layout ID 기반 매칭 로직 추가
- [ ] Lines 324-354: convertToPositionInfoMap에서 메서드 호출 변경
- [ ] Lines 713-740: groupSubQuestions() if-else 순서 변경 (second_question_number 우선)
- [ ] groupSubQuestions() 주석 업데이트 ("LAM 정상 출력" vs "Fallback")
- [ ] 로그 메시지 업데이트 (question_number → 경계 요소, fallback 구분)

### Phase 3: 테스트
- [ ] QuestionNumberExtractorTest 작성 (3개 테스트)
- [ ] UnifiedAnalysisEngineTest 작성 (4개 테스트 - groupSubQuestions 우선순위 포함)
- [ ] Swagger UI 통합 테스트
- [ ] 로그 확인 (question_type 감지, X 좌표 찾기, second_question_number 우선순위)

### Phase 4: 문서화
- [ ] API 문서 업데이트 (question_number 설명에 type_* 패턴 추가)
- [ ] README 업데이트 (UNIT 제거 이유 설명)
- [ ] CHANGELOG 작성

---

## 📚 참고 문서

1. **CIM_FUNCTIONALITY_ISSUES_ROOT_CAUSE_ANALYSIS.md**
   - ContentElement bbox 손실 문제
   - IntegratedCIMProcessor 수정 필요성
   - P1 작업과 연계 고려

2. **QUESTION_TYPE_INDEPENDENT_AREA_FIX_PLAN_REVIEWED.md**
   - 초기 계획안 (검토 버전)
   - UnifiedAnalysisEngine 수정 부족 지적
   - 본 문서에서 완전히 보완

3. **Backend/smarteye-backend/src/main/java/com/smarteye/application/analysis/**
   - `QuestionNumberExtractor.java`: 문제 경계 추출
   - `UnifiedAnalysisEngine.java`: 분석 엔진
   - `engine/SpatialAnalysisEngine.java`: 공간 분석
   - `engine/ColumnDetector.java`: 컬럼 감지

---

## 🎯 구현 우선순위 및 일정

### P0 (긴급) - 1일 내 완료

1. ✅ **QuestionNumberExtractor 수정** (2-3시간)
   - UNIT 제거
   - question_type 독립 영역 생성

2. ✅ **UnifiedAnalysisEngine 수정** (4-5시간)
   - findQuestionBoundaryElement() 추가
   - convertToPositionInfoMap() 수정
   - groupSubQuestions() 순서 변경 ⭐ 추가

3. ✅ **단위 테스트 작성** (2-3시간)
   - QuestionNumberExtractor 테스트 (3개)
   - UnifiedAnalysisEngine 테스트 (4개 - 우선순위 테스트 포함)

4. ✅ **Swagger UI 검증** (30분)

### P1 (중요) - 3일 내 완료

5. ✅ **통합 테스트 강화** (1일)
   - 다양한 레이아웃 패턴 테스트
   - 엣지 케이스 확인

6. ✅ **프론트엔드 연동** (1일)
   - type_* 패턴 처리
   - UI 업데이트

7. ✅ **문서화** (1일)
   - API 문서
   - 사용자 가이드

---

## 🔄 배포 계획

### 단계 1: 개발 환경 검증 (Day 1)
```bash
# 백엔드 수정 및 빌드
cd Backend/smarteye-backend
./gradlew clean build

# 개발 환경 시작
./start_dev.sh

# Swagger UI 테스트
# http://localhost:8080/swagger-ui/index.html
```

### 단계 2: 스테이징 배포 (Day 2)
```bash
# Docker 이미지 빌드
docker build -t smarteye-backend:v0.7 .

# 스테이징 환경 배포
docker-compose -f docker-compose.staging.yml up -d

# 통합 테스트 실행
./integration-tests.sh
```

### 단계 3: 프로덕션 배포 (Day 3)
```bash
# 프로덕션 배포 (Blue-Green)
./deploy-production.sh --version v0.7 --strategy blue-green

# 모니터링
tail -f logs/application.log | grep -E "(question_type|type_)"
```

---

**작성자**: GitHub Copilot AI Agent  
**검토자**: Backend Team Lead  
**승인자**: Tech Lead  

**변경 이력**:
- 2025-01-16: 초안 작성 (v0.7 Final)
- 2025-01-16: Backend 코드 분석 후 업데이트 (v0.7.1)
  - Section 1.1: 현재 코드와 이상적인 코드 구분 추가
  - Section 1.3: 결론 수정 (코드 순서 수정 필요)
  - Phase 2.3: groupSubQuestions() 순서 수정 섹션 추가
  - Phase 3: 테스트 케이스 2개 추가 (우선순위 검증)
  - 체크리스트: groupSubQuestions() 수정 항목 추가
  - 검증 항목: second_question_number 우선순위 확인 추가
- 조사 완료: second_question_number ✅, UNIT 제거 근거 확보 ✅
- 구현 계획 완성: QuestionNumberExtractor + UnifiedAnalysisEngine 수정
