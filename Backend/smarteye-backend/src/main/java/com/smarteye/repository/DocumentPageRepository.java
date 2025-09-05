package com.smarteye.repository;

import com.smarteye.entity.DocumentPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentPageRepository extends JpaRepository<DocumentPage, Long> {
    
    List<DocumentPage> findByAnalysisJobId(Long analysisJobId);
    
    @Query("SELECT dp FROM DocumentPage dp WHERE dp.analysisJob.jobId = :jobId ORDER BY dp.pageNumber")
    List<DocumentPage> findByJobIdOrderByPageNumber(@Param("jobId") String jobId);
    
    @Query("SELECT dp FROM DocumentPage dp WHERE dp.analysisJob.id = :analysisJobId ORDER BY dp.pageNumber")
    List<DocumentPage> findByAnalysisJobIdOrderByPageNumber(@Param("analysisJobId") Long analysisJobId);
    
    Optional<DocumentPage> findByAnalysisJobIdAndPageNumber(Long analysisJobId, Integer pageNumber);
    
    @Query("SELECT dp FROM DocumentPage dp WHERE dp.analysisJob.jobId = :jobId AND dp.pageNumber = :pageNumber")
    Optional<DocumentPage> findByJobIdAndPageNumber(@Param("jobId") String jobId, @Param("pageNumber") Integer pageNumber);
    
    List<DocumentPage> findByProcessingStatus(DocumentPage.ProcessingStatus processingStatus);
    
    @Query("SELECT dp FROM DocumentPage dp WHERE dp.processingStatus IN :statuses")
    List<DocumentPage> findByProcessingStatusIn(@Param("statuses") List<DocumentPage.ProcessingStatus> statuses);
    
    @Query("SELECT dp FROM DocumentPage dp WHERE dp.analysisJob.id = :analysisJobId AND dp.processingStatus = :status")
    List<DocumentPage> findByAnalysisJobIdAndProcessingStatus(@Param("analysisJobId") Long analysisJobId, 
                                                              @Param("status") DocumentPage.ProcessingStatus status);
    
    // Statistics queries
    @Query("SELECT COUNT(dp) FROM DocumentPage dp WHERE dp.processingStatus = :status")
    long countByProcessingStatus(@Param("status") DocumentPage.ProcessingStatus status);
    
    @Query("SELECT COUNT(dp) FROM DocumentPage dp WHERE dp.analysisJob.id = :analysisJobId")
    long countByAnalysisJobId(@Param("analysisJobId") Long analysisJobId);
    
    @Query("SELECT AVG(dp.processingTimeMs) FROM DocumentPage dp WHERE dp.processingStatus = 'COMPLETED'")
    Double getAverageProcessingTime();
    
    @Query("SELECT MAX(dp.pageNumber) FROM DocumentPage dp WHERE dp.analysisJob.id = :analysisJobId")
    Integer getMaxPageNumberByAnalysisJobId(@Param("analysisJobId") Long analysisJobId);
    
    // Complex queries with joins
    @Query("SELECT dp FROM DocumentPage dp LEFT JOIN FETCH dp.layoutBlocks WHERE dp.id = :pageId")
    Optional<DocumentPage> findByIdWithLayoutBlocks(@Param("pageId") Long pageId);
    
    @Query("""
        SELECT dp FROM DocumentPage dp 
        LEFT JOIN FETCH dp.layoutBlocks lb 
        LEFT JOIN FETCH lb.textBlock 
        WHERE dp.analysisJob.jobId = :jobId 
        ORDER BY dp.pageNumber
        """)
    List<DocumentPage> findByJobIdWithLayoutBlocksAndText(@Param("jobId") String jobId);
    
    // Performance queries
    @Query("""
        SELECT dp FROM DocumentPage dp 
        WHERE dp.processingTimeMs > :thresholdMs 
        ORDER BY dp.processingTimeMs DESC
        """)
    List<DocumentPage> findSlowProcessingPages(@Param("thresholdMs") Long thresholdMs);
    
    @Query("""
        SELECT dp FROM DocumentPage dp 
        WHERE dp.processingStatus = 'FAILED' 
        AND dp.updatedAt >= :since 
        ORDER BY dp.updatedAt DESC
        """)
    List<DocumentPage> findFailedPagesSince(@Param("since") LocalDateTime since);
    
    // Image dimension queries
    @Query("SELECT dp FROM DocumentPage dp WHERE dp.imageWidth > :width AND dp.imageHeight > :height")
    List<DocumentPage> findByMinImageDimensions(@Param("width") Integer width, @Param("height") Integer height);
    
    @Query("SELECT AVG(dp.imageWidth), AVG(dp.imageHeight) FROM DocumentPage dp WHERE dp.imageWidth IS NOT NULL AND dp.imageHeight IS NOT NULL")
    Object[] getAverageImageDimensions();
    
    // Layout analysis results
    @Query("SELECT dp FROM DocumentPage dp WHERE dp.analysisResult IS NOT NULL")
    List<DocumentPage> findPagesWithAnalysisResults();
    
    @Query("SELECT dp FROM DocumentPage dp WHERE dp.layoutVisualizationPath IS NOT NULL")
    List<DocumentPage> findPagesWithVisualization();
    
    // Cleanup queries
    @Query("SELECT dp FROM DocumentPage dp WHERE dp.updatedAt < :before AND dp.processingStatus = 'FAILED'")
    List<DocumentPage> findOldFailedPages(@Param("before") LocalDateTime before);
}