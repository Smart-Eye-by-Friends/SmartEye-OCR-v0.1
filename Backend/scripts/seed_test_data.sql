-- ============================================================================
-- SmartEyeSsen 테스트 데이터 시드 스크립트 (Docker MySQL용)
-- ============================================================================
-- 실행 방법: 
-- docker exec -i smart_mysql mysql -u root -p'1q2w3e4r' smarteyessen_db < Backend/scripts/seed_test_data.sql

USE smarteyessen_db;

-- ============================================================================
-- 1. 기본 테스트 사용자 생성
-- ============================================================================
INSERT INTO users (user_id, email, name, role, password_hash, api_key, created_at, updated_at)
VALUES 
    (1, 'test@smarteyessen.com', '테스트 사용자', 'user', 'dummy_hash_for_test', NULL, NOW(), NOW()),
    (2, 'admin@smarteyessen.com', '관리자', 'admin', 'dummy_hash_for_admin', NULL, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    email = VALUES(email),
    name = VALUES(name);

-- ============================================================================
-- 2. 기본 문서 타입 생성
-- ============================================================================
INSERT INTO document_types (doc_type_id, type_name, model_name, sorting_method, description, created_at, updated_at)
VALUES 
    (1, '일반문서', 'general', 'reading_order', '테스트용 기본 문서 타입', NOW(), NOW()),
    (2, '수학문제', 'math', 'question_based', '수학 문제가 포함된 문서', NOW(), NOW()),
    (3, '표/차트', 'table', 'reading_order', '표와 차트가 포함된 문서', NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    type_name = VALUES(type_name),
    model_name = VALUES(model_name),
    sorting_method = VALUES(sorting_method),
    description = VALUES(description);

-- ============================================================================
-- 3. 데이터 확인
-- ============================================================================
SELECT '=== Users ===' AS '';
SELECT user_id, email, name, role FROM users;

SELECT '=== Document Types ===' AS '';
SELECT doc_type_id, type_name, description FROM document_types;
