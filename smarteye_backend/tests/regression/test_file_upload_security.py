"""
회귀 테스트: 파일 업로드 보안 검증

2단계에서 수정한 파일 업로드 보안 취약점이 재발하지 않는지 검증
"""

import pytest
import tempfile
import magic
from io import BytesIO
from django.test import TestCase
from django.core.files.uploadedfile import SimpleUploadedFile
from django.contrib.auth import get_user_model
from rest_framework.test import APIClient
from rest_framework import status
from PIL import Image

from apps.analysis.views import validate_uploaded_file
from django.core.exceptions import ValidationError

User = get_user_model()


@pytest.mark.django_db
class TestFileUploadSecurity:
    """파일 업로드 보안 회귀 테스트"""
    
    def setup_method(self):
        """각 테스트 전 설정"""
        self.client = APIClient()
        self.user = User.objects.create_user(
            username='test_user',
            password='test_password',
            email='test@example.com'
        )
        self.client.force_authenticate(user=self.user)
    
    def create_test_image(self, format='JPEG', size=(100, 100)):
        """테스트용 이미지 파일 생성"""
        image = Image.new('RGB', size, color='red')
        buffer = BytesIO()
        image.save(buffer, format=format)
        buffer.seek(0)
        return buffer.getvalue()
    
    def create_test_pdf(self, content=b'%PDF-1.4 fake pdf content'):
        """테스트용 PDF 파일 생성"""
        return content
    
    def create_malicious_file(self, filename, content, mime_type):
        """악성 파일 생성"""
        return SimpleUploadedFile(
            filename, 
            content, 
            content_type=mime_type
        )
    
    def test_valid_image_upload_allowed(self):
        """유효한 이미지 파일은 업로드 허용되어야 함"""
        # 유효한 JPEG 이미지
        image_data = self.create_test_image('JPEG')
        valid_file = SimpleUploadedFile(
            'test_image.jpg',
            image_data,
            content_type='image/jpeg'
        )
        
        # 검증 함수가 예외를 발생시키지 않아야 함
        try:
            validate_uploaded_file(valid_file)
        except ValidationError:
            pytest.fail("유효한 이미지 파일이 거부되었습니다.")
    
    def test_valid_pdf_upload_allowed(self):
        """유효한 PDF 파일은 업로드 허용되어야 함"""
        pdf_data = self.create_test_pdf()
        valid_file = SimpleUploadedFile(
            'test_document.pdf',
            pdf_data,
            content_type='application/pdf'
        )
        
        try:
            validate_uploaded_file(valid_file)
        except ValidationError:
            pytest.fail("유효한 PDF 파일이 거부되었습니다.")
    
    def test_oversized_file_rejected(self):
        """크기가 초과된 파일은 거부되어야 함"""
        # 100MB 초과 파일 생성 (가짜로 큰 데이터)
        large_content = b'x' * (101 * 1024 * 1024)  # 101MB
        large_file = SimpleUploadedFile(
            'large_file.jpg',
            large_content,
            content_type='image/jpeg'
        )
        
        with pytest.raises(ValidationError, match="파일 크기가.*를 초과합니다"):
            validate_uploaded_file(large_file)
    
    def test_executable_file_rejected(self):
        """실행 파일은 거부되어야 함"""
        # 실행 파일 시뮬레이션
        executable_content = b'\x7fELF'  # ELF magic bytes
        executable_file = SimpleUploadedFile(
            'malware.exe',
            executable_content,
            content_type='application/x-executable'
        )
        
        with pytest.raises(ValidationError, match="허용되지 않는 파일 형식"):
            validate_uploaded_file(executable_file)
    
    def test_script_file_rejected(self):
        """스크립트 파일은 거부되어야 함"""
        script_content = b'#!/bin/bash\nrm -rf /'
        script_file = SimpleUploadedFile(
            'malicious_script.sh',
            script_content,
            content_type='application/x-shellscript'
        )
        
        with pytest.raises(ValidationError, match="허용되지 않는 파일 형식"):
            validate_uploaded_file(script_file)
    
    def test_fake_image_extension_rejected(self):
        """가짜 이미지 확장자 파일은 거부되어야 함"""
        # 실제로는 텍스트 파일이지만 .jpg 확장자 사용
        fake_image = SimpleUploadedFile(
            'fake_image.jpg',
            b'This is not an image file, it is text',
            content_type='text/plain'
        )
        
        with pytest.raises(ValidationError, match="허용되지 않는 파일 형식"):
            validate_uploaded_file(fake_image)
    
    def test_path_traversal_filename_rejected(self):
        """경로 조작 파일명은 거부되어야 함"""
        image_data = self.create_test_image()
        
        malicious_filenames = [
            '../../../etc/passwd',
            '..\\..\\windows\\system32\\config\\sam',
            'normal_name/../malicious.jpg',
            '/absolute/path/file.jpg',
            'C:\\Windows\\System32\\file.jpg'
        ]
        
        for malicious_name in malicious_filenames:
            malicious_file = SimpleUploadedFile(
                malicious_name,
                image_data,
                content_type='image/jpeg'
            )
            
            with pytest.raises(ValidationError, match="잘못된 파일명"):
                validate_uploaded_file(malicious_file)
    
    def test_null_byte_filename_rejected(self):
        """NULL 바이트가 포함된 파일명은 거부되어야 함"""
        image_data = self.create_test_image()
        malicious_file = SimpleUploadedFile(
            'image.jpg\x00.exe',
            image_data,
            content_type='image/jpeg'
        )
        
        with pytest.raises(ValidationError, match="잘못된 파일명"):
            validate_uploaded_file(malicious_file)
    
    def test_zip_bomb_rejected(self):
        """ZIP 폭탄 형태의 파일은 거부되어야 함"""
        # ZIP 파일 magic bytes
        zip_content = b'PK\x03\x04' + b'x' * 1000  # 가짜 ZIP 데이터
        zip_file = SimpleUploadedFile(
            'archive.zip',
            zip_content,
            content_type='application/zip'
        )
        
        with pytest.raises(ValidationError, match="허용되지 않는 파일 형식"):
            validate_uploaded_file(zip_file)
    
    @pytest.mark.integration
    def test_upload_and_analyze_security_integration(self):
        """전체 업로드 및 분석 API의 보안 검증"""
        # 악성 파일로 API 호출 시도
        malicious_file = self.create_malicious_file(
            'malware.exe',
            b'\x7fELF malicious content',
            'application/x-executable'
        )
        
        response = self.client.post('/api/v1/analysis/jobs/upload_and_analyze/', {
            'files': [malicious_file],
            'job_name': 'Security Test',
        })
        
        # 업로드가 거부되어야 함
        assert response.status_code == status.HTTP_400_BAD_REQUEST
        assert 'error' in response.data
        assert '파일 업로드 실패' in response.data['error']
    
    def test_content_type_spoofing_prevention(self):
        """Content-Type 스푸핑 방지 검증"""
        # 실제로는 실행 파일이지만 이미지 Content-Type 설정
        executable_content = b'\x7fELF'
        spoofed_file = SimpleUploadedFile(
            'fake_image.jpg',
            executable_content,
            content_type='image/jpeg'  # 거짓 Content-Type
        )
        
        # Magic bytes 검증으로 실제 파일 타입 확인하여 거부해야 함
        with pytest.raises(ValidationError, match="허용되지 않는 파일 형식"):
            validate_uploaded_file(spoofed_file)
    
    @pytest.mark.parametrize("extension,content,should_pass", [
        ('jpg', None, True),   # content=None이면 유효한 이미지 생성
        ('jpeg', None, True),
        ('png', None, True),
        ('pdf', b'%PDF-1.4', True),
        ('exe', b'\x7fELF', False),
        ('bat', b'@echo off', False),
        ('sh', b'#!/bin/bash', False),
        ('py', b'import os', False),
        ('js', b'alert("xss")', False),
    ])
    def test_file_extension_validation(self, extension, content, should_pass):
        """다양한 파일 확장자 검증"""
        if content is None:
            content = self.create_test_image()
        
        test_file = SimpleUploadedFile(
            f'test.{extension}',
            content,
            content_type='application/octet-stream'
        )
        
        if should_pass:
            try:
                validate_uploaded_file(test_file)
            except ValidationError:
                pytest.fail(f".{extension} 파일이 잘못 거부되었습니다.")
        else:
            with pytest.raises(ValidationError):
                validate_uploaded_file(test_file)
    
    def test_concurrent_upload_security(self):
        """동시 업로드 시 보안 검증"""
        import threading
        import time
        
        results = []
        
        def upload_file(file_content, filename):
            """스레드에서 실행할 업로드 함수"""
            try:
                test_file = SimpleUploadedFile(filename, file_content)
                validate_uploaded_file(test_file)
                results.append(('success', filename))
            except ValidationError as e:
                results.append(('error', str(e)))
        
        # 여러 스레드로 동시 업로드 시도
        threads = []
        for i in range(5):
            if i % 2 == 0:
                # 유효한 파일
                content = self.create_test_image()
                filename = f'valid_image_{i}.jpg'
            else:
                # 악성 파일
                content = b'\x7fELF malicious'
                filename = f'malware_{i}.exe'
            
            thread = threading.Thread(target=upload_file, args=(content, filename))
            threads.append(thread)
            thread.start()
        
        # 모든 스레드 완료 대기
        for thread in threads:
            thread.join()
        
        # 결과 검증
        assert len(results) == 5
        success_count = len([r for r in results if r[0] == 'success'])
        error_count = len([r for r in results if r[0] == 'error'])
        
        # 유효한 파일 3개는 성공, 악성 파일 2개는 실패해야 함
        assert success_count == 3
        assert error_count == 2