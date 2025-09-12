package com.smarteye.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 교육 문서의 개별 요소 정보 DTO
 * LayoutBlock과 TextBlock의 정보를 통합한 형태
 */
public class EducationalElement {
    
    @JsonProperty("layout_block_id")
    private Long layoutBlockId;
    
    @JsonProperty("original_class_name")
    private String originalClassName; // LAM에서 감지한 원본 클래스
    
    @JsonProperty("refined_type")
    private String refinedType; // TSPM으로 세분화된 타입 (choices, passage 등)
    
    @JsonProperty("extracted_text")
    private String extractedText; // OCR 추출된 텍스트
    
    @JsonProperty("ai_description")
    private String aiDescription; // AI 생성 설명
    
    @JsonProperty("position")
    private Position position; // 위치 정보
    
    @JsonProperty("confidence")
    private ConfidenceInfo confidence; // 신뢰도 정보
    
    @JsonProperty("text_patterns")
    private TextPatterns textPatterns; // 텍스트 패턴 분석
    
    // 기본 생성자
    public EducationalElement() {
        this.position = new Position();
        this.confidence = new ConfidenceInfo();
        this.textPatterns = new TextPatterns();
    }
    
    // 생성자
    public EducationalElement(Long layoutBlockId, String originalClassName, String extractedText) {
        this();
        this.layoutBlockId = layoutBlockId;
        this.originalClassName = originalClassName;
        this.extractedText = extractedText;
        this.refinedType = originalClassName; // 기본값
    }
    
    // Getters and Setters
    public Long getLayoutBlockId() { return layoutBlockId; }
    public void setLayoutBlockId(Long layoutBlockId) { this.layoutBlockId = layoutBlockId; }
    
    public String getOriginalClassName() { return originalClassName; }
    public void setOriginalClassName(String originalClassName) { this.originalClassName = originalClassName; }
    
    public String getRefinedType() { return refinedType; }
    public void setRefinedType(String refinedType) { this.refinedType = refinedType; }
    
    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
    
    public String getAiDescription() { return aiDescription; }
    public void setAiDescription(String aiDescription) { this.aiDescription = aiDescription; }
    
    public Position getPosition() { return position; }
    public void setPosition(Position position) { this.position = position; }
    
    public ConfidenceInfo getConfidence() { return confidence; }
    public void setConfidence(ConfidenceInfo confidence) { this.confidence = confidence; }
    
    public TextPatterns getTextPatterns() { return textPatterns; }
    public void setTextPatterns(TextPatterns textPatterns) { this.textPatterns = textPatterns; }
    
    /**
     * 위치 정보
     */
    public static class Position {
        @JsonProperty("x1")
        private int x1;
        
        @JsonProperty("y1")
        private int y1;
        
        @JsonProperty("x2")
        private int x2;
        
        @JsonProperty("y2")
        private int y2;
        
        @JsonProperty("width")
        private int width;
        
        @JsonProperty("height")
        private int height;
        
        @JsonProperty("area")
        private int area;
        
        // 기본 생성자
        public Position() {}
        
        // 생성자
        public Position(int x1, int y1, int x2, int y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.width = Math.abs(x2 - x1);
            this.height = Math.abs(y2 - y1);
            this.area = this.width * this.height;
        }
        
        // Getters and Setters
        public int getX1() { return x1; }
        public void setX1(int x1) { this.x1 = x1; }
        
        public int getY1() { return y1; }
        public void setY1(int y1) { this.y1 = y1; }
        
        public int getX2() { return x2; }
        public void setX2(int x2) { this.x2 = x2; }
        
        public int getY2() { return y2; }
        public void setY2(int y2) { this.y2 = y2; }
        
        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }
        
        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
        
        public int getArea() { return area; }
        public void setArea(int area) { this.area = area; }
    }
    
    /**
     * 신뢰도 정보
     */
    public static class ConfidenceInfo {
        @JsonProperty("layout_confidence")
        private double layoutConfidence; // LAM 분석 신뢰도
        
        @JsonProperty("ocr_confidence")
        private double ocrConfidence; // OCR 신뢰도
        
        @JsonProperty("tspm_confidence")
        private double tspmConfidence; // TSPM 할당 신뢰도
        
        // 기본 생성자
        public ConfidenceInfo() {}
        
        // 생성자
        public ConfidenceInfo(double layoutConfidence, double ocrConfidence) {
            this.layoutConfidence = layoutConfidence;
            this.ocrConfidence = ocrConfidence;
            this.tspmConfidence = 1.0; // 기본값
        }
        
        // Getters and Setters
        public double getLayoutConfidence() { return layoutConfidence; }
        public void setLayoutConfidence(double layoutConfidence) { this.layoutConfidence = layoutConfidence; }
        
        public double getOcrConfidence() { return ocrConfidence; }
        public void setOcrConfidence(double ocrConfidence) { this.ocrConfidence = ocrConfidence; }
        
        public double getTspmConfidence() { return tspmConfidence; }
        public void setTspmConfidence(double tspmConfidence) { this.tspmConfidence = tspmConfidence; }
    }
    
    /**
     * 텍스트 패턴 분석 결과
     */
    public static class TextPatterns {
        @JsonProperty("is_choice_pattern")
        private boolean isChoicePattern; // ①②③④ 패턴
        
        @JsonProperty("is_question_number_pattern")
        private boolean isQuestionNumberPattern; // 1번, 2번 패턴
        
        @JsonProperty("detected_patterns")
        private String[] detectedPatterns; // 감지된 패턴들
        
        @JsonProperty("text_classification")
        private String textClassification; // choices, passage, explanation 등
        
        // 기본 생성자
        public TextPatterns() {
            this.detectedPatterns = new String[0];
        }
        
        // Getters and Setters
        public boolean isChoicePattern() { return isChoicePattern; }
        public void setChoicePattern(boolean choicePattern) { isChoicePattern = choicePattern; }
        
        public boolean isQuestionNumberPattern() { return isQuestionNumberPattern; }
        public void setQuestionNumberPattern(boolean questionNumberPattern) { isQuestionNumberPattern = questionNumberPattern; }
        
        public String[] getDetectedPatterns() { return detectedPatterns; }
        public void setDetectedPatterns(String[] detectedPatterns) { this.detectedPatterns = detectedPatterns; }
        
        public String getTextClassification() { return textClassification; }
        public void setTextClassification(String textClassification) { this.textClassification = textClassification; }
    }
    
    @Override
    public String toString() {
        return "EducationalElement{" +
                "layoutBlockId=" + layoutBlockId +
                ", originalClassName='" + originalClassName + '\'' +
                ", refinedType='" + refinedType + '\'' +
                ", extractedText='" + (extractedText != null ? extractedText.substring(0, Math.min(50, extractedText.length())) : "") + "...'" +
                ", position=" + position.y1 +
                '}';
    }
}