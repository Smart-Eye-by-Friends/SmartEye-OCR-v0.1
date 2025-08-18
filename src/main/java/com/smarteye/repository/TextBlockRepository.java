package com.smarteye.repository;

import com.smarteye.model.entity.TextBlock;
import com.smarteye.model.entity.AnalysisJob;
import com.smarteye.model.entity.LayoutBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TextBlockRepository extends JpaRepository<TextBlock, Long> {
    
    List<TextBlock> findByAnalysisJob(AnalysisJob analysisJob);
    
    List<TextBlock> findByLayoutBlock(LayoutBlock layoutBlock);
    
    List<TextBlock> findByAnalysisJobAndProcessingMethod(AnalysisJob analysisJob, String processingMethod);
    
    @Query("SELECT tb FROM TextBlock tb WHERE tb.analysisJob = :analysisJob AND tb.confidence >= :minConfidence")
    List<TextBlock> findByAnalysisJobAndConfidenceGreaterThanEqual(AnalysisJob analysisJob, Double minConfidence);
    
    @Query("SELECT tb FROM TextBlock tb WHERE tb.extractedText IS NOT NULL AND LENGTH(tb.extractedText) > 0")
    List<TextBlock> findAllWithText();
}
