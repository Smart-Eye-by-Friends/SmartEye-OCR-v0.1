# SmartEye v0.1 - Scripts 디렉토리

## 📁 스크립트 개요

SmartEye v0.1의 하이브리드 마이크로서비스 아키텍처를 효율적으로 관리하기 위한 스크립트 모음입니다.

## 🚀 빠른 시작

```bash
# 1. 빠른 시작 (권장)
./scripts/quick-start.sh

# 2. 개발 환경으로 실행
./scripts/run.sh dev

# 3. Docker 개발 환경
./scripts/run.sh docker-dev

# 4. 시스템 전체 관리
./scripts/system-manager.sh status
```

## 📋 스크립트 목록

### 🎯 핵심 실행 스크립트

| 스크립트 | 설명 | 사용법 |
|---------|------|--------|
| `quick-start.sh` | **가장 간단한 시작 방법** | `./scripts/quick-start.sh` |
| `run.sh` | 통합 빌드 및 실행 | `./scripts/run.sh [dev\|prod\|docker\|docker-dev]` |
| `system-manager.sh` | 시스템 전체 관리 | `./scripts/system-manager.sh [start\|stop\|restart\|status]` |

### ⚙️ 환경 설정 스크립트

| 스크립트 | 설명 | 사용법 |
|---------|------|--------|
| `setup-env.sh` | 환경변수 설정 | `source ./scripts/setup-env.sh [dev\|prod\|docker]` |
| `deploy-dev.sh` | 개발 환경 Docker 배포 | `./scripts/deploy-dev.sh` |
| `deploy-lam-microservice.sh` | LAM 서비스만 독립 배포 | `./scripts/deploy-lam-microservice.sh` |

### 🔧 유지보수 스크립트

| 스크립트 | 설명 | 사용법 |
|---------|------|--------|
| `stop-system.sh` | 시스템 중지 (레거시) | `./scripts/stop-system.sh` |
| `install-git-hooks.sh` | Git Hook 설치 | `./scripts/install-git-hooks.sh` |
| `update-instructions.sh` | Copilot 지침 업데이트 | `./scripts/update-instructions.sh [--commit]` |

## 🏗️ 아키텍처별 실행 방법

### 1. 로컬 개발 환경 (H2 + LAM Docker)
```bash
# H2 메모리 DB + LAM 마이크로서비스
./scripts/run.sh dev
```

### 2. Docker 개발 환경 (PostgreSQL + 모든 서비스)
```bash
# 전체 시스템을 Docker로 실행
./scripts/run.sh docker-dev
```

### 3. 프로덕션 환경
```bash
# 환경변수 설정 후 실행
source ./scripts/setup-env.sh prod
./scripts/run.sh prod
```

### 4. LAM 서비스만 독립 실행
```bash
# LAM 마이크로서비스만 필요한 경우
./scripts/deploy-lam-microservice.sh
```

## 📊 실행 옵션 상세

### `run.sh` 옵션
- `build`: Spring Boot 프로젝트만 빌드
- `dev`: 개발 모드 (H2 + LAM Docker)
- `prod`: 프로덕션 모드 (PostgreSQL + LAM Docker)
- `docker`: Docker Compose 프로덕션 환경
- `docker-dev`: Docker Compose 개발 환경
- `package`: JAR 패키지 생성
- `stop`: 모든 서비스 중지

### `system-manager.sh` 옵션
- `start [env]`: 시스템 시작 (dev/prod/docker)
- `stop`: 시스템 중지
- `restart [env]`: 시스템 재시작
- `status`: 시스템 상태 확인
- `logs [service]`: 서비스 로그 조회
- `health`: 헬스체크 수행

### `setup-env.sh` 환경
- `dev`: H2 메모리 DB, 디버그 로깅
- `prod`: PostgreSQL, 프로덕션 로깅
- `docker`: Docker Compose 환경 변수

## 🔍 문제 해결

### 일반적인 문제

1. **LAM 서비스 연결 실패**
   ```bash
   # LAM 서비스 상태 확인
   docker ps | grep lam
   curl http://localhost:8081/health
   
   # LAM 서비스 재시작
   ./scripts/deploy-lam-microservice.sh
   ```

2. **포트 충돌**
   ```bash
   # 포트 사용 중인 프로세스 확인
   sudo lsof -i :8080
   sudo lsof -i :8081
   
   # 기존 서비스 중지
   ./scripts/system-manager.sh stop
   ```

3. **Docker 권한 문제**
   ```bash
   # Docker 그룹에 사용자 추가
   sudo usermod -aG docker $USER
   # 재로그인 필요
   ```

### 로그 확인 방법

```bash
# Spring Boot 애플리케이션 로그
./scripts/system-manager.sh logs backend

# LAM 마이크로서비스 로그
./scripts/system-manager.sh logs lam
docker logs smarteye-lam-service

# 전체 시스템 로그 (Docker)
docker-compose logs -f
```

## 📁 스크립트 아키텍처

```
scripts/
├── 🎯 핵심 실행
│   ├── quick-start.sh          # 가장 간단한 시작
│   ├── run.sh                  # 통합 실행 스크립트
│   └── system-manager.sh       # 시스템 전체 관리
├── ⚙️ 환경 설정
│   ├── setup-env.sh            # 환경변수 설정
│   ├── deploy-dev.sh           # 개발 환경 배포
│   └── deploy-lam-microservice.sh  # LAM 독립 배포
├── 🔧 유지보수
│   ├── stop-system.sh          # 레거시 중지 스크립트
│   ├── install-git-hooks.sh    # Git Hook 설치
│   ├── update-instructions.sh  # Copilot 지침 업데이트
│   └── update-copilot-instructions.js  # 지침 업데이트 로직
└── 📄 문서
    └── README.md               # 이 파일
```

## 🚦 권장 워크플로우

### 개발자 첫 실행
```bash
# 1. 빠른 시작으로 전체 시스템 확인
./scripts/quick-start.sh

# 2. 개발 환경 설정
source ./scripts/setup-env.sh dev

# 3. 개발 모드로 실행
./scripts/run.sh dev
```

### 일상 개발 작업
```bash
# 시스템 상태 확인
./scripts/system-manager.sh status

# 개발 모드로 실행
./scripts/run.sh dev

# 문제 발생 시 전체 재시작
./scripts/system-manager.sh restart dev
```

### 배포 환경 테스트
```bash
# Docker 개발 환경으로 배포 테스트
./scripts/run.sh docker-dev

# 또는 전체 개발 배포
./scripts/deploy-dev.sh
```

## 🔗 관련 문서

- [SmartEye 메인 README](../README.md)
- [LAM 마이크로서비스 문서](../smarteye-lam-service/README.md)
- [Docker 설정 문서](../docker-compose.yml)
- [Copilot 지침](../.github/copilot-instructions.md)

---

💡 **Tip**: 스크립트 실행 전에 항상 프로젝트 루트 디렉토리에서 실행하세요!
