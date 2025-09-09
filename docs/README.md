# SmartEye v0.4 - 문서 인덱스

이 디렉토리에는 SmartEye v0.4 시스템의 완전한 문서가 포함되어 있습니다.

## 📚 문서 목록

### 🚀 [사용자 가이드 (USER_GUIDE.md)](./USER_GUIDE.md)
**완전한 사용자 매뉴얼 - 모든 기능을 활용하기 위한 필수 가이드**

**주요 내용:**
- 시스템 개요 및 아키텍처
- 초기 설정 및 환경 구성
- 시스템 시작/중지/관리 명령어
- 모니터링 시스템 사용법
- 웹 애플리케이션 사용 방법
- 문제 해결 및 디버깅
- 백업/복구 절차
- 성능 최적화 가이드

**대상 독자:** 시스템 관리자, 개발자, 최종 사용자

---

### 🔌 [API 레퍼런스 (API_REFERENCE.md)](./API_REFERENCE.md)
**완전한 REST API 문서 - 개발 통합을 위한 필수 자료**

**주요 내용:**
- 전체 API 엔드포인트 명세
- 요청/응답 형식 및 예시
- 인증 및 보안 가이드
- 에러 코드 및 처리 방법
- Python, JavaScript, Java SDK
- cURL 명령어 예시
- 성능 최적화 가이드

**대상 독자:** 백엔드 개발자, 프론트엔드 개발자, API 사용자

---

### 🚢 [배포 가이드 (DEPLOYMENT_GUIDE.md)](./DEPLOYMENT_GUIDE.md)
**프로덕션 배포 완전 가이드 - 안전한 운영을 위한 필수 문서**

**주요 내용:**
- 시스템 요구사항 및 아키텍처
- SSL/TLS 인증서 설정
- Docker 프로덕션 배포
- 보안 구성 (방화벽, CORS, 컨테이너 보안)
- 모니터링 및 알림 설정
- 백업 자동화 및 복구 절차
- 성능 튜닝 및 최적화
- 운영 및 유지보수 가이드

**대상 독자:** DevOps 엔지니어, 시스템 관리자, 인프라 담당자

---

## 📖 문서 사용 가이드

### 🔰 처음 사용하시는 경우
1. **[사용자 가이드](./USER_GUIDE.md)** → 초기 설정 및 시스템 시작
2. **메인 README.md** → 빠른 시작 가이드 확인
3. **웹 브라우저에서 Swagger UI** → API 문서 실시간 확인

### 👨‍💻 개발자의 경우
1. **[API 레퍼런스](./API_REFERENCE.md)** → API 통합 개발
2. **[사용자 가이드](./USER_GUIDE.md)** → 개발 환경 설정
3. **Swagger UI** (http://localhost:8080/swagger-ui/index.html) → 실시간 API 테스트

### 🏢 운영 담당자의 경우
1. **[배포 가이드](./DEPLOYMENT_GUIDE.md)** → 프로덕션 배포
2. **[사용자 가이드](./USER_GUIDE.md)** → 시스템 관리 및 모니터링
3. **Grafana 대시보드** (http://localhost:3001) → 실시간 모니터링

---

## 🔄 문서 업데이트

### 최신 업데이트 (2025-09-09)
- ✅ **완전한 사용자 가이드** 작성 (10개 섹션, 200+ 명령어)
- ✅ **완전한 API 레퍼런스** 작성 (9개 섹션, 다중 언어 SDK)
- ✅ **완전한 배포 가이드** 작성 (프로덕션 준비 완료)
- ✅ **보안 강화** 반영 (non-root 컨테이너, CORS 강화, API 키 분리)
- ✅ **모니터링 시스템** 완전 통합 (Prometheus + Grafana)

### 문서 특징
- **실제 사용 가능**: 모든 명령어와 설정이 검증된 실제 동작 코드
- **완전한 예시**: cURL, Python, JavaScript, Java 코드 완전 제공
- **보안 중심**: 프로덕션 환경 보안 모범사례 적용
- **문제 해결**: 일반적인 문제와 해결책 상세 가이드

---

## 📞 문서 관련 문의

### 문서 개선 제안
- GitHub Issues를 통한 문서 개선 제안
- 실사용 중 발견한 오류나 개선점 제보

### 추가 문서 요청
- 특정 사용 사례에 대한 가이드 요청
- 고급 설정 및 커스터마이징 가이드

### 기술 지원
- 문서를 참조했지만 해결되지 않는 기술적 문제
- 시스템 통합 및 개발 관련 질문

---

**SmartEye v0.4** - 완전한 문서화로 더욱 쉬워진 AI 기반 학습지 분석 시스템 🚀

---

## 📋 빠른 참조

### 핵심 명령어
```bash
# 환경 설정
./scripts/setup-env.sh development

# 시스템 시작
./manage.sh start

# 모니터링 시작  
./scripts/start-monitoring.sh

# 상태 확인
./manage.sh status
```

### 주요 접속 URL
- **프론트엔드**: http://localhost:3000
- **API 문서**: http://localhost:8080/swagger-ui/index.html
- **모니터링**: http://localhost:3001 (admin/smarteye2024)
- **API 헬스체크**: http://localhost:8080/api/health

### 핵심 설정 파일
- **환경 설정**: `.env.development`, `.env.production`
- **Docker 구성**: `Backend/docker-compose.yml`
- **모니터링**: `monitoring/docker-compose.monitoring.yml`
- **환경 스크립트**: `scripts/setup-env.sh`