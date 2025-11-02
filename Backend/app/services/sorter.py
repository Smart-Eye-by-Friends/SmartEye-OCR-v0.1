# -*- coding: utf-8 -*-
"""
SmartEyeSsen Layout Sorter (v.LayoutDetect.2.4 - Tie-breaker in Post-processing)
=================================================================================

문제 레이아웃 정렬 알고리즘 구현 (Layout Type Detection 기반 Hybrid)
페이지 전체 레이아웃 유형(1단, 2단, 혼합형 등)을 먼저 판별하고,
유형에 맞는 분할 전략(수평/수직) 적용.
분할 실패 시(Base Case), 레이아웃 유형별로 특화된 그룹핑 로직 호출.
- 표준 1단/2단 컬럼: _base_case_standard_1_column
- 혼합형: _base_case_mixed_layout
최종 병합 시 전역 고아 그룹 처리 로직 적용.

알고리즘 흐름: (v.LayoutDetect.2.1/2.2/2.3과 동일)
0. 전처리
1. 레이아웃 유형 판별
2. 유형별 재귀 처리
3. Base Case 처리 (후처리 포함)
4. 최종 병합 및 순서 부여

v.LayoutDetect.2.4:
- _post_process_table_figure_assignment: 최적 그룹 탐색 시 Y 거리가 동일할 경우 더 뒤쪽 그룹을 우선하는 Tie-breaker 추가.
- sort_layout_elements: 후처리 호출 전에 임시 그룹 ID 할당하여 로그 가독성 개선.
- (v2.3 변경 유지) _post_process_table_figure_assignment: 최적 그룹 탐색 로직 (Lookahead).
- (v2.2 변경 유지) _post_process_table_figure_assignment: 이동 조건은 거리 비교 로직 사용.
- (v2.1 변경 유지) _post_process_table_figure_assignment: y_diff_threshold 기본값 150.
- (v2.1 변경 유지) _base_case_standard_1_column: 상단 고아 요소 분리 로직.
"""

# 필요한 라이브러리 임포트
from typing import List, Dict, Tuple, Optional, Any, Union, TYPE_CHECKING
from dataclasses import dataclass, field
import numpy as np
from sklearn.cluster import KMeans
from loguru import logger
import math
from enum import Enum, auto
import os

# Mock 모델 임포트 (호환성 유지용, 추후 제거 예정)
from .mock_models import MockElement

if TYPE_CHECKING:
    from sqlalchemy.orm import Session
    from ..models import LayoutElement


# ============================================================================
# 데이터 클래스 및 Enum 정의 (기존과 동일)
# ============================================================================

class LayoutType(Enum):
    STANDARD_1_COLUMN = auto()
    STANDARD_2_COLUMN = auto()
    MIXED_TOP1_BOTTOM2 = auto()
    MIXED_TOP2_BOTTOM1 = auto()
    HORIZONTAL_SEP_PRESENT = auto()
    READING_ORDER = auto()
    UNKNOWN = auto()

@dataclass
class Zone:
    x_min: int; y_min: int; x_max: int; y_max: int
    @property
    def width(self) -> int: return max(0, self.x_max - self.x_min)
    @property
    def height(self) -> int: return max(0, self.y_max - self.y_min)
    def __repr__(self) -> str: return f"Zone(x=[{self.x_min}, {self.x_max}), y=[{self.y_min}, {self.y_max}))"

@dataclass
class HorizontalSplit:
    top_zone: Zone
    bottom_zone: Zone
    separator_element: MockElement

@dataclass
class HorizontalSplitYGap:
    top_zone: Zone
    bottom_zone: Zone
    split_y: float

@dataclass
class VerticalSplit:
    left_zone: Zone
    right_zone: Zone
    gutter_x: float

@dataclass
class ElementGroup:
    anchor: Optional[MockElement]
    children: List[MockElement] = field(default_factory=list)
    group_id: int = -1 # flatten 함수에서 최종 할당, 후처리 전 임시 할당
    def add_child(self, child: MockElement): self.children.append(child)
    def get_all_elements_sorted(self) -> List[MockElement]:
        """
        그룹 내 요소들을 정렬합니다.
        - 앵커(Anchor)가 항상 가장 먼저 위치합니다.
        - 나머지 자식(Children) 요소들은 (Y, X) 좌표 순으로 정렬됩니다.
        """
        # 1. 앵커가 존재하면 리스트의 첫 요소로 설정합니다.
        elements = [self.anchor] if self.anchor else []
        
        # 2. 자식 요소들을 (Y, X) 좌표 기준으로 정렬합니다.
        sorted_children = sorted(self.children, key=lambda e: (e.y_position, e.x_position))
        
        # 3. 앵커 요소 뒤에 정렬된 자식 요소들을 추가합니다.
        elements.extend(sorted_children)
        
        return elements
    def is_empty(self) -> bool: return self.anchor is None and not self.children
    def __repr__(self) -> str:
        anchor_id = self.anchor.element_id if self.anchor else "Orphan"
        child_ids = sorted([c.element_id for c in self.children])
        # flatten 전에는 group_id가 임시값일 수 있음
        return f"Group(ID:{self.group_id}, Anchor: {anchor_id}, Children: {child_ids})"


# ============================================================================
# 상수 정의 (기존과 동일)
# ============================================================================

ALLOWED_ANCHORS = ["question type", "question number", "second_question_number"]
ALLOWED_CHILDREN = ["question text", "list", "choices", "figure", "table", "flowchart"]
ALLOWED_CLASSES = ALLOWED_ANCHORS + ALLOWED_CHILDREN

HORIZONTAL_SEP_WIDTH_THRESHOLD = 0.8
HORIZONTAL_SEP_Y_POS_THRESHOLD = 0.15
MIN_ANCHORS_FOR_SPLIT = 2
VERTICAL_GAP_THRESHOLD_RATIO = 1.5
VERTICAL_GAP_THRESHOLD_ABS = 100
KMEANS_N_CLUSTERS = 2
KMEANS_CLUSTER_SEPARATION_MIN = 50
LAYOUT_DETECT_Y_SPLIT_POINT = 0.4
LAYOUT_DETECT_X_STD_THRESHOLD_RATIO = 0.1

HORIZONTAL_ADJACENCY_Y_CENTER_RATIO = 0.7
HORIZONTAL_ADJACENCY_X_PROXIMITY = 50

BASE_CASE_TOP_ORPHAN_THRESHOLD_RATIO = 0.15
POST_PROCESS_CLOSENESS_RATIO = 0.5
POST_PROCESS_LOOKAHEAD = 2

# 2D 거리 기반 그룹핑 관련 상수
ANCHOR_VERTICAL_PROXIMITY_THRESHOLD = 250  # px - 앵커와 Y 거리 임계값
ANCHOR_2D_DISTANCE_WEIGHT_X = 0.2  # X 거리 가중치 (낮게 설정)
ANCHOR_2D_DISTANCE_WEIGHT_Y = 1.0  # Y 거리 가중치

# ============================================================================
# 메인 함수: 레이아웃 유형 판별 후 정렬 (수정됨)
# ============================================================================

def _sort_layout_elements_v24(
    elements: List[MockElement],
    document_type: str = "question_based",
    page_width: Optional[int] = None,
    page_height: Optional[int] = None
) -> List[MockElement]:
    """
    레이아웃 유형 판별 후 맞춤형 정렬 로직 적용 (v.LayoutDetect.2.4)
    """
    logger.info(f"맞춤형 정렬(v.LayoutDetect.2.4) 시작: {len(elements)}개 요소, 타입={document_type}")

    filtered_elements = preprocess_elements(elements, document_type)
    if not filtered_elements:
        logger.warning("전처리 후 정렬할 요소가 없습니다.")
        return []

    if page_width is None: page_width = calculate_page_width(filtered_elements)
    if page_height is None: page_height = calculate_page_height(filtered_elements)
    logger.info(f"페이지 크기: {page_width} x {page_height}")

    initial_zone = Zone(x_min=0, y_min=0, x_max=page_width, y_max=page_height)
    grouped_results: List[ElementGroup] = []

    try:
        if document_type == "reading_order":
            layout_type = LayoutType.READING_ORDER
            logger.info(f"판별된 레이아웃 유형: {layout_type.name} (문서 타입 지정)")
            sorted_elements_reading = sorted(filtered_elements, key=lambda e: (e.y_position, e.x_position))
            grouped_results = [ElementGroup(anchor=None, children=[elem]) for elem in sorted_elements_reading]
        else:
            layout_type = detect_layout_type(filtered_elements, page_width, page_height)
            logger.info(f"판별된 레이아웃 유형: {layout_type.name}")

            if layout_type == LayoutType.STANDARD_1_COLUMN:
                 logger.debug(f"{layout_type.name}: 분할 없이 전체 구역 표준 1단 Base Case 실행")
                 grouped_results = _base_case_standard_1_column(initial_zone, filtered_elements)
            elif layout_type == LayoutType.STANDARD_2_COLUMN:
                 grouped_results = _sort_standard_2_column(initial_zone, filtered_elements)
            elif layout_type in [LayoutType.HORIZONTAL_SEP_PRESENT,
                                 LayoutType.MIXED_TOP1_BOTTOM2,
                                 LayoutType.MIXED_TOP2_BOTTOM1,
                                 LayoutType.UNKNOWN]:
                 grouped_results = _sort_recursive_by_layout(initial_zone, filtered_elements, layout_type, depth=0)
            else:
                 logger.error(f"처리할 수 없는 레이아웃 유형: {layout_type.name}. (Y,X) 정렬로 대체합니다.")
                 sorted_elements_fallback = sorted(filtered_elements, key=lambda e: (e.y_position, e.x_position))
                 grouped_results = [ElementGroup(anchor=None, children=[elem]) for elem in sorted_elements_fallback]

            # --- 👇 수정: 후처리 전에 임시 그룹 ID 할당 (로깅용) ---
            if grouped_results and document_type == "question_based":
                logger.debug("후처리 전 임시 그룹 ID 할당...")
                temp_groups_with_id = []
                temp_group_id_counter = 0
                temp_orphan_groups = [g for g in grouped_results if g.anchor is None]
                temp_non_orphan_groups = [g for g in grouped_results if g.anchor is not None]

                # 고아 그룹 먼저 ID 할당
                if temp_orphan_groups:
                    temp_orphan_groups.sort(key=lambda g: min(c.y_position for c in g.children) if g.children else float('inf'))
                    for group in temp_orphan_groups:
                        group.group_id = temp_group_id_counter
                        temp_groups_with_id.append(group)
                        temp_group_id_counter += 1

                # 앵커 그룹 ID 할당
                # (주의: _post_process... 함수는 앵커 그룹 리스트만 받도록 수정 필요)
                # 우선 여기서 ID만 할당하고, 후처리는 non_orphan_groups 대상으로 수행
                for group in temp_non_orphan_groups:
                    group.group_id = temp_group_id_counter
                    # temp_groups_with_id.append(group) # flatten 전 최종 순서는 아직 모름
                    temp_group_id_counter += 1

                # 후처리는 앵커가 있는 그룹들을 대상으로 수행
                logger.debug(f"{len(temp_non_orphan_groups)}개 앵커 그룹 대상 후처리 실행...")
                processed_non_orphan_groups = _post_process_table_figure_assignment(temp_non_orphan_groups)

                # 최종 그룹 리스트 재구성 (고아 + 후처리된 앵커 그룹)
                grouped_results = temp_orphan_groups + processed_non_orphan_groups
                logger.debug("후처리 및 임시 그룹 ID 할당 완료.")
            # --- 👆 수정 끝 ---

    except Exception as e:
        logger.error(f"맞춤형 정렬 중 심각한 오류 발생: {e}. (Y,X) 좌표 정렬로 대체합니다.", exc_info=True)
        sorted_elements_fallback = sorted(filtered_elements, key=lambda e: (e.y_position, e.x_position))
        grouped_results = [ElementGroup(anchor=None, children=[elem]) for elem in sorted_elements_fallback]

    if not grouped_results:
        logger.warning("그룹핑 결과가 비어 있습니다.")
        return []

    # 최종 병합: 고아 그룹과 앵커 그룹 순서 결정 (기존 로직 유지)
    orphan_groups = [g for g in grouped_results if g.anchor is None]
    non_orphan_groups = [g for g in grouped_results if g.anchor is not None] # 후처리된 리스트 사용
    final_ordered_groups: List[ElementGroup] = []
    if orphan_groups:
        # 고아 그룹은 Y 좌표 기준으로 정렬
        orphan_groups.sort(key=lambda g: min(c.y_position for c in g.children) if g.children else float('inf'))
        logger.debug(f"전역 고아 그룹 {len(orphan_groups)}개 (Y 좌표 정렬됨) 리스트 맨 앞으로 이동")
        final_ordered_groups.extend(orphan_groups)
    else: logger.debug("전역 고아 그룹 없음")
    # 앵커 그룹은 Base Case/재귀 호출에서 결정된 순서 유지 (Y좌표 정렬 불필요)
    final_ordered_groups.extend(non_orphan_groups)

    # 최종 순서 및 ID 부여
    final_sorted_elements, _, _ = flatten_groups_and_assign_order(final_ordered_groups, start_global_order=0, start_group_id=0)

    logger.info(f"맞춤형 정렬 완료: {len(final_sorted_elements)}개 요소")
    return final_sorted_elements


def _use_adaptive_strategy() -> bool:
    """환경 변수 기반 Adaptive 전략 사용 여부 판단"""
    return os.getenv("USE_ADAPTIVE_SORTER", "false").lower() in {"1", "true", "yes"}


def sort_layout_elements(
    elements: List[MockElement],
    document_type: str = "question_based",
    page_width: Optional[int] = None,
    page_height: Optional[int] = None
) -> List[MockElement]:
    """
    Adaptive 전략 플래그가 활성화된 경우 sorter_strategies의 Adaptive 엔트리포인트로 위임하고,
    그렇지 않으면 v2.4 코어 구현을 그대로 사용한다.
    """
    if _use_adaptive_strategy():
        from .sorter_strategies import sort_layout_elements_adaptive

        return sort_layout_elements_adaptive(
            elements=elements,
            document_type=document_type,
            page_width=page_width,
            page_height=page_height,
            force_strategy=None
        )

    return _sort_layout_elements_v24(
        elements=elements,
        document_type=document_type,
        page_width=page_width,
        page_height=page_height
    )


# ============================================================================
# 레이아웃 유형 판별 함수 (기존과 동일)
# ============================================================================
def detect_layout_type(elements: List[MockElement], page_width: int, page_height: int) -> LayoutType:
    # ... (코드 동일) ...
    """앵커 요소 분포를 분석하여 페이지 레이아웃 유형 판별"""
    anchors = [e for e in elements if e.class_name in ALLOWED_ANCHORS]
    if len(anchors) < MIN_ANCHORS_FOR_SPLIT:
        logger.debug(f"레이아웃 판별: 앵커 수({len(anchors)}) 부족 -> STANDARD_1_COLUMN")
        return LayoutType.STANDARD_1_COLUMN

    top_zone_height = page_height * HORIZONTAL_SEP_Y_POS_THRESHOLD
    wide_q_type = find_wide_question_type(elements, page_width, top_zone_height)
    if wide_q_type:
        logger.debug(f"레이아웃 판별: 넓은 question_type(ID:{wide_q_type.element_id}) 존재 -> HORIZONTAL_SEP_PRESENT")
        return LayoutType.HORIZONTAL_SEP_PRESENT

    anchor_x_centers = np.array([[a.bbox_x + a.bbox_width / 2] for a in anchors])
    is_clearly_2_column = False
    if len(np.unique(anchor_x_centers)) >= 2:
        try:
            kmeans = KMeans(n_clusters=KMEANS_N_CLUSTERS, random_state=42, n_init='auto')
            kmeans.fit(anchor_x_centers)
            centers = sorted(kmeans.cluster_centers_.flatten())
            if len(centers) == 2 and centers[1] - centers[0] >= KMEANS_CLUSTER_SEPARATION_MIN:
                is_clearly_2_column = True
                logger.trace(f"레이아웃 판별: 전체 X 분포는 2단 구조 가능성 높음 (Centers: {centers})")
            else: logger.trace(f"레이아웃 판별: 전체 X 분포는 1단 구조 또는 불분명")
        except Exception as e: logger.warning(f"레이아웃 판별 중 K-Means 오류 발생: {e}")

    if is_clearly_2_column:
        split_y = page_height * LAYOUT_DETECT_Y_SPLIT_POINT
        top_anchors = [a for a in anchors if (a.y_position + a.bbox_height / 2) < split_y]
        bottom_anchors = [a for a in anchors if (a.y_position + a.bbox_height / 2) >= split_y]

        if not top_anchors or not bottom_anchors:
             logger.debug("레이아웃 판별: 상/하단 앵커 그룹 불완전 -> STANDARD_2_COLUMN")
             return LayoutType.STANDARD_2_COLUMN

        top_x_centers = np.array([[a.bbox_x + a.bbox_width / 2] for a in top_anchors]) if top_anchors else np.array([])
        bottom_x_centers = np.array([[a.bbox_x + a.bbox_width / 2] for a in bottom_anchors]) if bottom_anchors else np.array([])

        x_std_threshold = page_width * LAYOUT_DETECT_X_STD_THRESHOLD_RATIO
        top_is_multi_column = top_x_centers.size > 1 and np.std(top_x_centers) > x_std_threshold
        bottom_is_multi_column = bottom_x_centers.size > 1 and np.std(bottom_x_centers) > x_std_threshold

        if not top_is_multi_column and bottom_is_multi_column:
            logger.debug(f"레이아웃 판별: 상단({len(top_anchors)}개) 1단, 하단({len(bottom_anchors)}개) 2단 -> MIXED_TOP1_BOTTOM2")
            return LayoutType.MIXED_TOP1_BOTTOM2
        elif top_is_multi_column and not bottom_is_multi_column:
             logger.debug(f"레이아웃 판별: 상단({len(top_anchors)}개) 2단, 하단({len(bottom_anchors)}개) 1단 -> MIXED_TOP2_BOTTOM1")
             return LayoutType.MIXED_TOP2_BOTTOM1
        elif top_is_multi_column and bottom_is_multi_column:
             logger.debug(f"레이아웃 판별: 상단({len(top_anchors)}개) 2단, 하단({len(bottom_anchors)}개) 2단 -> STANDARD_2_COLUMN")
             return LayoutType.STANDARD_2_COLUMN
        else:
             logger.warning(f"레이아웃 판별: 상/하단 모두 1단으로 보이나 전체는 2단 구조? -> UNKNOWN")
             return LayoutType.UNKNOWN
    else:
        logger.debug("레이아웃 판별: 전체 1단 구조 -> STANDARD_1_COLUMN")
        return LayoutType.STANDARD_1_COLUMN

# ============================================================================
# 재귀 정렬 함수 (기존과 동일)
# ============================================================================
def _sort_recursive_by_layout(current_zone: Zone, elements_in_zone: List[MockElement], layout_type: LayoutType, depth: int) -> List[ElementGroup]:
    # ... (코드 동일) ...
    """레이아웃 유형에 따라 다른 분할 우선순위를 적용하는 재귀 함수"""
    indent = "  " * depth
    logger.debug(f"{indent}[Depth {depth}, Type: {layout_type.name}] 구역 처리 시작: {current_zone}, 요소 수={len(elements_in_zone)}")

    if not elements_in_zone: logger.trace(f"{indent} -> 빈 구역"); return []
    if len(elements_in_zone) == 1:
        element = elements_in_zone[0]
        logger.trace(f"{indent} -> 요소 1개")
        return [ElementGroup(anchor=element)] if element.class_name in ALLOWED_ANCHORS else [ElementGroup(anchor=None, children=[element])]

    if layout_type == LayoutType.STANDARD_2_COLUMN:
        logger.debug(f"{indent} -> {layout_type.name}: 표준 2단 처리 함수 직접 호출")
        return _sort_standard_2_column(current_zone, elements_in_zone)

    split_result: Optional[Union[HorizontalSplit, HorizontalSplitYGap, VerticalSplit]] = None
    split_type = "None"

    if layout_type == LayoutType.HORIZONTAL_SEP_PRESENT:
        split_result = find_horizontal_split_by_type(current_zone, elements_in_zone)
        if split_result: split_type = "H_Type"
        else:
            anchors = [e for e in elements_in_zone if e.class_name in ALLOWED_ANCHORS]
            split_result = find_vertical_split_kmeans(current_zone, anchors)
            if split_result: split_type = "Vertical"
            else:
                 split_result = find_horizontal_split_by_y_gap(current_zone, elements_in_zone)
                 if split_result: split_type = "H_YGap"

    elif layout_type == LayoutType.MIXED_TOP1_BOTTOM2 or layout_type == LayoutType.MIXED_TOP2_BOTTOM1:
        split_result = find_horizontal_split_by_y_gap(current_zone, elements_in_zone)
        if split_result: split_type = "H_YGap"
        else:
             split_result = find_horizontal_split_by_type(current_zone, elements_in_zone)
             if split_result: split_type = "H_Type"
             else:
                  anchors = [e for e in elements_in_zone if e.class_name in ALLOWED_ANCHORS]
                  split_result = find_vertical_split_kmeans(current_zone, anchors)
                  if split_result: split_type = "Vertical"

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

    if split_result:
        if isinstance(split_result, (HorizontalSplit, HorizontalSplitYGap)):
            split_y = split_result.split_y if isinstance(split_result, HorizontalSplitYGap) else \
                      split_result.separator_element.y_position + split_result.separator_element.bbox_height / 2
            top_elements = [e for e in elements_in_zone if getattr(e, 'element_id', -1) != getattr(getattr(split_result,'separator_element',None),'element_id',-2) and (e.bbox_y + e.bbox_height / 2) < split_y]
            bottom_elements = [e for e in elements_in_zone if getattr(e, 'element_id', -1) != getattr(getattr(split_result,'separator_element',None),'element_id',-2) and (e.bbox_y + e.bbox_height / 2) >= split_y]
            logger.debug(f"{indent} -> {split_type} 수평 분할 성공! Top:{len(top_elements)}, Bottom:{len(bottom_elements)}")
            top_layout_type = detect_layout_type(top_elements, split_result.top_zone.width, split_result.top_zone.height) if top_elements else LayoutType.UNKNOWN
            bottom_layout_type = detect_layout_type(bottom_elements, split_result.bottom_zone.width, split_result.bottom_zone.height) if bottom_elements else LayoutType.UNKNOWN
            sorted_top = _sort_recursive_by_layout(split_result.top_zone, top_elements, top_layout_type, depth + 1)
            sep_group = [ElementGroup(anchor=split_result.separator_element)] if isinstance(split_result, HorizontalSplit) else []
            sorted_bottom = _sort_recursive_by_layout(split_result.bottom_zone, bottom_elements, bottom_layout_type, depth + 1)
            logger.debug(f"{indent} <- {split_type} 수평 분할 결과 병합")
            return sorted_top + sep_group + sorted_bottom

        elif isinstance(split_result, VerticalSplit):
            left_elements = [e for e in elements_in_zone if (e.bbox_x + e.bbox_width / 2) < split_result.gutter_x]
            right_elements = [e for e in elements_in_zone if (e.bbox_x + e.bbox_width / 2) >= split_result.gutter_x]
            logger.debug(f"{indent} -> Vertical 수직 분할 성공! Left:{len(left_elements)}, Right:{len(right_elements)}")
            left_layout_type = detect_layout_type(left_elements, split_result.left_zone.width, split_result.left_zone.height) if left_elements else LayoutType.UNKNOWN
            right_layout_type = detect_layout_type(right_elements, split_result.right_zone.width, split_result.right_zone.height) if right_elements else LayoutType.UNKNOWN
            sorted_left = _sort_recursive_by_layout(split_result.left_zone, left_elements, left_layout_type, depth + 1)
            sorted_right = _sort_recursive_by_layout(split_result.right_zone, right_elements, right_layout_type, depth + 1)
            logger.debug(f"{indent} <- Vertical 수직 분할 결과 병합")
            return sorted_left + sorted_right
    else:
        logger.debug(f"{indent} -> 모든 분할 실패, 레이아웃 유형({layout_type.name})에 따른 Base Case 실행")
        result_groups: List[ElementGroup] = []
        if layout_type == LayoutType.STANDARD_1_COLUMN:
            result_groups = _base_case_standard_1_column(current_zone, elements_in_zone)
        elif layout_type == LayoutType.MIXED_TOP1_BOTTOM2 or layout_type == LayoutType.MIXED_TOP2_BOTTOM1:
            result_groups = _base_case_mixed_layout(current_zone, elements_in_zone, layout_type)
        elif layout_type == LayoutType.HORIZONTAL_SEP_PRESENT or layout_type == LayoutType.UNKNOWN:
             logger.warning(f"{indent} -> {layout_type.name} 유형 분할 실패. 1단 Base Case로 처리합니다.")
             result_groups = _base_case_standard_1_column(current_zone, elements_in_zone)
        else:
             logger.error(f"{indent} -> 처리할 수 없는 Base Case 유형: {layout_type.name}. 1단으로 처리.")
             result_groups = _base_case_standard_1_column(current_zone, elements_in_zone)

        logger.debug(f"{indent} <- Base Case 처리 완료: {len(result_groups)} 그룹 생성")
        return result_groups

# ============================================================================
# 표준 2단 레이아웃 처리 함수 (기존과 동일)
# ============================================================================
def _sort_standard_2_column(zone: Zone, elements: List[MockElement]) -> List[ElementGroup]:
    # ... (코드 동일) ...
    """표준 2단 레이아웃 처리: K-Means 분할 후 컬럼별 _base_case_standard_1_column 호출"""
    logger.debug("표준 2단 처리: K-Means 분할 시도")
    anchors = [e for e in elements if e.class_name in ALLOWED_ANCHORS]
    vertical_split = find_vertical_split_kmeans(zone, anchors)

    if vertical_split:
        logger.debug(f" -> 수직 분할 성공! 분리선 X={vertical_split.gutter_x:.1f}")
        left_elements = [e for e in elements if (e.bbox_x + e.bbox_width / 2) < vertical_split.gutter_x]
        right_elements = [e for e in elements if (e.bbox_x + e.bbox_width / 2) >= vertical_split.gutter_x]
        logger.debug(f"   Left 요소 수: {len(left_elements)}, Right 요소 수: {len(right_elements)}")
        groups_left = _base_case_standard_1_column(vertical_split.left_zone, left_elements)
        groups_right = _base_case_standard_1_column(vertical_split.right_zone, right_elements)
        logger.debug(f" <- 컬럼별 그룹핑 완료 (Left: {len(groups_left)} 그룹, Right: {len(groups_right)} 그룹)")
        return groups_left + groups_right
    else:
        logger.warning("표준 2단 처리 실패: 수직 분할 불가. 전체 구역 표준 1단 Base Case 실행")
        return _base_case_standard_1_column(zone, elements)

# ============================================================================
# 분할 함수 구현 (기존과 동일)
# ============================================================================
def find_wide_question_type(elements: List[MockElement], page_width: int, top_y_limit: float) -> Optional[MockElement]:
    # ... (코드 동일) ...
    """페이지 상단 영역에서 넓은 question_type 찾기"""
    wide_types = [
        e for e in elements
        if e.class_name == "question_type" and \
           e.y_position < top_y_limit and \
           (e.bbox_width / page_width if page_width > 0 else 0) >= HORIZONTAL_SEP_WIDTH_THRESHOLD
    ]
    return min(wide_types, key=lambda e: e.y_position) if wide_types else None

def find_horizontal_split_by_type(zone: Zone, elements: List[MockElement]) -> Optional[HorizontalSplit]:
    # ... (코드 동일) ...
    """넓은 question_type으로 수평 분할"""
    potential_separators = []
    for element in elements:
        if element.class_name == "question_type":
            width_ratio = element.bbox_width / zone.width if zone.width > 0 else 0
            if width_ratio >= HORIZONTAL_SEP_WIDTH_THRESHOLD:
                potential_separators.append(element)
    if not potential_separators: return None
    separator = min(potential_separators, key=lambda e: e.y_position)
    if not (zone.y_min < separator.y_position < zone.y_max): return None
    top_zone = Zone(zone.x_min, zone.y_min, zone.x_max, separator.y_position)
    bottom_zone = Zone(zone.x_min, separator.y_position + separator.bbox_height, zone.x_max, zone.y_max)
    if top_zone.height <= 0 or bottom_zone.height <= 0: return None
    return HorizontalSplit(top_zone, bottom_zone, separator)

def find_horizontal_split_by_y_gap(zone: Zone, elements: List[MockElement]) -> Optional[HorizontalSplitYGap]:
    # ... (코드 동일) ...
    """앵커 Y Gap으로 수평 분할"""
    anchors = sorted([e for e in elements if e.class_name in ALLOWED_ANCHORS], key=lambda e: e.y_position)
    if len(anchors) < MIN_ANCHORS_FOR_SPLIT: return None
    max_gap = -1; split_index = -1
    avg_anchor_height = np.mean([a.bbox_height for a in anchors if a.bbox_height > 0]) if any(a.bbox_height > 0 for a in anchors) else 30
    for i in range(len(anchors) - 1):
        gap = (anchors[i+1].y_position + anchors[i+1].bbox_height / 2) - \
              (anchors[i].y_position + anchors[i].bbox_height / 2)
        if gap > max_gap: max_gap = gap; split_index = i
    threshold = max(avg_anchor_height * VERTICAL_GAP_THRESHOLD_RATIO, VERTICAL_GAP_THRESHOLD_ABS)
    if max_gap >= threshold:
        split_y = (anchors[split_index].y_position + anchors[split_index].bbox_height + anchors[split_index + 1].y_position) / 2
        if zone.y_min < split_y < zone.y_max:
            top_zone = Zone(zone.x_min, zone.y_min, zone.x_max, int(split_y))
            bottom_zone = Zone(zone.x_min, int(split_y), zone.x_max, zone.y_max)
            logger.debug(f"    Y Gap 분석: 수평 분할 가능 (Max Gap={max_gap:.1f} >= Threshold={threshold:.1f})")
            return HorizontalSplitYGap(top_zone, bottom_zone, split_y)
        else: logger.warning(f"    Y Gap 분석: 분할선({split_y:.1f})이 구역({zone.y_min}-{zone.y_max}) 밖에 위치. 분할 취소."); return None
    else:
        logger.debug(f"    Y Gap 분석: 최대 간격({max_gap:.1f}) 임계값({threshold:.1f}) 미만. 수평 분할 불가.")
        return None

def find_vertical_split_kmeans(zone: Zone, anchors: List[MockElement]) -> Optional[VerticalSplit]:
    """앵커 X 좌표 K-Means로 수직 분할 (개선: 오른쪽 칼럼 시작점 기준 분할)"""
    if len(anchors) < MIN_ANCHORS_FOR_SPLIT: return None
    anchor_x_centers = np.array([[a.bbox_x + a.bbox_width / 2] for a in anchors])
    if len(np.unique(anchor_x_centers)) < 2: return None
    try:
        kmeans = KMeans(n_clusters=KMEANS_N_CLUSTERS, random_state=42, n_init='auto')
        kmeans.fit(anchor_x_centers)
        centers = sorted(kmeans.cluster_centers_.flatten())
        
        if len(centers) == 2 and centers[1] - centers[0] >= KMEANS_CLUSTER_SEPARATION_MIN:
            # 🔥 핵심 변경: 오른쪽 칼럼 앵커의 시작점을 경계로 사용
            # 너무 타이트한 경계가 문제될 경우
            COLUMN_BOUNDARY_MARGIN = 20  # px
            gutter_x = centers[1] - COLUMN_BOUNDARY_MARGIN
            # gutter_x = centers[1]  # 기존: (centers[0] + centers[1]) / 2
            
            if zone.x_min < gutter_x < zone.x_max:
                left_zone = Zone(zone.x_min, zone.y_min, int(gutter_x), zone.y_max)
                right_zone = Zone(int(gutter_x), zone.y_min, zone.x_max, zone.y_max)
                logger.debug(f"    수직 분할 성공: 왼쪽 칼럼 X=[{zone.x_min}, {int(gutter_x)}), "
                           f"오른쪽 칼럼 X=[{int(gutter_x)}, {zone.x_max})")
                return VerticalSplit(left_zone, right_zone, gutter_x)
            else: 
                logger.warning(f"    수직 분할: 경계선({gutter_x:.1f})이 구역 밖. 분할 취소.")
                return None
        else: 
            logger.debug(f"    수직 분할 실패: 중심간 거리 부족")
            return None
    except Exception as e: 
        logger.error(f"    수직 분할 K-Means 오류: {e}")
        return None

# ============================================================================
# 후처리 함수 (수정됨)
# ============================================================================
def _post_process_table_figure_assignment(groups: List[ElementGroup], y_diff_threshold: int = 150) -> List[ElementGroup]:
    """
    그룹핑 후처리: 테이블/그림 요소가 현재 앵커보다 다음 앵커(들)에 훨씬 가까우면 이동 시도
    --- 수정: 최적 그룹 탐색 및 Tie-breaker 추가 ---
    """
    logger.debug(f"    테이블/그림 할당 후처리 시작: {len(groups)}개 그룹 (Threshold={y_diff_threshold}px, Closeness Ratio={POST_PROCESS_CLOSENESS_RATIO}, Lookahead={POST_PROCESS_LOOKAHEAD})")
    adjusted_groups = groups # 원본 리스트를 직접 수정
    elements_to_move_dict: Dict[int, Tuple[MockElement, int]] = {} # {element_id: (element, target_group_idx)}
    moved_elements_log = [] # 로깅용

    for i in range(len(adjusted_groups)):
        current_group = adjusted_groups[i]
        if not current_group.anchor: continue

        current_children_copy = list(current_group.children) # 순회 중 변경을 위한 복사본

        for child_idx, child in enumerate(current_children_copy):
            # 이미 이동 대상으로 결정된 요소는 건너뜀
            if child.element_id in elements_to_move_dict: continue

            if child.class_name in ['table', 'figure', 'flowchart']:
                y_diff_current = child.y_position - current_group.anchor.y_position

                best_target_group_idx = -1
                min_y_diff_next = float('inf')

                # 현재 그룹 이후 몇 개 그룹까지 탐색
                for lookahead_idx in range(1, POST_PROCESS_LOOKAHEAD + 1):
                    next_group_idx = i + lookahead_idx
                    if next_group_idx >= len(adjusted_groups): break

                    next_group = adjusted_groups[next_group_idx]
                    if not next_group.anchor: continue

                    y_diff_next = abs(child.y_position - next_group.anchor.y_position)

                    # 이동 조건 검사 (v2.2 조건)
                    if y_diff_current > (y_diff_threshold / 2) and y_diff_next < (y_diff_current * POST_PROCESS_CLOSENESS_RATIO):
                        # --- 👇 Tie-breaker 수정 👇 ---
                        # 더 가까운 그룹을 찾거나, 거리가 같지만 더 뒤의 그룹일 경우 갱신
                        if y_diff_next < min_y_diff_next or \
                           (y_diff_next == min_y_diff_next and next_group_idx > best_target_group_idx):
                            min_y_diff_next = y_diff_next
                            best_target_group_idx = next_group_idx
                        # --- 👆 Tie-breaker 수정 끝 👆 ---

                # 최적 그룹을 찾았으면 이동 대상으로 등록
                if best_target_group_idx != -1:
                    elements_to_move_dict[child.element_id] = (child, best_target_group_idx)
                    moved_elements_log.append(f"Elem {child.element_id} ({child.class_name}) from Grp {current_group.group_id} to Grp {adjusted_groups[best_target_group_idx].group_id}")
                    logger.trace(f"        이동 후보 확정: Elem {child.element_id} -> Group {adjusted_groups[best_target_group_idx].group_id} (Min Y diff next={min_y_diff_next:.0f})")

    # --- 실제 요소 이동 (루프 종료 후) ---
    if elements_to_move_dict:
        # 1. 원본 그룹에서 요소 제거
        elements_removed_count = 0
        for group in adjusted_groups:
            original_children_count = len(group.children)
            group.children = [child for child in group.children if child.element_id not in elements_to_move_dict]
            elements_removed_count += (original_children_count - len(group.children))

        # 2. 대상 그룹에 요소 추가
        elements_added_count = 0
        for element_id, (element, target_group_idx) in elements_to_move_dict.items():
            if 0 <= target_group_idx < len(adjusted_groups):
                adjusted_groups[target_group_idx].children.insert(0, element) # 그룹 맨 앞에 추가
                elements_added_count += 1
            else:
                 logger.error(f"후처리 이동 중 유효하지 않은 대상 그룹 인덱스: {target_group_idx} for Elem {element_id}")

        logger.debug(f"    후처리 요소 이동 완료: {elements_removed_count}개 제거, {elements_added_count}개 추가")


    if moved_elements_log:
         logger.info(f"    테이블/그림 할당 후처리: {len(moved_elements_log)}개 요소 이동됨 - {', '.join(moved_elements_log)}")
    else:
         logger.debug("    테이블/그림 할당 후처리: 이동된 요소 없음")

    return adjusted_groups


# ============================================================================
# Base Case 함수들 (기존과 동일 v2.1)
# ============================================================================

def _assign_children_to_anchors_with_2d_proximity(
    anchors: List[MockElement],
    children: List[MockElement],
    zone: Zone,
    preserve_top_orphans: bool = True
) -> Tuple[List[ElementGroup], List[MockElement]]:
    """
    앵커와 자식 요소를 2D 거리 기반으로 그룹핑 (Phase 1: STANDARD_2_COLUMN 적용)
    
    Args:
        anchors: 앵커 요소 리스트
        children: 자식 요소 리스트
        zone: 현재 처리 중인 구역
        preserve_top_orphans: True일 경우 상단 영역의 요소는 고아로 유지
    
    Returns:
        (그룹 리스트, 고아 요소 리스트)
    """
    groups: List[ElementGroup] = [ElementGroup(anchor=a) for a in anchors]
    orphans: List[MockElement] = []
    
    # 상단 고아 임계값 (기존 로직 유지 옵션)
    top_orphan_threshold_y = zone.y_min + zone.height * BASE_CASE_TOP_ORPHAN_THRESHOLD_RATIO if preserve_top_orphans else zone.y_min
    
    for child in children:
        child_x_center = child.bbox_x + child.bbox_width / 2
        child_y_center = child.bbox_y + child.bbox_height / 2
        
        # 상단 고아 체크 (선택적)
        if preserve_top_orphans and child.bbox_y < top_orphan_threshold_y:
            # 첫 번째 앵커보다 훨씬 위쪽인 경우만 고아로 처리
            if not anchors or child_y_center < (anchors[0].bbox_y - ANCHOR_VERTICAL_PROXIMITY_THRESHOLD / 2):
                orphans.append(child)
                logger.trace(f"      Elem {child.element_id} 상단 고아 유지 (Y={child.bbox_y})")
                continue
        
        best_anchor_idx = None
        min_distance = float('inf')
        
        for idx, anchor in enumerate(anchors):
            anchor_x_center = anchor.bbox_x + anchor.bbox_width / 2
            anchor_y_center = anchor.bbox_y + anchor.bbox_height / 2
            
            # 🔥 핵심 수정: 자식이 앵커보다 위쪽에 있으면 제외
            # figure/table은 반드시 자신보다 위쪽에 있는 앵커에만 배정되어야 함
            if child_y_center < anchor_y_center:
                logger.trace(f"      Elem {child.element_id} → Anchor {anchor.element_id} 제외 "
                           f"(자식 Y={child_y_center:.0f} < 앵커 Y={anchor_y_center:.0f})")
                continue
            
            # 가중 2D 거리 계산
            x_diff = abs(child_x_center - anchor_x_center) * ANCHOR_2D_DISTANCE_WEIGHT_X
            y_diff = abs(child_y_center - anchor_y_center) * ANCHOR_2D_DISTANCE_WEIGHT_Y
            distance = (x_diff**2 + y_diff**2) ** 0.5
            
            if distance < min_distance:
                min_distance = distance
                best_anchor_idx = idx
        
        # 거리 임계값 체크
        if best_anchor_idx is not None and min_distance < ANCHOR_VERTICAL_PROXIMITY_THRESHOLD:
            groups[best_anchor_idx].children.append(child)
            logger.trace(f"      Elem {child.element_id} → Anchor {anchors[best_anchor_idx].element_id} "
                       f"(2D 거리={min_distance:.1f})")
        else:
            orphans.append(child)
            if best_anchor_idx is None:
                reason = "위쪽 앵커만 허용 (모든 앵커가 자식보다 아래쪽)"
            else:
                reason = f"최소 거리={min_distance:.1f} > {ANCHOR_VERTICAL_PROXIMITY_THRESHOLD}"
            logger.debug(f"      Elem {child.element_id} 고아 ({reason})")
    
    return groups, orphans


def _base_case_standard_1_column(zone: Zone, elements: List[MockElement]) -> List[ElementGroup]:
    # ... (v2.1 코드와 동일) ...
    """표준 1단 구역 Base Case 처리 (상단 고아 분리)"""
    logger.debug(f"    표준 1단 Base Case 시작 (순차 처리 + 고아 개선): {len(elements)}개 요소 in {zone}")
    anchors = sorted([e for e in elements if e.class_name in ALLOWED_ANCHORS], key=lambda e: e.y_position)
    children = [e for e in elements if e.class_name in ALLOWED_CHILDREN]
    groups: Dict[int, ElementGroup] = {anchor.element_id: ElementGroup(anchor=anchor) for anchor in anchors}
    assigned_children_ids = set()
    logger.trace("      수평 인접 처리 시작...")

    if anchors and children:
        for anchor in anchors:
            anchor_cy = anchor.bbox_y + anchor.bbox_height / 2; anchor_right_x = anchor.bbox_x + anchor.bbox_width; anchor_left_x = anchor.bbox_x
            unassigned_children = [c for c in children if c.element_id not in assigned_children_ids]
            adjacent_child = None; min_y_diff = float('inf')
            for child in unassigned_children:
                child_cy = child.bbox_y + child.bbox_height / 2; child_right_x = child.bbox_x + child.bbox_width; child_left_x = child.bbox_x
                y_diff = abs(anchor_cy - child_cy); y_threshold = (anchor.bbox_height + child.bbox_height) / 2 * HORIZONTAL_ADJACENCY_Y_CENTER_RATIO if (anchor.bbox_height + child.bbox_height)>0 else 0
                if y_diff >= y_threshold: continue
                gap_right = child_left_x - anchor_right_x; gap_left = anchor_left_x - child_right_x
                is_adjacent = (abs(gap_right) < HORIZONTAL_ADJACENCY_X_PROXIMITY) or (abs(gap_left) < HORIZONTAL_ADJACENCY_X_PROXIMITY)
                if is_adjacent and y_diff < min_y_diff: min_y_diff = y_diff; adjacent_child = child
            if adjacent_child:
                logger.trace(f"        수평 인접 배정: 앵커 ID {anchor.element_id} <- 자식 ID {adjacent_child.element_id}")
                groups[anchor.element_id].add_child(adjacent_child); assigned_children_ids.add(adjacent_child.element_id)
    logger.debug(f"    수평 인접 처리 완료: {len(assigned_children_ids)}개 자식 우선 배정됨")

    remaining_elements = anchors + [c for c in children if c.element_id not in assigned_children_ids]
    if not remaining_elements:
        logger.debug("    모든 요소가 수평 인접으로 배정되어 그룹핑 완료.")
        # 후처리 호출 전 그룹 ID 임시 할당 (선택적)
        temp_groups = sorted(list(groups.values()), key=lambda g: g.anchor.y_position if g.anchor else float('inf'))
        for idx, group in enumerate(temp_groups): group.group_id = idx
        return _post_process_table_figure_assignment(temp_groups)

    # 2단계: 나머지 요소를 2D 거리 기반으로 그룹핑 (Phase 1 적용)
    remaining_children = [c for c in children if c.element_id not in assigned_children_ids]
    
    if remaining_children and anchors:
        logger.trace(f"      2단계: 나머지 {len(remaining_children)}개 요소 2D 거리 그룹핑...")
        
        # 🔥 2D 거리 기반 그룹핑 (상단 고아 보존 옵션 활성화)
        proximity_groups, proximity_orphans = _assign_children_to_anchors_with_2d_proximity(
            anchors, 
            remaining_children, 
            zone,
            preserve_top_orphans=True  # 상단 고아 보존
        )
        
        # 2D 거리로 배정된 자식들을 기존 그룹에 병합
        for idx, proximity_group in enumerate(proximity_groups):
            anchor_id = anchors[idx].element_id
            if anchor_id in groups:
                groups[anchor_id].children.extend(proximity_group.children)
        
        # 2D 그룹핑 후 여전히 남은 요소들은 순차 처리로 넘김
        remaining_elements = [a for a in anchors if a.element_id not in assigned_children_ids] + proximity_orphans
        logger.debug(f"    2단계 완료: {len(remaining_children) - len(proximity_orphans)}개 배정, {len(proximity_orphans)}개 고아로 순차 처리 대기")
    else:
        remaining_elements = anchors + [c for c in children if c.element_id not in assigned_children_ids]

    if not remaining_elements:
        logger.debug("    2D 거리 그룹핑 후 나머지 요소 없음. 그룹핑 완료.")
        temp_groups = sorted(list(groups.values()), key=lambda g: g.anchor.y_position if g.anchor else float('inf'))
        for idx, group in enumerate(temp_groups): group.group_id = idx
        return _post_process_table_figure_assignment(temp_groups)

    logger.trace(f"      3단계: 나머지 요소 {len(remaining_elements)}개 (Y, X) 정렬 및 순차 그룹핑 시작...")
    remaining_elements.sort(key=lambda e: (e.y_position, e.x_position))

    final_groups: List[ElementGroup] = []
    current_group: Optional[ElementGroup] = None
    initial_top_orphan_children: List[MockElement] = []
    initial_bottom_orphan_children: List[MockElement] = []
    first_anchor_found = False
    
    top_orphan_threshold_y = zone.y_min + zone.height * BASE_CASE_TOP_ORPHAN_THRESHOLD_RATIO

    for element in remaining_elements:
        if element.class_name in ALLOWED_ANCHORS:
            first_anchor_found = True
            if initial_top_orphan_children:
                logger.trace(f"        독립적인 상단 고아 그룹 생성 ({len(initial_top_orphan_children)}개 요소)")
                final_groups.append(ElementGroup(anchor=None, children=initial_top_orphan_children))
                initial_top_orphan_children = []
            if current_group is not None and current_group.anchor is not None and not current_group.is_empty():
                 final_groups.append(current_group)
            if element.element_id in groups:
                 current_group = groups[element.element_id]
                 logger.trace(f"        앵커 그룹 재사용 (ID: {element.element_id})")
            else:
                 current_group = ElementGroup(anchor=element, children=[])
                 logger.trace(f"        새 앵커 그룹 시작 (ID: {element.element_id})")
            if initial_bottom_orphan_children:
                 logger.trace(f"        첫 앵커(ID: {element.element_id}) 그룹에 하단 고아 자식 {len(initial_bottom_orphan_children)}개 추가")
                 current_group.children = initial_bottom_orphan_children + current_group.children
                 initial_bottom_orphan_children = []
        else:
            if first_anchor_found:
                 if current_group is None:
                      logger.warning(f"        앵커 없이 자식 요소(ID: {element.element_id}) 발견됨. 위치({element.y_position:.1f}) 따라 임시 고아 리스트에 추가.")
                      if element.y_position < top_orphan_threshold_y: initial_top_orphan_children.append(element)
                      else: initial_bottom_orphan_children.append(element)
                 else:
                      current_group.add_child(element)
                      logger.trace(f"        현재 그룹(앵커: {current_group.anchor.element_id if current_group.anchor else 'Orphan'})에 자식 추가 (ID: {element.element_id})")
            else:
                 if element.y_position < top_orphan_threshold_y:
                      initial_top_orphan_children.append(element)
                      logger.trace(f"        상단 고아 자식 요소(ID: {element.element_id}) 임시 저장 (Y < {top_orphan_threshold_y:.0f})")
                 else:
                      initial_bottom_orphan_children.append(element)
                      logger.trace(f"        하단 고아 자식 요소(ID: {element.element_id}) 임시 저장 (Y >= {top_orphan_threshold_y:.0f})")

    if initial_top_orphan_children:
         logger.trace(f"        마지막 독립 상단 고아 그룹 생성 ({len(initial_top_orphan_children)}개 요소)")
         final_groups.append(ElementGroup(anchor=None, children=initial_top_orphan_children))
    if current_group is not None and not current_group.is_empty():
         final_groups.append(current_group)
    elif initial_bottom_orphan_children:
         logger.warning("        모든 요소가 하단 자식 요소임. 단일 고아 그룹 생성.")
         final_groups.append(ElementGroup(anchor=None, children=initial_bottom_orphan_children))

    processed_anchor_ids = set(g.anchor.element_id for g in final_groups if g.anchor)
    for anchor_id, group in groups.items():
        if anchor_id not in processed_anchor_ids and group.anchor:
            final_groups.append(group); logger.trace(f"        미포함 앵커 그룹 추가 (수평 인접만): ID {anchor_id}")

    final_groups.sort(key=lambda g: g.anchor.y_position if g.anchor else (min(c.y_position for c in g.children) if g.children else float('inf')))

    # 후처리 호출 전 그룹 ID 임시 할당
    for idx, group in enumerate(final_groups): group.group_id = idx
    final_groups = _post_process_table_figure_assignment(final_groups)

    logger.debug(f"    순차 처리 기반 그룹핑 (+후처리) 완료: {len(final_groups)} 그룹 생성")
    return final_groups

def _base_case_mixed_layout(zone: Zone, elements: List[MockElement], layout_type: LayoutType) -> List[ElementGroup]:
    """혼합형 레이아웃 Base Case 처리 (기존과 동일)"""
    # ... (v2.1 코드와 동일) ...
    logger.debug(f"    혼합형 Base Case 시작 ({layout_type.name}): {len(elements)}개 요소 in {zone}")
    sorted_elements = sorted(elements, key=lambda e: (e.y_position, e.x_position))
    final_groups: List[ElementGroup] = []
    current_group: Optional[ElementGroup] = None
    initial_top_orphan_children: List[MockElement] = []
    initial_bottom_orphan_children: List[MockElement] = []
    first_anchor_found = False
    split_y = zone.y_min + zone.height * LAYOUT_DETECT_Y_SPLIT_POINT
    logger.trace(f"      혼합형 Base Case Y 분할점: {split_y:.1f}")

    for element in sorted_elements:
        element_y_center = element.y_position + element.bbox_height / 2
        if element.class_name in ALLOWED_ANCHORS:
            first_anchor_found = True
            if initial_top_orphan_children:
                logger.trace(f"        독립적인 상단 고아 그룹 생성 ({len(initial_top_orphan_children)}개 요소)")
                final_groups.append(ElementGroup(anchor=None, children=initial_top_orphan_children))
                initial_top_orphan_children = []
            if current_group is not None and not current_group.is_empty():
                final_groups.append(current_group)
            current_group = ElementGroup(anchor=element, children=[])
            logger.trace(f"        새 앵커 그룹 시작 (ID: {element.element_id})")
            if initial_bottom_orphan_children:
                logger.trace(f"        첫 앵커(ID: {element.element_id}) 그룹에 하단 고아 자식 {len(initial_bottom_orphan_children)}개 추가")
                current_group.children = initial_bottom_orphan_children + current_group.children
                initial_bottom_orphan_children = []
        else:
            if first_anchor_found:
                if current_group is None:
                    logger.warning(f"        앵커 없이 자식 요소(ID: {element.element_id}) 발견됨. 위치({element_y_center:.1f}) 따라 임시 고아 리스트에 추가.")
                    if element_y_center < split_y: initial_top_orphan_children.append(element)
                    else: initial_bottom_orphan_children.append(element)
                else:
                    current_group.add_child(element)
                    logger.trace(f"        현재 그룹(앵커: {current_group.anchor.element_id if current_group.anchor else 'Orphan'})에 자식 추가 (ID: {element.element_id})")
            else:
                if element_y_center < split_y:
                    initial_top_orphan_children.append(element)
                    logger.trace(f"        상단 고아 자식 요소(ID: {element.element_id}) 임시 저장")
                else:
                    initial_bottom_orphan_children.append(element)
                    logger.trace(f"        하단 고아 자식 요소(ID: {element.element_id}) 임시 저장")

    if initial_top_orphan_children:
        logger.trace(f"        마지막 독립 상단 고아 그룹 생성 ({len(initial_top_orphan_children)}개 요소)")
        final_groups.append(ElementGroup(anchor=None, children=initial_top_orphan_children))
    if current_group is not None and not current_group.is_empty():
        final_groups.append(current_group)
    elif initial_bottom_orphan_children:
        logger.warning("        모든 요소가 하단 자식 요소임. 단일 고아 그룹 생성.")
        final_groups.append(ElementGroup(anchor=None, children=initial_bottom_orphan_children))

    # 후처리 호출 전 그룹 ID 임시 할당
    for idx, group in enumerate(final_groups): group.group_id = idx
    final_groups = _post_process_table_figure_assignment(final_groups)

    return final_groups


# ============================================================================
# 최종 병합 및 순서 부여 함수 (기존과 동일)
# ============================================================================
def flatten_groups_and_assign_order(
    groups: List[ElementGroup],
    start_global_order: int,
    start_group_id: int
) -> Tuple[List[MockElement], int, int]:
    # ... (코드 동일) ...
    """주어진 그룹 리스트를 평탄화하고 전역 순서/그룹 ID 부여"""
    flattened = []
    global_order = start_global_order
    group_id_counter = start_group_id
    logger.debug(f"    평탄화 시작: {len(groups)}개 그룹 (시작 order={global_order}, group_id={group_id_counter})")
    for group in groups: # 최종 정렬된 그룹 순서 사용
        # 그룹 객체의 ID는 임시 ID일 수 있으므로 여기서 최종 ID 할당
        final_group_id = group_id_counter
        group.group_id = final_group_id # 로깅 및 참조용 업데이트

        elements_in_group = group.get_all_elements_sorted()
        logger.trace(f"      그룹 {final_group_id} 평탄화 (Anchor: {group.anchor.element_id if group.anchor else 'Orphan'}, 요소 수: {len(elements_in_group)})")
        for local_order, element in enumerate(elements_in_group):
            try:
                setattr(element, 'order_in_question', global_order)
                setattr(element, 'group_id', final_group_id) # 최종 그룹 ID 사용
                setattr(element, 'order_in_group', local_order)
                flattened.append(element)
                global_order += 1
            except AttributeError as e: logger.error(f"요소 (ID: {getattr(element, 'element_id', 'N/A')})에 정렬 속성 추가 실패: {e}")
        group_id_counter += 1
    logger.debug(f"    평탄화 완료: {len(flattened)}개 요소 생성 (다음 order={global_order}, group_id={group_id_counter})")
    return flattened, global_order, group_id_counter

# ============================================================================
# 헬퍼 함수 (기존과 동일)
# ============================================================================
def preprocess_elements(elements: List[MockElement], document_type: str) -> List[MockElement]:
    # ... (코드 동일) ...
    """0단계 전처리"""
    original_count = len(elements)
    if document_type == "question_based":
        filtered = [e for e in elements if e.class_name in ALLOWED_CLASSES]
        logger.info(f"전처리 (question_based): {original_count}개 → {len(filtered)}개 (허용 클래스 필터링)")
    elif document_type == "reading_order": filtered = elements; logger.info(f"전처리 (reading_order): {original_count}개 (모든 클래스 허용)")
    else: logger.warning(f"알 수 없는 문서 타입 '{document_type}', 모든 요소 반환"); filtered = elements
    valid_elements = [e for e in filtered if hasattr(e, 'area') and e.area > 0]
    if len(valid_elements) < len(filtered): logger.warning(f"전처리: 면적이 0 이하인 요소 {len(filtered) - len(valid_elements)}개 제거")
    return valid_elements

def calculate_page_width(elements: List[MockElement]) -> int:
    # ... (코드 동일) ...
    """페이지 너비 추정"""
    if not elements: return 0; return max(e.bbox_x + e.bbox_width for e in elements) if elements else 0

def calculate_page_height(elements: List[MockElement]) -> int:
    # ... (코드 동일) ...
    """페이지 높이 추정"""
    if not elements: return 0; return max(e.bbox_y + e.bbox_height for e in elements) if elements else 0



# ============================================================================
# DB 저장 함수 (ORM 연동)
# ============================================================================

def save_sorting_results_to_db(
    db: "Session",
    page_id: int,
    sorted_elements: List["LayoutElement"]
) -> Tuple[int, int]:
    """
    정렬된 LayoutElement 리스트를 question_groups와 question_elements 테이블에 저장합니다.

    Args:
        db: SQLAlchemy 세션
        page_id: 페이지 ID
        sorted_elements: sorter.py로 정렬된 LayoutElement 리스트
                        (order_in_question, group_id 속성 필수)

    Returns:
        (생성된 그룹 수, 생성된 요소 수) 튜플

    Raises:
        ValueError: sorted_elements에 order_in_question 또는 group_id가 없는 경우
    """
    from .. import crud
    from ..schemas import QuestionGroupCreate, QuestionElementCreate

    if not sorted_elements:
        logger.warning(f"page_id={page_id}: 정렬된 요소가 없어 DB 저장을 건너뜁니다.")
        return 0, 0

    # 1. 요소들을 group_id별로 그룹화
    groups_dict: Dict[int, List["LayoutElement"]] = {}
    for elem in sorted_elements:
        if not hasattr(elem, "order_in_question") or not hasattr(elem, "group_id"):
            raise ValueError(
                f"element_id={elem.element_id}: order_in_question 또는 group_id 속성이 없습니다. "
                "sorter.py의 flatten_groups_and_assign_order() 실행 후 호출하세요."
            )

        group_id = elem.group_id
        if group_id not in groups_dict:
            groups_dict[group_id] = []
        groups_dict[group_id].append(elem)

    logger.info(f"page_id={page_id}: {len(groups_dict)}개 그룹, {len(sorted_elements)}개 요소를 DB에 저장 시작")

    # 2. 각 그룹에 대해 QuestionGroup 생성
    group_count = 0
    element_count = 0

    for group_id, group_elements in sorted(groups_dict.items()):
        # 앵커 요소 찾기 (그룹 내 첫 번째 요소가 앵커)
        anchor_elem = min(group_elements, key=lambda e: e.order_in_question)

        # Y 범위 계산
        start_y = min(e.y_position for e in group_elements)
        end_y = max(e.y_position + (e.bbox_height if hasattr(e, "bbox_height") else 0) for e in group_elements)

        # QuestionGroup 생성
        group_create = QuestionGroupCreate(
            page_id=page_id,
            anchor_element_id=anchor_elem.element_id,
            start_y=start_y,
            end_y=end_y,
            element_count=len(group_elements)
        )

        db_group = crud.create_question_group(db, group_create)
        group_count += 1
        logger.debug(f"  그룹 {group_id} → question_group_id={db_group.question_group_id} (앵커: {anchor_elem.element_id}, 요소 수: {len(group_elements)})")

        # 3. 그룹 내 각 요소에 대해 QuestionElement 생성
        for elem in group_elements:
            element_create = QuestionElementCreate(
                question_group_id=db_group.question_group_id,
                element_id=elem.element_id,
                order_in_question=elem.order_in_question
            )

            crud.create_question_element(db, element_create)
            element_count += 1

    logger.info(f"page_id={page_id}: DB 저장 완료 ({group_count}개 그룹, {element_count}개 요소)")
    return group_count, element_count

