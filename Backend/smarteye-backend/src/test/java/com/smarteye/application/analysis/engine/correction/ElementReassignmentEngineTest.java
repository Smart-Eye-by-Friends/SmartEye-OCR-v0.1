package com.smarteye.application.analysis.engine.correction;

import com.smarteye.application.analysis.UnifiedAnalysisEngine;
import com.smarteye.application.analysis.engine.validation.RangeConflict;
import com.smarteye.application.analysis.engine.validation.ValidationResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ElementReassignmentEngine 단위 테스트
 *
 * <p>Phase 4-A에서 구현된 IoU 기반 재할당 및 2D 거리 기반 최근접 할당 알고리즘을 검증합니다.</p>
 *
 * @author Claude Code (System Architect + Refactoring Expert)
 * @since v0.7 (CIM 그룹핑 알고리즘 재설계 - Phase 4-A)
 */
@DisplayName("ElementReassignmentEngine 테스트")
class ElementReassignmentEngineTest {

    private ElementReassignmentEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ElementReassignmentEngine();
    }

    @Test
    @DisplayName("충돌 없는 경우 재할당 불필요")
    void testNoConflict() {
        // Given: 충돌이 없는 ValidationResult
        ValidationResult validationResult = createValidationResult(Collections.emptyList());
        Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> currentAssignment = new HashMap<>();

        // When: 재할당 실행
        ReassignmentResult result = engine.reassign(validationResult, currentAssignment);

        // Then: 재할당 없음
        assertFalse(result.hasReassignments());
        assertEquals(0, result.getConflictsResolved());
    }

    @Test
    @DisplayName("IoU 기반 재할당: IoU 차이가 큰 경우")
    void testIoUBasedReassignment() {
        // Given: 요소가 q295보다 q296과 훨씬 많이 겹치는 경우
        UnifiedAnalysisEngine.AnalysisElement element1 = createElement("e1", 100, 100, 200, 150);
        UnifiedAnalysisEngine.AnalysisElement element2 = createElement("e2", 100, 200, 200, 250);
        UnifiedAnalysisEngine.AnalysisElement overlappingElement = createElement("e3", 100, 190, 200, 260);

        List<UnifiedAnalysisEngine.AnalysisElement> q295Elements = List.of(element1);
        List<UnifiedAnalysisEngine.AnalysisElement> q296Elements = List.of(element2, overlappingElement);

        Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> currentAssignment = new HashMap<>();
        currentAssignment.put("295", new ArrayList<>(q295Elements));
        currentAssignment.put("296", new ArrayList<>(q296Elements));

        RangeConflict conflict = new RangeConflict(295, 296, 5000.0, List.of(overlappingElement));
        ValidationResult validationResult = createValidationResult(List.of(conflict));

        // When: 재할당 실행
        ReassignmentResult result = engine.reassign(validationResult, currentAssignment);

        // Then: IoU 기반 재할당 발생 (e3은 q296과 더 많이 겹침)
        assertTrue(result.hasReassignments());
        assertEquals(1, result.getConflictsResolved());
    }

    @Test
    @DisplayName("2D 거리 기반 재할당: IoU 차이가 작은 경우")
    void testDistanceBasedReassignment() {
        // Given: IoU는 비슷하지만 거리 차이가 있는 경우
        UnifiedAnalysisEngine.AnalysisElement element1 = createElement("e1", 100, 100, 200, 150);
        UnifiedAnalysisEngine.AnalysisElement element2 = createElement("e2", 100, 500, 200, 550);
        UnifiedAnalysisEngine.AnalysisElement middleElement = createElement("e3", 100, 300, 200, 350);

        List<UnifiedAnalysisEngine.AnalysisElement> q295Elements = new ArrayList<>();
        q295Elements.add(element1);
        q295Elements.add(middleElement);  // 현재 q295에 할당됨

        List<UnifiedAnalysisEngine.AnalysisElement> q296Elements = new ArrayList<>();
        q296Elements.add(element2);

        Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> currentAssignment = new HashMap<>();
        currentAssignment.put("295", q295Elements);
        currentAssignment.put("296", q296Elements);

        RangeConflict conflict = new RangeConflict(295, 296, 3000.0, List.of(middleElement));
        ValidationResult validationResult = createValidationResult(List.of(conflict));

        // When: 재할당 실행
        ReassignmentResult result = engine.reassign(validationResult, currentAssignment);

        // Then: 거리 기반 재할당 발생 (e3은 q296에 더 가까움)
        assertTrue(result.hasReassignments());
    }

    @Test
    @DisplayName("복수 요소 충돌 처리")
    void testMultipleElementConflicts() {
        // Given: 여러 요소가 두 문제 사이에서 충돌
        UnifiedAnalysisEngine.AnalysisElement q295Base = createElement("e1", 100, 100, 200, 150);
        UnifiedAnalysisEngine.AnalysisElement q296Base = createElement("e2", 100, 400, 200, 450);

        UnifiedAnalysisEngine.AnalysisElement overlapping1 = createElement("e3", 100, 200, 200, 250);
        UnifiedAnalysisEngine.AnalysisElement overlapping2 = createElement("e4", 100, 350, 200, 400);

        Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> currentAssignment = new HashMap<>();
        currentAssignment.put("295", new ArrayList<>(List.of(q295Base, overlapping1)));
        currentAssignment.put("296", new ArrayList<>(List.of(q296Base, overlapping2)));

        RangeConflict conflict = new RangeConflict(295, 296, 8000.0, List.of(overlapping1, overlapping2));
        ValidationResult validationResult = createValidationResult(List.of(conflict));

        // When: 재할당 실행
        ReassignmentResult result = engine.reassign(validationResult, currentAssignment);

        // Then: 재할당 발생
        assertTrue(result.hasReassignments());
        assertTrue(result.getReassignments().size() >= 1);
    }

    @Test
    @DisplayName("재할당 로그 생성 검증")
    void testReassignmentLogging() {
        // Given: 재할당이 필요한 상황
        UnifiedAnalysisEngine.AnalysisElement element1 = createElement("e1", 100, 100, 200, 150);
        UnifiedAnalysisEngine.AnalysisElement element2 = createElement("e2", 100, 200, 200, 250);
        UnifiedAnalysisEngine.AnalysisElement overlappingElement = createElement("e3", 100, 190, 200, 260);

        Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> currentAssignment = new HashMap<>();
        currentAssignment.put("295", new ArrayList<>(List.of(element1)));
        currentAssignment.put("296", new ArrayList<>(List.of(element2, overlappingElement)));

        RangeConflict conflict = new RangeConflict(295, 296, 5000.0, List.of(overlappingElement));
        ValidationResult validationResult = createValidationResult(List.of(conflict));

        // When: 재할당 실행
        ReassignmentResult result = engine.reassign(validationResult, currentAssignment);

        // Then: 로그가 생성됨
        assertTrue(result.hasReassignments());
        assertFalse(result.getReassignmentLogs().isEmpty());

        ReassignmentResult.ReassignmentLog firstLog = result.getReassignmentLogs().get(0);
        assertNotNull(firstLog.getElementId());
        assertNotNull(firstLog.getFromQuestion());
        assertNotNull(firstLog.getToQuestion());
        assertNotNull(firstLog.getReason());
    }

    @Test
    @DisplayName("BoundingBox 계산 실패 시 안전 처리")
    void testSafeHandlingOfInvalidBoundingBox() {
        // Given: BoundingBox가 없는 요소
        UnifiedAnalysisEngine.AnalysisElement invalidElement = new UnifiedAnalysisEngine.AnalysisElement();
        invalidElement.setLayoutInfo(null);

        Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> currentAssignment = new HashMap<>();
        currentAssignment.put("295", new ArrayList<>(List.of(invalidElement)));

        RangeConflict conflict = new RangeConflict(295, 296, 1000.0, List.of(invalidElement));
        ValidationResult validationResult = createValidationResult(List.of(conflict));

        // When: 재할당 실행
        ReassignmentResult result = engine.reassign(validationResult, currentAssignment);

        // Then: 예외 없이 처리됨
        assertNotNull(result);
        assertFalse(result.hasReassignments());
    }

    // ========== Helper Methods ==========

    /**
     * AnalysisElement 생성 헬퍼
     */
    private UnifiedAnalysisEngine.AnalysisElement createElement(String label, int x1, int y1, int x2, int y2) {
        UnifiedAnalysisEngine.AnalysisElement element = new UnifiedAnalysisEngine.AnalysisElement();

        LayoutInfo layoutInfo = new LayoutInfo();
        layoutInfo.setBox(new int[]{x1, y1, x2, y2});
        layoutInfo.setClassName(label);
        layoutInfo.setConfidence(0.9);

        element.setLayoutInfo(layoutInfo);
        return element;
    }

    /**
     * ValidationResult 생성 헬퍼
     */
    private ValidationResult createValidationResult(List<RangeConflict> conflicts) {
        return new ValidationResult(
                Collections.emptyList(),  // sequenceGaps
                conflicts  // rangeConflicts
        );
    }
}
