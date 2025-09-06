package com.smarteye.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "cim_outputs")
@EntityListeners(AuditingEntityListener.class)
public class CIMOutput {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "cim_data", columnDefinition = "TEXT", nullable = false)
    private String cimData; // JSON format
    
    @Column(name = "formatted_text", columnDefinition = "TEXT")
    private String formattedText;
    
    @Column(name = "word_document_path", length = 500)
    private String wordDocumentPath;
    
    @Column(name = "json_file_path", length = 500)
    private String jsonFilePath;
    
    @Column(name = "layout_visualization_path", length = 500)
    private String layoutVisualizationPath;
    
    @Column(name = "text_visualization_path", length = 500)
    private String textVisualizationPath;
    
    @Column(name = "total_elements")
    private Integer totalElements;
    
    @Column(name = "text_elements")
    private Integer textElements;
    
    @Column(name = "ai_described_elements")
    private Integer aiDescribedElements;
    
    @Column(name = "total_figures")
    private Integer totalFigures;
    
    @Column(name = "total_tables")
    private Integer totalTables;
    
    @Column(name = "total_word_count")
    private Integer totalWordCount;
    
    @Column(name = "total_char_count")
    private Integer totalCharCount;
    
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "generation_status", nullable = false)
    private GenerationStatus generationStatus = GenerationStatus.PENDING;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    // 구조화 분석 관련 필드들 (CIM 기능 Java 이식)
    @Column(name = "structured_data", columnDefinition = "TEXT")
    private String structuredDataJson;  // Python structured_result와 동일 구조
    
    @Column(name = "structured_text", columnDefinition = "TEXT")  
    private String structuredText;      // 읽기 쉬운 포맷팅 텍스트
    
    @Column(name = "total_questions")
    private Integer totalQuestions;     // 감지된 총 문제 수
    
    @Column(name = "layout_type")
    private String layoutType;          // simple, sectioned, multiple_choice, standard
    
    @Column(name = "question_structure", columnDefinition = "TEXT")
    private String questionStructureJson; // 문제별 구조 정보
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_job_id", nullable = false)
    private AnalysisJob analysisJob;
    
    // 새로운 관계 매핑 (구조화 분석용)
    @OneToMany(mappedBy = "cimOutput", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuestionStructure> questionStructures = new ArrayList<>();
    
    @OneToMany(mappedBy = "cimOutput", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AIQuestionMapping> aiQuestionMappings = new ArrayList<>();
    
    public enum GenerationStatus {
        PENDING,
        GENERATING_JSON,
        GENERATING_FORMATTED_TEXT,
        GENERATING_WORD_DOCUMENT,
        COMPLETED,
        FAILED
    }
    
    // Constructors
    public CIMOutput() {}
    
    public CIMOutput(String cimData) {
        this.cimData = cimData;
    }
    
    public CIMOutput(String cimData, AnalysisJob analysisJob) {
        this.cimData = cimData;
        this.analysisJob = analysisJob;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getCimData() { return cimData; }
    public void setCimData(String cimData) { this.cimData = cimData; }
    
    public String getFormattedText() { return formattedText; }
    public void setFormattedText(String formattedText) { this.formattedText = formattedText; }
    
    public String getWordDocumentPath() { return wordDocumentPath; }
    public void setWordDocumentPath(String wordDocumentPath) { this.wordDocumentPath = wordDocumentPath; }
    
    public String getJsonFilePath() { return jsonFilePath; }
    public void setJsonFilePath(String jsonFilePath) { this.jsonFilePath = jsonFilePath; }
    
    public String getLayoutVisualizationPath() { return layoutVisualizationPath; }
    public void setLayoutVisualizationPath(String layoutVisualizationPath) { this.layoutVisualizationPath = layoutVisualizationPath; }
    
    public String getTextVisualizationPath() { return textVisualizationPath; }
    public void setTextVisualizationPath(String textVisualizationPath) { this.textVisualizationPath = textVisualizationPath; }
    
    public Integer getTotalElements() { return totalElements; }
    public void setTotalElements(Integer totalElements) { this.totalElements = totalElements; }
    
    public Integer getTextElements() { return textElements; }
    public void setTextElements(Integer textElements) { this.textElements = textElements; }
    
    public Integer getAiDescribedElements() { return aiDescribedElements; }
    public void setAiDescribedElements(Integer aiDescribedElements) { this.aiDescribedElements = aiDescribedElements; }
    
    public Integer getTotalFigures() { return totalFigures; }
    public void setTotalFigures(Integer totalFigures) { this.totalFigures = totalFigures; }
    
    public Integer getTotalTables() { return totalTables; }
    public void setTotalTables(Integer totalTables) { this.totalTables = totalTables; }
    
    public Integer getTotalWordCount() { return totalWordCount; }
    public void setTotalWordCount(Integer totalWordCount) { this.totalWordCount = totalWordCount; }
    
    public Integer getTotalCharCount() { return totalCharCount; }
    public void setTotalCharCount(Integer totalCharCount) { this.totalCharCount = totalCharCount; }
    
    public Long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    
    public GenerationStatus getGenerationStatus() { return generationStatus; }
    public void setGenerationStatus(GenerationStatus generationStatus) { this.generationStatus = generationStatus; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public AnalysisJob getAnalysisJob() { return analysisJob; }
    public void setAnalysisJob(AnalysisJob analysisJob) { this.analysisJob = analysisJob; }
    
    // 구조화 분석 필드 Getter/Setter
    public String getStructuredDataJson() { return structuredDataJson; }
    public void setStructuredDataJson(String structuredDataJson) { this.structuredDataJson = structuredDataJson; }
    
    public String getStructuredText() { return structuredText; }
    public void setStructuredText(String structuredText) { this.structuredText = structuredText; }
    
    public Integer getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(Integer totalQuestions) { this.totalQuestions = totalQuestions; }
    
    public String getLayoutType() { return layoutType; }
    public void setLayoutType(String layoutType) { this.layoutType = layoutType; }
    
    public String getQuestionStructureJson() { return questionStructureJson; }
    public void setQuestionStructureJson(String questionStructureJson) { this.questionStructureJson = questionStructureJson; }
    
    public List<QuestionStructure> getQuestionStructures() { return questionStructures; }
    public void setQuestionStructures(List<QuestionStructure> questionStructures) { this.questionStructures = questionStructures; }
    
    public List<AIQuestionMapping> getAiQuestionMappings() { return aiQuestionMappings; }
    public void setAiQuestionMappings(List<AIQuestionMapping> aiQuestionMappings) { this.aiQuestionMappings = aiQuestionMappings; }
    
    // Helper methods
    public boolean isCompleted() {
        return generationStatus == GenerationStatus.COMPLETED;
    }
    
    public boolean isFailed() {
        return generationStatus == GenerationStatus.FAILED;
    }
    
    public boolean isGenerating() {
        return generationStatus == GenerationStatus.GENERATING_JSON ||
               generationStatus == GenerationStatus.GENERATING_FORMATTED_TEXT ||
               generationStatus == GenerationStatus.GENERATING_WORD_DOCUMENT;
    }
    
    public boolean hasWordDocument() {
        return wordDocumentPath != null && !wordDocumentPath.trim().isEmpty();
    }
    
    public boolean hasFormattedText() {
        return formattedText != null && !formattedText.trim().isEmpty();
    }
    
    public boolean hasJsonFile() {
        return jsonFilePath != null && !jsonFilePath.trim().isEmpty();
    }
    
    public boolean hasStructuredData() {
        return structuredDataJson != null && !structuredDataJson.trim().isEmpty();
    }
    
    public boolean hasStructuredText() {
        return structuredText != null && !structuredText.trim().isEmpty();
    }
    
    public boolean hasQuestionStructure() {
        return questionStructureJson != null && !questionStructureJson.trim().isEmpty();
    }
    
    public boolean isMultipleChoiceLayout() {
        return "multiple_choice".equals(layoutType);
    }
    
    public boolean isSectionedLayout() {
        return "sectioned".equals(layoutType);
    }
    
    public double getTextExtractionRate() {
        if (totalElements == null || totalElements == 0) return 0.0;
        if (textElements == null) return 0.0;
        return (double) textElements / totalElements;
    }
    
    public double getAiDescriptionRate() {
        if (totalElements == null || totalElements == 0) return 0.0;
        if (aiDescribedElements == null) return 0.0;
        return (double) aiDescribedElements / totalElements;
    }
    
    public void calculateStatistics() {
        // This method will be called after all processing is complete
        // to calculate final statistics from related entities
        if (analysisJob != null && analysisJob.getDocumentPages() != null) {
            int totalElements = 0;
            int textElements = 0;
            int aiDescribedElements = 0;
            int totalFigures = 0;
            int totalTables = 0;
            int totalWordCount = 0;
            int totalCharCount = 0;
            
            for (DocumentPage page : analysisJob.getDocumentPages()) {
                if (page.getLayoutBlocks() != null) {
                    totalElements += page.getLayoutBlocks().size();
                    
                    for (LayoutBlock block : page.getLayoutBlocks()) {
                        if (block.hasOcrText()) {
                            textElements++;
                            if (block.getTextBlock() != null) {
                                totalWordCount += block.getTextBlock().getWordCount() != null ? 
                                    block.getTextBlock().getWordCount() : 0;
                                totalCharCount += block.getTextBlock().getCharCount() != null ? 
                                    block.getTextBlock().getCharCount() : 0;
                            }
                        }
                        if (block.hasAiDescription()) {
                            aiDescribedElements++;
                        }
                        if (block.isImageClass()) {
                            totalFigures++;
                        }
                        if (block.isTableClass()) {
                            totalTables++;
                        }
                    }
                }
            }
            
            this.totalElements = totalElements;
            this.textElements = textElements;
            this.aiDescribedElements = aiDescribedElements;
            this.totalFigures = totalFigures;
            this.totalTables = totalTables;
            this.totalWordCount = totalWordCount;
            this.totalCharCount = totalCharCount;
        }
    }
    
    @Override
    public String toString() {
        return "CIMOutput{" +
                "id=" + id +
                ", totalElements=" + totalElements +
                ", textElements=" + textElements +
                ", aiDescribedElements=" + aiDescribedElements +
                ", generationStatus=" + generationStatus +
                ", processingTimeMs=" + processingTimeMs +
                ", createdAt=" + createdAt +
                '}';
    }
}