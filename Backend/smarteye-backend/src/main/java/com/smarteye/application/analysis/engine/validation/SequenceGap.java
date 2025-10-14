package com.smarteye.application.analysis.engine.validation;

import java.util.List;

/**
 * 문제 번호 연속성 Gap 데이터 클래스
 *
 * 문제 번호 시퀀스에서 발견된 불연속 지점이나 이상 패턴을 표현합니다.
 * 예: [295, 204, 296] → 204는 역순이며 OCR 오류 가능성 (실제: 294)
 *
 * @author Claude Code (System Architect)
 * @since v0.7 (CIM 그룹핑 알고리즘 재설계)
 */
public class SequenceGap {

    /**
     * Gap 유형
     */
    public enum Type {
        /** 정방향 Gap (예: 295 → 297, 296 누락) */
        FORWARD_GAP,

        /** 역순 (예: 295 → 204, OCR 오류 가능성) */
        REVERSE,

        /** 대형 점프 (예: 295 → 305, 10 이상 점프) */
        LARGE_JUMP
    }

    private final int before;
    private final int after;
    private final int missingCount;
    private final List<Integer> inferredNumbers;
    private final Type type;
    private final String description;

    /**
     * 정방향 Gap 생성자
     *
     * @param before 이전 문제 번호
     * @param after 다음 문제 번호
     * @param missingCount 누락된 문제 개수
     * @param inferredNumbers 추론된 누락 번호 리스트
     */
    public SequenceGap(int before, int after, int missingCount, List<Integer> inferredNumbers) {
        this.before = before;
        this.after = after;
        this.missingCount = missingCount;
        this.inferredNumbers = inferredNumbers;

        // 자동 타입 감지
        if (after < before) {
            this.type = Type.REVERSE;
            this.description = String.format("역순 감지: %d → %d (OCR 오류 가능성)", before, after);
        } else if (after - before > 10) {
            this.type = Type.LARGE_JUMP;
            this.description = String.format("대형 점프: %d → %d (%d칸 점프)", before, after, after - before);
        } else {
            this.type = Type.FORWARD_GAP;
            this.description = String.format("연속성 Gap: %d → %d (%d개 누락)", before, after, missingCount);
        }
    }

    /**
     * 명시적 타입 지정 생성자
     *
     * @param before 이전 문제 번호
     * @param after 다음 문제 번호
     * @param type Gap 유형
     * @param description 설명
     */
    public SequenceGap(int before, int after, Type type, String description) {
        this.before = before;
        this.after = after;
        this.type = type;
        this.description = description;
        this.missingCount = (type == Type.FORWARD_GAP) ? (after - before - 1) : 0;
        this.inferredNumbers = List.of();
    }

    // Getters
    public int getBefore() {
        return before;
    }

    public int getAfter() {
        return after;
    }

    public int getMissingCount() {
        return missingCount;
    }

    public List<Integer> getInferredNumbers() {
        return inferredNumbers;
    }

    public Type getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 기대되는 다음 번호 (역순 감지용)
     *
     * @return before + 1
     */
    public int getExpectedNext() {
        return before + 1;
    }

    @Override
    public String toString() {
        return String.format("SequenceGap{type=%s, before=%d, after=%d, missing=%d, description='%s'}",
                type, before, after, missingCount, description);
    }
}
