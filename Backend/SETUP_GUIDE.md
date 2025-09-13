# SmartEye 백엔드 실행 가이드

## 🚀 빠른 실행

### 개발환경 (권장)
```bash
# 1. 외부 서비스 시작 (PostgreSQL + LAM Service)
# cd Backend
# docker-compose -f docker-compose-dev.yml up -d
./start-dev.sh

📟 터미널 1: Backend 시작
cd Backend/smarteye-backend && ./gradlew bootRun --args='--spring.profiles.active=dev'

📱 터미널 2: Frontend 시작
cd Frontend && npm start
```

### 프로덕션 환경 (전체 Docker)
```bash
cd Backend
docker-compose up -d
```

## 🔧 환경 설정

### Tesseract OCR 설정
모든 환경에서 Tesseract OCR이 올바르게 작동하도록 다음 환경변수가 자동 설정됩니다:

- `TESSERACT_DATAPATH=/usr/share/tesseract-ocr/5/tessdata`
- `TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata`

### 개발자 커스텀 설정
로컬 환경에 맞게 설정을 변경하려면:

1. `.env.example`을 `.env`로 복사
2. 필요한 값들을 수정
3. 환경변수로 로드하여 실행

```bash
# .env 파일 생성
cp .env.example .env

# 환경변수 로드 후 실행
source .env
./gradlew bootRun
```

## 🐛 문제 해결

### Tesseract OCR 오류
만약 Tesseract 관련 오류가 발생하면:

1. **시스템 확인**
   ```bash
   tesseract --list-langs
   ls -la /usr/share/tesseract-ocr/5/tessdata/
   ```

2. **수동 환경변수 설정**
   ```bash
   export TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata
   ./gradlew bootRun
   ```

3. **Docker 컨테이너에서 확인**
   ```bash
   docker exec -it smarteye-backend sh
   ls -la /usr/share/tessdata/
   echo $TESSDATA_PREFIX
   ```

### 환경별 차이점
| 구분 | 개발환경 | 프로덕션 |
|------|----------|----------|
| 실행방식 | 네이티브 (./gradlew bootRun) | Docker 컨테이너 |
| 데이터베이스 | localhost:5433 | postgres:5432 |
| Tesseract 경로 | 시스템 기본값 | Docker 컨테이너 내부 |
| 업로드 디렉토리 | ./dev-uploads | /app/uploads |
| 로그 레벨 | DEBUG | INFO |

## ✅ 검증 방법

### 1. 애플리케이션 상태 확인
```bash
curl http://localhost:8080/api/health
```

### 2. Tesseract OCR 테스트
```bash
curl -X POST -F "file=@test_image.jpg" http://localhost:8080/api/ocr/extract
```

### 3. 로그 확인
```bash
# 개발환경
tail -f Backend/smarteye-backend/logs/application.log

# 프로덕션 (Docker)
docker logs -f smarteye-backend
```

## 📋 체크리스트

실행 전 확인사항:
- [ ] Java 21 설치됨
- [ ] Tesseract OCR 설치됨 (`sudo apt-get install tesseract-ocr tesseract-ocr-kor`)
- [ ] Docker & Docker Compose 설치됨
- [ ] 포트 8080, 5433, 8001이 사용 가능함
- [ ] 충분한 디스크 공간 (최소 2GB)

이제 모든 환경에서 동일하게 작동하며, 새로운 개발자나 고객도 쉽게 실행할 수 있습니다! 🎉