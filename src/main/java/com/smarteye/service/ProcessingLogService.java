package com.smarteye.service;

import com.smarteye.model.entity.ProcessingLog;
import com.smarteye.model.entity.AnalysisJob;
import com.smarteye.repository.ProcessingLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProcessingLogService {
    
    private final ProcessingLogRepository processingLogRepository;
    
    public ProcessingLog createLog(AnalysisJob analysisJob, String moduleName, String logLevel, 
                                  String message, String sessionId, String metadata) {
        ProcessingLog processingLog = ProcessingLog.builder()
                .analysisJob(analysisJob)
                .moduleName(moduleName)
                .logLevel(logLevel)
                .message(message)
                .sessionId(sessionId)
                .metadata(metadata)
                .timestamp(LocalDateTime.now())
                .build();
        
        return processingLogRepository.save(processingLog);
    }
    
    public void logInfo(AnalysisJob analysisJob, String moduleName, String message) {
        createLog(analysisJob, moduleName, "INFO", message, null, null);
        log.info("[{}] {}: {}", analysisJob.getJobId(), moduleName, message);
    }
    
    public void logDebug(AnalysisJob analysisJob, String moduleName, String message) {
        createLog(analysisJob, moduleName, "DEBUG", message, null, null);
        log.debug("[{}] {}: {}", analysisJob.getJobId(), moduleName, message);
    }
    
    public void logWarn(AnalysisJob analysisJob, String moduleName, String message) {
        createLog(analysisJob, moduleName, "WARN", message, null, null);
        log.warn("[{}] {}: {}", analysisJob.getJobId(), moduleName, message);
    }
    
    public void logError(AnalysisJob analysisJob, String moduleName, String message) {
        createLog(analysisJob, moduleName, "ERROR", message, null, null);
        log.error("[{}] {}: {}", analysisJob.getJobId(), moduleName, message);
    }
    
    public void logError(AnalysisJob analysisJob, String moduleName, String message, Exception e) {
        String errorMessage = message + " - Exception: " + e.getMessage();
        createLog(analysisJob, moduleName, "ERROR", errorMessage, null, null);
        log.error("[{}] {}: {}", analysisJob.getJobId(), moduleName, errorMessage, e);
    }
    
    @Transactional(readOnly = true)
    public List<ProcessingLog> getLogsByJob(AnalysisJob analysisJob) {
        return processingLogRepository.findByAnalysisJobOrderByTimestampDesc(analysisJob);
    }
    
    @Transactional(readOnly = true)
    public List<ProcessingLog> getLogsBySession(String sessionId) {
        return processingLogRepository.findBySessionIdOrderByTimestampDesc(sessionId);
    }
    
    @Transactional(readOnly = true)
    public List<ProcessingLog> getLogsByModule(String moduleName) {
        return processingLogRepository.findByModuleNameOrderByTimestampDesc(moduleName);
    }
    
    @Transactional(readOnly = true)
    public List<ProcessingLog> getLogsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return processingLogRepository.findByTimestampBetween(startTime, endTime);
    }
    
    public void deleteLogsByJob(AnalysisJob analysisJob) {
        processingLogRepository.deleteByAnalysisJob(analysisJob);
        log.info("Deleted processing logs for job: {}", analysisJob.getJobId());
    }
}
