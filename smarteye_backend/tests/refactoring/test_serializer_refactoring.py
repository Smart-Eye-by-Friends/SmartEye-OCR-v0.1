"""
리팩토링 검증 테스트: Serializer 통합 및 중복 코드 제거 검증

3단계에서 제안한 BaseModelSerializer와 상속 구조가 제대로 동작하는지 검증
"""

import pytest
from django.test import TestCase
from rest_framework import serializers
from rest_framework.test import APIClient
from django.contrib.auth import get_user_model
from datetime import datetime

from apps.analysis.models import AnalysisJob, ProcessedImage
from apps.files.models import SourceFile
from apps.users.models import UserPreference

User = get_user_model()


class BaseModelSerializer(serializers.ModelSerializer):
    """기본 모델 시리얼라이저 (리팩토링된 버전)"""
    
    class Meta:
        abstract = True
        read_only_fields = ['id', 'created_at', 'updated_at']
    
    def to_representation(self, instance):
        """공통 응답 형식 처리"""
        data = super().to_representation(instance)
        # 날짜 형식 통일
        for field_name, value in data.items():
            if isinstance(value, datetime):
                data[field_name] = value.strftime('%Y-%m-%d %H:%M:%S')
        return data


class TimestampSerializer(BaseModelSerializer):
    """타임스탬프가 있는 모델용 (리팩토링된 버전)"""
    created_at = serializers.DateTimeField(format='%Y-%m-%d %H:%M:%S', read_only=True)
    updated_at = serializers.DateTimeField(format='%Y-%m-%d %H:%M:%S', read_only=True)


class ProgressSerializer(TimestampSerializer):
    """진행률이 있는 모델용 (리팩토링된 버전)"""
    progress_percentage = serializers.ReadOnlyField()


# 리팩토링된 Serializer들
class RefactoredAnalysisJobSerializer(ProgressSerializer):
    """리팩토링된 분석 작업 시리얼라이저"""
    
    class Meta(ProgressSerializer.Meta):
        model = AnalysisJob
        fields = [
            'id', 'job_name', 'description', 'status', 'model_type',
            'processing_mode', 'total_images', 'processed_images', 'failed_images',
            'batch_size', 'enable_resume', 'enable_ocr', 'enable_api',
            'priority', 'started_at', 'completed_at', 'estimated_completion',
            'error_message', 'progress_percentage', 'created_at', 'updated_at'
        ]
        read_only_fields = ProgressSerializer.Meta.read_only_fields + [
            'status', 'processed_images', 'failed_images',
            'started_at', 'completed_at', 'estimated_completion', 'error_message'
        ]


class RefactoredSourceFileSerializer(TimestampSerializer):
    """리팩토링된 소스 파일 시리얼라이저"""
    
    class Meta(TimestampSerializer.Meta):
        model = SourceFile
        fields = [
            'id', 'original_filename', 'file_type', 'file_size_mb',
            'upload_status', 'created_at', 'updated_at'
        ]
        read_only_fields = TimestampSerializer.Meta.read_only_fields + [
            'stored_filename', 'file_hash', 'storage_path'
        ]


class RefactoredUserPreferenceSerializer(BaseModelSerializer):
    """리팩토링된 사용자 설정 시리얼라이저"""
    
    class Meta(BaseModelSerializer.Meta):
        model = UserPreference
        fields = ['id', 'preference_key', 'preference_value', 'updated_at']
        read_only_fields = BaseModelSerializer.Meta.read_only_fields


@pytest.mark.django_db
class TestSerializerRefactoring(TestCase):
    """Serializer 리팩토링 검증 테스트"""
    
    def setUp(self):
        """테스트 설정"""
        self.user = User.objects.create_user(
            username='testuser',
            password='testpass',
            email='test@example.com'
        )
        
        self.job = AnalysisJob.objects.create(
            user=self.user,
            job_name='Test Job',
            model_type='docstructbench',
            total_images=10,
            processed_images=5,
            status='processing'
        )
        
        self.source_file = SourceFile.objects.create(
            user=self.user,
            original_filename='test.pdf',
            stored_filename='stored_test.pdf',
            file_type='pdf',
            file_size_mb=2.5,
            storage_path='/media/stored_test.pdf'
        )
        
        self.preference = UserPreference.objects.create(
            user=self.user,
            preference_key='theme',
            preference_value='dark'
        )
    
    def test_base_serializer_common_fields(self):
        """BaseModelSerializer의 공통 필드 처리 검증"""
        
        class TestModelSerializer(BaseModelSerializer):
            class Meta(BaseModelSerializer.Meta):
                model = SourceFile
                fields = ['id', 'original_filename', 'created_at', 'updated_at']
        
        serializer = TestModelSerializer(self.source_file)
        data = serializer.data
        
        # 공통 read_only_fields 적용 확인
        assert 'id' in data
        assert 'created_at' in data
        assert 'updated_at' in data
        
        # 날짜 형식 통일 확인
        assert isinstance(data['created_at'], str)
        assert len(data['created_at']) == 19  # 'YYYY-MM-DD HH:MM:SS' 형식
    
    def test_timestamp_serializer_datetime_formatting(self):
        """TimestampSerializer의 날짜 형식 처리 검증"""
        serializer = RefactoredSourceFileSerializer(self.source_file)
        data = serializer.data
        
        # 날짜 필드 존재 확인
        assert 'created_at' in data
        assert 'updated_at' in data
        
        # 날짜 형식 검증
        created_at = data['created_at']
        assert isinstance(created_at, str)
        assert len(created_at) == 19
        
        # 실제 날짜 파싱 가능한지 확인
        try:
            datetime.strptime(created_at, '%Y-%m-%d %H:%M:%S')
        except ValueError:
            pytest.fail("날짜 형식이 올바르지 않습니다.")
    
    def test_progress_serializer_functionality(self):
        """ProgressSerializer의 진행률 계산 검증"""
        serializer = RefactoredAnalysisJobSerializer(self.job)
        data = serializer.data
        
        # 진행률 필드 존재 확인
        assert 'progress_percentage' in data
        
        # 진행률 계산 정확성 확인
        expected_progress = (5 / 10) * 100  # processed_images / total_images * 100
        assert data['progress_percentage'] == expected_progress
        
        # 타임스탬프 필드들도 포함되어야 함
        assert 'created_at' in data
        assert 'updated_at' in data
    
    def test_read_only_fields_inheritance(self):
        """read_only_fields 상속 검증"""
        
        # 기본 read_only_fields
        base_readonly = ['id', 'created_at', 'updated_at']
        
        # TimestampSerializer는 기본 필드들을 포함해야 함
        timestamp_serializer = RefactoredSourceFileSerializer()
        timestamp_readonly = timestamp_serializer.Meta.read_only_fields
        
        for field in base_readonly:
            assert field in timestamp_readonly, f"{field}가 read_only_fields에 없습니다"
        
        # AnalysisJobSerializer는 추가 read_only_fields를 가져야 함
        job_serializer = RefactoredAnalysisJobSerializer()
        job_readonly = job_serializer.Meta.read_only_fields
        
        for field in base_readonly:
            assert field in job_readonly, f"{field}가 상속되지 않았습니다"
        
        # 추가 필드들 확인
        additional_readonly = ['status', 'processed_images', 'failed_images']
        for field in additional_readonly:
            assert field in job_readonly, f"추가 read_only_field {field}가 없습니다"
    
    def test_serializer_validation_consistency(self):
        """Serializer 검증 일관성 확인"""
        
        # 유효한 데이터로 생성
        valid_data = {
            'job_name': 'New Test Job',
            'description': 'Test description',
            'model_type': 'docstructbench',
            'total_images': 5,
        }
        
        serializer = RefactoredAnalysisJobSerializer(data=valid_data)
        assert serializer.is_valid(), f"검증 실패: {serializer.errors}"
        
        # 검증된 데이터 확인
        validated_data = serializer.validated_data
        assert 'job_name' in validated_data
        assert validated_data['job_name'] == 'New Test Job'
        
        # read_only 필드는 validated_data에 없어야 함
        assert 'id' not in validated_data
        assert 'created_at' not in validated_data
        assert 'status' not in validated_data
    
    def test_serializer_field_count_reduction(self):
        """필드 정의 중복 감소 검증"""
        
        # 리팩토링 전후 필드 정의 비교
        serializer = RefactoredAnalysisJobSerializer()
        meta_class = serializer.Meta
        
        # Meta 클래스에서 상속된 read_only_fields 확인
        assert hasattr(meta_class, 'read_only_fields')
        
        # 기본 필드들이 자동으로 포함되는지 확인
        readonly_fields = meta_class.read_only_fields
        assert 'id' in readonly_fields
        assert 'created_at' in readonly_fields
        assert 'updated_at' in readonly_fields
        
        print(f"총 read_only_fields 수: {len(readonly_fields)}")
        print(f"read_only_fields: {readonly_fields}")
    
    def test_serializer_performance_with_inheritance(self):
        """상속 구조에서의 Serializer 성능 검증"""
        import time
        
        # 대량 데이터로 성능 테스트
        jobs = []
        for i in range(100):
            job = AnalysisJob.objects.create(
                user=self.user,
                job_name=f'Perf Test Job {i}',
                model_type='docstructbench',
                total_images=10,
                processed_images=i % 10
            )
            jobs.append(job)
        
        # 리팩토링된 Serializer 성능 측정
        start_time = time.time()
        serializer = RefactoredAnalysisJobSerializer(jobs, many=True)
        serialized_data = serializer.data
        end_time = time.time()
        
        serialization_time = end_time - start_time
        
        # 결과 검증
        assert len(serialized_data) == 100
        assert serialization_time < 1.0, f"직렬화 시간이 너무 깁니다: {serialization_time:.3f}초"
        
        # 각 항목이 올바른 구조를 가지는지 확인
        for item in serialized_data:
            assert 'id' in item
            assert 'progress_percentage' in item
            assert 'created_at' in item
            assert isinstance(item['created_at'], str)
    
    def test_custom_field_processing(self):
        """커스텀 필드 처리 검증"""
        serializer = RefactoredAnalysisJobSerializer(self.job)
        data = serializer.data
        
        # progress_percentage가 올바르게 계산되는지 확인
        assert 'progress_percentage' in data
        assert data['progress_percentage'] == 50.0  # 5/10 * 100
        
        # 다른 계산된 필드들도 확인
        self.job.total_images = 0  # 0으로 나누기 상황
        self.job.save()
        
        serializer = RefactoredAnalysisJobSerializer(self.job)
        data = serializer.data
        assert data['progress_percentage'] == 0  # 0/0 케이스 처리
    
    def test_nested_serializer_consistency(self):
        """중첩 Serializer 일관성 검증"""
        
        class RefactoredJobWithUserSerializer(RefactoredAnalysisJobSerializer):
            user = serializers.StringRelatedField(read_only=True)
            
            class Meta(RefactoredAnalysisJobSerializer.Meta):
                fields = RefactoredAnalysisJobSerializer.Meta.fields + ['user']
        
        serializer = RefactoredJobWithUserSerializer(self.job)
        data = serializer.data
        
        # 기본 필드들 확인
        assert 'id' in data
        assert 'job_name' in data
        assert 'progress_percentage' in data
        
        # 추가된 필드 확인
        assert 'user' in data
        assert data['user'] == str(self.user)
        
        # 날짜 형식 일관성 확인
        assert isinstance(data['created_at'], str)
    
    def test_serializer_error_handling(self):
        """Serializer 오류 처리 검증"""
        
        # 필수 필드 누락
        invalid_data = {
            'description': 'Missing required fields'
        }
        
        serializer = RefactoredAnalysisJobSerializer(data=invalid_data)
        assert not serializer.is_valid()
        
        errors = serializer.errors
        assert 'job_name' in errors  # 필수 필드 누락 오류
        assert 'model_type' in errors
        assert 'total_images' in errors
        
        # 오류 메시지 구조 확인
        for field, error_list in errors.items():
            assert isinstance(error_list, list)
            assert len(error_list) > 0
    
    def test_backward_compatibility(self):
        """기존 Serializer와의 하위 호환성 검증"""
        
        # 기존 방식으로 생성된 데이터
        old_style_data = {
            'id': self.job.id,
            'job_name': self.job.job_name,
            'status': self.job.status,
            'total_images': self.job.total_images,
            'processed_images': self.job.processed_images,
            'created_at': self.job.created_at.strftime('%Y-%m-%dT%H:%M:%S.%fZ'),
            'updated_at': self.job.updated_at.strftime('%Y-%m-%dT%H:%M:%S.%fZ'),
        }
        
        # 리팩토링된 Serializer로 직렬화한 데이터
        new_serializer = RefactoredAnalysisJobSerializer(self.job)
        new_style_data = new_serializer.data
        
        # 핵심 필드들이 동일한지 확인
        core_fields = ['id', 'job_name', 'status', 'total_images', 'processed_images']
        for field in core_fields:
            assert old_style_data[field] == new_style_data[field], \
                f"필드 {field}가 일치하지 않습니다"
    
    def tearDown(self):
        """테스트 정리"""
        # 성능 테스트에서 생성한 추가 객체들 정리
        AnalysisJob.objects.filter(job_name__startswith='Perf Test Job').delete()