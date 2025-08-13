"""
SmartEye API 응답 속도 최적화

캐싱, 비동기 처리, 응답 압축 등을 통한 성능 향상을 제공합니다.
"""

import hashlib
import json
import asyncio
# import aioredis  # 임시로 주석 처리 - Python 3.11 호환성 문제
from typing import Any, Dict, Optional, List, Callable
from functools import wraps
from datetime import datetime, timedelta
from django.core.cache import cache
from django.conf import settings
from django.http import JsonResponse
from django.utils.decorators import method_decorator
from rest_framework.response import Response
from rest_framework import status
import logging
import time
import gzip
import pickle
from concurrent.futures import ThreadPoolExecutor, as_completed

logger = logging.getLogger(__name__)


class ResponseCache:
    """응답 캐싱 시스템"""
    
    def __init__(self, default_timeout: int = 3600):
        self.default_timeout = default_timeout
        self.cache_prefix = "smarteye_api"
    
    def _generate_cache_key(self, endpoint: str, params: Dict[str, Any], user_id: str = None) -> str:
        """캐시 키 생성"""
        # 파라미터를 정렬하여 일관된 키 생성
        sorted_params = json.dumps(params, sort_keys=True, default=str)
        key_data = f"{endpoint}:{sorted_params}:{user_id or 'anonymous'}"
        
        # SHA256 해시로 키 길이 제한
        hash_key = hashlib.sha256(key_data.encode()).hexdigest()
        return f"{self.cache_prefix}:{hash_key}"
    
    def get(self, endpoint: str, params: Dict[str, Any], user_id: str = None) -> Optional[Any]:
        """캐시된 응답 조회"""
        cache_key = self._generate_cache_key(endpoint, params, user_id)
        
        try:
            cached_data = cache.get(cache_key)
            if cached_data:
                logger.debug(f"캐시 히트: {endpoint}")
                return cached_data
        except Exception as e:
            logger.warning(f"캐시 조회 실패: {e}")
        
        return None
    
    def set(self, endpoint: str, params: Dict[str, Any], response_data: Any, 
            timeout: int = None, user_id: str = None):
        """응답 캐싱"""
        cache_key = self._generate_cache_key(endpoint, params, user_id)
        timeout = timeout or self.default_timeout
        
        try:
            # 응답 데이터에 메타정보 추가
            cached_response = {
                'data': response_data,
                'cached_at': datetime.now().isoformat(),
                'expires_at': (datetime.now() + timedelta(seconds=timeout)).isoformat()
            }
            
            cache.set(cache_key, cached_response, timeout)
            logger.debug(f"응답 캐시 저장: {endpoint} (TTL: {timeout}초)")
            
        except Exception as e:
            logger.warning(f"캐시 저장 실패: {e}")
    
    def invalidate_pattern(self, pattern: str):
        """패턴 매칭으로 캐시 무효화"""
        try:
            # Django 캐시에서 패턴 매칭 무효화는 제한적
            # Redis를 직접 사용하는 것이 더 효율적
            logger.info(f"캐시 무효화 패턴: {pattern}")
            
        except Exception as e:
            logger.warning(f"캐시 무효화 실패: {e}")


class AsyncProcessor:
    """비동기 처리 관리자"""
    
    def __init__(self, max_workers: int = 4):
        self.max_workers = max_workers
        self.executor = ThreadPoolExecutor(max_workers=max_workers)
    
    async def process_batch_async(self, tasks: List[Callable], *args, **kwargs) -> List[Any]:
        """배치 작업 비동기 처리"""
        loop = asyncio.get_event_loop()
        
        # Future 객체 생성
        futures = []
        for task in tasks:
            future = loop.run_in_executor(self.executor, task, *args, **kwargs)
            futures.append(future)
        
        # 모든 작업 완료 대기
        results = await asyncio.gather(*futures, return_exceptions=True)
        
        # 결과 정리
        successful_results = []
        errors = []
        
        for i, result in enumerate(results):
            if isinstance(result, Exception):
                errors.append({'task_index': i, 'error': str(result)})
            else:
                successful_results.append(result)
        
        return {
            'successful_results': successful_results,
            'errors': errors,
            'success_rate': len(successful_results) / len(tasks) * 100
        }
    
    def process_parallel(self, tasks: List[Callable], timeout: int = 30) -> List[Any]:
        """병렬 처리 (동기 버전)"""
        results = []
        errors = []
        
        # 작업 제출
        future_to_task = {
            self.executor.submit(task): i 
            for i, task in enumerate(tasks)
        }
        
        # 결과 수집
        for future in as_completed(future_to_task, timeout=timeout):
            task_index = future_to_task[future]
            try:
                result = future.result()
                results.append({'task_index': task_index, 'result': result})
            except Exception as e:
                errors.append({'task_index': task_index, 'error': str(e)})
        
        return {
            'results': results,
            'errors': errors,
            'total_tasks': len(tasks)
        }
    
    def cleanup(self):
        """리소스 정리"""
        self.executor.shutdown(wait=True)


class ResponseCompressor:
    """응답 압축 관리자"""
    
    @staticmethod
    def compress_response(data: Any, compression_level: int = 6) -> bytes:
        """응답 데이터 압축"""
        try:
            # JSON 직렬화 후 압축
            json_data = json.dumps(data, ensure_ascii=False, default=str)
            compressed_data = gzip.compress(
                json_data.encode('utf-8'), 
                compresslevel=compression_level
            )
            
            compression_ratio = len(compressed_data) / len(json_data.encode('utf-8'))
            logger.debug(f"응답 압축률: {compression_ratio:.2%}")
            
            return compressed_data
            
        except Exception as e:
            logger.warning(f"응답 압축 실패: {e}")
            return json.dumps(data, ensure_ascii=False, default=str).encode('utf-8')
    
    @staticmethod
    def should_compress(request, response_size: int, min_size: int = 1024) -> bool:
        """압축 여부 판단"""
        # Accept-Encoding 헤더 확인
        accept_encoding = request.META.get('HTTP_ACCEPT_ENCODING', '')
        supports_gzip = 'gzip' in accept_encoding.lower()
        
        # 최소 크기 이상인지 확인
        size_threshold = response_size >= min_size
        
        return supports_gzip and size_threshold


class APIOptimizer:
    """API 최적화 통합 관리자"""
    
    def __init__(self):
        self.cache = ResponseCache()
        self.async_processor = AsyncProcessor()
        self.compressor = ResponseCompressor()
        self.metrics = {
            'cache_hits': 0,
            'cache_misses': 0,
            'compressed_responses': 0,
            'async_tasks_completed': 0
        }
    
    def get_cache_stats(self) -> Dict[str, Any]:
        """캐시 통계 반환"""
        total_requests = self.metrics['cache_hits'] + self.metrics['cache_misses']
        hit_rate = (
            self.metrics['cache_hits'] / total_requests * 100 
            if total_requests > 0 else 0
        )
        
        return {
            'cache_hit_rate': f"{hit_rate:.2f}%",
            'total_requests': total_requests,
            'cache_hits': self.metrics['cache_hits'],
            'cache_misses': self.metrics['cache_misses'],
            'compressed_responses': self.metrics['compressed_responses'],
            'async_tasks_completed': self.metrics['async_tasks_completed']
        }
    
    def cleanup(self):
        """리소스 정리"""
        self.async_processor.cleanup()


# 전역 인스턴스
api_optimizer = APIOptimizer()


def get_api_optimizer() -> APIOptimizer:
    """API 최적화 인스턴스 반환"""
    return api_optimizer


def cached_response(timeout: int = 3600, cache_by_user: bool = True):
    """응답 캐싱 데코레이터"""
    def decorator(view_func):
        @wraps(view_func)
        def wrapped_view(request, *args, **kwargs):
            optimizer = get_api_optimizer()
            
            # 캐시 키 생성을 위한 파라미터 수집
            cache_params = {
                'args': args,
                'kwargs': kwargs,
                'method': request.method,
                'query_params': dict(request.GET.items()) if hasattr(request, 'GET') else {},
                'body_params': dict(request.POST.items()) if hasattr(request, 'POST') else {}
            }
            
            # 사용자 ID 추출
            user_id = str(request.user.id) if cache_by_user and hasattr(request, 'user') and request.user.is_authenticated else None
            
            # 캐시된 응답 확인
            endpoint = f"{request.resolver_match.view_name if hasattr(request, 'resolver_match') else view_func.__name__}"
            cached_response = optimizer.cache.get(endpoint, cache_params, user_id)
            
            if cached_response:
                optimizer.metrics['cache_hits'] += 1
                return JsonResponse(cached_response['data'])
            
            # 캐시 미스 - 실제 뷰 함수 실행
            optimizer.metrics['cache_misses'] += 1
            response = view_func(request, *args, **kwargs)
            
            # 응답 캐싱 (성공적인 응답만)
            if hasattr(response, 'status_code') and 200 <= response.status_code < 300:
                response_data = response.data if hasattr(response, 'data') else response.content
                optimizer.cache.set(endpoint, cache_params, response_data, timeout, user_id)
            
            return response
        
        return wrapped_view
    return decorator


def async_batch_processing(batch_size: int = 5, timeout: int = 30):
    """비동기 배치 처리 데코레이터"""
    def decorator(view_func):
        @wraps(view_func)
        def wrapped_view(request, *args, **kwargs):
            optimizer = get_api_optimizer()
            
            # 배치 처리가 필요한 데이터 확인
            batch_data = request.data.get('batch_items', []) if hasattr(request, 'data') else []
            
            if not batch_data or len(batch_data) <= 1:
                # 단일 처리
                return view_func(request, *args, **kwargs)
            
            # 배치를 작은 단위로 분할
            batches = [
                batch_data[i:i + batch_size] 
                for i in range(0, len(batch_data), batch_size)
            ]
            
            # 배치별 처리 함수 생성
            def process_batch(batch_items):
                # 임시 요청 객체 생성하여 개별 처리
                temp_request = request
                temp_request.data = {'items': batch_items}
                return view_func(temp_request, *args, **kwargs)
            
            # 병렬 처리
            batch_tasks = [lambda b=batch: process_batch(b) for batch in batches]
            results = optimizer.async_processor.process_parallel(batch_tasks, timeout)
            
            optimizer.metrics['async_tasks_completed'] += len(batch_tasks)
            
            # 결과 통합
            combined_results = {
                'batch_results': results['results'],
                'errors': results['errors'],
                'total_batches': len(batches),
                'success_rate': len(results['results']) / len(batches) * 100
            }
            
            return Response(combined_results)
        
        return wrapped_view
    return decorator


def compress_large_responses(min_size: int = 1024):
    """대용량 응답 압축 데코레이터"""
    def decorator(view_func):
        @wraps(view_func)
        def wrapped_view(request, *args, **kwargs):
            response = view_func(request, *args, **kwargs)
            optimizer = get_api_optimizer()
            
            # 응답 크기 확인
            response_data = response.data if hasattr(response, 'data') else response.content
            response_size = len(str(response_data))
            
            # 압축 필요성 판단
            if optimizer.compressor.should_compress(request, response_size, min_size):
                try:
                    compressed_data = optimizer.compressor.compress_response(response_data)
                    
                    # 압축된 응답 반환
                    from django.http import HttpResponse
                    compressed_response = HttpResponse(
                        compressed_data,
                        content_type='application/json'
                    )
                    compressed_response['Content-Encoding'] = 'gzip'
                    compressed_response['Content-Length'] = len(compressed_data)
                    
                    optimizer.metrics['compressed_responses'] += 1
                    return compressed_response
                    
                except Exception as e:
                    logger.warning(f"응답 압축 실패, 원본 반환: {e}")
            
            return response
        
        return wrapped_view
    return decorator


def performance_monitoring(include_metrics: bool = True):
    """성능 모니터링 데코레이터"""
    def decorator(view_func):
        @wraps(view_func)
        def wrapped_view(request, *args, **kwargs):
            start_time = time.time()
            
            try:
                response = view_func(request, *args, **kwargs)
                
                # 성능 메트릭 추가
                if include_metrics and hasattr(response, 'data'):
                    processing_time = (time.time() - start_time) * 1000
                    
                    # 응답에 메트릭 정보 추가
                    if isinstance(response.data, dict):
                        response.data['_performance'] = {
                            'processing_time_ms': round(processing_time, 2),
                            'timestamp': datetime.now().isoformat(),
                            'cache_stats': get_api_optimizer().get_cache_stats()
                        }
                
                return response
                
            except Exception as e:
                processing_time = (time.time() - start_time) * 1000
                logger.error(f"API 처리 실패 ({processing_time:.2f}ms): {e}")
                raise
        
        return wrapped_view
    return decorator


# 통합 최적화 데코레이터
def optimize_api(cache_timeout: int = 3600, enable_compression: bool = True, 
                enable_async: bool = False, batch_size: int = 5):
    """통합 API 최적화 데코레이터"""
    def decorator(view_func):
        optimized_func = view_func
        
        # 성능 모니터링 적용
        optimized_func = performance_monitoring()(optimized_func)
        
        # 캐싱 적용
        if cache_timeout > 0:
            optimized_func = cached_response(cache_timeout)(optimized_func)
        
        # 압축 적용
        if enable_compression:
            optimized_func = compress_large_responses()(optimized_func)
        
        # 비동기 배치 처리 적용
        if enable_async:
            optimized_func = async_batch_processing(batch_size)(optimized_func)
        
        return optimized_func
    
    return decorator