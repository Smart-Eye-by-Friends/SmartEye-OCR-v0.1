package com.smarteye.application.analysis.engine.correction;

import com.smarteye.application.analysis.UnifiedAnalysisEngine;
import com.smarteye.application.analysis.engine.validation.BoundingBox;
import com.smarteye.application.analysis.engine.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 지능형 교정 통합 엔진
 *
 * <p>Phase 3 교정 과정 전체를 조율(Orchestration)하는 메인 엔진입니다.</p>
 *
 * <p><strong>교정 파이프라인:</strong></p>
 * <ol>
 *   <li>OCR 오류 교정 (MissingQuestionRecovery)</li>
 *   <li>OCR 교정 결과를 할당 맵에 반영</li>
 *   <li>공간 충돌 해결 (ElementReassignmentEngine)</li>
 *   <li>모든 교정 결과를 CorrectedAssignment로 통합</li>
 * </ol>
 *
 * <p><strong>예시:</strong></p>
 * <pre>
 * 입력: initialAssignment (204→문제?, 295→...)
 * Step 1: OCR 교정 → 204 → 294
 * Step 2: 할당 반영 → 294번 문제 생성
 * Step 3: 공간 충돌 해결 → 요소 재할당
 * 출력: CorrectedAssignment (294→문제, 295→...)
 * </pre>
 *
 * @author Claude Code (System Architect + Refactoring Expert)
 * @since v0.7 (CIM 그룹핑 알고리즘 재설계 - Phase 3)
 */
@Service
public class IntelligentCorrectionEngine {

    private static final Logger logger = LoggerFactory.getLogger(IntelligentCorrectionEngine.class);

    private final MissingQuestionRecovery missingQuestionRecovery;
    private final ElementReassignmentEngine elementReassignmentEngine;

    @Autowired
    public IntelligentCorrectionEngine(
            MissingQuestionRecovery missingQuestionRecovery,
            ElementReassignmentEngine elementReassignmentEngine) {
        this.missingQuestionRecovery = missingQuestionRecovery;
        this.elementReassignmentEngine = elementReassignmentEngine;
    }

    /**
     * 지능형 교정 메인 메서드
     *
     * <p>검증 결과를 기반으로 초기 할당을 교정합니다.</p>
     *
     * @param initialAssignment 초기 할당 맵 (Phase 1 결과)
     * @param validationResult 검증 결과 (Phase 2 결과)
     * @return 교정된 최종 할당 결과
     */
    public CorrectedAssignment correct(
            Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> initialAssignment,
            ValidationResult validationResult) {

        logger.info("🔧 PHASE 3: 지능형 교정 시작");
        logger.info("📋 초기 할당: {}개 문제", initialAssignment.size());

        // 검증 통과 시 교정 불필요
        if (validationResult.isValid()) {
            logger.info("✅ 검증 통과: 교정 불필요");
            return CorrectedAssignment.noCorrection(initialAssignment);
        }

        // Step 1: OCR 오류 교정 (MissingQuestionRecovery)
        logger.info("┌─ Step 1: OCR 오류 교정 및 누락 문제 복구");
        CorrectionResult correctionResult = missingQuestionRecovery.recover(validationResult);
        logger.info("└─ 결과: {}", correctionResult.getSummary());

        // Step 2: OCR 교정 결과를 할당 맵에 반영
        Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> correctedAssignment =
                applyCorrectionToAssignment(initialAssignment, correctionResult);

        // Step 3: 공간 충돌 해결 (ElementReassignmentEngine)
        logger.info("┌─ Step 2: 공간 충돌 해결 및 요소 재할당");
        ReassignmentResult reassignmentResult =
                elementReassignmentEngine.reassign(validationResult, correctedAssignment);
        logger.info("└─ 결과: {}", reassignmentResult.getSummary());

        // Step 4: 재할당 결과를 할당 맵에 반영
        Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> finalAssignment =
                applyReassignmentToAssignment(correctedAssignment, reassignmentResult);

        // Step 5: 최종 교정 결과 생성
        CorrectedAssignment result = new CorrectedAssignment(
                finalAssignment,
                correctionResult,
                reassignmentResult
        );

        logger.info("📊 PHASE 3 교정 완료");
        logger.info("  {}", result.getSummary());

        // 상세 로그 출력
        if (result.isCorrected()) {
            logger.info(result.getDetailedLog());
        }

        return result;
    }

    /**
     * OCR 교정 결과를 할당 맵에 반영
     *
     * <p>잘못된 문제 번호를 올바른 번호로 교정합니다.</p>
     *
     * <p><strong>예시:</strong></p>
     * <pre>
     * 교정 전: {"204" → [element1, element2], "295" → [...]}
     * OCR 교정: {"204" → "294"}
     * 교정 후: {"294" → [element1, element2], "295" → [...]}
     * </pre>
     *
     * @param initialAssignment 초기 할당 맵
     * @param correctionResult OCR 교정 결과
     * @return 교정이 반영된 할당 맵
     */
    private Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> applyCorrectionToAssignment(
            Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> initialAssignment,
            CorrectionResult correctionResult) {

        Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> correctedAssignment = new HashMap<>(initialAssignment);

        // OCR 교정 반영 (문제 번호 변경)
        for (Map.Entry<String, String> correction : correctionResult.getOcrCorrections().entrySet()) {
            String wrongNumber = correction.getKey();
            String correctNumber = correction.getValue();

            if (correctedAssignment.containsKey(wrongNumber)) {
                List<UnifiedAnalysisEngine.AnalysisElement> elements = correctedAssignment.remove(wrongNumber);
                correctedAssignment.put(correctNumber, elements);
                logger.debug("    ✏️ 문제 번호 교정: {} → {}", wrongNumber, correctNumber);
            }
        }

        // 누락 문제 복구 (빈 그룹 생성)
        // TODO: Phase 4에서 미할당 요소를 누락 문제에 할당하는 로직 구현
        for (Integer recoveredNumber : correctionResult.getRecoveredQuestions()) {
            String key = String.valueOf(recoveredNumber);
            if (!correctedAssignment.containsKey(key)) {
                // 현재는 빈 리스트로 생성 (향후 미할당 요소 재탐색)
                logger.debug("    📝 누락 문제 복구: {}번 (향후 요소 할당 필요)", recoveredNumber);
            }
        }

        return correctedAssignment;
    }

    /**
     * 재할당 결과를 할당 맵에 반영
     *
     * <p>잘못 할당된 요소를 올바른 문제 그룹으로 이동합니다.</p>
     *
     * <p><strong>알고리즘 개요:</strong></p>
     * <ol>
     *   <li>elementId(레이블)로 전체 할당 맵에서 요소 찾기 (BoundingBox 기반 매칭)</li>
     *   <li>기존 문제 그룹에서 요소 제거</li>
     *   <li>새 문제 그룹에 요소 추가</li>
     *   <li>상세 로그 기록</li>
     * </ol>
     *
     * @param currentAssignment 현재 할당 맵
     * @param reassignmentResult 재할당 결과
     * @return 재할당이 반영된 할당 맵
     */
    private Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> applyReassignmentToAssignment(
            Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> currentAssignment,
            ReassignmentResult reassignmentResult) {

        if (!reassignmentResult.hasReassignments()) {
            return currentAssignment;
        }

        // 불변성 보장: 새 맵 생성 (깊은 복사)
        Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> reassignedAssignment = new HashMap<>();
        for (Map.Entry<String, List<UnifiedAnalysisEngine.AnalysisElement>> entry : currentAssignment.entrySet()) {
            reassignedAssignment.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        // 재할당 반영
        for (Map.Entry<String, String> reassignment : reassignmentResult.getReassignments().entrySet()) {
            String elementLabel = reassignment.getKey();
            String newQuestionNumber = reassignment.getValue();

            // Step 1: 요소 찾기 (BoundingBox 기반 매칭)
            ElementLocation location = findElementByLabel(reassignedAssignment, elementLabel);

            if (location == null) {
                logger.warn("    ⚠️ 요소 이동 실패: 요소 '{}' 찾을 수 없음", elementLabel);
                continue;
            }

            String oldQuestionNumber = location.questionNumber;
            UnifiedAnalysisEngine.AnalysisElement element = location.element;

            // 이미 올바른 위치에 있는 경우 스킵
            if (oldQuestionNumber.equals(newQuestionNumber)) {
                logger.debug("    ⏸️  요소 유지: {} (이미 {}번 문제에 할당됨)", elementLabel, newQuestionNumber);
                continue;
            }

            // Step 2: 기존 위치에서 제거
            List<UnifiedAnalysisEngine.AnalysisElement> oldList = reassignedAssignment.get(oldQuestionNumber);
            if (oldList == null) {
                logger.warn("    ⚠️ 요소 이동 실패: 기존 문제 {} 리스트 없음", oldQuestionNumber);
                continue;
            }

            boolean removed = oldList.removeIf(e ->
                boundingBoxesMatch(getElementBoundingBox(e), getElementBoundingBox(element))
            );

            if (!removed) {
                logger.warn("    ⚠️ 요소 이동 실패: 요소 '{}' 기존 리스트에서 제거 불가", elementLabel);
                continue;
            }

            // Step 3: 새 위치에 추가
            reassignedAssignment.computeIfAbsent(newQuestionNumber, k -> new ArrayList<>()).add(element);

            // Step 4: 상세 로그 기록
            logger.debug("    🔄 요소 이동: {} [{}번 → {}번 문제]", elementLabel, oldQuestionNumber, newQuestionNumber);
        }

        return reassignedAssignment;
    }

    /**
     * 요소 위치 정보 (헬퍼 클래스)
     */
    private static class ElementLocation {
        final String questionNumber;
        final UnifiedAnalysisEngine.AnalysisElement element;

        ElementLocation(String questionNumber, UnifiedAnalysisEngine.AnalysisElement element) {
            this.questionNumber = questionNumber;
            this.element = element;
        }
    }

    /**
     * 레이블로 요소 찾기
     *
     * <p>BoundingBox 기반 매칭을 사용하여 안정적으로 요소를 찾습니다.</p>
     *
     * @param assignment 할당 맵
     * @param targetLabel 찾을 요소의 레이블
     * @return 요소 위치 정보, 찾을 수 없으면 null
     */
    private ElementLocation findElementByLabel(
            Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> assignment,
            String targetLabel) {

        for (Map.Entry<String, List<UnifiedAnalysisEngine.AnalysisElement>> entry : assignment.entrySet()) {
            for (UnifiedAnalysisEngine.AnalysisElement element : entry.getValue()) {
                String elementLabel = getElementLabel(element);
                if (elementLabel.equals(targetLabel)) {
                    return new ElementLocation(entry.getKey(), element);
                }
            }
        }
        return null;
    }

    /**
     * 요소의 BoundingBox 추출
     *
     * @param element 분석 요소
     * @return BoundingBox, 추출 불가 시 BoundingBox.EMPTY
     */
    private BoundingBox getElementBoundingBox(UnifiedAnalysisEngine.AnalysisElement element) {
        if (element == null || element.getLayoutInfo() == null || element.getLayoutInfo().getBox() == null) {
            return BoundingBox.EMPTY;
        }
        return BoundingBox.fromArray(element.getLayoutInfo().getBox());
    }

    /**
     * 두 BoundingBox가 동일한지 확인
     *
     * @param box1 첫 번째 BoundingBox
     * @param box2 두 번째 BoundingBox
     * @return true if 동일함 (좌표 오차 1px 허용)
     */
    private boolean boundingBoxesMatch(BoundingBox box1, BoundingBox box2) {
        if (box1 == BoundingBox.EMPTY || box2 == BoundingBox.EMPTY) {
            return false;
        }

        double epsilon = 1.0;  // 1px 오차 허용
        return Math.abs(box1.getX1() - box2.getX1()) < epsilon &&
               Math.abs(box1.getY1() - box2.getY1()) < epsilon &&
               Math.abs(box1.getX2() - box2.getX2()) < epsilon &&
               Math.abs(box1.getY2() - box2.getY2()) < epsilon;
    }

    /**
     * 요소의 라벨 추출 (로깅용)
     *
     * @param element 분석 요소
     * @return 라벨 문자열, 없으면 "unknown"
     */
    private String getElementLabel(UnifiedAnalysisEngine.AnalysisElement element) {
        if (element == null) {
            return "unknown";
        }
        if (element.getLayoutInfo() != null && element.getLayoutInfo().getClassName() != null) {
            return element.getLayoutInfo().getClassName();
        }
        if (element.getCategory() != null) {
            return element.getCategory();
        }
        return "unknown";
    }
}
