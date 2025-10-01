-- Migration: Phase 2 구조적 개선 - AnalysisJob에 processingTimeMs 컬럼 추가
-- Author: SmartEye Development Team
-- Date: 2025-09-19
-- Description: Phase 2 리팩토링으로 누락된 AnalysisJob.processingTimeMs 필드를 복구하고 기존 데이터를 백필

-- ==========================================================================
-- 1. analysis_jobs 테이블에 processing_time_ms 컬럼 추가
-- ==========================================================================

-- 컬럼이 이미 존재하지 않는 경우에만 추가
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'analysis_jobs'
        AND column_name = 'processing_time_ms'
    ) THEN
        -- processingTimeMs 컬럼 추가 (BIGINT, NULL 허용)
        ALTER TABLE analysis_jobs
        ADD COLUMN processing_time_ms BIGINT NULL;

        -- 컬럼 설명 추가
        COMMENT ON COLUMN analysis_jobs.processing_time_ms IS
        'Phase 2: 전체 분석 작업 처리 시간 (밀리초)';

        RAISE NOTICE 'processing_time_ms 컬럼이 analysis_jobs 테이블에 추가되었습니다.';
    ELSE
        RAISE NOTICE 'processing_time_ms 컬럼이 이미 존재합니다.';
    END IF;
END $$;

-- ==========================================================================
-- 2. 기존 데이터 백필 (Backfill)
-- ==========================================================================

-- 기존 analysis_jobs 레코드에 대해 연결된 document_pages의 processing_time_ms 합계를 계산하여 채우기
UPDATE analysis_jobs
SET processing_time_ms = (
    SELECT COALESCE(SUM(COALESCE(dp.processing_time_ms, 0)), 0)
    FROM document_pages dp
    WHERE dp.analysis_job_id = analysis_jobs.id
)
WHERE processing_time_ms IS NULL
AND EXISTS (
    SELECT 1
    FROM document_pages dp
    WHERE dp.analysis_job_id = analysis_jobs.id
);

-- ==========================================================================
-- 3. 인덱스 추가 (성능 최적화)
-- ==========================================================================

-- processing_time_ms에 대한 인덱스 (통계 쿼리 최적화)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_analysis_jobs_processing_time_ms
ON analysis_jobs (processing_time_ms)
WHERE processing_time_ms IS NOT NULL;

-- 복합 인덱스: status + processing_time_ms (성능 모니터링용)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_analysis_jobs_status_processing_time
ON analysis_jobs (status, processing_time_ms)
WHERE processing_time_ms IS NOT NULL;

-- ==========================================================================
-- 4. 데이터 무결성 검증
-- ==========================================================================

-- 백필 결과 검증
DO $$
DECLARE
    total_jobs INTEGER;
    filled_jobs INTEGER;
    zero_time_jobs INTEGER;
BEGIN
    -- 전체 작업 수
    SELECT COUNT(*) INTO total_jobs FROM analysis_jobs;

    -- 처리 시간이 채워진 작업 수
    SELECT COUNT(*) INTO filled_jobs
    FROM analysis_jobs
    WHERE processing_time_ms IS NOT NULL;

    -- 처리 시간이 0인 작업 수 (정상적일 수 있음)
    SELECT COUNT(*) INTO zero_time_jobs
    FROM analysis_jobs
    WHERE processing_time_ms = 0;

    RAISE NOTICE '=== 데이터 백필 결과 ===';
    RAISE NOTICE '전체 작업 수: %', total_jobs;
    RAISE NOTICE '처리 시간 채워진 작업 수: %', filled_jobs;
    RAISE NOTICE '처리 시간 0인 작업 수: %', zero_time_jobs;
    RAISE NOTICE '백필 성공률: %', ROUND((filled_jobs::decimal / total_jobs) * 100, 2) || '%';
END $$;

-- ==========================================================================
-- 5. 향후 데이터 일관성 보장을 위한 트리거 (옵션)
-- ==========================================================================

-- ProcessingTimeMs 자동 계산 트리거 함수 (옵션 - 필요시 활성화)
/*
CREATE OR REPLACE FUNCTION calculate_analysis_job_processing_time()
RETURNS TRIGGER AS $$
BEGIN
    -- Document Page의 processing_time_ms가 변경될 때
    -- 상위 AnalysisJob의 processing_time_ms도 자동 업데이트
    IF TG_OP = 'INSERT' OR TG_OP = 'UPDATE' THEN
        UPDATE analysis_jobs
        SET processing_time_ms = (
            SELECT COALESCE(SUM(COALESCE(dp.processing_time_ms, 0)), 0)
            FROM document_pages dp
            WHERE dp.analysis_job_id = NEW.analysis_job_id
        )
        WHERE id = NEW.analysis_job_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE analysis_jobs
        SET processing_time_ms = (
            SELECT COALESCE(SUM(COALESCE(dp.processing_time_ms, 0)), 0)
            FROM document_pages dp
            WHERE dp.analysis_job_id = OLD.analysis_job_id
        )
        WHERE id = OLD.analysis_job_id;
    END IF;

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

-- 트리거 생성 (현재는 주석 처리 - 필요시 활성화)
-- CREATE TRIGGER trg_update_analysis_job_processing_time
--     AFTER INSERT OR UPDATE OR DELETE ON document_pages
--     FOR EACH ROW
--     EXECUTE FUNCTION calculate_analysis_job_processing_time();
*/

-- ==========================================================================
-- 6. 마이그레이션 완료 로그
-- ==========================================================================

INSERT INTO migration_history (
    version,
    description,
    executed_at,
    execution_time_ms
) VALUES (
    'V20250919002',
    'Add processing_time_ms to analysis_jobs table and backfill existing data',
    NOW(),
    0  -- 실제 실행 시간은 PostgreSQL이 자동 계산
) ON CONFLICT (version) DO NOTHING;

RAISE NOTICE '=== 마이그레이션 V20250919002 완료 ===';
RAISE NOTICE 'AnalysisJob.processingTimeMs 필드 복구 및 데이터 백필이 완료되었습니다.';
RAISE NOTICE 'Repository 쿼리에서 안전하게 사용할 수 있습니다.';