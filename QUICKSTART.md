# 🚀 SmartEye v0.4 - 빠른 시작 가이드

## 📋 개요

SmartEye v0.4는 Java/Spring Boot 기반의 OCR 문서 분석 시스템입니다. Docker Compose를 통해 마이크로서비스 아키텍처로 구성되어 있습니다.

## ⚡ 빠른 시작 (5분 완료)

### 1. 시스템 시작
```bash
cd /home/jongyoung3/SmartEye_v0.4
./start_services.sh
```

### 2. 시스템 검증
```bash
./system-validation.sh
```

### 3. API 테스트
```bash
curl -X POST \
  -F "image=@test_homework_image.jpg" \
  -F "modelChoice=SmartEyeSsen" \
  http://localhost:8080/api/document/analyze | jq .
```

## 🎯 기본 사용법

### API 엔드포인트
- **URL**: `POST /api/document/analyze`
- **Content-Type**: `multipart/form-data`

### 필수 파라미터
- `image`: 분석할 이미지 파일 (JPG, PNG, PDF)
- `modelChoice`: 분석 모델 선택
  - `SmartEyeSsen`: DocLayout-YOLO 기반 (권장)
  - `Tesseract`: OCR 전용
  - `OpenAI`: GPT-4 기반

### 응답 예시
```json
{
  "success": true,
  "layoutImageUrl": "/static/layout_viz_1756723030.png",
  "jsonUrl": "/static/analysis_result_20250901_103711.json",
  "stats": {
    "totalLayoutElements": 33,
    "ocrTextBlocks": 21,
    "classCounts": {
      "plain_text": 13,
      "question_number": 7,
      "figure": 5
    }
  },
  "jobId": "d588945a-459d-42e6-84c7-9b635cf2b8c7"
}
```

## 🔧 시스템 관리

### 서비스 상태 확인
```bash
docker ps
```

### 로그 확인
```bash
# 전체 로그
docker-compose logs -f

# 개별 서비스 로그
docker-compose logs -f smarteye-backend
docker-compose logs -f smarteye-lam-service
```

### 서비스 중지
```bash
docker-compose down
```

### 서비스 재시작
```bash
docker-compose restart
```

## 🌐 접속 정보

| 서비스 | URL | 용도 |
|--------|-----|------|
| Backend API | http://localhost:8080 | 메인 API |
| LAM Service | http://localhost:8001 | AI 모델 서비스 |
| Health Check | http://localhost:8080/actuator/health | 시스템 상태 |
| Static Files | http://localhost:8080/static/ | 분석 결과 |

## 🚨 문제 해결

### 일반적인 문제
1. **포트 충돌**: 8080, 8001, 5433 포트가 사용 중인지 확인
2. **Docker 메모리**: Docker에 충분한 메모리(4GB+) 할당 확인
3. **테스트 이미지**: `test_homework_image.jpg` 파일 존재 확인

### 에러 해결
```bash
# 컨테이너 완전 재시작
docker-compose down --volumes
./start_services.sh

# 이미지 재빌드
docker-compose build --no-cache
docker-compose up -d
```

## 📖 추가 문서
- [API 테스팅 가이드](API_TESTING.md)
- [프로젝트 완료 보고서](PROJECT_COMPLETION.md)
- [시스템 아키텍처](README.md)

---
**SmartEye v0.4** - 마지막 업데이트: 2025-09-01
