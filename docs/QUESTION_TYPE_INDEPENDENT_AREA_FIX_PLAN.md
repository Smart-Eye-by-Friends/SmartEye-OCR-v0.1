# Question Type 독립 영역 처리 구현 계획

## 📋 Executive Summary

**문제:** `question_type`이 현재 문제 경계 감지에만 사용되고, JSON 출력에 독립적인 문제 영역으로 표시되지 않음

**목표:** `question_type`을 `question_number`처럼 독립적인 문제 영역으로 처리하여 주변 요소를 할당받고 JSON에 출력

**영향 범위:**
- `QuestionNumberExtractor.java` - 문제 경계 클래스 필터링 로직
- `SpatialAnalysisEngine.java` - 요소 할당 및 그룹화 로직
- JSON 출력 DTO - `question_type` 필드 추가

---

## 🔍 현재 구현 분석

### 1. QuestionNumberExtractor.java (Lines 171-182)

**현재 코드:**
```java
// QUESTION_TYPE 또는 UNIT의 경우 특별 처리
if (cls == LayoutClass.QUESTION_TYPE || cls == LayoutClass.UNIT) {
    // 유형/단원 정보는 문제 번호가 아니므로 메타데이터로 저장
    logger.debug("📌 {} 감지: '{}' (LAM conf={})", 
               cls == LayoutClass.QUESTION_TYPE ? "문제 유형" : "단원",
               ocrText,
               String.format("%.3f", lamConfidence));
    
    // TODO: 유형/단원 정보를 별도로 저장하는 로직 추가 필요
    // 현재는 로깅만 하고 문제 번호 추출은 스킵
    continue;  // ❌ 문제점: question_type을 버림
}
```

**문제점:**
1. `question_type`과 `UNIT`이 `continue`로 스킵됨
2. `questionPositions` 맵에 포함되지 않음 (컬럼 감지용으로만 사용)
3. JSON 출력에 표시 안 됨

### 2. Lines 142-158 (경계 클래스 체크)

**현재 코드:**
```java
// 경계 클래스 체크: QUESTION_NUMBER, QUESTION_TYPE, UNIT 모두 허용
boolean isBoundaryClass = (
    cls == LayoutClass.QUESTION_NUMBER ||
    cls == LayoutClass.QUESTION_TYPE ||
    cls == LayoutClass.UNIT  // ❌ UNIT 제거 필요
);
```

**문제점:**
1. `UNIT`이 포함되어 있음 (요구사항: 제외해야 함)
2. 주석이 현재 요구사항과 맞지 않음

---

## 🎯 요구사항 정리

### 변경 사항

| 항목 | 기존 | 변경 후 |
|------|------|---------|
| `QUESTION_NUMBER` | 독립 문제 영역 생성 ✅ | 유지 |
| `QUESTION_TYPE` | 컬럼 감지용만 사용 ❌ | 독립 문제 영역 생성 ✅ |
| `UNIT` | 문제 경계로 포함 ❌ | 제외 (무시) |

### 기대 동작

1. **`question_type` 처리:**
   - LAM이 `question_type` 감지 시 → `questionPositions`에 추가
   - 주변 요소들을 해당 `question_type` 영역에 할당
   - JSON 출력에 독립 항목으로 표시
   - 예: `"question_type_001": { "elements": [...], "text": "유형 01" }`

2. **`UNIT` 제거:**
   - LAM이 `UNIT` 감지 시 → 무시 (로깅만)
   - 문제 경계에서 제외
   - 컬럼 감지에서도 제외

---

## 🔧 수정 계획

### Phase 1: QuestionNumberExtractor 수정

#### 1.1 경계 클래스 필터링 수정 (Lines 142-158)

**Before:**
```java
boolean isBoundaryClass = (
    cls == LayoutClass.QUESTION_NUMBER ||
    cls == LayoutClass.QUESTION_TYPE ||
    cls == LayoutClass.UNIT
);
```

**After:**
```java
// 문제 경계 클래스: QUESTION_NUMBER, QUESTION_TYPE만 허용
boolean isBoundaryClass = (
    cls == LayoutClass.QUESTION_NUMBER ||
    cls == LayoutClass.QUESTION_TYPE
);
```

#### 1.2 question_type 처리 로직 수정 (Lines 171-182)

**Before:**
```java
// QUESTION_TYPE 또는 UNIT의 경우 특별 처리
if (cls == LayoutClass.QUESTION_TYPE || cls == LayoutClass.UNIT) {
    // TODO: 유형/단원 정보를 별도로 저장하는 로직 추가 필요
    continue;  // ❌ 스킵
}
```

**After:**
```java
// UNIT은 제외 (문제 경계가 아님)
if (cls == LayoutClass.UNIT) {
    logger.debug("📌 단원 정보 감지 (문제 경계 아님): '{}' (LAM conf={})", 
               ocrText, String.format("%.3f", lamConfidence));
    continue;
}

// QUESTION_TYPE은 question_number처럼 처리
String questionIdentifier;
if (cls == LayoutClass.QUESTION_TYPE) {
    // question_type은 OCR 텍스트를 그대로 사용 (패턴 매칭 불필요)
    questionIdentifier = "type_" + ocrText.replaceAll("[^가-힣a-zA-Z0-9]", "_");
    logger.debug("📌 문제 유형 감지: '{}' → ID: '{}'", ocrText, questionIdentifier);
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

#### 1.3 후보 등록 로직 수정 (Lines 206-220)

**Before:**
```java
String questionNum = patternMatchingEngine.extractQuestionNumber(ocrText);
// ...
QuestionCandidate candidate = new QuestionCandidate(
    questionNum, yCoordinate, confidenceScore, "LAM+OCR"
);
```

**After:**
```java
// questionIdentifier는 위에서 이미 결정됨 (question_type 또는 question_number)
QuestionCandidate candidate = new QuestionCandidate(
    questionIdentifier, yCoordinate, confidenceScore, "LAM+OCR"
);
```

### Phase 2: 데이터 구조 확장 (필요 시)

현재 `Map<String, Integer>` 반환값이 `question_type`도 처리 가능한지 확인:
- Key: `"003"` (question_number) 또는 `"type_유형_01"` (question_type)
- Value: Y 좌표

→ **추가 수정 불필요** (String 키로 이미 유연함)

### Phase 3: JSON 출력 검증

`SpatialAnalysisEngine`과 CIM 서비스가 `type_*` 식별자를 올바르게 처리하는지 확인:
- 요소 할당 로직
- JSON 직렬화
- 프론트엔드 표시

---

## ✅ 검증 계획

### 테스트 시나리오

1. **QUESTION_TYPE 독립 영역 생성:**
   - Input: LAM 결과에 `question_type` 포함
   - Expected: `questionPositions`에 `"type_유형_01"` 같은 키로 등록
   - Expected: JSON 출력에 해당 영역 표시

2. **UNIT 제외:**
   - Input: LAM 결과에 `UNIT` 포함
   - Expected: 로그에만 기록, `questionPositions`에 미포함

3. **컬럼 감지 영향:**
   - Input: 2열 레이아웃 + question_type + question_number
   - Expected: question_type의 X 좌표도 컬럼 경계 감지에 사용

### 검증 방법

```bash
# 1. 백엔드 재빌드
cd Backend/smarteye-backend
./gradlew clean build

# 2. Swagger UI 테스트
# POST /api/analysis/unified-analysis
# - 이미지: question_type 포함 페이지
# - 응답 JSON에서 "type_*" 항목 확인

# 3. 로그 확인
tail -f logs/application.log | grep -E "(문제 유형|단원|question_type)"
```

---

## 📊 예상 결과

### Before (현재)
```json
{
  "questions": {
    "003": { "elements": [...] },
    "004": { "elements": [...] }
  }
  // question_type은 없음 ❌
}
```

### After (수정 후)
```json
{
  "questions": {
    "003": { "elements": [...] },
    "type_유형_01": { "elements": [...] },  // ✅ 추가됨
    "004": { "elements": [...] }
  }
}
```

---

## 🚀 구현 순서

1. ✅ **계획 수립** (현재 문서)
2. ⏳ **Phase 1 구현** - QuestionNumberExtractor 수정
3. ⏳ **빌드 및 단위 테스트**
4. ⏳ **통합 테스트** - Swagger UI
5. ⏳ **검증 및 문서화**

---

## 📝 변경 이력

- 2025-10-17: 초안 작성 - question_type 독립 영역 처리 계획
