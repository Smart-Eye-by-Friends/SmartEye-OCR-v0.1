package com.smarteye.application.analysis.engine.correction;

import com.smarteye.application.analysis.engine.validation.SequenceGap;
import com.smarteye.application.analysis.engine.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 누락 문제 복구 엔진
 *
 * <p>역할:</p>
 * <ul>
 *   <li>연속성 Gap을 기반으로 누락 문제 번호 추론</li>
 *   <li>OCR 오류 패턴 매칭 (2↔9, 1↔7, 0↔6 등)</li>
 *   <li>주변 문제 번호와의 연속성 기반 최적 교정 선택</li>
 * </ul>
 *
 * <p><strong>예시:</strong></p>
 * <pre>
 * 입력: [293, 295, 204, 296, 297] (204는 294의 OCR 오류)
 * 출력: CorrectionResult{ocrCorrections={"204" → "294"}}
 * </pre>
 *
 * @author Claude Code (System Architect + Refactoring Expert)
 * @since v0.7 (CIM 그룹핑 알고리즘 재설계 - Phase 3)
 */
@Component
public class MissingQuestionRecovery {

    private static final Logger logger = LoggerFactory.getLogger(MissingQuestionRecovery.class);

    /**
     * OCR 오류 패턴 매핑
     *
     * <p>흔한 OCR 오인식 패턴:</p>
     * <ul>
     *   <li>2 ↔ 9 (예: 204 → 294)</li>
     *   <li>1 ↔ 7 (예: 104 → 704)</li>
     *   <li>0 ↔ 6 (예: 205 → 265)</li>
     *   <li>3 ↔ 8 (예: 203 → 283)</li>
     * </ul>
     */
    private static final Map<Character, List<Character>> OCR_ERROR_PATTERNS = Map.of(
            '0', List.of('6', '9'),
            '1', List.of('7'),
            '2', List.of('9'),
            '3', List.of('8'),
            '6', List.of('0'),
            '7', List.of('1'),
            '8', List.of('3'),
            '9', List.of('0', '2')
    );

    /**
     * 검증 결과를 기반으로 누락 문제 복구
     *
     * <p>복구 전략:</p>
     * <ol>
     *   <li>REVERSE 타입 Gap → OCR 오류 교정 시도</li>
     *   <li>FORWARD_GAP 타입 → 누락 문제로 기록 (향후 복구)</li>
     *   <li>LARGE_JUMP 타입 → 정상 불연속으로 간주 (교정 불필요)</li>
     * </ol>
     *
     * @param validationResult 검증 결과 (SequenceGap 포함)
     * @return 교정 결과 (OCR 교정 맵, 복구된 문제 리스트 등)
     */
    public CorrectionResult recover(ValidationResult validationResult) {
        logger.info("🔧 PHASE 3: 지능형 교정 시작");
        logger.info("📋 검증 결과: {}", validationResult.getSummary());

        CorrectionResult result = new CorrectionResult();

        // Step 1: REVERSE Gap 처리 (OCR 오류 교정)
        List<SequenceGap> reverseGaps = validationResult.getReverseGaps();
        if (!reverseGaps.isEmpty()) {
            logger.info("┌─ Step 1: OCR 오류 교정 (REVERSE Gap)");
            processReverseGaps(reverseGaps, result);
            logger.info("└─ 결과: {}개 OCR 오류 교정", result.getOcrCorrections().size());
        }

        // Step 2: FORWARD_GAP 처리 (누락 문제 기록)
        List<SequenceGap> forwardGaps = validationResult.getForwardGaps();
        if (!forwardGaps.isEmpty()) {
            logger.info("┌─ Step 2: 누락 문제 기록 (FORWARD_GAP)");
            processForwardGaps(forwardGaps, result);
            logger.info("└─ 결과: {}개 누락 문제 기록", result.getRecoveredQuestions().size());
        }

        // Step 3: 교정 요약 로그
        logger.info("📊 PHASE 3 교정 완료");
        logger.info("  {}", result.getSummary());

        if (result.hasCorrections()) {
            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            logger.info("📋 상세 교정 리포트");
            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            for (CorrectionResult.CorrectionLog log : result.getCorrectionLogs()) {
                logger.info("  • {}", log);
            }
            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        }

        return result;
    }

    /**
     * REVERSE Gap 처리 (OCR 오류 교정)
     *
     * <p>처리 로직:</p>
     * <ol>
     *   <li>잘못된 번호(after)에 대해 OCR 오류 패턴 적용</li>
     *   <li>가능한 교정 후보 생성 (예: 204 → [294, 204, 284])</li>
     *   <li>이전 번호(before)와의 연속성 기반 최적 후보 선택</li>
     * </ol>
     *
     * @param reverseGaps REVERSE 타입 Gap 리스트
     * @param result 교정 결과 (누적)
     */
    private void processReverseGaps(List<SequenceGap> reverseGaps, CorrectionResult result) {
        for (SequenceGap gap : reverseGaps) {
            int before = gap.getBefore();
            int wrongNumber = gap.getAfter();
            int expectedNext = gap.getExpectedNext();

            logger.debug("⚠️ 역순 감지: {} → {} (기대: {})", before, wrongNumber, expectedNext);

            // OCR 오류 교정 시도
            Integer correctedNumber = correctOCRError(wrongNumber, before, expectedNext);

            if (correctedNumber != null) {
                result.addOCRCorrection(String.valueOf(wrongNumber), String.valueOf(correctedNumber));
                logger.info("    ⚡ OCR 오류 패턴 감지: {} → {} (교정 후보)", wrongNumber, correctedNumber);
            } else {
                logger.warn("    ⚠️ 교정 실패: {}번에 대한 적절한 후보를 찾지 못함", wrongNumber);
            }
        }
    }

    /**
     * FORWARD_GAP 처리 (누락 문제 기록)
     *
     * <p>현재는 누락 문제를 기록만 하고, 실제 복구는 향후 구현 예정</p>
     *
     * @param forwardGaps FORWARD_GAP 타입 Gap 리스트
     * @param result 교정 결과 (누적)
     */
    private void processForwardGaps(List<SequenceGap> forwardGaps, CorrectionResult result) {
        for (SequenceGap gap : forwardGaps) {
            List<Integer> missingNumbers = gap.getInferredNumbers();
            logger.debug("📝 누락 문제 기록: {} → {} (누락: {})", gap.getBefore(), gap.getAfter(), missingNumbers);

            for (Integer missingNumber : missingNumbers) {
                result.addRecoveredQuestion(missingNumber);
            }
        }
    }

    /**
     * OCR 오류 교정
     *
     * <p>교정 알고리즘:</p>
     * <ol>
     *   <li>잘못된 번호의 각 자리수에 대해 OCR 오류 패턴 적용</li>
     *   <li>가능한 모든 교정 후보 생성 (예: 204 → [294, 284, 264])</li>
     *   <li>이전 번호와의 연속성(expectedNext)에 가장 가까운 후보 선택</li>
     * </ol>
     *
     * @param wrongNumber 잘못 인식된 번호 (예: 204)
     * @param before 이전 문제 번호 (예: 295)
     * @param expectedNext 기대되는 다음 번호 (before + 1, 예: 296)
     * @return 교정된 번호 (예: 294), 교정 불가 시 null
     */
    private Integer correctOCRError(int wrongNumber, int before, int expectedNext) {
        // Step 1: 교정 후보 생성
        List<Integer> candidates = generateCorrectionCandidates(wrongNumber);

        if (candidates.isEmpty()) {
            return null;
        }

        logger.debug("    🔍 교정 후보 생성: {} → {}", wrongNumber, candidates);

        // Step 2: 연속성 기반 최적 후보 선택
        Integer bestCandidate = selectBestCandidate(candidates, before, expectedNext);

        if (bestCandidate != null) {
            logger.debug("    ✅ 최적 후보 선택: {} (연속성 점수 최고)", bestCandidate);
        }

        return bestCandidate;
    }

    /**
     * 교정 후보 생성
     *
     * <p>각 자리수에 대해 OCR 오류 패턴을 적용하여 가능한 모든 후보를 생성합니다.</p>
     *
     * <p><strong>예시:</strong></p>
     * <pre>
     * 입력: 204
     * 출력: [294, 284, 264] (2→9, 0→9, 0→6 패턴 적용)
     * </pre>
     *
     * @param wrongNumber 잘못 인식된 번호
     * @return 교정 후보 리스트 (중복 제거됨)
     */
    private List<Integer> generateCorrectionCandidates(int wrongNumber) {
        String wrongStr = String.valueOf(wrongNumber);
        Set<Integer> candidates = new HashSet<>();

        // 원본도 후보에 포함 (잘못된 인식이 아닐 가능성)
        candidates.add(wrongNumber);

        // 각 자리수에 대해 OCR 오류 패턴 적용
        for (int i = 0; i < wrongStr.length(); i++) {
            char digit = wrongStr.charAt(i);

            if (OCR_ERROR_PATTERNS.containsKey(digit)) {
                for (char replacement : OCR_ERROR_PATTERNS.get(digit)) {
                    String candidateStr = wrongStr.substring(0, i) +
                                          replacement +
                                          wrongStr.substring(i + 1);
                    try {
                        candidates.add(Integer.parseInt(candidateStr));
                    } catch (NumberFormatException e) {
                        // 무시 (잘못된 패턴)
                    }
                }
            }
        }

        return new ArrayList<>(candidates);
    }

    /**
     * 최적 후보 선택 (연속성 기반)
     *
     * <p>선택 전략:</p>
     * <ol>
     *   <li>expectedNext와 정확히 일치하는 후보 우선</li>
     *   <li>expectedNext - 2 <= candidate <= expectedNext + 2 범위 내 후보</li>
     *   <li>expectedNext와의 거리가 가장 가까운 후보</li>
     * </ol>
     *
     * @param candidates 교정 후보 리스트
     * @param before 이전 문제 번호
     * @param expectedNext 기대되는 다음 번호 (before + 1)
     * @return 최적 후보, 없으면 null
     */
    private Integer selectBestCandidate(List<Integer> candidates, int before, int expectedNext) {
        // Step 1: expectedNext와 정확히 일치하는 후보 찾기
        Optional<Integer> exactMatch = candidates.stream()
                .filter(c -> c == expectedNext)
                .findFirst();

        if (exactMatch.isPresent()) {
            return exactMatch.get();
        }

        // Step 2: 연속성 범위 내 후보 필터링 (expectedNext 전후 2칸)
        List<Integer> validCandidates = candidates.stream()
                .filter(c -> c >= expectedNext - 2 && c <= expectedNext + 2)
                .collect(Collectors.toList());

        if (validCandidates.isEmpty()) {
            return null;
        }

        // Step 3: expectedNext와의 거리가 가장 가까운 후보 선택
        return validCandidates.stream()
                .min(Comparator.comparingInt(c -> Math.abs(c - expectedNext)))
                .orElse(null);
    }
}
