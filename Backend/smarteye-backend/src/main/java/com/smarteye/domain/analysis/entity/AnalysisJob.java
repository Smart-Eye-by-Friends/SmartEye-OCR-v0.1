package com.smarteye.domain.analysis.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.smarteye.domain.user.entity.User;
import com.smarteye.domain.book.entity.Book;
import com.smarteye.domain.document.entity.DocumentPage;
import com.smarteye.domain.logging.entity.ProcessingLog;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "analysis_jobs")
@EntityListeners(AuditingEntityListener.class)
public class AnalysisJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", unique = true, nullable = false, length = 36)
    private String jobId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_type", length = 50)
    private String fileType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "model_choice", length = 50)
    private String modelChoice = "SmartEye";

    @Column(name = "use_ai_description")
    private Boolean useAiDescription = false;

    @Column(name = "progress_percentage")
    private Integer progressPercentage = 0;

    @Column(name = "current_step", length = 100)
    private String currentStep;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "sequence_in_book")
    private Integer sequenceInBook;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)  // 개발 환경에서 nullable 허용
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    @OneToMany(mappedBy = "analysisJob", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DocumentPage> documentPages = new ArrayList<>();

    @OneToMany(mappedBy = "analysisJob", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ProcessingLog> processingLogs = new ArrayList<>();

    @OneToOne(mappedBy = "analysisJob", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private CIMOutput cimOutput;

    public enum JobStatus {
        PENDING("PENDING"),
        PROCESSING("PROCESSING"),
        COMPLETED("COMPLETED"),
        FAILED("FAILED"),
        CANCELLED("CANCELLED");

        private final String value;

        JobStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    // Constructors
    public AnalysisJob() {}

    public AnalysisJob(String jobId, String originalFilename, String filePath,
                      String fileType, Long fileSize, User user) {
        this.jobId = jobId;
        this.originalFilename = originalFilename;
        this.filePath = filePath;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.user = user;
        this.status = JobStatus.PENDING;
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

    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }

    public String getModelChoice() { return modelChoice; }
    public void setModelChoice(String modelChoice) { this.modelChoice = modelChoice; }

    public Boolean getUseAiDescription() { return useAiDescription; }
    public void setUseAiDescription(Boolean useAiDescription) { this.useAiDescription = useAiDescription; }

    public Integer getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(Integer progressPercentage) { this.progressPercentage = progressPercentage; }

    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getSequenceInBook() { return sequenceInBook; }
    public void setSequenceInBook(Integer sequenceInBook) { this.sequenceInBook = sequenceInBook; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public Long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }

    public List<DocumentPage> getDocumentPages() { return documentPages; }
    public void setDocumentPages(List<DocumentPage> documentPages) { this.documentPages = documentPages; }

    // Legacy getter for backwards compatibility
    public List<DocumentPage> getPages() { return documentPages; }
    public void setPages(List<DocumentPage> pages) { this.documentPages = pages; }

    public List<ProcessingLog> getProcessingLogs() { return processingLogs; }
    public void setProcessingLogs(List<ProcessingLog> processingLogs) { this.processingLogs = processingLogs; }

    public CIMOutput getCimOutput() { return cimOutput; }
    public void setCimOutput(CIMOutput cimOutput) { this.cimOutput = cimOutput; }

    // Helper methods
    public void addDocumentPage(DocumentPage page) {
        documentPages.add(page);
        page.setAnalysisJob(this);
    }

    public void addPage(DocumentPage page) {
        addDocumentPage(page); // Delegate to new method
    }

    public void addProcessingLog(ProcessingLog log) {
        processingLogs.add(log);
        log.setAnalysisJob(this);
    }

    public boolean isCompleted() {
        return status == JobStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == JobStatus.FAILED;
    }

    public boolean isProcessing() {
        return status == JobStatus.PROCESSING;
    }

    @Override
    public String toString() {
        return "AnalysisJob{" +
                "id=" + id +
                ", jobId='" + jobId + '\'' +
                ", originalFilename='" + originalFilename + '\'' +
                ", status=" + status +
                ", progressPercentage=" + progressPercentage +
                ", sequenceInBook=" + sequenceInBook +
                ", createdAt=" + createdAt +
                ", completedAt=" + completedAt +
                '}';
    }
}