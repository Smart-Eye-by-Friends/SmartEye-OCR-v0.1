# SmartEye v0.4 - 사용자 가이드

이 문서는 SmartEye v0.4 시스템의 완전한 사용 방법을 제공합니다.

## 📋 목차

1. [시스템 개요](#시스템-개요)
2. [초기 설정](#초기-설정)
3. [시스템 시작 및 관리](#시스템-시작-및-관리)
4. [모니터링](#모니터링)
5. [웹 애플리케이션 사용](#웹-애플리케이션-사용)
6. [API 사용법](#api-사용법)
7. [문제 해결](#문제-해결)
8. [고급 설정](#고급-설정)
9. [사용 시나리오](#사용-시나리오)
10. [백업 및 복구](#백업-및-복구)

## 🎯 시스템 개요

SmartEye v0.4는 학습지 이미지 분석을 위한 마이크로서비스 기반 시스템입니다:

### 서비스 구성
- **Java Spring Boot 백엔드** (Port 8080) - 메인 API 서버
- **Python LAM 서비스** (Port 8001) - DocLayout-YOLO 기반 AI 분석
- **React 프론트엔드** (Port 3000/80) - 웹 사용자 인터페이스
- **PostgreSQL 데이터베이스** (Port 5433) - 데이터 저장소

### 주요 기능
- **33개 레이아웃 요소** 자동 검출
- **한국어/영어 OCR** 텍스트 추출
- **AI 기반 설명** 자동 생성
- **PDF 멀티페이지** 분석 지원
- **실시간 모니터링** 및 메트릭 수집

---

## 🔧 초기 설정

### 1.1 환경 설정

프로젝트 디렉토리로 이동:
```bash
cd /home/jongyoung3/SmartEye_v0.4
```

**개발 환경 설정:**
```bash
./scripts/setup-env.sh development
```

**프로덕션 환경 설정:**
```bash
# API 키 설정 (필수)
export OPENAI_API_KEY="your-openai-api-key-here"
export POSTGRES_PASSWORD="secure-database-password"

# 프로덕션 환경 적용
./scripts/setup-env.sh production
```

### 1.2 API 키 보안 확인

설정된 API 키의 보안 상태를 검증:
```bash
./scripts/setup-env.sh check
```

### 1.3 환경 파일 확인

설정된 환경 확인:
```bash
# 현재 환경 확인
ls -la .env*

# 환경 파일 내용 확인
cat .env
```

---

## 🚀 시스템 시작 및 관리

### 2.1 전체 시스템 관리

**시스템 시작:**
```bash
# 전체 서비스 시작 (빌드 + 실행)
./manage.sh start

# 빠른 시작 (빌드 생략)
./manage.sh up
```

**시스템 상태 확인:**
```bash
./manage.sh status
```

**시스템 중지:**
```bash
./manage.sh stop
```

**전체 재시작:**
```bash
./manage.sh restart
```

### 2.2 개별 서비스 관리

**특정 서비스 재시작:**
```bash
./manage.sh restart backend    # Java 백엔드만
./manage.sh restart frontend   # React 프론트엔드만
./manage.sh restart lam        # Python LAM 서비스만
./manage.sh restart db         # PostgreSQL 데이터베이스만
```

**개별 서비스 빌드:**
```bash
./manage.sh build backend
./manage.sh build frontend
./manage.sh build lam
```

### 2.3 로그 관리

**전체 로그 확인:**
```bash
./manage.sh logs
```

**특정 서비스 로그:**
```bash
./manage.sh logs backend
./manage.sh logs frontend  
./manage.sh logs lam
./manage.sh logs db
```

**실시간 로그 추적:**
```bash
docker logs -f smarteye-backend
docker logs -f smarteye-lam-service
```

### 2.4 도움말

```bash
./manage.sh help
```

---

## 📊 모니터링

### 3.1 모니터링 시스템 시작

```bash
# Prometheus + Grafana 모니터링 스택 시작
./scripts/start-monitoring.sh
```

### 3.2 모니터링 대시보드 접속

**웹 브라우저에서 접속:**
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3001
  - Username: `admin`
  - Password: `smarteye2024`
- **cAdvisor**: http://localhost:8080

### 3.3 시스템 메트릭 확인

**백엔드 메트릭:**
```bash
curl http://localhost:8080/actuator/prometheus
```

**LAM 서비스 메트릭:**
```bash
curl http://localhost:8001/metrics
```

**헬스체크:**
```bash
curl http://localhost:8080/api/health
curl http://localhost:8001/health
```

### 3.4 Grafana 대시보드 설정

1. Grafana 접속 후 로그인
2. **Data Sources** → **Add data source** → **Prometheus**
3. URL: `http://prometheus:9090`
4. **Save & Test**
5. **Import Dashboard** → `monitoring/grafana/dashboards/smarteye-dashboard.json`

---

## 🌐 웹 애플리케이션 사용

### 4.1 프론트엔드 접속

**개발 환경:**
```bash
http://localhost:3000
```

**프로덕션 환경 (Nginx):**
```bash
http://localhost:80
```

### 4.2 API 문서 확인

**Swagger UI:**
```bash
http://localhost:8080/swagger-ui/index.html
```

**OpenAPI JSON:**
```bash
http://localhost:8080/v3/api-docs
```

### 4.3 기본 사용 흐름

1. **웹 브라우저에서 프론트엔드 접속**
2. **이미지 또는 PDF 파일 업로드**
3. **분석 모델 선택** (기본: SmartEyeSsen)
4. **분석 시작** 버튼 클릭
5. **실시간 진행 상황** 확인
6. **분석 결과 확인** 및 다운로드

---

## 🔌 API 사용법

### 5.1 주요 API 엔드포인트

**이미지 분석:**
```bash
POST http://localhost:8080/api/document/analyze
Content-Type: multipart/form-data

Parameters:
- image: 이미지 파일 (JPG, PNG, GIF)
- modelChoice: 분석 모델 (기본값: SmartEyeSsen)  
- apiKey: OpenAI API 키 (선택사항)
```

**PDF 분석:**
```bash
POST http://localhost:8080/api/document/analyze-pdf
Content-Type: multipart/form-data

Parameters:
- file: PDF 파일
- modelChoice: 분석 모델 (기본값: SmartEyeSsen)
- apiKey: OpenAI API 키 (선택사항)
```

**분석 결과 조회:**
```bash
GET http://localhost:8080/api/analysis/job/{jobId}
```

### 5.2 사용자 관리

**사용자 생성:**
```bash
POST http://localhost:8080/api/users
Content-Type: application/json

Body:
{
  "username": "testuser",
  "email": "test@example.com"
}
```

**사용자 조회:**
```bash
GET http://localhost:8080/api/users/{userId}
```

### 5.3 API 사용 예시

**curl 예시:**
```bash
curl -X POST \
  http://localhost:8080/api/document/analyze \
  -H 'Content-Type: multipart/form-data' \
  -F 'image=@/path/to/image.jpg' \
  -F 'modelChoice=SmartEyeSsen'
```

**Python 예시:**
```python
import requests

url = "http://localhost:8080/api/document/analyze"
files = {"image": open("test.jpg", "rb")}
data = {"modelChoice": "SmartEyeSsen"}

response = requests.post(url, files=files, data=data)
result = response.json()
print(result)
```

**JavaScript/fetch 예시:**
```javascript
const formData = new FormData();
formData.append('image', fileInput.files[0]);
formData.append('modelChoice', 'SmartEyeSsen');

fetch('http://localhost:8080/api/document/analyze', {
  method: 'POST',
  body: formData
})
.then(response => response.json())
.then(data => console.log(data));
```

---

## 🔍 문제 해결

### 6.1 서비스 상태 확인

**Docker 컨테이너 상태:**
```bash
docker ps -a
```

**포트 사용 확인:**
```bash
sudo lsof -i :8080  # 백엔드
sudo lsof -i :8001  # LAM 서비스  
sudo lsof -i :3000  # React 개발 서버
sudo lsof -i :5433  # PostgreSQL
```

**네트워크 연결 확인:**
```bash
docker network ls
docker network inspect backend_smarteye-network
```

### 6.2 일반적인 문제와 해결책

**서비스가 시작되지 않는 경우:**
```bash
# 포트 충돌 해결
sudo pkill -f "java.*8080"
sudo pkill -f "python.*8001"

# Docker 네트워크 재생성
docker network rm backend_smarteye-network
./manage.sh start
```

**메모리 부족 오류:**
```bash
# 메모리 사용량 확인
free -h
docker stats

# JVM 메모리 설정 조정 (.env 파일)
JAVA_OPTS="-Xms512m -Xmx1024m"
```

**디스크 공간 부족:**
```bash
# 디스크 사용량 확인
df -h

# Docker 정리
docker system prune -a --volumes

# 로그 파일 정리
./manage.sh cleanup
```

### 6.3 데이터베이스 문제

**PostgreSQL 접속:**
```bash
docker exec -it smarteye-postgres psql -U smarteye -d smarteye_db
```

**데이터베이스 상태 확인:**
```sql
-- 연결 수 확인
SELECT count(*) FROM pg_stat_activity;

-- 테이블 목록
\dt

-- 최근 분석 작업 확인
SELECT * FROM analysis_jobs ORDER BY created_at DESC LIMIT 10;
```

**데이터베이스 재시작:**
```bash
./manage.sh restart db
```

### 6.4 로그 분석

**에러 로그 검색:**
```bash
# 백엔드 에러 로그
docker logs smarteye-backend 2>&1 | grep -i error

# LAM 서비스 에러 로그  
docker logs smarteye-lam-service 2>&1 | grep -i error

# 전체 시스템 상태 로그
./manage.sh logs | grep -i "error\|exception\|failed"
```

---

## ⚙️ 고급 설정

### 7.1 환경변수 커스터마이징

**개발 환경 설정 수정:**
```bash
nano .env.development
```

**주요 설정 변경:**
```bash
# 데이터베이스 연결 풀
DB_POOL_SIZE=20
DB_MIN_IDLE=5

# JVM 성능 튜닝
JAVA_OPTS="-Xms1g -Xmx2g -XX:+UseG1GC"

# OCR 언어 설정
TESSERACT_LANG=kor+eng+jpn

# 파일 업로드 크기 제한
REACT_APP_MAX_FILE_SIZE=52428800  # 50MB
```

### 7.2 보안 설정

**CORS 설정 (프로덕션):**
```bash
# .env.production 파일에서
CORS_ALLOWED_ORIGINS=https://yourdomain.com,https://www.yourdomain.com
CORS_ALLOWED_METHODS=GET,POST,PUT,DELETE,OPTIONS
CORS_ALLOWED_HEADERS=Content-Type,Authorization,X-Requested-With
```

**API 키 환경변수 설정:**
```bash
# 시스템 환경변수로 설정
echo 'export OPENAI_API_KEY="your-actual-api-key"' >> ~/.bashrc
source ~/.bashrc

# Docker 환경에서 전달
docker run -e OPENAI_API_KEY="$OPENAI_API_KEY" ...
```

### 7.3 성능 최적화

**Java 백엔드 튜닝:**
```bash
# .env 파일에서 JVM 옵션 설정
JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# 스레드 풀 설정
MAX_THREADS=400
MIN_SPARE_THREADS=20
```

**데이터베이스 튜닝:**
```bash
# PostgreSQL 설정
DB_POOL_SIZE=25
DB_MIN_IDLE=10
DB_CONNECTION_TIMEOUT=30000
DB_IDLE_TIMEOUT=600000
```

### 7.4 로깅 설정

**로그 레벨 조정:**
```bash
# 개발 환경 - 상세 로그
SQL_LOGGING_LEVEL=DEBUG
ROOT_LOGGING_LEVEL=DEBUG

# 프로덕션 환경 - 최소 로그  
SQL_LOGGING_LEVEL=WARN
ROOT_LOGGING_LEVEL=INFO
```

---

## 📱 사용 시나리오

### 8.1 학습지 이미지 분석

**시나리오**: 수학 문제집 페이지 분석

1. **웹 인터페이스 접속**
   ```bash
   http://localhost:3000
   ```

2. **이미지 업로드**
   - 파일 선택: `math_worksheet.jpg`
   - 모델 선택: `SmartEyeSsen`
   - API 키: 자동 사용 또는 수동 입력

3. **분석 진행**
   - 업로드 진행률 확인
   - LAM 서비스 레이아웃 분석
   - OCR 텍스트 추출
   - AI 설명 생성

4. **결과 확인**
   - 33개 레이아웃 블록 표시
   - 21개 텍스트 영역 OCR 결과
   - AI 생성 문제 설명
   - JSON 형태 구조화된 결과

5. **결과 활용**
   - JSON 다운로드
   - 시각화된 분석 결과 확인
   - 데이터베이스 저장 확인

### 8.2 PDF 문서 멀티페이지 분석

**시나리오**: 10페이지 시험지 분석

1. **API 직접 호출**
   ```bash
   curl -X POST \
     http://localhost:8080/api/document/analyze-pdf \
     -H 'Content-Type: multipart/form-data' \
     -F 'file=@exam_10pages.pdf' \
     -F 'modelChoice=SmartEyeSsen'
   ```

2. **진행 상황 모니터링**
   ```bash
   # 작업 상태 확인 (jobId 사용)
   curl http://localhost:8080/api/analysis/job/{jobId}
   ```

3. **페이지별 결과 확인**
   - 각 페이지별 레이아웃 분석
   - 페이지간 연관성 분석
   - 통합된 결과 JSON 생성

### 8.3 대량 처리 시나리오

**시나리오**: 100개 이미지 배치 처리

1. **Python 스크립트 작성**
   ```python
   import requests
   import os
   import json
   from concurrent.futures import ThreadPoolExecutor
   
   def analyze_image(image_path):
       url = "http://localhost:8080/api/document/analyze"
       files = {"image": open(image_path, "rb")}
       data = {"modelChoice": "SmartEyeSsen"}
       
       response = requests.post(url, files=files, data=data)
       return response.json()
   
   # 병렬 처리
   image_files = [f for f in os.listdir('./images') if f.endswith('.jpg')]
   
   with ThreadPoolExecutor(max_workers=5) as executor:
       results = list(executor.map(analyze_image, image_files))
   
   # 결과 저장
   with open('batch_results.json', 'w') as f:
       json.dump(results, f, ensure_ascii=False, indent=2)
   ```

2. **모니터링**
   ```bash
   # Grafana에서 실시간 처리량 확인
   # Prometheus 메트릭으로 성능 모니터링
   ```

---

## 🔄 백업 및 복구

### 9.1 데이터베이스 백업

**전체 데이터베이스 백업:**
```bash
# PostgreSQL 덤프 생성
docker exec smarteye-postgres pg_dump -U smarteye smarteye_db > smarteye_backup_$(date +%Y%m%d).sql
```

**특정 테이블 백업:**
```bash
# 분석 작업 테이블만 백업
docker exec smarteye-postgres pg_dump -U smarteye -t analysis_jobs smarteye_db > jobs_backup.sql
```

**백업 복구:**
```bash
# 데이터베이스 복구
cat smarteye_backup_20241201.sql | docker exec -i smarteye-postgres psql -U smarteye -d smarteye_db
```

### 9.2 파일 데이터 백업

**업로드된 파일 백업:**
```bash
# 컨테이너에서 호스트로 복사
docker cp smarteye-backend:/app/uploads ./uploads_backup_$(date +%Y%m%d)

# 압축 백업
tar -czf uploads_backup_$(date +%Y%m%d).tar.gz uploads_backup_$(date +%Y%m%d)
```

**정적 파일 백업:**
```bash
docker cp smarteye-backend:/app/static ./static_backup_$(date +%Y%m%d)
```

**백업 복구:**
```bash
# 압축 해제
tar -xzf uploads_backup_20241201.tar.gz

# 컨테이너로 복사
docker cp ./uploads_backup_20241201 smarteye-backend:/app/uploads
```

### 9.3 설정 파일 백업

**환경 설정 백업:**
```bash
# 환경 파일들 백업
cp .env.development .env.production .env.example ./config_backup/

# 모니터링 설정 백업  
cp -r monitoring ./monitoring_backup_$(date +%Y%m%d)

# 스크립트 백업
cp -r scripts ./scripts_backup_$(date +%Y%m%d)
```

### 9.4 자동화된 백업 스크립트

**일일 백업 스크립트 생성:**
```bash
cat > backup_daily.sh << 'EOF'
#!/bin/bash
BACKUP_DATE=$(date +%Y%m%d)
BACKUP_DIR="./backups/$BACKUP_DATE"

mkdir -p $BACKUP_DIR

# 데이터베이스 백업
docker exec smarteye-postgres pg_dump -U smarteye smarteye_db > $BACKUP_DIR/database.sql

# 파일 백업
docker cp smarteye-backend:/app/uploads $BACKUP_DIR/uploads

# 압축
tar -czf $BACKUP_DIR.tar.gz $BACKUP_DIR
rm -rf $BACKUP_DIR

echo "Backup completed: $BACKUP_DIR.tar.gz"
EOF

chmod +x backup_daily.sh
```

**크론탭 설정:**
```bash
# 매일 오전 2시 자동 백업
crontab -e
# 추가: 0 2 * * * /path/to/SmartEye_v0.4/backup_daily.sh
```

---

## 📞 지원 및 문의

### 문제 보고
- GitHub Issues를 통한 버그 리포트
- 로그 파일과 함께 상세한 에러 상황 제공

### 기능 요청  
- GitHub Discussions를 통한 새로운 기능 제안
- 사용 사례와 함께 구체적인 요구사항 작성

### 기여 방법
1. Fork the repository
2. Create feature branch (`git checkout -b feature/new-feature`)
3. Commit changes (`git commit -m 'Add new feature'`)
4. Push to branch (`git push origin feature/new-feature`)
5. Create Pull Request

---

이 문서가 SmartEye v0.4 시스템을 효과적으로 사용하는데 도움이 되기를 바랍니다. 추가적인 도움이 필요하시면 언제든 문의해 주세요! 🚀