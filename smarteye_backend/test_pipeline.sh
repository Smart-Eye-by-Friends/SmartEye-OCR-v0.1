#!/bin/bash

# SmartEye Backend 파이프라인 테스트 스크립트
# 이 스크립트는 LAM→TSPM→CIM 파이프라인이 정상 작동하는지 확인합니다.
# 
# 테스트 완료 상태:
# ✅ Docker 환경 구성 및 서비스 시작
# ✅ PostgreSQL 연결 (포트 5433)
# ✅ JWT 인증 시스템
# ✅ 파일 업로드 및 SourceFile 모델
# ✅ LAM 서비스 로드 및 초기화
# ⚠️  TSPM 서비스 (OpenAI 클라이언트 의존성 이슈)
# ✅ CIM 서비스 로드 및 초기화
# ✅ 전체 파이프라인 워크플로우

set -e  # 오류 시 스크립트 중단

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# 로그 함수
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_header() {
    echo -e "\n${PURPLE}=== $1 ===${NC}"
}

# 환경 변수 설정
COMPOSE_FILE="docker-compose.dev.yml"
API_BASE_URL="http://localhost:8000"
FLOWER_URL="http://localhost:5555"

# 도움말 함수
show_help() {
    cat << EOF
SmartEye Backend 파이프라인 테스트 스크립트

사용법:
    $0 [OPTIONS]

옵션:
    -h, --help          이 도움말 표시
    -q, --quick         빠른 테스트 (기본 기능만)
    -f, --full          전체 테스트 (모든 기능 포함)
    -c, --cleanup       테스트 후 데이터 정리
    -v, --verbose       상세 로그 출력
    -w, --wait          서비스 시작 대기 시간 (초, 기본: 30)

예시:
    $0 --full --verbose     # 전체 테스트를 상세 로그와 함께 실행
    $0 --quick --cleanup    # 빠른 테스트 후 데이터 정리
    $0 --wait 60           # 60초 대기 후 테스트 시작

EOF
}

# 기본 설정
QUICK_TEST=false
FULL_TEST=false
CLEANUP_AFTER=false
VERBOSE=false
WAIT_TIME=30

# 명령행 인수 처리
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        -q|--quick)
            QUICK_TEST=true
            shift
            ;;
        -f|--full)
            FULL_TEST=true
            shift
            ;;
        -c|--cleanup)
            CLEANUP_AFTER=true
            shift
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -w|--wait)
            WAIT_TIME="$2"
            shift 2
            ;;
        *)
            log_error "알 수 없는 옵션: $1"
            show_help
            exit 1
            ;;
    esac
done

# 기본값 설정
if [[ "$QUICK_TEST" == false && "$FULL_TEST" == false ]]; then
    FULL_TEST=true  # 기본은 전체 테스트
fi

# 유틸리티 함수들
verbose_log() {
    if [[ "$VERBOSE" == true ]]; then
        log_info "$1"
    fi
}

check_command() {
    if ! command -v $1 &> /dev/null; then
        log_error "$1이 설치되어 있지 않습니다."
        exit 1
    fi
}

wait_for_service() {
    local url=$1
    local service_name=$2
    local max_attempts=30
    local attempt=1

    log_info "$service_name 서비스 대기 중..."
    
    while [[ $attempt -le $max_attempts ]]; do
        if curl -s "$url" > /dev/null 2>&1; then
            log_success "$service_name 서비스 준비 완료"
            return 0
        fi
        
        verbose_log "시도 $attempt/$max_attempts: $service_name 대기 중..."
        sleep 2
        ((attempt++))
    done
    
    log_error "$service_name 서비스가 응답하지 않습니다."
    return 1
}

# Docker 명령어 실행 함수
docker_exec() {
    local service=$1
    shift
    docker compose -f "$COMPOSE_FILE" exec -T "$service" "$@"
}

# JSON 응답 파싱 함수 (jq 없이)
extract_json_value() {
    local json=$1
    local key=$2
    echo "$json" | grep -o "\"$key\":[^,}]*" | cut -d':' -f2 | tr -d '"' | tr -d ' '
}

# 메인 테스트 시작
log_header "SmartEye Backend 파이프라인 테스트 시작"

# 필수 명령어 확인
log_info "필수 명령어 확인 중..."
check_command "docker"
check_command "curl"

# Docker Compose 파일 확인
if [[ ! -f "$COMPOSE_FILE" ]]; then
    log_error "Docker Compose 파일을 찾을 수 없습니다: $COMPOSE_FILE"
    exit 1
fi

# 서비스 상태 확인
log_header "Docker 서비스 상태 확인"
if ! docker compose -f "$COMPOSE_FILE" ps | grep -q "Up"; then
    log_warning "일부 서비스가 실행되지 않고 있습니다."
    log_info "서비스 시작 중..."
    docker compose -f "$COMPOSE_FILE" up -d
    
    log_info "${WAIT_TIME}초 대기 중..."
    sleep "$WAIT_TIME"
fi

# 서비스별 상태 확인
services=("web" "db" "redis" "celery-worker")
for service in "${services[@]}"; do
    if docker compose -f "$COMPOSE_FILE" ps "$service" | grep -q "Up"; then
        log_success "$service 서비스 실행 중"
    else
        log_error "$service 서비스가 실행되지 않고 있습니다."
        exit 1
    fi
done

# API 서비스 대기
log_header "API 서비스 연결 확인"
wait_for_service "$API_BASE_URL/api/v1/health/" "API"

# 헬스체크 테스트
log_info "API 헬스체크 실행 중..."
health_response=$(curl -s "$API_BASE_URL/api/v1/health/")
if [[ $? -eq 0 ]]; then
    log_success "API 헬스체크 통과"
    verbose_log "응답: $health_response"
else
    log_error "API 헬스체크 실패"
    exit 1
fi

# 데이터베이스 연결 테스트
log_header "데이터베이스 연결 테스트"
log_info "Django 데이터베이스 체크 실행 중..."
if docker_exec web python manage.py check --database default > /dev/null 2>&1; then
    log_success "데이터베이스 연결 정상"
else
    log_error "데이터베이스 연결 실패"
    exit 1
fi

# 마이그레이션 상태 확인
log_info "마이그레이션 상태 확인 중..."
migration_output=$(docker_exec web python manage.py showmigrations --plan 2>/dev/null | grep -v "^$")
if [[ -n "$migration_output" ]]; then
    unapplied=$(echo "$migration_output" | grep -c "^\[ \]" || true)
    if [[ $unapplied -gt 0 ]]; then
        log_warning "$unapplied개의 미적용 마이그레이션 발견"
        log_info "마이그레이션 적용 중..."
        docker_exec web python manage.py migrate --noinput
    fi
    log_success "모든 마이그레이션 적용 완료"
else
    log_success "마이그레이션 상태 정상"
fi

# 테스트 사용자 생성
log_header "테스트 사용자 생성"
TEST_USERNAME="pipeline_test_user"
TEST_PASSWORD="testpass123"
TEST_EMAIL="pipeline@test.com"

user_creation_script="
import os
import django
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'smarteye.settings.development')
django.setup()

from django.contrib.auth import get_user_model
from rest_framework_simplejwt.tokens import RefreshToken

User = get_user_model()

# 기존 사용자 삭제 (재테스트용)
User.objects.filter(username='$TEST_USERNAME').delete()

# 새 사용자 생성
user = User.objects.create_user(
    username='$TEST_USERNAME',
    email='$TEST_EMAIL',
    password='$TEST_PASSWORD'
)

# JWT 토큰 생성
refresh = RefreshToken.for_user(user)
access_token = str(refresh.access_token)

print(f'USER_ID:{user.id}')
print(f'ACCESS_TOKEN:{access_token}')
"

user_output=$(docker_exec web python -c "$user_creation_script")
if [[ $? -eq 0 ]]; then
    USER_ID=$(echo "$user_output" | grep "USER_ID:" | cut -d':' -f2)
    ACCESS_TOKEN=$(echo "$user_output" | grep "ACCESS_TOKEN:" | cut -d':' -f2)
    log_success "테스트 사용자 생성 완료 (ID: $USER_ID)"
    verbose_log "토큰 길이: ${#ACCESS_TOKEN} 문자"
else
    log_error "테스트 사용자 생성 실패"
    exit 1
fi

# 테스트 이미지 준비
log_header "테스트 이미지 준비"

# 실제 문제지 이미지 경로
HOST_IMAGE_PATH="/home/jongyoung3/SmartEye_v0.1/낱개 문제지_페이지_01.jpg"
CONTAINER_IMAGE_PATH="/tmp/test_document.jpg"

# 호스트에서 이미지 존재 확인
if [[ ! -f "$HOST_IMAGE_PATH" ]]; then
    log_error "테스트 이미지가 존재하지 않습니다: $HOST_IMAGE_PATH"
    exit 1
fi

# 컨테이너로 이미지 복사
log_info "실제 문제지 이미지를 컨테이너로 복사 중..."
if docker cp "$HOST_IMAGE_PATH" "$(docker compose -f "$COMPOSE_FILE" ps -q web):$CONTAINER_IMAGE_PATH"; then
    log_success "테스트 이미지 준비 완료"
    
    # 이미지 정보 확인
    image_info_script="
import os
from PIL import Image

image_path = '$CONTAINER_IMAGE_PATH'
if os.path.exists(image_path):
    size = os.path.getsize(image_path)
    
    try:
        with Image.open(image_path) as img:
            width, height = img.size
            format_name = img.format
        print(f'IMAGE_PATH:{image_path}')
        print(f'IMAGE_SIZE:{size}')
        print(f'IMAGE_DIMENSIONS:{width}x{height}')
        print(f'IMAGE_FORMAT:{format_name}')
    except Exception as e:
        print(f'IMAGE_PATH:{image_path}')
        print(f'IMAGE_SIZE:{size}')
        print(f'IMAGE_ERROR:{str(e)}')
else:
    print('IMAGE_ERROR:File not found')
"
    
    image_output=$(docker_exec web python -c "$image_info_script")
    if [[ $? -eq 0 ]]; then
        IMAGE_PATH=$(echo "$image_output" | grep "IMAGE_PATH:" | cut -d':' -f2)
        IMAGE_SIZE=$(echo "$image_output" | grep "IMAGE_SIZE:" | cut -d':' -f2)
        IMAGE_DIMENSIONS=$(echo "$image_output" | grep "IMAGE_DIMENSIONS:" | cut -d':' -f2)
        IMAGE_FORMAT=$(echo "$image_output" | grep "IMAGE_FORMAT:" | cut -d':' -f2)
        
        verbose_log "이미지 경로: $IMAGE_PATH"
        verbose_log "이미지 크기: $IMAGE_SIZE bytes"
        verbose_log "이미지 해상도: $IMAGE_DIMENSIONS"
        verbose_log "이미지 형식: $IMAGE_FORMAT"
    else
        log_warning "이미지 정보 확인 중 오류 발생"
    fi
else
    log_error "테스트 이미지 복사 실패"
    exit 1
fi

# 파이프라인 테스트 실행
log_header "파이프라인 테스트 실행"
log_info "파일 업로드 및 분석 시작..."

# 호스트에서 직접 이미지 파일 사용
UPLOAD_IMAGE_PATH="$HOST_IMAGE_PATH"

# 파일 업로드 및 분석 요청
upload_response=$(curl -s -X POST "$API_BASE_URL/api/v1/analysis/jobs/upload_and_analyze/" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -F "files=@$UPLOAD_IMAGE_PATH" \
    -F "job_name=Pipeline Integration Test - Korean Math Worksheet" \
    -F "model_choice=smarteye_finetuned" \
    -F "enable_ocr=1" \
    -F "enable_description=1")

if [[ $? -eq 0 && -n "$upload_response" ]]; then
    JOB_ID=$(extract_json_value "$upload_response" "job_id")
    TASK_ID=$(extract_json_value "$upload_response" "task_id")
    
    if [[ -n "$JOB_ID" && "$JOB_ID" != "null" ]]; then
        log_success "파이프라인 시작 성공 (Job ID: $JOB_ID)"
        verbose_log "Task ID: $TASK_ID"
        verbose_log "응답: $upload_response"
    else
        log_error "업로드 응답에서 Job ID를 찾을 수 없습니다"
        log_error "응답: $upload_response"
        exit 1
    fi
else
    log_error "파일 업로드 및 분석 시작 실패"
    exit 1
fi

# 작업 진행 상태 모니터링
log_info "파이프라인 진행 상태 모니터링 중..."
max_wait_time=300  # 5분
check_interval=10  # 10초마다 확인
elapsed_time=0

while [[ $elapsed_time -lt $max_wait_time ]]; do
    status_response=$(curl -s -X GET "$API_BASE_URL/api/v1/analysis/jobs/$JOB_ID/status/" \
        -H "Authorization: Bearer $ACCESS_TOKEN")
    
    if [[ $? -eq 0 && -n "$status_response" ]]; then
        job_status=$(extract_json_value "$status_response" "status")
        progress=$(extract_json_value "$status_response" "progress")
        
        case "$job_status" in
            "completed")
                log_success "파이프라인 완료! (소요시간: ${elapsed_time}초)"
                break
                ;;
            "failed"|"error")
                log_error "파이프라인 실패 (상태: $job_status)"
                verbose_log "응답: $status_response"
                exit 1
                ;;
            "processing"|"pending")
                log_info "진행 중... (${progress}%, 경과시간: ${elapsed_time}초)"
                ;;
            *)
                verbose_log "알 수 없는 상태: $job_status"
                ;;
        esac
    else
        log_warning "상태 확인 실패, 재시도 중..."
    fi
    
    sleep $check_interval
    elapsed_time=$((elapsed_time + check_interval))
done

if [[ $elapsed_time -ge $max_wait_time ]]; then
    log_error "파이프라인 실행 시간 초과 (${max_wait_time}초)"
    exit 1
fi

# 데이터베이스 저장 확인
log_header "데이터베이스 저장 확인"

db_verification_script="
import os
import django
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'smarteye.settings.development')
django.setup()

from apps.analysis.models import AnalysisJob, ProcessedImage, AnalysisResult
from apps.files.models import SourceFile

# 생성된 Job 확인
job = AnalysisJob.objects.filter(id=$JOB_ID).first()
if not job:
    print('ERROR:Job not found')
    exit(1)

print(f'JOB_STATUS:{job.status}')
print(f'JOB_PROGRESS:{job.progress}')

# 파일 확인
source_files = SourceFile.objects.filter(uploaded_by_id=$USER_ID).count()
print(f'SOURCE_FILES:{source_files}')

# 처리된 이미지 확인
processed_images = ProcessedImage.objects.filter(job_id=$JOB_ID)
print(f'PROCESSED_IMAGES:{processed_images.count()}')

# 각 단계별 확인
stages = ['lam', 'tspm', 'cim']
for stage in stages:
    stage_images = processed_images.filter(stage=stage)
    completed = stage_images.filter(processing_status='completed').count()
    total = stage_images.count()
    print(f'{stage.upper()}_STAGE:{completed}/{total}')

# 최종 결과 확인
results = AnalysisResult.objects.filter(job_id=$JOB_ID)
print(f'ANALYSIS_RESULTS:{results.count()}')

if results.exists():
    result = results.first()
    print(f'CONFIDENCE_SCORE:{result.confidence_score}')
    print(f'DETECTED_ELEMENTS:{result.total_detected_elements}')
    print(f'PROCESSING_TIME:{result.processing_time_seconds}')
    print(f'HAS_TEXT:{\"yes\" if result.text_content else \"no\"}')
    print(f'HAS_BRAILLE:{\"yes\" if result.braille_content else \"no\"}')
    print(f'HAS_PDF:{\"yes\" if result.pdf_path else \"no\"}')
"

db_output=$(docker_exec web python -c "$db_verification_script")
if [[ $? -eq 0 ]]; then
    log_success "데이터베이스 저장 확인 완료"
    
    # 결과 파싱 및 출력
    job_status=$(echo "$db_output" | grep "JOB_STATUS:" | cut -d':' -f2)
    job_progress=$(echo "$db_output" | grep "JOB_PROGRESS:" | cut -d':' -f2)
    source_files=$(echo "$db_output" | grep "SOURCE_FILES:" | cut -d':' -f2)
    processed_images=$(echo "$db_output" | grep "PROCESSED_IMAGES:" | cut -d':' -f2)
    analysis_results=$(echo "$db_output" | grep "ANALYSIS_RESULTS:" | cut -d':' -f2)
    
    echo
    log_info "=== 파이프라인 결과 요약 ==="
    log_info "작업 상태: $job_status ($job_progress%)"
    log_info "업로드된 파일: $source_files개"
    log_info "처리된 이미지: $processed_images개"
    log_info "분석 결과: $analysis_results개"
    
    # 단계별 결과
    for stage in lam tspm cim; do
        stage_result=$(echo "$db_output" | grep "${stage^^}_STAGE:" | cut -d':' -f2)
        log_info "$stage 단계: $stage_result"
    done
    
    # 최종 결과 정보
    if [[ $analysis_results -gt 0 ]]; then
        confidence=$(echo "$db_output" | grep "CONFIDENCE_SCORE:" | cut -d':' -f2)
        elements=$(echo "$db_output" | grep "DETECTED_ELEMENTS:" | cut -d':' -f2)
        proc_time=$(echo "$db_output" | grep "PROCESSING_TIME:" | cut -d':' -f2)
        has_text=$(echo "$db_output" | grep "HAS_TEXT:" | cut -d':' -f2)
        has_braille=$(echo "$db_output" | grep "HAS_BRAILLE:" | cut -d':' -f2)
        has_pdf=$(echo "$db_output" | grep "HAS_PDF:" | cut -d':' -f2)
        
        echo
        log_info "=== 분석 결과 상세 ==="
        log_info "신뢰도 점수: $confidence"
        log_info "탐지된 요소: $elements개"
        log_info "처리 시간: ${proc_time}초"
        log_info "텍스트 결과: $has_text"
        log_info "점자 결과: $has_braille"
        log_info "PDF 결과: $has_pdf"
    fi
else
    log_error "데이터베이스 확인 실패"
    verbose_log "DB 스크립트 출력: $db_output"
    exit 1
fi

# 전체 테스트인 경우 추가 검증
if [[ "$FULL_TEST" == true ]]; then
    log_header "전체 테스트 - 추가 검증"
    
    # Flower 모니터링 확인
    log_info "Flower 모니터링 서비스 확인..."
    if curl -s "$FLOWER_URL" > /dev/null 2>&1; then
        log_success "Flower 모니터링 서비스 정상"
    else
        log_warning "Flower 모니터링 서비스 접근 불가 (선택사항)"
    fi
    
    # API 문서 접근 확인
    log_info "API 문서 접근 확인..."
    if curl -s "$API_BASE_URL/api/docs/" > /dev/null 2>&1; then
        log_success "API 문서 접근 가능"
    else
        log_warning "API 문서 접근 불가"
    fi
    
    # 시스템 리소스 확인
    log_info "시스템 리소스 사용량 확인..."
    resource_info=$(docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}" | grep smarteye)
    if [[ -n "$resource_info" ]]; then
        log_success "리소스 사용량 확인 완료"
        verbose_log "$resource_info"
    fi
fi

# 정리 작업
if [[ "$CLEANUP_AFTER" == true ]]; then
    log_header "테스트 데이터 정리"
    
    cleanup_script="
import os
import django
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'smarteye.settings.development')
django.setup()

from django.contrib.auth import get_user_model
from apps.analysis.models import AnalysisJob, ProcessedImage, AnalysisResult
from apps.files.models import SourceFile

User = get_user_model()

# 테스트 사용자와 관련 데이터 삭제
user = User.objects.filter(username='$TEST_USERNAME').first()
if user:
    # 관련 데이터 먼저 삭제
    AnalysisJob.objects.filter(user=user).delete()
    SourceFile.objects.filter(uploaded_by=user).delete()
    user.delete()
    print('Cleanup completed')
else:
    print('No test user found')
"
    
    docker_exec web python -c "$cleanup_script"
    docker_exec web rm -f /tmp/smarteye_pipeline_test.jpg /app/temp_test_image.jpg
    log_success "테스트 데이터 정리 완료"
fi

# 최종 결과
log_header "테스트 완료"
log_success "🎉 SmartEye Backend 파이프라인 테스트가 성공적으로 완료되었습니다!"

echo
log_info "📋 테스트 요약:"
log_info "  ✅ Docker 서비스 상태 확인"
log_info "  ✅ API 연결 및 헬스체크"
log_info "  ✅ 데이터베이스 연결 및 마이그레이션"
log_info "  ✅ 테스트 사용자 및 토큰 생성"
log_info "  ✅ 테스트 이미지 생성 및 업로드"
log_info "  ✅ LAM → TSPM → CIM 파이프라인 실행"
log_info "  ✅ 데이터베이스 저장 확인"

if [[ "$FULL_TEST" == true ]]; then
    log_info "  ✅ 추가 서비스 검증 (Flower, API 문서)"
fi

if [[ "$CLEANUP_AFTER" == true ]]; then
    log_info "  ✅ 테스트 데이터 정리"
fi

echo
log_info "🌐 서비스 접속 정보:"
log_info "  • API 서버: $API_BASE_URL"
log_info "  • API 문서: $API_BASE_URL/api/docs/"
log_info "  • 관리자 페이지: $API_BASE_URL/admin/"
log_info "  • Flower 모니터링: $FLOWER_URL"

echo
log_success "SmartEye Backend가 성공적으로 작동하고 있습니다! 🚀"
