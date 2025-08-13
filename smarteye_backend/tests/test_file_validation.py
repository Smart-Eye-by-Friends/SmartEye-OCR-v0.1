"""
File Validation 유틸리티 단위 테스트
"""

import pytest
from django.test import TestCase
from django.core.files.uploadedfile import SimpleUploadedFile
from rest_framework import status
from io import BytesIO

from utils.file_validation import FileValidator, FileValidationConfig, SecurityFileValidator


class TestFileValidationConfig(TestCase):
    """FileValidationConfig 테스트"""
    
    def test_config_constants(self):
        """설정 상수값 테스트"""
        config = FileValidationConfig()
        
        self.assertIsInstance(config.SUPPORTED_FORMATS, list)
        self.assertIn('.pdf', config.SUPPORTED_FORMATS)
        self.assertIn('.jpg', config.SUPPORTED_FORMATS)
        self.assertEqual(config.MAX_FILE_SIZE_MB, 50)
        self.assertEqual(config.DEFAULT_IMAGE_SIZE, (1920, 1080))


class TestFileValidator(TestCase):
    """FileValidator 클래스 테스트"""
    
    def setUp(self):
        self.validator = FileValidator()
    
    def test_validate_file_format_valid(self):
        """유효한 파일 형식 테스트"""
        valid_files = [
            'test.jpg',
            'document.pdf', 
            'image.PNG',
            'file.JPEG'
        ]
        
        for filename in valid_files:
            with self.subTest(filename=filename):
                self.assertTrue(self.validator.validate_file_format(filename))
    
    def test_validate_file_format_invalid(self):
        """무효한 파일 형식 테스트"""
        invalid_files = [
            'test.txt',
            'document.doc',
            'file.exe',
            'noextension',
            ''
        ]
        
        for filename in invalid_files:
            with self.subTest(filename=filename):
                self.assertFalse(self.validator.validate_file_format(filename))
    
    def test_validate_file_size_valid(self):
        """유효한 파일 크기 테스트"""
        # 1MB = 1024 * 1024 bytes
        valid_sizes = [
            1024 * 1024,      # 1MB
            10 * 1024 * 1024, # 10MB
            49 * 1024 * 1024  # 49MB (50MB 미만)
        ]
        
        for size in valid_sizes:
            with self.subTest(size=size):
                self.assertTrue(self.validator.validate_file_size(size))
    
    def test_validate_file_size_invalid(self):
        """무효한 파일 크기 테스트"""
        invalid_sizes = [
            51 * 1024 * 1024,  # 51MB
            100 * 1024 * 1024  # 100MB
        ]
        
        for size in invalid_sizes:
            with self.subTest(size=size):
                self.assertFalse(self.validator.validate_file_size(size))
    
    def test_get_file_type(self):
        """파일 타입 감지 테스트"""
        test_cases = [
            ('test.jpg', 'image'),
            ('document.pdf', 'pdf'),
            ('image.PNG', 'image'),
            ('file.txt', 'unknown'),
            ('noext', 'unknown')
        ]
        
        for filename, expected_type in test_cases:
            with self.subTest(filename=filename):
                self.assertEqual(self.validator.get_file_type(filename), expected_type)
    
    def test_validate_multiple_files_success(self):
        """여러 파일 검증 성공 테스트"""
        # 유효한 파일들 생성
        valid_files = [
            SimpleUploadedFile('test1.jpg', b'fake image content', content_type='image/jpeg'),
            SimpleUploadedFile('test2.pdf', b'fake pdf content', content_type='application/pdf')
        ]
        
        result = self.validator.validate_multiple_files(valid_files)
        self.assertEqual(result, valid_files)
    
    def test_validate_multiple_files_empty(self):
        """빈 파일 리스트 검증 테스트"""
        result = self.validator.validate_multiple_files([])
        
        # Response 객체가 반환되어야 함
        self.assertTrue(hasattr(result, 'status_code'))
        self.assertEqual(result.status_code, status.HTTP_400_BAD_REQUEST)
    
    def test_get_validation_summary(self):
        """파일 검증 요약 정보 테스트"""
        files = [
            SimpleUploadedFile('test1.jpg', b'x' * 1024, content_type='image/jpeg'),  # 1KB
            SimpleUploadedFile('test2.pdf', b'y' * 2048, content_type='application/pdf')  # 2KB
        ]
        
        summary = self.validator.get_validation_summary(files)
        
        self.assertEqual(summary['total_files'], 2)
        self.assertAlmostEqual(summary['total_size_mb'], 0.003, places=3)  # ~3KB
        self.assertEqual(summary['file_types']['image'], 1)
        self.assertEqual(summary['file_types']['pdf'], 1)


class TestSecurityFileValidator(TestCase):
    """SecurityFileValidator 테스트"""
    
    def test_is_safe_filename_valid(self):
        """안전한 파일명 테스트"""
        safe_files = [
            'document.pdf',
            'image_001.jpg',
            'test-file.png',
            'report.2024.pdf'
        ]
        
        for filename in safe_files:
            with self.subTest(filename=filename):
                self.assertTrue(SecurityFileValidator.is_safe_filename(filename))
    
    def test_is_safe_filename_invalid(self):
        """위험한 파일명 테스트"""
        dangerous_files = [
            'virus.exe',
            'script.bat',
            '../../../etc/passwd',
            'file with spaces.jpg',  # 공백도 위험할 수 있음
            'file:with:colons.pdf',
            'a' * 300 + '.jpg'  # 너무 긴 파일명
        ]
        
        for filename in dangerous_files:
            with self.subTest(filename=filename):
                self.assertFalse(SecurityFileValidator.is_safe_filename(filename))
    
    def test_sanitize_filename(self):
        """파일명 안전화 테스트"""
        test_cases = [
            ('test file.pdf', 'test_file.pdf'),
            ('document:with:colons.jpg', 'document_with_colons.jpg'),
            ('file/../path.png', 'file___path.png'),
            ('', 'unnamed_file')
        ]
        
        for original, expected in test_cases:
            with self.subTest(original=original):
                result = SecurityFileValidator.sanitize_filename(original)
                self.assertEqual(result, expected)


class TestFileValidatorIntegration(TestCase):
    """FileValidator 통합 테스트"""
    
    def setUp(self):
        self.validator = FileValidator()
    
    def create_test_file(self, name: str, size_mb: float = 1.0, content_type: str = 'image/jpeg'):
        """테스트용 파일 생성"""
        content = b'x' * int(size_mb * 1024 * 1024)
        return SimpleUploadedFile(name, content, content_type=content_type)
    
    def test_full_validation_flow_success(self):
        """전체 검증 플로우 성공 테스트"""
        files = [
            self.create_test_file('valid1.jpg', 1.0),
            self.create_test_file('valid2.pdf', 2.0, 'application/pdf')
        ]
        
        # 개별 검증
        for file in files:
            self.assertTrue(self.validator.validate_file_format(file.name))
            self.assertTrue(self.validator.validate_file_size(file.size))
        
        # 일괄 검증
        result = self.validator.validate_multiple_files(files)
        self.assertEqual(result, files)
        
        # 요약 정보
        summary = self.validator.get_validation_summary(files)
        self.assertEqual(summary['total_files'], 2)
    
    def test_full_validation_flow_failure(self):
        """전체 검증 플로우 실패 테스트"""
        files = [
            self.create_test_file('invalid.txt', 1.0),  # 지원하지 않는 형식
            self.create_test_file('toolarge.jpg', 60.0)  # 크기 초과
        ]
        
        # 첫 번째 파일은 형식 오류로 실패해야 함
        result = self.validator.validate_multiple_files(files)
        self.assertTrue(hasattr(result, 'status_code'))
        self.assertEqual(result.status_code, status.HTTP_400_BAD_REQUEST)


@pytest.mark.django_db
class TestFileValidatorWithDatabase(TestCase):
    """데이터베이스가 필요한 FileValidator 테스트"""
    
    def setUp(self):
        self.validator = FileValidator()
    
    def test_validator_with_custom_config(self):
        """커스텀 설정을 사용한 검증기 테스트"""
        custom_config = FileValidationConfig()
        custom_config.MAX_FILE_SIZE_MB = 10  # 10MB로 제한
        
        validator = FileValidator(custom_config)
        
        # 15MB 파일은 실패해야 함
        large_size = 15 * 1024 * 1024
        self.assertFalse(validator.validate_file_size(large_size))
        
        # 5MB 파일은 성공해야 함
        small_size = 5 * 1024 * 1024
        self.assertTrue(validator.validate_file_size(small_size))