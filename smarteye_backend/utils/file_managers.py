"""
파일 리소스 관리를 위한 Context Manager 및 유틸리티
"""
import os
import tempfile
import logging
from contextlib import contextmanager
from typing import Generator, Optional
from pathlib import Path

logger = logging.getLogger(__name__)


@contextmanager
def temp_file_manager(
    suffix: str = '',
    prefix: str = 'smarteye_',
    dir: Optional[str] = None,
    text: bool = False
) -> Generator[tempfile.NamedTemporaryFile, None, None]:
    """
    임시 파일 생성 및 자동 정리를 위한 Context Manager
    
    Args:
        suffix: 파일 확장자
        prefix: 파일명 접두사
        dir: 임시 파일 디렉토리
        text: 텍스트 모드 여부
        
    Yields:
        NamedTemporaryFile: 임시 파일 객체
        
    Example:
        with temp_file_manager('.txt', 'export_') as temp_file:
            temp_file.write(b'content')
            # 자동으로 파일이 정리됨
    """
    temp_file = None
    try:
        temp_file = tempfile.NamedTemporaryFile(
            suffix=suffix,
            prefix=prefix,
            dir=dir,
            delete=False,  # 수동으로 삭제할 예정
            mode='w+t' if text else 'w+b'
        )
        logger.debug(f"Created temporary file: {temp_file.name}")
        yield temp_file
        
    except Exception as e:
        logger.error(f"Error with temporary file: {e}")
        raise
        
    finally:
        if temp_file:
            try:
                temp_file.close()
                if os.path.exists(temp_file.name):
                    os.unlink(temp_file.name)
                    logger.debug(f"Cleaned up temporary file: {temp_file.name}")
            except OSError as e:
                logger.warning(f"Failed to cleanup temporary file {temp_file.name}: {e}")


@contextmanager
def managed_file_path(
    suffix: str = '',
    prefix: str = 'smarteye_',
    dir: Optional[str] = None
) -> Generator[str, None, None]:
    """
    임시 파일 경로만 필요한 경우를 위한 Context Manager
    
    Args:
        suffix: 파일 확장자
        prefix: 파일명 접두사
        dir: 임시 파일 디렉토리
        
    Yields:
        str: 임시 파일 경로
        
    Example:
        with managed_file_path('.pdf', 'report_') as file_path:
            generate_pdf(file_path)
            # 자동으로 파일이 정리됨
    """
    file_path = None
    try:
        # 임시 파일 생성 후 즉시 닫기 (경로만 사용)
        with tempfile.NamedTemporaryFile(
            suffix=suffix,
            prefix=prefix,
            dir=dir,
            delete=False
        ) as temp_file:
            file_path = temp_file.name
            
        logger.debug(f"Created temporary file path: {file_path}")
        yield file_path
        
    except Exception as e:
        logger.error(f"Error with temporary file path: {e}")
        raise
        
    finally:
        if file_path and os.path.exists(file_path):
            try:
                os.unlink(file_path)
                logger.debug(f"Cleaned up temporary file: {file_path}")
            except OSError as e:
                logger.warning(f"Failed to cleanup temporary file {file_path}: {e}")


class FileResourceManager:
    """
    여러 파일 리소스를 관리하는 클래스
    """
    
    def __init__(self):
        self._temp_files = []
        self._open_files = []
    
    def create_temp_file(self, suffix='', prefix='smarteye_', dir=None, text=False):
        """임시 파일 생성 및 추적"""
        temp_file = tempfile.NamedTemporaryFile(
            suffix=suffix,
            prefix=prefix,
            dir=dir,
            delete=False,
            mode='w+t' if text else 'w+b'
        )
        self._temp_files.append(temp_file.name)
        self._open_files.append(temp_file)
        logger.debug(f"Created tracked temporary file: {temp_file.name}")
        return temp_file
    
    def cleanup(self):
        """모든 리소스 정리"""
        # 열린 파일 닫기
        for file_obj in self._open_files:
            try:
                if not file_obj.closed:
                    file_obj.close()
            except Exception as e:
                logger.warning(f"Failed to close file: {e}")
        
        # 임시 파일 삭제
        for file_path in self._temp_files:
            try:
                if os.path.exists(file_path):
                    os.unlink(file_path)
                    logger.debug(f"Cleaned up tracked file: {file_path}")
            except OSError as e:
                logger.warning(f"Failed to cleanup file {file_path}: {e}")
        
        self._temp_files.clear()
        self._open_files.clear()
    
    def __del__(self):
        """소멸자에서 리소스 정리"""
        self.cleanup()
    
    def __enter__(self):
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        self.cleanup()


def ensure_directory_exists(directory: str) -> Path:
    """
    디렉토리가 존재하지 않으면 생성
    
    Args:
        directory: 디렉토리 경로
        
    Returns:
        Path: 디렉토리 경로 객체
    """
    path = Path(directory)
    path.mkdir(parents=True, exist_ok=True)
    return path


def safe_file_write(file_path: str, content: bytes, create_dirs: bool = True) -> bool:
    """
    안전한 파일 쓰기 (디렉토리 자동 생성, 예외 처리)
    
    Args:
        file_path: 파일 경로
        content: 파일 내용
        create_dirs: 디렉토리 자동 생성 여부
        
    Returns:
        bool: 성공 여부
    """
    try:
        path = Path(file_path)
        
        if create_dirs:
            path.parent.mkdir(parents=True, exist_ok=True)
        
        with open(file_path, 'wb') as f:
            f.write(content)
            
        logger.debug(f"Successfully wrote file: {file_path}")
        return True
        
    except Exception as e:
        logger.error(f"Failed to write file {file_path}: {e}")
        return False