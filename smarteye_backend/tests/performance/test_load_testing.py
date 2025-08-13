"""
성능 테스트: 부하 테스트 및 성능 벤치마크

시스템의 성능 한계와 동시 처리 능력을 검증
"""

import pytest
from django.test import TransactionTestCase
from django.core.files.uploadedfile import SimpleUploadedFile
from rest_framework.test import APIClient
from rest_framework import status
from django.contrib.auth import get_user_model
from django.db import transaction
import time
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
import statistics
from unittest.mock import patch
from PIL import Image
from io import BytesIO
import psutil
import gc

from apps.analysis.models import AnalysisJob, ProcessedImage
from apps.files.models import SourceFile

User = get_user_model()


@pytest.mark.django_db(transaction=True)
@pytest.mark.performance
class TestLoadTesting(TransactionTestCase):
    """부하 테스트 및 성능 검증"""
    
    def setUp(self):
        """테스트 설정"""
        self.base_client = APIClient()
        
        # 테스트용 사용자들 생성
        self.users = []
        for i in range(10):
            user = User.objects.create_user(
                username=f'loadtest_user_{i}',
                password='test_password',
                email=f'loadtest{i}@example.com',
                api_quota_limit=10000  # 높은 할당량
            )
            self.users.append(user)
    
    def create_test_image(self, size=(400, 300), complexity='simple'):
        """부하 테스트용 이미지 생성"""
        image = Image.new('RGB', size, color='white')
        draw = ImageDraw.Draw(image)
        
        if complexity == 'simple':
            # 간단한 텍스트
            draw.text((10, 10), "Simple Test Image", fill='black')
            
        elif complexity == 'medium':
            # 중간 복잡도: 여러 텍스트 블록
            for i in range(5):
                draw.rectangle([10, 10 + i*50, size[0]-10, 50 + i*50], 
                             outline='blue', width=2)
                draw.text((20, 20 + i*50), f"Text Block {i+1}", fill='black')
                
        elif complexity == 'complex':
            # 높은 복잡도: 많은 요소들
            for i in range(10):
                for j in range(8):
                    x = 10 + j * 45
                    y = 10 + i * 25
                    if x + 40 < size[0] and y + 20 < size[1]:
                        draw.rectangle([x, y, x+40, y+20], 
                                     outline=f'#{i*20:02x}{j*30:02x}FF', width=1)
                        draw.text((x+2, y+2), f"{i}{j}", fill='black')
        
        buffer = BytesIO()
        image.save(buffer, format='JPEG', quality=85)
        buffer.seek(0)
        return buffer.getvalue()
    
    def measure_system_resources(self):
        """시스템 리소스 사용량 측정"""
        return {
            'cpu_percent': psutil.cpu_percent(interval=0.1),
            'memory_percent': psutil.virtual_memory().percent,
            'memory_used_gb': psutil.virtual_memory().used / 1024**3,
            'disk_io': psutil.disk_io_counters()._asdict() if psutil.disk_io_counters() else {},
            'network_io': psutil.net_io_counters()._asdict() if psutil.net_io_counters() else {}
        }
    
    def test_single_user_load_test(self):
        """단일 사용자 연속 요청 부하 테스트"""
        client = APIClient()
        client.force_authenticate(user=self.users[0])
        
        request_count = 50
        response_times = []
        success_count = 0
        error_count = 0
        
        print(f"\n단일 사용자 {request_count}회 연속 요청 테스트 시작...")
        
        for i in range(request_count):
            image_data = self.create_test_image(complexity='simple')
            test_image = SimpleUploadedFile(
                f'load_test_{i}.jpg',
                image_data,
                content_type='image/jpeg'
            )
            
            start_time = time.time()
            
            response = client.post('/api/v1/analysis/jobs/upload_and_analyze/', {
                'files': [test_image],
                'job_name': f'Load Test {i}',
                'model_choice': 'docstructbench'
            })
            
            response_time = (time.time() - start_time) * 1000  # 밀리초
            response_times.append(response_time)
            
            if response.status_code == status.HTTP_201_CREATED:
                success_count += 1
            else:
                error_count += 1
                print(f"요청 {i} 실패: {response.status_code}")
            
            # 요청 간 짧은 대기 (서버 부하 조절)
            time.sleep(0.1)
        
        # 성능 통계 분석
        avg_response_time = statistics.mean(response_times)
        median_response_time = statistics.median(response_times)
        percentile_95 = sorted(response_times)[int(0.95 * len(response_times))]
        
        print(f"성공률: {success_count}/{request_count} ({success_count/request_count*100:.1f}%)")
        print(f"평균 응답 시간: {avg_response_time:.2f}ms")
        print(f"중간값 응답 시간: {median_response_time:.2f}ms")
        print(f"95% 응답 시간: {percentile_95:.2f}ms")
        
        # 성능 요구사항 검증
        assert success_count >= request_count * 0.95, "성공률이 95% 미만입니다"
        assert avg_response_time < 5000, f"평균 응답 시간이 너무 깁니다: {avg_response_time:.2f}ms"
        assert percentile_95 < 10000, f"95% 응답 시간이 너무 깁니다: {percentile_95:.2f}ms"
    
    def test_concurrent_users_load_test(self):
        """다중 사용자 동시 접속 부하 테스트"""
        concurrent_users = 5
        requests_per_user = 10
        
        results = []
        errors = []
        start_time = time.time()
        
        def user_load_test(user_index):
            """개별 사용자 부하 테스트"""
            client = APIClient()
            client.force_authenticate(user=self.users[user_index])
            
            user_results = []
            
            for i in range(requests_per_user):
                try:
                    image_data = self.create_test_image(complexity='medium')
                    test_image = SimpleUploadedFile(
                        f'concurrent_test_u{user_index}_r{i}.jpg',
                        image_data,
                        content_type='image/jpeg'
                    )
                    
                    request_start = time.time()
                    
                    response = client.post('/api/v1/analysis/jobs/upload_and_analyze/', {
                        'files': [test_image],
                        'job_name': f'Concurrent Test U{user_index} R{i}',
                    })
                    
                    request_time = (time.time() - request_start) * 1000
                    
                    user_results.append({
                        'user': user_index,
                        'request': i,
                        'status_code': response.status_code,
                        'response_time_ms': request_time,
                        'success': response.status_code == status.HTTP_201_CREATED
                    })
                    
                    # 요청 간격 조절
                    time.sleep(0.05)
                    
                except Exception as e:
                    errors.append({
                        'user': user_index,
                        'request': i,
                        'error': str(e)
                    })
            
            return user_results
        
        print(f"\n{concurrent_users}명 사용자, 각 {requests_per_user}회 동시 요청 테스트...")
        
        # 병렬 실행
        with ThreadPoolExecutor(max_workers=concurrent_users) as executor:
            futures = [executor.submit(user_load_test, i) for i in range(concurrent_users)]
            
            for future in as_completed(futures):
                try:
                    user_results = future.result()
                    results.extend(user_results)
                except Exception as e:
                    errors.append({'error': str(e)})
        
        total_time = time.time() - start_time
        
        # 결과 분석
        total_requests = len(results)
        successful_requests = len([r for r in results if r['success']])
        
        if results:
            response_times = [r['response_time_ms'] for r in results]
            avg_response_time = statistics.mean(response_times)
            max_response_time = max(response_times)
            
            print(f"총 요청 수: {total_requests}")
            print(f"성공한 요청: {successful_requests}")
            print(f"성공률: {successful_requests/total_requests*100:.1f}%")
            print(f"전체 실행 시간: {total_time:.2f}초")
            print(f"평균 응답 시간: {avg_response_time:.2f}ms")
            print(f"최대 응답 시간: {max_response_time:.2f}ms")
            print(f"초당 요청 수: {total_requests/total_time:.2f} req/sec")
            
            # 성능 요구사항 검증
            assert len(errors) == 0, f"예외 발생: {errors}"
            assert successful_requests >= total_requests * 0.9, "성공률이 90% 미만입니다"
            assert avg_response_time < 10000, f"평균 응답 시간 초과: {avg_response_time:.2f}ms"
    
    def test_memory_usage_under_load(self):
        """부하 상황에서 메모리 사용량 테스트"""
        import gc
        
        client = APIClient()
        client.force_authenticate(user=self.users[0])
        
        # 초기 메모리 사용량
        gc.collect()  # 가비지 컬렉션 강제 실행
        initial_memory = psutil.virtual_memory().used / 1024**2  # MB
        
        print(f"\n메모리 사용량 테스트 - 초기 메모리: {initial_memory:.2f}MB")
        
        memory_usage = [initial_memory]
        
        # 대용량 이미지로 연속 요청
        for i in range(20):
            # 큰 이미지 생성 (메모리 사용량 증가)
            large_image_data = self.create_test_image(
                size=(1200, 800), 
                complexity='complex'
            )
            
            test_image = SimpleUploadedFile(
                f'memory_test_{i}.jpg',
                large_image_data,
                content_type='image/jpeg'
            )
            
            response = client.post('/api/v1/analysis/jobs/upload_and_analyze/', {
                'files': [test_image],
                'job_name': f'Memory Test {i}',
            })
            
            # 메모리 사용량 측정
            current_memory = psutil.virtual_memory().used / 1024**2
            memory_usage.append(current_memory)
            
            if i % 5 == 0:
                print(f"요청 {i}: 메모리 {current_memory:.2f}MB (+{current_memory-initial_memory:.2f}MB)")
            
            # 가끔 가비지 컬렉션 실행
            if i % 10 == 9:
                gc.collect()
        
        # 최종 가비지 컬렉션 후 메모리 측정
        gc.collect()
        final_memory = psutil.virtual_memory().used / 1024**2
        
        print(f"최종 메모리: {final_memory:.2f}MB (+{final_memory-initial_memory:.2f}MB)")
        
        # 메모리 사용량 검증
        memory_increase = final_memory - initial_memory
        max_memory_during_test = max(memory_usage) - initial_memory
        
        print(f"최대 메모리 증가: {max_memory_during_test:.2f}MB")
        print(f"최종 메모리 증가: {memory_increase:.2f}MB")
        
        # 메모리 누수 검증: 최종 메모리 증가가 최대 증가의 50% 이하여야 함
        assert memory_increase < max_memory_during_test * 0.5, \
            f"메모리 누수 의심: 최종 증가 {memory_increase:.2f}MB > 허용치"
        
        # 절대적 메모리 사용량 제한: 500MB 이하 증가
        assert memory_increase < 500, \
            f"메모리 사용량이 너무 많습니다: {memory_increase:.2f}MB 증가"
    
    def test_database_connection_pool_load(self):
        """데이터베이스 연결 풀 부하 테스트"""
        from django.db import connections
        
        concurrent_operations = 20
        operations_per_thread = 5
        
        results = []
        errors = []
        
        def database_operations(thread_id):
            """데이터베이스 연산 수행"""
            thread_results = []
            
            try:
                for i in range(operations_per_thread):
                    start_time = time.time()
                    
                    # 복합 데이터베이스 연산
                    user = self.users[thread_id % len(self.users)]
                    
                    # 여러 테이블에 대한 연산
                    job = AnalysisJob.objects.create(
                        user=user,
                        job_name=f'DB Load Test T{thread_id} O{i}',
                        model_type='docstructbench',
                        total_images=5,
                        status='pending'
                    )
                    
                    # 조회 연산
                    jobs_count = AnalysisJob.objects.filter(user=user).count()
                    
                    # 업데이트 연산
                    job.processed_images = 2
                    job.save()
                    
                    # 복합 쿼리
                    recent_jobs = AnalysisJob.objects.filter(
                        user=user,
                        status='pending'
                    ).order_by('-created_at')[:10]
                    
                    list(recent_jobs)  # 쿼리 실행 강제
                    
                    operation_time = (time.time() - start_time) * 1000
                    
                    thread_results.append({
                        'thread': thread_id,
                        'operation': i,
                        'time_ms': operation_time,
                        'jobs_count': jobs_count
                    })
                    
                    time.sleep(0.01)  # 짧은 대기
                    
            except Exception as e:
                errors.append({
                    'thread': thread_id,
                    'error': str(e)
                })
            
            return thread_results
        
        print(f"\n데이터베이스 연결 풀 테스트: {concurrent_operations}개 동시 연결...")
        
        start_time = time.time()
        
        with ThreadPoolExecutor(max_workers=concurrent_operations) as executor:
            futures = [executor.submit(database_operations, i) for i in range(concurrent_operations)]
            
            for future in as_completed(futures):
                try:
                    thread_results = future.result()
                    results.extend(thread_results)
                except Exception as e:
                    errors.append({'executor_error': str(e)})
        
        total_time = time.time() - start_time
        
        # 결과 분석
        if results:
            operation_times = [r['time_ms'] for r in results]
            avg_time = statistics.mean(operation_times)
            max_time = max(operation_times)
            
            print(f"총 데이터베이스 연산: {len(results)}")
            print(f"평균 연산 시간: {avg_time:.2f}ms")
            print(f"최대 연산 시간: {max_time:.2f}ms")
            print(f"초당 연산 수: {len(results)/total_time:.2f} ops/sec")
            
            # 성능 검증
            assert len(errors) == 0, f"데이터베이스 연산 오류: {errors}"
            assert avg_time < 1000, f"평균 DB 연산 시간 초과: {avg_time:.2f}ms"
            assert max_time < 5000, f"최대 DB 연산 시간 초과: {max_time:.2f}ms"
        
        # 연결 풀 상태 확인
        connection = connections['default']
        print(f"DB 연결 상태: {connection.connection is not None}")
    
    def test_file_upload_size_limits(self):
        """파일 업로드 크기 제한 성능 테스트"""
        client = APIClient()
        client.force_authenticate(user=self.users[0])
        
        file_sizes = [
            (400, 300, '작은 파일'),     # ~50KB
            (800, 600, '중간 파일'),     # ~200KB  
            (1600, 1200, '큰 파일'),    # ~800KB
            (2400, 1800, '매우 큰 파일') # ~1.8MB
        ]
        
        upload_results = []
        
        for width, height, size_desc in file_sizes:
            print(f"\n{size_desc} 업로드 테스트 ({width}x{height})...")
            
            # 해당 크기의 이미지 생성
            image_data = self.create_test_image(
                size=(width, height), 
                complexity='medium'
            )
            
            file_size_mb = len(image_data) / 1024**2
            
            test_image = SimpleUploadedFile(
                f'size_test_{width}x{height}.jpg',
                image_data,
                content_type='image/jpeg'
            )
            
            # 업로드 시간 측정
            start_time = time.time()
            
            response = client.post('/api/v1/analysis/jobs/upload_and_analyze/', {
                'files': [test_image],
                'job_name': f'Size Test {size_desc}',
            })
            
            upload_time = (time.time() - start_time) * 1000
            
            upload_results.append({
                'size_desc': size_desc,
                'file_size_mb': file_size_mb,
                'upload_time_ms': upload_time,
                'success': response.status_code == status.HTTP_201_CREATED,
                'status_code': response.status_code
            })
            
            print(f"파일 크기: {file_size_mb:.2f}MB, 업로드 시간: {upload_time:.2f}ms")
            
            if response.status_code == status.HTTP_201_CREATED:
                print("업로드 성공")
            else:
                print(f"업로드 실패: {response.status_code}")
        
        # 업로드 성능 분석
        successful_uploads = [r for r in upload_results if r['success']]
        
        if successful_uploads:
            # 파일 크기 대비 업로드 속도 계산
            for result in successful_uploads:
                speed_mbps = (result['file_size_mb'] * 8) / (result['upload_time_ms'] / 1000)
                result['speed_mbps'] = speed_mbps
                print(f"{result['size_desc']}: {speed_mbps:.2f} Mbps")
        
        # 성능 요구사항 확인
        assert len(successful_uploads) >= 3, "대부분의 파일 크기에서 업로드가 성공해야 합니다"
        
        # 큰 파일도 합리적인 시간 내에 업로드되어야 함
        large_file_results = [r for r in successful_uploads if r['file_size_mb'] > 1.0]
        if large_file_results:
            max_large_file_time = max(r['upload_time_ms'] for r in large_file_results)
            assert max_large_file_time < 30000, f"큰 파일 업로드 시간 초과: {max_large_file_time:.2f}ms"
    
    def test_system_resource_monitoring(self):
        """시스템 리소스 모니터링 테스트"""
        client = APIClient()
        client.force_authenticate(user=self.users[0])
        
        # 리소스 사용량 모니터링
        resource_snapshots = []
        
        print("\n시스템 리소스 모니터링 테스트...")
        
        # 초기 리소스 상태
        initial_resources = self.measure_system_resources()
        resource_snapshots.append(('initial', initial_resources))
        
        print(f"초기 CPU: {initial_resources['cpu_percent']:.1f}%, "
              f"메모리: {initial_resources['memory_percent']:.1f}%")
        
        # 중간 부하로 요청 실행
        for i in range(15):
            image_data = self.create_test_image(complexity='medium')
            test_image = SimpleUploadedFile(
                f'resource_test_{i}.jpg',
                image_data,
                content_type='image/jpeg'
            )
            
            response = client.post('/api/v1/analysis/jobs/upload_and_analyze/', {
                'files': [test_image],
                'job_name': f'Resource Test {i}',
            })
            
            if i % 5 == 4:  # 5번마다 리소스 측정
                resources = self.measure_system_resources()
                resource_snapshots.append((f'request_{i}', resources))
                print(f"요청 {i}: CPU {resources['cpu_percent']:.1f}%, "
                      f"메모리 {resources['memory_percent']:.1f}%")
            
            time.sleep(0.2)
        
        # 최종 리소스 상태
        final_resources = self.measure_system_resources()
        resource_snapshots.append(('final', final_resources))
        
        print(f"최종 CPU: {final_resources['cpu_percent']:.1f}%, "
              f"메모리: {final_resources['memory_percent']:.1f}%")
        
        # 리소스 사용량 분석
        cpu_usage = [r[1]['cpu_percent'] for r in resource_snapshots]
        memory_usage = [r[1]['memory_percent'] for r in resource_snapshots]
        
        max_cpu = max(cpu_usage)
        max_memory = max(memory_usage)
        
        print(f"최대 CPU 사용량: {max_cpu:.1f}%")
        print(f"최대 메모리 사용량: {max_memory:.1f}%")
        
        # 리소스 사용량 제한 검증
        assert max_cpu < 90, f"CPU 사용량이 너무 높습니다: {max_cpu:.1f}%"
        assert max_memory < 85, f"메모리 사용량이 너무 높습니다: {max_memory:.1f}%"
    
    def tearDown(self):
        """테스트 정리"""
        # 테스트 데이터 정리
        for user in self.users:
            try:
                AnalysisJob.objects.filter(user=user).delete()
                SourceFile.objects.filter(user=user).delete()
            except Exception as e:
                print(f"사용자 {user.username} 데이터 정리 오류: {e}")
        
        # 가비지 컬렉션
        import gc
        gc.collect()
        
        print("부하 테스트 정리 완료")