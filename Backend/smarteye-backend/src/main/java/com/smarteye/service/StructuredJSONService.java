package com.smarteye.service;

import com.smarteye.dto.AIDescriptionResult;
import com.smarteye.dto.OCRResult;
import com.smarteye.dto.common.LayoutInfo;
import com.smarteye.service.StructuredAnalysisService.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 구조화된 JSON 생성 서비스
 * Python structured_json_generator.py의 기능을 Java로 포팅
 * 문제별로 정렬된 결과를 생성합니다.
 */
@Service
public class StructuredJSONService {
    
    private static final Logger logger = LoggerFactory.getLogger(StructuredJSONService.class);
    
    @Autowired
    private StructuredAnalysisService structuredAnalysisService;
    
    // 캐시된 OCR 결과 (AI 매핑에서 사용)
    private List<OCRResult> cachedOcrResults;
    
    /**
     * 구조화된 JSON 생성 (Python generate_structured_json() 메서드 포팅)
     */
    public StructuredResult generateStructuredJSON(List<OCRResult> ocrResults, 
                                                 List<AIDescriptionResult> aiResults, 
                                                 List<LayoutInfo> layoutElements) {
        
        logger.info("구조화된 JSON 생성 시작");
        
        // OCR 결과 캐시 (AI 매핑에서 사용)
        this.cachedOcrResults = ocrResults;
        
        // 디버깅: 입력 데이터 확인
        logger.info("OCR 결과 개수: {}", ocrResults.size());
        logger.info("AI 결과 개수: {}", aiResults.size());
        logger.info("레이아웃 요소 개수: {}", layoutElements.size());
        
        // 샘플 출력
        if (!ocrResults.isEmpty()) {
            logger.info("OCR 샘플: {}", ocrResults.get(0));
        }
        if (!aiResults.isEmpty()) {
            logger.info("AI 샘플: {}", aiResults.get(0));
        }
        if (!layoutElements.isEmpty()) {
            logger.info("레이아웃 샘플: {}", layoutElements.get(0));
        }
        
        // 1. 문제 구조 분석
        logger.info("문제 구조 분석 시작...");
        QuestionStructure structure = structuredAnalysisService.detectQuestionStructure(ocrResults, layoutElements);
        
        logger.info("감지된 문제 구조: 총 {}개 문제, 타입: {}", structure.totalQuestions, structure.layoutType);
        
        // 2. AI 결과를 문제별로 분류
        Map<String, List<AIDescriptionResult>> aiByQuestion = classifyAIResultsByQuestion(aiResults, structure);
        
        // 3. 최종 구조화된 결과 생성
        StructuredResult structuredResult = new StructuredResult();
        
        // 문서 정보 설정
        DocumentInfo documentInfo = new DocumentInfo();
        documentInfo.totalQuestions = structure.totalQuestions;
        documentInfo.layoutType = structure.layoutType;
        documentInfo.sections = structure.sections;
        structuredResult.documentInfo = documentInfo;
        
        // 4. 각 문제별로 정리
        structuredResult.questions = new ArrayList<>();
        
        for (Map.Entry<String, QuestionData> entry : structure.questions.entrySet()) {
            String qNum = entry.getKey();
            QuestionData questionData = entry.getValue();
            
            logger.info("문제 {} 처리 중", qNum);
            
            QuestionResult questionResult = formatQuestionResult(
                qNum, questionData, aiByQuestion.getOrDefault(qNum, new ArrayList<>())
            );
            structuredResult.questions.add(questionResult);
        }
        
        logger.info("최종 구조화 결과: {}개 문제", structuredResult.questions.size());
        
        return structuredResult;
    }
    
    /**
     * AI 결과를 문제별로 분류 (Python _classify_ai_results_by_question() 메서드 포팅)
     */
    private Map<String, List<AIDescriptionResult>> classifyAIResultsByQuestion(List<AIDescriptionResult> aiResults, QuestionStructure structure) {
        Map<String, List<AIDescriptionResult>> aiByQuestion = new HashMap<>();
        
        logger.info("AI 결과 분류 시작: {}개 항목", aiResults.size());
        
        for (int i = 0; i < aiResults.size(); i++) {
            AIDescriptionResult result = aiResults.get(i);
            logger.info("AI 결과 {}: {}", i, result);
            
            // AI 결과의 위치나 내용을 기반으로 문제 번호 추정
            String questionNum = estimateQuestionForAIResult(result, structure);
            logger.info("AI 결과 {} → 문제 {}에 할당", i, questionNum);
            
            aiByQuestion.computeIfAbsent(questionNum, k -> new ArrayList<>()).add(result);
        }
        
        logger.info("AI 분류 완료: {}", aiByQuestion);
        return aiByQuestion;
    }
    
    /**
     * AI 결과가 어느 문제에 속하는지 추정 (Python _estimate_question_for_ai_result() 메서드 포팅)
     */
    private String estimateQuestionForAIResult(AIDescriptionResult result, QuestionStructure structure) {
        // coordinates 키 확인
        int[] aiCoords = result.getCoordinates();
        if (aiCoords == null || aiCoords.length < 2) {
            logger.warn("AI 결과에 유효한 coordinates 없음: {}", result);
            return "unknown";
        }
        
        int aiY = aiCoords[1];
        logger.info("AI 결과 Y 좌표: {}", aiY);
        
        // 가장 가까운 문제 찾기
        String bestQuestion = "unknown";
        int minDistance = Integer.MAX_VALUE;
        
        // 문제별 Y 좌표를 OCR 결과에서 직접 찾기
        for (String qNum : structure.questions.keySet()) {
            Integer qY = getQuestionYFromOCR(qNum);
            
            if (qY != null) {
                int distance = Math.abs(aiY - qY);
                logger.debug("문제 {} Y={}, AI Y={}, 거리={}", qNum, qY, aiY, distance);
                
                if (distance < minDistance) {
                    minDistance = distance;
                    bestQuestion = qNum;
                }
            }
        }
        
        // 거리 임계값 확인 (너무 멀면 unknown)
        if (minDistance > 500) {  // 500px 이상 차이나면 unknown
            logger.warn("가장 가까운 문제와의 거리가 너무 큼: {}px", minDistance);
            bestQuestion = "unknown";
        }
        
        logger.info("가장 가까운 문제: {} (거리: {})", bestQuestion, minDistance);
        return bestQuestion;
    }
    
    /**
     * OCR 결과에서 특정 문제 번호의 Y 좌표 찾기 (Python _get_question_y_from_ocr() 메서드 포팅)
     */
    private Integer getQuestionYFromOCR(String questionNum) {
        if (cachedOcrResults != null) {
            for (OCRResult result : cachedOcrResults) {
                String text = result.getText() != null ? result.getText().trim() : "";
                String className = result.getClassName() != null ? result.getClassName() : "";
                
                if (text.equals(questionNum) && "question_number".equals(className)) {
                    int[] coords = result.getCoordinates();
                    if (coords != null && coords.length > 1) {
                        return coords[1];
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * 문제별 결과 포맷팅 (Python _format_question_result() 메서드 포팅)
     */
    private QuestionResult formatQuestionResult(String qNum, QuestionData questionData, List<AIDescriptionResult> aiResults) {
        logger.info("문제 {} 포맷팅", qNum);
        
        QuestionElements elements = questionData.elements;
        
        // 각 요소별 개수 로깅
        logger.info("문제 {} - 문제텍스트: {}개, 지문: {}개, 선택지: {}개, 이미지: {}개, 표: {}개", 
                   qNum, elements.questionText.size(), elements.passage.size(), 
                   elements.choices.size(), elements.images.size(), elements.tables.size());
        
        // 문제 내용 구성
        QuestionContent questionContent = new QuestionContent();
        questionContent.mainQuestion = extractMainQuestion(elements);
        questionContent.passage = combineTexts(elements.passage);
        questionContent.choices = formatChoices(elements.choices);
        questionContent.images = formatImages(elements.images, aiResults);
        questionContent.tables = formatTables(elements.tables, aiResults);
        questionContent.explanations = combineTexts(elements.explanations);
        
        // AI 분석 구성
        AIAnalysis aiAnalysis = new AIAnalysis();
        aiAnalysis.imageDescriptions = aiResults.stream()
            .filter(r -> "figure".equals(r.getClassName()))
            .collect(Collectors.toList());
        aiAnalysis.tableAnalysis = aiResults.stream()
            .filter(r -> "table".equals(r.getClassName()))
            .collect(Collectors.toList());
        aiAnalysis.problemAnalysis = aiResults.stream()
            .filter(r -> !"figure".equals(r.getClassName()) && !"table".equals(r.getClassName()))
            .collect(Collectors.toList());
        
        QuestionResult result = new QuestionResult();
        result.questionNumber = qNum;
        result.section = questionData.section;
        result.questionContent = questionContent;
        result.aiAnalysis = aiAnalysis;
        
        return result;
    }
    
    /**
     * 주요 문제 텍스트 추출 (Python _extract_main_question() 메서드 포팅)
     */
    private String extractMainQuestion(QuestionElements elements) {
        List<TextElement> questionTexts = elements.questionText;
        logger.info("주요 문제 텍스트 추출: {}개 후보", questionTexts.size());
        
        if (!questionTexts.isEmpty()) {
            // 가장 긴 텍스트를 주요 문제로 간주
            TextElement mainText = questionTexts.stream()
                .max(Comparator.comparing(t -> t.text != null ? t.text.length() : 0))
                .orElse(null);
            
            if (mainText != null) {
                String result = mainText.text != null ? mainText.text : "";
                logger.info("선택된 주요 문제: '{}'", result.length() > 50 ? result.substring(0, 50) + "..." : result);
                return result;
            }
        }
        
        logger.info("주요 문제 텍스트 없음");
        return "";
    }
    
    /**
     * 텍스트 요소들 결합 (Python _combine_texts() 메서드 포팅)
     */
    private String combineTexts(List<TextElement> textElements) {
        if (textElements == null || textElements.isEmpty()) {
            return "";
        }
        
        logger.info("텍스트 결합: {}개 요소", textElements.size());
        
        // Y 좌표 순으로 정렬 후 결합
        List<TextElement> sortedElements = textElements.stream()
            .sorted(Comparator.comparing(e -> e.bbox != null && e.bbox.length > 1 ? e.bbox[1] : 0))
            .collect(Collectors.toList());
        
        String result = sortedElements.stream()
            .map(elem -> elem.text != null ? elem.text : "")
            .collect(Collectors.joining(" "));
        
        logger.info("결합된 텍스트: '{}'", result.length() > 50 ? result.substring(0, 50) + "..." : result);
        return result;
    }
    
    /**
     * 선택지 포맷팅 (Python _format_choices() 메서드 포팅)
     */
    private List<Choice> formatChoices(List<TextElement> choiceElements) {
        if (choiceElements == null || choiceElements.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Y 좌표 순으로 정렬
        List<TextElement> sortedChoices = choiceElements.stream()
            .sorted(Comparator.comparing(e -> e.bbox != null && e.bbox.length > 1 ? e.bbox[1] : 0))
            .collect(Collectors.toList());
        
        List<Choice> formattedChoices = new ArrayList<>();
        for (TextElement choice : sortedChoices) {
            Choice choiceObj = new Choice();
            choiceObj.choiceNumber = extractChoiceNumber(choice.text);
            choiceObj.choiceText = choice.text;
            choiceObj.bbox = choice.bbox;
            
            formattedChoices.add(choiceObj);
        }
        
        return formattedChoices;
    }
    
    /**
     * 이미지 포맷팅 (Python _format_images() 메서드 포팅)
     */
    private List<ImageDescription> formatImages(List<LayoutInfo> imageElements, List<AIDescriptionResult> aiResults) {
        List<ImageDescription> formattedImages = new ArrayList<>();
        
        for (LayoutInfo image : imageElements) {
            // 해당 이미지에 대한 AI 설명 찾기
            String description = "";
            for (AIDescriptionResult aiResult : aiResults) {
                if ("figure".equals(aiResult.getClassName())) {
                    description = aiResult.getDescription() != null ? aiResult.getDescription() : "";
                    break;
                }
            }
            
            ImageDescription imageDesc = new ImageDescription();
            imageDesc.bbox = image.getBox();
            imageDesc.description = description;
            imageDesc.confidence = image.getConfidence();
            
            formattedImages.add(imageDesc);
        }
        
        return formattedImages;
    }
    
    /**
     * 표 포맷팅 (Python _format_tables() 메서드 포팅)
     */
    private List<TableDescription> formatTables(List<LayoutInfo> tableElements, List<AIDescriptionResult> aiResults) {
        List<TableDescription> formattedTables = new ArrayList<>();
        
        for (LayoutInfo table : tableElements) {
            // 해당 표에 대한 AI 설명 찾기
            String description = "";
            for (AIDescriptionResult aiResult : aiResults) {
                if ("table".equals(aiResult.getClassName())) {
                    description = aiResult.getDescription() != null ? aiResult.getDescription() : "";
                    break;
                }
            }
            
            TableDescription tableDesc = new TableDescription();
            tableDesc.bbox = table.getBox();
            tableDesc.description = description;
            tableDesc.confidence = table.getConfidence();
            
            formattedTables.add(tableDesc);
        }
        
        return formattedTables;
    }
    
    /**
     * 선택지 번호 추출 (Python _extract_choice_number() 메서드 포팅)
     */
    private String extractChoiceNumber(String text) {
        if (text == null) return "";
        
        List<Pattern> patterns = Arrays.asList(
            Pattern.compile("^([①②③④⑤⑥⑦⑧⑨⑩])"),
            Pattern.compile("^[(（]\\s*([1-5])\\s*[)）]"),
            Pattern.compile("^([1-5])\\s*[.．]")
        );
        
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        return "";
    }
    
    // === 결과 데이터 클래스들 ===
    
    public static class StructuredResult {
        public DocumentInfo documentInfo;
        public List<QuestionResult> questions;
    }
    
    public static class DocumentInfo {
        public int totalQuestions;
        public String layoutType;
        public Map<String, SectionInfo> sections;
    }
    
    public static class QuestionResult {
        public String questionNumber;
        public String section;
        public QuestionContent questionContent;
        public AIAnalysis aiAnalysis;
    }
    
    public static class QuestionContent {
        public String mainQuestion;
        public String passage;
        public List<Choice> choices;
        public List<ImageDescription> images;
        public List<TableDescription> tables;
        public String explanations;
    }
    
    public static class Choice {
        public String choiceNumber;
        public String choiceText;
        public int[] bbox;
    }
    
    public static class ImageDescription {
        public int[] bbox;
        public String description;
        public double confidence;
    }
    
    public static class TableDescription {
        public int[] bbox;
        public String description;
        public double confidence;
    }
    
    public static class AIAnalysis {
        public List<AIDescriptionResult> imageDescriptions;
        public List<AIDescriptionResult> tableAnalysis;
        public List<AIDescriptionResult> problemAnalysis;
    }
}