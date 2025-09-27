package com.smarteye.presentation.dto;

import com.smarteye.domain.analysis.entity.AnalysisJob;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Schema(description = "분석 작업 상세 DTO")
public class AnalysisJobDto {
    
    @Schema(description = "작업 ID", example = "1")
    private Long id;
    
    @Schema(description = "작업 UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String jobId;
    
    @Schema(description = "원본 파일명", example = "math_worksheet_page1.pdf")
    private String originalFilename;
    
    @Schema(description = "파일 경로", example = "/uploads/2024/01/file.pdf")
    private String filePath;
    
    @Schema(description = "파일 크기 (bytes)", example = "1024000")
    private Long fileSize;
    
    @Schema(description = "파일 타입", example = "PDF")
    private String fileType;
    
    @Schema(description = "작업 상태", example = "COMPLETED")
    private AnalysisJob.JobStatus status;
    
    @Schema(description = "모델 선택", example = "SmartEyeSsen")
    private String modelChoice;
    
    @Schema(description = "AI 설명 사용 여부", example = "true")
    private Boolean useAiDescription;
    
    @Schema(description = "진행률 (%)", example = "100")
    private Integer progressPercentage;
    
    @Schema(description = "오류 메시지")
    private String errorMessage;
    
    @Schema(description = "책 내 순서", example = "1")
    private Integer sequenceInBook;
    
    @Schema(description = "생성 일시")
    private LocalDateTime createdAt;
    
    @Schema(description = "수정 일시")
    private LocalDateTime updatedAt;
    
    @Schema(description = "완료 일시")
    private LocalDateTime completedAt;
    
    @Schema(description = "사용자 ID", example = "1")
    private Long userId;
    
    @Schema(description = "책 ID", example = "1")
    private Long bookId;
    
    @Schema(description = "문서 페이지 목록")
    private List<DocumentPageDto> documentPages;
    
    // Constructors
    public AnalysisJobDto() {}
    
    public AnalysisJobDto(AnalysisJob job) {
        this.id = job.getId();
        this.jobId = job.getJobId();
        this.originalFilename = job.getOriginalFilename();
        this.filePath = job.getFilePath();
        this.fileSize = job.getFileSize();
        this.fileType = job.getFileType();
        this.status = job.getStatus();
        this.modelChoice = job.getModelChoice();
        this.useAiDescription = job.getUseAiDescription();
        this.progressPercentage = job.getProgressPercentage();
        this.errorMessage = job.getErrorMessage();
        this.sequenceInBook = job.getSequenceInBook();
        this.createdAt = job.getCreatedAt();
        this.updatedAt = job.getUpdatedAt();
        this.completedAt = job.getCompletedAt();
        
        if (job.getUser() != null) {
            this.userId = job.getUser().getId();
        }
        
        if (job.getBook() != null) {
            this.bookId = job.getBook().getId();
        }
        
        if (job.getDocumentPages() != null) {
            this.documentPages = job.getDocumentPages().stream()
                    .map(DocumentPageDto::new)
                    .collect(Collectors.toList());
        }
    }
    
    public AnalysisJobDto(AnalysisJob job, boolean includeDocumentPages) {
        this(job);
        if (!includeDocumentPages) {
            this.documentPages = null;
        }
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    
    public AnalysisJob.JobStatus getStatus() { return status; }
    public void setStatus(AnalysisJob.JobStatus status) { this.status = status; }
    
    public String getModelChoice() { return modelChoice; }
    public void setModelChoice(String modelChoice) { this.modelChoice = modelChoice; }
    
    public Boolean getUseAiDescription() { return useAiDescription; }
    public void setUseAiDescription(Boolean useAiDescription) { this.useAiDescription = useAiDescription; }
    
    public Integer getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(Integer progressPercentage) { this.progressPercentage = progressPercentage; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public Integer getSequenceInBook() { return sequenceInBook; }
    public void setSequenceInBook(Integer sequenceInBook) { this.sequenceInBook = sequenceInBook; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    
    public List<DocumentPageDto> getDocumentPages() { return documentPages; }
    public void setDocumentPages(List<DocumentPageDto> documentPages) { this.documentPages = documentPages; }
    
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
    
    public int getDocumentPagesCount() {
        return documentPages != null ? documentPages.size() : 0;
    }
    
    public String getFormattedFileSize() {
        if (fileSize == null) return "Unknown";
        
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }
    
    @Override
    public String toString() {
        return "AnalysisJobDto{" +
                "id=" + id +
                ", jobId='" + jobId + '\'' +
                ", originalFilename='" + originalFilename + '\'' +
                ", status=" + status +
                ", sequenceInBook=" + sequenceInBook +
                ", progressPercentage=" + progressPercentage +
                ", bookId=" + bookId +
                ", documentPagesCount=" + getDocumentPagesCount() +
                '}';
    }
    
    // Static factory methods
    public static AnalysisJobDto fromEntity(AnalysisJob job) {
        return new AnalysisJobDto(job);
    }
    
    public static AnalysisJobDto fromEntityWithoutPages(AnalysisJob job) {
        return new AnalysisJobDto(job, false);
    }
}