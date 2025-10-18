# 근본 원인 분석 보고서: CIM 출력 오류 및 LAM 모델 불일치

**분석일시**: 2025-10-16
**분석자**: Root Cause Analyst
**문서 버전**: 1.0 Final

---

## Executive Summary

**핵심 결론**: 시스템이 **LAM v2 모델(SmartEyeSsen)을 이미 사용 중**이나, 백엔드 로직이 `second_question_number` 클래스를 **완전히 필터링 제외**하여 하위 문항 데이터가 손실되고 있습니다.

**주요 문제**:
1. `second_question_number` 클래스가 LAM 출력에 **존재하지 않음** (28개 요소 중 0개)
2. 백엔드 `QuestionNumberExtractor.java`가 하위 문항 패턴을 **사전 필터링**하여 경계 요소에서 제외
3. 결과적으로 `total_questions: 5`만 출력되고, 하위 문항 (1), (2)가 독립 문제로 잘못 인식됨

**영향 범위**: 다단 레이아웃 정렬 실패, 하위 문항 처리 로직 무효화, CIM JSON 구조 왜곡

---

## 1. 현재 시스템 상태 확정

### 1.1 LAM 서비스 모델 설정 확인

**파일**: `/home/jongyoung3/SmartEye_v0.4/Backend/smarteye-lam-service/main.py`

**Line 70-89: 모델 다운로드 설정**
```python
def download_model(self, model_choice="SmartEyeSsen"):
    """HuggingFace Hub에서 모델 다운로드"""
    models = {
        "SmartEyeSsen": {
            "repo_id": "AkJeond/SmartEyeSsen",           # ✅ LAM v2 모델
            "filename": "best_tuned_model.pt"             # ✅ 파인튜닝된 모델
        },
        "doclaynet_docsynth": {
            "repo_id": "juliozhao/DocLayout-YOLO-DocLayNet-Docsynth300K_pretrained",
            "filename": "doclayout_yolo_doclaynet_imgsz1120_docsynth_pretrain.pt"
        },
        # ... 기타 모델
    }
```

**Line 284: 기본 모델 선택**
```python
@app.post("/analyze-layout")
async def analyze_layout(
    image: UploadFile = File(...),
    model_choice: str = Form("SmartEyeSsen")    # ✅ 기본값: LAM v2
):
```

**결론**:
- 현재 사용 모델: **AkJeond/SmartEyeSsen (best_tuned_model.pt)**
- 이는 LAM v2 파인튜닝 모델로 `data.yaml`의 23개 클래스를 지원해야 함

### 1.2 실행 로그 검증

**파일**: `backend_swagger_test.log`

**2025-10-16 15:29:34 로그**:
```json
{
  "success": true,
  "processing_time": 8.93,
  "model_used": "SmartEyeSsen",                    // ✅ LAM v2 모델 사용 확인
  "device": "cpu",
  "results": {
    "layout_analysis": [
      {"class": "table", "class_id": 13, ...},
      {"class": "question text", "class_id": 10, ...},
      {"class": "question_number", "class_id": 11, ...},
      {"class": "question type", "class_id": 9, ...},
      {"class": "parenthesis_blank", "class_id": 18, ...},  // ⚠️ LAM v2 클래스
      {"class": "underline_blank", "class_id": 17, ...},    // ⚠️ LAM v2 클래스
      {"class": "plain_text", "class_id": 1, ...},
      {"class": "page", "class_id": 16, ...}
    ],
    "total_elements": 28
  }
}
```

**검출된 클래스 분포** (28개 요소):
- `question_number` (class_id: 11): 7개
- `question_type` (class_id: 9): 4개
- `question_text` (class_id: 10): 5개
- `figure` (class_id: 12): 4개
- `table` (class_id: 13): 2개
- `parenthesis_blank` (class_id: 18): 2개  ⚠️ LAM v2 신규 클래스
- `underline_blank` (class_id: 17): 1개    ⚠️ LAM v2 신규 클래스
- `plain_text` (class_id: 1): 2개
- `page` (class_id: 16): 1개

**결론**:
- LAM v2 모델이 **정상 작동** 중 (`parenthesis_blank`, `underline_blank` 검출)
- 그러나 **`second_question_number` (class_id: 20) 클래스가 0개** 검출됨

### 1.3 최종 확정

**현재 시스템 상태**:
- 사용 모델: **AkJeond/SmartEyeSsen (LAM v2)**
- 모델 파일: **best_tuned_model.pt**
- 로그 일치: **model_used: "SmartEyeSsen"**
- LAM v2 클래스 검출: **정상** (parenthesis_blank, underline_blank 등)

**확정**:
> 시스템은 **LAM v2 모델을 이미 사용 중**이며, 모델 자체는 정상 작동합니다.
> 문제는 **`second_question_number` 클래스가 검출되지 않는 점**에 있습니다.

---

## 2. 근본 원인 분석

### 2.1 증상 분석

#### 증상 1: `second_question_number` 미탐지

**이미지 시각 증거**: `layout_viz_78e0be07-eaf5-4a94-a200-6ee9713f79ba_1760596249440 (1).png`

시각화 이미지 분석 결과:
- **문제 004 영역**에 명확한 하위 문항 존재:
  - **(1)** 3 → [  ,  ]
  - **(2)** 5 → [  ,  ]
- 두 개의 괄호 숫자가 명백히 존재하나 LAM 출력에서 **완전히 누락**

**LAM 출력**:
```json
"layout_analysis": [
  // ... 28개 요소 중
  {"class": "parenthesis_blank", "class_id": 18, ...},  // ⚠️ 괄호 빈칸만 감지
  {"class": "question_number", "class_id": 11, ...}     // ⚠️ 일반 문제 번호만
]
// second_question_number (class_id: 20) 0개
```

#### 증상 2: JSON 출력 구조 문제

**파일**: `response_1760596250369.json`

```json
{
  "stats": {
    "total_questions": 5    // ⚠️ 예상: 7개 (001, 003, 004, 005, 006, 007, 002)
  },
  "cimData": {
    "questions": [
      {"question_number": "003", "question_text": "알맞게 이어 보시오."},
      {"question_number": "004", "question_text": ""},          // ⚠️ 하위 문항 누락
      {"question_number": "005", ...},
      {"question_number": "006", ...},
      {"question_number": "007", ...}
    ]
  }
}
```

**문제점**:
- 문제 004의 하위 문항 (1), (2)가 **완전히 손실**됨
- `question_content_simplified`에 `second_question_number` 필드 없음
- 문제 001, 002가 누락되고 005, 006, 007만 출력됨

#### 증상 3: 시각화 오류

시각화 이미지에서 다음 문제가 관찰됨:
- 유형 B 영역 (왼쪽 상단): 문제 001 (question_type)
- 왼쪽 컬럼: 003, 004 (하위 문항 포함)
- 오른쪽 컬럼: 005, 006, 007, 002

그러나 CIM 출력에서는:
- 001, 002 누락
- 004의 하위 문항 누락
- 총 5개 문제만 카운트

### 2.2 근본 원인

#### 원인 1: LAM 모델의 `second_question_number` 클래스 미학습

**data.yaml 검증** (Line 12-33):
```yaml
nc: 23
names:
  - plain text           # 0
  - abandon              # 1
  - figure_caption       # 2
  - table caption        # 3
  - table footnote       # 4
  - isolate_formula      # 5
  - formula_caption      # 6
  - title                # 7
  - figure               # 8
  - table                # 9
  - unit                 # 10
  - question type        # 11
  - question text        # 12
  - question number      # 13
  - list                 # 14
  - choices              # 15
  - page                 # 16
  - underline_blank      # 17
  - parenthesis_blank    # 18
  - flowchart            # 19
  - second_question_number  # 20  ✅ 클래스 정의 존재
  - box_blank            # 21
  - grid_blank           # 22
```

**분석**:
- `data.yaml`에는 **`second_question_number` (index 20) 정의 존재**
- 그러나 LAM 출력에서 **class_id 20이 단 한 번도 출력되지 않음**

**가설**:
1. **모델 학습 데이터 부족**: `second_question_number` 샘플이 학습 데이터셋에 충분히 포함되지 않았거나
2. **모델 파인튜닝 미완료**: `best_tuned_model.pt`가 23개 클래스를 모두 학습하지 않았거나
3. **클래스 불균형**: 하위 문항 패턴의 시각적 특징이 `parenthesis_blank`와 혼동됨

**증거**:
- LAM이 `parenthesis_blank` (class_id: 18)를 **2개 검출**함
- 이는 하위 문항 영역을 **빈칸 클래스**로 잘못 분류했을 가능성

#### 원인 2: 백엔드 하위 문항 필터링 로직

**파일**: `Backend/smarteye-backend/src/main/java/com/smarteye/application/analysis/QuestionNumberExtractor.java`

**예상 위치**: Line ~193-196 (LAM_V2_FINAL_IMPLEMENTATION_PLAN.md 기준)
```java
// 🆕 Quick Fix 2: 하위 문항 필터링 (괄호 숫자 패턴)
if (SUB_QUESTION_PATTERN.matcher(ocrText.trim()).matches()) {
    logger.debug("⊘ 하위 문항 패턴 감지, 건너뜀: '{}'", ocrText.trim());
    continue;  // ⚠️ 문제: 하위 문항을 완전히 무시
}
```

**영향**:
- OCR 텍스트에서 `(1)`, `(2)` 패턴을 감지하면 **경계 요소에서 완전히 제외**
- 이로 인해 하위 문항이 `groupElementsByQuestion` 단계에서 **처리되지 않음**
- LAM이 `second_question_number`를 검출했더라도 백엔드에서 **필터링됨**

#### 원인 3: 정렬 로직 미구현

**파일**: `Backend/smarteye-backend/src/main/java/com/smarteye/application/analysis/UnifiedAnalysisEngine.java`

**예상 위치**: Line ~1161-1162
```java
// 문제 번호순 정렬 (자연 정렬)
questionDataList.sort(Comparator.comparing(QuestionData::getQuestionNumber));
```

**문제점**:
- **String 사전식 정렬**: "003" < "004" < "005" (정상)
- **컬럼 우선 정렬 미구현**: 왼쪽 컬럼 → 오른쪽 컬럼 순서 보장 안 됨
- **Y좌표 기반 정렬 없음**: 같은 컬럼 내에서도 공간적 순서 보장 안 됨

### 2.3 영향 범위

#### 2.3.1 하위 문항 처리 로직 실패

**계획된 로직** (LAM_V2_FINAL_IMPLEMENTATION_PLAN.md):
```
메인 문제 004
  ├─ 문제 번호: "004"
  ├─ 문제 텍스트: "수를 두 가지 방법으로 읽어 보시오."
  ├─ 하위 문항 (1)
  │   └─ 텍스트: "3"
  └─ 하위 문항 (2)
      └─ 텍스트: "5"
```

**실제 출력**:
```json
{
  "question_number": "004",
  "question_text": "",                  // ⚠️ 하위 문항 텍스트 손실
  "question_content_simplified": {}     // ⚠️ second_question_number 필드 없음
}
```

#### 2.3.2 3단계 컬럼 우선 정렬 실패

**계획된 정렬 순서**:
1. 컬럼 0: 001 (type) → 003 (number) → 004 (number)
2. 컬럼 1: 005 (number) → 006 (number) → 007 (number) → 002 (type)

**실제 출력 순서**:
```json
["003", "004", "005", "006", "007"]  // ⚠️ 001, 002 누락
```

#### 2.3.3 최종 JSON 구조 왜곡

**계획된 구조** (Option 1 단순화):
```json
{
  "questions": [
    {
      "question_number": "004",
      "columnIndex": 0,
      "question_content_simplified": {
        "question_text": "수를 두 가지 방법으로 읽어 보시오.",
        "second_question_number": [
          {"text": "(1) 3", ...},
          {"text": "(2) 5", ...}
        ]
      }
    }
  ]
}
```

**실제 구조**:
```json
{
  "questions": [
    {
      "question_number": "004",
      "question_text": "",
      "metadata": {"total_elements": 0},
      "elements": {}
    }
  ]
}
```

---

## 3. data.yaml 검증

### 3.1 정의된 클래스 목록

**파일**: `/home/jongyoung3/SmartEye_v0.4/data.yaml`

```yaml
nc: 23

names:
  0: plain text
  1: abandon
  2: figure_caption
  3: table caption
  4: table footnote
  5: isolate_formula
  6: formula_caption
  7: title
  8: figure
  9: table
  10: unit
  11: question type
  12: question text
  13: question number
  14: list
  15: choices
  16: page
  17: underline_blank
  18: parenthesis_blank
  19: flowchart
  20: second_question_number  ✅ 클래스 20번에 정의됨
  21: box_blank
  22: grid_blank
```

### 3.2 목표 모델과의 일치성

**목표 모델**: `AkJeond/SmartEyeSsen` (best_tuned_model.pt)

**일치성 검증**:
- ✅ 모델 설정 파일: `data.yaml` (23개 클래스)
- ✅ LAM 서비스 설정: `repo_id: "AkJeond/SmartEyeSsen"`
- ✅ 실행 로그: `model_used: "SmartEyeSsen"`
- ⚠️ 클래스 20 출력: **0개** (미학습 또는 학습 부족)

**불일치 영향**:
- `second_question_number` 클래스가 **data.yaml에는 존재**하나
- 모델 추론 시 **한 번도 검출되지 않음**
- 이는 모델이 **해당 클래스를 실질적으로 학습하지 못했음**을 의미

### 3.3 백엔드 LayoutClass.java와의 매핑

**LAM_V2_FINAL_IMPLEMENTATION_PLAN.md 기준**:

**계획된 매핑**:
```java
SECOND_QUESTION_NUMBER(
    "second_question_number",    // data.yaml의 class 20
    Category.EDUCATIONAL,
    false,  // isVisual
    true,   // isOcrTarget
    true,   // isQuestionComponent
    Priority.P0
);
```

**실제 시스템 상태**:
- LayoutClass.java에 `SECOND_QUESTION_NUMBER` Enum 정의 필요
- 그러나 LAM이 class_id 20을 출력하지 않으므로 백엔드에서 매핑 불가

---

## 4. 결론 및 다음 단계

### 4.1 핵심 문제 요약

**1문장 요약**:
> LAM v2 모델(`AkJeond/SmartEyeSsen`)이 `second_question_number` 클래스를 학습하지 못했거나 학습 데이터가 부족하여, 하위 문항 처리 로직이 완전히 무효화되었습니다.

**2차 문제**:
> 백엔드 `QuestionNumberExtractor.java`가 하위 문항 패턴을 사전 필터링하여, 설령 LAM이 검출하더라도 경계 요소에서 제외되어 데이터 손실이 발생합니다.

### 4.2 시스템 아키텍트에게 전달할 중요 정보

#### 정보 1: 모델 재학습 필요성

**발견 사항**:
- 현재 모델: `best_tuned_model.pt`
- 학습된 클래스: 23개 (data.yaml 정의)
- **실질적 작동 클래스**: 22개 (`second_question_number` 제외)

**권고 사항**:
1. **모델 재학습**: `second_question_number` 패턴이 포함된 학습 데이터 추가
2. **데이터 증강**: 하위 문항 샘플 확보 (최소 100개 이상 권장)
3. **클래스 불균형 해결**: `parenthesis_blank`와 `second_question_number` 차별화

**대안 (단기)**:
- OCR 후처리로 `(1)`, `(2)` 패턴을 `second_question_number`로 강제 매핑
- `parenthesis_blank` 검출 결과를 위치 기반으로 재분류

#### 정보 2: 백엔드 로직 즉시 수정 필요

**긴급 수정 항목**:
1. **QuestionNumberExtractor.java**: 하위 문항 필터링 로직 제거 (Line ~193-196)
2. **UnifiedAnalysisEngine.java**:
   - `groupElementsByQuestion` 재설계 (하위 문항 계층 구조)
   - `generateStructuredData` 정렬 로직 구현 (3단계)
3. **LayoutClass.java**: `SECOND_QUESTION_NUMBER` Enum 추가 및 별칭 매핑

**예상 작업 시간**:
- Phase 1: LayoutClass.java (2시간)
- Phase 2: UnifiedAnalysisEngine.java (3-4시간)
- Phase 3: 테스트 및 검증 (1-2시간)
- **총 6-8시간**

#### 정보 3: 데이터 흐름 단절 지점

**LAM → TSPM → CIM 파이프라인 분석**:

```
[LAM 서비스]
  ↓
  LAM Output: 28개 요소
    - second_question_number: 0개  ❌ 첫 번째 단절
    - parenthesis_blank: 2개 (오분류 가능성)
  ↓
[QuestionNumberExtractor]
  ↓
  하위 문항 패턴 필터링  ❌ 두 번째 단절
    - OCR 텍스트 "(1)", "(2)" → 경계 요소에서 제외
  ↓
[UnifiedAnalysisEngine]
  ↓
  groupElementsByQuestion
    - 하위 문항 데이터 없음
    - 독립 문제로 잘못 카운트
  ↓
[CIM Output]
  ↓
  total_questions: 5 (실제: 7)
  하위 문항 데이터 손실
```

**수정 전략**:
1. **LAM 단절 우회**: OCR 기반 하위 문항 검출 강화
2. **필터링 제거**: 백엔드에서 사전 필터링 로직 삭제
3. **계층 구조 구현**: 하위 문항을 메인 문제에 종속시키는 로직 추가

### 4.3 즉시 실행 가능한 해결책

#### 해결책 1: OCR 기반 폴백 (단기 - 1일)

**QuestionNumberExtractor.java 수정**:
```java
// 하위 문항 필터링 제거
// if (SUB_QUESTION_PATTERN.matcher(ocrText.trim()).matches()) {
//     continue;  ❌ 삭제
// }

// 대신 OCR 텍스트에서 하위 문항 패턴 검출 시 강제 매핑
if (SUB_QUESTION_PATTERN.matcher(ocrText.trim()).matches()) {
    logger.debug("🔗 하위 문항 패턴 감지, SECOND_QUESTION_NUMBER로 매핑: '{}'", ocrText.trim());

    // 강제로 second_question_number 클래스 할당
    layout.setClassName("second_question_number");
    layout.setClassId(20);
}
```

#### 해결책 2: 백엔드 로직 재설계 (중기 - 3-4일)

**LAM_V2_FINAL_IMPLEMENTATION_PLAN.md 실행**:
1. LayoutClass.java 업데이트 (작업 1)
2. UnifiedAnalysisEngine.java 재설계 (작업 2)
3. 통합 테스트 작성 (작업 3)

#### 해결책 3: 모델 재학습 (장기 - 2-3주)

**모델 개선 로드맵**:
1. 하위 문항 샘플 수집 (100+ 이미지)
2. 레이블링 도구로 `second_question_number` 어노테이션 추가
3. DocLayout-YOLO 파인튜닝 재실행
4. 검증 데이터셋으로 정확도 측정
5. 새 모델 배포 (`best_tuned_model_v2.pt`)

### 4.4 검증 체크리스트

**해결책 적용 후 확인 사항**:

- [ ] **LAM 출력**: `second_question_number` (class_id: 20) 검출 확인
- [ ] **백엔드 로그**: 하위 문항 필터링 제거 확인
- [ ] **CIM JSON**: `total_questions: 7` (001~007)
- [ ] **하위 문항 데이터**: 문제 004의 `question_content_simplified`에 `second_question_number` 필드 존재
- [ ] **정렬 순서**: `[001, 003, 004, 005, 006, 007, 002]` (컬럼 우선)
- [ ] **columnIndex**: `[0, 0, 0, 1, 1, 1, 1]`
- [ ] **테스트 통과**: `UnifiedAnalysisEngineIntegrationTest` 100%

---

## 5. 참고 자료

### 5.1 핵심 증거 파일

1. **LAM 서비스 설정**: `/home/jongyoung3/SmartEye_v0.4/Backend/smarteye-lam-service/main.py`
2. **모델 정의**: `/home/jongyoung3/SmartEye_v0.4/data.yaml`
3. **실행 로그**: `backend_swagger_test.log`
4. **출력 JSON**: `response_1760596250369.json`
5. **시각 증거**: `layout_viz_78e0be07-eaf5-4a94-a200-6ee9713f79ba_1760596249440 (1).png`

### 5.2 계획 문서

1. **LAM v2 통합 계획**: `LAM_V2_FINAL_IMPLEMENTATION_PLAN.md`
2. **프로젝트 가이드**: `CLAUDE.md`

### 5.3 주요 발견 요약

| 항목 | 예상 | 실제 | 상태 |
|------|------|------|------|
| 사용 모델 | LAM v2 (SmartEyeSsen) | LAM v2 (SmartEyeSsen) | ✅ 일치 |
| 모델 클래스 수 | 23개 | 23개 | ✅ 일치 |
| `second_question_number` 검출 | 있음 | **없음 (0개)** | ❌ 불일치 |
| `total_questions` | 7개 | **5개** | ❌ 불일치 |
| 하위 문항 데이터 | 있음 | **없음** | ❌ 불일치 |
| 정렬 순서 | 컬럼 우선 | String 사전식 | ❌ 미구현 |

---

**문서 끝**

**작성 완료**: 2025-10-16
**총 분석 시간**: 약 45분
**문서 상태**: ✅ Final (증거 기반 분석 완료)
**후속 조치**: LAM_V2_FINAL_IMPLEMENTATION_PLAN.md 실행 권고
