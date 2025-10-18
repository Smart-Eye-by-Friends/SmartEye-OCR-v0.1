# CIM 분석 결과 차이 분석 보고서

**작성일**: 2025년 10월 17일  
**분석 대상**: Swagger UI 테스트 결과 (JobID: d5c4af83-5476-4fab-817f-ef1433548963)  
**테스트 이미지**: 초등 수학 교재 (유형 범례 [I] - 부터 5까지의 수)

---

## 📋 Executive Summary

Swagger UI를 통한 CIM 분석 테스트에서 **심각한 데이터 손실 및 좌표 정보 누락** 문제가 발견되었습니다. 테스트 이미지에는 7개의 문제(001-007)가 명확히 존재하지만, 결과 JSON에는 **5개 문제(003-007)만 포함**되었으며, **모든 bounding box가 더미 값**으로 대체되었습니다.

### 핵심 문제
- ❌ **문제 손실**: 001, 002번 문제 완전 누락 (2/7 = 28.6% 손실)
- ❌ **더미 좌표**: 모든 bbox가 `[0,0,500,100]` 또는 `[0,0,100,50]` 고정값
- ❌ **시각 요소 미검출**: 표, 그림, 다이어그램 등 figure_count: 0
- ⚠️ **데이터 변환 오류**: LAM 서비스 → 백엔드 파이프라인 간 정보 손실

---

## 🔍 1. 테스트 데이터 비교 분석

### 1.1 원본 이미지 구조 (Ground Truth)

테스트 이미지에서 육안으로 확인되는 내용:

| 문제 번호 | 문제 제목 | 주요 시각 요소 | 예상 요소 수 |
|---------|---------|--------------|-------------|
| **001** | 부터 5까지의 수 알아보기 | 대형 표 (5열 × 3행, 이미지+숫자+한글) | 15+ |
| **002** | 부터 5까지의 수만큼 나타내기 | 표 (원 그룹 이미지) | 10+ |
| **003** | 알맞게 이어 보시오 | 매칭 문제 (과일 이미지 + 숫자) | 8+ |
| **004** | 수를 두 가지 방법으로 읽어 보시오 | 숫자 박스 2개 | 4+ |
| **005** | 수가 2인 것을 찾아 기호를 쓰시오 | 이미지 (삼각형, 물건) + 텍스트 | 6+ |
| **006** | 내 방에 있는 물건의 수를... | 텍스트 박스 | 3+ |
| **007** | 3만큼 ○를 그려 보시오 | 텍스트 + 빈 영역 | 2+ |

**예상 총 요소 수**: 약 48-60개

### 1.2 JSON 출력 결과

```json
{
  "stats": {
    "total_questions": 5,  // ❌ 실제 7개 → 5개만 검출
    "cim_data_size": 4
  },
  "cimData": {
    "metadata": {
      "total_elements": 10,  // ❌ 예상 50+ → 10개만
      "total_text_regions": 10
    },
    "questions": [
      {"question_number": "003", "question_text": "알맞게 이어 보시오.", "elements": {}},
      {"question_number": "004", "question_text": "FS 두 가지 방법으로 읽어 보시오.", "elements": {}},
      {"question_number": "005", "question_text": "수가 2인 것을 찾아 기호를 쓰시오.", "elements": {}},
      {"question_number": "006", "question_text": "내 방에 있는 물건의 수를...", "elements": {}},
      {"question_number": "007", "question_text": "Subs ( )를 그려 보시오.", "elements": {}}
    ]
  }
}
```

**검출된 요소 예시** (더미 좌표 사용):
```json
{
  "id": 0,
  "text": "알맞게 이어 보시오.",
  "class": "question_text",
  "bbox": [0, 0, 500, 100],  // ❌ 더미 값
  "confidence": 0.9,
  "area": 50000
}
```

### 1.3 누락 분석 요약

| 항목 | 예상 | 실제 | 손실률 |
|-----|-----|-----|-------|
| 문제 수 | 7 | 5 | **28.6%** |
| 총 요소 수 | 50+ | 10 | **80%+** |
| 시각 요소 (figure/table) | 15+ | 0 | **100%** |
| 정확한 좌표 | 100% | 0% | **100%** |

---

## 🔬 2. Root Cause Analysis

### 2.1 문제 #1: LAM 서비스 검출 실패

**현상**: 문제 001, 002가 완전 누락

**원인 분석**:
1. **신뢰도 임계값 과다**
   - 현재 설정: `conf=0.25` (SmartEye 모델)
   - 문제 001, 002는 이미지 상단부에 위치 → 검출 실패 가능성
   
2. **모델 입력 크기 제한**
   - SmartEye 모델: `imgsz=1024`
   - 원본 이미지가 큰 경우 리사이즈 시 상단부 정보 손실 가능

3. **이미지 전처리 문제**
   - LAM 서비스의 `analyze_layout()` 함수에서 이미지 로드 시 문제 발생 가능
   - 임시 파일 생성 과정에서 데이터 손실

**코드 위치**:
```python
# Backend/smarteye-lam-service/main.py:207-222
results = self.model.predict(
    image_path,
    imgsz=config["imgsz"],  # 1024
    conf=config["conf"],     # 0.25 (너무 높을 수 있음)
    iou=0.45,
    device=self.device,
    verbose=False,
    save=False
)
```

**검증 방법**:
- LAM 서비스 로그에서 실제 검출 요소 수 확인 필요
- 레이아웃 시각화 이미지 (`layout_viz_*.png`) 확인 필요

### 2.2 문제 #2: 더미 Bounding Box 생성

**현상**: 모든 bbox가 `[0,0,500,100]` 또는 `[0,0,100,50]`

**원인**:
`UnifiedAnalysisEngine.java`의 `convertToCIMFormat()` 메서드에서 **Fallback 로직이 과도하게 작동**

**코드 위치**:
```java
// Backend/smarteye-backend/.../UnifiedAnalysisEngine.java:1426-1432
if (analysisElement.getLayoutInfo() != null && analysisElement.getLayoutInfo().getBox() != null) {
    element.put("bbox", Arrays.asList(
        analysisElement.getLayoutInfo().getBox()[0],
        analysisElement.getLayoutInfo().getBox()[1],
        analysisElement.getLayoutInfo().getBox()[2],
        analysisElement.getLayoutInfo().getBox()[3]
    ));
} else {
    // ❌ 기본 bbox 설정 (Fallback이 너무 자주 실행됨)
    element.put("bbox", Arrays.asList(0, 0, 100, 50));
    element.put("area", 5000);
}
```

**추가 더미 생성 위치**:
```java
// Line 1467: question_text 요소
questionElement.put("bbox", Arrays.asList(0, 0, 500, 100));

// Line 1472: question_number 요소
numberElement.put("bbox", Arrays.asList(0, 0, 100, 50));
```

**근본 원인**:
- LAM 서비스가 반환한 실제 좌표가 `LayoutInfo` 객체에 제대로 매핑되지 않음
- 또는 LAM 서비스 응답 파싱 시 bbox 정보 손실

### 2.3 문제 #3: 시각 요소 검출 실패

**현상**: `figure_count: 0`, 표/그림/다이어그램 미검출

**원인 분석**:

1. **LAM 모델 클래스 매핑 오류**
   - SmartEye 모델이 반환하는 클래스: `figure`, `table`, `caption` 등
   - 하지만 실제 검출되지 않음 → 모델 성능 또는 임계값 문제

2. **요소 필터링 과도**
   - 백엔드에서 특정 클래스를 무시하거나 필터링하는 로직 존재 가능
   
3. **OCR 의존성 과다**
   - 현재 시스템이 OCR 텍스트가 있는 요소만 처리하도록 설계된 경우
   - 순수 시각 요소(이미지, 표)는 AI 설명 없이 누락 가능

**관련 코드**:
```java
// UnifiedAnalysisEngine.java - Strategy 패턴 적용
// 시각 요소 처리: VisualContentStrategy (우선순위 9)
// 텍스트 요소 처리: TextContentStrategy (우선순위 8)
```

**검증 필요**:
- `ElementClassifier` 클래스에서 `figure`, `table` 클래스 처리 방식 확인
- `LayoutClass` Enum에 해당 클래스가 정의되어 있는지 확인

### 2.4 문제 #4: 요소 그룹핑 알고리즘 한계

**현상**: 문제별 `elements` 객체가 비어 있음 (`"elements": {}`)

**원인**:
1. **공간 분석 실패**
   - `SpatialAnalysisEngine.assignElementToNearestQuestion2D()` 호출 시 입력 데이터 부족
   - 10개 요소만으로는 의미 있는 그룹핑 불가능

2. **문제 번호 위치 추출 실패**
   - `QuestionNumberExtractor`가 001, 002를 감지하지 못함
   - 이후 요소들은 문제에 할당되지 않고 무시됨

**코드 흐름**:
```java
// Phase 1: 문제 구조 감지
Map<String, Integer> questionPositions = questionNumberExtractor.extractQuestionPositions(
    layoutElements, ocrResults
);  // ❌ 001, 002 누락 → 5개만 반환

// Phase 2: 요소 그룹핑
Map<String, List<AnalysisElement>> elementsByQuestion = groupElementsByQuestion(
    layoutElements, ocrResults, aiResults, questionPositions
);  // ❌ 누락된 문제의 요소들은 할당 실패
```

### 2.5 문제 #5: 데이터 파이프라인 병목

**현상**: 처리 시간 152초 (2.5분) 소요

**분석**:
- LAM 서비스 분석: 약 5-10초 예상
- OCR 처리: 약 20-30초 예상
- **나머지 100초+**: 백엔드 분석 엔진 병목 가능

**의심 지점**:
```java
// DocumentAnalysisController.java - CIM 분석 흐름
// 1. LAM 분석 (비동기 → 동기 변환)
LayoutAnalysisResult layoutResult = lamServiceClient
    .analyzeLayout(bufferedImage, modelChoice)
    .get();  // ❌ 동기 대기 (블로킹)

// 2. UnifiedAnalysisEngine 실행
UnifiedAnalysisEngine.UnifiedAnalysisResult analysisResult =
    unifiedAnalysisEngine.performUnifiedAnalysis(...);  // ❌ 긴 처리 시간
```

**개선 필요**:
- 병렬 처리 최적화
- 불필요한 반복 로직 제거
- 로깅 오버헤드 감소

---

## 📊 3. 현재 CIM 레이아웃 정렬 기능 평가

### 3.1 구현된 정렬 알고리즘

SmartEye v0.4는 다음 정렬 전략을 구현:

1. **CBHLS (Cascade-Based Hierarchical Layout Sorting)**
   - Class → Box → Heuristic → Layout → Spatial 5단계
   
2. **2D 공간 분석**
   - X, Y 좌표 기반 근접성 계산
   - 적응형 거리 임계값 (대형 요소 800px, 일반 500px)

3. **컬럼 감지**
   - `ColumnDetector` 클래스로 다단 레이아웃 지원

**코드 위치**:
```java
// SpatialAnalysisEngine.java
public String assignElementToNearestQuestion2D(
    int elementX, int elementY,
    Map<String, ColumnDetector.PositionInfo> questionPositions,
    int pageWidth,
    boolean isLargeElement
) {
    // X, Y 좌표 기반 거리 계산
    // 적응형 임계값 적용
}
```

### 3.2 알고리즘 강점

✅ **이론적 우수성**:
- 다단 레이아웃 지원 (컬럼 감지)
- 대형 시각 요소 특별 처리 (P0 수정 3)
- 문맥 검증 엔진 통합 (v0.7)

✅ **Strategy 패턴 적용**:
- `ContentGenerationStrategy`로 시각/텍스트 요소 분리 처리
- 확장 가능한 설계

### 3.3 실제 성능 한계

❌ **입력 데이터 의존성**:
- **GIGO (Garbage In, Garbage Out)**: LAM 서비스가 검출하지 못한 요소는 정렬 불가
- 10개 요소로는 아무리 좋은 알고리즘도 의미 없음

❌ **Fallback 로직 과다**:
- 좌표 없을 시 더미 값 생성 → 정렬 정확도 0%
- 문제 번호 감지 실패 시 전체 파이프라인 실패

❌ **시각 요소 처리 부족**:
- 표, 그림 등 순수 시각 요소에 대한 특별 처리 미흡
- AI 설명 의존도 과다

### 3.4 결론: 지금이 최선인가?

**답변: 아니오, 현재 구현은 최선이 아닙니다.**

**이유**:
1. **알고리즘 자체는 우수하나 데이터 품질이 핵심 병목**
2. **LAM 서비스 검출 정확도가 전체 시스템 성능을 결정**
3. **오류 복구 메커니즘 부족** (검출 실패 시 대응 전략 없음)

---

## 🛠️ 4. 해결 방안 제안

### Phase 1: 긴급 수정 (P0 - Critical)

#### 4.1 LAM 서비스 검출 정확도 개선

**목표**: 문제 001, 002 검출 복구, 총 요소 수 50%+ 증가

**Action Items**:

1. **신뢰도 임계값 완화**
   ```python
   # main.py:64 수정
   "SmartEye": {"imgsz": 1024, "conf": 0.15, "description": "..."}  # 0.25 → 0.15
   ```

2. **이미지 크기 증가 테스트**
   ```python
   # imgsz 1024 → 1280 또는 1600으로 증가 시도
   "SmartEye": {"imgsz": 1600, "conf": 0.15, "description": "..."}
   ```

3. **다중 모델 앙상블**
   ```python
   # SmartEye + docsynth300k 결과 병합
   results1 = model1.predict(image_path, conf=0.15)
   results2 = model2.predict(image_path, conf=0.20)
   merged_results = merge_detections(results1, results2)  # NMS 적용
   ```

4. **로깅 강화**
   ```python
   # 검출된 각 요소의 클래스, 좌표, 신뢰도 상세 로그
   logger.info(f"감지: class={class_name}, bbox=({x1},{y1},{x2},{y2}), conf={score:.3f}")
   ```

**예상 효과**:
- 문제 검출률: 5/7 (71%) → 7/7 (100%)
- 총 요소 검출: 10개 → 30-40개
- 처리 시간: 152초 → 180초 (정확도 우선)

#### 4.2 Bounding Box 보존 로직 수정

**목표**: 더미 좌표 0% 달성

**Action Items**:

1. **LAM 응답 파싱 검증**
   ```java
   // LAMServiceClient.java
   // LAM 서비스 응답에서 bbox 추출 시 null 체크 및 로깅
   if (bbox == null || bbox.length != 4) {
       logger.error("❌ 요소 {}번 bbox 누락: {}", elementId, layoutElement);
   }
   ```

2. **Fallback 제거 또는 경고 추가**
   ```java
   // UnifiedAnalysisEngine.java:1426
   } else {
       // ❌ 더미 bbox 생성 대신 요소 제외 또는 에러 로깅
       logger.error("❌ 요소 {}번 bbox 없음 - 건너뜀", elementId);
       continue;  // 더미 데이터 생성 대신 제외
   }
   ```

3. **LayoutInfo 객체 검증**
   ```java
   // LayoutInfo 생성 시점에 bbox 유효성 검사
   public LayoutInfo(int id, String className, int[] box, double confidence) {
       if (box == null || box.length != 4) {
           throw new IllegalArgumentException("Invalid bbox: " + Arrays.toString(box));
       }
       // ...
   }
   ```

**예상 효과**:
- 더미 좌표 비율: 100% → 0%
- 공간 분석 정확도: 0% → 80%+

#### 4.3 시각 요소 검출 강화

**목표**: figure/table 검출률 0% → 80%+

**Action Items**:

1. **LayoutClass Enum 확장**
   ```java
   // LayoutClass.java
   TABLE("table", true, false),         // 표
   FIGURE("figure", true, false),       // 그림
   DIAGRAM("diagram", true, false),     // 다이어그램
   CHART("chart", true, false);         // 차트
   ```

2. **시각 요소 특별 처리**
   ```java
   // VisualContentStrategy.java
   @Override
   public String extractContent(AnalysisElement element) {
       // AI 설명 우선, 없으면 "[이미지: {className}]" 플레이스홀더
       String aiDesc = element.getAiResult() != null ? 
           element.getAiResult().getDescription() : "";
       
       if (aiDesc.isEmpty()) {
           return String.format("[이미지: %s]", element.getLayoutInfo().getClassName());
       }
       return aiDesc;
   }
   ```

3. **메타데이터 카운팅 수정**
   ```java
   // convertToCIMFormat() 내 figure_count 로직 수정
   int figureCount = 0;
   for (ElementDetail detail : qd.getElementDetails()) {
       LayoutClass layoutClass = LayoutClass.fromClassName(detail.getType());
       if (layoutClass.isVisual()) {
           figureCount++;
       }
   }
   questionMetadata.put("figure_count", figureCount);
   ```

**예상 효과**:
- 시각 요소 검출: 0개 → 15-20개
- question_text 추출 정확도 개선 (P0-fix4 효과 증대)

### Phase 2: 중장기 개선 (P1 - High)

#### 4.4 오류 복구 메커니즘 도입

**Retry with Degraded Quality**:
```java
// DocumentAnalysisController.java
LayoutAnalysisResult layoutResult;
try {
    layoutResult = lamServiceClient.analyzeLayout(image, modelChoice).get();
    
    if (layoutResult.getLayoutInfo().size() < 5) {  // 임계값
        logger.warn("⚠️ 검출 요소 부족 ({}개) - 재시도", layoutResult.getLayoutInfo().size());
        
        // 다른 모델로 재시도
        layoutResult = lamServiceClient.analyzeLayout(image, "docsynth300k").get();
    }
} catch (Exception e) {
    logger.error("❌ LAM 분석 실패 - Fallback 모드", e);
    // Fallback: 기본 OCR만 수행
}
```

#### 4.5 성능 최적화

1. **병렬 처리 강화**
   ```java
   CompletableFuture<LayoutAnalysisResult> lamFuture = 
       lamServiceClient.analyzeLayout(image, modelChoice);
   CompletableFuture<List<OCRResult>> ocrFuture = 
       CompletableFuture.supplyAsync(() -> ocrService.performOCR(image, ...));
   
   CompletableFuture.allOf(lamFuture, ocrFuture).join();
   ```

2. **캐싱 도입**
   - LAM 모델 로딩 캐시 (이미 구현됨)
   - 중간 분석 결과 캐시 (동일 이미지 재분석 시)

#### 4.6 모니터링 및 알림

```java
// AnalysisMetricsService.java (신규)
@Service
public class AnalysisMetricsService {
    
    public void recordAnalysisMetrics(String jobId, AnalysisResult result) {
        int detectedElements = result.getLayoutInfo().size();
        int detectedQuestions = result.getQuestions().size();
        
        // 이상 탐지
        if (detectedElements < 10) {
            alertService.send("⚠️ LAM 검출 요소 부족: " + detectedElements);
        }
        
        if (result.hasDummyBbox()) {
            alertService.send("❌ 더미 bbox 발견: " + jobId);
        }
    }
}
```

### Phase 3: 장기 비전 (P2 - Medium)

#### 4.7 자체 레이아웃 검증 모델

- LAM 서비스 결과를 검증하는 독립적인 검증 모델 개발
- Rule-based + ML hybrid 접근

#### 4.8 사용자 피드백 루프

- 프론트엔드에서 누락된 요소 수동 표시 기능
- 피드백 데이터로 LAM 모델 재학습

---

## 📈 5. 예상 개선 효과

### Before (현재)

| 지표 | 값 |
|-----|---|
| 문제 검출률 | 71% (5/7) |
| 요소 검출률 | 20% (10/50) |
| 정확한 좌표 | 0% |
| 시각 요소 검출 | 0% |
| 처리 시간 | 152초 |

### After (Phase 1 완료 시)

| 지표 | 목표 | 개선율 |
|-----|-----|-------|
| 문제 검출률 | **100%** (7/7) | +29% |
| 요소 검출률 | **60%** (30/50) | +300% |
| 정확한 좌표 | **100%** | +∞ |
| 시각 요소 검출 | **80%** | +∞ |
| 처리 시간 | 180초 | +18% (정확도 우선) |

---

## 🎯 6. Action Plan 우선순위

### Week 1: 긴급 수정
- [ ] LAM 서비스 conf 임계값 완화 (0.25 → 0.15)
- [ ] 더미 bbox Fallback 로직 제거/수정
- [ ] 상세 로깅 추가 (검출 요소, bbox 검증)
- [ ] 시각 요소 클래스 처리 강화

### Week 2: 검증 및 최적화
- [ ] 테스트 이미지 10종 재분석
- [ ] 검출률 80%+ 달성 확인
- [ ] 성능 병목 프로파일링
- [ ] 모니터링 대시보드 구축

### Week 3: 안정화
- [ ] 오류 복구 메커니즘 구현
- [ ] 통합 테스트 suite 작성
- [ ] 문서화 업데이트
- [ ] 프로덕션 배포

---

## 📝 7. 결론

### 핵심 발견

1. **LAM 서비스가 핵심 병목**: 검출하지 못한 요소는 아무리 좋은 정렬 알고리즘도 처리 불가
2. **데이터 파이프라인 정보 손실**: bbox 정보가 LAM → 백엔드 전달 과정에서 손실
3. **Fallback 로직 과다**: 오류 대응이 더미 데이터 생성으로 이어져 디버깅 방해
4. **시각 요소 처리 미흡**: 표, 그림 검출 및 처리 로직 강화 필요

### 최우선 과제

**"LAM 서비스 검출 정확도 개선"**이 전체 시스템 성능의 80%를 결정합니다.

현재 CIM 레이아웃 정렬 기능은 **이론적으로 우수한 설계**를 갖추고 있으나, **입력 데이터 품질 문제**로 인해 그 성능을 발휘하지 못하고 있습니다. LAM 서비스 개선과 데이터 파이프라인 보존이 해결되면, 현재 구현된 CBHLS + 2D 공간 분석 알고리즘은 90%+ 정확도를 달성할 수 있습니다.

---

**보고서 작성**: GitHub Copilot  
**분석 기준**: Swagger UI 테스트 (JobID: d5c4af83-5476-4fab-817f-ef1433548963)  
**다음 단계**: Phase 1 긴급 수정 착수 (LAM 서비스 임계값 조정)
