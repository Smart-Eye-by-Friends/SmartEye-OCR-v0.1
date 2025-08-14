# � SmartEyeSsen 학습지 분석 시스템

AI 기반 학습지 레이아웃 분석 및 OCR 시스템입니다. SmartEyeSsen 모델을 사용하여 19개 클래스의 학습지 요소를 감지하고, Tesseract OCR과 OpenAI Vision API를 통해 텍스트 추출 및 AI 설명을 생성합니다.

## 🚀 주요 기능

- **레이아웃 분석**: DocLayout-YOLO 기반 19개 클래스 감지
- **OCR 처리**: Tesseract를 사용한 한국어/영어 텍스트 추출
- **AI 설명**: OpenAI GPT-4V를 활용한 그림/표 자동 설명
- **웹 인터페이스**: Vue.js 3 + TinyMCE 에디터 통합
- **실시간 편집**: 추출된 텍스트의 실시간 편집 가능

## 🛠️ 설치 및 설정

### 1. Python 환경 설정

```bash
# Python 의존성 설치
pip install -r requirements.txt

# 또는 conda 환경에서
conda activate pytorch
pip install -r requirements.txt
```

### 2. Tesseract OCR 설치 (필수)

#### Windows 환경:
1. **Tesseract 다운로드**: https://github.com/UB-Mannheim/tesseract/wiki
2. **설치 파일 실행**: `tesseract-ocr-w64-setup-v5.x.x.exe`
3. **한국어 언어팩 포함**: 설치 시 "Additional language data (kor)" 체크
4. **기본 설치 경로**: `C:\Program Files\Tesseract-OCR`

#### Ubuntu/Linux 환경:
```bash
sudo apt update
sudo apt install tesseract-ocr tesseract-ocr-kor
```

#### macOS 환경:
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

### 백엔드 서버 실행
```bash
python api_server.py
```

### 프론트엔드 서버 실행
```bash
npm run dev
```

### 브라우저 접속
- **프론트엔드**: http://localhost:5173/
- **API 문서**: http://localhost:8000/docs

## 🔧 Tesseract OCR 트러블슈팅

### 문제 1: OCR 결과가 비어있는 경우

**원인**: Tesseract 엔진이 설치되지 않았거나 경로 문제

**해결방법**:
```python
# api_server.py에 이미 포함된 설정 확인
import pytesseract
import platform

if platform.system() == "Windows":
    pytesseract.pytesseract.tesseract_cmd = r'C:\Program Files\Tesseract-OCR\tesseract.exe'
```

### 문제 2: 한국어 OCR 인식 불가

**원인**: 한국어 언어팩 미설치

**해결방법**:
- Windows: Tesseract 재설치 시 "kor" 언어팩 체크
- Linux: `sudo apt install tesseract-ocr-kor`
- 확인: `tesseract --list-langs | grep kor`

### 문제 3: PATH 에러 발생

**원인**: 시스템 환경변수에 Tesseract가 추가되지 않음

**해결방법**:
- Windows: 시스템 속성 → 환경변수 → Path에 `C:\Program Files\Tesseract-OCR` 추가
- 또는 코드에서 절대 경로 지정 (이미 적용됨)

## 📊 OCR 처리 대상 클래스

다음 10개 클래스에 대해 Tesseract OCR이 수행됩니다:
- `title` - 제목
- `plain text` - 일반 텍스트
- `abandon text` - 폐기된 텍스트
- `table caption` - 표 제목
- `table footnote` - 표 각주
- `isolated formula` - 독립 수식
- `formula caption` - 수식 제목
- `question type` - 문제 유형
- `question text` - 문제 텍스트
- `question number` - 문제 번호

## 🎯 지원하는 OCR 언어

- **한국어** (`kor`): 완전 지원
- **영어** (`eng`): 완전 지원
- **혼합 텍스트**: `kor+eng` 동시 처리

## 🐳 Docker 배포 (선택사항)

```dockerfile
FROM python:3.9

# Tesseract 및 한국어 언어팩 설치
RUN apt-get update && apt-get install -y \
    tesseract-ocr \
    tesseract-ocr-kor \
    && apt-get clean

# 애플리케이션 코드 복사
COPY . /app
WORKDIR /app

# Python 의존성 설치
RUN pip install -r requirements.txt

# 포트 노출
EXPOSE 8000

# 서버 실행
CMD ["python", "api_server.py"]
```

## 🔍 pytesseract vs tesseract 차이점

### **pytesseract** (Python 패키지)
- **Python 래퍼**: Tesseract OCR 엔진을 Python에서 사용할 수 있게 해주는 인터페이스
- `pip install pytesseract`로 설치
- **실제 OCR 엔진은 포함되지 않음**

### **tesseract** (실제 OCR 엔진)
- **Google에서 개발한 실제 OCR 엔진** (C++ 기반)
- 시스템에 별도로 설치해야 함
- pytesseract가 이 엔진을 호출함

## 📁 프로젝트 구조

```
SmartEye-FrontWeb/
├── api_server.py              # FastAPI 백엔드 서버
├── src/
│   ├── App.vue               # 메인 Vue.js 컴포넌트
│   ├── main.js               # Vue 앱 엔트리포인트
│   └── components/
│       └── ImageLoader.vue   # 이미지 업로드 컴포넌트
├── public/                   # 정적 파일
├── static/                   # 분석 결과 저장
├── requirements.txt          # Python 의존성
├── package.json             # Node.js 의존성
└── README.md                # 이 파일
```

## � 개발 워크플로우

1. **이미지 업로드**: 드래그&드롭으로 학습지 이미지 업로드
2. **레이아웃 분석**: SmartEyeSsen 모델로 19개 클래스 감지
3. **OCR 처리**: 텍스트 영역에 대해 Tesseract OCR 수행
4. **AI 설명**: 그림/표에 대해 OpenAI Vision API 호출
5. **결과 통합**: CIM 모듈로 JSON 형태 통합
6. **사용자 편집**: TinyMCE 에디터로 텍스트 수정
7. **결과 다운로드**: 편집된 텍스트 및 JSON 다운로드

## 🎨 사용 기술 스택

### Backend
- **Python 3.9+**
- **FastAPI**: REST API 서버
- **PyTorch**: 딥러닝 모델 실행
- **Tesseract OCR**: 텍스트 추출
- **OpenAI API**: AI 이미지 설명

### Frontend  
- **Vue.js 3**: 프론트엔드 프레임워크
- **TinyMCE**: 리치 텍스트 에디터
- **Axios**: HTTP 클라이언트
- **Vite**: 빌드 도구

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
