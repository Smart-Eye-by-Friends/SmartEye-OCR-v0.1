package com.smarteye.repository;

import com.smarteye.model.entity.ProcessingLog;
import com.smarteye.model.entity.AnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProcessingLogRepository extends JpaRepository<ProcessingLog, Long> {
    
    List<ProcessingLog> findByAnalysisJobOrderByTimestampDesc(AnalysisJob analysisJob);
    
    List<ProcessingLog> findBySessionIdOrderByTimestampDesc(String sessionId);
    
    List<ProcessingLog> findByModuleNameOrderByTimestampDesc(String moduleName);
    
    List<ProcessingLog> findByLogLevelOrderByTimestampDesc(String logLevel);
    
    @Query("SELECT p FROM ProcessingLog p WHERE p.timestamp BETWEEN :startTime AND :endTime ORDER BY p.timestamp DESC")
    List<ProcessingLog> findByTimestampBetween(@Param("startTime") LocalDateTime startTime, 
                                              @Param("endTime") LocalDateTime endTime);
    
    void deleteByAnalysisJob(AnalysisJob analysisJob);
}
