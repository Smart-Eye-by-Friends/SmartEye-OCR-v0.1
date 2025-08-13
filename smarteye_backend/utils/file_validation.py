"""
File Validation Utilities

파일 검증 관련 유틸리티 모듈
"""

import logging
from dataclasses import dataclass
from typing import List, Union
from rest_framework import status
from rest_framework.response import Response

logger = logging.getLogger(__name__)


@dataclass
class FileValidationConfig:
    """파일 검증 설정 상수"""
    SUPPORTED_FORMATS = ['.jpg', '.jpeg', '.png', '.pdf', '.bmp', '.tiff']
    MAX_FILE_SIZE_MB = 50
    DEFAULT_IMAGE_SIZE = (1920, 1080)
    
    # 파일 타입별 추가 제한
    IMAGE_MAX_DIMENSION = 8192  # 최대 8K 해상도
    PDF_MAX_PAGES = 500  # PDF 최대 페이지 수


class FileValidator:
    """파일 검증 유틸리티 클래스"""
    
    def __init__(self, config: FileValidationConfig = None):
        self.config = config or FileValidationConfig()
        self.logger = logger
    
    def validate_file_format(self, filename: str) -> bool:
        """파일 형식 검증"""
        if not filename or '.' not in filename:
            return False
        
        file_ext = '.' + filename.lower().split('.')[-1]
        return file_ext in self.config.SUPPORTED_FORMATS
    
    def validate_file_size(self, file_size_bytes: int) -> bool:
        """파일 크기 검증"""
        file_size_mb = file_size_bytes / (1024 * 1024)
        return file_size_mb <= self.config.MAX_FILE_SIZE_MB
    
    def validate_image_dimensions(self, width: int, height: int) -> bool:
        """이미지 크기 검증"""
        max_dim = self.config.IMAGE_MAX_DIMENSION
        return width <= max_dim and height <= max_dim
    
    def get_file_type(self, filename: str) -> str:
        """파일 타입 반환"""
        if not filename or '.' not in filename:
            return 'unknown'
        
        file_ext = filename.lower().split('.')[-1]
        
        image_formats = ['jpg', 'jpeg', 'png', 'bmp', 'tiff']
        if file_ext in image_formats:
            return 'image'
        elif file_ext == 'pdf':
            return 'pdf'
        else:
            return 'unknown'
    
    def validate_multiple_files(self, files: List) -> Union[List, Response]:
        """여러 파일 일괄 검증"""
        if not files:
            return self.get_validation_error_response('', 'no_files')
        
        for file in files:
            if not self.validate_file_format(file.name):
                return self.get_validation_error_response(file.name, 'format')
            
            if not self.validate_file_size(file.size):
                return self.get_validation_error_response(file.name, 'size')
        
        return files
    
    def get_validation_error_response(self, filename: str, issue_type: str) -> Response:
        """검증 오류 응답 생성"""
        error_messages = {
            'no_files': '업로드할 파일이 없습니다.',
            'format': f'지원하지 않는 파일 형식입니다: {filename}. 지원 형식: {", ".join(self.config.SUPPORTED_FORMATS)}',
            'size': f'파일 크기가 너무 큽니다: {filename} ({self._get_file_size_mb(filename)}MB). 최대 {self.config.MAX_FILE_SIZE_MB}MB까지 지원합니다.',
            'dimensions': f'이미지 크기가 너무 큽니다: {filename}. 최대 {self.config.IMAGE_MAX_DIMENSION}x{self.config.IMAGE_MAX_DIMENSION}까지 지원합니다.',
            'corrupted': f'파일이 손상되었거나 읽을 수 없습니다: {filename}'
        }
        
        error_message = error_messages.get(issue_type, f'파일 검증 실패: {filename}')
        
        self.logger.warning(f"File validation failed: {error_message}")
        
        return Response(
            {'error': error_message},
            status=status.HTTP_400_BAD_REQUEST
        )
    
    def _get_file_size_mb(self, filename: str) -> str:
        """파일 크기를 MB 단위로 반환 (표시용)"""
        # 실제로는 파일 객체에서 크기를 가져와야 함
        return "N/A"
    
    def get_validation_summary(self, files: List) -> dict:
        """파일 검증 요약 정보 반환"""
        total_files = len(files)
        total_size_mb = sum(file.size for file in files) / (1024 * 1024)
        
        file_types = {}
        for file in files:
            file_type = self.get_file_type(file.name)
            file_types[file_type] = file_types.get(file_type, 0) + 1
        
        return {
            'total_files': total_files,
            'total_size_mb': round(total_size_mb, 2),
            'file_types': file_types,
            'max_allowed_size_mb': self.config.MAX_FILE_SIZE_MB,
            'supported_formats': self.config.SUPPORTED_FORMATS
        }


class FileTypeDetector:
    """파일 타입 감지 유틸리티"""
    
    @staticmethod
    def is_pdf(filename: str) -> bool:
        """PDF 파일 여부 확인"""
        return filename.lower().endswith('.pdf')
    
    @staticmethod
    def is_image(filename: str) -> bool:
        """이미지 파일 여부 확인"""
        image_extensions = ['.jpg', '.jpeg', '.png', '.bmp', '.tiff']
        file_ext = '.' + filename.lower().split('.')[-1] if '.' in filename else ''
        return file_ext in image_extensions
    
    @staticmethod
    def get_mime_type(filename: str) -> str:
        """MIME 타입 반환"""
        mime_types = {
            '.jpg': 'image/jpeg',
            '.jpeg': 'image/jpeg',
            '.png': 'image/png',
            '.bmp': 'image/bmp',
            '.tiff': 'image/tiff',
            '.pdf': 'application/pdf'
        }
        
        file_ext = '.' + filename.lower().split('.')[-1] if '.' in filename else ''
        return mime_types.get(file_ext, 'application/octet-stream')


class SecurityFileValidator:
    """보안 중심 파일 검증"""
    
    DANGEROUS_EXTENSIONS = ['.exe', '.bat', '.cmd', '.scr', '.com', '.pif', '.vbs', '.js', '.jar']
    MAX_FILENAME_LENGTH = 255
    
    @staticmethod
    def is_safe_filename(filename: str) -> bool:
        """안전한 파일명인지 검증"""
        if not filename or len(filename) > SecurityFileValidator.MAX_FILENAME_LENGTH:
            return False
        
        # 위험한 확장자 검사
        file_ext = '.' + filename.lower().split('.')[-1] if '.' in filename else ''
        if file_ext in SecurityFileValidator.DANGEROUS_EXTENSIONS:
            return False
        
        # 특수 문자 검사 (기본적인 수준)
        dangerous_chars = ['..', '/', '\\', ':', '*', '?', '"', '<', '>', '|']
        return not any(char in filename for char in dangerous_chars)
    
    @staticmethod
    def sanitize_filename(filename: str) -> str:
        """파일명 안전화"""
        if not filename:
            return 'unnamed_file'
        
        # 특수 문자 제거/치환
        safe_chars = []
        for char in filename:
            if char.isalnum() or char in '.-_':
                safe_chars.append(char)
            else:
                safe_chars.append('_')
        
        sanitized = ''.join(safe_chars)
        
        # 길이 제한
        if len(sanitized) > SecurityFileValidator.MAX_FILENAME_LENGTH:
            name_part, ext_part = sanitized.rsplit('.', 1) if '.' in sanitized else (sanitized, '')
            max_name_length = SecurityFileValidator.MAX_FILENAME_LENGTH - len(ext_part) - 1
            sanitized = name_part[:max_name_length] + ('.' + ext_part if ext_part else '')
        
        return sanitized