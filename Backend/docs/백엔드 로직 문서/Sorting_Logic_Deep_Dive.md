# 정렬 로직 심층 분석 (Sorter v2.4 & Adaptive 전략)

## 1. 개요
- 목적: OCR·레이아웃 감지 결과를 **문제 단위(앵커-자식)**로 재조합해 사람이 읽기 쉬운 순서로 정렬.
- 제품 환경: 어린이 학습지, 점자 제작 파이프라인 등 좌표 기반 요소 재배치가 필수인 도메인에 맞춰 설계.
- 진화 방향: `sorter_구버전.py` → `sorter.py` (v2.4) → Adaptive Strategy(`sorter_strategies.py`)로 단계적 개선.

## 2. `services/sorter.py` (v.LayoutDetect.2.4)

### 2.1 진입점 선택 로직
- `sort_layout_elements()`은 환경 변수 `USE_ADAPTIVE_SORTER`에 따라 실행 경로를 결정.
  - `true/1/yes`이면 `sorter_strategies.sort_layout_elements_adaptive()`로 위임.
  - 그렇지 않으면 내부 코어 `_sort_layout_elements_v24()`를 직접 사용.
- 추가 인자 `page_dpi`는 Adaptive 모드에서 물리적 페이지 크기 평가에 활용.

### 2.2 v2.4 코어 파이프라인 (`_sort_layout_elements_v24`)
1. **전처리 (`preprocess_elements`)**
   - 문제지 모드(`question_based`)일 때 허용 클래스(`question type`, `question number`, `choices`, `figure` 등)만 필터링.
   - 면적이 0 이하인 요소 제거.
2. **페이지 크기 추정**
   - 요소 좌표를 기반으로 너비/높이 추정 → 이후 레이아웃 판별 및 분할 경계에 사용.
3. **레이아웃 유형 판별 (`detect_layout_type`)**
   - 앵커 위치를 분석해 1단/2단/혼합형/수평 구분선 존재 여부 판단.
   - K-Means와 표준편차를 활용, 상단/하단 분포를 별도로 비교.
4. **재귀 분할 (`_sort_recursive_by_layout`)**
   - 레이아웃 유형에 따라 우선 수평/수직 분할 전략을 결정.
   - 2단 구조는 `_sort_standard_2_column`에서 전용 로직 실행.
5. **Base Case 처리**
   - `_base_case_standard_1_column`: 수평 인접 우선 배정 → 나머지는 2D 거리 기반 그룹핑 → 순차 그룹핑.
   - `_base_case_mixed_layout`: 상·하단 독립 영역, 고아 요소를 별도로 처리.
6. **후처리 (`_post_process_table_figure_assignment`)**
   - 테이블/그림/플로차트가 다음 앵커에 더 근접하면 Lookahead(최대 2 그룹)으로 이동.
   - v2.4 추가: Y 거리 동일 시 뒤쪽 그룹을 우선하는 Tie-breaker.
7. **평탄화 (`flatten_groups_and_assign_order`)**
   - `group_id`, `order_in_group`, `order_in_question`를 부여해 순차 리스트로 반환.

### 2.3 2D 거리 기반 그룹핑
- 함수: `_assign_children_to_anchors_with_2d_proximity()`
  - **거리 정의**: `distance = sqrt((Δy)^2 + (Δx * 0.2)^2)`로 X보다 Y를 강조.
  - **제약**: 자식이 앵커보다 위에 있으면 배정 금지, 상단 고아 구역은 별도 보존.
  - 결과: 앵커별 임시 그룹과 고아 리스트를 반환, 이후 순차 처리 단계와 결합.
- 개선 효과: 구버전 대비 칼럼 경계 오차나 수평 인접 실패로 발생하던 오배정 감소.

### 2.4 수직 분할 개선
- `_find_vertical_split_kmeans()`가 오른쪽 칼럼 앵커 시작점을 기준(`center[1] - 20px`)으로 분할선을 잡아,
  - 중간 경계선이 자식 요소를 잘라내던 문제 완화.
  - 분할 실패 시 경고 로그를 남기고 Base Case로 폴백.

### 2.5 DB 적재 연동
- `save_sorting_results_to_db()`:
  - `sorted_elements`에서 `group_id`별로 묶어 `question_groups`를 생성, 앵커와 Y 범위를 저장.
  - 각 요소를 `question_elements`에 연결하고 `order_in_question`을 1-base로 기록.
  - 실패 시 ValueError를 던져 upstream에서 롤백 처리하도록 함 (`batch_analysis.py`에서 사용).

## 3. `services/sorter_strategies.py` (Adaptive Strategy)

### 3.1 전략 타입과 팩토리
- `SortingStrategyType`: `GLOBAL_FIRST`, `LOCAL_FIRST`, `HYBRID`.
- `SortingStrategyFactory`는 싱글턴 인스턴스를 반환해 전략 재사용 비용을 최소화.

### 3.2 LayoutProfiler
- `LayoutProfiler.analyze()` 핵심 지표:
  - `global_consistency_score`: 앵커 X 좌표 표준편차 기반 0~1 스코어.
  - `horizontal_adjacency_ratio`: 앵커 주변에 수평 인접한 자식 존재 비율.
  - `layout_type`: `detect_layout_type()` 재사용.
  - `effective_dpi`, `width_in_inches`: DPI 추정(페이지 높이 기반) 후 물리 크기 계산.
  - `anchor_y_variance`, `anchor_count`: 정렬 난이도 판단.
- 전략 추천 (`_recommend_strategy`):
  - 명확한 2단 구조 → 인접 비율·일관성 기반으로 Global/Local/Hybrid 중 선택.
  - 혼합형/수평 구분 → Local 또는 Hybrid 선호.
  - 일관성·인접성 지표가 중간대이면 Hybrid, 극단이면 Global/Local로 치우침.
  - `force_strategy` 파라미터로 수동 강제도 지원(테스트·디버깅용).

### 3.3 전략 구현
| 전략 | 설명 | 구현 특징 |
| --- | --- | --- |
| GlobalFirstStrategy | v2.4 코어 로직을 그대로 호출 | 안정적인 PDF/스캔본, 균일한 앵커 배치에 적합 |
| LocalFirstStrategy | `sorter_구버전.sort_layout_elements()` 재사용 | 불규칙한 이미지, 손그림 등 로컬 기준 우선 |
| HybridStrategy | 두 전략을 모두 실행 후 품질 평가 | `_score_grouping()`이 앵커/자식 유효성, 칼럼逸脱, 고아 여부에 패널티 부여 |

- Hybrid 패널티 요소:
  - 앵커 없는 그룹: +5.0
  - 자식 없는 앵커: +1.0
  - 자식이 앵커보다 위: +1.0 / 칼럼 범위 이탈: +0.5
  - 그룹에 속하지 않은 자식: +2.0
  - 미배정 앵커: +1.5
- 낮은 패널티 결과를 채택해 구버전/신버전 중 상황별 최적 결과 확보.

### 3.4 Adaptive 진입점 (`sort_layout_elements_adaptive`)
1. 요소 전처리 및 페이지 크기 추정.
2. `force_strategy` 지정 시 해당 전략 즉시 실행.
3. 미지정 시 `LayoutProfiler.analyze()`로 추천 전략을 결정.
4. 선택된 전략 인스턴스 실행 후 정렬 결과 반환.
5. 로그로 선택 이유·지표·전략 실행 결과를 남겨 회귀 테스트와 현장 튜닝을 용이하게 함.

## 4. `services/sorter_구버전.py` 비교 분석

### 4.1 주요 특징
- v2.3 시절의 레거시 로직을 유지:
  - 수평 인접 중심의 Base Case, 순차 정렬 기반 그룹핑.
  - 수직 분할은 K-Means 중심 간 중간값을 경계선으로 사용.
  - 후처리는 존재하나 Tie-breaker나 2D 거리 계산 미적용.
- 테스트용 MockElement 구조는 동일, Adaptive 전략에서 Local 기준으로 활용.

### 4.2 한계
- **맞춤형 분할 부족**: 경계선이 요소 중앙을 가르는 케이스에서 오배정 발생.
- **2D 거리 미활용**: 앵커 바로 아래에 있어야 할 그림이 이웃 문제로 이동하는 사례 빈번.
- **DB 연동 부재**: 정렬 결과를 question_groups/question_elements에 채우는 기능이 없어 상위 파이프라인에서 추가 후처리 필요.
- **전략 선택 불가**: 모든 상황에 단일 로직 적용 → 고정형 PDF에는 과도하고, 불규칙 이미지에는 부족.

### 4.3 신버전(v2.4) 개선 효과
- **정밀한 칼럼 분할**: 오른쪽 경계 기준 분할로 좌우 칼럼 혼선 감소.
- **2D 프로ximity 그룹핑**: 상단 고아 보호, 앵커 위 자식 배제 등 규칙 강화.
- **후처리 Tie-breaker**: 동일 거리일 때 뒤쪽 그룹 우선 → 표/그림이 가려지는 현상 최소화.
- **DB 저장 통합**: 정렬 후 즉시 `question_groups`/`question_elements` 생성 → 텍스트 포맷터, 다운로드 서비스가 바로 재사용.
- **Adaptive 연결**: 환경 플래그로 상황별 전략 조합 가능, 구버전은 Local 전략으로 안전하게 유지되어 회귀 대비책으로 활용.

## 5. 파이프라인 통합 포인트
- `services/batch_analysis.py`에서 `sort_layout_elements()` 호출 후 결과를 DB에 반영하고 `TextFormatter`가 소비.
- `routers/analysis.py`의 비동기 작업에서도 동일 정렬 로직 재사용 → 전략 토글 시 전역적으로 일관된 결과 확보.
- `USE_ADAPTIVE_SORTER`는 배포 환경에서 점진적으로 켜고 끄며 회귀를 검증할 수 있는 안전장치로 활용.

## 6. 운영 시 고려 사항
- 레이아웃 패턴이 다양해지는 경우 `LayoutProfiler._recommend_strategy()` 임계값을 조정하거나 전략을 추가 구현 가능.
- Hybrid 전략은 두 배 가까운 실행 시간을 요구 → GPU/OCR 비용을 고려해 `OPENAI_MAX_CONCURRENCY`, 페이지 DPI 정보와 함께 튜닝.
- 새로운 요소 클래스가 추가되면 `ALLOWED_ANCHORS`, `ALLOWED_CHILDREN` 상수와 포스트 프로세싱 규칙을 함께 업데이트해야 한다.

