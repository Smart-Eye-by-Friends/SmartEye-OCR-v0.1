package com.smarteye.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TSPM (Text Structure Pattern Matching) 분석 결과 DTO
 * 문제별로 정렬된 교육 문서 레이아웃 분석 결과
 */
public class TSPMResult {
    
    @JsonProperty("document_info")
    private DocumentInfo documentInfo;
    
    @JsonProperty("question_groups")
    private List<QuestionGroup> questionGroups;
    
    @JsonProperty("analysis_metadata")
    private AnalysisMetadata analysisMetadata;
    
    // 기본 생성자
    public TSPMResult() {
        this.questionGroups = new ArrayList<>();
        this.analysisMetadata = new AnalysisMetadata();
    }
    
    // 생성자
    public TSPMResult(DocumentInfo documentInfo, List<QuestionGroup> questionGroups) {
        this.documentInfo = documentInfo;
        this.questionGroups = questionGroups != null ? questionGroups : new ArrayList<>();
        this.analysisMetadata = new AnalysisMetadata();
    }
    
    // Getters and Setters
    public DocumentInfo getDocumentInfo() { return documentInfo; }
    public void setDocumentInfo(DocumentInfo documentInfo) { this.documentInfo = documentInfo; }
    
    public List<QuestionGroup> getQuestionGroups() { return questionGroups; }
    public void setQuestionGroups(List<QuestionGroup> questionGroups) { this.questionGroups = questionGroups; }
    
    public AnalysisMetadata getAnalysisMetadata() { return analysisMetadata; }
    public void setAnalysisMetadata(AnalysisMetadata analysisMetadata) { this.analysisMetadata = analysisMetadata; }
    
    /**
     * 문서 정보
     */
    public static class DocumentInfo {
        @JsonProperty("total_questions")
        private int totalQuestions;
        
        @JsonProperty("layout_type") 
        private String layoutType;
        
        @JsonProperty("sections")
        private Map<String, Object> sections;
        
        @JsonProperty("detected_patterns")
        private List<String> detectedPatterns;
        
        // 기본 생성자
        public DocumentInfo() {
            this.detectedPatterns = new ArrayList<>();
        }
        
        // Getters and Setters
        public int getTotalQuestions() { return totalQuestions; }
        public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }
        
        public String getLayoutType() { return layoutType; }
        public void setLayoutType(String layoutType) { this.layoutType = layoutType; }
        
        public Map<String, Object> getSections() { return sections; }
        public void setSections(Map<String, Object> sections) { this.sections = sections; }
        
        public List<String> getDetectedPatterns() { return detectedPatterns; }
        public void setDetectedPatterns(List<String> detectedPatterns) { this.detectedPatterns = detectedPatterns; }
    }
    
    /**
     * 분석 메타데이터
     */
    public static class AnalysisMetadata {
        @JsonProperty("analysis_timestamp")
        private LocalDateTime analysisTimestamp;
        
        @JsonProperty("processing_time_ms")
        private long processingTimeMs;
        
        @JsonProperty("algorithm_version")
        private String algorithmVersion = "TSPM-1.0";
        
        @JsonProperty("proximity_threshold")
        private int proximityThreshold = 500; // 500px
        
        // 기본 생성자
        public AnalysisMetadata() {
            this.analysisTimestamp = LocalDateTime.now();
        }
        
        // Getters and Setters
        public LocalDateTime getAnalysisTimestamp() { return analysisTimestamp; }
        public void setAnalysisTimestamp(LocalDateTime analysisTimestamp) { this.analysisTimestamp = analysisTimestamp; }
        
        public long getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
        
        public String getAlgorithmVersion() { return algorithmVersion; }
        public void setAlgorithmVersion(String algorithmVersion) { this.algorithmVersion = algorithmVersion; }
        
        public int getProximityThreshold() { return proximityThreshold; }
        public void setProximityThreshold(int proximityThreshold) { this.proximityThreshold = proximityThreshold; }
    }
    
    @Override
    public String toString() {
        return "TSPMResult{" +
                "documentInfo=" + documentInfo +
                ", questionGroups=" + (questionGroups != null ? questionGroups.size() : 0) + " groups" +
                ", analysisMetadata=" + analysisMetadata +
                '}';
    }
}