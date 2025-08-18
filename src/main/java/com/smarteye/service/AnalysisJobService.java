package com.smarteye.service;

import com.smarteye.model.entity.AnalysisJob;
import com.smarteye.model.entity.User;
import com.smarteye.repository.AnalysisJobRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 분석 작업 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AnalysisJobService {
    
    private final AnalysisJobRepository analysisJobRepository;
    private final UserService userService;
    private final ProcessingLogService processingLogService;
    
    /**
     * 새로운 분석 작업 생성
     */
    public AnalysisJob createAnalysisJob(User user, String originalFilename, String fileType, 
                                        Long fileSize, String filePath) {
        String jobId = UUID.randomUUID().toString();
        
        AnalysisJob job = AnalysisJob.builder()
                .jobId(jobId)
                .user(user)
                .originalFilename(originalFilename)
                .fileType(fileType)
                .fileSize(fileSize)
                .filePath(filePath)
                .status("PENDING")
                .progress(0)
                .build();
        
        AnalysisJob savedJob = analysisJobRepository.save(job);
        processingLogService.logInfo(savedJob, "JOB_SERVICE", "Created new analysis job");
        
        log.info("Created analysis job: {} for user: {}", jobId, user.getUsername());
        return savedJob;
    }
    
    /**
     * 사용자별 분석 작업 생성 (기본 사용자 자동 생성)
     */
    public AnalysisJob createAnalysisJob(String originalFilename, String fileType, 
                                        Long fileSize, String filePath) {
        User defaultUser = userService.getOrCreateDefaultUser();
        return createAnalysisJob(defaultUser, originalFilename, fileType, fileSize, filePath);
    }
    
    /**
     * 분석 작업 조회
     */
    @Transactional(readOnly = true)
    public AnalysisJob getAnalysisJob(String jobId) {
        Optional<AnalysisJob> job = analysisJobRepository.findByJobId(jobId);
        return job.orElse(null);
    }
    
    /**
     * 모든 분석 작업 조회
     */
    @Transactional(readOnly = true)
    public List<AnalysisJob> getAllAnalysisJobs() {
        return analysisJobRepository.findAllByOrderByCreatedAtDesc();
    }
    
    /**
     * 사용자별 분석 작업 조회
     */
    @Transactional(readOnly = true)
    public List<AnalysisJob> getAnalysisJobsByUser(User user) {
        return analysisJobRepository.findByUserOrderByCreatedAtDesc(user);
    }
    
    /**
     * 분석 작업 상태 업데이트
     */
    public AnalysisJob updateJobStatus(String jobId, String status) {
        Optional<AnalysisJob> jobOpt = analysisJobRepository.findByJobId(jobId);
        if (jobOpt.isPresent()) {
            AnalysisJob job = jobOpt.get();
            String oldStatus = job.getStatus();
            job.setStatus(status);
            
            if ("PROCESSING".equals(status)) {
                job.setStartedAt(LocalDateTime.now());
            } else if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                job.setCompletedAt(LocalDateTime.now());
            }
            
            AnalysisJob savedJob = analysisJobRepository.save(job);
            processingLogService.logInfo(savedJob, "JOB_SERVICE", 
                String.format("Status changed from %s to %s", oldStatus, status));
            
            return savedJob;
        }
        return null;
    }
    
    /**
     * 분석 작업 진행률 업데이트
     */
    public AnalysisJob updateJobProgress(String jobId, int progress) {
        Optional<AnalysisJob> jobOpt = analysisJobRepository.findByJobId(jobId);
        if (jobOpt.isPresent()) {
            AnalysisJob job = jobOpt.get();
            int oldProgress = job.getProgress();
            job.setProgress(progress);
            
            AnalysisJob savedJob = analysisJobRepository.save(job);
            if (progress % 10 == 0 || progress == 100) { // 10% 단위로 로그
                processingLogService.logInfo(savedJob, "JOB_SERVICE", 
                    String.format("Progress updated from %d%% to %d%%", oldProgress, progress));
            }
            
            return savedJob;
        }
        return null;
    }
    
    /**
     * 분석 작업에 메타데이터 업데이트
     */
    public AnalysisJob updateJobMetadata(String jobId, String modelUsed, Integer detectedObjectsCount, 
                                        Double processingTime, String apiProvider) {
        Optional<AnalysisJob> jobOpt = analysisJobRepository.findByJobId(jobId);
        if (jobOpt.isPresent()) {
            AnalysisJob job = jobOpt.get();
            
            if (modelUsed != null) job.setModelUsed(modelUsed);
            if (detectedObjectsCount != null) job.setDetectedObjectsCount(detectedObjectsCount);
            if (processingTime != null) job.setProcessingTime(processingTime);
            if (apiProvider != null) job.setApiProvider(apiProvider);
            
            AnalysisJob savedJob = analysisJobRepository.save(job);
            processingLogService.logInfo(savedJob, "JOB_SERVICE", "Metadata updated");
            
            return savedJob;
        }
        return null;
    }
    
    /**
     * 분석 작업 삭제
     */
    public boolean deleteAnalysisJob(String jobId) {
        Optional<AnalysisJob> jobOpt = analysisJobRepository.findByJobId(jobId);
        if (jobOpt.isPresent()) {
            AnalysisJob job = jobOpt.get();
            processingLogService.logInfo(job, "JOB_SERVICE", "Job deletion requested");
            analysisJobRepository.delete(job);
            log.info("Deleted analysis job: {}", jobId);
            return true;
        }
        return false;
    }
    
    /**
     * 상태별 작업 개수 조회
     */
    @Transactional(readOnly = true)
    public Long countJobsByStatus(String status) {
        return analysisJobRepository.countByStatus(status);
    }
    
    /**
     * 사용자별 상태별 작업 개수 조회
     */
    @Transactional(readOnly = true)
    public Long countJobsByUserAndStatus(User user, String status) {
        return analysisJobRepository.countByUserAndStatus(user, status);
    }
}
