# SmartEye OCR - AI 학습지 분석 시스템 🎯

AI를 활용한 스마트 학습지 OCR 및 구조 분석 시스템입니다. Vue.js에서 React로의 프론트엔드 마이그레이션과 Python에서 Java Spring Boot로의 백엔드 포팅을 통해 팀 협업 기반의 모던 웹 애플리케이션으로 발전시키고 있습니다.

## 🎯 프로젝트 목표

- **정확한 OCR**: 한국어 학습지에 특화된 텍스트 인식
- **지능형 레이아웃 분석**: AI 기반 문서 구조 파악
- **문제별 자동 정렬**: 섹션별 문제 분류 및 구조화
- **팀 협업 지향**: 프론트엔드/백엔드 분리 개발 환경

## 📁 프로젝트 구조

```
Smart-Eye-OCR/
├── 📂 frontend/                    # React 18 프론트엔드
│   ├── src/
│   │   ├── components/             # 리액트 컴포넌트 (11개)
│   │   ├── hooks/                  # 커스텀 훅 (2개)
│   │   ├── services/               # API 서비스 레이어
│   │   └── styles/                 # CSS 스타일시트
│   ├── public/
│   ├── package.json
│   └── README.md
├── 📂 backend/                     # Java Spring Boot 백엔드
│   ├── src/main/java/
│   ├── src/main/resources/
│   ├── pom.xml (또는 build.gradle)
│   └── README.md
├── 📂 legacy/                      # 기존 Python 구현체 (참고용)
│   └── SmartEye-FrontWeb/
├── 📂 docs/                        # 프로젝트 문서
│   ├── API.md                      # API 명세서
│   └── DEPLOYMENT.md               # 배포 가이드
└── README.md                       # 이 파일
```

## 🚀 팀 역할 분담

### 👨‍💻 프론트엔드 개발팀

- **기술 스택**: React 18, Hooks, Axios, TinyMCE
- **담당 업무**:
  - Vue.js → React 마이그레이션 완료 ✅
  - 사용자 인터페이스 개발
  - 반응형 디자인 구현
  - API 통신 레이어 구축

### 👩‍💻 백엔드 개발팀

- **기술 스택**: Java 17, Spring Boot 3.x, Maven
- **담당 업무**:
  - Python FastAPI → Java Spring Boot 포팅
  - REST API 엔드포인트 개발
  - OCR 및 AI 분석 서비스 구현
  - 파일 처리 및 데이터베이스 연동

## 🛠️ 주요 기능

### 📤 이미지 업로드 및 분석

- 드래그 앤 드롭 지원
- 다중 AI 모델 선택 (SmartEyeSsen 권장)
- 실시간 분석 진행률 표시

### 🧠 AI 기반 분석

- **레이아웃 분석**: 문서 구조 자동 감지
- **텍스트 인식**: 한국어 최적화 OCR
- **이미지 설명**: OpenAI API 연동
- **구조화된 결과**: 문제별 자동 정렬

### 📊 결과 표시 및 편집

- 5개 탭 기반 결과 뷰
- 실시간 텍스트 편집 (TinyMCE)
- 워드 문서 출력 기능
- 클립보드 복사 및 파일 다운로드

## ⚡ 빠른 시작

### 🖥️ 프론트엔드 실행

```bash
# frontend 폴더로 이동
cd frontend

# 의존성 설치
npm install

# 개발 서버 시작
npm start

# 브라우저에서 접속
# http://localhost:3000
```

### 🔧 백엔드 실행 (개발 예정)

```bash
# backend 폴더로 이동
cd backend

# Maven 빌드
./mvnw clean install

# Spring Boot 실행
./mvnw spring-boot:run

# API 서버 접속
# http://localhost:8080
```

### 🔄 기존 Python 백엔드 실행 (참고용)

```bash
# legacy 폴더로 이동
cd legacy

# 가상환경 활성화
conda activate pytorch

# 의존성 설치
pip install -r requirements.txt

# Python API 서버 실행
python api_server.py
```

## 🌿 Git 브랜치 전략

### 메인 브랜치

- `main`: 프로덕션 배포용
- `develop`: 통합 개발 브랜치
- `feature/frontWeb`: 프론트엔드 기능 개발
- `feature/backendWeb`: 백엔드 기능 개발

### 현재 진행상황

- ✅ `migration/vue-to-react-uiux`: React 마이그레이션 완료
- 🔄 백엔드 Java 포팅 진행 중
- 📋 통합 테스트 예정

## 🔌 API 명세

### 주요 엔드포인트

- `POST /api/analyze` - 기본 분석
- `POST /api/analyze-structured` - 구조화된 분석
- `POST /api/save-as-word` - 워드 문서 저장
- `GET /api/health` - 헬스 체크

자세한 API 명세는 [docs/API.md](docs/API.md)를 참고하세요.

## 🧪 기술 세부사항

### 프론트엔드 아키텍처

```
React 18 App
├── 🧩 11개 모듈화된 컴포넌트
├── 🪝 2개 커스텀 훅 (useAnalysis, useTextEditor)
├── 🔌 Axios 기반 API 서비스
├── 🎨 CSS Variables 반응형 디자인
└── 📱 PWA 지원 준비
```

### 백엔드 아키텍처 (계획)

```
Spring Boot 3.x
├── 🎯 RESTful API Controllers
├── 🔧 비즈니스 로직 Services
├── 📄 DTO/Entity 모델링
├── 🗄️ JPA/Hibernate ORM
└── 🔐 Spring Security 통합
```

## 📊 현재 개발 상태

### ✅ 완료된 작업

- [x] Vue.js → React 18 완전 마이그레이션
- [x] 모던 React Hooks 패턴 적용
- [x] 반응형 UI/UX 디자인 구현
- [x] API 서비스 레이어 구축
- [x] 커스텀 훅 기반 상태 관리
- [x] 문제 레이아웃 정렬 알고리즘 개선
- [x] 구조화된 JSON 생성기 구현

### 🔄 진행 중인 작업

- [ ] Python → Java Spring Boot 백엔드 포팅
- [ ] RESTful API 엔드포인트 구현
- [ ] 데이터베이스 스키마 설계
- [ ] 파일 업로드 처리 로직 포팅

### 📋 예정 작업

- [ ] 프론트엔드-백엔드 통합 테스트
- [ ] 성능 최적화 및 캐싱
- [ ] CI/CD 파이프라인 구축
- [ ] 프로덕션 배포 및 모니터링

## 🤝 협업 가이드

### Pull Request 프로세스

1. **feature 브랜치에서 개발**

   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **코드 작성 및 테스트**

   ```bash
   # 프론트엔드
   npm test
   npm run build

   # 백엔드
   ./mvnw test
   ./mvnw package
   ```

3. **Pull Request 생성**

   - Base: `develop` ← Compare: `feature/your-feature-name`
   - 상대방 팀원을 리뷰어로 지정
   - 체크리스트 작성 및 테스트 결과 첨부

4. **코드 리뷰 및 머지**
   - 상호 리뷰를 통한 품질 관리
   - 통합 테스트 후 develop 브랜치 머지

### 커밋 메시지 컨벤션

```bash
✨ feat(frontend): Add React image upload component
🐛 fix(backend): Fix CORS configuration for localhost:3000
📝 docs(api): Update API documentation
🔧 config: Setup CI/CD pipeline
♻️ refactor: Improve error handling structure
🧪 test: Add unit tests for analysis service
```

## 📚 참고 자료

- [프론트엔드 README](frontend/README.md) - React 앱 상세 가이드
- [백엔드 README](backend/README.md) - Spring Boot 개발 가이드
- [API 문서](docs/API.md) - REST API 명세서
- [배포 가이드](docs/DEPLOYMENT.md) - 프로덕션 배포 방법

## 🔧 환경 설정

### 개발 환경 요구사항

- **Node.js**: 18.x 이상
- **Java**: 17 이상
- **Maven**: 3.8 이상
- **Git**: 2.x 이상

### 환경 변수 설정

#### 프론트엔드 (`.env`)

```env
REACT_APP_API_URL=http://localhost:8080
REACT_APP_VERSION=1.0.0
```

#### 백엔드 (`application.yml`)

```yaml
server:
  port: 8080
spring:
  web:
    cors:
      allowed-origins: http://localhost:3000
```

## 📞 지원 및 기여

### 이슈 리포트

- 버그 발견 시 GitHub Issues에 등록
- 기능 요청은 Feature Request 템플릿 사용

### 기여 방법

1. 프로젝트 포크
2. 기능 브랜치 생성
3. 변경사항 커밋
4. Pull Request 제출

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 공개됩니다. 자세한 내용은 [LICENSE](LICENSE) 파일을 참고하세요.

---

## 👥 개발팀

**Smart-Eye-by-Friends**

- 🎨 **프론트엔드**: React 18 기반 UI/UX 개발
- ⚙️ **백엔드**: Java Spring Boot 기반 API 개발
- 🤖 **AI/ML**: OCR 및 레이아웃 분석 알고리즘

**버전**: 1.0.0  
**최종 업데이트**: 2024년 9월 4일

---

🎯 **목표**: 팀 협업을 통한 고품질 AI 학습지 분석 시스템 구축  
🚀 **비전**: 교육 현장의 디지털 전환을 이끄는 혁신적인 OCR 솔루션
