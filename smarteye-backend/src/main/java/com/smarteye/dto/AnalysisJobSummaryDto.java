package com.smarteye.dto;

import com.smarteye.entity.AnalysisJob;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "분석 작업 요약 DTO")
public class AnalysisJobSummaryDto {
    
    @Schema(description = "작업 ID", example = "1")
    private Long id;
    
    @Schema(description = "작업 UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String jobId;
    
    @Schema(description = "원본 파일명", example = "math_worksheet_page1.pdf")
    private String originalFilename;
    
    @Schema(description = "파일 타입", example = "PDF")
    private String fileType;
    
    @Schema(description = "작업 상태", example = "COMPLETED")
    private AnalysisJob.JobStatus status;
    
    @Schema(description = "책 내 순서", example = "1")
    private Integer sequenceInBook;
    
    @Schema(description = "진행률 (%)", example = "100")
    private Integer progressPercentage;
    
    @Schema(description = "문서 페이지 수", example = "5")
    private Integer documentPagesCount;
    
    @Schema(description = "생성 일시")
    private LocalDateTime createdAt;
    
    @Schema(description = "완료 일시")
    private LocalDateTime completedAt;
    
    // Constructors
    public AnalysisJobSummaryDto() {}
    
    public AnalysisJobSummaryDto(AnalysisJob job) {
        this.id = job.getId();
        this.jobId = job.getJobId();
        this.originalFilename = job.getOriginalFilename();
        this.fileType = job.getFileType();
        this.status = job.getStatus();
        this.sequenceInBook = job.getSequenceInBook();
        this.progressPercentage = job.getProgressPercentage();
        this.createdAt = job.getCreatedAt();
        this.completedAt = job.getCompletedAt();
        
        if (job.getDocumentPages() != null) {
            this.documentPagesCount = job.getDocumentPages().size();
        } else {
            this.documentPagesCount = 0;
        }
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    
    public AnalysisJob.JobStatus getStatus() { return status; }
    public void setStatus(AnalysisJob.JobStatus status) { this.status = status; }
    
    public Integer getSequenceInBook() { return sequenceInBook; }
    public void setSequenceInBook(Integer sequenceInBook) { this.sequenceInBook = sequenceInBook; }
    
    public Integer getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(Integer progressPercentage) { this.progressPercentage = progressPercentage; }
    
    public Integer getDocumentPagesCount() { return documentPagesCount; }
    public void setDocumentPagesCount(Integer documentPagesCount) { this.documentPagesCount = documentPagesCount; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    
    // Helper methods
    public boolean isCompleted() {
        return status == AnalysisJob.JobStatus.COMPLETED;
    }
    
    public boolean isFailed() {
        return status == AnalysisJob.JobStatus.FAILED;
    }
    
    public boolean isProcessing() {
        return status == AnalysisJob.JobStatus.PROCESSING;
    }
    
    @Override
    public String toString() {
        return "AnalysisJobSummaryDto{" +
                "id=" + id +
                ", jobId='" + jobId + '\'' +
                ", originalFilename='" + originalFilename + '\'' +
                ", status=" + status +
                ", sequenceInBook=" + sequenceInBook +
                ", progressPercentage=" + progressPercentage +
                ", documentPagesCount=" + documentPagesCount +
                '}';
    }
}