"""
SmartEye 보안 강화 유틸리티

API 키 암호화, Rate Limiting, 입력 검증, 로그 마스킹 등을 제공합니다.
"""

import os
import hashlib
import hmac
import base64
import re
import json
import time
from typing import Dict, Any, Optional, List
from datetime import datetime, timedelta
from functools import wraps
from cryptography.fernet import Fernet
from django.core.cache import cache
from django.conf import settings
from django.http import JsonResponse
from rest_framework import status
from rest_framework.response import Response
import logging

logger = logging.getLogger(__name__)


class SecureKeyManager:
    """보안 키 관리자"""
    
    def __init__(self):
        # 암호화 키 생성/로드
        self.encryption_key = self._get_or_create_encryption_key()
        self.cipher_suite = Fernet(self.encryption_key)
    
    def _get_or_create_encryption_key(self) -> bytes:
        """암호화 키 생성 또는 로드"""
        # 환경변수에서 키 읽기 시도
        env_key = os.environ.get('FERNET_ENCRYPTION_KEY')
        if env_key:
            try:
                return base64.urlsafe_b64decode(env_key.encode())
            except Exception as e:
                logger.warning(f"환경변수의 암호화 키 처리 실패: {e}")
        
        # 키 파일 경로 (컨테이너에서 안전한 위치)
        key_file = '/app/cache/encryption.key'
        
        try:
            # 기존 키 파일 확인
            if os.path.exists(key_file):
                with open(key_file, 'rb') as f:
                    return f.read()
            else:
                # 새 키 생성
                key = Fernet.generate_key()
                
                # 키 파일 디렉터리 생성 (cache 디렉터리는 이미 존재)
                os.makedirs(os.path.dirname(key_file), exist_ok=True)
                
                # 키 파일 저장
                try:
                    with open(key_file, 'wb') as f:
                        f.write(key)
                    # 파일 권한 설정 (소유자만 읽기)
                    os.chmod(key_file, 0o600)
                    logger.info("새로운 암호화 키 생성됨")
                except PermissionError:
                    logger.warning("키 파일 저장 실패, 메모리에서만 사용")
                
                return key
                
        except Exception as e:
            logger.error(f"암호화 키 처리 실패: {e}")
            # 임시 키 생성 (프로덕션에서는 안전하지 않음)
            logger.warning("임시 암호화 키 사용 중 - 프로덕션에서는 FERNET_ENCRYPTION_KEY 환경변수를 설정하세요")
            return Fernet.generate_key()
    
    def encrypt_api_key(self, api_key: str) -> str:
        """API 키 암호화"""
        try:
            encrypted_key = self.cipher_suite.encrypt(api_key.encode())
            return base64.b64encode(encrypted_key).decode()
        except Exception as e:
            logger.error(f"API 키 암호화 실패: {e}")
            return api_key  # 실패 시 원본 반환 (위험)
    
    def decrypt_api_key(self, encrypted_key: str) -> str:
        """API 키 복호화"""
        try:
            encrypted_data = base64.b64decode(encrypted_key.encode())
            decrypted_key = self.cipher_suite.decrypt(encrypted_data)
            return decrypted_key.decode()
        except Exception as e:
            logger.error(f"API 키 복호화 실패: {e}")
            return encrypted_key  # 실패 시 원본 반환
    
    def hash_sensitive_data(self, data: str, salt: str = None) -> str:
        """민감한 데이터 해싱"""
        if salt is None:
            salt = os.urandom(32)
        else:
            salt = salt.encode() if isinstance(salt, str) else salt
        
        # PBKDF2 해싱
        hashed = hashlib.pbkdf2_hmac('sha256', data.encode(), salt, 100000)
        return base64.b64encode(salt + hashed).decode()
    
    def verify_hashed_data(self, data: str, hashed_data: str) -> bool:
        """해싱된 데이터 검증"""
        try:
            decoded = base64.b64decode(hashed_data.encode())
            salt = decoded[:32]
            stored_hash = decoded[32:]
            
            new_hash = hashlib.pbkdf2_hmac('sha256', data.encode(), salt, 100000)
            return hmac.compare_digest(stored_hash, new_hash)
        except Exception:
            return False


class RateLimiter:
    """API Rate Limiting"""
    
    def __init__(self):
        self.default_limits = {
            'upload': {'requests': 10, 'window': 3600},  # 시간당 10개
            'api_calls': {'requests': 100, 'window': 3600},  # 시간당 100개
            'auth': {'requests': 5, 'window': 900}  # 15분당 5개
        }
    
    def _get_cache_key(self, identifier: str, endpoint_type: str) -> str:
        """캐시 키 생성"""
        return f"rate_limit:{endpoint_type}:{identifier}"
    
    def check_rate_limit(self, identifier: str, endpoint_type: str = 'api_calls') -> Dict[str, Any]:
        """Rate Limit 확인"""
        limits = self.default_limits.get(endpoint_type, self.default_limits['api_calls'])
        cache_key = self._get_cache_key(identifier, endpoint_type)
        
        # 현재 요청 수 확인
        current_requests = cache.get(cache_key, 0)
        
        # 제한 확인
        if current_requests >= limits['requests']:
            return {
                'allowed': False,
                'limit': limits['requests'],
                'remaining': 0,
                'reset_time': time.time() + limits['window']
            }
        
        # 요청 수 증가
        cache.set(cache_key, current_requests + 1, limits['window'])
        
        return {
            'allowed': True,
            'limit': limits['requests'],
            'remaining': limits['requests'] - (current_requests + 1),
            'reset_time': time.time() + limits['window']
        }
    
    def get_rate_limit_headers(self, rate_limit_info: Dict[str, Any]) -> Dict[str, str]:
        """Rate Limit 헤더 생성"""
        return {
            'X-RateLimit-Limit': str(rate_limit_info['limit']),
            'X-RateLimit-Remaining': str(rate_limit_info['remaining']),
            'X-RateLimit-Reset': str(int(rate_limit_info['reset_time']))
        }


class InputValidator:
    """입력 데이터 검증"""
    
    def __init__(self):
        # 위험한 패턴 정의
        self.dangerous_patterns = [
            r'<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>',  # Script 태그
            r'javascript:',  # JavaScript 프로토콜
            r'on\w+\s*=',  # 이벤트 핸들러
            r'expression\s*\(',  # CSS expression
            r'@import',  # CSS import
            r'<iframe\b',  # iframe 태그
            r'<object\b',  # object 태그
            r'<embed\b',  # embed 태그
        ]
        
        # SQL 인젝션 패턴
        self.sql_patterns = [
            r'(\b(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|EXEC|UNION)\b)',
            r'(\b(OR|AND)\s+\d+\s*=\s*\d+)',
            r'(\bOR\s+\'.*\'=\'.*\')',
            r'(\b1\s*=\s*1)',
            r'(--|\#|\/\*)',
        ]
    
    def validate_text_input(self, text: str, max_length: int = 10000) -> Dict[str, Any]:
        """텍스트 입력 검증"""
        if not text:
            return {'valid': True, 'cleaned': text}
        
        # 길이 검증
        if len(text) > max_length:
            return {
                'valid': False,
                'error': f'입력 텍스트가 너무 깁니다 (최대 {max_length}자)',
                'cleaned': text[:max_length]
            }
        
        # XSS 패턴 검사
        for pattern in self.dangerous_patterns:
            if re.search(pattern, text, re.IGNORECASE):
                return {
                    'valid': False,
                    'error': '위험한 스크립트 패턴이 감지되었습니다',
                    'cleaned': self._clean_dangerous_content(text)
                }
        
        # SQL 인젝션 패턴 검사
        for pattern in self.sql_patterns:
            if re.search(pattern, text, re.IGNORECASE):
                return {
                    'valid': False,
                    'error': 'SQL 인젝션 패턴이 감지되었습니다',
                    'cleaned': self._clean_sql_content(text)
                }
        
        return {'valid': True, 'cleaned': text}
    
    def _clean_dangerous_content(self, text: str) -> str:
        """위험한 콘텐츠 제거"""
        cleaned_text = text
        for pattern in self.dangerous_patterns:
            cleaned_text = re.sub(pattern, '[REMOVED]', cleaned_text, flags=re.IGNORECASE)
        return cleaned_text
    
    def _clean_sql_content(self, text: str) -> str:
        """SQL 관련 콘텐츠 제거"""
        cleaned_text = text
        for pattern in self.sql_patterns:
            cleaned_text = re.sub(pattern, '[FILTERED]', cleaned_text, flags=re.IGNORECASE)
        return cleaned_text
    
    def validate_file_upload(self, file) -> Dict[str, Any]:
        """파일 업로드 검증"""
        # 파일 크기 검증
        max_size = 50 * 1024 * 1024  # 50MB
        if file.size > max_size:
            return {
                'valid': False,
                'error': f'파일 크기가 너무 큽니다 (최대 {max_size // (1024*1024)}MB)'
            }
        
        # 파일 확장자 검증
        allowed_extensions = ['.jpg', '.jpeg', '.png', '.pdf', '.bmp', '.tiff']
        file_ext = '.' + file.name.lower().split('.')[-1] if '.' in file.name else ''
        
        if file_ext not in allowed_extensions:
            return {
                'valid': False,
                'error': f'지원하지 않는 파일 형식입니다: {file_ext}'
            }
        
        # MIME 타입 검증
        allowed_mime_types = [
            'image/jpeg', 'image/png', 'image/bmp', 'image/tiff',
            'application/pdf'
        ]
        
        if hasattr(file, 'content_type') and file.content_type not in allowed_mime_types:
            return {
                'valid': False,
                'error': f'유효하지 않은 MIME 타입입니다: {file.content_type}'
            }
        
        return {'valid': True}


class LogMasker:
    """로그 민감정보 마스킹"""
    
    def __init__(self):
        # 민감한 정보 패턴
        self.sensitive_patterns = {
            'api_key': r'(api[_-]?key|token|secret)["\s]*[:=]["\s]*([a-zA-Z0-9+/=]{20,})',
            'password': r'(password|pass|pwd)["\s]*[:=]["\s]*([^\s"\']{6,})',
            'email': r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b',
            'phone': r'(\+?1?[-.\s]?\(?[0-9]{3}\)?[-.\s]?[0-9]{3}[-.\s]?[0-9]{4})',
            'credit_card': r'\b(?:\d{4}[-\s]?){3}\d{4}\b'
        }
    
    def mask_sensitive_data(self, text: str) -> str:
        """민감한 데이터 마스킹"""
        masked_text = text
        
        for pattern_name, pattern in self.sensitive_patterns.items():
            if pattern_name in ['api_key', 'password']:
                # 키-값 쌍 마스킹
                masked_text = re.sub(
                    pattern,
                    lambda m: f'{m.group(1)}": "***MASKED***"',
                    masked_text,
                    flags=re.IGNORECASE
                )
            else:
                # 전체 값 마스킹
                masked_text = re.sub(
                    pattern,
                    lambda m: '*' * len(m.group(0)),
                    masked_text,
                    flags=re.IGNORECASE
                )
        
        return masked_text


class SecurityManager:
    """보안 관리 통합 클래스"""
    
    def __init__(self):
        self.key_manager = SecureKeyManager()
        self.rate_limiter = RateLimiter()
        self.input_validator = InputValidator()
        self.log_masker = LogMasker()
    
    def get_secure_openai_key(self) -> str:
        """안전한 OpenAI API 키 반환"""
        encrypted_key = os.getenv('ENCRYPTED_OPENAI_API_KEY')
        if encrypted_key:
            return self.key_manager.decrypt_api_key(encrypted_key)
        
        # 암호화되지 않은 키 처리 (기존 호환성)
        plain_key = os.getenv('OPENAI_API_KEY')
        if plain_key:
            # 암호화하여 저장
            encrypted = self.key_manager.encrypt_api_key(plain_key)
            logger.warning("암호화되지 않은 API 키 감지, 암호화 권장")
            return plain_key
        
        return None
    
    def create_secure_log_entry(self, message: str, level: str = 'info') -> str:
        """안전한 로그 엔트리 생성"""
        masked_message = self.log_masker.mask_sensitive_data(message)
        timestamp = datetime.now().isoformat()
        
        return f"[{timestamp}] [{level.upper()}] {masked_message}"


# 전역 인스턴스
security_manager = SecurityManager()


def get_security_manager() -> SecurityManager:
    """보안 관리자 인스턴스 반환"""
    return security_manager


def rate_limit(endpoint_type: str = 'api_calls'):
    """Rate Limiting 데코레이터"""
    def decorator(view_func):
        @wraps(view_func)
        def wrapped_view(request, *args, **kwargs):
            # 사용자 식별자 생성
            identifier = str(request.user.id) if hasattr(request, 'user') and request.user.is_authenticated else request.META.get('REMOTE_ADDR', 'anonymous')
            
            # Rate Limit 확인
            rate_limit_info = security_manager.rate_limiter.check_rate_limit(identifier, endpoint_type)
            
            if not rate_limit_info['allowed']:
                headers = security_manager.rate_limiter.get_rate_limit_headers(rate_limit_info)
                response = JsonResponse({
                    'error': 'Rate limit exceeded',
                    'retry_after': int(rate_limit_info['reset_time'] - time.time())
                }, status=429)
                
                for key, value in headers.items():
                    response[key] = value
                
                return response
            
            # 정상 처리
            response = view_func(request, *args, **kwargs)
            
            # Rate Limit 헤더 추가
            if hasattr(response, '__setitem__'):
                headers = security_manager.rate_limiter.get_rate_limit_headers(rate_limit_info)
                for key, value in headers.items():
                    response[key] = value
            
            return response
        
        return wrapped_view
    return decorator


def validate_input(max_length: int = 10000):
    """입력 검증 데코레이터"""
    def decorator(view_func):
        @wraps(view_func)
        def wrapped_view(request, *args, **kwargs):
            # POST 데이터 검증
            if hasattr(request, 'data') and request.data:
                for key, value in request.data.items():
                    if isinstance(value, str):
                        validation_result = security_manager.input_validator.validate_text_input(value, max_length)
                        if not validation_result['valid']:
                            return JsonResponse({
                                'error': f'Invalid input for {key}: {validation_result["error"]}'
                            }, status=400)
            
            # 파일 업로드 검증
            if hasattr(request, 'FILES') and request.FILES:
                for file in request.FILES.values():
                    validation_result = security_manager.input_validator.validate_file_upload(file)
                    if not validation_result['valid']:
                        return JsonResponse({
                            'error': validation_result['error']
                        }, status=400)
            
            return view_func(request, *args, **kwargs)
        
        return wrapped_view
    return decorator


def secure_api(endpoint_type: str = 'api_calls', max_input_length: int = 10000):
    """통합 보안 데코레이터"""
    def decorator(view_func):
        @rate_limit(endpoint_type)
        @validate_input(max_input_length)
        @wraps(view_func)
        def wrapped_view(request, *args, **kwargs):
            return view_func(request, *args, **kwargs)
        
        return wrapped_view
    return decorator