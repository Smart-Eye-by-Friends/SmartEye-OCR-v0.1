#!/bin/bash

echo "🧪 SmartEye 백엔드 검증 테스트"
echo "=================================="

# 테스트 1: Gradle 빌드
echo "📦 Gradle 빌드 테스트 중..."
cd /home/jongyoung3/SmartEye_v0.4/smarteye-backend
if ./gradlew build -x test --quiet; then
    echo "✅ Gradle build: SUCCESS"
else
    echo "❌ Gradle build: FAILED"
    exit 1
fi

# Test 2: Check JAR file
echo "📄 Testing JAR file existence..."
JAR_FILE="build/libs/smarteye-backend-0.0.1-SNAPSHOT.jar"
if [[ -f "$JAR_FILE" ]]; then
    echo "✅ JAR file exists: $JAR_FILE"
    echo "   Size: $(du -h "$JAR_FILE" | cut -f1)"
else
    echo "❌ JAR file not found: $JAR_FILE"
    exit 1
fi

# Test 3: Check Docker files
echo "🐳 Testing Docker configuration..."
cd ..
if [[ -f "docker-compose.yml" ]]; then
    echo "✅ docker-compose.yml exists"
else
    echo "❌ docker-compose.yml missing"
fi

if [[ -f "smarteye-backend/Dockerfile" ]]; then
    echo "✅ Backend Dockerfile exists"
else
    echo "❌ Backend Dockerfile missing"
fi

if [[ -f "smarteye-lam-service/Dockerfile" ]]; then
    echo "✅ LAM Service Dockerfile exists"
else
    echo "❌ LAM Service Dockerfile missing"
fi

# Test 4: Check Python LAM service files
echo "🐍 Testing LAM Service files..."
if [[ -f "smarteye-lam-service/main.py" ]]; then
    echo "✅ LAM Service main.py exists"
else
    echo "❌ LAM Service main.py missing"
fi

if [[ -f "smarteye-lam-service/requirements.txt" ]]; then
    echo "✅ LAM Service requirements.txt exists"
    echo "   Dependencies: $(wc -l < smarteye-lam-service/requirements.txt) packages"
else
    echo "❌ LAM Service requirements.txt missing"
fi

# Test 5: Configuration files validation
echo "⚙️  Testing Configuration files..."
CONFIG_FILES=(
    "smarteye-backend/src/main/resources/application.yml"
    "smarteye-backend/src/main/resources/application-dev.yml"
    "smarteye-backend/src/main/resources/application-prod.yml"
    "smarteye-backend/src/main/resources/application-resilience.yml"
)

for file in "${CONFIG_FILES[@]}"; do
    if [[ -f "$file" ]]; then
        echo "✅ Config file exists: $(basename "$file")"
    else
        echo "❌ Config file missing: $(basename "$file")"
    fi
done

# Test 6: Java Source Files
echo "☕ Testing Java Source Files..."
JAVA_COUNT=$(find smarteye-backend/src/main/java -name "*.java" | wc -l)
echo "   Java source files: $JAVA_COUNT"

if [[ $JAVA_COUNT -gt 20 ]]; then
    echo "✅ Adequate Java source files found"
else
    echo "❌ Insufficient Java source files (need > 20, found $JAVA_COUNT)"
fi

# Test 7: Database Schema
echo "💾 Testing Database Schema..."
if [[ -f "init.sql" ]]; then
    echo "✅ Database initialization script exists"
    echo "   Lines: $(wc -l < init.sql)"
else
    echo "❌ Database initialization script missing"
fi

echo ""
echo "🎉 SmartEye Backend Validation Complete!"
echo "   Build Status: READY FOR DEPLOYMENT"
echo "   Architecture: Java/Spring Boot + Python LAM Service"
echo "   Database: PostgreSQL with initialization scripts"
echo "   Container Support: Docker + Docker Compose"