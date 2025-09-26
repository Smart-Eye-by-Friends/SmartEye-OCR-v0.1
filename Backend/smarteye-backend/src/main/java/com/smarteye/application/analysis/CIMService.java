package com.smarteye.application.analysis;

import com.smarteye.presentation.dto.AIDescriptionResult;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.domain.analysis.AnalysisJob;
import com.smarteye.domain.analysis.CIMOutput;
import com.smarteye.domain.document.DocumentPage;
import com.smarteye.infrastructure.persistence.AnalysisJobRepository;
import com.smarteye.application.analysis.AnalysisJobService;
import com.smarteye.application.user.UserService;
import com.smarteye.domain.document.DocumentPage;
import com.smarteye.infrastructure.external.*;
import com.smarteye.application.file.*;
import com.smarteye.infrastructure.persistence.CIMOutputRepository;
import com.smarteye.infrastructure.persistence.DocumentPageRepository;
import com.smarteye.application.analysis.UnifiedAnalysisEngine.UnifiedAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/**
 * CIM (Common Information Model) 통합 서비스
 * 통합된 UnifiedAnalysisEngine을 사용하여 분석 워크플로우를 처리합니다.
 */
@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class CIMService {
    
    private static final Logger logger = LoggerFactory.getLogger(CIMService.class);
    
    @Autowired
    private UnifiedAnalysisEngine unifiedAnalysisEngine; // 통합된 분석 엔진

    @Autowired
    private com.smarteye.infrastructure.external.OCRService ocrService;
    
    @Autowired
    private com.smarteye.infrastructure.external.AIDescriptionService aiDescriptionService;
    
    @Autowired
    private com.smarteye.infrastructure.external.LAMServiceClient lamServiceClient;
    
    @Autowired
    private com.smarteye.application.file.ImageProcessingService imageProcessingService;
    
    @Autowired
    private DocumentAnalysisDataService documentAnalysisDataService;
    
    @Autowired
    private CIMOutputRepository cimOutputRepository;
    
    @Autowired
    private com.smarteye.infrastructure.persistence.DocumentPageRepository documentPageRepository;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AnalysisJobRepository analysisJobRepository;

    /**
     * 통합된 분석 엔진을 사용하여 구조화된 분석을 수행하고 CIM으로 통합 처리
     */
    public UnifiedAnalysisResult performUnifiedAnalysisWithCIM(BufferedImage image, 
                                                               AnalysisJob analysisJob, 
                                                               String modelChoice, 
                                                               String apiKey) {
        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("통합 분석 워크플로우 시작 - JobID: {}, 모델: {}", analysisJob.getJobId(), modelChoice);
            
            // 1. LAM, OCR, AI 분석 수행 (기존 로직과 유사)
            var layoutResult = lamServiceClient.analyzeLayout(image, modelChoice).get();
            if (layoutResult.getLayoutInfo().isEmpty()) {
                throw new RuntimeException("레이아웃 분석에 실패했습니다.");
            }
            List<OCRResult> ocrResults = ocrService.performOCR(image, layoutResult.getLayoutInfo());
            List<AIDescriptionResult> aiResults = (apiKey != null && !apiKey.trim().isEmpty())
                ? aiDescriptionService.generateDescriptions(image, layoutResult.getLayoutInfo(), apiKey).get()
                : List.of();

            // 2. 통합 분석 엔진 호출
            UnifiedAnalysisResult analysisResult = unifiedAnalysisEngine.performUnifiedAnalysis(
                layoutResult.getLayoutInfo(), ocrResults, aiResults
            );

            // 3. 결과 DB 저장
            String layoutVisualizationPath = imageProcessingService.generateAndSaveLayoutVisualization(image, layoutResult.getLayoutInfo(), analysisJob.getJobId());
            saveUnifiedResultToDatabase(analysisJob, analysisResult, layoutResult.getLayoutInfo(), ocrResults, aiResults, layoutVisualizationPath, System.currentTimeMillis() - startTime);
            
            logger.info("통합 분석 워크플로우 완료 - JobID: {}, 처리시간: {}ms", 
                       analysisJob.getJobId(), System.currentTimeMillis() - startTime);
            
            return analysisResult;
            
        } catch (Exception e) {
            logger.error("통합 분석 워크플로우 실패 - JobID: {}", analysisJob.getJobId(), e);
            throw new RuntimeException("통합 분석 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 통합된 분석 결과를 데이터베이스에 저장
     */
    @Transactional(rollbackFor = Exception.class)
    private void saveUnifiedResultToDatabase(AnalysisJob analysisJob,
                                             UnifiedAnalysisResult analysisResult,
                                             List<LayoutInfo> layoutInfo,
                                             List<OCRResult> ocrResults,
                                             List<AIDescriptionResult> aiResults,
                                             String layoutVisualizationPath,
                                             long processingTimeMs) {
        try {
            DocumentPage documentPage = createOrUpdateDocumentPage(analysisJob);
            
            // DocumentAnalysisDataService를 사용하여 기본 블록 정보 저장 (7개 매개변수)
            documentAnalysisDataService.saveAnalysisResultsBatch(
                analysisJob.getJobId(),
                layoutInfo,
                ocrResults,
                aiResults,
                analysisResult.getCimData(),
                "Formatted text from unified analysis",
                processingTimeMs
            );

            // CIMOutput 저장
            CIMOutput cimOutput = cimOutputRepository.findByAnalysisJobId(analysisJob.getId())
                .orElse(new CIMOutput());
            
            cimOutput.setAnalysisJob(analysisJob);
            cimOutput.setCimData(objectMapper.writeValueAsString(analysisResult.getCimData()));
            cimOutput.setFormattedText("Formatted text from unified analysis");
            cimOutput.setLayoutVisualizationPath(layoutVisualizationPath);
            cimOutput.setProcessingTimeMs(processingTimeMs);
            cimOutput.setGenerationStatus(CIMOutput.GenerationStatus.COMPLETED);
            
            cimOutputRepository.save(cimOutput);

        } catch (Exception e) {
            logger.error("통합 분석 결과 DB 저장 실패 - JobID: {}", analysisJob.getJobId(), e);
            throw new RuntimeException("DB 저장 중 오류 발생: " + e.getMessage(), e);
        }
    }

    private com.smarteye.domain.document.DocumentPage createOrUpdateDocumentPage(AnalysisJob analysisJob) {
        Optional<DocumentPage> existingPage = documentPageRepository
            .findByAnalysisJobAndPageNumber(analysisJob, 1);

        DocumentPage documentPage;
        if (existingPage.isPresent()) {
            documentPage = existingPage.get();
        } else {
            documentPage = new DocumentPage();
            documentPage.setAnalysisJob(analysisJob);
            documentPage.setPageNumber(1);
        }

        documentPage.setImagePath(analysisJob.getFilePath());
        documentPage.setProcessingStatus(DocumentPage.ProcessingStatus.COMPLETED);

        return documentPageRepository.save(documentPage);
    }
}