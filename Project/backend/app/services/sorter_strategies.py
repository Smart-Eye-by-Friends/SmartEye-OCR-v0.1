# -*- coding: utf-8 -*-
"""
SmartEyeSsen Sorter - Adaptive Strategy Pattern (Phase 1)
==========================================================

Phase 1 프로토타입: 전략 패턴 기반 정렬 로직
- GlobalFirstStrategy: 현재 sorter.py 로직 (PDF에서 성공)
- LocalFirstStrategy: 구 버전 로직 (특정 이미지에서 성공)
- LayoutProfiler: 입력 분석 및 전략 추천

Phase 1 목표: 강제 전략으로 양쪽 테스트 모두 통과
"""

from abc import ABC, abstractmethod
from typing import List, Optional, Dict
from dataclasses import dataclass
from enum import Enum, auto
import numpy as np
from sklearn.cluster import KMeans
from loguru import logger

# sorter.py의 모든 함수와 클래스 임포트
from .sorter import (
    sort_layout_elements as _sort_layout_elements_global,
    MockElement, ElementGroup, Zone, VerticalSplit, LayoutType,
    ALLOWED_ANCHORS, ALLOWED_CHILDREN, ALLOWED_CLASSES,
    MIN_ANCHORS_FOR_SPLIT, KMEANS_N_CLUSTERS, KMEANS_CLUSTER_SEPARATION_MIN,
    HORIZONTAL_ADJACENCY_Y_CENTER_RATIO, HORIZONTAL_ADJACENCY_X_PROXIMITY,
    BASE_CASE_TOP_ORPHAN_THRESHOLD_RATIO, POST_PROCESS_CLOSENESS_RATIO,
    POST_PROCESS_LOOKAHEAD,
    detect_layout_type, preprocess_elements, calculate_page_width, calculate_page_height,
    flatten_groups_and_assign_order, _post_process_table_figure_assignment,
    _sort_recursive_by_layout, find_wide_question_type, find_horizontal_split_by_type,
    find_horizontal_split_by_y_gap, _sort_standard_2_column, _base_case_mixed_layout
)


# ============================================================================
# Enum 및 Dataclass 정의
# ============================================================================

class SortingStrategyType(Enum):
    """정렬 전략 타입"""
    GLOBAL_FIRST = auto()    # 전역 우선 (신규 로직)
    LOCAL_FIRST = auto()     # 로컬 우선 (구 로직)
    HYBRID = auto()          # 혼합형 (Phase 3)


@dataclass
class LayoutProfile:
    """레이아웃 프로파일 (Phase 1 기본 버전)"""
    global_consistency_score: float  # 전역 일관성 점수 (0.0-1.0)
    anchor_x_std: float              # 앵커 X 좌표 표준편차
    horizontal_adjacency_ratio: float # 수평 인접 요소 비율
    recommended_strategy: SortingStrategyType  # 추천 전략


# ============================================================================
# Strategy 인터페이스
# ============================================================================

class SortingStrategy(ABC):
    """정렬 전략 추상 인터페이스"""

    @abstractmethod
    def sort(
        self,
        elements: List[MockElement],
        document_type: str,
        page_width: int,
        page_height: int
    ) -> List[MockElement]:
        """
        레이아웃 요소 정렬

        Args:
            elements: 정렬할 요소 리스트
            document_type: 문서 타입 ("question_based" 또는 "reading_order")
            page_width: 페이지 너비
            page_height: 페이지 높이

        Returns:
            정렬된 요소 리스트 (group_id, order_in_group 할당됨)
        """
        pass


# ============================================================================
# GlobalFirstStrategy (신규 로직)
# ============================================================================

class GlobalFirstStrategy(SortingStrategy):
    """
    전역 우선 전략 (Global-First Strategy)

    현재 sorter.py의 로직을 그대로 사용:
    - 오른쪽 칼럼 시작점 기준 수직 분할
    - 2D 거리 기반 그룹핑 적용

    PDF와 같은 전역적으로 일관된 레이아웃에 효과적
    """

    def sort(
        self,
        elements: List[MockElement],
        document_type: str,
        page_width: int,
        page_height: int
    ) -> List[MockElement]:
        """현재 sorter.py 로직 사용 (래핑)"""
        logger.info("[GlobalFirstStrategy] 전역 우선 전략 실행 중...")

        # 현재 sorter.py의 sort_layout_elements 함수 호출
        return _sort_layout_elements_global(
            elements=elements,
            document_type=document_type,
            page_width=page_width,
            page_height=page_height
        )


# ============================================================================
# LocalFirstStrategy (구 버전 로직)
# ============================================================================

class LocalFirstStrategy(SortingStrategy):
    """
    로컬 우선 전략 (Local-First Strategy)

    구 버전 sorter_구버전.py의 로직을 사용:
    - 중앙점 기준 수직 분할 (보수적)
    - 2D 거리 기반 그룹핑 생략 (순차 처리만)

    불규칙한 레이아웃(특정 이미지)에 효과적
    """

    def sort(
        self,
        elements: List[MockElement],
        document_type: str,
        page_width: int,
        page_height: int
    ) -> List[MockElement]:
        """구 버전 로직 사용 (핵심 함수 오버라이드)"""
        logger.info("[LocalFirstStrategy] 로컬 우선 전략 실행 중...")

        # 전처리
        elements = preprocess_elements(elements, document_type)
        if not elements:
            logger.warning("전처리 후 요소가 없습니다.")
            return []

        # 레이아웃 유형 판별 (global과 동일)
        layout_type = detect_layout_type(elements, page_width, page_height)
        logger.info(f"레이아웃 유형 판별: {layout_type.name}")

        # 전체 구역 정의
        full_zone = Zone(x_min=0, y_min=0, x_max=page_width, y_max=page_height)

        # 재귀 정렬 (로컬 버전 함수 사용)
        groups = self._sort_recursive_by_layout_local(
            current_zone=full_zone,
            elements_in_zone=elements,
            layout_type=layout_type,
            depth=0
        )

        # 최종 병합 및 순서 부여
        sorted_elements, _, _ = flatten_groups_and_assign_order(
            groups=groups,
            start_global_order=0,
            start_group_id=0
        )

        logger.info(f"LocalFirstStrategy 완료: {len(sorted_elements)}개 요소 정렬됨")
        return sorted_elements

    # ========================================================================
    # 로컬 버전 핵심 함수 (구 버전 로직)
    # ========================================================================

    def _sort_recursive_by_layout_local(
        self,
        current_zone: Zone,
        elements_in_zone: List[MockElement],
        layout_type: LayoutType,
        depth: int
    ) -> List[ElementGroup]:
        """재귀 정렬 (로컬 버전 - 구버전의 find_vertical_split_kmeans 사용)"""

        indent = "  " * depth
        logger.debug(f"{indent}재귀 깊이 {depth}: {len(elements_in_zone)}개 요소, 레이아웃={layout_type.name}")

        if not elements_in_zone:
            return []

        # 앵커와 자식 분리
        anchors = [e for e in elements_in_zone if e.class_name in ALLOWED_ANCHORS]

        # 분할 우선순위 (레이아웃 유형별)
        if layout_type == LayoutType.HORIZONTAL_SEP_PRESENT:
            h_split = find_horizontal_split_by_type(current_zone, elements_in_zone)
            if h_split:
                logger.debug(f"{indent}  수평 분할 (넓은 question_type) 성공")
                top_groups = self._sort_recursive_by_layout_local(
                    h_split.top_zone, h_split.top_elements,
                    detect_layout_type(h_split.top_elements, current_zone.width, h_split.top_zone.height),
                    depth + 1
                )
                bottom_groups = self._sort_recursive_by_layout_local(
                    h_split.bottom_zone, h_split.bottom_elements,
                    layout_type, depth + 1
                )
                return top_groups + bottom_groups

        # 수직 분할 시도 (로컬 버전 - 중앙점 기준)
        if layout_type in [LayoutType.STANDARD_2_COLUMN, LayoutType.MIXED_TOP1_BOTTOM2, LayoutType.MIXED_TOP2_BOTTOM1]:
            v_split = self._find_vertical_split_kmeans_local(current_zone, anchors)
            if v_split:
                logger.debug(f"{indent}  수직 분할 성공 (로컬 - 중앙점 기준)")
                left_groups = self._sort_recursive_by_layout_local(
                    v_split.left_zone,
                    [e for e in elements_in_zone if e.bbox_x + e.bbox_width / 2 < v_split.gutter_x],
                    LayoutType.STANDARD_1_COLUMN, depth + 1
                )
                right_groups = self._sort_recursive_by_layout_local(
                    v_split.right_zone,
                    [e for e in elements_in_zone if e.bbox_x + e.bbox_width / 2 >= v_split.gutter_x],
                    LayoutType.STANDARD_1_COLUMN, depth + 1
                )
                return left_groups + right_groups

        # 수평 Y Gap 분할
        if layout_type in [LayoutType.MIXED_TOP1_BOTTOM2, LayoutType.MIXED_TOP2_BOTTOM1]:
            h_gap_split = find_horizontal_split_by_y_gap(current_zone, elements_in_zone)
            if h_gap_split:
                logger.debug(f"{indent}  수평 분할 (Y Gap) 성공")
                top_groups = self._sort_recursive_by_layout_local(
                    h_gap_split.top_zone, h_gap_split.top_elements,
                    LayoutType.STANDARD_1_COLUMN if layout_type == LayoutType.MIXED_TOP1_BOTTOM2 else LayoutType.STANDARD_2_COLUMN,
                    depth + 1
                )
                bottom_groups = self._sort_recursive_by_layout_local(
                    h_gap_split.bottom_zone, h_gap_split.bottom_elements,
                    LayoutType.STANDARD_2_COLUMN if layout_type == LayoutType.MIXED_TOP1_BOTTOM2 else LayoutType.STANDARD_1_COLUMN,
                    depth + 1
                )
                return top_groups + bottom_groups

        # Base Case (로컬 버전)
        logger.debug(f"{indent}  Base Case 도달 (로컬 버전)")
        if layout_type in [LayoutType.STANDARD_1_COLUMN, LayoutType.STANDARD_2_COLUMN]:
            return self._base_case_standard_1_column_local(current_zone, elements_in_zone)
        elif layout_type in [LayoutType.MIXED_TOP1_BOTTOM2, LayoutType.MIXED_TOP2_BOTTOM1]:
            return _base_case_mixed_layout(current_zone, elements_in_zone, layout_type)
        else:
            return self._base_case_standard_1_column_local(current_zone, elements_in_zone)

    def _find_vertical_split_kmeans_local(
        self,
        zone: Zone,
        anchors: List[MockElement]
    ) -> Optional[VerticalSplit]:
        """
        수직 분할 (로컬 버전 - 구버전)

        핵심 차이: 중앙점 기준 경계 설정 (보수적)
        gutter_x = (centers[0] + centers[1]) / 2
        """
        if len(anchors) < MIN_ANCHORS_FOR_SPLIT:
            return None

        anchor_x_centers = np.array([[a.bbox_x + a.bbox_width / 2] for a in anchors])
        if len(np.unique(anchor_x_centers)) < 2:
            return None

        try:
            kmeans = KMeans(n_clusters=KMEANS_N_CLUSTERS, random_state=42, n_init='auto')
            kmeans.fit(anchor_x_centers)
            centers = sorted(kmeans.cluster_centers_.flatten())

            if len(centers) == 2 and centers[1] - centers[0] >= KMEANS_CLUSTER_SEPARATION_MIN:
                # 🔥 로컬 버전: 중앙점 기준 (구버전)
                gutter_x = (centers[0] + centers[1]) / 2

                if zone.x_min < gutter_x < zone.x_max:
                    left_zone = Zone(zone.x_min, zone.y_min, int(gutter_x), zone.y_max)
                    right_zone = Zone(int(gutter_x), zone.y_min, zone.x_max, zone.y_max)
                    logger.debug(f"    수직 분할 성공 (로컬 - 중앙점): gutter_x={gutter_x:.1f}")
                    return VerticalSplit(left_zone, right_zone, gutter_x)
                else:
                    logger.warning(f"    수직 분할: 경계선({gutter_x:.1f})이 구역 밖")
                    return None
            else:
                logger.debug(f"    수직 분할 실패: 중심간 거리 부족")
                return None
        except Exception as e:
            logger.error(f"    수직 분할 K-Means 오류: {e}")
            return None

    def _base_case_standard_1_column_local(
        self,
        zone: Zone,
        elements: List[MockElement]
    ) -> List[ElementGroup]:
        """
        표준 1단 Base Case (로컬 버전 - 구버전)

        핵심 차이: 2D 거리 기반 그룹핑 생략
        - 수평 인접 처리
        - 순차 처리만 (2D 거리 그룹핑 X)
        """
        logger.debug(f"    표준 1단 Base Case (로컬 버전 - 순차 처리만): {len(elements)}개 요소")

        anchors = sorted([e for e in elements if e.class_name in ALLOWED_ANCHORS], key=lambda e: e.y_position)
        children = [e for e in elements if e.class_name in ALLOWED_CHILDREN]
        groups: Dict[int, ElementGroup] = {anchor.element_id: ElementGroup(anchor=anchor) for anchor in anchors}
        assigned_children_ids = set()

        # 1단계: 수평 인접 처리 (global과 동일)
        if anchors and children:
            for anchor in anchors:
                anchor_cy = anchor.bbox_y + anchor.bbox_height / 2
                anchor_right_x = anchor.bbox_x + anchor.bbox_width
                anchor_left_x = anchor.bbox_x
                unassigned_children = [c for c in children if c.element_id not in assigned_children_ids]
                adjacent_child = None
                min_y_diff = float('inf')

                for child in unassigned_children:
                    child_cy = child.bbox_y + child.bbox_height / 2
                    child_right_x = child.bbox_x + child.bbox_width
                    child_left_x = child.bbox_x
                    y_diff = abs(anchor_cy - child_cy)
                    y_threshold = (anchor.bbox_height + child.bbox_height) / 2 * HORIZONTAL_ADJACENCY_Y_CENTER_RATIO if (anchor.bbox_height + child.bbox_height) > 0 else 0

                    if y_diff >= y_threshold:
                        continue

                    gap_right = child_left_x - anchor_right_x
                    gap_left = anchor_left_x - child_right_x
                    is_adjacent = (abs(gap_right) < HORIZONTAL_ADJACENCY_X_PROXIMITY) or (abs(gap_left) < HORIZONTAL_ADJACENCY_X_PROXIMITY)

                    if is_adjacent and y_diff < min_y_diff:
                        min_y_diff = y_diff
                        adjacent_child = child

                if adjacent_child:
                    logger.trace(f"        수평 인접 배정: 앵커 ID {anchor.element_id} <- 자식 ID {adjacent_child.element_id}")
                    groups[anchor.element_id].add_child(adjacent_child)
                    assigned_children_ids.add(adjacent_child.element_id)

        logger.debug(f"    수평 인접 처리 완료: {len(assigned_children_ids)}개 자식 배정됨")

        # 2단계: 나머지 요소 순차 처리 (로컬 버전 - 2D 거리 그룹핑 생략)
        remaining_elements = anchors + [c for c in children if c.element_id not in assigned_children_ids]

        if not remaining_elements:
            logger.debug("    모든 요소가 수평 인접으로 배정되어 그룹핑 완료.")
            temp_groups = sorted(list(groups.values()), key=lambda g: g.anchor.y_position if g.anchor else float('inf'))
            for idx, group in enumerate(temp_groups):
                group.group_id = idx
            return _post_process_table_figure_assignment(temp_groups)

        logger.trace(f"      나머지 요소 {len(remaining_elements)}개 (Y, X) 정렬 및 순차 그룹핑 시작...")
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
                        logger.warning(f"        앵커 없이 자식 요소(ID: {element.element_id}) 발견됨")
                        if element.y_position < top_orphan_threshold_y:
                            initial_top_orphan_children.append(element)
                        else:
                            initial_bottom_orphan_children.append(element)
                    else:
                        current_group.add_child(element)
                        logger.trace(f"        현재 그룹에 자식 추가 (ID: {element.element_id})")
                else:
                    if element.y_position < top_orphan_threshold_y:
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

        processed_anchor_ids = set(g.anchor.element_id for g in final_groups if g.anchor)
        for anchor_id, group in groups.items():
            if anchor_id not in processed_anchor_ids and group.anchor:
                final_groups.append(group)
                logger.trace(f"        미포함 앵커 그룹 추가 (수평 인접만): ID {anchor_id}")

        final_groups.sort(key=lambda g: g.anchor.y_position if g.anchor else (min(c.y_position for c in g.children) if g.children else float('inf')))

        # 후처리 호출 전 그룹 ID 임시 할당
        for idx, group in enumerate(final_groups):
            group.group_id = idx

        final_groups = _post_process_table_figure_assignment(final_groups)

        logger.debug(f"    순차 처리 기반 그룹핑 (+후처리) 완료: {len(final_groups)} 그룹 생성")
        return final_groups


# ============================================================================
# LayoutProfiler (Phase 1 기본 구현)
# ============================================================================

class LayoutProfiler:
    """
    레이아웃 프로파일 분석기 (Phase 1 기본 버전)

    입력 요소들을 분석하여 전략을 추천
    """

    @staticmethod
    def analyze(
        elements: List[MockElement],
        page_width: int,
        page_height: int
    ) -> LayoutProfile:
        """
        레이아웃 분석 및 전략 추천

        Phase 1: 기본 휴리스틱 사용
        - global_consistency_score > 0.7 → GLOBAL_FIRST
        - horizontal_adjacency_ratio > 0.5 → LOCAL_FIRST
        - 그 외 → GLOBAL_FIRST (기본값)
        """
        logger.info("[LayoutProfiler] 레이아웃 분석 시작...")

        anchors = [e for e in elements if e.class_name in ALLOWED_ANCHORS]
        children = [e for e in elements if e.class_name in ALLOWED_CHILDREN]

        # 1. 앵커 X 좌표 표준편차 계산
        if len(anchors) >= 2:
            anchor_x_centers = [a.bbox_x + a.bbox_width / 2 for a in anchors]
            anchor_x_std = float(np.std(anchor_x_centers))
        else:
            anchor_x_std = 0.0

        # 2. 전역 일관성 점수 계산 (간단한 휴리스틱)
        # X 좌표 분산이 작을수록 일관성 높음
        max_x_std = page_width * 0.3  # 페이지 너비의 30%를 최대 표준편차로 가정
        global_consistency_score = max(0.0, 1.0 - (anchor_x_std / max_x_std)) if max_x_std > 0 else 0.5

        # 3. 수평 인접 요소 비율 계산 (Phase 1 간단 버전)
        horizontal_adjacency_count = 0
        if anchors and children:
            for anchor in anchors:
                anchor_cy = anchor.bbox_y + anchor.bbox_height / 2
                anchor_right_x = anchor.bbox_x + anchor.bbox_width
                for child in children:
                    child_cy = child.bbox_y + child.bbox_height / 2
                    child_left_x = child.bbox_x
                    y_diff = abs(anchor_cy - child_cy)
                    y_threshold = (anchor.bbox_height + child.bbox_height) / 2 * HORIZONTAL_ADJACENCY_Y_CENTER_RATIO if (anchor.bbox_height + child.bbox_height) > 0 else 0
                    gap_right = child_left_x - anchor_right_x
                    if y_diff < y_threshold and abs(gap_right) < HORIZONTAL_ADJACENCY_X_PROXIMITY:
                        horizontal_adjacency_count += 1
                        break  # 각 앵커당 하나의 인접 요소만 카운트

        horizontal_adjacency_ratio = horizontal_adjacency_count / len(anchors) if anchors else 0.0

        # 4. 전략 추천 (Phase 1 간단한 규칙)
        if global_consistency_score > 0.7:
            recommended_strategy = SortingStrategyType.GLOBAL_FIRST
            reason = f"전역 일관성 점수 높음 ({global_consistency_score:.2f})"
        elif horizontal_adjacency_ratio > 0.5:
            recommended_strategy = SortingStrategyType.LOCAL_FIRST
            reason = f"수평 인접 비율 높음 ({horizontal_adjacency_ratio:.2f})"
        else:
            recommended_strategy = SortingStrategyType.GLOBAL_FIRST
            reason = "기본값 (전역 우선)"

        logger.info(f"[LayoutProfiler] 분석 완료:")
        logger.info(f"  - 전역 일관성 점수: {global_consistency_score:.3f}")
        logger.info(f"  - 앵커 X 표준편차: {anchor_x_std:.1f}px")
        logger.info(f"  - 수평 인접 비율: {horizontal_adjacency_ratio:.3f}")
        logger.info(f"  - 추천 전략: {recommended_strategy.name} ({reason})")

        return LayoutProfile(
            global_consistency_score=global_consistency_score,
            anchor_x_std=anchor_x_std,
            horizontal_adjacency_ratio=horizontal_adjacency_ratio,
            recommended_strategy=recommended_strategy
        )


# ============================================================================
# Strategy Factory
# ============================================================================

class SortingStrategyFactory:
    """전략 인스턴스 생성 팩토리"""

    _strategies: Dict[SortingStrategyType, SortingStrategy] = {
        SortingStrategyType.GLOBAL_FIRST: GlobalFirstStrategy(),
        SortingStrategyType.LOCAL_FIRST: LocalFirstStrategy(),
    }

    @classmethod
    def get_strategy(cls, strategy_type: SortingStrategyType) -> SortingStrategy:
        """전략 타입에 따라 전략 인스턴스 반환"""
        if strategy_type not in cls._strategies:
            raise ValueError(f"지원되지 않는 전략 타입: {strategy_type}")
        return cls._strategies[strategy_type]


# ============================================================================
# Adaptive 메인 함수 (Phase 1)
# ============================================================================

def sort_layout_elements_adaptive(
    elements: List[MockElement],
    document_type: str,
    page_width: int,
    page_height: int,
    force_strategy: Optional[str] = None
) -> List[MockElement]:
    """
    Adaptive 정렬 함수 (Phase 1 프로토타입)

    Args:
        elements: 정렬할 요소 리스트
        document_type: 문서 타입 ("question_based" 또는 "reading_order")
        page_width: 페이지 너비
        page_height: 페이지 높이
        force_strategy: 강제 전략 ("GLOBAL_FIRST", "LOCAL_FIRST", None)
                       Phase 1에서는 이 파라미터로 전략 강제 선택
                       None이면 자동 선택 (Phase 2)

    Returns:
        정렬된 요소 리스트
    """
    logger.info("=" * 80)
    logger.info("[Adaptive Sorter] Phase 1 프로토타입 실행")
    logger.info(f"  - 강제 전략: {force_strategy if force_strategy else '자동 선택'}")
    logger.info("=" * 80)

    # 전략 선택
    if force_strategy:
        # Phase 1: 강제 전략 사용
        try:
            strategy_type = SortingStrategyType[force_strategy.upper()]
            logger.info(f"[Phase 1] 강제 전략 사용: {strategy_type.name}")
        except KeyError:
            logger.error(f"유효하지 않은 전략 이름: {force_strategy}")
            logger.info("기본 전략(GLOBAL_FIRST) 사용")
            strategy_type = SortingStrategyType.GLOBAL_FIRST
    else:
        # Phase 2: 자동 선택 (Phase 1에서는 기본값)
        logger.info("[Phase 1] 자동 전략 선택은 Phase 2에서 구현 예정. 기본값(GLOBAL_FIRST) 사용")

        # Phase 2 연동을 위한 프로파일링 (현재는 로그만)
        profile = LayoutProfiler.analyze(elements, page_width, page_height)
        logger.info(f"프로파일 분석 결과: 추천 전략 = {profile.recommended_strategy.name}")
        logger.info("(Phase 1에서는 무시하고 GLOBAL_FIRST 사용)")

        strategy_type = SortingStrategyType.GLOBAL_FIRST

    # 전략 실행
    strategy = SortingStrategyFactory.get_strategy(strategy_type)
    sorted_elements = strategy.sort(
        elements=elements,
        document_type=document_type,
        page_width=page_width,
        page_height=page_height
    )

    logger.info(f"[Adaptive Sorter] 완료: {len(sorted_elements)}개 요소 정렬됨")
    logger.info("=" * 80)

    return sorted_elements
