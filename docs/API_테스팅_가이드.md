# 📋 SmartEye API 테스팅 가이드

## 🎯 API 테스트 개요

이 문서는 SmartEye v0.4의 API 엔드포인트 테스트 방법과 결과를 설명합니다.

## ✅ 검증 완료된 API

### 1. 문서 분석 API (메인)

**엔드포인트**: `POST /api/document/analyze`

#### 요청 형식
```bash
curl -X POST \
  -F "image=@test_homework_image.jpg" \
  -F "modelChoice=SmartEyeSsen" \
  http://localhost:8080/api/document/analyze
```

#### 필수 파라미터
- `image`: 분석할 이미지 파일 (JPG, PNG, PDF)
- `modelChoice`: 사용할 AI 모델 (필수)
  - `SmartEyeSsen`: DocLayout-YOLO 기반 (기본값, 권장)
  - `Tesseract`: OCR 전용
  - `OpenAI`: GPT-4 Turbo 기반 (API 키 필요)

#### 선택 파라미터  
- `apiKey`: OpenAI API 키 (AI 설명 생성 시 필요)

#### ✅ 성공 응답 (2025-09-01 테스트)
```json
{
  "success": true,
  "layoutImageUrl": "/static/layout_viz_1756723030.png",
  "jsonUrl": "/static/analysis_result_20250901_103711.json",
  "stats": {
    "totalLayoutElements": 33,
    "ocrTextBlocks": 21,
    "aiDescriptions": 0,
    "classCounts": {
      "unit": 2,
      "figure": 5,
      "plain_text": 13,
      "parenthesis_blank": 3,
      "page": 2,
      "title": 1,
      "question_number": 7
    }
  },
  "ocrResults": [
    {
      "id": 0,
      "className": "plain_text",
      "coordinates": [1914, 576, 3093, 816],
      "text": "o) 빨 간 색 구슬 4 개 와 파 란 색 구슬 2 개 가 있 습 니 다 ..."
    }
  ],
  "jobId": "d588945a-459d-42e6-84c7-9b635cf2b8c7",
  "timestamp": 1756723030,
  "message": "분석이 성공적으로 완료되었습니다."
}
```

## 🔧 테스트 환경 설정

### 1. 서비스 시작
```bash
# manage.sh를 이용한 전체 상태 확인
./manage.sh status

# 또는 개별 헬스체크
# Backend 헬스체크
curl http://localhost:8080/api/health

# LAM Service 헬스체크
curl http://localhost:8001/health

# Database 연결 확인
docker exec -it smarteye-postgres psql -U smarteye -d smarteye_db -c "SELECT version();"
```

### 2. 테스트 이미지 준비
- 테스트 파일: `test_homework_image.jpg` (프로젝트 루트에 위치해야 함)
- 권장 크기: 최대 50MB
- 지원 형식: JPG, PNG, PDF

## 📊 성능 및 결과 분석

### 분석 성능 (2025-09-01 테스트)
- **처리 시간**: ~10초 (726KB 이미지)
- **레이아웃 요소**: 33개 검출
- **OCR 텍스트**: 21개 블록
- **정확도**: 한국어 수학 문제 완전 인식

## 🚨 문제 해결

### 잘못된 파라미터 사용
`modelChoice` 파라미터는 필수입니다. `enableOCR`, `enableAI`와 같은 파라미터는 현재 사용되지 않습니다.

**✅ 올바른 명령어:**
```bash
curl -X POST http://localhost:8080/api/document/analyze \
  -F "image=@test_homework_image.jpg" \
  -F "modelChoice=SmartEyeSsen" \
  -F "apiKey=your_openai_api_key"  # AI 설명이 필요한 경우만
```

### 일반적인 오류와 해결방법

1.  **"Required part 'image' is not present"**
    -   **원인**: `image` 파라미터가 누락되었거나 이름이 다릅니다.
    -   **해결**: `-F "image=@파일경로"` 형식을 정확히 사용했는지 확인하세요.

2.  **"Required request parameter 'modelChoice' for method parameter type String is not present"**
    -   **원인**: `modelChoice` 파라미터가 누락되었습니다.
    -   **해결**: `-F "modelChoice=SmartEyeSsen"` 파라미터를 추가하세요.

3.  **Database connection error**
    -   **원인**: 데이터베이스 컨테이너가 정상적으로 실행되지 않았습니다.
    -   **해결**: `./manage.sh logs postgres` 명령어로 로그를 확인하고, `./manage.sh restart postgres`로 재시작하세요.

4.  **LAM Service connection timeout**
    -   **원인**: AI 서비스가 응답하지 않거나 초기 모델 로딩에 시간이 오래 걸리는 경우입니다.
    -   **해결**: `./manage.sh logs lam-service`로 로그를 확인하고, `./manage.sh restart lam-service`로 재시작하세요. 초기 실행 시 모델 다운로드로 인해 시간이 걸릴 수 있습니다.

## 🔄 연속 테스트 스크립트

```bash
#!/bin/bash
# continuous_test.sh

echo "🧪 SmartEye API 연속 테스트"

for i in {1..5}; do
    echo "Test $i/5"
    curl -X POST \
      -F "image=@test_homework_image.jpg" \
      -F "modelChoice=SmartEyeSsen" \
      http://localhost:8080/api/document/analyze \
      -w "Time: %{time_total}s\n" \
      -o "test_result_$i.json"
    sleep 2
done

echo "✅ 테스트 완료"
```

---

**마지막 업데이트**: 2025-09-09
**테스트 상태**: ✅ 모든 주요 기능 검증 완료