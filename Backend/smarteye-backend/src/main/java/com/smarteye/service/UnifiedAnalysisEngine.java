package com.smarteye.service;

import com.smarteye.dto.*;
import com.smarteye.dto.common.LayoutInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 통합 분석 엔진 - TSPM 모듈 중복 로직 통합
 *
 * 통합 기능:
 * 1. 공통 패턴 매칭 (문제 번호, 선택지, 섹션)
 * 2. 레이아웃 분석 로직 통합
 * 3. 구조화된 데이터 변환
 * 4. 성능 최적화된 패턴 매칭
 *
 * 기존 서비스들의 중복 제거:
 * - TSPMEngine
 * - StructuredAnalysisService
 * - CIMService
 * - StructuredJSONService
 */
@Service
public class UnifiedAnalysisEngine {

    private static final Logger logger = LoggerFactory.getLogger(UnifiedAnalysisEngine.class);

    // ============================================================================
    // 통합된 패턴 정의 (기존 서비스들의 중복 패턴 통합)
    // ============================================================================

    /**
     * 문제 번호 패턴들 (모든 서비스에서 공통으로 사용)
     */
    private static final List<Pattern> QUESTION_NUMBER_PATTERNS = Arrays.asList(
        Pattern.compile("(\\d+)번"),           // 1번, 2번 형식
        Pattern.compile("(\\d+)\\."),          // 1., 2. 형식
        Pattern.compile("문제\\s*(\\d+)"),     // 문제 1, 문제 2 형식
        Pattern.compile("(\\d+)\\s*(?:\\)|）)"), // 1), 2) 형식
        Pattern.compile("Q\\s*(\\d+)"),        // Q1, Q2 형식
        Pattern.compile("(\\d{2,3})")          // 593, 594 등 문제번호
    );

    /**
     * 선택지 패턴들 (모든 서비스에서 공통으로 사용)
     */
    private static final List<Pattern> CHOICE_PATTERNS = Arrays.asList(
        Pattern.compile("^[①②③④⑤⑥⑦⑧⑨⑩]"),    // 원문자 선택지
        Pattern.compile("^[(（]\\s*[1-5]\\s*[)）]"),  // (1), (2) 형식
        Pattern.compile("^[1-5]\\s*[.．]")           // 1., 2. 형식
    );

    /**
     * 섹션 패턴들
     */
    private static final List<Pattern> SECTION_PATTERNS = Arrays.asList(
        Pattern.compile("([A-Z])\\s*섹션"),    // A섹션, B섹션
        Pattern.compile("([A-Z])\\s*부분"),    // A부분, B부분
        Pattern.compile("([A-Z])\\s+")         // A, B (단독)
    );

    // ============================================================================
    // 통합된 분석 메서드들
    // ============================================================================

    /**
     * 통합 분석 실행 - 모든 서비스의 핵심 기능을 하나로 통합
     */
    public UnifiedAnalysisResult performUnifiedAnalysis(
            List<LayoutInfo> layoutElements,
            List<OCRResult> ocrResults,
            List<AIDescriptionResult> aiResults) {

        logger.info("🔄 통합 분석 시작 - 레이아웃: {}개, OCR: {}개, AI: {}개",
                   layoutElements.size(), ocrResults.size(), aiResults.size());

        long startTime = System.currentTimeMillis();

        try {
            // 1. 문제 구조 감지 (TSPMEngine + StructuredAnalysisService 통합)
            List<QuestionStructure> questionStructures = detectQuestionStructures(layoutElements, ocrResults);

            // 2. 요소 분류 및 그룹핑
            Map<String, List<AnalysisElement>> classifiedElements = classifyElements(layoutElements, ocrResults, aiResults);

            // 3. 구조화된 데이터 생성 (StructuredJSONService 로직)
            StructuredData structuredData = generateStructuredData(questionStructures, classifiedElements);

            // 4. CIM 형식으로 변환 (CIMService 로직)
            Map<String, Object> cimData = convertToCIMFormat(structuredData);

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("✅ 통합 분석 완료 ({}ms) - 문제: {}개, 분류 요소: {}개",
                       processingTime, questionStructures.size(), classifiedElements.size());

            return new UnifiedAnalysisResult(
                true,
                "통합 분석이 성공적으로 완료되었습니다.",
                questionStructures,
                classifiedElements,
                structuredData,
                cimData,
                processingTime
            );

        } catch (Exception e) {
            logger.error("❌ 통합 분석 실패", e);
            return new UnifiedAnalysisResult(
                false,
                "통합 분석 중 오류 발생: " + e.getMessage(),
                new ArrayList<>(),
                new HashMap<>(),
                null,
                new HashMap<>(),
                System.currentTimeMillis() - startTime
            );
        }
    }

    /**
     * 문제 구조 감지 - TSPMEngine과 StructuredAnalysisService 로직 통합
     */
    public List<QuestionStructure> detectQuestionStructures(
            List<LayoutInfo> layoutElements,
            List<OCRResult> ocrResults) {

        logger.debug("📝 문제 구조 감지 시작");

        List<QuestionStructure> questionStructures = new ArrayList<>();

        // OCR 결과를 Map으로 변환 (성능 최적화)
        Map<Integer, OCRResult> ocrMap = ocrResults.stream()
            .collect(Collectors.toMap(OCRResult::getId, ocr -> ocr));

        // 문제 번호가 포함된 요소들 찾기
        for (LayoutInfo layout : layoutElements) {
            OCRResult ocr = ocrMap.get(layout.getId());
            if (ocr == null || ocr.getText() == null) continue;

            String text = ocr.getText().trim();
            Integer questionNumber = extractQuestionNumber(text);

            if (questionNumber != null) {
                QuestionStructure structure = new QuestionStructure();
                structure.setQuestionNumber(questionNumber);
                structure.setLayoutElement(layout);
                structure.setOcrResult(ocr);
                structure.setQuestionText(text);

                // Y좌표 기반으로 관련 요소들 찾기 (proximity 알고리즘)
                List<LayoutInfo> relatedElements = findRelatedElements(layout, layoutElements, 50); // 50픽셀 범위
                structure.setRelatedElements(relatedElements);

                questionStructures.add(structure);
                logger.debug("✓ 문제 {}번 감지 - 관련 요소: {}개", questionNumber, relatedElements.size());
            }
        }

        // 문제 번호순으로 정렬
        questionStructures.sort(Comparator.comparing(QuestionStructure::getQuestionNumber));

        logger.debug("📝 문제 구조 감지 완료 - 총 {}개 문제", questionStructures.size());
        return questionStructures;
    }

    /**
     * 요소 분류 및 그룹핑 - 모든 서비스의 분류 로직 통합
     */
    public Map<String, List<AnalysisElement>> classifyElements(
            List<LayoutInfo> layoutElements,
            List<OCRResult> ocrResults,
            List<AIDescriptionResult> aiResults) {

        logger.debug("🏷️ 요소 분류 시작");

        Map<String, List<AnalysisElement>> classifiedElements = new HashMap<>();

        // OCR, AI 결과를 Map으로 변환 (성능 최적화)
        Map<Integer, OCRResult> ocrMap = ocrResults.stream()
            .collect(Collectors.toMap(OCRResult::getId, ocr -> ocr));
        Map<Integer, AIDescriptionResult> aiMap = aiResults.stream()
            .collect(Collectors.toMap(AIDescriptionResult::getId, ai -> ai));

        for (LayoutInfo layout : layoutElements) {
            AnalysisElement element = new AnalysisElement();
            element.setLayoutInfo(layout);
            element.setOcrResult(ocrMap.get(layout.getId()));
            element.setAiResult(aiMap.get(layout.getId()));

            // 클래스명 기반 분류
            String category = classifyByClassName(layout.getClassName());

            // OCR 텍스트 기반 세부 분류
            if (element.getOcrResult() != null) {
                String textCategory = classifyByTextPattern(element.getOcrResult().getText());
                if (textCategory != null) {
                    category = textCategory;
                }
            }

            element.setCategory(category);

            classifiedElements.computeIfAbsent(category, k -> new ArrayList<>()).add(element);
        }

        logger.debug("🏷️ 요소 분류 완료 - 카테고리: {}개", classifiedElements.size());
        return classifiedElements;
    }

    /**
     * 구조화된 데이터 생성 - StructuredJSONService 로직
     */
    public StructuredData generateStructuredData(
            List<QuestionStructure> questionStructures,
            Map<String, List<AnalysisElement>> classifiedElements) {

        logger.debug("📊 구조화된 데이터 생성 시작");

        StructuredData structuredData = new StructuredData();

        // 문서 정보 설정
        DocumentInfo documentInfo = new DocumentInfo();
        documentInfo.setTotalQuestions(questionStructures.size());
        documentInfo.setTotalElements(classifiedElements.values().stream()
            .mapToInt(List::size).sum());
        documentInfo.setProcessingTimestamp(System.currentTimeMillis());
        structuredData.setDocumentInfo(documentInfo);

        // 문제별 데이터 구조화
        List<QuestionData> questionDataList = new ArrayList<>();
        for (QuestionStructure structure : questionStructures) {
            QuestionData questionData = new QuestionData();
            questionData.setQuestionNumber(structure.getQuestionNumber());
            questionData.setQuestionText(structure.getQuestionText());

            // 관련 요소들 분류
            Map<String, List<AnalysisElement>> questionElements = new HashMap<>();
            for (LayoutInfo relatedLayout : structure.getRelatedElements()) {
                for (Map.Entry<String, List<AnalysisElement>> entry : classifiedElements.entrySet()) {
                    entry.getValue().stream()
                        .filter(element -> element.getLayoutInfo().getId() == relatedLayout.getId())
                        .forEach(element ->
                            questionElements.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(element)
                        );
                }
            }

            questionData.setElements(questionElements);
            questionDataList.add(questionData);
        }

        structuredData.setQuestions(questionDataList);

        logger.debug("📊 구조화된 데이터 생성 완료 - 문제: {}개", questionDataList.size());
        return structuredData;
    }

    /**
     * CIM 형식으로 변환 - CIMService 로직
     */
    public Map<String, Object> convertToCIMFormat(StructuredData structuredData) {
        logger.debug("🔄 CIM 형식 변환 시작");

        Map<String, Object> cimData = new HashMap<>();

        // 문서 정보
        cimData.put("document_info", structuredData.getDocumentInfo());

        // 문제 데이터
        List<Map<String, Object>> cimQuestions = new ArrayList<>();
        for (QuestionData questionData : structuredData.getQuestions()) {
            Map<String, Object> cimQuestion = new HashMap<>();
            cimQuestion.put("question_number", questionData.getQuestionNumber());
            cimQuestion.put("question_text", questionData.getQuestionText());
            cimQuestion.put("elements", questionData.getElements());
            cimQuestions.add(cimQuestion);
        }
        cimData.put("questions", cimQuestions);

        logger.debug("🔄 CIM 형식 변환 완료");
        return cimData;
    }

    // ============================================================================
    // 유틸리티 메서드들 (기존 서비스들의 중복 메서드 통합)
    // ============================================================================

    /**
     * 문제 번호 추출 (모든 서비스의 공통 로직)
     */
    public Integer extractQuestionNumber(String text) {
        if (text == null || text.trim().isEmpty()) return null;

        for (Pattern pattern : QUESTION_NUMBER_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    // 다음 패턴 시도
                }
            }
        }
        return null;
    }

    /**
     * 선택지 패턴 확인
     */
    public boolean isChoiceText(String text) {
        if (text == null || text.trim().isEmpty()) return false;

        return CHOICE_PATTERNS.stream()
            .anyMatch(pattern -> pattern.matcher(text.trim()).find());
    }

    /**
     * 클래스명 기반 분류
     */
    private String classifyByClassName(String className) {
        if (className == null) return "unknown";

        switch (className.toLowerCase()) {
            case "question_number": return "question_number";
            case "question_text": return "question_text";
            case "choice": return "choice";
            case "answer": return "answer";
            case "explanation": return "explanation";
            case "figure": return "figure";
            case "table": return "table";
            default: return className.toLowerCase();
        }
    }

    /**
     * 텍스트 패턴 기반 분류
     */
    private String classifyByTextPattern(String text) {
        if (text == null || text.trim().isEmpty()) return null;

        // 문제 번호 패턴
        if (extractQuestionNumber(text) != null) {
            return "question_number";
        }

        // 선택지 패턴
        if (isChoiceText(text)) {
            return "choice";
        }

        return null;
    }

    /**
     * Y좌표 기반 관련 요소 찾기 (proximity 알고리즘)
     */
    private List<LayoutInfo> findRelatedElements(LayoutInfo targetLayout, List<LayoutInfo> allLayouts, int proximityThreshold) {
        int targetY = targetLayout.getBox()[1]; // Y1 좌표

        return allLayouts.stream()
            .filter(layout -> {
                int layoutY = layout.getBox()[1];
                return Math.abs(layoutY - targetY) <= proximityThreshold;
            })
            .sorted(Comparator.comparing(layout -> layout.getBox()[0])) // X좌표순 정렬
            .collect(Collectors.toList());
    }

    // ============================================================================
    // 내부 데이터 클래스들
    // ============================================================================

    /**
     * 통합 분석 결과
     */
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

    /**
     * 문제 구조
     */
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

    /**
     * 분석 요소
     */
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

    /**
     * 구조화된 데이터
     */
    public static class StructuredData {
        private DocumentInfo documentInfo;
        private List<QuestionData> questions;

        // Getters and Setters
        public DocumentInfo getDocumentInfo() { return documentInfo; }
        public void setDocumentInfo(DocumentInfo documentInfo) { this.documentInfo = documentInfo; }
        public List<QuestionData> getQuestions() { return questions; }
        public void setQuestions(List<QuestionData> questions) { this.questions = questions; }
    }

    /**
     * 문서 정보
     */
    public static class DocumentInfo {
        private int totalQuestions;
        private int totalElements;
        private long processingTimestamp;

        // Getters and Setters
        public int getTotalQuestions() { return totalQuestions; }
        public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }
        public int getTotalElements() { return totalElements; }
        public void setTotalElements(int totalElements) { this.totalElements = totalElements; }
        public long getProcessingTimestamp() { return processingTimestamp; }
        public void setProcessingTimestamp(long processingTimestamp) { this.processingTimestamp = processingTimestamp; }
    }

    /**
     * 문제 데이터
     */
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