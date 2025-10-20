package com.smarteye.application.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Ground Truth 데이터 구조 (Phase 7 - E2E Testing)
 * 
 * <p>종단간 테스트를 위한 정답 데이터 구조입니다.
 * 테스트 이미지에 대한 예상 결과를 정의하여 정확도를 측정합니다.</p>
 * 
 * @version 2.0
 * @since 2025-01-20
 * @see AccuracyMetrics
 */
public class GroundTruth {
    
    /**
     * 이미지 식별자
     * <p>예: "sample_001", "sen_math_1-1_page_016"</p>
     */
    @JsonProperty("image_id")
    private String imageId;
    
    /**
     * 레이아웃 타입
     * <p>가능한 값:</p>
     * <ul>
     *   <li>"single_column" - 단일 컬럼</li>
     *   <li>"pure_2_columns" - 순수 2단</li>
     *   <li>"pure_3_columns" - 순수 3단</li>
     *   <li>"mixed_1col_top_2col_bottom" - 상단 1단 + 하단 2단 ⭐</li>
     *   <li>"mixed_2col_top_1col_bottom" - 상단 2단 + 하단 1단</li>
     *   <li>"mixed_center_leftright" - 중앙 + 좌우</li>
     *   <li>"asymmetric_columns" - 비대칭 컬럼</li>
     * </ul>
     */
    @JsonProperty("layout_type")
    private String layoutType;
    
    /**
     * 문제 목록 (Ground Truth)
     */
    @JsonProperty("questions")
    private List<QuestionGroundTruth> questions;
    
    /**
     * 메타데이터 (선택)
     */
    @JsonProperty("metadata")
    private Metadata metadata;
    
    // Constructors
    
    public GroundTruth() {}
    
    public GroundTruth(String imageId, String layoutType, List<QuestionGroundTruth> questions, Metadata metadata) {
        this.imageId = imageId;
        this.layoutType = layoutType;
        this.questions = questions;
        this.metadata = metadata;
    }
    
    // Getters and Setters
    
    public String getImageId() {
        return imageId;
    }
    
    public void setImageId(String imageId) {
        this.imageId = imageId;
    }
    
    public String getLayoutType() {
        return layoutType;
    }
    
    public void setLayoutType(String layoutType) {
        this.layoutType = layoutType;
    }
    
    public List<QuestionGroundTruth> getQuestions() {
        return questions;
    }
    
    public void setQuestions(List<QuestionGroundTruth> questions) {
        this.questions = questions;
    }
    
    public Metadata getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }
    
    /**
     * 문제별 Ground Truth
     */
    public static class QuestionGroundTruth {
        
        /**
         * 문제 식별자
         * <p>예: "001", "002", "294."</p>
         */
        @JsonProperty("identifier")
        private String identifier;
        
        /**
         * 문제 경계 X좌표
         */
        @JsonProperty("x")
        private int x;
        
        /**
         * 문제 경계 Y좌표
         */
        @JsonProperty("y")
        private int y;
        
        /**
         * 문제 경계 너비
         */
        @JsonProperty("width")
        private int width;
        
        /**
         * 문제 경계 높이
         */
        @JsonProperty("height")
        private int height;
        
        /**
         * 이 문제에 속해야 하는 요소 목록
         */
        @JsonProperty("elements")
        private List<ElementGroundTruth> elements;
        
        // Constructors
        
        public QuestionGroundTruth() {}
        
        public QuestionGroundTruth(String identifier, int x, int y, int width, int height, List<ElementGroundTruth> elements) {
            this.identifier = identifier;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.elements = elements;
        }
        
        // Getters and Setters
        
        public String getIdentifier() {
            return identifier;
        }
        
        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }
        
        public int getX() {
            return x;
        }
        
        public void setX(int x) {
            this.x = x;
        }
        
        public int getY() {
            return y;
        }
        
        public void setY(int y) {
            this.y = y;
        }
        
        public int getWidth() {
            return width;
        }
        
        public void setWidth(int width) {
            this.width = width;
        }
        
        public int getHeight() {
            return height;
        }
        
        public void setHeight(int height) {
            this.height = height;
        }
        
        public List<ElementGroundTruth> getElements() {
            return elements;
        }
        
        public void setElements(List<ElementGroundTruth> elements) {
            this.elements = elements;
        }
    }
    
    /**
     * 요소별 Ground Truth
     */
    public static class ElementGroundTruth {
        
        /**
         * 요소 타입
         * <p>예: "question_number", "question_text", "figure", "answer"</p>
         */
        @JsonProperty("type")
        private String type;
        
        /**
         * 요소 ID (LAM 결과의 ID 또는 임의 ID)
         * <p>예: "elem_001", "layout_12345"</p>
         */
        @JsonProperty("id")
        private String id;
        
        /**
         * 예상 할당 문제 ID
         * <p>예: "001", "002"</p>
         */
        @JsonProperty("expected_question_id")
        private String expectedQuestionId;
        
        /**
         * X좌표 (선택)
         */
        @JsonProperty("x")
        private Integer x;
        
        /**
         * Y좌표 (선택)
         */
        @JsonProperty("y")
        private Integer y;
        
        // Constructors
        
        public ElementGroundTruth() {}
        
        public ElementGroundTruth(String type, String id, String expectedQuestionId, Integer x, Integer y) {
            this.type = type;
            this.id = id;
            this.expectedQuestionId = expectedQuestionId;
            this.x = x;
            this.y = y;
        }
        
        // Getters and Setters
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public String getId() {
            return id;
        }
        
        public void setId(String id) {
            this.id = id;
        }
        
        public String getExpectedQuestionId() {
            return expectedQuestionId;
        }
        
        public void setExpectedQuestionId(String expectedQuestionId) {
            this.expectedQuestionId = expectedQuestionId;
        }
        
        public Integer getX() {
            return x;
        }
        
        public void setX(Integer x) {
            this.x = x;
        }
        
        public Integer getY() {
            return y;
        }
        
        public void setY(Integer y) {
            this.y = y;
        }
    }
    
    /**
     * 메타데이터
     */
    public static class Metadata {
        
        /**
         * 출처
         * <p>예: "쎈 수학 1-1", "개념원리 수학(상)"</p>
         */
        @JsonProperty("source")
        private String source;
        
        /**
         * 페이지 번호
         */
        @JsonProperty("page")
        private Integer page;
        
        /**
         * 총 문제 수
         */
        @JsonProperty("total_questions")
        private Integer totalQuestions;
        
        /**
         * 총 요소 수
         */
        @JsonProperty("total_elements")
        private Integer totalElements;
        
        /**
         * 작성자
         */
        @JsonProperty("author")
        private String author;
        
        /**
         * 작성일
         */
        @JsonProperty("created_at")
        private String createdAt;
        
        // Constructors
        
        public Metadata() {}
        
        public Metadata(String source, Integer page, Integer totalQuestions, Integer totalElements, String author, String createdAt) {
            this.source = source;
            this.page = page;
            this.totalQuestions = totalQuestions;
            this.totalElements = totalElements;
            this.author = author;
            this.createdAt = createdAt;
        }
        
        // Getters and Setters
        
        public String getSource() {
            return source;
        }
        
        public void setSource(String source) {
            this.source = source;
        }
        
        public Integer getPage() {
            return page;
        }
        
        public void setPage(Integer page) {
            this.page = page;
        }
        
        public Integer getTotalQuestions() {
            return totalQuestions;
        }
        
        public void setTotalQuestions(Integer totalQuestions) {
            this.totalQuestions = totalQuestions;
        }
        
        public Integer getTotalElements() {
            return totalElements;
        }
        
        public void setTotalElements(Integer totalElements) {
            this.totalElements = totalElements;
        }
        
        public String getAuthor() {
            return author;
        }
        
        public void setAuthor(String author) {
            this.author = author;
        }
        
        public String getCreatedAt() {
            return createdAt;
        }
        
        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }
}
