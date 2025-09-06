package com.smarteye.service;

import com.smarteye.entity.*;
import com.smarteye.repository.*;
import com.smarteye.exception.CIMGenerationException;
import com.smarteye.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional
public class CIMService {

    private static final Logger logger = LoggerFactory.getLogger(CIMService.class);

    @Autowired
    private AnalysisJobRepository analysisJobRepository;
    
    @Autowired
    private LayoutBlockRepository layoutBlockRepository;
    
    @Autowired
    private TextBlockRepository textBlockRepository;
    
    @Autowired
    private CIMOutputRepository cimOutputRepository;
    
    @Autowired
    private QuestionStructureRepository questionStructureRepository;
    
    @Autowired
    private AIQuestionMappingRepository aiQuestionMappingRepository;
    
    @Autowired
    private JsonUtils jsonUtils;

    // Python의 정규식 패턴들을 정확히 Java로 변환
    private static final List<Pattern> QUESTION_PATTERNS = Arrays.asList(
        Pattern.compile("(\\d+)번"),           // 1번, 2번
        Pattern.compile("(\\d+)\\."),          // 1., 2.
        Pattern.compile("문제\\s*(\\d+)"),       // 문제 1, 문제 2
        Pattern.compile("(\\d+)\\s*(?:\\)|）)"), // 1), 2)
        Pattern.compile("Q\\s*(\\d+)"),         // Q1, Q2
        Pattern.compile("(\\d{2,3})")          // 593, 594
    );
    
    private static final List<Pattern> SECTION_PATTERNS = Arrays.asList(
        Pattern.compile("([A-Z])\\s*섹션"),     // A섹션, B섹션
        Pattern.compile("([A-Z])\\s*부분"),     // A부분, B부분  
        Pattern.compile("([A-Z])\\s+")         // A, B
    );
    
    private static final List<Pattern> CHOICE_PATTERNS = Arrays.asList(
        Pattern.compile("^[①②③④⑤⑥⑦⑧⑨⑩]"),
        Pattern.compile("^[(（]\\s*[1-5]\\s*[)）]"),
        Pattern.compile("^[1-5]\\s*[.．]")
    );

    /**
     * 메인 메서드: Python의 generate_structured_json 완전 구현
     */
    public Map<String, Object> generateStructuredCIM(Long jobId) {
        logger.info("구조화된 CIM 생성 시작 - Job ID: {}", jobId);
        
        // 1. 기존 분석 결과 로드 (DB에서)
        AnalysisJob job = analysisJobRepository.findById(jobId)
            .orElseThrow(() -> CIMGenerationException.analysisJobNotFound(jobId));
        
        List<LayoutBlock> layoutBlocks = layoutBlockRepository.findByDocumentPageAnalysisJobOrderByY1Asc(job);
        List<TextBlock> textBlocks = textBlockRepository.findByLayoutBlockDocumentPageAnalysisJobOrderByLayoutBlockY1Asc(job);
        
        logger.info("로드된 데이터 - Layout 블록: {}, Text 블록: {}", layoutBlocks.size(), textBlocks.size());
        
        // 2. 문제 구조 분석 (Python의 detect_question_structure)
        QuestionStructureResult structure = detectQuestionStructure(textBlocks, layoutBlocks);
        
        // 3. AI 결과 문제별 분류는 나중에 구현 (현재는 빈 맵 반환)
        Map<String, List<String>> aiByQuestion = new HashMap<>();
        
        // 4. 최종 구조화 결과 생성
        return buildStructuredResult(structure, aiByQuestion, layoutBlocks, textBlocks);
    }

    /**
     * Python의 detect_question_structure 구현
     */
    private QuestionStructureResult detectQuestionStructure(
            List<TextBlock> textBlocks, List<LayoutBlock> layoutBlocks) {
        
        logger.info("문제 구조 분석 시작");
        
        // 1. 문제 번호 추출 (Python의 _extract_question_numbers)
        List<String> questionNumbers = extractQuestionNumbers(textBlocks);
        logger.info("감지된 문제 번호: {}", questionNumbers);
        
        // 2. 섹션 추출 (Python의 _extract_sections)  
        Map<String, SectionInfo> sections = extractSections(textBlocks);
        logger.info("감지된 섹션: {}", sections.keySet());
        
        // 3. 각 문제별 요소 그룹핑
        Map<String, QuestionData> questions = new HashMap<>();
        for (String qNum : questionNumbers) {
            QuestionData questionData = QuestionData.builder()
                .number(qNum)
                .section(findSectionForQuestion(qNum, sections))
                .elements(groupElementsByQuestion(qNum, textBlocks, layoutBlocks))
                .build();
            questions.put(qNum, questionData);
        }
        
        String layoutType = determineLayoutType(questions);
        logger.info("감지된 레이아웃 타입: {}", layoutType);
        
        return QuestionStructureResult.builder()
            .totalQuestions(questions.size())
            .sections(sections)
            .questions(questions)
            .layoutType(layoutType)
            .build();
    }

    /**
     * Python의 _extract_question_numbers 구현
     */
    private List<String> extractQuestionNumbers(List<TextBlock> textBlocks) {
        Set<String> questionNumbers = new LinkedHashSet<>();
        
        for (TextBlock textBlock : textBlocks) {
            String text = textBlock.getExtractedText();
            if (text == null || text.trim().isEmpty()) continue;
            
            for (Pattern pattern : QUESTION_PATTERNS) {
                var matcher = pattern.matcher(text);
                if (matcher.find()) {
                    String qNum = matcher.group(1);
                    if (qNum != null && !qNum.isEmpty()) {
                        questionNumbers.add(qNum);
                        logger.debug("문제 번호 감지: {} (텍스트: {})", qNum, text.substring(0, Math.min(30, text.length())));
                    }
                }
            }
        }
        
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
     * Python의 _extract_sections 구현
     */
    private Map<String, SectionInfo> extractSections(List<TextBlock> textBlocks) {
        Map<String, SectionInfo> sections = new HashMap<>();
        
        for (TextBlock textBlock : textBlocks) {
            String text = textBlock.getExtractedText();
            if (text == null || text.trim().isEmpty()) continue;
            
            for (Pattern pattern : SECTION_PATTERNS) {
                var matcher = pattern.matcher(text);
                if (matcher.find()) {
                    String sectionName = matcher.group(1);
                    if (sectionName != null && !sectionName.isEmpty()) {
                        SectionInfo sectionInfo = SectionInfo.builder()
                            .name(sectionName)
                            .startY(textBlock.getLayoutBlock().getY1())
                            .text(text)
                            .build();
                        sections.put(sectionName, sectionInfo);
                        logger.debug("섹션 감지: {} (Y좌표: {})", sectionName, textBlock.getLayoutBlock().getY1());
                    }
                }
            }
        }
        
        return sections;
    }

    /**
     * 문제에 해당하는 섹션 찾기
     */
    private String findSectionForQuestion(String questionNum, Map<String, SectionInfo> sections) {
        // 문제 번호에 따른 섹션 매핑 로직
        // 간단한 구현: 첫 번째 섹션 반환 (향후 개선 필요)
        return sections.keySet().stream().findFirst().orElse("기본");
    }

    /**
     * Python의 _group_elements_by_question 구현 (핵심 로직)
     */
    private Map<String, List<?>> groupElementsByQuestion(
            String questionNum, 
            List<TextBlock> textBlocks, 
            List<LayoutBlock> layoutBlocks) {
            
        logger.debug("문제 {}의 요소 그룹핑 시작", questionNum);
        
        // 문제 번호의 Y좌표 찾기
        Optional<Integer> questionY = findQuestionYPosition(questionNum, textBlocks);
        if (questionY.isEmpty()) {
            logger.warn("문제 {}의 Y좌표를 찾을 수 없습니다", questionNum);
            return createEmptyElementMap();
        }
        
        // 다음 문제의 Y좌표 찾기 (범위 설정)
        int nextQuestionY = findNextQuestionY(questionNum, textBlocks);
        
        logger.debug("문제 {} 범위: Y={} ~ {}", questionNum, questionY.get(), nextQuestionY);
        
        // Python과 동일한 6개 카테고리 초기화
        Map<String, List<?>> elements = new HashMap<>();
        elements.put("question_text", new ArrayList<TextBlock>());
        elements.put("passage", new ArrayList<TextBlock>());
        elements.put("images", new ArrayList<LayoutBlock>());
        elements.put("tables", new ArrayList<LayoutBlock>());
        elements.put("choices", new ArrayList<TextBlock>());
        elements.put("explanations", new ArrayList<TextBlock>());
        
        // Y좌표 범위 내의 텍스트 블록 분류
        textBlocks.stream()
            .filter(block -> isInQuestionRange(block, questionY.get(), nextQuestionY))
            .forEach(block -> {
                String elementType = classifyTextElement(block.getExtractedText());
                ((List<TextBlock>) elements.get(elementType)).add(block);
            });
            
        // Y좌표 범위 내의 레이아웃 블록 분류  
        layoutBlocks.stream()
            .filter(block -> isInQuestionRange(block, questionY.get(), nextQuestionY))
            .forEach(block -> {
                if ("figure".equals(block.getClassName())) {
                    ((List<LayoutBlock>) elements.get("images")).add(block);
                } else if ("table".equals(block.getClassName())) {
                    ((List<LayoutBlock>) elements.get("tables")).add(block);
                }
            });
        
        logger.debug("문제 {} 요소 분류 완료 - 이미지: {}, 테이블: {}, 선택지: {}", 
            questionNum, 
            ((List<LayoutBlock>) elements.get("images")).size(),
            ((List<LayoutBlock>) elements.get("tables")).size(),
            ((List<TextBlock>) elements.get("choices")).size());
            
        return elements;
    }

    /**
     * Python의 _classify_text_element 구현
     */
    private String classifyTextElement(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "question_text";
        }
        
        // 선택지 패턴 확인
        for (Pattern pattern : CHOICE_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return "choices";
            }
        }
        
        // 지문 패턴
        if (containsAny(text, "다음을", "아래의", "위의", "그림을", "표를")) {
            return "passage";
        }
        
        // 설명/해설 패턴
        if (containsAny(text, "설명", "해설", "풀이", "답:")) {
            return "explanations";
        }
        
        // 기본값
        return "question_text";
    }

    /**
     * 문제 번호의 Y좌표 찾기
     */
    private Optional<Integer> findQuestionYPosition(String questionNum, List<TextBlock> textBlocks) {
        for (TextBlock textBlock : textBlocks) {
            String text = textBlock.getExtractedText();
            if (text != null && text.contains(questionNum)) {
                for (Pattern pattern : QUESTION_PATTERNS) {
                    var matcher = pattern.matcher(text);
                    if (matcher.find() && questionNum.equals(matcher.group(1))) {
                        return Optional.of(textBlock.getLayoutBlock().getY1());
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 다음 문제의 Y좌표 찾기
     */
    private int findNextQuestionY(String currentQuestionNum, List<TextBlock> textBlocks) {
        try {
            int currentNum = Integer.parseInt(currentQuestionNum);
            String nextQuestionNum = String.valueOf(currentNum + 1);
            
            Optional<Integer> nextY = findQuestionYPosition(nextQuestionNum, textBlocks);
            if (nextY.isPresent()) {
                return nextY.get();
            }
        } catch (NumberFormatException e) {
            logger.debug("숫자가 아닌 문제 번호: {}", currentQuestionNum);
        }
        
        // 다음 문제가 없으면 매우 큰 값 반환 (문서 끝)
        return Integer.MAX_VALUE;
    }

    /**
     * Y좌표 범위 체크 (TextBlock용)
     */
    private boolean isInQuestionRange(TextBlock block, int startY, int endY) {
        return block.getLayoutBlock().getY1() >= startY && block.getLayoutBlock().getY1() < endY;
    }

    /**
     * Y좌표 범위 체크 (LayoutBlock용)
     */
    private boolean isInQuestionRange(LayoutBlock block, int startY, int endY) {
        return block.getY1() >= startY && block.getY1() < endY;
    }

    /**
     * 빈 요소 맵 생성
     */
    private Map<String, List<?>> createEmptyElementMap() {
        Map<String, List<?>> elements = new HashMap<>();
        elements.put("question_text", new ArrayList<>());
        elements.put("passage", new ArrayList<>());
        elements.put("images", new ArrayList<>());
        elements.put("tables", new ArrayList<>());
        elements.put("choices", new ArrayList<>());
        elements.put("explanations", new ArrayList<>());
        return elements;
    }

    /**
     * 텍스트에 특정 키워드들이 포함되어 있는지 확인
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 레이아웃 타입 결정
     */
    private String determineLayoutType(Map<String, QuestionData> questions) {
        if (questions.isEmpty()) return "empty";
        if (questions.size() == 1) return "simple";
        
        // 섹션이 있는지 확인
        boolean hasSections = questions.values().stream()
            .anyMatch(q -> q.getSection() != null && !q.getSection().equals("기본"));
        
        if (hasSections) return "sectioned";
        
        // 선택지가 있는지 확인
        boolean hasChoices = questions.values().stream()
            .anyMatch(q -> {
                List<TextBlock> choices = (List<TextBlock>) q.getElements().get("choices");
                return choices != null && !choices.isEmpty();
            });
        
        if (hasChoices) return "multiple_choice";
        
        return "standard";
    }

    /**
     * 최종 구조화 결과 생성
     */
    private Map<String, Object> buildStructuredResult(
            QuestionStructureResult structure, 
            Map<String, List<String>> aiByQuestion,
            List<LayoutBlock> layoutBlocks, 
            List<TextBlock> textBlocks) {
        
        Map<String, Object> result = new HashMap<>();
        
        // 문서 정보
        Map<String, Object> documentInfo = new HashMap<>();
        documentInfo.put("total_questions", structure.getTotalQuestions());
        documentInfo.put("layout_type", structure.getLayoutType());
        documentInfo.put("total_elements", layoutBlocks.size());
        documentInfo.put("total_text_blocks", textBlocks.size());
        documentInfo.put("sections", structure.getSections());
        
        result.put("document_info", documentInfo);
        
        // 문제별 구조화 결과
        Map<String, Object> structuredQuestions = new HashMap<>();
        for (Map.Entry<String, QuestionData> entry : structure.getQuestions().entrySet()) {
            String questionNum = entry.getKey();
            QuestionData questionData = entry.getValue();
            
            Map<String, Object> questionResult = new HashMap<>();
            questionResult.put("number", questionNum);
            questionResult.put("section", questionData.getSection());
            questionResult.put("elements", questionData.getElements());
            questionResult.put("ai_descriptions", aiByQuestion.getOrDefault(questionNum, new ArrayList<>()));
            
            structuredQuestions.put("question_" + questionNum, questionResult);
        }
        
        result.put("questions", structuredQuestions);
        
        logger.info("구조화된 결과 생성 완료 - 총 {}개 문제", structure.getTotalQuestions());
        return result;
    }

    // 내부 데이터 클래스들
    public static class QuestionStructureResult {
        private int totalQuestions;
        private Map<String, SectionInfo> sections;
        private Map<String, QuestionData> questions;
        private String layoutType;

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private QuestionStructureResult result = new QuestionStructureResult();

            public Builder totalQuestions(int totalQuestions) {
                result.totalQuestions = totalQuestions;
                return this;
            }

            public Builder sections(Map<String, SectionInfo> sections) {
                result.sections = sections;
                return this;
            }

            public Builder questions(Map<String, QuestionData> questions) {
                result.questions = questions;
                return this;
            }

            public Builder layoutType(String layoutType) {
                result.layoutType = layoutType;
                return this;
            }

            public QuestionStructureResult build() {
                return result;
            }
        }

        // Getters
        public int getTotalQuestions() { return totalQuestions; }
        public Map<String, SectionInfo> getSections() { return sections; }
        public Map<String, QuestionData> getQuestions() { return questions; }
        public String getLayoutType() { return layoutType; }
    }

    public static class SectionInfo {
        private String name;
        private Integer startY;
        private String text;

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private SectionInfo sectionInfo = new SectionInfo();

            public Builder name(String name) {
                sectionInfo.name = name;
                return this;
            }

            public Builder startY(Integer startY) {
                sectionInfo.startY = startY;
                return this;
            }

            public Builder text(String text) {
                sectionInfo.text = text;
                return this;
            }

            public SectionInfo build() {
                return sectionInfo;
            }
        }

        // Getters
        public String getName() { return name; }
        public Integer getStartY() { return startY; }
        public String getText() { return text; }
    }

    public static class QuestionData {
        private String number;
        private String section;
        private Map<String, List<?>> elements;

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private QuestionData questionData = new QuestionData();

            public Builder number(String number) {
                questionData.number = number;
                return this;
            }

            public Builder section(String section) {
                questionData.section = section;
                return this;
            }

            public Builder elements(Map<String, List<?>> elements) {
                questionData.elements = elements;
                return this;
            }

            public QuestionData build() {
                return questionData;
            }
        }

        // Getters
        public String getNumber() { return number; }
        public String getSection() { return section; }
        public Map<String, List<?>> getElements() { return elements; }
    }

    /**
     * 구조화된 텍스트 생성 (Python의 create_structured_text)
     */
    public String createStructuredText(Map<String, Object> structuredResult) {
        StringBuilder text = new StringBuilder();
        
        Map<String, Object> documentInfo = (Map<String, Object>) structuredResult.get("document_info");
        if (documentInfo != null) {
            text.append("=== 문서 정보 ===\n");
            text.append("총 문제 수: ").append(documentInfo.get("total_questions")).append("\n");
            text.append("레이아웃 타입: ").append(documentInfo.get("layout_type")).append("\n");
            text.append("총 요소 수: ").append(documentInfo.get("total_elements")).append("\n");
            text.append("\n");
        }
        
        Map<String, Object> questions = (Map<String, Object>) structuredResult.get("questions");
        if (questions != null) {
            for (Map.Entry<String, Object> entry : questions.entrySet()) {
                Map<String, Object> questionData = (Map<String, Object>) entry.getValue();
                String questionNum = (String) questionData.get("number");
                
                text.append("=== 문제 ").append(questionNum).append(" ===\n");
                text.append("섹션: ").append(questionData.get("section")).append("\n");
                
                Map<String, Object> elements = (Map<String, Object>) questionData.get("elements");
                if (elements != null) {
                    for (Map.Entry<String, Object> elementEntry : elements.entrySet()) {
                        String elementType = elementEntry.getKey();
                        List<?> elementList = (List<?>) elementEntry.getValue();
                        if (elementList != null && !elementList.isEmpty()) {
                            text.append(elementType).append(" (").append(elementList.size()).append("개)\n");
                        }
                    }
                }
                text.append("\n");
            }
        }
        
        return text.toString();
    }

    /**
     * 작업 ID로 CIM Output 조회
     */
    public CIMOutput findByJobId(Long jobId) {
        return cimOutputRepository.findByAnalysisJobId(jobId)
            .orElseThrow(() -> CIMGenerationException.analysisJobNotFound(jobId));
    }

    /**
     * 다중 페이지 CIM 결과 통합 (기본 구현)
     */
    public Map<String, Object> integrateCIMResults(java.util.List<Long> jobIds) {
        logger.info("다중 페이지 CIM 통합 시작 - Job IDs: {}", jobIds);
        
        Map<String, Object> integratedResult = new HashMap<>();
        Map<String, Object> documentInfo = new HashMap<>();
        Map<String, Object> allQuestions = new HashMap<>();
        
        int totalQuestions = 0;
        int totalElements = 0;
        int totalTextBlocks = 0;
        
        for (Long jobId : jobIds) {
            try {
                Map<String, Object> singleResult = generateStructuredCIM(jobId);
                Map<String, Object> singleDocInfo = (Map<String, Object>) singleResult.get("document_info");
                Map<String, Object> singleQuestions = (Map<String, Object>) singleResult.get("questions");
                
                if (singleDocInfo != null) {
                    totalQuestions += (Integer) singleDocInfo.getOrDefault("total_questions", 0);
                    totalElements += (Integer) singleDocInfo.getOrDefault("total_elements", 0);
                    totalTextBlocks += (Integer) singleDocInfo.getOrDefault("total_text_blocks", 0);
                }
                
                if (singleQuestions != null) {
                    // 문제 번호에 페이지 정보 추가하여 중복 방지
                    for (Map.Entry<String, Object> entry : singleQuestions.entrySet()) {
                        String questionKey = "page_" + jobId + "_" + entry.getKey();
                        allQuestions.put(questionKey, entry.getValue());
                    }
                }
                
            } catch (Exception e) {
                logger.warn("페이지 통합 중 오류 - Job ID: {}, 오류: {}", jobId, e.getMessage());
            }
        }
        
        // 통합된 문서 정보
        documentInfo.put("total_questions", totalQuestions);
        documentInfo.put("layout_type", "integrated");
        documentInfo.put("total_elements", totalElements);
        documentInfo.put("total_text_blocks", totalTextBlocks);
        documentInfo.put("page_count", jobIds.size());
        documentInfo.put("integration_method", "sequential");
        
        integratedResult.put("document_info", documentInfo);
        integratedResult.put("questions", allQuestions);
        
        logger.info("다중 페이지 CIM 통합 완료 - 총 문제: {}, 페이지: {}", totalQuestions, jobIds.size());
        return integratedResult;
    }

    /**
     * 구조화된 분석 결과 저장 (데이터베이스에)
     */
    @Transactional
    public void saveStructuredResult(Long jobId, Map<String, Object> structuredResult) {
        try {
            CIMOutput cimOutput = findByJobId(jobId);
            
            // JSON으로 저장
            cimOutput.setStructuredDataJson(jsonUtils.toJson(structuredResult));
            cimOutput.setStructuredText(createStructuredText(structuredResult));
            
            // 메타데이터 추출 및 저장
            Map<String, Object> documentInfo = (Map<String, Object>) structuredResult.get("document_info");
            if (documentInfo != null) {
                cimOutput.setTotalQuestions((Integer) documentInfo.get("total_questions"));
                cimOutput.setLayoutType((String) documentInfo.get("layout_type"));
            }
            
            cimOutput.setGenerationStatus(CIMOutput.GenerationStatus.COMPLETED);
            cimOutputRepository.save(cimOutput);
            
            logger.info("구조화된 결과 저장 완료 - Job ID: {}", jobId);
            
        } catch (Exception e) {
            logger.error("구조화된 결과 저장 실패 - Job ID: {}, 오류: {}", jobId, e.getMessage(), e);
            throw CIMGenerationException.databaseError(e);
        }
    }

    /**
     * 문제 구조 정보를 별도 테이블에 저장
     */
    @Transactional
    public void saveQuestionStructures(Long jobId, QuestionStructureResult structureResult) {
        try {
            CIMOutput cimOutput = findByJobId(jobId);
            
            // 기존 구조 삭제
            questionStructureRepository.deleteByCimOutput(cimOutput);
            
            // 새 구조 저장
            for (Map.Entry<String, QuestionData> entry : structureResult.getQuestions().entrySet()) {
                String questionNum = entry.getKey();
                QuestionData questionData = entry.getValue();
                
                QuestionStructure structure = new QuestionStructure(questionNum, questionData.getSection(), cimOutput);
                // Map<String, List<?>>를 Map<String, Object>로 변환
                Map<String, Object> elementsMap = new HashMap<>();
                questionData.getElements().forEach((key, value) -> elementsMap.put(key, value));
                structure.setElementsMap(elementsMap);
                
                // 통계 정보 계산
                List<TextBlock> choices = (List<TextBlock>) questionData.getElements().get("choices");
                List<LayoutBlock> images = (List<LayoutBlock>) questionData.getElements().get("images");
                List<LayoutBlock> tables = (List<LayoutBlock>) questionData.getElements().get("tables");
                
                structure.setChoicesCount(choices != null ? choices.size() : 0);
                structure.setImagesCount(images != null ? images.size() : 0);
                structure.setTablesCount(tables != null ? tables.size() : 0);
                
                questionStructureRepository.save(structure);
            }
            
            logger.info("문제 구조 저장 완료 - Job ID: {}, 문제 수: {}", jobId, structureResult.getTotalQuestions());
            
        } catch (Exception e) {
            logger.error("문제 구조 저장 실패 - Job ID: {}, 오류: {}", jobId, e.getMessage(), e);
            throw CIMGenerationException.databaseError(e);
        }
    }
}