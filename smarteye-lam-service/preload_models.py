#!/usr/bin/env python3
"""
3단계: DocLayout-YOLO 모델 사전 로딩 스크립트
Docker 빌드 시점에 모델을 미리 다운로드하여 초기 실행 시간 단축
"""

import os
import sys
import logging
from pathlib import Path

# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def preload_doclayout_yolo():
    """DocLayout-YOLO 모델 사전 다운로드"""
    try:
        logger.info("DocLayout-YOLO 모델 사전 로딩 시작...")
        
        # 캐시 디렉토리 설정
        cache_dir = os.environ.get('MODEL_CACHE_DIR', '/app/cache')
        torch_cache = os.environ.get('TORCH_CACHE_DIR', f'{cache_dir}/torch')
        hf_cache = os.environ.get('HF_CACHE_DIR', f'{cache_dir}/huggingface')
        
        # 캐시 디렉토리 생성
        Path(cache_dir).mkdir(parents=True, exist_ok=True)
        Path(torch_cache).mkdir(parents=True, exist_ok=True)
        Path(hf_cache).mkdir(parents=True, exist_ok=True)
        
        # 환경 변수 설정
        os.environ['TORCH_HOME'] = torch_cache
        os.environ['HF_HOME'] = hf_cache
        
        # DocLayout-YOLO 모델 임포트 및 초기화
        try:
            from doclayout_yolo import YOLOv10
            
            # 기본 모델 사전 로딩
            model_configs = [
                'D4LA/doclayout_yolo_docstructbench_rvlcdip_D4LA',
                # 필요한 다른 모델들 추가 가능
            ]
            
            for model_config in model_configs:
                try:
                    logger.info(f"모델 로딩 중: {model_config}")
                    model = YOLOv10(model_config)
                    logger.info(f"모델 로딩 완료: {model_config}")
                    
                    # 메모리 정리
                    del model
                    
                except Exception as e:
                    logger.warning(f"모델 로딩 실패 (무시함): {model_config} - {e}")
                    continue
            
            logger.info("DocLayout-YOLO 모델 사전 로딩 완료")
            
        except ImportError as e:
            logger.warning(f"DocLayout-YOLO 라이브러리를 찾을 수 없음: {e}")
            logger.info("런타임에 모델을 로딩합니다.")
            
    except Exception as e:
        logger.error(f"모델 사전 로딩 중 오류 발생: {e}")
        logger.info("런타임에 모델을 로딩합니다.")

def preload_torch_models():
    """PyTorch 관련 모델 사전 로딩"""
    try:
        logger.info("PyTorch 모델 사전 로딩 시작...")
        
        import torch
        import torchvision
        
        # GPU 사용 가능 여부 확인
        if torch.cuda.is_available():
            logger.info(f"CUDA 사용 가능: {torch.cuda.get_device_name()}")
            device = torch.device('cuda')
        else:
            logger.info("CPU 모드로 실행")
            device = torch.device('cpu')
        
        # 기본 torchvision 모델들 사전 로딩 (선택사항)
        # 필요한 경우 주석 해제
        # models = ['resnet50', 'mobilenet_v2']
        # for model_name in models:
        #     try:
        #         model = getattr(torchvision.models, model_name)(pretrained=True)
        #         model.to(device)
        #         del model
        #         logger.info(f"{model_name} 모델 사전 로딩 완료")
        #     except Exception as e:
        #         logger.warning(f"{model_name} 모델 로딩 실패: {e}")
        
        logger.info("PyTorch 모델 사전 로딩 완료")
        
    except ImportError as e:
        logger.warning(f"PyTorch를 찾을 수 없음: {e}")
    except Exception as e:
        logger.error(f"PyTorch 모델 사전 로딩 중 오류: {e}")

def main():
    """메인 사전 로딩 함수"""
    logger.info("=== 모델 사전 로딩 스크립트 시작 ===")
    
    # 환경 확인
    python_version = sys.version
    logger.info(f"Python 버전: {python_version}")
    
    cache_dir = os.environ.get('MODEL_CACHE_DIR', '/app/cache')
    logger.info(f"캐시 디렉토리: {cache_dir}")
    
    # 사전 로딩 실행
    preload_torch_models()
    preload_doclayout_yolo()
    
    logger.info("=== 모델 사전 로딩 스크립트 완료 ===")

if __name__ == "__main__":
    main()
