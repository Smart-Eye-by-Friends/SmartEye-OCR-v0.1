package com.smarteye.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "processing_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessingLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String sessionId;
    
    @Column(nullable = false)
    private String moduleName;
    
    private String logLevel;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column(columnDefinition = "TEXT")
    private String metadata; // JSON 형태로 저장
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_job_id")
    private AnalysisJob analysisJob;
    
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
