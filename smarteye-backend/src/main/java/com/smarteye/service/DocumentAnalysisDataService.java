package com.smarteye.service;

import com.smarteye.dto.AIDescriptionResult;
import com.smarteye.dto.OCRResult;
import com.smarteye.dto.common.LayoutInfo;
import com.smarteye.entity.*;
import com.smarteye.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 문서 분석 결과를 데이터베이스에 저장하는 서비스
 */
@Service
@Transactional
public class DocumentAnalysisDataService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentAnalysisDataService.class);
    
    @Autowired
    private AnalysisJobRepository analysisJobRepository;
    
    @Autowired
    private DocumentPageRepository documentPageRepository;
    
    @Autowired
    private LayoutBlockRepository layoutBlockRepository;
    
    @Autowired
    private TextBlockRepository textBlockRepository;
    
    @Autowired
    private CIMOutputRepository cimOutputRepository;
    
    @Autowired
    private ProcessingLogRepository processingLogRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 분석 결과를 데이터베이스에 저장
     */
    public void saveAnalysisResults(String jobId, 
                                   List<LayoutInfo> layoutInfo,
                                   List<OCRResult> ocrResults, 
                                   List<AIDescriptionResult> aiResults,
                                   Map<String, Object> cimResult,
                                   String formattedText,
                                   String jsonFilePath,
                                   String layoutImagePath,
                                   long processingTimeMs) {
        try {
            logger.info("분석 결과 DB 저장 시작 - JobID: {}", jobId);
            
            // 1. AnalysisJob 조회
            AnalysisJob analysisJob = analysisJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new RuntimeException("분석 작업을 찾을 수 없습니다: " + jobId));
            
            // 2. DocumentPage 생성 (단일 이미지 분석의 경우)
            DocumentPage documentPage = createDocumentPage(analysisJob);
            
            // 3. LayoutBlock 저장
            saveLayoutBlocks(layoutInfo, documentPage, ocrResults, aiResults);
            
            // 4. CIMOutput 저장
            saveCIMOutput(analysisJob, cimResult, formattedText, jsonFilePath, layoutImagePath, 
                         layoutInfo, ocrResults, aiResults, processingTimeMs);
            
            // 5. ProcessingLog 추가
            addProcessingLog(analysisJob, "ANALYSIS_COMPLETED", 
                           String.format("분석 완료 - 레이아웃: %d개, OCR: %d개, AI: %d개", 
                                       layoutInfo.size(), ocrResults.size(), aiResults.size()),
                           processingTimeMs);
            
            logger.info("분석 결과 DB 저장 완료 - JobID: {}, 레이아웃: {}개, OCR: {}개, AI: {}개", 
                       jobId, layoutInfo.size(), ocrResults.size(), aiResults.size());
            
        } catch (Exception e) {
            logger.error("분석 결과 DB 저장 실패 - JobID: {}", jobId, e);
            throw new RuntimeException("분석 결과 저장 중 오류 발생", e);
        }
    }
    
    /**
     * DocumentPage 생성
     */
    private DocumentPage createDocumentPage(AnalysisJob analysisJob) {
        DocumentPage documentPage = new DocumentPage();
        documentPage.setAnalysisJob(analysisJob);
        documentPage.setPageNumber(1); // 단일 이미지는 페이지 1
        documentPage.setImagePath(analysisJob.getFilePath()); // 업로드된 이미지 경로
        documentPage.setImageWidth(null); // 실제 이미지 크기 정보가 있다면 설정
        documentPage.setImageHeight(null);
        documentPage.setProcessingStatus(DocumentPage.ProcessingStatus.COMPLETED);
        
        return documentPageRepository.save(documentPage);
    }
    
    /**
     * LayoutBlock들을 데이터베이스에 저장
     */
    private void saveLayoutBlocks(List<LayoutInfo> layoutInfo, 
                                 DocumentPage documentPage,
                                 List<OCRResult> ocrResults, 
                                 List<AIDescriptionResult> aiResults) {
        
        logger.info("LayoutBlock 저장 시작 - 총 {}개", layoutInfo.size());
        
        for (int i = 0; i < layoutInfo.size(); i++) {
            LayoutInfo layout = layoutInfo.get(i);
            
            // LayoutBlock 생성
            LayoutBlock layoutBlock = new LayoutBlock();
            layoutBlock.setDocumentPage(documentPage);
            layoutBlock.setBlockIndex(layout.getId());
            layoutBlock.setClassName(layout.getClassName());
            layoutBlock.setConfidence(layout.getConfidence());
            
            // 좌표 설정
            int[] box = layout.getBox();
            if (box.length >= 4) {
                layoutBlock.setX1(box[0]);
                layoutBlock.setY1(box[1]);
                layoutBlock.setX2(box[2]);
                layoutBlock.setY2(box[3]);
            }
            
            // OCR 결과 매핑
            OCRResult ocrResult = findOCRByLayoutId(layout.getId(), ocrResults);
            if (ocrResult != null) {
                layoutBlock.setOcrText(ocrResult.getText());
                layoutBlock.setOcrConfidence(90.0); // OCR 신뢰도 기본값
                layoutBlock.setProcessingStatus(LayoutBlock.ProcessingStatus.OCR_COMPLETED);
            }
            
            // AI 설명 매핑
            AIDescriptionResult aiResult = findAIByLayoutId(layout.getId(), aiResults);
            if (aiResult != null) {
                layoutBlock.setAiDescription(aiResult.getDescription());
                layoutBlock.setProcessingStatus(LayoutBlock.ProcessingStatus.AI_COMPLETED);
            }
            
            if (layoutBlock.getProcessingStatus() == null) {
                layoutBlock.setProcessingStatus(LayoutBlock.ProcessingStatus.LAYOUT_DETECTED);
            }
            
            // LayoutBlock 저장
            LayoutBlock savedLayoutBlock = layoutBlockRepository.save(layoutBlock);
            
            // TextBlock 생성 (OCR 텍스트가 있는 경우)
            if (ocrResult != null && ocrResult.getText() != null && !ocrResult.getText().trim().isEmpty()) {
                createTextBlock(savedLayoutBlock, ocrResult);
            }
        }
        
        logger.info("LayoutBlock 저장 완료 - 총 {}개", layoutInfo.size());
    }
    
    /**
     * TextBlock 생성 및 저장
     */
    private void createTextBlock(LayoutBlock layoutBlock, OCRResult ocrResult) {
        TextBlock textBlock = new TextBlock(ocrResult.getText());
        textBlock.setLayoutBlock(layoutBlock);
        textBlock.setConfidence(90.0); // OCR 신뢰도
        textBlock.setLanguage("kor");
        textBlock.inferTextType(); // 클래스명 기반으로 텍스트 타입 추론
        
        textBlockRepository.save(textBlock);
        
        // LayoutBlock에 연결
        layoutBlock.setTextBlock(textBlock);
        layoutBlockRepository.save(layoutBlock);
    }
    
    /**
     * CIMOutput 저장
     */
    private void saveCIMOutput(AnalysisJob analysisJob,
                              Map<String, Object> cimResult,
                              String formattedText,
                              String jsonFilePath,
                              String layoutImagePath,
                              List<LayoutInfo> layoutInfo,
                              List<OCRResult> ocrResults,
                              List<AIDescriptionResult> aiResults,
                              long processingTimeMs) {
        try {
            CIMOutput cimOutput = new CIMOutput();
            cimOutput.setAnalysisJob(analysisJob);
            
            // CIM 데이터를 JSON 문자열로 저장
            cimOutput.setCimData(objectMapper.writeValueAsString(cimResult));
            cimOutput.setFormattedText(formattedText);
            cimOutput.setJsonFilePath(jsonFilePath);
            cimOutput.setLayoutVisualizationPath(layoutImagePath);
            
            // 통계 정보 설정
            cimOutput.setTotalElements(layoutInfo.size());
            cimOutput.setTextElements(ocrResults.size());
            cimOutput.setAiDescribedElements(aiResults.size());
            
            // 클래스별 통계
            long figureCount = layoutInfo.stream().filter(l -> "figure".equals(l.getClassName())).count();
            long tableCount = layoutInfo.stream().filter(l -> "table".equals(l.getClassName())).count();
            cimOutput.setTotalFigures((int) figureCount);
            cimOutput.setTotalTables((int) tableCount);
            
            // 텍스트 통계
            int totalWords = ocrResults.stream().mapToInt(ocr -> 
                ocr.getText() != null ? ocr.getText().split("\\s+").length : 0).sum();
            int totalChars = ocrResults.stream().mapToInt(ocr -> 
                ocr.getText() != null ? ocr.getText().length() : 0).sum();
            cimOutput.setTotalWordCount(totalWords);
            cimOutput.setTotalCharCount(totalChars);
            
            cimOutput.setProcessingTimeMs(processingTimeMs);
            cimOutput.setGenerationStatus(CIMOutput.GenerationStatus.COMPLETED);
            
            cimOutputRepository.save(cimOutput);
            
            // AnalysisJob에 CIMOutput 연결
            analysisJob.setCimOutput(cimOutput);
            analysisJobRepository.save(analysisJob);
            
            logger.info("CIMOutput 저장 완료 - 총 요소: {}, 텍스트: {}, AI 설명: {}", 
                       layoutInfo.size(), ocrResults.size(), aiResults.size());
            
        } catch (Exception e) {
            logger.error("CIMOutput 저장 실패", e);
            throw new RuntimeException("CIMOutput 저장 중 오류 발생", e);
        }
    }
    
    /**
     * ProcessingLog 추가
     */
    private void addProcessingLog(AnalysisJob analysisJob, String step, String message, long executionTimeMs) {
        ProcessingLog log = ProcessingLog.info(step, message);
        log.setAnalysisJob(analysisJob);
        log.setExecutionTimeMs(executionTimeMs);
        
        processingLogRepository.save(log);
    }
    
    /**
     * 레이아웃 ID로 OCR 결과 찾기
     */
    private OCRResult findOCRByLayoutId(int layoutId, List<OCRResult> ocrResults) {
        return ocrResults.stream()
            .filter(ocr -> ocr.getId() == layoutId)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 레이아웃 ID로 AI 설명 찾기
     */
    private AIDescriptionResult findAIByLayoutId(int layoutId, List<AIDescriptionResult> aiResults) {
        return aiResults.stream()
            .filter(ai -> ai.getId() == layoutId)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 다중 페이지 분석을 위한 개별 페이지 분석 결과 저장
     */
    public DocumentPage savePageAnalysisResult(AnalysisJob analysisJob,
                                             int pageNumber,
                                             String imagePath,
                                             List<LayoutInfo> layoutInfo,
                                             List<OCRResult> ocrResults,
                                             List<AIDescriptionResult> aiResults,
                                             String formattedText,
                                             String jsonFilePath,
                                             String layoutImagePath,
                                             long processingTimeMs) {
        try {
            logger.info("페이지 분석 결과 DB 저장 시작 - JobID: {}, 페이지: {}", analysisJob.getJobId(), pageNumber);
            
            // 1. DocumentPage 생성
            DocumentPage documentPage = new DocumentPage();
            documentPage.setAnalysisJob(analysisJob);
            documentPage.setPageNumber(pageNumber);
            documentPage.setImagePath(imagePath);
            documentPage.setLayoutVisualizationPath(layoutImagePath);
            documentPage.setProcessingStatus(DocumentPage.ProcessingStatus.COMPLETED);
            documentPage.setProcessingTimeMs(processingTimeMs);
            documentPage = documentPageRepository.save(documentPage);
            
            // 2. LayoutBlock 저장
            saveLayoutBlocks(layoutInfo, documentPage, ocrResults, aiResults);
            
            // 3. CIMOutput 저장 (페이지별)
            saveCIMOutput(analysisJob, createPageCIMResult(layoutInfo, ocrResults, aiResults), 
                         formattedText, jsonFilePath, layoutImagePath, 
                         layoutInfo, ocrResults, aiResults, processingTimeMs);
            
            // 4. ProcessingLog 추가
            addProcessingLog(analysisJob, "PAGE_ANALYSIS_COMPLETED", 
                           String.format("페이지 %d 분석 완료 - 레이아웃: %d개, OCR: %d개, AI: %d개", 
                                       pageNumber, layoutInfo.size(), ocrResults.size(), aiResults.size()),
                           processingTimeMs);
            
            logger.info("페이지 분석 결과 DB 저장 완료 - JobID: {}, 페이지: {}, 레이아웃: {}개", 
                       analysisJob.getJobId(), pageNumber, layoutInfo.size());
            
            return documentPage;
            
        } catch (Exception e) {
            logger.error("페이지 분석 결과 DB 저장 실패 - JobID: {}, 페이지: {}", analysisJob.getJobId(), pageNumber, e);
            throw new RuntimeException("페이지 분석 결과 저장 중 오류 발생", e);
        }
    }
    
    /**
     * 페이지별 CIM 결과 생성
     */
    private Map<String, Object> createPageCIMResult(List<LayoutInfo> layoutInfo,
                                                   List<OCRResult> ocrResults,
                                                   List<AIDescriptionResult> aiResults) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("layout_info", layoutInfo);
        result.put("ocr_results", ocrResults);
        result.put("ai_results", aiResults);
        result.put("total_elements", layoutInfo.size());
        result.put("text_elements", ocrResults.size());
        result.put("ai_described_elements", aiResults.size());
        return result;
    }
}
