#!/bin/bash

# =============================================================================
# SmartEye Backend Advanced Health Check Script
# Comprehensive health monitoring for all system components
# =============================================================================

set -e

# Configuration
HEALTH_URL="http://localhost:8000/api/v1/health/"
TIMEOUT=15
MAX_RETRIES=3
CRITICAL_SERVICES=("database" "redis" "django")

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_debug() {
    if [ "$DEBUG_HEALTH" = "true" ]; then
        echo -e "${YELLOW}[HEALTH-DEBUG]${NC} $1" >&2
    fi
}

log_error() {
    echo -e "${RED}[HEALTH-ERROR]${NC} $1" >&2
}

# HTTP Health Check with retries
http_health_check() {
    local url="$1"
    local timeout="$2"
    local retries="$3"
    
    for i in $(seq 1 $retries); do
        log_debug "HTTP health check attempt $i/$retries"
        
        # Try curl first (most reliable)
        if command -v curl >/dev/null 2>&1; then
            if curl -f -s --max-time "$timeout" --connect-timeout 5 "$url" >/dev/null 2>&1; then
                log_debug "HTTP health check passed (curl)"
                return 0
            fi
        # Try wget as fallback
        elif command -v wget >/dev/null 2>&1; then
            if wget --quiet --timeout="$timeout" --tries=1 --spider "$url" >/dev/null 2>&1; then
                log_debug "HTTP health check passed (wget)"
                return 0
            fi
        # Use Python as last resort
        else
            if python3 -c "
import urllib.request
import socket
import sys
try:
    socket.setdefaulttimeout($timeout)
    with urllib.request.urlopen('$url') as response:
        if response.getcode() == 200:
            sys.exit(0)
    sys.exit(1)
except Exception as e:
    sys.exit(1)
" 2>/dev/null; then
                log_debug "HTTP health check passed (python)"
                return 0
            fi
        fi
        
        if [ $i -lt $retries ]; then
            log_debug "HTTP health check failed, retrying in 2 seconds..."
            sleep 2
        fi
    done
    
    log_error "HTTP health check failed after $retries attempts"
    return 1
}

# Database connectivity check
check_database() {
    log_debug "Checking database connectivity..."
    
    # Use Django management command for database check
    if python3 manage.py shell -c "
from django.db import connections
from django.core.exceptions import ImproperlyConfigured
import sys
try:
    db_conn = connections['default']
    with db_conn.cursor() as cursor:
        cursor.execute('SELECT 1')
        result = cursor.fetchone()
        if result[0] == 1:
            sys.exit(0)
    sys.exit(1)
except Exception as e:
    print(f'Database check failed: {e}', file=sys.stderr)
    sys.exit(1)
" 2>/dev/null; then
        log_debug "Database connectivity: OK"
        return 0
    else
        log_error "Database connectivity: FAILED"
        return 1
    fi
}

# Redis connectivity check (for cache and channels)
check_redis() {
    log_debug "Checking Redis connectivity..."
    
    if python3 -c "
import redis
import sys
import os
from django.conf import settings
import django
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'smarteye.settings.production')
django.setup()

try:
    # Check cache backend
    from django.core.cache import cache
    cache.set('health_check', 'ok', 30)
    if cache.get('health_check') == 'ok':
        cache.delete('health_check')
        print('Redis cache: OK')
        sys.exit(0)
    else:
        print('Redis cache check failed')
        sys.exit(1)
except Exception as e:
    print(f'Redis check failed: {e}', file=sys.stderr)
    sys.exit(1)
" 2>/dev/null; then
        log_debug "Redis connectivity: OK"
        return 0
    else
        log_error "Redis connectivity: FAILED"
        return 1
    fi
}

# Check disk space
check_disk_space() {
    log_debug "Checking disk space..."
    
    # Check if disk usage is below 90%
    disk_usage=$(df /app | awk 'NR==2 {print $5}' | sed 's/%//')
    
    if [ "$disk_usage" -lt 90 ]; then
        log_debug "Disk space: OK ($disk_usage% used)"
        return 0
    else
        log_error "Disk space: WARNING ($disk_usage% used)"
        return 1
    fi
}

# Check memory usage
check_memory() {
    log_debug "Checking memory usage..."
    
    # Get memory info
    if [ -r /proc/meminfo ]; then
        mem_total=$(awk '/MemTotal/ {print $2}' /proc/meminfo)
        mem_available=$(awk '/MemAvailable/ {print $2}' /proc/meminfo)
        
        if [ "$mem_available" -gt 0 ] && [ "$mem_total" -gt 0 ]; then
            mem_usage=$((100 - (mem_available * 100 / mem_total)))
            
            if [ "$mem_usage" -lt 95 ]; then
                log_debug "Memory usage: OK ($mem_usage% used)"
                return 0
            else
                log_error "Memory usage: WARNING ($mem_usage% used)"
                return 1
            fi
        fi
    fi
    
    log_debug "Memory check: skipped (unable to read /proc/meminfo)"
    return 0
}

# Main health check logic
main() {
    local exit_code=0
    local start_time=$(date +%s)
    
    log_debug "Starting comprehensive health check..."
    
    # Primary HTTP health check (most important)
    if ! http_health_check "$HEALTH_URL" "$TIMEOUT" "$MAX_RETRIES"; then
        exit_code=1
    fi
    
    # Additional checks only in debug mode or if primary check passes
    if [ "$EXTENDED_HEALTH_CHECK" = "true" ] || [ "$exit_code" -eq 0 ]; then
        
        # Database check
        if ! check_database; then
            exit_code=1
        fi
        
        # Redis check (non-critical, warn only)
        if ! check_redis; then
            log_error "Redis check failed (non-critical)"
        fi
        
        # Resource checks (non-critical)
        check_disk_space || true
        check_memory || true
    fi
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    if [ "$exit_code" -eq 0 ]; then
        log_debug "Health check completed successfully in ${duration}s"
        exit 0
    else
        log_error "Health check failed in ${duration}s"
        exit 1
    fi
}

# Run main function
main "$@"
