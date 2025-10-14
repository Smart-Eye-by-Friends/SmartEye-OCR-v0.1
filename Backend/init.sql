-- SmartEye Database Initialization Script

-- 데이터베이스 및 사용자 설정 (필요한 경우)
-- CREATE DATABASE smarteye_db;
-- CREATE USER smarteye WITH ENCRYPTED PASSWORD 'smarteye_password';
-- GRANT ALL PRIVILEGES ON DATABASE smarteye_db TO smarteye;

-- Extensions 설치 (UUID 지원)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 기본 데이터 삽입
INSERT INTO users (username, email, created_at, updated_at) 
VALUES 
    ('admin', 'admin@smarteye.com', NOW(), NOW()),
    ('demo_user', 'demo@smarteye.com', NOW(), NOW())
ON CONFLICT DO NOTHING;

-- 인덱스 생성 (성능 최적화)
-- 사용자 테이블
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);

-- 분석 작업 테이블
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_user_id ON analysis_jobs(user_id);
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_status ON analysis_jobs(status);
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_created_at ON analysis_jobs(created_at);
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_job_id ON analysis_jobs(job_id);

-- 문서 페이지 테이블
CREATE INDEX IF NOT EXISTS idx_document_pages_analysis_job_id ON document_pages(analysis_job_id);
CREATE INDEX IF NOT EXISTS idx_document_pages_page_number ON document_pages(page_number);

-- 레이아웃 블록 테이블
CREATE INDEX IF NOT EXISTS idx_layout_blocks_document_page_id ON layout_blocks(document_page_id);
CREATE INDEX IF NOT EXISTS idx_layout_blocks_class_name ON layout_blocks(class_name);

-- P3.4 성능 최적화: 공간 분석 및 조회 성능 향상 인덱스
-- 1. 좌표 기반 검색 최적화 (X 좌표)
CREATE INDEX IF NOT EXISTS idx_layout_blocks_x1_x2 ON layout_blocks(x1, x2);

-- 2. 좌표 기반 검색 최적화 (Y 좌표)
CREATE INDEX IF NOT EXISTS idx_layout_blocks_y1_y2 ON layout_blocks(y1, y2);

-- 3. 복합 인덱스: 좌표 범위 쿼리 최적화
CREATE INDEX IF NOT EXISTS idx_layout_blocks_coords ON layout_blocks(x1, y1, x2, y2);

-- 4. 레이아웃 클래스 + 문서 페이지 복합 인덱스
CREATE INDEX IF NOT EXISTS idx_layout_blocks_page_class ON layout_blocks(document_page_id, class_name);

-- 5. 신뢰도 기반 필터링 최적화
CREATE INDEX IF NOT EXISTS idx_layout_blocks_confidence ON layout_blocks(confidence DESC);

-- 텍스트 블록 테이블
CREATE INDEX IF NOT EXISTS idx_text_blocks_layout_block_id ON text_blocks(layout_block_id);

-- CIM 출력 테이블
CREATE INDEX IF NOT EXISTS idx_cim_outputs_analysis_job_id ON cim_outputs(analysis_job_id);

-- 처리 로그 테이블
CREATE INDEX IF NOT EXISTS idx_processing_logs_job_id ON processing_logs(job_id);
CREATE INDEX IF NOT EXISTS idx_processing_logs_level ON processing_logs(level);
CREATE INDEX IF NOT EXISTS idx_processing_logs_timestamp ON processing_logs(timestamp);

-- 뷰 생성 (분석 통계)
CREATE OR REPLACE VIEW analysis_stats AS
SELECT 
    DATE(aj.created_at) as analysis_date,
    COUNT(*) as total_jobs,
    COUNT(CASE WHEN aj.status = 'COMPLETED' THEN 1 END) as completed_jobs,
    COUNT(CASE WHEN aj.status = 'FAILED' THEN 1 END) as failed_jobs,
    AVG(EXTRACT(EPOCH FROM (aj.completed_at - aj.created_at))) as avg_processing_time_seconds
FROM analysis_jobs aj
WHERE aj.created_at >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY DATE(aj.created_at)
ORDER BY analysis_date DESC;

-- 테이블 통계 업데이트
ANALYZE;