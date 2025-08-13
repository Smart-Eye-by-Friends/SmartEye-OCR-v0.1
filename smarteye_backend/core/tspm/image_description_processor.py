"""
SmartEye TSPM Image Description Processor

OpenAI Vision API를 이용한 이미지 설명 생성
"""

import os
import io
import time
import base64
import tempfile
from typing import List, Dict, Any, Optional
from PIL import Image
import cv2
import numpy as np
import logging
logger = logging.getLogger(__name__)

try:
    import openai
    from openai import OpenAI
except ImportError:
    logger.error("OpenAI 라이브러리가 설치되지 않았습니다: pip install openai")
    openai = None

from .config import TSPMConfig
from utils.security_enhancements import get_security_manager


class ImageDescriptionProcessor:
    """이미지 설명 생성 처리기"""
    
    def __init__(self, api_key: str = None):
        # 보안 관리자를 통한 안전한 API 키 획득
        security_manager = get_security_manager()
        self.api_key = api_key or security_manager.get_secure_openai_key() or TSPMConfig.get_openai_api_key()
        
        if not self.api_key:
            raise ValueError("OpenAI API 키가 설정되지 않았습니다.")
        
        if openai is None:
            raise ImportError("OpenAI 라이브러리가 설치되지 않았습니다.")
        
        # OpenAI 클라이언트 초기화 (개선된 설정)
        try:
            self.client = OpenAI(
                api_key=self.api_key,
                timeout=TSPMConfig.OPENAI_TIMEOUT,
                max_retries=TSPMConfig.OPENAI_MAX_RETRIES
            )
        except Exception as e:
            logger.error(f"OpenAI 클라이언트 초기화 실패: {e}")
            raise
        
        # 모델 설정
        self.model = TSPMConfig.OPENAI_MODEL
        self.max_tokens = TSPMConfig.OPENAI_MAX_TOKENS
        self.temperature = TSPMConfig.OPENAI_TEMPERATURE
        self.timeout = TSPMConfig.OPENAI_TIMEOUT
        self.max_retries = TSPMConfig.OPENAI_MAX_RETRIES
        
        logger.info(f"이미지 설명 프로세서 초기화 완료 - 모델: {self.model}")
    
    def process_detection(self, image: np.ndarray, detection: Dict[str, Any]) -> Dict[str, Any]:
        """단일 감지 결과에 대한 이미지 설명 생성"""
        try:
            start_time = time.time()
            
            # 클래스 확인
            class_name = detection.get('class_name', '').lower()
            if class_name not in TSPMConfig.API_TARGET_CLASSES:
                return {
                    'success': False,
                    'error': f'이미지 설명 대상 클래스가 아님: {class_name}'
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
            
            # 이미지 전처리 및 인코딩
            encoded_image = self._encode_image_for_api(cropped_image)
            if not encoded_image:
                return {
                    'success': False,
                    'error': '이미지 인코딩 실패'
                }
            
            # 프롬프트 생성
            prompt = self._get_prompt_for_class(class_name)
            
            # API 호출
            description = self._call_vision_api(encoded_image, prompt)
            if not description:
                return {
                    'success': False,
                    'error': 'API 응답에서 설명을 가져올 수 없음'
                }
            
            processing_time_ms = int((time.time() - start_time) * 1000)
            
            # 주제 분류 
            subject_category = self._classify_subject(description, class_name)
            
            result = {
                'success': True,
                'description_text': description,
                'subject_category': subject_category,
                'description_type': class_name,
                'api_model': self.model,
                'api_cost': self._estimate_api_cost(),
                'processing_time_ms': processing_time_ms
            }
            
            logger.info(f"이미지 설명 완료: {class_name} - {len(description)}자 ({processing_time_ms}ms)")
            return result
            
        except Exception as e:
            logger.error(f"이미지 설명 처리 실패: {e}")
            return {
                'success': False,
                'error': str(e)
            }
    
    def batch_process(self, image: np.ndarray, detections: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """배치 이미지 설명 처리"""
        results = []
        
        api_targets = [
            det for det in detections 
            if det.get('class_name', '').lower() in TSPMConfig.API_TARGET_CLASSES
        ]
        
        logger.info(f"이미지 설명 대상: {len(api_targets)}개 / 전체: {len(detections)}개")
        
        for detection in api_targets:
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
    
    def _encode_image_for_api(self, image: np.ndarray) -> str:
        """API 전송을 위한 이미지 인코딩"""
        try:
            # BGR → RGB 변환 (OpenCV 기본은 BGR)
            if len(image.shape) == 3 and image.shape[2] == 3:
                image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
            else:
                image_rgb = image
            
            # PIL 이미지로 변환
            pil_image = Image.fromarray(image_rgb)
            
            # 크기 조정 (API 제한 고려)
            if max(pil_image.size) > TSPMConfig.MAX_IMAGE_SIZE:
                ratio = TSPMConfig.MAX_IMAGE_SIZE / max(pil_image.size)
                new_size = tuple(int(dim * ratio) for dim in pil_image.size)
                pil_image = pil_image.resize(new_size, Image.Resampling.LANCZOS)
            
            # base64 인코딩
            buffer = io.BytesIO()
            pil_image.save(buffer, format='JPEG', quality=int(TSPMConfig.IMAGE_QUALITY_FACTOR * 100))
            encoded_image = base64.b64encode(buffer.getvalue()).decode('utf-8')
            
            return encoded_image
            
        except Exception as e:
            logger.error(f"이미지 인코딩 실패: {e}")
            return ""
    
    def _get_prompt_for_class(self, class_name: str) -> str:
        """클래스별 프롬프트 반환"""
        return TSPMConfig.API_PROMPTS.get(class_name, TSPMConfig.API_PROMPTS['figure'])
    
    def _call_vision_api(self, encoded_image: str, prompt: str) -> str:
        """OpenAI Vision API 호출 (개선된 에러 처리)"""
        for attempt in range(self.max_retries):
            try:
                logger.debug(f"OpenAI API 호출 시도 {attempt + 1}/{self.max_retries}")
                
                response = self.client.chat.completions.create(
                    model=self.model,
                    messages=[
                        {
                            "role": "user",
                            "content": [
                                {
                                    "type": "text",
                                    "text": prompt
                                },
                                {
                                    "type": "image_url",
                                    "image_url": {
                                        "url": f"data:image/jpeg;base64,{encoded_image}",
                                        "detail": "auto"
                                    }
                                }
                            ]
                        }
                    ],
                    max_tokens=self.max_tokens,
                    temperature=self.temperature,
                    timeout=self.timeout
                )
                
                if response.choices and len(response.choices) > 0:
                    description = response.choices[0].message.content.strip()
                    logger.debug(f"API 응답 성공: {len(description)}자")
                    return description
                else:
                    logger.error("API 응답에 선택사항이 없음")
                    if attempt < self.max_retries - 1:
                        time.sleep(2 ** attempt)  # 지수 백오프
                        continue
                    return ""
                    
            except openai.RateLimitError as e:
                logger.warning(f"API 요청 제한 초과 (시도 {attempt + 1}): {e}")
                if attempt < self.max_retries - 1:
                    time.sleep(5 * (attempt + 1))  # 더 긴 대기
                    continue
                return ""
                
            except openai.APIConnectionError as e:
                logger.warning(f"API 연결 오류 (시도 {attempt + 1}): {e}")
                if attempt < self.max_retries - 1:
                    time.sleep(2 ** attempt)
                    continue
                return ""
                
            except openai.AuthenticationError as e:
                logger.error(f"API 인증 오류: {e}")
                return ""
                
            except Exception as e:
                logger.error(f"OpenAI Vision API 호출 실패 (시도 {attempt + 1}): {e}")
                if attempt < self.max_retries - 1:
                    time.sleep(2 ** attempt)
                    continue
                return ""
        
        logger.error("모든 재시도 실패")
        return ""
    
    def _classify_subject(self, description: str, class_name: str) -> str:
        """설명 내용을 기반으로 주제 분류"""
        try:
            description_lower = description.lower()
            
            # 수학 관련 키워드
            math_keywords = ['수학', '계산', '공식', '그래프', '도형', '표', '차트']
            # 과학 관련 키워드
            science_keywords = ['과학', '실험', '화학', '물리', '생물', '원소', '분자']
            # 언어 관련 키워드
            language_keywords = ['문법', '단어', '문장', '언어', '읽기', '쓰기']
            # 사회 관련 키워드
            social_keywords = ['역사', '지리', '사회', '정치', '경제', '문화']
            
            for keyword in math_keywords:
                if keyword in description_lower:
                    return '수학'
            
            for keyword in science_keywords:
                if keyword in description_lower:
                    return '과학'
            
            for keyword in language_keywords:
                if keyword in description_lower:
                    return '언어'
            
            for keyword in social_keywords:
                if keyword in description_lower:
                    return '사회'
            
            # 클래스명 기반 기본 분류
            if class_name == 'table':
                return '표/데이터'
            elif class_name == 'figure':
                return '그림/도표'
            
            return '일반'
            
        except Exception:
            return '일반'
    
    def _estimate_api_cost(self) -> float:
        """API 호출 비용 추정 (간단한 계산)"""
        # GPT-4 Vision의 대략적인 비용 (실제 토큰 수는 OpenAI에서 계산)
        # 이는 추정치이며 실제 비용과 다를 수 있음
        base_cost = 0.01  # 기본 이미지 처리 비용 (USD)
        text_cost = self.max_tokens * 0.00003  # 텍스트 생성 비용 추정
        return base_cost + text_cost
    
    def test_api_connection(self) -> bool:
        """API 연결 테스트 (개선된 버전)"""
        try:
            # 간단한 텍스트 요청으로 연결 테스트
            response = self.client.chat.completions.create(
                model="gpt-3.5-turbo",
                messages=[{"role": "user", "content": "Test connection"}],
                max_tokens=5,
                timeout=10
            )
            
            if response.choices and len(response.choices) > 0:
                logger.info("OpenAI API 연결 테스트 성공")
                return True
            else:
                logger.error("API 연결 테스트: 응답이 비어있음")
                return False
                
        except openai.AuthenticationError as e:
            logger.error(f"API 인증 실패: {e}")
            return False
        except openai.APIConnectionError as e:
            logger.error(f"API 연결 실패: {e}")
            return False
        except Exception as e:
            logger.error(f"API 연결 테스트 실패: {e}")
            return False
    
    def cleanup(self):
        """리소스 정리"""
        # OpenAI 클라이언트는 특별한 정리가 필요하지 않음
        logger.debug("이미지 설명 프로세서 정리 완료")
