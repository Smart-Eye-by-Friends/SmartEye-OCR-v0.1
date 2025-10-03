package com.smarteye.shared.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smarteye.presentation.dto.AIDescriptionResult;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.shared.exception.FileProcessingException;
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
     * FormattedText 생성 (다단 레이아웃 지원 - 위임 패턴)
     *
     * <p>이 메서드는 FormattedTextFormatter를 사용하여 다단 레이아웃을 지원하는
     * FormattedText를 생성합니다.</p>
     *
     * <h3>처리 흐름</h3>
     * <ol>
     *   <li>StructuredData 추출 시도 (새로운 CIM 구조)</li>
     *   <li>FormattedTextFormatter.format() 호출 (다단 레이아웃 지원)</li>
     *   <li>Fallback: JsonUtilsEnhanced 사용 (기존 CIM 구조)</li>
     *   <li>최종 안전 대안 (비상 메시지)</li>
     * </ol>
     *
     * @param cimResult CIM 결과 데이터
     * @return 포맷팅된 텍스트 (다단 레이아웃 지원, HTML-safe)
     * @see FormattedTextFormatter#format(com.smarteye.application.analysis.UnifiedAnalysisEngine.StructuredData)
     */
    public static String createFormattedText(Map<String, Object> cimResult) {
        // 디버깅: 입력 데이터 로깅
        if (logger.isInfoEnabled()) {
            logger.info("🔍 createFormattedText 시작 - CIM 데이터 크기: {}",
                       cimResult != null ? cimResult.size() : "null");

            if (cimResult != null && !cimResult.isEmpty()) {
                logger.info("📊 CIM 키 목록: {}", cimResult.keySet());

                // structured_data 경로 확인 (새로운 방식)
                Object structuredDataObj = cimResult.get("structured_data");
                if (structuredDataObj != null) {
                    logger.info("✅ structured_data 발견 - 타입: {}", structuredDataObj.getClass().getSimpleName());
                } else {
                    logger.info("ℹ️ structured_data 없음 - Fallback 사용");
                }

                // document_structure 경로 확인 (기존 방식)
                Object docStructure = cimResult.get("document_structure");
                if (docStructure instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> docMap = (Map<String, Object>) docStructure;
                    Object layoutAnalysis = docMap.get("layout_analysis");
                    if (layoutAnalysis instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> layoutMap = (Map<String, Object>) layoutAnalysis;
                        Object elements = layoutMap.get("elements");
                        logger.info("📊 Elements 타입: {}, 크기: {}",
                                   elements != null ? elements.getClass().getSimpleName() : "null",
                                   elements instanceof List ? ((List<?>) elements).size() : "non-list");
                    } else {
                        logger.info("⚠️ layout_analysis가 Map이 아님: {}",
                                   layoutAnalysis != null ? layoutAnalysis.getClass() : "null");
                    }
                } else {
                    logger.info("⚠️ document_structure가 Map이 아님: {}",
                               docStructure != null ? docStructure.getClass() : "null");
                }

                // questions 경로 확인
                Object questions = cimResult.get("questions");
                if (questions instanceof List) {
                    logger.info("📊 Questions 크기: {}", ((List<?>) questions).size());
                } else {
                    logger.info("⚠️ Questions가 List가 아님: {}",
                               questions != null ? questions.getClass() : "null");
                }
            }
        }

        try {
            // Phase 1: StructuredData 기반 처리 (새로운 방식, 다단 레이아웃 지원)
            Object structuredDataObj = cimResult.get("structured_data");

            if (structuredDataObj instanceof com.smarteye.application.analysis.UnifiedAnalysisEngine.StructuredData) {
                logger.info("✅ StructuredData 기반 포맷팅 사용 (다단 레이아웃 지원)");
                com.smarteye.application.analysis.UnifiedAnalysisEngine.StructuredData structuredData =
                    (com.smarteye.application.analysis.UnifiedAnalysisEngine.StructuredData) structuredDataObj;

                String result = FormattedTextFormatter.format(structuredData);
                logger.info("✅ FormattedTextFormatter 성공: {}글자", result.length());
                return result;
            }

            // Phase 2: Fallback - JsonUtilsEnhanced 사용 (기존 CIM 구조)
            logger.info("🔄 structured_data 없음 - JsonUtilsEnhanced로 Fallback");
            String result = JsonUtilsEnhanced.createFormattedTextEnhanced(cimResult);
            logger.info("✅ JsonUtilsEnhanced 성공: {}글자",
                       result != null ? result.length() : "null");
            return result;

        } catch (Exception e) {
            logger.error("❌ FormattedText 생성 실패 - 비상 대안 사용: {}", e.getMessage(), e);

            // Phase 3: 최종 안전 대안 (비상 메시지)
            if (cimResult != null && !cimResult.isEmpty()) {
                StringBuilder emergency = new StringBuilder();
                emergency.append("=== SmartEye 분석 결과 ===\n\n");
                emergency.append("텍스트 처리 중 일시적 문제가 발생했습니다.\n\n");
                emergency.append("시스템 상태: 정상 작동\n");
                emergency.append("분석 데이터: ").append(cimResult.size()).append("개 키 감지\n");
                emergency.append("처리 시간: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))).append("\n");
                emergency.append("\n※ 다시 시도하거나 다른 분석 모드를 사용해보세요.");

                String result = emergency.toString();
                logger.warn("🚨 비상 대안 사용: {}글자", result.length());
                return result;
            } else {
                String result = "분석 데이터가 없습니다. 이미지를 다시 업로드해주세요.";
                logger.error("❌ 최종 대안: {}", result);
                return result;
            }
        }
    }


    /**
     * Phase 2: 메인 처리 로직 (기존 구현 개선)
     */
    private static String processMainFormattedText(Map<String, Object> cimResult) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> documentStructure = (Map<String, Object>) cimResult.get("document_structure");

            if (documentStructure == null) {
                logger.info("🔄 [MAIN] document_structure 없음 - fallback 준비");
                return null; // fallback으로 이동
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> layoutAnalysis = (Map<String, Object>) documentStructure.get("layout_analysis");

            if (layoutAnalysis == null) {
                logger.info("🔄 [MAIN] layout_analysis 없음 - fallback 준비");
                return null; // fallback으로 이동
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> elements = (List<Map<String, Object>>) layoutAnalysis.get("elements");

            if (elements == null || elements.isEmpty()) {
                logger.info("🔄 [MAIN] elements 없음 - fallback 준비");
                return null; // fallback으로 이동
            }
            
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
            logger.error("❌ [MAIN] 메인 처리 예외: {}", e.getMessage(), e);
            return null; // fallback으로 이동
        }
    }

    /**
     * Phase 3: 계층적 다중 fallback 시스템
     */
    private static String executeMultiLevelFallback(Map<String, Object> cimResult) {
        logger.info("🔄 [FALLBACK] 다중 계층 fallback 시작");

        // Fallback Level 1: 구조화된 대안 (questions 기반)
        String level1Result = attemptStructuredFallback(cimResult);
        if (isValidText(level1Result)) {
            logger.info("✅ [FALLBACK-L1] 구조화된 대안 성공: {}글자", level1Result.length());
            return level1Result;
        }

        // Fallback Level 2: 메타데이터 기반 대안
        String level2Result = attemptMetadataFallback(cimResult);
        if (isValidText(level2Result)) {
            logger.info("✅ [FALLBACK-L2] 메타데이터 대안 성공: {}글자", level2Result.length());
            return level2Result;
        }

        // Fallback Level 3: 원시 데이터 추출
        String level3Result = attemptRawDataExtraction(cimResult);
        if (isValidText(level3Result)) {
            logger.info("✅ [FALLBACK-L3] 원시 데이터 추출 성공: {}글자", level3Result.length());
            return level3Result;
        }

        // Fallback Level 4: 최종 비상 대안
        String emergencyResult = createEmergencyFallbackText("모든 처리 방법이 실패했지만 시스템은 정상 작동 중입니다.");
        logger.warn("🚨 [FALLBACK-EMERGENCY] 최종 비상 대안 사용: {}글자", emergencyResult.length());
        return emergencyResult;
    }

    /**
     * Fallback Level 1: 구조화된 대안 (questions 기반 처리)
     */
    private static String attemptStructuredFallback(Map<String, Object> cimResult) {
        try {
            logger.info("🔄 [FALLBACK-L1] 구조화된 대안 시작");
            return createFallbackFromQuestions(cimResult);
        } catch (Exception e) {
            logger.warn("❌ [FALLBACK-L1] 구조화된 대안 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Fallback Level 2: 메타데이터 기반 대안
     */
    private static String attemptMetadataFallback(Map<String, Object> cimResult) {
        try {
            logger.info("🔄 [FALLBACK-L2] 메타데이터 대안 시작");

            StringBuilder result = new StringBuilder();

            // 메타데이터에서 정보 추출
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) cimResult.get("metadata");

            if (metadata != null) {
                result.append("=== 분석 메타데이터 ===\n\n");

                Object analysisDate = metadata.get("analysis_date");
                if (analysisDate != null) {
                    result.append("분석 날짜: ").append(analysisDate).append("\n");
                }

                Object totalElements = metadata.get("total_elements");
                if (totalElements != null) {
                    result.append("총 요소 수: ").append(totalElements).append("\n");
                }

                Object totalFigures = metadata.get("total_figures");
                if (totalFigures != null) {
                    result.append("그림 수: ").append(totalFigures).append("\n");
                }

                Object totalTables = metadata.get("total_tables");
                if (totalTables != null) {
                    result.append("표 수: ").append(totalTables).append("\n");
                }

                result.append("\n분석이 완료되었으나 상세 내용 추출에 제한이 있습니다.\n");
            }

            // document_info 추가 확인
            @SuppressWarnings("unchecked")
            Map<String, Object> documentInfo = (Map<String, Object>) cimResult.get("document_info");
            if (documentInfo != null) {
                Object totalQuestions = documentInfo.get("total_questions");
                if (totalQuestions != null) {
                    result.append("\n총 문제 수: ").append(totalQuestions).append("\n");
                }
            }

            if (result.length() > 0) {
                return result.toString();
            }

        } catch (Exception e) {
            logger.warn("❌ [FALLBACK-L2] 메타데이터 대안 실패: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Fallback Level 3: 원시 데이터 추출
     */
    private static String attemptRawDataExtraction(Map<String, Object> cimResult) {
        try {
            logger.info("🔄 [FALLBACK-L3] 원시 데이터 추출 시작");

            StringBuilder result = new StringBuilder();
            result.append("=== 원시 데이터 추출 결과 ===\n\n");

            // 모든 키-값 쌍을 순회하며 텍스트 데이터 추출
            for (Map.Entry<String, Object> entry : cimResult.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (value != null) {
                    String extractedText = extractTextFromObject(value);
                    if (extractedText != null && !extractedText.trim().isEmpty()) {
                        result.append("[").append(key).append("] ");
                        result.append(extractedText.substring(0, Math.min(200, extractedText.length())));
                        if (extractedText.length() > 200) {
                            result.append("...");
                        }
                        result.append("\n\n");
                    }
                }
            }

            if (result.length() > 50) { // 최소한의 내용이 있는지 확인
                return result.toString();
            }

        } catch (Exception e) {
            logger.warn("❌ [FALLBACK-L3] 원시 데이터 추출 실패: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 객체에서 텍스트 추출 (재귀적)
     */
    private static String extractTextFromObject(Object obj) {
        if (obj == null) return null;

        if (obj instanceof String) {
            return (String) obj;
        } else if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;

            StringBuilder result = new StringBuilder();
            for (Object value : map.values()) {
                String text = extractTextFromObject(value);
                if (text != null && !text.trim().isEmpty()) {
                    result.append(text).append(" ");
                }
            }
            return result.length() > 0 ? result.toString().trim() : null;

        } else if (obj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) obj;

            StringBuilder result = new StringBuilder();
            for (Object item : list) {
                String text = extractTextFromObject(item);
                if (text != null && !text.trim().isEmpty()) {
                    result.append(text).append(" ");
                }
            }
            return result.length() > 0 ? result.toString().trim() : null;
        } else {
            return obj.toString();
        }
    }

    /**
     * 최종 비상 대안 텍스트 생성
     */
    private static String createEmergencyFallbackText(String reason) {
        StringBuilder emergency = new StringBuilder();
        emergency.append("=== SmartEye 분석 결과 ===\n\n");
        emergency.append(reason).append("\n\n");
        emergency.append("시스템 상태: 정상 작동\n");
        emergency.append("분석 시간: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        emergency.append("\n※ 다른 분석 모드를 시도하거나 이미지를 다시 업로드해보세요.");

        return emergency.toString();
    }

    /**
     * 텍스트 유효성 검증
     */
    private static boolean isValidText(String text) {
        return text != null && !text.trim().isEmpty() && text.trim().length() > 5;
    }

    /**
     * questions 데이터에서 텍스트 생성 (기존 createFallbackFormattedText 개선)
     */
    private static String createFallbackFromQuestions(Map<String, Object> cimResult) {
        StringBuilder formattedText = new StringBuilder();

        try {
            // questions 데이터에서 텍스트 추출 시도
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questions = (List<Map<String, Object>>) cimResult.get("questions");

            if (questions != null && !questions.isEmpty()) {
                formattedText.append("=== 문제 분석 결과 ===\n\n");

                for (Map<String, Object> question : questions) {
                    // 문제 번호
                    Object questionNumber = question.get("question_number");
                    if (questionNumber != null) {
                        formattedText.append(questionNumber).append(". ");
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> questionContent = (Map<String, Object>) question.get("question_content");

                    if (questionContent != null) {
                        // 문제 본문
                        String mainQuestion = (String) questionContent.get("main_question");
                        if (mainQuestion != null && !mainQuestion.trim().isEmpty()) {
                            formattedText.append(mainQuestion.trim()).append("\n\n");
                        }

                        // 선택지
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> choices = (List<Map<String, Object>>) questionContent.get("choices");
                        if (choices != null) {
                            for (Map<String, Object> choice : choices) {
                                Object choiceNumber = choice.get("choice_number");
                                String choiceText = (String) choice.get("choice_text");
                                if (choiceNumber != null && choiceText != null && !choiceText.trim().isEmpty()) {
                                    formattedText.append("    ").append(choiceNumber).append(". ")
                                                .append(choiceText.trim()).append("\n");
                                }
                            }
                            formattedText.append("\n");
                        }

                        // 이미지 설명
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> images = (List<Map<String, Object>>) questionContent.get("images");
                        if (images != null) {
                            for (Map<String, Object> image : images) {
                                String description = (String) image.get("description");
                                if (description != null && !description.trim().isEmpty()) {
                                    formattedText.append("\n[그림 설명] ").append(description.trim()).append("\n\n");
                                }
                            }
                        }

                        // 표 설명
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> tables = (List<Map<String, Object>>) questionContent.get("tables");
                        if (tables != null) {
                            for (Map<String, Object> table : tables) {
                                String description = (String) table.get("description");
                                if (description != null && !description.trim().isEmpty()) {
                                    formattedText.append("\n[표 설명] ").append(description.trim()).append("\n\n");
                                }
                            }
                        }

                        // 해설
                        @SuppressWarnings("unchecked")
                        List<String> explanations = (List<String>) questionContent.get("explanations");
                        if (explanations != null && !explanations.isEmpty()) {
                            formattedText.append("해설:\n");
                            for (String explanation : explanations) {
                                if (explanation != null && !explanation.trim().isEmpty()) {
                                    formattedText.append("    ").append(explanation.trim()).append("\n");
                                }
                            }
                            formattedText.append("\n");
                        }
                    }

                    formattedText.append("---\n\n");
                }
            }

            // document_info에서 추가 정보 추출
            @SuppressWarnings("unchecked")
            Map<String, Object> documentInfo = (Map<String, Object>) cimResult.get("document_info");
            if (documentInfo != null) {
                Object totalQuestions = documentInfo.get("total_questions");
                if (totalQuestions != null) {
                    formattedText.append("총 문제 수: ").append(totalQuestions).append("\n");
                }
            }

            // 생성된 텍스트가 없는 경우 기본 메시지
            if (formattedText.length() == 0) {
                return "분석된 텍스트 내용을 추출할 수 없습니다.";
            }

            return formattedText.toString();

        } catch (Exception e) {
            logger.error("대안 텍스트 생성 중 오류: {}", e.getMessage());
            return "분석 결과에서 텍스트를 추출하는 중 오류가 발생했습니다.";
        }
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

            // Questions 변환 (간소화 - UnifiedAnalysisEngine 구조에 맞게)
            List<Map<String, Object>> questions = new ArrayList<>();
            var questionList = structuredResult.getQuestions();
            if (questionList != null) {
                for (var question : questionList) {
                    Map<String, Object> questionMap = new HashMap<>();
                    questionMap.put("question_number", question.getQuestionNumber());
                    questionMap.put("question_text", question.getQuestionText());

                    // 요소별 정보 간소화
                    Map<String, Object> elements = new HashMap<>();
                    if (question.getElements() != null) {
                        question.getElements().forEach((type, elementList) -> {
                            elements.put(type, elementList.size());
                        });
                    }
                    questionMap.put("elements", elements);

                    questions.add(questionMap);
                }
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
     * questions 데이터를 elements 형태로 변환 (createFormattedText 호환성)
     */
    private static List<Map<String, Object>> convertQuestionsToElements(List<Map<String, Object>> questions) {
        List<Map<String, Object>> elements = new ArrayList<>();

        try {
            for (Map<String, Object> question : questions) {
                @SuppressWarnings("unchecked")
                Map<String, Object> questionContent = (Map<String, Object>) question.get("question_content");

                if (questionContent != null) {
                    // 문제 번호 요소 추가
                    Object questionNumber = question.get("question_number");
                    if (questionNumber != null) {
                        Map<String, Object> numberElement = new HashMap<>();
                        numberElement.put("class", "question_number");
                        numberElement.put("text", questionNumber.toString());
                        numberElement.put("bbox", Arrays.asList(0, 0, 100, 30)); // 기본 bbox
                        elements.add(numberElement);
                    }

                    // 본문 텍스트 요소 추가
                    String mainQuestion = (String) questionContent.get("main_question");
                    if (mainQuestion != null && !mainQuestion.trim().isEmpty()) {
                        Map<String, Object> textElement = new HashMap<>();
                        textElement.put("class", "question_text");
                        textElement.put("text", mainQuestion);
                        textElement.put("bbox", Arrays.asList(0, 30, 500, 100)); // 기본 bbox
                        elements.add(textElement);
                    }

                    // 선택지 요소들 추가
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) questionContent.get("choices");
                    if (choices != null) {
                        for (Map<String, Object> choice : choices) {
                            String choiceText = (String) choice.get("choice_text");
                            if (choiceText != null && !choiceText.trim().isEmpty()) {
                                Map<String, Object> choiceElement = new HashMap<>();
                                choiceElement.put("class", "question_type");
                                choiceElement.put("text", choiceText);
                                choiceElement.put("bbox", Arrays.asList(20, 100, 480, 130)); // 기본 bbox
                                elements.add(choiceElement);
                            }
                        }
                    }

                    // 이미지 요소들 추가
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> images = (List<Map<String, Object>>) questionContent.get("images");
                    if (images != null) {
                        for (Map<String, Object> image : images) {
                            Map<String, Object> imageElement = new HashMap<>();
                            imageElement.put("class", "figure");
                            imageElement.put("text", "[그림] " + image.get("description"));
                            imageElement.put("bbox", image.get("bbox") != null ? image.get("bbox") : Arrays.asList(0, 0, 400, 300));
                            elements.add(imageElement);
                        }
                    }

                    // 표 요소들 추가
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> tables = (List<Map<String, Object>>) questionContent.get("tables");
                    if (tables != null) {
                        for (Map<String, Object> table : tables) {
                            Map<String, Object> tableElement = new HashMap<>();
                            tableElement.put("class", "table");
                            tableElement.put("text", "[표] " + table.get("description"));
                            tableElement.put("bbox", table.get("bbox") != null ? table.get("bbox") : Arrays.asList(0, 0, 400, 200));
                            elements.add(tableElement);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("questions를 elements로 변환 중 오류: {}", e.getMessage());
            // 오류 발생 시 기본 요소라도 반환
            Map<String, Object> fallbackElement = new HashMap<>();
            fallbackElement.put("class", "plain_text");
            fallbackElement.put("text", "텍스트 변환 중 일부 내용을 처리할 수 없습니다.");
            fallbackElement.put("bbox", Arrays.asList(0, 0, 500, 50));
            elements.add(fallbackElement);
        }

        return elements;
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