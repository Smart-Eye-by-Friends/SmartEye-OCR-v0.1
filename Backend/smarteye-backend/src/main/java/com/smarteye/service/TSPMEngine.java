package com.smarteye.service;

import com.smarteye.dto.*;
import com.smarteye.entity.LayoutBlock;
import com.smarteye.entity.TextBlock;
import com.smarteye.repository.LayoutBlockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * TSPM (Text Structure Pattern Matching) 엔진
 * JPA 관계를 활용한 교육 문서 레이아웃 분석 및 문제별 정렬
 * 
 * 레거시 Python 코드의 핵심 알고리즘을 Java로 변환 구현:
 * - question_number 클래스 기반 문제 감지
 * - Y좌표 proximity 알고리즘
 * - 텍스트 패턴 매칭 (①②③④, 1번 2번 등)
 * - 실제 LAM 클래스 기반 교육 문서 요소 분류
 */
@Service
public class TSPMEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(TSPMEngine.class);
    
    @Autowired
    private LayoutBlockRepository layoutBlockRepository;
    
    // 레거시 Python에서 사용된 패턴들을 Java로 변환
    private static final List<Pattern> QUESTION_NUMBER_PATTERNS = Arrays.asList(
        Pattern.compile("(\\d+)번"),           // 1번, 2번 형식
        Pattern.compile("(\\d+)\\."),          // 1., 2. 형식  
        Pattern.compile("문제\\s*(\\d+)"),     // 문제 1, 문제 2 형식
        Pattern.compile("(\\d+)\\s*(?:\\)|）)"), // 1), 2) 형식
        Pattern.compile("Q\\s*(\\d+)"),        // Q1, Q2 형식
        Pattern.compile("(\\d{2,3})")          // 593, 594 등 문제번호
    );
    
    // 선택지 패턴들 (레거시 Python에서 가져옴)
    private static final List<Pattern> CHOICE_PATTERNS = Arrays.asList(
        Pattern.compile("^[①②③④⑤⑥⑦⑧⑨⑩]"),    // 원문자 선택지
        Pattern.compile("^[(（]\\s*[1-5]\\s*[)）]"),  // (1), (2) 형식
        Pattern.compile("^[1-5]\\s*[.．]")           // 1., 2. 형식
    );
    
    // 실제 LAM 클래스 기반 교육 문서 요소 우선순위
    private static final Map<String, Integer> EDUCATIONAL_PRIORITY = Map.of(
        "question_number", 1,    // 문제 번호 (문제 구분 기준)
        "question_text", 2,      // 문제 내용
        "question_type", 3,      // 문제 유형
        "title", 4,              // 제목/소제목
        "figure", 5,             // 이미지/그림
        "table", 6,              // 표
        "list", 7,               // 선택지 (패턴 매칭 필요)
        "plain_text", 8,         // 지문/설명
        "isolated_formula", 9,   // 수식
        "formula_caption", 10    // 수식 설명
    );
    
    // proximity 알고리즘 임계값 (레거시 Python: 500px)
    private static final int PROXIMITY_THRESHOLD = 500;
    
    /**
     * 메인 TSPM 분석 수행
     * @param documentPageId 문서 페이지 ID
     * @return TSPM 분석 결과
     */
    public TSPMResult performTSPMAnalysis(Long documentPageId) {
        logger.info("🔧 TSPM 분석 시작 - 페이지 ID: {}", documentPageId);
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. JPA 관계를 활용한 데이터 로드 (Y좌표 순 정렬)
            List<LayoutBlock> layoutsWithText = layoutBlockRepository
                .findByDocumentPageIdWithTextBlocksOrderByPosition(documentPageId);
            
            logger.info("📊 로드된 데이터: LayoutBlock {} 개 (TextBlock 포함)", layoutsWithText.size());
            
            // 2. question_number 클래스 감지 및 문제 위치 추출
            Map<String, Integer> questionPositions = extractQuestionPositions(layoutsWithText);
            logger.info("🔍 감지된 문제 번호: {} 개 - {}", questionPositions.size(), questionPositions.keySet());
            
            // 3. TSPM 공간/텍스트 분석 수행
            TSPMResult result = analyzeSpatialAndTextualPatterns(layoutsWithText, questionPositions);
            
            // 4. 메타데이터 설정
            long processingTime = System.currentTimeMillis() - startTime;
            result.getAnalysisMetadata().setProcessingTimeMs(processingTime);
            
            logger.info("✅ TSPM 분석 완료 - 처리 시간: {}ms, 문제 수: {}", 
                       processingTime, result.getQuestionGroups().size());
            
            return result;
            
        } catch (Exception e) {
            logger.error("❌ TSPM 분석 실패: {}", e.getMessage(), e);
            throw new RuntimeException("TSPM 분석 중 오류 발생", e);
        }
    }
    
    /**
     * question_number 클래스에서 문제 번호와 Y좌표 추출
     * 레거시 Python: _get_question_y_from_ocr() 메서드 변환
     */
    private Map<String, Integer> extractQuestionPositions(List<LayoutBlock> layouts) {
        Map<String, Integer> questionPositions = new HashMap<>();
        
        for (LayoutBlock layout : layouts) {
            // question_number 클래스만 처리
            if (!"question_number".equals(layout.getClassName())) {
                continue;
            }
            
            String extractedText = getExtractedText(layout);
            if (extractedText == null || extractedText.trim().isEmpty()) {
                continue;
            }
            
            // 텍스트에서 문제 번호 추출
            String questionNum = extractQuestionNumber(extractedText.trim());
            if (questionNum != null) {
                questionPositions.put(questionNum, layout.getY1());
                logger.debug("📍 문제 번호 감지: {} → Y좌표: {}", questionNum, layout.getY1());
            }
        }
        
        return questionPositions;
    }
    
    /**
     * 텍스트에서 문제 번호 추출
     * 레거시 Python 패턴들을 활용
     */
    private String extractQuestionNumber(String text) {
        for (Pattern pattern : QUESTION_NUMBER_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }
    
    /**
     * 공간적/텍스트 패턴 통합 분석
     * 레거시 Python: _estimate_question_for_ai_result() + _classify_text_element() 통합
     */
    private TSPMResult analyzeSpatialAndTextualPatterns(List<LayoutBlock> layouts, Map<String, Integer> questionPositions) {
        Map<String, QuestionGroup> questionGroups = new HashMap<>();
        List<LayoutBlock> unassignedElements = new ArrayList<>();
        
        // 문제 그룹 초기화
        for (Map.Entry<String, Integer> entry : questionPositions.entrySet()) {
            QuestionGroup group = new QuestionGroup(entry.getKey(), entry.getValue());
            questionGroups.put(entry.getKey(), group);
        }
        
        // 각 요소를 문제별로 할당
        for (LayoutBlock layout : layouts) {
            String assignedQuestion = assignElementToQuestion(layout, questionPositions);
            
            if ("unknown".equals(assignedQuestion)) {
                unassignedElements.add(layout);
                continue;
            }
            
            // EducationalElement 생성
            EducationalElement element = createEducationalElement(layout);
            
            // 문제 그룹에 추가
            QuestionGroup group = questionGroups.get(assignedQuestion);
            if (group != null) {
                addElementToGroup(group, element);
            }
        }
        
        logger.info("📊 할당 결과: 할당됨 {} 개, 미할당 {} 개", 
                   layouts.size() - unassignedElements.size(), unassignedElements.size());
        
        // TSPMResult 구성
        return buildTSPMResult(questionGroups, unassignedElements.size());
    }
    
    /**
     * Y좌표 proximity 알고리즘으로 요소를 문제에 할당
     * 레거시 Python: _estimate_question_for_ai_result() 메서드 변환
     */
    private String assignElementToQuestion(LayoutBlock layout, Map<String, Integer> questionPositions) {
        int elementY = layout.getY1();
        String bestQuestion = "unknown";
        int minDistance = Integer.MAX_VALUE;
        
        // 가장 가까운 문제 찾기
        for (Map.Entry<String, Integer> entry : questionPositions.entrySet()) {
            int distance = Math.abs(elementY - entry.getValue());
            
            if (distance < minDistance) {
                minDistance = distance;
                bestQuestion = entry.getKey();
            }
        }
        
        // 거리 임계값 확인 (레거시 Python: 500px)
        if (minDistance > PROXIMITY_THRESHOLD) {
            logger.debug("⚠️ 요소 Y={} 가 가장 가까운 문제와 거리 {}px로 임계값 초과", 
                        elementY, minDistance);
            return "unknown";
        }
        
        logger.debug("✅ 요소 Y={} → 문제 {} 할당 (거리: {}px)", 
                    elementY, bestQuestion, minDistance);
        return bestQuestion;
    }
    
    /**
     * LayoutBlock + TextBlock에서 EducationalElement 생성
     */
    private EducationalElement createEducationalElement(LayoutBlock layout) {
        String extractedText = getExtractedText(layout);
        EducationalElement element = new EducationalElement(layout.getId(), layout.getClassName(), extractedText);
        
        // 위치 정보 설정
        element.setPosition(new EducationalElement.Position(
            layout.getX1(), layout.getY1(), layout.getX2(), layout.getY2()));
        
        // 신뢰도 정보 설정
        TextBlock textBlock = layout.getTextBlock();
        double ocrConfidence = textBlock != null && textBlock.getConfidence() != null ? 
                              textBlock.getConfidence() : 0.0;
        element.setConfidence(new EducationalElement.ConfidenceInfo(
            layout.getConfidence(), ocrConfidence));
        
        // AI 설명 설정
        element.setAiDescription(layout.getAiDescription());
        
        // 텍스트 패턴 분석 및 세분화된 타입 결정
        analyzeTextPatternsAndRefinedType(element, extractedText, layout.getClassName());
        
        return element;
    }
    
    /**
     * 텍스트 패턴 분석 및 세분화된 타입 결정
     * 레거시 Python: _classify_text_element() 메서드 변환
     */
    private void analyzeTextPatternsAndRefinedType(EducationalElement element, String text, String originalClass) {
        EducationalElement.TextPatterns patterns = element.getTextPatterns();
        
        if (text == null || text.trim().isEmpty()) {
            element.setRefinedType(originalClass);
            return;
        }
        
        String trimmedText = text.trim();
        
        // 선택지 패턴 확인
        boolean isChoice = CHOICE_PATTERNS.stream()
            .anyMatch(pattern -> pattern.matcher(trimmedText).find());
        patterns.setChoicePattern(isChoice);
        
        // 문제 번호 패턴 확인
        boolean isQuestionNumber = QUESTION_NUMBER_PATTERNS.stream()
            .anyMatch(pattern -> pattern.matcher(trimmedText).find());
        patterns.setQuestionNumberPattern(isQuestionNumber);
        
        // 세분화된 타입 결정
        String refinedType = determineRefinedType(originalClass, trimmedText, isChoice);
        element.setRefinedType(refinedType);
        patterns.setTextClassification(refinedType);
        
        logger.debug("🔍 패턴 분석: {} → {} (선택지: {}, 문제번호: {})", 
                    originalClass, refinedType, isChoice, isQuestionNumber);
    }
    
    /**
     * 실제 LAM 클래스 + 텍스트 패턴으로 세분화된 타입 결정
     */
    private String determineRefinedType(String originalClass, String text, boolean isChoicePattern) {
        // list 클래스의 경우 텍스트 패턴으로 세분화
        if ("list".equals(originalClass) && isChoicePattern) {
            return "choices";
        }
        
        // plain_text의 경우 내용으로 세분화
        if ("plain_text".equals(originalClass)) {
            if (isPassagePattern(text)) {
                return "passage";
            } else if (isExplanationPattern(text)) {
                return "explanation";
            }
        }
        
        // 기본적으로 LAM 원본 클래스 사용
        return originalClass;
    }
    
    /**
     * 지문 패턴 확인 (레거시 Python 로직)
     */
    private boolean isPassagePattern(String text) {
        return text.contains("다음을") || text.contains("아래의") || 
               text.contains("위의") || text.contains("그림을") || text.contains("표를");
    }
    
    /**
     * 설명/해설 패턴 확인 (레거시 Python 로직)
     */
    private boolean isExplanationPattern(String text) {
        return text.contains("설명") || text.contains("해설") || 
               text.contains("풀이") || text.contains("답:");
    }
    
    /**
     * QuestionGroup에 요소 추가
     */
    private void addElementToGroup(QuestionGroup group, EducationalElement element) {
        QuestionGroup.QuestionElements elements = group.getElements();
        String refinedType = element.getRefinedType();
        
        switch (refinedType) {
            case "question_text" -> elements.getQuestionText().add(element);
            case "question_type" -> elements.getQuestionType().add(element);
            case "title" -> elements.getTitle().add(element);
            case "plain_text", "passage" -> elements.getPlainText().add(element);
            case "list", "choices" -> elements.getListItems().add(element);
            case "figure" -> elements.getFigures().add(element);
            case "table" -> elements.getTables().add(element);
            case "isolated_formula", "formula_caption" -> elements.getFormulas().add(element);
            default -> elements.getPlainText().add(element); // 기본값
        }
        
        // AI 분석 정보 추가
        if (element.getAiDescription() != null && !element.getAiDescription().trim().isEmpty()) {
            QuestionGroup.AIAnalysis aiAnalysis = group.getAiAnalysis();
            switch (refinedType) {
                case "figure" -> aiAnalysis.getImageDescriptions().add(element.getAiDescription());
                case "table" -> aiAnalysis.getTableAnalysis().add(element.getAiDescription());
                case "isolated_formula", "formula_caption" -> 
                    aiAnalysis.getFormulaDescriptions().add(element.getAiDescription());
            }
        }
        
        // 공간 정보 업데이트
        updateSpatialInfo(group, element);
    }
    
    /**
     * 공간 정보 업데이트
     */
    private void updateSpatialInfo(QuestionGroup group, EducationalElement element) {
        QuestionGroup.SpatialInfo spatialInfo = group.getSpatialInfo();
        spatialInfo.setTotalElements(spatialInfo.getTotalElements() + 1);
        spatialInfo.setProximityElements(spatialInfo.getProximityElements() + 1);
        
        // Y 범위 업데이트
        int elementY = element.getPosition().getY1();
        int[] yRange = spatialInfo.getYRange();
        if (yRange[0] == 0 || elementY < yRange[0]) yRange[0] = elementY;
        if (elementY > yRange[1]) yRange[1] = elementY;
    }
    
    /**
     * 최종 TSPMResult 구성
     */
    private TSPMResult buildTSPMResult(Map<String, QuestionGroup> questionGroups, int unassignedCount) {
        // QuestionGroup을 번호 순으로 정렬
        List<QuestionGroup> sortedGroups = questionGroups.values().stream()
            .sorted(Comparator.comparing(group -> {
                try {
                    return Integer.parseInt(group.getQuestionNumber());
                } catch (NumberFormatException e) {
                    return 999; // 숫자가 아닌 경우 뒤로
                }
            }))
            .collect(Collectors.toList());
        
        // DocumentInfo 설정
        TSPMResult.DocumentInfo documentInfo = new TSPMResult.DocumentInfo();
        documentInfo.setTotalQuestions(questionGroups.size());
        documentInfo.setLayoutType(determineLayoutType(questionGroups.size()));
        
        // TSPMResult 생성
        TSPMResult result = new TSPMResult(documentInfo, sortedGroups);
        
        // 분석 메타데이터 설정
        TSPMResult.AnalysisMetadata metadata = result.getAnalysisMetadata();
        metadata.setAnalysisTimestamp(LocalDateTime.now());
        metadata.setProximityThreshold(PROXIMITY_THRESHOLD);
        
        logger.info("📋 TSPM 결과 구성: 문제 {} 개, 미할당 요소 {} 개", 
                   questionGroups.size(), unassignedCount);
        
        return result;
    }
    
    /**
     * 레이아웃 타입 결정 (레거시 Python 로직)
     */
    private String determineLayoutType(int totalQuestions) {
        if (totalQuestions <= 2) return "simple";
        else if (totalQuestions > 5) return "multiple_choice";
        else return "standard";
    }
    
    /**
     * LayoutBlock에서 텍스트 추출 (JPA 관계 활용)
     */
    private String getExtractedText(LayoutBlock layout) {
        TextBlock textBlock = layout.getTextBlock();
        if (textBlock != null && textBlock.getExtractedText() != null) {
            return textBlock.getExtractedText();
        }
        // fallback: OCR 텍스트 사용
        return layout.getOcrText();
    }
}