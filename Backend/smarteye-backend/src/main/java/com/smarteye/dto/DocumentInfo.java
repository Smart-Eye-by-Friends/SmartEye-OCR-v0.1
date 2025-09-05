package com.smarteye.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * 문서 정보 DTO - 구조화된 분석 결과의 문서 메타데이터
 */
public class DocumentInfo {
    
    @JsonProperty("total_questions")
    private Integer totalQuestions;
    
    @JsonProperty("layout_type")
    private String layoutType;
    
    private Map<String, Object> sections;
    
    public DocumentInfo() {}
    
    public DocumentInfo(Integer totalQuestions, String layoutType, Map<String, Object> sections) {
        this.totalQuestions = totalQuestions;
        this.layoutType = layoutType;
        this.sections = sections;
    }
    
    // Getters and Setters
    public Integer getTotalQuestions() {
        return totalQuestions;
    }
    
    public void setTotalQuestions(Integer totalQuestions) {
        this.totalQuestions = totalQuestions;
    }
    
    public String getLayoutType() {
        return layoutType;
    }
    
    public void setLayoutType(String layoutType) {
        this.layoutType = layoutType;
    }
    
    public Map<String, Object> getSections() {
        return sections;
    }
    
    public void setSections(Map<String, Object> sections) {
        this.sections = sections;
    }
    
    @Override
    public String toString() {
        return "DocumentInfo{" +
                "totalQuestions=" + totalQuestions +
                ", layoutType='" + layoutType + '\'' +
                ", sections=" + sections +
                '}';
    }
}