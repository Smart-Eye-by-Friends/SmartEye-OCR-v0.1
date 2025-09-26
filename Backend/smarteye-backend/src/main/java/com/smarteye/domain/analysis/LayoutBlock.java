package com.smarteye.domain.analysis;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.smarteye.domain.document.DocumentPage;

import java.time.LocalDateTime;

@Entity
@Table(name = "layout_blocks")
@EntityListeners(AuditingEntityListener.class)
public class LayoutBlock {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "block_index", nullable = false)
    private Integer blockIndex;
    
    @Column(name = "class_name", nullable = false, length = 100)
    private String className;
    
    @Column(name = "confidence", nullable = false)
    private Double confidence;
    
    @Column(name = "x1", nullable = false)
    private Integer x1;
    
    @Column(name = "y1", nullable = false)
    private Integer y1;
    
    @Column(name = "x2", nullable = false)
    private Integer x2;
    
    @Column(name = "y2", nullable = false)
    private Integer y2;
    
    @Column(name = "width", nullable = false)
    private Integer width;
    
    @Column(name = "height", nullable = false)
    private Integer height;
    
    @Column(name = "area", nullable = false)
    private Integer area;
    
    @Column(name = "ocr_text", columnDefinition = "TEXT")
    private String ocrText;
    
    @Column(name = "ocr_confidence")
    private Double ocrConfidence;
    
    @Column(name = "ai_description", columnDefinition = "TEXT")
    private String aiDescription;
    
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
    @JoinColumn(name = "document_page_id", nullable = false)
    private DocumentPage documentPage;
    
    @OneToOne(mappedBy = "layoutBlock", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private TextBlock textBlock;
    
    public enum ProcessingStatus {
        PENDING,
        LAYOUT_DETECTED,
        OCR_COMPLETED,
        AI_COMPLETED,
        FAILED
    }
    
    // Constructors
    public LayoutBlock() {}
    
    public LayoutBlock(Integer blockIndex, String className, Double confidence, 
                      Integer x1, Integer y1, Integer x2, Integer y2) {
        this.blockIndex = blockIndex;
        this.className = className;
        this.confidence = confidence;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.width = x2 - x1;
        this.height = y2 - y1;
        this.area = this.width * this.height;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Integer getBlockIndex() { return blockIndex; }
    public void setBlockIndex(Integer blockIndex) { this.blockIndex = blockIndex; }
    
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    
    public Integer getX1() { return x1; }
    public void setX1(Integer x1) { 
        this.x1 = x1; 
        updateDimensions();
    }
    
    public Integer getY1() { return y1; }
    public void setY1(Integer y1) { 
        this.y1 = y1; 
        updateDimensions();
    }
    
    public Integer getX2() { return x2; }
    public void setX2(Integer x2) { 
        this.x2 = x2; 
        updateDimensions();
    }
    
    public Integer getY2() { return y2; }
    public void setY2(Integer y2) { 
        this.y2 = y2; 
        updateDimensions();
    }
    
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    
    public Integer getArea() { return area; }
    public void setArea(Integer area) { this.area = area; }
    
    public String getOcrText() { return ocrText; }
    public void setOcrText(String ocrText) { this.ocrText = ocrText; }
    
    public Double getOcrConfidence() { return ocrConfidence; }
    public void setOcrConfidence(Double ocrConfidence) { this.ocrConfidence = ocrConfidence; }
    
    public String getAiDescription() { return aiDescription; }
    public void setAiDescription(String aiDescription) { this.aiDescription = aiDescription; }
    
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
    
    public DocumentPage getDocumentPage() { return documentPage; }
    public void setDocumentPage(DocumentPage documentPage) { this.documentPage = documentPage; }
    
    public TextBlock getTextBlock() { return textBlock; }
    public void setTextBlock(TextBlock textBlock) { this.textBlock = textBlock; }
    
    // Helper methods
    private void updateDimensions() {
        if (x1 != null && y1 != null && x2 != null && y2 != null) {
            this.width = Math.abs(x2 - x1);
            this.height = Math.abs(y2 - y1);
            this.area = this.width * this.height;
        }
    }
    
    public boolean isTextClass() {
        if (className == null) return false;
        String lowerClassName = className.toLowerCase();
        return lowerClassName.contains("text") || 
               lowerClassName.contains("title") || 
               lowerClassName.contains("question") ||
               lowerClassName.contains("caption") ||
               lowerClassName.contains("list");
    }
    
    public boolean isImageClass() {
        if (className == null) return false;
        String lowerClassName = className.toLowerCase();
        return lowerClassName.contains("figure") || 
               lowerClassName.contains("image") ||
               lowerClassName.contains("photo");
    }
    
    public boolean isTableClass() {
        if (className == null) return false;
        return className.toLowerCase().contains("table");
    }
    
    public boolean hasOcrText() {
        return ocrText != null && !ocrText.trim().isEmpty();
    }
    
    public boolean hasAiDescription() {
        return aiDescription != null && !aiDescription.trim().isEmpty();
    }
    
    public boolean isProcessingComplete() {
        return processingStatus == ProcessingStatus.OCR_COMPLETED || 
               processingStatus == ProcessingStatus.AI_COMPLETED;
    }
    
    @Override
    public String toString() {
        return "LayoutBlock{" +
                "id=" + id +
                ", blockIndex=" + blockIndex +
                ", className='" + className + '\'' +
                ", confidence=" + confidence +
                ", bounds=[" + x1 + "," + y1 + "," + x2 + "," + y2 + "]" +
                ", area=" + area +
                ", processingStatus=" + processingStatus +
                '}';
    }
}