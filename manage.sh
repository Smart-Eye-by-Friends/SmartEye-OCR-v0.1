#!/bin/bash

# ==============================================================================
# SmartEye v0.4 - Unified Management Script
#
# A single script to manage all aspects of the SmartEye system, including
# starting, stopping, checking status, rebuilding, and development.
# ==============================================================================

set -e

# --- Configuration ---
# Directories
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="${ROOT_DIR}/Backend"
FRONTEND_DIR="${ROOT_DIR}/frontend"

# Docker
COMPOSE_FILE="${BACKEND_DIR}/docker-compose.yml"
# --- End Configuration ---


# --- Color Definitions ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color
# --- End Color Definitions ---


# --- Logging Functions ---
log_info() { echo -e "${BLUE}ℹ️  $1${NC}"; }
log_success() { echo -e "${GREEN}✅ $1${NC}"; }
log_warning() { echo -e "${YELLOW}⚠️  $1${NC}"; }
log_error() { echo -e "${RED}❌ $1${NC}"; }
# --- End Logging Functions ---


# --- Command Functions ---

# Show help menu
show_help() {
    echo -e "${BLUE}SmartEye v0.4 - Unified Management Script${NC}"
    echo "--------------------------------------------------"
    echo -e "${YELLOW}Usage: ./manage.sh [command] [options]${NC}"
    echo ""
    echo "Commands:"
    echo -e "  ${GREEN}start${NC}         - Start all services."
    echo -e "  ${GREEN}stop${NC}          - Stop all services."
    echo -e "  ${GREEN}status${NC}        - Check the status of all services."
    echo -e "  ${GREEN}restart [svc]${NC} - Restart a specific service (or all)."
    echo -e "  ${GREEN}rebuild [svc]${NC} - Rebuild a specific service (or all)."
    echo -e "  ${GREEN}validate${NC}      - Run system validation tests."
    echo -e "  ${GREEN}logs [svc]${NC}    - View logs for a specific service."
    echo -e "  ${GREEN}dev${NC}           - Start the development environment."
    echo -e "  ${GREEN}help${NC}          - Show this help menu."
}

# Start all services
start_system() {
    log_info "Starting SmartEye v0.4 services..."
    cd "${BACKEND_DIR}"

    if ! command -v docker &> /dev/null || ! command -v docker-compose &> /dev/null; then
        log_error "Docker and Docker Compose are required."
        exit 1
    fi

    log_info "Cleaning up old containers..."
    docker-compose -f "${COMPOSE_FILE}" down --remove-orphans || true

    log_info "Building images (this may take a while)..."
    if ! docker-compose -f "${COMPOSE_FILE}" build --no-cache; then
        log_error "Image build failed."
        exit 1
    fi

    log_info "Starting services..."
    docker-compose -f "${COMPOSE_FILE}" up -d

    log_info "Waiting for services to initialize..."
    sleep 20

    # Health checks
    log_info "Running health checks..."
    check_status
    log_success "SmartEye services started successfully!"
    echo "--------------------------------------------------"
    echo -e "${GREEN}Web App: http://localhost${NC}"
    echo -e "${GREEN}Backend API: http://localhost:8080/api/health${NC}"
    echo -e "${GREEN}Swagger UI: http://localhost:8080/swagger-ui/index.html${NC}"
}

# Stop all services
stop_system() {
    log_info "Stopping SmartEye v0.4 services..."
    cd "${BACKEND_DIR}"
    docker-compose -f "${COMPOSE_FILE}" down --remove-orphans

    read -p "Do you want to remove data volumes? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        log_warning "Removing data volumes..."
        docker-compose -f "${COMPOSE_FILE}" down -v
    fi
    log_success "System stopped."
}

# Check service status
check_status() {
    log_info "Checking SmartEye v0.4 service status..."
    cd "${BACKEND_DIR}"
    docker-compose -f "${COMPOSE_FILE}" ps

    echo
    log_info "Performing health checks..."
    # Backend
    echo -n "Backend (8080): "
    if curl -s http://localhost:8080/api/health > /dev/null 2>&1; then
        log_success "OK"
    else
        log_error "FAIL"
    fi
    # LAM Service
    echo -n "LAM Service (8001): "
    if curl -s http://localhost:8001/health > /dev/null 2>&1; then
        log_success "OK"
    else
        log_error "FAIL"
    fi
    # PostgreSQL
    echo -n "PostgreSQL (5433): "
    if docker exec smarteye-postgres pg_isready -U smarteye > /dev/null 2>&1; then
        log_success "OK"
    else
        log_error "FAIL"
    fi
}

# Restart a service
restart_service() {
    SERVICE=$1
    log_info "Restarting service: ${SERVICE:-all}"
    cd "${BACKEND_DIR}"
    docker-compose -f "${COMPOSE_FILE}" restart "${SERVICE}"
    log_success "Service ${SERVICE:-all} restarted."
}

# Rebuild a service
rebuild_service() {
    SERVICE=$1
    if [ -z "${SERVICE}" ]; then
        log_error "Usage: ./manage.sh rebuild [service_name|all]"
        echo "Available services: frontend, smarteye-backend, lam-service, nginx"
        exit 1
    fi

    log_info "Rebuilding service: ${SERVICE}"
    cd "${BACKEND_DIR}"
    if [ "${SERVICE}" == "all" ]; then
        docker-compose -f "${COMPOSE_FILE}" up -d --build --force-recreate
    else
        docker-compose -f "${COMPOSE_FILE}" build --no-cache "${SERVICE}"
        docker-compose -f "${COMPOSE_FILE}" up -d --force-recreate "${SERVICE}"
    fi
    log_success "Service ${SERVICE} rebuilt and restarted."
}

# Run validation tests
validate_system() {
    log_info "Running system validation tests..."
    # This is a simplified version. The original script had more checks.
    # For now, we'll just check the main API endpoint.
    if [[ -f "${ROOT_DIR}/test_homework_image.jpg" ]]; then
        log_info "Performing API analysis test..."
        API_RESPONSE=$(curl -s -X POST \
            -F "image=@${ROOT_DIR}/test_homework_image.jpg" \
            -F "modelChoice=SmartEyeSsen" \
            "http://localhost:8080/api/document/analyze")
        if echo "${API_RESPONSE}" | jq -e '.success == true' > /dev/null 2>&1; then
            log_success "API analysis test PASSED."
        else
            log_error "API analysis test FAILED."
            echo "Response: ${API_RESPONSE}"
        fi
    else
        log_warning "Test image not found, skipping API test."
    fi
}

# View logs
view_logs() {
    SERVICE=$1
    log_info "Showing logs for service: ${SERVICE:-all}"
    cd "${BACKEND_DIR}"
    docker-compose -f "${COMPOSE_FILE}" logs -f "${SERVICE}"
}

# Start development environment
start_dev() {
    log_info "Starting development environment..."
    echo "1) Frontend local dev (npm start) + Backend Docker"
    echo "2) Full Docker dev (rebuild all)"
    read -p "Select mode (1 or 2): " -n 1 -r
    echo

    cd "${BACKEND_DIR}"
    if [[ $REPLY == "1" ]]; then
        log_info "Starting backend services on Docker..."
        docker-compose -f "${COMPOSE_FILE}" up -d postgres lam-service smarteye-backend
        log_success "Backend services are up."
        echo "--------------------------------------------------"
        log_info "Now, in a new terminal, run:"
        echo -e "${GREEN}cd ${FRONTEND_DIR} && npm install && npm start${NC}"
    elif [[ $REPLY == "2" ]]; then
        log_info "Starting full Docker environment with rebuild..."
        docker-compose -f "${COMPOSE_FILE}" up -d --build
        log_success "Full system is up."
        check_status
    else
        log_error "Invalid selection."
    fi
}

# --- Main Execution Logic ---
COMMAND=$1
shift || true

case $COMMAND in
    start)
        start_system
        ;;
    stop)
        stop_system
        ;;
    status)
        check_status
        ;;
    restart)
        restart_service "$1"
        ;;
    rebuild)
        rebuild_service "$1"
        ;;
    validate)
        validate_system
        ;;
    logs)
        view_logs "$1"
        ;;
    dev)
        start_dev
        ;;
    help|--help|-h|*)
        show_help
        ;;
esac

exit 0
