#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import sys
import cv2
import os

def crop_image(image_path, x1, y1, x2, y2, output_path):
    try:
        # 이미지 로드
        img = cv2.imread(image_path)
        if img is None:
            raise ValueError(f"이미지를 로드할 수 없습니다: {image_path}")

        # 좌표 변환
        x1, y1, x2, y2 = int(x1), int(y1), int(x2), int(y2)

        # 이미지 크기 확인 및 좌표 보정
        h, w = img.shape[:2]
        x1 = max(0, min(x1, w-1))
        y1 = max(0, min(y1, h-1))
        x2 = max(x1+1, min(x2, w))
        y2 = max(y1+1, min(y2, h))

        # 크롭
        cropped = img[y1:y2, x1:x2]

        # 출력 디렉토리 생성
        os.makedirs(os.path.dirname(output_path), exist_ok=True)

        # 저장
        cv2.imwrite(output_path, cropped)
        print(f"이미지 크롭 완료: {output_path}")

    except Exception as e:
        print(f"이미지 크롭 실패: {e}")
        sys.exit(1)

if __name__ == "__main__":
    if len(sys.argv) != 7:
        print("사용법: python crop_image.py <image_path> <x1> <y1> <x2> <y2> <output_path>")
        sys.exit(1)

    crop_image(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5], sys.argv[6])
