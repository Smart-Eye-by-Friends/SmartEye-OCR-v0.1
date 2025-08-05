"""
Configuration management for SmartEye backend
"""
import os
from typing import Optional, List
from pydantic import BaseSettings


class Settings(BaseSettings):
    """Application settings"""
    
    # Application
    app_name: str = "SmartEye OCR API"
    debug: bool = False
    version: str = "0.1.0"
    
    # Server
    host: str = "0.0.0.0"
    port: int = 8000
    
    # OpenAI API
    openai_api_key: Optional[str] = None
    
    # Model settings
    default_model: str = "docstructbench"
    confidence_threshold: float = 0.25
    device: str = "auto"  # auto, cpu, cuda
    
    # Memory management
    memory_warning_threshold: float = 0.8
    memory_critical_threshold: float = 0.9
    batch_size_colab: int = 2
    batch_size_local: int = 4
    
    # Redis settings (for caching and task queue)
    redis_url: str = "redis://localhost:6379"
    task_timeout: int = 3600  # 1 hour
    
    # PostgreSQL settings (for result storage)
    database_url: Optional[str] = None
    
    # File upload settings
    max_file_size: int = 50 * 1024 * 1024  # 50MB
    upload_dir: str = "/tmp/smarteye_uploads"
    allowed_extensions: List[str] = [".jpg", ".jpeg", ".png", ".pdf"]
    
    # Logging
    log_level: str = "INFO"
    log_format: str = "%(asctime)s - %(name)s - %(levelname)s - %(message)s"
    
    # OCR settings
    tesseract_config: str = '--oem 3 --psm 6'
    ocr_languages: List[str] = ["kor", "eng"]
    
    class Config:
        env_file = ".env"
        env_prefix = "SMARTEYE_"


# Global settings instance
settings = Settings()

# Model configurations
MODEL_CONFIGS = {
    "docstructbench": {
        "repo_id": "juliozhao/DocLayout-YOLO-DocStructBench",
        "filename": "doclayout_yolo_docstructbench_imgsz1024.pt",
        "description": "Optimized for educational worksheets"
    },
    "doclaynet": {
        "repo_id": "juliozhao/DocLayout-YOLO-DocLayNet-Docsynth300K_pretrained", 
        "filename": "doclayout_yolo_doclaynet_imgsz1120_docsynth_pretrain.pt",
        "description": "General document layout analysis"
    },
    "docsynth": {
        "repo_id": "juliozhao/DocLayout-YOLO-DocSynth300K-pretrain",
        "filename": "doclayout_yolo_docsynth300k_imgsz1600.pt",
        "description": "Custom synthetic document training"
    }
}

# Supported layout classes
SUPPORTED_CLASSES = [
    'title', 'plain text', 'abandon text',
    'figure', 'figure caption', 'table', 'table caption',
    'table footnote', 'isolated formula', 'formula caption'
]

# OCR vs API processing mapping
OCR_TARGET_CLASSES = ['title', 'plain text', 'isolated formula']
API_TARGET_CLASSES = ['figure', 'table', 'figure caption', 'table caption']