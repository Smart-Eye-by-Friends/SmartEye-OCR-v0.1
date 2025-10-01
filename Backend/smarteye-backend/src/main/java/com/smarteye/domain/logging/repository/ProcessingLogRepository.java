package com.smarteye.domain.logging.repository;

import com.smarteye.domain.logging.entity.ProcessingLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProcessingLogRepository extends JpaRepository<ProcessingLog, Long> {
    
    List<ProcessingLog> findByAnalysisJobId(Long analysisJobId);
    
    Page<ProcessingLog> findByAnalysisJobId(Long analysisJobId, Pageable pageable);
    
    @Query("SELECT pl FROM ProcessingLog pl WHERE pl.analysisJob.jobId = :jobId ORDER BY pl.createdAt")
    List<ProcessingLog> findByJobIdOrderByCreatedAt(@Param("jobId") String jobId);
    
    @Query("SELECT pl FROM ProcessingLog pl WHERE pl.analysisJob.jobId = :jobId ORDER BY pl.createdAt DESC")
    Page<ProcessingLog> findByJobIdOrderByCreatedAtDesc(@Param("jobId") String jobId, Pageable pageable);
    
    List<ProcessingLog> findByLevel(ProcessingLog.LogLevel level);
    
    @Query("SELECT pl FROM ProcessingLog pl WHERE pl.level IN :levels")
    List<ProcessingLog> findByLevelIn(@Param("levels") List<ProcessingLog.LogLevel> levels);
    
    List<ProcessingLog> findByStep(String step);
    
    @Query("SELECT pl FROM ProcessingLog pl WHERE pl.step LIKE %:stepPattern%")
    List<ProcessingLog> findByStepContaining(@Param("stepPattern") String stepPattern);
    
    @Query("SELECT pl FROM ProcessingLog pl WHERE pl.message LIKE %:messagePattern%")
    List<ProcessingLog> findByMessageContaining(@Param("messagePattern") String messagePattern);
    
    // Error and warning queries
    @Query("SELECT pl FROM ProcessingLog pl WHERE pl.level IN ('ERROR', 'FATAL')")
    List<ProcessingLog> findErrorLogs();
    
    @Query("SELECT pl FROM ProcessingLog pl WHERE pl.level = 'WARN'")
    List<ProcessingLog> findWarningLogs();
    
    @Query("SELECT pl FROM ProcessingLog pl WHERE pl.exceptionTrace IS NOT NULL")
    List<ProcessingLog> findLogsWithExceptions();
    
    @Query("""
        SELECT pl FROM ProcessingLog pl 
        WHERE pl.analysisJob.jobId = :jobId 
        AND pl.level IN ('ERROR', 'FATAL') 
        ORDER BY pl.createdAt DESC
        """)
    List<ProcessingLog> findErrorLogsByJobId(@Param("jobId") String jobId);
    
    // Time-based queries
    @Query("SELECT pl FROM ProcessingLog pl WHERE pl.createdAt >= :fromDate AND pl.createdAt <= :toDate")
    List<ProcessingLog> findByCreatedAtBetween(@Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate);
    
    @Query("SELECT pl FROM ProcessingLog pl WHERE pl.createdAt >= :since ORDER BY pl.createdAt DESC")
    List<ProcessingLog> findRecentLogs(@Param("since") LocalDateTime since);
    
    @Query("""
        SELECT pl FROM ProcessingLog pl 
        WHERE pl.analysisJob.id = :analysisJobId 
        AND pl.createdAt >= :since 
        ORDER BY pl.createdAt DESC
        """)
    List<ProcessingLog> findRecentLogsByJobId(@Param("analysisJobId") Long analysisJobId, @Param("since") LocalDateTime since);
    
    // Performance queries
    @Query("SELECT pl FROM ProcessingLog pl WHERE pl.executionTimeMs > :thresholdMs ORDER BY pl.executionTimeMs DESC")
    List<ProcessingLog> findSlowExecutions(@Param("thresholdMs") Long thresholdMs);
    
    @Query("SELECT pl FROM ProcessingLog pl WHERE pl.memoryUsageMb > :thresholdMb ORDER BY pl.memoryUsageMb DESC")
    List<ProcessingLog> findHighMemoryUsage(@Param("thresholdMb") Long thresholdMb);
    
    @Query("SELECT AVG(pl.executionTimeMs) FROM ProcessingLog pl WHERE pl.step = :step AND pl.executionTimeMs IS NOT NULL")
    Double getAverageExecutionTimeByStep(@Param("step") String step);
    
    @Query("SELECT AVG(pl.memoryUsageMb) FROM ProcessingLog pl WHERE pl.memoryUsageMb IS NOT NULL")
    Double getAverageMemoryUsage();
    
    // Statistics queries
    @Query("SELECT COUNT(pl) FROM ProcessingLog pl WHERE pl.level = :level")
    long countByLevel(@Param("level") ProcessingLog.LogLevel level);
    
    @Query("SELECT COUNT(pl) FROM ProcessingLog pl WHERE pl.step = :step")
    long countByStep(@Param("step") String step);
    
    @Query("SELECT COUNT(pl) FROM ProcessingLog pl WHERE pl.analysisJob.id = :analysisJobId")
    long countByAnalysisJobId(@Param("analysisJobId") Long analysisJobId);
    
    @Query("SELECT COUNT(pl) FROM ProcessingLog pl WHERE pl.createdAt >= :fromDate")
    long countByCreatedAtAfter(@Param("fromDate") LocalDateTime fromDate);
    
    // Step analysis queries
    @Query("SELECT pl.step, COUNT(pl) FROM ProcessingLog pl GROUP BY pl.step ORDER BY COUNT(pl) DESC")
    List<Object[]> getStepStatistics();
    
    @Query("SELECT pl.level, COUNT(pl) FROM ProcessingLog pl GROUP BY pl.level ORDER BY COUNT(pl) DESC")
    List<Object[]> getLevelStatistics();
    
    @Query("""
        SELECT pl.step, AVG(pl.executionTimeMs), COUNT(pl) 
        FROM ProcessingLog pl 
        WHERE pl.executionTimeMs IS NOT NULL 
        GROUP BY pl.step 
        ORDER BY AVG(pl.executionTimeMs) DESC
        """)
    List<Object[]> getStepPerformanceStatistics();
    
    // Job analysis queries
    @Query("""
        SELECT aj.jobId, COUNT(pl), 
               SUM(CASE WHEN pl.level IN ('ERROR', 'FATAL') THEN 1 ELSE 0 END) as errorCount,
               SUM(CASE WHEN pl.level = 'WARN' THEN 1 ELSE 0 END) as warnCount
        FROM ProcessingLog pl 
        JOIN pl.analysisJob aj 
        GROUP BY aj.jobId 
        ORDER BY errorCount DESC, warnCount DESC
        """)
    List<Object[]> getJobLogStatistics();
    
    @Query("""
        SELECT aj.jobId, pl.step, COUNT(pl), AVG(pl.executionTimeMs)
        FROM ProcessingLog pl 
        JOIN pl.analysisJob aj 
        WHERE aj.jobId = :jobId 
        GROUP BY aj.jobId, pl.step 
        ORDER BY pl.step
        """)
    List<Object[]> getJobStepStatistics(@Param("jobId") String jobId);
    
    // Complex queries
    @Query("""
        SELECT pl FROM ProcessingLog pl 
        JOIN pl.analysisJob aj 
        WHERE aj.user.id = :userId 
        AND pl.level IN ('ERROR', 'FATAL') 
        ORDER BY pl.createdAt DESC
        """)
    List<ProcessingLog> findUserErrorLogs(@Param("userId") Long userId);
    
    @Query("""
        SELECT pl FROM ProcessingLog pl 
        WHERE pl.analysisJob.id = :analysisJobId 
        AND pl.step = :step 
        ORDER BY pl.createdAt DESC
        """)
    List<ProcessingLog> findByJobIdAndStep(@Param("analysisJobId") Long analysisJobId, @Param("step") String step);
    
    // Additional data queries
    @Query("SELECT pl FROM ProcessingLog pl WHERE pl.additionalData IS NOT NULL")
    List<ProcessingLog> findLogsWithAdditionalData();
    
    @Query("SELECT pl FROM ProcessingLog pl WHERE pl.additionalData LIKE %:pattern%")
    List<ProcessingLog> findByAdditionalDataContaining(@Param("pattern") String pattern);
    
    // Daily statistics
    @Query("""
        SELECT DATE(pl.createdAt), pl.level, COUNT(pl) 
        FROM ProcessingLog pl 
        WHERE pl.createdAt >= :fromDate 
        GROUP BY DATE(pl.createdAt), pl.level 
        ORDER BY DATE(pl.createdAt), pl.level
        """)
    List<Object[]> getDailyLogStatistics(@Param("fromDate") LocalDateTime fromDate);
    
    @Query("""
        SELECT DATE(pl.createdAt), COUNT(pl), 
               COUNT(CASE WHEN pl.level IN ('ERROR', 'FATAL') THEN 1 END) as errorCount
        FROM ProcessingLog pl 
        WHERE pl.createdAt >= :fromDate 
        GROUP BY DATE(pl.createdAt) 
        ORDER BY DATE(pl.createdAt)
        """)
    List<Object[]> getDailyErrorStatistics(@Param("fromDate") LocalDateTime fromDate);
    
    // Cleanup queries
    @Query("SELECT pl FROM ProcessingLog pl WHERE pl.createdAt < :before AND pl.level NOT IN ('ERROR', 'FATAL')")
    List<ProcessingLog> findOldNonErrorLogs(@Param("before") LocalDateTime before);
    
    @Query("SELECT pl FROM ProcessingLog pl WHERE pl.createdAt < :before")
    List<ProcessingLog> findOldLogs(@Param("before") LocalDateTime before);
    
    // Latest logs for monitoring
    @Query("""
        SELECT pl FROM ProcessingLog pl 
        WHERE pl.analysisJob.status IN ('PROCESSING', 'PENDING') 
        ORDER BY pl.createdAt DESC
        """)
    List<ProcessingLog> findActiveJobLogs(Pageable pageable);
}