package com.smarteye.repository;

import com.smarteye.model.entity.CIMOutput;
import com.smarteye.model.entity.AnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CIMOutputRepository extends JpaRepository<CIMOutput, Long> {
    
    List<CIMOutput> findByAnalysisJobOrderByGeneratedAtDesc(AnalysisJob analysisJob);
    
    List<CIMOutput> findByOutputFormat(String outputFormat);
    
    Optional<CIMOutput> findByAnalysisJobAndOutputFormat(AnalysisJob analysisJob, String outputFormat);
    
    void deleteByAnalysisJob(AnalysisJob analysisJob);
}
