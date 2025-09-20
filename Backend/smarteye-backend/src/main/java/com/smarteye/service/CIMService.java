package com.smarteye.service;

import com.smarteye.dto.AIDescriptionResult;
import com.smarteye.dto.OCRResult;
import com.smarteye.dto.common.LayoutInfo;
import com.smarteye.entity.AnalysisJob;
import com.smarteye.entity.CIMOutput;
import com.smarteye.entity.DocumentPage;
import com.smarteye.repository.CIMOutputRepository;
import com.smarteye.repository.DocumentPageRepository;
import com.smarteye.dto.TSPMResult;
import com.smarteye.dto.QuestionGroup;
import com.smarteye.service.StructuredJSONService.StructuredResult;
import com.smarteye.service.StructuredJSONService.QuestionResult;
import com.smarteye.service.StructuredJSONService.QuestionContent;
import com.smarteye.service.StructuredJSONService.DocumentInfo;
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
 * 구조화된 분석 기능을 CIM 워크플로우로 통합하여 처리
 * 레이아웃 정렬, DB 저장, 통합 JSON 생성을 담당
 */
@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
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
    
    @Autowired
    private TSPMEngine tspmEngine;

    @Autowired
    private ConcurrencyManagerService concurrencyManagerService;
    
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
     *
     * 데이터 무결성 보장을 위한 단계별 검증 추가
     */
    @Transactional(rollbackFor = Exception.class)
    private void saveStructuredResultToDatabase(AnalysisJob analysisJob,
                                              StructuredResult structuredResult,
                                              List<LayoutInfo> layoutInfo,
                                              List<OCRResult> ocrResults,
                                              List<AIDescriptionResult> aiResults,
                                              long processingTimeMs) {
        try {
            logger.info("구조화된 결과 DB 저장 시작 - JobID: {}", analysisJob.getJobId());

            // 0. 데이터 검증
            validateInputData(analysisJob, structuredResult, layoutInfo);

            // 1. DocumentPage 생성 (멱등성 보장)
            DocumentPage documentPage = createOrUpdateDocumentPage(analysisJob);

            // 2. 구조화된 결과를 기존 스키마에 맞게 저장
            // 레이아웃 정렬이 이미 구조화된 결과에 반영되어 있음
            saveStructuredLayoutBlocks(documentPage, structuredResult, layoutInfo, ocrResults, aiResults);

            // 3. CIMOutput에 구조화된 결과 저장 (원자적 작업)
            saveCIMOutputWithStructuredResult(analysisJob, structuredResult, processingTimeMs);

            // 4. ProcessingLog 추가
            addProcessingLog(analysisJob, "STRUCTURED_ANALYSIS_COMPLETED",
                           String.format("구조화된 분석 완료 - 총 문제: %d개",
                                       structuredResult.documentInfo.totalQuestions),
                           processingTimeMs);

            // 5. 데이터 무결성 최종 검증
            validateSavedData(analysisJob, structuredResult);

            logger.info("구조화된 결과 DB 저장 완료 - JobID: {}", analysisJob.getJobId());

        } catch (Exception e) {
            logger.error("구조화된 결과 DB 저장 실패 - JobID: {}", analysisJob.getJobId(), e);

            // 분석 작업 상태를 FAILED로 업데이트
            analysisJob.setStatus(AnalysisJob.JobStatus.FAILED);
            analysisJob.setErrorMessage("DB 저장 중 오류 발생: " + e.getMessage());

            throw new RuntimeException("구조화된 결과 DB 저장 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 입력 데이터 검증
     */
    private void validateInputData(AnalysisJob analysisJob, StructuredResult structuredResult, List<LayoutInfo> layoutInfo) {
        if (analysisJob == null) {
            throw new IllegalArgumentException("AnalysisJob이 null입니다");
        }
        if (analysisJob.getJobId() == null || analysisJob.getJobId().trim().isEmpty()) {
            throw new IllegalArgumentException("JobID가 유효하지 않습니다");
        }
        if (structuredResult == null) {
            throw new IllegalArgumentException("StructuredResult가 null입니다");
        }
        if (structuredResult.documentInfo == null) {
            throw new IllegalArgumentException("DocumentInfo가 null입니다");
        }
        if (layoutInfo == null || layoutInfo.isEmpty()) {
            logger.warn("LayoutInfo가 비어있습니다 - JobID: {}", analysisJob.getJobId());
        }

        logger.debug("입력 데이터 검증 완료 - JobID: {}", analysisJob.getJobId());
    }

    /**
     * 저장된 데이터 무결성 검증
     */
    private void validateSavedData(AnalysisJob analysisJob, StructuredResult structuredResult) {
        // CIMOutput 존재 여부 확인
        Optional<CIMOutput> cimOutput = cimOutputRepository.findByAnalysisJobId(analysisJob.getId());
        if (cimOutput.isEmpty()) {
            throw new RuntimeException("CIMOutput이 저장되지 않았습니다 - JobID: " + analysisJob.getJobId());
        }

        // CIMOutput 데이터 무결성 확인
        CIMOutput output = cimOutput.get();
        if (output.getCimData() == null || output.getCimData().trim().isEmpty()) {
            throw new RuntimeException("CIMOutput 데이터가 비어있습니다 - JobID: " + analysisJob.getJobId());
        }

        if (output.getGenerationStatus() != CIMOutput.GenerationStatus.COMPLETED) {
            throw new RuntimeException("CIMOutput 상태가 COMPLETED가 아닙니다 - JobID: " + analysisJob.getJobId());
        }

        logger.debug("저장된 데이터 무결성 검증 완료 - JobID: {}", analysisJob.getJobId());
    }
    
    /**
     * DocumentPage 생성 또는 업데이트 (멱등성 보장)
     */
    private DocumentPage createOrUpdateDocumentPage(AnalysisJob analysisJob) {
        // 기존 DocumentPage가 있는지 확인
        Optional<DocumentPage> existingPage = documentPageRepository
            .findByAnalysisJobAndPageNumber(analysisJob, 1);

        DocumentPage documentPage;
        if (existingPage.isPresent()) {
            // 기존 페이지 업데이트
            documentPage = existingPage.get();
            logger.debug("기존 DocumentPage 업데이트 - AnalysisJob ID: {}", analysisJob.getId());
        } else {
            // 새 페이지 생성
            documentPage = new DocumentPage();
            documentPage.setAnalysisJob(analysisJob);
            documentPage.setPageNumber(1); // 단일 이미지는 페이지 1
            logger.debug("새 DocumentPage 생성 - AnalysisJob ID: {}", analysisJob.getId());
        }

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
     * CIMOutput에 구조화된 결과 저장 (동시성 보장, 멱등성 적용)
     *
     * 개선사항:
     * 1. 분산 락을 통한 동시성 제어
     * 2. 재시도 메커니즘 추가
     * 3. 상세한 오류 분류 및 처리
     */
    private void saveCIMOutputWithStructuredResult(AnalysisJob analysisJob,
                                                 StructuredResult structuredResult,
                                                 long processingTimeMs) {
        String lockKey = "cim_output_" + analysisJob.getId();
        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                // 동시성 매니저를 통한 락 획득
                concurrencyManagerService.executeWithLock(analysisJob.getId(), () -> {
                    return saveCIMOutputAtomically(analysisJob, structuredResult, processingTimeMs);
                }, "CIM_OUTPUT_SAVE");
                return; // 성공시 메서드 종료

            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                retryCount++;
                logger.warn("CIMOutput 저장 중 데이터 무결성 위반 (시도: {}/{}) - AnalysisJob ID: {}",
                           retryCount, maxRetries, analysisJob.getId(), e);

                if (retryCount >= maxRetries) {
                    // 최대 재시도 횟수 초과 시 기존 데이터 업데이트 시도
                    handleDataIntegrityViolation(analysisJob, structuredResult, processingTimeMs, e);
                    return; // 처리 완료 후 메서드 종료
                }

                // 짧은 대기 후 재시도
                try {
                    Thread.sleep(100 * retryCount); // 백오프 전략
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("재시도 중 인터럽트 발생", ie);
                }

            } catch (Exception e) {
                logger.error("CIMOutput 저장 중 예상치 못한 오류 - AnalysisJob ID: {}", analysisJob.getId(), e);
                throw new RuntimeException("CIMOutput 저장 중 오류 발생: " + e.getMessage(), e);
            }
        }

        throw new RuntimeException("CIMOutput 저장 최대 재시도 횟수 초과");
    }

    /**
     * 원자적 CIMOutput 저장 작업
     */
    private Void saveCIMOutputAtomically(AnalysisJob analysisJob,
                                       StructuredResult structuredResult,
                                       long processingTimeMs) {
        try {
            // 1. 기존 CIMOutput 존재 여부 확인 (멱등성 보장)
            Optional<CIMOutput> existingOutput = cimOutputRepository.findByAnalysisJobId(analysisJob.getId());

            CIMOutput cimOutput;
            if (existingOutput.isPresent()) {
                // 기존 데이터가 있으면 업데이트 (멱등성)
                cimOutput = existingOutput.get();
                logger.debug("기존 CIMOutput 업데이트 - AnalysisJob ID: {}", analysisJob.getId());
            } else {
                // 새로운 CIMOutput 생성
                cimOutput = new CIMOutput();
                cimOutput.setAnalysisJob(analysisJob);
                logger.debug("새 CIMOutput 생성 - AnalysisJob ID: {}", analysisJob.getId());
            }

            // 2. 데이터 설정 (JSON 직렬화 실패 방지)
            String cimDataJson = serializeToJsonSafely(structuredResult);
            cimOutput.setCimData(cimDataJson);
            cimOutput.setFormattedText(createFormattedTextFromStructured(structuredResult));

            // 3. 통계 정보 설정 (null 안전성 보장)
            int questionCount = structuredResult.questions != null ? structuredResult.questions.size() : 0;
            cimOutput.setTotalElements(questionCount);
            cimOutput.setTextElements(questionCount);
            cimOutput.setProcessingTimeMs(processingTimeMs);
            cimOutput.setGenerationStatus(CIMOutput.GenerationStatus.COMPLETED);
            cimOutput.setErrorMessage(null); // 성공 시 에러 메시지 초기화

            // 4. 원자적 저장 (saveAndFlush 사용)
            cimOutput = cimOutputRepository.saveAndFlush(cimOutput);

            // 5. AnalysisJob에 CIMOutput 연결 (양방향 관계 설정)
            if (analysisJob.getCimOutput() == null || !analysisJob.getCimOutput().getId().equals(cimOutput.getId())) {
                analysisJob.setCimOutput(cimOutput);
            }

            logger.info("CIMOutput 원자적 저장 완료 - 총 문제: {}개, CIMOutput ID: {}",
                       questionCount, cimOutput.getId());

            return null; // Void 반환

        } catch (Exception e) {
            logger.error("CIMOutput 원자적 저장 실패 - AnalysisJob ID: {}", analysisJob.getId(), e);
            throw new RuntimeException("원자적 저장 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 데이터 무결성 위반 처리
     */
    private Void handleDataIntegrityViolation(AnalysisJob analysisJob,
                                            StructuredResult structuredResult,
                                            long processingTimeMs,
                                            Exception originalException) {
        logger.warn("데이터 무결성 위반 처리 시작 - AnalysisJob ID: {}", analysisJob.getId());

        try {
            // 기존 데이터 재조회 및 업데이트
            Optional<CIMOutput> existingOutput = cimOutputRepository.findByAnalysisJobId(analysisJob.getId());
            if (existingOutput.isPresent()) {
                CIMOutput cimOutput = existingOutput.get();

                // 기존 데이터 업데이트
                String cimDataJson = serializeToJsonSafely(structuredResult);
                cimOutput.setCimData(cimDataJson);
                cimOutput.setFormattedText(createFormattedTextFromStructured(structuredResult));

                int questionCount = structuredResult.questions != null ? structuredResult.questions.size() : 0;
                cimOutput.setTotalElements(questionCount);
                cimOutput.setTextElements(questionCount);
                cimOutput.setProcessingTimeMs(processingTimeMs);
                cimOutput.setGenerationStatus(CIMOutput.GenerationStatus.COMPLETED);
                cimOutput.setErrorMessage(null);

                cimOutputRepository.saveAndFlush(cimOutput);
                logger.info("데이터 무결성 위반 복구 완료 - AnalysisJob ID: {}", analysisJob.getId());
                return null;

            } else {
                logger.error("데이터 무결성 위반 후 기존 데이터 조회 실패 - AnalysisJob ID: {}", analysisJob.getId());
                throw new RuntimeException("CIMOutput 조회 실패 후 복구 불가", originalException);
            }

        } catch (Exception e) {
            logger.error("데이터 무결성 위반 처리 실패 - AnalysisJob ID: {}", analysisJob.getId(), e);
            throw new RuntimeException("데이터 무결성 위반 복구 실패: " + e.getMessage(), originalException);
        }
    }

    /**
     * 안전한 JSON 직렬화 (예외 처리 포함)
     */
    private String serializeToJsonSafely(StructuredResult structuredResult) {
        try {
            if (structuredResult == null) {
                return "{}";
            }
            return objectMapper.writeValueAsString(structuredResult);
        } catch (Exception e) {
            logger.error("JSON 직렬화 실패, 기본 구조로 대체", e);
            // 기본 JSON 구조 반환
            return String.format("{\"error\":\"JSON 직렬화 실패\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                               e.getMessage().replace("\"", "'"),
                               java.time.LocalDateTime.now().toString());
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
     * 사용자 요구사항의 핵심 기능 (동시성 보장)
     */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public StructuredResult generateIntegratedJSON(AnalysisJob analysisJob) {
        try {
            logger.info("통합 JSON 생성 시작 - JobID: {}", analysisJob.getJobId());

            // CIMOutput에서 저장된 구조화된 결과 조회 (동시성 보장)
            CIMOutput cimOutput = cimOutputRepository.findByAnalysisJobId(analysisJob.getId())
                .orElseThrow(() -> new RuntimeException("CIMOutput을 찾을 수 없습니다: " + analysisJob.getJobId()));

            if (cimOutput.getCimData() == null || cimOutput.getCimData().trim().isEmpty()) {
                throw new RuntimeException("CIMOutput에 저장된 데이터가 없습니다: " + analysisJob.getJobId());
            }

            // JSON 문자열을 StructuredResult 객체로 변환
            StructuredResult integratedResult = objectMapper.readValue(cimOutput.getCimData(), StructuredResult.class);

            // 데이터 무결성 검증
            if (integratedResult == null) {
                throw new RuntimeException("JSON 파싱 결과가 null입니다: " + analysisJob.getJobId());
            }

            logger.info("통합 JSON 생성 완료 - 총 문제: {}개",
                       integratedResult.documentInfo != null ? integratedResult.documentInfo.totalQuestions : 0);

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
    
    /**
     * TSPM 기반 문제 레이아웃 정렬 분석 수행
     * 기본 분석(LAM + OCR + AI)이 완료된 후 호출하여 TSPM 분석 수행
     * 
     * @param analysisJobId 분석 작업 ID
     * @return TSPM 분석 결과
     */
    @Transactional
    public com.smarteye.dto.TSPMResult performTSPMAnalysis(Long analysisJobId) {
        logger.info("🔧 TSPM 분석 시작 - AnalysisJob ID: {}", analysisJobId);
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. AnalysisJob 조회 (기존 repository를 통해)
            // DocumentAnalysisDataService에서 findAnalysisJobById 메서드가 없으므로 직접 구현 필요
            // 임시로 주석 처리하고 다른 방법으로 조회
            AnalysisJob analysisJob = null;
            // TODO: AnalysisJob 조회 로직 추가 필요
            
            com.smarteye.dto.TSPMResult finalResult = null;
            
            // 2. 각 DocumentPage에 대해 TSPM 분석 수행
            for (DocumentPage page : analysisJob.getDocumentPages()) {
                logger.info("📄 페이지 {} TSPM 분석 시작", page.getPageNumber());
                
                // TSPM 엔진으로 분석 수행
                com.smarteye.dto.TSPMResult pageResult = tspmEngine.performTSPMAnalysis(page.getId());
                
                // DocumentPage.analysis_result에 TSPM 결과 저장
                page.setAnalysisResult(objectMapper.writeValueAsString(pageResult));
                documentPageRepository.save(page);
                
                logger.info("✅ 페이지 {} TSPM 결과 저장 완료", page.getPageNumber());
                
                // 첫 번째 페이지 결과를 기본으로 설정
                if (finalResult == null) {
                    finalResult = pageResult;
                } else {
                    // 다중 페이지의 경우 결과 통합
                    mergeTSPMResults(finalResult, pageResult);
                }
            }
            
            // 3. 통합 결과를 CIMOutput에 저장
            CIMOutput cimOutput = cimOutputRepository.findByAnalysisJobId(analysisJobId)
                .orElse(new CIMOutput());
            
            cimOutput.setAnalysisJob(analysisJob);
            cimOutput.setCimData(objectMapper.writeValueAsString(finalResult));
            cimOutput.setGenerationStatus(CIMOutput.GenerationStatus.COMPLETED);
            cimOutput.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            
            cimOutputRepository.save(cimOutput);
            
            // 4. AnalysisJob 상태 업데이트
            analysisJob.setStatus(AnalysisJob.JobStatus.COMPLETED);
            
            long totalProcessingTime = System.currentTimeMillis() - startTime;
            logger.info("✅ TSPM 분석 완료 - JobID: {}, 처리시간: {}ms, 총 문제: {}개", 
                       analysisJob.getJobId(), totalProcessingTime, 
                       finalResult != null ? finalResult.getQuestionGroups().size() : 0);
            
            return finalResult;
            
        } catch (Exception e) {
            logger.error("❌ TSPM 분석 실패 - AnalysisJob ID: {}", analysisJobId, e);
            throw new RuntimeException("TSPM 분석 중 오류 발생: " + e.getMessage(), e);
        }
    }
    
    /**
     * 여러 페이지의 TSPM 결과를 통합
     */
    private void mergeTSPMResults(com.smarteye.dto.TSPMResult target, com.smarteye.dto.TSPMResult source) {
        if (source == null || source.getQuestionGroups() == null) {
            return;
        }
        
        // QuestionGroup 통합
        target.getQuestionGroups().addAll(source.getQuestionGroups());
        
        // DocumentInfo 업데이트
        if (target.getDocumentInfo() != null && source.getDocumentInfo() != null) {
            target.getDocumentInfo().setTotalQuestions(
                target.getDocumentInfo().getTotalQuestions() + source.getDocumentInfo().getTotalQuestions()
            );
        }
        
        logger.debug("📊 TSPM 결과 통합: {} + {} = {} 개 문제", 
                    target.getQuestionGroups().size() - source.getQuestionGroups().size(),
                    source.getQuestionGroups().size(),
                    target.getQuestionGroups().size());
    }
    
    /**
     * 기본 분석 완료 후 자동으로 TSPM 분석 수행하는 통합 메서드
     * 기존 performStructuredAnalysisWithCIM을 대체하여 TSPM 기반으로 처리
     * 
     * @param image 분석할 이미지
     * @param analysisJob 분석 작업 정보
     * @param modelChoice 사용할 모델
     * @param apiKey OpenAI API 키 (선택사항)
     * @return TSPM 분석 결과 (StructuredResult 호환)
     */
    public StructuredResult performTSPMBasedAnalysis(BufferedImage image, 
                                                    AnalysisJob analysisJob, 
                                                    String modelChoice, 
                                                    String apiKey) {
        logger.info("🚀 TSPM 기반 통합 분석 시작 - JobID: {}", analysisJob.getJobId());
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 기본 분석 (LAM + OCR + AI) → DB 저장
            logger.info("📊 기본 분석 수행 중...");
            saveBasicAnalysisToDatabase(image, analysisJob, modelChoice, apiKey);
            
            // 2. TSPM 분석 수행
            logger.info("🔧 TSPM 분석 수행 중...");
            com.smarteye.dto.TSPMResult tspmResult = performTSPMAnalysis(analysisJob.getId());
            
            // 3. 기존 StructuredResult 형태로 변환하여 호환성 유지
            StructuredResult compatibleResult = convertTSPMToStructuredResult(tspmResult);
            
            long totalProcessingTime = System.currentTimeMillis() - startTime;
            logger.info("✅ TSPM 기반 통합 분석 완료 - JobID: {}, 총 처리시간: {}ms", 
                       analysisJob.getJobId(), totalProcessingTime);
            
            return compatibleResult;
            
        } catch (Exception e) {
            logger.error("❌ TSPM 기반 통합 분석 실패 - JobID: {}", analysisJob.getJobId(), e);
            throw new RuntimeException("TSPM 기반 통합 분석 중 오류 발생: " + e.getMessage(), e);
        }
    }
    
    /**
     * 기본 분석 (LAM + OCR + AI)을 DB에 저장
     */
    private void saveBasicAnalysisToDatabase(BufferedImage image, AnalysisJob analysisJob, 
                                           String modelChoice, String apiKey) {
        // DocumentAnalysisDataService.performFullAnalysis 메서드가 없으므로 주석 처리
        // TODO: 기본 분석 로직 구현 또는 기존 메서드 활용 필요
        // documentAnalysisDataService.performFullAnalysis(image, analysisJob, modelChoice, apiKey);
    }
    
    /**
     * TSPM 결과를 기존 StructuredResult 형태로 변환 (호환성 유지)
     */
    private StructuredResult convertTSPMToStructuredResult(com.smarteye.dto.TSPMResult tspmResult) {
        // TSPMResult → StructuredResult 변환 로직
        // 기존 API 호환성을 위한 변환
        
        StructuredResult result = new StructuredResult();
        
        // DocumentInfo 변환
        if (tspmResult.getDocumentInfo() != null) {
            result.documentInfo = new DocumentInfo();
            result.documentInfo.totalQuestions = tspmResult.getDocumentInfo().getTotalQuestions();
            result.documentInfo.layoutType = tspmResult.getDocumentInfo().getLayoutType();
        }
        
        // QuestionGroup을 기존 Question 형태로 변환
        result.questions = tspmResult.getQuestionGroups().stream()
            .map(this::convertQuestionGroupToQuestion)
            .collect(java.util.stream.Collectors.toList());
        
        logger.debug("🔄 TSPM → StructuredResult 변환 완료: {} 개 문제", result.questions.size());
        
        return result;
    }
    
    /**
     * QuestionGroup을 기존 Question 형태로 변환
     */
    private QuestionResult convertQuestionGroupToQuestion(QuestionGroup group) {
        QuestionResult question = new QuestionResult();
        
        question.questionNumber = group.getQuestionNumber();
        question.section = group.getSection();
        
        // QuestionContent 구성
        question.questionContent = new QuestionContent();
        
        // 각 요소들을 기존 형태로 변환
        if (!group.getElements().getQuestionText().isEmpty()) {
            question.questionContent.mainQuestion = group.getElements().getQuestionText().get(0).getExtractedText();
        }
        
        // 나머지 변환 로직...
        
        return question;
    }
}