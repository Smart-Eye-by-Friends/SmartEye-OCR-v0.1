package com.smarteye.service;

import com.smarteye.presentation.dto.AIDescriptionResult;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.service.analysis.ElementClassifier;
import com.smarteye.service.analysis.PatternMatchingEngine;
import com.smarteye.service.analysis.SpatialAnalysisEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 통합 분석 엔진 - TSPM 모듈 중복 로직 통합
 *
 * 통합된 기능:
 * 1. 공통 패턴 매칭 (문제 번호, 선택지)
 * 2. 공간 근접성 분석 (Proximity-based grouping)
 * 3. 요소 분류 및 구조화
 * 4. 최종 CIM 데이터 모델 생성
 */
@Service
public class UnifiedAnalysisEngine {

    private static final Logger logger = LoggerFactory.getLogger(UnifiedAnalysisEngine.class);

    @Autowired
    private PatternMatchingEngine patternMatchingEngine;

    @Autowired
    private SpatialAnalysisEngine spatialAnalysisEngine;

    @Autowired
    private ElementClassifier elementClassifier;

    /**
     * 통합 분석 실행 - 모든 서비스의 핵심 기능을 하나로 통합
     */
    public UnifiedAnalysisResult performUnifiedAnalysis(
            List<LayoutInfo> layoutElements,
            List<OCRResult> ocrResults,
            List<AIDescriptionResult> aiResults) {

        long startTime = System.currentTimeMillis();
        logger.info("🔄 통합 분석 시작 - 레이아웃: {}개, OCR: {}개, AI: {}개",
                   layoutElements.size(), ocrResults.size(), aiResults.size());

        try {
            // 1. 문제 구조 감지 (문제 번호 위치 추출)
            Map<String, Integer> questionPositions = extractQuestionPositions(ocrResults);
            logger.info("🔍 감지된 문제: {}개", questionPositions.size());

            // 2. 요소 분류 및 문제에 할당
            Map<String, List<AnalysisElement>> elementsByQuestion = groupElementsByQuestion(
                layoutElements, ocrResults, aiResults, questionPositions
            );
            logger.info("📊 요소 그룹핑 완료");

            // 3. 구조화된 데이터 생성
            StructuredData structuredData = generateStructuredData(elementsByQuestion);
            logger.info("🏗️ 구조화된 데이터 생성 완료");

            // 4. CIM 형식으로 변환
            Map<String, Object> cimData = convertToCIMFormat(structuredData);
            logger.info("🔄 CIM 형식 변환 완료");

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("✅ 통합 분석 완료 ({}ms)", processingTime);

            return new UnifiedAnalysisResult(
                true, "통합 분석 성공", null, elementsByQuestion, structuredData, cimData, processingTime
            );

        } catch (Exception e) {
            logger.error("❌ 통합 분석 실패", e);
            return new UnifiedAnalysisResult(
                false, "통합 분석 중 오류 발생: " + e.getMessage(), null, null, null, null, System.currentTimeMillis() - startTime
            );
        }
    }

    /**
     * OCR 결과에서 문제 번호와 위치를 추출
     */
    private Map<String, Integer> extractQuestionPositions(List<OCRResult> ocrResults) {
        Map<String, Integer> positions = new HashMap<>();
        for (OCRResult ocr : ocrResults) {
            if (ocr.getText() == null) continue;
            String questionNumText = patternMatchingEngine.extractQuestionNumber(ocr.getText());
            if (questionNumText != null && ocr.getCoordinates() != null) {
                positions.put(questionNumText, ocr.getCoordinates()[1]); // y1 coordinate
            }
        }
        return positions;
    }

    /**
     * 모든 요소를 문제별로 그룹핑
     */
    private Map<String, List<AnalysisElement>> groupElementsByQuestion(
            List<LayoutInfo> layoutElements,
            List<OCRResult> ocrResults,
            List<AIDescriptionResult> aiResults,
            Map<String, Integer> questionPositions) {

        Map<String, List<AnalysisElement>> groupedElements = new HashMap<>();
        Map<Integer, OCRResult> ocrMap = ocrResults.stream().collect(Collectors.toMap(OCRResult::getId, ocr -> ocr, (a, b) -> a));
        Map<Integer, AIDescriptionResult> aiMap = aiResults.stream().collect(Collectors.toMap(AIDescriptionResult::getId, ai -> ai, (a, b) -> a));

        for (LayoutInfo layout : layoutElements) {
            int elementY = layout.getBox()[1];
            String assignedQuestion = spatialAnalysisEngine.assignElementToNearestQuestion(elementY, questionPositions);

            AnalysisElement element = new AnalysisElement();
            element.setLayoutInfo(layout);
            element.setOcrResult(ocrMap.get(layout.getId()));
            element.setAiResult(aiMap.get(layout.getId()));
            
            String ocrText = Optional.ofNullable(ocrMap.get(layout.getId())).map(OCRResult::getText).orElse("");
            element.setCategory(elementClassifier.determineRefinedType(layout.getClassName(), ocrText, patternMatchingEngine.isChoicePattern(ocrText)));

            groupedElements.computeIfAbsent(assignedQuestion, k -> new ArrayList<>()).add(element);
        }
        return groupedElements;
    }

    /**
     * 구조화된 데이터 생성
     */
    private StructuredData generateStructuredData(Map<String, List<AnalysisElement>> elementsByQuestion) {
        StructuredData structuredData = new StructuredData();
        DocumentInfo docInfo = new DocumentInfo();
        docInfo.setTotalQuestions(elementsByQuestion.keySet().stream().filter(k -> !"unknown".equals(k)).count());
        structuredData.setDocumentInfo(docInfo);

        List<QuestionData> questionDataList = new ArrayList<>();
        for (Map.Entry<String, List<AnalysisElement>> entry : elementsByQuestion.entrySet()) {
            if ("unknown".equals(entry.getKey())) continue;

            QuestionData qd = new QuestionData();
            try {
                qd.setQuestionNumber(Integer.parseInt(entry.getKey()));
            } catch (NumberFormatException e) {
                logger.warn("Invalid question number format: {}", entry.getKey());
                continue;
            }
            qd.setElements(Map.of("main", entry.getValue())); // Simplified grouping
            questionDataList.add(qd);
        }
        structuredData.setQuestions(questionDataList);
        return structuredData;
    }

    /**
     * CIM 형식으로 변환
     */
    private Map<String, Object> convertToCIMFormat(StructuredData structuredData) {
        Map<String, Object> cimData = new HashMap<>();
        cimData.put("document_info", structuredData.getDocumentInfo());
        cimData.put("questions", structuredData.getQuestions());
        return cimData;
    }

    // ============================================================================
    // 내부 데이터 클래스들 (기존 구조 유지)
    // ============================================================================

    public static class UnifiedAnalysisResult {
        private boolean success;
        private String message;
        private List<QuestionStructure> questionStructures;
        private Map<String, List<AnalysisElement>> classifiedElements;
        private StructuredData structuredData;
        private Map<String, Object> cimData;
        private long processingTimeMs;

        public UnifiedAnalysisResult(boolean success, String message, List<QuestionStructure> questionStructures,
                                   Map<String, List<AnalysisElement>> classifiedElements, StructuredData structuredData,
                                   Map<String, Object> cimData, long processingTimeMs) {
            this.success = success;
            this.message = message;
            this.questionStructures = questionStructures;
            this.classifiedElements = classifiedElements;
            this.structuredData = structuredData;
            this.cimData = cimData;
            this.processingTimeMs = processingTimeMs;
        }

        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<QuestionStructure> getQuestionStructures() { return questionStructures; }
        public void setQuestionStructures(List<QuestionStructure> questionStructures) { this.questionStructures = questionStructures; }
        public Map<String, List<AnalysisElement>> getClassifiedElements() { return classifiedElements; }
        public void setClassifiedElements(Map<String, List<AnalysisElement>> classifiedElements) { this.classifiedElements = classifiedElements; }
        public StructuredData getStructuredData() { return structuredData; }
        public void setStructuredData(StructuredData structuredData) { this.structuredData = structuredData; }
        public Map<String, Object> getCimData() { return cimData; }
        public void setCimData(Map<String, Object> cimData) { this.cimData = cimData; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    }

    public static class QuestionStructure {
        private Integer questionNumber;
        private LayoutInfo layoutElement;
        private OCRResult ocrResult;
        private String questionText;
        private List<LayoutInfo> relatedElements;

        // Getters and Setters
        public Integer getQuestionNumber() { return questionNumber; }
        public void setQuestionNumber(Integer questionNumber) { this.questionNumber = questionNumber; }
        public LayoutInfo getLayoutElement() { return layoutElement; }
        public void setLayoutElement(LayoutInfo layoutElement) { this.layoutElement = layoutElement; }
        public OCRResult getOcrResult() { return ocrResult; }
        public void setOcrResult(OCRResult ocrResult) { this.ocrResult = ocrResult; }
        public String getQuestionText() { return questionText; }
        public void setQuestionText(String questionText) { this.questionText = questionText; }
        public List<LayoutInfo> getRelatedElements() { return relatedElements; }
        public void setRelatedElements(List<LayoutInfo> relatedElements) { this.relatedElements = relatedElements; }
    }

    public static class AnalysisElement {
        private LayoutInfo layoutInfo;
        private OCRResult ocrResult;
        private AIDescriptionResult aiResult;
        private String category;

        // Getters and Setters
        public LayoutInfo getLayoutInfo() { return layoutInfo; }
        public void setLayoutInfo(LayoutInfo layoutInfo) { this.layoutInfo = layoutInfo; }
        public OCRResult getOcrResult() { return ocrResult; }
        public void setOcrResult(OCRResult ocrResult) { this.ocrResult = ocrResult; }
        public AIDescriptionResult getAiResult() { return aiResult; }
        public void setAiResult(AIDescriptionResult aiResult) { this.aiResult = aiResult; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
    }

    public static class StructuredData {
        private DocumentInfo documentInfo;
        private List<QuestionData> questions;

        // Getters and Setters
        public DocumentInfo getDocumentInfo() { return documentInfo; }
        public void setDocumentInfo(DocumentInfo documentInfo) { this.documentInfo = documentInfo; }
        public List<QuestionData> getQuestions() { return questions; }
        public void setQuestions(List<QuestionData> questions) { this.questions = questions; }
    }

    public static class DocumentInfo {
        private long totalQuestions;
        private int totalElements;
        private long processingTimestamp;

        // Getters and Setters
        public long getTotalQuestions() { return totalQuestions; }
        public void setTotalQuestions(long totalQuestions) { this.totalQuestions = totalQuestions; }
        public int getTotalElements() { return totalElements; }
        public long getProcessingTimestamp() { return processingTimestamp; }
        public void setProcessingTimestamp(long processingTimestamp) { this.processingTimestamp = processingTimestamp; }
    }

    public static class QuestionData {
        private Integer questionNumber;
        private String questionText;
        private Map<String, List<AnalysisElement>> elements;

        // Getters and Setters
        public Integer getQuestionNumber() { return questionNumber; }
        public void setQuestionNumber(Integer questionNumber) { this.questionNumber = questionNumber; }
        public String getQuestionText() { return questionText; }
        public void setQuestionText(String questionText) { this.questionText = questionText; }
        public Map<String, List<AnalysisElement>> getElements() { return elements; }
        public void setElements(Map<String, List<AnalysisElement>> elements) { this.elements = elements; }
    }
}