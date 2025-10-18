# CIM 기능 문제 근본 원인 분석 보고서

**작성일**: 2025-01-16  
**분석 대상**: SmartEye v0.4 Backend - CIM (Circuit Integration Management) 시스템  
**테스트 환경**: Swagger UI Test (2025-01-16 17:28:10)  
**심각도**: 🔴 Critical - 핵심 기능 장애

---

## Executive Summary (요약)

Swagger UI 테스트 결과 CIM 분석 기능에서 **2가지 치명적 데이터 손실 문제**가 발견되었습니다:

1. **Bbox 좌표 손실** - LAM 서비스가 반환한 실제 좌표가 더미 값(`[0,0,500,100]`)으로 대체됨
2. **AI 설명 누락** - 생성된 6개의 figure 설명이 최종 JSON에서 완전히 누락됨
3. ~~**OCR 순서 오류**~~ - **FALSE POSITIVE** (재분석 결과 문제 없음, 2단 칼럼 구조 정상 인식)

**근본 원인**: v3.0 리팩토링 과정에서 `ContentElement` 클래스 설계 결함 및 `IntegratedCIMProcessor`와의 필드 불일치

**영향 범위**:
- ✅ LAM 서비스: 정상 (29 elements 감지, 실제 좌표 반환)
- ✅ OCR 서비스: 정상 (16 텍스트 추출)
- ✅ AI 서비스: 정상 (6 figure 설명 생성)
- ❌ 데이터 변환 파이프라인: **심각한 데이터 손실**

---

## 1. 문제 상세 분석

### 1.1 Issue #1: Bbox 좌표 더미 값 문제

#### 증상
```json
// 기대값 (LAM 원시 응답)
{
  "bbox": [320, 757, 1667, 877],  // 실제 좌표
  "class": "figure"
}

// 실제 출력 (response_1760678910265.json)
{
  "bbox": [0, 0, 500, 100],  // 더미 값
  "class": "question_text"
}
```

#### 로그 증거
```
[backend_swagger_test.log:27292]
LAM 서비스 원시 응답: 29개 요소
- Element 5: class=figure, bbox=[320, 757, 1667, 877], conf=0.915
```

#### 근본 원인

**파일**: `UnifiedAnalysisEngine.java`  
**클래스 정의** (Line 2066-2083):
```java
public static class ContentElement {
    private String type;        // className만
    private String content;     // OCR 텍스트 OR AI 설명
    // ❌ bbox 필드 없음!
    // ❌ confidence 필드 없음!
}
```

**데이터 손실 지점** (Line 943-990):
```java
private List<ContentElement> buildElements(List<AnalysisElement> sortedElements) {
    for (AnalysisElement element : sortedElements) {
        String className = element.getLayoutInfo().getClassName();
        String content = extractContentForElement(element, className);
        
        // ❌ bbox, confidence 정보를 버림!
        ContentElement contentElement = new ContentElement(className, content);
        elements.add(contentElement);
    }
}
```

**더미 값 생성** (Line 1428, 1464, 1476):
```java
private Map<String, Object> convertToCIMFormat(StructuredData structuredData) {
    // ...
    if (analysisElement.getLayoutInfo() != null && analysisElement.getLayoutInfo().getBox() != null) {
        element.put("bbox", Arrays.asList(...));  // 실제 좌표
    } else {
        element.put("bbox", Arrays.asList(0, 0, 100, 50));  // ❌ 더미 값!
    }
}
```

#### 심각도: 🔴 High
- 프론트엔드에서 요소 위치 시각화 불가능
- 레이아웃 분석 결과 활용 불가

---

### 1.2 Issue #2: AI 설명 완전 누락 문제

#### 증상
```json
// 로그에서 확인됨
{
  "figure": "이 그림은 다양한 견과류와 씨앗을 보여주는 이미지입니다. (194 chars)"
}

// 최종 JSON
{
  "questions": [
    {
      "question_number": "003",
      "elements": {}  // ❌ 비어있음!
    }
  ]
}
```

#### 로그 증거
```
[backend_swagger_test.log:27810-27830]
AI 설명 생성: Element 5 (figure)
→ "이 그림은 다양한 견과류와 씨앗을 보여주는..." (194 chars)

[backend_swagger_test.log:27929]
📝 레거시 콘텐츠: 2 개 필드
  - question_text: 11 chars
  - figure: 194 chars  ← AI 설명 존재!

[response_1760678910265.json]
"elements": {}  ← 비어있음!
```

#### 근본 원인

**파일**: `IntegratedCIMProcessor.java`  
**필드 불일치** (Line 343-376):
```java
private List<QuestionGroup> convertToQuestionGroups(
        UnifiedAnalysisEngine.StructuredData structuredData) {
    
    for (var question : structuredData.getQuestions()) {
        QuestionGroup group = new QuestionGroup();
        
        // ✅ Phase 4: elementDetails 확인
        if (question.getElementDetails() != null && !question.getElementDetails().isEmpty()) {
            List<ProcessedElement> elements = question.getElementDetails().stream()
                .map(this::convertElementDetailToProcessedElement)
                .collect(Collectors.toList());
            group.setElements(elements);
        } 
        // ✅ Fallback: 기존 elements 확인
        else if (question.getElements() != null) {
            List<ProcessedElement> elements = question.getElements().entrySet().stream()
                .flatMap(entry -> entry.getValue().stream())
                .map(this::convertToProcessedElement)
                .collect(Collectors.toList());
            group.setElements(elements);
        }
        // ❌ question.getContentElements() 확인 안 함!
    }
}
```

**데이터 설정** (UnifiedAnalysisEngine.java Line 567):
```java
QuestionData qd = new QuestionData();
qd.setContentElements(contentElements);  // ✅ v3.0 필드 설정
qd.setElements(Map.of("main", elements));  // ✅ 레거시 필드 설정
// ❌ qd.setElementDetails() 호출 안 함!
```

**결과**: IntegratedCIMProcessor가 `contentElements`를 확인하지 않으므로, `group.setElements(null)` 상태가 됨.

#### 데이터 파이프라인 흐름
```
LAM (29 elements)
  ↓
UnifiedAnalysisEngine.buildElements()
  → ContentElement 생성 (8개, AI 설명 포함)
    ↓
  qd.setContentElements(contentElements) ✅
  qd.setElements(Map.of(...)) ✅
  qd.setElementDetails(???) ❌ 호출 안 함!
    ↓
IntegratedCIMProcessor.convertToQuestionGroups()
  → question.getElementDetails() 확인 → null ❌
  → question.getElements() 확인 → Map 존재 ✅ (하지만 IntegratedCIMProcessor가 기대하는 구조와 다름)
  → question.getContentElements() 확인 안 함! ❌
    ↓
  group.setElements(null)
    ↓
JsonUtils.convertStructuredResultToCIM()
  → question.getContentElements() → 비어있음!
    ↓
  elements: {} 빈 객체 반환
```

#### 심각도: 🔴 Critical
- AI 설명 기능 완전 실패
- 핵심 비즈니스 가치 손실

---

### 1.3 Issue #3: OCR 순서 - 실제로는 문제 없음 (FALSE POSITIVE)

#### 재분석 결과

**초기 오해**: Y좌표만으로 순서를 판단하여 잘못된 문제로 보고함

**실제 상황**: 
- 문서는 **2단 칼럼 레이아웃** (왼쪽 칼럼 X≈300-450, 오른쪽 칼럼 X≈1800-1950)
- LAM이 감지한 실제 좌표:
  ```
  ID 21 (005): X=1823, Y=515   (오른쪽 상단)
  ID 14 (006): X=1817, Y=1589  (오른쪽 중간)
  ID 17 (003): X=317,  Y=1710  (왼쪽 중간)
  ID 16 (004): X=312,  Y=3429  (왼쪽 하단)
  ID 20 (007): X=1811, Y=3695  (오른쪽 하단)
  ```

**올바른 읽기 순서** (칼럼 기반):
1. 왼쪽 칼럼: 003 (Y=1710) → 004 (Y=3429)
2. 오른쪽 칼럼: 005 (Y=515) → 006 (Y=1589) → 007 (Y=3695)

**JSON 출력 순서**: 003, 004, 005, 006, 007 ✅ **올바름!**

#### 결론
- ✅ **문제 없음** - UnifiedAnalysisEngine이 칼럼을 올바르게 인식
- ✅ 문제 번호 순서가 공간적 읽기 순서와 일치함
- ❌ **이 이슈는 삭제 대상** (잘못된 분석)

#### 심각도: ⚪ N/A (False Positive)
- 실제 문제 없음

---

## 2. 아키텍처 설계 결함 분석

### 2.1 v3.0 리팩토링 불완전성

**도입된 변경사항**:
- `ContentElement` 클래스 추가 (간소화된 구조)
- `QuestionData.contentElements` 필드 추가
- 기존 `elements`, `elementDetails` 필드 유지 (하위 호환성)

**문제점**:
1. **3가지 필드 중복**:
   ```java
   public static class QuestionData {
       private Map<String, List<AnalysisElement>> elements;  // 레거시
       private List<ElementDetail> elementDetails;  // Phase 1
       private List<ContentElement> contentElements;  // v3.0
       // ❌ 일관성 없음!
   }
   ```

2. **ContentElement 설계 과도 간소화**:
   ```java
   public static class ContentElement {
       private String type;     // className만
       private String content;  // OCR OR AI (둘 중 하나!)
       // ❌ bbox, confidence, layoutInfo 없음
   }
   ```

3. **파이프라인 불일치**:
   - `UnifiedAnalysisEngine`: `contentElements` 설정 ✅
   - `IntegratedCIMProcessor`: `contentElements` 확인 안 함 ❌
   - `JsonUtils`: `contentElements` 사용 시도 → 비어있음 ❌

### 2.2 데이터 손실 지점 요약

| 단계 | 입력 | 출력 | 손실 데이터 |
|------|------|------|------------|
| LAM Service | 이미지 | 29 elements (bbox 포함) | - |
| UnifiedAnalysisEngine.buildElements() | AnalysisElement (bbox, OCR, AI) | ContentElement (type, content) | ✅ bbox, confidence |
| IntegratedCIMProcessor.convertToQuestionGroups() | QuestionData (contentElements 설정됨) | QuestionGroup (elements=null) | ✅ 모든 contentElements |
| JsonUtils.convertStructuredResultToCIM() | QuestionData (contentElements 비어있음) | JSON (elements: {}) | ✅ AI 설명, OCR 텍스트 |

---

## 3. 해결 방안

### 3.1 단기 해결 (Hotfix) - P0 긴급

**파일**: `IntegratedCIMProcessor.java`  
**메서드**: `convertToQuestionGroups()` (Line 343)

```java
private List<QuestionGroup> convertToQuestionGroups(
        UnifiedAnalysisEngine.StructuredData structuredData) {
    
    List<QuestionGroup> questionGroups = new ArrayList<>();

    if (structuredData.getQuestions() != null) {
        for (var question : structuredData.getQuestions()) {
            QuestionGroup group = new QuestionGroup();
            group.setQuestionNumber(question.getQuestionNumber());
            group.setQuestionText(question.getQuestionText() != null ?
                question.getQuestionText() : "문제 텍스트 추출 중...");

            // 🆕 FIX: contentElements 우선 확인 (v3.0)
            if (question.getContentElements() != null && !question.getContentElements().isEmpty()) {
                List<ProcessedElement> elements = question.getContentElements().stream()
                    .map(this::convertContentElementToProcessedElement)
                    .collect(Collectors.toList());
                group.setElements(elements);
            } 
            // ✅ Phase 4: elementDetails 확인 (기존 로직)
            else if (question.getElementDetails() != null && !question.getElementDetails().isEmpty()) {
                List<ProcessedElement> elements = question.getElementDetails().stream()
                    .map(this::convertElementDetailToProcessedElement)
                    .collect(Collectors.toList());
                group.setElements(elements);
            } 
            // ✅ Fallback: 기존 elements
            else if (question.getElements() != null) {
                List<ProcessedElement> elements = question.getElements().entrySet().stream()
                    .flatMap(entry -> entry.getValue().stream())
                    .map(this::convertToProcessedElement)
                    .collect(Collectors.toList());
                group.setElements(elements);
            }

            questionGroups.add(group);
        }
    }

    return questionGroups;
}

// 🆕 새 변환 메서드 추가
private ProcessedElement convertContentElementToProcessedElement(
        UnifiedAnalysisEngine.ContentElement contentElement) {
    
    ProcessedElement pe = new ProcessedElement();
    
    // LayoutInfo 생성 (간소화된 정보)
    LayoutInfo layoutInfo = new LayoutInfo();
    layoutInfo.setClassName(contentElement.getType());
    // ❌ ContentElement에 bbox가 없으므로 더미 값 사용
    layoutInfo.setBox(new int[]{0, 0, 100, 50});
    layoutInfo.setConfidence(0.8);
    pe.setLayoutInfo(layoutInfo);
    
    // Content 설정 (OCR 또는 AI 중 하나)
    if (contentElement.getContent() != null) {
        // 시각 요소인지 판단
        if (isVisualElement(contentElement.getType())) {
            AIDescriptionResult aiResult = new AIDescriptionResult();
            aiResult.setDescription(contentElement.getContent());
            pe.setAiResult(aiResult);
        } else {
            OCRResult ocrResult = new OCRResult();
            ocrResult.setText(contentElement.getContent());
            pe.setOcrResult(ocrResult);
        }
    }
    
    pe.setCategory(contentElement.getType());
    
    return pe;
}

private boolean isVisualElement(String type) {
    return "figure".equals(type) || 
           "table".equals(type) || 
           "flowchart".equals(type) ||
           "equation".equals(type);
}
```

**제약사항**:
- ❌ Bbox 정보는 여전히 복구 불가 (ContentElement에 필드 없음)
- ✅ AI 설명은 복구 가능 (content 필드에 저장되어 있음)

---

### 3.2 중기 해결 (Refactoring) - P1 중요

**파일**: `UnifiedAnalysisEngine.java`  
**클래스**: `ContentElement` (Line 2066)

```java
public static class ContentElement {
    private String type;        // className (기존)
    private String content;     // OCR 또는 AI (기존, 하위 호환성)
    
    // 🆕 v3.1: 상세 정보 추가
    private int[] bbox;         // [x1, y1, x2, y2]
    private double confidence;  // LAM 신뢰도
    private String ocrText;     // OCR 텍스트 (content와 별도)
    private String aiDescription;  // AI 설명 (content와 별도)
    
    public ContentElement() {}
    
    public ContentElement(String type, String content) {
        this.type = type;
        this.content = content;
    }
    
    // Getters/Setters
    public int[] getBbox() { return bbox; }
    public void setBbox(int[] bbox) { this.bbox = bbox; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getOcrText() { return ocrText; }
    public void setOcrText(String ocrText) { this.ocrText = ocrText; }
    public String getAiDescription() { return aiDescription; }
    public void setAiDescription(String aiDescription) { this.aiDescription = aiDescription; }
    
    // 기존 필드
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
```

**파일**: `UnifiedAnalysisEngine.java`  
**메서드**: `buildElements()` (Line 985)

```java
ContentElement contentElement = new ContentElement();
contentElement.setType(className);

// 🆕 Bbox 정보 보존
if (element.getLayoutInfo() != null && element.getLayoutInfo().getBox() != null) {
    contentElement.setBbox(element.getLayoutInfo().getBox());
    contentElement.setConfidence(element.getLayoutInfo().getConfidence());
}

// 🆕 OCR과 AI 설명 모두 보존
if (element.getOcrResult() != null && element.getOcrResult().getText() != null) {
    contentElement.setOcrText(element.getOcrResult().getText());
}
if (element.getAiResult() != null && element.getAiResult().getDescription() != null) {
    contentElement.setAiDescription(element.getAiResult().getDescription());
}

// 기존 content 필드 (하위 호환성)
String legacyContent = extractContentForElement(element, className);
contentElement.setContent(legacyContent);

elements.add(contentElement);
```

**IntegratedCIMProcessor 업데이트**:
```java
private ProcessedElement convertContentElementToProcessedElement(
        UnifiedAnalysisEngine.ContentElement ce) {
    
    ProcessedElement pe = new ProcessedElement();
    
    // ✅ 실제 bbox 복원
    LayoutInfo layoutInfo = new LayoutInfo();
    layoutInfo.setClassName(ce.getType());
    if (ce.getBbox() != null) {
        layoutInfo.setBox(ce.getBbox());
        layoutInfo.setConfidence(ce.getConfidence());
    }
    pe.setLayoutInfo(layoutInfo);
    
    // ✅ OCR 설정
    if (ce.getOcrText() != null) {
        OCRResult ocrResult = new OCRResult();
        ocrResult.setText(ce.getOcrText());
        pe.setOcrResult(ocrResult);
    }
    
    // ✅ AI 설명 설정
    if (ce.getAiDescription() != null) {
        AIDescriptionResult aiResult = new AIDescriptionResult();
        aiResult.setDescription(ce.getAiDescription());
        pe.setAiResult(aiResult);
    }
    
    pe.setCategory(ce.getType());
    
    return pe;
}
```

**효과**:
- ✅ Bbox 좌표 완전 복구
- ✅ AI 설명 완전 복구
- ✅ OCR 텍스트와 AI 설명 동시 저장 가능

---

### 3.3 ~~장기 해결 (OCR 순서 수정)~~ - 삭제됨 (FALSE POSITIVE)

**재분석 결과**: OCR 순서 문제는 실제로 존재하지 않음. 2단 칼럼 구조를 올바르게 인식하여 정상 동작 중.

---

## 4. 구현 계획

### 4.1 Phase 1: 긴급 Hotfix (1-2일)

**목표**: AI 설명 복구 (Critical 문제 해결)

**작업 항목**:
1. ✅ `IntegratedCIMProcessor.convertToQuestionGroups()` 수정
   - `question.getContentElements()` 확인 추가
   - `convertContentElementToProcessedElement()` 메서드 추가

2. ✅ 단위 테스트 작성
   ```java
   @Test
   void testContentElementsConversion() {
       // Given: ContentElement with AI description
       ContentElement ce = new ContentElement("figure", "AI 설명 텍스트");
       
       // When: ProcessedElement로 변환
       ProcessedElement pe = processor.convertContentElementToProcessedElement(ce);
       
       // Then: AI 설명 보존 확인
       assertNotNull(pe.getAiResult());
       assertEquals("AI 설명 텍스트", pe.getAiResult().getDescription());
   }
   ```

3. ✅ Swagger UI 검증
   - AI 설명이 JSON에 포함되는지 확인
   - elements: {} 빈 객체 해결 확인

**제약사항**:
- Bbox 문제는 미해결 (P1에서 해결)

---

### 4.2 Phase 2: ContentElement 확장 (3-5일)

**목표**: Bbox 좌표 복구 및 완전한 데이터 보존

---

## 5. 테스트 시나리오

### 5.1 단위 테스트

```java
@Test
void testContentElementPreservesBbox() {
    // Given
    AnalysisElement element = new AnalysisElement();
    element.setLayoutInfo(new LayoutInfo("figure", new int[]{100, 200, 300, 400}, 0.95));
    
    // When
    ContentElement ce = buildElements(List.of(element)).get(0);
    
    // Then
    assertNotNull(ce.getBbox());
    assertArrayEquals(new int[]{100, 200, 300, 400}, ce.getBbox());
    assertEquals(0.95, ce.getConfidence(), 0.01);
}

@Test
void testContentElementPreservesOCRandAI() {
    // Given
    AnalysisElement element = new AnalysisElement();
    element.setOcrResult(new OCRResult("그림 1"));
    element.setAiResult(new AIDescriptionResult("견과류 이미지"));
    
    // When
    ContentElement ce = buildElements(List.of(element)).get(0);
    
    // Then
    assertEquals("그림 1", ce.getOcrText());
    assertEquals("견과류 이미지", ce.getAiDescription());
}
```

### 5.2 통합 테스트

```java
@Test
void testCIMPipelineDataIntegrity() {
    // Given: LAM 원시 응답 시뮬레이션
    List<LayoutInfo> layouts = createTestLayouts();  // 29 elements
    List<OCRResult> ocrs = createTestOCRs();  // 16 texts
    List<AIDescriptionResult> ais = createTestAIs();  // 6 descriptions
    
    // When: 전체 파이프라인 실행
    UnifiedAnalysisResult result = analysisEngine.analyze(layouts, ocrs, ais);
    Map<String, Object> cimJson = JsonUtils.convertStructuredResultToCIM(result.getStructuredData());
    
    // Then: 데이터 손실 없음 확인
    List<Map<String, Object>> questions = (List) cimJson.get("questions");
    assertFalse(questions.isEmpty());
    
    for (Map<String, Object> question : questions) {
        Map<String, Object> elements = (Map) question.get("elements");
        assertNotNull(elements);
        assertFalse(elements.isEmpty());  // ❌ 현재 실패
        
        // Bbox 실제 좌표 확인
        List<Integer> bbox = (List) elements.get("bbox");
        assertNotEquals(List.of(0, 0, 500, 100), bbox);  // 더미 값 아님
    }
}
```

### 5.3 Swagger UI 검증 체크리스트

- [ ] AI 설명이 `elements` 내부에 포함됨
- [ ] Bbox 좌표가 실제 값 (더미 값 아님)
- [ ] OCR 텍스트와 AI 설명 모두 존재
- [ ] 요소 순서가 Y좌표 기반 (번호 순 아님)
- [ ] 총 요소 개수 일치 (LAM 29개 → 필터링 8개)

---

## 6. 리스크 및 완화 방안

### 6.1 하위 호환성 문제

**리스크**: 기존 클라이언트가 새 JSON 구조를 처리 못 할 수 있음

**완화**:
- `content` 필드 유지 (하위 호환성)
- 새 필드(`ocrText`, `aiDescription`) 추가 (optional)
- API 버전 관리 (v3.1)

### 6.2 성능 영향

**리스크**: 추가 필드로 인한 JSON 크기 증가

**측정**:
- 기존: ~10KB
- 예상: ~15KB (+50%)

**완화**:
- 필요 시 gzip 압축
- 페이지네이션 고려

### 6.3 테스트 커버리지

**리스크**: 모든 엣지 케이스 테스트 어려움

**완화**:
- 단위 테스트 + 통합 테스트 작성
- Swagger UI 실제 데이터 테스트
- 로깅 강화 (데이터 손실 지점 추적)

---

## 7. 결론 및 권장사항

### 7.1 즉시 조치 사항 (P0)

1. ✅ **IntegratedCIMProcessor 수정** (1일)
   - AI 설명 복구
   - Critical 문제 해결

2. ✅ **긴급 배포**
   - Hotfix 브랜치 생성
   - QA 검증 후 프로덕션 배포

### 7.2 중기 계획 (P1)

1. ✅ **ContentElement 확장** (3-5일)
   - 모든 메타데이터 보존
   - 완전한 데이터 무결성

2. ✅ **통합 테스트 강화**
   - 전체 파이프라인 검증

### 7.3 장기 비전 (P2)

1. ✅ **아키텍처 리팩토링**
   - 중복 필드 제거 (`elements`, `elementDetails`, `contentElements` 통합)
   - 단일 데이터 모델

2. ✅ **문서화**
   - API 스펙 업데이트
   - 개발자 가이드 작성

---

## 8. 참고 자료

### 8.1 관련 파일

- `Backend/smarteye-backend/src/main/java/com/smarteye/application/analysis/UnifiedAnalysisEngine.java`
  - Line 2066: ContentElement 클래스
  - Line 943: buildElements() 메서드
  - Line 1392: convertToCIMFormat() 메서드

- `Backend/smarteye-backend/src/main/java/com/smarteye/application/analysis/IntegratedCIMProcessor.java`
  - Line 343: convertToQuestionGroups() 메서드

- `Backend/smarteye-backend/src/main/java/com/smarteye/shared/util/JsonUtils.java`
  - Line 238: convertStructuredResultToCIM() 메서드

### 8.2 로그 파일

- `backend_swagger_test.log` (30,375 lines)
  - Line 27292: LAM 원시 응답
  - Line 27810-27830: AI 설명 생성
  - Line 27929: ContentElement 생성 완료

### 8.3 테스트 결과

- `response_1760678910265.json`
  - 최종 CIM JSON 출력
  - elements: {} 빈 객체 문제 확인

---

**작성자**: GitHub Copilot AI Agent  
**검토**: Backend 팀 리뷰 필요  
**승인**: Tech Lead 승인 후 구현 시작
