package com.smarteye.presentation.dto;

import com.smarteye.domain.book.Book;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Schema(description = "책 정보 DTO")
public class BookDto {
    
    @Schema(description = "책 ID", example = "1")
    private Long id;
    
    @Schema(description = "책 제목", example = "수학 문제집 1권")
    private String title;
    
    @Schema(description = "책 설명", example = "중학교 1학년 수학 문제집입니다.")
    private String description;
    
    @Schema(description = "책 상태", example = "ACTIVE")
    private Book.BookStatus status;
    
    @Schema(description = "전체 페이지 수", example = "50")
    private Integer totalPages;
    
    @Schema(description = "완료된 페이지 수", example = "25")
    private Integer completedPages;
    
    @Schema(description = "전체 분석 작업 수", example = "10")
    private Integer totalAnalysisJobs;
    
    @Schema(description = "완료된 분석 작업 수", example = "5")
    private Integer completedAnalysisJobs;
    
    @Schema(description = "진행률 (%)", example = "50.0")
    private Double progressPercentage;
    
    @Schema(description = "생성 일시")
    private LocalDateTime createdAt;
    
    @Schema(description = "수정 일시")
    private LocalDateTime updatedAt;
    
    @Schema(description = "사용자 ID", example = "1")
    private Long userId;
    
    @Schema(description = "사용자명", example = "john_doe")
    private String username;
    
    @Schema(description = "분석 작업 목록 (간략)")
    private List<AnalysisJobSummaryDto> analysisJobs;
    
    // Constructors
    public BookDto() {}
    
    public BookDto(Book book) {
        this.id = book.getId();
        this.title = book.getTitle();
        this.description = book.getDescription();
        this.status = book.getStatus();
        this.totalPages = book.getTotalPages();
        this.completedPages = book.getCompletedPages();
        this.totalAnalysisJobs = book.getTotalAnalysisJobs();
        this.completedAnalysisJobs = book.getCompletedAnalysisJobs();
        this.progressPercentage = book.getProgressPercentage();
        this.createdAt = book.getCreatedAt();
        this.updatedAt = book.getUpdatedAt();
        
        if (book.getUser() != null) {
            this.userId = book.getUser().getId();
            this.username = book.getUser().getUsername();
        }
        
        if (book.getAnalysisJobs() != null) {
            this.analysisJobs = book.getAnalysisJobs().stream()
                    .map(AnalysisJobSummaryDto::new)
                    .collect(Collectors.toList());
        }
    }
    
    public BookDto(Book book, boolean includeAnalysisJobs) {
        this(book);
        if (!includeAnalysisJobs) {
            this.analysisJobs = null;
        }
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Book.BookStatus getStatus() { return status; }
    public void setStatus(Book.BookStatus status) { this.status = status; }
    
    public Integer getTotalPages() { return totalPages; }
    public void setTotalPages(Integer totalPages) { this.totalPages = totalPages; }
    
    public Integer getCompletedPages() { return completedPages; }
    public void setCompletedPages(Integer completedPages) { this.completedPages = completedPages; }
    
    public Integer getTotalAnalysisJobs() { return totalAnalysisJobs; }
    public void setTotalAnalysisJobs(Integer totalAnalysisJobs) { this.totalAnalysisJobs = totalAnalysisJobs; }
    
    public Integer getCompletedAnalysisJobs() { return completedAnalysisJobs; }
    public void setCompletedAnalysisJobs(Integer completedAnalysisJobs) { this.completedAnalysisJobs = completedAnalysisJobs; }
    
    public Double getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(Double progressPercentage) { this.progressPercentage = progressPercentage; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public List<AnalysisJobSummaryDto> getAnalysisJobs() { return analysisJobs; }
    public void setAnalysisJobs(List<AnalysisJobSummaryDto> analysisJobs) { this.analysisJobs = analysisJobs; }
    
    // Helper methods
    public boolean isCompleted() {
        return completedAnalysisJobs != null && 
               totalAnalysisJobs != null && 
               completedAnalysisJobs.equals(totalAnalysisJobs) && 
               totalAnalysisJobs > 0;
    }
    
    public boolean isProcessing() {
        return status == Book.BookStatus.PROCESSING;
    }
    
    public boolean isActive() {
        return status == Book.BookStatus.ACTIVE;
    }
    
    @Override
    public String toString() {
        return "BookDto{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", status=" + status +
                ", totalAnalysisJobs=" + totalAnalysisJobs +
                ", completedAnalysisJobs=" + completedAnalysisJobs +
                ", progressPercentage=" + progressPercentage +
                ", username='" + username + '\'' +
                '}';
    }
    
    // Static factory methods
    public static BookDto fromEntity(Book book) {
        return new BookDto(book);
    }
    
    public static BookDto fromEntityWithoutJobs(Book book) {
        return new BookDto(book, false);
    }
}