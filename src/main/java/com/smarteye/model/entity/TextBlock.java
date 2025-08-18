package com.smarteye.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "text_blocks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TextBlock {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_job_id", nullable = false)
    private AnalysisJob analysisJob;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "layout_block_id")
    private LayoutBlock layoutBlock;
    
    @Column(nullable = false)
    private String processingMethod; // OCR, VISION_API
    
    @Column(columnDefinition = "TEXT")
    private String extractedText;
    
    @Column(columnDefinition = "TEXT")
    private String processedContent;
    
    @Column(nullable = false)
    private Double confidence;
    
    @Column(columnDefinition = "TEXT")
    private String metadata; // JSON 형태로 저장
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
