package com.smarteye.dto;

import java.util.List;

/**
 * 표 설명 DTO - 문제 내 표 정보
 */
public class TableDescription {
    
    private List<Integer> bbox;
    private String description;
    private Double confidence;
    
    public TableDescription() {}
    
    public TableDescription(List<Integer> bbox, String description, Double confidence) {
        this.bbox = bbox;
        this.description = description;
        this.confidence = confidence;
    }
    
    // Getters and Setters
    public List<Integer> getBbox() {
        return bbox;
    }
    
    public void setBbox(List<Integer> bbox) {
        this.bbox = bbox;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Double getConfidence() {
        return confidence;
    }
    
    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }
    
    @Override
    public String toString() {
        return "TableDescription{" +
                "bbox=" + bbox +
                ", description='" + description + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}