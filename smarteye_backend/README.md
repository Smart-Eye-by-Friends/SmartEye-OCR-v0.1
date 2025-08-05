# SmartEye Backend Service

Django 기반 SmartEye 문서 분석 백엔드 서비스입니다.

## 프로젝트 구조

```
smarteye_backend/
├── manage.py
├── requirements.txt
├── .env.example
├── smarteye/
│   ├── __init__.py
│   ├── settings/
│   │   ├── __init__.py
│   │   ├── base.py
│   │   ├── development.py
│   │   └── production.py
│   ├── urls.py
│   └── wsgi.py
├── apps/
│   ├── __init__.py
│   ├── users/
│   ├── analysis/
│   ├── files/
│   └── api/
├── core/
│   ├── __init__.py
│   ├── lam/          # Layout Analysis Module
│   ├── tspm/         # Text & Scene Processing Module
│   └── cim/          # Content Integration Module
├── static/
├── media/
├── logs/
└── requirements.txt
```

## 설치 및 실행

1. 가상환경 생성 및 활성화
```bash
python -m venv venv
source venv/bin/activate  # Linux/Mac
# venv\Scripts\activate  # Windows
```

2. 패키지 설치
```bash
pip install -r requirements.txt
```

3. 환경 변수 설정
```bash
cp .env.example .env
# .env 파일 편집
```

4. 데이터베이스 마이그레이션
```bash
python manage.py makemigrations
python manage.py migrate
```

5. 관리자 계정 생성
```bash
python manage.py createsuperuser
```

6. 개발 서버 실행
```bash
python manage.py runserver
```

## API 엔드포인트

- `/api/v1/auth/` - 인증 관련
- `/api/v1/analysis/` - 문서 분석 작업
- `/api/v1/files/` - 파일 업로드/관리
- `/api/v1/users/` - 사용자 관리

## 주요 기능

1. **LAM (Layout Analysis Module)**: DocLayout-YOLO를 활용한 레이아웃 분석
2. **TSPM (Text & Scene Processing Module)**: OCR 및 이미지 설명 생성
3. **CIM (Content Integration Module)**: 최종 결과 통합 및 출력
4. **비동기 처리**: Celery를 활용한 백그라운드 작업
5. **실시간 진행률**: WebSocket을 통한 실시간 업데이트
