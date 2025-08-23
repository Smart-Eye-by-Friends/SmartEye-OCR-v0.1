# SmartEye LAM (Layout Analysis Module) 마이크로서비스

## 개요
DocLayout-YOLO 기반 문서 레이아웃 분석을 위한 독립적인 FastAPI 마이크로서비스

허깅페이스에서 자동으로 모델을 다운로드하고, GitHub에서 DocLayout-YOLO를 설치하여 즉시 사용 가능한 서비스입니다.

## 주요 기능
- **자동 모델 다운로드**: 허깅페이스에서 사전 훈련된 모델 자동 다운로드
- **GitHub 자동 설치**: DocLayout-YOLO 리포지토리 자동 클론 및 설치
- **다중 모델 지원**: docstructbench, doclaynet, docsynth300k 모델 지원
- **GPU/CPU 자동 감지**: 환경에 따른 최적 디바이스 선택
- **REST API**: RESTful API를 통한 간편한 레이아웃 분석

## 기술 스택
- **FastAPI**: 고성능 비동기 웹 프레임워크
- **DocLayout-YOLO**: 문서 레이아웃 분석 모델
- **HuggingFace Hub**: 모델 다운로드 및 관리
- **OpenCV**: 이미지 처리
- **Uvicorn**: ASGI 서버
- **Pydantic**: 데이터 검증

## 지원 모델

### 1. DocStructBench (기본값)
- **Repository**: `juliozhao/DocLayout-YOLO-DocStructBench`
- **파일명**: `doclayout_yolo_docstructbench_imgsz1024.pt`
- **용도**: 학습지/교과서 최적화 모델

### 2. DocLayNet
- **Repository**: `juliozhao/DocLayout-YOLO-DocLayNet-Docsynth300K_pretrained`
- **파일명**: `doclayout_yolo_doclaynet_imgsz1120_docsynth_pretrain.pt`
- **용도**: 일반 문서 최적화 모델

### 3. DocSynth300K
- **Repository**: `juliozhao/DocLayout-YOLO-DocSynth300K-pretrain`
- **파일명**: `doclayout_yolo_docsynth300k_imgsz1600.pt`
- **용도**: 사전훈련 모델 (연구용)

### 4. SmartEyeSsen
- **Repository**: `AkJeond/SmartEyeSsen`
- **파일명**: `best_tuned_model.pt`
- **용도**: SmartEye 쎈 수학 파인튜닝 모델

## API 엔드포인트

### POST /analyze/layout
문서 이미지의 레이아웃 분석

**Request:**
```json
{
  "image_path": "/path/to/image.jpg",
  "job_id": "unique_job_id",
  "options": {
    "confidence_threshold": 0.5,
    "model_version": "latest"
  }
}
```

**Response:**
```json
{
  "job_id": "unique_job_id",
  "status": "success",
  "processing_time_ms": 1500,
  "image_info": {
    "width": 1920,
    "height": 1080,
    "format": "JPEG"
  },
  "layout_blocks": [
    {
      "block_index": 0,
      "class_name": "title",
      "confidence": 0.95,
      "coordinates": {
        "x1": 100, "y1": 50,
        "x2": 800, "y2": 150
      },
      "area": 105000
    }
  ],
  "detected_objects_count": 3,
  "model_used": "DocLayout-YOLO-v1.0"
}
```

### GET /health
서비스 상태 확인

### GET /models/info
사용 가능한 모델 정보 조회

## 설치 및 실행

```bash
# 의존성 설치
pip install -r requirements.txt

# 개발 서버 실행
uvicorn app.main:app --host 0.0.0.0 --port 8081 --reload

# Docker 실행
docker build -t smarteye-lam-service .
docker run -p 8081:8081 smarteye-lam-service
```

## 환경 변수

- `LAM_MODEL_PATH`: DocLayout-YOLO 모델 파일 경로
- `LAM_CONFIDENCE_THRESHOLD`: 기본 신뢰도 임계값 (default: 0.5)
- `LAM_MAX_IMAGE_SIZE`: 최대 이미지 크기 (default: 4096x4096)
- `LAM_LOG_LEVEL`: 로그 레벨 (default: INFO)
