#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import sys
import cv2
import pytesseract

def perform_ocr(image_path, job_id, block_index):
    try:
        # 이미지 로드
        img = cv2.imread(image_path)
        if img is None:
            raise ValueError(f"이미지를 로드할 수 없습니다: {image_path}")

        # OCR 수행 (한국어 + 영어)
        config = r'--oem 3 --psm 6'
        text = pytesseract.image_to_string(img, lang='kor+eng', config=config)

        # 신뢰도 계산 (간단한 휴리스틱)
        confidence = 0.8 if len(text.strip()) > 0 else 0.1

        print(f"OCR_TEXT:{text.strip()}")
        print(f"OCR_CONFIDENCE:{confidence}")

        return text.strip(), confidence

    except Exception as e:
        print(f"OCR 처리 실패: {e}")
        print("OCR_TEXT:")
        print("OCR_CONFIDENCE:0.0")
        return "", 0.0

if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("사용법: python ocr_processing.py <image_path> <job_id> <block_index>")
        sys.exit(1)

    image_path = sys.argv[1]
    job_id = sys.argv[2]
    block_index = sys.argv[3]

    perform_ocr(image_path, job_id, block_index)
