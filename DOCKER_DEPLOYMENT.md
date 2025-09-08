# 🐳 SmartEye Docker 통합 배포 가이드

## 📊 시스템 구성

### 서비스 구조
```
                  사용자 (http://localhost)
                           ↓
    nginx:80 (smarteye-nginx) [프록시 + 정적파일 서빙]
           ↓                              ↓
    Frontend (React)              Backend API (/api/*)
    [정적 파일]              [smarteye-backend:8080]
                                        ↓
                            LAM Service (smarteye-lam-service:8001)
                                        ↓  
                             PostgreSQL (smarteye-postgres:5433)
```

### 컨테이너 목록
- **smarteye-frontend**: React 앱 (빌드된 정적 파일)
- **smarteye-nginx**: Nginx 프록시 서버 (포트 80)
- **smarteye-backend**: Java Spring Boot API 서버 (포트 8080)
- **smarteye-lam-service**: Python FastAPI LAM 서비스 (포트 8001)
- **smarteye-postgres**: PostgreSQL 데이터베이스 (포트 5433)

## 🚀 빠른 시작

### 1. 전체 시스템 시작
```bash
# SmartEye 루트 디렉토리에서 실행
./start_full_system.sh
```

### 2. 시스템 접속
- **웹 서비스**: http://localhost
- **Backend API**: http://localhost/api/health
- **Swagger UI**: http://localhost/api/swagger-ui.html

### 3. 시스템 중지
```bash
# SmartEye 루트 디렉토리에서 실행
./stop_full_system.sh
```

## 🔧 수동 명령어

### Docker Compose 직접 사용
```bash
# Backend 디렉토리로 이동
cd Backend

# 전체 시스템 시작 (빌드 포함)
docker-compose up -d --build

# 로그 확인
docker-compose logs -f

# 특정 서비스 로그 확인
docker-compose logs -f frontend
docker-compose logs -f smarteye-backend

# 서비스 상태 확인
docker-compose ps

# 시스템 중지
docker-compose down

# 볼륨 포함 완전 삭제
docker-compose down -v
```

### 개별 서비스 제어
```bash
# 특정 서비스만 재시작
docker-compose restart frontend
docker-compose restart smarteye-backend

# 특정 서비스 로그 실시간 확인
docker-compose logs -f --tail=100 smarteye-backend

# 컨테이너 내부 접속
docker exec -it smarteye-backend bash
docker exec -it smarteye-postgres psql -U smarteye -d smarteye_db
```

## 🛠️ 개발 환경

### Frontend 개별 개발
```bash
# Backend Docker 서비스만 실행
cd Backend
docker-compose up -d postgres lam-service smarteye-backend

# Frontend 개발 서버 실행 (별도 터미널)
cd frontend
npm install
npm start  # http://localhost:3000
```

### Backend 개별 개발
```bash
# 의존 서비스만 실행
cd Backend
docker-compose up -d postgres lam-service

# IDE에서 Spring Boot 앱 직접 실행
# application-dev.yml 프로파일 사용
```

## 📁 파일 구조

```
SmartEye_v0.4/
├── frontend/
│   ├── Dockerfile              # Frontend Docker 빌드 파일
│   ├── .env                    # 개발 환경변수
│   ├── .env.production         # 프로덕션 환경변수
│   └── src/
├── Backend/
│   ├── docker-compose.yml      # 전체 서비스 정의
│   ├── nginx/
│   │   └── nginx.conf          # Nginx 설정 (프록시 + 정적파일)
│   ├── smarteye-backend/       # Java Spring Boot
│   └── smarteye-lam-service/   # Python FastAPI
├── start_full_system.sh        # 전체 시스템 시작
└── stop_full_system.sh         # 전체 시스템 중지
```

## 🔍 트러블슈팅

### 포트 충돌
```bash
# 포트 사용 중인 프로세스 확인
lsof -i :80
lsof -i :8080
lsof -i :5433

# 프로세스 종료
sudo kill -9 <PID>
```

### 메모리 부족
```bash
# Docker 시스템 리소스 확인
docker system df

# 미사용 이미지/컨테이너 정리
docker system prune -a

# 특정 서비스 메모리 제한
# docker-compose.yml에서 deploy.resources 설정 조정
```

### 데이터베이스 초기화
```bash
# 데이터베이스 볼륨 삭제 후 재시작
docker-compose down -v
docker-compose up -d postgres
```

### 빌드 실패
```bash
# Docker 빌드 캐시 무시하고 재빌드
docker-compose build --no-cache frontend
docker-compose build --no-cache smarteye-backend

# 전체 시스템 강제 재빌드
docker-compose up -d --build --force-recreate
```

## 🚨 중요 참고사항

1. **초기 빌드**: 첫 실행 시 이미지 빌드로 5-10분 소요
2. **메모리 요구사항**: 최소 8GB RAM 권장
3. **디스크 용량**: 최소 10GB 여유 공간 필요
4. **네트워크**: 모든 서비스는 `smarteye-network` 내부에서 통신

## 📊 성능 모니터링

### 시스템 리소스 확인
```bash
# Docker 컨테이너 리소스 사용량
docker stats

# 디스크 사용량
docker system df

# 로그 용량 확인
docker-compose logs --no-color | wc -l
```

### 헬스체크 엔드포인트
- **Backend**: http://localhost/api/health
- **LAM Service**: http://localhost:8001/health
- **Nginx**: http://localhost/health

이제 `./start_full_system.sh` 명령어 하나로 전체 웹 서비스를 실행할 수 있습니다!