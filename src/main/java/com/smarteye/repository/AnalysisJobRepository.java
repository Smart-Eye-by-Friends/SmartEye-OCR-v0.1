package com.smarteye.repository;

import com.smarteye.model.entity.AnalysisJob;
import com.smarteye.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {
    
    Optional<AnalysisJob> findByJobId(String jobId);
    
    List<AnalysisJob> findAllByOrderByCreatedAtDesc();
    
    List<AnalysisJob> findByStatus(String status);
    
    List<AnalysisJob> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    // 사용자별 조회
    List<AnalysisJob> findByUserOrderByCreatedAtDesc(User user);
    
    List<AnalysisJob> findByUserAndStatus(User user, String status);
    
    @Query("SELECT aj FROM AnalysisJob aj WHERE aj.status = :status ORDER BY aj.createdAt DESC")
    List<AnalysisJob> findByStatusOrderByCreatedAtDesc(String status);
    
    @Query("SELECT COUNT(aj) FROM AnalysisJob aj WHERE aj.status = :status")
    Long countByStatus(String status);
    
    @Query("SELECT COUNT(aj) FROM AnalysisJob aj WHERE aj.user = :user AND aj.status = :status")
    Long countByUserAndStatus(User user, String status);
}
