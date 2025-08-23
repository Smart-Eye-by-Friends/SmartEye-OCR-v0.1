# 2단계 LAM 마이크로서비스 배포 스크립트

# LAM 서비스 Docker 이미지 빌드
echo "LAM 마이크로서비스 Docker 이미지 빌드 중..."
cd smarteye-lam-service
docker build -t smarteye-lam-service:latest .

# LAM 서비스 컨테이너 실행
echo "LAM 마이크로서비스 시작 중..."
docker run -d --name smarteye-lam-service \
  -p 8081:8000 \
  -e PYTHONPATH=/app \
  smarteye-lam-service:latest

# 서비스 시작 대기
echo "LAM 서비스 시작 대기 중..."
sleep 10

# 헬스 체크
echo "LAM 서비스 상태 확인..."
curl -f http://localhost:8081/health || echo "LAM 서비스 연결 실패"

cd ..

echo "LAM 마이크로서비스 배포 완료!"
echo "접속 URL: http://localhost:8081"
echo "상태 확인: curl http://localhost:8081/health"
echo "모델 정보: curl http://localhost:8081/models/info"
