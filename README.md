## 🔍 pytesseract vs tesseract 차이점

### **pytesseract** (Python 패키지)

- **Python 래퍼**: Tesseract OCR 엔진을 Python에서 사용할 수 있게 해주는 인터페이스
- `pip install pytesseract`로 설치
- **실제 OCR 엔진은 포함되지 않음**

### **tesseract** (실제 OCR 엔진)

- **Google에서 개발한 실제 OCR 엔진** (C++ 기반)
- 시스템에 별도로 설치해야 함
- pytesseract가 이 엔진을 호출함

## 🛠️ 설치 필요성

### **개발 환경 (로컬):**

```bash
# 1. Tesseract OCR 엔진 설치 (Windows)
# https://github.com/UB-Mannheim/tesseract/wiki 에서 다운로드

# 2. Python 패키지 설치
pip install pytesseract

# 3. 한국어 언어팩 설치 (별도 다운로드 필요)
```

### **서버 배포 시 (Ubuntu/Linux):**

```bash
# 1. Tesseract OCR 엔진 및 한국어 언어팩 설치
sudo apt update
sudo apt install tesseract-ocr tesseract-ocr-kor

# 2. Python 패키지는 requirements.txt에 포함
pip install pytesseract
```

## 🚨 현재 OCR이 안되는 이유

여러분의 JSON 결과에서 OCR 텍스트가 비어있는 이유는 아마:

### **1. Tesseract 엔진이 설치되지 않음**

```bash
# 확인 방법
tesseract --version
# 오류가 나면 설치되지 않음
```

### **2. 한국어 언어팩 부족**

```bash
# 확인 방법
tesseract --list-langs
# 'kor'이 없으면 한국어 지원 안됨
```

### **3. PATH 설정 문제**

```python
# api_server.py에서 경로 명시적 설정 (Windows)
import pytesseract
pytesseract.pytesseract.tesseract_cmd = r'C:\Program Files\Tesseract-OCR\tesseract.exe'
```

## 🎯 해결 방법

### **즉시 확인할 사항:**

```bash
# 1. 현재 Tesseract 설치 상태 확인
tesseract --version

# 2. 지원 언어 확인
tesseract --list-langs

# 3. Python에서 연결 확인
python -c "import pytesseract; print(pytesseract.get_tesseract_version())"
```

### **Windows에서 Tesseract 설치:**

1. **다운로드**: https://github.com/UB-Mannheim/tesseract/wiki
2. **설치**: `tesseract-ocr-w64-setup-v5.3.3.20231005.exe`
3. **한국어 언어팩**: 설치 시 "Additional language data" 체크
4. **PATH 확인**: 시스템 환경변수에 추가되었는지 확인

### **api_server.py에 경로 추가:**

```python
# api_server.py 상단에 추가
import pytesseract

# Windows의 경우 Tesseract 경로 명시
import platform
if platform.system() == "Windows":
    pytesseract.pytesseract.tesseract_cmd = r'C:\Program Files\Tesseract-OCR\tesseract.exe'
```

## 🌐 서버 배포 시 고려사항

### **Docker 컨테이너 사용 시:**

```dockerfile
FROM python:3.9

# Tesseract 및 한국어 언어팩 설치
RUN apt-get update && apt-get install -y \
    tesseract-ocr \
    tesseract-ocr-kor \
    && apt-get clean

# Python 의존성 설치
COPY requirements.txt .
RUN pip install -r requirements.txt
```

### **Ubuntu 서버 배포 시:**

```bash
# 서버에서 실행
sudo apt update
sudo apt install tesseract-ocr tesseract-ocr-kor
pip install -r requirements.txt
```

## 📝 결론

**예, 웹 서비스 배포 시에도 Tesseract OCR 엔진을 서버에 설치해야 합니다!**

- **pytesseract**: Python 인터페이스만 제공
- **tesseract**: 실제 OCR 처리 엔진
- **둘 다 필요**: pytesseract → tesseract 호출 구조

현재 OCR이 안되는 이유는 **Tesseract 엔진 자체가 설치되지 않았기 때문**일 가능성이 높습니다. 위의 설치 과정을 따라해보시고 결과를 알려주세요! 🚀
