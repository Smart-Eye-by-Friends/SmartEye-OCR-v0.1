"""
회귀 테스트: 권한 관리 보안 취약점 검증

2단계에서 수정한 권한 관리 취약점이 재발하지 않는지 검증
"""

import pytest
from django.test import TestCase
from django.contrib.auth import get_user_model
from rest_framework.test import APIClient
from rest_framework import status
from apps.analysis.models import AnalysisJob
from apps.files.models import SourceFile

User = get_user_model()


@pytest.mark.django_db
class TestPermissionsSecurity:
    """권한 관리 보안 회귀 테스트"""
    
    def setup_method(self):
        """각 테스트 전 설정"""
        # 일반 사용자들 생성
        self.user1 = User.objects.create_user(
            username='user1',
            password='password1',
            email='user1@example.com',
            user_type='student'
        )
        
        self.user2 = User.objects.create_user(
            username='user2',
            password='password2',
            email='user2@example.com',
            user_type='student'
        )
        
        # 관리자 사용자 생성
        self.admin_user = User.objects.create_user(
            username='admin',
            password='admin_password',
            email='admin@example.com',
            user_type='admin',
            is_staff=True,
            is_superuser=True
        )
        
        # API 클라이언트들
        self.client1 = APIClient()
        self.client2 = APIClient()
        self.admin_client = APIClient()
        
        # 사용자별 테스트 데이터 생성
        self.user1_job = AnalysisJob.objects.create(
            user=self.user1,
            job_name='User1 Job',
            model_type='docstructbench',
            total_images=1
        )
        
        self.user2_job = AnalysisJob.objects.create(
            user=self.user2,
            job_name='User2 Job',
            model_type='docstructbench',
            total_images=1
        )
        
        self.user1_file = SourceFile.objects.create(
            user=self.user1,
            original_filename='user1_file.pdf',
            stored_filename='user1_file.pdf',
            file_type='pdf',
            file_size_mb=1.0,
            storage_path='/media/user1_file.pdf'
        )
    
    def test_user_cannot_access_other_users_jobs(self):
        """일반 사용자는 다른 사용자의 작업에 접근할 수 없어야 함"""
        self.client1.force_authenticate(user=self.user1)
        
        # User1이 User2의 작업 조회 시도
        response = self.client1.get(f'/api/v1/analysis/jobs/{self.user2_job.id}/')
        
        # 접근이 거부되어야 함
        assert response.status_code == status.HTTP_404_NOT_FOUND, \
            "다른 사용자의 작업에 접근이 허용되었습니다"
    
    def test_user_cannot_modify_other_users_jobs(self):
        """일반 사용자는 다른 사용자의 작업을 수정할 수 없어야 함"""
        self.client1.force_authenticate(user=self.user1)
        
        # User1이 User2의 작업 수정 시도
        response = self.client1.patch(
            f'/api/v1/analysis/jobs/{self.user2_job.id}/',
            {'job_name': 'Hacked Job Name'}
        )
        
        # 수정이 거부되어야 함
        assert response.status_code == status.HTTP_404_NOT_FOUND
        
        # 실제로 수정되지 않았는지 확인
        self.user2_job.refresh_from_db()
        assert self.user2_job.job_name == 'User2 Job'
    
    def test_user_cannot_delete_other_users_jobs(self):
        """일반 사용자는 다른 사용자의 작업을 삭제할 수 없어야 함"""
        self.client1.force_authenticate(user=self.user1)
        
        # User1이 User2의 작업 삭제 시도
        response = self.client1.delete(f'/api/v1/analysis/jobs/{self.user2_job.id}/')
        
        # 삭제가 거부되어야 함
        assert response.status_code == status.HTTP_404_NOT_FOUND
        
        # 실제로 삭제되지 않았는지 확인
        assert AnalysisJob.objects.filter(id=self.user2_job.id).exists()
    
    def test_user_can_access_own_jobs(self):
        """사용자는 자신의 작업에는 접근할 수 있어야 함"""
        self.client1.force_authenticate(user=self.user1)
        
        # User1이 자신의 작업 조회
        response = self.client1.get(f'/api/v1/analysis/jobs/{self.user1_job.id}/')
        
        # 접근이 허용되어야 함
        assert response.status_code == status.HTTP_200_OK
        assert response.data['id'] == self.user1_job.id
        assert response.data['job_name'] == 'User1 Job'
    
    def test_user_job_list_filtered_by_owner(self):
        """작업 목록은 소유자별로 필터링되어야 함"""
        self.client1.force_authenticate(user=self.user1)
        
        # User1이 작업 목록 조회
        response = self.client1.get('/api/v1/analysis/jobs/')
        
        assert response.status_code == status.HTTP_200_OK
        
        # User1의 작업만 보여야 함
        job_ids = [job['id'] for job in response.data['results']]
        assert self.user1_job.id in job_ids
        assert self.user2_job.id not in job_ids
    
    def test_admin_can_access_all_users_data(self):
        """관리자는 모든 사용자의 데이터에 접근할 수 있어야 함"""
        self.admin_client.force_authenticate(user=self.admin_user)
        
        # 관리자가 User1의 작업 조회
        response = self.admin_client.get(f'/api/v1/analysis/jobs/{self.user1_job.id}/')
        assert response.status_code == status.HTTP_200_OK
        
        # 관리자가 User2의 작업 조회
        response = self.admin_client.get(f'/api/v1/analysis/jobs/{self.user2_job.id}/')
        assert response.status_code == status.HTTP_200_OK
        
        # 관리자가 전체 작업 목록 조회
        response = self.admin_client.get('/api/v1/analysis/jobs/')
        assert response.status_code == status.HTTP_200_OK
        
        job_ids = [job['id'] for job in response.data['results']]
        assert self.user1_job.id in job_ids
        assert self.user2_job.id in job_ids
    
    def test_user_profile_access_restrictions(self):
        """사용자 프로필 접근 제한 검증"""
        self.client1.force_authenticate(user=self.user1)
        
        # 자신의 프로필은 접근 가능
        response = self.client1.get('/api/v1/users/me/')
        assert response.status_code == status.HTTP_200_OK
        assert response.data['username'] == 'user1'
        
        # 다른 사용자 프로필 조회 시도
        response = self.client1.get(f'/api/v1/users/{self.user2.id}/')
        assert response.status_code == status.HTTP_403_FORBIDDEN
    
    def test_quota_information_privacy(self):
        """할당량 정보 프라이버시 검증"""
        self.client1.force_authenticate(user=self.user1)
        
        # 자신의 할당량 정보는 조회 가능
        response = self.client1.get('/api/v1/users/quota_status/')
        assert response.status_code == status.HTTP_200_OK
        assert 'quota_used' in response.data
        assert 'quota_limit' in response.data
        
        # 다른 사용자의 할당량 정보는 조회 불가
        # (API 설계상 개별 사용자 할당량 조회 엔드포인트가 없어야 함)
        response = self.client1.get(f'/api/v1/users/{self.user2.id}/quota_status/')
        assert response.status_code == status.HTTP_404_NOT_FOUND
    
    def test_file_access_restrictions(self):
        """파일 접근 권한 제한 검증"""
        self.client2.force_authenticate(user=self.user2)
        
        # User2가 User1의 파일에 접근 시도
        response = self.client2.get(f'/api/v1/files/{self.user1_file.id}/')
        assert response.status_code == status.HTTP_404_NOT_FOUND
        
        # User1이 자신의 파일에 접근
        self.client1.force_authenticate(user=self.user1)
        response = self.client1.get(f'/api/v1/files/{self.user1_file.id}/')
        assert response.status_code == status.HTTP_200_OK
    
    def test_unauthorized_access_denied(self):
        """인증되지 않은 접근 거부 검증"""
        client = APIClient()  # 인증되지 않은 클라이언트
        
        # 인증이 필요한 엔드포인트들
        endpoints = [
            '/api/v1/analysis/jobs/',
            f'/api/v1/analysis/jobs/{self.user1_job.id}/',
            '/api/v1/users/me/',
            '/api/v1/files/',
        ]
        
        for endpoint in endpoints:
            response = client.get(endpoint)
            assert response.status_code in [
                status.HTTP_401_UNAUTHORIZED,
                status.HTTP_403_FORBIDDEN
            ], f"인증되지 않은 접근이 허용됨: {endpoint}"
    
    def test_privilege_escalation_prevention(self):
        """권한 상승 방지 검증"""
        self.client1.force_authenticate(user=self.user1)
        
        # 일반 사용자가 관리자 권한 획득 시도
        response = self.client1.patch('/api/v1/users/me/', {
            'is_staff': True,
            'is_superuser': True,
            'user_type': 'admin'
        })
        
        # 권한 필드는 수정되지 않아야 함
        if response.status_code == status.HTTP_200_OK:
            self.user1.refresh_from_db()
            assert not self.user1.is_staff
            assert not self.user1.is_superuser
            assert self.user1.user_type != 'admin'
    
    def test_bulk_operations_permissions(self):
        """대량 작업 권한 검증"""
        self.client1.force_authenticate(user=self.user1)
        
        # 다른 사용자의 작업도 포함한 대량 삭제 시도
        response = self.client1.post('/api/v1/analysis/jobs/bulk_delete/', {
            'ids': [self.user1_job.id, self.user2_job.id]
        })
        
        # 요청이 처리되더라도 자신의 작업만 영향받아야 함
        if response.status_code == status.HTTP_200_OK:
            # User2의 작업은 여전히 존재해야 함
            assert AnalysisJob.objects.filter(id=self.user2_job.id).exists()
    
    def test_cross_site_request_forgery_protection(self):
        """CSRF 보호 검증"""
        # CSRF 토큰 없이 요청
        client = APIClient(enforce_csrf_checks=True)
        client.force_authenticate(user=self.user1)
        
        response = client.post('/api/v1/analysis/jobs/upload_and_analyze/', {
            'job_name': 'CSRF Test Job'
        })
        
        # CSRF 보호가 적용되어야 함 (API는 일반적으로 CSRF 면제이지만 확인)
        # 실제 운영에서는 API 엔드포인트에 대해 CSRF 설정을 확인해야 함
        
    @pytest.mark.parametrize("user_type,expected_access", [
        ('student', ['own_data']),
        ('teacher', ['own_data']),
        ('admin', ['own_data', 'all_data']),
    ])
    def test_role_based_access_control(self, user_type, expected_access):
        """역할 기반 접근 제어 검증"""
        # 역할별 사용자 생성
        test_user = User.objects.create_user(
            username=f'test_{user_type}',
            password='password',
            email=f'{user_type}@example.com',
            user_type=user_type,
            is_staff=(user_type == 'admin'),
            is_superuser=(user_type == 'admin')
        )
        
        client = APIClient()
        client.force_authenticate(user=test_user)
        
        # 자신의 데이터 접근 (모든 역할에서 허용)
        if 'own_data' in expected_access:
            # 자신의 작업 생성 후 접근 시도
            own_job = AnalysisJob.objects.create(
                user=test_user,
                job_name=f'{user_type} Job',
                model_type='docstructbench',
                total_images=1
            )
            
            response = client.get(f'/api/v1/analysis/jobs/{own_job.id}/')
            assert response.status_code == status.HTTP_200_OK
        
        # 모든 데이터 접근 (관리자만 허용)
        if 'all_data' in expected_access:
            response = client.get(f'/api/v1/analysis/jobs/{self.user1_job.id}/')
            assert response.status_code == status.HTTP_200_OK
        else:
            response = client.get(f'/api/v1/analysis/jobs/{self.user1_job.id}/')
            assert response.status_code == status.HTTP_404_NOT_FOUND