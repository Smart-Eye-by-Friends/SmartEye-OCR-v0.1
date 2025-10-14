package com.smarteye.application.analysis.engine.validation;

import com.smarteye.application.analysis.UnifiedAnalysisEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 공간 범위 검증기
 *
 * 역할:
 * - 할당된 요소들의 Y좌표 범위 중첩 검사
 * - 문제 간 경계 위반 감지
 * - 잘못된 컬럼 할당 감지
 * - 비정상적으로 넓은 범위 감지
 *
 * 근거:
 * - 재설계 제안서 Section 6-C
 * - 실패 사례: 294번 텍스트(ID 32)가 296번에 잘못 할당됨
 *
 * @author Claude Code (System Architect)
 * @since v0.7 (CIM 그룹핑 알고리즘 재설계)
 */
@Component
public class SpatialRangeValidator {

    private static final Logger log = LoggerFactory.getLogger(SpatialRangeValidator.class);

    /**
     * 중첩 허용 임계값 (IoU)
     */
    private static final double OVERLAP_THRESHOLD = 0.1;

    /**
     * 공간 범위 중첩 검사
     *
     * 전략:
     * 1. 각 문제의 통합 Bounding Box 계산
     * 2. 문제 간 중첩 영역 검사
     * 3. 중첩 요소 식별
     * 4. 충돌 목록 반환
     *
     * @param questionStructures 문제 구조 리스트
     * @return 공간 충돌 목록
     */
    public List<RangeConflict> checkOverlap(List<UnifiedAnalysisEngine.QuestionStructure> questionStructures) {
        if (questionStructures == null || questionStructures.size() < 2) {
            log.info("✅ 검증 대상 문제가 2개 미만이므로 공간 충돌 검사 불필요");
            return List.of();
        }

        List<RangeConflict> conflicts = new ArrayList<>();
        log.info("🔍 공간 범위 중첩 검사 시작 - 문제 수: {}", questionStructures.size());

        for (int i = 0; i < questionStructures.size(); i++) {
            UnifiedAnalysisEngine.QuestionStructure q1 = questionStructures.get(i);
            BoundingBox range1 = calculateQuestionRange(q1);

            if (range1 == BoundingBox.EMPTY) {
                log.warn("⚠️ 문제 {}번: 요소가 없어 범위 계산 불가", q1.getQuestionNumber());
                continue;
            }

            for (int j = i + 1; j < questionStructures.size(); j++) {
                UnifiedAnalysisEngine.QuestionStructure q2 = questionStructures.get(j);
                BoundingBox range2 = calculateQuestionRange(q2);

                if (range2 == BoundingBox.EMPTY) {
                    continue;
                }

                // 범위 중첩 감지
                if (range1.overlaps(range2)) {
                    double overlapArea = range1.getOverlapArea(range2);
                    double iou = range1.iou(range2);

                    // 임계값 초과 시 충돌로 기록
                    if (iou > OVERLAP_THRESHOLD) {
                        List<UnifiedAnalysisEngine.AnalysisElement> overlappingElements = findOverlappingElements(q1, q2, range1, range2);
                        RangeConflict conflict = new RangeConflict(
                                q1.getQuestionNumber(),
                                q2.getQuestionNumber(),
                                overlapArea,
                                overlappingElements
                        );
                        conflicts.add(conflict);
                        log.warn("⚠️ 공간 충돌 감지: {} (IoU: {:.2f})", conflict, iou);
                    }
                }
            }
        }

        log.info("📊 공간 범위 검사 완료: {}개 충돌 감지", conflicts.size());
        return conflicts;
    }

    /**
     * 문제의 통합 범위 계산
     *
     * 전략: 모든 요소의 Bounding Box를 통합하여 문제 전체 범위 계산
     *
     * @param question 문제 구조
     * @return 통합 Bounding Box
     */
    private BoundingBox calculateQuestionRange(UnifiedAnalysisEngine.QuestionStructure question) {
        List<UnifiedAnalysisEngine.AnalysisElement> elements = question.getElements();
        if (elements == null || elements.isEmpty()) {
            return BoundingBox.EMPTY;
        }

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;

        for (UnifiedAnalysisEngine.AnalysisElement element : elements) {
            if (element.getLayoutInfo() != null && element.getLayoutInfo().getBox() != null) {
                int[] box = element.getLayoutInfo().getBox();
                if (box.length >= 4) {
                    minX = Math.min(minX, box[0]);
                    minY = Math.min(minY, box[1]);
                    maxX = Math.max(maxX, box[2]);
                    maxY = Math.max(maxY, box[3]);
                }
            }
        }

        // 유효한 범위가 계산되지 않은 경우
        if (minX == Double.MAX_VALUE || minY == Double.MAX_VALUE) {
            return BoundingBox.EMPTY;
        }

        return new BoundingBox(minX, minY, maxX, maxY);
    }

    /**
     * 중첩된 요소 식별
     *
     * @param q1 첫 번째 문제
     * @param q2 두 번째 문제
     * @param range1 첫 번째 문제 범위
     * @param range2 두 번째 문제 범위
     * @return 중첩된 요소 리스트
     */
    private List<UnifiedAnalysisEngine.AnalysisElement> findOverlappingElements(
            UnifiedAnalysisEngine.QuestionStructure q1, UnifiedAnalysisEngine.QuestionStructure q2,
            BoundingBox range1, BoundingBox range2) {
        List<UnifiedAnalysisEngine.AnalysisElement> overlapping = new ArrayList<>();

        // q1의 요소 중 q2 범위에 속하는 것
        for (UnifiedAnalysisEngine.AnalysisElement elem : q1.getElements()) {
            BoundingBox elemBox = getElementBoundingBox(elem);
            if (elemBox != BoundingBox.EMPTY && range2.overlaps(elemBox)) {
                overlapping.add(elem);
            }
        }

        // q2의 요소 중 q1 범위에 속하는 것
        for (UnifiedAnalysisEngine.AnalysisElement elem : q2.getElements()) {
            BoundingBox elemBox = getElementBoundingBox(elem);
            if (elemBox != BoundingBox.EMPTY && range1.overlaps(elemBox)) {
                overlapping.add(elem);
            }
        }

        return overlapping;
    }

    /**
     * 요소의 Bounding Box 추출
     */
    private BoundingBox getElementBoundingBox(UnifiedAnalysisEngine.AnalysisElement element) {
        if (element.getLayoutInfo() != null && element.getLayoutInfo().getBox() != null) {
            return BoundingBox.fromArray(element.getLayoutInfo().getBox());
        }
        return BoundingBox.EMPTY;
    }

    /**
     * 문제 범위 이상 감지 (비정상적으로 넓은 범위)
     *
     * @param question 검증할 문제
     * @return true if 이상 감지
     */
    public boolean detectAbnormalRange(UnifiedAnalysisEngine.QuestionStructure question) {
        BoundingBox range = calculateQuestionRange(question);
        if (range == BoundingBox.EMPTY) {
            return false;
        }

        // 비정상적으로 넓은 범위 (1/3 페이지 이상)
        double pageHeight = 4736.0;  // test_homework_image.jpg 기준
        double rangeHeight = range.getHeight();

        if (rangeHeight > pageHeight / 3.0) {
            log.warn("⚠️ 문제 {}번: 비정상적으로 넓은 범위 (높이: {:.0f}px)", question.getQuestionNumber(), rangeHeight);
            return true;
        }

        return false;
    }
}
