"""
SmartEye CIM (Content Integration Module) Configuration

CIM 모듈의 설정을 관리하는 클래스
"""

import os
from django.conf import settings


class CIMConfig:
    """CIM 모듈 설정"""
    
    # 시각화 설정
    VISUALIZATION_FORMATS = ['png', 'jpg', 'pdf']
    DEFAULT_FORMAT = 'png'
    
    # 이미지 출력 설정
    OUTPUT_DPI = 300
    OUTPUT_QUALITY = 95
    MAX_OUTPUT_WIDTH = 2048
    MAX_OUTPUT_HEIGHT = 2048
    
    # 폰트 설정
    FONT_PATHS = [
        "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",  # Ubuntu/Colab
        "/System/Library/Fonts/AppleGothic.ttf",           # macOS
        "C:/Windows/Fonts/malgun.ttf"                      # Windows
    ]
    
    DEFAULT_FONT_SIZE = 12
    MIN_FONT_SIZE = 8
    MAX_FONT_SIZE = 24
    
    # 색상 설정
    COLORS = {
        'ocr_bbox': (0, 255, 0),      # 녹색 - OCR 박스
        'api_bbox': (255, 0, 0),      # 빨간색 - API 박스
        'text_color': (0, 0, 0),      # 검은색 - 텍스트
        'background': (255, 255, 255), # 흰색 - 배경
        'separator': (0, 0, 0)        # 검은색 - 구분선
    }
    
    # 박스 스타일 설정
    BBOX_THICKNESS = 2
    BBOX_OPACITY = 0.3
    TEXT_PADDING = 5
    
    # 결과 통합 설정
    CONTENT_TYPES = {
        'text': ['title', 'plain text', 'abandon text', 'table caption', 'table footnote'],
        'formula': ['isolated formula', 'formula caption'],
        'visual': ['figure', 'table']
    }
    
    # 출력 템플릿 설정
    DOCUMENT_TEMPLATE = {
        'title': '📄 SmartEye 문서 분석 결과',
        'sections': {
            'summary': '📊 분석 요약',
            'text_content': '📝 텍스트 내용',
            'visual_content': '🖼️ 시각적 내용',
            'detailed_results': '📋 상세 결과'
        }
    }
    
    # 통계 설정
    STATISTICS_CONFIG = {
        'show_confidence': True,
        'show_processing_time': True,
        'show_class_distribution': True,
        'show_subject_categories': True
    }
    
    # 내보내기 설정
    EXPORT_FORMATS = ['json', 'csv', 'txt', 'html', 'pdf']
    DEFAULT_EXPORT_FORMAT = 'json'
    
    # 시각화 비교 설정
    COMPARISON_LAYOUT = {
        'gap_width': 5,  # 구분선 폭
        'show_labels': True,
        'label_height': 30
    }
    
    @classmethod
    def get_font_path(cls) -> str:
        """사용 가능한 폰트 경로 반환"""
        for font_path in cls.FONT_PATHS:
            if os.path.exists(font_path):
                return font_path
        return None  # 기본 폰트 사용
    
    @classmethod
    def validate_config(cls) -> bool:
        """설정 유효성 검사"""
        # 기본적인 라이브러리 확인
        try:
            import matplotlib.pyplot as plt
            import numpy as np
            from PIL import Image, ImageDraw, ImageFont
            return True
        except ImportError:
            return False
