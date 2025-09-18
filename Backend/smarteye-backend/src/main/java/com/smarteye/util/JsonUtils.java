package com.smarteye.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smarteye.dto.AIDescriptionResult;
import com.smarteye.dto.OCRResult;
import com.smarteye.dto.common.LayoutInfo;
import com.smarteye.exception.FileProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        metadata.put("total_figures", layoutInfo.stream().mapToInt(info -> "figure".equals(info.getClassName()) ? 1 : 0).sum());
        metadata.put("total_tables", layoutInfo.stream().mapToInt(info -> "table".equals(info.getClassName()) ? 1 : 0).sum());
        cimResult.put("metadata", metadata);
        
        return cimResult;
    }
    
    /**
     * 포맷팅된 텍스트 생성
     * Python api_server.py의 create_formatted_text() 메서드와 동일한 로직
     */
    public static String createFormattedText(Map<String, Object> cimResult) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> documentStructure = (Map<String, Object>) cimResult.get("document_structure");
            @SuppressWarnings("unchecked")
            Map<String, Object> layoutAnalysis = (Map<String, Object>) documentStructure.get("layout_analysis");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> elements = (List<Map<String, Object>>) layoutAnalysis.get("elements");
            
            // 포맷팅 규칙 정의 (Python 코드와 동일하지만 HTML 표시 개선)
            Map<String, FormattingRule> formattingRules = Map.ofEntries(
                Map.entry("title", new FormattingRule("", "\n\n", 0)),
                Map.entry("question_number", new FormattingRule("", ". ", 0)),
                Map.entry("question_type", new FormattingRule("    ", "\n", 3)), // 4칸으로 증가
                Map.entry("question_text", new FormattingRule("    ", "\n", 3)), // 4칸으로 증가
                Map.entry("plain_text", new FormattingRule("", "\n", 0)),
                Map.entry("table_caption", new FormattingRule("\n", "\n", 0)),
                Map.entry("table_footnote", new FormattingRule("", "\n\n", 0)),
                Map.entry("isolated_formula", new FormattingRule("\n", "\n\n", 0)),
                Map.entry("formula_caption", new FormattingRule("", "\n", 0)),
                Map.entry("abandon_text", new FormattingRule("[삭제됨] ", "\n", 0)),
                Map.entry("figure", new FormattingRule("\n[그림 설명] ", "\n\n", 0)),
                Map.entry("table", new FormattingRule("\n[표 설명] ", "\n\n", 0))
            );
            
            // 요소들을 위치 기준으로 정렬 (Y 좌표 기준)
            List<ElementWithContent> elementsWithContent = new ArrayList<>();
            
            for (Map<String, Object> element : elements) {
                String className = ((String) element.get("class")).toLowerCase().replace(" ", "_");
                
                // bbox 타입 안전 처리
                Object bboxObj = element.get("bbox");
                List<Integer> bbox;
                
                if (bboxObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Integer> bboxList = (List<Integer>) bboxObj;
                    bbox = bboxList;
                } else if (bboxObj instanceof int[]) {
                    int[] bboxArray = (int[]) bboxObj;
                    bbox = Arrays.asList(bboxArray[0], bboxArray[1], bboxArray[2], bboxArray[3]);
                } else {
                    logger.warn("알 수 없는 bbox 타입: {} - 요소 ID: {}", bboxObj.getClass(), element.get("id"));
                    continue; // 이 요소는 건너뛰기
                }
                
                String content = null;
                String contentType = null;
                
                // OCR 텍스트 확인
                if (element.containsKey("text")) {
                    content = (String) element.get("text");
                    contentType = "ocr";
                }
                // AI 설명 확인
                else if (element.containsKey("ai_description")) {
                    content = (String) element.get("ai_description");
                    contentType = "ai";
                }
                
                if (content != null && !content.trim().isEmpty()) {
                    elementsWithContent.add(new ElementWithContent(
                        (Integer) element.get("id"),
                        className,
                        content.trim(),
                        contentType,
                        bbox.get(1), // y 좌표
                        bbox.get(0)  // x 좌표
                    ));
                }
            }
            
            // Y 좌표 기준으로 정렬
            elementsWithContent.sort((a, b) -> {
                int yCompare = Integer.compare(a.yPosition, b.yPosition);
                return yCompare != 0 ? yCompare : Integer.compare(a.xPosition, b.xPosition);
            });
            
            // 포맷팅된 텍스트 생성
            StringBuilder formattedText = new StringBuilder();
            String prevClass = null;
            
            for (ElementWithContent element : elementsWithContent) {
                FormattingRule rule = formattingRules.getOrDefault(element.className,
                    new FormattingRule("", "\n", 0));
                
                String formattedLine;
                
                // 문제번호와 문제텍스트가 연속으로 나오는 경우 처리
                if ("question_text".equals(element.className) && "question_number".equals(prevClass)) {
                    formattedLine = element.content + rule.suffix;
                } else {
                    formattedLine = rule.prefix + element.content + rule.suffix;
                }
                
                formattedText.append(formattedLine);
                prevClass = element.className;
            }
            
            // 연속된 빈 줄 정리
            return cleanupFormattedText(formattedText.toString());
            
        } catch (Exception e) {
            logger.error("포맷팅된 텍스트 생성 실패: {}", e.getMessage(), e);
            return "텍스트 포맷팅 중 오류가 발생했습니다.";
        }
    }
    
    // Helper methods
    
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
    
    private static String cleanupFormattedText(String text) {
        String[] lines = text.split("\n");
        List<String> cleanedLines = new ArrayList<>();
        boolean prevEmpty = false;
        
        for (String line : lines) {
            boolean isEmpty = line.trim().isEmpty();
            
            // 연속된 빈 줄이 3개 이상 나오지 않도록 제한
            if (isEmpty && prevEmpty) {
                continue;
            }
            
            cleanedLines.add(line);
            prevEmpty = isEmpty;
        }
        
        return String.join("\n", cleanedLines).trim();
    }
    
    // Helper classes
    
    private static class FormattingRule {
        final String prefix;
        final String suffix;
        final int indent;
        
        FormattingRule(String prefix, String suffix, int indent) {
            this.prefix = prefix;
            this.suffix = suffix;
            this.indent = indent;
        }
    }
    
    private static class ElementWithContent {
        final int id;
        final String className;
        final String content;
        final String type;
        final int yPosition;
        final int xPosition;
        
        ElementWithContent(int id, String className, String content, String type, int yPosition, int xPosition) {
            this.id = id;
            this.className = className;
            this.content = content;
            this.type = type;
            this.yPosition = yPosition;
            this.xPosition = xPosition;
        }
    }
}