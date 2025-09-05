package com.smarteye.dto;

import java.util.List;

/**
 * 이미지 설명 DTO - 문제 내 이미지 정보
 */
public class ImageDescription {
    
    private List<Integer> bbox;
    private String description;
    private Double confidence;
    
    public ImageDescription() {}
    
    public ImageDescription(List<Integer> bbox, String description, Double confidence) {
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
        return "ImageDescription{" +
                "bbox=" + bbox +
                ", description='" + description + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}