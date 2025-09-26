package com.smarteye.domain.document;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.smarteye.domain.analysis.AnalysisJob;
import com.smarteye.domain.analysis.LayoutBlock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "document_pages")
@EntityListeners(AuditingEntityListener.class)
public class DocumentPage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "page_number", nullable = false)
    private Integer pageNumber;
    
    @Column(name = "image_path", nullable = false, length = 500)
    private String imagePath;
    
    @Column(name = "image_width")
    private Integer imageWidth;
    
    @Column(name = "image_height")
    private Integer imageHeight;
    
    @Column(name = "analysis_result", columnDefinition = "TEXT")
    private String analysisResult; // JSON format
    
    @Column(name = "layout_visualization_path", length = 500)
    private String layoutVisualizationPath;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false)
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;
    
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_job_id", nullable = false)
    private AnalysisJob analysisJob;
    
    @OneToMany(mappedBy = "documentPage", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LayoutBlock> layoutBlocks = new ArrayList<>();
    
    public enum ProcessingStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
    
    // Constructors
    public DocumentPage() {}
    
    public DocumentPage(Integer pageNumber, String imagePath) {
        this.pageNumber = pageNumber;
        this.imagePath = imagePath;
    }
    
    public DocumentPage(Integer pageNumber, String imagePath, AnalysisJob analysisJob) {
        this.pageNumber = pageNumber;
        this.imagePath = imagePath;
        this.analysisJob = analysisJob;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
    
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    
    public Integer getImageWidth() { return imageWidth; }
    public void setImageWidth(Integer imageWidth) { this.imageWidth = imageWidth; }
    
    public Integer getImageHeight() { return imageHeight; }
    public void setImageHeight(Integer imageHeight) { this.imageHeight = imageHeight; }
    
    public String getAnalysisResult() { return analysisResult; }
    public void setAnalysisResult(String analysisResult) { this.analysisResult = analysisResult; }
    
    public String getLayoutVisualizationPath() { return layoutVisualizationPath; }
    public void setLayoutVisualizationPath(String layoutVisualizationPath) { this.layoutVisualizationPath = layoutVisualizationPath; }
    
    public ProcessingStatus getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(ProcessingStatus processingStatus) { this.processingStatus = processingStatus; }
    
    public Long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public AnalysisJob getAnalysisJob() { return analysisJob; }
    public void setAnalysisJob(AnalysisJob analysisJob) { this.analysisJob = analysisJob; }
    
    public List<LayoutBlock> getLayoutBlocks() { return layoutBlocks; }
    public void setLayoutBlocks(List<LayoutBlock> layoutBlocks) { this.layoutBlocks = layoutBlocks; }
    
    // Helper methods
    public void addLayoutBlock(LayoutBlock layoutBlock) {
        layoutBlocks.add(layoutBlock);
        layoutBlock.setDocumentPage(this);
    }
    
    public void removeLayoutBlock(LayoutBlock layoutBlock) {
        layoutBlocks.remove(layoutBlock);
        layoutBlock.setDocumentPage(null);
    }
    
    public boolean isCompleted() {
        return processingStatus == ProcessingStatus.COMPLETED;
    }
    
    public boolean isFailed() {
        return processingStatus == ProcessingStatus.FAILED;
    }
    
    public boolean isProcessing() {
        return processingStatus == ProcessingStatus.PROCESSING;
    }
    
    @Override
    public String toString() {
        return "DocumentPage{" +
                "id=" + id +
                ", pageNumber=" + pageNumber +
                ", imagePath='" + imagePath + '\'' +
                ", processingStatus=" + processingStatus +
                ", processingTimeMs=" + processingTimeMs +
                ", createdAt=" + createdAt +
                '}';
    }
}