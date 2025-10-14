package com.smarteye.application.analysis.engine.correction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 요소 재할당 결과 데이터 클래스
 *
 * 공간 충돌 해결을 통해 재할당된 요소들의 정보를 표현합니다.
 *
 * @author Claude Code (System Architect + Refactoring Expert)
 * @since v0.7 (CIM 그룹핑 알고리즘 재설계 - Phase 3-B)
 */
public class ReassignmentResult {

    /**
     * 재할당 로그 엔트리
     */
    public static class ReassignmentLog {
        private final String elementId;
        private final String fromQuestion;
        private final String toQuestion;
        private final String reason;

        public ReassignmentLog(String elementId, String fromQuestion, String toQuestion, String reason) {
            this.elementId = elementId;
            this.fromQuestion = fromQuestion;
            this.toQuestion = toQuestion;
            this.reason = reason;
        }

        // Getters
        public String getElementId() { return elementId; }
        public String getFromQuestion() { return fromQuestion; }
        public String getToQuestion() { return toQuestion; }
        public String getReason() { return reason; }

        @Override
        public String toString() {
            return String.format("요소 %s: %s → %s (%s)", elementId, fromQuestion, toQuestion, reason);
        }
    }

    private final Map<String, String> reassignments;  // [elementId → newQuestionNumber]
    private final List<ReassignmentLog> reassignmentLogs;
    private final int conflictsResolved;

    /**
     * 기본 생성자
     */
    public ReassignmentResult() {
        this.reassignments = new HashMap<>();
        this.reassignmentLogs = new ArrayList<>();
        this.conflictsResolved = 0;
    }

    /**
     * 전체 생성자
     *
     * @param reassignments 재할당 맵 (요소 ID → 새 문제 번호)
     * @param reassignmentLogs 재할당 로그 리스트
     * @param conflictsResolved 해결된 충돌 개수
     */
    public ReassignmentResult(
            Map<String, String> reassignments,
            List<ReassignmentLog> reassignmentLogs,
            int conflictsResolved) {
        this.reassignments = reassignments;
        this.reassignmentLogs = reassignmentLogs;
        this.conflictsResolved = conflictsResolved;
    }

    /**
     * 재할당 추가
     *
     * @param elementId 요소 ID
     * @param fromQuestion 원래 문제 번호
     * @param toQuestion 새 문제 번호
     * @param reason 재할당 이유
     */
    public void addReassignment(String elementId, String fromQuestion, String toQuestion, String reason) {
        reassignments.put(elementId, toQuestion);
        reassignmentLogs.add(new ReassignmentLog(elementId, fromQuestion, toQuestion, reason));
    }

    // Getters
    public Map<String, String> getReassignments() {
        return reassignments;
    }

    public List<ReassignmentLog> getReassignmentLogs() {
        return reassignmentLogs;
    }

    public int getConflictsResolved() {
        return conflictsResolved;
    }

    public boolean hasReassignments() {
        return !reassignments.isEmpty();
    }

    /**
     * 재할당 요약 생성
     *
     * @return 재할당 요약 문자열
     */
    public String getSummary() {
        if (!hasReassignments()) {
            return "✅ 재할당 불필요: 공간 충돌 없음";
        }

        return String.format("🔄 재할당 완료: %d개 요소, %d개 충돌 해결",
                reassignments.size(), conflictsResolved);
    }

    @Override
    public String toString() {
        return String.format("ReassignmentResult{reassignments=%d, conflictsResolved=%d, summary='%s'}",
                reassignments.size(), conflictsResolved, getSummary());
    }
}
