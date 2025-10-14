package com.smarteye.application.analysis.engine.correction;

import com.smarteye.application.analysis.UnifiedAnalysisEngine;
import com.smarteye.application.analysis.engine.validation.BoundingBox;
import com.smarteye.application.analysis.engine.validation.RangeConflict;
import com.smarteye.application.analysis.engine.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 요소 재할당 엔진
 *
 * <p>역할:</p>
 * <ul>
 *   <li>공간 범위 충돌(RangeConflict) 감지 및 해결</li>
 *   <li>잘못 할당된 요소를 올바른 문제 그룹으로 재할당</li>
 *   <li>IoU(Intersection over Union) 기반 재할당 판단</li>
 * </ul>
 *
 * <p><strong>예시:</strong></p>
 * <pre>
 * 입력: RangeConflict{q294 ↔ q296, overlap=15000px²}
 * 처리: 중첩된 요소를 각 문제와의 거리 기반으로 재할당
 * 출력: ReassignmentResult{reassignments=2, conflictsResolved=1}
 * </pre>
 *
 * @author Claude Code (System Architect + Refactoring Expert)
 * @since v0.7 (CIM 그룹핑 알고리즘 재설계 - Phase 3-B)
 */
@Component
public class ElementReassignmentEngine {

    private static final Logger logger = LoggerFactory.getLogger(ElementReassignmentEngine.class);

    /**
     * IoU 임계값: 이 값 이상이면 심각한 충돌로 판단
     */
    private static final double SEVERE_OVERLAP_THRESHOLD = 0.3;

    /**
     * 재할당 판단 임계값: 현재 할당과 새 할당의 IoU 차이가 이 값 이상이면 재할당 수행
     */
    private static final double REASSIGNMENT_IOU_DELTA_THRESHOLD = 0.15;

    /**
     * 요소 재할당 메인 메서드
     *
     * <p>공간 충돌을 해결하여 요소들을 재할당합니다.</p>
     *
     * @param validationResult 검증 결과 (RangeConflict 포함)
     * @param currentAssignment 현재 할당 맵 (문제 번호 → 요소 리스트)
     * @return 재할당 결과
     */
    public ReassignmentResult reassign(
            ValidationResult validationResult,
            Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> currentAssignment) {

        logger.info("🔄 공간 충돌 재할당 시작");

        List<RangeConflict> conflicts = validationResult.getRangeConflicts();

        if (conflicts.isEmpty()) {
            logger.info("✅ 공간 충돌 없음: 재할당 불필요");
            return new ReassignmentResult();
        }

        logger.info("📋 감지된 충돌: {}개", conflicts.size());

        ReassignmentResult result = new ReassignmentResult();
        int conflictsResolved = 0;

        for (RangeConflict conflict : conflicts) {
            if (resolveConflict(conflict, currentAssignment, result)) {
                conflictsResolved++;
            }
        }

        logger.info("📊 재할당 완료: {}개 충돌 해결, {}개 요소 재할당",
                conflictsResolved, result.getReassignments().size());

        return new ReassignmentResult(
                result.getReassignments(),
                result.getReassignmentLogs(),
                conflictsResolved
        );
    }

    /**
     * 개별 충돌 해결
     *
     * <p>중첩된 요소들을 분석하여 어느 문제에 더 가까운지 판단하고 재할당합니다.</p>
     *
     * <p><strong>알고리즘 개요:</strong></p>
     * <ol>
     *   <li>각 중첩 요소에 대해 두 문제와의 IoU 계산</li>
     *   <li>IoU 차이가 REASSIGNMENT_IOU_DELTA_THRESHOLD 이상이면 재할당 판단</li>
     *   <li>IoU 차이가 작으면 2D 거리 기반 최근접 문제로 재할당</li>
     *   <li>재할당 결과를 ReassignmentResult에 기록</li>
     * </ol>
     *
     * @param conflict 충돌 정보
     * @param currentAssignment 현재 할당 맵
     * @param result 재할당 결과 (누적)
     * @return true if 충돌이 해결됨
     */
    private boolean resolveConflict(
            RangeConflict conflict,
            Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> currentAssignment,
            ReassignmentResult result) {

        int q1 = conflict.getQuestion1();
        int q2 = conflict.getQuestion2();
        List<UnifiedAnalysisEngine.AnalysisElement> overlappingElements = conflict.getOverlappingElements();

        logger.debug("⚡ 충돌 해결 시도: q{} ↔ q{} (중첩: {:.0f}px², 요소: {}개)",
                q1, q2, conflict.getOverlapArea(), overlappingElements.size());

        if (overlappingElements.isEmpty()) {
            logger.warn("⚠️ 충돌 해결 실패: 중첩된 요소 없음");
            return false;
        }

        // Step 1: 두 문제의 BoundingBox 계산
        String q1Key = String.valueOf(q1);
        String q2Key = String.valueOf(q2);

        BoundingBox q1Box = getQuestionBoundingBox(currentAssignment.get(q1Key));
        BoundingBox q2Box = getQuestionBoundingBox(currentAssignment.get(q2Key));

        if (q1Box == BoundingBox.EMPTY || q2Box == BoundingBox.EMPTY) {
            logger.warn("⚠️ 충돌 해결 실패: 문제 영역 계산 불가");
            return false;
        }

        // Step 2: 각 중첩 요소에 대해 재할당 판단
        int reassignedCount = 0;
        for (UnifiedAnalysisEngine.AnalysisElement element : overlappingElements) {
            BoundingBox elementBox = getElementBoundingBox(element);
            if (elementBox == BoundingBox.EMPTY) {
                continue;
            }

            // Step 2-1: IoU 계산
            double iouWithQ1 = elementBox.iou(q1Box);
            double iouWithQ2 = elementBox.iou(q2Box);
            double iouDelta = Math.abs(iouWithQ1 - iouWithQ2);

            String elementLabel = getElementLabel(element);
            logger.debug("  📊 요소 {} IoU 분석: q{}={:.3f}, q{}={:.3f}, delta={:.3f}",
                    elementLabel, q1, iouWithQ1, q2, iouWithQ2, iouDelta);

            // Step 2-2: 현재 할당된 문제 확인
            String currentQuestion = findCurrentQuestion(element, currentAssignment);
            if (currentQuestion == null) {
                logger.warn("  ⚠️ 요소 {} 현재 할당 확인 불가", elementLabel);
                continue;
            }

            // Step 2-3: 재할당 판단
            String targetQuestion = null;
            String reason = null;

            if (iouDelta >= REASSIGNMENT_IOU_DELTA_THRESHOLD) {
                // IoU 차이가 큰 경우: IoU가 더 높은 문제로 재할당
                targetQuestion = iouWithQ1 > iouWithQ2 ? q1Key : q2Key;
                reason = String.format("IoU 기반 (q%s=%.3f > q%s=%.3f, delta=%.3f)",
                        targetQuestion, Math.max(iouWithQ1, iouWithQ2),
                        targetQuestion.equals(q1Key) ? q2 : q1, Math.min(iouWithQ1, iouWithQ2),
                        iouDelta);
            } else {
                // IoU 차이가 작은 경우: 2D 거리 기반 최근접 문제로 재할당
                int closerQuestion = findCloserQuestion(element, q1, q2, q1Box, q2Box);
                targetQuestion = String.valueOf(closerQuestion);
                double dist1 = calculate2DDistance(elementBox.getCenterX(), elementBox.getCenterY(),
                        q1Box.getCenterX(), q1Box.getCenterY());
                double dist2 = calculate2DDistance(elementBox.getCenterX(), elementBox.getCenterY(),
                        q2Box.getCenterX(), q2Box.getCenterY());
                reason = String.format("거리 기반 (q%d=%.0fpx < q%d=%.0fpx)",
                        closerQuestion, Math.min(dist1, dist2),
                        closerQuestion == q1 ? q2 : q1, Math.max(dist1, dist2));
            }

            // Step 2-4: 재할당 필요 여부 확인
            if (!targetQuestion.equals(currentQuestion)) {
                result.addReassignment(elementLabel, currentQuestion, targetQuestion, reason);
                reassignedCount++;
                logger.debug("  ✅ 재할당: {} → {} (이유: {})",
                        elementLabel, targetQuestion, reason);
            } else {
                logger.debug("  ⏸️  유지: {} (현재 할당 유효)", elementLabel);
            }
        }

        logger.info("📊 충돌 해결 완료: q{} ↔ q{} ({}개 재할당)", q1, q2, reassignedCount);
        return reassignedCount > 0;
    }

    /**
     * 요소가 특정 문제에 더 가까운지 판단
     *
     * <p><strong>판단 알고리즘:</strong></p>
     * <ol>
     *   <li>2D Euclidean 거리 계산 (요소 중심 ↔ 문제 영역 중심)</li>
     *   <li>더 가까운 문제 선택</li>
     * </ol>
     *
     * @param element 판단할 요소
     * @param question1 첫 번째 문제 번호
     * @param question2 두 번째 문제 번호
     * @param q1Box 첫 번째 문제의 BoundingBox
     * @param q2Box 두 번째 문제의 BoundingBox
     * @return 더 가까운 문제 번호
     */
    private int findCloserQuestion(
            UnifiedAnalysisEngine.AnalysisElement element,
            int question1,
            int question2,
            BoundingBox q1Box,
            BoundingBox q2Box) {

        BoundingBox elementBox = getElementBoundingBox(element);
        if (elementBox == BoundingBox.EMPTY) {
            // 요소의 BoundingBox를 계산할 수 없으면 문제 번호 순서로 결정
            return question1 < question2 ? question1 : question2;
        }

        // 요소 중심점
        double elementCenterX = elementBox.getCenterX();
        double elementCenterY = elementBox.getCenterY();

        // 각 문제 영역 중심점과의 2D 거리 계산
        double distanceToQ1 = calculate2DDistance(
                elementCenterX, elementCenterY,
                q1Box.getCenterX(), q1Box.getCenterY()
        );

        double distanceToQ2 = calculate2DDistance(
                elementCenterX, elementCenterY,
                q2Box.getCenterX(), q2Box.getCenterY()
        );

        // 더 가까운 문제 선택
        return distanceToQ1 <= distanceToQ2 ? question1 : question2;
    }

    /**
     * 2D Euclidean 거리 계산
     *
     * @param x1 첫 번째 점 X 좌표
     * @param y1 첫 번째 점 Y 좌표
     * @param x2 두 번째 점 X 좌표
     * @param y2 두 번째 점 Y 좌표
     * @return Euclidean 거리
     */
    private double calculate2DDistance(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
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
     * 문제 영역의 BoundingBox 계산
     *
     * <p>문제에 할당된 모든 요소의 통합 범위를 계산합니다.</p>
     *
     * @param elements 문제에 할당된 요소 리스트
     * @return 통합 BoundingBox, 계산 불가 시 BoundingBox.EMPTY
     */
    private BoundingBox getQuestionBoundingBox(List<UnifiedAnalysisEngine.AnalysisElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return BoundingBox.EMPTY;
        }

        BoundingBox result = null;
        for (UnifiedAnalysisEngine.AnalysisElement element : elements) {
            BoundingBox elementBox = getElementBoundingBox(element);
            if (elementBox != BoundingBox.EMPTY) {
                result = (result == null) ? elementBox : result.union(elementBox);
            }
        }

        return result != null ? result : BoundingBox.EMPTY;
    }

    /**
     * 요소가 현재 어느 문제에 할당되어 있는지 확인
     *
     * <p>BoundingBox를 기반으로 요소를 매칭합니다. (객체 동일성 문제 회피)</p>
     *
     * @param element 확인할 요소
     * @param currentAssignment 현재 할당 맵
     * @return 현재 할당된 문제 번호 (문자열), 찾을 수 없으면 null
     */
    private String findCurrentQuestion(
            UnifiedAnalysisEngine.AnalysisElement element,
            Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> currentAssignment) {

        BoundingBox targetBox = getElementBoundingBox(element);
        if (targetBox == BoundingBox.EMPTY) {
            return null;
        }

        for (Map.Entry<String, List<UnifiedAnalysisEngine.AnalysisElement>> entry : currentAssignment.entrySet()) {
            for (UnifiedAnalysisEngine.AnalysisElement candidate : entry.getValue()) {
                BoundingBox candidateBox = getElementBoundingBox(candidate);
                if (boundingBoxesMatch(targetBox, candidateBox)) {
                    return entry.getKey();
                }
            }
        }
        return null;
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
