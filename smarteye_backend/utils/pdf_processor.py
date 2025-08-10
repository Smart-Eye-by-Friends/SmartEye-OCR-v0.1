"""
PDF 파일 처리를 위한 유틸리티
"""
import logging
import tempfile
from typing import List, Tuple, Dict, Any
from pathlib import Path
import os

logger = logging.getLogger(__name__)


class PDFProcessor:
    """PDF 처리를 위한 클래스"""
    
    def __init__(self, scale_factor: float = 2.0, dpi: int = 150):
        """
        Args:
            scale_factor: 이미지 스케일링 비율 (기본 2.0x)
            dpi: DPI 설정 (기본 150)
        """
        self.scale_factor = scale_factor
        self.dpi = dpi
        
    def extract_pages_as_images(self, pdf_file, temp_dir: str = None) -> List[Dict[str, Any]]:
        """
        PDF 파일에서 페이지를 이미지로 추출
        
        Args:
            pdf_file: PDF 파일 객체
            temp_dir: 임시 디렉토리
            
        Returns:
            List[Dict]: 페이지 정보 목록 [{'image_path': str, 'page_num': int, 'width': int, 'height': int}]
        """
        try:
            import fitz  # PyMuPDF
            
            # PDF 파일 읽기
            if hasattr(pdf_file, 'read'):
                pdf_data = pdf_file.read()
                pdf_file.seek(0)  # 포인터 리셋
                doc = fitz.open(stream=pdf_data, filetype="pdf")
            else:
                doc = fitz.open(pdf_file)
            
            pages_info = []
            
            for page_num in range(len(doc)):
                try:
                    page = doc.load_page(page_num)
                    
                    # 변환 매트릭스 생성 (스케일링 + DPI)
                    zoom = self.scale_factor * (self.dpi / 72.0)
                    matrix = fitz.Matrix(zoom, zoom)
                    
                    # 페이지를 이미지로 렌더링
                    pix = page.get_pixmap(matrix=matrix)
                    
                    # 임시 파일로 저장
                    temp_file = tempfile.NamedTemporaryFile(
                        suffix='.png',
                        prefix=f'pdf_page_{page_num + 1}_',
                        dir=temp_dir,
                        delete=False
                    )
                    
                    pix.save(temp_file.name)
                    
                    pages_info.append({
                        'image_path': temp_file.name,
                        'page_num': page_num + 1,
                        'width': pix.width,
                        'height': pix.height,
                        'original_page': page_num
                    })
                    
                    logger.debug(f"PDF 페이지 {page_num + 1} 변환 완료: {pix.width}x{pix.height}")
                    
                except Exception as e:
                    logger.error(f"PDF 페이지 {page_num + 1} 변환 실패: {e}")
                    continue
            
            doc.close()
            
            logger.info(f"PDF 변환 완료: 총 {len(pages_info)}개 페이지")
            return pages_info
            
        except ImportError:
            logger.error("PyMuPDF (fitz) 라이브러리가 설치되지 않았습니다.")
            raise ImportError("PyMuPDF is required for PDF processing")
            
        except Exception as e:
            logger.error(f"PDF 처리 실패: {e}")
            raise
    
    def get_pdf_info(self, pdf_file) -> Dict[str, Any]:
        """
        PDF 파일의 메타데이터 정보 추출
        
        Args:
            pdf_file: PDF 파일 객체
            
        Returns:
            Dict: PDF 메타데이터
        """
        try:
            import fitz
            
            if hasattr(pdf_file, 'read'):
                pdf_data = pdf_file.read()
                pdf_file.seek(0)
                doc = fitz.open(stream=pdf_data, filetype="pdf")
            else:
                doc = fitz.open(pdf_file)
            
            metadata = doc.metadata
            page_count = len(doc)
            
            # 첫 번째 페이지 크기 정보
            first_page = doc.load_page(0)
            page_rect = first_page.rect
            
            info = {
                'page_count': page_count,
                'title': metadata.get('title', ''),
                'author': metadata.get('author', ''),
                'subject': metadata.get('subject', ''),
                'creator': metadata.get('creator', ''),
                'producer': metadata.get('producer', ''),
                'creation_date': metadata.get('creationDate', ''),
                'modification_date': metadata.get('modDate', ''),
                'page_width': page_rect.width,
                'page_height': page_rect.height,
                'is_encrypted': doc.is_encrypted,
                'needs_password': doc.needs_pass if hasattr(doc, 'needs_pass') else False
            }
            
            doc.close()
            
            logger.debug(f"PDF 정보 추출 완료: {page_count} 페이지")
            return info
            
        except Exception as e:
            logger.error(f"PDF 정보 추출 실패: {e}")
            return {'page_count': 0, 'error': str(e)}
    
    def cleanup_temp_images(self, pages_info: List[Dict[str, Any]]):
        """
        임시 이미지 파일들 정리
        
        Args:
            pages_info: extract_pages_as_images()에서 반환된 페이지 정보 목록
        """
        for page_info in pages_info:
            try:
                image_path = page_info.get('image_path')
                if image_path and os.path.exists(image_path):
                    os.unlink(image_path)
                    logger.debug(f"임시 이미지 파일 삭제: {image_path}")
            except OSError as e:
                logger.warning(f"임시 파일 삭제 실패 {image_path}: {e}")


def process_pdf_to_images(pdf_file, scale_factor: float = 2.0, dpi: int = 150) -> Tuple[List[Dict[str, Any]], Dict[str, Any]]:
    """
    편의 함수: PDF를 이미지로 변환하고 메타데이터 추출
    
    Args:
        pdf_file: PDF 파일 객체
        scale_factor: 스케일링 비율
        dpi: DPI 설정
        
    Returns:
        Tuple: (페이지 정보 목록, PDF 메타데이터)
    """
    processor = PDFProcessor(scale_factor=scale_factor, dpi=dpi)
    
    # PDF 정보 추출
    pdf_info = processor.get_pdf_info(pdf_file)
    
    # 암호화된 PDF나 오류가 있는 경우
    if pdf_info.get('error') or pdf_info.get('is_encrypted'):
        return [], pdf_info
    
    # 페이지를 이미지로 변환
    pages_info = processor.extract_pages_as_images(pdf_file)
    
    return pages_info, pdf_info