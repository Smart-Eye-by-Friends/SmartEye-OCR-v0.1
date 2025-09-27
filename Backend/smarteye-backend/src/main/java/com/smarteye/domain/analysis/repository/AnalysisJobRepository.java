package com.smarteye.domain.analysis.repository;

import com.smarteye.domain.analysis.entity.AnalysisJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {
    
    Optional<AnalysisJob> findByJobId(String jobId);
    
    List<AnalysisJob> findByUserId(Long userId);
    
    Page<AnalysisJob> findByUserId(Long userId, Pageable pageable);
    
    List<AnalysisJob> findByStatus(AnalysisJob.JobStatus status);
    
    @Query("SELECT aj FROM AnalysisJob aj WHERE aj.status IN :statuses")
    List<AnalysisJob> findByStatusIn(@Param("statuses") List<AnalysisJob.JobStatus> statuses);
    
    @Query("SELECT aj FROM AnalysisJob aj WHERE aj.user.id = :userId AND aj.status = :status")
    List<AnalysisJob> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") AnalysisJob.JobStatus status);
    
    @Query("SELECT aj FROM AnalysisJob aj WHERE aj.createdAt >= :fromDate AND aj.createdAt <= :toDate")
    List<AnalysisJob> findByCreatedAtBetween(@Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate);
    
    @Query("SELECT aj FROM AnalysisJob aj WHERE aj.user.id = :userId AND aj.createdAt >= :fromDate")
    List<AnalysisJob> findByUserIdAndCreatedAtAfter(@Param("userId") Long userId, @Param("fromDate") LocalDateTime fromDate);
    
    @Query("SELECT aj FROM AnalysisJob aj WHERE aj.originalFilename LIKE %:filename%")
    List<AnalysisJob> findByOriginalFilenameContaining(@Param("filename") String filename);
    
    @Query("SELECT aj FROM AnalysisJob aj WHERE aj.fileType = :fileType")
    List<AnalysisJob> findByFileType(@Param("fileType") String fileType);
    
    @Query("SELECT aj FROM AnalysisJob aj WHERE aj.modelChoice = :modelChoice")
    List<AnalysisJob> findByModelChoice(@Param("modelChoice") String modelChoice);
    
    // Statistics queries
    @Query("SELECT COUNT(aj) FROM AnalysisJob aj WHERE aj.status = :status")
    long countByStatus(@Param("status") AnalysisJob.JobStatus status);
    
    @Query("SELECT COUNT(aj) FROM AnalysisJob aj WHERE aj.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);
    
    @Query("SELECT COUNT(aj) FROM AnalysisJob aj WHERE aj.createdAt >= :fromDate")
    long countByCreatedAtAfter(@Param("fromDate") LocalDateTime fromDate);

    @Query("SELECT COUNT(aj) FROM AnalysisJob aj WHERE aj.status = :status AND aj.createdAt >= :fromDate")
    long countByStatusAndCreatedAtAfter(@Param("status") AnalysisJob.JobStatus status, @Param("fromDate") LocalDateTime fromDate);

    @Query("SELECT COUNT(aj) FROM AnalysisJob aj WHERE aj.status = :status AND aj.updatedAt >= :fromDate")
    long countByStatusAndUpdatedAtAfter(@Param("status") AnalysisJob.JobStatus status, @Param("fromDate") LocalDateTime fromDate);

    @Query("SELECT aj FROM AnalysisJob aj WHERE aj.status = :status AND aj.updatedAt >= :fromDate ORDER BY aj.updatedAt DESC")
    List<AnalysisJob> findByStatusAndUpdatedAtAfter(@Param("status") AnalysisJob.JobStatus status, @Param("fromDate") LocalDateTime fromDate);

    @Query("SELECT COUNT(aj) FROM AnalysisJob aj WHERE aj.createdAt BETWEEN :fromDate AND :toDate")
    long countByCreatedAtBetween(@Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate);
    
    @Query("SELECT AVG(aj.progressPercentage) FROM AnalysisJob aj WHERE aj.status = :status")
    Double getAverageProgressOfProcessingJobs(@Param("status") AnalysisJob.JobStatus status);
    
    // Complex queries with joins
    @Query("SELECT aj FROM AnalysisJob aj LEFT JOIN FETCH aj.documentPages dp WHERE aj.jobId = :jobId")
    Optional<AnalysisJob> findByJobIdWithDocumentPages(@Param("jobId") String jobId);
    
    @Query("SELECT aj FROM AnalysisJob aj LEFT JOIN FETCH aj.cimOutput WHERE aj.jobId = :jobId")
    Optional<AnalysisJob> findByJobIdWithCimOutput(@Param("jobId") String jobId);
    
    @Query("SELECT aj FROM AnalysisJob aj LEFT JOIN FETCH aj.processingLogs WHERE aj.jobId = :jobId")
    Optional<AnalysisJob> findByJobIdWithProcessingLogs(@Param("jobId") String jobId);
    
    @Query("""
        SELECT aj FROM AnalysisJob aj 
        LEFT JOIN FETCH aj.documentPages dp 
        LEFT JOIN FETCH dp.layoutBlocks lb 
        WHERE aj.jobId = :jobId
        """)
    Optional<AnalysisJob> findByJobIdWithFullData(@Param("jobId") String jobId);
    
    // Recent jobs
    @Query("SELECT aj FROM AnalysisJob aj WHERE aj.user.id = :userId ORDER BY aj.createdAt DESC")
    List<AnalysisJob> findRecentJobsByUserId(@Param("userId") Long userId, Pageable pageable);
    
    // Failed jobs for retry
    @Query("""
        SELECT aj FROM AnalysisJob aj 
        WHERE aj.status = :status 
        AND aj.updatedAt >= :since 
        ORDER BY aj.updatedAt DESC
        """)
    List<AnalysisJob> findFailedJobsSince(@Param("status") AnalysisJob.JobStatus status, @Param("since") LocalDateTime since);
    
    // Cleanup queries
    @Query("SELECT aj FROM AnalysisJob aj WHERE aj.status = :status AND aj.completedAt < :before")
    List<AnalysisJob> findOldCompletedJobs(@Param("status") AnalysisJob.JobStatus status, @Param("before") LocalDateTime before);
    
    @Query("SELECT aj FROM AnalysisJob aj WHERE aj.status = :status AND aj.updatedAt < :before")
    List<AnalysisJob> findOldFailedJobs(@Param("status") AnalysisJob.JobStatus status, @Param("before") LocalDateTime before);
    
    // Additional method needed by service
    @Query("SELECT aj FROM AnalysisJob aj WHERE aj.createdAt < :before AND aj.status IN :statuses")
    List<AnalysisJob> findByCreatedAtBeforeAndStatusIn(@Param("before") LocalDateTime before, @Param("statuses") List<AnalysisJob.JobStatus> statuses);
}