"""
Django & PostgreSQL 특화 테스트: 모델 제약 조건 검증

PostgreSQL 특화 기능과 Django 모델 제약 조건이 올바르게 동작하는지 검증
"""

import pytest
from django.test import TestCase, TransactionTestCase
from django.db import IntegrityError, transaction
from django.core.exceptions import ValidationError
from django.contrib.auth import get_user_model
from decimal import Decimal

from apps.analysis.models import AnalysisJob, ProcessedImage, LAMLayoutDetection
from apps.files.models import SourceFile
from apps.users.models import User

User = get_user_model()


@pytest.mark.django_db
class TestModelConstraints(TransactionTestCase):
    """모델 제약 조건 테스트"""
    
    def setUp(self):
        """테스트 설정"""
        self.user = User.objects.create_user(
            username='testuser',
            password='testpass',
            email='test@example.com',
            api_quota_limit=100
        )
    
    def test_analysis_job_check_constraints(self):
        """AnalysisJob 모델의 체크 제약 조건 검증"""
        
        # 유효한 작업 생성
        valid_job = AnalysisJob.objects.create(
            user=self.user,
            job_name='Valid Job',
            model_type='docstructbench',
            total_images=10,
            processed_images=5
        )
        assert valid_job.pk is not None
        
        # total_images >= processed_images 제약 위반 시도
        with pytest.raises((IntegrityError, ValidationError)):
            with transaction.atomic():
                AnalysisJob.objects.create(
                    user=self.user,
                    job_name='Invalid Job',
                    model_type='docstructbench',
                    total_images=5,
                    processed_images=10  # total_images보다 큼
                )
        
        # 기존 작업 수정으로 제약 위반 시도
        with pytest.raises((IntegrityError, ValidationError)):
            with transaction.atomic():
                valid_job.processed_images = 15  # total_images(10)보다 큼
                valid_job.full_clean()  # Django 레벨 검증
                valid_job.save()
    
    def test_user_unique_constraints(self):
        """User 모델의 유니크 제약 조건 검증"""
        
        # 동일한 username으로 사용자 생성 시도
        with pytest.raises(IntegrityError):
            with transaction.atomic():
                User.objects.create_user(
                    username='testuser',  # 이미 존재하는 username
                    password='anotherpass',
                    email='another@example.com'
                )
        
        # 동일한 email로 사용자 생성 시도
        with pytest.raises(IntegrityError):
            with transaction.atomic():
                User.objects.create_user(
                    username='anotheruser',
                    password='testpass',
                    email='test@example.com'  # 이미 존재하는 email
                )
    
    def test_source_file_unique_constraints(self):
        """SourceFile 모델의 유니크 제약 조건 검증"""
        
        # 첫 번째 파일 생성
        file1 = SourceFile.objects.create(
            user=self.user,
            original_filename='test.pdf',
            stored_filename='unique_stored_1.pdf',
            file_type='pdf',
            file_size_mb=Decimal('1.5'),
            storage_path='/media/unique_stored_1.pdf'
        )
        
        # 동일한 stored_filename으로 파일 생성 시도
        with pytest.raises(IntegrityError):
            with transaction.atomic():
                SourceFile.objects.create(
                    user=self.user,
                    original_filename='test2.pdf',
                    stored_filename='unique_stored_1.pdf',  # 중복된 저장 파일명
                    file_type='pdf',
                    file_size_mb=Decimal('2.0'),
                    storage_path='/media/unique_stored_2.pdf'
                )
    
    def test_foreign_key_constraints(self):
        """외래 키 제약 조건 검증"""
        
        # 존재하지 않는 사용자 ID로 작업 생성 시도
        with pytest.raises(IntegrityError):
            with transaction.atomic():
                AnalysisJob.objects.create(
                    user_id=99999,  # 존재하지 않는 사용자 ID
                    job_name='Invalid Job',
                    model_type='docstructbench',
                    total_images=1
                )
        
        # 외래 키 연쇄 삭제 검증
        job = AnalysisJob.objects.create(
            user=self.user,
            job_name='Test Job',
            model_type='docstructbench',
            total_images=1
        )
        
        source_file = SourceFile.objects.create(
            user=self.user,
            original_filename='test.pdf',
            stored_filename='test_stored.pdf',
            file_type='pdf',
            file_size_mb=Decimal('1.0'),
            storage_path='/media/test_stored.pdf'
        )
        
        processed_image = ProcessedImage.objects.create(
            source_file=source_file,
            job=job,
            processed_filename='test_image.jpg',
            image_width=1920,
            image_height=1080
        )
        
        # 작업 삭제 시 연쇄 삭제 확인
        job_id = job.id
        processed_image_id = processed_image.id
        
        job.delete()
        
        # 연관된 ProcessedImage도 삭제되어야 함
        assert not ProcessedImage.objects.filter(id=processed_image_id).exists()
        
        # SourceFile은 다른 외래키 관계이므로 남아있어야 함
        assert SourceFile.objects.filter(id=source_file.id).exists()
    
    def test_decimal_field_precision(self):
        """Decimal 필드 정밀도 검증"""
        
        # 정확한 decimal 값
        file = SourceFile.objects.create(
            user=self.user,
            original_filename='precision_test.pdf',
            stored_filename='precision_test_stored.pdf',
            file_type='pdf',
            file_size_mb=Decimal('123.45'),
            storage_path='/media/precision_test.pdf'
        )
        
        file.refresh_from_db()
        assert file.file_size_mb == Decimal('123.45')
        
        # 정밀도 초과 값 (소수점 3자리)
        file.file_size_mb = Decimal('123.456')
        file.save()
        file.refresh_from_db()
        
        # PostgreSQL에서 반올림되어 저장되는지 확인
        assert file.file_size_mb == Decimal('123.46')  # 반올림됨
    
    def test_text_field_length_constraints(self):
        """텍스트 필드 길이 제약 검증"""
        
        # 긴 텍스트로 작업 생성
        long_description = 'x' * 10000  # 매우 긴 설명
        
        job = AnalysisJob.objects.create(
            user=self.user,
            job_name='Long Description Job',
            description=long_description,
            model_type='docstructbench',
            total_images=1
        )
        
        job.refresh_from_db()
        assert len(job.description) == 10000
        assert job.description == long_description
    
    def test_json_field_operations(self):
        """JSONField 연산 검증 (PostgreSQL 특화)"""
        
        # JSON 데이터가 있는 모델 생성 (예: CIMOutput의 reading_order)
        job = AnalysisJob.objects.create(
            user=self.user,
            job_name='JSON Test Job',
            model_type='docstructbench',
            total_images=1
        )
        
        from apps.analysis.models import CIMOutput
        
        json_data = {
            'sections': [
                {'order': 1, 'type': 'title', 'content': '제목'},
                {'order': 2, 'type': 'paragraph', 'content': '내용'},
                {'order': 3, 'type': 'image', 'content': '이미지 설명'}
            ],
            'metadata': {
                'total_sections': 3,
                'reading_time': 5
            }
        }
        
        output = CIMOutput.objects.create(
            job=job,
            final_text='Test output text',
            reading_order=json_data
        )
        
        output.refresh_from_db()
        
        # JSON 데이터 저장/조회 검증
        assert output.reading_order == json_data
        assert output.reading_order['metadata']['total_sections'] == 3
        
        # PostgreSQL JSON 쿼리 연산
        from django.db.models import Q
        
        # JSON 필드 쿼리
        results = CIMOutput.objects.filter(
            reading_order__metadata__total_sections=3
        )
        assert output in results
        
        # 중첩 JSON 쿼리
        results = CIMOutput.objects.filter(
            reading_order__sections__0__type='title'
        )
        assert output in results
    
    def test_index_performance(self):
        """인덱스 성능 검증"""
        import time
        
        # 대량 데이터 생성
        jobs = []
        for i in range(1000):
            job = AnalysisJob.objects.create(
                user=self.user,
                job_name=f'Performance Test Job {i}',
                model_type='docstructbench',
                total_images=1,
                status='completed' if i % 2 == 0 else 'processing'
            )
            jobs.append(job)
        
        # 인덱싱된 필드로 쿼리 성능 측정
        start_time = time.time()
        
        # user + status + created_at 복합 인덱스 활용 쿼리
        completed_jobs = AnalysisJob.objects.filter(
            user=self.user,
            status='completed'
        ).order_by('-created_at')[:10]
        
        list(completed_jobs)  # 쿼리 실행 강제
        
        end_time = time.time()
        query_time = end_time - start_time
        
        # 인덱스 덕분에 빠른 쿼리여야 함 (1초 이하)
        assert query_time < 1.0, f"인덱싱된 쿼리가 너무 느립니다: {query_time:.3f}초"
        
        print(f"1000개 레코드에서 인덱싱된 쿼리 시간: {query_time:.3f}초")
    
    def test_transaction_isolation(self):
        """트랜잭션 격리 수준 검증"""
        from django.db import connections
        
        # 현재 격리 수준 확인
        connection = connections['default']
        
        with connection.cursor() as cursor:
            cursor.execute("SHOW transaction_isolation")
            isolation_level = cursor.fetchone()[0]
            
            # PostgreSQL의 기본 격리 수준 확인
            assert isolation_level in ['read committed', 'READ COMMITTED']
        
        # 트랜잭션 내에서 데이터 일관성 검증
        job = AnalysisJob.objects.create(
            user=self.user,
            job_name='Transaction Test',
            model_type='docstructbench',
            total_images=1,
            processed_images=0
        )
        
        with transaction.atomic():
            # 트랜잭션 내에서 수정
            job.processed_images = 1
            job.save()
            
            # 같은 트랜잭션에서 조회
            updated_job = AnalysisJob.objects.get(id=job.id)
            assert updated_job.processed_images == 1
            
            # 의도적으로 예외 발생으로 롤백
            raise Exception("Intentional rollback")
        
        # 트랜잭션 롤백 후 확인
        job.refresh_from_db()
        assert job.processed_images == 0  # 롤백됨
    
    def test_database_functions(self):
        """PostgreSQL 데이터베이스 함수 활용 검증"""
        from django.db.models import Count, Avg, Sum, F
        from django.db.models.functions import Extract, Now
        
        # 여러 작업 생성
        for i in range(10):
            AnalysisJob.objects.create(
                user=self.user,
                job_name=f'Function Test Job {i}',
                model_type='docstructbench',
                total_images=i + 1,
                processed_images=i,
                status='completed'
            )
        
        # 집계 함수 검증
        stats = AnalysisJob.objects.filter(user=self.user).aggregate(
            total_jobs=Count('id'),
            avg_total_images=Avg('total_images'),
            sum_processed_images=Sum('processed_images')
        )
        
        assert stats['total_jobs'] >= 10
        assert stats['avg_total_images'] > 0
        assert stats['sum_processed_images'] > 0
        
        # PostgreSQL 날짜 함수 활용
        jobs_with_year = AnalysisJob.objects.annotate(
            created_year=Extract('created_at', 'year')
        ).filter(user=self.user)
        
        for job in jobs_with_year[:5]:
            assert job.created_year is not None
            assert job.created_year >= 2020
        
        # F() 표현식 활용
        progress_jobs = AnalysisJob.objects.annotate(
            completion_rate=F('processed_images') * 100.0 / F('total_images')
        ).filter(user=self.user)
        
        for job in progress_jobs[:5]:
            expected_rate = (job.processed_images / job.total_images) * 100
            assert abs(job.completion_rate - expected_rate) < 0.01
    
    def test_custom_managers_and_querysets(self):
        """커스텀 매니저와 쿼리셋 검증"""
        
        # 다양한 상태의 작업 생성
        statuses = ['pending', 'processing', 'completed', 'failed']
        for i, status in enumerate(statuses):
            for j in range(3):
                AnalysisJob.objects.create(
                    user=self.user,
                    job_name=f'{status.title()} Job {j}',
                    model_type='docstructbench',
                    total_images=5,
                    processed_images=j + 1,
                    status=status
                )
        
        # 상태별 쿼리
        completed_jobs = AnalysisJob.objects.filter(status='completed')
        assert completed_jobs.count() == 3
        
        processing_jobs = AnalysisJob.objects.filter(status='processing')
        assert processing_jobs.count() == 3
        
        # 복합 조건 쿼리
        active_jobs = AnalysisJob.objects.filter(
            user=self.user,
            status__in=['pending', 'processing']
        ).order_by('created_at')
        
        assert active_jobs.count() == 6  # pending 3개 + processing 3개
    
    def tearDown(self):
        """테스트 정리"""
        # 대량 생성된 테스트 데이터 정리
        AnalysisJob.objects.filter(user=self.user).delete()
        SourceFile.objects.filter(user=self.user).delete()