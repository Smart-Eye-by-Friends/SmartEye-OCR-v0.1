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
   - OCR 처리 클래스:
     'title', 'plain_text', 'abandon_text',
     'table_caption', 'table_footnote', 'unit', 'page',
     'isolated_formula', 'formula_caption', 'question_type',
     'question_text', 'question_number', 'list'

3. **🖼️ 그림/표 AI 설명 생성**

   - OpenAI GPT-4V를 활용한 시각 자료 설명
   - 시각 장애 아동을 위한 음성 변환 최적화
   - OpenAI Vision API 호출:
     'figure', 'table'

4. **📄 접근 가능한 문서 형태 변환**
   - JSON 형태의 구조화된 결과 제공
   - 시각화된 분석 결과 이미지

## 🏗️ 시스템 아키텍처

```
┌─────────────────┐    HTTP API     ┌──────────────────┐
│                 │◄───────────────►│                  │
│   Vue.js        │                 │   FastAPI        │
│   Frontend      │                 │   Backend        │
│   (Port 5173)   │                 │   (Port 8000)    │
│                 │                 │                  │
└─────────────────┘                 └──────────────────┘
                                             │
                                             ▼
                                    ┌──────────────────┐
                                    │  AI/ML Pipeline  │
                                    │                  │
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

- Python 3.8+
- Node.js 16+
- CUDA GPU (권장, CPU도 가능)

### 1. 저장소 클론

```bash
brew install tesseract tesseract-lang
```

### 3. 설치 확인

```bash
# Tesseract 버전 확인
tesseract --version

# 지원 언어 확인 (kor, eng 포함되어야 함)
tesseract --list-langs
```

### 4. 프론트엔드 설정

```bash
# Node.js 의존성 설치
npm install

# 개발 서버 실행
npm run dev
```

## 🏃‍♂️ 실행 방법

#### 백엔드 서버 시작 (터미널 1)

```bash
python api_server.py
```

- 서버 주소: http://localhost:8000
- API 문서: http://localhost:8000/docs

#### 프론트엔드 서버 시작 (터미널 2)

```bash
npm run dev
```

- 서버 주소: http://localhost:5173

### 5. 웹 브라우저 접속

http://localhost:5173 으로 접속하여 시스템을 사용하세요.

## 🔧 사용 방법

1. **이미지 업로드**: 분석할 학습지 이미지를 업로드하세요
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
