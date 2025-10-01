-- V002__add_cim_output_constraints.sql
-- CIM 분석 API DB 저장 오류 해결을 위한 스키마 개선

-- 1. CIMOutput 테이블에 UNIQUE 제약조건 추가 (중복 방지)
ALTER TABLE cim_outputs
ADD CONSTRAINT uk_cim_outputs_analysis_job_id
UNIQUE (analysis_job_id);

-- 2. CIMOutput 테이블 인덱스 최적화
CREATE INDEX IF NOT EXISTS idx_cim_outputs_status_created
ON cim_outputs (generation_status, created_at);

CREATE INDEX IF NOT EXISTS idx_cim_outputs_analysis_job_status
ON cim_outputs (analysis_job_id, generation_status);

-- 3. AnalysisJob 테이블 상태 인덱스 최적화
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_status_updated
ON analysis_jobs (status, updated_at);

-- 4. DocumentPage 테이블 복합 인덱스 추가 (멱등성 보장용)
CREATE INDEX IF NOT EXISTS idx_document_pages_job_page
ON document_pages (analysis_job_id, page_number);

-- 5. 외래키 제약조건 강화 (CASCADE 설정)
ALTER TABLE cim_outputs
DROP CONSTRAINT IF EXISTS fk_cim_outputs_analysis_job;

ALTER TABLE cim_outputs
ADD CONSTRAINT fk_cim_outputs_analysis_job
FOREIGN KEY (analysis_job_id)
REFERENCES analysis_jobs(id)
ON DELETE CASCADE ON UPDATE CASCADE;

-- 6. CIMOutput 테이블에 체크 제약조건 추가 (데이터 무결성)
ALTER TABLE cim_outputs
ADD CONSTRAINT chk_cim_outputs_cim_data_not_empty
CHECK (cim_data IS NOT NULL AND LENGTH(TRIM(cim_data)) > 0);

ALTER TABLE cim_outputs
ADD CONSTRAINT chk_cim_outputs_processing_time_positive
CHECK (processing_time_ms IS NULL OR processing_time_ms >= 0);

ALTER TABLE cim_outputs
ADD CONSTRAINT chk_cim_outputs_elements_non_negative
CHECK (
    (total_elements IS NULL OR total_elements >= 0) AND
    (text_elements IS NULL OR text_elements >= 0) AND
    (ai_described_elements IS NULL OR ai_described_elements >= 0) AND
    (total_figures IS NULL OR total_figures >= 0) AND
    (total_tables IS NULL OR total_tables >= 0)
);

-- 7. AnalysisJob 상태 체크 제약조건 추가
ALTER TABLE analysis_jobs
ADD CONSTRAINT chk_analysis_jobs_progress_range
CHECK (progress_percentage >= 0 AND progress_percentage <= 100);

-- 8. DocumentPage 처리 시간 체크 제약조건 추가
ALTER TABLE document_pages
ADD CONSTRAINT chk_document_pages_processing_time_positive
CHECK (processing_time_ms IS NULL OR processing_time_ms >= 0);

-- 9. 성능 최적화를 위한 파티셔닝 준비 (향후 확장용)
-- 대량 데이터 처리시 created_at 기준 월별 파티셔닝 가능하도록 인덱스 추가
CREATE INDEX IF NOT EXISTS idx_cim_outputs_created_month
ON cim_outputs (EXTRACT(YEAR FROM created_at), EXTRACT(MONTH FROM created_at));

CREATE INDEX IF NOT EXISTS idx_analysis_jobs_created_month
ON analysis_jobs (EXTRACT(YEAR FROM created_at), EXTRACT(MONTH FROM created_at));

-- 10. 통계 정보 업데이트를 위한 트리거 함수 생성 (PostgreSQL)
CREATE OR REPLACE FUNCTION update_cim_output_statistics()
RETURNS TRIGGER AS $$
BEGIN
    -- CIMOutput 생성/업데이트시 관련 통계 정보 자동 계산
    IF TG_OP = 'INSERT' OR TG_OP = 'UPDATE' THEN
        -- 기본 통계 정보가 없으면 0으로 초기화
        NEW.total_elements = COALESCE(NEW.total_elements, 0);
        NEW.text_elements = COALESCE(NEW.text_elements, 0);
        NEW.ai_described_elements = COALESCE(NEW.ai_described_elements, 0);
        NEW.total_figures = COALESCE(NEW.total_figures, 0);
        NEW.total_tables = COALESCE(NEW.total_tables, 0);
        NEW.total_word_count = COALESCE(NEW.total_word_count, 0);
        NEW.total_char_count = COALESCE(NEW.total_char_count, 0);

        RETURN NEW;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- 11. 트리거 생성
DROP TRIGGER IF EXISTS trigger_update_cim_output_statistics ON cim_outputs;
CREATE TRIGGER trigger_update_cim_output_statistics
    BEFORE INSERT OR UPDATE ON cim_outputs
    FOR EACH ROW
    EXECUTE FUNCTION update_cim_output_statistics();

-- 12. 댓글 추가 (개발자 참고용)
COMMENT ON CONSTRAINT uk_cim_outputs_analysis_job_id ON cim_outputs
IS 'CIM 분석 API에서 동일 AnalysisJob에 대한 중복 CIMOutput 생성 방지';

COMMENT ON INDEX idx_cim_outputs_status_created
IS 'CIMOutput 상태별 시간순 조회 성능 최적화';

COMMENT ON FUNCTION update_cim_output_statistics()
IS 'CIMOutput 통계 정보 자동 계산 및 초기화';

-- 13. 마이그레이션 적용 확인을 위한 뷰 생성
CREATE OR REPLACE VIEW v_cim_analysis_health AS
SELECT
    aj.job_id,
    aj.status as job_status,
    aj.progress_percentage,
    co.generation_status as cim_status,
    co.total_elements,
    co.processing_time_ms,
    co.created_at as cim_created_at,
    CASE
        WHEN aj.status = 'COMPLETED' AND co.generation_status = 'COMPLETED' THEN 'HEALTHY'
        WHEN aj.status = 'FAILED' OR co.generation_status = 'FAILED' THEN 'FAILED'
        ELSE 'PROCESSING'
    END as overall_health
FROM analysis_jobs aj
LEFT JOIN cim_outputs co ON aj.id = co.analysis_job_id
WHERE aj.created_at >= CURRENT_DATE - INTERVAL '7 days'
ORDER BY aj.created_at DESC;

COMMENT ON VIEW v_cim_analysis_health
IS 'CIM 분석 시스템 상태 모니터링을 위한 뷰 (최근 7일)';