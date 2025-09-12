package com.smarteye.service;

import com.smarteye.dto.OCRResult;
import com.smarteye.dto.common.LayoutInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 구조화된 레이아웃 분석 서비스
 * Python layout_analyzer_enhanced.py의 기능을 Java로 포팅
 * 문제별 구조 감지 및 요소 그룹핑을 수행합니다.
 */
@Service
public class StructuredAnalysisService {
    
    private static final Logger logger = LoggerFactory.getLogger(StructuredAnalysisService.class);
    
    // 문제 번호 패턴들 (Python과 동일)
    private static final List<Pattern> QUESTION_PATTERNS = Arrays.asList(
        Pattern.compile("(\\d+)번"),           // 1번, 2번 형식
        Pattern.compile("(\\d+)\\."),          // 1., 2. 형식  
        Pattern.compile("문제\\s*(\\d+)"),      // 문제 1, 문제 2 형식
        Pattern.compile("(\\d+)\\s*(?:\\)|）)"), // 1), 2) 형식
        Pattern.compile("Q\\s*(\\d+)"),        // Q1, Q2 형식
        Pattern.compile("(\\d{2,3})")          // 593, 594 등 문제번호
    );
    
    // 섹션 패턴들 (Python과 동일)
    private static final List<Pattern> SECTION_PATTERNS = Arrays.asList(
        Pattern.compile("([A-Z])\\s*섹션"),    // A섹션, B섹션
        Pattern.compile("([A-Z])\\s*부분"),    // A부분, B부분
        Pattern.compile("([A-Z])\\s+")         // A, B (단독)
    );
    
    // 선택지 패턴들
    private static final List<Pattern> CHOICE_PATTERNS = Arrays.asList(
        Pattern.compile("^[①②③④⑤⑥⑦⑧⑨⑩]"),  // 원문자 선택지
        Pattern.compile("^[(（]\\s*[1-5]\\s*[)）]"),   // (1), (2) 형식
        Pattern.compile("^[1-5]\\s*[.．]")          // 1., 2. 형식
    );

    /**
     * 문제 구조 감지 및 분석
     * Python detect_question_structure() 메서드와 동일한 기능
     */
    public QuestionStructure detectQuestionStructure(List<OCRResult> ocrResults, List<LayoutInfo> layoutElements) {
        logger.info("문제 구조 감지 시작 - OCR: {}개, Layout: {}개", ocrResults.size(), layoutElements.size());
        
        Map<String, QuestionData> questions = new HashMap<>();
        
        // 1. 문제 번호 감지
        List<String> questionNumbers = extractQuestionNumbers(ocrResults);
        logger.info("감지된 문제 번호들: {}", questionNumbers);
        
        // 2. 섹션 구분 감지  
        Map<String, SectionInfo> sections = extractSections(ocrResults);
        logger.info("감지된 섹션들: {}", sections.keySet());
        
        // 3. 각 문제별 요소 그룹핑
        for (String qNum : questionNumbers) {
            logger.info("문제 {} 처리 시작", qNum);
            QuestionData questionData = new QuestionData();
            questionData.number = qNum;
            questionData.section = findSectionForQuestion(qNum, sections);
            questionData.elements = groupElementsByQuestion(qNum, ocrResults, layoutElements);
            
            questions.put(qNum, questionData);
            logger.info("문제 {} 처리 완료", qNum);
        }
        
        QuestionStructure result = new QuestionStructure();
        result.totalQuestions = questions.size();
        result.sections = sections;
        result.questions = questions;
        result.layoutType = determineLayoutType(questions);
        
        logger.info("최종 문제 구조: 총 {}개 문제, 타입: {}", questions.size(), result.layoutType);
        return result;
    }
    
    /**
     * 문제 번호 추출 (Python _extract_question_numbers() 메서드 포팅)
     */
    private List<String> extractQuestionNumbers(List<OCRResult> ocrResults) {
        Set<String> questionNumbers = new HashSet<>();
        
        for (OCRResult result : ocrResults) {
            String text = result.getText() != null ? result.getText().trim() : "";
            
            for (Pattern pattern : QUESTION_PATTERNS) {
                Matcher matcher = pattern.matcher(text);
                while (matcher.find()) {
                    String number = matcher.group(1);
                    questionNumbers.add(number);
                }
            }
        }
        
        // 숫자 순으로 정렬
        return questionNumbers.stream()
            .sorted((a, b) -> {
                try {
                    return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
                } catch (NumberFormatException e) {
                    return a.compareTo(b);
                }
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 섹션 구분 추출 (Python _extract_sections() 메서드 포팅)
     */
    private Map<String, SectionInfo> extractSections(List<OCRResult> ocrResults) {
        Map<String, SectionInfo> sections = new HashMap<>();
        
        for (OCRResult result : ocrResults) {
            String text = result.getText() != null ? result.getText().trim() : "";
            
            for (Pattern pattern : SECTION_PATTERNS) {
                Matcher matcher = pattern.matcher(text);
                while (matcher.find()) {
                    String sectionName = matcher.group(1);
                    SectionInfo sectionInfo = new SectionInfo();
                    sectionInfo.name = sectionName;
                    sectionInfo.bbox = result.getCoordinates();
                    sectionInfo.yPosition = result.getCoordinates() != null && result.getCoordinates().length > 1 ? 
                        result.getCoordinates()[1] : 0;
                    
                    sections.put(sectionName, sectionInfo);
                }
            }
        }
        
        return sections;
    }
    
    /**
     * 문제가 속한 섹션 찾기 (Python _find_section_for_question() 메서드 포팅)
     */
    private String findSectionForQuestion(String questionNum, Map<String, SectionInfo> sections) {
        // 간단한 구현: 첫 번째 섹션에 할당
        if (!sections.isEmpty()) {
            return sections.keySet().iterator().next();
        }
        return null;
    }
    
    /**
     * 문제별 요소 그룹핑 (Python _group_elements_by_question() 메서드 포팅)
     */
    private QuestionElements groupElementsByQuestion(String questionNum, List<OCRResult> ocrResults, List<LayoutInfo> layoutElements) {
        logger.info("문제 {} 요소 그룹핑 시작", questionNum);
        
        // 문제 번호의 위치 찾기
        int[] questionBbox = findQuestionBbox(questionNum, ocrResults);
        if (questionBbox == null || questionBbox.length < 2) {
            logger.warn("문제 {}의 유효한 bbox를 찾을 수 없음", questionNum);
            return new QuestionElements();
        }
        
        int questionY = questionBbox[1];  // 문제의 Y 좌표
        logger.info("문제 {} Y 좌표: {}", questionNum, questionY);
        
        // 다음 문제의 Y 좌표 찾기 (경계 설정)
        int nextQuestionY = findNextQuestionY(questionNum, ocrResults);
        logger.info("문제 {} 다음 경계 Y: {}", questionNum, nextQuestionY);
        
        QuestionElements elements = new QuestionElements();
        
        // OCR 결과에서 해당 문제 범위의 텍스트 수집
        int matchedCount = 0;
        for (OCRResult result : ocrResults) {
            int[] bbox = result.getCoordinates();
            int yPos = bbox != null && bbox.length > 1 ? bbox[1] : 0;
            
            // 문제 범위 내의 요소만 포함
            if (questionY <= yPos && yPos < nextQuestionY) {
                String text = result.getText() != null ? result.getText().trim() : "";
                
                // 텍스트 유형 분류
                String elementType = classifyTextElement(text);
                TextElement textElement = new TextElement();
                textElement.text = text;
                textElement.bbox = bbox;
                textElement.confidence = 90.0; // 기본 신뢰도
                
                switch (elementType) {
                    case "choices":
                        elements.choices.add(textElement);
                        break;
                    case "passage":
                        elements.passage.add(textElement);
                        break;
                    case "explanations":
                        elements.explanations.add(textElement);
                        break;
                    default:
                        elements.questionText.add(textElement);
                        break;
                }
                
                matchedCount++;
                logger.debug("문제 {}에 할당: {} - '{}'", questionNum, elementType, text.substring(0, Math.min(30, text.length())) + "...");
            }
        }
        
        logger.info("문제 {} 범위에서 {}개 OCR 요소 발견", questionNum, matchedCount);
        
        // 레이아웃 요소에서 이미지, 표 등 수집
        int layoutMatchedCount = 0;
        for (LayoutInfo element : layoutElements) {
            int[] bbox = element.getBox();
            int yPos = bbox != null && bbox.length > 1 ? bbox[1] : 0;
            
            if (questionY <= yPos && yPos < nextQuestionY) {
                String className = element.getClassName() != null ? element.getClassName() : "";
                if ("figure".equals(className)) {
                    elements.images.add(element);
                    layoutMatchedCount++;
                    logger.debug("문제 {}에 이미지 할당", questionNum);
                } else if ("table".equals(className)) {
                    elements.tables.add(element);
                    layoutMatchedCount++;
                    logger.debug("문제 {}에 표 할당", questionNum);
                }
            }
        }
        
        logger.info("문제 {} 범위에서 {}개 레이아웃 요소 발견", questionNum, layoutMatchedCount);
        
        // 결과 요약
        logger.info("문제 {} - 문제텍스트: {}개, 지문: {}개, 선택지: {}개, 이미지: {}개, 표: {}개", 
                   questionNum, elements.questionText.size(), elements.passage.size(), 
                   elements.choices.size(), elements.images.size(), elements.tables.size());
        
        return elements;
    }
    
    /**
     * 텍스트 요소 분류 (Python _classify_text_element() 메서드 포팅)
     */
    private String classifyTextElement(String text) {
        // 선택지 패턴 체크
        for (Pattern pattern : CHOICE_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return "choices";
            }
        }
        
        // 지문/설명 패턴
        if (text.contains("다음을") || text.contains("아래의") || text.contains("위의") || 
            text.contains("그림을") || text.contains("표를")) {
            return "passage";
        }
        
        // 설명/해설 패턴  
        if (text.contains("설명") || text.contains("해설") || text.contains("풀이") || text.contains("답:")) {
            return "explanations";
        }
        
        // 기본은 문제 텍스트
        return "question_text";
    }
    
    /**
     * 문제 번호의 bbox 찾기 (Python _find_question_bbox() 메서드 포팅)
     */
    private int[] findQuestionBbox(String questionNum, List<OCRResult> ocrResults) {
        logger.info("문제 번호 '{}' 찾는 중...", questionNum);
        
        for (OCRResult result : ocrResults) {
            String text = result.getText() != null ? result.getText().trim() : "";
            String className = result.getClassName() != null ? result.getClassName() : "";
            
            logger.debug("OCR 텍스트 확인: '{}' (클래스: {})", text, className);
            
            // 1차: 정확한 텍스트 매칭 + 클래스 확인
            if (text.equals(questionNum) && "question_number".equals(className)) {
                int[] bbox = result.getCoordinates();
                logger.info("정확한 매칭: 문제 {} bbox = {}", questionNum, Arrays.toString(bbox));
                return bbox;
            }
            
            // 2차: 유연한 매칭 (fallback)
            if ("question_number".equals(className)) {
                if (text.equals(questionNum + "번") || text.equals(questionNum + ".") || text.startsWith(questionNum)) {
                    int[] bbox = result.getCoordinates();
                    logger.info("패턴 매칭: 문제 {} bbox = {}", questionNum, Arrays.toString(bbox));
                    return bbox;
                }
            }
        }
        
        logger.warn("문제 {}의 bbox를 찾을 수 없음", questionNum);
        return null;
    }
    
    /**
     * 다음 문제의 Y 좌표 찾기 (Python _find_next_question_y() 메서드 포팅)
     */
    private int findNextQuestionY(String currentNum, List<OCRResult> ocrResults) {
        try {
            int currentInt = Integer.parseInt(currentNum);
            logger.debug("현재 문제 번호: {}", currentInt);
            
            // 다음 문제들 순차적으로 확인 (최대 10개까지)
            for (int nextInt = currentInt + 1; nextInt <= currentInt + 10; nextInt++) {
                String nextNum = String.valueOf(nextInt);
                int[] nextBbox = findQuestionBbox(nextNum, ocrResults);
                
                if (nextBbox != null && nextBbox.length > 1) {
                    int nextY = nextBbox[1];
                    logger.info("다음 문제 {} Y좌표: {}", nextNum, nextY);
                    return nextY;
                }
            }
            
            // 다음 문제를 찾을 수 없는 경우
            logger.info("문제 {} 이후 문제가 없음 (마지막 문제)", currentNum);
            return Integer.MAX_VALUE;
            
        } catch (NumberFormatException e) {
            logger.error("문제 번호 '{}'를 정수로 변환할 수 없음", currentNum);
            return Integer.MAX_VALUE;
        } catch (Exception e) {
            logger.error("다음 문제 Y좌표 찾기 실패: {}", e.getMessage());
            return Integer.MAX_VALUE;
        }
    }
    
    /**
     * 레이아웃 타입 결정 (Python _determine_layout_type() 메서드 포팅)
     */
    private String determineLayoutType(Map<String, QuestionData> questions) {
        if (questions.size() <= 2) {
            return "simple";
        } else if (questions.values().stream().anyMatch(q -> q.section != null)) {
            return "sectioned";
        } else if (questions.size() > 5) {
            return "multiple_choice";
        } else {
            return "standard";
        }
    }
    
    // === 내부 데이터 클래스들 ===
    
    public static class QuestionStructure {
        public int totalQuestions;
        public Map<String, SectionInfo> sections;
        public Map<String, QuestionData> questions;
        public String layoutType;
    }
    
    public static class SectionInfo {
        public String name;
        public int[] bbox;
        public int yPosition;
    }
    
    public static class QuestionData {
        public String number;
        public String section;
        public QuestionElements elements;
    }
    
    public static class QuestionElements {
        public List<TextElement> questionText = new ArrayList<>();
        public List<TextElement> passage = new ArrayList<>();
        public List<LayoutInfo> images = new ArrayList<>();
        public List<LayoutInfo> tables = new ArrayList<>();
        public List<TextElement> choices = new ArrayList<>();
        public List<TextElement> explanations = new ArrayList<>();
    }
    
    public static class TextElement {
        public String text;
        public int[] bbox;
        public double confidence;
    }
}