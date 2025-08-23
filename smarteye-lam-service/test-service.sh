#!/bin/bash

# SmartEye LAM Service 테스트 스크립트

echo "🚀 SmartEye LAM Service 테스트 시작"

# 서비스 시작 대기
echo "⏳ 서비스 시작 대기 중..."
sleep 10

# Health Check
echo "🔍 Health Check 테스트"
response=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/health)

if [ "$response" = "200" ]; then
    echo "✅ Health Check 통과"
else
    echo "❌ Health Check 실패 (HTTP $response)"
    exit 1
fi

# Model Info Check
echo "🔍 모델 정보 확인"
model_info=$(curl -s http://localhost:8081/model/info)

if echo "$model_info" | grep -q "model_name"; then
    echo "✅ 모델 정보 조회 성공"
    echo "$model_info" | jq '.'
else
    echo "❌ 모델 정보 조회 실패"
    echo "$model_info"
    exit 1
fi

# 테스트 이미지 분석 (예시)
if [ -f "test_image.jpg" ]; then
    echo "🔍 이미지 분석 테스트"
    
    response=$(curl -s -X POST \
      http://localhost:8081/analyze \
      -H "Content-Type: multipart/form-data" \
      -F "file=@test_image.jpg" \
      -F "confidence_threshold=0.5")
    
    if echo "$response" | grep -q "layout_blocks"; then
        echo "✅ 이미지 분석 성공"
        echo "$response" | jq '.detected_objects_count'
    else
        echo "❌ 이미지 분석 실패"
        echo "$response"
    fi
else
    echo "⚠️ 테스트 이미지(test_image.jpg)가 없어 분석 테스트를 건너뜁니다."
fi

echo "🎉 테스트 완료"
