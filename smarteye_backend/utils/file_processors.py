"""
File Processing Utilities

파일 처리 관련 유틸리티 모듈
"""

import logging
from typing import Tuple, Optional, Dict, Any
from dataclasses import dataclass
from apps.files.models import SourceFile
from apps.analysis.models import AnalysisJob, ProcessedImage
from utils.pdf_processor import PDFProcessor
from utils.file_validation import FileValidationConfig

logger = logging.getLogger(__name__)


@dataclass
class ProcessingResult:
    """처리 결과 데이터 클래스"""
    success: bool
    page_count: int = 0
    error_message: str = ""
    processed_images: list = None
    
    def __post_init__(self):
        if self.processed_images is None:
            self.processed_images = []


class ImageProcessor:
    """이미지 처리 클래스"""
    
    def __init__(self):
        self.logger = logger
        self.default_size = FileValidationConfig.DEFAULT_IMAGE_SIZE
    
    def get_image_dimensions(self, file) -> Tuple[int, int]:
        """이미지 크기 추출"""
        try:
            from PIL import Image
            
            # 파일 포인터를 처음으로 되돌림
            file.seek(0)
            
            with Image.open(file) as img:
                width, height = img.size
                self.logger.debug(f"Image dimensions extracted: {width}x{height}")
                return width, height
                
        except ImportError:
            self.logger.warning("PIL이 설치되지 않았습니다. 기본 크기를 사용합니다.")
            return self.default_size
        except Exception as e:
            self.logger.warning(f"이미지 크기 추출 실패: {e}. 기본 크기를 사용합니다.")
            return self.default_size
        finally:
            # 파일 포인터를 다시 처음으로 되돌림
            try:
                file.seek(0)
            except Exception:
                pass
    
    def validate_image_file(self, file) -> bool:
        """이미지 파일 유효성 검증"""
        try:
            from PIL import Image
            
            file.seek(0)
            with Image.open(file) as img:
                # 이미지 로드 테스트
                img.verify()
                return True
        except ImportError:
            # PIL이 없으면 기본적으로 유효하다고 가정
            return True
        except Exception as e:
            self.logger.error(f"이미지 파일 검증 실패: {e}")
            return False
        finally:
            try:
                file.seek(0)
            except Exception:
                pass
    
    def create_processed_image(self, source_file: SourceFile, job: AnalysisJob, 
                             filename: str, page_num: int = 1, 
                             dimensions: Tuple[int, int] = None) -> ProcessedImage:
        """ProcessedImage 객체 생성"""
        if dimensions is None:
            dimensions = self.default_size
        
        processed_image = ProcessedImage.objects.create(
            source_file=source_file,
            job=job,
            processed_filename=filename,
            page_number=page_num,
            image_width=dimensions[0],
            image_height=dimensions[1],
            processing_status='pending'
        )
        
        self.logger.debug(f"ProcessedImage 생성됨: {filename} ({dimensions[0]}x{dimensions[1]})")
        return processed_image
    
    def process_image_file(self, file, source_file: SourceFile, job: AnalysisJob) -> ProcessingResult:
        """단일 이미지 파일 처리"""
        try:
            # 이미지 유효성 검증
            if not self.validate_image_file(file):
                return ProcessingResult(
                    success=False,
                    error_message="유효하지 않은 이미지 파일입니다."
                )
            
            # 이미지 크기 추출
            dimensions = self.get_image_dimensions(file)
            
            # ProcessedImage 생성
            processed_image = self.create_processed_image(
                source_file=source_file,
                job=job,
                filename=file.name,
                page_num=1,
                dimensions=dimensions
            )
            
            return ProcessingResult(
                success=True,
                page_count=1,
                processed_images=[processed_image]
            )
            
        except Exception as e:
            self.logger.error(f"이미지 파일 처리 실패: {e}")
            return ProcessingResult(
                success=False,
                error_message=f"이미지 파일 처리 중 오류: {str(e)}"
            )


class PDFHandler:
    """PDF 처리 핸들러 클래스"""
    
    def __init__(self, scale_factor: float = 2.0, dpi: int = 150):
        self.processor = PDFProcessor(scale_factor=scale_factor, dpi=dpi)
        self.logger = logger
    
    def validate_pdf_file(self, file) -> Dict[str, Any]:
        """PDF 파일 유효성 검증 및 정보 추출"""
        try:
            pdf_info = self.processor.get_pdf_info(file)
            
            if pdf_info.get('error'):
                return {
                    'valid': False,
                    'error': f'PDF 파일을 처리할 수 없습니다: {pdf_info["error"]}'
                }
            
            if pdf_info.get('is_encrypted'):
                return {
                    'valid': False,
                    'error': '암호화된 PDF는 지원하지 않습니다.'
                }
            
            page_count = pdf_info.get('page_count', 0)
            if page_count > 500:  # 최대 페이지 제한
                return {
                    'valid': False,
                    'error': f'PDF 페이지 수가 너무 많습니다 ({page_count}페이지). 최대 500페이지까지 지원합니다.'
                }
            
            return {
                'valid': True,
                'pdf_info': pdf_info
            }
            
        except ImportError:
            return {
                'valid': False,
                'error': 'PDF 처리를 위한 라이브러리가 설치되지 않았습니다.'
            }
        except Exception as e:
            return {
                'valid': False,
                'error': f'PDF 검증 중 오류가 발생했습니다: {str(e)}'
            }
    
    def process_pdf_file(self, file, source_file: SourceFile, job: AnalysisJob) -> ProcessingResult:
        """PDF 파일 처리 및 페이지별 이미지 생성"""
        try:
            # PDF 파일 검증
            validation_result = self.validate_pdf_file(file)
            if not validation_result['valid']:
                return ProcessingResult(
                    success=False,
                    error_message=validation_result['error']
                )
            
            pdf_info = validation_result['pdf_info']
            page_count = pdf_info['page_count']
            default_dimensions = (
                int(pdf_info.get('page_width', 1920)),
                int(pdf_info.get('page_height', 1080))
            )
            
            processed_images = []
            
            # 각 페이지를 ProcessedImage로 생성
            for page_num in range(1, page_count + 1):
                processed_image = ProcessedImage.objects.create(
                    source_file=source_file,
                    job=job,
                    processed_filename=f"{file.name}_page_{page_num}",
                    page_number=page_num,
                    image_width=default_dimensions[0],
                    image_height=default_dimensions[1],
                    processing_status='pending'
                )
                processed_images.append(processed_image)
            
            self.logger.info(f"PDF 처리 준비 완료: {page_count} 페이지")
            
            return ProcessingResult(
                success=True,
                page_count=page_count,
                processed_images=processed_images
            )
            
        except Exception as e:
            self.logger.error(f"PDF 파일 처리 실패: {e}")
            return ProcessingResult(
                success=False,
                error_message=f'PDF 처리 중 오류가 발생했습니다: {str(e)}'
            )
    
    def extract_pdf_metadata(self, file) -> Dict[str, Any]:
        """PDF 메타데이터 추출"""
        try:
            return self.processor.extract_metadata(file)
        except Exception as e:
            self.logger.warning(f"PDF 메타데이터 추출 실패: {e}")
            return {}


class FileProcessorFactory:
    """파일 프로세서 팩토리 클래스"""
    
    def __init__(self):
        self.image_processor = ImageProcessor()
        self.pdf_handler = PDFHandler()
    
    def get_processor(self, filename: str):
        """파일 타입에 따른 적절한 프로세서 반환"""
        if filename.lower().endswith('.pdf'):
            return self.pdf_handler
        else:
            return self.image_processor
    
    def process_file(self, file, source_file: SourceFile, job: AnalysisJob) -> ProcessingResult:
        """파일 타입에 따라 적절한 처리 수행"""
        if file.name.lower().endswith('.pdf'):
            return self.pdf_handler.process_pdf_file(file, source_file, job)
        else:
            return self.image_processor.process_image_file(file, source_file, job)


class BatchFileProcessor:
    """배치 파일 처리 클래스"""
    
    def __init__(self):
        self.factory = FileProcessorFactory()
        self.logger = logger
    
    def process_multiple_files(self, files: list, user, job: AnalysisJob) -> Dict[str, Any]:
        """여러 파일 일괄 처리"""
        total_images = 0
        processed_files = []
        failed_files = []
        
        for file in files:
            try:
                # SourceFile 생성
                source_file = self._create_source_file(user, file)
                
                # 파일 처리
                result = self.factory.process_file(file, source_file, job)
                
                if result.success:
                    total_images += result.page_count
                    processed_files.append({
                        'filename': file.name,
                        'page_count': result.page_count,
                        'source_file_id': source_file.id
                    })
                else:
                    failed_files.append({
                        'filename': file.name,
                        'error': result.error_message
                    })
                    self.logger.error(f"파일 처리 실패: {file.name} - {result.error_message}")
                
            except Exception as e:
                failed_files.append({
                    'filename': file.name,
                    'error': str(e)
                })
                self.logger.error(f"파일 처리 중 예외 발생: {file.name} - {e}")
        
        return {
            'total_images': total_images,
            'processed_files': processed_files,
            'failed_files': failed_files,
            'success_count': len(processed_files),
            'failure_count': len(failed_files)
        }
    
    def _create_source_file(self, user, file) -> SourceFile:
        """SourceFile 생성"""
        from django.core.files.storage import default_storage
        
        # 파일 저장
        file_path = default_storage.save(f'uploads/{file.name}', file)
        
        source_file = SourceFile.objects.create(
            user=user,
            original_filename=file.name,
            stored_filename=file.name,
            file_type='pdf' if file.name.lower().endswith('.pdf') else 'image',
            file_size_mb=file.size / (1024 * 1024),
            upload_status='completed',
            storage_path=file_path
        )
        
        return source_file