# � SmartEyeSsen 학습지 분석 시스템

**시각 장애 아동을 위한 AI 기반 학습지 분석 및 텍스트 변환 시스템**

## 🌟 프로젝트 개요

SmartEyeSsen은 기존 TESSERACT-OCR-WEB 프로젝트를 확장하여 학습지 이미지를 종합적으로 분석하고 접근 가능한 형태로 변환하는 통합 시스템입니다.

### 시스템 구성

- **LAM (Layout Analysis Module)**: DocLayout-YOLO를 사용한 레이아웃 분석
- **TSPM (Text & Scene Processing Module)**: Tesseract OCR + OpenAI Vision API
- **CIM (Content Integration Module)**: 결과 통합 및 시각화

## 🚀 주요 기능

1. **🎯 학습지 레이아웃 자동 분석**

   - SmartEyeSsen 파인튜닝 모델 활용
   - 제목, 텍스트, 그림, 표, 수식 등 다양한 요소 감지

2. **📝 텍스트 영역 OCR 처리**

   - 한국어/영어 혼합 텍스트 인식
   - 높은 정확도의 텍스트 추출

3. **🖼️ 그림/표 AI 설명 생성**

   - OpenAI GPT-4V를 활용한 시각 자료 설명
   - 시각 장애 아동을 위한 음성 변환 최적화

4. **📄 접근 가능한 문서 형태 변환**
   - JSON 형태의 구조화된 결과 제공
   - 시각화된 분석 결과 이미지

## 🏗️ 시스템 아키텍처

### 디렉토리 구조
```
SmartEye_v0.4/
├── Backend/                        # 백엔드 서비스
│   ├── smarteye-backend/          # Java Spring Boot API
│   ├── smarteye-lam-service/      # Python FastAPI LAM Service
│   ├── nginx/                     # 리버스 프록시
│   ├── docker-compose.yml         # 서비스 오케스트레이션
│   └── start_services_enhanced.sh # 백엔드 시작 스크립트
├── Frontend/                       # 프론트엔드 (예정)
├── start_system.sh                # 전체 시스템 시작
├── check_system.sh               # 시스템 상태 확인
└── stop_system.sh                # 전체 시스템 중지
```

### 서비스 아키텍처
```
┌─────────────────┐    HTTP API     ┌──────────────────┐
│                 │◄───────────────►│    Nginx         │
│   Frontend      │                 │  (Port 80/443)   │
│   (예정)        │                 │                  │
│                 │                 └──────────────────┘
└─────────────────┘                          │
                                             ▼
                                   ┌──────────────────┐
                                   │ Java Spring Boot │
                                   │   Backend API    │
                                   │   (Port 8080)    │
                                   └──────────────────┘
                                             │
                                             ▼
                                   ┌──────────────────┐
                                   │  Python FastAPI  │
                                   │   LAM Service    │
                                   │   (Port 8001)    │
                                    │ ┌─────────────┐  │
                                    │ │ DocLayout   │  │
                                    │ │ YOLO        │  │
                                    │ └─────────────┘  │
                                    │                  │
                                    │ ┌─────────────┐  │
                                    │ │ Tesseract   │  │
                                    │ │ OCR         │  │
                                    │ └─────────────┘  │
                                    │                  │
                                    │ ┌─────────────┐  │
                                    │ │ OpenAI      │  │
                                    │ │ GPT-4V      │  │
                                    │ └─────────────┘  │
                                    └──────────────────┘
```

## 📋 설치 및 실행 가이드

### 사전 요구사항

- Docker & Docker Compose
- Git

### 1. 저장소 클론

```bash
git clone <repository-url>
cd SmartEye_v0.4
```

### 2. 시스템 시작

```bash
# 전체 시스템 시작
chmod +x start_system.sh
./start_system.sh
```

### 3. 접속 확인

- **Backend API**: http://localhost:8080
- **LAM Service**: http://localhost:8001  
- **API 문서**: http://localhost:8080/swagger-ui/index.html
- **FastAPI 문서**: http://localhost:8001/docs

### 4. 시스템 관리

```bash
# 시스템 상태 확인
./check_system.sh

# 시스템 중지
./stop_system.sh

# Backend 개별 관리
cd Backend
./start_services_enhanced.sh  # Backend만 시작
./check_services.sh           # Backend 상태 확인
```

## 🏃‍♂️ 개발 환경 실행 방법

### Backend 개발 환경

```bash
# Backend 디렉토리로 이동
cd Backend

# 개발 환경 시작
docker-compose -f docker-compose-dev.yml up -d

# Java 백엔드 로컬 실행
cd smarteye-backend
./gradlew bootRun --args='--spring.profiles.active=dev'

# Python LAM 서비스 로컬 실행
cd ../smarteye-lam-service
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8001 --reload
```

## 🔧 사용 방법

1. **이미지 업로드**: POST `/api/document/analyze`로 분석할 학습지 이미지를 업로드하세요
2. **모델 선택**:
   - SmartEyeSsen (추천): 학습지에 특화된 파인튜닝 모델
   - DocStructBench: 구조화된 문서 분석에 최적화
   - 기타 옵션들
3. **API 키 입력** (선택사항): OpenAI API 키 입력 시 그림/표 AI 설명 생성
4. **분석 시작**: "🚀 분석 시작" 버튼 클릭
5. **결과 확인**: 다양한 탭에서 분석 결과 확인
   - 🎯 레이아웃 분석: 감지된 요소들의 시각화
   - 📄 CIM 결과: 텍스트/설명이 통합된 문서 시각화
   - 📊 분석 통계: 감지 요소 통계 및 JSON 다운로드
   - 📝 OCR 텍스트: 추출된 모든 텍스트
   - 🤖 AI 설명: 그림/표에 대한 AI 생성 설명

## 💡 기술 스택

### 백엔드

- **FastAPI**: 고성능 웹 프레임워크
- **DocLayout-YOLO**: 문서 레이아웃 분석
- **Tesseract OCR**: 텍스트 인식
- **OpenAI GPT-4V**: 이미지 설명 생성
- **PyTorch**: 딥러닝 프레임워크
- **OpenCV**: 이미지 처리

### 프론트엔드

- **Vue.js 3**: 반응형 웹 프레임워크
- **TypeScript**: 타입 안전성
- **Vite**: 빌드 도구
- **Axios**: HTTP 클라이언트

## ⚙️ 환경 설정

### Python 의존성 관리

이 프로젝트는 `requirements.txt` 파일을 통해 Python 패키지 의존성을 관리합니다:

- **의존성 명시**: 프로젝트에서 사용하는 모든 Python 패키지와 버전을 명확히 기록
- **환경 재현**: 다른 개발자나 서버에서 동일한 환경을 쉽게 구축
- **자동 설치**: `pip install -r requirements.txt` 명령으로 한 번에 모든 패키지 설치
- **버전 관리**: 최소 버전을 지정하여 호환성 문제 방지

### OpenAI API 키 설정

그림과 표에 대한 AI 설명을 생성하려면 OpenAI API 키가 필요합니다.

1. https://openai.com 에서 API 키 발급
2. 웹 인터페이스에서 API 키 입력

## 📝 라이선스

This project is licensed under the MIT License.

## 🤝 기여하기

1. 이 저장소를 Fork합니다
2. 새로운 기능 브랜치를 생성합니다 (`git checkout -b feature/AmazingFeature`)
3. 변경사항을 커밋합니다 (`git commit -m 'Add some AmazingFeature'`)
4. 브랜치에 푸시합니다 (`git push origin feature/AmazingFeature`)
5. Pull Request를 생성합니다

---

**⚠️ 중요**: Tesseract OCR 엔진이 시스템에 설치되어 있어야 정상적으로 작동합니다!
