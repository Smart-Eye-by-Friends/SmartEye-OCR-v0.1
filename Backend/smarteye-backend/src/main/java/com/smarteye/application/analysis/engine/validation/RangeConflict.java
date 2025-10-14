package com.smarteye.application.analysis.engine.validation;

import com.smarteye.application.analysis.UnifiedAnalysisEngine;

import java.util.List;

/**
 * 공간 범위 충돌 데이터 클래스
 *
 * 두 문제의 공간 범위가 비정상적으로 중첩되는 경우를 표현합니다.
 * 예: 294번 텍스트가 296번 영역에 잘못 할당된 경우
 *
 * @author Claude Code (System Architect)
 * @since v0.7 (CIM 그룹핑 알고리즘 재설계)
 */
public class RangeConflict {

    private final int question1;
    private final int question2;
    private final double overlapArea;
    private final List<UnifiedAnalysisEngine.AnalysisElement> overlappingElements;

    /**
     * 범위 충돌 생성자
     *
     * @param question1 첫 번째 문제 번호
     * @param question2 두 번째 문제 번호
     * @param overlapArea 중첩 영역 크기 (px²)
     * @param overlappingElements 중첩된 요소 리스트
     */
    public RangeConflict(int question1, int question2, double overlapArea,
                         List<UnifiedAnalysisEngine.AnalysisElement> overlappingElements) {
        this.question1 = question1;
        this.question2 = question2;
        this.overlapArea = overlapArea;
        this.overlappingElements = overlappingElements;
    }

    // Getters
    public int getQuestion1() {
        return question1;
    }

    public int getQuestion2() {
        return question2;
    }

    public double getOverlapArea() {
        return overlapArea;
    }

    public List<UnifiedAnalysisEngine.AnalysisElement> getOverlappingElements() {
        return overlappingElements;
    }

    /**
     * 충돌 심각도 판단
     *
     * @return true if 중첩 영역이 10,000px² 이상
     */
    public boolean isSevere() {
        return overlapArea > 10000;
    }

    @Override
    public String toString() {
        return String.format("RangeConflict{q%d ↔ q%d, overlap=%.0fpx², elements=%d, severe=%s}",
                question1, question2, overlapArea, overlappingElements.size(), isSevere());
    }
}
