package com.smarteye.application.analysis;

import com.smarteye.application.analysis.engine.PatternMatchingEngine;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CBHLS 전략 1단계: LAM 우선 + 신뢰도 검증 (v0.5 Enhanced)
 *
 * 문제 번호 추출 서비스
 * - LAM(Layout Analysis Module)의 question_number 분류를 최우선으로 신뢰
 * - OCR 신뢰도로 교차 검증
 * - 신뢰도 점수 기반 필터링 (가중 평균 방식)
 * - Fallback: 기존 패턴 매칭 엔진
 *
 * P0 Hotfix 개선 사항:
 * 1. OCR 텍스트 정제 로직 추가 (cleanOCRText)
 * 2. 패턴 매칭 유연화 (Tier 시스템)
 * 3. 신뢰도 계산 공식 개선 (가중 평균)
 *
 * @version 0.5-hotfix
 * @since 2025-10-05
 */
@Service
public class QuestionNumberExtractor {

    private static final Logger logger = LoggerFactory.getLogger(QuestionNumberExtractor.class);

    /** 신뢰도 임계값 (CBHLS 전략 명세서 기준 - v0.5 상향 조정) */
    private static final double CONFIDENCE_THRESHOLD = 0.70; // 0.65 → 0.70

    /** OCR 최소 신뢰도 임계값 */
    private static final double MIN_OCR_CONFIDENCE = 0.5;

    /** LAM 단독 사용 가능 최소 신뢰도 */
    private static final double LAM_HIGH_CONFIDENCE_THRESHOLD = 0.85;

    /** 가중 평균 가중치 (총합 1.0) */
    private static final double WEIGHT_LAM = 0.5;      // LAM 우선 (시각적 맥락)
    private static final double WEIGHT_OCR = 0.3;      // OCR 보조 (텍스트 검증)
    private static final double WEIGHT_PATTERN = 0.2;  // Pattern 최소 (휴리스틱)

    @Autowired
    private PatternMatchingEngine patternMatchingEngine;

    /**
     * 문제 위치 추출 (CBHLS 전략 구현)
     *
     * @param layoutElements LAM 분석 결과
     * @param ocrResults OCR 결과
     * @return 문제 번호 → Y 좌표 매핑
     */
    public Map<String, Integer> extractQuestionPositions(
            List<LayoutInfo> layoutElements,
            List<OCRResult> ocrResults) {

        long startTime = System.currentTimeMillis();
        logger.info("🔍 문제 번호 추출 시작 (v0.5-hotfix) - LAM: {}개, OCR: {}개",
                   layoutElements.size(), ocrResults.size());

        Map<String, QuestionCandidate> candidates = new HashMap<>();

        // Phase 1: LAM 기반 추출 + OCR 교차 검증
        extractFromLAMWithValidation(layoutElements, ocrResults, candidates);

        // Phase 2: Fallback - 기존 패턴 매칭 (LAM에서 발견하지 못한 경우)
        if (candidates.isEmpty()) {
            logger.warn("⚠️ LAM 기반 추출 실패 - Fallback: 패턴 매칭 실행");
            extractFromPatternMatching(ocrResults, candidates);
        }

        // 최종 결과 변환 (신뢰도 기반 필터링)
        Map<String, Integer> questionPositions = filterAndConvert(candidates);

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("✅ 문제 번호 추출 완료 - 발견: {}개 ({}ms)", questionPositions.size(), elapsed);

        return questionPositions;
    }

    /**
     * LAM 결과에서 question_number 분류 추출 + OCR 교차 검증
     */
    private void extractFromLAMWithValidation(
            List<LayoutInfo> layoutElements,
            List<OCRResult> ocrResults,
            Map<String, QuestionCandidate> candidates) {

        // OCR 결과를 ID로 매핑 (빠른 조회)
        Map<Integer, OCRResult> ocrMap = new HashMap<>();
        for (OCRResult ocr : ocrResults) {
            ocrMap.put(ocr.getId(), ocr);
        }

        // LAM에서 question_number 클래스 요소 검색
        for (LayoutInfo layout : layoutElements) {
            if (!"question_number".equals(layout.getClassName())) {
                continue; // question_number가 아니면 스킵
            }

            // LAM 신뢰도
            double lamConfidence = layout.getConfidence();

            // 대응하는 OCR 결과 찾기
            OCRResult correspondingOCR = ocrMap.get(layout.getId());
            if (correspondingOCR == null || correspondingOCR.getText() == null) {
                logger.debug("⚠️ LAM question_number (id={})에 대응하는 OCR 없음", layout.getId());
                continue;
            }

            // P0 Hotfix 1: OCR 텍스트 정제 (노이즈 제거)
            String ocrText = cleanOCRText(correspondingOCR.getText());
            double ocrConfidence = correspondingOCR.getConfidence();

            // 패턴 매칭으로 문제 번호 추출
            String questionNum = patternMatchingEngine.extractQuestionNumber(ocrText);
            if (questionNum == null) {
                logger.debug("⚠️ 패턴 매칭 실패 - OCR 텍스트: '{}'", ocrText);
                continue;
            }

            // P0 Hotfix 2: 패턴 매칭 점수 계산 (Tier 시스템)
            double patternScore = calculatePatternMatchScore(ocrText, questionNum);

            // P0 Hotfix 3: 신뢰도 점수 계산 (가중 평균 방식)
            double confidenceScore = calculateConfidenceScore(lamConfidence, ocrConfidence, patternScore);

            // Y 좌표 (문제 위치)
            int yCoordinate = layout.getBox()[1]; // y1

            // 후보 등록 또는 업데이트
            QuestionCandidate candidate = new QuestionCandidate(
                questionNum, yCoordinate, confidenceScore, "LAM+OCR"
            );

            // 동일 문제 번호가 이미 있으면 신뢰도 높은 것 선택
            candidates.merge(questionNum, candidate, (existing, newCand) ->
                newCand.confidenceScore > existing.confidenceScore ? newCand : existing
            );

            logger.trace("📍 LAM 후보: 문제 {}, Y={}, 신뢰도={} (LAM:{}, OCR:{}, 패턴:{})",
                        questionNum, yCoordinate,
                        String.format("%.3f", confidenceScore),
                        String.format("%.2f", lamConfidence),
                        String.format("%.2f", ocrConfidence),
                        String.format("%.2f", patternScore));
        }

        logger.info("🎯 LAM 기반 추출: {}개 후보 발견", candidates.size());
    }

    /**
     * Fallback: 기존 패턴 매칭 엔진 사용
     */
    private void extractFromPatternMatching(
            List<OCRResult> ocrResults,
            Map<String, QuestionCandidate> candidates) {

        for (OCRResult ocr : ocrResults) {
            if (ocr.getText() == null || ocr.getText().trim().isEmpty()) {
                continue;
            }

            // P0 Hotfix 1: OCR 텍스트 정제
            String ocrText = cleanOCRText(ocr.getText());
            double ocrConfidence = ocr.getConfidence();

            // OCR 신뢰도 필터링
            if (ocrConfidence < MIN_OCR_CONFIDENCE) {
                continue;
            }

            String questionNum = patternMatchingEngine.extractQuestionNumber(ocrText);
            if (questionNum == null) {
                continue;
            }

            // 패턴 매칭 기반 점수 (LAM 없으므로 OCR + 패턴만)
            double patternScore = calculatePatternMatchScore(ocrText, questionNum);

            // Fallback은 가중 평균 대신 곱셈 방식 유지 (보수적 평가)
            double confidenceScore = ocrConfidence * patternScore;

            int yCoordinate = ocr.getCoordinates()[1]; // y1

            QuestionCandidate candidate = new QuestionCandidate(
                questionNum, yCoordinate, confidenceScore, "PatternOnly"
            );

            // 신뢰도 높은 것 선택
            candidates.merge(questionNum, candidate, (existing, newCand) ->
                newCand.confidenceScore > existing.confidenceScore ? newCand : existing
            );

            logger.trace("📍 패턴 매칭 후보: 문제 {}, Y={}, 신뢰도={}",
                        questionNum, yCoordinate, String.format("%.3f", confidenceScore));
        }

        logger.info("🔄 Fallback 추출: {}개 후보 발견", candidates.size());
    }

    /**
     * P0 Hotfix 1: OCR 텍스트 정제 로직
     *
     * OCR 노이즈 제거 및 표준화
     * - "299..." → "299."
     * - "299 .  ." → "299."
     * - 불필요한 공백 제거
     *
     * @param text 원본 OCR 텍스트
     * @return 정제된 텍스트
     */
    private String cleanOCRText(String text) {
        if (text == null) {
            return "";
        }

        String cleaned = text.trim();

        // 연속된 마침표 정규화: "299..." → "299."
        cleaned = cleaned.replaceAll("(\\d+)\\.{2,}", "$1.");

        // 숫자 뒤 공백+마침표 정규화: "299 .  ." → "299."
        // 숫자 다음에 오는 모든 공백과 점 조합을 단일 점으로 변환
        cleaned = cleaned.replaceAll("(\\d+)[\\s\\.]+", "$1.");

        // 불필요한 공백 제거
        cleaned = cleaned.replaceAll("\\s+", " ");

        logger.trace("OCR 텍스트 정제: '{}' → '{}'", text.trim(), cleaned);

        return cleaned;
    }

    /**
     * P0 Hotfix 2: 패턴 매칭 점수 계산 (Tier 시스템)
     *
     * Tier 1 (1.0): 완전 일치 패턴 (1번, [1], 【1】, <1>, 문제 1, 문제1)
     * Tier 2 (0.9): 높은 일치 패턴 (Q1, 문1)
     * Tier 3 (0.8): 중간 일치 패턴 (1., 1-1)
     * Tier 4 (0.5): 부분 일치 (1번 포함, [1] 포함)
     * Tier 5 (0.3): 저밀도 (단순 숫자 포함, False Positive 방지)
     *
     * @param ocrText OCR 원본 텍스트
     * @param extractedNumber 추출된 문제 번호
     * @return 매칭 점수 (0.0 ~ 1.0)
     */
    private double calculatePatternMatchScore(String ocrText, String extractedNumber) {
        if (ocrText == null || extractedNumber == null) {
            return 0.0;
        }

        String cleanText = ocrText.trim();

        // Tier 1: 완전 일치 패턴 (점수 1.0)
        if (cleanText.matches("^\\s*" + extractedNumber + "번\\s*$") ||
            cleanText.matches("^\\s*\\[" + extractedNumber + "\\]\\s*$") ||
            cleanText.matches("^\\s*【" + extractedNumber + "】\\s*$") ||
            cleanText.matches("^\\s*<" + extractedNumber + ">\\s*$") ||
            cleanText.matches("^\\s*문제\\s*" + extractedNumber + "\\s*$") ||
            cleanText.matches("^\\s*문제" + extractedNumber + "\\s*$")) {
            return 1.0;
        }

        // Tier 2: 높은 일치 패턴 (점수 0.9)
        if (cleanText.matches("^\\s*Q\\s*" + extractedNumber + "\\s*$") ||
            cleanText.matches("^\\s*문" + extractedNumber + "\\s*$")) {
            return 0.9;
        }

        // Tier 3: 중간 일치 패턴 (점수 0.8) - 유연화: 뒤에 추가 문자 허용
        if (cleanText.matches("^\\s*" + extractedNumber + "\\.+.*") ||
            cleanText.matches("^\\s*" + extractedNumber + "[-－]\\d+\\s*$")) {
            return 0.8;
        }

        // Tier 4: 부분 일치 패턴 (점수 0.5)
        if (cleanText.contains(extractedNumber + "번") ||
            cleanText.contains("[" + extractedNumber + "]") ||
            cleanText.contains(extractedNumber + ".")) {
            return 0.5;
        }

        // Tier 5: 저밀도 패턴 (점수 0.3) - False Positive 방지 강화
        if (cleanText.contains(extractedNumber)) {
            // 문맥 검증: 문제 번호가 아닐 가능성 체크
            if (cleanText.contains("정답") || cleanText.contains("명") ||
                cleanText.contains("개") || cleanText.contains("점") ||
                cleanText.contains("학년") || cleanText.contains("반") ||
                cleanText.contains("번호") || cleanText.contains("쪽")) {
                return 0.0; // 문제 번호 아님
            }
            return 0.3; // 낮은 매칭
        }

        return 0.0; // 매칭 실패
    }

    /**
     * P0 Hotfix 3: 신뢰도 점수 계산 (가중 평균 방식)
     *
     * 기존 곱셈 방식의 문제점:
     * - 하나의 요소가 낮으면 전체 점수 급격히 하락
     * - 예: LAM 0.85 × OCR 0.60 × Pattern 0.8 = 0.408 (임계값 0.65 미달)
     *
     * 신규 가중 평균 방식:
     * - LAM 50%, OCR 30%, Pattern 20% 가중치 적용
     * - 예: 0.5×0.85 + 0.3×0.60 + 0.2×0.8 = 0.735 (임계값 0.70 통과)
     *
     * @param lamConfidence LAM 신뢰도 (0.0 ~ 1.0)
     * @param ocrConfidence OCR 신뢰도 (0.0 ~ 1.0)
     * @param patternScore 패턴 매칭 점수 (0.0 ~ 1.0)
     * @return 통합 신뢰도 점수 (0.0 ~ 1.0)
     */
    private double calculateConfidenceScore(double lamConfidence,
                                           double ocrConfidence,
                                           double patternScore) {
        // 가중 평균 방식 (총합 1.0)
        double score = (WEIGHT_LAM * lamConfidence) +
                      (WEIGHT_OCR * ocrConfidence) +
                      (WEIGHT_PATTERN * patternScore);

        logger.trace("신뢰도 계산: LAM={}, OCR={}, Pattern={} → Score={} (가중 평균)",
                    String.format("%.2f", lamConfidence),
                    String.format("%.2f", ocrConfidence),
                    String.format("%.2f", patternScore),
                    String.format("%.3f", score));

        return score;
    }

    /**
     * 신뢰도 필터링 및 최종 맵 변환
     */
    private Map<String, Integer> filterAndConvert(Map<String, QuestionCandidate> candidates) {
        Map<String, Integer> result = new HashMap<>();
        int filteredCount = 0;

        for (Map.Entry<String, QuestionCandidate> entry : candidates.entrySet()) {
            String questionNum = entry.getKey();
            QuestionCandidate candidate = entry.getValue();

            // 신뢰도 임계값 검증
            if (candidate.confidenceScore >= CONFIDENCE_THRESHOLD) {
                result.put(questionNum, candidate.yCoordinate);
                logger.debug("✅ 문제 {} 채택: Y={}, 신뢰도={}, 소스={}",
                            questionNum, candidate.yCoordinate,
                            String.format("%.3f", candidate.confidenceScore),
                            candidate.source);
            } else {
                filteredCount++;
                logger.debug("❌ 문제 {} 필터링: 신뢰도={} < 임계값={}",
                            questionNum,
                            String.format("%.3f", candidate.confidenceScore),
                            String.format("%.2f", CONFIDENCE_THRESHOLD));
            }
        }

        if (filteredCount > 0) {
            logger.info("🔍 신뢰도 필터링: {}개 제외됨 (임계값: {})",
                       filteredCount, String.format("%.2f", CONFIDENCE_THRESHOLD));
        }

        return result;
    }

    /**
     * 문제 번호 후보 내부 클래스
     */
    private static class QuestionCandidate {
        String questionNumber;
        int yCoordinate;
        double confidenceScore;
        String source; // "LAM+OCR" or "PatternOnly"

        QuestionCandidate(String questionNumber, int yCoordinate,
                         double confidenceScore, String source) {
            this.questionNumber = questionNumber;
            this.yCoordinate = yCoordinate;
            this.confidenceScore = confidenceScore;
            this.source = source;
        }
    }
}
