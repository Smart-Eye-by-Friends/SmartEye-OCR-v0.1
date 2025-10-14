package com.smarteye.application.analysis.engine.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * 컨텍스트 검증 결과 데이터 클래스
 *
 * 문제 번호 연속성 검증, 공간 범위 검증의 종합 결과를 표현합니다.
 *
 * @author Claude Code (System Architect)
 * @since v0.7 (CIM 그룹핑 알고리즘 재설계)
 */
public class ValidationResult {

    private final List<SequenceGap> sequenceGaps;
    private final List<RangeConflict> rangeConflicts;
    private final boolean isValid;
    private final String summary;

    /**
     * 연속성 검증 전용 생성자
     *
     * @param sequenceGaps 연속성 Gap 리스트
     */
    public ValidationResult(List<SequenceGap> sequenceGaps) {
        this.sequenceGaps = sequenceGaps;
        this.rangeConflicts = new ArrayList<>();
        this.isValid = sequenceGaps.isEmpty();
        this.summary = generateSummary();
    }

    /**
     * 전체 검증 결과 생성자
     *
     * @param sequenceGaps 연속성 Gap 리스트
     * @param rangeConflicts 공간 범위 충돌 리스트
     */
    public ValidationResult(List<SequenceGap> sequenceGaps, List<RangeConflict> rangeConflicts) {
        this.sequenceGaps = sequenceGaps;
        this.rangeConflicts = rangeConflicts;
        this.isValid = sequenceGaps.isEmpty() && rangeConflicts.isEmpty();
        this.summary = generateSummary();
    }

    /**
     * 요약 메시지 생성
     */
    private String generateSummary() {
        if (isValid) {
            return "✅ 검증 성공: 모든 검사 통과";
        }

        List<String> issues = new ArrayList<>();
        if (!sequenceGaps.isEmpty()) {
            long reverseCount = sequenceGaps.stream()
                    .filter(g -> g.getType() == SequenceGap.Type.REVERSE)
                    .count();
            long gapCount = sequenceGaps.stream()
                    .filter(g -> g.getType() == SequenceGap.Type.FORWARD_GAP)
                    .count();

            if (reverseCount > 0) {
                issues.add(String.format("%d개 역순 (OCR 오류 가능성)", reverseCount));
            }
            if (gapCount > 0) {
                issues.add(String.format("%d개 연속성 Gap", gapCount));
            }
        }

        if (!rangeConflicts.isEmpty()) {
            long severeCount = rangeConflicts.stream()
                    .filter(RangeConflict::isSevere)
                    .count();
            if (severeCount > 0) {
                issues.add(String.format("%d개 심각한 공간 충돌", severeCount));
            } else {
                issues.add(String.format("%d개 공간 충돌", rangeConflicts.size()));
            }
        }

        return "⚠️ 검증 실패: " + String.join(", ", issues);
    }

    // Getters
    public List<SequenceGap> getSequenceGaps() {
        return sequenceGaps;
    }

    public List<RangeConflict> getRangeConflicts() {
        return rangeConflicts;
    }

    public boolean isValid() {
        return isValid;
    }

    public String getSummary() {
        return summary;
    }

    /**
     * 역순 Gap 필터링 (OCR 오류 후보)
     */
    public List<SequenceGap> getReverseGaps() {
        return sequenceGaps.stream()
                .filter(g -> g.getType() == SequenceGap.Type.REVERSE)
                .toList();
    }

    /**
     * 정방향 Gap 필터링 (누락 문제 후보)
     */
    public List<SequenceGap> getForwardGaps() {
        return sequenceGaps.stream()
                .filter(g -> g.getType() == SequenceGap.Type.FORWARD_GAP)
                .toList();
    }

    @Override
    public String toString() {
        return String.format("ValidationResult{valid=%s, gaps=%d, conflicts=%d, summary='%s'}",
                isValid, sequenceGaps.size(), rangeConflicts.size(), summary);
    }
}
