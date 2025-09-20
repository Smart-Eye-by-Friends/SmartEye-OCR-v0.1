-- CIM 서비스 동시성 개선을 위한 데이터베이스 스키마 수정
-- 생성일: 2025-09-20
-- 작성자: SmartEye Backend Architect

-- 1. CIM Outputs 테이블에 동시성 보장을 위한 인덱스 추가
CREATE UNIQUE INDEX IF NOT EXISTS uk_cim_outputs_analysis_job_id
ON cim_outputs(analysis_job_id);

-- 2. 성능 최적화를 위한 복합 인덱스 추가
CREATE INDEX IF NOT EXISTS idx_cim_outputs_status_created_at
ON cim_outputs(generation_status, created_at);

CREATE INDEX IF NOT EXISTS idx_cim_outputs_analysis_job_status
ON cim_outputs(analysis_job_id, generation_status);

-- 3. Analysis Jobs 테이블에 CIM 관련 인덱스 강화
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_status_updated_at
ON analysis_jobs(status, updated_at);

-- 4. 동시성 제어를 위한 락 타임아웃 설정
-- PostgreSQL의 경우 lock_timeout 설정 (30초)
-- 이는 세션별로 설정되므로 애플리케이션에서 관리

-- 5. CIM Output 생성 상태 체크를 위한 뷰 생성
CREATE OR REPLACE VIEW cim_output_status_view AS
SELECT
    aj.id as analysis_job_id,
    aj.job_id,
    aj.status as job_status,
    aj.created_at as job_created_at,
    co.id as cim_output_id,
    co.generation_status,
    co.created_at as cim_created_at,
    co.processing_time_ms,
    CASE
        WHEN co.id IS NULL THEN 'NOT_CREATED'
        WHEN co.generation_status = 'COMPLETED' THEN 'READY'
        WHEN co.generation_status = 'FAILED' THEN 'ERROR'
        ELSE 'PROCESSING'
    END as overall_status
FROM analysis_jobs aj
LEFT JOIN cim_outputs co ON aj.id = co.analysis_job_id
WHERE aj.created_at >= CURRENT_DATE - INTERVAL '7 days'
ORDER BY aj.created_at DESC;

-- 6. 데이터 무결성 검증을 위한 함수 생성
CREATE OR REPLACE FUNCTION validate_cim_output_integrity()
RETURNS TABLE(
    analysis_job_id BIGINT,
    issue_type TEXT,
    issue_description TEXT
) AS $$
BEGIN
    -- CIMOutput이 없는 완료된 AnalysisJob 찾기
    RETURN QUERY
    SELECT
        aj.id,
        'MISSING_CIM_OUTPUT'::TEXT,
        'AnalysisJob은 완료되었지만 CIMOutput이 없습니다'::TEXT
    FROM analysis_jobs aj
    LEFT JOIN cim_outputs co ON aj.id = co.analysis_job_id
    WHERE aj.status = 'COMPLETED'
      AND co.id IS NULL
      AND aj.created_at >= CURRENT_DATE - INTERVAL '1 day';

    -- CIMOutput은 있지만 데이터가 비어있는 경우
    RETURN QUERY
    SELECT
        co.analysis_job_id,
        'EMPTY_CIM_DATA'::TEXT,
        'CIMOutput이 존재하지만 cim_data가 비어있습니다'::TEXT
    FROM cim_outputs co
    WHERE (co.cim_data IS NULL OR LENGTH(TRIM(co.cim_data)) = 0)
      AND co.created_at >= CURRENT_DATE - INTERVAL '1 day';

    -- 중복된 CIMOutput이 있는 경우 (이론적으로 불가능하지만 확인)
    RETURN QUERY
    SELECT
        co.analysis_job_id,
        'DUPLICATE_CIM_OUTPUT'::TEXT,
        '동일한 AnalysisJob에 대해 여러 CIMOutput이 존재합니다'::TEXT
    FROM cim_outputs co
    GROUP BY co.analysis_job_id
    HAVING COUNT(*) > 1;
END;
$$ LANGUAGE plpgsql;

-- 7. 성능 통계를 위한 인덱스 추가
CREATE INDEX IF NOT EXISTS idx_cim_outputs_processing_time
ON cim_outputs(processing_time_ms)
WHERE processing_time_ms IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cim_outputs_total_elements
ON cim_outputs(total_elements)
WHERE total_elements IS NOT NULL;

-- 8. 테이블 통계 업데이트
ANALYZE cim_outputs;
ANALYZE analysis_jobs;

-- 9. 제약조건 확인 및 강화
-- analysis_job_id가 UNIQUE인지 확인 (이미 OneToOne 관계로 보장되지만 명시적 확인)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_cim_outputs_analysis_job_id'
    ) THEN
        ALTER TABLE cim_outputs
        ADD CONSTRAINT uk_cim_outputs_analysis_job_id
        UNIQUE (analysis_job_id);
    END IF;
END $$;

-- 10. 주석 추가
COMMENT ON INDEX uk_cim_outputs_analysis_job_id IS '동시성 보장을 위한 CIMOutput-AnalysisJob 일대일 관계 인덱스';
COMMENT ON VIEW cim_output_status_view IS 'CIM 출력 상태 모니터링을 위한 뷰';
COMMENT ON FUNCTION validate_cim_output_integrity() IS 'CIM 출력 데이터 무결성 검증 함수';