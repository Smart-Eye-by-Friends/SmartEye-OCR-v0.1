package com.smarteye.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.ArrayList;

/**
 * AI 분석 결과 DTO - 문제별 AI 분석 정보
 */
public class AIAnalysis {
    
    @JsonProperty("image_descriptions")
    private List<AIDescriptionResult> imageDescriptions = new ArrayList<>();
    
    @JsonProperty("table_analysis")
    private List<AIDescriptionResult> tableAnalysis = new ArrayList<>();
    
    @JsonProperty("problem_analysis")
    private List<AIDescriptionResult> problemAnalysis = new ArrayList<>();
    
    public AIAnalysis() {}
    
    public AIAnalysis(List<AIDescriptionResult> imageDescriptions, 
                     List<AIDescriptionResult> tableAnalysis, 
                     List<AIDescriptionResult> problemAnalysis) {
        this.imageDescriptions = imageDescriptions != null ? imageDescriptions : new ArrayList<>();
        this.tableAnalysis = tableAnalysis != null ? tableAnalysis : new ArrayList<>();
        this.problemAnalysis = problemAnalysis != null ? problemAnalysis : new ArrayList<>();
    }
    
    // Getters and Setters
    public List<AIDescriptionResult> getImageDescriptions() {
        return imageDescriptions;
    }
    
    public void setImageDescriptions(List<AIDescriptionResult> imageDescriptions) {
        this.imageDescriptions = imageDescriptions != null ? imageDescriptions : new ArrayList<>();
    }
    
    public List<AIDescriptionResult> getTableAnalysis() {
        return tableAnalysis;
    }
    
    public void setTableAnalysis(List<AIDescriptionResult> tableAnalysis) {
        this.tableAnalysis = tableAnalysis != null ? tableAnalysis : new ArrayList<>();
    }
    
    public List<AIDescriptionResult> getProblemAnalysis() {
        return problemAnalysis;
    }
    
    public void setProblemAnalysis(List<AIDescriptionResult> problemAnalysis) {
        this.problemAnalysis = problemAnalysis != null ? problemAnalysis : new ArrayList<>();
    }
    
    @Override
    public String toString() {
        return "AIAnalysis{" +
                "imageDescriptions=" + imageDescriptions +
                ", tableAnalysis=" + tableAnalysis +
                ", problemAnalysis=" + problemAnalysis +
                '}';
    }
}