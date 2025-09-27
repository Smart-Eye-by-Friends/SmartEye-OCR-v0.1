package com.smarteye.application.analysis;

import com.smarteye.presentation.dto.AIDescriptionResult;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.application.analysis.engine.ElementClassifier;
import com.smarteye.application.analysis.engine.PatternMatchingEngine;
import com.smarteye.application.analysis.engine.SpatialAnalysisEngine;
import org.slf4j.Logger;
import com.smarteye.application.analysis.AnalysisJobService;
import com.smarteye.application.user.UserService;
import com.smarteye.domain.document.entity.DocumentPage;
import com.smarteye.infrastructure.external.*;
import com.smarteye.application.file.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.Arrays;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
     * 모든 요소를 문제별로 그룹핑 (강화된 다중 처리)
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
     * 🔧 강화된 구조화된 데이터 생성 (questionText 추출 로직 추가)
     */
    private StructuredData generateStructuredData(Map<String, List<AnalysisElement>> elementsByQuestion) {
        StructuredData structuredData = new StructuredData();
        DocumentInfo docInfo = new DocumentInfo();

        // 유효한 문제 수 계산 ("unknown" 제외)
        long validQuestions = elementsByQuestion.keySet().stream()
            .filter(k -> !"unknown".equals(k))
            .count();
        docInfo.setTotalQuestions(validQuestions);

        // 총 요소 수 계산
        int totalElements = elementsByQuestion.values().stream()
            .mapToInt(List::size)
            .sum();
        docInfo.setTotalElements(totalElements);
        docInfo.setProcessingTimestamp(System.currentTimeMillis());

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

            // 🔥 핵심 수정: questionText 추출 로직 추가
            String questionText = extractQuestionTextFromElements(entry.getValue());
            qd.setQuestionText(questionText != null ? questionText : "문제 텍스트 추출 중...");

            qd.setElements(Map.of("main", entry.getValue()));
            questionDataList.add(qd);

            logger.debug("✅ 문제 {}번: 텍스트='{}', 요소={}개",
                        entry.getKey(),
                        questionText != null ? questionText.substring(0, Math.min(20, questionText.length())) + "..." : "null",
                        entry.getValue().size());
        }

        // 문제 번호순 정렬
        questionDataList.sort(Comparator.comparing(QuestionData::getQuestionNumber));
        structuredData.setQuestions(questionDataList);

        logger.info("🏗️ 구조화된 데이터 생성 완료: 문제 {}개, 총 요소 {}개",
                   questionDataList.size(), totalElements);

        return structuredData;
    }

    /**
     * CIM 형식으로 변환 (완전한 구조 생성)
     */
    private Map<String, Object> convertToCIMFormat(StructuredData structuredData) {
        Map<String, Object> cimData = new HashMap<>();

        // Document structure 생성 (JsonUtils.createFormattedText 호환)
        Map<String, Object> documentStructure = new HashMap<>();
        Map<String, Object> layoutAnalysis = new HashMap<>();

        // Elements 리스트 생성
        List<Map<String, Object>> elements = new ArrayList<>();

        // 구조화된 데이터에서 elements 추출 및 변환
        if (structuredData.getQuestions() != null) {
            int elementId = 0;
            for (QuestionData question : structuredData.getQuestions()) {
                if (question.getElements() != null) {
                    for (Map.Entry<String, List<AnalysisElement>> entry : question.getElements().entrySet()) {
                        for (AnalysisElement analysisElement : entry.getValue()) {
                            Map<String, Object> element = new HashMap<>();
                            element.put("id", elementId++);

                            // 레이아웃 정보에서 클래스명 추출
                            String className = analysisElement.getLayoutInfo() != null ?
                                analysisElement.getLayoutInfo().getClassName() : "plain_text";
                            element.put("class", className);

                            // 좌표 정보 추가
                            if (analysisElement.getLayoutInfo() != null && analysisElement.getLayoutInfo().getBox() != null) {
                                element.put("bbox", Arrays.asList(
                                    analysisElement.getLayoutInfo().getBox()[0],
                                    analysisElement.getLayoutInfo().getBox()[1],
                                    analysisElement.getLayoutInfo().getBox()[2],
                                    analysisElement.getLayoutInfo().getBox()[3]
                                ));
                                element.put("area", analysisElement.getLayoutInfo().getArea());
                            } else {
                                // 기본 bbox 설정
                                element.put("bbox", Arrays.asList(0, 0, 100, 50));
                                element.put("area", 5000);
                            }

                            // 신뢰도 추가
                            if (analysisElement.getLayoutInfo() != null) {
                                element.put("confidence", analysisElement.getLayoutInfo().getConfidence());
                            } else {
                                element.put("confidence", 0.8);
                            }

                            // OCR 텍스트 추가
                            if (analysisElement.getOcrResult() != null &&
                                analysisElement.getOcrResult().getText() != null &&
                                !analysisElement.getOcrResult().getText().trim().isEmpty()) {
                                element.put("text", analysisElement.getOcrResult().getText());
                            }

                            // AI 설명 추가
                            if (analysisElement.getAiResult() != null &&
                                analysisElement.getAiResult().getDescription() != null &&
                                !analysisElement.getAiResult().getDescription().trim().isEmpty()) {
                                element.put("ai_description", analysisElement.getAiResult().getDescription());
                            }

                            elements.add(element);
                        }
                    }
                }

                // 질문 텍스트가 있으면 별도 요소로 추가
                if (question.getQuestionText() != null && !question.getQuestionText().trim().isEmpty()) {
                    Map<String, Object> questionElement = new HashMap<>();
                    questionElement.put("id", elementId++);
                    questionElement.put("class", "question_text");
                    questionElement.put("text", question.getQuestionText());
                    questionElement.put("bbox", Arrays.asList(0, 0, 500, 100));
                    questionElement.put("confidence", 0.9);
                    questionElement.put("area", 50000);
                    elements.add(questionElement);
                }

                // 질문 번호 요소 추가
                if (question.getQuestionNumber() != null) {
                    Map<String, Object> numberElement = new HashMap<>();
                    numberElement.put("id", elementId++);
                    numberElement.put("class", "question_number");
                    numberElement.put("text", question.getQuestionNumber().toString());
                    numberElement.put("bbox", Arrays.asList(0, 0, 100, 50));
                    numberElement.put("confidence", 0.95);
                    numberElement.put("area", 5000);
                    elements.add(numberElement);
                }
            }
        }

        layoutAnalysis.put("total_elements", elements.size());
        layoutAnalysis.put("elements", elements);
        documentStructure.put("layout_analysis", layoutAnalysis);

        // Text content 생성
        List<Map<String, Object>> textContent = new ArrayList<>();
        List<Map<String, Object>> aiDescriptions = new ArrayList<>();

        for (Map<String, Object> element : elements) {
            Integer elementId = (Integer) element.get("id");
            String className = (String) element.get("class");

            if (element.containsKey("text")) {
                Map<String, Object> textItem = new HashMap<>();
                textItem.put("element_id", elementId);
                textItem.put("text", element.get("text"));
                textItem.put("class", className);
                textContent.add(textItem);
            }

            if (element.containsKey("ai_description")) {
                Map<String, Object> aiItem = new HashMap<>();
                aiItem.put("element_id", elementId);
                aiItem.put("description", element.get("ai_description"));
                aiItem.put("class", className);
                aiDescriptions.add(aiItem);
            }
        }

        documentStructure.put("text_content", textContent);
        documentStructure.put("ai_descriptions", aiDescriptions);
        cimData.put("document_structure", documentStructure);

        // Metadata 생성
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("analysis_date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        metadata.put("total_text_regions", textContent.size());
        metadata.put("total_elements", elements.size());
        metadata.put("source", "UnifiedAnalysisEngine");
        cimData.put("metadata", metadata);

        // 구조화된 데이터도 추가 (fallback용)
        cimData.put("document_info", structuredData.getDocumentInfo());
        cimData.put("questions", structuredData.getQuestions());

        logger.info("✅ CIM 형식 변환 완료 - Elements: {}개, TextContent: {}개",
                   elements.size(), textContent.size());

        return cimData;
    }

    /**
     * 🔍 요소들로부터 문제 텍스트 추출 (새로운 핵심 메서드)
     */
    private String extractQuestionTextFromElements(List<AnalysisElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return null;
        }

        StringBuilder questionText = new StringBuilder();

        // 1. 문제 텍스트 카테고리 우선 검색
        for (AnalysisElement element : elements) {
            if (isQuestionTextElement(element)) {
                String text = extractCleanText(element);
                if (text != null && text.length() > 10) { // 의미있는 길이
                    questionText.append(text).append(" ");
                }
            }
        }

        // 2. 문제 텍스트가 부족한 경우 다른 텍스트 요소들 활용
        if (questionText.length() < 20) {
            for (AnalysisElement element : elements) {
                if (element.getCategory() != null &&
                    (element.getCategory().contains("text") ||
                     element.getCategory().contains("title") ||
                     element.getCategory().contains("paragraph"))) {
                    String text = extractCleanText(element);
                    if (text != null && text.length() > 5) {
                        questionText.append(text).append(" ");
                    }
                }
            }
        }

        // 3. 최종 정리 및 검증
        String result = questionText.toString().trim();
        if (result.isEmpty()) {
            return null;
        }

        // 너무 긴 텍스트는 잘라내기 (200자 제한)
        if (result.length() > 200) {
            result = result.substring(0, 197) + "...";
        }

        return result;
    }

    /**
     * 문제 텍스트 요소인지 판단
     */
    private boolean isQuestionTextElement(AnalysisElement element) {
        if (element == null) return false;

        // 카테고리 기반 판단
        String category = element.getCategory();
        if (category != null) {
            return category.equals("question_text") ||
                   category.equals("passage") ||
                   category.equals("plain_text") ||
                   category.contains("text");
        }

        // 레이아웃 클래스 기반 판단
        if (element.getLayoutInfo() != null) {
            String className = element.getLayoutInfo().getClassName();
            return "text".equals(className) ||
                   "paragraph".equals(className) ||
                   "title".equals(className);
        }

        return false;
    }

    /**
     * 요소에서 깨끗한 텍스트 추출
     */
    private String extractCleanText(AnalysisElement element) {
        if (element == null) return null;

        // OCR 텍스트 우선
        if (element.getOcrResult() != null &&
            element.getOcrResult().getText() != null &&
            !element.getOcrResult().getText().trim().isEmpty()) {

            String text = element.getOcrResult().getText().trim();

            // 문제 번호 패턴 제거 ("1.", "1번", "Q1" 등)
            text = text.replaceAll("^\\d+[.번)]\\s*", "");
            text = text.replaceAll("^Q\\d+\\s*", "");
            text = text.replaceAll("^문제\\s*\\d+\\s*", "");

            return text.trim();
        }

        // AI 설명 보조 사용
        if (element.getAiResult() != null &&
            element.getAiResult().getDescription() != null &&
            !element.getAiResult().getDescription().trim().isEmpty()) {
            return element.getAiResult().getDescription().trim();
        }

        return null;
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

        // Convenience methods
        public long getTotalQuestions() {
            return documentInfo != null ? documentInfo.getTotalQuestions() : 0;
        }
        public int getTotalElements() {
            return documentInfo != null ? documentInfo.getTotalElements() : 0;
        }
    }

    public static class DocumentInfo {
        private long totalQuestions;
        private int totalElements;
        private long processingTimestamp;

        // Getters and Setters
        public long getTotalQuestions() { return totalQuestions; }
        public void setTotalQuestions(long totalQuestions) { this.totalQuestions = totalQuestions; }
        public int getTotalElements() { return totalElements; }
        public void setTotalElements(int totalElements) { this.totalElements = totalElements; }
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