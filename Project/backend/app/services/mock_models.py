"""
데이터베이스 독립성을 위한 Mock 모델 (v2.1 스키마 호환)
======================================================

이 모듈은 데이터베이스 의존성을 제거하기 위한 Mock 모델들을 정의하며,
v2.1 E-R 다이어그램에 맞춰져 있습니다. 테스트 목적으로 layout_elements,
text_contents, question_groups, question_elements 테이블을 대체합니다.

주요 특징:
- MockElement, MockTextContent, MockQuestionGroup, MockQuestionElement 구현
- 자동 계산 속성 (y_position, x_position, area)
- Pydantic (설치된 경우) 또는 순수 Python 클래스 지원

v2.1 스키마 변경 사항 반영:
- MockElement: DB 표현에서는 정렬 필드(order_in_question 등)가 없지만, sorter.py 호환성을 위해 메모리 내 속성으로 유지
- MockQuestionGroup: question_groups 테이블 표현
- MockQuestionElement: question_elements 테이블 (N:M 매핑) 표현

사용법:
- analysis_service, formatter에서 MockElement, MockTextContent 임포트
- db_saver (v2.1)에서 MockQuestionGroup, MockQuestionElement 임포트

참조:
- E-R 다이어그램 v2.1: Project/docs/E-R_다이어그램_v2.1_스키마.md
"""

from datetime import datetime
from typing import Optional, Dict, Any, List # Pydantic MockElement bbox 호환성을 위해 List 추가
import json as json_module
from dataclasses import dataclass

# 의존성 체크
try:
    from pydantic import BaseModel, Field, computed_field
    USE_PYDANTIC = True
except ImportError:
    USE_PYDANTIC = False
    BaseModel = object # type: ignore


# ============================================================================
# Pydantic 기반 구현 (Pydantic이 설치된 경우)
# ============================================================================

if USE_PYDANTIC:

    class MockElement(BaseModel): # type: ignore
        """
        layout_elements 테이블을 대체하는 Mock 모델 (Pydantic 버전).
        레이아웃 분석 결과의 각 요소를 나타냅니다.

        v2.1 참고: 데이터베이스의 layout_elements 테이블에는 정렬 정보가 저장되지 않습니다.
        하지만 sorter.py는 이 속성들을 메모리 내에서 동적으로 추가합니다.
        따라서 sorter.py 출력과의 호환성을 위해 이 선택적 필드들을 유지합니다.
        """

        # 기본 필드
        element_id: int = Field(..., description="요소 고유 식별자", ge=1)
        page_id: Optional[int] = Field(None, description="페이지 식별자", ge=1)
        class_name: str = Field(..., description="요소 클래스명", min_length=1, max_length=100)
        confidence: float = Field(..., description="탐지 신뢰도", ge=0.0, le=1.0)

        # 바운딩 박스 좌표
        bbox_x: int = Field(..., description="바운딩 박스 좌상단 X", ge=0)
        bbox_y: int = Field(..., description="바운딩 박스 좌상단 Y", ge=0)
        bbox_width: int = Field(..., description="바운딩 박스 너비", ge=1)
        bbox_height: int = Field(..., description="바운딩 박스 높이", ge=1)

        # 메타데이터
        created_at: Optional[datetime] = Field(default_factory=datetime.now, description="생성 타임스탬프")

        # ===>>> sorter.py가 동적으로 추가하는 필드 (v2.1 DB 스키마에는 없음) <<<===
        order_in_question: Optional[int] = Field(None, description="전체 정렬 순서 (sorter가 추가)")
        group_id: Optional[int] = Field(None, description="할당된 그룹 ID (sorter가 추가)")
        order_in_group: Optional[int] = Field(None, description="그룹 내 정렬 순서 (sorter가 추가)")
        # ===>>> sorter.py 필드 끝 <<<===

        # 이전 bbox 형식 호환성 필드 (필요시)
        bbox: Optional[List[int]] = Field(None, description="선택적 bbox 리스트 [x, y, w, h]")

        # 계산된 속성
        @computed_field # type: ignore[misc]
        @property
        def area(self) -> int:
            """요소 면적 계산 (너비 * 높이)"""
            return self.bbox_width * self.bbox_height

        @computed_field # type: ignore[misc]
        @property
        def y_position(self) -> int:
            """Y 좌표 정렬용 속성 (= bbox_y)"""
            return self.bbox_y

        @computed_field # type: ignore[misc]
        @property
        def x_position(self) -> int:
            """X 좌표 정렬용 속성 (= bbox_x)"""
            return self.bbox_x

        class Config:
            """Pydantic 모델 설정"""
            json_schema_extra = {
                "example": {
                    "element_id": 1, "page_id": 1, "class_name": "question_number",
                    "confidence": 0.95, "bbox_x": 100, "bbox_y": 200,
                    "bbox_width": 50, "bbox_height": 30,
                }
            }

    class MockTextContent(BaseModel): # type: ignore
        """
        text_contents 테이블을 대체하는 Mock 모델 (Pydantic 버전).
        레이아웃 요소의 OCR 결과 텍스트를 저장합니다 (1:1 관계).
        """
        # 기본 필드
        text_id: int = Field(..., description="텍스트 콘텐츠 고유 식별자", ge=1)
        element_id: int = Field(..., description="연결된 레이아웃 요소 식별자 (UNIQUE)", ge=1)

        # OCR 콘텐츠
        ocr_text: str = Field(..., description="추출된 OCR 텍스트")

        # OCR 메타데이터
        ocr_engine: str = Field(default="PaddleOCR", description="사용된 OCR 엔진", max_length=50)
        ocr_confidence: Optional[float] = Field(None, description="OCR 신뢰도", ge=0.0, le=1.0)
        language: str = Field(default="ko", description="텍스트 언어 코드", max_length=10)

        # 메타데이터
        created_at: Optional[datetime] = Field(default_factory=datetime.now, description="생성 타임스탬프")

        class Config:
            """Pydantic 모델 설정"""
            json_schema_extra = {
                "example": {
                    "text_id": 1, "element_id": 1, "ocr_text": "1. 다음 중 옳은 것을 고르시오.",
                    "ocr_engine": "PaddleOCR", "ocr_confidence": 0.98, "language": "ko",
                }
            }

    # ===>>> v2.1 스키마 Mock 모델 (마이그레이션 계획에 따라 추가됨) <<<===
    @dataclass # 계획에서 정의된 대로 dataclass 사용
    class MockQuestionGroup:
        """
        v2.1 question_groups 테이블 Mock

        E-R 다이어그램 line 199-234 참조
        """
        question_group_id: int
        page_id: int
        anchor_element_id: Optional[int]  # None = 고아 그룹
        group_type: str  # 'anchor' | 'orphan'
        start_y: int
        end_y: int
        element_count: int
        created_at: Optional[str] = None # Mock에서는 간단히 str 사용
        updated_at: Optional[str] = None # Mock에서는 간단히 str 사용

    @dataclass # 계획에서 정의된 대로 dataclass 사용
    class MockQuestionElement:
        """
        v2.1 question_elements 테이블 Mock (N:M 매핑 테이블)

        E-R 다이어그램 line 236-261 참조
        """
        qe_id: int  # PK, auto_increment 시뮬레이션
        question_group_id: int  # FK -> question_groups
        element_id: int  # FK -> layout_elements
        order_in_question: int  # 전체 정렬 순서 (sorter.py 결과)
        order_in_group: int  # 그룹 내 정렬 순서 (sorter.py 결과)
        created_at: Optional[str] = None # Mock에서는 간단히 str 사용
    # ===>>> v2.1 스키마 Mock 모델 끝 <<<===


# ============================================================================
# 순수 Python 클래스 구현 (Pydantic이 설치되지 않은 경우)
# ============================================================================

else:

    @dataclass
    class MockElement:
        """
        layout_elements 테이블을 대체하는 Mock 모델 (순수 Python 버전).

        v2.1 참고: Pydantic 버전 주석 참조. 정렬 필드는 sorter.py 호환성용.
        """
        element_id: int
        class_name: str
        confidence: float
        bbox_x: int
        bbox_y: int
        bbox_width: int
        bbox_height: int
        page_id: Optional[int] = None
        created_at: Optional[datetime] = None

        # sorter.py가 동적으로 추가하는 필드
        order_in_question: Optional[int] = None
        group_id: Optional[int] = None
        order_in_group: Optional[int] = None

        def __post_init__(self):
            """순수 Python 버전 유효성 검사"""
            if self.element_id < 1: raise ValueError("element_id는 1 이상이어야 합니다")
            if not self.class_name or len(self.class_name) > 100: raise ValueError("class_name 길이가 유효하지 않습니다")
            if not (0.0 <= self.confidence <= 1.0): raise ValueError("confidence는 0.0과 1.0 사이여야 합니다")
            if self.bbox_x < 0 or self.bbox_y < 0: raise ValueError("bbox 좌표는 0 이상이어야 합니다")
            if self.bbox_width < 1 or self.bbox_height < 1: raise ValueError("bbox 크기는 1 이상이어야 합니다")
            if self.page_id is not None and self.page_id < 1: raise ValueError("page_id는 1 이상이어야 합니다")
            self.created_at = self.created_at or datetime.now()

        @property
        def area(self) -> int: return self.bbox_width * self.bbox_height
        @property
        def y_position(self) -> int: return self.bbox_y
        @property
        def x_position(self) -> int: return self.bbox_x

        def to_dict(self) -> Dict[str, Any]:
            """인스턴스를 딕셔너리로 변환"""
            data = self.__dict__.copy()
            data["area"] = self.area
            data["y_position"] = self.y_position
            data["x_position"] = self.x_position
            if self.created_at: data["created_at"] = self.created_at.isoformat()
            return data

        def to_json(self, indent: Optional[int] = None) -> str:
            """인스턴스를 JSON 문자열로 변환"""
            return json_module.dumps(self.to_dict(), ensure_ascii=False, indent=indent)

        def __repr__(self) -> str:
            return (f"MockElement(id={self.element_id}, cls='{self.class_name}', "
                    f"bbox=({self.bbox_x}, {self.bbox_y}, {self.bbox_width}, {self.bbox_height}))")

    @dataclass
    class MockTextContent:
        """
        text_contents 테이블을 대체하는 Mock 모델 (순수 Python 버전).
        """
        text_id: int
        element_id: int
        ocr_text: str
        ocr_engine: str = "PaddleOCR"
        ocr_confidence: Optional[float] = None
        language: str = "ko"
        created_at: Optional[datetime] = None

        def __post_init__(self):
            """순수 Python 버전 유효성 검사"""
            if self.text_id < 1: raise ValueError("text_id는 1 이상이어야 합니다")
            if self.element_id < 1: raise ValueError("element_id는 1 이상이어야 합니다")
            if not self.ocr_text: raise ValueError("ocr_text는 비어 있을 수 없습니다")
            if self.ocr_confidence is not None and not (0.0 <= self.ocr_confidence <= 1.0): raise ValueError("ocr_confidence는 0.0과 1.0 사이여야 합니다")
            if len(self.ocr_engine) > 50: raise ValueError("ocr_engine이 너무 깁니다")
            if len(self.language) > 10: raise ValueError("language가 너무 깁니다")
            self.created_at = self.created_at or datetime.now()

        def to_dict(self) -> Dict[str, Any]:
            """인스턴스를 딕셔너리로 변환"""
            data = self.__dict__.copy()
            if self.created_at: data["created_at"] = self.created_at.isoformat()
            return data

        def to_json(self, indent: Optional[int] = None) -> str:
            """인스턴스를 JSON 문자열로 변환"""
            return json_module.dumps(self.to_dict(), ensure_ascii=False, indent=indent)

        def __repr__(self) -> str:
            return (f"MockTextContent(id={self.text_id}, elem_id={self.element_id}, "
                    f"text='{self.ocr_text[:30]}...')")

    # ===>>> v2.1 스키마 Mock 모델 (순수 Python) <<<===
    @dataclass
    class MockQuestionGroup:
        """v2.1 question_groups 테이블 Mock (순수 Python)"""
        question_group_id: int
        page_id: int
        anchor_element_id: Optional[int]
        group_type: str
        start_y: int
        end_y: int
        element_count: int
        created_at: Optional[str] = None
        updated_at: Optional[str] = None

    @dataclass
    class  MockQuestionElement:
        """v2.1 question_elements 테이블 Mock (순수 Python)"""
        qe_id: int
        question_group_id: int
        element_id: int
        order_in_question: int
        order_in_group: int
        created_at: Optional[str] = None
    # ===>>> v2.1 스키마 Mock 모델 끝 <<<===


# ============================================================================
# 유틸리티 함수 (공통)
# ============================================================================

def create_mock_element_from_detection(
    element_id: int,
    detection_result: Dict[str, Any],
    page_id: Optional[int] = None
) -> MockElement:
    """
    레이아웃 탐지 결과로부터 MockElement 생성.
    다양한 bbox 형식을 처리합니다.
    """
    bbox = detection_result['bbox']
    bbox_x, bbox_y, bbox_width, bbox_height = 0, 0, 0, 0 # 초기화

    if isinstance(bbox, list) and len(bbox) == 4:
        # 형식 [x, y, 너비, 높이] 또는 [x1, y1, x2, y2] 가정
        # 실제 사용된 YOLO 출력 형식에 따라 명확화 필요
        # 현재는 순수 Python init 기반으로 [x, y, 너비, 높이] 가정
        bbox_x, bbox_y, bbox_width, bbox_height = bbox
        # 만약 [x1, y1, x2, y2] 형식이면 아래 주석 해제:
        # bbox_x, bbox_y = bbox[0], bbox[1]
        # bbox_width, bbox_height = bbox[2] - bbox[0], bbox[3] - bbox[1]
    elif isinstance(bbox, dict):
        bbox_x = bbox.get('x', 0)
        bbox_y = bbox.get('y', 0)
        bbox_width = bbox.get('width', 0)
        bbox_height = bbox.get('height', 0)

    # 유효성 검사를 위해 너비/높이가 양수인지 확인
    bbox_width = max(1, bbox_width)
    bbox_height = max(1, bbox_height)

    return MockElement(
        element_id=element_id,
        page_id=page_id,
        class_name=str(detection_result['class_name']), # str 확인
        confidence=float(detection_result['confidence']), # float 확인
        bbox_x=int(bbox_x), # int 확인
        bbox_y=int(bbox_y), # int 확인
        bbox_width=int(bbox_width), # int 확인
        bbox_height=int(bbox_height) # int 확인
    )


def create_mock_text_content(
    text_id: int,
    element_id: int,
    ocr_result: str,
    ocr_confidence: Optional[float] = None,
    ocr_engine: str = "PaddleOCR"
) -> MockTextContent:
    """
    OCR 결과로부터 MockTextContent 생성.
    """
    return MockTextContent(
        text_id=text_id,
        element_id=element_id,
        ocr_text=ocr_result,
        ocr_engine=ocr_engine,
        ocr_confidence=ocr_confidence
    )


# ============================================================================
# 사용 예시 (공통)
# ============================================================================

if __name__ == "__main__":
    print(f"Mock 모델 구현: {'Pydantic' if USE_PYDANTIC else '순수 Python'}")

    # 예시 1: MockElement
    element = MockElement(
        element_id=1, page_id=1, class_name="question_number", confidence=0.95,
        bbox_x=100, bbox_y=200, bbox_width=50, bbox_height=30
    )
    print("\nMockElement 예시:")
    print(element)
    print(f"면적: {element.area}, Y-위치: {element.y_position}, X-위치: {element.x_position}")

    # 예시 2: MockTextContent
    text_content = MockTextContent(
        text_id=1, element_id=1, ocr_text="1. 다음 중 옳은 것을 고르시오.", ocr_confidence=0.98
    )
    print("\nMockTextContent 예시:")
    print(text_content)

    # 예시 3: v2.1 모델
    group = MockQuestionGroup(
        question_group_id=1, page_id=1, anchor_element_id=1, group_type='anchor',
        start_y=100, end_y=450, element_count=3
    )
    qe = MockQuestionElement(
        qe_id=1, question_group_id=1, element_id=2, order_in_question=1, order_in_group=1
    )
    print("\nv2.1 MockQuestionGroup 예시:")
    print(group)
    print("\nv2.1 MockQuestionElement 예시:")
    print(qe)

    # 예시 4: 유틸리티 함수
    detection = {'class_name': 'figure', 'confidence': 0.88, 'bbox': [50, 300, 200, 150]}
    element_util = create_mock_element_from_detection(2, detection, page_id=1)
    print("\n유틸리티 함수 예시 (Element):")
    print(element_util)

    text_util = create_mock_text_content(2, 2, "그림 A는 ...", ocr_confidence=0.91)
    print("\n유틸리티 함수 예시 (Text):")
    print(text_util)

    # 예시 5: JSON 직렬화
    print("\nJSON 직렬화 예시:")
    if USE_PYDANTIC:
        # Pydantic v2는 model_dump_json 사용
        print(element.model_dump_json(indent=2)) # type: ignore[attr-defined]
        print(text_content.model_dump_json(indent=2)) # type: ignore[attr-defined]
    else:
        print(element.to_json(indent=2))
        print(text_content.to_json(indent=2))