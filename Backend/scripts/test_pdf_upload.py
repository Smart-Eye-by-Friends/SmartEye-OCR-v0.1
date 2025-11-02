#!/usr/bin/env python3
"""
PDF 업로드 기능 테스트 스크립트
"""

import io
import sys
from pathlib import Path

# Backend 경로 추가
backend_root = Path(__file__).parent.parent
sys.path.insert(0, str(backend_root))

from PIL import Image
import fitz  # PyMuPDF


def create_sample_pdf(output_path: str = "test_sample.pdf", num_pages: int = 3):
    """
    테스트용 샘플 PDF 생성 (텍스트가 있는 3페이지 PDF)

    Args:
        output_path: 저장할 PDF 파일 경로
        num_pages: 생성할 페이지 수
    """
    doc = fitz.open()  # 새 PDF 문서 생성

    for page_num in range(1, num_pages + 1):
        page = doc.new_page(width=595, height=842)  # A4 크기

        # 텍스트 추가
        text = f"테스트 페이지 {page_num}"
        point = fitz.Point(100, 100)
        page.insert_text(point, text, fontsize=20, color=(0, 0, 0))

        # 간단한 도형 추가
        rect = fitz.Rect(100, 150, 400, 300)
        page.draw_rect(rect, color=(0, 0, 1), width=2)
        page.insert_text(fitz.Point(150, 200), f"Sample Box - Page {page_num}", fontsize=14)

    doc.save(output_path)
    doc.close()

    print(f"✅ 샘플 PDF 생성 완료: {output_path} ({num_pages}페이지)")
    return output_path


def test_pdf_processor():
    """PDF 처리 모듈 단위 테스트"""
    from app.services.pdf_processor import pdf_processor

    print("=" * 60)
    print("PDF 처리 모듈 테스트 시작")
    print("=" * 60)

    # 1. 샘플 PDF 생성
    pdf_path = create_sample_pdf("test_sample.pdf", num_pages=3)

    # 2. PDF 바이트 읽기
    with open(pdf_path, "rb") as f:
        pdf_bytes = f.read()

    print(f"\n📄 PDF 파일 크기: {len(pdf_bytes):,} bytes")

    # 3. PDF → 이미지 변환 테스트
    try:
        project_id = 999  # 테스트용 프로젝트 ID
        start_page_number = 1

        converted_pages = pdf_processor.convert_pdf_to_images(
            pdf_bytes=pdf_bytes,
            project_id=project_id,
            start_page_number=start_page_number
        )

        print(f"\n✅ PDF 변환 성공:")
        print(f"   - 총 {len(converted_pages)}개 페이지 변환")

        for page_info in converted_pages:
            print(f"   - 페이지 {page_info['page_number']}: "
                  f"{page_info['width']}x{page_info['height']}px, "
                  f"경로: {page_info['image_path']}")

            # 변환된 이미지 파일 존재 확인
            if Path(page_info['full_path']).exists():
                size_kb = Path(page_info['full_path']).stat().st_size / 1024
                print(f"      → 파일 크기: {size_kb:.1f} KB")
            else:
                print(f"      → ⚠️ 파일 없음: {page_info['full_path']}")

        # 4. PDF 메타데이터 추출 테스트
        pdf_info = pdf_processor.get_pdf_info(pdf_bytes)
        print(f"\n📋 PDF 메타데이터:")
        print(f"   - 총 페이지 수: {pdf_info['total_pages']}")
        print(f"   - 제목: {pdf_info.get('title', 'N/A')}")
        print(f"   - 작성자: {pdf_info.get('author', 'N/A')}")

        print("\n" + "=" * 60)
        print("✅ PDF 처리 모듈 테스트 완료!")
        print("=" * 60)

        # 5. 생성된 파일 정리 (선택)
        cleanup = input("\n생성된 테스트 파일을 삭제하시겠습니까? (y/n): ")
        if cleanup.lower() == 'y':
            import shutil
            Path(pdf_path).unlink(missing_ok=True)
            shutil.rmtree(f"uploads/{project_id}", ignore_errors=True)
            print("✅ 테스트 파일 삭제 완료")
        else:
            print(f"테스트 파일 유지: {pdf_path}, uploads/{project_id}/")

        return True

    except Exception as e:
        print(f"\n❌ PDF 변환 실패: {str(e)}")
        import traceback
        traceback.print_exc()
        return False


if __name__ == "__main__":
    success = test_pdf_processor()
    sys.exit(0 if success else 1)
