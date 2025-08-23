# SmartEye v0.1 QuickStart Guide

## 🚀 빠른 시작 (5분 설정)

### 1. 환경 준비
```bash
# 저장소 클론
git clone https://github.com/Smart-Eye-by-Friends/SmartEye-OCR-v0.1.git
cd SmartEye_v0.1

# 개발 환경 설정 (H2 데이터베이스)
export SPRING_PROFILES_ACTIVE=dev
```

### 2. 애플리케이션 실행
```bash
# 개발 모드로 실행
./scripts/run.sh dev

# 또는 직접 실행
./gradlew bootRun
```

### 3. 첫 번째 API 호출
```bash
# 시스템 상태 확인
curl http://localhost:8080/api/v2/analysis/status

# 테스트 이미지로 분석 실행
curl -X POST \
  -F "file=@test_image.jpg" \
  -F "analysisType=both" \
  http://localhost:8080/api/v2/analysis/integrated
```

## 🎯 주요 API 엔드포인트

### 통합 분석 (권장)
```bash
POST /api/v2/analysis/integrated
```

### 개별 모듈 분석
```bash
POST /api/v2/analysis/lam/analyze    # 레이아웃 분석만
POST /api/v2/analysis/tspm/analyze   # 텍스트 처리만
```

### 상태 확인
```bash
GET /api/v2/analysis/status          # 전체 시스템 상태
GET /api/v2/analysis/lam/health      # LAM 마이크로서비스 상태
```

## 🔧 고급 설정 (PostgreSQL)

### 1. PostgreSQL 설정
```bash
# PostgreSQL 설치 (Ubuntu)
sudo apt install postgresql postgresql-contrib

# 데이터베이스 생성
sudo -u postgres createuser smarteye
sudo -u postgres createdb smarteye_db
sudo -u postgres psql -c "ALTER USER smarteye PASSWORD 'smarteye123';"
```

### 2. 환경변수 설정
```bash
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/smarteye_db
export SPRING_DATASOURCE_USERNAME=smarteye
export SPRING_DATASOURCE_PASSWORD=smarteye123
export OPENAI_API_KEY=your_openai_api_key
```

### 3. 실행
```bash
./gradlew bootRun
```

## 📝 리팩토링된 아키텍처 특징

### ✅ 통합된 API 구조
- **단일 컨트롤러**: `IntegratedAnalysisController`
- **중앙 서비스**: `DocumentAnalysisService`
- **정리된 DTO**: `dto/request/`, `dto/response/`

### ✅ 개선된 예외 처리
- `DocumentAnalysisException`
- `TSPMAnalysisException`
- `FileProcessingException`

### ✅ 제거된 레거시 코드
- `AnalysisController` (deprecated)
- `AnalysisService` (deprecated)  
- `LAMMicroserviceController` (통합됨)

## 🐛 문제 해결

### 일반적인 문제들

#### 1. 포트 충돌
```bash
# 포트 8080이 사용중인 경우
lsof -ti:8080 | xargs kill -9
```

#### 2. 데이터베이스 연결 오류
```bash
# PostgreSQL 상태 확인
sudo systemctl status postgresql

# 연결 테스트
psql -h localhost -U smarteye -d smarteye_db
```

#### 3. LAM 마이크로서비스 연결 오류
```bash
# LAM 서비스 단독 실행
cd smarteye-lam-service
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8081
```

### 로그 확인
```bash
# 애플리케이션 로그
tail -f logs/smarteye.log

# 실시간 에러 로그
./gradlew bootRun | grep ERROR
```

## 📚 다음 단계

1. **API 문서**: README.md의 전체 API 엔드포인트 참조
2. **개발 가이드**: README.md의 개발 가이드 섹션 참조
3. **아키텍처**: `.github/copilot-instructions.md` 참조
4. **고급 설정**: `docs/` 폴더의 상세 문서 참조

---
**SmartEye v0.1** - 리팩토링 완료 버전  
더 많은 정보: [README.md](README.md)
