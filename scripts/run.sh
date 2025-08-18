#!/bin/bash

# SmartEye Spring Boot Backend 빌드 및 실행 스크립트

echo "=== SmartEye Backend Build & Run Script ==="

# 환경변수 설정
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}
export SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-dev}

# 함수 정의
build_project() {
    echo "Building project..."
    ./gradlew clean build --exclude-task test
}

run_dev() {
    echo "Running in development mode..."
    ./gradlew bootRun --args='--spring.profiles.active=dev'
}

run_prod() {
    echo "Running in production mode..."
    ./gradlew bootRun --args='--spring.profiles.active=prod'
}

package_jar() {
    echo "Creating JAR package..."
    ./gradlew bootJar
    echo "JAR created at: build/libs/"
}

show_help() {
    echo "Usage: $0 [OPTION]"
    echo "Options:"
    echo "  build     Build the project"
    echo "  dev       Run in development mode"
    echo "  prod      Run in production mode"
    echo "  package   Create JAR package"
    echo "  help      Show this help message"
}

# 메인 로직
case "${1:-dev}" in
    build)
        build_project
        ;;
    dev)
        build_project && run_dev
        ;;
    prod)
        build_project && run_prod
        ;;
    package)
        build_project && package_jar
        ;;
    help)
        show_help
        ;;
    *)
        echo "Unknown option: $1"
        show_help
        exit 1
        ;;
esac
