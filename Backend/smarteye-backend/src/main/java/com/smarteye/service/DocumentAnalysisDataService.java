package com.smarteye.service;

import com.smarteye.config.SmartEyeProperties;
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

import java.util.List;
import java.util.Map;

/**
 * 문서 분석 결과를 데이터베이스에 저장하는 서비스
 * Level 5: 통합 CIM 생성 지원 및 메모리 최적화 적용
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

    @Autowired
    private CIMService cimService;

    @Autowired
    private SmartEyeProperties smartEyeProperties;
    
    @Autowired
    private MemoryService memoryService;

    /**
     * 분석 결과를 데이터베이스에 저장 (단일 이미지용)
     */
    public void saveAnalysisResults(String jobId,
                                   List<LayoutInfo> layoutInfo,
                                   List<OCRResult> ocrResults,
                                   List<AIDescriptionResult> aiResults,
                                   String jsonFilePath,
                                   String layoutImagePath,
                                   long processingTimeMs) {
        try {
            logger.info("분석 결과 DB 저장 시작 - JobID: {}", jobId);

            AnalysisJob analysisJob = analysisJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new RuntimeException("분석 작업을 찾을 수 없습니다: " + jobId));

            DocumentPage documentPage = createDocumentPage(analysisJob);
            saveLayoutBlocks(layoutInfo, documentPage, ocrResults, aiResults);

            // Level 5: 통합 CIM 생성 적용
            if (smartEyeProperties.getProcessing().isAutoGenerateCim()) {
                try {
                    CIMOutput cimOutput;
                    
                    // 메모리 최적화 모드 체크
                    if (memoryService.isUnifiedCIMEnabled()) {
                        logger.info("통합 CIM 생성 모드 사용 - Job ID: {}", analysisJob.getId());
                        
                        // 통합 CIM 생성 (DB 중복 읽기 없이 메모리 데이터 사용)
                        Map<String, Object> completeCIM = cimService.generateOptimizedCompleteCIM(
                            analysisJob.getId(), layoutInfo, ocrResults, aiResults);
                        
                        // 통합 CIM 결과를 기본 + 구조화 데이터 모두 저장
                        cimOutput = createUnifiedCIMOutput(analysisJob, jsonFilePath, layoutImagePath, 
                            processingTimeMs, completeCIM, layoutInfo, ocrResults, aiResults);
                        
                        logger.info("통합 CIM 생성 완료 (메모리 최적화) - Job ID: {}", analysisJob.getId());
                        
                    } else {
                        logger.info("기존 방식 CIM 생성 - Job ID: {}", analysisJob.getId());
                        
                        // 기존 방식: 초기 CIM + 별도 구조화 CIM
                        cimOutput = createAndSaveInitialCIMOutput(analysisJob, jsonFilePath, layoutImagePath, 
                            processingTimeMs, layoutInfo, ocrResults, aiResults);
                        
                        Map<String, Object> structuredCIM = cimService.generateStructuredCIM(analysisJob.getId());
                        cimService.saveStructuredResult(analysisJob.getId(), structuredCIM);
                        
                        logger.info("기존 방식 CIM 생성 완료 - Job ID: {}", analysisJob.getId());
                    }
                    
                } catch (Exception e) {
                    logger.error("자동 CIM 생성 실패 - Job ID: {}, Error: {}", analysisJob.getId(), e.getMessage(), e);
                    
                    // 오류 발생 시 기본 CIM만 생성
                    CIMOutput fallbackCIM = createAndSaveInitialCIMOutput(analysisJob, jsonFilePath, layoutImagePath, 
                        processingTimeMs, layoutInfo, ocrResults, aiResults);
                    fallbackCIM.setGenerationStatus(CIMOutput.GenerationStatus.FAILED);
                    fallbackCIM.setErrorMessage("자동 CIM 생성 실패: " + e.getMessage());
                    cimOutputRepository.save(fallbackCIM);
                }
            } else {
                // CIM 자동 생성이 비활성화된 경우 기본 CIM만 생성
                createAndSaveInitialCIMOutput(analysisJob, jsonFilePath, layoutImagePath, 
                    processingTimeMs, layoutInfo, ocrResults, aiResults);
            }

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

            LayoutBlock layoutBlock = new LayoutBlock();
            layoutBlock.setDocumentPage(documentPage);
            layoutBlock.setBlockIndex(layout.getId());
            layoutBlock.setClassName(layout.getClassName());
            layoutBlock.setConfidence(layout.getConfidence());

            int[] box = layout.getBox();
            if (box.length >= 4) {
                layoutBlock.setX1(box[0]);
                layoutBlock.setY1(box[1]);
                layoutBlock.setX2(box[2]);
                layoutBlock.setY2(box[3]);
            }

            OCRResult ocrResult = findOCRByLayoutId(layout.getId(), ocrResults);
            if (ocrResult != null) {
                layoutBlock.setOcrText(ocrResult.getText());
                layoutBlock.setOcrConfidence(90.0); // OCR 신뢰도 기본값
                layoutBlock.setProcessingStatus(LayoutBlock.ProcessingStatus.OCR_COMPLETED);
            }

            AIDescriptionResult aiResult = findAIByLayoutId(layout.getId(), aiResults);
            if (aiResult != null) {
                layoutBlock.setAiDescription(aiResult.getDescription());
                layoutBlock.setProcessingStatus(LayoutBlock.ProcessingStatus.AI_COMPLETED);
            }

            if (layoutBlock.getProcessingStatus() == null) {
                layoutBlock.setProcessingStatus(LayoutBlock.ProcessingStatus.LAYOUT_DETECTED);
            }

            LayoutBlock savedLayoutBlock = layoutBlockRepository.save(layoutBlock);

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

        layoutBlock.setTextBlock(textBlock);
        layoutBlockRepository.save(layoutBlock);
    }

    /**
     * 초기 CIMOutput 저장
     */
    private CIMOutput createAndSaveInitialCIMOutput(AnalysisJob analysisJob,
                                                    String jsonFilePath,
                                                    String layoutImagePath,
                                                    long processingTimeMs,
                                                    List<LayoutInfo> layoutInfo,
                                                    List<OCRResult> ocrResults,
                                                    List<AIDescriptionResult> aiResults) {
        try {
            CIMOutput cimOutput = new CIMOutput();
            cimOutput.setAnalysisJob(analysisJob);

            Map<String, Object> initialCimResult = createPageCIMResult(layoutInfo, ocrResults, aiResults);
            cimOutput.setCimData(objectMapper.writeValueAsString(initialCimResult));
            cimOutput.setJsonFilePath(jsonFilePath);
            cimOutput.setLayoutVisualizationPath(layoutImagePath);

            cimOutput.setTotalElements(layoutInfo.size());
            cimOutput.setTextElements(ocrResults.size());
            cimOutput.setAiDescribedElements(aiResults.size());

            long figureCount = layoutInfo.stream().filter(l -> "figure".equals(l.getClassName())).count();
            long tableCount = layoutInfo.stream().filter(l -> "table".equals(l.getClassName())).count();
            cimOutput.setTotalFigures((int) figureCount);
            cimOutput.setTotalTables((int) tableCount);

            int totalWords = ocrResults.stream().mapToInt(ocr -> 
                ocr.getText() != null ? ocr.getText().split("\\s+").length : 0).sum();
            int totalChars = ocrResults.stream().mapToInt(ocr -> 
                ocr.getText() != null ? ocr.getText().length() : 0).sum();
            cimOutput.setTotalWordCount(totalWords);
            cimOutput.setTotalCharCount(totalChars);

            cimOutput.setProcessingTimeMs(processingTimeMs);
            cimOutput.setGenerationStatus(CIMOutput.GenerationStatus.PENDING); // 초기 상태는 PENDING

            cimOutputRepository.save(cimOutput);

            analysisJob.setCimOutput(cimOutput);
            analysisJobRepository.save(analysisJob);

            logger.info("초기 CIMOutput 저장 완료 - Job ID: {}", analysisJob.getId());
            return cimOutput;

        } catch (Exception e) {
            logger.error("초기 CIMOutput 저장 실패", e);
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
                                             String jsonFilePath,
                                             String layoutImagePath,
                                             long processingTimeMs) {
        try {
            logger.info("페이지 분석 결과 DB 저장 시작 - JobID: {}, 페이지: {}", analysisJob.getJobId(), pageNumber);

            DocumentPage documentPage = new DocumentPage();
            documentPage.setAnalysisJob(analysisJob);
            documentPage.setPageNumber(pageNumber);
            documentPage.setImagePath(imagePath);
            documentPage.setLayoutVisualizationPath(layoutImagePath);
            documentPage.setAnalysisResult(null); // 포맷된 텍스트는 CIMService에서 생성
            documentPage.setProcessingStatus(DocumentPage.ProcessingStatus.COMPLETED);
            documentPage.setProcessingTimeMs(processingTimeMs);
            documentPage = documentPageRepository.save(documentPage);

            saveLayoutBlocks(layoutInfo, documentPage, ocrResults, aiResults);

            // Level 5: 페이지별 통합 CIM 생성 적용
            if (smartEyeProperties.getProcessing().isAutoGenerateCim()) {
                try {
                    CIMOutput cimOutput;
                    
                    // 메모리 최적화 모드 체크
                    if (memoryService.isUnifiedCIMEnabled()) {
                        logger.info("페이지 통합 CIM 생성 - Job ID: {}, Page: {}", analysisJob.getId(), pageNumber);
                        
                        // 통합 CIM 생성 (DB 중복 읽기 없이 메모리 데이터 사용)
                        Map<String, Object> completeCIM = cimService.generateOptimizedCompleteCIM(
                            analysisJob.getId(), layoutInfo, ocrResults, aiResults);
                        
                        cimOutput = createUnifiedCIMOutput(analysisJob, jsonFilePath, layoutImagePath, 
                            processingTimeMs, completeCIM, layoutInfo, ocrResults, aiResults);
                        
                        logger.info("페이지 통합 CIM 생성 완료 - Job ID: {}, Page: {}", analysisJob.getId(), pageNumber);
                        
                    } else {
                        logger.info("페이지 기존 방식 CIM 생성 - Job ID: {}, Page: {}", analysisJob.getId(), pageNumber);
                        
                        cimOutput = createAndSaveInitialCIMOutput(analysisJob, jsonFilePath, layoutImagePath, 
                            processingTimeMs, layoutInfo, ocrResults, aiResults);
                        
                        Map<String, Object> structuredCIM = cimService.generateStructuredCIM(analysisJob.getId());
                        cimService.saveStructuredResult(analysisJob.getId(), structuredCIM);
                    }
                    
                } catch (Exception e) {
                    logger.error("페이지 CIM 생성 실패 - Job ID: {}, Page: {}, Error: {}", 
                        analysisJob.getId(), pageNumber, e.getMessage(), e);
                    
                    // 오류 발생 시 기본 CIM만 생성
                    CIMOutput fallbackCIM = createAndSaveInitialCIMOutput(analysisJob, jsonFilePath, layoutImagePath, 
                        processingTimeMs, layoutInfo, ocrResults, aiResults);
                    fallbackCIM.setGenerationStatus(CIMOutput.GenerationStatus.FAILED);
                    fallbackCIM.setErrorMessage("페이지 CIM 생성 실패: " + e.getMessage());
                    cimOutputRepository.save(fallbackCIM);
                }
            } else {
                // CIM 자동 생성이 비활성화된 경우 기본 CIM만 생성
                createAndSaveInitialCIMOutput(analysisJob, jsonFilePath, layoutImagePath, 
                    processingTimeMs, layoutInfo, ocrResults, aiResults);
            }

            addProcessingLog(analysisJob, "PAGE_ANALYSIS_COMPLETED",
                           String.format("페이지 %d 분석 완료 - 레이아웃: %d개, OCR: %d개, AI: %d개",
                                       pageNumber, layoutInfo.size(), ocrResults.size(), aiResults.size()),
                           processingTimeMs);

            DocumentPage completeDocumentPage = documentPageRepository.findByIdWithLayoutBlocks(documentPage.getId())
                .orElseThrow(() -> new RuntimeException("저장된 DocumentPage를 찾을 수 없습니다"));

            logger.info("페이지 분석 결과 DB 저장 완료 - JobID: {}, 페이지: {}, 레이아웃: {}개",
                       analysisJob.getJobId(), pageNumber, layoutInfo.size());

            return completeDocumentPage;

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
    
    /**
     * Level 5: 통합 CIM 결과를 저장하는 메서드
     * 기본 분석과 구조화 분석을 모두 포함한 통합 CIM 생성
     * 
     * @param analysisJob 분석 작업
     * @param jsonFilePath JSON 결과 파일 경로
     * @param layoutImagePath 레이아웃 시각화 이미지 경로
     * @param processingTimeMs 처리 시간
     * @param completeCIM 통합 CIM 결과
     * @param layoutInfo 레이아웃 정보
     * @param ocrResults OCR 결과
     * @param aiResults AI 결과
     * @return 저장된 CIMOutput
     */
    private CIMOutput createUnifiedCIMOutput(AnalysisJob analysisJob,
                                            String jsonFilePath,
                                            String layoutImagePath,
                                            long processingTimeMs,
                                            Map<String, Object> completeCIM,
                                            List<LayoutInfo> layoutInfo,
                                            List<OCRResult> ocrResults,
                                            List<AIDescriptionResult> aiResults) {
        try {
            logger.info("통합 CIMOutput 생성 시작 - Job ID: {}", analysisJob.getId());
            
            CIMOutput cimOutput = new CIMOutput();
            cimOutput.setAnalysisJob(analysisJob);
            
            // 통합 CIM 데이터 저장
            cimOutput.setCimData(objectMapper.writeValueAsString(completeCIM));
            
            // 구조화 데이터 따로 저장
            Map<String, Object> structuredAnalysis = (Map<String, Object>) completeCIM.get("structured_analysis");
            if (structuredAnalysis != null) {
                cimOutput.setStructuredDataJson(objectMapper.writeValueAsString(structuredAnalysis));
                cimOutput.setStructuredText(cimService.createStructuredText(structuredAnalysis));
                
                // 구조화 메타데이터 추출
                Map<String, Object> documentInfo = (Map<String, Object>) structuredAnalysis.get("document_info");
                if (documentInfo != null) {
                    cimOutput.setTotalQuestions((Integer) documentInfo.getOrDefault("total_questions", 0));
                    cimOutput.setLayoutType((String) documentInfo.get("layout_type"));
                }
            }
            
            // 기본 메타데이터
            cimOutput.setJsonFilePath(jsonFilePath);
            cimOutput.setLayoutVisualizationPath(layoutImagePath);
            
            // 통계 정보
            cimOutput.setTotalElements(layoutInfo.size());
            cimOutput.setTextElements(ocrResults.size());
            cimOutput.setAiDescribedElements(aiResults.size());
            
            long figureCount = layoutInfo.stream().filter(l -> "figure".equals(l.getClassName())).count();
            long tableCount = layoutInfo.stream().filter(l -> "table".equals(l.getClassName())).count();
            cimOutput.setTotalFigures((int) figureCount);
            cimOutput.setTotalTables((int) tableCount);
            
            int totalWords = ocrResults.stream().mapToInt(ocr -> 
                ocr.getText() != null ? ocr.getText().split("\\s+").length : 0).sum();
            int totalChars = ocrResults.stream().mapToInt(ocr -> 
                ocr.getText() != null ? ocr.getText().length() : 0).sum();
            cimOutput.setTotalWordCount(totalWords);
            cimOutput.setTotalCharCount(totalChars);
            
            cimOutput.setProcessingTimeMs(processingTimeMs);
            cimOutput.setGenerationStatus(CIMOutput.GenerationStatus.COMPLETED); // 통합 생성이므로 즉시 COMPLETED
            
            cimOutputRepository.save(cimOutput);
            
            analysisJob.setCimOutput(cimOutput);
            analysisJobRepository.save(analysisJob);
            
            logger.info("통합 CIMOutput 생성 완료 - Job ID: {}, 문제 수: {}, 요소 수: {}", 
                analysisJob.getId(), 
                cimOutput.getTotalQuestions(),
                cimOutput.getTotalElements());
            
            return cimOutput;
            
        } catch (Exception e) {
            logger.error("통합 CIMOutput 생성 실패 - Job ID: {}", analysisJob.getId(), e);
            throw new RuntimeException("통합 CIMOutput 생성 중 오류 발생", e);
        }
    }
}
