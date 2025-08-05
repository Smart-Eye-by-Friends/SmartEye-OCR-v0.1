"""
SmartEye TSPM OCR Processor

Tesseract OCR을 이용한 텍스트 추출 처리
"""

import os
import re
import time
import textwrap
import tempfile
from typing import List, Dict, Any, Optional
from PIL import Image
import pytesseract
import cv2
import numpy as np
import logging
logger = logging.getLogger(__name__)

from .config import TSPMConfig


class OCRProcessor:
    """OCR 처리기 클래스"""
    
    def __init__(self, language: str = None):
        self.language = language or TSPMConfig.DEFAULT_OCR_LANGUAGE
        self.config = TSPMConfig.OCR_CONFIG
        
        # Tesseract 설치 확인
        try:
            self.tesseract_version = pytesseract.get_tesseract_version()
            logger.info(f"Tesseract 버전: {self.tesseract_version}")
        except Exception as e:
            logger.error(f"Tesseract 설치 확인 실패: {e}")
            raise RuntimeError("Tesseract OCR이 설치되지 않았습니다.")
    
    def process_detection(self, image: np.ndarray, detection: Dict[str, Any]) -> Dict[str, Any]:
        """단일 감지 결과에 대한 OCR 처리"""
        try:
            start_time = time.time()
            
            # 클래스 확인
            class_name = detection.get('class_name', '').lower()
            if class_name not in TSPMConfig.OCR_TARGET_CLASSES:
                return {
                    'success': False,
                    'error': f'OCR 대상 클래스가 아님: {class_name}'
                }
            
            # 바운딩 박스 좌표
            bbox = [
                detection['bbox_x1'],
                detection['bbox_y1'], 
                detection['bbox_x2'],
                detection['bbox_y2']
            ]
            
            # 이미지 크롭
            cropped_image = self._safe_crop_image(image, bbox)
            if cropped_image.size == 0:
                return {
                    'success': False,
                    'error': '크롭된 이미지가 비어있음'
                }
            
            # 이미지 전처리
            processed_image = self._preprocess_image(cropped_image)
            
            # OCR 실행
            raw_text = self._extract_text(processed_image)
            
            # 텍스트 후처리
            processed_text = self._process_text(raw_text)
            
            processing_time_ms = int((time.time() - start_time) * 1000)
            
            if not processed_text:
                return {
                    'success': False,
                    'error': '추출된 텍스트가 없음'
                }
            
            # 신뢰도 계산 (간단한 휴리스틱)
            confidence = self._calculate_confidence(raw_text, processed_text)
            
            result = {
                'success': True,
                'extracted_text': raw_text,
                'processed_text': processed_text,
                'confidence': confidence,
                'language': self.language,
                'processing_method': 'tesseract',
                'text_length': len(processed_text),
                'processing_time_ms': processing_time_ms
            }
            
            logger.info(f"OCR 완료: {len(processed_text)}자 추출 ({processing_time_ms}ms)")
            return result
            
        except Exception as e:
            logger.error(f"OCR 처리 실패: {e}")
            return {
                'success': False,
                'error': str(e)
            }
    
    def batch_process(self, image: np.ndarray, detections: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """배치 OCR 처리"""
        results = []
        
        ocr_targets = [
            det for det in detections 
            if det.get('class_name', '').lower() in TSPMConfig.OCR_TARGET_CLASSES
        ]
        
        logger.info(f"OCR 대상: {len(ocr_targets)}개 / 전체: {len(detections)}개")
        
        for detection in ocr_targets:
            result = self.process_detection(image, detection)
            results.append({
                'detection_id': detection.get('id'),
                'detection_order': detection.get('detection_order'),
                'class_name': detection.get('class_name'),
                **result
            })
        
        return results
    
    def _safe_crop_image(self, image: np.ndarray, bbox: List[int]) -> np.ndarray:
        """안전한 이미지 크롭"""
        x1, y1, x2, y2 = bbox
        img_height, img_width = image.shape[:2]
        
        # 좌표 보정
        x1 = max(0, min(x1, img_width - 1))
        y1 = max(0, min(y1, img_height - 1))
        x2 = max(x1 + 1, min(x2, img_width))
        y2 = max(y1 + 1, min(y2, img_height))
        
        return image[y1:y2, x1:x2]
    
    def _preprocess_image(self, image: np.ndarray) -> Image.Image:
        """OCR을 위한 이미지 전처리"""
        try:
            # 그레이스케일 변환
            if len(image.shape) == 3:
                gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
            else:
                gray = image
            
            # 노이즈 제거
            denoised = cv2.medianBlur(gray, 3)
            
            # 이진화 (적응적 임계처리)
            binary = cv2.adaptiveThreshold(
                denoised, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, 
                cv2.THRESH_BINARY, 11, 2
            )
            
            # PIL 이미지로 변환
            pil_image = Image.fromarray(binary)
            
            return pil_image
            
        except Exception as e:
            logger.warning(f"이미지 전처리 실패, 원본 사용: {e}")
            # 전처리 실패시 원본 사용
            if len(image.shape) == 3:
                image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
            else:
                image_rgb = cv2.cvtColor(image, cv2.COLOR_GRAY2RGB)
            return Image.fromarray(image_rgb)
    
    def _extract_text(self, image: Image.Image) -> str:
        """Tesseract를 이용한 텍스트 추출"""
        try:
            text = pytesseract.image_to_string(
                image,
                lang=self.language,
                config=self.config
            )
            return text.strip()
            
        except Exception as e:
            logger.error(f"Tesseract 텍스트 추출 실패: {e}")
            return ""
    
    def _process_text(self, raw_text: str) -> str:
        """추출된 텍스트 후처리"""
        if not raw_text or len(raw_text.strip()) <= TSPMConfig.MIN_TEXT_LENGTH:
            return ""
        
        # 기본 정리
        text = raw_text.strip()
        
        # 줄바꿈 정리
        processed_lines = []
        for line in text.split('\n'):
            line = line.strip()
            if not line:
                continue
                
            # 번호나 목록 기호로 시작하는 줄은 그대로 유지
            if re.match(r"^\s*(\d+\.\s|[-*]\s)", line):
                processed_lines.append(line)
            else:
                # 일반 텍스트는 줄바꿈 처리
                wrapped = textwrap.fill(line, width=TSPMConfig.TEXT_WRAP_WIDTH)
                processed_lines.append(wrapped)
        
        result = '\n'.join(processed_lines)
        
        # 길이 제한
        if len(result) > TSPMConfig.MAX_TEXT_LENGTH:
            result = result[:TSPMConfig.MAX_TEXT_LENGTH] + "..."
        
        return result
    
    def _calculate_confidence(self, raw_text: str, processed_text: str) -> float:
        """OCR 신뢰도 계산 (간단한 휴리스틱)"""
        try:
            if not raw_text or not processed_text:
                return 0.0
            
            # 기본 신뢰도
            base_confidence = 0.7
            
            # 텍스트 길이 보정
            if len(processed_text) >= 10:
                base_confidence += 0.1
            elif len(processed_text) >= 20:
                base_confidence += 0.2
            
            # 특수문자 비율 확인
            special_chars = len(re.findall(r'[^\w\s가-힣]', processed_text))
            total_chars = len(processed_text)
            if total_chars > 0:
                special_ratio = special_chars / total_chars
                if special_ratio > 0.3:  # 특수문자가 30% 이상이면 신뢰도 감소
                    base_confidence -= 0.2
            
            # 한글/영문 비율 확인
            korean_chars = len(re.findall(r'[가-힣]', processed_text))
            if korean_chars > 0:
                base_confidence += 0.1
            
            return min(0.99, max(0.1, base_confidence))
            
        except Exception:
            return 0.5  # 기본값
    
    def get_supported_languages(self) -> List[str]:
        """지원되는 언어 목록"""
        try:
            langs = pytesseract.get_languages()
            return langs
        except Exception:
            return list(TSPMConfig.OCR_LANGUAGES.values())
    
    def set_language(self, language: str):
        """OCR 언어 설정"""
        if language in TSPMConfig.OCR_LANGUAGES:
            self.language = TSPMConfig.OCR_LANGUAGES[language]
        else:
            self.language = language
        logger.info(f"OCR 언어 설정: {self.language}")
    
    def cleanup(self):
        """리소스 정리"""
        # OCR 프로세서는 특별한 정리가 필요하지 않음
        logger.debug("OCR 프로세서 정리 완료")
