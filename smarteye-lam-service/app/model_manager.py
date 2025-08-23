"""
모델 관리자
DocLayout-YOLO 모델 다운로드 및 설정 관리
"""

import os
import subprocess
import logging
from pathlib import Path
from typing import Optional

logger = logging.getLogger(__name__)

class ModelManager:
    """DocLayout-YOLO 모델 관리자"""
    
    def __init__(self, settings):
        self.settings = settings
        self.doclayout_yolo_path = os.path.join(self.settings.model_cache_dir, "DocLayout-YOLO")
        
    def ensure_doclayout_yolo(self) -> str:
        """DocLayout-YOLO GitHub 리포지토리 확인 및 다운로드"""
        try:
            if self._is_doclayout_yolo_installed():
                logger.info(f"DocLayout-YOLO가 이미 설치되어 있습니다: {self.doclayout_yolo_path}")
                return self.doclayout_yolo_path
            
            logger.info("DocLayout-YOLO GitHub 리포지토리를 클론합니다...")
            return self._clone_doclayout_yolo()
            
        except Exception as e:
            logger.error(f"DocLayout-YOLO 설치 실패: {e}")
            raise e
    
    def _is_doclayout_yolo_installed(self) -> bool:
        """DocLayout-YOLO 설치 여부 확인"""
        required_files = [
            "doclayout_yolo/__init__.py",
            "doclayout_yolo/models/__init__.py",
            "setup.py"
        ]
        
        if not os.path.exists(self.doclayout_yolo_path):
            return False
        
        for file_path in required_files:
            full_path = os.path.join(self.doclayout_yolo_path, file_path)
            if not os.path.exists(full_path):
                logger.warning(f"필요한 파일이 없습니다: {full_path}")
                return False
        
        return True
    
    def _clone_doclayout_yolo(self) -> str:
        """DocLayout-YOLO GitHub 리포지토리 클론"""
        try:
            # 캐시 디렉토리 생성
            Path(self.settings.model_cache_dir).mkdir(parents=True, exist_ok=True)
            
            # 기존 디렉토리가 있다면 제거
            if os.path.exists(self.doclayout_yolo_path):
                import shutil
                shutil.rmtree(self.doclayout_yolo_path)
            
            # Git 클론
            clone_cmd = [
                "git", "clone", 
                "https://github.com/opendatalab/DocLayout-YOLO.git",
                self.doclayout_yolo_path
            ]
            
            logger.info(f"실행 중: {' '.join(clone_cmd)}")
            result = subprocess.run(
                clone_cmd, 
                capture_output=True, 
                text=True,
                cwd=self.settings.model_cache_dir,
                timeout=300  # 5분 타임아웃
            )
            
            if result.returncode != 0:
                raise RuntimeError(f"Git 클론 실패: {result.stderr}")
            
            logger.info("✅ DocLayout-YOLO 클론 완료")
            
            # 의존성 설치
            self._install_doclayout_yolo_dependencies()
            
            return self.doclayout_yolo_path
            
        except subprocess.TimeoutExpired:
            raise RuntimeError("Git 클론 타임아웃 (5분 초과)")
        except Exception as e:
            raise RuntimeError(f"DocLayout-YOLO 클론 실패: {e}")
    
    def _install_doclayout_yolo_dependencies(self):
        """DocLayout-YOLO 의존성 설치"""
        try:
            logger.info("DocLayout-YOLO 의존성 설치 중...")
            
            # requirements.txt가 있는지 확인
            requirements_path = os.path.join(self.doclayout_yolo_path, "requirements.txt")
            if os.path.exists(requirements_path):
                install_cmd = [
                    "pip", "install", "-r", requirements_path
                ]
                
                result = subprocess.run(
                    install_cmd,
                    capture_output=True,
                    text=True,
                    timeout=600  # 10분 타임아웃
                )
                
                if result.returncode != 0:
                    logger.warning(f"의존성 설치 중 경고: {result.stderr}")
                else:
                    logger.info("✅ 의존성 설치 완료")
            
            # 패키지 자체 설치 (editable mode)
            setup_py_path = os.path.join(self.doclayout_yolo_path, "setup.py")
            if os.path.exists(setup_py_path):
                install_cmd = [
                    "pip", "install", "-e", self.doclayout_yolo_path
                ]
                
                result = subprocess.run(
                    install_cmd,
                    capture_output=True,
                    text=True,
                    timeout=300
                )
                
                if result.returncode != 0:
                    logger.warning(f"패키지 설치 중 경고: {result.stderr}")
                else:
                    logger.info("✅ DocLayout-YOLO 패키지 설치 완료")
            
        except Exception as e:
            logger.warning(f"의존성 설치 실패 (계속 진행): {e}")
    
    def get_doclayout_yolo_path(self) -> str:
        """DocLayout-YOLO 경로 반환"""
        return self.doclayout_yolo_path
    
    def is_model_available(self) -> bool:
        """모델 사용 가능 여부 확인"""
        return (
            self.settings.model_path is not None and 
            os.path.exists(self.settings.model_path) and
            self._is_doclayout_yolo_installed()
        )
