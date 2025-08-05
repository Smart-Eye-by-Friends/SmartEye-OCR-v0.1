"""
SmartEye LAM (Layout Analysis Module) Configuration

현재 노트북 코드에서 추출한 설정과 상수들을 Django 프로젝트로 이전
"""

import os
from pathlib import Path
from django.conf import settings


class LAMConfig:
    """LAM 모듈 설정"""
    
    # 지원되는 파일 형식
    SUPPORTED_IMAGE_FORMATS = {'.jpg', '.jpeg', '.png', '.pdf'}
    
    # 이미지 처리 설정
    MIN_AREA_THRESHOLD = 100  # 최소 픽셀 수
    MAX_DISPLAY_SIZE = 1000
    
    # 메모리 관리 설정
    MEMORY_LIMIT_MB = getattr(settings, 'SMARTEYE_CONFIG', {}).get('MEMORY_LIMIT_MB', 500)
    MEMORY_WARNING_THRESHOLD = 0.8  # 80% 사용 시 경고
    MEMORY_CRITICAL_THRESHOLD = 0.9  # 90% 사용 시 중단
    
    # 배치 처리 설정
    DEFAULT_BATCH_SIZE = getattr(settings, 'SMARTEYE_CONFIG', {}).get('BATCH_SIZE', 2)
    MAX_WORKERS = getattr(settings, 'SMARTEYE_CONFIG', {}).get('MAX_WORKERS', 2)
    
    # 임시 파일 설정
    TEMP_FILE_PREFIX = "smarteye_temp_"
    AUTO_CLEANUP_TEMP_FILES = True
    
    # 진행률 저장 설정
    CHECKPOINT_INTERVAL = 3  # 3개 이미지마다 체크포인트 저장
    
    # PDF 처리 최적화
    PDF_ZOOM_FACTOR = 1.5
    PDF_MAX_PAGES_PER_BATCH = 3
    
    # 모델 설정 (노트북에서 추출)
    MODEL_CONFIGS = {
        "doclaynet_docsynth": {
            "repo_id": "juliozhao/DocLayout-YOLO-DocLayNet-Docsynth300K_pretrained",
            "filename": "doclayout_yolo_doclaynet_imgsz1120_docsynth_pretrain.pt",
            "imgsz": 1536,
            "conf": 0.20
        },
        "docstructbench": {
            "repo_id": "juliozhao/DocLayout-YOLO-DocStructBench",
            "filename": "doclayout_yolo_docstructbench_imgsz1024.pt",
            "imgsz": 1024,
            "conf": 0.25
        },
        "docsynth300k": {
            "repo_id": "juliozhao/DocLayout-YOLO-DocSynth300K-pretrain",
            "filename": "doclayout_yolo_docsynth300k_imgsz1600.pt",
            "imgsz": 1600,
            "conf": 0.15
        }
    }
    
    # 기본 모델
    DEFAULT_MODEL = getattr(settings, 'SMARTEYE_CONFIG', {}).get('DEFAULT_MODEL', 'docstructbench')
    
    # 로깅 설정
    LOG_LEVEL = getattr(settings, 'SMARTEYE_CONFIG', {}).get('LOG_LEVEL', 'INFO')
    
    # 디버그 모드
    DEBUG_MODE = getattr(settings, 'SMARTEYE_CONFIG', {}).get('DEBUG_MODE', settings.DEBUG)
    SAVE_DEBUG_IMAGES = DEBUG_MODE
