package com.smarteye.domain.book;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.smarteye.domain.user.User;
import com.smarteye.domain.analysis.AnalysisJob;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "books")
@EntityListeners(AuditingEntityListener.class)
public class Book {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 255)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookStatus status = BookStatus.ACTIVE;
    
    @Column(name = "total_pages")
    private Integer totalPages = 0;
    
    @Column(name = "completed_pages")
    private Integer completedPages = 0;
    
    @Column(name = "total_analysis_jobs")
    private Integer totalAnalysisJobs = 0;
    
    @Column(name = "completed_analysis_jobs")
    private Integer completedAnalysisJobs = 0;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequenceInBook ASC")
    private List<AnalysisJob> analysisJobs = new ArrayList<>();
    
    public enum BookStatus {
        ACTIVE,      // 활성 상태
        ARCHIVED,    // 보관됨
        DELETED,     // 삭제됨
        PROCESSING   // 분석 중
    }
    
    // Constructors
    public Book() {}
    
    public Book(String title, User user) {
        this.title = title;
        this.user = user;
    }
    
    public Book(String title, String description, User user) {
        this.title = title;
        this.description = description;
        this.user = user;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public BookStatus getStatus() { return status; }
    public void setStatus(BookStatus status) { this.status = status; }
    
    public Integer getTotalPages() { return totalPages; }
    public void setTotalPages(Integer totalPages) { this.totalPages = totalPages; }
    
    public Integer getCompletedPages() { return completedPages; }
    public void setCompletedPages(Integer completedPages) { this.completedPages = completedPages; }
    
    public Integer getTotalAnalysisJobs() { return totalAnalysisJobs; }
    public void setTotalAnalysisJobs(Integer totalAnalysisJobs) { this.totalAnalysisJobs = totalAnalysisJobs; }
    
    public Integer getCompletedAnalysisJobs() { return completedAnalysisJobs; }
    public void setCompletedAnalysisJobs(Integer completedAnalysisJobs) { this.completedAnalysisJobs = completedAnalysisJobs; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    
    public List<AnalysisJob> getAnalysisJobs() { return analysisJobs; }
    public void setAnalysisJobs(List<AnalysisJob> analysisJobs) { this.analysisJobs = analysisJobs; }
    
    // Helper methods
    public void addAnalysisJob(AnalysisJob analysisJob) {
        // 시퀀스 번호 자동 할당
        if (analysisJob.getSequenceInBook() == null) {
            int nextSequence = analysisJobs.size() + 1;
            analysisJob.setSequenceInBook(nextSequence);
        }
        
        analysisJobs.add(analysisJob);
        analysisJob.setBook(this);
        
        updateTotalAnalysisJobs();
        updateTotalPages();
    }
    
    public void removeAnalysisJob(AnalysisJob analysisJob) {
        analysisJobs.remove(analysisJob);
        analysisJob.setBook(null);
        
        // 시퀀스 번호 재정렬
        reorderSequences();
        updateTotalAnalysisJobs();
        updateTotalPages();
    }
    
    public void updateProgress() {
        long completedJobs = analysisJobs.stream()
            .mapToLong(job -> job.isCompleted() ? 1 : 0)
            .sum();
        
        int completedPagesCount = analysisJobs.stream()
            .mapToInt(job -> job.getDocumentPages().size())
            .sum();
        
        this.completedAnalysisJobs = (int) completedJobs;
        this.completedPages = completedPagesCount;
        
        // 책 상태 업데이트
        if (completedAnalysisJobs.equals(totalAnalysisJobs) && totalAnalysisJobs > 0) {
            this.status = BookStatus.ACTIVE;
        } else if (completedAnalysisJobs > 0) {
            this.status = BookStatus.PROCESSING;
        }
    }
    
    private void updateTotalAnalysisJobs() {
        this.totalAnalysisJobs = analysisJobs.size();
    }
    
    private void updateTotalPages() {
        this.totalPages = analysisJobs.stream()
            .mapToInt(job -> Math.max(1, job.getDocumentPages().size()))
            .sum();
    }
    
    private void reorderSequences() {
        for (int i = 0; i < analysisJobs.size(); i++) {
            analysisJobs.get(i).setSequenceInBook(i + 1);
        }
    }
    
    // Business logic methods
    public boolean isCompleted() {
        return completedAnalysisJobs != null && 
               totalAnalysisJobs != null && 
               completedAnalysisJobs.equals(totalAnalysisJobs) && 
               totalAnalysisJobs > 0;
    }
    
    public boolean isProcessing() {
        return status == BookStatus.PROCESSING;
    }
    
    public boolean isActive() {
        return status == BookStatus.ACTIVE;
    }
    
    public double getProgressPercentage() {
        if (totalAnalysisJobs == null || totalAnalysisJobs == 0) {
            return 0.0;
        }
        return (double) completedAnalysisJobs / totalAnalysisJobs * 100.0;
    }
    
    public AnalysisJob getAnalysisJobBySequence(int sequence) {
        return analysisJobs.stream()
            .filter(job -> sequence == job.getSequenceInBook())
            .findFirst()
            .orElse(null);
    }
    
    @Override
    public String toString() {
        return "Book{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", status=" + status +
                ", totalAnalysisJobs=" + totalAnalysisJobs +
                ", completedAnalysisJobs=" + completedAnalysisJobs +
                ", totalPages=" + totalPages +
                ", completedPages=" + completedPages +
                ", createdAt=" + createdAt +
                '}';
    }
}