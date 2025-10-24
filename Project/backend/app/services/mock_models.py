"""
Mock Models for Database Independence
======================================

이 모듈은 데이터베이스 의존성을 제거하기 위한 Mock 모델들을 정의합니다.
E-R 다이어그램 (v2)의 layout_elements와 text_contents 테이블을 대체합니다.

주요 특징:
- 두 가지 구현 제공: Pydantic 모델 (의존성 필요) 또는 순수 Python 클래스 (의존성 없음)
- 자동 계산 속성 (y_position, x_position, area)
- 데이터 검증 및 직렬화 지원
- 데이터베이스 연결 없이 독립적으로 동작

사용법:
- Pydantic이 설치된 경우: 자동으로 Pydantic 모델 사용
- 순수 Python 클래스 사용: Pydantic 미설치 시 자동 전환

References:
- E-R 다이어그램: Project/docs/E-R 다이어그램 (v2 - 레이아웃 정렬 알고리즘 반영).md
"""

from datetime import datetime
from typing import Optional, Dict, Any
import json as json_module

# 의존성 체크
try:
    from pydantic import BaseModel, Field, computed_field
    USE_PYDANTIC = True
except ImportError:
    USE_PYDANTIC = False
    BaseModel = object


# ============================================================================
# Pydantic 기반 구현 (의존성 필요)
# ============================================================================

if USE_PYDANTIC:

    class MockElement(BaseModel):
        """
        layout_elements 테이블을 대체하는 Mock 모델 (Pydantic 버전)

        레이아웃 분석 결과의 각 요소(element)를 나타냅니다.
        문제 번호, 텍스트, 그림, 표 등 페이지 내 모든 레이아웃 요소를 포함합니다.

        Attributes:
            element_id: 요소 고유 식별자 (PK)
            page_id: 페이지 식별자 (FK, optional for mock usage)
            class_name: 요소 클래스명 (예: 'question_number', 'question_text', 'figure', 'table')
            confidence: 탐지 신뢰도 (0.0 ~ 1.0)
            bbox_x: Bounding box 좌측 상단 X 좌표
            bbox_y: Bounding box 좌측 상단 Y 좌표
            bbox_width: Bounding box 너비
            bbox_height: Bounding box 높이
            created_at: 생성 시각 (optional)

        Computed Properties:
            area: 요소의 면적 (bbox_width * bbox_height)
            y_position: Y 좌표 정렬용 속성 (= bbox_y)
            x_position: X 좌표 정렬용 속성 (= bbox_x)
        
        Added Attributes (by sorter.py):
            order_in_question: 전체 정렬 순서 (0부터 시작)
            group_id: 소속 그룹 ID (0부터 시작)
            order_in_group: 그룹 내 정렬 순서 (0부터 시작)
        """

        # Primary Fields
        element_id: int = Field(..., description="요소 고유 식별자", ge=1)
        page_id: Optional[int] = Field(None, description="페이지 식별자", ge=1)
        class_name: str = Field(..., description="요소 클래스명", min_length=1, max_length=100)
        confidence: float = Field(..., description="탐지 신뢰도", ge=0.0, le=1.0)

        # Bounding Box Coordinates
        bbox_x: int = Field(..., description="Bounding box 좌측 상단 X 좌표", ge=0)
        bbox_y: int = Field(..., description="Bounding box 좌측 상단 Y 좌표", ge=0)
        bbox_width: int = Field(..., description="Bounding box 너비", ge=1)
        bbox_height: int = Field(..., description="Bounding box 높이", ge=1)

        # Metadata
        created_at: Optional[datetime] = Field(default_factory=datetime.now, description="생성 시각")

        # ===>>> 추가할 필드 시작 <<<===
        order_in_question: Optional[int] = Field(None, description="전체 정렬 순서 (0부터 시작)")
        group_id: Optional[int] = Field(None, description="소속 그룹 ID (0부터 시작)")
        order_in_group: Optional[int] = Field(None, description="그룹 내 정렬 순서 (0부터 시작)")
        # ===>>> 추가할 필드 끝 <<<===
        
        # Computed Properties
        @computed_field
        @property
        def area(self) -> int:
            """요소의 면적 계산 (bbox_width * bbox_height)"""
            return self.bbox_width * self.bbox_height

        @computed_field
        @property
        def y_position(self) -> int:
            """Y 좌표 정렬용 속성 (= bbox_y)"""
            return self.bbox_y

        @computed_field
        @property
        def x_position(self) -> int:
            """X 좌표 정렬용 속성 (= bbox_x)"""
            return self.bbox_x

        class Config:
            """Pydantic 모델 설정"""
            json_schema_extra = {
                "example": {
                    "element_id": 1,
                    "page_id": 1,
                    "class_name": "question_number",
                    "confidence": 0.95,
                    "bbox_x": 100,
                    "bbox_y": 200,
                    "bbox_width": 50,
                    "bbox_height": 30,
                }
            }

    class MockTextContent(BaseModel):
        """
        text_contents 테이블을 대체하는 Mock 모델 (Pydantic 버전)

        OCR 결과 텍스트 정보를 저장합니다.
        각 레이아웃 요소(MockElement)는 하나의 텍스트 콘텐츠를 가집니다 (1:1 관계).

        Attributes:
            text_id: 텍스트 콘텐츠 고유 식별자 (PK)
            element_id: 연결된 레이아웃 요소 식별자 (FK, UNIQUE)
            ocr_text: OCR로 추출된 텍스트
            ocr_engine: 사용된 OCR 엔진 이름 (기본: 'PaddleOCR')
            ocr_confidence: OCR 신뢰도 (0.0 ~ 1.0, optional)
            language: 텍스트 언어 (기본: 'ko')
            created_at: 생성 시각 (optional)
        """

        # Primary Fields
        text_id: int = Field(..., description="텍스트 콘텐츠 고유 식별자", ge=1)
        element_id: int = Field(..., description="연결된 레이아웃 요소 식별자 (UNIQUE)", ge=1)

        # OCR Content
        ocr_text: str = Field(..., description="OCR로 추출된 텍스트")

        # OCR Metadata
        ocr_engine: str = Field(default="PaddleOCR", description="사용된 OCR 엔진 이름", max_length=50)
        ocr_confidence: Optional[float] = Field(None, description="OCR 신뢰도", ge=0.0, le=1.0)
        language: str = Field(default="ko", description="텍스트 언어 코드", max_length=10)

        # Metadata
        created_at: Optional[datetime] = Field(default_factory=datetime.now, description="생성 시각")

        class Config:
            """Pydantic 모델 설정"""
            json_schema_extra = {
                "example": {
                    "text_id": 1,
                    "element_id": 1,
                    "ocr_text": "1. 다음 중 옳은 것을 고르시오.",
                    "ocr_engine": "PaddleOCR",
                    "ocr_confidence": 0.98,
                    "language": "ko",
                }
            }


# ============================================================================
# 순수 Python 클래스 구현 (의존성 없음)
# ============================================================================


else:

    class MockElement:
        """
        layout_elements 테이블을 대체하는 Mock 모델 (순수 Python 버전)

        레이아웃 분석 결과의 각 요소(element)를 나타냅니다.
        문제 번호, 텍스트, 그림, 표 등 페이지 내 모든 레이아웃 요소를 포함합니다.

        Attributes:
            element_id: 요소 고유 식별자 (PK)
            page_id: 페이지 식별자 (FK, optional for mock usage)
            class_name: 요소 클래스명 (예: 'question_number', 'question_text', 'figure', 'table')
            confidence: 탐지 신뢰도 (0.0 ~ 1.0)
            bbox_x: Bounding box 좌측 상단 X 좌표
            bbox_y: Bounding box 좌측 상단 Y 좌표
            bbox_width: Bounding box 너비
            bbox_height: Bounding box 높이
            created_at: 생성 시각 (optional)

        Computed Properties:
            area: 요소의 면적 (bbox_width * bbox_height)
            y_position: Y 좌표 정렬용 속성 (= bbox_y)
            x_position: X 좌표 정렬용 속성 (= bbox_x)
        """

        def __init__(
            self,
            element_id: int,
            class_name: str,
            confidence: float,
            bbox_x: int,
            bbox_y: int,
            bbox_width: int,
            bbox_height: int,
            page_id: Optional[int] = None,
            created_at: Optional[datetime] = None
        ):
            """MockElement 초기화"""
            # Validation
            if element_id < 1:
                raise ValueError("element_id must be >= 1")
            if not class_name or len(class_name) > 100:
                raise ValueError("class_name must be between 1 and 100 characters")
            if not (0.0 <= confidence <= 1.0):
                raise ValueError("confidence must be between 0.0 and 1.0")
            if bbox_x < 0 or bbox_y < 0:
                raise ValueError("bbox coordinates must be >= 0")
            if bbox_width < 1 or bbox_height < 1:
                raise ValueError("bbox dimensions must be >= 1")
            if page_id is not None and page_id < 1:
                raise ValueError("page_id must be >= 1")

            # Assign fields
            self.element_id = element_id
            self.page_id = page_id
            self.class_name = class_name
            self.confidence = confidence
            self.bbox_x = bbox_x
            self.bbox_y = bbox_y
            self.bbox_width = bbox_width
            self.bbox_height = bbox_height
            self.created_at = created_at or datetime.now()

        @property
        def area(self) -> int:
            """요소의 면적 계산 (bbox_width * bbox_height)"""
            return self.bbox_width * self.bbox_height

        @property
        def y_position(self) -> int:
            """Y 좌표 정렬용 속성 (= bbox_y)"""
            return self.bbox_y

        @property
        def x_position(self) -> int:
            """X 좌표 정렬용 속성 (= bbox_x)"""
            return self.bbox_x

        def to_dict(self) -> Dict[str, Any]:
            """딕셔너리로 변환"""
            return {
                "element_id": self.element_id,
                "page_id": self.page_id,
                "class_name": self.class_name,
                "confidence": self.confidence,
                "bbox_x": self.bbox_x,
                "bbox_y": self.bbox_y,
                "bbox_width": self.bbox_width,
                "bbox_height": self.bbox_height,
                "area": self.area,
                "y_position": self.y_position,
                "x_position": self.x_position,
                "created_at": self.created_at.isoformat() if self.created_at else None
            }

        def to_json(self, indent: Optional[int] = None) -> str:
            """JSON 문자열로 변환"""
            return json_module.dumps(self.to_dict(), ensure_ascii=False, indent=indent)

        def __repr__(self) -> str:
            return (
                f"MockElement(element_id={self.element_id}, class_name='{self.class_name}', "
                f"bbox=({self.bbox_x}, {self.bbox_y}, {self.bbox_width}, {self.bbox_height}))"
            )

    class MockTextContent:
        """
        text_contents 테이블을 대체하는 Mock 모델 (순수 Python 버전)

        OCR 결과 텍스트 정보를 저장합니다.
        각 레이아웃 요소(MockElement)는 하나의 텍스트 콘텐츠를 가집니다 (1:1 관계).

        Attributes:
            text_id: 텍스트 콘텐츠 고유 식별자 (PK)
            element_id: 연결된 레이아웃 요소 식별자 (FK, UNIQUE)
            ocr_text: OCR로 추출된 텍스트
            ocr_engine: 사용된 OCR 엔진 이름 (기본: 'PaddleOCR')
            ocr_confidence: OCR 신뢰도 (0.0 ~ 1.0, optional)
            language: 텍스트 언어 (기본: 'ko')
            created_at: 생성 시각 (optional)
        """

        def __init__(
            self,
            text_id: int,
            element_id: int,
            ocr_text: str,
            ocr_engine: str = "PaddleOCR",
            ocr_confidence: Optional[float] = None,
            language: str = "ko",
            created_at: Optional[datetime] = None
        ):
            """MockTextContent 초기화"""
            # Validation
            if text_id < 1:
                raise ValueError("text_id must be >= 1")
            if element_id < 1:
                raise ValueError("element_id must be >= 1")
            if not ocr_text:
                raise ValueError("ocr_text cannot be empty")
            if ocr_confidence is not None and not (0.0 <= ocr_confidence <= 1.0):
                raise ValueError("ocr_confidence must be between 0.0 and 1.0")
            if len(ocr_engine) > 50:
                raise ValueError("ocr_engine must be <= 50 characters")
            if len(language) > 10:
                raise ValueError("language must be <= 10 characters")

            # Assign fields
            self.text_id = text_id
            self.element_id = element_id
            self.ocr_text = ocr_text
            self.ocr_engine = ocr_engine
            self.ocr_confidence = ocr_confidence
            self.language = language
            self.created_at = created_at or datetime.now()

        def to_dict(self) -> Dict[str, Any]:
            """딕셔너리로 변환"""
            return {
                "text_id": self.text_id,
                "element_id": self.element_id,
                "ocr_text": self.ocr_text,
                "ocr_engine": self.ocr_engine,
                "ocr_confidence": self.ocr_confidence,
                "language": self.language,
                "created_at": self.created_at.isoformat() if self.created_at else None
            }

        def to_json(self, indent: Optional[int] = None) -> str:
            """JSON 문자열로 변환"""
            return json_module.dumps(self.to_dict(), ensure_ascii=False, indent=indent)

        def __repr__(self) -> str:
            return (
                f"MockTextContent(text_id={self.text_id}, element_id={self.element_id}, "
                f"ocr_text='{self.ocr_text[:30]}...')"
            )


# ============================================================================
# Utility Functions (공통)
# ============================================================================

def create_mock_element_from_detection(
    element_id: int,
    detection_result: Dict[str, Any],
    page_id: Optional[int] = None
) -> MockElement:
    """
    레이아웃 탐지 결과로부터 MockElement 생성

    Args:
        element_id: 요소 고유 식별자
        detection_result: 탐지 결과 딕셔너리
            - class_name: 클래스명
            - confidence: 신뢰도
            - bbox: [x, y, width, height] 또는 {'x': x, 'y': y, 'width': w, 'height': h}
        page_id: 페이지 식별자 (optional)

    Returns:
        MockElement: 생성된 Mock 요소

    Example:
        >>> detection = {
        ...     'class_name': 'question_number',
        ...     'confidence': 0.95,
        ...     'bbox': [100, 200, 50, 30]
        ... }
        >>> element = create_mock_element_from_detection(1, detection)
    """
    bbox = detection_result['bbox']

    # bbox가 리스트인 경우
    if isinstance(bbox, list):
        bbox_x, bbox_y, bbox_width, bbox_height = bbox
    # bbox가 딕셔너리인 경우
    else:
        bbox_x = bbox['x']
        bbox_y = bbox['y']
        bbox_width = bbox['width']
        bbox_height = bbox['height']

    return MockElement(
        element_id=element_id,
        page_id=page_id,
        class_name=detection_result['class_name'],
        confidence=detection_result['confidence'],
        bbox_x=bbox_x,
        bbox_y=bbox_y,
        bbox_width=bbox_width,
        bbox_height=bbox_height
    )


def create_mock_text_content(
    text_id: int,
    element_id: int,
    ocr_result: str,
    ocr_confidence: Optional[float] = None,
    ocr_engine: str = "PaddleOCR"
) -> MockTextContent:
    """
    OCR 결과로부터 MockTextContent 생성

    Args:
        text_id: 텍스트 콘텐츠 고유 식별자
        element_id: 연결된 요소 식별자
        ocr_result: OCR 추출 텍스트
        ocr_confidence: OCR 신뢰도 (optional)
        ocr_engine: OCR 엔진 이름 (기본: 'PaddleOCR')

    Returns:
        MockTextContent: 생성된 Mock 텍스트 콘텐츠

    Example:
        >>> text_content = create_mock_text_content(
        ...     text_id=1,
        ...     element_id=1,
        ...     ocr_result="1. 다음 중 옳은 것을 고르시오.",
        ...     ocr_confidence=0.98
        ... )
    """
    return MockTextContent(
        text_id=text_id,
        element_id=element_id,
        ocr_text=ocr_result,
        ocr_engine=ocr_engine,
        ocr_confidence=ocr_confidence
    )


# ============================================================================
# Example Usage
# ============================================================================

if __name__ == "__main__":
    print("=" * 70)
    print(f"Mock Models Implementation: {'Pydantic' if USE_PYDANTIC else 'Pure Python'}")
    print("=" * 70)
    print()

    # Example 1: MockElement 생성 및 computed fields 확인
    print("=" * 70)
    print("Example 1: MockElement with computed fields")
    print("=" * 70)

    element = MockElement(
        element_id=1,
        page_id=1,
        class_name="question_number",
        confidence=0.95,
        bbox_x=100,
        bbox_y=200,
        bbox_width=50,
        bbox_height=30
    )

    print(f"Element ID: {element.element_id}")
    print(f"Class Name: {element.class_name}")
    print(f"Bbox: ({element.bbox_x}, {element.bbox_y}, {element.bbox_width}, {element.bbox_height})")
    print(f"Area (computed): {element.area}")
    print(f"Y Position (computed): {element.y_position}")
    print(f"X Position (computed): {element.x_position}")
    print()

    # Example 2: MockTextContent 생성
    print("=" * 70)
    print("Example 2: MockTextContent")
    print("=" * 70)

    text_content = MockTextContent(
        text_id=1,
        element_id=1,
        ocr_text="1. 다음 중 옳은 것을 고르시오.",
        ocr_confidence=0.98
    )

    print(f"Text ID: {text_content.text_id}")
    print(f"Element ID: {text_content.element_id}")
    print(f"OCR Text: {text_content.ocr_text}")
    print(f"OCR Engine: {text_content.ocr_engine}")
    print(f"OCR Confidence: {text_content.ocr_confidence}")
    print(f"Language: {text_content.language}")
    print()

    # Example 3: Utility 함수 사용
    print("=" * 70)
    print("Example 3: Using utility functions")
    print("=" * 70)

    detection_result = {
        'class_name': 'question_text',
        'confidence': 0.92,
        'bbox': [150, 250, 400, 60]
    }

    element2 = create_mock_element_from_detection(
        element_id=2,
        detection_result=detection_result,
        page_id=1
    )

    print(f"Created element: {element2.class_name}")
    print(f"Position: ({element2.x_position}, {element2.y_position})")
    print(f"Area: {element2.area}")
    print()

    text_content2 = create_mock_text_content(
        text_id=2,
        element_id=2,
        ocr_result="다음 보기에서 정답을 선택하시오.",
        ocr_confidence=0.96
    )

    print(f"Created text content: {text_content2.ocr_text}")
    print()

    # Example 4: JSON 직렬화
    print("=" * 70)
    print("Example 4: JSON serialization")
    print("=" * 70)

    if USE_PYDANTIC:
        print("Element as JSON (Pydantic):")
        print(element.model_dump_json(indent=2))
        print()
        print("Text Content as JSON (Pydantic):")
        print(text_content.model_dump_json(indent=2))
    else:
        print("Element as JSON (Pure Python):")
        print(element.to_json(indent=2))
        print()
        print("Text Content as JSON (Pure Python):")
        print(text_content.to_json(indent=2))
