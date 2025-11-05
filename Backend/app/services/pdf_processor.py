# -*- coding: utf-8 -*-
"""
SmartEyeSsen PDF 처리 서비스
============================

PDF 파일을 페이지별 이미지로 변환하는 기능을 제공합니다.
PyMuPDF (fitz)를 사용하여 고품질 이미지 변환을 수행합니다.
"""

from typing import List, Dict, Optional, Tuple
from loguru import logger
import os
import fitz  # PyMuPDF
from PIL import Image
import io
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, as_completed

DEFAULT_PDF_DPI = 300


class PDFProcessor:
    """PDF 파일 처리 클래스"""

    def __init__(self, upload_directory: str = "uploads", dpi: Optional[int] = None):
        """
        PDF 처리기 초기화

        Args:
            upload_directory: 파일 저장 기본 디렉토리
            dpi: 이미지 변환 해상도 (기본값: 300)
        """
        self.upload_directory = Path(upload_directory).resolve()
        self.dpi = self._resolve_dpi(dpi)
        self.jpeg_quality = 95
        os.makedirs(self.upload_directory, exist_ok=True)
        logger.info(
            f"PDFProcessor 초기화 완료 - DPI: {self.dpi}, 저장 경로: {self.upload_directory}"
        )

    @staticmethod
    def _resolve_dpi(provided_dpi: Optional[int]) -> int:
        """환경 변수와 인자 값을 고려해 DPI를 결정"""
        if provided_dpi and provided_dpi > 0:
            return int(provided_dpi)

        env_value = os.getenv("PDF_PROCESSOR_DPI")
        if env_value:
            try:
                parsed = int(env_value)
                if parsed > 0:
                    logger.debug(
                        f"환경 변수 PDF_PROCESSOR_DPI 적용: {parsed} (인자 미지정)"
                    )
                    return parsed
            except ValueError:
                logger.warning(
                    f"환경 변수 PDF_PROCESSOR_DPI 값 '{env_value}'을(를) 정수로 변환할 수 없어 기본값 {DEFAULT_PDF_DPI}을 사용합니다."
                )
        return DEFAULT_PDF_DPI

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
        project_dir = self.upload_directory / str(project_id)
        project_dir.mkdir(parents=True, exist_ok=True)

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
            original_pdf_path = project_dir / "original.pdf"
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
                    full_path = project_dir / filename
                    public_path = Path("uploads") / str(project_id) / filename

                    # 이미지 저장 (JPEG 품질 적용)
                    img.save(str(full_path), "JPEG", quality=self.jpeg_quality, optimize=True)

                    # 변환 정보 저장
                    page_info = {
                        'page_number': page_number,
                        'image_path': str(public_path).replace("\\", "/"),
                        'full_path': str(full_path),
                        'width': width,
                        'height': height,
                        'dpi': self.dpi,
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

    def convert_pdf_to_images_parallel(
        self,
        pdf_bytes: bytes,
        project_id: int,
        start_page_number: int,
        max_workers: Optional[int] = None
    ) -> List[Dict[str, any]]:
        """
        PDF 바이트 데이터를 페이지별 이미지로 병렬 변환하고 저장
        
        Args:
            pdf_bytes: PDF 파일의 바이트 데이터
            project_id: 프로젝트 ID (폴더 경로용)
            start_page_number: 시작 페이지 번호
            max_workers: 최대 워커 스레드 수 (None이면 CPU 코어 수, 최대 4개)
            
        Returns:
            변환된 이미지 정보 리스트
            
        Note:
            ThreadPoolExecutor를 사용하여 여러 페이지를 동시에 변환합니다.
            대용량 PDF의 경우 변환 속도가 2-3배 향상됩니다.
            max_workers를 너무 크게 설정하면 메모리 사용량이 증가할 수 있으므로 주의하세요.
        """
        logger.info(
            f"PDF 병렬 변환 시작 - ProjectID: {project_id}, 시작 페이지: {start_page_number}"
        )

        # 프로젝트별 저장 디렉토리 생성
        project_dir = self.upload_directory / str(project_id)
        project_dir.mkdir(parents=True, exist_ok=True)

        pdf_document = None
        converted_pages = []

        try:
            # PDF 문서 열기
            pdf_document = fitz.open(stream=pdf_bytes, filetype="pdf")
            total_pages = len(pdf_document)
            logger.info(f"PDF 페이지 수: {total_pages}")

            if total_pages == 0:
                raise ValueError("PDF 파일에 페이지가 없습니다.")

            # PDF 원본 파일 저장
            original_pdf_path = project_dir / "original.pdf"
            with open(original_pdf_path, "wb") as f:
                f.write(pdf_bytes)
            logger.info(f"PDF 원본 저장 완료: {original_pdf_path}")

            # 워커 수 결정 (기본: CPU 코어 수, 최대 4개)
            if max_workers is None:
                max_workers = min(os.cpu_count() or 4, 4)
            
            logger.info(f"병렬 변환 시작: {max_workers}개 워커 사용")

            def convert_single_page(page_index: int) -> Dict[str, any]:
                """
                단일 페이지 변환 (완전 독립 실행)
                
                각 스레드가 독립적인 PDF 문서 인스턴스를 생성하여
                진정한 병렬 처리를 수행합니다.
                """
                page_number = start_page_number + page_index

                try:
                    # 각 스레드가 독립적인 PDF 문서 인스턴스 생성
                    # PyMuPDF는 각 Document 객체가 독립적이면 스레드 안전함
                    temp_doc = fitz.open(stream=pdf_bytes, filetype="pdf")
                    page = temp_doc[page_index]

                    # DPI 기반 확대 비율 계산
                    zoom = self.dpi / 72
                    mat = fitz.Matrix(zoom, zoom)
                    pix = page.get_pixmap(matrix=mat, alpha=False)

                    # PIL Image로 변환
                    img_data = pix.tobytes("jpeg")
                    temp_doc.close()

                    img = Image.open(io.BytesIO(img_data))
                    width, height = img.size

                    # 파일명 및 경로 생성
                    filename = f"page_{page_number}.jpg"
                    full_path = project_dir / filename
                    public_path = Path("uploads") / str(project_id) / filename

                    # 이미지 저장
                    img.save(str(full_path), "JPEG", quality=self.jpeg_quality, optimize=True)

                    logger.debug(
                        f"페이지 {page_index + 1}/{total_pages} 변환 완료 - "
                        f"페이지 번호: {page_number}, 크기: {width}x{height}"
                    )

                    return {
                        'page_number': page_number,
                        'image_path': str(public_path).replace("\\", "/"),
                        'full_path': str(full_path),
                        'width': width,
                        'height': height,
                        'dpi': self.dpi,
                    }

                except Exception as e:
                    logger.error(f"페이지 {page_index + 1} 병렬 변환 실패: {str(e)}")
                    raise ValueError(f"PDF 페이지 {page_index + 1} 변환 실패: {str(e)}")

            # ThreadPoolExecutor로 병렬 처리
            with ThreadPoolExecutor(max_workers=max_workers) as executor:
                # 모든 페이지에 대한 Future 생성
                future_to_page = {
                    executor.submit(convert_single_page, i): i 
                    for i in range(total_pages)
                }

                # 완료된 순서대로 결과 수집
                for future in as_completed(future_to_page):
                    page_index = future_to_page[future]
                    try:
                        page_info = future.result()
                        converted_pages.append(page_info)
                    except Exception as e:
                        logger.error(f"페이지 {page_index + 1} 처리 실패: {str(e)}")
                        # 실패 시 롤백
                        self._rollback_conversion(converted_pages)
                        raise

            # 페이지 번호 순으로 정렬
            converted_pages.sort(key=lambda x: x['page_number'])

            logger.info(
                f"PDF 병렬 변환 완료 - ProjectID: {project_id}, "
                f"총 {len(converted_pages)}개 페이지 변환"
            )
            return converted_pages

        except fitz.fitz.FileDataError as e:
            logger.error(f"PDF 파일 오류: {str(e)}")
            raise ValueError(f"PDF 파일이 손상되었거나 읽을 수 없습니다: {str(e)}")

        except Exception as e:
            logger.error(f"PDF 병렬 변환 중 예상치 못한 오류: {str(e)}")
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
UPLOAD_ROOT = os.getenv("UPLOAD_DIR", "uploads")
pdf_processor = PDFProcessor(upload_directory=UPLOAD_ROOT)
