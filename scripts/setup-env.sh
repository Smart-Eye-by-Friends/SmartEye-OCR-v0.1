#!/bin/bash

# SmartEye v0.1 - 통합 환경변수 설정 스크립트
# 사용법: source scripts/setup-env.sh [dev|prod|docker]

echo "🔧 SmartEye v0.1 환경변수 설정..."

# 파라미터 확인
ENVIRONMENT=${1:-dev}

case $ENVIRONMENT in
    dev)
        echo "📊 개발 환경 (H2 In-Memory Database) 설정 중..."
        export SPRING_PROFILES_ACTIVE=dev
        export SPRING_DATASOURCE_URL=jdbc:h2:mem:smarteye;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
        export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver
        export SPRING_DATASOURCE_USERNAME=sa
        export SPRING_DATASOURCE_PASSWORD=
        export SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop
        export SPRING_JPA_SHOW_SQL=true
        export LOGGING_LEVEL_COM_SMARTEYE=DEBUG
        export OPENAI_API_KEY=${OPENAI_API_KEY:-dummy-api-key}
        export LAM_SERVICE_URL=http://localhost:8081
        echo "✅ 개발 환경 설정 완료!"
        echo "   - Database: H2 In-Memory"
        echo "   - Profile: dev"
        echo "   - Debug Logging: Enabled"
        ;;
    
    prod)
        echo "🚀 프로덕션 환경 (PostgreSQL) 설정 중..."
        export SPRING_PROFILES_ACTIVE=prod
        export SPRING_DATASOURCE_URL=${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:5432/smarteye}
        export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
        export SPRING_DATASOURCE_USERNAME=${SPRING_DATASOURCE_USERNAME:-smarteye}
        export SPRING_DATASOURCE_PASSWORD=${SPRING_DATASOURCE_PASSWORD:-smarteye123}
        export SPRING_JPA_HIBERNATE_DDL_AUTO=validate
        export SPRING_JPA_SHOW_SQL=false
        export LOGGING_LEVEL_COM_SMARTEYE=INFO
        export LAM_SERVICE_URL=http://localhost:8081
        
        if [ -z "$OPENAI_API_KEY" ]; then
            echo "⚠️  경고: OPENAI_API_KEY 환경변수가 설정되지 않았습니다."
            echo "    Vision API 기능이 제한될 수 있습니다."
        fi
        
        echo "✅ 프로덕션 환경 설정 완료!"
        echo "   - Database: PostgreSQL"
        echo "   - Profile: prod" 
        echo "   - Production Logging: Enabled"
        ;;
    
    docker)
        echo "🐳 Docker 환경 설정 중..."
        export SPRING_PROFILES_ACTIVE=docker
        export LAM_SERVICE_URL=http://smarteye-lam:8081
        export DB_HOST=postgres
        export DB_PORT=5432
        export DB_NAME=smarteye
        export DB_USERNAME=smarteye
        export DB_PASSWORD=password
        export LOGGING_LEVEL_COM_SMARTEYE=INFO
        
        echo "✅ Docker 환경 설정 완료!"
        echo "   - Service Discovery: Docker Compose"
        echo "   - LAM Service: smarteye-lam:8081"
        echo "   - Database: postgres:5432"
        ;;
    
    *)
        echo "❌ 알 수 없는 환경: $ENVIRONMENT"
        echo "사용 가능한 환경: dev, prod, docker"
        return 1
        ;;
esac

# 공통 환경변수
export SMARTEYE_UPLOAD_TEMP_DIR=./temp
export SMARTEYE_UPLOAD_MAX_FILE_SIZE=50MB
export SMARTEYE_TSPM_USE_JAVA_NATIVE=true

echo ""
echo "📊 현재 설정:"
echo "   Environment: $ENVIRONMENT"
echo "   Profile: $SPRING_PROFILES_ACTIVE"
echo "   Database: ${SPRING_DATASOURCE_URL:-Container Managed}"
echo "   LAM Service: $LAM_SERVICE_URL"
echo ""
echo "🚀 실행 명령어:"
echo "   ./scripts/run.sh $ENVIRONMENT"
echo "   또는"
echo "   ./gradlew bootRun"
