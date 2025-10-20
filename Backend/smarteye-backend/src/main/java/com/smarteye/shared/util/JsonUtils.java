package com.smarteye.shared.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smarteye.application.formatter.FormattedTextGenerator;
import com.smarteye.domain.layout.LayoutClass;
import com.smarteye.presentation.dto.AIDescriptionResult;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.shared.constants.QuestionTypeConstants;
import com.smarteye.shared.exception.FileProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class JsonUtils {

    private static final Logger logger = LoggerFactory.getLogger(JsonUtils.class);

    private final ObjectMapper objectMapper;

    private static FormattedTextGenerator formattedTextGenerator;

    @Autowired
    public void setFormattedTextGenerator(FormattedTextGenerator generator) {
        JsonUtils.formattedTextGenerator = generator;
    }

    public JsonUtils() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
    
    public String toJson(Object object) {
        try {
            String json = objectMapper.writeValueAsString(object);
            logger.debug("Object converted to JSON: {} characters", json.length());
            return json;
            
        } catch (JsonProcessingException e) {
            logger.error("Failed to convert object to JSON: {}", e.getMessage(), e);
            throw new FileProcessingException("JSON 변환에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    public <T> T fromJson(String json, Class<T> clazz) {
        try {
            T object = objectMapper.readValue(json, clazz);
            logger.debug("JSON converted to object: {}", clazz.getSimpleName());
            return object;
            
        } catch (JsonProcessingException e) {
            logger.error("Failed to convert JSON to object: {}", e.getMessage(), e);
            throw new FileProcessingException("JSON 파싱에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    public void saveJsonToFile(Object object, String filePath) {
        try {
            File file = new File(filePath);
            file.getParentFile().mkdirs();
            
            objectMapper.writeValue(file, object);
            logger.info("JSON saved to file: {}", filePath);
            
        } catch (IOException e) {
            logger.error("Failed to save JSON to file: {} - {}", filePath, e.getMessage(), e);
            throw new FileProcessingException("JSON 파일 저장에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    public <T> T loadJsonFromFile(String filePath, Class<T> clazz) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new FileProcessingException("JSON 파일을 찾을 수 없습니다: " + filePath);
            }
            
            T object = objectMapper.readValue(file, clazz);
            logger.info("JSON loaded from file: {}", filePath);
            return object;
            
        } catch (IOException e) {
            logger.error("Failed to load JSON from file: {} - {}", filePath, e.getMessage(), e);
            throw new FileProcessingException("JSON 파일 로드에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    public String toPrettyJson(Object object) {
        try {
            ObjectMapper prettyMapper = new ObjectMapper();
            prettyMapper.registerModule(new JavaTimeModule());
            prettyMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            prettyMapper.enable(SerializationFeature.INDENT_OUTPUT);
            
            return prettyMapper.writeValueAsString(object);
            
        } catch (JsonProcessingException e) {
            logger.error("Failed to convert object to pretty JSON: {}", e.getMessage(), e);
            throw new FileProcessingException("Pretty JSON 변환에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    public boolean isValidJson(String json) {
        try {
            objectMapper.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            logger.debug("Invalid JSON: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * CIM (Content Information Model) 결과 생성
     * Python api_server.py의 create_cim_result() 메서드와 동일한 구조
     */
    public static Map<String, Object> createCIMResult(
            List<LayoutInfo> layoutInfo,
            List<OCRResult> ocrResults,
            List<AIDescriptionResult> aiResults) {
        
        Map<String, Object> cimResult = new HashMap<>();
        
        // Document structure
        Map<String, Object> documentStructure = new HashMap<>();
        Map<String, Object> layoutAnalysis = new HashMap<>();
        
        layoutAnalysis.put("total_elements", layoutInfo.size());
        
        List<Map<String, Object>> elements = new ArrayList<>();
        List<Map<String, Object>> textContent = new ArrayList<>();
        List<Map<String, Object>> aiDescriptions = new ArrayList<>();
        
        // 레이아웃 정보 통합
        for (int i = 0; i < layoutInfo.size(); i++) {
            LayoutInfo info = layoutInfo.get(i);
            Map<String, Object> element = new HashMap<>();
            
            element.put("id", i);
            element.put("class", info.getClassName());
            element.put("confidence", info.getConfidence());
            element.put("bbox", info.getBox());
            element.put("area", info.getArea());
            
            // OCR 텍스트 추가
            String ocrText = findOCRTextById(info.getId(), ocrResults);
            if (ocrText != null && !ocrText.trim().isEmpty()) {
                element.put("text", ocrText);
                Map<String, Object> textItem = new HashMap<>();
                textItem.put("element_id", i);
                textItem.put("text", ocrText);
                textItem.put("class", info.getClassName());
                textContent.add(textItem);
            }
            
            // AI 설명 추가
            String aiDescription = findAIDescriptionById(info.getId(), aiResults);
            if (aiDescription != null && !aiDescription.trim().isEmpty()) {
                element.put("ai_description", aiDescription);
                Map<String, Object> aiItem = new HashMap<>();
                aiItem.put("element_id", i);
                aiItem.put("description", aiDescription);
                aiItem.put("class", info.getClassName());
                aiDescriptions.add(aiItem);
            }
            
            elements.add(element);
        }
        
        layoutAnalysis.put("elements", elements);
        documentStructure.put("layout_analysis", layoutAnalysis);
        documentStructure.put("text_content", textContent);
        documentStructure.put("ai_descriptions", aiDescriptions);
        cimResult.put("document_structure", documentStructure);
        
        // Metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("analysis_date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        metadata.put("total_text_regions", textContent.size());
        metadata.put("total_figures", layoutInfo.stream().mapToInt(info -> LayoutClass.FIGURE.getClassName().equals(info.getClassName()) ? 1 : 0).sum());
        metadata.put("total_tables", layoutInfo.stream().mapToInt(info -> LayoutClass.TABLE.getClassName().equals(info.getClassName()) ? 1 : 0).sum());
        cimResult.put("metadata", metadata);
        
        return cimResult;
    }
    
    /**
     * FormattedText 생성 (FormattedTextGenerator로 위임)
     *
     * <p>이 메서드는 FormattedTextGenerator를 사용하여 다단 레이아웃을 지원하는
     * FormattedText를 생성합니다. 모든 복잡한 처리 로직은 FormattedTextGenerator에
     * 위임되었습니다.</p>
     *
     * @param cimResult CIM 결과 데이터
     * @return 포맷팅된 텍스트 (다단 레이아웃 지원, XSS 방지)
     * @see FormattedTextGenerator#generateWithFallback(Map)
     */
    public static String createFormattedText(Map<String, Object> cimResult) {
        if (formattedTextGenerator == null) {
            logger.error("FormattedTextGenerator가 주입되지 않았습니다. Spring Context 초기화를 확인하세요.");
            return "시스템 초기화 오류: FormattedText 생성기를 사용할 수 없습니다.";
        }
        return formattedTextGenerator.generateWithFallback(cimResult);
    }
    /**
     * 구조화된 결과를 CIM 형태로 변환
     * UnifiedAnalysisEngine.StructuredData → CIM Map<String, Object>
     */
    public static Map<String, Object> convertStructuredResultToCIM(
            com.smarteye.application.analysis.UnifiedAnalysisEngine.StructuredData structuredResult) {

        Map<String, Object> cimResult = new HashMap<>();

        try {
            // Document info 변환 (UnifiedAnalysisEngine 구조에 맞게)
            var docInfo = structuredResult.getDocumentInfo();
            if (docInfo != null) {
                Map<String, Object> documentInfo = new HashMap<>();
                documentInfo.put("total_questions", docInfo.getTotalQuestions());
                documentInfo.put("total_elements", docInfo.getTotalElements());
                documentInfo.put("processing_timestamp", docInfo.getProcessingTimestamp());
                cimResult.put("document_info", documentInfo);
            }

            // Questions 변환 (✅ CIM JSON 구조 수정: question_text 중복 제거)
            List<Map<String, Object>> questions = new ArrayList<>();
            var questionList = structuredResult.getQuestions();
            if (questionList != null) {
                logger.debug("🔄 [CIM-FORMAT] v3.0 변환 시작: {} 개 문제", questionList.size());
                
                for (var question : questionList) {
                    Map<String, Object> questionMap = new LinkedHashMap<>();
                    
                    // v0.7: question_type ID(type_*) 처리
                    String rawQuestionNumber = question.getQuestionNumber();
                    boolean isQuestionType = QuestionTypeConstants.isQuestionTypeIdentifier(rawQuestionNumber);
                    
                    // question_number 필드 처리
                    if (isQuestionType) {
                        // question_type ID는 question_number에 표시하지 않음 (question_type 필드로만 출력)
                        questionMap.put("question_number", "");
                        logger.debug("  🔧 문제 {} - question_type ID 감지, question_number 비움", rawQuestionNumber);
                    } else {
                        // 일반 문제 번호는 그대로 표시
                        questionMap.put("question_number", rawQuestionNumber);
                    }
                    
                    // 🆕 v3.0: content_elements 배열 추가 (우선)
                    if (question.getContentElements() != null && !question.getContentElements().isEmpty()) {
                        List<Map<String, Object>> contentElementsArray = new ArrayList<>();
                        
                        for (var element : question.getContentElements()) {
                            Map<String, Object> elementMap = new LinkedHashMap<>();
                            elementMap.put("type", element.getType());
                            elementMap.put("content", element.getContent());
                            contentElementsArray.add(elementMap);
                        }
                        
                        questionMap.put("content_elements", contentElementsArray);
                        logger.debug("  ✅ 문제 {} - content_elements: {}개", 
                                   question.getQuestionNumber(), contentElementsArray.size());
                    }
                    
                    // 🆕 v3.0: 메타데이터 추가 (question_type)
                    if (question.getQuestionType() != null && !question.getQuestionType().isEmpty()) {
                        String questionType = question.getQuestionType();
                        
                        // v0.7: question_type ID(type_*)인 경우 텍스트만 추출
                        if (QuestionTypeConstants.isQuestionTypeIdentifier(questionType)) {
                            String extractedText = QuestionTypeConstants.extractText(questionType);
                            if (!extractedText.isBlank()) {
                                questionMap.put("question_type", extractedText);
                                logger.debug("  📌 문제 {} - question_type (추출): '{}'", 
                                           rawQuestionNumber, extractedText);
                            }
                        } else {
                            // 일반 텍스트는 그대로 표시
                            questionMap.put("question_type", questionType);
                            logger.debug("  📌 문제 {} - question_type: '{}'", 
                                       rawQuestionNumber, questionType);
                        }
                    }
                    if (question.getUnit() != null && !question.getUnit().isEmpty()) {
                        questionMap.put("unit", question.getUnit());
                        logger.debug("  📌 문제 {} - unit: {}", 
                                   question.getQuestionNumber(), question.getUnit());
                    }
                    
                    // ✅ 하위 호환: question_content_simplified (선택적)
                    Map<String, String> simplifiedContent = question.getQuestionContentSimplified();
                    if (simplifiedContent != null && !simplifiedContent.isEmpty()) {
                        questionMap.put("question_content_simplified", simplifiedContent);
                        logger.debug("  ✅ 문제 {} - 하위 호환 필드 포함 (question_content_simplified)", 
                                   question.getQuestionNumber());
                    }

                    // 🆕 Phase 2: 하위 문항 포함 (sub_questions)
                    if (question.hasSubQuestions()) {
                        List<Map<String, Object>> subQuestionsList = new ArrayList<>();
                        
                        for (var subQuestion : question.getSubQuestions()) {
                            Map<String, Object> subQuestionMap = new LinkedHashMap<>();
                            subQuestionMap.put("sub_question_number", subQuestion.getQuestionNumber());
                            
                            // v3.0: 하위 문항도 content_elements 우선
                            if (subQuestion.getContentElements() != null && !subQuestion.getContentElements().isEmpty()) {
                                List<Map<String, Object>> subContentElementsArray = new ArrayList<>();
                                for (var element : subQuestion.getContentElements()) {
                                    Map<String, Object> elementMap = new LinkedHashMap<>();
                                    elementMap.put("type", element.getType());
                                    elementMap.put("content", element.getContent());
                                    subContentElementsArray.add(elementMap);
                                }
                                subQuestionMap.put("content_elements", subContentElementsArray);
                            }
                            
                            // 하위 호환: simplified content
                            Map<String, String> subContent = subQuestion.getQuestionContentSimplified();
                            if (subContent != null && !subContent.isEmpty()) {
                                subQuestionMap.put("question_content_simplified", subContent);
                            }
                            
                            subQuestionsList.add(subQuestionMap);
                        }
                        
                        questionMap.put("sub_questions", subQuestionsList);
                        logger.debug("  📌 문제 {} - 하위 문항 {}개 포함",
                                    question.getQuestionNumber(),
                                    subQuestionsList.size());
                    }

                    questions.add(questionMap);
                    
                    logger.debug("  ✅ 문제 {} v3.0 변환 완료", question.getQuestionNumber());
                }
                
                logger.debug("✅ [CIM-FORMAT] v3.0 변환 완료: {}개 문제", questions.size());
            }
            cimResult.put("questions", questions);

            // Metadata 추가 (간소화)
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("analysis_date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            metadata.put("conversion_source", "UnifiedAnalysisEngine");
            metadata.put("total_questions", questions.size());
            cimResult.put("metadata", metadata);

        } catch (Exception e) {
            logger.error("구조화된 결과를 CIM으로 변환 실패: {}", e.getMessage(), e);
            // 실패 시 빈 CIM 데이터 반환
            cimResult.put("error", "변환 실패: " + e.getMessage());
            cimResult.put("document_info", Map.of("total_questions", 0, "layout_type", "unknown"));
            cimResult.put("questions", new ArrayList<>());
        }

        return cimResult;
    }

    // Helper methods

    /**
     * ✅ 제안 A: QuestionContentDTO를 Map으로 변환
     */
    private static Map<String, Object> convertQuestionContentToMap(
            com.smarteye.application.analysis.dto.QuestionContentDTO content) {
        Map<String, Object> contentMap = new HashMap<>();
        
        if (content.getQuestionText() != null) {
            contentMap.put("question_text", content.getQuestionText());
        }
        
        if (content.getPlainText() != null && !content.getPlainText().isEmpty()) {
            contentMap.put("plain_text", content.getPlainText());
        }
        
        if (content.getOcrResults() != null && !content.getOcrResults().isEmpty()) {
            List<Map<String, Object>> ocrList = new ArrayList<>();
            for (var ocr : content.getOcrResults()) {
                Map<String, Object> ocrMap = new HashMap<>();
                ocrMap.put("text", ocr.getText());
                ocrMap.put("element_id", ocr.getElementId());
                ocrMap.put("type", ocr.getType());
                if (ocr.getBbox() != null) {
                    ocrMap.put("bbox", ocr.getBbox());
                }
                if (ocr.getConfidence() != null) {
                    ocrMap.put("confidence", ocr.getConfidence());
                }
                ocrList.add(ocrMap);
            }
            contentMap.put("ocr_results", ocrList);
        }
        
        if (content.getAiDescriptions() != null && !content.getAiDescriptions().isEmpty()) {
            List<Map<String, Object>> aiList = new ArrayList<>();
            for (var ai : content.getAiDescriptions()) {
                Map<String, Object> aiMap = new HashMap<>();
                aiMap.put("description", ai.getDescription());
                aiMap.put("element_id", ai.getElementId());
                aiMap.put("element_type", ai.getElementType());
                if (ai.getBbox() != null) {
                    aiMap.put("bbox", ai.getBbox());
                }
                aiList.add(aiMap);
            }
            contentMap.put("ai_descriptions", aiList);
        }
        
        if (content.getPassage() != null) {
            contentMap.put("passage", content.getPassage());
        }
        
        if (content.getChoices() != null && !content.getChoices().isEmpty()) {
            contentMap.put("choices", content.getChoices());
        }
        
        if (content.getImages() != null && !content.getImages().isEmpty()) {
            List<Map<String, Object>> imagesList = new ArrayList<>();
            for (var img : content.getImages()) {
                Map<String, Object> imgMap = new HashMap<>();
                imgMap.put("element_id", img.getElementId());
                imgMap.put("description", img.getDescription());
                if (img.getBbox() != null) {
                    imgMap.put("bbox", img.getBbox());
                }
                if (img.getConfidence() != null) {
                    imgMap.put("confidence", img.getConfidence());
                }
                imagesList.add(imgMap);
            }
            contentMap.put("images", imagesList);
        }
        
        if (content.getTables() != null && !content.getTables().isEmpty()) {
            List<Map<String, Object>> tablesList = new ArrayList<>();
            for (var table : content.getTables()) {
                Map<String, Object> tableMap = new HashMap<>();
                tableMap.put("element_id", table.getElementId());
                if (table.getData() != null) {
                    tableMap.put("data", table.getData());
                }
                if (table.getBbox() != null) {
                    tableMap.put("bbox", table.getBbox());
                }
                tablesList.add(tableMap);
            }
            contentMap.put("tables", tablesList);
        }
        
        return contentMap;
    }

    private static String findOCRTextById(int id, List<OCRResult> ocrResults) {
        return ocrResults.stream()
            .filter(result -> result.getId() == id)
            .map(OCRResult::getText)
            .findFirst()
            .orElse(null);
    }
    
    private static String findAIDescriptionById(int id, List<AIDescriptionResult> aiResults) {
        return aiResults.stream()
            .filter(result -> result.getId() == id)
            .map(AIDescriptionResult::getDescription)
            .findFirst()
            .orElse(null);
    }
    
        /**
     * 🔧 simplifiedContent → elements 변환 (빈 요소 완전 제외)
     * 
     * 규칙:
     * 1. null 또는 빈 문자열은 무조건 제외
     * 2. 텍스트 요소 (OCR): 빈 문자열 제외
     * 3. 비텍스트 요소 (figure, table 등): AI 설명이 있을 때만 포함
     * 
     * @param simplifiedContent UnifiedAnalysisEngine에서 생성한 간소화된 콘텐츠
     * @return 실제 콘텐츠가 있는 요소만 포함된 동적 JSON 구조
     */
    private static Map<String, Object> convertSimplifiedToElements(Map<String, String> simplifiedContent) {
        if (simplifiedContent == null || simplifiedContent.isEmpty()) {
            logger.warn("  ⚠️ simplifiedContent가 비어있음 - 빈 elements 반환");
            return new LinkedHashMap<>();
        }
        
        Map<String, Object> elements = new LinkedHashMap<>();
        int includedCount = 0;
        int excludedCount = 0;
        
        for (Map.Entry<String, String> entry : simplifiedContent.entrySet()) {
            String className = entry.getKey();
            String content = entry.getValue();
            
            // 🔧 규칙: null 또는 빈 문자열은 무조건 제외
            if (content == null || content.trim().isEmpty()) {
                logger.debug("    ⊘ 클래스 '{}' - 빈 콘텐츠로 제외", className);
                excludedCount++;
                continue;
            }
            
            // 🔧 실제 콘텐츠가 있으면 포함
            // LAM 클래스명을 그대로 키로 사용 (동적 구조)
            elements.put(className, content);
            includedCount++;
            
            logger.trace("    ✅ 클래스 '{}' 추가: {}자", className, content.length());
        }
        
        logger.debug("    📦 elements 생성 완료: {}개 포함, {}개 제외", includedCount, excludedCount);
        return elements;
    }
}