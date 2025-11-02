-- ============================================================================
-- SmartEyeSsen DB 초기화 스크립트
-- ============================================================================
-- 이 스크립트는 Docker 컨테이너 최초 실행 시 자동으로 실행됩니다.
-- (/docker-entrypoint-initdb.d/ 디렉토리)

-- 문자셋 설정 확인
SHOW VARIABLES LIKE 'character_set%';
SHOW VARIABLES LIKE 'collation%';

-- 데이터베이스 문자셋 강제 설정
ALTER DATABASE smarteyessen_db CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 선택적: 테스트용 사용자 생성
-- CREATE USER IF NOT EXISTS 'test_user'@'%' IDENTIFIED BY 'test_password';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON smarteyessen_db.* TO 'test_user'@'%';

-- 선택적: 읽기 전용 사용자 생성 (분석/모니터링용)
-- CREATE USER IF NOT EXISTS 'readonly_user'@'%' IDENTIFIED BY 'readonly_password';
-- GRANT SELECT ON smarteyessen_db.* TO 'readonly_user'@'%';

-- 권한 적용
FLUSH PRIVILEGES;

-- 초기화 완료 로그
SELECT '초기화 완료: UTF-8 설정 및 사용자 권한 설정 완료' AS status;
