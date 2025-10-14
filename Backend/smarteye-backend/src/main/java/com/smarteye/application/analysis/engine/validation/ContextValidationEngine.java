package com.smarteye.application.analysis.engine.validation;

import com.smarteye.application.analysis.UnifiedAnalysisEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 컨텍스트 검증 엔진 (v0.7 Phase 2)
 *
 * 역할:
 * - 1단계 초기 할당 결과를 검증
 * - 문제 번호 연속성 검증 (QuestionSequenceValidator)
 * - 공간 범위 타당성 검증 (SpatialRangeValidator)
 * - 검증 결과를 다음 단계(Phase 3: 지능형 교정)로 전달
 *
 * 파이프라인:
 * PHASE 1 (초기 할당) → PHASE 2 (컨텍스트 검증) → PHASE 3 (지능형 교정) → PHASE 4 (CIM 생성)
 *
 * 근거:
 * - 재설계 제안서 Section 5-A (4단계 파이프라인)
 * - P0 수정 실패 원인: 컨텍스트 검증 부재
 *
 * @author Claude Code (System Architect)
 * @since v0.7 (CIM 그룹핑 알고리즘 재설계)
 */
@Service
public class ContextValidationEngine {

    private static final Logger log = LoggerFactory.getLogger(ContextValidationEngine.class);

    private final QuestionSequenceValidator sequenceValidator;
    private final SpatialRangeValidator spatialValidator;

    @Autowired
    public ContextValidationEngine(QuestionSequenceValidator sequenceValidator,
                                    SpatialRangeValidator spatialValidator) {
        this.sequenceValidator = sequenceValidator;
        this.spatialValidator = spatialValidator;
    }

    /**
     * 컨텍스트 검증 메인 메서드
     *
     * 전략:
     * 1. 문제 번호 연속성 검증
     * 2. 공간 범위 충돌 검증
     * 3. 종합 검증 결과 반환
     *
     * @param questionStructures 1단계 초기 할당 결과
     * @return 종합 검증 결과
     */
    public ValidationResult validateContext(List<UnifiedAnalysisEngine.QuestionStructure> questionStructures) {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("🔍 PHASE 2: 컨텍스트 검증 시작");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (questionStructures == null || questionStructures.isEmpty()) {
            log.warn("⚠️ 검증 대상 문제가 없습니다.");
            return new ValidationResult(List.of(), List.of());
        }

        long startTime = System.currentTimeMillis();

        // Step 1: 문제 번호 추출
        List<Integer> questionNumbers = questionStructures.stream()
                .map(UnifiedAnalysisEngine.QuestionStructure::getQuestionNumber)
                .filter(num -> num != null)
                .collect(Collectors.toList());

        log.info("📋 검증 대상: {}개 문제 - {}", questionNumbers.size(), questionNumbers);

        // Step 2: 연속성 검증
        log.info("┌─ Step 1: 문제 번호 연속성 검증");
        ValidationResult sequenceResult = sequenceValidator.checkContinuity(questionNumbers);
        log.info("└─ 결과: {}", sequenceResult.getSummary());

        // Step 3: 공간 범위 검증
        log.info("┌─ Step 2: 공간 범위 충돌 검증");
        List<RangeConflict> rangeConflicts = spatialValidator.checkOverlap(questionStructures);
        log.info("└─ 결과: {}개 충돌 감지", rangeConflicts.size());

        // Step 4: 종합 결과 생성
        ValidationResult finalResult = new ValidationResult(
                sequenceResult.getSequenceGaps(),
                rangeConflicts
        );

        long elapsedTime = System.currentTimeMillis() - startTime;

        // 결과 요약 출력
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("📊 PHASE 2 검증 완료 ({}ms)", elapsedTime);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("  {}", finalResult.getSummary());

        if (!finalResult.isValid()) {
            printDetailedReport(finalResult);
        }

        return finalResult;
    }

    /**
     * 상세 검증 리포트 출력
     *
     * @param result 검증 결과
     */
    private void printDetailedReport(ValidationResult result) {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("📋 상세 검증 리포트");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // 연속성 Gap 리포트
        if (!result.getSequenceGaps().isEmpty()) {
            log.info("⚠️ 연속성 Gap: {}개", result.getSequenceGaps().size());
            for (SequenceGap gap : result.getSequenceGaps()) {
                log.info("  • {}", gap);

                // OCR 오류 가능성 표시
                if (sequenceValidator.isLikelyOCRError(gap)) {
                    log.info("    ⚡ OCR 오류 패턴 감지: {} → {} (교정 후보)",
                            gap.getAfter(), gap.getExpectedNext());
                }

                // 누락 번호 표시
                if (gap.getType() == SequenceGap.Type.FORWARD_GAP && !gap.getInferredNumbers().isEmpty()) {
                    log.info("    🔧 추론된 누락 번호: {}", gap.getInferredNumbers());
                }
            }
        }

        // 공간 충돌 리포트
        if (!result.getRangeConflicts().isEmpty()) {
            log.info("⚠️ 공간 충돌: {}개", result.getRangeConflicts().size());
            for (RangeConflict conflict : result.getRangeConflicts()) {
                log.info("  • {}", conflict);
                if (conflict.isSevere()) {
                    log.info("    🚨 심각한 충돌 (재할당 필요)");
                }
            }
        }

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 빠른 검증 (연속성만)
     *
     * @param questionNumbers 문제 번호 리스트
     * @return 연속성 검증 결과
     */
    public ValidationResult quickValidate(List<Integer> questionNumbers) {
        log.info("🔍 빠른 검증 (연속성만) - 문제: {}", questionNumbers);
        return sequenceValidator.checkContinuity(questionNumbers);
    }

    /**
     * 검증 결과가 교정 필요한지 판단
     *
     * @param result 검증 결과
     * @return true if 교정이 필요함
     */
    public boolean needsCorrection(ValidationResult result) {
        if (result.isValid()) {
            return false;
        }

        // OCR 오류 가능성이 있는 역순 Gap 존재
        boolean hasOCRErrors = !result.getReverseGaps().isEmpty();

        // 정방향 Gap (누락 문제) 존재
        boolean hasMissingQuestions = !result.getForwardGaps().isEmpty();

        // 심각한 공간 충돌 존재
        boolean hasSevereConflicts = result.getRangeConflicts().stream()
                .anyMatch(RangeConflict::isSevere);

        return hasOCRErrors || hasMissingQuestions || hasSevereConflicts;
    }
}
