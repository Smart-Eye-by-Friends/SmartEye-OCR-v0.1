package com.smarteye.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "analysis_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisJob {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String jobId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    
    @Column(nullable = false)
    private String originalFilename;
    
    @Column(nullable = false)
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED
    
    @Column(nullable = false)
    private String fileType;
    
    private Long fileSize;
    
    private String filePath;
    
    private String resultPath;
    
    // 파일 정보 강화 (Django 호환)
    private Integer pageNumber;
    
    private String sourceType; // PDF, IMAGE, SCAN 등
    
    private Integer imageWidth;
    
    private Integer imageHeight;
    
    // 처리 메타데이터
    private String modelUsed; // YOLO 모델명 등
    
    private Integer detectedObjectsCount;
    
    private Double processingTime; // 초 단위
    
    // API 설정
    private String apiProvider; // OPENAI, GOOGLE_VISION 등
    
    private String integrationMethod; // OCR_ONLY, VISION_API, HYBRID 등
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime startedAt;
    
    private LocalDateTime completedAt;
    
    private LocalDateTime updatedAt;
    
    @Builder.Default
    @Column(nullable = false)
    private Integer progress = 0; // 0-100
    
    @OneToMany(mappedBy = "analysisJob", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<LayoutBlock> layoutBlocks;
    
    @OneToMany(mappedBy = "analysisJob", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TextBlock> textBlocks;
    
    @OneToMany(mappedBy = "analysisJob", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ProcessingLog> processingLogs;
    
    @OneToMany(mappedBy = "analysisJob", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CIMOutput> cimOutputs;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = "PENDING";
        }
        if (progress == null) {
            progress = 0;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
