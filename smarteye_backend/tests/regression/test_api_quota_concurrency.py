"""
회귀 테스트: API 할당량 동시성 문제 검증

2단계에서 수정한 API 할당량 경합 조건이 재발하지 않는지 검증
PostgreSQL의 transaction.atomic과 select_for_update 사용 검증
"""

import pytest
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from django.test import TransactionTestCase
from django.db import transaction
from django.core.exceptions import ValidationError
from django.contrib.auth import get_user_model

User = get_user_model()


@pytest.mark.django_db(transaction=True)
class TestAPIQuotaConcurrency(TransactionTestCase):
    """API 할당량 동시성 회귀 테스트"""
    
    def setUp(self):
        """테스트 설정"""
        self.user = User.objects.create_user(
            username='quota_test_user',
            password='test_password',
            email='quota@example.com',
            api_quota_limit=10,  # 제한된 할당량으로 테스트
            api_quota_used=0
        )
    
    def test_sequential_api_usage_increment(self):
        """순차적 API 사용량 증가 테스트 (기준점)"""
        initial_usage = self.user.api_quota_used
        
        # 순차적으로 API 사용량 증가
        for i in range(5):
            self.user.increment_api_usage(1)
            self.user.refresh_from_db()
            assert self.user.api_quota_used == initial_usage + i + 1
        
        assert self.user.api_quota_used == initial_usage + 5
    
    def test_concurrent_api_usage_with_race_condition_prevention(self):
        """동시성 환경에서 API 사용량 증가 시 경합 조건 방지 검증"""
        initial_usage = self.user.api_quota_used
        num_threads = 10
        increment_per_thread = 1
        expected_final_usage = initial_usage + (num_threads * increment_per_thread)
        
        # 동시성 테스트를 위한 결과 저장
        results = []
        errors = []
        
        def increment_usage(thread_id):
            """각 스레드에서 실행할 API 사용량 증가 함수"""
            try:
                # 새로운 DB 연결에서 사용자 객체 다시 가져오기
                user = User.objects.get(id=self.user.id)
                user.increment_api_usage(increment_per_thread)
                results.append(f"Thread {thread_id}: success")
                return True
            except Exception as e:
                errors.append(f"Thread {thread_id}: {str(e)}")
                return False
        
        # 다중 스레드로 동시 실행
        threads = []
        for i in range(num_threads):
            thread = threading.Thread(target=increment_usage, args=(i,))
            threads.append(thread)
        
        # 모든 스레드 거의 동시에 시작
        start_time = time.time()
        for thread in threads:
            thread.start()
        
        # 모든 스레드 완료 대기
        for thread in threads:
            thread.join()
        
        end_time = time.time()
        
        # 결과 검증
        self.user.refresh_from_db()
        
        # 에러가 없어야 함
        assert len(errors) == 0, f"동시성 에러 발생: {errors}"
        
        # 모든 스레드가 성공해야 함
        assert len(results) == num_threads
        
        # 최종 사용량이 정확해야 함
        assert self.user.api_quota_used == expected_final_usage, \
            f"예상: {expected_final_usage}, 실제: {self.user.api_quota_used}"
        
        # 실행 시간이 합리적이어야 함 (데드락 없음)
        execution_time = end_time - start_time
        assert execution_time < 10.0, f"실행 시간이 너무 김: {execution_time}초"
    
    def test_quota_limit_enforcement_under_concurrency(self):
        """동시성 환경에서 할당량 한도 강제 적용 검증"""
        # 사용자 할당량을 거의 한도에 가깝게 설정
        self.user.api_quota_used = 8
        self.user.api_quota_limit = 10
        self.user.save()
        
        num_threads = 5  # 5개 스레드가 각각 1씩 증가 시도 (총 5, 한도 초과)
        successful_increments = []
        failed_increments = []
        
        def try_increment_usage(thread_id):
            """할당량 체크와 함께 사용량 증가 시도"""
            try:
                user = User.objects.get(id=self.user.id)
                success = user.check_and_increment_api_usage(1)
                if success:
                    successful_increments.append(thread_id)
                    return True
                else:
                    failed_increments.append(thread_id)
                    return False
            except ValidationError:
                failed_increments.append(thread_id)
                return False
            except Exception as e:
                failed_increments.append(f"{thread_id}: {str(e)}")
                return False
        
        # 병렬 실행
        with ThreadPoolExecutor(max_workers=num_threads) as executor:
            futures = [executor.submit(try_increment_usage, i) for i in range(num_threads)]
            
            for future in as_completed(futures):
                future.result()  # 예외 발생 시 재발생
        
        # 결과 검증
        self.user.refresh_from_db()
        
        # 할당량을 초과하지 않아야 함
        assert self.user.api_quota_used <= self.user.api_quota_limit, \
            f"할당량 초과: {self.user.api_quota_used}/{self.user.api_quota_limit}"
        
        # 일부 요청만 성공해야 함 (정확히 2개: 8+2=10)
        assert len(successful_increments) == 2, \
            f"예상 성공 수: 2, 실제: {len(successful_increments)}"
        
        # 나머지는 실패해야 함
        assert len(failed_increments) == 3, \
            f"예상 실패 수: 3, 실제: {len(failed_increments)}"
        
        # 최종 사용량이 한도와 같아야 함
        assert self.user.api_quota_used == self.user.api_quota_limit
    
    def test_select_for_update_prevents_lost_updates(self):
        """select_for_update가 업데이트 손실을 방지하는지 검증"""
        initial_usage = self.user.api_quota_used
        num_operations = 20
        
        def atomic_increment():
            """원자적 증가 연산"""
            with transaction.atomic():
                user = User.objects.select_for_update().get(id=self.user.id)
                current_usage = user.api_quota_used
                # 의도적으로 지연 추가 (경합 조건 유발)
                time.sleep(0.01)
                user.api_quota_used = current_usage + 1
                user.save()
        
        # 다중 스레드로 원자적 증가 실행
        threads = []
        for _ in range(num_operations):
            thread = threading.Thread(target=atomic_increment)
            threads.append(thread)
        
        for thread in threads:
            thread.start()
        
        for thread in threads:
            thread.join()
        
        # 결과 검증
        self.user.refresh_from_db()
        expected_usage = initial_usage + num_operations
        
        assert self.user.api_quota_used == expected_usage, \
            f"업데이트 손실 발생. 예상: {expected_usage}, 실제: {self.user.api_quota_used}"
    
    def test_deadlock_prevention(self):
        """데드락 방지 검증"""
        # 두 명의 사용자 생성
        user2 = User.objects.create_user(
            username='quota_test_user2',
            password='test_password',
            api_quota_limit=10,
            api_quota_used=0
        )
        
        deadlock_occurred = threading.Event()
        results = []
        
        def cross_update(user1_id, user2_id, thread_name):
            """교차 업데이트 (데드락 유발 가능성)"""
            try:
                with transaction.atomic():
                    # 항상 동일한 순서로 락 획득 (데드락 방지)
                    users = User.objects.filter(
                        id__in=[user1_id, user2_id]
                    ).select_for_update().order_by('id')
                    
                    for user in users:
                        user.api_quota_used += 1
                        user.save()
                        time.sleep(0.01)  # 다른 스레드가 개입할 시간 제공
                
                results.append(f"{thread_name}: success")
                
            except Exception as e:
                if "deadlock" in str(e).lower():
                    deadlock_occurred.set()
                results.append(f"{thread_name}: error - {str(e)}")
        
        # 두 스레드가 서로 다른 순서로 사용자 업데이트
        thread1 = threading.Thread(
            target=cross_update,
            args=(self.user.id, user2.id, "thread1")
        )
        thread2 = threading.Thread(
            target=cross_update,
            args=(user2.id, self.user.id, "thread2")
        )
        
        thread1.start()
        thread2.start()
        
        # 최대 5초 대기
        thread1.join(timeout=5.0)
        thread2.join(timeout=5.0)
        
        # 결과 검증
        assert not deadlock_occurred.is_set(), "데드락이 발생했습니다"
        assert len(results) == 2, f"예상 결과 수: 2, 실제: {len(results)}"
        
        # 모든 결과가 성공이어야 함
        for result in results:
            assert "success" in result, f"실패한 스레드: {result}"
    
    def test_transaction_rollback_on_quota_exceeded(self):
        """할당량 초과 시 트랜잭션 롤백 검증"""
        # 할당량을 한도에 가깝게 설정
        self.user.api_quota_used = 9
        self.user.api_quota_limit = 10
        self.user.save()
        
        initial_usage = self.user.api_quota_used
        
        def failing_transaction():
            """실패하는 트랜잭션"""
            try:
                with transaction.atomic():
                    user = User.objects.select_for_update().get(id=self.user.id)
                    
                    # 할당량 체크
                    if user.api_quota_used + 2 > user.api_quota_limit:
                        raise ValidationError("할당량 초과")
                    
                    # 이 부분은 실행되지 않아야 함
                    user.api_quota_used += 2
                    user.save()
                    
            except ValidationError:
                pass  # 예상된 예외
        
        # 실패하는 트랜잭션 실행
        failing_transaction()
        
        # 데이터베이스 상태 확인
        self.user.refresh_from_db()
        
        # 사용량이 변경되지 않았어야 함 (롤백됨)
        assert self.user.api_quota_used == initial_usage, \
            f"트랜잭션 롤백 실패. 초기: {initial_usage}, 현재: {self.user.api_quota_used}"
    
    @pytest.mark.performance
    def test_concurrent_performance_under_load(self):
        """부하 상황에서의 동시성 성능 테스트"""
        num_threads = 50
        increments_per_thread = 2
        
        # 충분한 할당량 설정
        self.user.api_quota_limit = num_threads * increments_per_thread + 100
        self.user.save()
        
        initial_usage = self.user.api_quota_used
        start_time = time.time()
        
        def load_test_increment(thread_id):
            """부하 테스트용 증가 함수"""
            for _ in range(increments_per_thread):
                user = User.objects.get(id=self.user.id)
                user.increment_api_usage(1)
        
        # 고부하 동시 실행
        with ThreadPoolExecutor(max_workers=num_threads) as executor:
            futures = [
                executor.submit(load_test_increment, i) 
                for i in range(num_threads)
            ]
            
            for future in as_completed(futures):
                future.result()
        
        end_time = time.time()
        execution_time = end_time - start_time
        
        # 결과 검증
        self.user.refresh_from_db()
        expected_usage = initial_usage + (num_threads * increments_per_thread)
        
        assert self.user.api_quota_used == expected_usage, \
            f"부하 테스트 실패. 예상: {expected_usage}, 실제: {self.user.api_quota_used}"
        
        # 성능 요구사항: 100개 스레드 * 2회 = 200회 증가가 10초 내 완료
        assert execution_time < 10.0, \
            f"성능 요구사항 미달. 실행 시간: {execution_time:.2f}초"
        
        throughput = (num_threads * increments_per_thread) / execution_time
        print(f"처리량: {throughput:.2f} operations/second")