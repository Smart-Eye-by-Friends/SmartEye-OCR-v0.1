#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import sys
import cv2
import numpy as np
import json
import os

def mock_yolo_analysis(image_path, job_id):
    """
    DocLayout-YOLO 모의 분석
    실제 환경에서는 실제 YOLO 모델을 로드하여 사용
    """
    try:
        # 이미지 로드
        img = cv2.imread(image_path)
        if img is None:
            raise ValueError(f"이미지를 로드할 수 없습니다: {image_path}")

        height, width = img.shape[:2]

        # 모의 레이아웃 분석 결과 생성
        layout_blocks = [
            {
                "class_name": "title",
                "confidence": 0.95,
                "x1": int(width * 0.1),
                "y1": int(height * 0.05),
                "x2": int(width * 0.9),
                "y2": int(height * 0.15)
            },
            {
                "class_name": "plain text",
                "confidence": 0.88,
                "x1": int(width * 0.1),
                "y1": int(height * 0.2),
                "x2": int(width * 0.9),
                "y2": int(height * 0.6)
            },
            {
                "class_name": "table",
                "confidence": 0.82,
                "x1": int(width * 0.15),
                "y1": int(height * 0.65),
                "x2": int(width * 0.85),
                "y2": int(height * 0.9)
            }
        ]

        # 결과 출력 (Java에서 파싱)
        for i, block in enumerate(layout_blocks):
            print(f"LAYOUT_BLOCK:{i}:{block['class_name']}:{block['confidence']}:{block['x1']}:{block['y1']}:{block['x2']}:{block['y2']}")

        print(f"ANALYSIS_COMPLETE:{len(layout_blocks)}")

        return layout_blocks

    except Exception as e:
        print(f"레이아웃 분석 실패: {e}")
        print("ANALYSIS_FAILED")
        return []

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("사용법: python layout_analysis.py <image_path> <job_id>")
        sys.exit(1)

    image_path = sys.argv[1]
    job_id = sys.argv[2]

    mock_yolo_analysis(image_path, job_id)
