# SmartEye Backend 문서 총정리 📋

SmartEye Backend 프로젝트의 모든 문서와 도구를 한 곳에서 확인할 수 있도록 정리한 인덱스입니다.

## 🚀 시작하기

### 신규 사용자용
1. **[README.md](README.md)** - 프로젝트 개요 및 빠른 시작
2. **[QUICKSTART.md](QUICKSTART.md)** - 5분 만에 실행하고 테스트

### 개발자용  
1. **[DEVELOPER_SETUP_GUIDE.md](DEVELOPER_SETUP_GUIDE.md)** - 상세한 개발 환경 설정
2. **[API_USAGE_EXAMPLES.md](API_USAGE_EXAMPLES.md)** - API 사용법 및 예제
3. **[CLAUDE.md](CLAUDE.md)** - Claude Code 개발자 참조

## 📚 핵심 문서 (7개)

| 문서 | 용도 | 대상 | 라인 수 |
|------|------|------|---------|
| **[README.md](README.md)** | 프로젝트 전체 개요 및 빠른 시작 | 모든 사용자 | ~230줄 |
| **[QUICKSTART.md](QUICKSTART.md)** | 5분 만에 실행하고 테스트 | 신규 사용자 | ~170줄 |
| **[DEVELOPER_SETUP_GUIDE.md](DEVELOPER_SETUP_GUIDE.md)** | 상세한 환경 설정 및 개발 도구 | 개발자 | ~750줄 |
| **[API_USAGE_EXAMPLES.md](API_USAGE_EXAMPLES.md)** | REST API 사용법 및 예시 코드 | 프론트엔드 개발자 | ~330줄 |
| **[DOCUMENTATION.md](DOCUMENTATION.md)** | 기술 명세서 (아키텍처, DB 스키마) | 시스템 관리자 | ~300줄 |
| **[CLAUDE.md](CLAUDE.md)** | Claude Code를 위한 개발자 참조 | AI 개발자 | ~270줄 |
| **[DOCUMENTATION_INDEX.md](DOCUMENTATION_INDEX.md)** | 이 문서 (전체 문서 인덱스) | 모든 사용자 | ~280줄 |

## 📁 디렉토리 구조

```
smarteye_backend/
├── 📄 핵심 문서 (7개)
│   ├── README.md                    # 프로젝트 개요
│   ├── QUICKSTART.md               # 5분 시작 가이드  
│   ├── DEVELOPER_SETUP_GUIDE.md    # 상세 설정 가이드
│   ├── API_USAGE_EXAMPLES.md       # API 사용 예제
│   ├── DOCUMENTATION.md            # 기술 명세서
│   ├── CLAUDE.md                   # Claude Code 참조
│   └── DOCUMENTATION_INDEX.md      # 이 문서
│
├── 🐳 Docker 설정
│   ├── docker-compose.yml          # 운영 환경
│   ├── docker-compose.dev.yml      # 개발 환경  
│   ├── Dockerfile                  # 앱 이미지
│   └── .env.docker                 # 환경 변수
│
├── 📱 Django 애플리케이션
│   ├── apps/analysis/              # AI 분석 모듈
│   ├── apps/files/                 # 파일 관리
│   ├── apps/users/                 # 사용자 관리
│   └── apps/api/                   # 공통 API
│
├── 🧠 AI 코어 모듈
│   ├── core/lam/                   # Layout Analysis
│   ├── core/tspm/                  # Text & Scene Processing  
│   └── core/cim/                   # Content Integration
│
└── 🛠️ 유틸리티
    ├── utils/                      # 공통 유틸리티
    ├── tests/                      # 테스트 코드
    └── scripts/                    # 자동화 스크립트
```

## 🚀 빠른 시작

### 1단계: 환경 설정 (5분)
```bash
# 프로젝트 클론
git clone https://github.com/Smart-Eye-by-Friends/SmartEye-OCR-v0.1.git
cd SmartEye-OCR-v0.1/smarteye_backend

# 환경 변수 설정
cp .env.docker.example .env.docker
nano .env.docker  # 필수 설정 편집
```

### 2단계: 서비스 실행 (3분)
```bash
# Docker 서비스 빌드 및 실행
docker compose up --build -d

# 초기 데이터베이스 설정
docker compose exec web python manage.py migrate
docker compose exec web python manage.py createsuperuser
```

### 3단계: 접속 확인
- **API 서버**: http://localhost:8000
- **관리자 패널**: http://localhost:8000/admin
- **API 문서**: http://localhost:8000/api/v1/docs/

## 💡 주요 명령어

### 개발용
```bash
# 개발 모드 실행 (코드 실시간 반영)
docker compose -f docker-compose.dev.yml up -d

# 로그 확인
docker compose logs -f web

# 테스트 실행
docker compose exec web python manage.py test
```

### 운영용
```bash
# 운영 모드 실행
docker compose up -d

# 백업
docker compose exec db pg_dump -U smarteye_user smarteye_db > backup.sql

# 재시작
docker compose restart web
```

## � 문제 해결

### 자주 발생하는 문제
1. **포트 충돌**: `docker compose down && docker compose up -d`
2. **권한 오류**: `sudo usermod -aG docker $USER`
3. **메모리 부족**: Docker Desktop에서 메모리 할당 증가
4. **API 키 오류**: `.env.docker`에서 `OPENAI_API_KEY` 설정 확인

### 로그 확인
```bash
# 모든 서비스 로그
docker compose logs -f

# 특정 서비스 로그  
docker compose logs -f web
docker compose logs -f celery-worker
```

## 📞 지원 및 기여

- **이슈 리포트**: [GitHub Issues](https://github.com/Smart-Eye-by-Friends/SmartEye-OCR-v0.1/issues)
- **기여 방법**: Fork → Branch → PR
- **이메일**: smart.eye.by.friends@gmail.com

## 📄 라이선스

이 프로젝트는 MIT 라이선스를 따릅니다.
- **내용**:
  - 상세한 환경 설정 방법
  - 단계별 검증 방법
  - LAM→TSPM→CIM 파이프라인 테스트
  - 데이터베이스 저장 확인 방법
  - 트러블슈팅 가이드
- **언제 사용**: 새 개발자 온보딩, 문제 해결 시

### 📊 IMPROVEMENTS_SUMMARY.md
- **목적**: 기술적 개선사항 정리
- **내용**:
  - 코드 품질 개선 내역
  - Docker 최적화 과정
  - 성능 향상 결과
  - 아키텍처 변경 사항
- **언제 읽나**: 코드 리뷰, 아키텍처 개선 시

---

## 🛠 자동화 도구 사용법

### test_pipeline.sh
```bash
# 빠른 테스트
./test_pipeline.sh --quick

# 전체 테스트 (상세 출력)
./test_pipeline.sh --full --verbose

# 테스트 후 데이터 정리
./test_pipeline.sh --full --cleanup

# 도움말 확인
./test_pipeline.sh --help
```

**기능**:
- ✅ Docker 서비스 상태 확인
- ✅ API 연결 테스트
- ✅ 데이터베이스 연결 확인
- ✅ 테스트 사용자/이미지 생성
- ✅ LAM→TSPM→CIM 파이프라인 실행
- ✅ 결과 데이터베이스 저장 확인

### verify_database.py
```bash
# 전체 데이터베이스 검증
python verify_database.py --verbose

# 특정 작업만 검증
python verify_database.py --job-id 5

# 결과를 JSON으로 저장
python verify_database.py --export report.json

# 도움말 확인
python verify_database.py --help
```

**기능**:
- ✅ 데이터베이스 연결 확인
- ✅ 테이블 구조 검증
- ✅ 데이터 무결성 확인
- ✅ 파이프라인 단계별 검증
- ✅ 최종 결과 분석
- ✅ 성능 통계 제공

---

## 📋 개발 워크플로우

### 새 개발자 온보딩
```bash
1. README.md 읽기 (프로젝트 이해)
2. QUICKSTART.md 실행 (빠른 체험)
3. DEVELOPER_SETUP_GUIDE.md 따라하기 (완전 설정)
4. test_pipeline.sh 실행 (검증)
5. API 문서 탐색 (http://localhost:8000/api/docs/)
```

### 일반 개발 작업
```bash
1. 브랜치 생성
2. 코드 변경
3. ./test_pipeline.sh --quick (빠른 검증)
4. ./test_pipeline.sh --full (전체 검증)
5. python verify_database.py (DB 확인)
6. 커밋 및 PR 생성
```

### 문제 해결
```bash
1. DEVELOPER_SETUP_GUIDE.md의 트러블슈팅 섹션 확인
2. docker compose logs 확인
3. verify_database.py로 DB 상태 확인
4. 필요 시 서비스 재시작
```

---

## 🔗 외부 참조

### API 문서
- **Swagger UI**: http://localhost:8000/api/docs/
- **ReDoc**: http://localhost:8000/api/redoc/
- **OpenAPI 스키마**: http://localhost:8000/api/schema/

### 모니터링
- **Flower (Celery)**: http://localhost:5555/
- **Django Admin**: http://localhost:8000/admin/
- **헬스체크**: http://localhost:8000/api/v1/health/

### 개발 도구
- **실시간 로그**: `docker compose -f docker-compose.dev.yml logs -f`
- **DB 접속**: `docker compose -f docker-compose.dev.yml exec db psql -U smarteye_user smarteye_db`
- **Django Shell**: `docker compose -f docker-compose.dev.yml exec web python manage.py shell`

---

## 📞 지원 및 문의

### 문제 해결 순서
1. **문서 확인**: 해당 가이드의 트러블슈팅 섹션
2. **로그 확인**: `docker compose logs`
3. **자동 진단**: `./test_pipeline.sh` 또는 `verify_database.py`
4. **GitHub Issues**: 새 이슈 생성

### 개발 참여
1. 이 저장소를 Fork
2. 기능 브랜치 생성
3. 변경사항 구현
4. 테스트 통과 확인
5. Pull Request 생성

---

## 📊 프로젝트 상태

### ✅ 완료된 기능
- Docker 기반 개발 환경
- LAM → TSPM → CIM 파이프라인
- RESTful API
- 데이터베이스 스키마
- 자동화된 테스트 도구
- 종합적인 문서화

### 🔄 진행 중인 작업
- 성능 최적화
- 추가 AI 모델 통합
- 모니터링 강화

### 📈 향후 계획
- 프론트엔드 통합
- 클라우드 배포
- API 확장

---

**🎉 SmartEye Backend 개발에 참여해 주셔서 감사합니다!**

*더 나은 접근성과 포용적인 디지털 환경을 만들어가는 여정에 함께해 주세요.*
