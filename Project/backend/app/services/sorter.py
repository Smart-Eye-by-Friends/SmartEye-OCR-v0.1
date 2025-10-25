"""
SmartEyeSsen Layout Sorter (v.LayoutDetect.1 - Layout Type Detection)
====================================================================

문제 레이아웃 정렬 알고리즘 구현 (Layout Type Detection 기반 Hybrid)
페이지 전체 레이아웃 유형(1단, 2단, 혼합형 등)을 먼저 판별하고,
유형에 맞는 분할 전략(수평/수직) 또는 직접 그룹핑을 적용.
각 구역/컬럼 내 그룹핑은 수평 인접 처리가 포함된 hybrid_grouping_strategy 사용.
최종 병합 시 전역 고아 그룹 처리 로직 적용.

알고리즘 흐름:
0. 전처리: 허용된 클래스 필터링 (question_based 모드)
1. 레이아웃 유형 판별: 앵커 분포 분석 (detect_layout_type)
2. 유형별 처리:
    - HORIZONTAL_SEP_PRESENT: 넓은 Type 기반 수평 분할 후 재귀적 유형 판별/처리
    - MIXED_TOP1_BOTTOM2 / MIXED_TOP2_BOTTOM1: Y Gap 기반 수평 분할 후 재귀적 유형 판별/처리
    - STANDARD_2_COLUMN: K-Means 수직 분할 후 컬럼별 hybrid_grouping_strategy
    - STANDARD_1_COLUMN: 전체 요소에 대해 hybrid_grouping_strategy
    - reading_order: (Y,X) 정렬
3. 최종 병합 및 순서 부여: 그룹 병합, 전역 고아 처리, 순서 속성 부여

v.LayoutDetect.1: 기존 v.Final.1(컬럼 우선)과 재귀 분할 아이디어를 결합하여 레이아웃 유형 판별 기반 접근 방식 구현.
"""

# 필요한 라이브러리 임포트
from typing import List, Dict, Tuple, Optional, Any, Union # 타입 힌팅을 위한 Union 추가
from dataclasses import dataclass, field # 데이터 클래스 사용
import numpy as np # 수치 계산 (K-Means 입력 데이터)
from sklearn.cluster import KMeans # K-Means 클러스터링 (수직 분할)
from loguru import logger # 로깅
import math # 수학 함수 (사용되지 않음, 필요시 추가)
from enum import Enum, auto # 열거형 (레이아웃 유형 정의)

# Mock 모델 임포트 (데이터베이스 독립적인 처리를 위해)
from .mock_models import MockElement


# ============================================================================
# ⭐ 데이터 클래스 정의 (파일 상단으로 이동) ⭐
# ============================================================================

# 레이아웃 유형을 정의하는 열거형 클래스
class LayoutType(Enum):
    STANDARD_1_COLUMN = auto() # 표준 1단 레이아웃
    STANDARD_2_COLUMN = auto() # 표준 2단 레이아웃
    MIXED_TOP1_BOTTOM2 = auto() # 상단 1단, 하단 2단 혼합 레이아웃
    MIXED_TOP2_BOTTOM1 = auto() # 상단 2단, 하단 1단 혼합 레이아웃
    HORIZONTAL_SEP_PRESENT = auto() # 넓은 question_type 기반 수평 분할 가능 레이아웃
    READING_ORDER = auto() # 문서 타입 자체가 reading_order (Y/X 정렬)
    UNKNOWN = auto() # 판별되지 않은 레이아웃 (오류 또는 예외 처리용)

# 페이지 내 구역(Zone) 정보를 담는 데이터 클래스
@dataclass
class Zone:
    x_min: int # 구역의 좌측 X 좌표
    y_min: int # 구역의 상단 Y 좌표
    x_max: int # 구역의 우측 X 좌표
    y_max: int # 구역의 하단 Y 좌표

    @property
    def width(self) -> int:
        """구역의 너비 계산"""
        return max(0, self.x_max - self.x_min)

    @property
    def height(self) -> int:
        """구역의 높이 계산"""
        return max(0, self.y_max - self.y_min)

    def __repr__(self) -> str:
        """Zone 객체를 문자열로 표현 (디버깅용)"""
        return f"Zone(x=[{self.x_min}, {self.x_max}), y=[{self.y_min}, {self.y_max}))"

# 수평 분할 결과를 담는 데이터 클래스 (넓은 question_type 기반)
@dataclass
class HorizontalSplit:
    top_zone: Zone # 분할된 상단 구역
    bottom_zone: Zone # 분할된 하단 구역
    separator_element: MockElement # 분할 기준으로 사용된 요소 (question_type)

# 수평 분할 결과를 담는 데이터 클래스 (Y 좌표 Gap 기반)
@dataclass
class HorizontalSplitYGap:
    top_zone: Zone # 분할된 상단 구역
    bottom_zone: Zone # 분할된 하단 구역
    split_y: float # 분할 기준 Y 좌표

# 수직 분할 결과를 담는 데이터 클래스 (K-Means 기반)
@dataclass
class VerticalSplit:
    left_zone: Zone # 분할된 좌측 구역
    right_zone: Zone # 분할된 우측 구역
    gutter_x: float # 분할 기준 X 좌표 (좌우 컬럼 사이 간격)

# 문제 요소 그룹 (앵커 + 자식 요소들)을 나타내는 데이터 클래스
@dataclass
class ElementGroup:
    anchor: Optional[MockElement] # 그룹의 기준이 되는 앵커 요소 (없으면 고아 그룹)
    children: List[MockElement] = field(default_factory=list) # 그룹에 속한 자식 요소들
    group_id: int = -1 # 최종적으로 부여될 그룹 ID (초기값 -1)

    def add_child(self, child: MockElement):
        """그룹에 자식 요소를 추가합니다."""
        self.children.append(child)

    def get_all_elements_sorted(self) -> List[MockElement]:
        """그룹 내 모든 요소(앵커 포함)를 Y/X 좌표 순서로 정렬하여 반환합니다."""
        elements = [self.anchor] if self.anchor else [] # 앵커가 있으면 리스트에 추가
        elements.extend(self.children) # 자식 요소들 추가
        elements.sort(key=lambda e: (e.y_position, e.x_position)) # Y 좌표 우선, 그 다음 X 좌표 순으로 정렬
        return elements

    def is_empty(self) -> bool:
        """그룹이 비어있는지 (앵커도 없고 자식도 없는지) 확인합니다."""
        return self.anchor is None and not self.children

    def __repr__(self) -> str:
        """ElementGroup 객체를 문자열로 표현 (디버깅용)"""
        anchor_id = self.anchor.element_id if self.anchor else "Orphan" # 앵커 ID 또는 "Orphan"
        child_ids = sorted([c.element_id for c in self.children]) # 자식 ID 목록 정렬
        return f"Group(Anchor: {anchor_id}, Children: {child_ids})"

# ============================================================================
# 상수 정의
# ============================================================================

# question_based 모드에서 허용되는 클래스 이름 정의
ALLOWED_ANCHORS = ["question type", "question number", "second_question_number"] # 앵커 요소 클래스
ALLOWED_CHILDREN = ["question text", "list", "choices", "figure", "table", "flowchart"] # 자식 요소 클래스
ALLOWED_CLASSES = ALLOWED_ANCHORS + ALLOWED_CHILDREN # 허용되는 모든 클래스

# 레이아웃 판별 및 분할 관련 상수
HORIZONTAL_SEP_WIDTH_THRESHOLD = 0.8 # 넓은 question_type 너비 임계값 (구역 너비의 80%)
HORIZONTAL_SEP_Y_POS_THRESHOLD = 0.15 # 넓은 question_type 위치 임계값 (구역 높이의 상위 15%)
MIN_ANCHORS_FOR_SPLIT = 2 # 구역 분할을 시도하기 위한 최소 앵커 요소 개수
VERTICAL_GAP_THRESHOLD_RATIO = 1.5 # Y 좌표 Gap 기반 수평 분할 상대 임계값 (평균 앵커 높이의 1.5배)
VERTICAL_GAP_THRESHOLD_ABS = 100 # Y 좌표 Gap 기반 수평 분할 절대 임계값 (최소 100px)
KMEANS_N_CLUSTERS = 2 # 수직 분할 시 사용할 K-Means 클러스터 개수
KMEANS_CLUSTER_SEPARATION_MIN = 50 # 수직 분할 시 두 클러스터 중심 간 최소 거리 (50px)
LAYOUT_DETECT_Y_SPLIT_POINT = 0.4 # 혼합형 레이아웃 판별 시 상/하단 분할 기준 Y 좌표 비율 (상위 40%)
LAYOUT_DETECT_X_STD_THRESHOLD_RATIO = 0.1 # 혼합형 레이아웃 판별 시 X 좌표 표준편차 임계값 (페이지 너비 대비 10%)

# 하이브리드 그룹핑 관련 상수
HORIZONTAL_ADJACENCY_Y_CENTER_RATIO = 0.7 # 수평 인접 자식 탐색 시 허용 Y 중심 좌표 차이 비율 (두 요소 높이 합의 70%)
HORIZONTAL_ADJACENCY_X_PROXIMITY = 50 # 수평 인접 자식 탐색 시 허용 X 좌표 간격 (50px)

# ============================================================================
# 메인 함수: 레이아웃 유형 판별 후 정렬
# ============================================================================

def sort_layout_elements(
    elements: List[MockElement],
    document_type: str = "question_based", # 기본 문서 타입: 문제지
    page_width: Optional[int] = None, # 페이지 너비 (없으면 자동 계산)
    page_height: Optional[int] = None # 페이지 높이 (없으면 자동 계산)
) -> List[MockElement]:
    """
    레이아웃 유형 판별 후 맞춤형 정렬 로직 적용 (v.LayoutDetect.1)

    Args:
        elements: 정렬할 MockElement 객체 리스트.
        document_type: 문서 타입 ('question_based' 또는 'reading_order').
        page_width: 페이지 너비 (픽셀 단위). None이면 요소들로부터 추정.
        page_height: 페이지 높이 (픽셀 단위). None이면 요소들로부터 추정.

    Returns:
        List[MockElement]: 정렬된 MockElement 리스트. 각 요소에는
                           `order_in_question`, `group_id`, `order_in_group` 속성이 추가됨.
    """
    logger.info(f"맞춤형 정렬(v.LayoutDetect.1) 시작: {len(elements)}개 요소, 타입={document_type}")

    # 0단계: 전처리 (허용 클래스 필터링 등)
    filtered_elements = preprocess_elements(elements, document_type)
    if not filtered_elements:
        logger.warning("전처리 후 정렬할 요소가 없습니다.")
        return []

    # 페이지 크기 계산 (주어지지 않은 경우)
    if page_width is None:
        page_width = calculate_page_width(filtered_elements)
    if page_height is None:
        page_height = calculate_page_height(filtered_elements)
    logger.info(f"페이지 크기: {page_width} x {page_height}")

    # 초기 전체 페이지 구역 설정
    initial_zone = Zone(x_min=0, y_min=0, x_max=page_width, y_max=page_height)
    grouped_results: List[ElementGroup] = [] # 최종 그룹핑 결과를 담을 리스트

    try:
        # 문서 타입이 'reading_order'인 경우 (일반 문서)
        if document_type == "reading_order":
            layout_type = LayoutType.READING_ORDER
            logger.info(f"판별된 레이아웃 유형: {layout_type.name} (문서 타입 지정)")
            # Y/X 좌표 순서로 정렬
            sorted_elements_reading = sorted(filtered_elements, key=lambda e: (e.y_position, e.x_position))
            # 각 요소를 개별 그룹으로 만듦 (그룹핑 의미 없음)
            grouped_results = [ElementGroup(anchor=None, children=[elem]) for elem in sorted_elements_reading]
        # 문서 타입이 'question_based'인 경우 (문제지)
        else:
            # 1단계: ⭐ 레이아웃 유형 판별 ⭐
            layout_type = detect_layout_type(filtered_elements, page_width, page_height)
            logger.info(f"판별된 레이아웃 유형: {layout_type.name}")

            # 2단계: ⭐ 유형별 맞춤 로직 호출 ⭐
            # 표준 2단 처리
            if layout_type == LayoutType.STANDARD_2_COLUMN:
                 # _sort_standard_2_column 함수는 그룹 리스트를 반환
                 grouped_results = _sort_standard_2_column(initial_zone, filtered_elements)
            # 수평 분할 우선 처리 (넓은 타입 또는 혼합형) 또는 UNKNOWN
            elif layout_type == LayoutType.HORIZONTAL_SEP_PRESENT or \
                 layout_type == LayoutType.MIXED_TOP1_BOTTOM2 or \
                 layout_type == LayoutType.MIXED_TOP2_BOTTOM1 or \
                 layout_type == LayoutType.UNKNOWN:
                 # 재귀 함수 _sort_recursive_by_layout 호출 (최종 그룹 리스트 반환)
                 grouped_results = _sort_recursive_by_layout(initial_zone, filtered_elements, layout_type, depth=0)
            # 표준 1단 처리
            elif layout_type == LayoutType.STANDARD_1_COLUMN:
                 # 전체 구역에 대해 하이브리드 그룹핑 전략 실행 (그룹 리스트 반환)
                 logger.debug(f"{layout_type.name}: 분할 없이 전체 구역 하이브리드 그룹핑 실행")
                 grouped_results = hybrid_grouping_strategy(initial_zone, filtered_elements)
            # 예외 처리: 판별된 유형을 처리할 수 없는 경우 (Fallback)
            else:
                 logger.error(f"처리할 수 없는 레이아웃 유형: {layout_type.name}. (Y,X) 정렬로 대체합니다.")
                 sorted_elements_fallback = sorted(filtered_elements, key=lambda e: (e.y_position, e.x_position))
                 grouped_results = [ElementGroup(anchor=None, children=[elem]) for elem in sorted_elements_fallback]

    # 예상치 못한 오류 발생 시 (Fallback)
    except Exception as e:
        logger.error(f"맞춤형 정렬 중 심각한 오류 발생: {e}. (Y,X) 좌표 정렬로 대체합니다.", exc_info=True)
        sorted_elements_fallback = sorted(filtered_elements, key=lambda e: (e.y_position, e.x_position))
        grouped_results = [ElementGroup(anchor=None, children=[elem]) for elem in sorted_elements_fallback]

    # --- ⭐ 3단계: 최종 병합 및 순서 부여 (수정된 로직) ⭐ ---
    if not grouped_results:
        logger.warning("그룹핑 결과가 비어 있습니다.")
        return []

    # 1. 전역 고아 그룹 처리 (앵커가 없는 그룹)
    orphan_groups = [g for g in grouped_results if g.anchor is None] # 앵커 없는 그룹 필터링
    non_orphan_groups = [g for g in grouped_results if g.anchor is not None] # 앵커 있는 그룹 필터링
    final_ordered_groups: List[ElementGroup] = [] # 최종 순서가 적용된 그룹 리스트

    if orphan_groups:
        # 고아 그룹들을 첫 자식 요소의 Y 좌표 기준으로 정렬
        orphan_groups.sort(key=lambda g: min(c.y_position for c in g.children) if g.children else float('inf'))
        logger.debug(f"전역 고아 그룹 {len(orphan_groups)}개 발견, 리스트 맨 앞으로 이동시킴")
        final_ordered_groups.extend(orphan_groups) # 최종 리스트 앞에 추가
    else:
        logger.debug("전역 고아 그룹 없음")

    # 앵커 그룹들은 이미 유형별 처리 과정에서 적절한 순서(컬럼 우선 또는 Y순서)로 병합되었으므로 추가 정렬 불필요
    final_ordered_groups.extend(non_orphan_groups) # 최종 리스트 뒤에 추가

    # 2. 그룹 리스트 평탄화 및 순서 속성(order_in_question, group_id, order_in_group) 부여
    final_sorted_elements, _, _ = flatten_groups_and_assign_order(final_ordered_groups, start_global_order=0, start_group_id=0)

    logger.info(f"맞춤형 정렬 완료: {len(final_sorted_elements)}개 요소")
    return final_sorted_elements


# ============================================================================
# 레이아웃 유형 판별 함수
# ============================================================================

def detect_layout_type(elements: List[MockElement], page_width: int, page_height: int) -> LayoutType:
    """앵커 요소 분포를 분석하여 페이지 레이아웃 유형 판별"""
    # 앵커 요소만 필터링
    anchors = [e for e in elements if e.class_name in ALLOWED_ANCHORS]
    # 앵커 수가 최소 기준 미만이면 1단으로 간주
    if len(anchors) < MIN_ANCHORS_FOR_SPLIT:
        logger.debug(f"레이아웃 판별: 앵커 수({len(anchors)}) 부족 -> STANDARD_1_COLUMN")
        return LayoutType.STANDARD_1_COLUMN

    # 1. 넓은 question_type 확인 (수평 분할 가능성)
    top_zone_height = page_height * HORIZONTAL_SEP_Y_POS_THRESHOLD # 상위 15% 영역
    wide_q_type = find_wide_question_type(elements, page_width, top_zone_height)
    if wide_q_type:
        logger.debug(f"레이아웃 판별: 넓은 question_type(ID:{wide_q_type.element_id}) 존재 -> HORIZONTAL_SEP_PRESENT")
        return LayoutType.HORIZONTAL_SEP_PRESENT

    # 2. 앵커 X 좌표 분포 분석 (K-Means)
    anchor_x_centers = np.array([[a.bbox_x + a.bbox_width / 2] for a in anchors]) # 앵커 중심 X 좌표 추출
    is_clearly_2_column = False # 명확한 2단 구조인지 여부 플래그
    if len(np.unique(anchor_x_centers)) >= 2: # X 좌표 값이 2개 이상 다른 경우만 K-Means 시도
        try:
            kmeans = KMeans(n_clusters=KMEANS_N_CLUSTERS, random_state=42, n_init='auto')
            kmeans.fit(anchor_x_centers)
            centers = sorted(kmeans.cluster_centers_.flatten()) # 클러스터 중심 X 좌표 정렬
            # 클러스터 중심이 2개이고, 거리가 최소 기준 이상이면 2단 구조 가능성 높음
            if len(centers) == 2 and centers[1] - centers[0] >= KMEANS_CLUSTER_SEPARATION_MIN:
                is_clearly_2_column = True
                logger.trace(f"레이아웃 판별: 전체 X 분포는 2단 구조 가능성 높음 (Centers: {centers})")
            else:
                logger.trace(f"레이아웃 판별: 전체 X 분포는 1단 구조 또는 불분명")
        except Exception as e:
            logger.warning(f"레이아웃 판별 중 K-Means 오류 발생: {e}") # K-Means 오류 처리

    # 3. Y 좌표 분포와 결합하여 혼합형 판별 (명확히 2단 구조일 가능성이 있을 때)
    if is_clearly_2_column:
        split_y = page_height * LAYOUT_DETECT_Y_SPLIT_POINT # 상/하단 분할 기준 Y 좌표
        # 상단과 하단 영역의 앵커 분리
        top_anchors = [a for a in anchors if (a.y_position + a.bbox_height / 2) < split_y]
        bottom_anchors = [a for a in anchors if (a.y_position + a.bbox_height / 2) >= split_y]

        # 상단 또는 하단에 앵커가 없으면 혼합형 판별 불가 -> 표준 2단으로 간주
        if not top_anchors or not bottom_anchors:
             logger.debug("레이아웃 판별: 상/하단 앵커 그룹 불완전 -> STANDARD_2_COLUMN")
             return LayoutType.STANDARD_2_COLUMN

        # 상/하단 각각의 X 좌표 중심 추출
        top_x_centers = np.array([[a.bbox_x + a.bbox_width / 2] for a in top_anchors]) if top_anchors else np.array([])
        bottom_x_centers = np.array([[a.bbox_x + a.bbox_width / 2] for a in bottom_anchors]) if bottom_anchors else np.array([])

        # X 좌표 표준편차 임계값 계산
        x_std_threshold = page_width * LAYOUT_DETECT_X_STD_THRESHOLD_RATIO
        # 각 영역이 다단 구조인지(X 좌표 분산이 큰지) 판별
        top_is_multi_column = top_x_centers.size > 1 and np.std(top_x_centers) > x_std_threshold
        bottom_is_multi_column = bottom_x_centers.size > 1 and np.std(bottom_x_centers) > x_std_threshold

        # 혼합형 판별 로직
        if not top_is_multi_column and bottom_is_multi_column: # 상단 1단, 하단 2단
            logger.debug(f"레이아웃 판별: 상단({len(top_anchors)}개) 1단, 하단({len(bottom_anchors)}개) 2단 -> MIXED_TOP1_BOTTOM2")
            return LayoutType.MIXED_TOP1_BOTTOM2
        elif top_is_multi_column and not bottom_is_multi_column: # 상단 2단, 하단 1단
             logger.debug(f"레이아웃 판별: 상단({len(top_anchors)}개) 2단, 하단({len(bottom_anchors)}개) 1단 -> MIXED_TOP2_BOTTOM1")
             return LayoutType.MIXED_TOP2_BOTTOM1
        elif top_is_multi_column and bottom_is_multi_column: # 상단 2단, 하단 2단
             logger.debug(f"레이아웃 판별: 상단({len(top_anchors)}개) 2단, 하단({len(bottom_anchors)}개) 2단 -> STANDARD_2_COLUMN")
             return LayoutType.STANDARD_2_COLUMN
        else: # 상단 1단, 하단 1단인데 전체적으로는 2단 가능성이 높은 경우 -> UNKNOWN
             logger.warning(f"레이아웃 판별: 상/하단 모두 1단으로 보이나 전체는 2단 구조? -> UNKNOWN")
             return LayoutType.UNKNOWN
    # 명확한 2단 구조 가능성이 낮으면 1단으로 판별
    else:
        logger.debug("레이아웃 판별: 전체 1단 구조 -> STANDARD_1_COLUMN")
        return LayoutType.STANDARD_1_COLUMN

# ============================================================================
# 유형별 정렬 함수
# ============================================================================

def _sort_recursive_by_layout(current_zone: Zone, elements_in_zone: List[MockElement], layout_type: LayoutType, depth: int) -> List[ElementGroup]:
    """레이아웃 유형에 따라 다른 분할 우선순위를 적용하는 재귀 함수"""
    indent = "  " * depth # 재귀 깊이에 따른 들여쓰기 (로그용)
    logger.debug(f"{indent}[Depth {depth}, Type: {layout_type.name}] 구역 처리 시작: {current_zone}, 요소 수={len(elements_in_zone)}")

    # 재귀 종료 조건: 구역 내 요소가 없거나 1개인 경우
    if not elements_in_zone:
        logger.trace(f"{indent} -> 빈 구역, 빈 리스트 반환")
        return []
    if len(elements_in_zone) == 1:
        element = elements_in_zone[0]
        logger.trace(f"{indent} -> 요소 1개, 그룹으로 만들어 반환")
        # 요소가 앵커면 앵커 그룹, 아니면 고아 그룹 생성
        if element.class_name in ALLOWED_ANCHORS:
            return [ElementGroup(anchor=element)]
        else:
            return [ElementGroup(anchor=None, children=[element])]

    split_result: Optional[Union[HorizontalSplit, HorizontalSplitYGap, VerticalSplit]] = None # 분할 결과 저장 변수
    split_type = "None" # 분할 유형 저장 변수 (로그용)

    # --- 분할 시도 (유형별 우선순위 적용) ---
    # 1. HORIZONTAL_SEP_PRESENT: 넓은 타입 수평 분할 우선
    if layout_type == LayoutType.HORIZONTAL_SEP_PRESENT:
        split_result = find_horizontal_split_by_type(current_zone, elements_in_zone)
        if split_result: split_type = "H_Type"
        else: # Type 분할 실패 시 Vertical(K-Means) -> Y Gap 순서로 시도
            anchors = [e for e in elements_in_zone if e.class_name in ALLOWED_ANCHORS]
            split_result = find_vertical_split_kmeans(current_zone, anchors)
            if split_result: split_type = "Vertical"
            else:
                 split_result = find_horizontal_split_by_y_gap(current_zone, elements_in_zone)
                 if split_result: split_type = "H_YGap"

    # 2. MIXED_...: Y Gap 수평 분할 우선
    elif layout_type == LayoutType.MIXED_TOP1_BOTTOM2 or layout_type == LayoutType.MIXED_TOP2_BOTTOM1:
        split_result = find_horizontal_split_by_y_gap(current_zone, elements_in_zone)
        if split_result: split_type = "H_YGap"
        else: # Y Gap 실패 시 Type -> Vertical 순서로 시도
             split_result = find_horizontal_split_by_type(current_zone, elements_in_zone)
             if split_result: split_type = "H_Type"
             else:
                  anchors = [e for e in elements_in_zone if e.class_name in ALLOWED_ANCHORS]
                  split_result = find_vertical_split_kmeans(current_zone, anchors)
                  if split_result: split_type = "Vertical"
    
    # 3. STANDARD_2_COLUMN: 별도 함수 호출
    elif layout_type == LayoutType.STANDARD_2_COLUMN:
        logger.debug(f"{indent} -> {layout_type.name}: 표준 2단 처리 함수 호출")
        # _sort_standard_2_column 함수를 직접 호출하여 그룹 리스트 반환 받기
        return _sort_standard_2_column(current_zone, elements_in_zone)
        # (대안: 여기서 K-Means 수직 분할 시도 후 재귀 호출)
        # anchors = [e for e in elements_in_zone if e.class_name in ALLOWED_ANCHORS]
        # split_result = find_vertical_split_kmeans(current_zone, anchors)
        # if split_result: split_type = "Vertical"
        # else: pass # 분할 실패 시 아래 Base Case 로직으로 넘어감

    # 4. UNKNOWN 또는 Fallback: 기본 우선순위 (Type -> Vertical -> Y Gap)
    elif layout_type == LayoutType.UNKNOWN:
        split_result = find_horizontal_split_by_type(current_zone, elements_in_zone)
        if split_result: split_type = "H_Type"
        else:
             anchors = [e for e in elements_in_zone if e.class_name in ALLOWED_ANCHORS]
             split_result = find_vertical_split_kmeans(current_zone, anchors)
             if split_result: split_type = "Vertical"
             else:
                  split_result = find_horizontal_split_by_y_gap(current_zone, elements_in_zone)
                  if split_result: split_type = "H_YGap"

    # --- 분할 성공 시 재귀 호출 ---
    if split_result:
        # 수평 분할 성공 시
        if isinstance(split_result, (HorizontalSplit, HorizontalSplitYGap)):
            # 분할 기준 Y 좌표 계산
            split_y = split_result.split_y if isinstance(split_result, HorizontalSplitYGap) else \
                      split_result.separator_element.y_position + split_result.separator_element.bbox_height / 2
            # 분할된 구역에 속하는 요소 필터링 (분할 기준 요소 제외)
            top_elements = [e for e in elements_in_zone if getattr(e, 'element_id', -1) != getattr(getattr(split_result,'separator_element',None),'element_id',-2) and (e.bbox_y + e.bbox_height / 2) < split_y]
            bottom_elements = [e for e in elements_in_zone if getattr(e, 'element_id', -1) != getattr(getattr(split_result,'separator_element',None),'element_id',-2) and (e.bbox_y + e.bbox_height / 2) >= split_y]
            logger.debug(f"{indent} -> {split_type} 수평 분할 성공! Top:{len(top_elements)}, Bottom:{len(bottom_elements)}")

            # 하위 구역 레이아웃 유형 재판별
            top_layout_type = detect_layout_type(top_elements, split_result.top_zone.width, split_result.top_zone.height) if top_elements else LayoutType.UNKNOWN
            bottom_layout_type = detect_layout_type(bottom_elements, split_result.bottom_zone.width, split_result.bottom_zone.height) if bottom_elements else LayoutType.UNKNOWN

            # 각 하위 구역에 대해 재귀 호출
            sorted_top = _sort_recursive_by_layout(split_result.top_zone, top_elements, top_layout_type, depth + 1)
            # 분할 기준 요소(question_type)가 있었으면 별도 그룹으로 추가
            sep_group = [ElementGroup(anchor=split_result.separator_element)] if isinstance(split_result, HorizontalSplit) else []
            sorted_bottom = _sort_recursive_by_layout(split_result.bottom_zone, bottom_elements, bottom_layout_type, depth + 1)
            logger.debug(f"{indent} <- {split_type} 수평 분할 결과 병합")
            # 상단 그룹 + 분할 그룹 + 하단 그룹 순서로 결과 병합
            return sorted_top + sep_group + sorted_bottom

        # 수직 분할 성공 시
        elif isinstance(split_result, VerticalSplit):
            # 분할된 구역에 속하는 요소 필터링
            left_elements = [e for e in elements_in_zone if (e.bbox_x + e.bbox_width / 2) < split_result.gutter_x]
            right_elements = [e for e in elements_in_zone if (e.bbox_x + e.bbox_width / 2) >= split_result.gutter_x]
            logger.debug(f"{indent} -> Vertical 수직 분할 성공! Left:{len(left_elements)}, Right:{len(right_elements)}")

            # 하위 구역 레이아웃 유형 재판별
            left_layout_type = detect_layout_type(left_elements, split_result.left_zone.width, split_result.left_zone.height) if left_elements else LayoutType.UNKNOWN
            right_layout_type = detect_layout_type(right_elements, split_result.right_zone.width, split_result.right_zone.height) if right_elements else LayoutType.UNKNOWN

            # 각 하위 구역에 대해 재귀 호출
            sorted_left = _sort_recursive_by_layout(split_result.left_zone, left_elements, left_layout_type, depth + 1)
            sorted_right = _sort_recursive_by_layout(split_result.right_zone, right_elements, right_layout_type, depth + 1)
            logger.debug(f"{indent} <- Vertical 수직 분할 결과 병합")
            # 좌측 그룹 + 우측 그룹 순서로 결과 병합
            return sorted_left + sorted_right

    # --- 분할 실패 시 Base Case 실행 (Hybrid Grouping) ---
    logger.debug(f"{indent} -> 모든 분할 실패 (또는 1단 유형), 기본 구역 정렬 (Base Case - Hybrid) 실행")
    result_groups = hybrid_grouping_strategy(current_zone, elements_in_zone)
    logger.debug(f"{indent} <- 기본 구역 정렬 완료: {len(result_groups)} 그룹 생성")
    return result_groups


def _sort_standard_2_column(zone: Zone, elements: List[MockElement]) -> List[ElementGroup]:
    """표준 2단 레이아웃 처리: K-Means 분할 후 컬럼별 hybrid_grouping"""
    logger.debug("표준 2단 처리: K-Means 분할 시도")
    anchors = [e for e in elements if e.class_name in ALLOWED_ANCHORS]
    # K-Means를 이용한 수직 분할 시도
    vertical_split = find_vertical_split_kmeans(zone, anchors)

    if vertical_split:
        logger.debug(f" -> 수직 분할 성공! 분리선 X={vertical_split.gutter_x:.1f}")
        # 좌우 컬럼 요소 분리
        left_elements = [e for e in elements if (e.bbox_x + e.bbox_width / 2) < vertical_split.gutter_x]
        right_elements = [e for e in elements if (e.bbox_x + e.bbox_width / 2) >= vertical_split.gutter_x]
        logger.debug(f"   Left 요소 수: {len(left_elements)}, Right 요소 수: {len(right_elements)}")

        # 각 컬럼에 대해 하이브리드 그룹핑 전략 실행
        groups_left = hybrid_grouping_strategy(vertical_split.left_zone, left_elements)
        groups_right = hybrid_grouping_strategy(vertical_split.right_zone, right_elements)
        logger.debug(f" <- 컬럼별 그룹핑 완료 (Left: {len(groups_left)} 그룹, Right: {len(groups_right)} 그룹)")
        # 좌측 그룹 + 우측 그룹 순서로 결과 병합
        return groups_left + groups_right
    else:
        # 수직 분할 실패 시, 전체 구역에 대해 하이브리드 그룹핑 실행 (Fallback)
        logger.warning("표준 2단 처리 실패: 수직 분할 불가. 전체 구역 하이브리드 그룹핑 실행")
        return hybrid_grouping_strategy(zone, elements)

# ============================================================================
# 분할 함수 구현 (find_..._by_type, _by_y_gap, _kmeans)
# ============================================================================

def find_wide_question_type(elements: List[MockElement], page_width: int, top_y_limit: float) -> Optional[MockElement]:
     """페이지 상단 영역(top_y_limit 아래)에서 가장 위에 있는 넓은(80% 이상) question_type 찾기"""
     wide_types = [
         e for e in elements
         if e.class_name == "question_type" and \
            e.y_position < top_y_limit and \
            (e.bbox_width / page_width if page_width > 0 else 0) >= HORIZONTAL_SEP_WIDTH_THRESHOLD
     ]
     # 가장 Y 좌표가 작은(가장 위에 있는) 요소를 반환
     return min(wide_types, key=lambda e: e.y_position) if wide_types else None

def find_horizontal_split_by_type(zone: Zone, elements: List[MockElement]) -> Optional[HorizontalSplit]:
    """넓은(80% 이상) question_type 요소를 찾아 수평 분할 정보 반환"""
    potential_separators = [] # 분할 기준 후보 요소 리스트
    for element in elements:
        if element.class_name == "question_type":
            width_ratio = element.bbox_width / zone.width if zone.width > 0 else 0
            # 너비 비율이 임계값 이상이면 후보에 추가
            if width_ratio >= HORIZONTAL_SEP_WIDTH_THRESHOLD:
                potential_separators.append(element)
    if not potential_separators: return None # 후보 없음

    # 후보 중 가장 위에 있는(Y 좌표가 가장 작은) 요소를 분할 기준으로 선택
    separator = min(potential_separators, key=lambda e: e.y_position)
    # 분할 기준 요소의 Y 좌표가 현재 구역 내부에 있는지 확인 (구역 경계 제외)
    if not (zone.y_min < separator.y_position < zone.y_max): return None
    # 상단 구역 정의 (현재 구역 상단 ~ 분할 기준 요소 상단)
    top_zone = Zone(zone.x_min, zone.y_min, zone.x_max, separator.y_position)
    # 하단 구역 정의 (분할 기준 요소 하단 ~ 현재 구역 하단)
    bottom_zone = Zone(zone.x_min, separator.y_position + separator.bbox_height, zone.x_max, zone.y_max)
    # 분할된 구역의 높이가 0 이하이면 유효하지 않은 분할
    if top_zone.height <= 0 or bottom_zone.height <= 0: return None
    # 분할 정보 반환
    return HorizontalSplit(top_zone, bottom_zone, separator)

def find_horizontal_split_by_y_gap(zone: Zone, elements: List[MockElement]) -> Optional[HorizontalSplitYGap]:
    """앵커 요소들 간의 Y 좌표 간격(Gap)을 분석하여 수평 분할 정보 반환"""
    # 앵커 요소만 필터링 후 Y 좌표 기준으로 정렬
    anchors = sorted([e for e in elements if e.class_name in ALLOWED_ANCHORS], key=lambda e: e.y_position)
    if len(anchors) < MIN_ANCHORS_FOR_SPLIT: return None # 앵커 부족

    max_gap = -1 # 최대 간격 초기화
    split_index = -1 # 최대 간격이 발생한 앵커 인덱스 초기화
    # 평균 앵커 높이 계산 (간격 임계값 계산용)
    avg_anchor_height = np.mean([a.bbox_height for a in anchors if a.bbox_height > 0]) if any(a.bbox_height > 0 for a in anchors) else 30

    # 인접한 앵커들 사이의 Y 중심 좌표 간격 계산
    for i in range(len(anchors) - 1):
        gap = (anchors[i+1].y_position + anchors[i+1].bbox_height / 2) - \
              (anchors[i].y_position + anchors[i].bbox_height / 2)
        if gap > max_gap: max_gap = gap; split_index = i # 최대 간격 및 인덱스 업데이트

    # 분할 임계값 계산 (상대 임계값과 절대 임계값 중 큰 값 사용)
    threshold = max(avg_anchor_height * VERTICAL_GAP_THRESHOLD_RATIO, VERTICAL_GAP_THRESHOLD_ABS)
    # 최대 간격이 임계값 이상이면 수평 분할 가능
    if max_gap >= threshold:
        # 분할 기준 Y 좌표 계산 (최대 간격 사이의 중간 지점)
        split_y = (anchors[split_index].y_position + anchors[split_index].bbox_height + anchors[split_index + 1].y_position) / 2
        # 분할선이 구역 내부에 있는지 확인
        if zone.y_min < split_y < zone.y_max:
            # 상/하단 구역 정의
            top_zone = Zone(zone.x_min, zone.y_min, zone.x_max, int(split_y))
            bottom_zone = Zone(zone.x_min, int(split_y), zone.x_max, zone.y_max)
            logger.debug(f"    Y Gap 분석: 수평 분할 가능 (Max Gap={max_gap:.1f} >= Threshold={threshold:.1f})")
            return HorizontalSplitYGap(top_zone, bottom_zone, split_y)
        else:
            logger.warning(f"    Y Gap 분석: 분할선({split_y:.1f})이 구역({zone.y_min}-{zone.y_max}) 밖에 위치. 분할 취소.")
            return None
    else:
        logger.debug(f"    Y Gap 분석: 최대 간격({max_gap:.1f}) 임계값({threshold:.1f}) 미만. 수평 분할 불가.")
        return None

def find_vertical_split_kmeans(zone: Zone, anchors: List[MockElement]) -> Optional[VerticalSplit]:
    """앵커 요소들의 X 좌표를 K-Means 클러스터링하여 수직 분할 정보 반환"""
    if len(anchors) < MIN_ANCHORS_FOR_SPLIT: return None # 앵커 부족
    # 앵커 중심 X 좌표 추출
    anchor_x_centers = np.array([[a.bbox_x + a.bbox_width / 2] for a in anchors])
    if len(np.unique(anchor_x_centers)) < 2: return None # X 좌표 값이 모두 같으면 분할 불가

    try:
        # K-Means 실행 (클러스터 수 = 2)
        kmeans = KMeans(n_clusters=KMEANS_N_CLUSTERS, random_state=42, n_init='auto')
        kmeans.fit(anchor_x_centers)
        centers = sorted(kmeans.cluster_centers_.flatten()) # 클러스터 중심 X 좌표 정렬

        # 클러스터 중심이 2개이고 거리가 임계값 이상이면 수직 분할 가능
        if len(centers) == 2 and centers[1] - centers[0] >= KMEANS_CLUSTER_SEPARATION_MIN:
            # 분할 기준 X 좌표 계산 (두 중심의 중간 지점)
            gutter_x = (centers[0] + centers[1]) / 2
            # 분할선이 구역 내부에 있는지 확인
            if zone.x_min < gutter_x < zone.x_max:
                 # 좌/우 구역 정의
                 left_zone = Zone(zone.x_min, zone.y_min, int(gutter_x), zone.y_max)
                 right_zone = Zone(int(gutter_x), zone.y_min, zone.x_max, zone.y_max)
                 return VerticalSplit(left_zone, right_zone, gutter_x)
            else:
                logger.warning(f"    수직 분할 K-Means: 분할선({gutter_x:.1f})이 구역({zone.x_min}-{zone.x_max}) 밖에 위치. 분할 취소.")
                return None
        else:
            logger.debug(f"    수직 분할 K-Means 실패: 중심간 거리({(centers[1] - centers[0]) if len(centers)==2 else 0:.1f}px) 임계값 미만")
            return None
    except Exception as e:
        logger.error(f"    수직 분할 K-Means 중 오류: {e}")
        return None

# ============================================================================
# 하이브리드 그룹핑 함수 (Base Case 역할도 수행)
# ============================================================================

def hybrid_grouping_strategy(zone: Zone, elements: List[MockElement]) -> List[ElementGroup]:
    """
    구역 내 요소 그룹핑: 수평 인접 우선 처리 후, 나머지 (Y, X) 정렬 및 앵커 그룹핑
    재귀 호출의 Base Case 역할 또는 단일 컬럼 처리에 사용됨.
    """
    logger.debug(f"    하이브리드 그룹핑 시작 (Base Case 역할): {len(elements)}개 요소 in {zone}")
    # 앵커와 자식 요소 분리 (앵커는 Y 좌표 정렬)
    anchors = sorted([e for e in elements if e.class_name in ALLOWED_ANCHORS], key=lambda e: e.y_position)
    children = [e for e in elements if e.class_name in ALLOWED_CHILDREN]
    # 각 앵커를 키로 하는 그룹 딕셔너리 초기화
    groups: Dict[int, ElementGroup] = {anchor.element_id: ElementGroup(anchor=anchor) for anchor in anchors}
    assigned_children_ids = set() # 이미 그룹에 할당된 자식 요소 ID 집합

    # 1단계: 수평 인접 처리 (앵커 바로 옆에 있는 자식 요소 우선 할당)
    logger.trace("      수평 인접 처리 시작...")
    if anchors and children:
        for anchor in anchors:
            # 앵커의 Y 중심, 좌/우 X 좌표 계산
            anchor_cy = anchor.bbox_y + anchor.bbox_height / 2
            anchor_right_x = anchor.bbox_x + anchor.bbox_width
            anchor_left_x = anchor.bbox_x
            # 아직 할당되지 않은 자식 요소만 대상
            unassigned_children = [c for c in children if c.element_id not in assigned_children_ids]
            adjacent_child = None # 가장 인접한 자식 요소 저장 변수
            min_y_diff = float('inf') # 가장 작은 Y 중심 차이 저장 변수

            for child in unassigned_children:
                # 자식의 Y 중심, 좌/우 X 좌표 계산
                child_cy = child.bbox_y + child.bbox_height / 2
                child_right_x = child.bbox_x + child.bbox_width
                child_left_x = child.bbox_x
                # Y 중심 좌표 차이 및 임계값 계산
                y_diff = abs(anchor_cy - child_cy)
                y_threshold = (anchor.bbox_height + child.bbox_height) / 2 * HORIZONTAL_ADJACENCY_Y_CENTER_RATIO if (anchor.bbox_height + child.bbox_height)>0 else 0
                # Y 중심 차이가 임계값 이상이면 수평 인접으로 보지 않음
                if y_diff >= y_threshold: continue
                # X 좌표 간격 계산 (앵커 오른쪽에 자식, 앵커 왼쪽에 자식)
                gap_right = child_left_x - anchor_right_x
                gap_left = anchor_left_x - child_right_x
                # 간격이 0 이상이고 임계값 미만이면 인접한 것으로 판단
                is_adjacent = (0 <= gap_right < HORIZONTAL_ADJACENCY_X_PROXIMITY) or (0 <= gap_left < HORIZONTAL_ADJACENCY_X_PROXIMITY)
                # 인접하고, Y 중심 차이가 현재까지 최소이면 업데이트
                if is_adjacent and y_diff < min_y_diff:
                    min_y_diff = y_diff
                    adjacent_child = child

            # 가장 인접한 자식을 찾았으면 그룹에 추가하고 할당 처리
            if adjacent_child:
                logger.trace(f"        수평 인접 배정: 앵커 ID {anchor.element_id} <- 자식 ID {adjacent_child.element_id}")
                groups[anchor.element_id].add_child(adjacent_child)
                assigned_children_ids.add(adjacent_child.element_id)
    logger.debug(f"    수평 인접 처리 완료: {len(assigned_children_ids)}개 자식 우선 배정됨")

    # 2단계: 나머지 요소 처리 (Y/X 정렬 기반 그룹핑)
    # 수평 인접으로 할당되지 않은 요소들 + 앵커 요소들
    remaining_elements = anchors + [c for c in children if c.element_id not in assigned_children_ids]
    # 남은 요소가 없으면 그룹핑 완료
    if not remaining_elements:
        logger.debug("    모든 요소가 수평 인접으로 배정되어 그룹핑 완료.")
        # 앵커 Y 좌표 기준으로 그룹 정렬 후 반환
        return sorted(list(groups.values()), key=lambda g: g.anchor.y_position if g.anchor else float('inf'))

    logger.trace(f"      나머지 요소 {len(remaining_elements)}개 (Y, X) 정렬 및 그룹핑 시작...")
    # 남은 요소들을 Y/X 좌표 순서로 정렬 (읽기 순서)
    remaining_elements.sort(key=lambda e: (e.y_position, e.x_position))
    final_groups: List[ElementGroup] = [] # 최종 그룹 리스트
    current_group = ElementGroup(anchor=None, children=[]) # 현재 처리 중인 그룹 (초기에는 고아 그룹)

    # 정렬된 요소들을 순회하며 그룹 구축
    for element in remaining_elements:
        # 요소가 앵커인 경우
        if element.class_name in ALLOWED_ANCHORS:
            # 이 앵커가 이미 수평 인접 처리에서 그룹을 가지고 있다면 해당 그룹 재사용
            if element.element_id in groups:
                 if not current_group.is_empty(): final_groups.append(current_group) # 이전 그룹(고아 또는 다른 앵커) 완료 처리
                 current_group = groups[element.element_id] # 해당 앵커 그룹으로 전환
                 logger.trace(f"        앵커 그룹 재사용 (ID: {element.element_id})")
            # 새로운 앵커인 경우
            else:
                 if not current_group.is_empty(): final_groups.append(current_group) # 이전 그룹 완료 처리
                 current_group = ElementGroup(anchor=element, children=[]) # 새 앵커 그룹 시작
                 logger.trace(f"        새 앵커 그룹 시작 (ID: {element.element_id})")
        # 요소가 자식인 경우
        else:
            # 현재 그룹(current_group)에 자식 추가
            # (만약 첫 요소가 자식이면, 초기 current_group은 고아 그룹이 됨)
            current_group.add_child(element)
            logger.trace(f"        현재 그룹(앵커: {current_group.anchor.element_id if current_group.anchor else 'Orphan'})에 자식 추가 (ID: {element.element_id})")

    # 마지막 그룹 완료 처리
    if not current_group.is_empty(): final_groups.append(current_group)

    # 3단계: 누락된 앵커 그룹 추가 (수평 인접 자식만 가지고 Y/X 정렬에 포함되지 않은 앵커)
    processed_anchor_ids = set(g.anchor.element_id for g in final_groups if g.anchor) # 최종 그룹에 포함된 앵커 ID 집합
    for anchor_id, group in groups.items():
        if anchor_id not in processed_anchor_ids: # 처리되지 않은 앵커 그룹 추가
            final_groups.append(group)
            logger.trace(f"        미포함 앵커 그룹 추가 (ID: {anchor_id})")

    # 4단계: 그룹 최종 정렬 (앵커 Y 좌표 우선, 없으면 첫 자식 Y 좌표)
    final_groups.sort(key=lambda g: g.anchor.y_position if g.anchor else (min(c.y_position for c in g.children) if g.children else float('inf')))
    return final_groups


# ============================================================================
# 최종 병합 및 순서 부여 함수
# ============================================================================

def flatten_groups_and_assign_order(
    groups: List[ElementGroup], # 정렬 및 전역 고아 처리가 완료된 그룹 리스트
    start_global_order: int, # 시작 전역 순서 번호
    start_group_id: int # 시작 그룹 ID 번호
) -> Tuple[List[MockElement], int, int]: # (평탄화된 요소 리스트, 다음 순서 번호, 다음 그룹 ID)
    """주어진 그룹 리스트를 평탄화하고 전역 순서/그룹 ID 부여 (입력 그룹 순서 유지)"""
    flattened = [] # 평탄화된 요소 리스트
    global_order = start_global_order # 전역 순서 카운터
    group_id_counter = start_group_id # 그룹 ID 카운터
    logger.debug(f"    평탄화 시작: {len(groups)}개 그룹 (시작 order={global_order}, group_id={group_id_counter})")

    # 입력된 그룹 순서대로 처리
    for group in groups:
        # 그룹에 최종 group_id 부여
        group.group_id = group_id_counter
        # 그룹 내 요소를 Y/X 좌표 기준으로 정렬
        elements_in_group = group.get_all_elements_sorted()
        logger.trace(f"      그룹 {group_id_counter} 평탄화 (Anchor: {group.anchor.element_id if group.anchor else 'Orphan'}, 요소 수: {len(elements_in_group)})")

        # 그룹 내 요소들을 순회하며 속성 부여 및 평탄화 리스트에 추가
        for local_order, element in enumerate(elements_in_group):
            try:
                # MockElement 객체에 동적으로 속성 추가
                setattr(element, 'order_in_question', global_order) # 전역 순서
                setattr(element, 'group_id', group_id_counter) # 그룹 ID
                setattr(element, 'order_in_group', local_order) # 그룹 내 순서
                flattened.append(element)
                global_order += 1 # 전역 순서 증가
            except AttributeError as e:
                # setattr 실패 시 에러 로깅 (일반적으로 발생하지 않음)
                logger.error(f"요소 (ID: {getattr(element, 'element_id', 'N/A')})에 정렬 속성 추가 실패: {e}")
        group_id_counter += 1 # 다음 그룹 ID 증가

    logger.debug(f"    평탄화 완료: {len(flattened)}개 요소 생성 (다음 order={global_order}, group_id={group_id_counter})")
    # 평탄화된 요소 리스트, 다음 전역 순서, 다음 그룹 ID 반환
    return flattened, global_order, group_id_counter

# ============================================================================
# 헬퍼 함수
# ============================================================================

def preprocess_elements(elements: List[MockElement], document_type: str) -> List[MockElement]:
    """
    0단계 전처리: 문서 타입에 따라 허용된 클래스만 필터링하고, 면적이 0 이하인 요소 제거
    """
    original_count = len(elements)
    # 문제지 모드: ALLOWED_CLASSES (앵커 3종 + 자식 6종)만 허용
    if document_type == "question_based":
        filtered = [e for e in elements if e.class_name in ALLOWED_CLASSES]
        logger.info(f"전처리 (question_based): {original_count}개 → {len(filtered)}개 (허용 클래스 필터링)")
    # 일반 문서 모드: 모든 클래스 허용
    elif document_type == "reading_order":
        filtered = elements
        logger.info(f"전처리 (reading_order): {original_count}개 (모든 클래스 허용)")
    # 알 수 없는 문서 타입: 경고 후 모든 요소 반환
    else:
        logger.warning(f"알 수 없는 문서 타입 '{document_type}', 모든 요소 반환")
        filtered = elements

    # 면적이 0 이하인 요소 제거 (잘못된 BBox 등)
    valid_elements = [e for e in filtered if hasattr(e, 'area') and e.area > 0]
    if len(valid_elements) < len(filtered):
        logger.warning(f"전처리: 면적이 0 이하인 요소 {len(filtered) - len(valid_elements)}개 제거")

    return valid_elements

def calculate_page_width(elements: List[MockElement]) -> int:
    """요소 리스트로부터 페이지의 최대 너비 추정"""
    if not elements: return 0
    # 각 요소의 우측 X 좌표 (bbox_x + bbox_width) 중 최대값 반환
    return max(e.bbox_x + e.bbox_width for e in elements) if elements else 0

def calculate_page_height(elements: List[MockElement]) -> int:
    """요소 리스트로부터 페이지의 최대 높이 추정"""
    if not elements: return 0
    # 각 요소의 하단 Y 좌표 (bbox_y + bbox_height) 중 최대값 반환
    return max(e.bbox_y + e.bbox_height for e in elements) if elements else 0
