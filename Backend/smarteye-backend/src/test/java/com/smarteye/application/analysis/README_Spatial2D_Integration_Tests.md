# 2D 공간 분석 통합 테스트 문서

## 📋 개요

**파일**: `Spatial2DIntegrationTest.java`
**목적**: CBHLS Phase 2 - 2D 공간 분석 기능의 통합 검증
**Feature Flag**: `smarteye.features.use-2d-spatial-analysis=true`
**작성일**: 2025-10-01

## 🎯 테스트 범위

### 핵심 검증 목표
1. ✅ **교차 컬럼 할당 방지** - 다단 레이아웃에서 요소가 잘못된 컬럼의 문제에 할당되지 않음
2. ✅ **비대칭 레이아웃 처리** - 컬럼 높이가 다를 때도 정확한 할당
3. ✅ **다중 컬럼 감지** - 3단 이상의 복잡한 레이아웃 처리
4. ✅ **걸친 요소 처리** - 여러 컬럼에 걸친 이미지/표의 중심점 기반 할당
5. ✅ **1D Fallback** - 컬럼 감지 실패 시 안전한 Y좌표 기반 할당

## 🧪 테스트 시나리오

### 테스트 1: 표준 2단 신문 레이아웃

```
Layout:
┌─────────────────────────────────────┐
│ [Q1]    [Q4]                        │
│ text1   text4                       │
│                                     │
│ [Q2]    [Q5]                        │
│ text2   text5                       │
│                                     │
│ [Q3]    [Q6]                        │
│ text3   text6                       │
└─────────────────────────────────────┘
```

**검증 포인트**:
- ✅ 왼쪽 컬럼 문제(1,2,3)는 왼쪽 요소만 포함
- ✅ 오른쪽 컬럼 문제(4,5,6)는 오른쪽 요소만 포함
- ✅ 교차 할당 없음 (Q1 ≠ text4, Q4 ≠ text1)

**데이터**:
- 문제 위치: 왼쪽(X=100), 오른쪽(X=600)
- Y좌표: 100, 300, 500 (각 컬럼)
- 요소: 각 문제 아래 50px에 텍스트

### 테스트 2: 비대칭 2단 레이아웃

```
Layout:
┌─────────────────────────────────────┐
│ [Q1]    [Q3]                        │
│ text_L  text_R (경계 테스트)         │
│                                     │
│ [Q2]    [Q4]                        │
│         [Q5]                        │
│         [Q6]                        │
└─────────────────────────────────────┘
```

**검증 포인트**:
- ✅ 왼쪽 짧은 컬럼 (Q1, Q2)
- ✅ 오른쪽 긴 컬럼 (Q3, Q4, Q5, Q6)
- ✅ 컬럼 경계 근처 요소 올바른 할당

**데이터**:
- 왼쪽: X=100, Y=100,400
- 오른쪽: X=600, Y=100,300,500,700
- 경계 요소: X=120 (왼쪽), X=580 (오른쪽)

### 테스트 3: 3단 레이아웃 (넓은 중앙)

```
Layout:
┌──────────────────────────────────────┐
│       │         │                    │
│ [Q1]  │  [Q2]   │  [Q5]              │
│ text1 │  text2  │  text5             │
│       │  [Q3]   │                    │
│       │  text3  │                    │
│       │  [Q4]   │                    │
└──────────────────────────────────────┘
```

**검증 포인트**:
- ✅ 3개 컬럼 모두 독립적으로 감지
- ✅ 각 컬럼의 요소 교차 할당 없음

**데이터**:
- 왼쪽 좁음: X=80
- 중앙 넓음: X=400
- 오른쪽 좁음: X=720

### 테스트 4: 2단 걸친 이미지

```
Layout:
┌─────────────────────────────────────┐
│ [Q1]         [Q2]                   │
│ text1        text2                  │
│ ┌─────────────────┐                 │
│ │  Wide Image     │                 │
│ └─────────────────┘                 │
└─────────────────────────────────────┘
```

**검증 포인트**:
- ✅ 넓은 이미지가 중심점 기반으로 할당
- ✅ 텍스트 요소는 각자 컬럼에 올바르게 할당

**데이터**:
- 이미지: X=200, Y=200, width=400, height=100
- 중심점: (400, 250)
- 가까운 문제에 할당 (거리 계산)

### 테스트 5: 1D Fallback

```
Layout (단일 컬럼):
┌─────────────────────────────────────┐
│ [Q1]                                │
│ text (Y=200)                        │
│ [Q2]                                │
│ text (Y=400)                        │
│ [Q3]                                │
└─────────────────────────────────────┘
```

**검증 포인트**:
- ✅ 모든 문제가 같은 X좌표 → 단일 컬럼 감지
- ✅ Y좌표 기반 할당으로 fallback
- ✅ 정상 작동 (안전성 보장)

## 📊 테스트 커버리지

### 통합 지점
- ✅ `UnifiedAnalysisEngine.performUnifiedAnalysis()`
- ✅ `QuestionNumberExtractor.extractQuestionPositions()`
- ✅ `ColumnDetector.detectColumns()`
- ✅ `Spatial2DAnalyzer.assignElementToQuestion()`
- ✅ `SpatialAnalysisEngine.assignElementToNearestQuestion2D()`

### 검증 레이어
1. **Layout 생성** → 올바른 좌표 데이터
2. **OCR/AI 결과** → 실제 분석 시뮬레이션
3. **통합 분석 실행** → 전체 파이프라인
4. **결과 검증** → StructuredData 정확성
5. **요소 할당** → 문제별 그룹핑 확인

## 🔍 검증 헬퍼 메서드

### `assertQuestionHasElement()`
```java
// 문제가 특정 텍스트를 포함하는지 검증
assertQuestionHasElement(questionsById, 1, "문제 1의 텍스트");
```

### `assertQuestionDoesNotHaveElement()`
```java
// 교차 할당이 없는지 검증 (negative test)
assertQuestionDoesNotHaveElement(questionsById, 1, "문제 4의 텍스트");
```

### `hasElementWithDescription()`
```java
// AI 설명 기반 요소 검색 (이미지/차트용)
boolean hasImage = hasElementWithDescription(question, "넓은 이미지");
```

## 🏗️ 테스트 구조

### 데이터 생성 헬퍼
```java
// 문제 번호 요소
addQuestionNumber(layouts, ocrs, id, x, y, number);

// 텍스트 요소
addTextElement(layouts, ocrs, id, x, y, text);

// 이미지 요소 (AI 설명 포함)
addImageElement(layouts, aiResults, id, x, y, width, height, description);
```

### LayoutInfo 생성
```java
createLayoutInfo(id, x, y, width, height, className)
→ {id, box:[x, y, x+w, y+h], className, confidence, area}
```

## ⚙️ 실행 방법

### Gradle (권장)
```bash
cd Backend/smarteye-backend
./gradlew test --tests "*Spatial2DIntegrationTest" \
  -Dsmartey.features.use-2d-spatial-analysis=true
```

### IDE (IntelliJ IDEA)
1. `Spatial2DIntegrationTest.java` 열기
2. 클래스 왼쪽 초록색 실행 버튼 클릭
3. "Run with Coverage" 선택 (커버리지 확인)

### 필수 설정
```properties
# application-test.properties
smarteye.features.use-2d-spatial-analysis=true
```

## 🎯 예상 결과

### 성공 케이스
```
✅ 테스트 1: 교차 컬럼 할당 방지 - PASSED
✅ 테스트 2: 비대칭 레이아웃 처리 - PASSED
✅ 테스트 3: 3단 레이아웃 감지 - PASSED
✅ 테스트 4: 걸친 이미지 할당 - PASSED
✅ 테스트 5: 1D Fallback 정상 작동 - PASSED

Total: 5 tests, 5 passed, 0 failed
Execution Time: < 2 seconds
```

### 실패 시 디버깅
```bash
# 상세 로그 확인
./gradlew test --tests "*Spatial2DIntegrationTest" --info

# 특정 테스트만 실행
./gradlew test --tests "*Spatial2DIntegrationTest.testStandard2ColumnNewspaperLayout"
```

## 📈 품질 메트릭

### 테스트 품질
- ✅ **명확한 시나리오**: 각 테스트가 하나의 명확한 목적
- ✅ **독립성**: 테스트 간 의존성 없음
- ✅ **반복 가능성**: 동일한 입력 → 동일한 결과
- ✅ **빠른 실행**: < 2초 (단위 테스트 수준)
- ✅ **의미 있는 assertion**: 비즈니스 로직 검증

### 커버리지 목표
- **라인 커버리지**: > 85% (핵심 경로)
- **분기 커버리지**: > 75% (주요 조건문)
- **통합 지점 커버리지**: 100% (모든 컴포넌트 연결)

## 🚀 향후 개선 사항

### 추가 테스트 케이스
1. **성능 테스트**: 1000개 요소 처리 시간
2. **스트레스 테스트**: 10단 이상 복잡한 레이아웃
3. **경계 케이스**: 컬럼 경계 정확히 걸친 요소
4. **오류 복구**: 잘못된 좌표 데이터 처리

### 테스트 자동화
- CI/CD 파이프라인 통합
- 매 커밋마다 자동 실행
- 커버리지 리포트 자동 생성

## 📝 참고 문서

- [CBHLS Phase 2 설계 문서](../../docs/CBHLS_Phase2_Design.md)
- [ColumnDetector 사양](./engine/ColumnDetector.java)
- [Spatial2DAnalyzer 사양](./engine/Spatial2DAnalyzer.java)
- [UnifiedAnalysisEngine API](./UnifiedAnalysisEngine.java)

## 👥 작성자

**SmartEye Backend Quality Engineering Team**
**날짜**: 2025-10-01
**버전**: v0.7 (CBHLS Phase 2)
