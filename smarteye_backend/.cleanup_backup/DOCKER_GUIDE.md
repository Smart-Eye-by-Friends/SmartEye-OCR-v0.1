# 🐳 SmartEye Backend - Docker 환경설정 가이드

## 📋 목차
1. [사전 준비](#사전-준비)
2. [환경 설정](#환경-설정)
3. [개발 환경 실행](#개발-환경-실행)
4. [프로덕션 환경 실행](#프로덕션-환경-실행)
5. [관리 명령어](#관리-명령어)
6. [모니터링](#모니터링)
7. [문제 해결](#문제-해결)

## 🚀 사전 준비

### 필수 소프트웨어
- **Docker**: 20.10+ 
- **Docker Compose**: 2.0+
- **Git**: 최신 버전

### 설치 확인
```bash
docker --version
docker-compose --version
git --version
```

## ⚙️ 환경 설정

### 1. 환경 변수 파일 생성

#### 개발 환경 (자동으로 설정됨)
개발 환경은 별도 설정 없이 바로 실행 가능합니다.

#### 프로덕션 환경
```bash
# .env.docker 파일을 복사하여 수정
cp .env.docker .env.local

# 필수 변경 사항:
# - SECRET_KEY: 강력한 시크릿 키로 변경
# - DATABASE_PASSWORD: 안전한 패스워드로 변경  
# - REDIS_PASSWORD: Redis 패스워드 설정
# - OPENAI_API_KEY: OpenAI API 키 설정
```

### 2. OpenAI API 키 설정
```bash
# 환경 변수로 설정 (권장)
export OPENAI_API_KEY="your-openai-api-key-here"

# 또는 .env 파일에 추가
echo "OPENAI_API_KEY=your-openai-api-key-here" >> .env.local
```

## 🏗️ 개발 환경 실행

### 빠른 시작
```bash
# 개발 환경 시작
./docker-manage.sh dev

# 또는 직접 실행
docker-compose -f docker-compose.dev.yml up -d
```

### 초기 설정
```bash
# 데이터베이스 마이그레이션
./docker-manage.sh migrate

# 관리자 계정 생성
docker-compose -f docker-compose.dev.yml exec web python manage.py createsuperuser

# 정적 파일 수집
./docker-manage.sh collectstatic
```

### 개발 환경 접속
- **웹 애플리케이션**: http://localhost:8000
- **API 문서**: http://localhost:8000/api/docs/
- **관리자 페이지**: http://localhost:8000/admin/
- **Flower (Celery 모니터링)**: http://localhost:5555

## 🏭 프로덕션 환경 실행

### 환경 파일 설정
```bash
# 프로덕션용 환경 파일 생성
cp .env.docker .env.production

# 보안 설정 변경 (필수!)
vim .env.production
```

### 프로덕션 실행
```bash
# 프로덕션 환경 시작
./docker-manage.sh prod

# 또는 직접 실행  
docker-compose --env-file .env.production up -d
```

### SSL 인증서 설정 (선택사항)
```bash
# Let's Encrypt 인증서 생성
# nginx 설정에 SSL 구성 추가 필요
```

## 🔧 관리 명령어

### Docker 관리 스크립트 사용법
```bash
# 도움말 확인
./docker-manage.sh help

# 개발 환경 시작
./docker-manage.sh dev

# 프로덕션 환경 시작  
./docker-manage.sh prod

# 모든 서비스 중지
./docker-manage.sh stop

# 서비스 재시작
./docker-manage.sh restart

# 로그 확인
./docker-manage.sh logs
./docker-manage.sh logs -f  # 실시간 로그

# Django shell 접속
./docker-manage.sh shell

# 데이터베이스 shell 접속
./docker-manage.sh dbshell

# 마이그레이션 실행
./docker-manage.sh migrate

# 테스트 실행
./docker-manage.sh test

# 헬스체크
./docker-manage.sh health

# 데이터베이스 백업
./docker-manage.sh backup

# 데이터베이스 복원
./docker-manage.sh restore backup_20240108_120000.sql
```

### 직접 Docker 명령어 사용
```bash
# 특정 서비스 로그 확인
docker-compose -f docker-compose.dev.yml logs web
docker-compose -f docker-compose.dev.yml logs celery-worker

# 컨테이너 내부 접속
docker-compose -f docker-compose.dev.yml exec web bash

# 특정 서비스만 재시작
docker-compose -f docker-compose.dev.yml restart web

# 볼륨 확인
docker volume ls | grep smarteye

# 이미지 다시 빌드
docker-compose -f docker-compose.dev.yml build --no-cache
```

## 📊 모니터링

### 서비스 상태 확인
```bash
# 실행 중인 서비스 확인
docker-compose -f docker-compose.dev.yml ps

# 리소스 사용량 확인
docker stats

# 헬스체크 상태 확인
docker-compose -f docker-compose.dev.yml exec web python manage.py check
```

### Flower를 통한 Celery 모니터링
- **URL**: http://localhost:5555
- **계정**: admin / admin (개발환경)
- **기능**: 
  - 실시간 작업 모니터링
  - 워커 상태 확인
  - 작업 결과 확인
  - 성능 메트릭

### 로그 모니터링
```bash
# 실시간 로그 확인
./docker-manage.sh logs -f

# 특정 서비스 로그
docker-compose logs -f web
docker-compose logs -f celery-worker
docker-compose logs -f db
docker-compose logs -f redis

# 로그 파일 직접 확인
tail -f logs/django.log
```

## 🐛 문제 해결

### 일반적인 문제들

#### 1. 포트 충돌
```bash
# 사용 중인 포트 확인
sudo lsof -i :8000
sudo lsof -i :5432
sudo lsof -i :6379

# 기존 서비스 중지
sudo systemctl stop postgresql
sudo systemctl stop redis-server
```

#### 2. 권한 문제
```bash
# 로그 디렉토리 권한 설정
sudo chown -R $USER:$USER logs/
chmod 755 logs/

# 미디어 디렉토리 권한 설정
sudo chown -R $USER:$USER media/
chmod 755 media/
```

#### 3. 메모리 부족
```bash
# Docker 메모리 할당 증가
# Docker Desktop > Settings > Resources > Memory 증가

# 스왑 메모리 확인
free -h
sudo swapon --show
```

#### 4. 데이터베이스 연결 오류
```bash
# 데이터베이스 컨테이너 재시작
docker-compose restart db

# 데이터베이스 연결 테스트
docker-compose exec db psql -U smarteye_user -d smarteye_dev -c "SELECT 1;"
```

#### 5. Redis 연결 오류
```bash
# Redis 컨테이너 재시작
docker-compose restart redis

# Redis 연결 테스트
docker-compose exec redis redis-cli ping
```

### 디버깅 명령어
```bash
# 컨테이너 상태 확인
docker ps -a

# 컨테이너 로그 확인
docker logs <container_id>

# 컨테이너 내부 접속
docker exec -it <container_id> bash

# 네트워크 확인
docker network ls
docker network inspect smarteye_smarteye-network

# 볼륨 확인
docker volume ls
docker volume inspect smarteye_postgres_data
```

### 성능 최적화

#### Docker 설정 최적화
```bash
# Docker daemon 설정 (/etc/docker/daemon.json)
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "default-ulimits": {
    "nofile": {
      "hard": 65536,
      "soft": 65536
    }
  }
}
```

#### 리소스 모니터링
```bash
# 컨테이너별 리소스 사용량
docker stats --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}\t{{.BlockIO}}"

# 시스템 리소스 확인
htop
iotop
```

## 🔒 보안 고려사항

### 프로덕션 환경 보안
1. **환경 변수 보안**
   - 강력한 SECRET_KEY 설정
   - 데이터베이스 패스워드 변경
   - Redis 패스워드 설정

2. **네트워크 보안**
   - 방화벽 설정
   - 불필요한 포트 닫기
   - SSL/TLS 인증서 설정

3. **컨테이너 보안**
   - 비특권 사용자로 실행
   - 읽기 전용 파일시스템
   - 리소스 제한 설정

### 백업 전략
```bash
# 정기 백업 스크립트 설정
# /etc/cron.d/smarteye-backup
0 2 * * * /path/to/smarteye/docker-manage.sh backup

# 백업 파일 로테이션
find /path/to/backups -name "backup_*.sql" -mtime +7 -delete
```

## 📚 추가 자료

- [Django 공식 문서](https://docs.djangoproject.com/)
- [Docker 공식 문서](https://docs.docker.com/)
- [Docker Compose 문서](https://docs.docker.com/compose/)
- [Celery 문서](https://docs.celeryproject.org/)
- [PostgreSQL 문서](https://www.postgresql.org/docs/)
- [Redis 문서](https://redis.io/documentation)

---

문제가 발생하면 GitHub Issues에 문의하거나 팀 채널을 이용하세요! 🚀
