package com.smarteye.infrastructure.persistence;

import com.smarteye.domain.analysis.CIMOutput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CIMOutputRepository extends JpaRepository<CIMOutput, Long> {
    
    Optional<CIMOutput> findByAnalysisJobId(Long analysisJobId);
    
    Optional<CIMOutput> findByAnalysisJob(com.smarteye.domain.analysis.AnalysisJob analysisJob);
    
    @Query("SELECT co FROM CIMOutput co WHERE co.analysisJob.jobId = :jobId")
    Optional<CIMOutput> findByJobId(@Param("jobId") String jobId);
    
    List<CIMOutput> findByGenerationStatus(CIMOutput.GenerationStatus generationStatus);
    
    @Query("SELECT co FROM CIMOutput co WHERE co.generationStatus IN :statuses")
    List<CIMOutput> findByGenerationStatusIn(@Param("statuses") List<CIMOutput.GenerationStatus> statuses);
    
    @Query("SELECT co FROM CIMOutput co WHERE co.analysisJob.user.id = :userId")
    List<CIMOutput> findByUserId(@Param("userId") Long userId);
    
    // File path queries
    @Query("SELECT co FROM CIMOutput co WHERE co.wordDocumentPath IS NOT NULL")
    List<CIMOutput> findWithWordDocuments();
    
    @Query("SELECT co FROM CIMOutput co WHERE co.jsonFilePath IS NOT NULL")
    List<CIMOutput> findWithJsonFiles();
    
    @Query("SELECT co FROM CIMOutput co WHERE co.formattedText IS NOT NULL AND LENGTH(co.formattedText) > 0")
    List<CIMOutput> findWithFormattedText();
    
    @Query("SELECT co FROM CIMOutput co WHERE co.layoutVisualizationPath IS NOT NULL")
    List<CIMOutput> findWithLayoutVisualization();
    
    @Query("SELECT co FROM CIMOutput co WHERE co.textVisualizationPath IS NOT NULL")
    List<CIMOutput> findWithTextVisualization();
    
    // Statistics queries
    @Query("SELECT COUNT(co) FROM CIMOutput co WHERE co.generationStatus = :status")
    long countByGenerationStatus(@Param("status") CIMOutput.GenerationStatus status);
    
    @Query("SELECT AVG(co.processingTimeMs) FROM CIMOutput co WHERE co.processingTimeMs IS NOT NULL")
    Double getAverageProcessingTime();
    
    @Query("SELECT AVG(co.totalElements) FROM CIMOutput co WHERE co.totalElements IS NOT NULL")
    Double getAverageTotalElements();
    
    @Query("SELECT AVG(co.textElements) FROM CIMOutput co WHERE co.textElements IS NOT NULL")
    Double getAverageTextElements();
    
    @Query("SELECT AVG(co.aiDescribedElements) FROM CIMOutput co WHERE co.aiDescribedElements IS NOT NULL")
    Double getAverageAiDescribedElements();
    
    @Query("SELECT SUM(co.totalWordCount) FROM CIMOutput co WHERE co.totalWordCount IS NOT NULL")
    Long getTotalWordCount();
    
    @Query("SELECT SUM(co.totalCharCount) FROM CIMOutput co WHERE co.totalCharCount IS NOT NULL")
    Long getTotalCharCount();
    
    // Element statistics
    @Query("SELECT co FROM CIMOutput co WHERE co.totalElements >= :minElements")
    List<CIMOutput> findByMinTotalElements(@Param("minElements") Integer minElements);
    
    @Query("SELECT co FROM CIMOutput co WHERE co.textElements >= :minTextElements")
    List<CIMOutput> findByMinTextElements(@Param("minTextElements") Integer minTextElements);
    
    @Query("SELECT co FROM CIMOutput co WHERE co.aiDescribedElements >= :minAiElements")
    List<CIMOutput> findByMinAiDescribedElements(@Param("minAiElements") Integer minAiElements);
    
    @Query("SELECT co FROM CIMOutput co WHERE co.totalFigures >= :minFigures")
    List<CIMOutput> findByMinFigures(@Param("minFigures") Integer minFigures);
    
    @Query("SELECT co FROM CIMOutput co WHERE co.totalTables >= :minTables")
    List<CIMOutput> findByMinTables(@Param("minTables") Integer minTables);
    
    // Word count queries
    @Query("SELECT co FROM CIMOutput co WHERE co.totalWordCount >= :minWords")
    List<CIMOutput> findByMinWordCount(@Param("minWords") Integer minWords);
    
    @Query("SELECT co FROM CIMOutput co WHERE co.totalWordCount BETWEEN :minWords AND :maxWords")
    List<CIMOutput> findByWordCountBetween(@Param("minWords") Integer minWords, @Param("maxWords") Integer maxWords);
    
    // Performance queries
    @Query("SELECT co FROM CIMOutput co WHERE co.processingTimeMs > :thresholdMs ORDER BY co.processingTimeMs DESC")
    List<CIMOutput> findSlowProcessing(@Param("thresholdMs") Long thresholdMs);
    
    @Query("SELECT co FROM CIMOutput co WHERE co.processingTimeMs < :thresholdMs ORDER BY co.processingTimeMs ASC")
    List<CIMOutput> findFastProcessing(@Param("thresholdMs") Long thresholdMs);
    
    // File size queries
    @Query("SELECT co FROM CIMOutput co WHERE co.fileSizeBytes >= :minSize")
    List<CIMOutput> findByMinFileSize(@Param("minSize") Long minSize);
    
    @Query("SELECT AVG(co.fileSizeBytes) FROM CIMOutput co WHERE co.fileSizeBytes IS NOT NULL")
    Double getAverageFileSize();
    
    @Query("SELECT SUM(co.fileSizeBytes) FROM CIMOutput co WHERE co.fileSizeBytes IS NOT NULL")
    Long getTotalFileSize();
    
    // Time-based queries
    @Query("SELECT co FROM CIMOutput co WHERE co.createdAt >= :fromDate AND co.createdAt <= :toDate")
    List<CIMOutput> findByCreatedAtBetween(@Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate);
    
    @Query("SELECT co FROM CIMOutput co WHERE co.updatedAt >= :since ORDER BY co.updatedAt DESC")
    List<CIMOutput> findRecentlyUpdated(@Param("since") LocalDateTime since);
    
    // Quality metrics
    @Query("""
        SELECT co FROM CIMOutput co 
        WHERE (CAST(co.textElements AS double) / CAST(co.totalElements AS double)) >= :minRatio
        """)
    List<CIMOutput> findByMinTextExtractionRate(@Param("minRatio") Double minRatio);
    
    @Query("""
        SELECT co FROM CIMOutput co 
        WHERE co.aiDescribedElements > 0 
        AND (CAST(co.aiDescribedElements AS double) / CAST(co.totalElements AS double)) >= :minRatio
        """)
    List<CIMOutput> findByMinAiDescriptionRate(@Param("minRatio") Double minRatio);
    
    // Error and failure queries
    @Query("SELECT co FROM CIMOutput co WHERE co.generationStatus = 'FAILED'")
    List<CIMOutput> findFailedGenerations();
    
    @Query("SELECT co FROM CIMOutput co WHERE co.errorMessage IS NOT NULL")
    List<CIMOutput> findWithErrors();
    
    @Query("""
        SELECT co FROM CIMOutput co 
        WHERE co.generationStatus = 'FAILED' 
        AND co.updatedAt >= :since 
        ORDER BY co.updatedAt DESC
        """)
    List<CIMOutput> findRecentFailures(@Param("since") LocalDateTime since);
    
    // Complex queries with joins
    @Query("""
        SELECT co FROM CIMOutput co 
        JOIN co.analysisJob aj 
        WHERE aj.user.id = :userId 
        AND co.generationStatus = 'COMPLETED'
        ORDER BY co.createdAt DESC
        """)
    List<CIMOutput> findCompletedByUserId(@Param("userId") Long userId);
    
    @Query("""
        SELECT co FROM CIMOutput co 
        JOIN co.analysisJob aj 
        WHERE aj.status = 'COMPLETED' 
        AND co.generationStatus = 'COMPLETED'
        """)
    List<CIMOutput> findFullyCompleted();
    
    // Aggregation queries
    @Query("""
        SELECT co.generationStatus, COUNT(co), AVG(co.processingTimeMs), AVG(co.totalElements) 
        FROM CIMOutput co 
        GROUP BY co.generationStatus
        """)
    List<Object[]> getGenerationStatistics();
    
    @Query("""
        SELECT DATE(co.createdAt), COUNT(co), AVG(co.processingTimeMs) 
        FROM CIMOutput co 
        WHERE co.createdAt >= :fromDate 
        GROUP BY DATE(co.createdAt) 
        ORDER BY DATE(co.createdAt)
        """)
    List<Object[]> getDailyStatistics(@Param("fromDate") LocalDateTime fromDate);
    
    // Cleanup queries
    @Query("SELECT co FROM CIMOutput co WHERE co.createdAt < :before AND co.generationStatus = 'FAILED'")
    List<CIMOutput> findOldFailedOutputs(@Param("before") LocalDateTime before);
    
    @Query("SELECT co FROM CIMOutput co WHERE co.createdAt < :before AND co.generationStatus = 'COMPLETED'")
    List<CIMOutput> findOldCompletedOutputs(@Param("before") LocalDateTime before);
}