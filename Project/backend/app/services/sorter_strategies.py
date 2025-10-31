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
    _sort_layout_elements_v24 as _sort_layout_elements_new,
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
from .sorter_구버전 import (
    sort_layout_elements as _sort_layout_elements_legacy
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
@dataclass
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
        """현재 sorter.py (v2.4) 로직 직접 호출"""
        logger.info("[GlobalFirstStrategy] 전역 우선 전략 실행 중 (v2.4 코어 호출)")
        return _sort_layout_elements_new(
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

    구 버전 sorter_구버전.py의 정렬 함수를 그대로 호출한다.
    """

    def sort(
        self,
        elements: List[MockElement],
        document_type: str,
        page_width: int,
        page_height: int
    ) -> List[MockElement]:
        logger.info("[LocalFirstStrategy] 로컬 우선 전략 실행 중 (구버전 직접 호출)")
        return _sort_layout_elements_legacy(
            elements=elements,
            document_type=document_type,
            page_width=page_width,
            page_height=page_height
        )

@dataclass
class LayoutProfile:
    """레이아웃 프로파일 (Phase 2 확장 버전)"""

    global_consistency_score: float
    anchor_x_std: float
    horizontal_adjacency_ratio: float
    anchor_count: int
    layout_type: LayoutType
    page_width: int
    page_height: int
    anchor_y_variance: float
    recommended_strategy: SortingStrategyType


# ============================================================================
# LayoutProfiler (Phase 2 구현)
# ============================================================================

class LayoutProfiler:
    """
    레이아웃 프로파일 분석기

    입력 요소들의 전역/로컬 패턴을 분석하여 최적 전략을 추천한다.
    """

    @staticmethod
    def analyze(
        elements: List[MockElement],
        page_width: Optional[int],
        page_height: Optional[int]
    ) -> LayoutProfile:
        """레이아웃 특성 분석 및 전략 추천"""

        logger.info("[LayoutProfiler] 레이아웃 분석 시작...")

        anchors = [e for e in elements if e.class_name in ALLOWED_ANCHORS]
        children = [e for e in elements if e.class_name in ALLOWED_CHILDREN]

        if not page_width or page_width <= 0:
            page_width = int(max(e.bbox_x + e.bbox_width for e in elements)) if elements else 0
        if not page_height or page_height <= 0:
            page_height = int(max(e.bbox_y + e.bbox_height for e in elements)) if elements else 0

        # 1. 앵커 통계
        if len(anchors) >= 2:
            anchor_x_centers = [a.bbox_x + a.bbox_width / 2 for a in anchors]
            anchor_x_std = float(np.std(anchor_x_centers))
            anchor_y_centers = [a.bbox_y + a.bbox_height / 2 for a in anchors]
            anchor_y_variance = float(np.var(anchor_y_centers))
        else:
            anchor_x_std = 0.0
            anchor_y_variance = 0.0

        anchor_count = len(anchors)

        # 2. 전역 일관성 점수
        max_x_std = page_width * 0.3 if page_width else 0.0
        global_consistency_score = max(0.0, 1.0 - (anchor_x_std / max_x_std)) if max_x_std > 0 else 0.5

        # 3. 수평 인접 비율
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
                        break

        horizontal_adjacency_ratio = horizontal_adjacency_count / anchor_count if anchor_count else 0.0

        # 4. 레이아웃 유형 판별
        layout_type = detect_layout_type(elements, page_width, page_height)

        # 5. 전략 추천
        recommended_strategy = LayoutProfiler._recommend_strategy(
            consistency=global_consistency_score,
            anchor_x_std=anchor_x_std,
            horizontal_adjacency_ratio=horizontal_adjacency_ratio,
            layout_type=layout_type,
            anchor_count=anchor_count,
            page_width=page_width,
            page_height=page_height,
            anchor_y_variance=anchor_y_variance
        )

        logger.info(
            "[LayoutProfiler] 분석 완료: "
            f"consistency={global_consistency_score:.3f}, "
            f"adjacency={horizontal_adjacency_ratio:.3f}, "
            f"anchors={anchor_count}, "
            f"layout={layout_type.name}, "
            f"추천 전략={recommended_strategy.name}"
        )

        return LayoutProfile(
            global_consistency_score=global_consistency_score,
            anchor_x_std=anchor_x_std,
            horizontal_adjacency_ratio=horizontal_adjacency_ratio,
            anchor_count=anchor_count,
            layout_type=layout_type,
            page_width=page_width,
            page_height=page_height,
            anchor_y_variance=anchor_y_variance,
            recommended_strategy=recommended_strategy
        )

    @staticmethod
    def _recommend_strategy(
        consistency: float,
        anchor_x_std: float,
        horizontal_adjacency_ratio: float,
        layout_type: LayoutType,
        anchor_count: int,
        page_width: int,
        page_height: int,
        anchor_y_variance: float
    ) -> SortingStrategyType:
        """전략 추천 로직 (Phase 2)"""

        # 명확한 2단 구조
        if layout_type == LayoutType.STANDARD_2_COLUMN:
            if horizontal_adjacency_ratio >= 0.6:
                # 좁은 PDF 폭(주로 스캔된 PDF)에 최적화
                if page_width and page_width <= 2000:
                    return SortingStrategyType.GLOBAL_FIRST
                return SortingStrategyType.LOCAL_FIRST

            if horizontal_adjacency_ratio < 0.4:
                return SortingStrategyType.LOCAL_FIRST

            if anchor_count < 8:
                return SortingStrategyType.LOCAL_FIRST

            return SortingStrategyType.GLOBAL_FIRST if consistency >= 0.6 else SortingStrategyType.LOCAL_FIRST

        if layout_type in (LayoutType.MIXED_TOP1_BOTTOM2, LayoutType.MIXED_TOP2_BOTTOM1):
            return SortingStrategyType.LOCAL_FIRST

        if layout_type == LayoutType.HORIZONTAL_SEP_PRESENT:
            return SortingStrategyType.LOCAL_FIRST if horizontal_adjacency_ratio >= 0.4 else SortingStrategyType.GLOBAL_FIRST

        if horizontal_adjacency_ratio > 0.5:
            return SortingStrategyType.LOCAL_FIRST
        if consistency > 0.75:
            return SortingStrategyType.GLOBAL_FIRST
        if consistency < 0.4:
            return SortingStrategyType.LOCAL_FIRST

        return SortingStrategyType.GLOBAL_FIRST


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
    page_width: Optional[int] = None,
    page_height: Optional[int] = None,
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

    filtered_elements = preprocess_elements(elements, document_type)
    if not filtered_elements:
        logger.warning("전처리 후 정렬할 요소가 없습니다.")
        return []

    if not page_width or page_width <= 0:
        page_width = int(max(e.bbox_x + e.bbox_width for e in filtered_elements)) if filtered_elements else 0
    if not page_height or page_height <= 0:
        page_height = int(max(e.bbox_y + e.bbox_height for e in filtered_elements)) if filtered_elements else 0

    logger.info(f"[Adaptive] 페이지 크기 추정: {page_width} x {page_height}")

    # Phase 1: 강제 전략 사용
    if force_strategy:
        try:
            strategy_type = SortingStrategyType[force_strategy.upper()]
            logger.info(f"[Adaptive] 강제 전략 사용: {strategy_type.name}")
        except KeyError:
            logger.error(f"유효하지 않은 전략 이름: {force_strategy}")
            logger.info("기본 전략(GLOBAL_FIRST) 사용")
            strategy_type = SortingStrategyType.GLOBAL_FIRST
    else:
        # Phase 2: 자동 선택
        profile = LayoutProfiler.analyze(
            filtered_elements,
            page_width,
            page_height
        )
        logger.info(f"[Adaptive] 자동 전략 선택: {profile.recommended_strategy.name}")
        strategy_type = profile.recommended_strategy

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
