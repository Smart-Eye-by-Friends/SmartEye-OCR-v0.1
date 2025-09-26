package com.smarteye.application.analysis;

import com.smarteye.domain.analysis.AnalysisJob;
import com.smarteye.domain.user.User;
import com.smarteye.infrastructure.persistence.AnalysisJobRepository;
import com.smarteye.infrastructure.persistence.UserRepository;
import com.smarteye.exception.DocumentAnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.smarteye.application.analysis.AnalysisJobService;
import com.smarteye.application.user.UserService;
import com.smarteye.domain.document.DocumentPage;
import com.smarteye.infrastructure.external.*;
import com.smarteye.application.file.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 분석 작업 관리 서비스
 */
@Service
@Transactional
public class AnalysisJobService {
    
    private static final Logger logger = LoggerFactory.getLogger(AnalysisJobService.class);
    
    @Autowired
    private AnalysisJobRepository analysisJobRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * 새로운 분석 작업 생성 (MultipartFile 버전)
     */
    public AnalysisJob createAnalysisJob(MultipartFile file, User user) {
        logger.info("새 분석 작업 생성 - 사용자: {}, 파일: {}", 
                   user != null ? user.getUsername() : "anonymous", file.getOriginalFilename());
        
        String jobId = UUID.randomUUID().toString();
        
        AnalysisJob job = new AnalysisJob(jobId, file.getOriginalFilename(), user);
        job.setFileSize(file.getSize());
        job.setFileType(getFileTypeFromFilename(file.getOriginalFilename()));
        job.setStatus(AnalysisJob.JobStatus.PENDING);
        
        job = analysisJobRepository.save(job);
        
        logger.info("분석 작업 생성 완료: {} (ID: {})", job.getJobId(), job.getId());
        return job;
    }
    
    /**
     * 새로운 분석 작업 생성
     */
    public AnalysisJob createAnalysisJob(Long userId, String originalFilename, String filePath, 
                                        Long fileSize, String fileType, String modelChoice) {
        
        logger.info("새 분석 작업 생성 - 사용자: {}, 파일: {}", userId, originalFilename);
        
        // 사용자 조회 (없으면 null로 설정)
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                logger.warn("사용자를 찾을 수 없습니다: {}. 사용자 없이 작업을 생성합니다.", userId);
            }
        } else {
            logger.info("사용자 ID가 제공되지 않았습니다. 익명 작업으로 생성합니다.");
        }
        
        AnalysisJob job = new AnalysisJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setUser(user);  // null일 수 있음
        job.setOriginalFilename(originalFilename);
        job.setFilePath(filePath);
        job.setFileSize(fileSize);
        job.setFileType(fileType);
        job.setModelChoice(modelChoice);
        job.setStatus(AnalysisJob.JobStatus.PENDING);
        job.setProgressPercentage(0);
        
        AnalysisJob savedJob = analysisJobRepository.save(job);
        logger.info("분석 작업 생성 완료 - ID: {}, JobID: {}", savedJob.getId(), savedJob.getJobId());
        
        return savedJob;
    }
    
    /**
     * 분석 작업 처리 (Book 서비스를 위한 메서드)
     */
    public void processAnalysisJob(AnalysisJob job, String modelChoice, String apiKey) {
        logger.info("분석 작업 처리 시작: {}", job.getJobId());
        
        try {
            job.setStatus(AnalysisJob.JobStatus.PROCESSING);
            job.setModelChoice(modelChoice);
            job.setUseAiDescription(apiKey != null);
            analysisJobRepository.save(job);
            
            // 실제 분석 로직은 DocumentAnalysisController에서 구현된 것을 활용
            // 여기서는 상태만 업데이트
            job.setStatus(AnalysisJob.JobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            job.setProgressPercentage(100);
            analysisJobRepository.save(job);
            
            logger.info("분석 작업 처리 완료: {}", job.getJobId());
            
        } catch (Exception e) {
            logger.error("분석 작업 처리 실패: {} - {}", job.getJobId(), e.getMessage());
            job.setStatus(AnalysisJob.JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            analysisJobRepository.save(job);
            throw e;
        }
    }
    
    /**
     * 파일 확장자에서 파일 타입 추출
     */
    private String getFileTypeFromFilename(String filename) {
        if (filename == null) return "UNKNOWN";
        
        String extension = "";
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = filename.substring(lastDotIndex + 1).toUpperCase();
        }
        
        switch (extension) {
            case "PDF": return "PDF";
            case "JPG":
            case "JPEG": return "JPEG";
            case "PNG": return "PNG";
            case "BMP": return "BMP";
            case "TIFF":
            case "TIF": return "TIFF";
            default: return "UNKNOWN";
        }
    }
    
    /**
     * 작업 ID로 분석 작업 조회
     */
    @Transactional(readOnly = true)
    public Optional<AnalysisJob> getAnalysisJobByJobId(String jobId) {
        return analysisJobRepository.findByJobId(jobId);
    }
    
    /**
     * 분석 작업 상태 업데이트
     */
    public AnalysisJob updateJobStatus(String jobId, AnalysisJob.JobStatus status, 
                                      Integer progressPercentage, String errorMessage) {
        
        AnalysisJob job = analysisJobRepository.findByJobId(jobId)
            .orElseThrow(() -> new DocumentAnalysisException("분석 작업을 찾을 수 없습니다: " + jobId));
        
        job.setStatus(status);
        if (progressPercentage != null) {
            job.setProgressPercentage(progressPercentage);
        }
        if (errorMessage != null) {
            job.setErrorMessage(errorMessage);
        }
        if (status == AnalysisJob.JobStatus.COMPLETED || status == AnalysisJob.JobStatus.FAILED) {
            job.setCompletedAt(LocalDateTime.now());
        }
        
        AnalysisJob updatedJob = analysisJobRepository.save(job);
        logger.info("작업 상태 업데이트 - JobID: {}, Status: {}, Progress: {}%", 
                   jobId, status, progressPercentage);
        
        return updatedJob;
    }
    
    /**
     * 사용자별 분석 작업 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<AnalysisJob> getAnalysisJobsByUser(Long userId, Pageable pageable) {
        return analysisJobRepository.findByUserId(userId, pageable);
    }
    
    /**
     * 사용자별 특정 상태의 분석 작업 목록 조회
     */
    @Transactional(readOnly = true)
    public List<AnalysisJob> getAnalysisJobsByUserAndStatus(Long userId, AnalysisJob.JobStatus status) {
        return analysisJobRepository.findByUserIdAndStatus(userId, status);
    }
    
    /**
     * 특정 상태의 모든 분석 작업 조회
     */
    @Transactional(readOnly = true)
    public List<AnalysisJob> getAnalysisJobsByStatus(AnalysisJob.JobStatus status) {
        return analysisJobRepository.findByStatus(status);
    }
    
    /**
     * 오래된 작업 정리
     */
    public int cleanupOldJobs(int daysOld) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysOld);
        List<AnalysisJob> oldJobs = analysisJobRepository.findByCreatedAtBeforeAndStatusIn(
            cutoffDate, List.of(AnalysisJob.JobStatus.COMPLETED, AnalysisJob.JobStatus.FAILED)
        );
        
        for (AnalysisJob job : oldJobs) {
            analysisJobRepository.delete(job);
        }
        
        logger.info("오래된 분석 작업 정리 완료 - 삭제된 작업 수: {}", oldJobs.size());
        return oldJobs.size();
    }
    
    /**
     * 작업 진행률 업데이트
     */
    public void updateJobProgress(String jobId, int progressPercentage, String message) {
        AnalysisJob job = analysisJobRepository.findByJobId(jobId)
            .orElseThrow(() -> new DocumentAnalysisException("분석 작업을 찾을 수 없습니다: " + jobId));
        
        job.setProgressPercentage(progressPercentage);
        if (message != null) {
            job.setErrorMessage(message); // 진행 메시지도 여기에 저장
        }
        
        if (progressPercentage >= 100) {
            job.setStatus(AnalysisJob.JobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
        }
        
        analysisJobRepository.save(job);
        
        logger.debug("작업 진행률 업데이트 - JobID: {}, Progress: {}%", jobId, progressPercentage);
    }
    
    /**
     * CIMOutput에서 layoutVisualizationPath 가져오기
     */
    @Transactional(readOnly = true)
    public String getCIMOutputLayoutVisualizationPath(String jobId) {
        Optional<AnalysisJob> jobOptional = analysisJobRepository.findByJobId(jobId);
        
        if (jobOptional.isPresent() && jobOptional.get().getCimOutput() != null) {
            String layoutPath = jobOptional.get().getCimOutput().getLayoutVisualizationPath();
            logger.debug("CIM 레이아웃 시각화 경로 조회 - JobID: {}, Path: {}", jobId, layoutPath);
            return layoutPath;
        }
        
        logger.debug("CIM 출력을 찾을 수 없음 - JobID: {}", jobId);
        return null;
    }
}