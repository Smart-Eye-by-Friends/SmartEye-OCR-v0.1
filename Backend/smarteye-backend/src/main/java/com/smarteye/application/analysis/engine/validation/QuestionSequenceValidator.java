package com.smarteye.application.analysis.engine.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 문제 번호 연속성 검증기
 *
 * 역할:
 * - 문제 번호의 연속성 확인 (294 → 295 → 296...)
 * - 누락된 문제 번호 감지
 * - 불연속 패턴 경고 (294 → 204 → 295)
 * - OCR 오류 가능성이 있는 역순 감지
 *
 * 근거:
 * - 재설계 제안서 Section 6-A
 * - 실패 사례: 294 → "204" OCR 오류로 인한 연속성 위반
 *
 * @author Claude Code (System Architect)
 * @since v0.7 (CIM 그룹핑 알고리즘 재설계)
 */
@Component
public class QuestionSequenceValidator {

    private static final Logger log = LoggerFactory.getLogger(QuestionSequenceValidator.class);

    /**
     * 최대 허용 Gap (연속 번호 기대)
     */
    private static final int MAX_EXPECTED_GAP = 1;

    /**
     * 대형 점프 임계값
     */
    private static final int LARGE_JUMP_THRESHOLD = 10;

    /**
     * 문제 번호 연속성 검증
     *
     * 전략:
     * 1. 문제 번호 정렬
     * 2. 인접 번호 간 Gap 계산
     * 3. Gap 유형 분류 (FORWARD_GAP, REVERSE, LARGE_JUMP)
     * 4. 누락 번호 추론
     *
     * @param questionNumbers 감지된 문제 번호 리스트
     * @return 검증 결과 (Gap 리스트 포함)
     */
    public ValidationResult checkContinuity(List<Integer> questionNumbers) {
        if (questionNumbers == null || questionNumbers.isEmpty()) {
            log.warn("⚠️ 검증 대상 문제 번호가 없습니다.");
            return new ValidationResult(List.of());
        }

        if (questionNumbers.size() == 1) {
            log.info("✅ 단일 문제만 존재하여 연속성 검증 불필요");
            return new ValidationResult(List.of());
        }

        List<SequenceGap> gaps = new ArrayList<>();
        List<Integer> sorted = questionNumbers.stream()
                .sorted()
                .collect(Collectors.toList());

        log.info("🔍 연속성 검증 시작 - 문제 번호: {}", sorted);

        for (int i = 0; i < sorted.size() - 1; i++) {
            int current = sorted.get(i);
            int next = sorted.get(i + 1);
            int gap = next - current;

            // Case 1: 역순 감지 (예: 295 → 204)
            if (gap < 0) {
                SequenceGap reverseGap = new SequenceGap(
                        current,
                        next,
                        SequenceGap.Type.REVERSE,
                        String.format("역순 감지: %d → %d (OCR 오류 가능성, 실제: %d?)", current, next, current + 1)
                );
                gaps.add(reverseGap);
                log.warn("⚠️ {}", reverseGap.getDescription());
                continue;
            }

            // Case 2: 대형 점프 (예: 295 → 305)
            if (gap > LARGE_JUMP_THRESHOLD) {
                SequenceGap largeJump = new SequenceGap(
                        current,
                        next,
                        SequenceGap.Type.LARGE_JUMP,
                        String.format("대형 점프: %d → %d (%d칸 점프, 정상 불연속 가능성)", current, next, gap)
                );
                gaps.add(largeJump);
                log.warn("⚠️ {}", largeJump.getDescription());
                continue;
            }

            // Case 3: 연속성 위반 (예: 295 → 297, 296 누락)
            if (gap > MAX_EXPECTED_GAP) {
                List<Integer> inferredNumbers = inferMissingNumbers(current, next);
                SequenceGap forwardGap = new SequenceGap(
                        current,
                        next,
                        gap - 1,  // 누락 개수
                        inferredNumbers
                );
                gaps.add(forwardGap);
                log.warn("⚠️ {} - 추론된 누락 번호: {}", forwardGap.getDescription(), inferredNumbers);
            }
        }

        ValidationResult result = new ValidationResult(gaps);
        log.info("📊 연속성 검증 완료: {}", result.getSummary());

        return result;
    }

    /**
     * 누락 번호 추론
     *
     * 전략: 시작과 끝 사이의 모든 정수 생성
     * 예: inferMissingNumbers(295, 297) → [296]
     *
     * @param start 시작 번호
     * @param end 종료 번호
     * @return 추론된 누락 번호 리스트
     */
    private List<Integer> inferMissingNumbers(int start, int end) {
        List<Integer> missing = new ArrayList<>();
        for (int i = start + 1; i < end; i++) {
            missing.add(i);
        }
        return missing;
    }

    /**
     * 특정 Gap이 OCR 오류 가능성인지 판단
     *
     * OCR 오류 패턴:
     * - 2 ↔ 9 혼동: 294 → 204
     * - 1 ↔ 7 혼동: 197 → 107
     * - 3 ↔ 8 혼동: 385 → 335
     *
     * @param gap 검증할 Gap
     * @return true if OCR 오류 가능성이 높음
     */
    public boolean isLikelyOCRError(SequenceGap gap) {
        if (gap.getType() != SequenceGap.Type.REVERSE) {
            return false;
        }

        String beforeStr = String.valueOf(gap.getBefore());
        String afterStr = String.valueOf(gap.getAfter());

        // 같은 자리수이고, 한 자리만 다른 경우
        if (beforeStr.length() == afterStr.length()) {
            int differentDigits = 0;
            for (int i = 0; i < beforeStr.length(); i++) {
                if (beforeStr.charAt(i) != afterStr.charAt(i)) {
                    differentDigits++;
                }
            }

            // 한 자리만 다르면 OCR 오류 가능성
            if (differentDigits == 1) {
                log.info("🔍 OCR 오류 패턴 감지: {} → {} (한 자리 차이)", gap.getBefore(), gap.getAfter());
                return true;
            }
        }

        return false;
    }
}
