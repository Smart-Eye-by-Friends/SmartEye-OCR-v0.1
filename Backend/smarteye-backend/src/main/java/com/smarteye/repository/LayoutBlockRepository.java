package com.smarteye.repository;

import com.smarteye.entity.LayoutBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LayoutBlockRepository extends JpaRepository<LayoutBlock, Long> {
    
    List<LayoutBlock> findByDocumentPageId(Long documentPageId);
    
    @Query("SELECT lb FROM LayoutBlock lb WHERE lb.documentPage.id = :pageId ORDER BY lb.blockIndex")
    List<LayoutBlock> findByDocumentPageIdOrderByBlockIndex(@Param("pageId") Long pageId);
    
    @Query("SELECT lb FROM LayoutBlock lb WHERE lb.documentPage.analysisJob.jobId = :jobId")
    List<LayoutBlock> findByJobId(@Param("jobId") String jobId);
    
    List<LayoutBlock> findByClassName(String className);
    
    @Query("SELECT lb FROM LayoutBlock lb WHERE lb.className LIKE %:classPattern%")
    List<LayoutBlock> findByClassNameContaining(@Param("classPattern") String classPattern);
    
    List<LayoutBlock> findByProcessingStatus(LayoutBlock.ProcessingStatus processingStatus);
    
    @Query("SELECT lb FROM LayoutBlock lb WHERE lb.processingStatus IN :statuses")
    List<LayoutBlock> findByProcessingStatusIn(@Param("statuses") List<LayoutBlock.ProcessingStatus> statuses);
    
    @Query("SELECT lb FROM LayoutBlock lb WHERE lb.confidence >= :minConfidence")
    List<LayoutBlock> findByConfidenceGreaterThanEqual(@Param("minConfidence") Double minConfidence);
    
    @Query("SELECT lb FROM LayoutBlock lb WHERE lb.confidence < :threshold")
    List<LayoutBlock> findLowConfidenceBlocks(@Param("threshold") Double threshold);
    
    // Text-related queries
    @Query("SELECT lb FROM LayoutBlock lb WHERE lb.ocrText IS NOT NULL AND LENGTH(lb.ocrText) > 0")
    List<LayoutBlock> findBlocksWithOcrText();
    
    @Query("SELECT lb FROM LayoutBlock lb WHERE lb.ocrText IS NULL OR LENGTH(lb.ocrText) = 0")
    List<LayoutBlock> findBlocksWithoutOcrText();
    
    @Query("SELECT lb FROM LayoutBlock lb WHERE lb.aiDescription IS NOT NULL AND LENGTH(lb.aiDescription) > 0")
    List<LayoutBlock> findBlocksWithAiDescription();
    
    @Query("SELECT lb FROM LayoutBlock lb WHERE lb.aiDescription IS NULL OR LENGTH(lb.aiDescription) = 0")
    List<LayoutBlock> findBlocksWithoutAiDescription();
    
    @Query("SELECT lb FROM LayoutBlock lb WHERE lb.ocrText LIKE %:text%")
    List<LayoutBlock> findByOcrTextContaining(@Param("text") String text);
    
    @Query("SELECT lb FROM LayoutBlock lb WHERE lb.aiDescription LIKE %:text%")
    List<LayoutBlock> findByAiDescriptionContaining(@Param("text") String text);
    
    // Geometric queries
    @Query("SELECT lb FROM LayoutBlock lb WHERE lb.area >= :minArea")
    List<LayoutBlock> findByMinArea(@Param("minArea") Integer minArea);
    
    @Query("SELECT lb FROM LayoutBlock lb WHERE lb.width >= :minWidth AND lb.height >= :minHeight")
    List<LayoutBlock> findByMinDimensions(@Param("minWidth") Integer minWidth, @Param("minHeight") Integer minHeight);
    
    @Query("""
        SELECT lb FROM LayoutBlock lb 
        WHERE lb.x1 >= :x1 AND lb.y1 >= :y1 AND lb.x2 <= :x2 AND lb.y2 <= :y2
        """)
    List<LayoutBlock> findBlocksInRegion(@Param("x1") Integer x1, @Param("y1") Integer y1, 
                                        @Param("x2") Integer x2, @Param("y2") Integer y2);
    
    // Statistics queries
    @Query("SELECT COUNT(lb) FROM LayoutBlock lb WHERE lb.className = :className")
    long countByClassName(@Param("className") String className);
    
    @Query("SELECT COUNT(lb) FROM LayoutBlock lb WHERE lb.processingStatus = :status")
    long countByProcessingStatus(@Param("status") LayoutBlock.ProcessingStatus status);
    
    @Query("SELECT COUNT(lb) FROM LayoutBlock lb WHERE lb.ocrText IS NOT NULL AND LENGTH(lb.ocrText) > 0")
    long countBlocksWithText();
    
    @Query("SELECT COUNT(lb) FROM LayoutBlock lb WHERE lb.aiDescription IS NOT NULL AND LENGTH(lb.aiDescription) > 0")
    long countBlocksWithAiDescription();
    
    @Query("SELECT AVG(lb.confidence) FROM LayoutBlock lb WHERE lb.className = :className")
    Double getAverageConfidenceByClassName(@Param("className") String className);
    
    @Query("SELECT AVG(lb.area) FROM LayoutBlock lb WHERE lb.className = :className")
    Double getAverageAreaByClassName(@Param("className") String className);
    
    @Query("SELECT AVG(lb.processingTimeMs) FROM LayoutBlock lb WHERE lb.processingTimeMs IS NOT NULL")
    Double getAverageProcessingTime();
    
    // Complex queries with joins
    @Query("SELECT lb FROM LayoutBlock lb LEFT JOIN FETCH lb.textBlock WHERE lb.id = :blockId")
    Optional<LayoutBlock> findByIdWithTextBlock(@Param("blockId") Long blockId);
    
    @Query("""
        SELECT lb FROM LayoutBlock lb 
        LEFT JOIN FETCH lb.textBlock 
        WHERE lb.documentPage.id = :pageId 
        ORDER BY lb.blockIndex
        """)
    List<LayoutBlock> findByDocumentPageIdWithTextBlocks(@Param("pageId") Long pageId);
    
    // Class-specific queries
    @Query("SELECT lb FROM LayoutBlock lb WHERE lb.className IN ('title', 'heading')")
    List<LayoutBlock> findTitleAndHeadingBlocks();
    
    @Query("SELECT lb FROM LayoutBlock lb WHERE lb.className LIKE '%question%'")
    List<LayoutBlock> findQuestionBlocks();
    
    @Query("SELECT lb FROM LayoutBlock lb WHERE lb.className IN ('figure', 'image')")
    List<LayoutBlock> findFigureBlocks();
    
    @Query("SELECT lb FROM LayoutBlock lb WHERE lb.className = 'table'")
    List<LayoutBlock> findTableBlocks();
    
    @Query("""
        SELECT lb FROM LayoutBlock lb 
        WHERE lb.className IN ('title', 'plain_text', 'question_text', 'question_number')
        AND lb.ocrText IS NOT NULL 
        AND LENGTH(lb.ocrText) > 0
        """)
    List<LayoutBlock> findTextElements();
    
    // Performance and optimization queries
    @Query("""
        SELECT lb FROM LayoutBlock lb 
        WHERE lb.processingTimeMs > :thresholdMs 
        ORDER BY lb.processingTimeMs DESC
        """)
    List<LayoutBlock> findSlowProcessingBlocks(@Param("thresholdMs") Long thresholdMs);
    
    @Query("SELECT lb.className, COUNT(lb), AVG(lb.confidence) FROM LayoutBlock lb GROUP BY lb.className")
    List<Object[]> getClassStatistics();
    
    // Document page specific queries
    @Query("""
        SELECT lb FROM LayoutBlock lb 
        WHERE lb.documentPage.analysisJob.jobId = :jobId 
        AND lb.documentPage.pageNumber = :pageNumber 
        ORDER BY lb.blockIndex
        """)
    List<LayoutBlock> findByJobIdAndPageNumber(@Param("jobId") String jobId, @Param("pageNumber") Integer pageNumber);
}