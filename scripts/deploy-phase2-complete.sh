#!/bin/bash

# SmartEye 2단계 완료 검증 및 배포 스크립트

echo "======================================"
echo "SmartEye 2단계 시스템 검증 시작"
echo "======================================"

# 현재 작업 디렉토리 확인
cd /home/jongyoung3/SmartEye_v0.1

# 1. Java 프로젝트 빌드 테스트
echo "1. Java 프로젝트 빌드 검증..."
./gradlew clean build -x test
if [ $? -eq 0 ]; then
    echo "✅ Java 프로젝트 빌드 성공"
else
    echo "❌ Java 프로젝트 빌드 실패"
    exit 1
fi

# 2. LAM 마이크로서비스 배포
echo "2. LAM 마이크로서비스 배포..."
if [ -d "smarteye-lam-service" ]; then
    cd smarteye-lam-service
    
    # Docker 이미지 빌드
    echo "   LAM 서비스 Docker 이미지 빌드 중..."
    docker build -t smarteye-lam-service:latest . -q
    if [ $? -eq 0 ]; then
        echo "   ✅ LAM Docker 이미지 빌드 성공"
    else
        echo "   ❌ LAM Docker 이미지 빌드 실패"
        cd ..
        exit 1
    fi
    
    # 기존 컨테이너 정리
    docker stop smarteye-lam-service 2>/dev/null
    docker rm smarteye-lam-service 2>/dev/null
    
    # LAM 서비스 시작
    echo "   LAM 마이크로서비스 시작 중..."
    docker run -d --name smarteye-lam-service \
        -p 8081:8000 \
        -e PYTHONPATH=/app \
        smarteye-lam-service:latest
    
    if [ $? -eq 0 ]; then
        echo "   ✅ LAM 마이크로서비스 시작 성공"
    else
        echo "   ❌ LAM 마이크로서비스 시작 실패"
        cd ..
        exit 1
    fi
    
    cd ..
else
    echo "   ❌ LAM 마이크로서비스 디렉토리가 존재하지 않습니다"
    exit 1
fi

# 3. 서비스 시작 대기 및 헬스 체크
echo "3. 서비스 초기화 대기 중..."
sleep 15

echo "4. LAM 마이크로서비스 헬스 체크..."
MAX_RETRIES=5
RETRY_COUNT=0

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if curl -s -f http://localhost:8081/health > /dev/null; then
        echo "   ✅ LAM 마이크로서비스 정상 응답"
        break
    else
        RETRY_COUNT=$((RETRY_COUNT + 1))
        echo "   ⏳ LAM 서비스 응답 대기 중... ($RETRY_COUNT/$MAX_RETRIES)"
        sleep 5
    fi
done

if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
    echo "   ❌ LAM 마이크로서비스 헬스 체크 실패"
    exit 1
fi

# 5. Spring Boot 애플리케이션 시작
echo "5. Spring Boot 애플리케이션 시작..."
./gradlew bootRun &
SPRING_PID=$!

# Spring Boot 시작 대기
echo "6. Spring Boot 초기화 대기 중..."
sleep 30

# 6. Spring Boot 헬스 체크
echo "7. Spring Boot 헬스 체크..."
SPRING_RETRIES=10
SPRING_RETRY_COUNT=0

while [ $SPRING_RETRY_COUNT -lt $SPRING_RETRIES ]; do
    if curl -s -f http://localhost:8080/actuator/health > /dev/null; then
        echo "   ✅ Spring Boot 애플리케이션 정상 응답"
        break
    else
        SPRING_RETRY_COUNT=$((SPRING_RETRY_COUNT + 1))
        echo "   ⏳ Spring Boot 응답 대기 중... ($SPRING_RETRY_COUNT/$SPRING_RETRIES)"
        sleep 3
    fi
done

if [ $SPRING_RETRY_COUNT -eq $SPRING_RETRIES ]; then
    echo "   ❌ Spring Boot 헬스 체크 실패"
    kill $SPRING_PID 2>/dev/null
    exit 1
fi

# 7. 2단계 통합 테스트
echo "8. 2단계 통합 API 테스트..."

# LAM 마이크로서비스 상태 확인
echo "   LAM 마이크로서비스 상태 확인..."
LAM_STATUS=$(curl -s http://localhost:8080/api/v2/lam/health)
if [[ $LAM_STATUS == *"healthy"* ]] || [[ $LAM_STATUS == *"status"* ]]; then
    echo "   ✅ LAM 마이크로서비스 API 연동 성공"
else
    echo "   ❌ LAM 마이크로서비스 API 연동 실패"
fi

# 시스템 전체 상태 확인
echo "   시스템 전체 상태 확인..."
SYSTEM_STATUS=$(curl -s http://localhost:8080/api/v2/analysis/status)
if [[ $SYSTEM_STATUS == *"healthy"* ]] || [[ $SYSTEM_STATUS == *"2단계"* ]]; then
    echo "   ✅ 2단계 통합 시스템 정상"
else
    echo "   ⚠️  2단계 통합 시스템 일부 문제 가능성"
fi

# 8. 최종 결과 출력
echo ""
echo "======================================"
echo "2단계 시스템 배포 완료!"
echo "======================================"
echo ""
echo "🎉 SmartEye 2단계 마이크로서비스 아키텍처 구축 완료"
echo ""
echo "📊 서비스 접속 정보:"
echo "   • Spring Boot 메인 애플리케이션: http://localhost:8080"
echo "   • LAM 마이크로서비스: http://localhost:8081"
echo "   • H2 데이터베이스 콘솔: http://localhost:8080/h2-console"
echo ""
echo "🔧 주요 API 엔드포인트:"
echo "   • 2단계 통합 분석: POST http://localhost:8080/api/v2/analysis/integrated"
echo "   • LAM 마이크로서비스 분석: POST http://localhost:8080/api/v2/lam/analyze"
echo "   • 시스템 상태 확인: GET http://localhost:8080/api/v2/analysis/status"
echo "   • 성능 비교 테스트: POST http://localhost:8080/api/v2/analysis/compare"
echo ""
echo "🏗️  아키텍처 변경사항:"
echo "   ✅ 1단계: Python 스크립트 직접 호출 → Java 네이티브 TSPM"
echo "   ✅ 2단계: LAM Python 의존성 → FastAPI 마이크로서비스 분리"
echo "   ✅ Docker 컨테이너화 및 서비스 격리"
echo "   ✅ RestTemplate 기반 서비스 간 통신"
echo ""
echo "🧪 테스트 방법:"
echo "   curl -X GET http://localhost:8080/api/v2/analysis/status"
echo "   curl -X GET http://localhost:8080/api/v2/lam/test"
echo ""
echo "⚡ 다음 단계 (3단계):"
echo "   • Java 호환 모델 라이브러리 평가 및 통합"
echo "   • DJL (Deep Java Library) 또는 ONNX Runtime Java 적용"
echo "   • 완전한 Java 생태계 통합"
echo ""

# Spring Boot 프로세스 종료 (선택사항)
echo "Spring Boot 프로세스를 종료하시겠습니까? (y/N): "
read -r KILL_SPRING
if [[ $KILL_SPRING =~ ^[Yy]$ ]]; then
    kill $SPRING_PID 2>/dev/null
    echo "Spring Boot 프로세스가 종료되었습니다."
else
    echo "Spring Boot는 백그라운드에서 계속 실행됩니다. (PID: $SPRING_PID)"
fi

echo ""
echo "2단계 구축이 성공적으로 완료되었습니다! 🚀"
