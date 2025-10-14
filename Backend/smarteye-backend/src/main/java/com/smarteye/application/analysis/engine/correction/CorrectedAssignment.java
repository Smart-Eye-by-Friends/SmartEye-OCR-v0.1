package com.smarteye.application.analysis.engine.correction;

import com.smarteye.application.analysis.UnifiedAnalysisEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 교정된 할당 결과 데이터 클래스
 *
 * Phase 3 지능형 교정의 최종 결과를 통합하여 표현합니다.
 * OCR 교정, 누락 문제 복구, 요소 재할당의 모든 결과를 포함합니다.
 *
 * @author Claude Code (System Architect + Refactoring Expert)
 * @since v0.7 (CIM 그룹핑 알고리즘 재설계 - Phase 3)
 */
public class CorrectedAssignment {

    private final Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> assignments;
    private final CorrectionResult correctionResult;
    private final ReassignmentResult reassignmentResult;
    private final boolean corrected;

    /**
     * 전체 생성자
     *
     * @param assignments 최종 할당 맵 (문제 번호 → 요소 리스트)
     * @param correctionResult OCR 교정 및 누락 문제 복구 결과
     * @param reassignmentResult 요소 재할당 결과
     */
    public CorrectedAssignment(
            Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> assignments,
            CorrectionResult correctionResult,
            ReassignmentResult reassignmentResult) {
        this.assignments = assignments != null ? assignments : new HashMap<>();
        this.correctionResult = correctionResult != null ? correctionResult : new CorrectionResult();
        this.reassignmentResult = reassignmentResult != null ? reassignmentResult : new ReassignmentResult();
        this.corrected = this.correctionResult.hasCorrections() || this.reassignmentResult.hasReassignments();
    }

    /**
     * 교정 없이 원본 할당 유지 생성자
     *
     * @param originalAssignments 원본 할당 맵
     */
    public static CorrectedAssignment noCorrection(
            Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> originalAssignments) {
        return new CorrectedAssignment(originalAssignments, new CorrectionResult(), new ReassignmentResult());
    }

    // Getters
    public Map<String, List<UnifiedAnalysisEngine.AnalysisElement>> getAssignments() {
        return assignments;
    }

    public CorrectionResult getCorrectionResult() {
        return correctionResult;
    }

    public ReassignmentResult getReassignmentResult() {
        return reassignmentResult;
    }

    public boolean isCorrected() {
        return corrected;
    }

    /**
     * 전체 교정 요약 생성
     *
     * @return 교정 요약 문자열
     */
    public String getSummary() {
        if (!corrected) {
            return "✅ 교정 불필요: 모든 검증 통과";
        }

        List<String> summary = new ArrayList<>();

        if (correctionResult.hasCorrections()) {
            summary.add(correctionResult.getSummary());
        }

        if (reassignmentResult.hasReassignments()) {
            summary.add(reassignmentResult.getSummary());
        }

        return String.join(" | ", summary);
    }

    /**
     * 상세 교정 로그 생성
     *
     * @return 상세 로그 문자열
     */
    public String getDetailedLog() {
        StringBuilder log = new StringBuilder();

        log.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        log.append("📋 Phase 3 교정 상세 리포트\n");
        log.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        // OCR 교정 및 누락 문제 로그
        if (correctionResult.hasCorrections()) {
            log.append("\n🔧 OCR 교정 및 누락 문제 복구:\n");
            for (CorrectionResult.CorrectionLog corrLog : correctionResult.getCorrectionLogs()) {
                log.append(String.format("  • %s\n", corrLog));
            }
        }

        // 요소 재할당 로그
        if (reassignmentResult.hasReassignments()) {
            log.append("\n🔄 요소 재할당:\n");
            for (ReassignmentResult.ReassignmentLog reassignLog : reassignmentResult.getReassignmentLogs()) {
                log.append(String.format("  • %s\n", reassignLog));
            }
        }

        if (!corrected) {
            log.append("\n✅ 교정이 수행되지 않았습니다. 모든 검증 통과.\n");
        }

        log.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        return log.toString();
    }

    @Override
    public String toString() {
        return String.format("CorrectedAssignment{assignments=%d, corrected=%s, summary='%s'}",
                assignments.size(), corrected, getSummary());
    }
}
