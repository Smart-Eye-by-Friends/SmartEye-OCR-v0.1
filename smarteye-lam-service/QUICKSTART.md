# SmartEye LAM Service - 빠른 시작 가이드

## 1. Docker를 사용한 빠른 실행

```bash
# 리포지토리 클론
git clone https://github.com/your-repo/SmartEye_v0.1.git
cd SmartEye_v0.1/smarteye-lam-service

# 서비스 시작 (모델 자동 다운로드)
docker-compose up -d

# 로그 확인
docker-compose logs -f

# 서비스 상태 확인
curl http://localhost:8081/health

# 모델 정보 확인
curl http://localhost:8081/model/info | jq '.'
```

## 2. 로컬 환경 실행

```bash
# Python 가상환경 생성
python -m venv venv
source venv/bin/activate  # Linux/Mac
# 또는
venv\Scripts\activate     # Windows

# 의존성 설치
pip install -r requirements.txt

# 환경변수 설정 (선택사항)
cp .env.example .env
# .env 파일 편집

# 서비스 실행
python -m uvicorn app.main:app --host 0.0.0.0 --port 8081
```

## 3. 다른 모델 사용하기

### docstructbench (기본값) - 학습지/교과서 최적화
```bash
docker run -d \
  --name smarteye-lam \
  -p 8081:8081 \
  -e LAM_MODEL_CHOICE=docstructbench \
  smarteye-lam:latest
```

### doclaynet - 일반 문서 최적화
```bash
docker run -d \
  --name smarteye-lam \
  -p 8081:8081 \
  -e LAM_MODEL_CHOICE=doclaynet \
  smarteye-lam:latest
```

### docsynth300k - 연구용 모델
```bash
docker run -d \
  --name smarteye-lam \
  -p 8081:8081 \
  -e LAM_MODEL_CHOICE=docsynth300k \
  smarteye-lam:latest
```

### SmartEyeSsen - 쎈 수학 파인튜닝 모델
```bash
docker run -d \
  --name smarteye-lam \
  -p 8081:8081 \
  -e LAM_MODEL_CHOICE=SmartEyeSsen \
  smarteye-lam:latest
```

## 4. API 사용 예제

### Python 클라이언트
```python
import requests

# 이미지 분석
with open('document.jpg', 'rb') as f:
    response = requests.post(
        'http://localhost:8081/analyze',
        files={'file': f},
        data={'confidence_threshold': 0.5}
    )

result = response.json()
print(f"감지된 객체 수: {result['detected_objects_count']}")

for block in result['layout_blocks']:
    print(f"- {block['class_name']}: {block['confidence']:.2f}")
```

### cURL 예제
```bash
# 이미지 업로드 및 분석
curl -X POST http://localhost:8081/analyze \
  -H "Content-Type: multipart/form-data" \
  -F "file=@document.jpg" \
  -F "confidence_threshold=0.5"
```

## 5. GPU 사용하기

```bash
# GPU 사용 설정
docker run -d \
  --name smarteye-lam \
  --gpus all \
  -p 8081:8081 \
  -e LAM_USE_GPU=true \
  -e LAM_GPU_DEVICE=0 \
  smarteye-lam:latest
```

## 6. 개발 모드

```bash
# 개발 서버 실행 (핫 리로드)
uvicorn app.main:app --reload --host 0.0.0.0 --port 8081

# 테스트 실행
pytest

# 커버리지 확인
pytest --cov=app
```

## 7. 문제 해결

### 모델 다운로드 실패
```bash
# 컨테이너 로그 확인
docker logs smarteye-lam-service

# 수동 모델 다운로드 테스트
python -c "
from app.config import get_settings
settings = get_settings()
print(settings.download_model())
"
```

### 메모리 부족
```bash
# 더 작은 모델 사용
docker run -e LAM_MODEL_CHOICE=docstructbench ...

# 이미지 크기 제한
docker run -e LAM_MAX_IMAGE_SIZE=2048 ...
```

### 네트워크 문제
```bash
# 프록시 설정
docker run \
  -e HTTP_PROXY=http://proxy:8080 \
  -e HTTPS_PROXY=http://proxy:8080 \
  ...
```

## 8. 성능 최적화

### 동시 요청 수 조정
```bash
docker run -e LAM_MAX_CONCURRENT_REQUESTS=5 ...
```

### 타임아웃 설정
```bash
docker run -e LAM_REQUEST_TIMEOUT=60 ...
```

### 모델 캐시 유지
```bash
# 볼륨 마운트로 모델 캐시 유지
docker run -v model-cache:/app/models ...
```
