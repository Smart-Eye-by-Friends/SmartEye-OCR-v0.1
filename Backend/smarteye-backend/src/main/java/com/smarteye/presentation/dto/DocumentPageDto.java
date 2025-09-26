package com.smarteye.presentation.dto;

import com.smarteye.domain.document.DocumentPage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "문서 페이지 DTO")
public class DocumentPageDto {
    
    @Schema(description = "페이지 ID", example = "1")
    private Long id;
    
    @Schema(description = "페이지 번호", example = "1")
    private Integer pageNumber;
    
    @Schema(description = "이미지 경로", example = "/uploads/pages/page_1.jpg")
    private String imagePath;
    
    @Schema(description = "이미지 너비", example = "1024")
    private Integer imageWidth;
    
    @Schema(description = "이미지 높이", example = "768")
    private Integer imageHeight;
    
    @Schema(description = "분석 결과 JSON")
    private String analysisResult;
    
    @Schema(description = "레이아웃 시각화 이미지 경로")
    private String layoutVisualizationPath;
    
    @Schema(description = "처리 상태", example = "COMPLETED")
    private DocumentPage.ProcessingStatus processingStatus;
    
    @Schema(description = "처리 시간 (ms)", example = "2500")
    private Long processingTimeMs;
    
    @Schema(description = "오류 메시지")
    private String errorMessage;
    
    @Schema(description = "생성 일시")
    private LocalDateTime createdAt;
    
    @Schema(description = "수정 일시")
    private LocalDateTime updatedAt;
    
    @Schema(description = "분석 작업 ID", example = "1")
    private Long analysisJobId;
    
    // Constructors
    public DocumentPageDto() {}
    
    public DocumentPageDto(DocumentPage page) {
        this.id = page.getId();
        this.pageNumber = page.getPageNumber();
        this.imagePath = page.getImagePath();
        this.imageWidth = page.getImageWidth();
        this.imageHeight = page.getImageHeight();
        this.analysisResult = page.getAnalysisResult();
        this.layoutVisualizationPath = page.getLayoutVisualizationPath();
        this.processingStatus = page.getProcessingStatus();
        this.processingTimeMs = page.getProcessingTimeMs();
        this.errorMessage = page.getErrorMessage();
        this.createdAt = page.getCreatedAt();
        this.updatedAt = page.getUpdatedAt();
        
        if (page.getAnalysisJob() != null) {
            this.analysisJobId = page.getAnalysisJob().getId();
        }
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
    
    public DocumentPage.ProcessingStatus getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(DocumentPage.ProcessingStatus processingStatus) { this.processingStatus = processingStatus; }
    
    public Long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Long getAnalysisJobId() { return analysisJobId; }
    public void setAnalysisJobId(Long analysisJobId) { this.analysisJobId = analysisJobId; }
    
    // Helper methods
    public boolean isCompleted() {
        return processingStatus == DocumentPage.ProcessingStatus.COMPLETED;
    }
    
    public boolean isFailed() {
        return processingStatus == DocumentPage.ProcessingStatus.FAILED;
    }
    
    public boolean isProcessing() {
        return processingStatus == DocumentPage.ProcessingStatus.PROCESSING;
    }
    
    public String getFormattedProcessingTime() {
        if (processingTimeMs == null) return "Unknown";
        
        if (processingTimeMs < 1000) {
            return processingTimeMs + " ms";
        } else {
            return String.format("%.2f s", processingTimeMs / 1000.0);
        }
    }
    
    @Override
    public String toString() {
        return "DocumentPageDto{" +
                "id=" + id +
                ", pageNumber=" + pageNumber +
                ", processingStatus=" + processingStatus +
                ", processingTimeMs=" + processingTimeMs +
                ", analysisJobId=" + analysisJobId +
                '}';
    }
}