import cv2
import pytest
from pathlib import Path
from typing import Dict, List

from Backend.app import models
from Backend.app.services.sorter import save_sorting_results_to_db
from Backend.app.services.sorter_strategies import sort_layout_elements_adaptive
from Backend.app.services.batch_analysis import _sync_layout_runtime_fields
from Backend.app.services.formatter import TextFormatter
from Backend.app.services.mock_models import MockElement


# ============================================================================
# 테스트 자원 경로
# ============================================================================
PROJECT_DIR = Path(__file__).resolve().parents[2]
IMAGE_PATH = PROJECT_DIR / "test_images" / "쎈 수학1-1_페이지_014.jpg"


def _seed_common_entities(session) -> models.Page:
    """유저, 문서 유형, 프로젝트, 페이지를 순차적으로 생성한다."""
    user = models.User(
        email="mysql_tester@example.com",
        name="mysql-tester",
        role="user",
        password_hash="not_used_in_tests",
    )

    doc_type = models.DocumentType(
        type_name="worksheet",
        model_name="SmartEyeSsen",
        sorting_method="question_based",
        description="통합 테스트 전용 문서 유형",
    )

    session.add_all([user, doc_type])
    session.commit()

    project = models.Project(
        user_id=user.user_id,
        doc_type_id=doc_type.doc_type_id,
        project_name="MySQL Integration Scenario",
    )
    session.add(project)
    session.commit()

    page = models.Page(
        project_id=project.project_id,
        page_number=1,
        image_path=str(IMAGE_PATH),
        analysis_status="pending",
        image_width=1240,
        image_height=1754,
    )
    session.add(page)
    session.commit()

    return page


def _create_layout_elements(session, page: models.Page) -> List[models.LayoutElement]:
    """단순화된 레이아웃 요소 4개를 생성한다."""
    layout_specs = [
        ("question_number", 0.96, 120, 110, 70, 50),
        ("question_text", 0.94, 180, 200, 520, 160),
        ("question_number", 0.95, 150, 450, 70, 50),
        ("question_text", 0.92, 210, 540, 520, 170),
    ]

    records: List[models.LayoutElement] = []
    for class_name, conf, x, y, w, h in layout_specs:
        record = models.LayoutElement(
            page_id=page.page_id,
            class_name=class_name,
            confidence=conf,
            bbox_x=x,
            bbox_y=y,
            bbox_width=w,
            bbox_height=h,
        )
        records.append(record)

    session.add_all(records)
    session.commit()

    return (
        session.query(models.LayoutElement)
        .filter(models.LayoutElement.page_id == page.page_id)
        .order_by(models.LayoutElement.element_id)
        .all()
    )


def _to_mock(elements: List[models.LayoutElement]) -> List[MockElement]:
    """ORM LayoutElement → MockElement 변환."""
    mocks: List[MockElement] = []
    for element in elements:
        mock = MockElement(
            element_id=element.element_id,
            page_id=element.page_id,
            class_name=element.class_name,
            confidence=float(element.confidence),
            bbox_x=int(element.bbox_x),
            bbox_y=int(element.bbox_y),
            bbox_width=int(element.bbox_width),
            bbox_height=int(element.bbox_height),
        )
        mocks.append(mock)
    return mocks


@pytest.mark.mysql_integration
@pytest.mark.parametrize("force_strategy", ["GLOBAL_FIRST", "LOCAL_FIRST", "HYBRID"])
def test_sorter_results_are_persisted(clean_db, force_strategy):
    """
    Adaptive 정렬 전략 선택과 관계없이 정렬 결과가 MySQL에 저장되는지 검증한다.
    """
    session = clean_db
    page = _seed_common_entities(session)
    orm_layouts = _create_layout_elements(session, page)

    mock_elements = _to_mock(orm_layouts)
    assert mock_elements, "정렬 대상 MockElement 생성 실패"

    sorted_mock = sort_layout_elements_adaptive(
        elements=mock_elements,
        document_type="question_based",
        page_width=max(m.bbox_x + m.bbox_width for m in mock_elements),
        page_height=max(m.bbox_y + m.bbox_height for m in mock_elements),
        force_strategy=force_strategy,
    )
    assert all(getattr(m, "group_id", None) is not None for m in sorted_mock)

    synced_layouts = _sync_layout_runtime_fields(orm_layouts, sorted_mock)
    group_count, element_count = save_sorting_results_to_db(
        session, page.page_id, synced_layouts
    )
    session.commit()

    assert group_count >= 2
    assert element_count == len(sorted_mock)

    stored_groups = (
        session.query(models.QuestionGroup)
        .filter(models.QuestionGroup.page_id == page.page_id)
        .order_by(models.QuestionGroup.question_group_id)
        .all()
    )
    stored_elements = (
        session.query(models.QuestionElement)
        .order_by(models.QuestionElement.order_in_question)
        .all()
    )
    assert len(stored_groups) == group_count
    assert len(stored_elements) == element_count
    assert {g.group_type for g in stored_groups} == {"anchor"}

    # 그룹 → 요소 매핑 유효성
    mapping: Dict[int, List[int]] = {}
    for qe in stored_elements:
        mapping.setdefault(qe.question_group_id, []).append(qe.element_id)
    assert all(len(ids) >= 1 for ids in mapping.values())

    # TextFormatter와 TextVersion 동작 검증
    formatter = TextFormatter(
        doc_type_id=page.project.document_type.doc_type_id,
        db=session,
        use_db_rules=True,
    )
    ocr_map = {elem.element_id: f"OCR-{elem.element_id}" for elem in synced_layouts}
    formatted_text = formatter.format_page(synced_layouts, ocr_map)
    assert formatted_text.strip()

    from Backend.app.services.batch_analysis import _create_text_version

    _create_text_version(session, page, formatted_text)
    session.commit()

    text_versions = (
        session.query(models.TextVersion)
        .filter(models.TextVersion.page_id == page.page_id)
        .all()
    )
    assert text_versions
    assert text_versions[0].content.strip() == formatted_text.strip()


@pytest.mark.mysql_integration
def test_visual_artifacts_saved(clean_db, tmp_path):
    """
    실제 이미지 분석 없이 정렬 결과를 이용해 시각화를 생성하여
    test_utils 헬퍼가 올바르게 동작하는지 확인한다.
    """
    session = clean_db
    page = _seed_common_entities(session)
    orm_layouts = _create_layout_elements(session, page)
    mocks = _to_mock(orm_layouts)

    sorted_mock = sort_layout_elements_adaptive(
        elements=mocks,
        document_type="question_based",
        page_width=max(m.bbox_x + m.bbox_width for m in mocks),
        page_height=max(m.bbox_y + m.bbox_height for m in mocks),
        force_strategy="GLOBAL_FIRST",
    )
    _sync_layout_runtime_fields(orm_layouts, sorted_mock)

    image = cv2.imread(str(IMAGE_PATH))
    assert image is not None, f"이미지를 읽을 수 없습니다: {IMAGE_PATH}"

    from Project.tests.backend.test_utils import save_visual_artifacts

    outputs = save_visual_artifacts(
        output_dir=str(tmp_path),
        image=image,
        sorted_elements=sorted_mock,
        ocr_map={m.element_id: f"OCR-{m.element_id}" for m in sorted_mock},
        ai_map={},
        image_filename=IMAGE_PATH.name,
    )

    for path in outputs.values():
        assert Path(path).exists(), f"출력 파일을 찾을 수 없습니다: {path}"
