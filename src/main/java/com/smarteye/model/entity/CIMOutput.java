package com.smarteye.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "cim_outputs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CIMOutput {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long outputId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_job_id", nullable = false)
    private AnalysisJob analysisJob;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String outputContent;
    
    @Column(nullable = false, length = 20)
    private String outputFormat; // JSON, MARKDOWN, PDF, DOCX 등
    
    @Column(nullable = false)
    private LocalDateTime generatedAt;
    
    private String filePath; // 파일로 저장된 경우 경로
    
    @Column(columnDefinition = "TEXT")
    private String integrationMethod; // 통합 방법 설명
    
    @PrePersist
    protected void onCreate() {
        generatedAt = LocalDateTime.now();
    }
}
