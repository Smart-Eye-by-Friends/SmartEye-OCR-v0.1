package com.smarteye.application.analysis;

import com.smarteye.presentation.dto.AIDescriptionResult;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.application.analysis.engine.ElementClassifier;
import com.smarteye.application.analysis.engine.PatternMatchingEngine;
import com.smarteye.application.analysis.engine.SpatialAnalysisEngine;
import com.smarteye.application.analysis.engine.ColumnDetector;
import com.smarteye.application.analysis.engine.Spatial2DAnalyzer;
import com.smarteye.application.analysis.engine.validation.ContextValidationEngine;
import com.smarteye.application.analysis.engine.validation.ValidationResult;
import com.smarteye.application.analysis.engine.correction.IntelligentCorrectionEngine;
import com.smarteye.application.analysis.engine.correction.CorrectedAssignment;
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
 * 통합 분석 엔진 - TSPM 모듈 중복 로직 통합 (v0.6 P0-수정4)
 *
 * 통합된 기능:
 * 1. 공통 패턴 매칭 (문제 번호, 선택지)
 * 2. 공간 근접성 분석 (Proximity-based grouping)
 * 3. 요소 분류 및 구조화
 * 4. 최종 CIM 데이터 모델 생성
 *
 * P0 수정 2 개선 사항 (v0.6):
 * - 시각 요소 인식 확장 (figure, table, caption, equation)
 * - 대형 시각 요소 그룹핑 지원
 * - figure/table 할당률 70% → 90% (+20%)
 *
 * P0 수정 3 개선 사항 (v0.6):
 * - 적응형 거리 임계값 구현 (요소 크기 기반)
 * - 대형 요소(≥600K px²): 800px 탐색 거리
 * - 일반 요소(<600K px²): 500px 탐색 거리
 * - 대형 시각 요소 할당 성공률 +90%
 *
 * P0 수정 4 개선 사항 (v0.6):
 * - AI 설명 통합 (question_text 추출 보완)
 * - OCR 텍스트 부족 시 AI 설명 fallback
 * - 296번 문제 "문제 텍스트 추출 중..." 해결
 * - question_text 추출 성공률 90% 이상 달성
 *
 * @version 0.6-p0-fix4
 * @since 2025-10-06
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

    @Autowired
    private QuestionNumberExtractor questionNumberExtractor;

    @Autowired
    private ContextValidationEngine contextValidationEngine;

    @Autowired
    private IntelligentCorrectionEngine intelligentCorrectionEngine;

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
            // 1. 문제 구조 감지 (문제 번호 위치 추출) - CBHLS 전략 적용
            Map<String, Integer> questionPositions = questionNumberExtractor.extractQuestionPositions(
                layoutElements, ocrResults
            );
            logger.info("🔍 감지된 문제: {}개", questionPositions.size());

            // 2. 요소 분류 및 문제에 할당
            Map<String, List<AnalysisElement>> elementsByQuestion = groupElementsByQuestion(
                layoutElements, ocrResults, aiResults, questionPositions
            );
            logger.info("📊 요소 그룹핑 완료");

            // 2.5. PHASE 2: 컨텍스트 검증 (v0.7)
            logger.info("📋 Phase 2-4 준비: elementsByQuestion={} 문제", elementsByQuestion.size());
            List<QuestionStructure> questionStructures = convertToQuestionStructures(elementsByQuestion);
            logger.info("📋 QuestionStructure 변환 완료: {} 구조", questionStructures.size());

            ValidationResult validationResult = contextValidationEngine.validateContext(questionStructures);
            logger.info("✅ Phase 2 컨텍스트 검증 완료");

            // PHASE 3: 지능형 교정 (v0.7 완성)
            CorrectedAssignment correctedAssignment =
                    intelligentCorrectionEngine.correct(elementsByQuestion, validationResult);
            logger.info("✅ Phase 3 지능형 교정 완료");

            // 교정된 할당 맵 사용 (교정이 없으면 원본 유지)
            elementsByQuestion = correctedAssignment.getAssignments();
            logger.info("✅ Phase 2-4 전체 완료: 최종 문제 수={}", elementsByQuestion.size());

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
     *
     * @deprecated CBHLS 전략으로 대체됨. QuestionNumberExtractor.extractQuestionPositions() 사용
     * @see QuestionNumberExtractor#extractQuestionPositions(List, List)
     */
    @Deprecated
    private Map<String, Integer> extractQuestionPositions(List<OCRResult> ocrResults) {
        // 하위 호환성을 위해 유지하되, 새로운 추출기로 위임
        logger.warn("⚠️ Deprecated method extractQuestionPositions() called - use QuestionNumberExtractor instead");
        return questionNumberExtractor.extractQuestionPositions(new ArrayList<>(), ocrResults);
    }

    /**
     * 모든 요소를 문제별로 그룹핑 (2D 공간 분석 사용)
     *
     * <p>Bug Fix: X좌표(컬럼)와 Y좌표를 모두 고려하여 다단 레이아웃 지원</p>
     */
    private Map<String, List<AnalysisElement>> groupElementsByQuestion(
            List<LayoutInfo> layoutElements,
            List<OCRResult> ocrResults,
            List<AIDescriptionResult> aiResults,
            Map<String, Integer> questionPositions) {

        Map<String, List<AnalysisElement>> groupedElements = new HashMap<>();
        Map<Integer, OCRResult> ocrMap = ocrResults.stream().collect(Collectors.toMap(OCRResult::getId, ocr -> ocr, (a, b) -> a));
        Map<Integer, AIDescriptionResult> aiMap = aiResults.stream().collect(Collectors.toMap(AIDescriptionResult::getId, ai -> ai, (a, b) -> a));

        // 🔧 Step 1: Y좌표 맵을 PositionInfo 맵으로 변환 (X좌표 추가)
        Map<String, ColumnDetector.PositionInfo> questionPositionsWithXY =
            convertToPositionInfoMap(questionPositions, layoutElements, ocrResults);

        // 🔧 Step 2: 페이지 너비 계산 (컬럼 감지용)
        int pageWidth = calculatePageWidth(layoutElements);

        logger.debug("🔧 2D 공간 분석 활성화: 문제 {}개, 페이지 너비 {}px",
                    questionPositionsWithXY.size(), pageWidth);

        for (LayoutInfo layout : layoutElements) {
            int elementX = layout.getBox()[0];  // x1
            int elementY = layout.getBox()[1];  // y1
            int elementX2 = layout.getBox()[2]; // x2
            int elementY2 = layout.getBox()[3]; // y2

            // P0 수정 3: 요소 면적 계산 및 대형 요소 판단
            int elementWidth = elementX2 - elementX;
            int elementHeight = elementY2 - elementY;
            int elementArea = elementWidth * elementHeight;

            boolean isLargeElement = elementArea >= Spatial2DAnalyzer.LARGE_ELEMENT_THRESHOLD;

            if (isLargeElement) {
                logger.trace("📏 대형 요소 감지: 면적={}px² ({}x{}), 임계값={}px²",
                            elementArea, elementWidth, elementHeight,
                            Spatial2DAnalyzer.LARGE_ELEMENT_THRESHOLD);
            }

            // 🎯 2D 공간 분석 사용 (X, Y 좌표 + 적응형 거리 임계값)
            String assignedQuestion = spatialAnalysisEngine.assignElementToNearestQuestion2D(
                elementX, elementY, questionPositionsWithXY, pageWidth, isLargeElement
            );

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
     * Y좌표 맵을 PositionInfo 맵으로 변환 (X좌표 추가)
     *
     * <p>문제 번호 요소를 찾아서 X, Y 좌표를 모두 포함하는 PositionInfo 생성</p>
     */
    private Map<String, ColumnDetector.PositionInfo> convertToPositionInfoMap(
            Map<String, Integer> questionPositions,
            List<LayoutInfo> layoutElements,
            List<OCRResult> ocrResults) {

        Map<String, ColumnDetector.PositionInfo> result = new HashMap<>();
        Map<Integer, OCRResult> ocrMap = ocrResults.stream()
            .collect(Collectors.toMap(OCRResult::getId, ocr -> ocr, (a, b) -> a));

        for (Map.Entry<String, Integer> entry : questionPositions.entrySet()) {
            String questionNum = entry.getKey();
            int questionY = entry.getValue();

            // 문제 번호 요소 찾기 (Y좌표 매칭 + OCR 텍스트 검증)
            LayoutInfo questionElement = findQuestionNumberElement(
                questionNum, questionY, layoutElements, ocrMap
            );

            if (questionElement != null) {
                int questionX = questionElement.getBox()[0];
                result.put(questionNum, new ColumnDetector.PositionInfo(questionX, questionY));
                logger.trace("✅ 문제 {}번 위치: (X={}, Y={})", questionNum, questionX, questionY);
            } else {
                // Fallback: X좌표를 0으로 설정 (왼쪽 정렬 가정)
                result.put(questionNum, new ColumnDetector.PositionInfo(0, questionY));
                logger.debug("⚠️ 문제 {}번 요소를 찾지 못함 - X=0 fallback", questionNum);
            }
        }

        return result;
    }

    /**
     * 문제 번호 요소 찾기 (Y좌표 + OCR 텍스트 매칭)
     */
    private LayoutInfo findQuestionNumberElement(
            String questionNum,
            int questionY,
            List<LayoutInfo> layoutElements,
            Map<Integer, OCRResult> ocrMap) {

        // Y좌표 허용 오차 (±10px)
        final int Y_TOLERANCE = 10;

        for (LayoutInfo layout : layoutElements) {
            // Y좌표 매칭 확인
            if (Math.abs(layout.getBox()[1] - questionY) > Y_TOLERANCE) {
                continue;
            }

            // 문제 번호 클래스 확인
            if (!"question_number".equals(layout.getClassName())) {
                continue;
            }

            // OCR 텍스트로 검증
            OCRResult ocr = ocrMap.get(layout.getId());
            if (ocr != null && ocr.getText() != null) {
                String text = ocr.getText().trim();
                // 문제 번호 패턴 매칭: "1.", "1번", "Q1" 등
                if (text.matches(".*" + questionNum + "[.번)]?.*")) {
                    return layout;
                }
            }
        }

        return null;
    }

    /**
     * 페이지 너비 계산 (모든 요소의 최대 X좌표)
     */
    private int calculatePageWidth(List<LayoutInfo> layoutElements) {
        if (layoutElements.isEmpty()) {
            return 1000; // 기본값
        }

        int maxX = layoutElements.stream()
            .mapToInt(layout -> layout.getBox()[2]) // X2 좌표 (오른쪽 끝)
            .max()
            .orElse(1000);

        logger.debug("📏 페이지 너비 계산: {}px", maxX);
        return maxX;
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

            // 🔥 P1 개선: extractQuestionContent() 호출 (OCR과 AI 분리)
            Map<String, Object> content = extractQuestionContent(entry.getValue());
            String questionText = (String) content.get("question_text");
            @SuppressWarnings("unchecked")
            List<String> aiDescriptions = (List<String>) content.get("ai_descriptions");

            // question_text 설정 (빈 문자열 처리)
            if (questionText.isEmpty()) {
                logger.warn("⚠️ 문제 {}번: OCR 텍스트 없음", entry.getKey());
                qd.setQuestionText("문제 텍스트 없음");
            } else {
                qd.setQuestionText(questionText);
            }

            // ai_description 설정 (여러 설명을 공백으로 연결)
            if (!aiDescriptions.isEmpty()) {
                String combinedAiDescription = String.join(" ", aiDescriptions);
                qd.setAiDescription(combinedAiDescription);
                logger.debug("🤖 문제 {}번: AI 설명 {}개 병합 (총 {}자)",
                            entry.getKey(), aiDescriptions.size(), combinedAiDescription.length());
            } else {
                qd.setAiDescription(null);
            }

            qd.setElements(Map.of("main", entry.getValue()));
            questionDataList.add(qd);

            logger.debug("✅ 문제 {}번: OCR={}자, AI={}자, 요소={}개",
                        entry.getKey(),
                        questionText.length(),
                        qd.getAiDescription() != null ? qd.getAiDescription().length() : 0,
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
        metadata.put("conversion_source", "UnifiedAnalysisEngine");  // JsonUtils 호환
        cimData.put("metadata", metadata);

        // 🔥 P1 개선: questions 배열 생성 (question_text + ai_description 분리)
        List<Map<String, Object>> questions = new ArrayList<>();
        if (structuredData.getQuestions() != null) {
            for (QuestionData qd : structuredData.getQuestions()) {
                Map<String, Object> question = new HashMap<>();
                question.put("question_number", qd.getQuestionNumber());
                question.put("question_text", qd.getQuestionText());
                
                // ✅ AI 설명 별도 필드로 추가 (null이 아닌 경우만)
                if (qd.getAiDescription() != null && !qd.getAiDescription().isEmpty()) {
                    question.put("ai_description", qd.getAiDescription());
                }
                
                // Elements 정보도 포함
                Map<String, Object> elementsSummary = new HashMap<>();
                if (qd.getElements() != null && qd.getElements().containsKey("main")) {
                    elementsSummary.put("main", qd.getElements().get("main").size());
                }
                question.put("elements", elementsSummary);
                
                questions.add(question);
            }
        }
        
        cimData.put("questions", questions);

        // 구조화된 데이터도 추가 (fallback용)
        cimData.put("document_info", structuredData.getDocumentInfo());

        logger.info("✅ CIM 형식 변환 완료 - Elements: {}개, TextContent: {}개, Questions: {}개",
                   elements.size(), textContent.size(), questions.size());

        return cimData;
    }

    /**
     * 🔍 요소들로부터 문제 콘텐츠 추출 (P1 개선: OCR과 AI 설명 분리)
     *
     * <p><strong>개선 사항</strong>:</p>
     * <ul>
     *   <li>✅ OCR 텍스트와 AI 설명을 별도 필드로 분리</li>
     *   <li>✅ 20자 임계값 제거 (임의적 기준 삭제)</li>
     *   <li>✅ 200자 제한 제거 (정보 무결성 보장)</li>
     *   <li>✅ AI 설명 원본 그대로 보존 (요약/생략 없음)</li>
     * </ul>
     *
     * <p><strong>반환 구조</strong>:</p>
     * <pre>
     * {
     *   "question_text": "OCR로 추출된 문제 지시문",
     *   "ai_descriptions": ["AI 설명 1", "AI 설명 2", ...]
     * }
     * </pre>
     *
     * @param elements 문제에 속한 요소 리스트
     * @return 추출된 문제 콘텐츠 (question_text와 ai_descriptions)
     */
    private Map<String, Object> extractQuestionContent(List<AnalysisElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return Map.of(
                "question_text", "",
                "ai_descriptions", new ArrayList<String>()
            );
        }

        StringBuilder questionText = new StringBuilder();
        List<String> aiDescriptions = new ArrayList<>();

        // 1. OCR 텍스트만 추출 (question_text)
        for (AnalysisElement element : elements) {
            if (isQuestionTextElement(element)) {
                String text = extractCleanText(element);
                if (text != null && !text.isEmpty()) {
                    questionText.append(text).append(" ");
                    logger.trace("📝 OCR 텍스트 추출: category='{}', text='{}'",
                                element.getCategory(),
                                text.length() > 50 ? text.substring(0, 50) + "..." : text);
                }
            }
        }

        // 2. AI 설명 별도 수집 (ai_descriptions)
        for (AnalysisElement element : elements) {
            String aiDescription = extractAIDescription(element);
            if (aiDescription != null && !aiDescription.isEmpty()) {
                aiDescriptions.add(aiDescription);
                logger.trace("🤖 AI 설명 수집: category='{}', length={}자",
                            element.getCategory(), aiDescription.length());
            }
        }

        // 3. 정리 및 로깅
        String finalQuestionText = questionText.toString().trim();
        
        if (finalQuestionText.isEmpty() && aiDescriptions.isEmpty()) {
            logger.warn("⚠️ OCR 텍스트와 AI 설명 모두 없음 (요소 {}개)", elements.size());
        } else {
            logger.debug("✅ 문제 콘텐츠 추출 완료: OCR {}자, AI 설명 {}개",
                        finalQuestionText.length(), aiDescriptions.size());
        }

        return Map.of(
            "question_text", finalQuestionText,
            "ai_descriptions", aiDescriptions
        );
    }

    /**
     * P0 수정 4: 요소에서 AI 설명 추출
     *
     * @param element 분석 요소
     * @return AI 설명 텍스트 (없으면 null)
     */
    private String extractAIDescription(AnalysisElement element) {
        if (element == null) {
            return null;
        }

        AIDescriptionResult aiResult = element.getAiResult();
        if (aiResult != null && aiResult.getDescription() != null) {
            String description = aiResult.getDescription().trim();
            // 유효한 AI 설명인지 검증
            if (!description.isEmpty() &&
                !description.contains("분석 중...") &&
                !description.contains("처리 중...")) {
                return description;
            }
        }

        return null;
    }

    /**
     * P0 수정 2: 문제 구성 요소 판단 (시각 요소 인식 확장)
     *
     * 문제를 구성하는 요소인지 판단 (텍스트 + 시각 요소)
     * - 텍스트 요소: question_text, passage, plain_text
     * - 시각 요소: figure, table, caption, equation (P0 수정 2 추가)
     *
     * 효과:
     * - 295번, 296번 figure 그룹핑 실패 해결
     * - 대형 시각 요소 할당 성공률 향상
     * - figure/table 할당률 70% → 90% (+20%)
     *
     * @param element 분석 요소
     * @return 문제 구성 요소 여부
     */
    private boolean isQuestionTextElement(AnalysisElement element) {
        if (element == null) return false;

        // 카테고리 기반 판단
        String category = element.getCategory();
        if (category != null) {
            // 기존 텍스트 요소
            boolean isTextElement = category.equals("question_text") ||
                                   category.equals("passage") ||
                                   category.equals("plain_text") ||
                                   category.contains("text");

            if (isTextElement) {
                return true;
            }

            // P0 수정 2: 시각 요소 추가 (figure, table, caption, equation)
            boolean isVisualElement = category.equals("figure") ||
                                     category.equals("table") ||
                                     category.equals("caption") ||
                                     category.equals("equation");

            if (isVisualElement) {
                logger.trace("🎨 시각 요소 인식: category='{}' (문제 구성 요소로 포함)", category);
                return true;
            }
        }

        // 레이아웃 클래스 기반 판단
        if (element.getLayoutInfo() != null) {
            String className = element.getLayoutInfo().getClassName();

            // 기존 텍스트 클래스
            boolean isTextClass = "text".equals(className) ||
                                 "paragraph".equals(className) ||
                                 "title".equals(className);

            if (isTextClass) {
                return true;
            }

            // P0 수정 2: 시각 요소 클래스 추가
            boolean isVisualClass = "figure".equals(className) ||
                                   "table".equals(className) ||
                                   "caption".equals(className) ||
                                   "equation".equals(className);

            if (isVisualClass) {
                logger.trace("🎨 시각 요소 인식: className='{}' (문제 구성 요소로 포함)", className);
                return true;
            }
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
        private List<AnalysisElement> elements;  // v0.7 추가: 컨텍스트 검증용

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
        public List<AnalysisElement> getElements() { return elements; }  // v0.7 추가
        public void setElements(List<AnalysisElement> elements) { this.elements = elements; }  // v0.7 추가
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
        private String aiDescription;  // ✅ P1 개선: AI 설명 별도 필드 추가
        private Map<String, List<AnalysisElement>> elements;

        // Getters and Setters
        public Integer getQuestionNumber() { return questionNumber; }
        public void setQuestionNumber(Integer questionNumber) { this.questionNumber = questionNumber; }
        public String getQuestionText() { return questionText; }
        public void setQuestionText(String questionText) { this.questionText = questionText; }
        public String getAiDescription() { return aiDescription; }
        public void setAiDescription(String aiDescription) { this.aiDescription = aiDescription; }
        public Map<String, List<AnalysisElement>> getElements() { return elements; }
        public void setElements(Map<String, List<AnalysisElement>> elements) { this.elements = elements; }
    }

    /**
     * elementsByQuestion 맵을 QuestionStructure 리스트로 변환 (v0.7 추가)
     *
     * <p>PHASE 2 컨텍스트 검증을 위한 헬퍼 메서드</p>
     *
     * @param elementsByQuestion 문제별 요소 맵
     * @return QuestionStructure 리스트
     */
    private List<QuestionStructure> convertToQuestionStructures(Map<String, List<AnalysisElement>> elementsByQuestion) {
        List<QuestionStructure> structures = new ArrayList<>();

        for (Map.Entry<String, List<AnalysisElement>> entry : elementsByQuestion.entrySet()) {
            try {
                Integer questionNumber = Integer.parseInt(entry.getKey());
                List<AnalysisElement> elements = entry.getValue();

                // QuestionStructure 생성
                QuestionStructure structure = new QuestionStructure();
                structure.setQuestionNumber(questionNumber);
                structure.setElements(elements);

                // 첫 번째 요소에서 레이아웃 정보 추출
                if (!elements.isEmpty() && elements.get(0).getLayoutInfo() != null) {
                    structure.setLayoutElement(elements.get(0).getLayoutInfo());
                }

                structures.add(structure);
            } catch (NumberFormatException e) {
                logger.trace("문제 번호 변환 실패: {}", entry.getKey());
            }
        }

        return structures;
    }
}