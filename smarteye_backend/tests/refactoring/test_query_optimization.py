"""
리팩토링 검증 테스트: N+1 쿼리 최적화 검증

3단계에서 제안한 쿼리 최적화가 실제로 동작하는지 검증
"""

import pytest
from django.test import TestCase, override_settings
from django.db import connection
from django.test.utils import override_settings
from rest_framework.test import APIClient
from rest_framework import status
from django.contrib.auth import get_user_model

from apps.analysis.models import AnalysisJob, ProcessedImage, LAMLayoutDetection
from apps.files.models import SourceFile

User = get_user_model()


# 쿼리 수 측정을 위한 헬퍼 클래스
class QueryCountDebugMixin:
    """쿼리 수 측정 Mixin"""
    
    def assertNumQueriesLessThan(self, num, func=None, *args, **kwargs):
        """특정 개수보다 적은 쿼리가 실행되는지 검증"""
        with self.assertNumQueries(0):
            pass  # 초기 쿼리 수 측정
            
        initial_queries = len(connection.queries)
        
        if func:
            result = func(*args, **kwargs)
        
        final_queries = len(connection.queries)
        executed_queries = final_queries - initial_queries
        
        assert executed_queries < num, \
            f"쿼리 수가 예상보다 많습니다. 실행됨: {executed_queries}, 최대: {num-1}"
        
        return result if func else None
    
    def get_query_count(self, func, *args, **kwargs):
        """함수 실행 시 쿼리 수 반환"""
        initial_queries = len(connection.queries)
        result = func(*args, **kwargs)
        final_queries = len(connection.queries)
        
        return final_queries - initial_queries, result


@pytest.mark.django_db
@override_settings(DEBUG=True)  # SQL 쿼리 로깅 활성화
class TestQueryOptimization(TestCase, QueryCountDebugMixin):
    """N+1 쿼리 최적화 검증 테스트"""
    
    def setUp(self):
        """테스트 데이터 설정"""
        self.client = APIClient()
        
        # 사용자 생성
        self.user = User.objects.create_user(
            username='testuser',
            password='testpass',
            email='test@example.com'
        )
        self.client.force_authenticate(user=self.user)
        
        # 테스트 데이터 생성
        self.create_test_data()
    
    def create_test_data(self):
        """N+1 쿼리 테스트를 위한 복합 데이터 생성"""
        # 여러 작업 생성
        self.jobs = []
        for i in range(5):
            job = AnalysisJob.objects.create(
                user=self.user,
                job_name=f'Test Job {i}',
                model_type='docstructbench',
                total_images=3,
                status='completed'
            )
            self.jobs.append(job)
            
            # 각 작업마다 파일 생성
            for j in range(3):
                source_file = SourceFile.objects.create(
                    user=self.user,
                    original_filename=f'file_{i}_{j}.pdf',
                    stored_filename=f'stored_file_{i}_{j}.pdf',
                    file_type='pdf',
                    file_size_mb=1.0,
                    storage_path=f'/media/file_{i}_{j}.pdf'
                )
                
                # 각 파일마다 처리된 이미지 생성
                processed_image = ProcessedImage.objects.create(
                    source_file=source_file,
                    job=job,
                    processed_filename=f'image_{i}_{j}.jpg',
                    page_number=j + 1,
                    image_width=1920,
                    image_height=1080,
                    processing_status='completed'
                )
                
                # 각 이미지마다 여러 레이아웃 감지 결과 생성
                for k in range(4):
                    LAMLayoutDetection.objects.create(
                        image=processed_image,
                        detection_order=k,
                        class_name=f'class_{k}',
                        confidence=0.9,
                        bbox_x1=10 + k * 10,
                        bbox_y1=10 + k * 10,
                        bbox_x2=50 + k * 10,
                        bbox_y2=50 + k * 10,
                        area_pixels=1600,
                        width_ratio=0.1,
                        height_ratio=0.1,
                        center_x=30 + k * 10,
                        center_y=30 + k * 10
                    )
    
    def test_analysis_job_list_query_optimization(self):
        """분석 작업 목록 조회 시 N+1 쿼리 최적화 검증"""
        
        def get_jobs_without_optimization():
            """최적화 없는 조회 (기존 방식)"""
            jobs = AnalysisJob.objects.filter(user=self.user)
            result = []
            for job in jobs:
                # 이 부분에서 N+1 쿼리 발생
                user_info = {
                    'username': job.user.username,  # 추가 쿼리
                    'email': job.user.email,        # 추가 쿼리
                }
                processed_count = job.processed_image_set.count()  # 추가 쿼리
                result.append({
                    'id': job.id,
                    'name': job.job_name,
                    'user': user_info,
                    'processed_count': processed_count
                })
            return result
        
        def get_jobs_with_optimization():
            """최적화된 조회 (개선된 방식)"""
            jobs = AnalysisJob.objects.filter(user=self.user).select_related('user').prefetch_related('processed_image_set')
            result = []
            for job in jobs:
                user_info = {
                    'username': job.user.username,  # 캐시된 데이터 사용
                    'email': job.user.email,        # 캐시된 데이터 사용
                }
                processed_count = len(job.processed_image_set.all())  # 프리페치된 데이터 사용
                result.append({
                    'id': job.id,
                    'name': job.job_name,
                    'user': user_info,
                    'processed_count': processed_count
                })
            return result
        
        # 최적화 없는 버전 쿼리 수 측정
        unoptimized_queries, unoptimized_result = self.get_query_count(get_jobs_without_optimization)
        
        # 최적화된 버전 쿼리 수 측정
        optimized_queries, optimized_result = self.get_query_count(get_jobs_with_optimization)
        
        # 결과 검증
        assert len(unoptimized_result) == len(optimized_result) == 5
        
        # 쿼리 최적화 검증: 최적화된 버전이 훨씬 적은 쿼리 사용해야 함
        print(f"최적화 전 쿼리 수: {unoptimized_queries}")
        print(f"최적화 후 쿼리 수: {optimized_queries}")
        
        # 최적화된 버전은 3개 이하의 쿼리만 사용해야 함
        # 1. AnalysisJob 조회 with select_related(user)
        # 2. ProcessedImage prefetch
        # 3. 기타 최적화 관련 쿼리
        assert optimized_queries <= 3, f"최적화된 쿼리 수가 예상보다 많습니다: {optimized_queries}"
        
        # 최적화 효과 검증: 최소 50% 이상 쿼리 감소
        optimization_ratio = (unoptimized_queries - optimized_queries) / unoptimized_queries
        assert optimization_ratio >= 0.5, \
            f"쿼리 최적화 효과가 부족합니다. 감소율: {optimization_ratio:.2%}"
    
    def test_api_endpoint_query_optimization(self):
        """API 엔드포인트 쿼리 최적화 검증"""
        
        def call_api_endpoint():
            """API 엔드포인트 호출"""
            response = self.client.get('/api/v1/analysis/jobs/')
            return response
        
        # API 호출 시 쿼리 수 측정
        query_count, response = self.get_query_count(call_api_endpoint)
        
        # 응답 검증
        assert response.status_code == status.HTTP_200_OK
        assert len(response.data['results']) == 5
        
        # 쿼리 최적화 검증: 5개 이하여야 함
        assert query_count <= 5, \
            f"API 엔드포인트 쿼리 수가 예상보다 많습니다: {query_count}"
        
        print(f"API 엔드포인트 쿼리 수: {query_count}")
    
    def test_deep_relationship_query_optimization(self):
        """깊은 관계 쿼리 최적화 검증"""
        
        def get_deep_relationship_data_unoptimized():
            """최적화 없는 깊은 관계 데이터 조회"""
            jobs = AnalysisJob.objects.filter(user=self.user)
            result = []
            for job in jobs:
                images = []
                for image in job.processed_image_set.all():  # N+1 쿼리
                    detections = []
                    for detection in image.layout_detections.all():  # N+1 쿼리
                        detections.append({
                            'class_name': detection.class_name,
                            'confidence': float(detection.confidence)
                        })
                    images.append({
                        'filename': image.processed_filename,
                        'detections': detections
                    })
                result.append({
                    'job_name': job.job_name,
                    'images': images
                })
            return result
        
        def get_deep_relationship_data_optimized():
            """최적화된 깊은 관계 데이터 조회"""
            jobs = AnalysisJob.objects.filter(user=self.user).prefetch_related(
                'processed_image_set__layout_detections'
            )
            result = []
            for job in jobs:
                images = []
                for image in job.processed_image_set.all():  # 프리페치된 데이터
                    detections = []
                    for detection in image.layout_detections.all():  # 프리페치된 데이터
                        detections.append({
                            'class_name': detection.class_name,
                            'confidence': float(detection.confidence)
                        })
                    images.append({
                        'filename': image.processed_filename,
                        'detections': detections
                    })
                result.append({
                    'job_name': job.job_name,
                    'images': images
                })
            return result
        
        # 각 방식의 쿼리 수 측정
        unoptimized_queries, unoptimized_result = self.get_query_count(
            get_deep_relationship_data_unoptimized
        )
        optimized_queries, optimized_result = self.get_query_count(
            get_deep_relationship_data_optimized
        )
        
        # 결과 동일성 검증
        assert len(unoptimized_result) == len(optimized_result) == 5
        
        print(f"깊은 관계 최적화 전: {unoptimized_queries} 쿼리")
        print(f"깊은 관계 최적화 후: {optimized_queries} 쿼리")
        
        # 최적화 효과 검증: 최소 80% 감소
        optimization_ratio = (unoptimized_queries - optimized_queries) / unoptimized_queries
        assert optimization_ratio >= 0.8, \
            f"깊은 관계 최적화 효과가 부족합니다. 감소율: {optimization_ratio:.2%}"
    
    @pytest.mark.benchmark
    def test_query_performance_benchmark(self):
        """쿼리 성능 벤치마크"""
        import time
        
        def benchmark_unoptimized():
            start = time.time()
            jobs = AnalysisJob.objects.filter(user=self.user)
            data = []
            for job in jobs:
                job_data = {
                    'id': job.id,
                    'name': job.job_name,
                    'user': job.user.username,  # N+1 쿼리
                    'image_count': job.processed_image_set.count(),  # N+1 쿼리
                }
                data.append(job_data)
            end = time.time()
            return end - start, data
        
        def benchmark_optimized():
            start = time.time()
            jobs = AnalysisJob.objects.filter(user=self.user).select_related('user').prefetch_related('processed_image_set')
            data = []
            for job in jobs:
                job_data = {
                    'id': job.id,
                    'name': job.job_name,
                    'user': job.user.username,  # 캐시된 데이터
                    'image_count': len(job.processed_image_set.all()),  # 프리페치된 데이터
                }
                data.append(job_data)
            end = time.time()
            return end - start, data
        
        # 각 방식의 실행 시간 측정
        unoptimized_time, unoptimized_data = benchmark_unoptimized()
        optimized_time, optimized_data = benchmark_optimized()
        
        # 결과 검증
        assert len(unoptimized_data) == len(optimized_data)
        
        print(f"최적화 전 실행 시간: {unoptimized_time:.4f}초")
        print(f"최적화 후 실행 시간: {optimized_time:.4f}초")
        
        # 성능 개선 검증: 최소 30% 이상 개선
        if unoptimized_time > 0:
            performance_improvement = (unoptimized_time - optimized_time) / unoptimized_time
            assert performance_improvement >= 0.3, \
                f"성능 개선이 부족합니다. 개선율: {performance_improvement:.2%}"
    
    def test_pagination_query_optimization(self):
        """페이지네이션 쿼리 최적화 검증"""
        
        def get_paginated_data():
            """페이지네이션된 데이터 조회"""
            response = self.client.get('/api/v1/analysis/jobs/?page=1&page_size=3')
            return response
        
        # 페이지네이션 쿼리 수 측정
        query_count, response = self.get_query_count(get_paginated_data)
        
        # 응답 검증
        assert response.status_code == status.HTTP_200_OK
        assert len(response.data['results']) == 3  # page_size
        
        # 페이지네이션 쿼리 최적화: 4개 이하여야 함
        # 1. COUNT 쿼리 (총 개수)
        # 2. SELECT 쿼리 (페이지 데이터)
        # 3-4. select_related/prefetch_related 쿼리
        assert query_count <= 4, \
            f"페이지네이션 쿼리 수가 예상보다 많습니다: {query_count}"
        
        print(f"페이지네이션 쿼리 수: {query_count}")
    
    def test_filtering_query_optimization(self):
        """필터링 쿼리 최적화 검증"""
        
        def get_filtered_data():
            """필터링된 데이터 조회"""
            response = self.client.get('/api/v1/analysis/jobs/?status=completed')
            return response
        
        # 필터링 쿼리 수 측정
        query_count, response = self.get_query_count(get_filtered_data)
        
        # 응답 검증
        assert response.status_code == status.HTTP_200_OK
        assert len(response.data['results']) == 5  # 모든 작업이 completed 상태
        
        # 필터링 쿼리 최적화 검증
        assert query_count <= 3, \
            f"필터링 쿼리 수가 예상보다 많습니다: {query_count}"
        
        print(f"필터링 쿼리 수: {query_count}")
    
    def test_search_query_optimization(self):
        """검색 쿼리 최적화 검증"""
        
        def search_data():
            """데이터 검색"""
            response = self.client.get('/api/v1/analysis/jobs/?search=Test')
            return response
        
        # 검색 쿼리 수 측정
        query_count, response = self.get_query_count(search_data)
        
        # 응답 검증
        assert response.status_code == status.HTTP_200_OK
        assert len(response.data['results']) == 5  # 모든 작업이 'Test'를 포함
        
        # 검색 쿼리 최적화 검증
        assert query_count <= 4, \
            f"검색 쿼리 수가 예상보다 많습니다: {query_count}"
        
        print(f"검색 쿼리 수: {query_count}")
    
    def test_complex_query_optimization(self):
        """복합 쿼리 최적화 검증 (필터링 + 검색 + 정렬 + 페이지네이션)"""
        
        def complex_query():
            """복합 쿼리"""
            response = self.client.get(
                '/api/v1/analysis/jobs/?status=completed&search=Test&'
                'ordering=-created_at&page=1&page_size=2'
            )
            return response
        
        # 복합 쿼리 수 측정
        query_count, response = self.get_query_count(complex_query)
        
        # 응답 검증
        assert response.status_code == status.HTTP_200_OK
        assert len(response.data['results']) == 2  # page_size
        
        # 복합 쿼리 최적화: 6개 이하여야 함
        assert query_count <= 6, \
            f"복합 쿼리 수가 예상보다 많습니다: {query_count}"
        
        print(f"복합 쿼리 수: {query_count}")
    
    def tearDown(self):
        """테스트 정리"""
        # 연결된 쿼리 로그 출력 (디버깅용)
        if hasattr(self, '_should_print_queries'):
            for query in connection.queries:
                print(f"SQL: {query['sql']}")
                print(f"Time: {query['time']}")
                print("---")