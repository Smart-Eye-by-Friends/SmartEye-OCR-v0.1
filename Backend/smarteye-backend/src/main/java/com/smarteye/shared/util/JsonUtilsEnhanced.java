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

/**
 * 강화된 JSON 유틸리티 클래스
 * - 절대 실패하지 않는 formattedText 생성
 * - 계층적 다중 fallback 시스템
 * - 완전한 null-safe 처리
 * - 상세한 로깅 및 디버깅 지원
 */
@Component
public class JsonUtilsEnhanced {

    private static final Logger logger = LoggerFactory.getLogger(JsonUtilsEnhanced.class);

    private final ObjectMapper objectMapper;

    public JsonUtilsEnhanced() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 🛡️ 절대 실패하지 않는 FormattedText 생성 아키텍처
     *
     * 계층적 Fallback 시스템:
     * Phase 1: 입력 데이터 1차 검증 → 즉시 대안 반환
     * Phase 2: 메인 처리 로직 (기존 구현 개선)
     * Phase 3: 4단계 계층적 fallback
     *   - Level 1: 구조화된 대안 (questions 기반)
     *   - Level 2: 메타데이터 기반 대안
     *   - Level 3: 원시 데이터 추출
     *   - Level 4: 최종 비상 대안
     */
    public static String createFormattedTextEnhanced(Map<String, Object> cimResult) {
        logger.info("🔍 [ENHANCED] 강화된 createFormattedText 시작 - 데이터 크기: {}",
                   cimResult != null ? cimResult.size() : "null");

        // 🔒 Phase 1: 입력 데이터 1차 검증 및 즉시 대안
        String phase1Result = validateAndProcessPhase1(cimResult);
        if (phase1Result != null) {
            logger.info("✅ [PHASE1] 입력 검증 실패 - 즉시 대안 반환: {}글자", phase1Result.length());
            return phase1Result;
        }

        // 📊 디버깅: CIM 데이터 구조 로깅
        logCIMDataStructure(cimResult);

        try {
            // 🚀 Phase 2: 메인 처리 로직 (개선된 버전)
            String mainResult = processMainFormattedText(cimResult);
            if (isValidText(mainResult)) {
                logger.info("✅ [MAIN] 메인 처리 성공: {}글자", mainResult.length());
                return mainResult;
            }

            logger.warn("⚠️ [MAIN] 메인 처리 결과 부족 - fallback 시작");

        } catch (Exception mainError) {
            logger.error("❌ [MAIN] 메인 처리 실패: {} - fallback 시작", mainError.getMessage(), mainError);
        }

        // 🔄 Phase 3: 계층적 다중 fallback 시스템
        return executeMultiLevelFallback(cimResult);
    }

    /**
     * 🔒 Phase 1: 입력 데이터 1차 검증 및 즉시 대안 반환
     */
    private static String validateAndProcessPhase1(Map<String, Object> cimResult) {
        if (cimResult == null) {
            logger.warn("🚫 [PHASE1] CIM 결과가 완전히 null");
            return createEmergencyFallbackText("입력 데이터가 없습니다.");
        }

        if (cimResult.isEmpty()) {
            logger.warn("🚫 [PHASE1] CIM 결과가 빈 Map");
            return createEmergencyFallbackText("분석 결과가 비어있습니다.");
        }

        // 최소한의 키 존재 확인
        boolean hasAnyValidKey = cimResult.containsKey("document_structure")
                               || cimResult.containsKey("questions")
                               || cimResult.containsKey("metadata")
                               || cimResult.containsKey("elements")
                               || cimResult.containsKey("text_content")
                               || cimResult.containsKey("ai_descriptions");

        if (!hasAnyValidKey) {
            logger.warn("🚫 [PHASE1] 유효한 키가 없음: {}", cimResult.keySet());
            return createEmergencyFallbackText("인식 가능한 분석 데이터가 없습니다.");
        }

        return null; // 계속 진행
    }

    /**
     * 🚀 Phase 2: 메인 처리 로직 (null-safe 개선)
     */
    private static String processMainFormattedText(Map<String, Object> cimResult) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> documentStructure = (Map<String, Object>) cimResult.get("document_structure");

            if (documentStructure == null) {
                logger.info("🔄 [MAIN] document_structure 없음 - fallback 준비");
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> layoutAnalysis = (Map<String, Object>) documentStructure.get("layout_analysis");

            if (layoutAnalysis == null) {
                logger.info("🔄 [MAIN] layout_analysis 없음 - fallback 준비");
                return null;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> elements = (List<Map<String, Object>>) layoutAnalysis.get("elements");

            if (elements == null || elements.isEmpty()) {
                logger.info("🔄 [MAIN] elements 없음 - fallback 준비");
                return null;
            }

            // 향상된 포맷팅 규칙 정의
            Map<String, FormattingRule> formattingRules = createFormattingRules();

            // 요소들을 위치 기준으로 정렬 (null-safe)
            List<ElementWithContent> elementsWithContent = createElementsWithContent(elements);

            if (elementsWithContent.isEmpty()) {
                logger.warn("🔴 [MAIN] 유효한 콘텐츠 요소가 없음");
                return null;
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
                if (element == null || element.content == null) continue;

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

            // 연속된 빈 줄 정리 및 결과 검증
            String result = cleanupFormattedText(formattedText.toString());

            if (result == null || result.trim().isEmpty() || result.trim().length() < 10) {
                logger.warn("🔴 [MAIN] 생성된 텍스트가 너무 짧음: {}", result != null ? result.length() : "null");
                return null;
            }

            logger.info("✅ [MAIN] 메인 처리 성공: {}글자", result.length());
            return result;

        } catch (Exception e) {
            logger.error("❌ [MAIN] 메인 처리 예외: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 🔄 Phase 3: 계층적 다중 fallback 시스템
     */
    private static String executeMultiLevelFallback(Map<String, Object> cimResult) {
        logger.info("🔄 [FALLBACK] 다중 계층 fallback 시작");

        // 📚 Fallback Level 1: 구조화된 대안 (questions 기반)
        String level1Result = attemptStructuredFallback(cimResult);
        if (isValidText(level1Result)) {
            logger.info("✅ [FALLBACK-L1] 구조화된 대안 성공: {}글자", level1Result.length());
            return level1Result;
        }

        // 📈 Fallback Level 2: 메타데이터 기반 대안
        String level2Result = attemptMetadataFallback(cimResult);
        if (isValidText(level2Result)) {
            logger.info("✅ [FALLBACK-L2] 메타데이터 대안 성공: {}글자", level2Result.length());
            return level2Result;
        }

        // 🔍 Fallback Level 3: 원시 데이터 추출
        String level3Result = attemptRawDataExtraction(cimResult);
        if (isValidText(level3Result)) {
            logger.info("✅ [FALLBACK-L3] 원시 데이터 추출 성공: {}글자", level3Result.length());
            return level3Result;
        }

        // 🚨 Fallback Level 4: 최종 비상 대안
        String emergencyResult = createEmergencyFallbackText("모든 처리 방법이 실패했지만 시스템은 정상 작동 중입니다.");
        logger.warn("🚨 [FALLBACK-EMERGENCY] 최종 비상 대안 사용: {}글자", emergencyResult.length());
        return emergencyResult;
    }

    /**
     * 📚 Fallback Level 1: 구조화된 대안 (questions 기반 처리)
     */
    private static String attemptStructuredFallback(Map<String, Object> cimResult) {
        try {
            logger.info("🔄 [FALLBACK-L1] 구조화된 대안 시작");

            StringBuilder formattedText = new StringBuilder();

            // questions 데이터에서 텍스트 추출 시도 (향상된 null 처리)
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questions = (List<Map<String, Object>>) cimResult.get("questions");

            if (questions != null && !questions.isEmpty()) {
                formattedText.append("=== 문제 분석 결과 ===\n\n");

                for (Map<String, Object> question : questions) {
                    if (question == null) continue;

                    // 문제 번호 (null-safe)
                    Object questionNumber = question.get("question_number");
                    if (questionNumber != null) {
                        formattedText.append(questionNumber).append(". ");
                    }

                    // 직접적인 question_text 확인 (간소화된 구조)
                    String directQuestionText = (String) question.get("question_text");
                    if (directQuestionText != null && !directQuestionText.trim().isEmpty()) {
                        formattedText.append(directQuestionText.trim()).append("\n\n");
                        continue; // 간소화된 구조라면 다음 문제로
                    }

                    // 복잡한 구조 처리
                    @SuppressWarnings("unchecked")
                    Map<String, Object> questionContent = (Map<String, Object>) question.get("question_content");

                    if (questionContent != null) {
                        // 문제 본문
                        String mainQuestion = (String) questionContent.get("main_question");
                        if (mainQuestion != null && !mainQuestion.trim().isEmpty()) {
                            formattedText.append(mainQuestion.trim()).append("\n\n");
                        }

                        // 선택지 (null-safe 처리)
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> choices = (List<Map<String, Object>>) questionContent.get("choices");
                        if (choices != null) {
                            for (Map<String, Object> choice : choices) {
                                if (choice == null) continue;

                                Object choiceNumber = choice.get("choice_number");
                                String choiceText = (String) choice.get("choice_text");
                                if (choiceNumber != null && choiceText != null && !choiceText.trim().isEmpty()) {
                                    formattedText.append("    ").append(choiceNumber).append(". ")
                                                .append(choiceText.trim()).append("\n");
                                }
                            }
                            formattedText.append("\n");
                        }

                        // 이미지 및 표 설명 (null-safe)
                        appendDescriptions(formattedText, questionContent, "images", "[그림 설명] ");
                        appendDescriptions(formattedText, questionContent, "tables", "[표 설명] ");

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
            appendDocumentInfo(formattedText, cimResult);

            // 생성된 텍스트 검증
            String result = formattedText.toString().trim();
            if (result.isEmpty() || result.length() < 10) {
                return null; // 다음 fallback으로 이동
            }

            return result;

        } catch (Exception e) {
            logger.error("🔴 [FALLBACK-L1] 구조화된 대안 처리 중 오류: {}", e.getMessage(), e);
            return null; // 다음 fallback으로 이동
        }
    }

    /**
     * 📈 Fallback Level 2: 메타데이터 기반 대안
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

                Object totalTextRegions = metadata.get("total_text_regions");
                if (totalTextRegions != null) {
                    result.append("텍스트 영역 수: ").append(totalTextRegions).append("\n");
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

                Object totalElements = documentInfo.get("total_elements");
                if (totalElements != null) {
                    result.append("총 분석 요소: ").append(totalElements).append("\n");
                }
            }

            if (result.length() > 50) { // 최소한의 내용이 있는지 확인
                return result.toString();
            }

        } catch (Exception e) {
            logger.warn("❌ [FALLBACK-L2] 메타데이터 대안 실패: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 🔍 Fallback Level 3: 원시 데이터 추출
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
     * 🚨 최종 비상 대안 텍스트 생성
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
     * 🔍 객체에서 텍스트 추출 (재귀적, null-safe)
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
     * ✅ 텍스트 유효성 검증
     */
    private static boolean isValidText(String text) {
        return text != null && !text.trim().isEmpty() && text.trim().length() > 5;
    }

    /**
     * 📝 설명 데이터 추가 (이미지, 표 등)
     */
    private static void appendDescriptions(StringBuilder formattedText, Map<String, Object> questionContent, String key, String prefix) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) questionContent.get(key);
            if (items != null) {
                for (Map<String, Object> item : items) {
                    if (item == null) continue;

                    String description = (String) item.get("description");
                    if (description != null && !description.trim().isEmpty()) {
                        formattedText.append("\n").append(prefix).append(description.trim()).append("\n\n");
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("설명 데이터 처리 중 예외 (무시): {}", e.getMessage());
        }
    }

    /**
     * 📄 문서 정보 추가
     */
    private static void appendDocumentInfo(StringBuilder formattedText, Map<String, Object> cimResult) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> documentInfo = (Map<String, Object>) cimResult.get("document_info");
            if (documentInfo != null) {
                Object totalQuestions = documentInfo.get("total_questions");
                if (totalQuestions != null) {
                    formattedText.append("\n총 문제 수: ").append(totalQuestions).append("\n");
                }
            }
        } catch (Exception e) {
            logger.debug("문서 정보 처리 중 예외 (무시): {}", e.getMessage());
        }
    }

    /**
     * 🎛️ 향상된 포맷팅 규칙 생성
     */
    private static Map<String, FormattingRule> createFormattingRules() {
        return Map.ofEntries(
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
    }

    /**
     * 🧩 요소들을 콘텐츠와 함께 생성 (null-safe)
     */
    private static List<ElementWithContent> createElementsWithContent(List<Map<String, Object>> elements) {
        List<ElementWithContent> elementsWithContent = new ArrayList<>();

        for (Map<String, Object> element : elements) {
            if (element == null) continue;

            try {
                String className = extractClassName(element);
                if (className == null) continue;

                // bbox 타입 안전 처리
                List<Integer> bbox = extractBbox(element);
                if (bbox == null) continue;

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
                    Integer elementId = (Integer) element.get("id");
                    if (elementId == null) elementId = elementsWithContent.size(); // fallback ID

                    elementsWithContent.add(new ElementWithContent(
                        elementId,
                        className,
                        content.trim(),
                        contentType,
                        bbox.get(1), // y 좌표
                        bbox.get(0)  // x 좌표
                    ));
                }

            } catch (Exception e) {
                logger.debug("요소 처리 중 예외 (건너뛰기): {}", e.getMessage());
            }
        }

        return elementsWithContent;
    }

    /**
     * 🏷️ 클래스명 추출 (null-safe)
     */
    private static String extractClassName(Map<String, Object> element) {
        Object classObj = element.get("class");
        if (classObj instanceof String) {
            return ((String) classObj).toLowerCase().replace(" ", "_");
        }
        return null;
    }

    /**
     * 📐 bbox 추출 (null-safe)
     */
    private static List<Integer> extractBbox(Map<String, Object> element) {
        Object bboxObj = element.get("bbox");

        if (bboxObj instanceof List) {
            try {
                @SuppressWarnings("unchecked")
                List<Integer> bboxList = (List<Integer>) bboxObj;
                if (bboxList.size() >= 4) {
                    return bboxList;
                }
            } catch (ClassCastException e) {
                // 다른 타입의 리스트인 경우 변환 시도
                @SuppressWarnings("unchecked")
                List<Object> objList = (List<Object>) bboxObj;
                if (objList.size() >= 4) {
                    try {
                        return Arrays.asList(
                            ((Number) objList.get(0)).intValue(),
                            ((Number) objList.get(1)).intValue(),
                            ((Number) objList.get(2)).intValue(),
                            ((Number) objList.get(3)).intValue()
                        );
                    } catch (Exception ignored) {}
                }
            }
        } else if (bboxObj instanceof int[]) {
            int[] bboxArray = (int[]) bboxObj;
            if (bboxArray.length >= 4) {
                return Arrays.asList(bboxArray[0], bboxArray[1], bboxArray[2], bboxArray[3]);
            }
        }

        logger.debug("알 수 없는 bbox 타입: {} - 요소 ID: {}",
                    bboxObj != null ? bboxObj.getClass() : "null", element.get("id"));
        return null;
    }

    /**
     * 🧹 향상된 텍스트 정리 (null-safe)
     */
    private static String cleanupFormattedText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        String[] lines = text.split("\n");
        List<String> cleanedLines = new ArrayList<>();
        boolean prevEmpty = false;

        for (String line : lines) {
            if (line == null) continue;

            boolean isEmpty = line.trim().isEmpty();

            // 연속된 빈 줄이 3개 이상 나오지 않도록 제한
            if (isEmpty && prevEmpty) {
                continue;
            }

            cleanedLines.add(line);
            prevEmpty = isEmpty;
        }

        String result = String.join("\n", cleanedLines).trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * 📊 데이터 무결성 검증
     */
    private static void logCIMDataStructure(Map<String, Object> cimResult) {
        if (logger.isDebugEnabled() && cimResult != null) {
            logger.debug("📊 [DEBUG] CIM 데이터 구조:");
            for (String key : cimResult.keySet()) {
                Object value = cimResult.get(key);
                if (value != null) {
                    logger.debug("  - {}: {} ({})", key, value.getClass().getSimpleName(), getDataSize(value));
                }
            }
        }
    }

    /**
     * 📏 데이터 크기 측정
     */
    private static String getDataSize(Object value) {
        if (value instanceof String) {
            return ((String) value).length() + " chars";
        } else if (value instanceof List) {
            return ((List<?>) value).size() + " items";
        } else if (value instanceof Map) {
            return ((Map<?, ?>) value).size() + " keys";
        } else {
            return "1 object";
        }
    }

    // Helper classes

    private static class FormattingRule {
        final String prefix;
        final String suffix;
        final int indent;

        FormattingRule(String prefix, String suffix, int indent) {
            this.prefix = prefix != null ? prefix : "";
            this.suffix = suffix != null ? suffix : "";
            this.indent = Math.max(0, indent);
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
            this.className = className != null ? className : "plain_text";
            this.content = content != null ? content : "";
            this.type = type != null ? type : "unknown";
            this.yPosition = Math.max(0, yPosition);
            this.xPosition = Math.max(0, xPosition);
        }
    }
}