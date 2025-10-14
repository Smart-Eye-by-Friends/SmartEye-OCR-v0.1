package com.smarteye.application.analysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 문제의 콘텐츠를 타입별로 세분화한 구조
 * 제안 A: Granular Object Structure 적용
 * 
 * OCR 결과와 AI 설명을 question_text에 병합하지 않고 분리하여 관리
 * 
 * @version 1.0
 * @since 2025-10-11
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuestionContentDTO {
    
    /**
     * 핵심 질문 텍스트 (문제 번호 제외)
     * 예: "수를 두 가지 방법으로 읽어 보시오."
     */
    @JsonProperty("question_text")
    private String questionText;
    
    /**
     * 일반 텍스트 블록들 (보기, 설명 등)
     */
    @JsonProperty("plain_text")
    private List<String> plainText = new ArrayList<>();
    
    /**
     * OCR로 추출된 텍스트들 (숫자, 단어 등)
     * question_text와 분리하여 관리
     */
    @JsonProperty("ocr_results")
    private List<OcrResult> ocrResults = new ArrayList<>();
    
    /**
     * AI가 생성한 이미지/도형 설명들
     * question_text와 분리하여 관리
     */
    @JsonProperty("ai_descriptions")
    private List<AiDescription> aiDescriptions = new ArrayList<>();
    
    /**
     * 독해 지문 (passage 타입)
     */
    @JsonProperty("passage")
    private String passage;
    
    /**
     * 선택지 목록 (①, ②, ③ 등)
     */
    @JsonProperty("choices")
    private List<String> choices = new ArrayList<>();
    
    /**
     * 이미지 상세 정보
     */
    @JsonProperty("images")
    private List<ImageDetail> images = new ArrayList<>();
    
    /**
     * 표 데이터
     */
    @JsonProperty("tables")
    private List<TableDetail> tables = new ArrayList<>();
    
    // Constructors
    public QuestionContentDTO() {}
    
    // Getters and Setters
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    
    public List<String> getPlainText() { return plainText; }
    public void setPlainText(List<String> plainText) { this.plainText = plainText; }
    
    public List<OcrResult> getOcrResults() { return ocrResults; }
    public void setOcrResults(List<OcrResult> ocrResults) { this.ocrResults = ocrResults; }
    
    public List<AiDescription> getAiDescriptions() { return aiDescriptions; }
    public void setAiDescriptions(List<AiDescription> aiDescriptions) { this.aiDescriptions = aiDescriptions; }
    
    public String getPassage() { return passage; }
    public void setPassage(String passage) { this.passage = passage; }
    
    public List<String> getChoices() { return choices; }
    public void setChoices(List<String> choices) { this.choices = choices; }
    
    public List<ImageDetail> getImages() { return images; }
    public void setImages(List<ImageDetail> images) { this.images = images; }
    
    public List<TableDetail> getTables() { return tables; }
    public void setTables(List<TableDetail> tables) { this.tables = tables; }
    
    /**
     * OCR 결과 상세 정보
     */
    public static class OcrResult {
        @JsonProperty("text")
        private String text;
        
        @JsonProperty("element_id")
        private String elementId;
        
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("bbox")
        private Map<String, Double> bbox;
        
        @JsonProperty("confidence")
        private Double confidence;
        
        // Constructors
        public OcrResult() {}
        
        // Getters and Setters
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        
        public String getElementId() { return elementId; }
        public void setElementId(String elementId) { this.elementId = elementId; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public Map<String, Double> getBbox() { return bbox; }
        public void setBbox(Map<String, Double> bbox) { this.bbox = bbox; }
        
        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }
    }
    
    /**
     * AI 설명 상세 정보
     */
    public static class AiDescription {
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("element_id")
        private String elementId;
        
        @JsonProperty("element_type")
        private String elementType;
        
        @JsonProperty("bbox")
        private Map<String, Double> bbox;
        
        // Constructors
        public AiDescription() {}
        
        // Getters and Setters
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getElementId() { return elementId; }
        public void setElementId(String elementId) { this.elementId = elementId; }
        
        public String getElementType() { return elementType; }
        public void setElementType(String elementType) { this.elementType = elementType; }
        
        public Map<String, Double> getBbox() { return bbox; }
        public void setBbox(Map<String, Double> bbox) { this.bbox = bbox; }
    }
    
    /**
     * 이미지 상세 정보
     */
    public static class ImageDetail {
        @JsonProperty("element_id")
        private String elementId;
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("bbox")
        private Map<String, Double> bbox;
        
        @JsonProperty("confidence")
        private Double confidence;
        
        // Constructors
        public ImageDetail() {}
        
        // Getters and Setters
        public String getElementId() { return elementId; }
        public void setElementId(String elementId) { this.elementId = elementId; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public Map<String, Double> getBbox() { return bbox; }
        public void setBbox(Map<String, Double> bbox) { this.bbox = bbox; }
        
        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }
    }
    
    /**
     * 표 상세 정보
     */
    public static class TableDetail {
        @JsonProperty("element_id")
        private String elementId;
        
        @JsonProperty("data")
        private List<List<String>> data;
        
        @JsonProperty("bbox")
        private Map<String, Double> bbox;
        
        // Constructors
        public TableDetail() {}
        
        // Getters and Setters
        public String getElementId() { return elementId; }
        public void setElementId(String elementId) { this.elementId = elementId; }
        
        public List<List<String>> getData() { return data; }
        public void setData(List<List<String>> data) { this.data = data; }
        
        public Map<String, Double> getBbox() { return bbox; }
        public void setBbox(Map<String, Double> bbox) { this.bbox = bbox; }
    }
}
