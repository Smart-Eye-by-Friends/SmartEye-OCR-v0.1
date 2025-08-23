"""
설정 관리
환경 변수 및 애플리케이션 설정
"""

import os
from pathlib import Path
from typing import Optional
import logging

logger = logging.getLogger(__name__)

class Settings:
    """애플리케이션 설정"""
    
    def __init__(self):
        # 서버 설정
        self.host: str = os.getenv("LAM_HOST", "0.0.0.0")
        self.port: int = int(os.getenv("LAM_PORT", "8081"))
        self.debug: bool = os.getenv("LAM_DEBUG", "false").lower() == "true"
        
        # 모델 설정 - 허깅페이스에서 다운로드
        self.model_choice: str = os.getenv("LAM_MODEL_CHOICE", "docstructbench")
        self.model_configs = {
            "docstructbench": {
                "repo_id": "juliozhao/DocLayout-YOLO-DocStructBench",
                "filename": "doclayout_yolo_docstructbench_imgsz1024.pt",
                "imgsz": 1024,
                "conf": 0.25,
                "description": "학습지/교과서 최적화 모델"
            },
            "doclaynet": {
                "repo_id": "juliozhao/DocLayout-YOLO-DocLayNet-Docsynth300K_pretrained",
                "filename": "doclayout_yolo_doclaynet_imgsz1120_docsynth_pretrain.pt",
                "imgsz": 1120,
                "conf": 0.25,
                "description": "일반 문서 최적화 모델"
            },
            "docsynth300k": {
                "repo_id": "juliozhao/DocLayout-YOLO-DocSynth300K-pretrain",
                "filename": "doclayout_yolo_docsynth300k_imgsz1600.pt",
                "imgsz": 1600,
                "conf": 0.25,
                "description": "사전훈련 모델 (연구용)"
            },
            "SmartEyeSsen": {
                "repo_id": "AkJeond/SmartEyeSsen",
                "filename": "best_tuned_model.pt",
                "imgsz": 1024,
                "conf": 0.25,
                "description": "SmartEye 쎈 수학 파인튜닝 모델"
            }
        }
        
        # 모델 다운로드 및 캐시 경로
        self.model_cache_dir: str = os.getenv("LAM_MODEL_CACHE_DIR", "/app/models")
        self.model_path: Optional[str] = None  # 실제 모델 경로는 다운로드 후 설정
        
        # 기타 모델 설정
        self.confidence_threshold: float = float(os.getenv("LAM_CONFIDENCE_THRESHOLD", "0.5"))
        self.max_image_size: int = int(os.getenv("LAM_MAX_IMAGE_SIZE", "4096"))
        
        # GPU 설정
        self.use_gpu: bool = os.getenv("LAM_USE_GPU", "false").lower() == "true"
        self.gpu_device: int = int(os.getenv("LAM_GPU_DEVICE", "0"))
        
        # 파일 시스템 설정
        self.temp_dir: str = os.getenv("LAM_TEMP_DIR", "/tmp/smarteye-lam")
        self.max_file_size: int = int(os.getenv("LAM_MAX_FILE_SIZE", "10485760"))  # 10MB
        
        # 로깅 설정
        self.log_level: str = os.getenv("LAM_LOG_LEVEL", "INFO")
        self.log_file: Optional[str] = os.getenv("LAM_LOG_FILE", None)
        
        # 모델 클래스 정의
        self.supported_classes = [
            "title",
            "plain text", 
            "abandon",
            "figure",
            "figure_caption",
            "table",
            "table_caption",
            "header",
            "footer",
            "reference",
            "equation"
        ]
        
        # 성능 설정
        self.max_concurrent_requests: int = int(os.getenv("LAM_MAX_CONCURRENT_REQUESTS", "10"))
        self.request_timeout: int = int(os.getenv("LAM_REQUEST_TIMEOUT", "30"))
        
        # 검증
        self._validate_settings()
    
    def download_model(self) -> str:
        """허깅페이스에서 모델 다운로드"""
        try:
            from huggingface_hub import hf_hub_download
        except ImportError:
            logger.error("huggingface_hub가 설치되지 않았습니다. pip install huggingface_hub를 실행하세요.")
            raise ImportError("huggingface_hub가 필요합니다")
        
        if self.model_choice not in self.model_configs:
            raise ValueError(f"지원하지 않는 모델: {self.model_choice}")
        
        selected_model = self.model_configs[self.model_choice]
        
        try:
            logger.info(f"📥 모델 다운로드: {selected_model['description']}")
            logger.info(f"Repository: {selected_model['repo_id']}")
            logger.info(f"Filename: {selected_model['filename']}")
            
            # 모델 다운로드
            filepath = hf_hub_download(
                repo_id=selected_model["repo_id"],
                filename=selected_model["filename"],
                cache_dir=self.model_cache_dir
            )
            
            logger.info(f"✅ 모델 다운로드 완료: {filepath}")
            self.model_path = filepath
            return filepath
            
        except Exception as e:
            logger.error(f"❌ 모델 다운로드 실패: {e}")
            raise e
    
    def _validate_settings(self):
        """설정 검증"""
        # 모델 캐시 디렉토리 생성
        Path(self.model_cache_dir).mkdir(parents=True, exist_ok=True)
        
        # 신뢰도 임계값 검증
        if not 0.0 <= self.confidence_threshold <= 1.0:
            raise ValueError(f"신뢰도 임계값이 유효하지 않습니다: {self.confidence_threshold}")
        
        # 임시 디렉토리 생성
        Path(self.temp_dir).mkdir(parents=True, exist_ok=True)
        
        # 모델 선택 검증
        if self.model_choice not in self.model_configs:
            raise ValueError(f"지원하지 않는 모델 선택: {self.model_choice}")
    
    def get_model_config(self) -> dict:
        """모델 설정 딕셔너리 반환"""
        current_config = self.model_configs.get(self.model_choice, self.model_configs["docstructbench"])
        return {
            "model_path": self.model_path,
            "model_choice": self.model_choice,
            "confidence_threshold": self.confidence_threshold,
            "max_image_size": self.max_image_size,
            "use_gpu": self.use_gpu,
            "gpu_device": self.gpu_device,
            "supported_classes": self.supported_classes,
            "imgsz": current_config["imgsz"],
            "conf": current_config["conf"],
            "repo_id": current_config["repo_id"],
            "filename": current_config["filename"]
        }

# 전역 설정 인스턴스
_settings = None

def get_settings() -> Settings:
    """설정 인스턴스 반환 (싱글톤)"""
    global _settings
    if _settings is None:
        _settings = Settings()
    return _settings
