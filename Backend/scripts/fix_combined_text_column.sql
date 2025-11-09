-- ============================================================================
-- Fix combined_text column size issue
-- ============================================================================
-- Issue: TEXT column can only store up to 65,535 bytes
-- Solution: Change to MEDIUMTEXT (up to 16MB) for large documents
-- 
-- Created: 2025-11-05
-- Database: smarteye_db
-- ============================================================================

USE smarteye_db;

-- Step 1: Check current column type
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    CHARACTER_MAXIMUM_LENGTH,
    COLUMN_TYPE
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = 'smarteye_db' 
  AND TABLE_NAME = 'combined_results' 
  AND COLUMN_NAME = 'combined_text';

-- Step 2: Backup existing data (optional but recommended)
-- CREATE TABLE combined_results_backup AS SELECT * FROM combined_results;

-- Step 3: Modify column to MEDIUMTEXT (16,777,215 bytes = ~16MB)
ALTER TABLE combined_results 
MODIFY COLUMN combined_text MEDIUMTEXT NOT NULL 
COMMENT '통합된 전체 텍스트 (페이지별 결과 합침) - MEDIUMTEXT';

-- Step 4: Verify the change
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    CHARACTER_MAXIMUM_LENGTH,
    COLUMN_TYPE,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = 'smarteye_db' 
  AND TABLE_NAME = 'combined_results' 
  AND COLUMN_NAME = 'combined_text';

-- Step 5: Check existing data count
SELECT COUNT(*) as total_records FROM combined_results;

-- Expected output:
-- Before: TEXT (65,535 bytes)
-- After:  MEDIUMTEXT (16,777,215 bytes)
