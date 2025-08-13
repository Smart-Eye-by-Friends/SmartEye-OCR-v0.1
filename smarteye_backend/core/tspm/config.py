"""
SmartEye TSPM (Text & Scene Processing Module) Configuration

TSPM 모듈의 설정을 관리하는 클래스
"""

import os
from django.conf import settings


class TSPMConfig:
    """TSPM 모듈 설정"""
    
    # OCR 설정
    OCR_TARGET_CLASSES = [
        'title', 'plain text', 'abandon text',
        'table caption', 'table footnote',
        'isolated formula', 'formula caption'
    ]
    
    OCR_CONFIG = r'--oem 3 --psm 6'
    OCR_LANGUAGES = {
        'korean': 'kor',
        'english': 'eng',
        'auto': 'kor+eng'
    }
    DEFAULT_OCR_LANGUAGE = 'kor+eng'
    
    # API 설정 (이미지 설명)
    API_TARGET_CLASSES = ['figure', 'table']
    
    # OpenAI API 설정
    OPENAI_MODEL = 'gpt-4o'
    OPENAI_MAX_TOKENS = 300
    OPENAI_TEMPERATURE = 0.1
    OPENAI_TIMEOUT = 30  # API 호출 타임아웃 (초)
    OPENAI_MAX_RETRIES = 3  # 재시도 횟수
    
    # API 프롬프트 템플릿
    API_PROMPTS = {
        'figure': """이 이미지는 학습지에서 추출된 그림/도표입니다. 
다음 내용을 포함하여 간단명료하게 설명해주세요:
1. 주요 내용이나 주제
2. 중요한 구성요소나 특징
3. 학습적 의미나 목적

설명은 200자 이내로 작성해주세요.""",
        
        'table': """이 이미지는 학습지에서 추출된 표입니다.
다음 내용을 포함하여 간단명료하게 설명해주세요:
1. 표의 주제나 목적
2. 주요 열/행의 구성
3. 담고 있는 데이터 유형

설명은 200자 이내로 작성해주세요."""
    }
    
    # 이미지 처리 설정
    MIN_AREA_THRESHOLD = 100  # 최소 픽셀 수
    IMAGE_QUALITY_FACTOR = 0.9  # JPEG 품질 (API 전송용)
    MAX_IMAGE_SIZE = 2048  # API 전송용 최대 이미지 크기
    
    # 텍스트 처리 설정
    MIN_TEXT_LENGTH = 2  # 최소 텍스트 길이
    MAX_TEXT_LENGTH = 10000  # 최대 텍스트 길이
    TEXT_WRAP_WIDTH = 25  # 텍스트 줄바꿈 폭
    
    # 임시 파일 설정
    TEMP_FILE_PREFIX = 'tspm_'
    TEMP_FILE_CLEANUP_HOURS = 24
    
    # 배치 처리 설정
    DEFAULT_BATCH_SIZE = 5
    MAX_BATCH_SIZE = 20
    
    @classmethod
    def get_openai_api_key(cls) -> str:
        """OpenAI API 키 반환"""
        api_key = getattr(settings, 'OPENAI_API_KEY', None)
        if not api_key:
            api_key = os.getenv('OPENAI_API_KEY')
        return api_key
    
    @classmethod
    def validate_config(cls) -> bool:
        """설정 유효성 검사"""
        # OpenAI API 키 확인
        api_key = cls.get_openai_api_key()
        if not api_key:
            return False
        
        # Tesseract 설치 확인
        try:
            import pytesseract
            pytesseract.get_tesseract_version()
        except:
            return False
        
        return True
