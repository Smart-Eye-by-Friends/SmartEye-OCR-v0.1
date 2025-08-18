-- PostgreSQL 초기화 스크립트
-- 프로덕션 환경에서 사용할 데이터베이스 스키마

-- 사용자 테이블
CREATE TABLE IF NOT EXISTS users (
    user_id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 분석 작업 테이블
CREATE TABLE IF NOT EXISTS analysis_jobs (
    id SERIAL PRIMARY KEY,
    job_id VARCHAR(36) UNIQUE NOT NULL,
    user_id BIGINT REFERENCES users(user_id),
    original_filename VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    file_type VARCHAR(50) NOT NULL,
    file_size BIGINT,
    file_path TEXT,
    result_path TEXT,
    
    -- 파일 정보 강화
    page_number INTEGER,
    source_type VARCHAR(50),
    image_width INTEGER,
    image_height INTEGER,
    
    -- 처리 메타데이터
    model_used VARCHAR(100),
    detected_objects_count INTEGER,
    processing_time DOUBLE PRECISION,
    
    -- API 설정
    api_provider VARCHAR(50),
    integration_method VARCHAR(100),
    
    error_message TEXT,
    progress INTEGER NOT NULL DEFAULT 0,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 레이아웃 블록 테이블
CREATE TABLE IF NOT EXISTS layout_blocks (
    id SERIAL PRIMARY KEY,
    analysis_job_id BIGINT NOT NULL REFERENCES analysis_jobs(id) ON DELETE CASCADE,
    block_index INTEGER NOT NULL,
    class_name VARCHAR(50) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    x1 INTEGER NOT NULL,
    y1 INTEGER NOT NULL,
    x2 INTEGER NOT NULL,
    y2 INTEGER NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    area BIGINT NOT NULL,
    cropped_image_path TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 텍스트 블록 테이블
CREATE TABLE IF NOT EXISTS text_blocks (
    id SERIAL PRIMARY KEY,
    analysis_job_id BIGINT NOT NULL REFERENCES analysis_jobs(id) ON DELETE CASCADE,
    layout_block_id BIGINT REFERENCES layout_blocks(id) ON DELETE SET NULL,
    processing_method VARCHAR(50) NOT NULL,
    extracted_text TEXT,
    processed_content TEXT,
    confidence DOUBLE PRECISION NOT NULL,
    metadata TEXT, -- JSON 형태
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 처리 로그 테이블
CREATE TABLE IF NOT EXISTS processing_logs (
    id SERIAL PRIMARY KEY,
    analysis_job_id BIGINT REFERENCES analysis_jobs(id) ON DELETE CASCADE,
    session_id VARCHAR(36),
    module_name VARCHAR(100) NOT NULL,
    log_level VARCHAR(10),
    message TEXT NOT NULL,
    metadata TEXT, -- JSON 형태
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- CIM 출력 테이블
CREATE TABLE IF NOT EXISTS cim_outputs (
    output_id SERIAL PRIMARY KEY,
    analysis_job_id BIGINT NOT NULL REFERENCES analysis_jobs(id) ON DELETE CASCADE,
    output_content TEXT,
    output_format VARCHAR(20) NOT NULL,
    file_path TEXT,
    integration_method TEXT,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_job_id ON analysis_jobs(job_id);
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_user_id ON analysis_jobs(user_id);
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_status ON analysis_jobs(status);
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_created_at ON analysis_jobs(created_at);

CREATE INDEX IF NOT EXISTS idx_layout_blocks_analysis_job_id ON layout_blocks(analysis_job_id);
CREATE INDEX IF NOT EXISTS idx_text_blocks_analysis_job_id ON text_blocks(analysis_job_id);
CREATE INDEX IF NOT EXISTS idx_text_blocks_layout_block_id ON text_blocks(layout_block_id);

CREATE INDEX IF NOT EXISTS idx_processing_logs_analysis_job_id ON processing_logs(analysis_job_id);
CREATE INDEX IF NOT EXISTS idx_processing_logs_module_name ON processing_logs(module_name);
CREATE INDEX IF NOT EXISTS idx_processing_logs_timestamp ON processing_logs(timestamp);

CREATE INDEX IF NOT EXISTS idx_cim_outputs_analysis_job_id ON cim_outputs(analysis_job_id);
CREATE INDEX IF NOT EXISTS idx_cim_outputs_output_format ON cim_outputs(output_format);

-- 기본 사용자 생성
INSERT INTO users (username, email) 
VALUES ('default', 'default@smarteye.com')
ON CONFLICT (username) DO NOTHING;

-- 트리거: updated_at 자동 업데이트
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_analysis_jobs_updated_at 
    BEFORE UPDATE ON analysis_jobs 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();
