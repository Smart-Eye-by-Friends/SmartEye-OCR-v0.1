package com.smarteye.service;

import com.smarteye.model.entity.CIMOutput;
import com.smarteye.model.entity.AnalysisJob;
import com.smarteye.repository.CIMOutputRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CIMOutputService {
    
    private final CIMOutputRepository cimOutputRepository;
    
    public CIMOutput createOutput(AnalysisJob analysisJob, String outputContent, String outputFormat, 
                                 String filePath, String integrationMethod) {
        CIMOutput cimOutput = CIMOutput.builder()
                .analysisJob(analysisJob)
                .outputContent(outputContent)
                .outputFormat(outputFormat)
                .filePath(filePath)
                .integrationMethod(integrationMethod)
                .build();
        
        CIMOutput savedOutput = cimOutputRepository.save(cimOutput);
        log.info("Created CIM output for job: {} in format: {}", analysisJob.getJobId(), outputFormat);
        return savedOutput;
    }
    
    public CIMOutput createJsonOutput(AnalysisJob analysisJob, String jsonContent, String integrationMethod) {
        return createOutput(analysisJob, jsonContent, "JSON", null, integrationMethod);
    }
    
    public CIMOutput createMarkdownOutput(AnalysisJob analysisJob, String markdownContent, String integrationMethod) {
        return createOutput(analysisJob, markdownContent, "MARKDOWN", null, integrationMethod);
    }
    
    public CIMOutput createFileOutput(AnalysisJob analysisJob, String filePath, String outputFormat, String integrationMethod) {
        return createOutput(analysisJob, null, outputFormat, filePath, integrationMethod);
    }
    
    @Transactional(readOnly = true)
    public List<CIMOutput> getOutputsByJob(AnalysisJob analysisJob) {
        return cimOutputRepository.findByAnalysisJobOrderByGeneratedAtDesc(analysisJob);
    }
    
    @Transactional(readOnly = true)
    public Optional<CIMOutput> getOutputByJobAndFormat(AnalysisJob analysisJob, String outputFormat) {
        return cimOutputRepository.findByAnalysisJobAndOutputFormat(analysisJob, outputFormat);
    }
    
    @Transactional(readOnly = true)
    public List<CIMOutput> getOutputsByFormat(String outputFormat) {
        return cimOutputRepository.findByOutputFormat(outputFormat);
    }
    
    public CIMOutput updateOutput(Long outputId, String outputContent, String filePath) {
        CIMOutput cimOutput = cimOutputRepository.findById(outputId)
                .orElseThrow(() -> new IllegalArgumentException("CIM Output not found: " + outputId));
        
        if (outputContent != null) {
            cimOutput.setOutputContent(outputContent);
        }
        if (filePath != null) {
            cimOutput.setFilePath(filePath);
        }
        
        CIMOutput updatedOutput = cimOutputRepository.save(cimOutput);
        log.info("Updated CIM output: {}", outputId);
        return updatedOutput;
    }
    
    public void deleteOutput(Long outputId) {
        CIMOutput cimOutput = cimOutputRepository.findById(outputId)
                .orElseThrow(() -> new IllegalArgumentException("CIM Output not found: " + outputId));
        
        cimOutputRepository.delete(cimOutput);
        log.info("Deleted CIM output: {}", outputId);
    }
    
    public void deleteOutputsByJob(AnalysisJob analysisJob) {
        cimOutputRepository.deleteByAnalysisJob(analysisJob);
        log.info("Deleted CIM outputs for job: {}", analysisJob.getJobId());
    }
}
