# SmartEye Backend QuickStart 가이드 ⚡

**5분 만에 SmartEye Backend를 실행하고 테스트하는 방법**

## 🚀 빠른 시작

### 1. 환경 설정 (1분)

```bash
# 1. 프로젝트 디렉토리로 이동
cd /home/jongyoung3/SmartEye_v0.1/smarteye_backend

# 2. 환경 변수 파일 복사
cp .env.docker.example .env.docker

# 3. 필수 환경 변수 설정 (기본값 사용 가능)
echo "SECRET_KEY=quickstart-secret-key-$(date +%s)" >> .env.docker
echo "OPENAI_API_KEY=your-api-key-here" >> .env.docker
```

### 2. Docker 서비스 시작 (2분)

```bash
# 모든 서비스 빌드 및 시작
docker compose -f docker-compose.dev.yml up --build -d

# 서비스 상태 확인
docker compose -f docker-compose.dev.yml ps
```

### 3. 자동 테스트 실행 (2분)

```bash
# 파이프라인 전체 테스트 실행
./test_pipeline.sh --full --verbose

# 또는 빠른 테스트
./test_pipeline.sh --quick
```

---

## 🔍 수동 확인 방법

### API 접속 확인
```bash
# 헬스체크
curl http://localhost:8000/api/v1/health/

# 브라우저에서 접속
# - API 문서: http://localhost:8000/api/docs/
# - 관리자: http://localhost:8000/admin/ (admin/admin)
# - Flower: http://localhost:5555/ (admin/admin)
```

### 데이터베이스 확인
```bash
# 데이터베이스 검증 스크립트 실행
docker compose -f docker-compose.dev.yml exec web python verify_database.py --verbose

# 직접 데이터베이스 접속
docker compose -f docker-compose.dev.yml exec db psql -U smarteye_user smarteye_db
```

### 파이프라인 테스트
```bash
# 1. 테스트 사용자 생성 및 토큰 받기
TOKEN=$(docker compose -f docker-compose.dev.yml exec web python -c "
import os, django
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'smarteye.settings.development')
django.setup()
from django.contrib.auth import get_user_model
from rest_framework_simplejwt.tokens import RefreshToken
User = get_user_model()
user, _ = User.objects.get_or_create(username='quicktest', defaults={'email': 'test@quick.com'})
user.set_password('test123')
user.save()
print(RefreshToken.for_user(user).access_token)
" | tr -d '\r')

# 2. 테스트 이미지 생성
docker compose -f docker-compose.dev.yml exec web python -c "
from PIL import Image, ImageDraw
img = Image.new('RGB', (400, 300), 'white')
draw = ImageDraw.Draw(img)
draw.text((50, 50), 'QuickStart Test', fill='black')
draw.rectangle([50, 100, 350, 200], outline='blue', width=2)
img.save('/tmp/quicktest.jpg')
print('Test image created')
"

# 3. 파이프라인 실행
docker cp $(docker compose -f docker-compose.dev.yml ps -q web):/tmp/quicktest.jpg ./quicktest.jpg

curl -X POST http://localhost:8000/api/v1/analysis/jobs/upload_and_analyze/ \
     -H "Authorization: Bearer $TOKEN" \
     -F "files=@./quicktest.jpg" \
     -F "job_name=QuickStart Test" \
     -F "enable_ocr=true"

rm ./quicktest.jpg
```

---

## ✅ 성공 확인 체크리스트

**Docker 서비스 ✅**
- [ ] `docker compose ps`에서 모든 서비스가 `Up (healthy)` 상태
- [ ] http://localhost:8000/api/v1/health/ 응답 정상

**데이터베이스 ✅**
- [ ] `verify_database.py` 스크립트 오류 없이 완료
- [ ] 모든 테이블 생성 확인

**파이프라인 ✅**
- [ ] 파일 업로드 성공
- [ ] LAM → TSPM → CIM 각 단계 완료
- [ ] 최종 결과 데이터베이스 저장 확인

---

## 🔧 문제 해결

### 포트 충돌
```bash
# 사용 중인 포트 확인
sudo netstat -tulpn | grep -E ":8000|:5432|:6379|:5555"

# 충돌 시 서비스 중지 후 재시작
docker compose -f docker-compose.dev.yml down
docker compose -f docker-compose.dev.yml up -d
```

### 메모리 부족
```bash
# 메모리 사용량 확인
docker stats --no-stream

# 설정 조정 (.env.docker 파일)
echo "SMARTEYE_BATCH_SIZE=1" >> .env.docker
echo "SMARTEYE_MEMORY_LIMIT_MB=512" >> .env.docker
docker compose -f docker-compose.dev.yml restart
```

### 서비스 재시작
```bash
# 특정 서비스만 재시작
docker compose -f docker-compose.dev.yml restart web celery-worker

# 전체 재시작
docker compose -f docker-compose.dev.yml restart
```

---

## 🎯 다음 단계

1. **개발 시작**: `DEVELOPER_SETUP_GUIDE.md` 참조
2. **상세 테스트**: `test_pipeline.sh --full --verbose` 실행
3. **API 탐색**: http://localhost:8000/api/docs/ 방문
4. **모니터링**: http://localhost:5555/ 에서 Celery 작업 확인

---

**🎉 성공! SmartEye Backend가 정상적으로 작동하고 있습니다.**
