"""
File Processors 단위 테스트
"""

import pytest
from django.test import TestCase
from django.contrib.auth import get_user_model
from django.core.files.uploadedfile import SimpleUploadedFile
from unittest.mock import Mock, patch, MagicMock

from apps.analysis.models import AnalysisJob, ProcessedImage
from apps.files.models import SourceFile
from utils.file_processors import (
    ImageProcessor, PDFHandler, FileProcessorFactory, 
    BatchFileProcessor, ProcessingResult
)


User = get_user_model()


@pytest.mark.django_db
class TestProcessingResult(TestCase):
    """ProcessingResult 데이터 클래스 테스트"""
    
    def test_processing_result_initialization(self):
        """ProcessingResult 초기화 테스트"""
        result = ProcessingResult(success=True, page_count=5)
        
        self.assertTrue(result.success)
        self.assertEqual(result.page_count, 5)
        self.assertEqual(result.error_message, "")
        self.assertEqual(result.processed_images, [])
    
    def test_processing_result_with_error(self):
        """에러가 있는 ProcessingResult 테스트"""
        result = ProcessingResult(
            success=False, 
            error_message="Test error"
        )
        
        self.assertFalse(result.success)
        self.assertEqual(result.error_message, "Test error")
        self.assertEqual(result.page_count, 0)


@pytest.mark.django_db
class TestImageProcessor(TestCase):
    """ImageProcessor 클래스 테스트"""
    
    def setUp(self):
        self.processor = ImageProcessor()
        self.user = User.objects.create_user(
            username='testuser',
            email='test@example.com',
            password='testpass123'
        )
        self.job = AnalysisJob.objects.create(
            user=self.user,
            job_name='Test Job',
            total_images=1
        )
        self.source_file = SourceFile.objects.create(
            user=self.user,
            original_filename='test.jpg',
            file_type='image'
        )
    
    @patch('PIL.Image.open')
    def test_get_image_dimensions_success(self, mock_image_open):
        """이미지 크기 추출 성공 테스트"""
        # PIL Image 객체 모킹
        mock_img = Mock()
        mock_img.size = (1920, 1080)
        mock_image_open.return_value.__enter__.return_value = mock_img
        
        file = SimpleUploadedFile('test.jpg', b'fake image data')
        dimensions = self.processor.get_image_dimensions(file)
        
        self.assertEqual(dimensions, (1920, 1080))
    
    @patch('PIL.Image.open')
    def test_get_image_dimensions_pil_not_available(self, mock_image_open):
        """PIL 라이브러리가 없을 때 테스트"""
        mock_image_open.side_effect = ImportError("PIL not available")
        
        file = SimpleUploadedFile('test.jpg', b'fake image data')
        dimensions = self.processor.get_image_dimensions(file)
        
        # 기본 크기가 반환되어야 함
        self.assertEqual(dimensions, (1920, 1080))
    
    @patch('PIL.Image.open')
    def test_get_image_dimensions_corrupted_file(self, mock_image_open):
        """손상된 이미지 파일 테스트"""
        mock_image_open.side_effect = Exception("Corrupted image")
        
        file = SimpleUploadedFile('test.jpg', b'fake image data')
        dimensions = self.processor.get_image_dimensions(file)
        
        # 기본 크기가 반환되어야 함
        self.assertEqual(dimensions, (1920, 1080))
    
    @patch('PIL.Image.open')
    def test_validate_image_file_success(self, mock_image_open):
        """이미지 파일 검증 성공 테스트"""
        mock_img = Mock()
        mock_image_open.return_value.__enter__.return_value = mock_img
        
        file = SimpleUploadedFile('test.jpg', b'fake image data')
        is_valid = self.processor.validate_image_file(file)
        
        self.assertTrue(is_valid)
    
    @patch('PIL.Image.open')
    def test_validate_image_file_failure(self, mock_image_open):
        """이미지 파일 검증 실패 테스트"""
        mock_image_open.side_effect = Exception("Invalid image")
        
        file = SimpleUploadedFile('test.jpg', b'fake image data')
        is_valid = self.processor.validate_image_file(file)
        
        self.assertFalse(is_valid)
    
    def test_create_processed_image(self):
        """ProcessedImage 생성 테스트"""
        filename = 'test_image.jpg'
        dimensions = (800, 600)
        
        processed_image = self.processor.create_processed_image(
            source_file=self.source_file,
            job=self.job,
            filename=filename,
            page_num=1,
            dimensions=dimensions
        )
        
        self.assertEqual(processed_image.source_file, self.source_file)
        self.assertEqual(processed_image.job, self.job)
        self.assertEqual(processed_image.processed_filename, filename)
        self.assertEqual(processed_image.page_number, 1)
        self.assertEqual(processed_image.image_width, 800)
        self.assertEqual(processed_image.image_height, 600)
        self.assertEqual(processed_image.processing_status, 'pending')
    
    @patch.object(ImageProcessor, 'validate_image_file')
    @patch.object(ImageProcessor, 'get_image_dimensions')
    @patch.object(ImageProcessor, 'create_processed_image')
    def test_process_image_file_success(self, mock_create, mock_dimensions, mock_validate):
        """이미지 파일 처리 성공 테스트"""
        # 모킹 설정
        mock_validate.return_value = True
        mock_dimensions.return_value = (1920, 1080)
        mock_processed_image = Mock()
        mock_create.return_value = mock_processed_image
        
        file = SimpleUploadedFile('test.jpg', b'fake image data')
        result = self.processor.process_image_file(file, self.source_file, self.job)
        
        self.assertTrue(result.success)
        self.assertEqual(result.page_count, 1)
        self.assertEqual(len(result.processed_images), 1)
    
    @patch.object(ImageProcessor, 'validate_image_file')
    def test_process_image_file_invalid(self, mock_validate):
        """유효하지 않은 이미지 파일 처리 테스트"""
        mock_validate.return_value = False
        
        file = SimpleUploadedFile('test.jpg', b'fake image data')
        result = self.processor.process_image_file(file, self.source_file, self.job)
        
        self.assertFalse(result.success)
        self.assertEqual(result.error_message, "유효하지 않은 이미지 파일입니다.")


@pytest.mark.django_db
class TestPDFHandler(TestCase):
    """PDFHandler 클래스 테스트"""
    
    def setUp(self):
        self.handler = PDFHandler()
        self.user = User.objects.create_user(
            username='testuser',
            email='test@example.com',
            password='testpass123'
        )
        self.job = AnalysisJob.objects.create(
            user=self.user,
            job_name='Test Job',
            total_images=1
        )
        self.source_file = SourceFile.objects.create(
            user=self.user,
            original_filename='test.pdf',
            file_type='pdf'
        )
    
    @patch('utils.file_processors.PDFProcessor')
    def test_validate_pdf_file_success(self, mock_pdf_processor):
        """PDF 파일 검증 성공 테스트"""
        mock_processor_instance = Mock()
        mock_processor_instance.get_pdf_info.return_value = {
            'page_count': 10,
            'page_width': 612,
            'page_height': 792,
            'is_encrypted': False
        }
        mock_pdf_processor.return_value = mock_processor_instance
        
        handler = PDFHandler()
        handler.processor = mock_processor_instance
        
        file = SimpleUploadedFile('test.pdf', b'fake pdf data')
        result = handler.validate_pdf_file(file)
        
        self.assertTrue(result['valid'])
        self.assertEqual(result['pdf_info']['page_count'], 10)
    
    @patch('utils.file_processors.PDFProcessor')
    def test_validate_pdf_file_encrypted(self, mock_pdf_processor):
        """암호화된 PDF 파일 테스트"""
        mock_processor_instance = Mock()
        mock_processor_instance.get_pdf_info.return_value = {
            'is_encrypted': True,
            'error': None
        }
        mock_pdf_processor.return_value = mock_processor_instance
        
        handler = PDFHandler()
        handler.processor = mock_processor_instance
        
        file = SimpleUploadedFile('test.pdf', b'fake pdf data')
        result = handler.validate_pdf_file(file)
        
        self.assertFalse(result['valid'])
        self.assertIn('암호화된 PDF', result['error'])
    
    @patch('utils.file_processors.PDFProcessor')
    def test_validate_pdf_file_too_many_pages(self, mock_pdf_processor):
        """페이지 수가 너무 많은 PDF 테스트"""
        mock_processor_instance = Mock()
        mock_processor_instance.get_pdf_info.return_value = {
            'page_count': 600,  # 500 페이지 제한 초과
            'is_encrypted': False,
            'error': None
        }
        mock_pdf_processor.return_value = mock_processor_instance
        
        handler = PDFHandler()
        handler.processor = mock_processor_instance
        
        file = SimpleUploadedFile('test.pdf', b'fake pdf data')
        result = handler.validate_pdf_file(file)
        
        self.assertFalse(result['valid'])
        self.assertIn('페이지 수가 너무 많습니다', result['error'])
    
    @patch.object(PDFHandler, 'validate_pdf_file')
    def test_process_pdf_file_success(self, mock_validate):
        """PDF 파일 처리 성공 테스트"""
        mock_validate.return_value = {
            'valid': True,
            'pdf_info': {
                'page_count': 3,
                'page_width': 612,
                'page_height': 792
            }
        }
        
        file = SimpleUploadedFile('test.pdf', b'fake pdf data')
        result = self.handler.process_pdf_file(file, self.source_file, self.job)
        
        self.assertTrue(result.success)
        self.assertEqual(result.page_count, 3)
        self.assertEqual(len(result.processed_images), 3)
        
        # 데이터베이스에서 ProcessedImage 확인
        processed_images = ProcessedImage.objects.filter(job=self.job)
        self.assertEqual(processed_images.count(), 3)
    
    @patch.object(PDFHandler, 'validate_pdf_file')
    def test_process_pdf_file_invalid(self, mock_validate):
        """유효하지 않은 PDF 파일 처리 테스트"""
        mock_validate.return_value = {
            'valid': False,
            'error': 'Invalid PDF file'
        }
        
        file = SimpleUploadedFile('test.pdf', b'fake pdf data')
        result = self.handler.process_pdf_file(file, self.source_file, self.job)
        
        self.assertFalse(result.success)
        self.assertEqual(result.error_message, 'Invalid PDF file')


@pytest.mark.django_db
class TestFileProcessorFactory(TestCase):
    """FileProcessorFactory 테스트"""
    
    def setUp(self):
        self.factory = FileProcessorFactory()
    
    def test_get_processor_pdf(self):
        """PDF 파일에 대한 프로세서 반환 테스트"""
        processor = self.factory.get_processor('document.pdf')
        self.assertIsInstance(processor, PDFHandler)
    
    def test_get_processor_image(self):
        """이미지 파일에 대한 프로세서 반환 테스트"""
        for filename in ['image.jpg', 'picture.png', 'photo.JPEG']:
            with self.subTest(filename=filename):
                processor = self.factory.get_processor(filename)
                self.assertIsInstance(processor, ImageProcessor)
    
    @patch.object(PDFHandler, 'process_pdf_file')
    @patch.object(ImageProcessor, 'process_image_file')
    def test_process_file_routing(self, mock_image_process, mock_pdf_process):
        """파일 타입에 따른 처리 라우팅 테스트"""
        user = User.objects.create_user(username='test', password='pass')
        job = AnalysisJob.objects.create(user=user, job_name='test')
        source_file = SourceFile.objects.create(user=user, original_filename='test')
        
        # PDF 파일 처리
        pdf_file = SimpleUploadedFile('test.pdf', b'pdf data')
        self.factory.process_file(pdf_file, source_file, job)
        mock_pdf_process.assert_called_once()
        
        # 이미지 파일 처리
        img_file = SimpleUploadedFile('test.jpg', b'image data')
        self.factory.process_file(img_file, source_file, job)
        mock_image_process.assert_called_once()


@pytest.mark.django_db
class TestBatchFileProcessor(TestCase):
    """BatchFileProcessor 테스트"""
    
    def setUp(self):
        self.processor = BatchFileProcessor()
        self.user = User.objects.create_user(
            username='testuser',
            email='test@example.com',
            password='testpass123'
        )
        self.job = AnalysisJob.objects.create(
            user=self.user,
            job_name='Batch Test Job',
            total_images=0
        )
    
    @patch('django.core.files.storage.default_storage.save')
    @patch.object(FileProcessorFactory, 'process_file')
    def test_process_multiple_files_success(self, mock_process_file, mock_storage_save):
        """여러 파일 처리 성공 테스트"""
        # 모킹 설정
        mock_storage_save.return_value = 'uploads/test.jpg'
        mock_process_file.return_value = ProcessingResult(
            success=True, 
            page_count=1,
            processed_images=[Mock()]
        )
        
        files = [
            SimpleUploadedFile('test1.jpg', b'image data 1'),
            SimpleUploadedFile('test2.jpg', b'image data 2')
        ]
        
        result = self.processor.process_multiple_files(files, self.user, self.job)
        
        self.assertEqual(result['total_images'], 2)
        self.assertEqual(result['success_count'], 2)
        self.assertEqual(result['failure_count'], 0)
        self.assertEqual(len(result['processed_files']), 2)
        self.assertEqual(len(result['failed_files']), 0)
    
    @patch('django.core.files.storage.default_storage.save')
    @patch.object(FileProcessorFactory, 'process_file')
    def test_process_multiple_files_mixed_results(self, mock_process_file, mock_storage_save):
        """여러 파일 처리 - 성공/실패 혼합 테스트"""
        # 모킹 설정
        mock_storage_save.return_value = 'uploads/test.jpg'
        
        # 첫 번째 파일은 성공, 두 번째 파일은 실패
        mock_process_file.side_effect = [
            ProcessingResult(success=True, page_count=1),
            ProcessingResult(success=False, error_message='Processing failed')
        ]
        
        files = [
            SimpleUploadedFile('success.jpg', b'good image data'),
            SimpleUploadedFile('fail.jpg', b'bad image data')
        ]
        
        result = self.processor.process_multiple_files(files, self.user, self.job)
        
        self.assertEqual(result['total_images'], 1)
        self.assertEqual(result['success_count'], 1)
        self.assertEqual(result['failure_count'], 1)
        self.assertEqual(len(result['processed_files']), 1)
        self.assertEqual(len(result['failed_files']), 1)
        self.assertEqual(result['failed_files'][0]['error'], 'Processing failed')
    
    @patch('django.core.files.storage.default_storage.save')
    def test_create_source_file(self, mock_storage_save):
        """SourceFile 생성 테스트"""
        mock_storage_save.return_value = 'uploads/test.jpg'
        
        file = SimpleUploadedFile('test.jpg', b'image data')
        source_file = self.processor._create_source_file(self.user, file)
        
        self.assertEqual(source_file.user, self.user)
        self.assertEqual(source_file.original_filename, 'test.jpg')
        self.assertEqual(source_file.file_type, 'image')
        self.assertEqual(source_file.upload_status, 'completed')
        self.assertEqual(source_file.storage_path, 'uploads/test.jpg')
        
        # 데이터베이스에 저장되었는지 확인
        self.assertTrue(SourceFile.objects.filter(id=source_file.id).exists())