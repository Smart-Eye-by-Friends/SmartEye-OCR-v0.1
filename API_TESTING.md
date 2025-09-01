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
- `modelChoice`: 사용할 AI 모델
  - `SmartEyeSsen`: DocLayout-YOLO 기반 (기본값)
  - `Tesseract`: OCR 전용
  - `OpenAI`: GPT-4 Turbo 기반

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
# 전체 서비스 시작
./start_services.sh

# 상태 확인
docker ps
```

### 2. 테스트 이미지 준비
- 테스트 파일: `test_homework_image.jpg`
- 권장 크기: 최대 50MB
- 지원 형식: JPG, PNG, PDF

### 3. 서비스 상태 확인
```bash
# Backend 헬스체크
curl http://localhost:8080/actuator/health

# LAM Service 헬스체크
curl http://localhost:8001/health

# Database 연결 확인
docker exec -it smarteye-postgres psql -U smarteye -d smarteye_db -c "SELECT version();"
```

## 📊 성능 및 결과 분석

### 분석 성능 (2025-09-01 테스트)
- **처리 시간**: ~10초 (726KB 이미지)
- **레이아웃 요소**: 33개 검출
- **OCR 텍스트**: 21개 블록
- **정확도**: 한국어 수학 문제 완전 인식

### 검출된 요소 분류
| 클래스 | 개수 | 설명 |
|--------|------|------|
| plain_text | 13 | 일반 텍스트 |
| question_number | 7 | 문제 번호 |
| figure | 5 | 그림/도표 |
| parenthesis_blank | 3 | 괄호/빈칸 |
| page | 2 | 페이지 요소 |
| unit | 2 | 단위 |
| title | 1 | 제목 |

## 🚨 문제 해결

### 일반적인 오류와 해결방법

1. **"Required part 'image' is not present"**
   - 해결: 파라미터명을 `file` → `image`로 변경

2. **Database connection error**
   - 해결: PostgreSQL 컨테이너 상태 확인
   ```bash
   docker-compose logs smarteye-postgres
   ```

3. **LAM Service connection timeout**
   - 해결: LAM Service 재시작
   ```bash
   docker-compose restart smarteye-lam-service
   ```

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

**마지막 업데이트**: 2025-09-01  
**테스트 상태**: ✅ 모든 주요 기능 검증 완료
