# -*- coding: utf-8 -*-
"""
SmartEyeSsen Layout Sorter (v.Final.1 - Orphan Handling Enhanced)
=================================================================

문제 레이아웃 정렬 알고리즘 구현 (최종 설계안 v.Final 기반)
페이지 전체 컬럼 구조를 먼저 파악하고, 각 컬럼 내에서 '영역 확장 하이브리드 정렬' 적용.
v.Final.1: 고아 그룹(orphan_group) 처리 로직 개선 추가.

알고리즘 흐름:
1. 컬럼 감지 및 분리: 앵커 X좌표 K-Means로 컬럼 수 결정 후 모든 요소 배정.
2. 컬럼별 그룹화: 각 컬럼 내에서 '영역 확장 하이브리드 정렬' 실행.
3. 최종 병합 및 순서 부여: 컬럼별 그룹 결과를 순서대로 병합 (고아 그룹 우선 배치 고려).

주요 변경사항 (v.Final 대비):
- hybrid_grouping_strategy: 고아 그룹 처리 로직 수정 (페이지 최상단 요소 우선 배치).

References:
- 알고리즘 명세: 최종 설계 보고서 (v.Final), 영역 확장 기반 하이브리드 정렬 전략
- Mock 모델: backend/app/services/mock_models.py
"""

from typing import List, Dict, Tuple, Optional, Any
from dataclasses import dataclass, field
import numpy as np
from sklearn.cluster import KMeans
from sklearn.metrics import silhouette_score # 컬럼 수 결정을 위해 추가
from loguru import logger
import math

# Mock 모델 임포트 (기존 코드와 동일)
from .mock_models import MockElement


# ============================================================================
# 상수 정의 (기존과 동일)
# ============================================================================

ALLOWED_ANCHORS = ["question type", "question number", "second_question_number"]
ALLOWED_CHILDREN = ["question text", "list", "choices", "figure", "table", "flowchart"]
ALLOWED_CLASSES = ALLOWED_ANCHORS + ALLOWED_CHILDREN

MAX_COLUMNS_TO_CHECK = 3
MIN_ANCHORS_FOR_KMEANS = 3
COLUMN_SILHOUETTE_THRESHOLD = 0.6
MIN_COLUMN_SEPARATION = 50

HORIZONTAL_ADJACENCY_Y_CENTER_RATIO = 0.7
HORIZONTAL_ADJACENCY_X_PROXIMITY = 50

# ============================================================================
# 데이터 클래스 (ElementGroup - 기존과 동일)
# ============================================================================

@dataclass
class ElementGroup:
    """하이브리드 그룹핑 결과를 담는 클래스"""
    anchor: Optional[MockElement]
    children: List[MockElement] = field(default_factory=list)
    group_id: int = -1

    def add_child(self, child: MockElement):
        self.children.append(child)

    def get_all_elements(self) -> List[MockElement]:
        """앵커와 자식들을 합쳐 Y좌표 순으로 정렬된 리스트 반환"""
        elements = []
        if self.anchor:
            elements.append(self.anchor)
        elements.extend(self.children)
        elements.sort(key=lambda e: (e.y_position, e.x_position))
        return elements

    def __repr__(self) -> str:
        anchor_id = self.anchor.element_id if self.anchor else "None"
        child_ids = [c.element_id for c in self.children]
        return f"Group(Anchor: {anchor_id}, Children: {child_ids})"

# ============================================================================
# 메인 함수: 컬럼 우선 정렬 (v.Final.1 - 기존과 동일)
# ============================================================================

def sort_layout_elements_v_final(
    elements: List[MockElement],
    document_type: str = "question_based",
    page_width: Optional[int] = None,
    page_height: Optional[int] = None
) -> List[MockElement]:
    """
    레이아웃 요소 정렬 - 최종 설계안 (v.Final.1) 구현
    """
    logger.info(f"v.Final.1 정렬 시작: {len(elements)}개 요소, 문서 타입={document_type}")

    filtered_elements = preprocess_elements(elements, document_type)
    if not filtered_elements:
        logger.warning("정렬할 요소가 없습니다 (전처리 후 빈 리스트)")
        return []

    if page_width is None:
        page_width = calculate_page_width(filtered_elements)
    logger.info(f"페이지 너비: {page_width}px")

    if document_type == "reading_order":
        logger.info("reading_order 타입: 컬럼 분리 없이 (Y,X) 정렬 수행")
        sorted_elements = sorted(filtered_elements, key=lambda e: (e.y_position, e.x_position))
        grouped_results = [ElementGroup(anchor=None, children=[elem]) for elem in sorted_elements]
        final_sorted_elements = flatten_and_assign_order(grouped_results)
        logger.info(f"v.Final.1 정렬 완료 (reading_order): {len(final_sorted_elements)}개 요소")
        return final_sorted_elements

    try:
        elements_by_column = detect_columns_and_assign_elements(filtered_elements, page_width)
        num_columns = len(elements_by_column)
        logger.info(f"1단계 완료: {num_columns}개 컬럼 감지 및 요소 배정 완료")
        for i, col_elements in enumerate(elements_by_column):
            logger.debug(f"  컬럼 {i+1}: {len(col_elements)}개 요소")
    except Exception as e:
        logger.error(f"1단계 컬럼 감지/분리 중 오류 발생: {e}. 단일 컬럼으로 처리합니다.", exc_info=True)
        elements_by_column = [filtered_elements]

    all_grouped_results: List[ElementGroup] = []
    logger.info("2단계 시작: 컬럼별 하이브리드 그룹핑 수행...")
    for i, column_elements in enumerate(elements_by_column):
        if not column_elements:
            logger.debug(f"  컬럼 {i+1} 건너뜀 (요소 없음)")
            continue
        logger.debug(f"  컬럼 {i+1} 그룹핑 시작 ({len(column_elements)}개 요소)")
        groups_in_column = hybrid_grouping_strategy(None, column_elements) # 변경된 로직 호출
        all_grouped_results.extend(groups_in_column)
        logger.debug(f"  컬럼 {i+1} 그룹핑 완료 ({len(groups_in_column)}개 그룹 생성)")

    # << --- 여기에 아래 코드 추가 시작 --- >>
    logger.info(f"2단계 완료: 총 {len(all_grouped_results)}개 그룹 생성 (컬럼 병합)") # 위치 이동

    # --- 신규: 전역 고아 그룹 처리 ---
    orphan_groups = [g for g in all_grouped_results if g.anchor is None]
    non_orphan_groups = [g for g in all_grouped_results if g.anchor is not None]

    if orphan_groups:
        logger.debug(f"전역 고아 그룹 {len(orphan_groups)}개 발견, 리스트 맨 앞으로 이동시킴")
        # 고아 그룹들을 먼저 배치. 필요 시 고아 그룹 내부 정렬 또는 앵커 그룹 정렬 추가 가능.
        # 예: orphan_groups.sort(key=lambda g: min(c.y_position for c in g.children) if g.children else float('inf'))
        # 예: non_orphan_groups.sort(key=lambda g: g.anchor.y_position if g.anchor else float('inf'))
        all_grouped_results = orphan_groups + non_orphan_groups # 고아 그룹을 앞으로
    else:
        logger.debug("전역 고아 그룹 없음")
        # 필요 시 앵커 그룹 정렬 로직 추가 가능
        pass # 현재는 기존 컬럼 순서 유지
    # << --- 코드 추가 끝 --- >>
    
    logger.info("3단계 시작: 최종 병합 및 순서 부여...")
    final_sorted_elements = flatten_and_assign_order(all_grouped_results)

    logger.info(f"v.Final.1 정렬 완료 (question_based): {len(final_sorted_elements)}개 요소")
    return final_sorted_elements

# ============================================================================
# 1단계: 컬럼 감지 및 분리 (기존과 동일)
# ============================================================================

def detect_columns_and_assign_elements(elements: List[MockElement], page_width: int) -> List[List[MockElement]]:
    """앵커 K-Means로 컬럼 감지 및 요소 배정 (기존과 동일)"""
    anchors = [e for e in elements if e.class_name in ALLOWED_ANCHORS]
    logger.debug(f"컬럼 감지 시작: {len(anchors)}개 앵커 사용")

    if len(anchors) < MIN_ANCHORS_FOR_KMEANS:
        logger.info("앵커 수가 부족하여 단일 컬럼으로 간주합니다.")
        return [elements]

    anchor_x_centers = np.array([[a.bbox_x + a.bbox_width / 2] for a in anchors])

    best_k = 1
    best_score = -1
    best_kmeans = None

    for k in range(1, MAX_COLUMNS_TO_CHECK + 1):
        if len(anchors) < k: break
        if k == 1 and len(np.unique(anchor_x_centers)) == 1:
             logger.debug(f"모든 앵커 X좌표 동일, K=1 확정")
             best_k = 1
             break
        if k == 1:
             best_k = 1
             continue

        try:
            kmeans = KMeans(n_clusters=k, random_state=42, n_init='auto')
            labels = kmeans.fit_predict(anchor_x_centers)
            score = silhouette_score(anchor_x_centers, labels)
            logger.debug(f"  K={k} 시도: 실루엣 점수 = {score:.3f}")

            if score >= COLUMN_SILHOUETTE_THRESHOLD and score > best_score:
                 centers = sorted(kmeans.cluster_centers_.flatten())
                 if k > 1:
                     min_dist = min(centers[j+1] - centers[j] for j in range(k-1))
                     if min_dist < MIN_COLUMN_SEPARATION:
                         logger.debug(f"    K={k}: 컬럼 중심 간 최소 거리({min_dist:.1f}px)가 임계값({MIN_COLUMN_SEPARATION}px) 미만")
                         continue
                 best_k = k
                 best_score = score
                 best_kmeans = kmeans
                 logger.debug(f"    K={k}가 새로운 최적 후보 (점수: {score:.3f})")
        except ValueError as e:
            logger.warning(f"  K={k} 시도 중 오류: {e}")
            if k == 1: best_k = 1
            break
        except Exception as e:
            logger.error(f"  K={k} 시도 중 예외 발생: {e}", exc_info=True)
            if k == 1: best_k = 1
            break

    logger.info(f"최적 컬럼 수 결정: K = {best_k}")

    if best_k == 1:
        return [elements]

    centers = sorted(best_kmeans.cluster_centers_.flatten())
    boundaries = [(centers[i] + centers[i+1]) / 2 for i in range(best_k - 1)]
    logger.debug(f"컬럼 경계 (Boundaries): {boundaries}")

    elements_by_column: List[List[MockElement]] = [[] for _ in range(best_k)]
    for element in elements:
        x_center = element.bbox_x + element.bbox_width / 2
        assigned_column = 0
        for i, boundary in enumerate(boundaries):
            if x_center >= boundary:
                assigned_column = i + 1
            else:
                break
        elements_by_column[assigned_column].append(element)
    return elements_by_column

# ============================================================================
# 2단계: 컬럼별 그룹화 (하이브리드 전략 - 고아 처리 수정)
# ============================================================================

def hybrid_grouping_strategy(zone: Optional[Any], elements: List[MockElement]) -> List[ElementGroup]:
    """
    '영역 확장 기반 하이브리드 정렬' + 개선된 고아 처리 로직
    """
    logger.debug(f"하이브리드 그룹핑 시작 (컬럼 내): {len(elements)}개 요소")
    if zone is not None:
         logger.warning("hybrid_grouping_strategy에 zone 객체가 전달되었으나 사용되지 않습니다.")

    # 1단계: 역할 분리 및 앵커 정렬
    anchors = sorted([e for e in elements if e.class_name in ALLOWED_ANCHORS], key=lambda e: e.y_position)
    children = [e for e in elements if e.class_name in ALLOWED_CHILDREN]
    logger.debug(f"  역할 분리: {len(anchors)}개 앵커, {len(children)}개 자식")

    groups: Dict[int, ElementGroup] = {anchor.element_id: ElementGroup(anchor=anchor) for anchor in anchors}
    orphan_group = ElementGroup(anchor=None) # 고아 요소 그룹 초기화
    assigned_children_ids = set()

    # 2단계: 영역 확장 (수평 인접) - 기존과 동일
    logger.debug("  2단계: 영역 확장 (수평 인접) 처리 시작...")
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
                y_threshold = (anchor.bbox_height + child.bbox_height) / 2 * HORIZONTAL_ADJACENCY_Y_CENTER_RATIO
                if y_diff >= y_threshold: continue
                gap_right = child_left_x - anchor_right_x
                gap_left = anchor_left_x - child_right_x
                is_adjacent = (0 <= gap_right < HORIZONTAL_ADJACENCY_X_PROXIMITY) or \
                              (0 <= gap_left < HORIZONTAL_ADJACENCY_X_PROXIMITY)
                if is_adjacent and y_diff < min_y_diff:
                     min_y_diff = y_diff
                     adjacent_child = child
            if adjacent_child:
                logger.debug(f"    수평 인접 발견 및 배정: 앵커 ID {anchor.element_id} <- 자식 ID {adjacent_child.element_id}")
                groups[anchor.element_id].add_child(adjacent_child)
                assigned_children_ids.add(adjacent_child.element_id)
    logger.debug(f"  2단계 완료: {len(assigned_children_ids)}개 자식 우선 배정됨")

    # 3단계: 읽기 흐름 (일반 자식) - 기존과 동일
    logger.debug("  3단계: 읽기 흐름 (일반 자식) 처리 시작...")
    remaining_children = [c for c in children if c.element_id not in assigned_children_ids]
    remaining_children.sort(key=lambda e: (e.y_position, e.x_position))
    for child in remaining_children:
        parent_candidates = [a for a in anchors if a.y_position < child.y_position]
        if parent_candidates:
            best_parent = max(parent_candidates, key=lambda a: a.y_position)
            groups[best_parent.element_id].add_child(child)
            assigned_children_ids.add(child.element_id)
            logger.debug(f"    자식 ID {child.element_id} -> 앵커 ID {best_parent.element_id} 배정 (읽기 흐름)")
        else:
            orphan_group.add_child(child) # 고아 그룹에 추가
            assigned_children_ids.add(child.element_id)
            logger.debug(f"    자식 ID {child.element_id} -> 고아 그룹 배정")
    logger.debug("  3단계 완료.")

    # --- 4단계: 그룹 완성 및 반환 (고아 그룹 처리 로직 수정) ---
    final_groups = []
    is_orphan_first = False # 고아 그룹이 맨 앞에 와야 하는지 플래그

    if orphan_group.children:
        logger.debug(f"  고아 그룹 존재: {len(orphan_group.children)}개 요소")
        # 고아 그룹의 최대 Y 위치 계산 (요소의 하단 기준)
        max_orphan_y = max(o.bbox_y + o.bbox_height for o in orphan_group.children)

        # 첫 번째 앵커의 최소 Y 위치 계산 (앵커의 상단 기준)
        min_anchor_y = anchors[0].y_position if anchors else float('inf')

        # 고아 그룹이 명확히 첫 앵커보다 위에 있는지 확인
        if max_orphan_y < min_anchor_y:
            logger.debug(f"    고아 그룹이 첫 앵커보다 위에 있음 (max_orphan_y={max_orphan_y} < min_anchor_y={min_anchor_y})")
            final_groups.append(orphan_group) # 고아 그룹을 맨 앞에 추가
            is_orphan_first = True
        else:
            # 고아 그룹이 첫 앵커와 섞여 있거나 아래에 있으면 일단 뒤에 추가 (나중에 순서 재정렬 가능성 있음)
            logger.debug(f"    고아 그룹이 첫 앵커와 섞여 있거나 아래에 있음 (max_orphan_y={max_orphan_y} >= min_anchor_y={min_anchor_y})")
            # 일단 앵커 그룹들 뒤에 추가하고, flatten 단계에서 전체 순서 부여
            pass # 아래 extend 후에 추가

    # 앵커 그룹들을 Y좌표 순서대로 추가
    anchor_groups = [groups[anchor.element_id] for anchor in anchors]
    final_groups.extend(anchor_groups)

    # 고아 그룹이 맨 앞에 배치되지 않았고 존재한다면 맨 뒤에 추가
    if orphan_group.children and not is_orphan_first:
        final_groups.append(orphan_group)
        logger.debug(f"    고아 그룹을 맨 뒤에 추가")


    logger.debug(f"하이브리드 그룹핑 완료 (컬럼 내): {len(final_groups)}개 그룹 생성 (고아 그룹 처리 포함)")
    return final_groups


# ============================================================================
# 3단계: 최종 병합 및 순서 부여 (기존과 동일)
# ============================================================================

def flatten_and_assign_order(groups: List[ElementGroup]) -> List[MockElement]:
    """그룹 리스트 평탄화 및 최종 순서 부여 (기존과 동일)"""
    flattened = []
    global_order = 0
    logger.debug(f"Flatten 시작: 총 {len(groups)}개 그룹")

    for group_idx, group in enumerate(groups):
        anchor_id_repr = group.anchor.element_id if group.anchor else "Orphan"
        logger.debug(f"  Group {group_idx} 처리 (Anchor: {anchor_id_repr})")
        elements_in_group = group.get_all_elements()
        logger.debug(f"    - 요소 (정렬 후): {[e.element_id for e in elements_in_group]}")

        for local_order, element in enumerate(elements_in_group):
            try:
                setattr(element, 'order_in_question', global_order)
                setattr(element, 'group_id', group_idx)
                setattr(element, 'order_in_group', local_order)
            except AttributeError:
                 logger.warning(f"요소 ID {element.element_id}에 정렬 속성 추가 실패")
                 pass
            flattened.append(element)
            global_order += 1

    logger.info(f"최종 병합 및 순서 부여 완료: {len(groups)}개 그룹 → {len(flattened)}개 요소")
    return flattened

# ============================================================================
# 헬퍼 함수 (기존과 동일)
# ============================================================================

def preprocess_elements(elements: List[MockElement], document_type: str) -> List[MockElement]:
    """0단계: 전처리 (기존과 동일)"""
    if document_type == "question_based":
        filtered = [e for e in elements if e.class_name in ALLOWED_CLASSES]
        logger.info(f"전처리 (question_based): {len(elements)}개 → {len(filtered)}개 (허용 클래스 필터링)")
    elif document_type == "reading_order":
        filtered = elements
        logger.info(f"전처리 (reading_order): {len(elements)}개 (모든 클래스 허용)")
    else:
        logger.warning(f"알 수 없는 문서 타입 '{document_type}', 모든 요소 반환")
        filtered = elements
    filtered = [e for e in filtered if hasattr(e, 'area') and e.area > 0]
    return filtered

def calculate_page_width(elements: List[MockElement]) -> int:
    """페이지 너비 추정 (기존과 동일)"""
    if not elements: return 0
    max_x = max(e.bbox_x + e.bbox_width for e in elements) if elements else 0
    return max_x

# ============================================================================
# 공개 API (기존과 동일)
# ============================================================================

def sort_layout_elements(
    elements: List[MockElement],
    document_type: str = "question_based",
    page_width: Optional[int] = None,
    page_height: Optional[int] = None
) -> List[MockElement]:
    """레이아웃 요소 정렬 - 공개 API (v.Final.1 알고리즘 호출)"""
    return sort_layout_elements_v_final(
        elements=elements,
        document_type=document_type,
        page_width=page_width,
        page_height=page_height
    )