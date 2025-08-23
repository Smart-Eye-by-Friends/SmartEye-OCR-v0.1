# SmartEye v0.1 - Docker 구성 정리 문서

## 📁 Docker 파일 구조 (정리 후)

### 🏗️ 메인 Docker 파일들
```
SmartEye_v0.1/
├── docker-compose.yml              # 통합 프로덕션 환경 (전체 시스템)
├── docker-compose.dev.yml          # 개발 환경 전용
├── Dockerfile                      # Java Spring Boot 애플리케이션
├── .env.example                    # 환경 변수 템플릿
├── .env.dev                        # 개발 환경 변수
└── smarteye-lam-service/
    ├── Dockerfile                  # LAM 서비스 기본
    └── Dockerfile.optimized        # LAM 서비스 최적화
```

### ❌ 제거된 중복 파일들
- `docker-compose.optimized.yml` → `docker-compose.yml`에 통합
- `smarteye-lam-service/docker-compose.yml` → 메인 파일에 통합

## 🎯 통합된 Docker Compose 구조

### 🚀 **docker-compose.yml** (프로덕션)
```yaml
services:
  smarteye-backend:     # Spring Boot 메인 애플리케이션
  smarteye-lam:         # LAM 마이크로서비스 (최적화)
  postgres:             # PostgreSQL 데이터베이스
  redis:                # Redis 캐시
  nginx:                # 리버스 프록시 (profile: production)
  prometheus:           # 모니터링 (profile: monitoring)
```

**주요 개선사항:**
- ✅ 환경 변수 기반 설정 (`${변수:-기본값}`)
- ✅ 헬스체크 및 의존성 관리
- ✅ 리소스 제한 및 예약
- ✅ 네트워크 격리 및 보안
- ✅ 프로파일 기반 서비스 활성화

### 🔧 **docker-compose.dev.yml** (개발)
```yaml
services:
  smarteye-backend-dev: # 개발용 Spring Boot
  smarteye-lam-dev:     # 개발용 LAM 서비스
  postgres-dev:         # 개발용 PostgreSQL
  redis-dev:            # 개발용 Redis
```

**개발 환경 특징:**
- ✅ 소스 코드 마운트 (실시간 반영)
- ✅ 리소스 절약 설정
- ✅ 개발용 포트 분리 (5433, 6380)
- ✅ 디버그 로그 활성화

## 🛠️ Dockerfile 최적화

### 📋 **Dockerfile** (Spring Boot)
```dockerfile
FROM openjdk:17-jdk-slim
# 시스템 패키지 + Python + Tesseract
# Gradle 빌드 및 실행
```

### 🐍 **smarteye-lam-service/Dockerfile** (기본)
```dockerfile
FROM python:3.10-slim
# 기본 LAM 서비스 (단순 구성)
# 개발 환경용 적합
```

### ⚡ **smarteye-lam-service/Dockerfile.optimized** (최적화)
```dockerfile
FROM python:3.10-slim
# 모델 사전 로딩
# Redis 캐싱 지원
# 멀티 워커 uvicorn
# 프로덕션 환경용 최적화
```

## 🚀 배포 및 실행 방법

### 📦 **프로덕션 환경**
```bash
# 환경 변수 설정
cp .env.example .env
# .env 파일 수정 (실제 값 입력)

# 전체 시스템 배포
./scripts/deploy-phase3-complete.sh

# 또는 직접 실행
docker-compose up -d

# 모니터링 포함 실행
docker-compose --profile monitoring up -d

# 프로덕션 환경 (Nginx 포함)
docker-compose --profile production up -d
```

### 🔧 **개발 환경**
```bash
# 개발 환경 배포
./scripts/deploy-dev.sh

# 또는 직접 실행
docker-compose -f docker-compose.dev.yml --env-file .env.dev up -d
```

### 🛑 **시스템 중지**
```bash
# 전체 시스템 중지
./scripts/stop-system.sh

# 또는 직접 중지
docker-compose down
docker-compose -f docker-compose.dev.yml down
```

## 🌐 서비스 접속 정보

### 🏭 **프로덕션 환경**
| 서비스 | URL | 설명 |
|--------|-----|------|
| 메인 애플리케이션 | http://localhost:8080 | Spring Boot |
| LAM 마이크로서비스 | http://localhost:8081 | FastAPI |
| API 문서 | http://localhost:8080/swagger-ui.html | Swagger |
| LAM API 문서 | http://localhost:8081/docs | FastAPI Docs |
| PostgreSQL | localhost:5432 | 데이터베이스 |
| Redis | localhost:6379 | 캐시 |
| Nginx | http://localhost:80 | 리버스 프록시 |
| Prometheus | http://localhost:9090 | 모니터링 |

### 🔧 **개발 환경**
| 서비스 | URL | 설명 |
|--------|-----|------|
| 메인 애플리케이션 | http://localhost:8080 | Spring Boot (개발) |
| LAM 마이크로서비스 | http://localhost:8081 | FastAPI (개발) |
| PostgreSQL | localhost:5433 | 개발 DB |
| Redis | localhost:6380 | 개발 캐시 |

## 📊 성능 모니터링

### 🎯 **핵심 API 엔드포인트**
```bash
# 시스템 대시보드
curl http://localhost:8080/api/v3/monitoring/dashboard

# 성능 요약
curl http://localhost:8080/api/v3/monitoring/performance/summary

# 성능 알림
curl http://localhost:8080/api/v3/monitoring/performance/alerts

# 통합 분석 (성능 모니터링 포함)
curl -X POST http://localhost:8080/api/v2/analysis/integrated \
  -F "file=@test.jpg" \
  -F "analysisType=both"
```

## 🔧 환경 변수 설정

### 📋 **주요 환경 변수**
```bash
# Spring Boot
SPRING_PROFILES_ACTIVE=prod|dev
OPENAI_API_KEY=your-api-key

# 데이터베이스
DB_NAME=smarteye
DB_USERNAME=smarteye
DB_PASSWORD=your-password

# LAM 서비스 성능
LAM_WORKERS=4                    # 워커 수
LAM_MEMORY_LIMIT=4G             # 메모리 제한
LAM_CPU_LIMIT=2.0               # CPU 제한

# Redis
REDIS_PORT=6379
```

## 🎯 Docker 정리 결과

### ✅ **개선된 점**
1. **파일 통합**: 7개 → 4개 Docker 파일로 정리
2. **환경 분리**: 프로덕션/개발 환경 명확한 분리
3. **설정 통합**: 환경 변수 기반 동적 설정
4. **프로파일 활용**: 선택적 서비스 활성화
5. **자동화**: 배포 스크립트 개선 및 통합

### 🎪 **주요 특징**
- **확장성**: 마이크로서비스 아키텍처 유지
- **유연성**: 환경별 최적화 설정
- **안정성**: 헬스체크 및 의존성 관리
- **보안성**: 네트워크 격리 및 비root 사용자
- **관찰성**: 포괄적인 로그 및 모니터링

SmartEye v0.1의 Docker 구성이 효율적이고 관리하기 쉬운 구조로 정리되었습니다! 🎉

---

**작성일**: 2024년 12월 19일  
**버전**: SmartEye v0.1 - Docker 정리 완료  
**상태**: 통합 및 최적화 완료 ✅
