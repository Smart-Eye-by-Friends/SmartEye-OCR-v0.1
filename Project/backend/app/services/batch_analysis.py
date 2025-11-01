# -*- coding: utf-8 -*-
"""
SmartEyeSsen 배치 분석 서비스 (Phase 3.2 - v2.1 스키마 준비 완료)
===================================================================
(Phase 3.1, 3.3 대비 Mock DB 관리 함수 및 mock_text_versions 추가)

프로젝트 내 여러 페이지를 배치로 분석하는 서비스 모듈.

주요 기능:
- 프로젝트 내 모든 pending 페이지 자동 분석
- 6단계 분석 파이프라인 실행 (레이아웃 → OCR → AI → 정렬 → DB저장 → 포맷팅)
- db_saver.py (이제 v2.1 스키마 대상)를 사용하여 정렬 결과 저장
- 페이지별 에러 처리, 배치 처리 계속 진행 가능
- Mock DB에서 프로젝트 및 페이지 상태 업데이트
- (추가) Mock DB CRUD 함수 제공 (project_service, download_service용)
"""

import sys
from typing import List, Dict, Optional, Tuple, Any
from loguru import logger
import time
from datetime import datetime
import sys

# 애플리케이션 모듈 임포트
from .mock_models import MockElement, MockTextContent
from .sorter_strategies import sort_layout_elements_adaptive
from .formatter import TextFormatter
# db_saver는 이제 v2.1 저장 로직을 구현한다고 가정하고 임포트
from .db_saver import save_sorted_elements_to_mock_db
# 테스트/잠재적 사용을 위한 v2.1 조회 함수 임포트
from .db_saver import get_question_groups_by_page, get_question_elements_by_group

# ============================================================================
# Mock DB: 프로젝트, 페이지, 텍스트 버전 상태 관리
# (Phase 3.1, 3.3 구현을 위해 중앙 관리 지점으로 사용)
# ============================================================================

mock_projects: Dict[int, Dict] = {}
""" Mock projects 테이블 """
_next_project_id = 1 # 프로젝트 ID 자동 증가 카운터

mock_pages: Dict[int, Dict] = {}
""" Mock pages 테이블 """
_next_page_id = 1 # 페이지 ID 자동 증가 카운터

mock_text_versions: List[Dict] = []
"""
Mock text_versions 테이블 (Phase 3.3용 추가)
스키마:
- version_id: int (PK, auto_increment 시뮬레이션)
- page_id: int (FK)
- user_id: int | None
- content: str
- version_number: int
- version_type: str ('original' | 'auto_formatted' | 'user_edited')
- is_current: bool
- created_at: datetime
"""
_next_version_id = 1 # 버전 ID 자동 증가 카운터

mock_combined_results: Dict[int, Dict] = {}
"""
Mock combined_results 테이블 (Phase 3.3 캐싱용)
스키마:
- project_id: int (PK)
- combined_text: str
- stats: Dict (total_pages, total_words, total_characters)
- generated_at: datetime
"""

# ============================================================================
# Mock 분석 함수들 (레이아웃/OCR/AI) - 파일 내부에 정의
# ============================================================================

def _mock_layout_detection(page: Dict) -> List[MockElement]:
    """Mock 레이아웃 분석 (실제로는 YOLO 모델 호출)"""
    # 페이지 ID별로 다른 Mock 데이터를 생성하여 테스트 다양성 확보
    base_id = 1000 + page.get('page_id', 0) * 10
    mock_elements = [
        MockElement(
            element_id=base_id + 1, class_name="question number", confidence=0.95,
            bbox_x=100, bbox_y=100 + page.get('page_number', 1)*20, bbox_width=50, bbox_height=30
        ),
        MockElement(
            element_id=base_id + 2, class_name="question text", confidence=0.92,
            bbox_x=150, bbox_y=150 + page.get('page_number', 1)*20, bbox_width=400, bbox_height=60
        ),
        MockElement(
            element_id=base_id + 3, class_name="figure", confidence=0.88,
            bbox_x=100, bbox_y=250 + page.get('page_number', 1)*20, bbox_width=300, bbox_height=200
        ),
    ]
    return mock_elements

def _mock_ocr_processing(layout_elements: List[MockElement]) -> List[MockTextContent]:
    """Mock OCR 처리 (실제로는 Tesseract/PaddleOCR 호출)"""
    text_contents = []
    text_id_counter = 2000 # 고유 ID 시작 번호
    for element in layout_elements:
        mock_text = f"Mock OCR text for element {element.element_id} ({element.class_name})"
        text_content = MockTextContent(
            text_id=text_id_counter,
            element_id=element.element_id,
            ocr_text=mock_text,
            ocr_confidence=0.95 # Mock 신뢰도
        )
        text_contents.append(text_content)
        text_id_counter += 1
    return text_contents

def _mock_ai_description_generation(
    layout_elements: List[MockElement],
    api_key: str # api_key는 사용하지 않지만 인터페이스 일관성 유지
) -> Dict[int, str]:
    """Mock AI 설명 생성 (실제로는 GPT-4o-turbo 호출)"""
    AI_DESC_CLASSES = ['figure', 'table', 'flowchart']
    ai_descriptions: Dict[int, str] = {}
    for element in layout_elements:
        if element.class_name in AI_DESC_CLASSES:
            mock_desc = f"Mock AI description for {element.class_name} (ID: {element.element_id})"
            ai_descriptions[element.element_id] = mock_desc
    return ai_descriptions

# _save_text_version 함수 업데이트 (Phase 3.3 용)
def _save_text_version(
    page_id: int,
    content: str,
    version_type: str = 'auto_formatted',
    user_id: Optional[int] = None # 사용자 편집 시 필요
) -> None:
    """Mock text_versions 테이블에 저장"""
    global _next_version_id, mock_text_versions
    now = datetime.now()

    # 해당 페이지의 이전 current 버전 찾아서 False로 설정
    for version in mock_text_versions:
        if version['page_id'] == page_id and version['is_current']:
            version['is_current'] = False

    # ✅ 새 버전 번호 계산 (삭제된 버전 고려하여 max + 1 방식 사용)
    existing_versions = [v for v in mock_text_versions if v['page_id'] == page_id]
    if existing_versions:
        max_version_number = max(v['version_number'] for v in existing_versions)
        next_version_number = max_version_number + 1
    else:
        next_version_number = 1

    new_version = {
        'version_id': _next_version_id,
        'page_id': page_id,
        'user_id': user_id,
        'content': content,
        'version_number': next_version_number,
        'version_type': version_type,
        'is_current': True, # 새 버전은 항상 current
        'created_at': now
    }
    mock_text_versions.append(new_version)
    _next_version_id += 1
    logger.trace(f"   (Mock) text_version 저장됨: ID={new_version['version_id']}, PageID={page_id}, Type={version_type}, Version={next_version_number}")

# ============================================================================
# 1. 배치 분석 메인 함수 (변경 없음)
# ============================================================================
def analyze_project_batch(
    project_id: int,
    document_type: str = "question_based",
    use_ai_descriptions: bool = True,
    api_key: Optional[str] = None
) -> Dict[str, Any]:
    # ... (이전 답변과 동일한 코드) ...
    """
    v2.1 스키마 파이프라인을 사용하여 프로젝트 내 모든 보류 중인 페이지에 대한 배치 분석을 수행합니다.
    """
    logger.info(f"🚀 배치 분석 시작 (v2.1): project_id={project_id}, doc_type={document_type}")
    start_time = time.time()

    result = {
        'project_id': project_id, 'total_pages': 0, 'processed_pages': 0,
        'successful_pages': 0, 'failed_pages': 0, 'total_time': 0.0,
        'status': 'error', 'page_results': [] # 기본 상태 'error'로 설정
    }

    try:
        project = get_project_mock(project_id) # 수정: Mock DB 조회 함수 사용
        if not project:
            raise ValueError(f"프로젝트 ID {project_id}를 찾을 수 없습니다.")

        pending_pages = [p for p in get_pages_for_project_mock(project_id) if p['analysis_status'] == 'pending'] # 수정: Mock DB 조회 함수 사용
        result['total_pages'] = len(pending_pages)
        logger.info(f"   분석 대상 페이지: {len(pending_pages)}개")

        if not pending_pages:
            logger.warning("   분석할 페이지가 없습니다.")
            result['status'] = 'completed'
            _update_project_status(project_id, 'completed')
            return result

        _update_project_status(project_id, 'in_progress')

        for page in pending_pages:
            page_result = _analyze_single_page(page, document_type, use_ai_descriptions, api_key)
            result['page_results'].append(page_result)
            result['processed_pages'] += 1
            if page_result['status'] == 'completed': result['successful_pages'] += 1
            else: result['failed_pages'] += 1

        if result['failed_pages'] == 0: final_status = 'completed'
        elif result['successful_pages'] == 0: final_status = 'error'
        else: final_status = 'partial'

        result['status'] = final_status
        _update_project_status(project_id, final_status)

    except Exception as e:
        logger.error(f"❌ 배치 분석 중 심각한 오류 발생: {e}", exc_info=True)
        result['status'] = 'error'
        _update_project_status(project_id, 'error')

    result['total_time'] = time.time() - start_time
    logger.info(f"✅ 배치 분석 완료 (v2.1): {result['successful_pages']}/{result['processed_pages']}개 성공 ({result['total_pages']}개 대상), "
                f"{result['total_time']:.2f}초 소요")
    return result

# ============================================================================
# 2. 단일 페이지 분석 함수 (변경 없음)
# ============================================================================
def _analyze_single_page(
    page: Dict,
    document_type: str,
    use_ai_descriptions: bool,
    api_key: Optional[str]
) -> Dict:
    # ... (이전 답변과 동일한 코드) ...
    """
    v2.1 스키마를 대상으로 6단계 파이프라인을 사용하여 단일 페이지를 분석합니다.
    """
    page_id = page['page_id']
    page_number = page['page_number']
    page_start_time = time.time()

    page_result = {
        'page_id': page_id, 'page_number': page_number, 'status': 'error',
        'processing_time': 0.0, 'error': None, 'stats': {}
    }

    try:
        logger.debug(f"   📄 페이지 {page_number} 분석 시작 (ID: {page_id})")
        _update_page_status(page_id, 'processing')

        layout_elements: List[MockElement] = _mock_layout_detection(page)
        logger.debug(f"      [1/6] 레이아웃 분석 완료: {len(layout_elements)}개 요소 검출")
        if not layout_elements: raise ValueError("레이아웃 분석 결과 요소가 없습니다.")

        text_contents: List[MockTextContent] = _mock_ocr_processing(layout_elements)
        logger.debug(f"      [2/6] OCR 처리 완료: {len(text_contents)}개 텍스트 추출")

        ai_descriptions: Dict[int, str] = {}
        if use_ai_descriptions and api_key and api_key != "sk-...":
            ai_descriptions = _mock_ai_description_generation(layout_elements, api_key)
            logger.debug(f"      [3/6] AI 설명 생성 완료: {len(ai_descriptions)}개")
        else:
            logger.debug(f"      [3/6] AI 설명 생성 건너<0xEB><0x9B><0x84>뜀 (use_ai={use_ai_descriptions}, has_key={bool(api_key and api_key != 'sk-...')})")

        sorted_elements: List[MockElement] = sort_layout_elements_adaptive(
            elements=layout_elements,
            document_type=document_type,
            page_width=page.get('image_width'),
            page_height=page.get('image_height'),
            force_strategy=None  # Adaptive Strategy: 자동 전략 선택
        )
        logger.debug(f"      [4/6] 정렬 완료 (Adaptive): {len(sorted_elements)}개 요소 (type={document_type})")

        save_stats = save_sorted_elements_to_mock_db(
            page_id=page_id, sorted_elements=sorted_elements, clear_existing=True )
        logger.debug(f"      [5/6] DB 저장 완료 (v2.1): {save_stats}")

        doc_type_id = 1 if document_type == "question_based" else 2
        formatter = TextFormatter(doc_type_id=doc_type_id)
        ocr_dict = {tc.element_id: tc.ocr_text for tc in text_contents}
        for elem_id, desc in ai_descriptions.items(): ocr_dict[elem_id] = desc
        formatted_text = formatter.format_page(sorted_elements, ocr_dict)
        logger.debug(f"      [6/6] 포맷팅 완료: {len(formatted_text)}자")

        _save_text_version(page_id, formatted_text, version_type='auto_formatted') # 수정: 업데이트된 함수 호출

        _update_page_status(page_id, 'completed')
        page_result['status'] = 'completed'
        page_result['stats'] = {
            'elements_detected': len(layout_elements),
            'elements_sorted': len(sorted_elements),
            'groups_created': save_stats.get('groups_created', 0),
            'anchor_groups': save_stats.get('anchor_groups', 0),
            'orphan_groups': save_stats.get('orphan_groups', 0),
            'elements_saved': save_stats.get('elements_saved', 0),
            'formatted_chars': len(formatted_text) }
        logger.debug(f"   ✅ 페이지 {page_number} 분석 완료 (v2.1)")

    except Exception as e:
        logger.error(f"   ❌ 페이지 {page_number} 분석 실패: {e}", exc_info=True)
        page_result['status'] = 'error'; page_result['error'] = str(e)
        _update_page_status(page_id, 'error')
    finally:
        page_result['processing_time'] = time.time() - page_start_time
        _update_page_processing_time(page_id, page_result['processing_time'])
    return page_result

# ============================================================================
# 3. Mock DB 업데이트 함수 (변경 없음)
# ============================================================================
def _update_project_status(project_id: int, status: str) -> None:
    # ... (이전 답변과 동일한 코드) ...
    """mock_projects에서 프로젝트 상태 업데이트"""
    if project_id in mock_projects:
        mock_projects[project_id]['status'] = status
        mock_projects[project_id]['updated_at'] = datetime.now()
        logger.trace(f"   프로젝트 {project_id} 상태 → {status}")

def _update_page_status(page_id: int, status: str) -> None:
    # ... (이전 답변과 동일한 코드) ...
    """mock_pages에서 페이지 상태 업데이트"""
    if page_id in mock_pages:
        mock_pages[page_id]['analysis_status'] = status
        if status in ['completed', 'error']:
            mock_pages[page_id]['analyzed_at'] = datetime.now()
        logger.trace(f"   페이지 {page_id} 상태 → {status}")

def _update_page_processing_time(page_id: int, processing_time: float) -> None:
    # ... (이전 답변과 동일한 코드) ...
    """mock_pages에서 페이지 처리 시간 업데이트"""
    if page_id in mock_pages:
        mock_pages[page_id]['processing_time'] = processing_time
        logger.trace(f"   페이지 {page_id} 처리 시간: {processing_time:.2f}초")

# ============================================================================
# 4. Mock DB 초기화 함수 (변경 없음)
# ============================================================================
def initialize_mock_db_for_test(num_pages: int = 3) -> int:
    # ... (이전 답변과 동일한 코드, db_saver 카운터 리셋 로직 포함) ...
    """테스트용으로 mock_projects와 mock_pages 초기화"""
    global mock_projects, mock_pages, mock_text_versions, mock_combined_results
    mock_projects.clear()
    mock_pages.clear()
    mock_text_versions.clear() # 텍스트 버전도 초기화
    mock_combined_results.clear() # ✅ 캐시도 초기화
    # db_saver 카운터 리셋
    global _next_question_group_id, _next_qe_id
    try:
        from . import db_saver
        db_saver._next_question_group_id = 1
        db_saver._next_qe_id = 1
        logger.trace("DB Saver 카운터 리셋 완료.")
    except (ImportError, AttributeError):
        logger.warning("DB Saver 카운터를 리셋할 수 없습니다.")
    # 버전 카운터 리셋
    global _next_project_id, _next_page_id, _next_version_id
    _next_project_id = 1
    _next_page_id = 1
    _next_version_id = 1

    project_id = create_project_mock(1, 1, f'테스트 프로젝트 v2.1 - {num_pages} 페이지') # 수정: 생성 함수 사용

    for i in range(1, num_pages + 1):
        add_page_mock(project_id, i, f'/mock/path/{project_id}/page_{i}.jpg') # 수정: 생성 함수 사용

    logger.info(f"Mock DB (v2.1) 초기화 완료: project_id={project_id}, {num_pages}개 페이지")
    return project_id


# ============================================================================
# 5. (신규) Mock DB CRUD 함수 (Phase 3.1, 3.3용)
# ============================================================================

# --- Project CRUD ---
def create_project_mock(user_id: int, doc_type_id: int, project_name: str) -> int:
    """Mock DB에 새 프로젝트 생성"""
    global _next_project_id
    project_id = _next_project_id
    now = datetime.now()
    mock_projects[project_id] = {
        'project_id': project_id,
        'user_id': user_id,
        'doc_type_id': doc_type_id,
        'project_name': project_name,
        'total_pages': 0,
        'status': 'created',
        'created_at': now,
        'updated_at': now
    }
    _next_project_id += 1
    logger.info(f"Mock DB: 프로젝트 생성됨 ID={project_id}, Name='{project_name}'")
    return project_id

def get_project_mock(project_id: int) -> Optional[Dict]:
    """Mock DB에서 프로젝트 조회"""
    return mock_projects.get(project_id)

def get_all_projects_mock() -> List[Dict]:
    """Mock DB에서 모든 프로젝트 조회"""
    return list(mock_projects.values())

# --- Page CRUD ---
def add_page_mock(project_id: int, page_number: Optional[int], image_path: str,
                  image_width: int = 2480, image_height: int = 3508) -> int:
    """Mock DB에 새 페이지 추가"""
    global _next_page_id
    if project_id not in mock_projects:
        raise ValueError(f"프로젝트 ID {project_id} 없음")

    # 페이지 번호 자동 결정
    if page_number is None:
        existing_pages = [p['page_number'] for p in mock_pages.values() if p['project_id'] == project_id]
        page_number = max(existing_pages) + 1 if existing_pages else 1

    page_id = _next_page_id
    now = datetime.now()
    mock_pages[page_id] = {
        'page_id': page_id,
        'project_id': project_id,
        'page_number': page_number,
        'image_path': image_path,
        'image_width': image_width,
        'image_height': image_height,
        'analysis_status': 'pending',
        'processing_time': None,
        'created_at': now,
        'analyzed_at': None
    }
    _next_page_id += 1

    # 트리거 시뮬레이션: total_pages 업데이트
    mock_projects[project_id]['total_pages'] = len([p for p in mock_pages.values() if p['project_id'] == project_id])
    mock_projects[project_id]['updated_at'] = now

    logger.info(f"Mock DB: 페이지 추가됨 ID={page_id}, ProjectID={project_id}, Number={page_number}")
    return page_id

def get_page_mock(page_id: int) -> Optional[Dict]:
    """Mock DB에서 페이지 조회"""
    return mock_pages.get(page_id)

def get_pages_for_project_mock(project_id: int) -> List[Dict]:
    """Mock DB에서 특정 프로젝트의 모든 페이지 조회 (페이지 번호 순 정렬)"""
    pages = [p for p in mock_pages.values() if p['project_id'] == project_id]
    pages.sort(key=lambda x: x['page_number'])
    return pages

# --- Text Version CRUD ---
def get_latest_version_mock(page_id: int) -> Optional[Dict]:
    """Mock DB에서 특정 페이지의 최신 (is_current=True) 텍스트 버전 조회"""
    versions = [v for v in mock_text_versions if v['page_id'] == page_id and v['is_current']]
    if versions:
        # created_at으로 정렬하여 가장 최신 것 반환 (is_current가 여러 개일 경우 대비)
        versions.sort(key=lambda x: x['created_at'], reverse=True)
        return versions[0]
    # is_current가 없으면 가장 최신 버전 번호 반환
    all_versions = [v for v in mock_text_versions if v['page_id'] == page_id]
    if all_versions:
         all_versions.sort(key=lambda x: x['version_number'], reverse=True)
         return all_versions[0]
    return None

# --- Combined Results CRUD (Phase 3.3 캐싱) ---
def save_combined_result_mock(project_id: int, combined_text: str, stats: Dict) -> None:
    """
    ✅ Mock combined_results 캐시에 통합 텍스트 저장

    Args:
        project_id: 프로젝트 ID
        combined_text: 통합된 텍스트
        stats: 통계 정보 (total_pages, total_words, total_characters)
    """
    global mock_combined_results
    mock_combined_results[project_id] = {
        'project_id': project_id,
        'combined_text': combined_text,
        'stats': stats,
        'generated_at': datetime.now()
    }
    logger.trace(f"   (Mock) combined_results 캐시 저장됨: ProjectID={project_id}, Stats={stats}")

def get_combined_result_mock(project_id: int) -> Optional[Dict]:
    """
    ✅ Mock combined_results 캐시에서 통합 텍스트 조회

    Args:
        project_id: 프로젝트 ID

    Returns:
        캐시된 통합 결과 (combined_text, stats, generated_at) 또는 None
    """
    return mock_combined_results.get(project_id)

def is_cache_valid_mock(project_id: int) -> bool:
    """
    ✅ Mock combined_results 캐시 유효성 검사

    캐시가 유효한 조건:
    1. 캐시가 존재해야 함
    2. 캐시 생성 시간이 모든 현재 텍스트 버전의 생성 시간보다 최신이어야 함

    Args:
        project_id: 프로젝트 ID

    Returns:
        캐시가 유효하면 True, 아니면 False
    """
    if project_id not in mock_combined_results:
        return False

    # 프로젝트의 모든 페이지 조회
    pages = get_pages_for_project_mock(project_id)
    if not pages:
        return False

    page_ids = [p['page_id'] for p in pages]

    # 캐시 생성 시간
    cache_time = mock_combined_results[project_id]['generated_at']

    # 모든 현재 텍스트 버전의 생성 시간 조회
    current_versions = [
        v for v in mock_text_versions
        if v['page_id'] in page_ids and v['is_current']
    ]

    if not current_versions:
        # 텍스트 버전이 없으면 캐시 무효
        return False

    # 가장 최신 버전의 생성 시간
    latest_version_time = max(v['created_at'] for v in current_versions)

    # 캐시가 최신 버전보다 나중에 생성되었으면 유효
    is_valid = cache_time >= latest_version_time
    logger.trace(f"   (Mock) 캐시 유효성 검사: ProjectID={project_id}, Valid={is_valid}")
    return is_valid

def invalidate_cache_mock(project_id: int) -> None:
    """
    ✅ Mock combined_results 캐시 무효화 (삭제)

    Args:
        project_id: 프로젝트 ID
    """
    if project_id in mock_combined_results:
        del mock_combined_results[project_id]
        logger.trace(f"   (Mock) 캐시 무효화됨: ProjectID={project_id}")

# ============================================================================
# 6. 테스트 코드 (변경 없음)
# ============================================================================
if __name__ == "__main__":
    # ... (이전 답변과 동일한 테스트 코드) ...
    logger.remove(); logger.add(sys.stderr, level="DEBUG")
    print("\n" + "=" * 70); print("배치 분석 모듈 테스트 (v2.1 스키마 파이프라인)"); print("=" * 70 + "\n")
    print("Step 1: Mock DB 초기화"); print("-" * 70)
    test_project_id = initialize_mock_db_for_test(num_pages=3)
    print(f"Project ID: {test_project_id}"); print(f"페이지 수: {len([p for p in mock_pages.values() if p['project_id'] == test_project_id])}개\n")
    print("Step 2: 배치 분석 실행"); print("-" * 70)
    analysis_result = analyze_project_batch(project_id=test_project_id, document_type="question_based", use_ai_descriptions=False, api_key=None)
    print("-" * 70 + "\n")
    print("Step 3: 분석 결과 요약"); print("-" * 70); print(f"Project ID: {analysis_result['project_id']}"); print(f"분석 대상 페이지: {analysis_result['total_pages']}"); print(f"처리된 페이지: {analysis_result['processed_pages']}"); print(f"성공: {analysis_result['successful_pages']}"); print(f"실패: {analysis_result['failed_pages']}"); print(f"소요 시간: {analysis_result['total_time']:.2f}초"); print(f"최종 상태: {analysis_result['status']}"); print("-" * 70 + "\n")
    print("Step 4: 페이지별 상세 결과"); print("-" * 70)
    for res in analysis_result['page_results']:
        print(f"📄 Page {res['page_number']} (ID: {res['page_id']}): {res['status']} ({res['processing_time']:.3f}s)")
        if res['stats']: s = res['stats']; print(f"   Stats: Detect={s.get('elements_detected', 'N/A')}, Sort={s.get('elements_sorted', 'N/A')}, Groups={s.get('groups_created', 'N/A')} (A:{s.get('anchor_groups', 'N/A')}/O:{s.get('orphan_groups', 'N/A')}), Saved={s.get('elements_saved', 'N/A')}, Format={s.get('formatted_chars', 'N/A')}chars")
        if res['error']: print(f"   Error: {res['error']}")
    print("-" * 70 + "\n")
    print("Step 5: Mock DB v2.1 검증"); print("-" * 70)
    try: from .db_saver import print_mock_db_summary; print_mock_db_summary()
    except ImportError: logger.error("db_saver.print_mock_db_summary 함수를 찾을 수 없습니다.")
    first_page_id = 1; groups_page1 = get_question_groups_by_page(first_page_id); print(f"\n페이지 {first_page_id} 그룹 조회:")
    if groups_page1:
        for group in groups_page1: print(f"  Group {group['question_group_id']}: Type={group['group_type']}, Anchor={group['anchor_element_id']}"); elements_in_group = get_question_elements_by_group(group['question_group_id']); print(f"    Elements ({len(elements_in_group)}): {[qe['element_id'] for qe in elements_in_group]}")
    else: print(f"  페이지 {first_page_id}에 그룹 없음")
    print("=" * 70); print("테스트 완료"); print("=" * 70)

# ============================================================================
# 7. (신규) Phase 3.1/3.3: 페이지 텍스트 버전 관리 서비스 함수
# ============================================================================
def get_current_page_text(page_id: int) -> Optional[Dict]:
    """
    (Phase 3.1/3.3 신규)
    페이지의 현재 활성화된 텍스트 버전을 가져옵니다. (프론트엔드 에디터 로딩용)
    """
    logger.debug(f"서비스: 페이지 {page_id}의 현재 텍스트 버전 조회")
    latest_version = get_latest_version_mock(page_id)
    
    if not latest_version:
        # 분석은 완료되었으나 텍스트 버전이 없는 경우 (예: 포맷팅 실패)
        page = get_page_mock(page_id)
        if page and page['analysis_status'] == 'completed':
            logger.warning(f"페이지 {page_id}는 분석 완료되었으나 텍스트 버전이 없습니다.")
            return None
        elif page:
            logger.warning(f"페이지 {page_id}가 아직 분석되지 않았습니다.")
            return None
        else:
            raise ValueError(f"페이지 ID {page_id}를 찾을 수 없습니다.")
            
    return latest_version

def save_user_edited_version(page_id: int, content: str, user_id: Optional[int]) -> Dict:
    """
    (Phase 3.1/3.3 신규)
    사용자가 수정한 텍스트를 'user_edited' 타입의 새 버전으로 저장합니다.
    ✅ 저장 후 해당 프로젝트의 캐시를 무효화합니다.
    """
    logger.info(f"서비스: 페이지 {page_id}에 사용자 수정 버전 저장 (User ID: {user_id})")

    page = get_page_mock(page_id)
    if not page:
        raise ValueError(f"페이지 ID {page_id}를 찾을 수 없습니다.")

    project_id = page['project_id']

    # _save_text_version는 내부적으로 이전 버전을 is_current=False로 처리함
    _save_text_version(
        page_id=page_id,
        content=content,
        version_type='user_edited', # 타입을 'user_edited'로 지정
        user_id=user_id
    )

    # ✅ 캐시 무효화: 사용자가 텍스트를 수정하면 통합 텍스트 캐시를 삭제
    invalidate_cache_mock(project_id)
    logger.info(f"서비스: 프로젝트 {project_id}의 combined_results 캐시 무효화됨")

    new_version = get_latest_version_mock(page_id)
    if not new_version or new_version['content'] != content:
         logger.error(f"페이지 {page_id}에 사용자 버전 저장 실패!")
         raise Exception("텍스트 버전 저장 실패")

    return new_version