-- ============================================================================
-- SmartEye Processing Time 성능 최적화 인덱스
-- 생성일: 2025-09-19
-- 목적: processingTimeMs 필드 관련 쿼리 성능 개선 및 NULL 안전성 보장
-- ============================================================================

-- 1. AnalysisJob processingTimeMs 인덱스 (NULL 값 포함)
-- OptimizedQueryRepository.getUserProcessingStatistics() 쿼리 최적화
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_analysis_jobs_processing_time_with_nulls
ON analysis_jobs (processing_time_ms NULLS LAST, status, user_id);

-- 2. DocumentPage processingTimeMs 복합 인덱스
-- Page 레벨 처리시간 집계를 위한 최적화
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_document_pages_processing_time_job
ON document_pages (analysis_job_id, processing_time_ms NULLS LAST, processing_status);

-- 3. 느린 작업 조회용 인덱스 (OptimizedQueryRepository.findSlowJobsWithDetails)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_analysis_jobs_slow_processing
ON analysis_jobs (processing_time_ms DESC)
WHERE processing_time_ms IS NOT NULL;

-- 4. 통계 계산용 복합 인덱스
-- 사용자별 작업 통계 집계 최적화
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_analysis_jobs_user_stats
ON analysis_jobs (user_id, status, processing_time_ms NULLS LAST, created_at);

-- 5. DocumentPage 통계용 인덱스
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_document_pages_stats
ON document_pages (processing_status, processing_time_ms NULLS LAST, created_at);

-- 6. LayoutBlock 처리시간 인덱스 (기존 쿼리 최적화)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_layout_blocks_processing_time
ON layout_blocks (processing_time_ms DESC NULLS LAST, processing_status);

-- 7. CIMOutput 처리시간 인덱스
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cim_output_processing_time
ON cim_output (processing_time_ms DESC NULLS LAST, generation_status);

-- ============================================================================
-- 인덱스 생성 확인 및 통계 업데이트
-- ============================================================================

-- 통계 정보 업데이트 (PostgreSQL 성능 최적화)
ANALYZE analysis_jobs;
ANALYZE document_pages;
ANALYZE layout_blocks;
ANALYZE cim_output;

-- 인덱스 생성 확인용 뷰
CREATE OR REPLACE VIEW v_processing_time_indexes AS
SELECT
    schemaname,
    tablename,
    indexname,
    indexdef
FROM pg_indexes
WHERE indexname LIKE '%processing_time%'
   OR indexname LIKE '%slow_processing%'
   OR indexname LIKE '%user_stats%'
ORDER BY tablename, indexname;

-- 코멘트 추가
COMMENT ON INDEX idx_analysis_jobs_processing_time_with_nulls IS 'OptimizedQueryRepository.getUserProcessingStatistics() 성능 최적화';
COMMENT ON INDEX idx_document_pages_processing_time_job IS 'Page 레벨 처리시간 집계 쿼리 최적화';
COMMENT ON INDEX idx_analysis_jobs_slow_processing IS '느린 작업 조회 성능 최적화';
COMMENT ON INDEX idx_analysis_jobs_user_stats IS '사용자별 통계 계산 성능 최적화';
COMMENT ON INDEX idx_document_pages_stats IS 'DocumentPage 통계 쿼리 최적화';
COMMENT ON INDEX idx_layout_blocks_processing_time IS 'LayoutBlock 처리시간 정렬 최적화';
COMMENT ON INDEX idx_cim_output_processing_time IS 'CIMOutput 처리시간 쿼리 최적화';