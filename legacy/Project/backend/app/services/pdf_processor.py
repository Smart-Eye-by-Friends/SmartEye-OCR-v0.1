# -*- coding: utf-8 -*-
"""
SmartEyeSsen PDF 처리 서비스
============================

PDF 파일을 페이지별 이미지로 변환하는 기능을 제공합니다.
PyMuPDF (fitz)를 사용하여 고품질 이미지 변환을 수행합니다.
"""

from typing import List, Dict, Optional
from loguru import logger
import os
import fitz  # PyMuPDF
from PIL import Image
import io
from pathlib import Path


class PDFProcessor:
    """PDF 파일 처리 클래스"""

    def __init__(self, upload_directory: str = "uploads", dpi: int = 300):
        """
        PDF 처리기 초기화

        Args:
            upload_directory: 파일 저장 기본 디렉토리
            dpi: 이미지 변환 해상도 (기본값: 300)
        """
        self.upload_directory = upload_directory
        self.dpi = dpi
        self.jpeg_quality = 95
        os.makedirs(upload_directory, exist_ok=True)
        logger.info(f"PDFProcessor 초기화 완료 - DPI: {dpi}, 저장 경로: {upload_directory}")

    def convert_pdf_to_images(
        self,
        pdf_bytes: bytes,
        project_id: int,
        start_page_number: int
    ) -> List[Dict[str, any]]:
        """
        PDF 바이트 데이터를 페이지별 이미지로 변환하고 저장

        Args:
            pdf_bytes: PDF 파일의 바이트 데이터
            project_id: 프로젝트 ID (폴더 경로용)
            start_page_number: 시작 페이지 번호

        Returns:
            변환된 이미지 정보 리스트
            [
                {
                    'page_number': 1,
                    'image_path': '123/page_1.jpg',  # DB 저장용 상대 경로
                    'full_path': 'uploads/123/page_1.jpg',  # 실제 파일 경로
                    'width': 2480,
                    'height': 3508
                },
                ...
            ]

        Raises:
            ValueError: PDF 파일이 손상되었거나 읽을 수 없는 경우
            OSError: 파일 저장 중 디스크 오류 발생 시
        """
        logger.info(f"PDF 변환 시작 - ProjectID: {project_id}, 시작 페이지: {start_page_number}")

        # 프로젝트별 저장 디렉토리 생성
        project_dir = os.path.join(self.upload_directory, str(project_id))
        os.makedirs(project_dir, exist_ok=True)

        converted_pages = []
        pdf_document = None

        try:
            # PDF 문서 열기
            pdf_document = fitz.open(stream=pdf_bytes, filetype="pdf")
            total_pages = len(pdf_document)
            logger.info(f"PDF 페이지 수: {total_pages}")

            if total_pages == 0:
                raise ValueError("PDF 파일에 페이지가 없습니다.")

            # PDF 원본 파일 저장
            original_pdf_path = os.path.join(project_dir, "original.pdf")
            with open(original_pdf_path, "wb") as f:
                f.write(pdf_bytes)
            logger.info(f"PDF 원본 저장 완료: {original_pdf_path}")

            # 각 페이지를 이미지로 변환
            for page_index in range(total_pages):
                page_number = start_page_number + page_index

                try:
                    # PDF 페이지를 Pixmap으로 렌더링
                    page = pdf_document[page_index]

                    # DPI 기반 확대 비율 계산 (72 DPI가 기본)
                    zoom = self.dpi / 72
                    mat = fitz.Matrix(zoom, zoom)
                    pix = page.get_pixmap(matrix=mat, alpha=False)

                    # PIL Image로 변환
                    img_data = pix.tobytes("jpeg")
                    img = Image.open(io.BytesIO(img_data))

                    # 이미지 크기
                    width, height = img.size

                    # 파일명 및 경로 생성
                    filename = f"page_{page_number}.jpg"
                    full_path = os.path.join(project_dir, filename)
                    relative_path = os.path.join(str(project_id), filename)

                    # 이미지 저장 (JPEG 품질 적용)
                    img.save(full_path, "JPEG", quality=self.jpeg_quality, optimize=True)

                    # 변환 정보 저장
                    page_info = {
                        'page_number': page_number,
                        'image_path': relative_path,
                        'full_path': full_path,
                        'width': width,
                        'height': height
                    }
                    converted_pages.append(page_info)

                    logger.debug(
                        f"페이지 {page_index + 1}/{total_pages} 변환 완료 - "
                        f"페이지 번호: {page_number}, 크기: {width}x{height}"
                    )

                except Exception as e:
                    logger.error(f"페이지 {page_index + 1} 변환 실패: {str(e)}")
                    # 부분 변환 실패 시 롤백
                    self._rollback_conversion(converted_pages)
                    raise ValueError(f"PDF 페이지 {page_index + 1} 변환 실패: {str(e)}")

            logger.info(
                f"PDF 변환 완료 - ProjectID: {project_id}, "
                f"총 {len(converted_pages)}개 페이지 변환"
            )
            return converted_pages

        except fitz.fitz.FileDataError as e:
            logger.error(f"PDF 파일 오류: {str(e)}")
            raise ValueError(f"PDF 파일이 손상되었거나 읽을 수 없습니다: {str(e)}")

        except Exception as e:
            logger.error(f"PDF 변환 중 예상치 못한 오류: {str(e)}")
            if converted_pages:
                self._rollback_conversion(converted_pages)
            raise

        finally:
            # PDF 문서 닫기
            if pdf_document:
                pdf_document.close()

    def _rollback_conversion(self, converted_pages: List[Dict[str, any]]) -> None:
        """
        변환 실패 시 생성된 이미지 파일 롤백

        Args:
            converted_pages: 롤백할 페이지 정보 리스트
        """
        logger.warning(f"변환 롤백 시작 - {len(converted_pages)}개 파일 삭제")

        for page_info in converted_pages:
            try:
                full_path = page_info.get('full_path')
                if full_path and os.path.exists(full_path):
                    os.remove(full_path)
                    logger.debug(f"파일 삭제: {full_path}")
            except Exception as e:
                logger.error(f"롤백 중 파일 삭제 실패: {full_path}, 오류: {str(e)}")

        logger.info("변환 롤백 완료")

    def get_pdf_info(self, pdf_bytes: bytes) -> Dict[str, any]:
        """
        PDF 파일의 메타데이터 추출

        Args:
            pdf_bytes: PDF 파일의 바이트 데이터

        Returns:
            PDF 정보 딕셔너리
            {
                'total_pages': 10,
                'title': '문서 제목',
                'author': '작성자',
                'subject': '주제',
                'creator': '생성 프로그램',
                'producer': 'PDF 생성기',
                'creation_date': '생성 날짜'
            }
        """
        try:
            pdf_document = fitz.open(stream=pdf_bytes, filetype="pdf")
            metadata = pdf_document.metadata

            info = {
                'total_pages': len(pdf_document),
                'title': metadata.get('title', ''),
                'author': metadata.get('author', ''),
                'subject': metadata.get('subject', ''),
                'creator': metadata.get('creator', ''),
                'producer': metadata.get('producer', ''),
                'creation_date': metadata.get('creationDate', '')
            }

            pdf_document.close()
            logger.debug(f"PDF 메타데이터 추출 완료: {info}")
            return info

        except Exception as e:
            logger.error(f"PDF 메타데이터 추출 실패: {str(e)}")
            raise ValueError(f"PDF 파일 정보를 읽을 수 없습니다: {str(e)}")


# 전역 인스턴스 생성 (싱글톤 패턴)
pdf_processor = PDFProcessor(upload_directory="uploads", dpi=300)
