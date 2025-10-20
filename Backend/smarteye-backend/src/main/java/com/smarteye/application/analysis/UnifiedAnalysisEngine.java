package com.smarteye.application.analysis;

import com.smarteye.presentation.dto.AIDescriptionResult;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.application.analysis.engine.ElementClassifier;
import com.smarteye.application.analysis.engine.PatternMatchingEngine;
import com.smarteye.application.analysis.engine.PureDistance2DAnalyzer;
import com.smarteye.application.analysis.dto.QuestionBoundary;
import com.smarteye.application.analysis.engine.validation.ContextValidationEngine;
import com.smarteye.application.analysis.engine.validation.ValidationResult;
import com.smarteye.application.analysis.engine.correction.IntelligentCorrectionEngine;
import com.smarteye.application.analysis.engine.correction.CorrectedAssignment;
import com.smarteye.application.analysis.engine.correction.CorrectionResult;
import com.smarteye.application.analysis.engine.correction.ReassignmentResult;
import com.smarteye.application.analysis.dto.QuestionContentDTO;
import com.smarteye.domain.layout.LayoutClass;
import com.smarteye.application.analysis.engine.content.ContentGenerationStrategy;
import com.smarteye.application.analysis.finder.BoundaryElementFinder;
import com.smarteye.application.analysis.finder.BoundaryElementFinderFactory;
import com.smarteye.shared.constants.QuestionTypeConstants;
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
import java.util.regex.Pattern;
import java.util.regex.Matcher;
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
    private ElementClassifier elementClassifier;

    /**
     * ⚠️ v2.0 - 순수 2D 거리 방식: QuestionBoundaryDetector
     * <p>QuestionNumberExtractor를 대체하여 문제 경계(X, Y 좌표) 추출</p>
     */
    @Autowired
    private QuestionBoundaryDetector questionBoundaryDetector;

    /**
     * ⚠️ v2.0 - 순수 2D 거리 방식: PureDistance2DAnalyzer
     * <p>Spatial2DAnalyzer를 대체하여 컬럼 필터링 없이 순수 2D 유클리드 거리 계산</p>
     */
    @Autowired
    private PureDistance2DAnalyzer pureDistance2DAnalyzer;

    @Autowired
    private ContextValidationEngine contextValidationEngine;

    @Autowired
    private IntelligentCorrectionEngine intelligentCorrectionEngine;

    /**
     * BoundaryElementFinder 팩토리 (Strategy Pattern)
     * <p>question_number 및 question_type(type_*) 요소를 찾는 전략을 제공합니다.</p>
     */
    @Autowired
    private BoundaryElementFinderFactory finderFactory;

    /**
     * ContentGenerationStrategy 구현체를 LayoutClass별로 매핑
     * <p>Spring이 자동으로 모든 ContentGenerationStrategy 구현체를 주입하고,
     * 각 전략이 지원하는 LayoutClass와 매핑합니다.</p>
     */
    private final Map<LayoutClass, ContentGenerationStrategy> contentStrategies;

    /**
     * Constructor Injection으로 ContentGenerationStrategy 주입
     * <p>Spring이 VisualContentStrategy, TextContentStrategy를 자동 주입</p>
     */
    @Autowired
    public UnifiedAnalysisEngine(List<ContentGenerationStrategy> strategies) {
        // LayoutClass별로 전략 매핑 (각 전략이 supports() 메서드로 지원 여부 판단)
        this.contentStrategies = Arrays.stream(LayoutClass.values())
            .collect(Collectors.toMap(
                layoutClass -> layoutClass,
                layoutClass -> strategies.stream()
                    .filter(strategy -> strategy.supports(layoutClass))
                    .max(Comparator.comparingInt(ContentGenerationStrategy::getPriority))
                    .orElse(null)
            ));

        long visualStrategies = contentStrategies.values().stream()
            .filter(s -> s != null && s.getPriority() == 9)
            .count();
        long textStrategies = contentStrategies.values().stream()
            .filter(s -> s != null && s.getPriority() == 8)
            .count();

        logger.info("✅ ContentGenerationStrategy 초기화 완료: 시각 {}개, 텍스트 {}개, 총 매핑 {}개",
                   visualStrategies, textStrategies, contentStrategies.size());
    }

    /**
     * 통합 분석 실행 - 모든 서비스의 핵심 기능을 하나로 통합
     * <p>P2 로깅 강화: 각 Phase별 처리 시간 및 통계 로깅</p>
     */
    public UnifiedAnalysisResult performUnifiedAnalysis(
            List<LayoutInfo> layoutElements,
            List<OCRResult> ocrResults,
            List<AIDescriptionResult> aiResults) {

        long startTime = System.currentTimeMillis();
        logger.info("🔄 통합 분석 시작 - 레이아웃: {}개, OCR: {}개, AI: {}개",
                   layoutElements.size(), ocrResults.size(), aiResults.size());

        try {
            // 1. 문제 구조 감지 (문제 경계 추출) - ⚠️ v2.0 순수 2D 거리 방식
            long phase1Start = System.currentTimeMillis();
            List<QuestionBoundary> questionBoundaries = questionBoundaryDetector.extractBoundaries(
                layoutElements, ocrResults
            );
            long phase1Time = System.currentTimeMillis() - phase1Start;
            logger.info("✅ Phase 1 완료: 감지된 문제 경계 {}개 (처리시간: {}ms)", questionBoundaries.size(), phase1Time);

            // 2. 요소 분류 및 문제에 할당
            long groupingStart = System.currentTimeMillis();
            Map<String, List<AnalysisElement>> elementsByQuestion = groupElementsByQuestion(
                layoutElements, ocrResults, aiResults, questionBoundaries
            );
            long groupingTime = System.currentTimeMillis() - groupingStart;

            int totalElements = elementsByQuestion.values().stream()
                .mapToInt(List::size)
                .sum();
            logger.info("📊 요소 그룹핑 완료: {}개 문제, 총 {}개 요소 (처리시간: {}ms)",
                       elementsByQuestion.size(), totalElements, groupingTime);

            // DEBUG: 문제별 요소 수 로깅
            elementsByQuestion.forEach((questionNum, elements) -> {
                if (!"unknown".equals(questionNum)) {
                    logger.debug("  - 문제 {}: {}개 요소", questionNum, elements.size());
                }
            });

            // 2.5. PHASE 2: 컨텍스트 검증 (v0.7)
            long phase2Start = System.currentTimeMillis();
            logger.info("📋 Phase 2 시작: 컨텍스트 검증 (문제 {}개)", elementsByQuestion.size());
            List<QuestionStructure> questionStructures = convertToQuestionStructures(elementsByQuestion);

            ValidationResult validationResult = contextValidationEngine.validateContext(questionStructures);
            long phase2Time = System.currentTimeMillis() - phase2Start;
            
            int sequenceGaps = validationResult.getSequenceGaps().size();
            int rangeConflicts = validationResult.getRangeConflicts().size();
            logger.info("✅ Phase 2 완료: 연속성 Gap {}개, 공간 충돌 {}개 (처리시간: {}ms)",
                       sequenceGaps, rangeConflicts, phase2Time);

            // PHASE 3: 지능형 교정 (v0.7 완성)
            long phase3Start = System.currentTimeMillis();
            CorrectedAssignment correctedAssignment =
                    intelligentCorrectionEngine.correct(elementsByQuestion, validationResult);
            long phase3Time = System.currentTimeMillis() - phase3Start;

            CorrectionResult corrResult = correctedAssignment.getCorrectionResult();
            ReassignmentResult reassignResult = correctedAssignment.getReassignmentResult();
            
            int ocrCorrections = corrResult != null ? corrResult.getOcrCorrections().size() : 0;
            int reassignments = reassignResult != null ? reassignResult.getReassignments().size() : 0;
            
            logger.info("✅ Phase 3 완료: OCR 교정 {}개, 재할당 {}개 (처리시간: {}ms)",
                       ocrCorrections, reassignments, phase3Time);

            // DEBUG: 교정 내역 로깅
            if (ocrCorrections > 0 || reassignments > 0) {
                logger.debug("  📋 교정 상세:");
                
                if (corrResult != null && ocrCorrections > 0) {
                    corrResult.getOcrCorrections().forEach((wrong, correct) -> {
                        logger.debug("    • OCR: {}번 → {}번", wrong, correct);
                    });
                }
                
                if (reassignResult != null && reassignments > 0) {
                    reassignResult.getReassignments().forEach((elementId, newQuestion) -> {
                        logger.debug("    • 재할당: {} → {}", elementId, newQuestion);
                    });
                }
            }

            // 교정된 할당 맵 사용 (교정이 없으면 원본 유지)
            elementsByQuestion = correctedAssignment.getAssignments();
            logger.info("✅ Phase 2-4 전체 완료: 최종 문제 수={}", elementsByQuestion.size());

            // 3. 구조화된 데이터 생성 (layoutElements, ocrResults 전달)
            long structuredStart = System.currentTimeMillis();
            StructuredData structuredData = generateStructuredData(elementsByQuestion, layoutElements, ocrResults);
            long structuredTime = System.currentTimeMillis() - structuredStart;
            logger.info("🏗️ 구조화된 데이터 생성 완료: 문제 {}개 (처리시간: {}ms)",
                       structuredData.getQuestions().size(), structuredTime);

            // 4. CIM 형식으로 변환
            long cimStart = System.currentTimeMillis();
            Map<String, Object> cimData = convertToCIMFormat(structuredData);
            long cimTime = System.currentTimeMillis() - cimStart;
            logger.info("🔄 CIM 형식 변환 완료 (처리시간: {}ms)", cimTime);

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("✅ 통합 분석 완료 (총 처리시간: {}ms, Phase1: {}ms, 그룹핑: {}ms, Phase2: {}ms, Phase3: {}ms, 구조화: {}ms, CIM: {}ms)",
                       processingTime, phase1Time, groupingTime, phase2Time, phase3Time, structuredTime, cimTime);

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
     * 모든 요소를 문제별로 그룹핑 (⚠️ v2.0 순수 2D 거리 방식)
     *
     * <p>컬럼 감지 제거: QuestionBoundary의 X, Y 좌표를 직접 사용하여 순수 2D 유클리드 거리 계산</p>
     * <p>방향성 가중치 및 적응형 임계값 적용</p>
     */
    private Map<String, List<AnalysisElement>> groupElementsByQuestion(
            List<LayoutInfo> layoutElements,
            List<OCRResult> ocrResults,
            List<AIDescriptionResult> aiResults,
            List<QuestionBoundary> questionBoundaries) {

        Map<String, List<AnalysisElement>> groupedElements = new HashMap<>();
        Map<Integer, OCRResult> ocrMap = ocrResults.stream().collect(Collectors.toMap(OCRResult::getId, ocr -> ocr, (a, b) -> a));
        Map<Integer, AIDescriptionResult> aiMap = aiResults.stream().collect(Collectors.toMap(AIDescriptionResult::getId, ai -> ai, (a, b) -> a));

        logger.debug("🔧 순수 2D 거리 분석 시작: 문제 경계 {}개", questionBoundaries.size());

        for (LayoutInfo layout : layoutElements) {
            int elementX = layout.getBox()[0];  // x1
            int elementY = layout.getBox()[1];  // y1
            int elementX2 = layout.getBox()[2]; // x2
            int elementY2 = layout.getBox()[3]; // y2

            // P0 수정 3: 요소 면적 계산 및 대형 요소 판단
            int elementWidth = elementX2 - elementX;
            int elementHeight = elementY2 - elementY;

            // ⚠️ v2.0: PureDistance2DAnalyzer의 isLargeElement() 사용
            boolean isLargeElement = pureDistance2DAnalyzer.isLargeElement(elementWidth, elementHeight);

            if (isLargeElement) {
                logger.trace("📏 대형 요소 감지: 크기={}x{}px", elementWidth, elementHeight);
            }

            // ⚠️ v2.0: 순수 2D 유클리드 거리 계산 (컬럼 필터링 없음)
            String assignedQuestion = pureDistance2DAnalyzer.findNearestQuestion(
                elementX, elementY, questionBoundaries, isLargeElement
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
     * 🔧 하위 문항 패턴 (괄호 숫자)
     */
    private static final Pattern SUB_QUESTION_PATTERN = Pattern.compile(
        "^\\s*\\((\\d+)\\)\\s*",
        Pattern.MULTILINE
    );

    /**
     * 🔧 강화된 구조화된 데이터 생성 (간소화 + 소문제 계층 구조 지원)
     * 
     * v0.5: LAM v2.0 대응 - second_question_number 지원
     * v0.6-p0-fix5: QUESTION_TYPE, UNIT 메타데이터 추가
     * 
     * @param elementsByQuestion 문제별 요소 맵
     * @param layoutElements 전체 레이아웃 요소 (메타데이터 추출용)
     * @param ocrResults 전체 OCR 결과 (텍스트 추출용)
     */
    private StructuredData generateStructuredData(
            Map<String, List<AnalysisElement>> elementsByQuestion,
            List<LayoutInfo> layoutElements,
            List<OCRResult> ocrResults) {
        
        logger.info("=== 📊 CIM 구조화된 데이터 생성 시작 (총 {} 개 그룹) ===", elementsByQuestion.size());
        
        // 🆕 QUESTION_TYPE, UNIT 메타데이터 추출
        Map<String, String> questionTypeMetadata = extractQuestionTypeMetadata(layoutElements, ocrResults);
        Map<String, String> unitMetadata = extractUnitMetadata(layoutElements, ocrResults);
        
        logger.info("📌 메타데이터 추출 완료: question_type={}개, unit={}개",
                   questionTypeMetadata.size(), unitMetadata.size());
        
        StructuredData structuredData = new StructuredData();
        DocumentInfo docInfo = new DocumentInfo();

        // 유효한 문제 수 계산 ("unknown" 제외)
        long validQuestions = elementsByQuestion.keySet().stream()
            .filter(k -> !"unknown".equals(k) && !"header".equals(k))
            .filter(k -> !isSubQuestionNumber(k))  // 🆕 하위 문항 제외
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
        Map<String, List<QuestionData>> subQuestionsByMain = new LinkedHashMap<>();
        
        for (Map.Entry<String, List<AnalysisElement>> entry : elementsByQuestion.entrySet()) {
            String questionNumber = entry.getKey();
            List<AnalysisElement> elements = entry.getValue();
            
            logger.debug("━━━ 문제 {} 처리 중 (요소 수: {}) ━━━", questionNumber, elements.size());
            
            // unknown, header 그룹 제외
            if ("unknown".equals(questionNumber) || "header".equals(questionNumber)) {
                logger.debug("그룹 건너뜀: {}", questionNumber);
                continue;
            }
            
            // 🆕 하위 문항 그룹 제외 (예: (1), (2))
            if (isSubQuestionNumber(questionNumber)) {
                logger.debug("🔗 하위 문항 그룹 건너뜀: {}", questionNumber);
                continue;
            }
            
            // 빈 문제 번호 방어
            if (questionNumber == null || questionNumber.trim().isEmpty()) {
                logger.error("❌ 문제 번호가 null 또는 빈 문자열 - 건너뜀");
                continue;
            }
            
            // 요소 상세 로깅
            logger.debug("📦 요소 목록:");
            for (int i = 0; i < elements.size(); i++) {
                AnalysisElement elem = elements.get(i);
                String className = elem.getLayoutInfo() != null ? 
                    elem.getLayoutInfo().getClassName() : "null";
                boolean hasOCR = elem.getOcrResult() != null && 
                    elem.getOcrResult().getText() != null;
                boolean hasAI = elem.getAiResult() != null && 
                    elem.getAiResult().getDescription() != null;
                
                logger.debug("  [{}] class={}, hasOCR={}, hasAI={}", 
                    i + 1, className, hasOCR, hasAI);
                
                if (hasOCR) {
                    logger.debug("      OCR: \"{}\" ({} chars)", 
                        elem.getOcrResult().getText().substring(
                            0, Math.min(50, elem.getOcrResult().getText().length())
                        ), 
                        elem.getOcrResult().getText().length());
                }
                if (hasAI) {
                    logger.debug("      AI: \"{}\" ({} chars)", 
                        elem.getAiResult().getDescription().substring(
                            0, Math.min(50, elem.getAiResult().getDescription().length())
                        ), 
                        elem.getAiResult().getDescription().length());
                }
            }

            // 🆕 v3.0 Step 1: 메타데이터 추출 (문제별)
            String questionType = extractMetadataFromElements(elements, "question_type");
            String unit = extractMetadataFromElements(elements, "unit");
            
            // 전역 메타데이터 폴백
            if (questionType == null) {
                questionType = questionTypeMetadata.get("global");
            }
            if (unit == null) {
                unit = unitMetadata.get("global");
            }
            
            // 🆕 v3.0 Step 2: Y좌표 기반 기본 정렬
            List<AnalysisElement> sortedElements = new ArrayList<>(elements);
            sortedElements.sort(Comparator
                .<AnalysisElement>comparingInt(e -> e.getLayoutInfo().getBox()[1])  // Y좌표
                .thenComparingInt(e -> e.getLayoutInfo().getBox()[0])  // X좌표
            );
            
            logger.debug("� 요소 정렬 완료: {} 개", sortedElements.size());
            
            // 🆕 v3.0 Step 3: ContentElement 리스트 생성
            List<ContentElement> contentElements = buildElements(sortedElements);
            
            logger.info("📝 ContentElement: {} 개 생성 (원본 {} → 필터링 후 {})", 
                contentElements.size(), sortedElements.size(), contentElements.size());
            
            // 빈 문제 제외
            if (contentElements.isEmpty()) {
                logger.warn("⚠️ 빈 문제 제외: {}", questionNumber);
                continue;
            }

            // ✅ v0.5: 간소화된 콘텐츠 생성 (하위 호환성 - deprecated)
            Map<String, String> simplifiedContent = convertToLegacyFormat(contentElements);
            
            logger.debug("📝 레거시 콘텐츠: {} 개 필드 (하위호환)", simplifiedContent.size());
            for (Map.Entry<String, String> contentEntry : simplifiedContent.entrySet()) {
                logger.debug("  - {}: {} chars", 
                    contentEntry.getKey(), contentEntry.getValue().length());
            }

            // 🆕 Phase 2: 하위 문항 그룹핑 (second_question_number 지원)
            Map<String, Map<String, String>> subQuestions = groupSubQuestions(questionNumber, elements);

            QuestionData qd = new QuestionData();
            qd.setQuestionNumber(questionNumber);  // ✅ String으로 직접 설정
            
            // 🆕 v3.0: contentElements 설정 (메인 구조)
            qd.setContentElements(contentElements);
            
            // 🆕 v3.0: 메타데이터 설정 (문제별 우선, 전역 폴백)
            if (questionType != null && !questionType.isEmpty()) {
                qd.setQuestionType(questionType);
                logger.debug("📌 문제 {} - question_type: '{}'", questionNumber, questionType);
            }
            
            if (unit != null && !unit.isEmpty()) {
                qd.setUnit(unit);
                logger.debug("📌 문제 {} - unit: '{}'", questionNumber, unit);
            }
            
            // ✅ v0.5: 간소화된 콘텐츠 설정 (하위 호환성 - deprecated)
            qd.setQuestionContentSimplified(simplifiedContent);
            
            // 하위 호환성: questionText 설정
            qd.setQuestionText(simplifiedContent.getOrDefault("question_text", ""));
            
            // 🆕 Phase 2: 하위 문항이 있으면 설정
            if (!subQuestions.isEmpty()) {
                List<QuestionData> subQuestionList = new ArrayList<>();
                
                for (Map.Entry<String, Map<String, String>> subEntry : subQuestions.entrySet()) {
                    String subNumber = subEntry.getKey();
                    Map<String, String> subContent = subEntry.getValue();
                    
                    QuestionData subQuestion = new QuestionData();
                    subQuestion.setQuestionNumber(subNumber);
                    subQuestion.setQuestionContentSimplified(subContent);
                    subQuestion.setQuestionText(subContent.getOrDefault("question_text", ""));
                    
                    subQuestionList.add(subQuestion);
                    logger.debug("  📌 하위 문항 추가: {} (필드 수: {})", subNumber, subContent.size());
                }
                
                qd.setSubQuestions(subQuestionList);
                logger.info("✅ 메인 문제 {} - 하위 문항 {}개 포함", 
                    questionNumber, subQuestionList.size());
            }
            
            // 🆕 v3.0: 메타데이터 생성 (확장)
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("total_elements", contentElements.size());
            metadata.put("original_elements", sortedElements.size());
            metadata.put("filtered_elements", sortedElements.size() - contentElements.size());
            metadata.put("field_count", simplifiedContent.size());
            
            // 타입별 요소 개수
            Map<String, Long> elementsByType = contentElements.stream()
                .collect(Collectors.groupingBy(
                    ContentElement::getType, 
                    Collectors.counting()
                ));
            metadata.put("elements_by_type", elementsByType);
            
            if (!subQuestions.isEmpty()) {
                metadata.put("sub_questions_count", subQuestions.size());
            }
            qd.setMetadata(metadata);
            
            // 소문제 분류
            if (questionNumber.contains("-")) {
                // 004-1 → 메인: 004
                String mainNumber = questionNumber.substring(0, questionNumber.indexOf("-"));
                subQuestionsByMain
                    .computeIfAbsent(mainNumber, k -> new ArrayList<>())
                    .add(qd);
                
                logger.debug("🔗 소문제 분류: {} → 메인: {}", questionNumber, mainNumber);
            } else {
                // 메인 문제
                questionDataList.add(qd);
                logger.info("✅ 메인 문제 생성: {} ({} 개 요소, 타입: {})", 
                    questionNumber, contentElements.size(), elementsByType.keySet());
            }
        }
        
        // 소문제를 메인 문제에 병합
        for (QuestionData mainQuestion : questionDataList) {
            String mainNumber = mainQuestion.getQuestionNumber();
            List<QuestionData> subQuestions = subQuestionsByMain.get(mainNumber);
            
            if (subQuestions != null && !subQuestions.isEmpty()) {
                // 소문제 번호 순서로 정렬
                subQuestions.sort(Comparator.comparing(QuestionData::getQuestionNumber));
                mainQuestion.setSubQuestions(subQuestions);
                
                logger.debug("소문제 병합: {} → {} 개", mainNumber, subQuestions.size());
            }
        }

        // 문제 번호순 정렬 (자연 정렬)
        questionDataList.sort(Comparator.comparing(QuestionData::getQuestionNumber));
        structuredData.setQuestions(questionDataList);

        // 🆕 v3.0: 전체 통계 로깅
        int totalContentElements = questionDataList.stream()
            .mapToInt(q -> q.getContentElements() != null ? q.getContentElements().size() : 0)
            .sum();
        
        logger.info("🏗️ v3.0 구조화 데이터 생성 완료: 메인 문제 {}개, 총 콘텐츠 요소 {}개 (원본 {}개에서 필터링)",
                   questionDataList.size(), totalContentElements, totalElements);

        return structuredData;
    }
    
    /**
     * 🆕 하위 문항 번호 판단 (괄호 숫자)
     */
    private boolean isSubQuestionNumber(String questionNumber) {
        if (questionNumber == null || questionNumber.trim().isEmpty()) {
            return false;
        }
        
        // (1), (2), (3) 등의 패턴 매칭
        return SUB_QUESTION_PATTERN.matcher(questionNumber.trim()).matches();
    }
    
    /**
     * 🆕 Phase 2: 하위 문항 그룹핑 (LAM 클래스 기반)
     * 
     * 현재 LAM 모델: second_question_number 클래스 자동 인식
     * 
     * @param mainQuestionNumber 메인 문제 번호
     * @param elements 메인 문제에 속한 모든 요소
     * @return 하위 문항 번호 → 콘텐츠 맵
     */
    private Map<String, Map<String, String>> groupSubQuestions(
        String mainQuestionNumber,
        List<AnalysisElement> elements
    ) {
        Map<String, List<AnalysisElement>> subQuestionElements = new LinkedHashMap<>();
        List<AnalysisElement> remainingElements = new ArrayList<>();
        
        logger.debug("  🔍 하위 문항 그룹핑 시작: 문제 {} (요소 수: {})", 
            mainQuestionNumber, elements.size());
        
        for (AnalysisElement element : elements) {
            String className = element.getLayoutInfo() != null ? 
                element.getLayoutInfo().getClassName() : null;
            
            boolean isSubQuestion = false;
            String subNumber = null;
            
            // 🆕 우선순위 1: second_question_number 클래스 (LAM 모델이 명시적으로 감지)
            if ("second_question_number".equals(className)) {
                String ocrText = element.getOcrResult() != null ? 
                    element.getOcrResult().getText() : null;
                
                if (ocrText != null) {
                    // v0.7 P1 Fix: 전각 문자 정규화 (한국어 학습지 대응)
                    String normalizedOCR = QuestionTypeConstants.normalizeFullWidthCharacters(ocrText);
                    
                    // v0.7 P0 Fix: 첫 번째 연속 숫자만 추출 (연속 번호 "(1)(2)" → "12" 방지)
                    Matcher numberMatcher = Pattern.compile("([0-9]+)").matcher(normalizedOCR);
                    if (numberMatcher.find()) {
                        subNumber = numberMatcher.group(1);
                        isSubQuestion = true;
                        logger.debug("    📌 하위 문항 감지 (second_question_number): {}", subNumber);
                        
                        // 연속 번호 경고 (예: "(1)(2)" 패턴 감지)
                        if (normalizedOCR.matches(".*\\([0-9]+\\).*\\([0-9]+\\).*")) {
                            logger.warn("⚠️ 연속 하위 문항 감지됨: '{}' - 첫 번째 번호만 사용: {}", 
                                       ocrText, subNumber);
                        }
                    } else {
                        logger.warn("⚠️ second_question_number OCR에서 숫자 추출 실패: '{}'", ocrText);
                    }
                }
            }
            
            // 🔧 우선순위 2: question_number 클래스 (Fallback - 현재 LAM 모델)
            else if ("question_number".equals(className)) {
                String ocrText = element.getOcrResult() != null ? 
                    element.getOcrResult().getText() : null;
                
                if (ocrText != null) {
                    Matcher matcher = SUB_QUESTION_PATTERN.matcher(ocrText.trim());
                    if (matcher.find()) {
                        subNumber = matcher.group(1);
                        isSubQuestion = true;
                        logger.debug("    📌 하위 문항 감지 (question_number): ({})", subNumber);
                    }
                }
            }
            
            if (isSubQuestion && subNumber != null) {
                subQuestionElements.computeIfAbsent(subNumber, k -> new ArrayList<>())
                    .add(element);
            } else {
                remainingElements.add(element);
            }
        }
        
        // 하위 문항별로 콘텐츠 생성
        Map<String, Map<String, String>> subQuestions = new LinkedHashMap<>();
        
        for (Map.Entry<String, List<AnalysisElement>> entry : subQuestionElements.entrySet()) {
            String subNumber = entry.getKey();
            List<AnalysisElement> subElements = entry.getValue();
            
            // 간소화된 콘텐츠 생성
            Map<String, String> subContent = buildSimplifiedQuestionContent(subElements);
            
            if (!subContent.isEmpty()) {
                subQuestions.put(subNumber, subContent);
                logger.debug("    ✅ 하위 문항 ({}) 콘텐츠 생성: {} 필드", 
                    subNumber, subContent.size());
            }
        }
        
        logger.debug("  🔍 하위 문항 그룹핑 완료: {}개 하위 문항 감지", subQuestions.size());
        
        return subQuestions;
    }
    
    /**
     * ✅ v0.5: 간소화된 문제 콘텐츠 생성 (텍스트/설명만, 메타데이터 제외)
     * 
     * 프론트엔드 TextEditor에서 편집하기 쉽도록 단순한 구조로 반환
     */
    private Map<String, String> buildSimplifiedQuestionContent(List<AnalysisElement> elements) {
        logger.debug("┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓");
        logger.debug("┃  buildSimplifiedQuestionContent 시작: {} 개 요소  ┃", elements.size());
        logger.debug("┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
        
        Map<String, String> content = new LinkedHashMap<>();
        Map<String, List<String>> textsByClass = new LinkedHashMap<>();
        
        int processedCount = 0;
        int skippedCount = 0;
        int extractedTextCount = 0;
        
        for (int i = 0; i < elements.size(); i++) {
            AnalysisElement element = elements.get(i);
            logger.debug("  ┌─ 요소 [{}] ─────────────────────────────────", i + 1);
            
            if (element.getLayoutInfo() == null) {
                logger.debug("  │  ⚠️ layoutInfo == null → SKIP");
                logger.debug("  └───────────────────────────────────────────");
                skippedCount++;
                continue;
            }
            
            if (element.getLayoutInfo().getClassName() == null) {
                logger.debug("  │  ⚠️ className == null → SKIP");
                logger.debug("  └───────────────────────────────────────────");
                skippedCount++;
                continue;
            }
            
            String className = element.getLayoutInfo().getClassName();
            logger.debug("  │  className: {}", className);
            
            // question_number와 second_question_number는 제외 (questionNumber 필드에 있음)
            if ("question_number".equals(className) || "second_question_number".equals(className)) {
                logger.debug("  │  ⊘ question_number 계열 제외 → SKIP");
                logger.debug("  └───────────────────────────────────────────");
                skippedCount++;
                continue;
            }
            
            // 텍스트 추출
            logger.debug("  │  🔍 텍스트 추출 시도...");
            String text = extractSimpleText(element, className);
            
            if (text != null && !text.trim().isEmpty()) {
                logger.debug("  │  ✅ 추출 성공: {} chars", text.length());
                logger.debug("  │     내용: \"{}\"", 
                    text.length() > 50 ? text.substring(0, 50) + "..." : text);
                
                textsByClass
                    .computeIfAbsent(className, k -> new ArrayList<>())
                    .add(text.trim());
                
                extractedTextCount++;
                logger.debug("  └───────────────────────────────────────────");
            } else {
                logger.debug("  │  ❌ 추출 실패: text == null or empty");
                logger.debug("  └───────────────────────────────────────────");
            }
            
            processedCount++;
        }
        
        logger.debug("");
        logger.debug("📊 요소 처리 통계:");
        logger.debug("   - 총 요소: {}", elements.size());
        logger.debug("   - 처리됨: {}", processedCount);
        logger.debug("   - 건너뜀: {}", skippedCount);
        logger.debug("   - 텍스트 추출: {}", extractedTextCount);
        logger.debug("   - 클래스별 그룹: {}", textsByClass.size());
        logger.debug("");
        
        // 클래스별로 텍스트 결합
        logger.debug("🔗 클래스별 텍스트 결합:");
        for (Map.Entry<String, List<String>> entry : textsByClass.entrySet()) {
            String className = entry.getKey();
            List<String> texts = entry.getValue();
            
            logger.debug("   - {}: {} 개 텍스트", className, texts.size());
            
            // 공백으로 결합
            String combinedText = String.join(" ", texts);
            content.put(className, combinedText);
            
            logger.debug("     → 결합 결과: {} chars", combinedText.length());
        }
        
        logger.debug("");
        logger.debug("✅ buildSimplifiedQuestionContent 완료: {} 개 필드 반환", content.size());
        logger.debug("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        return content;
    }
    
    /**
     * ✅ v0.5: 간단한 텍스트 추출 (좌표/메타데이터 제외)
     */
    private String extractSimpleText(AnalysisElement element, String className) {
        logger.trace("      ▶ extractSimpleText: className={}", className);
        
        // 시각 요소: AI 설명만
        if (isVisualElement(className)) {
            logger.trace("        → 시각 요소로 판단 (AI 설명 우선)");
            
            if (element.getAiResult() != null) {
                logger.trace("        → aiResult != null");
                if (element.getAiResult().getDescription() != null) {
                    String desc = element.getAiResult().getDescription();
                    logger.trace("        → AI 설명 추출: {} chars", desc.length());
                    return desc;
                } else {
                    logger.trace("        → AI 설명 == null");
                }
            } else {
                logger.trace("        → aiResult == null");
            }
            
            logger.trace("        → 빈 문자열 반환 (AI 설명 없음)");
            return "";
        }
        
        // 텍스트 요소: OCR 텍스트만
        logger.trace("        → 텍스트 요소로 판단 (OCR 우선)");
        
        if (element.getOcrResult() != null) {
            logger.trace("        → ocrResult != null");
            if (element.getOcrResult().getText() != null) {
                String text = element.getOcrResult().getText();
                logger.trace("        → OCR 텍스트 추출: {} chars", text.length());
                return text;
            } else {
                logger.trace("        → OCR 텍스트 == null");
            }
        } else {
            logger.trace("        → ocrResult == null");
        }
        
        logger.trace("        → 빈 문자열 반환 (OCR 텍스트 없음)");
        return "";
    }
    
    /**
     * ✅ v0.5: 시각 요소 판단
     */
    private boolean isVisualElement(String className) {
        return className != null && (
            className.equals("figure") ||
            className.equals("table") ||
            className.equals("chart") ||
            className.equals("equation") ||
            className.equals("diagram")
        );
    }

    /**
     * 🆕 v3.0: 정렬된 요소들로부터 ContentElement 리스트 생성
     * 
     * 필터링 규칙:
     * - question_number, question_type, unit 메타데이터 클래스 제외
     * - second_question_number도 제외
     * - OCR 텍스트 또는 AI 설명이 있는 요소만 포함
     * 
     * @param sortedElements Y좌표 기반 정렬된 요소 리스트
     * @return ContentElement 리스트 (읽기 순서 보존)
     */
    private List<ContentElement> buildElements(List<AnalysisElement> sortedElements) {
        logger.debug("┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓");
        logger.debug("┃  buildElements 시작: {} 요소  ┃", sortedElements.size());
        logger.debug("┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
        
        List<ContentElement> elements = new ArrayList<>();
        int includedCount = 0;
        int metadataCount = 0;
        int emptyCount = 0;
        
        for (int i = 0; i < sortedElements.size(); i++) {
            AnalysisElement element = sortedElements.get(i);
            
            // Null 체크
            if (element.getLayoutInfo() == null || 
                element.getLayoutInfo().getClassName() == null) {
                logger.trace("  [{}] ⊘ layoutInfo 또는 className null", i + 1);
                emptyCount++;
                continue;
            }
            
            String className = element.getLayoutInfo().getClassName();
            
            // 1. 메타데이터 클래스 필터링
            if (isMetadataClass(className)) {
                logger.trace("  [{}] ⊘ 메타데이터 클래스: {}", i + 1, className);
                metadataCount++;
                continue;
            }
            
            // 2. 콘텐츠 추출 (OCR 또는 AI)
            String content = extractContentForElement(element, className);
            
            if (content == null || content.trim().isEmpty()) {
                logger.trace("  [{}] ⊘ 빈 콘텐츠: {}", i + 1, className);
                emptyCount++;
                continue;
            }
            
            // 3. ContentElement 생성
            ContentElement contentElement = new ContentElement(className, content);
            elements.add(contentElement);
            includedCount++;
            
            if (includedCount <= 10) {  // 처음 10개만 상세 로깅
                logger.debug("  [{}] ✅ {} = \"{}\"", 
                    includedCount, className, 
                    content.length() > 40 ? content.substring(0, 40) + "..." : content);
            }
        }
        
        logger.info("✅ buildElements 완료: 포함={}, 메타데이터={}, 빈콘텐츠={}, 총={}",
            includedCount, metadataCount, emptyCount, sortedElements.size());
        
        return elements;
    }

    /**
     * 🆕 v3.0: 메타데이터 클래스 판단
     * 
     * @param className 레이아웃 클래스명
     * @return true = 메타데이터 클래스, false = 콘텐츠 클래스
     */
    private boolean isMetadataClass(String className) {
        return "question_number".equals(className) ||
               "second_question_number".equals(className) ||
               "question_type".equals(className) ||
               "unit".equals(className);
    }

    /**
     * 🆕 v3.0: 요소로부터 콘텐츠 추출 (OCR 또는 AI)
     * 
     * 시각 요소(figure, table 등)는 AI 설명 우선,
     * 텍스트 요소는 OCR 텍스트 우선
     * 
     * @param element 분석 요소
     * @param className 레이아웃 클래스명
     * @return 추출된 콘텐츠 (없으면 null)
     */
    private String extractContentForElement(AnalysisElement element, String className) {
        // 시각 요소: AI 설명 우선
        if (isVisualElement(className)) {
            if (element.getAiResult() != null && 
                element.getAiResult().getDescription() != null) {
                return element.getAiResult().getDescription();
            }
            return null;
        }
        
        // 텍스트 요소: OCR 텍스트 우선
        if (element.getOcrResult() != null && 
            element.getOcrResult().getText() != null) {
            return element.getOcrResult().getText();
        }
        
        return null;
    }

    /**
     * 🆕 v3.0: 요소 리스트에서 특정 메타데이터 클래스의 텍스트 추출
     * 
     * @param elements 요소 리스트
     * @param metadataClassName 추출할 메타데이터 클래스명 (예: "question_type", "unit")
     * @return OCR 텍스트 (없으면 null)
     */
    private String extractMetadataFromElements(
            List<AnalysisElement> elements, 
            String metadataClassName) {
        
        for (AnalysisElement element : elements) {
            if (element.getLayoutInfo() == null) continue;
            
            String className = element.getLayoutInfo().getClassName();
            if (metadataClassName.equals(className)) {
                if (element.getOcrResult() != null && 
                    element.getOcrResult().getText() != null) {
                    String text = element.getOcrResult().getText().trim();
                    logger.trace("  📌 메타데이터: {}=\"{}\"", metadataClassName, text);
                    return text;
                }
            }
        }
        
        return null;
    }

    /**
     * 🆕 v3.0: ContentElement 리스트를 레거시 형식(Map)으로 변환
     * 
     * 하위 호환성을 위해 필요시 사용
     * 같은 className의 여러 요소를 공백으로 결합
     * 
     * @param contentElements ContentElement 리스트
     * @return Map<String, String> (className → 통합 콘텐츠)
     */
    private Map<String, String> convertToLegacyFormat(List<ContentElement> contentElements) {
        Map<String, List<String>> textsByClass = new LinkedHashMap<>();
        
        for (ContentElement ce : contentElements) {
            textsByClass.computeIfAbsent(ce.getType(), k -> new ArrayList<>())
                        .add(ce.getContent());
        }
        
        Map<String, String> legacyFormat = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : textsByClass.entrySet()) {
            String className = entry.getKey();
            List<String> texts = entry.getValue();
            String combinedText = String.join(" ", texts);
            legacyFormat.put(className, combinedText);
        }
        
        logger.debug("🔄 레거시 형식 변환: {} 개별요소 → {} 필드", 
            contentElements.size(), legacyFormat.size());
        
        return legacyFormat;
    }

    /**
     * 🔧 강화된 구조화된 데이터 생성 (questionText 추출 로직 추가)
     * @deprecated v0.5에서 간소화된 버전으로 대체됨
     */
    @Deprecated
    private StructuredData generateStructuredDataOld(Map<String, List<AnalysisElement>> elementsByQuestion) {
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
            qd.setQuestionNumber(entry.getKey());  // ✅ String으로 직접 설정 (Integer.parseInt 제거)

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
            
            // ✅ Phase 1: 요소별 상세 정보 생성
            List<ElementDetail> elementDetails = createElementDetails(entry.getValue());
            qd.setElementDetails(elementDetails);
            
            // ✅ 제안 A: 타입별로 세분화된 콘텐츠 구조 생성
            QuestionContentDTO questionContent = createQuestionContent(entry.getValue(), questionText);
            qd.setQuestionContent(questionContent);
            
            // 메타데이터 생성
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("total_elements", entry.getValue().size());
            metadata.put("text_count", entry.getValue().stream()
                .filter(e -> e.getOcrResult() != null && e.getOcrResult().getText() != null)
                .count());
            metadata.put("figure_count", entry.getValue().stream()
                .filter(e -> e.getLayoutInfo() != null && "figure".equals(e.getLayoutInfo().getClassName()))
                .count());
            metadata.put("ocr_count", entry.getValue().stream()
                .filter(e -> e.getOcrResult() != null && e.getOcrResult().getText() != null && !e.getOcrResult().getText().isBlank())
                .count());
            metadata.put("ai_description_count", entry.getValue().stream()
                .filter(e -> e.getAiResult() != null && e.getAiResult().getDescription() != null && !e.getAiResult().getDescription().isBlank())
                .count());
            qd.setMetadata(metadata);
            
            questionDataList.add(qd);

            logger.debug("✅ 문제 {}번: OCR={}자, AI={}자, 요소={}개, 상세={}개",
                        entry.getKey(),
                        questionText.length(),
                        qd.getAiDescription() != null ? qd.getAiDescription().length() : 0,
                        entry.getValue().size(),
                        elementDetails.size());
        }

        // 문제 번호순 정렬
        questionDataList.sort(Comparator.comparing(QuestionData::getQuestionNumber));
        structuredData.setQuestions(questionDataList);

        logger.info("🏗️ 구조화된 데이터 생성 완료: 문제 {}개, 총 요소 {}개",
                   questionDataList.size(), totalElements);

        return structuredData;
    }

    /**
     * ✅ Phase 1: AnalysisElement 리스트를 ElementDetail 리스트로 변환
     * OCR 텍스트와 AI 설명을 분리하여 JSON에 각각 제공
     */
    private List<ElementDetail> createElementDetails(List<AnalysisElement> elements) {
        List<ElementDetail> details = new ArrayList<>();
        
        for (int i = 0; i < elements.size(); i++) {
            AnalysisElement element = elements.get(i);
            ElementDetail detail = new ElementDetail();
            
            // Element ID 생성
            detail.setElementId("block_" + (element.getLayoutInfo() != null ? element.getLayoutInfo().getId() : i));
            
            // Type (layout class name)
            if (element.getLayoutInfo() != null) {
                detail.setType(element.getLayoutInfo().getClassName());
                
                // Bounding Box
                int[] box = element.getLayoutInfo().getBox();
                if (box != null && box.length == 4) {
                    detail.setBbox(new BoundingBox(box[0], box[1], box[2], box[3]));
                    
                    // Area 계산
                    int width = box[2] - box[0];
                    int height = box[3] - box[1];
                    detail.setArea(width * height);
                }
                
                // Confidence
                detail.setConfidence(element.getLayoutInfo().getConfidence());
            }
            
            // OCR 텍스트 (분리)
            if (element.getOcrResult() != null && element.getOcrResult().getText() != null) {
                String ocrText = element.getOcrResult().getText().trim();
                if (!ocrText.isEmpty()) {
                    detail.setOcrText(ocrText);
                }
            }
            
            // AI 설명 (분리)
            if (element.getAiResult() != null && element.getAiResult().getDescription() != null) {
                String aiDesc = element.getAiResult().getDescription().trim();
                if (!aiDesc.isEmpty()) {
                    detail.setAiDescription(aiDesc);
                }
            }
            
            details.add(detail);
        }
        
        return details;
    }

    /**
     * ✅ 제안 A: QuestionContentDTO 생성
     * OCR 결과와 AI 설명을 question_text에 병합하지 않고 타입별로 분리
     */
    private QuestionContentDTO createQuestionContent(List<AnalysisElement> elements, String extractedQuestionText) {
        QuestionContentDTO content = new QuestionContentDTO();
        
        // 1. 핵심 질문 텍스트 설정 (문제 번호 제거)
        if (extractedQuestionText != null && !extractedQuestionText.equals("문제 텍스트 없음")) {
            // 문제 번호 패턴 제거 (★001, □002 등)
            String cleanText = extractedQuestionText.replaceAll("^[★□●◆■▲]?\\s*\\d+[.)]?\\s*", "").trim();
            if (!cleanText.isEmpty()) {
                content.setQuestionText(cleanText);
            }
        }
        
        // 2. 타입별로 분류할 리스트 초기화
        List<String> plainText = new ArrayList<>();
        List<QuestionContentDTO.OcrResult> ocrResults = new ArrayList<>();
        List<QuestionContentDTO.AiDescription> aiDescriptions = new ArrayList<>();
        List<String> choices = new ArrayList<>();
        List<QuestionContentDTO.ImageDetail> images = new ArrayList<>();
        List<QuestionContentDTO.TableDetail> tables = new ArrayList<>();
        StringBuilder passageBuilder = new StringBuilder();
        
        // 3. 각 요소 분류
        for (AnalysisElement element : elements) {
            String type = element.getLayoutInfo() != null ? element.getLayoutInfo().getClassName() : "";
            String ocrText = element.getOcrResult() != null ? element.getOcrResult().getText() : null;
            String aiDesc = element.getAiResult() != null ? element.getAiResult().getDescription() : null;
            
            // Bounding Box 생성
            Map<String, Double> bbox = createBboxMap(element);
            String elementId = "block_" + (element.getLayoutInfo() != null ? element.getLayoutInfo().getId() : 0);
            
            // OCR 텍스트 처리 (question_text와 분리)
            if (ocrText != null && !ocrText.isBlank()) {
                // 선택지 패턴 확인
                if (isChoicePattern(ocrText)) {
                    choices.add(ocrText);
                }
                // 지문
                else if ("passage".equals(type)) {
                    if (passageBuilder.length() > 0) {
                        passageBuilder.append("\n");
                    }
                    passageBuilder.append(ocrText);
                }
                // 일반 텍스트
                else if ("text".equals(type) || "plain_text".equals(type)) {
                    plainText.add(ocrText);
                }
                // 기타 OCR 결과
                else {
                    QuestionContentDTO.OcrResult ocrResult = new QuestionContentDTO.OcrResult();
                    ocrResult.setText(ocrText);
                    ocrResult.setElementId(elementId);
                    ocrResult.setType(type);
                    ocrResult.setBbox(bbox);
                    ocrResult.setConfidence(element.getLayoutInfo() != null ? element.getLayoutInfo().getConfidence() : null);
                    ocrResults.add(ocrResult);
                }
            }
            
            // AI 설명 처리 (question_text와 분리)
            if (aiDesc != null && !aiDesc.isBlank()) {
                QuestionContentDTO.AiDescription aiDescription = new QuestionContentDTO.AiDescription();
                aiDescription.setDescription(aiDesc);
                aiDescription.setElementId(elementId);
                aiDescription.setElementType(type);
                aiDescription.setBbox(bbox);
                aiDescriptions.add(aiDescription);
                
                // 이미지/도형인 경우 images 배열에도 추가
                if ("figure".equals(type) || "image".equals(type)) {
                    QuestionContentDTO.ImageDetail imageDetail = new QuestionContentDTO.ImageDetail();
                    imageDetail.setElementId(elementId);
                    imageDetail.setDescription(aiDesc);
                    imageDetail.setBbox(bbox);
                    imageDetail.setConfidence(element.getLayoutInfo() != null ? element.getLayoutInfo().getConfidence() : null);
                    images.add(imageDetail);
                }
            }
            
            // 표 처리
            if ("table".equals(type)) {
                QuestionContentDTO.TableDetail tableDetail = new QuestionContentDTO.TableDetail();
                tableDetail.setElementId(elementId);
                tableDetail.setBbox(bbox);
                tableDetail.setData(new ArrayList<>());  // TODO: 표 데이터 파싱 추가
                tables.add(tableDetail);
            }
        }
        
        // 4. 빈 배열이 아닌 경우만 추가
        if (!plainText.isEmpty()) content.setPlainText(plainText);
        if (!ocrResults.isEmpty()) content.setOcrResults(ocrResults);
        if (!aiDescriptions.isEmpty()) content.setAiDescriptions(aiDescriptions);
        if (!choices.isEmpty()) content.setChoices(choices);
        if (!images.isEmpty()) content.setImages(images);
        if (!tables.isEmpty()) content.setTables(tables);
        if (passageBuilder.length() > 0) content.setPassage(passageBuilder.toString());
        
        return content;
    }
    
    /**
     * 선택지 패턴 확인
     */
    private boolean isChoicePattern(String text) {
        return text.matches("^[①②③④⑤⑥⑦⑧⑨⑩].*") || 
               text.matches("^\\d+[).)].*");
    }
    
    /**
     * Bounding Box 맵 생성
     */
    private Map<String, Double> createBboxMap(AnalysisElement element) {
        Map<String, Double> bbox = new LinkedHashMap<>();
        if (element.getLayoutInfo() != null && element.getLayoutInfo().getBox() != null) {
            int[] box = element.getLayoutInfo().getBox();
            if (box.length == 4) {
                bbox.put("x1", (double) box[0]);
                bbox.put("y1", (double) box[1]);
                bbox.put("x2", (double) box[2]);
                bbox.put("y2", (double) box[3]);
            }
        }
        return bbox;
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

                            // 레이아웃 정보에서 클래스명 추출 (fallback: PLAIN_TEXT Enum)
                            String className = analysisElement.getLayoutInfo() != null ?
                                analysisElement.getLayoutInfo().getClassName() : LayoutClass.PLAIN_TEXT.getClassName();
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

                // 질문 텍스트가 있으면 별도 요소로 추가 (Type-Safe Enum 사용)
                if (question.getQuestionText() != null && !question.getQuestionText().trim().isEmpty()) {
                    Map<String, Object> questionElement = new HashMap<>();
                    questionElement.put("id", elementId++);
                    questionElement.put("class", LayoutClass.QUESTION_TEXT.getClassName());
                    questionElement.put("text", question.getQuestionText());
                    questionElement.put("bbox", Arrays.asList(0, 0, 500, 100));
                    questionElement.put("confidence", 0.9);
                    questionElement.put("area", 50000);
                    elements.add(questionElement);
                }

                // 질문 번호 요소 추가 (Type-Safe Enum 사용)
                if (question.getQuestionNumber() != null) {
                    Map<String, Object> numberElement = new HashMap<>();
                    numberElement.put("id", elementId++);
                    numberElement.put("class", LayoutClass.QUESTION_NUMBER.getClassName());
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
                
                // ✅ Phase 1: elementDetails 추가 (요소별 상세 정보)
                if (qd.getElementDetails() != null && !qd.getElementDetails().isEmpty()) {
                    List<Map<String, Object>> elementsArray = new ArrayList<>();
                    
                    for (ElementDetail detail : qd.getElementDetails()) {
                        Map<String, Object> elem = new HashMap<>();
                        elem.put("element_id", detail.getElementId());
                        elem.put("type", detail.getType());
                        
                        // OCR 텍스트 (null이 아닌 경우만)
                        if (detail.getOcrText() != null) {
                            elem.put("ocr_text", detail.getOcrText());
                        }
                        
                        // AI 설명 (null이 아닌 경우만)
                        if (detail.getAiDescription() != null) {
                            elem.put("ai_description", detail.getAiDescription());
                        }
                        
                        // Bounding Box
                        if (detail.getBbox() != null) {
                            Map<String, Integer> bbox = new HashMap<>();
                            bbox.put("x1", detail.getBbox().getX1());
                            bbox.put("y1", detail.getBbox().getY1());
                            bbox.put("x2", detail.getBbox().getX2());
                            bbox.put("y2", detail.getBbox().getY2());
                            elem.put("bbox", bbox);
                        }
                        
                        // Confidence
                        if (detail.getConfidence() != null) {
                            elem.put("confidence", detail.getConfidence());
                        }
                        
                        // Area
                        if (detail.getArea() != null) {
                            elem.put("area", detail.getArea());
                        }
                        
                        elementsArray.add(elem);
                    }
                    
                    question.put("elements", elementsArray);
                } else {
                    // 기존 호환성: elements summary
                    Map<String, Object> elementsSummary = new HashMap<>();
                    if (qd.getElements() != null && qd.getElements().containsKey("main")) {
                        elementsSummary.put("main", qd.getElements().get("main").size());
                    }
                    question.put("elements", elementsSummary);
                }
                
                // Metadata 추가
                Map<String, Integer> questionMetadata = new HashMap<>();
                int totalElements = qd.getElementDetails() != null ? qd.getElementDetails().size() : 0;
                int textCount = 0;
                int figureCount = 0;
                
                if (qd.getElementDetails() != null) {
                    for (ElementDetail detail : qd.getElementDetails()) {
                        if (detail.getOcrText() != null && !detail.getOcrText().isEmpty()) {
                            textCount++;
                        }
                        if ("figure".equalsIgnoreCase(detail.getType()) || 
                            "table".equalsIgnoreCase(detail.getType())) {
                            figureCount++;
                        }
                    }
                }
                
                questionMetadata.put("total_elements", totalElements);
                questionMetadata.put("text_count", textCount);
                questionMetadata.put("figure_count", figureCount);
                question.put("metadata", questionMetadata);
                
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
     * 🔍 요소들로부터 문제 콘텐츠 추출 (v0.5 Phase 1: ContentGenerationStrategy 패턴 적용)
     *
     * <p><strong>개선 사항 (v0.5)</strong>:</p>
     * <ul>
     *   <li>✅ Strategy 패턴 적용: LayoutClass별로 적절한 전략 자동 선택</li>
     *   <li>✅ VisualContentStrategy: AI 설명 우선 추출 (figure, table, chart)</li>
     *   <li>✅ TextContentStrategy: OCR 텍스트 우선 추출 (question_text, plain_text)</li>
     *   <li>✅ 타입 안전성: LayoutClass Enum 활용</li>
     *   <li>✅ 확장성: 새 전략 추가 시 기존 코드 수정 불필요</li>
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

        // 1. LayoutClass별로 요소 그룹화 (null 키 방지)
        Map<LayoutClass, List<AnalysisElement>> elementsByClass = elements.stream()
            .filter(e -> e.getLayoutInfo() != null && e.getLayoutInfo().getClassName() != null)
            .filter(e -> LayoutClass.fromString(e.getLayoutInfo().getClassName()).isPresent()) // ✅ null 키 방지
            .collect(Collectors.groupingBy(
                e -> LayoutClass.fromString(e.getLayoutInfo().getClassName()).get() // ✅ get() 안전 (필터링으로 보장)
            ));

        logger.trace("📊 요소 그룹화 완료: {} 레이아웃 클래스, 총 {} 요소",
                    elementsByClass.size(), elements.size());

        // 2. 각 레이아웃 클래스에 대해 적절한 전략 적용
        for (Map.Entry<LayoutClass, List<AnalysisElement>> entry : elementsByClass.entrySet()) {
            LayoutClass layoutClass = entry.getKey();
            List<AnalysisElement> classElements = entry.getValue();

            // 전략 선택
            ContentGenerationStrategy strategy = contentStrategies.get(layoutClass);
            if (strategy == null) {
                logger.trace("⚠️ 전략 없음: layoutClass={} ({}개 요소 스킵)",
                            layoutClass.getClassName(), classElements.size());
                continue;
            }

            // 콘텐츠 생성
            String content = strategy.generateContent(classElements);
            if (content == null || content.isEmpty()) {
                continue;
            }

            // 시각 요소 vs 텍스트 요소 분류
            if (layoutClass.isVisual()) {
                // 시각 요소: AI 설명으로 추가
                aiDescriptions.add(content);
                logger.trace("🎨 시각 콘텐츠 추가: class={}, length={}자",
                            layoutClass.getClassName(), content.length());
            } else {
                // 텍스트 요소: question_text로 추가
                questionText.append(content).append(" ");
                logger.trace("📝 텍스트 콘텐츠 추가: class={}, length={}자",
                            layoutClass.getClassName(), content.length());
            }
        }

        // 3. 정리 및 로깅
        String finalQuestionText = questionText.toString().trim();

        if (finalQuestionText.isEmpty() && aiDescriptions.isEmpty()) {
            logger.warn("⚠️ OCR 텍스트와 AI 설명 모두 없음 (요소 {}개)", elements.size());
        } else {
            logger.debug("✅ 문제 콘텐츠 추출 완료 (Strategy 패턴): OCR {}자, AI 설명 {}개",
                        finalQuestionText.length(), aiDescriptions.size());
        }

        return Map.of(
            "question_text", finalQuestionText,
            "ai_descriptions", aiDescriptions
        );
    }

    // ============================================================================
    // 이전 헬퍼 메서드들 (v0.5에서 ContentGenerationStrategy로 대체됨)
    // ============================================================================

    /**
     * @deprecated v0.5에서 ContentGenerationStrategy로 대체됨
     * @see ContentGenerationStrategy
     * @see VisualContentStrategy
     * @see TextContentStrategy
     */
    @Deprecated
    private String extractAIDescription(AnalysisElement element) {
        // Strategy 패턴으로 대체되어 더 이상 사용되지 않음
        throw new UnsupportedOperationException(
            "이 메서드는 v0.5에서 ContentGenerationStrategy로 대체되었습니다.");
    }

    /**
     * @deprecated v0.5에서 LayoutClass.isVisual() 및 ContentGenerationStrategy로 대체됨
     * @see LayoutClass#isVisual()
     * @see LayoutClass#isOcrTarget()
     */
    @Deprecated
    private boolean isQuestionTextElement(AnalysisElement element) {
        // Strategy 패턴으로 대체되어 더 이상 사용되지 않음
        throw new UnsupportedOperationException(
            "이 메서드는 v0.5에서 LayoutClass Enum 메서드로 대체되었습니다.");
    }

    /**
     * @deprecated v0.5에서 ContentGenerationStrategy.extractContent()로 대체됨
     * @see ContentGenerationStrategy#extractContent(AnalysisElement)
     */
    @Deprecated
    private String extractCleanText(AnalysisElement element) {
        // Strategy 패턴으로 대체되어 더 이상 사용되지 않음
        throw new UnsupportedOperationException(
            "이 메서드는 v0.5에서 ContentGenerationStrategy로 대체되었습니다.");
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
        private String questionNumber;  // ✅ Integer → String 변경 (소문제 지원: "004-1")
        private String questionText;
        private String aiDescription;  // ✅ P1 개선: AI 설명 별도 필드 추가
        private Map<String, List<AnalysisElement>> elements;  // 기존 호환성 유지용
        private List<ElementDetail> elementDetails;  // ✅ Phase 1: 요소별 상세 정보
        
        /**
         * 🆕 v3.0: 간소화된 요소 리스트 (메인 구조)
         * 
         * 메타데이터 클래스 제외, OCR/AI 콘텐츠만 포함
         * 읽기 순서대로 배열된 개별 요소 리스트
         */
        private List<ContentElement> contentElements;
        
        /**
         * ✅ v0.5: 간소화된 콘텐츠 (텍스트/설명만, Map 형식)
         * 프론트엔드 편집 친화적 구조
         * 
         * @deprecated v3.0에서 contentElements로 대체
         * 하위 호환성을 위해 유지하며, 필요시 자동 생성
         */
        @Deprecated
        private Map<String, String> questionContentSimplified;
        
        /**
         * ✅ 제안 A: 타입별로 세분화된 콘텐츠 구조 (deprecated)
         * @deprecated v0.5에서 questionContentSimplified로 대체
         */
        @Deprecated
        private QuestionContentDTO questionContent;
        
        /**
         * ✅ v0.5: 소문제 리스트 (LAM v2.0 대응)
         * 메인 문제의 하위 소문제들을 계층 구조로 관리
         */
        private List<QuestionData> subQuestions;
        
        /**
         * 🆕 v0.6-p0-fix5: question_type 메타데이터
         * LAM이 감지한 "D형 번개기", "A형 기본" 등의 문제 유형 분류
         */
        private String questionType;
        
        /**
         * 🆕 v0.6-p0-fix5: unit 메타데이터
         * LAM이 감지한 "[1]", "[2]" 등의 단원 정보
         */
        private String unit;
        
        private Map<String, Object> metadata;  // 메타데이터

        // Getters and Setters
        public String getQuestionNumber() { return questionNumber; }
        public void setQuestionNumber(String questionNumber) { this.questionNumber = questionNumber; }
        public String getQuestionText() { return questionText; }
        public void setQuestionText(String questionText) { this.questionText = questionText; }
        public String getAiDescription() { return aiDescription; }
        public void setAiDescription(String aiDescription) { this.aiDescription = aiDescription; }
        public Map<String, List<AnalysisElement>> getElements() { return elements; }
        public void setElements(Map<String, List<AnalysisElement>> elements) { this.elements = elements; }
        public List<ElementDetail> getElementDetails() { return elementDetails; }
        public void setElementDetails(List<ElementDetail> elementDetails) { this.elementDetails = elementDetails; }
        
        /**
         * 🆕 v3.0: contentElements getter/setter
         */
        public List<ContentElement> getContentElements() { return contentElements; }
        public void setContentElements(List<ContentElement> contentElements) { 
            this.contentElements = contentElements; 
        }
        
        /**
         * @deprecated v3.0에서 contentElements로 대체
         */
        @Deprecated
        public Map<String, String> getQuestionContentSimplified() { return questionContentSimplified; }
        @Deprecated
        public void setQuestionContentSimplified(Map<String, String> questionContentSimplified) { 
            this.questionContentSimplified = questionContentSimplified; 
        }
        
        @Deprecated
        public QuestionContentDTO getQuestionContent() { return questionContent; }
        @Deprecated
        public void setQuestionContent(QuestionContentDTO questionContent) { this.questionContent = questionContent; }
        
        public List<QuestionData> getSubQuestions() { return subQuestions; }
        public void setSubQuestions(List<QuestionData> subQuestions) { this.subQuestions = subQuestions; }
        
        public String getQuestionType() { return questionType; }
        public void setQuestionType(String questionType) { this.questionType = questionType; }
        
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        
        /**
         * 소문제가 있는지 판단
         */
        @com.fasterxml.jackson.annotation.JsonIgnore
        public boolean hasSubQuestions() {
            return subQuestions != null && !subQuestions.isEmpty();
        }
        
        /**
         * 메인 문제인지 판단
         */
        @com.fasterxml.jackson.annotation.JsonIgnore
        public boolean isMainQuestion() {
            return questionNumber != null && !questionNumber.contains("-");
        }
    }

    /**
     * ✅ Phase 1: 요소별 상세 정보를 담는 DTO 클래스
     * JSON 응답에서 각 블록의 OCR, AI 설명, 좌표 등을 개별적으로 제공
     */
    public static class ElementDetail {
        private String elementId;
        private String type;          // layout class name
        private String ocrText;       // OCR 텍스트 (분리)
        private String aiDescription; // AI 설명 (분리)
        private BoundingBox bbox;     // 좌표 정보
        private Double confidence;    // 신뢰도
        private Integer area;         // 면적

        // Getters and Setters
        public String getElementId() { return elementId; }
        public void setElementId(String elementId) { this.elementId = elementId; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getOcrText() { return ocrText; }
        public void setOcrText(String ocrText) { this.ocrText = ocrText; }
        public String getAiDescription() { return aiDescription; }
        public void setAiDescription(String aiDescription) { this.aiDescription = aiDescription; }
        public BoundingBox getBbox() { return bbox; }
        public void setBbox(BoundingBox bbox) { this.bbox = bbox; }
        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }
        public Integer getArea() { return area; }
        public void setArea(Integer area) { this.area = area; }
    }

    /**
     * ✅ Phase 1: Bounding Box 좌표 정보
     */
    public static class BoundingBox {
        private Integer x1;
        private Integer y1;
        private Integer x2;
        private Integer y2;

        public BoundingBox() {}
        
        public BoundingBox(Integer x1, Integer y1, Integer x2, Integer y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        // Getters and Setters
        public Integer getX1() { return x1; }
        public void setX1(Integer x1) { this.x1 = x1; }
        public Integer getY1() { return y1; }
        public void setY1(Integer y1) { this.y1 = y1; }
        public Integer getX2() { return x2; }
        public void setX2(Integer x2) { this.x2 = x2; }
        public Integer getY2() { return y2; }
        public void setY2(Integer y2) { this.y2 = y2; }
    }

    /**
     * 🆕 v3.0: 간소화된 콘텐츠 요소 (읽기 순서 보존)
     * 
     * 메타데이터 클래스(question_number, question_type, unit)를 제외하고,
     * OCR 텍스트 또는 AI 설명이 있는 콘텐츠만 포함합니다.
     * 
     * 특징:
     * - 개별 요소 보존 (통합 안 됨)
     * - 읽기 순서대로 배열
     * - type(className) + content(텍스트/설명)만 포함
     * 
     * @since v3.0
     */
    public static class ContentElement {
        private String type;        // className (text, figure, table 등)
        private String content;     // OCR 텍스트 또는 AI 설명
        
        public ContentElement() {}
        
        public ContentElement(String type, String content) {
            this.type = type;
            this.content = content;
        }
        
        // Getters/Setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    /**
     * 🆕 v0.6-p0-fix5: QUESTION_TYPE 메타데이터 추출
     * 
     * LAM이 감지한 question_type 요소를 찾아서, 공간적으로 가장 가까운 문제에 할당
     * 
     * @param layoutElements 전체 레이아웃 요소
     * @param ocrResults 전체 OCR 결과
     * @return 문제 번호 → question_type 텍스트 맵
     */
    private Map<String, String> extractQuestionTypeMetadata(
            List<LayoutInfo> layoutElements,
            List<OCRResult> ocrResults) {
        
        Map<String, String> questionTypeMap = new HashMap<>();
        
        // OCR 결과를 ID로 매핑
        Map<Integer, OCRResult> ocrMap = ocrResults.stream()
            .collect(Collectors.toMap(OCRResult::getId, ocr -> ocr));
        
        for (LayoutInfo layout : layoutElements) {
            // question_type 클래스 필터링
            Optional<LayoutClass> layoutClass = LayoutClass.fromString(layout.getClassName());
            if (layoutClass.isEmpty() || layoutClass.get() != LayoutClass.QUESTION_TYPE) {
                continue;
            }
            
            // OCR 텍스트 추출
            OCRResult ocr = ocrMap.get(layout.getId());
            if (ocr == null || ocr.getText() == null || ocr.getText().trim().isEmpty()) {
                logger.warn("⚠️ question_type 요소 (id={})에 OCR 텍스트 없음", layout.getId());
                continue;
            }
            
            String typeText = ocr.getText().trim();
            logger.info("📌 question_type 감지: '{}' (LAM conf={}, 위치: x={}, y={})",
                       typeText,
                       String.format("%.3f", layout.getConfidence()),
                       layout.getBox()[0],
                       layout.getBox()[1]);
            
            // TODO: 공간적으로 가장 가까운 문제에 할당
            // 현재는 첫 번째 question_type만 저장 (단일 유형 문서 가정)
            if (questionTypeMap.isEmpty()) {
                questionTypeMap.put("global", typeText);
            }
        }
        
        return questionTypeMap;
    }

    /**
     * 🆕 v0.6-p0-fix5: UNIT 메타데이터 추출
     * 
     * LAM이 감지한 unit 요소를 찾아서, 공간적으로 가장 가까운 문제에 할당
     * 
     * @param layoutElements 전체 레이아웃 요소
     * @param ocrResults 전체 OCR 결과
     * @return 문제 번호 → unit 텍스트 맵
     */
    private Map<String, String> extractUnitMetadata(
            List<LayoutInfo> layoutElements,
            List<OCRResult> ocrResults) {
        
        Map<String, String> unitMap = new HashMap<>();
        
        // OCR 결과를 ID로 매핑
        Map<Integer, OCRResult> ocrMap = ocrResults.stream()
            .collect(Collectors.toMap(OCRResult::getId, ocr -> ocr));
        
        for (LayoutInfo layout : layoutElements) {
            // unit 클래스 필터링
            Optional<LayoutClass> layoutClass = LayoutClass.fromString(layout.getClassName());
            if (layoutClass.isEmpty() || layoutClass.get() != LayoutClass.UNIT) {
                continue;
            }
            
            // OCR 텍스트 추출
            OCRResult ocr = ocrMap.get(layout.getId());
            if (ocr == null || ocr.getText() == null || ocr.getText().trim().isEmpty()) {
                logger.warn("⚠️ unit 요소 (id={})에 OCR 텍스트 없음", layout.getId());
                continue;
            }
            
            String unitText = ocr.getText().trim();
            logger.info("📌 unit 감지: '{}' (LAM conf={}, 위치: x={}, y={})",
                       unitText,
                       String.format("%.3f", layout.getConfidence()),
                       layout.getBox()[0],
                       layout.getBox()[1]);
            
            // TODO: 공간적으로 가장 가까운 문제에 할당
            // 현재는 첫 번째 unit만 저장 (단일 단원 문서 가정)
            if (unitMap.isEmpty()) {
                unitMap.put("global", unitText);
            }
        }
        
        return unitMap;
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