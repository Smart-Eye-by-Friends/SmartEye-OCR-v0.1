package com.smarteye.presentation.dto;

import java.util.Map;
import java.util.HashMap;

/**
 * AI 설명 결과 DTO
 * Python api_server.py의 AI 결과 구조와 동일
 */
public class AIDescriptionResult {
    
    private int id;
    private String className;
    private int[] coordinates; // [x1, y1, x2, y2]
    private String description;

    // 프론트엔드 호환을 위한 추가 필드들
    private String elementType; // element_type
    private double confidence; // AI 분석 신뢰도
    private String extractedText; // extracted_text
    private Map<String, Object> analysisMetadata; // analysis_metadata

    public AIDescriptionResult() {
        this.analysisMetadata = new HashMap<>();
        this.confidence = 1.0; // 기본값
    }

    public AIDescriptionResult(int id, String className, int[] coordinates, String description) {
        this.id = id;
        this.className = className;
        this.coordinates = coordinates;
        this.description = description;
        this.elementType = className; // 기본값으로 className 사용
        this.confidence = 1.0;
        this.extractedText = "";
        this.analysisMetadata = new HashMap<>();
    }

    public AIDescriptionResult(int id, String className, int[] coordinates, String description,
                              String elementType, double confidence, String extractedText,
                              Map<String, Object> analysisMetadata) {
        this.id = id;
        this.className = className;
        this.coordinates = coordinates;
        this.description = description;
        this.elementType = elementType;
        this.confidence = confidence;
        this.extractedText = extractedText;
        this.analysisMetadata = analysisMetadata != null ? analysisMetadata : new HashMap<>();
    }
    
    // Getters and Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getClassName() {
        return className;
    }
    
    public void setClassName(String className) {
        this.className = className;
    }
    
    public int[] getCoordinates() {
        return coordinates;
    }
    
    public void setCoordinates(int[] coordinates) {
        this.coordinates = coordinates;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }

    // 새로운 필드들의 getter/setter
    public String getElementType() {
        return elementType;
    }

    public void setElementType(String elementType) {
        this.elementType = elementType;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public Map<String, Object> getAnalysisMetadata() {
        return analysisMetadata;
    }

    public void setAnalysisMetadata(Map<String, Object> analysisMetadata) {
        this.analysisMetadata = analysisMetadata;
    }

    // Additional compatibility methods
    public String getType() {
        return elementType;
    }

    public void setType(String type) {
        this.elementType = type;
    }

    public Map<String, Object> getMetadata() {
        return analysisMetadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.analysisMetadata = metadata;
    }

    // 프론트엔드에서 기대하는 snake_case 필드들
    @com.fasterxml.jackson.annotation.JsonProperty("element_type")
    public String getElement_type() {
        return elementType;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("extracted_text")
    public String getExtracted_text() {
        return extractedText;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("analysis_metadata")
    public Map<String, Object> getAnalysis_metadata() {
        return analysisMetadata;
    }
    
    @Override
    public String toString() {
        return "AIDescriptionResult{" +
                "id=" + id +
                ", className='" + className + '\'' +
                ", coordinates=" + java.util.Arrays.toString(coordinates) +
                ", description='" + (description != null && description.length() > 100 ? description.substring(0, 100) + "..." : description) + '\'' +
                ", elementType='" + elementType + '\'' +
                ", confidence=" + confidence +
                ", extractedText='" + (extractedText != null && extractedText.length() > 50 ? extractedText.substring(0, 50) + "..." : extractedText) + '\'' +
                '}';
    }
}