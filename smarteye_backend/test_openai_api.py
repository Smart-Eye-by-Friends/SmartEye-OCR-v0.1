#!/usr/bin/env python3
"""
OpenAI API 연결 및 이미지 설명 기능 테스트 스크립트
"""

import os
import sys
import django
import logging
from pathlib import Path

# Django 설정
sys.path.append('/home/jongyoung3/SmartEye_v0.1/smarteye_backend')
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'smarteye.settings.development')
django.setup()

from core.tspm.image_description_processor import ImageDescriptionProcessor
from core.tspm.config import TSPMConfig
import cv2
import numpy as np

# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def test_openai_connection():
    """OpenAI API 연결 테스트"""
    print("🔍 OpenAI API 연결 테스트 시작...")
    
    try:
        # API 키 확인
        api_key = TSPMConfig.get_openai_api_key()
        if not api_key:
            print("❌ OpenAI API 키가 설정되지 않았습니다.")
            return False
        
        print(f"✅ API 키 확인됨: {api_key[:10]}...")
        
        # 이미지 설명 프로세서 초기화
        processor = ImageDescriptionProcessor()
        print(f"✅ 프로세서 초기화 완료 (모델: {processor.model})")
        
        # API 연결 테스트
        if processor.test_api_connection():
            print("✅ OpenAI API 연결 성공")
            return True
        else:
            print("❌ OpenAI API 연결 실패")
            return False
            
    except Exception as e:
        print(f"❌ 테스트 중 오류 발생: {e}")
        return False

def test_image_description():
    """이미지 설명 생성 테스트"""
    print("\n🖼️  이미지 설명 생성 테스트 시작...")
    
    try:
        # 테스트 이미지 경로
        test_image_path = "/tmp/test_image.jpg"
        
        if not os.path.exists(test_image_path):
            print(f"❌ 테스트 이미지를 찾을 수 없습니다: {test_image_path}")
            return False
        
        print(f"✅ 테스트 이미지 확인: {test_image_path}")
        
        # 이미지 로드
        image = cv2.imread(test_image_path)
        if image is None:
            print("❌ 이미지 로드 실패")
            return False
            
        print(f"✅ 이미지 로드 완료: {image.shape}")
        
        # 프로세서 초기화
        processor = ImageDescriptionProcessor()
        
        # 가짜 탐지 결과 (그림 영역으로 가정)
        h, w = image.shape[:2]
        fake_detection = {
            'id': 1,
            'class_name': 'figure',
            'detection_order': 1,
            'bbox_x1': int(w * 0.1),
            'bbox_y1': int(h * 0.1),
            'bbox_x2': int(w * 0.9),
            'bbox_y2': int(h * 0.9),
            'confidence': 0.95
        }
        
        print("🎯 가짜 탐지 결과로 이미지 설명 생성 중...")
        
        # 이미지 설명 생성
        result = processor.process_detection(image, fake_detection)
        
        if result['success']:
            print("✅ 이미지 설명 생성 성공!")
            print(f"📝 설명: {result['description_text']}")
            print(f"🏷️  주제 분류: {result['subject_category']}")
            print(f"⏱️  처리 시간: {result['processing_time_ms']}ms")
            print(f"💰 추정 비용: ${result['api_cost']:.4f}")
            return True
        else:
            print(f"❌ 이미지 설명 생성 실패: {result['error']}")
            return False
            
    except Exception as e:
        print(f"❌ 이미지 설명 테스트 중 오류 발생: {e}")
        return False

def main():
    """메인 테스트 함수"""
    print("🚀 SmartEye OpenAI API 통합 테스트 시작\n")
    
    # 1. 연결 테스트
    connection_ok = test_openai_connection()
    
    if not connection_ok:
        print("\n❌ API 연결 테스트 실패. 이미지 설명 테스트를 건너뜁니다.")
        return False
    
    # 2. 이미지 설명 테스트
    description_ok = test_image_description()
    
    # 결과 요약
    print("\n" + "="*50)
    print("📊 테스트 결과 요약")
    print("="*50)
    print(f"API 연결: {'✅ 성공' if connection_ok else '❌ 실패'}")
    print(f"이미지 설명: {'✅ 성공' if description_ok else '❌ 실패'}")
    
    if connection_ok and description_ok:
        print("\n🎉 모든 테스트 통과! OpenAI API 통합이 정상 작동합니다.")
        return True
    else:
        print("\n⚠️  일부 테스트 실패. 로그를 확인하세요.")
        return False

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)