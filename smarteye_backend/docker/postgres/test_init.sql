-- 테스트 데이터베이스 초기화 스크립트

-- 테스트용 확장 모듈 설치
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- 테스트 최적화 설정
ALTER DATABASE smarteye_test SET timezone TO 'UTC';
ALTER DATABASE smarteye_test SET log_statement TO 'none';  -- 테스트 시 로그 감소
ALTER DATABASE smarteye_test SET log_duration TO 'off';   -- 성능 로그 비활성화

-- 테스트용 성능 설정 (낮은 리소스 사용)
ALTER SYSTEM SET shared_buffers = '128MB';
ALTER SYSTEM SET effective_cache_size = '256MB';
ALTER SYSTEM SET maintenance_work_mem = '32MB';

-- 완료 로그
\echo 'Test database initialization completed successfully.'