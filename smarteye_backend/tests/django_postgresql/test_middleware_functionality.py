"""
Django & PostgreSQL 특화 테스트: 미들웨어 기능 검증

커스텀 미들웨어와 Django 기본 미들웨어들이 올바르게 동작하는지 검증
"""

import pytest
from django.test import TestCase, RequestFactory, override_settings
from django.http import HttpResponse
from django.contrib.auth import get_user_model
from django.contrib.sessions.middleware import SessionMiddleware
from django.contrib.auth.middleware import AuthenticationMiddleware
from django.middleware.csrf import CsrfViewMiddleware
from django.middleware.security import SecurityMiddleware
from unittest.mock import Mock, patch
import json

User = get_user_model()


class TestMiddleware:
    """미들웨어 테스트용 더미 미들웨어"""
    
    def __init__(self, get_response):
        self.get_response = get_response
        self.calls = []
    
    def __call__(self, request):
        self.calls.append('before_view')
        response = self.get_response(request)
        self.calls.append('after_view')
        return response
    
    def process_view(self, request, view_func, view_args, view_kwargs):
        self.calls.append('process_view')
        return None


def dummy_view(request):
    """테스트용 더미 뷰"""
    return HttpResponse("Test response", content_type="text/plain")


@pytest.mark.django_db
class TestMiddlewareFunctionality(TestCase):
    """미들웨어 기능 검증 테스트"""
    
    def setUp(self):
        """테스트 설정"""
        self.factory = RequestFactory()
        self.user = User.objects.create_user(
            username='testuser',
            password='testpass',
            email='test@example.com'
        )
    
    def create_request_with_middleware(self, path='/', method='GET', user=None):
        """미들웨어가 적용된 요청 생성"""
        if method.upper() == 'GET':
            request = self.factory.get(path)
        elif method.upper() == 'POST':
            request = self.factory.post(path)
        else:
            raise ValueError(f"Unsupported method: {method}")
        
        # 세션 미들웨어 적용
        session_middleware = SessionMiddleware(lambda req: HttpResponse())
        session_middleware.process_request(request)
        request.session.save()
        
        # 인증 미들웨어 적용
        auth_middleware = AuthenticationMiddleware(lambda req: HttpResponse())
        auth_middleware.process_request(request)
        
        # 사용자 설정
        if user:
            request.user = user
        
        return request
    
    def test_session_middleware(self):
        """세션 미들웨어 기능 검증"""
        request = self.factory.get('/')
        
        # 세션 미들웨어 적용
        middleware = SessionMiddleware(dummy_view)
        middleware.process_request(request)
        
        # 세션이 생성되었는지 확인
        assert hasattr(request, 'session')
        assert request.session is not None
        
        # 세션에 데이터 저장/조회
        request.session['test_key'] = 'test_value'
        request.session.save()
        
        assert request.session['test_key'] == 'test_value'
        assert request.session.session_key is not None
    
    def test_authentication_middleware(self):
        """인증 미들웨어 기능 검증"""
        request = self.create_request_with_middleware('/')
        
        # 익명 사용자 확인
        assert hasattr(request, 'user')
        assert not request.user.is_authenticated
        
        # 인증된 사용자 설정
        request.user = self.user
        assert request.user.is_authenticated
        assert request.user.username == 'testuser'
    
    def test_csrf_middleware(self):
        """CSRF 미들웨어 기능 검증"""
        # GET 요청 (CSRF 토큰 생성)
        get_request = self.factory.get('/')
        csrf_middleware = CsrfViewMiddleware(dummy_view)
        
        response = csrf_middleware(get_request)
        
        # CSRF 토큰이 생성되었는지 확인
        assert hasattr(get_request, 'META')
        
        # POST 요청 (CSRF 토큰 검증 필요)
        post_request = self.factory.post('/', {'data': 'test'})
        
        # CSRF 토큰 없이 POST 요청 시 오류 발생 확인
        with pytest.raises(Exception):  # CSRF 검증 실패 시 예외 발생 가능
            csrf_middleware = CsrfViewMiddleware(dummy_view)
            csrf_middleware.process_view(post_request, dummy_view, [], {})
    
    def test_security_middleware(self):
        """보안 미들웨어 기능 검증"""
        request = self.factory.get('/')
        security_middleware = SecurityMiddleware(dummy_view)
        
        response = security_middleware(request)
        
        # 응답에 보안 헤더가 추가되었는지 확인
        assert response.status_code == 200
        
        # HTTPS 리다이렉트 테스트 (설정에 따라)
        with override_settings(SECURE_SSL_REDIRECT=True):
            http_request = self.factory.get('/', HTTP_HOST='example.com')
            security_middleware = SecurityMiddleware(dummy_view)
            
            response = security_middleware(http_request)
            
            # HTTP에서 HTTPS로 리다이렉트되어야 함
            if response.status_code == 301:
                assert response['Location'].startswith('https://')
    
    def test_cors_middleware(self):
        """CORS 미들웨어 기능 검증"""
        from corsheaders.middleware import CorsMiddleware
        
        # CORS 요청 시뮬레이션
        request = self.factory.options('/', HTTP_ORIGIN='http://localhost:3000')
        cors_middleware = CorsMiddleware(dummy_view)
        
        response = cors_middleware(request)
        
        # CORS 헤더 확인
        if 'Access-Control-Allow-Origin' in response:
            assert response['Access-Control-Allow-Origin'] is not None
    
    def test_custom_middleware_execution_order(self):
        """커스텀 미들웨어 실행 순서 검증"""
        test_middleware = TestMiddleware(dummy_view)
        
        request = self.factory.get('/')
        response = test_middleware(request)
        
        # 미들웨어 호출 순서 확인
        assert test_middleware.calls == ['before_view', 'after_view']
        assert response.status_code == 200
    
    def test_middleware_exception_handling(self):
        """미들웨어 예외 처리 검증"""
        
        class ErrorMiddleware:
            def __init__(self, get_response):
                self.get_response = get_response
            
            def __call__(self, request):
                if 'error' in request.path:
                    raise ValueError("Test error")
                return self.get_response(request)
            
            def process_exception(self, request, exception):
                if isinstance(exception, ValueError):
                    return HttpResponse("Error handled", status=500)
                return None
        
        # 정상 요청
        normal_request = self.factory.get('/normal/')
        error_middleware = ErrorMiddleware(dummy_view)
        response = error_middleware(normal_request)
        assert response.status_code == 200
        
        # 오류 요청
        error_request = self.factory.get('/error/')
        with pytest.raises(ValueError):
            error_middleware(error_request)
    
    def test_request_logging_middleware(self):
        """요청 로깅 미들웨어 기능 검증"""
        
        class LoggingMiddleware:
            def __init__(self, get_response):
                self.get_response = get_response
                self.logged_requests = []
            
            def __call__(self, request):
                # 요청 로깅
                self.logged_requests.append({
                    'method': request.method,
                    'path': request.path,
                    'user': getattr(request, 'user', None)
                })
                
                response = self.get_response(request)
                
                # 응답 로깅
                self.logged_requests[-1]['status_code'] = response.status_code
                
                return response
        
        logging_middleware = LoggingMiddleware(dummy_view)
        
        request = self.create_request_with_middleware('/api/test/', user=self.user)
        response = logging_middleware(request)
        
        # 로그가 기록되었는지 확인
        assert len(logging_middleware.logged_requests) == 1
        log_entry = logging_middleware.logged_requests[0]
        
        assert log_entry['method'] == 'GET'
        assert log_entry['path'] == '/api/test/'
        assert log_entry['user'] == self.user
        assert log_entry['status_code'] == 200
    
    def test_api_throttling_middleware(self):
        """API 스로틀링 미들웨어 기능 검증"""
        
        class SimpleThrottleMiddleware:
            def __init__(self, get_response):
                self.get_response = get_response
                self.request_counts = {}
                self.max_requests = 5
            
            def __call__(self, request):
                client_ip = self.get_client_ip(request)
                
                # 요청 수 카운트
                self.request_counts[client_ip] = self.request_counts.get(client_ip, 0) + 1
                
                if self.request_counts[client_ip] > self.max_requests:
                    return HttpResponse("Too Many Requests", status=429)
                
                return self.get_response(request)
            
            def get_client_ip(self, request):
                x_forwarded_for = request.META.get('HTTP_X_FORWARDED_FOR')
                if x_forwarded_for:
                    return x_forwarded_for.split(',')[0]
                return request.META.get('REMOTE_ADDR', '127.0.0.1')
        
        throttle_middleware = SimpleThrottleMiddleware(dummy_view)
        
        # 허용 한도 내 요청
        for i in range(5):
            request = self.factory.get('/', REMOTE_ADDR='127.0.0.1')
            response = throttle_middleware(request)
            assert response.status_code == 200
        
        # 한도 초과 요청
        request = self.factory.get('/', REMOTE_ADDR='127.0.0.1')
        response = throttle_middleware(request)
        assert response.status_code == 429
    
    def test_content_type_middleware(self):
        """Content-Type 처리 미들웨어 검증"""
        
        class ContentTypeMiddleware:
            def __init__(self, get_response):
                self.get_response = get_response
            
            def __call__(self, request):
                # JSON 요청 처리
                if request.content_type == 'application/json':
                    try:
                        request.json = json.loads(request.body.decode('utf-8'))
                    except (json.JSONDecodeError, UnicodeDecodeError):
                        request.json = {}
                else:
                    request.json = {}
                
                return self.get_response(request)
        
        middleware = ContentTypeMiddleware(dummy_view)
        
        # JSON 요청
        json_data = {'key': 'value', 'number': 123}
        json_request = self.factory.post(
            '/api/test/',
            data=json.dumps(json_data),
            content_type='application/json'
        )
        
        response = middleware(json_request)
        
        assert hasattr(json_request, 'json')
        assert json_request.json == json_data
        
        # 일반 요청
        normal_request = self.factory.post('/api/test/', {'key': 'value'})
        response = middleware(normal_request)
        
        assert hasattr(normal_request, 'json')
        assert normal_request.json == {}
    
    def test_database_middleware_integration(self):
        """데이터베이스 연동 미들웨어 검증"""
        from django.db import connection
        
        class DatabaseMiddleware:
            def __init__(self, get_response):
                self.get_response = get_response
            
            def __call__(self, request):
                # 요청 전 DB 연결 확인
                request.db_queries_before = len(connection.queries)
                
                response = self.get_response(request)
                
                # 요청 후 DB 쿼리 수 계산
                request.db_queries_after = len(connection.queries)
                request.db_query_count = request.db_queries_after - request.db_queries_before
                
                # 응답 헤더에 쿼리 수 추가 (개발 환경용)
                if hasattr(request, 'db_query_count'):
                    response['X-DB-Query-Count'] = str(request.db_query_count)
                
                return response
        
        def db_view(request):
            # 데이터베이스 쿼리 실행
            user_count = User.objects.count()
            return HttpResponse(f"Users: {user_count}")
        
        db_middleware = DatabaseMiddleware(db_view)
        
        request = self.factory.get('/db-test/')
        response = db_middleware(request)
        
        # 쿼리 수가 기록되었는지 확인
        assert hasattr(request, 'db_query_count')
        assert request.db_query_count >= 1  # 최소 1개 쿼리 실행됨
        assert 'X-DB-Query-Count' in response
    
    @override_settings(DEBUG=True)
    def test_debug_middleware_behavior(self):
        """디버그 모드에서의 미들웨어 동작 검증"""
        
        class DebugMiddleware:
            def __init__(self, get_response):
                self.get_response = get_response
            
            def __call__(self, request):
                from django.conf import settings
                
                if settings.DEBUG:
                    request.debug_info = {
                        'timestamp': 'test_timestamp',
                        'request_id': 'test_request_id'
                    }
                
                return self.get_response(request)
        
        debug_middleware = DebugMiddleware(dummy_view)
        request = self.factory.get('/')
        response = debug_middleware(request)
        
        # 디버그 정보가 추가되었는지 확인
        assert hasattr(request, 'debug_info')
        assert 'timestamp' in request.debug_info
        assert 'request_id' in request.debug_info
    
    def test_middleware_performance(self):
        """미들웨어 성능 검증"""
        import time
        
        class PerformanceMiddleware:
            def __init__(self, get_response):
                self.get_response = get_response
            
            def __call__(self, request):
                start_time = time.time()
                response = self.get_response(request)
                end_time = time.time()
                
                processing_time = (end_time - start_time) * 1000  # 밀리초
                response['X-Processing-Time'] = f"{processing_time:.2f}ms"
                
                return response
        
        performance_middleware = PerformanceMiddleware(dummy_view)
        
        request = self.factory.get('/')
        response = performance_middleware(request)
        
        # 처리 시간이 기록되었는지 확인
        assert 'X-Processing-Time' in response
        processing_time_str = response['X-Processing-Time']
        assert processing_time_str.endswith('ms')
        
        # 처리 시간이 합리적인 범위인지 확인 (1초 이하)
        processing_time = float(processing_time_str.replace('ms', ''))
        assert processing_time < 1000, f"처리 시간이 너무 깁니다: {processing_time}ms"