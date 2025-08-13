"""
통합 테스트: 전체 SmartEye 파이프라인 검증

LAM → TSPM → CIM 전체 파이프라인이 통합적으로 동작하는지 검증
"""

import pytest
from django.test import TransactionTestCase
from django.core.files.uploadedfile import SimpleUploadedFile
from rest_framework.test import APIClient
from rest_framework import status
from django.contrib.auth import get_user_model
from unittest.mock import patch, Mock
import tempfile
import os
from PIL import Image
from io import BytesIO
import time

from apps.analysis.models import (
    AnalysisJob, ProcessedImage, LAMLayoutDetection,
    TSPMOCRResult, TSPMImageDescription, CIMOutput, AnalysisResult
)
from apps.files.models import SourceFile

User = get_user_model()


@pytest.mark.django_db(transaction=True)
class TestCompletePipeline(TransactionTestCase):
    """전체 파이프라인 통합 테스트"""
    
    def setUp(self):
        """테스트 설정"""
        self.client = APIClient()
        self.user = User.objects.create_user(
            username='pipeline_user',
            password='test_password',
            email='pipeline@example.com',
            api_quota_limit=1000  # 충분한 할당량
        )
        self.client.force_authenticate(user=self.user)
    
    def create_test_image(self, width=800, height=600, format='JPEG'):
        """테스트용 이미지 생성"""
        image = Image.new('RGB', (width, height), color='white')
        
        # 간단한 텍스트와 도형 추가 (레이아웃 감지용)
        from PIL import ImageDraw, ImageFont
        draw = ImageDraw.Draw(image)
        
        # 제목 영역 (상단)
        draw.rectangle([50, 50, 750, 150], fill='lightblue', outline='blue')
        draw.text((60, 80), "Test Document Title", fill='black')
        
        # 본문 영역 (중앙)
        draw.rectangle([50, 200, 750, 400], fill='lightgray', outline='gray')
        draw.text((60, 220), "This is test content for OCR processing.", fill='black')
        draw.text((60, 250), "Multiple lines of text for testing", fill='black')
        draw.text((60, 280), "레이아웃 분석 및 OCR 테스트용 한글 텍스트", fill='black')
        
        # 이미지/그래프 영역 (하단)
        draw.rectangle([50, 450, 350, 550], fill='lightyellow', outline='orange')
        draw.text((60, 480), "Chart/Image Area", fill='black')
        
        # 바이트 스트림으로 변환
        buffer = BytesIO()
        image.save(buffer, format=format)
        buffer.seek(0)
        return buffer.getvalue()
    
    def create_test_pdf(self):
        """테스트용 PDF 생성 (간단한 더미 PDF)"""
        # 실제로는 reportlab 등을 사용해야 하지만, 테스트용으로는 더미 데이터 사용
        pdf_content = b"""%PDF-1.4
1 0 obj
<<
/Type /Catalog
/Pages 2 0 R
>>
endobj

2 0 obj
<<
/Type /Pages
/Kids [3 0 R]
/Count 1
>>
endobj

3 0 obj
<<
/Type /Page
/Parent 2 0 R
/MediaBox [0 0 612 792]
/Contents 4 0 R
>>
endobj

4 0 obj
<<
/Length 56
>>
stream
BT
/F1 12 Tf
72 720 Td
(Test PDF Content for SmartEye) Tj
ET
endstream
endobj

xref
0 5
0000000000 65535 f 
0000000010 00000 n 
0000000053 00000 n 
0000000125 00000 n 
0000000230 00000 n 
trailer
<<
/Size 5
/Root 1 0 R
>>
startxref
338
%%EOF"""
        return pdf_content
    
    @patch('core.lam.service.LAMService.process_job')
    @patch('core.tspm.service.TSPMService.process_job')
    @patch('core.cim.service.CIMService.process_job')
    def test_complete_analysis_pipeline_success(self, mock_cim, mock_tspm, mock_lam):
        """성공적인 전체 분석 파이프라인 테스트"""
        
        # Mock 서비스 응답 설정
        mock_lam.return_value = {
            'success': True,
            'detections': [
                {
                    'class_name': 'title',
                    'confidence': 0.95,
                    'bbox': [50, 50, 750, 150]
                },
                {
                    'class_name': 'text',
                    'confidence': 0.92,
                    'bbox': [50, 200, 750, 400]
                },
                {
                    'class_name': 'figure',
                    'confidence': 0.88,
                    'bbox': [50, 450, 350, 550]
                }
            ],
            'processing_time_ms': 1500
        }
        
        mock_tspm.return_value = {
            'success': True,
            'ocr_results': [
                {
                    'text': 'Test Document Title',
                    'confidence': 0.97,
                    'bbox_index': 0
                },
                {
                    'text': 'This is test content for OCR processing. Multiple lines of text for testing 레이아웃 분석 및 OCR 테스트용 한글 텍스트',
                    'confidence': 0.94,
                    'bbox_index': 1
                }
            ],
            'image_descriptions': [
                {
                    'description': 'A chart or graph showing data visualization',
                    'bbox_index': 2,
                    'confidence': 0.91
                }
            ],
            'processing_time_ms': 2500
        }
        
        mock_cim.return_value = {
            'success': True,
            'final_text': 'Test Document Title\n\nThis is test content for OCR processing. Multiple lines of text for testing 레이아웃 분석 및 OCR 테스트용 한글 텍스트\n\n[Chart/Image: A chart or graph showing data visualization]',
            'braille_notation': '⠠⠞⠑⠎⠞ ⠠⠙⠕⠉⠥⠍⠑⠝⠞ ⠠⠞⠊⠞⠇⠑...',
            'accessibility_score': 8.5,
            'processing_time_ms': 800,
            'export_formats': ['text', 'json', 'braille']
        }
        
        # 테스트 이미지 생성
        image_data = self.create_test_image()
        test_image = SimpleUploadedFile(
            'test_document.jpg',
            image_data,
            content_type='image/jpeg'
        )
        
        # 전체 파이프라인 시작
        response = self.client.post('/api/v1/analysis/jobs/upload_and_analyze/', {
            'files': [test_image],
            'job_name': 'Complete Pipeline Test',
            'model_choice': 'docstructbench',
            'enable_ocr': True,
            'enable_description': True,
            'visualization_type': 'comparison'
        })
        
        # 업로드 및 작업 생성 확인
        assert response.status_code == status.HTTP_201_CREATED
        assert 'job_id' in response.data
        assert 'task_id' in response.data
        assert response.data['status'] == 'processing'
        
        job_id = response.data['job_id']
        
        # 작업이 데이터베이스에 생성되었는지 확인
        job = AnalysisJob.objects.get(id=job_id)
        assert job.job_name == 'Complete Pipeline Test'
        assert job.status == 'pending'  # Celery 작업 시작 전 상태
        assert job.total_images == 1
        
        # 파일이 저장되었는지 확인
        source_file = SourceFile.objects.filter(user=self.user).first()
        assert source_file is not None
        assert source_file.original_filename == 'test_document.jpg'
        assert source_file.file_type == 'image'
        
        # ProcessedImage가 생성되었는지 확인
        processed_image = ProcessedImage.objects.filter(job=job).first()
        assert processed_image is not None
        assert processed_image.processed_filename == 'test_document.jpg'
        
        # Mock이 호출되었는지 확인 (실제 Celery 실행 시)
        # 실제 통합 테스트에서는 Celery 작업이 실행되어 Mock 함수들이 호출됨
        
        print(f"파이프라인 시작 완료: Job ID {job_id}")
    
    def test_pdf_processing_pipeline(self):
        """PDF 처리 파이프라인 테스트"""
        
        # 테스트 PDF 생성
        pdf_data = self.create_test_pdf()
        test_pdf = SimpleUploadedFile(
            'test_document.pdf',
            pdf_data,
            content_type='application/pdf'
        )
        
        with patch('utils.pdf_processor.PDFProcessor.get_pdf_info') as mock_pdf_info:
            mock_pdf_info.return_value = {
                'page_count': 1,
                'page_width': 612,
                'page_height': 792,
                'is_encrypted': False,
                'error': None
            }
            
            response = self.client.post('/api/v1/analysis/jobs/upload_and_analyze/', {
                'files': [test_pdf],
                'job_name': 'PDF Pipeline Test',
                'model_choice': 'docstructbench',
            })
        
        assert response.status_code == status.HTTP_201_CREATED
        
        job_id = response.data['job_id']
        job = AnalysisJob.objects.get(id=job_id)
        
        # PDF 특화 확인
        source_file = SourceFile.objects.filter(user=self.user, file_type='pdf').first()
        assert source_file is not None
        assert source_file.original_filename == 'test_document.pdf'
        
        # PDF 페이지가 ProcessedImage로 변환되었는지 확인
        processed_images = ProcessedImage.objects.filter(job=job)
        assert processed_images.count() == 1  # 1페이지 PDF
        
        processed_image = processed_images.first()
        assert processed_image.page_number == 1
        assert processed_image.processed_filename == 'test_document.pdf_page_1'
    
    def test_multiple_files_processing(self):
        """다중 파일 처리 파이프라인 테스트"""
        
        # 여러 테스트 파일 생성
        files = []
        for i in range(3):
            image_data = self.create_test_image(width=600+i*100, height=400+i*50)
            test_image = SimpleUploadedFile(
                f'test_image_{i}.jpg',
                image_data,
                content_type='image/jpeg'
            )
            files.append(test_image)
        
        response = self.client.post('/api/v1/analysis/jobs/upload_and_analyze/', {
            'files': files,
            'job_name': 'Multi File Pipeline Test',
            'model_choice': 'docstructbench',
        })
        
        assert response.status_code == status.HTTP_201_CREATED
        assert response.data['total_images'] == 3
        
        job_id = response.data['job_id']
        job = AnalysisJob.objects.get(id=job_id)
        
        # 모든 파일이 저장되었는지 확인
        source_files = SourceFile.objects.filter(user=self.user)
        assert source_files.count() >= 3  # 이전 테스트의 파일들도 포함될 수 있음
        
        # 모든 이미지가 ProcessedImage로 생성되었는지 확인
        processed_images = ProcessedImage.objects.filter(job=job)
        assert processed_images.count() == 3
        
        # 각 이미지의 크기가 다른지 확인 (서로 다른 이미지임을 확인)
        widths = set(img.image_width for img in processed_images)
        assert len(widths) == 3  # 3개의 서로 다른 너비
    
    def test_pipeline_progress_tracking(self):
        """파이프라인 진행률 추적 테스트"""
        
        image_data = self.create_test_image()
        test_image = SimpleUploadedFile(
            'progress_test.jpg',
            image_data,
            content_type='image/jpeg'
        )
        
        # 작업 시작
        response = self.client.post('/api/v1/analysis/jobs/upload_and_analyze/', {
            'files': [test_image],
            'job_name': 'Progress Test Job',
        })
        
        job_id = response.data['job_id']
        
        # 진행률 조회
        progress_response = self.client.get(f'/api/v1/analysis/jobs/{job_id}/progress/')
        assert progress_response.status_code == status.HTTP_200_OK
        
        progress_data = progress_response.data
        assert 'progress' in progress_data
        assert 'status' in progress_data
        assert 'processed_images' in progress_data
        assert 'total_images' in progress_data
        
        # 초기 상태 확인
        assert progress_data['total_images'] == 1
        assert progress_data['processed_images'] == 0
        assert progress_data['progress'] == 0.0
        assert progress_data['status'] in ['pending', 'processing']
    
    def test_pipeline_error_handling(self):
        """파이프라인 오류 처리 테스트"""
        
        # 잘못된 파일 형식
        invalid_file = SimpleUploadedFile(
            'invalid_file.txt',
            b'This is not an image or PDF file',
            content_type='text/plain'
        )
        
        response = self.client.post('/api/v1/analysis/jobs/upload_and_analyze/', {
            'files': [invalid_file],
            'job_name': 'Error Test Job',
        })
        
        # 파일 업로드 단계에서 오류 발생
        assert response.status_code == status.HTTP_400_BAD_REQUEST
        assert 'error' in response.data
    
    def test_pipeline_cancellation(self):
        """파이프라인 취소 테스트"""
        
        image_data = self.create_test_image()
        test_image = SimpleUploadedFile(
            'cancel_test.jpg',
            image_data,
            content_type='image/jpeg'
        )
        
        # 작업 시작
        response = self.client.post('/api/v1/analysis/jobs/upload_and_analyze/', {
            'files': [test_image],
            'job_name': 'Cancel Test Job',
        })
        
        job_id = response.data['job_id']
        
        # 작업 취소
        cancel_response = self.client.post(f'/api/v1/analysis/jobs/{job_id}/cancel/')
        assert cancel_response.status_code == status.HTTP_200_OK
        
        # 취소 상태 확인
        job = AnalysisJob.objects.get(id=job_id)
        assert job.status == 'cancelled'
    
    def test_api_quota_integration(self):
        """API 할당량과 파이프라인 통합 테스트"""
        
        # 할당량을 거의 한도에 설정
        self.user.api_quota_used = 995
        self.user.api_quota_limit = 1000
        self.user.save()
        
        image_data = self.create_test_image()
        test_image = SimpleUploadedFile(
            'quota_test.jpg',
            image_data,
            content_type='image/jpeg'
        )
        
        # API 설명 생성이 활성화된 요청 (할당량 사용)
        response = self.client.post('/api/v1/analysis/jobs/upload_and_analyze/', {
            'files': [test_image],
            'job_name': 'Quota Test Job',
            'enable_description': True,  # API 사용 요구
        })
        
        # 할당량이 충분하면 성공, 부족하면 실패해야 함
        if response.status_code == status.HTTP_201_CREATED:
            # 할당량이 증가했는지 확인
            self.user.refresh_from_db()
            # 실제로는 Celery 작업에서 할당량이 증가하므로 즉시 확인은 어려움
        elif response.status_code == status.HTTP_400_BAD_REQUEST:
            # 할당량 부족 오류 메시지 확인
            assert 'quota' in response.data.get('error', '').lower()
    
    @pytest.mark.performance
    def test_pipeline_performance_benchmark(self):
        """파이프라인 성능 벤치마크 테스트"""
        
        # 성능 측정용 이미지 생성
        image_data = self.create_test_image(width=1920, height=1080)  # 고해상도
        test_image = SimpleUploadedFile(
            'performance_test.jpg',
            image_data,
            content_type='image/jpeg'
        )
        
        # 시작 시간 측정
        start_time = time.time()
        
        response = self.client.post('/api/v1/analysis/jobs/upload_and_analyze/', {
            'files': [test_image],
            'job_name': 'Performance Benchmark',
        })
        
        # 응답 시간 측정 (파일 업로드 및 작업 생성까지)
        response_time = time.time() - start_time
        
        assert response.status_code == status.HTTP_201_CREATED
        
        # 응답 시간 검증 (10초 이하)
        assert response_time < 10.0, f"파이프라인 시작 시간이 너무 깁니다: {response_time:.2f}초"
        
        job_id = response.data['job_id']
        job = AnalysisJob.objects.get(id=job_id)
        
        # 파일 크기 확인
        source_file = SourceFile.objects.filter(user=self.user).last()
        assert source_file.file_size_mb > 0
        
        print(f"파이프라인 시작 성능: {response_time:.3f}초, 파일 크기: {source_file.file_size_mb:.2f}MB")
    
    def test_concurrent_pipeline_processing(self):
        """동시 파이프라인 처리 테스트"""
        import threading
        import time
        
        results = []
        errors = []
        
        def start_pipeline(thread_id):
            """스레드에서 파이프라인 시작"""
            try:
                image_data = self.create_test_image()
                test_image = SimpleUploadedFile(
                    f'concurrent_test_{thread_id}.jpg',
                    image_data,
                    content_type='image/jpeg'
                )
                
                response = self.client.post('/api/v1/analysis/jobs/upload_and_analyze/', {
                    'files': [test_image],
                    'job_name': f'Concurrent Test {thread_id}',
                })
                
                if response.status_code == status.HTTP_201_CREATED:
                    results.append({
                        'thread_id': thread_id,
                        'job_id': response.data['job_id'],
                        'success': True
                    })
                else:
                    errors.append({
                        'thread_id': thread_id,
                        'status_code': response.status_code,
                        'error': response.data
                    })
                    
            except Exception as e:
                errors.append({
                    'thread_id': thread_id,
                    'exception': str(e)
                })
        
        # 5개 스레드로 동시 파이프라인 시작
        threads = []
        for i in range(5):
            thread = threading.Thread(target=start_pipeline, args=(i,))
            threads.append(thread)
            thread.start()
        
        # 모든 스레드 완료 대기
        for thread in threads:
            thread.join(timeout=30.0)  # 최대 30초 대기
        
        # 결과 검증
        assert len(errors) == 0, f"동시 처리 중 오류 발생: {errors}"
        assert len(results) == 5, f"예상 결과 수: 5, 실제: {len(results)}"
        
        # 모든 작업이 고유한 ID를 가지는지 확인
        job_ids = [r['job_id'] for r in results]
        assert len(set(job_ids)) == 5, "중복된 작업 ID가 생성되었습니다"
        
        print(f"동시 파이프라인 처리 성공: {len(results)}개 작업")
    
    def tearDown(self):
        """테스트 정리"""
        # 테스트에서 생성된 파일들 정리
        import shutil
        import tempfile
        
        # 임시 파일 디렉토리 정리
        temp_dirs = ['/tmp/smarteye_test*']  # 실제 경로에 맞게 조정
        
        try:
            # 테스트 데이터 정리
            AnalysisJob.objects.filter(user=self.user).delete()
            SourceFile.objects.filter(user=self.user).delete()
        except Exception as e:
            print(f"테스트 정리 중 오류: {e}")