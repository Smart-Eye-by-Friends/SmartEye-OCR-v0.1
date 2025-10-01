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
import java.util.Optional;

/**
 * CBHLS 전략 1단계: LAM 우선 + 신뢰도 검증
 *
 * 문제 번호 추출 서비스
 * - LAM(Layout Analysis Module)의 question_number 분류를 최우선으로 신뢰
 * - OCR 신뢰도로 교차 검증
 * - 신뢰도 점수 기반 필터링
 * - Fallback: 기존 패턴 매칭 엔진
 */
@Service
public class QuestionNumberExtractor {

    private static final Logger logger = LoggerFactory.getLogger(QuestionNumberExtractor.class);

    /** 신뢰도 임계값 (CBHLS 전략 명세서 기준) */
    private static final double CONFIDENCE_THRESHOLD = 0.65;

    /** OCR 최소 신뢰도 임계값 */
    private static final double MIN_OCR_CONFIDENCE = 0.5;

    /** LAM 단독 사용 가능 최소 신뢰도 */
    private static final double LAM_HIGH_CONFIDENCE_THRESHOLD = 0.85;

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
        logger.info("🔍 문제 번호 추출 시작 - LAM: {}개, OCR: {}개",
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

            // OCR 텍스트 및 신뢰도
            String ocrText = correspondingOCR.getText().trim();
            double ocrConfidence = correspondingOCR.getConfidence();

            // 패턴 매칭으로 문제 번호 추출
            String questionNum = patternMatchingEngine.extractQuestionNumber(ocrText);
            if (questionNum == null) {
                logger.debug("⚠️ 패턴 매칭 실패 - OCR 텍스트: '{}'", ocrText);
                continue;
            }

            // 패턴 매칭 점수 계산
            double patternScore = calculatePatternMatchScore(ocrText, questionNum);

            // 신뢰도 점수 계산 (CBHLS 공식)
            double confidenceScore = lamConfidence * ocrConfidence * patternScore;

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

            logger.trace("📍 LAM 후보: 문제 {}, Y={}, 신뢰도={:.3f} (LAM:{:.2f}, OCR:{:.2f}, 패턴:{:.2f})",
                        questionNum, yCoordinate, confidenceScore,
                        lamConfidence, ocrConfidence, patternScore);
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

            String ocrText = ocr.getText().trim();
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
            double confidenceScore = ocrConfidence * patternScore;

            int yCoordinate = ocr.getCoordinates()[1]; // y1

            QuestionCandidate candidate = new QuestionCandidate(
                questionNum, yCoordinate, confidenceScore, "PatternOnly"
            );

            // 신뢰도 높은 것 선택
            candidates.merge(questionNum, candidate, (existing, newCand) ->
                newCand.confidenceScore > existing.confidenceScore ? newCand : existing
            );

            logger.trace("📍 패턴 매칭 후보: 문제 {}, Y={}, 신뢰도={:.3f}",
                        questionNum, yCoordinate, confidenceScore);
        }

        logger.info("🔄 Fallback 추출: {}개 후보 발견", candidates.size());
    }

    /**
     * 패턴 매칭 점수 계산
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

        // 완전 일치 패턴 (고밀도)
        if (cleanText.matches("^\\s*" + extractedNumber + "번\\s*$") ||
            cleanText.matches("^\\s*" + extractedNumber + "\\.\\s*$") ||
            cleanText.matches("^\\s*Q\\s*" + extractedNumber + "\\s*$") ||
            cleanText.matches("^\\s*문제\\s*" + extractedNumber + "\\s*$")) {
            return 1.0; // 완벽한 매칭
        }

        // 부분 일치 패턴 (중간밀도)
        if (cleanText.contains(extractedNumber + "번") ||
            cleanText.contains(extractedNumber + ".") ||
            cleanText.contains("Q" + extractedNumber) ||
            cleanText.contains(extractedNumber + ")")) {
            return 0.8; // 높은 매칭
        }

        // 저밀도 패턴 (단순 숫자 포함)
        if (cleanText.contains(extractedNumber)) {
            // 문맥 검증: 문제 번호가 아닐 가능성 체크
            if (cleanText.contains("정답") || cleanText.contains("명") ||
                cleanText.contains("개") || cleanText.contains("점")) {
                return 0.0; // 문제 번호 아님
            }
            return 0.5; // 낮은 매칭
        }

        return 0.0; // 매칭 실패
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
                logger.debug("✅ 문제 {} 채택: Y={}, 신뢰도={:.3f}, 소스={}",
                            questionNum, candidate.yCoordinate,
                            candidate.confidenceScore, candidate.source);
            } else {
                filteredCount++;
                logger.debug("❌ 문제 {} 필터링: 신뢰도={:.3f} < 임계값={:.2f}",
                            questionNum, candidate.confidenceScore, CONFIDENCE_THRESHOLD);
            }
        }

        if (filteredCount > 0) {
            logger.info("🔍 신뢰도 필터링: {}개 제외됨 (임계값: {:.2f})",
                       filteredCount, CONFIDENCE_THRESHOLD);
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
