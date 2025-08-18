package com.smarteye.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "layout_blocks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LayoutBlock {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_job_id", nullable = false)
    private AnalysisJob analysisJob;
    
    @Column(nullable = false)
    private Integer blockIndex;
    
    @Column(nullable = false)
    private String className;
    
    @Column(nullable = false)
    private Double confidence;
    
    @Column(nullable = false)
    private Integer x1;
    
    @Column(nullable = false)
    private Integer y1;
    
    @Column(nullable = false)
    private Integer x2;
    
    @Column(nullable = false)
    private Integer y2;
    
    @Column(nullable = false)
    private Integer width;
    
    @Column(nullable = false)
    private Integer height;
    
    @Column(nullable = false)
    private Long area;
    
    @Column(columnDefinition = "TEXT")
    private String croppedImagePath;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
