package com.smarteye.repository;

import com.smarteye.model.entity.LayoutBlock;
import com.smarteye.model.entity.AnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LayoutBlockRepository extends JpaRepository<LayoutBlock, Long> {
    
    List<LayoutBlock> findByAnalysisJobOrderByBlockIndex(AnalysisJob analysisJob);
    
    List<LayoutBlock> findByAnalysisJobAndClassName(AnalysisJob analysisJob, String className);
    
    @Query("SELECT lb FROM LayoutBlock lb WHERE lb.analysisJob = :analysisJob AND lb.confidence >= :minConfidence")
    List<LayoutBlock> findByAnalysisJobAndConfidenceGreaterThanEqual(AnalysisJob analysisJob, Double minConfidence);
    
    @Query("SELECT lb FROM LayoutBlock lb WHERE lb.analysisJob = :analysisJob AND lb.area >= :minArea")
    List<LayoutBlock> findByAnalysisJobAndAreaGreaterThanEqual(AnalysisJob analysisJob, Long minArea);
}
