package com.smarteye.application.analysis.engine.correction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 교정 결과 데이터 클래스
 *
 * 검증 단계에서 감지된 오류를 교정한 결과를 표현합니다.
 * OCR 오류 교정, 누락 문제 복구, 요소 재할당 등의 정보를 포함합니다.
 *
 * @author Claude Code (System Architect + Refactoring Expert)
 * @since v0.7 (CIM 그룹핑 알고리즘 재설계 - Phase 3)
 */
public class CorrectionResult {

    /**
     * 교정 로그 엔트리
     */
    public static class CorrectionLog {
        private final String type;           // "OCR_CORRECTION", "MISSING_RECOVERY", "REASSIGNMENT"
        private final String description;    // "204 → 294 (OCR 오류 교정)"
        private final Object before;         // 교정 전 값
        private final Object after;          // 교정 후 값

        public CorrectionLog(String type, String description, Object before, Object after) {
            this.type = type;
            this.description = description;
            this.before = before;
            this.after = after;
        }

        // Getters
        public String getType() { return type; }
        public String getDescription() { return description; }
        public Object getBefore() { return before; }
        public Object getAfter() { return after; }

        @Override
        public String toString() {
            return String.format("%s: %s", type, description);
        }
    }

    private final Map<String, String> ocrCorrections;        // [204 → 294]
    private final List<Integer> recoveredQuestions;          // [294]
    private final Map<String, String> elementReassignments;  // [elementId → newQuestionNumber]
    private final List<CorrectionLog> correctionLogs;        // 모든 교정 내역

    /**
     * 기본 생성자
     */
    public CorrectionResult() {
        this.ocrCorrections = new HashMap<>();
        this.recoveredQuestions = new ArrayList<>();
        this.elementReassignments = new HashMap<>();
        this.correctionLogs = new ArrayList<>();
    }

    /**
     * 전체 생성자
     *
     * @param ocrCorrections OCR 오류 교정 맵 (잘못된 번호 → 올바른 번호)
     * @param recoveredQuestions 복구된 문제 번호 리스트
     * @param elementReassignments 요소 재할당 맵 (요소 ID → 새 문제 번호)
     * @param correctionLogs 교정 로그 리스트
     */
    public CorrectionResult(
            Map<String, String> ocrCorrections,
            List<Integer> recoveredQuestions,
            Map<String, String> elementReassignments,
            List<CorrectionLog> correctionLogs) {
        this.ocrCorrections = ocrCorrections;
        this.recoveredQuestions = recoveredQuestions;
        this.elementReassignments = elementReassignments;
        this.correctionLogs = correctionLogs;
    }

    /**
     * OCR 교정 추가
     *
     * @param wrongNumber 잘못 인식된 번호 (예: "204")
     * @param correctNumber 올바른 번호 (예: "294")
     */
    public void addOCRCorrection(String wrongNumber, String correctNumber) {
        ocrCorrections.put(wrongNumber, correctNumber);
        correctionLogs.add(new CorrectionLog(
                "OCR_CORRECTION",
                String.format("%s → %s (OCR 오류 교정)", wrongNumber, correctNumber),
                wrongNumber,
                correctNumber
        ));
    }

    /**
     * 복구된 문제 추가
     *
     * @param questionNumber 복구된 문제 번호
     */
    public void addRecoveredQuestion(int questionNumber) {
        recoveredQuestions.add(questionNumber);
        correctionLogs.add(new CorrectionLog(
                "MISSING_RECOVERY",
                String.format("문제 %d번 복구", questionNumber),
                null,
                questionNumber
        ));
    }

    /**
     * 요소 재할당 추가
     *
     * @param elementId 요소 ID
     * @param newQuestionNumber 새 문제 번호
     */
    public void addElementReassignment(String elementId, String newQuestionNumber) {
        elementReassignments.put(elementId, newQuestionNumber);
        correctionLogs.add(new CorrectionLog(
                "REASSIGNMENT",
                String.format("요소 %s → 문제 %s번으로 재할당", elementId, newQuestionNumber),
                elementId,
                newQuestionNumber
        ));
    }

    // Getters
    public Map<String, String> getOcrCorrections() {
        return ocrCorrections;
    }

    public List<Integer> getRecoveredQuestions() {
        return recoveredQuestions;
    }

    public Map<String, String> getElementReassignments() {
        return elementReassignments;
    }

    public List<CorrectionLog> getCorrectionLogs() {
        return correctionLogs;
    }

    public boolean hasCorrections() {
        return !ocrCorrections.isEmpty() ||
               !recoveredQuestions.isEmpty() ||
               !elementReassignments.isEmpty();
    }

    /**
     * 교정 요약 생성
     *
     * @return 교정 요약 문자열
     */
    public String getSummary() {
        if (!hasCorrections()) {
            return "✅ 교정 불필요: 모든 검증 통과";
        }

        List<String> summary = new ArrayList<>();
        if (!ocrCorrections.isEmpty()) {
            summary.add(String.format("%d개 OCR 오류 교정", ocrCorrections.size()));
        }
        if (!recoveredQuestions.isEmpty()) {
            summary.add(String.format("%d개 문제 복구", recoveredQuestions.size()));
        }
        if (!elementReassignments.isEmpty()) {
            summary.add(String.format("%d개 요소 재할당", elementReassignments.size()));
        }

        return "🔧 교정 완료: " + String.join(", ", summary);
    }

    @Override
    public String toString() {
        return String.format("CorrectionResult{ocrCorrections=%d, recovered=%d, reassignments=%d, summary='%s'}",
                ocrCorrections.size(), recoveredQuestions.size(), elementReassignments.size(), getSummary());
    }
}
