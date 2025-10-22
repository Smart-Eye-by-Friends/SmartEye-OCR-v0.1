-- ============================================================================
-- SmartEyeSsen Database Schema (Final Production Version)
-- ============================================================================
-- 프로젝트명: SmartEyeSsen - AI 기반 학습지 분석 시스템
-- 데이터베이스: smarteyessen_db
-- 문자셋: utf8mb4 (이모지, 다국어 지원)
-- 엔진: InnoDB (트랜잭션, 외래키 지원)
-- 총 테이블 수: 12개
-- 최종 수정일: 2025-01-22
-- 작성자: SmartEyeSsen Team
-- ============================================================================

-- ============================================================================
-- 📋 테이블 목록 및 관계
-- ============================================================================
-- 1. users                (사용자 관리) - 독립 테이블
-- 2. document_types       (문서 타입 정의) - 독립 테이블
-- 3. projects             (프로젝트/세션) - FK: user_id, doc_type_id
-- 4. pages                (페이지 정보) - FK: project_id
-- 5. layout_elements      (레이아웃 요소) - FK: page_id
-- 6. text_contents        (OCR 결과) - FK: element_id (1:1)
-- 7. ai_descriptions      (AI 설명) - FK: element_id (1:1)
-- 8. question_groups      (문제 그룹) - FK: page_id
-- 9. question_elements    (문제-요소 매핑) - FK: question_group_id, element_id
-- 10. text_versions       (텍스트 버전 관리) - FK: page_id, user_id
-- 11. formatting_rules    (포맷팅 규칙) - FK: doc_type_id
-- 12. combined_results    (통합 문서 캐시) - FK: project_id (1:1)

-- ============================================================================
-- 🔗 주요 관계 요약
-- ============================================================================
-- users → projects (1:N)
-- document_types → projects (1:N)
-- document_types → formatting_rules (1:N)
-- projects → pages (1:N)
-- projects → combined_results (1:1)
-- pages → layout_elements (1:N)
-- pages → question_groups (1:N)
-- pages → text_versions (1:N)
-- layout_elements → text_contents (1:1)
-- layout_elements → ai_descriptions (1:1)
-- layout_elements ← question_elements (N:1)
-- question_groups → question_elements (1:N)

-- ============================================================================
-- 데이터베이스 생성 (기존 DB가 있으면 삭제 후 재생성)
-- ============================================================================
DROP DATABASE IF EXISTS smarteyessen_db;

CREATE DATABASE smarteyessen_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE smarteyessen_db;

-- ============================================================================
-- 1️⃣ Users Table (사용자 관리)
-- ============================================================================
-- 설명: 시스템 사용자 정보 (학생, 교사, 관리자)
-- 주요 기능: 회원가입, 로그인, 권한 관리, API 키 관리
-- ============================================================================
CREATE TABLE users (
    -- 기본 정보
    user_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '사용자 고유 ID',
    email VARCHAR(255) NOT NULL UNIQUE COMMENT '이메일 (로그인 ID)',
    name VARCHAR(100) NOT NULL COMMENT '사용자 이름',
    role VARCHAR(50) NOT NULL DEFAULT 'user' COMMENT '역할 (admin/teacher/student/user)',
    
    -- 보안 정보
    password_hash VARCHAR(255) NOT NULL COMMENT 'bcrypt 해시된 비밀번호',
    api_key VARCHAR(255) DEFAULT NULL COMMENT 'OpenAI API 키 (AES-256 암호화 저장)',
    
    -- 타임스탬프
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '계정 생성일',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '마지막 수정일',
    
    -- 인덱스
    INDEX idx_email (email) COMMENT '이메일 검색 최적화',
    INDEX idx_role (role) COMMENT '역할별 필터링 최적화'
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci
  COMMENT='시스템 사용자 정보';

-- ============================================================================
-- 2️⃣ Document_Types Table (문서 타입 정의)
-- ============================================================================
-- 설명: 문서 종류별 처리 방식 정의 (문제지/일반문서)
-- 주요 기능: 모델 선택, 정렬 방식 지정, 포맷팅 규칙 연결
-- ============================================================================
CREATE TABLE document_types (
    -- 기본 정보
    doc_type_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '문서 타입 고유 ID',
    type_name VARCHAR(100) NOT NULL UNIQUE COMMENT '타입명 (worksheet/document/form)',
    
    -- 처리 설정
    model_name VARCHAR(100) NOT NULL COMMENT 'AI 모델명 (SmartEyeSsen/DocLayout-YOLO)',
    sorting_method ENUM('question_based', 'coordinate_based', 'reading_order') NOT NULL 
        COMMENT '정렬 방식: question_based(문제지), coordinate_based(일반문서), reading_order(읽기순서)',
    
    -- 부가 정보
    description TEXT DEFAULT NULL COMMENT '타입 설명',
    
    -- 타임스탬프
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    
    -- 인덱스
    INDEX idx_type_name (type_name) COMMENT '타입명 검색 최적화'
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci
  COMMENT='문서 타입 정의 (문제지/일반문서)';

-- ============================================================================
-- 3️⃣ Projects Table (프로젝트/세션 관리)
-- ============================================================================
-- 설명: 사용자의 분석 프로젝트 (여러 페이지 포함)
-- 주요 기능: 프로젝트 생성, 진행률 추적, 상태 관리
-- ============================================================================
CREATE TABLE projects (
    -- 기본 정보
    project_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '프로젝트 고유 ID',
    user_id INT NOT NULL COMMENT '소유자 ID',
    doc_type_id INT NOT NULL COMMENT '문서 타입 ID',
    project_name VARCHAR(255) NOT NULL COMMENT '프로젝트 이름',
    
    -- 진행 상태
    total_pages INT DEFAULT 0 COMMENT '총 페이지 수 (트리거로 자동 계산)',
    analysis_mode ENUM('auto', 'manual', 'hybrid') DEFAULT 'auto' 
        COMMENT '분석 모드: auto(자동), manual(수동), hybrid(혼합)',
    status ENUM('created', 'in_progress', 'completed', 'error') DEFAULT 'created' 
        COMMENT '프로젝트 상태',
    
    -- 타임스탬프
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '프로젝트 생성일',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '마지막 수정일',
    
    -- 외래키
    FOREIGN KEY (user_id) REFERENCES users(user_id) 
        ON DELETE CASCADE 
        COMMENT '사용자 삭제 시 프로젝트도 삭제',
    FOREIGN KEY (doc_type_id) REFERENCES document_types(doc_type_id) 
        ON DELETE RESTRICT 
        COMMENT '사용 중인 타입은 삭제 불가',
    
    -- 인덱스
    INDEX idx_user_id (user_id) COMMENT '사용자별 프로젝트 조회 최적화',
    INDEX idx_doc_type_id (doc_type_id) COMMENT '타입별 프로젝트 조회 최적화',
    INDEX idx_status (status) COMMENT '상태별 필터링 최적화'
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci
  COMMENT='사용자 프로젝트 (분석 세션)';

-- ============================================================================
-- 4️⃣ Pages Table (페이지 정보)
-- ============================================================================
-- 설명: 프로젝트 내 개별 페이지 (이미지 파일)
-- 주요 기능: 페이지 순서 관리, 분석 상태 추적, 이미지 저장
-- ============================================================================
CREATE TABLE pages (
    -- 기본 정보
    page_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '페이지 고유 ID',
    project_id INT NOT NULL COMMENT '소속 프로젝트 ID',
    page_number INT NOT NULL COMMENT '페이지 번호 (1부터 시작)',
    
    -- 이미지 정보
    image_path VARCHAR(500) NOT NULL COMMENT '이미지 파일 경로',
    image_width INT DEFAULT NULL COMMENT '이미지 너비 (픽셀)',
    image_height INT DEFAULT NULL COMMENT '이미지 높이 (픽셀)',
    
    -- 분석 상태
    analysis_status ENUM('pending', 'processing', 'completed', 'error') DEFAULT 'pending' 
        COMMENT '분석 상태',
    processing_time FLOAT DEFAULT NULL COMMENT '처리 시간 (초)',
    
    -- 타임스탬프
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '페이지 추가일',
    analyzed_at TIMESTAMP NULL DEFAULT NULL COMMENT '분석 완료일',
    
    -- 외래키
    FOREIGN KEY (project_id) REFERENCES projects(project_id) 
        ON DELETE CASCADE 
        COMMENT '프로젝트 삭제 시 페이지도 삭제',
    
    -- 제약조건 및 인덱스
    UNIQUE KEY uk_project_page (project_id, page_number) 
        COMMENT '프로젝트 내 페이지 번호 중복 방지',
    INDEX idx_project_id (project_id) COMMENT '프로젝트별 페이지 조회 최적화',
    INDEX idx_analysis_status (analysis_status) COMMENT '상태별 필터링 최적화'
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci
  COMMENT='프로젝트 내 페이지 정보';

-- ============================================================================
-- 5️⃣ Layout_Elements Table (레이아웃 요소)
-- ============================================================================
-- 설명: AI 모델이 검출한 레이아웃 요소 (제목, 본문, 그림 등)
-- 주요 기능: 바운딩 박스 저장, 클래스 분류, 좌표 관리
-- ============================================================================
CREATE TABLE layout_elements (
    -- 기본 정보
    element_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '요소 고유 ID',
    page_id INT NOT NULL COMMENT '소속 페이지 ID',
    
    -- 분류 정보
    class_name VARCHAR(100) NOT NULL COMMENT '클래스명 (question_number/figure/table/text 등)',
    confidence FLOAT NOT NULL COMMENT '신뢰도 (0.0~1.0)',
    
    -- 바운딩 박스 좌표
    bbox_x INT NOT NULL COMMENT 'X 좌표 (왼쪽 상단)',
    bbox_y INT NOT NULL COMMENT 'Y 좌표 (왼쪽 상단)',
    bbox_width INT NOT NULL COMMENT '너비 (픽셀)',
    bbox_height INT NOT NULL COMMENT '높이 (픽셀)',
    
    -- 자동 계산 컬럼 (GENERATED COLUMN)
    area INT GENERATED ALWAYS AS (bbox_width * bbox_height) STORED 
        COMMENT '면적 (자동 계산)',
    y_position INT GENERATED ALWAYS AS (bbox_y) STORED 
        COMMENT 'Y 정렬용 좌표 (자동 계산)',
    x_position INT GENERATED ALWAYS AS (bbox_x) STORED 
        COMMENT 'X 정렬용 좌표 (자동 계산)',
    
    -- 정렬 정보
    order_index INT DEFAULT 0 COMMENT '정렬 순서 (수동 지정 가능)',
    
    -- 타임스탬프
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    
    -- 외래키
    FOREIGN KEY (page_id) REFERENCES pages(page_id) 
        ON DELETE CASCADE 
        COMMENT '페이지 삭제 시 요소도 삭제',
    
    -- 인덱스
    INDEX idx_page_id (page_id) COMMENT '페이지별 요소 조회 최적화',
    INDEX idx_class_name (class_name) COMMENT '클래스별 필터링 최적화',
    INDEX idx_position (page_id, y_position, x_position) 
        COMMENT '좌표 기반 정렬 최적화 (복합 인덱스)'
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci
  COMMENT='AI가 검출한 레이아웃 요소 (바운딩 박스)';

-- ============================================================================
-- 6️⃣ Text_Contents Table (OCR 결과)
-- ============================================================================
-- 설명: 레이아웃 요소에서 추출한 텍스트 (OCR 결과)
-- 주요 기능: OCR 텍스트 저장, 언어 감지, 전문 검색
-- 관계: layout_elements와 1:1 관계
-- ============================================================================
CREATE TABLE text_contents (
    -- 기본 정보
    text_id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'OCR 결과 고유 ID',
    element_id INT NOT NULL COMMENT '레이아웃 요소 ID (1:1 매핑)',
    
    -- OCR 결과
    ocr_text TEXT NOT NULL COMMENT 'OCR 추출 텍스트',
    ocr_engine VARCHAR(50) DEFAULT 'PaddleOCR' COMMENT '사용한 OCR 엔진',
    ocr_confidence FLOAT DEFAULT NULL COMMENT 'OCR 신뢰도 (0.0~1.0)',
    language VARCHAR(10) DEFAULT 'ko' COMMENT '언어 코드 (ko/en/ja/zh)',
    
    -- 타임스탬프
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    
    -- 외래키
    FOREIGN KEY (element_id) REFERENCES layout_elements(element_id) 
        ON DELETE CASCADE 
        COMMENT '요소 삭제 시 OCR 결과도 삭제',
    
    -- 제약조건 및 인덱스
    UNIQUE KEY uk_element (element_id) COMMENT '1:1 관계 보장 (중복 방지)',
    INDEX idx_language (language) COMMENT '언어별 필터링 최적화',
    FULLTEXT INDEX ft_ocr_text (ocr_text) WITH PARSER ngram 
        COMMENT '한글/영문 전문 검색 (n-gram 파서 사용)'
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci
  COMMENT='OCR 추출 텍스트';

-- ============================================================================
-- 7️⃣ AI_Descriptions Table (AI 설명)
-- ============================================================================
-- 설명: 그림/표에 대한 AI 생성 설명 (GPT-4o-mini)
-- 주요 기능: 시각 자료 텍스트 설명, 프롬프트 이력 관리
-- 관계: layout_elements와 1:1 관계
-- ============================================================================
CREATE TABLE ai_descriptions (
    -- 기본 정보
    ai_desc_id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'AI 설명 고유 ID',
    element_id INT NOT NULL COMMENT '레이아웃 요소 ID (1:1 매핑)',
    
    -- AI 생성 결과
    description TEXT NOT NULL COMMENT 'AI가 생성한 설명 텍스트',
    ai_model VARCHAR(100) DEFAULT 'gpt-4o-mini' COMMENT '사용한 AI 모델명',
    prompt_used TEXT DEFAULT NULL COMMENT '사용한 프롬프트 (디버깅용)',
    
    -- 타임스탬프
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    
    -- 외래키
    FOREIGN KEY (element_id) REFERENCES layout_elements(element_id) 
        ON DELETE CASCADE 
        COMMENT '요소 삭제 시 AI 설명도 삭제',
    
    -- 제약조건 및 인덱스
    UNIQUE KEY uk_element (element_id) COMMENT '1:1 관계 보장 (중복 방지)',
    INDEX idx_ai_model (ai_model) COMMENT '모델별 필터링 최적화'
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci
  COMMENT='그림/표에 대한 AI 생성 설명';

-- ============================================================================
-- 8️⃣ Question_Groups Table (문제 그룹)
-- ============================================================================
-- 설명: 문제지에서 감지된 문제 단위 (문제 1, 문제 2, ...)
-- 주요 기능: 문제 번호 관리, Y좌표 범위 저장, 요소 카운트
-- 관계: pages와 1:N 관계
-- ============================================================================
CREATE TABLE question_groups (
    -- 기본 정보
    question_group_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '문제 그룹 고유 ID',
    page_id INT NOT NULL COMMENT '소속 페이지 ID',
    question_number INT NOT NULL COMMENT '문제 번호 (1, 2, 3, ...)',
    
    -- Y좌표 범위
    start_y INT NOT NULL COMMENT '문제 시작 Y좌표',
    end_y INT NOT NULL COMMENT '문제 종료 Y좌표',
    
    -- 통계 정보
    element_count INT DEFAULT 0 COMMENT '문제에 속한 요소 개수',
    
    -- 타임스탬프
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    
    -- 외래키
    FOREIGN KEY (page_id) REFERENCES pages(page_id) 
        ON DELETE CASCADE 
        COMMENT '페이지 삭제 시 문제 그룹도 삭제',
    
    -- 제약조건 및 인덱스
    UNIQUE KEY uk_page_question (page_id, question_number) 
        COMMENT '페이지 내 문제 번호 중복 방지',
    INDEX idx_page_id (page_id) COMMENT '페이지별 문제 조회 최적화'
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci
  COMMENT='문제지의 문제 그룹 (문제 단위)';

-- ============================================================================
-- 9️⃣ Question_Elements Table (문제-요소 매핑)
-- ============================================================================
-- 설명: 문제 그룹과 레이아웃 요소의 매핑 테이블 (N:N → 1:N)
-- 주요 기능: 문제별 요소 그룹핑, 순서 관리
-- 관계: question_groups ← N:1 → layout_elements
-- ============================================================================
CREATE TABLE question_elements (
    -- 기본 정보
    qe_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '매핑 레코드 고유 ID',
    question_group_id INT NOT NULL COMMENT '문제 그룹 ID',
    element_id INT NOT NULL COMMENT '레이아웃 요소 ID',
    
    -- 순서 정보
    order_in_question INT NOT NULL COMMENT '문제 내 요소 순서 (1, 2, 3, ...)',
    
    -- 타임스탬프
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    
    -- 외래키
    FOREIGN KEY (question_group_id) REFERENCES question_groups(question_group_id) 
        ON DELETE CASCADE 
        COMMENT '문제 그룹 삭제 시 매핑도 삭제',
    FOREIGN KEY (element_id) REFERENCES layout_elements(element_id) 
        ON DELETE CASCADE 
        COMMENT '요소 삭제 시 매핑도 삭제',
    
    -- 제약조건 및 인덱스
    UNIQUE KEY uk_question_element (question_group_id, element_id) 
        COMMENT '문제-요소 중복 매핑 방지',
    INDEX idx_order (question_group_id, order_in_question) 
        COMMENT '순서별 정렬 최적화 (복합 인덱스)'
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci
  COMMENT='문제-요소 매핑 테이블';

-- ============================================================================
-- 🔟 Text_Versions Table (텍스트 버전 관리)
-- ============================================================================
-- 설명: 페이지별 텍스트 버전 이력 (원본/자동포맷/사용자수정)
-- 주요 기능: 버전 관리, 수정 이력 추적, 현재 버전 플래그
-- 관계: pages와 1:N 관계
-- ============================================================================
CREATE TABLE text_versions (
    -- 기본 정보
    version_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '버전 고유 ID',
    page_id INT NOT NULL COMMENT '소속 페이지 ID',
    user_id INT DEFAULT NULL COMMENT '수정한 사용자 ID (사용자 수정 시)',
    
    -- 버전 정보
    content TEXT NOT NULL COMMENT '텍스트 내용',
    version_number INT NOT NULL COMMENT '버전 번호 (1, 2, 3, ...)',
    version_type ENUM('original', 'auto_formatted', 'user_edited') NOT NULL 
        COMMENT '버전 유형: original(원본), auto_formatted(자동포맷), user_edited(사용자수정)',
    
    -- 상태 플래그
    is_current BOOLEAN DEFAULT FALSE COMMENT '현재 버전 여부 (TRUE: 현재 버전)',
    
    -- 타임스탬프
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '버전 생성일',
    
    -- 외래키
    FOREIGN KEY (page_id) REFERENCES pages(page_id) 
        ON DELETE CASCADE 
        COMMENT '페이지 삭제 시 모든 버전 삭제',
    FOREIGN KEY (user_id) REFERENCES users(user_id) 
        ON DELETE SET NULL 
        COMMENT '사용자 삭제 시 user_id는 NULL로 변경',
    
    -- 제약조건 및 인덱스
    UNIQUE KEY uk_page_version (page_id, version_number) 
        COMMENT '페이지 내 버전 번호 중복 방지',
    INDEX idx_page_id (page_id) COMMENT '페이지별 버전 조회 최적화',
    INDEX idx_is_current (is_current) COMMENT '현재 버전 빠른 조회'
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci
  COMMENT='페이지별 텍스트 버전 관리';

-- ============================================================================
-- 1️⃣1️⃣ Formatting_Rules Table (포맷팅 규칙)
-- ============================================================================
-- 설명: 문서 타입별 클래스별 포맷팅 규칙 (접두사/접미사/들여쓰기)
-- 주요 기능: 자동 포맷팅 규칙 관리, 동적 규칙 변경
-- 관계: document_types와 1:N 관계
-- ============================================================================
CREATE TABLE formatting_rules (
    -- 기본 정보
    rule_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '규칙 고유 ID',
    doc_type_id INT NOT NULL COMMENT '문서 타입 ID',
    class_name VARCHAR(100) NOT NULL COMMENT '적용 클래스명 (question_number/figure/text 등)',
    
    -- 포맷팅 설정
    prefix VARCHAR(50) DEFAULT '' COMMENT '접두사 (예: "\\n\\n", "   ")',
    suffix VARCHAR(50) DEFAULT '' COMMENT '접미사 (예: ". ", "\\n")',
    indent_level INT DEFAULT 0 COMMENT '들여쓰기 레벨 (0~10)',
    
    -- 스타일 설정 (선택 사항)
    font_size VARCHAR(20) DEFAULT NULL COMMENT '폰트 크기 (예: "14pt")',
    font_weight VARCHAR(20) DEFAULT NULL COMMENT '폰트 두께 (예: "bold")',
    
    -- 타임스탬프
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '규칙 생성일',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '규칙 수정일',
    
    -- 외래키
    FOREIGN KEY (doc_type_id) REFERENCES document_types(doc_type_id) 
        ON DELETE CASCADE 
        COMMENT '타입 삭제 시 규칙도 삭제',
    
    -- 제약조건 및 인덱스
    UNIQUE KEY uk_type_class (doc_type_id, class_name) 
        COMMENT '타입별 클래스 규칙 중복 방지',
    INDEX idx_doc_type_id (doc_type_id) COMMENT '타입별 규칙 조회 최적화'
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci
  COMMENT='문서 타입별 포맷팅 규칙';

-- ============================================================================
-- 1️⃣2️⃣ Combined_Results Table (통합 문서 캐시)
-- ============================================================================
-- 설명: 프로젝트의 모든 페이지를 통합한 최종 결과 캐시
-- 주요 기능: 통합 텍스트 저장, 통계 정보, 다운로드 최적화
-- 관계: projects와 1:1 관계
-- ============================================================================
CREATE TABLE combined_results (
    -- 기본 정보
    combined_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '통합 결과 고유 ID',
    project_id INT NOT NULL COMMENT '프로젝트 ID (1:1 매핑)',
    
    -- 통합 결과
    combined_text LONGTEXT NOT NULL COMMENT '통합된 전체 텍스트 (페이지별 결과 합침)',
    combined_stats JSON DEFAULT NULL COMMENT '통계 정보 (JSON 형식: 페이지수, 단어수, 문제수 등)',
    
    -- 타임스탬프
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '최초 생성일',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '마지막 업데이트일',
    
    -- 외래키
    FOREIGN KEY (project_id) REFERENCES projects(project_id) 
        ON DELETE CASCADE 
        COMMENT '프로젝트 삭제 시 캐시도 삭제',
    
    -- 제약조건 및 인덱스
    UNIQUE KEY uk_project (project_id) COMMENT '1:1 관계 보장 (프로젝트당 1개 캐시)',
    INDEX idx_project_id (project_id) COMMENT '프로젝트별 캐시 조회 최적화'
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci
  COMMENT='프로젝트 통합 문서 캐시';

-- ============================================================================
-- 🔧 Triggers (트리거)
-- ============================================================================
-- 트리거 1: 페이지 추가 시 projects.total_pages 자동 증가
-- ============================================================================
DELIMITER //

CREATE TRIGGER trg_update_total_pages
AFTER INSERT ON pages
FOR EACH ROW
BEGIN
    -- 새 페이지가 추가되면 해당 프로젝트의 total_pages를 1 증가
    UPDATE projects 
    SET total_pages = (
        SELECT COUNT(*) 
        FROM pages 
        WHERE project_id = NEW.project_id
    )
    WHERE project_id = NEW.project_id;
END//

DELIMITER ;

-- ============================================================================
-- 🔧 Triggers (선택 사항: 페이지 삭제 시 total_pages 감소)
-- ============================================================================
DELIMITER //

CREATE TRIGGER trg_update_total_pages_on_delete
AFTER DELETE ON pages
FOR EACH ROW
BEGIN
    -- 페이지가 삭제되면 해당 프로젝트의 total_pages를 재계산
    UPDATE projects 
    SET total_pages = (
        SELECT COUNT(*) 
        FROM pages 
        WHERE project_id = OLD.project_id
    )
    WHERE project_id = OLD.project_id;
END//

DELIMITER ;

-- ============================================================================
-- 📊 Initial Data (초기 데이터)
-- ============================================================================
-- 시스템 기본 설정 데이터 삽입
-- ============================================================================

-- 1. Document Types (문서 타입 2개)
INSERT INTO document_types (type_name, model_name, sorting_method, description) VALUES
('worksheet', 'SmartEyeSsen', 'question_based', '시험 문제지 - 문제 번호 기준 정렬'),
('document', 'DocLayout-YOLO', 'coordinate_based', '일반 문서 - Y/X 좌표 기준 정렬');

-- 2. Formatting Rules (문제지 전용 규칙)
-- worksheet (doc_type_id = 1) 포맷팅 규칙
INSERT INTO formatting_rules (doc_type_id, class_name, prefix, suffix, indent_level, font_size, font_weight) VALUES
-- 문제 번호
(1, 'question_number', '\n\n', '. ', 0, '14pt', 'bold'),

-- 문제 텍스트
(1, 'question_text', '   ', '\n', 3, NULL, NULL),

-- 선택지
(1, 'choices', '   ', '\n', 3, NULL, NULL),

-- 지문
(1, 'passage', '\n   [지문]\n   ', '\n', 3, NULL, NULL),

-- 그림
(1, 'figure', '\n   [그림 설명]\n   ', '\n', 3, NULL, NULL),

-- 표
(1, 'table', '\n   [표]\n   ', '\n', 3, NULL, NULL),

-- 제목
(1, 'title', '', '\n\n', 0, '16pt', 'bold'),

-- 단원명
(1, 'unit', '\n', '\n\n', 0, '14pt', 'bold'),

-- 문제 유형
(1, 'question_type', '\n[', ']\n', 0, NULL, NULL),

-- 목록
(1, 'list', '   • ', '\n', 3, NULL, NULL),

-- 페이지 번호
(1, 'page', '\n\n─────────────────────\n페이지 ', '\n─────────────────────\n\n', 0, '10pt', NULL);

-- 3. Formatting Rules (일반 문서 전용 규칙)
-- document (doc_type_id = 2) 포맷팅 규칙
INSERT INTO formatting_rules (doc_type_id, class_name, prefix, suffix, indent_level, font_size, font_weight) VALUES
-- 제목
(2, 'title', '', '\n\n', 0, '18pt', 'bold'),

-- 소제목
(2, 'heading', '\n', '\n\n', 0, '16pt', 'bold'),

-- 본문 텍스트
(2, 'plain_text', '', '\n\n', 0, NULL, NULL),

-- 그림
(2, 'figure', '\n[그림 ', ']\n\n', 0, NULL, NULL),

-- 그림 캡션
(2, 'figure_caption', '', '\n', 2, '10pt', NULL),

-- 표
(2, 'table', '\n[표 ', ']\n\n', 0, NULL, NULL),

-- 표 캡션
(2, 'table_caption', '', '\n', 2, '10pt', NULL),

-- 표 각주
(2, 'table_footnote', '\n* ', '\n', 2, '9pt', NULL),

-- 수식
(2, 'isolate_formula', '\n', '\n\n', 2, NULL, NULL),

-- 수식 캡션
(2, 'formula_caption', '', '\n', 2, '10pt', NULL);

-- ============================================================================
-- 🎉 데이터베이스 생성 완료!
-- ============================================================================
-- 다음 단계:
-- 1. MySQL Workbench에서 erd_schema.sql 파일 실행
-- 2. 테이블 생성 확인: SHOW TABLES;
-- 3. 초기 데이터 확인: 
--    - SELECT * FROM document_types;
--    - SELECT * FROM formatting_rules;
-- 4. 백엔드 ORM 연결 (SQLAlchemy/Flask-SQLAlchemy)
-- 5. API 개발 시작
-- ============================================================================