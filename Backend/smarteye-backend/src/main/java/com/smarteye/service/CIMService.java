package com.smarteye.service;

import com.smarteye.dto.AIDescriptionResult;
import com.smarteye.dto.OCRResult;
import com.smarteye.dto.common.LayoutInfo;
import com.smarteye.entity.AnalysisJob;
import com.smarteye.entity.CIMOutput;
import com.smarteye.entity.DocumentPage;
import com.smarteye.repository.CIMOutputRepository;
import com.smarteye.repository.DocumentPageRepository;
import com.smarteye.service.StructuredJSONService.StructuredResult;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 구조화된 분석 기능을 CIM 워크플로우로 통합하여 처리
 * 레이아웃 정렬, DB 저장, 통합 JSON 생성을 담당
 */
@Service
@Transactional
public class CIMService {
    
    private static final Logger logger = LoggerFactory.getLogger(CIMService.class);
    
    @Autowired
    private StructuredJSONService structuredJSONService;
    
    @Autowired
    private OCRService ocrService;
    
    @Autowired
    private AIDescriptionService aiDescriptionService;
    
    @Autowired
    private LAMServiceClient lamServiceClient;
    
    @Autowired
    private DocumentAnalysisDataService documentAnalysisDataService;
    
    @Autowired
    private CIMOutputRepository cimOutputRepository;
    
    @Autowired
    private DocumentPageRepository documentPageRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 구조화된 분석을 수행하고 CIM으로 통합 처리
     * 
     * @param image 분석할 이미지
     * @param analysisJob 분석 작업 정보
     * @param modelChoice 사용할 모델
     * @param apiKey OpenAI API 키 (선택사항)
     * @return 구조화된 분석 결과
     */
    public StructuredResult performStructuredAnalysisWithCIM(BufferedImage image, 
                                                           AnalysisJob analysisJob, 
                                                           String modelChoice, 
                                                           String apiKey) {
        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("CIM 통합 구조화된 분석 시작 - JobID: {}, 모델: {}", analysisJob.getJobId(), modelChoice);
            
            // 1. 기본 레이아웃 분석 수행
            logger.info("레이아웃 분석 시작...");
            var layoutResult = lamServiceClient.analyzeLayout(image, modelChoice).get();
            
            if (layoutResult.getLayoutInfo().isEmpty()) {
                logger.warn("레이아웃 분석 결과가 비어있음");
                throw new RuntimeException("레이아웃 분석에 실패했습니다. 감지된 요소가 없습니다.");
            }
            
            // 2. OCR 처리
            logger.info("OCR 처리 시작...");
            List<OCRResult> ocrResults = ocrService.performOCR(image, layoutResult.getLayoutInfo());
            
            // 3. AI 설명 생성 (API 키가 있는 경우)
            List<AIDescriptionResult> aiResults;
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                logger.info("AI 설명 생성 시작...");
                aiResults = aiDescriptionService.generateDescriptions(image, layoutResult.getLayoutInfo(), apiKey).get();
            } else {
                aiResults = List.of();
                logger.info("API 키가 없어 AI 설명 생성을 건너뜁니다.");
            }
            
            // 4. 구조화된 JSON 생성 (문제별 정렬된 결과)
            logger.info("구조화된 JSON 생성 시작...");
            StructuredResult structuredResult = structuredJSONService.generateStructuredJSON(
                ocrResults, aiResults, layoutResult.getLayoutInfo()
            );
            
            // 5. DB에 구조화된 결과 저장 (레이아웃 정렬 포함)
            long processingTimeMs = System.currentTimeMillis() - startTime;
            logger.info("DB 저장 시작...");
            saveStructuredResultToDatabase(analysisJob, structuredResult, layoutResult.getLayoutInfo(), 
                                         ocrResults, aiResults, processingTimeMs);
            
            // 6. 통합 JSON 생성 및 반환
            logger.info("통합 JSON 생성 완료");
            StructuredResult finalResult = generateIntegratedJSON(analysisJob);
            
            logger.info("CIM 통합 구조화된 분석 완료 - JobID: {}, 처리시간: {}ms", 
                       analysisJob.getJobId(), processingTimeMs);
            
            return finalResult;
            
        } catch (Exception e) {
            logger.error("CIM 통합 구조화된 분석 실패 - JobID: {}", analysisJob.getJobId(), e);
            throw new RuntimeException("CIM 통합 구조화된 분석 중 오류 발생: " + e.getMessage(), e);
        }
    }
    
    /**
     * 구조화된 결과를 데이터베이스에 저장 (레이아웃 정렬 포함)
     * 기존 LAM Service 방식과 동일한 레이아웃 정렬 로직 적용
     */
    private void saveStructuredResultToDatabase(AnalysisJob analysisJob, 
                                              StructuredResult structuredResult,
                                              List<LayoutInfo> layoutInfo,
                                              List<OCRResult> ocrResults,
                                              List<AIDescriptionResult> aiResults,
                                              long processingTimeMs) {
        try {
            logger.info("구조화된 결과 DB 저장 시작 - JobID: {}", analysisJob.getJobId());
            
            // 1. DocumentPage 생성
            DocumentPage documentPage = createDocumentPage(analysisJob);
            
            // 2. 구조화된 결과를 기존 스키마에 맞게 저장
            // 레이아웃 정렬이 이미 구조화된 결과에 반영되어 있음
            saveStructuredLayoutBlocks(documentPage, structuredResult, layoutInfo, ocrResults, aiResults);
            
            // 3. CIMOutput에 구조화된 결과 저장
            saveCIMOutputWithStructuredResult(analysisJob, structuredResult, processingTimeMs);
            
            // 4. ProcessingLog 추가
            addProcessingLog(analysisJob, "STRUCTURED_ANALYSIS_COMPLETED", 
                           String.format("구조화된 분석 완료 - 총 문제: %d개", 
                                       structuredResult.documentInfo.totalQuestions),
                           processingTimeMs);
            
            logger.info("구조화된 결과 DB 저장 완료 - JobID: {}", analysisJob.getJobId());
            
        } catch (Exception e) {
            logger.error("구조화된 결과 DB 저장 실패 - JobID: {}", analysisJob.getJobId(), e);
            throw new RuntimeException("구조화된 결과 DB 저장 중 오류 발생", e);
        }
    }
    
    /**
     * DocumentPage 생성
     */
    private DocumentPage createDocumentPage(AnalysisJob analysisJob) {
        DocumentPage documentPage = new DocumentPage();
        documentPage.setAnalysisJob(analysisJob);
        documentPage.setPageNumber(1); // 단일 이미지는 페이지 1
        documentPage.setImagePath(analysisJob.getFilePath());
        documentPage.setProcessingStatus(DocumentPage.ProcessingStatus.COMPLETED);
        
        return documentPageRepository.save(documentPage);
    }
    
    /**
     * 구조화된 레이아웃 블록들을 DB에 저장
     * 문제별로 정렬된 순서대로 저장하여 레이아웃 정렬 반영
     */
    private void saveStructuredLayoutBlocks(DocumentPage documentPage, 
                                          StructuredResult structuredResult,
                                          List<LayoutInfo> layoutInfo,
                                          List<OCRResult> ocrResults,
                                          List<AIDescriptionResult> aiResults) {
        
        logger.info("구조화된 레이아웃 블록 저장 시작 - 총 문제: {}개", structuredResult.questions.size());
        
        // 기존 DocumentAnalysisDataService의 저장 로직을 활용하되,
        // 구조화된 순서대로 재정렬하여 저장
        documentAnalysisDataService.saveAnalysisResults(
            documentPage.getAnalysisJob().getJobId(),
            layoutInfo,  // 레이아웃 정보
            ocrResults,  // OCR 결과
            aiResults,   // AI 결과
            createCIMResultFromStructured(structuredResult), // 구조화된 결과를 CIM 형태로 변환
            createFormattedTextFromStructured(structuredResult), // 구조화된 텍스트
            null, // JSON 파일 경로 (별도 생성)
            null, // 레이아웃 시각화 경로 (별도 생성)
            0     // 처리 시간 (별도 계산)
        );
    }
    
    /**
     * 구조화된 결과를 CIM 형태로 변환
     */
    private Map<String, Object> createCIMResultFromStructured(StructuredResult structuredResult) {
        return Map.of(
            "structured_analysis", structuredResult,
            "document_info", Map.of(
                "total_questions", structuredResult.documentInfo.totalQuestions,
                "layout_type", structuredResult.documentInfo.layoutType
            ),
            "questions", structuredResult.questions
        );
    }
    
    /**
     * 구조화된 결과에서 포맷된 텍스트 생성
     */
    private String createFormattedTextFromStructured(StructuredResult structuredResult) {
        StringBuilder formattedText = new StringBuilder();
        
        // 문서 정보 추가
        formattedText.append("📋 구조화된 분석 결과\n");
        formattedText.append("총 문제 수: ").append(structuredResult.documentInfo.totalQuestions).append("개\n");
        formattedText.append("레이아웃 유형: ").append(structuredResult.documentInfo.layoutType).append("\n\n");
        formattedText.append("=".repeat(50)).append("\n\n");
        
        // 각 문제별 처리
        for (int i = 0; i < structuredResult.questions.size(); i++) {
            var question = structuredResult.questions.get(i);
            String questionNum = question.questionNumber != null ? question.questionNumber : "문제" + (i + 1);
            
            formattedText.append("🔸 ").append(questionNum).append("\n\n");
            
            var content = question.questionContent;
            if (content != null) {
                // 주요 문제
                if (content.mainQuestion != null && !content.mainQuestion.trim().isEmpty()) {
                    formattedText.append("❓ 문제:\n").append(content.mainQuestion).append("\n\n");
                }
                
                // 지문
                if (content.passage != null && !content.passage.trim().isEmpty()) {
                    formattedText.append("📖 지문:\n").append(content.passage).append("\n\n");
                }
                
                // 선택지
                if (content.choices != null && !content.choices.isEmpty()) {
                    formattedText.append("📝 선택지:\n");
                    for (var choice : content.choices) {
                        if (choice.choiceNumber != null && choice.choiceText != null) {
                            formattedText.append("   ").append(choice.choiceNumber).append(" ").append(choice.choiceText).append("\n");
                        }
                    }
                    formattedText.append("\n");
                }
            }
            
            // 문제 구분선
            if (i < structuredResult.questions.size() - 1) {
                formattedText.append("-".repeat(30)).append("\n\n");
            }
        }
        
        return formattedText.toString().trim();
    }
    
    /**
     * CIMOutput에 구조화된 결과 저장
     */
    private void saveCIMOutputWithStructuredResult(AnalysisJob analysisJob, 
                                                 StructuredResult structuredResult, 
                                                 long processingTimeMs) {
        try {
            CIMOutput cimOutput = new CIMOutput();
            cimOutput.setAnalysisJob(analysisJob);
            
            // 구조화된 결과를 JSON 문자열로 저장
            cimOutput.setCimData(objectMapper.writeValueAsString(structuredResult));
            cimOutput.setFormattedText(createFormattedTextFromStructured(structuredResult));
            
            // 통계 정보 설정
            cimOutput.setTotalElements(structuredResult.questions.size()); // 문제 수
            cimOutput.setTextElements(structuredResult.questions.size()); // 텍스트 요소는 문제 수와 동일
            cimOutput.setProcessingTimeMs(processingTimeMs);
            cimOutput.setGenerationStatus(CIMOutput.GenerationStatus.COMPLETED);
            
            cimOutputRepository.save(cimOutput);
            
            // AnalysisJob에 CIMOutput 연결
            analysisJob.setCimOutput(cimOutput);
            
            logger.info("CIMOutput 저장 완료 - 총 문제: {}개", structuredResult.questions.size());
            
        } catch (Exception e) {
            logger.error("CIMOutput 저장 실패", e);
            throw new RuntimeException("CIMOutput 저장 중 오류 발생", e);
        }
    }
    
    /**
     * ProcessingLog 추가 (기존 DocumentAnalysisDataService 로직 활용)
     */
    private void addProcessingLog(AnalysisJob analysisJob, String step, String message, long executionTimeMs) {
        // DocumentAnalysisDataService의 기존 메서드를 활용하거나 직접 구현
        logger.info("Processing Log: {} - {}", step, message);
    }
    
    /**
     * DB에 저장된 각 페이지의 JSON 데이터를 불러와 통합된 JSON 객체로 생성 후 반환
     * 사용자 요구사항의 핵심 기능
     */
    public StructuredResult generateIntegratedJSON(AnalysisJob analysisJob) {
        try {
            logger.info("통합 JSON 생성 시작 - JobID: {}", analysisJob.getJobId());
            
            // CIMOutput에서 저장된 구조화된 결과 조회
            CIMOutput cimOutput = cimOutputRepository.findByAnalysisJob(analysisJob)
                .orElseThrow(() -> new RuntimeException("CIMOutput을 찾을 수 없습니다: " + analysisJob.getJobId()));
            
            if (cimOutput.getCimData() == null) {
                throw new RuntimeException("CIMOutput에 저장된 데이터가 없습니다: " + analysisJob.getJobId());
            }
            
            // JSON 문자열을 StructuredResult 객체로 변환
            StructuredResult integratedResult = objectMapper.readValue(cimOutput.getCimData(), StructuredResult.class);
            
            logger.info("통합 JSON 생성 완료 - 총 문제: {}개", 
                       integratedResult.documentInfo.totalQuestions);
            
            return integratedResult;
            
        } catch (Exception e) {
            logger.error("통합 JSON 생성 실패 - JobID: {}", analysisJob.getJobId(), e);
            throw new RuntimeException("통합 JSON 생성 중 오류 발생: " + e.getMessage(), e);
        }
    }
    
    /**
     * 다중 페이지 분석을 위한 통합 JSON 생성
     * 여러 페이지의 데이터를 하나의 통합된 JSON 객체로 병합
     */
    public StructuredResult generateIntegratedJSONForMultiplePages(List<AnalysisJob> analysisJobs) {
        try {
            logger.info("다중 페이지 통합 JSON 생성 시작 - 작업 수: {}", analysisJobs.size());
            
            // 첫 번째 페이지의 결과를 기본으로 사용
            StructuredResult integratedResult = generateIntegratedJSON(analysisJobs.get(0));
            
            // 나머지 페이지들의 결과를 통합
            for (int i = 1; i < analysisJobs.size(); i++) {
                StructuredResult pageResult = generateIntegratedJSON(analysisJobs.get(i));
                
                // 문제들을 통합 결과에 추가
                if (pageResult.questions != null) {
                    integratedResult.questions.addAll(pageResult.questions);
                }
                
                // 문서 정보 업데이트
                integratedResult.documentInfo.totalQuestions += pageResult.documentInfo.totalQuestions;
            }
            
            logger.info("다중 페이지 통합 JSON 생성 완료 - 총 문제: {}개", 
                       integratedResult.documentInfo.totalQuestions);
            
            return integratedResult;
            
        } catch (Exception e) {
            logger.error("다중 페이지 통합 JSON 생성 실패", e);
            throw new RuntimeException("다중 페이지 통합 JSON 생성 중 오류 발생: " + e.getMessage(), e);
        }
    }
}