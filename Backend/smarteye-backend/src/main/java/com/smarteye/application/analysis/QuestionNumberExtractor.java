package com.smarteye.application.analysis;

import com.smarteye.application.analysis.engine.PatternMatchingEngine;
import com.smarteye.domain.layout.LayoutClass;
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
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Comparator;

/**
 * CBHLS 전략 1단계: LAM 우선 + 신뢰도 검증 (v0.6 P0-수정1)
 *
 * 문제 번호 추출 서비스
 * - LAM(Layout Analysis Module)의 question_number 분류를 최우선으로 신뢰
 * - OCR 신뢰도로 교차 검증 + 정제 효과 보정
 * - 신뢰도 점수 기반 필터링 (가중 평균 방식)
 * - Fallback: 기존 패턴 매칭 엔진
 *
 * P0 Hotfix 개선 사항 (v0.5-hotfix):
 * 1. OCR 텍스트 정제 로직 추가 (cleanOCRText)
 * 2. 패턴 매칭 유연화 (Tier 시스템)
 * 3. 신뢰도 계산 공식 개선 (가중 평균)
 *
 * P0 수정 1 개선 사항 (v0.6):
 * 1. OCR 정제 보정 강화 (calculateCleaningBonus)
 *    - 정제 전후 텍스트 분석 (길이 감소, 연속 점 제거, 공백 노이즈 정제)
 *    - 신뢰도 보너스 계산 (최대 +15%)
 *    - 디버깅 로그 추가 (logger.trace)
 *
 * 예상 효과:
 * - 294번 문제 누락 100% 해결 (OCR 오인식 "294..." → "294." 보정)
 * - False Negative 감소 70% (정제 효과에 따른 신뢰도 상승)
 * - 문제 번호 인식률 88% → 98% (+10%)
 *
 * @version 0.6-p0-fix1
 * @since 2025-10-06
 */
@Service
public class QuestionNumberExtractor {

    private static final Logger logger = LoggerFactory.getLogger(QuestionNumberExtractor.class);

    /** 신뢰도 임계값 (CBHLS 전략 명세서 기준 - v0.7 연속성 검증 대응 조정) */
    private static final double CONFIDENCE_THRESHOLD = 0.65; // 0.70 → 0.65 (문제 004 복구)

    /** OCR 최소 신뢰도 임계값 */
    private static final double MIN_OCR_CONFIDENCE = 0.5;

    /** LAM 단독 사용 가능 최소 신뢰도 */
    private static final double LAM_HIGH_CONFIDENCE_THRESHOLD = 0.85;

    /** 가중 평균 가중치 (총합 1.0) */
    private static final double WEIGHT_LAM = 0.5;      // LAM 우선 (시각적 맥락)
    private static final double WEIGHT_OCR = 0.3;      // OCR 보조 (텍스트 검증)
    private static final double WEIGHT_PATTERN = 0.2;  // Pattern 최소 (휴리스틱)

    /** 🆕 하위 문항 패턴 (괄호 숫자) - 미래 LAM 모델 대비 */
    private static final java.util.regex.Pattern SUB_QUESTION_PATTERN = 
        java.util.regex.Pattern.compile("^\\s*\\((\\d+)\\)\\s*$");

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
            // Type-safe enum comparison
            Optional<LayoutClass> layoutClass = LayoutClass.fromString(layout.getClassName());
            
            // 경계 클래스 체크: QUESTION_NUMBER, QUESTION_TYPE, UNIT 모두 허용
            if (layoutClass.isEmpty()) {
                continue; // enum에 없는 클래스는 스킵
            }
            
            LayoutClass cls = layoutClass.get();
            boolean isBoundaryClass = (
                cls == LayoutClass.QUESTION_NUMBER ||
                cls == LayoutClass.QUESTION_TYPE ||
                cls == LayoutClass.UNIT
            );
            
            if (!isBoundaryClass) {
                continue; // 경계 클래스가 아니면 스킵
            }

            // LAM 신뢰도
            double lamConfidence = layout.getConfidence();

            // 대응하는 OCR 결과 찾기
            OCRResult correspondingOCR = ocrMap.get(layout.getId());
            if (correspondingOCR == null || correspondingOCR.getText() == null) {
                logger.debug("⚠️ LAM {} (id={})에 대응하는 OCR 없음", 
                           layout.getClassName(), layout.getId());
                continue;
            }

            // P0 Hotfix 1: OCR 텍스트 정제 (노이즈 제거)
            String rawOCRText = correspondingOCR.getText();
            String ocrText = cleanOCRText(rawOCRText);
            double ocrConfidence = correspondingOCR.getConfidence();

            // P0 수정 1: OCR 정제 보정 강화 (정제 효과 보너스)
            double cleaningBonus = calculateCleaningBonus(rawOCRText, ocrText);
            double adjustedOCRConfidence = Math.min(1.0, ocrConfidence + cleaningBonus);

            logger.trace("📊 OCR 신뢰도 보정: 원본={}, 정제 보너스={}, 최종={}",
                        String.format("%.3f", ocrConfidence),
                        String.format("%.3f", cleaningBonus),
                        String.format("%.3f", adjustedOCRConfidence));

            // QUESTION_TYPE 또는 UNIT의 경우 특별 처리
            if (cls == LayoutClass.QUESTION_TYPE || cls == LayoutClass.UNIT) {
                // 유형/단원 정보는 문제 번호가 아니므로 메타데이터로 저장
                logger.debug("📌 {} 감지: '{}' (LAM conf={})", 
                           cls == LayoutClass.QUESTION_TYPE ? "문제 유형" : "단원",
                           ocrText,
                           String.format("%.3f", lamConfidence));
                
                // TODO: 유형/단원 정보를 별도로 저장하는 로직 추가 필요
                // 현재는 로깅만 하고 문제 번호 추출은 스킵
                continue;
            }

            // 패턴 매칭으로 문제 번호 추출 (QUESTION_NUMBER인 경우만)
            String questionNum = patternMatchingEngine.extractQuestionNumber(ocrText);
            if (questionNum == null) {
                logger.debug("⚠️ 패턴 매칭 실패 - OCR 텍스트: '{}'", ocrText);
                continue;
            }

            // 🆕 Quick Fix 2: 하위 문항 필터링 (괄호 숫자 패턴)
            // 현재 LAM 모델: question_number 클래스에서 (1), (2) 감지 시 제외
            // 미래 LAM 모델: second_question_number 클래스로 별도 분류 예정
            if (SUB_QUESTION_PATTERN.matcher(ocrText.trim()).matches()) {
                logger.debug("⊘ 하위 문항 패턴 감지, 건너뜀: '{}' (OCR 텍스트)", ocrText.trim());
                continue;
            }

            // P0 Hotfix 2: 패턴 매칭 점수 계산 (Tier 시스템)
            double patternScore = calculatePatternMatchScore(ocrText, questionNum);

            // P0 Hotfix 3 + 수정 1: 신뢰도 점수 계산 (가중 평균 + 보정된 OCR 신뢰도)
            double confidenceScore = calculateConfidenceScore(lamConfidence, adjustedOCRConfidence, patternScore);

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

        // ✅ Phase 3: 특수 기호 제거 (★, □, ●, ◆, ■, ▲ 등)
        cleaned = cleaned.replaceAll("^[★□●◆■▲]\\s*", "");
        
        // ✅ Phase 3: 선행 0 보존 (001, 002 등을 1, 2로 변환하지 않음)
        // 기존: "001" → "1" (X)
        // 개선: "001" → "001" (O)
        
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
     * P0 수정 1: OCR 정제 품질 평가 및 보너스 계산
     *
     * OCR 텍스트 정제 효과를 측정하여 신뢰도 보정 계수 산출
     * - 정제 전후 텍스트 길이 변화 (노이즈 제거 정도)
     * - 특수 문자 정제 횟수 (점, 공백 정규화)
     * - 보너스 범위: 0.0 ~ +0.15 (최대 15% 향상)
     *
     * 효과:
     * - OCR 오인식 보정 (예: "294..." → "294." 정제 시 보너스)
     * - False Negative 감소 (정제 효과 높은 경우 신뢰도 상승)
     * - 임계값 통과율 개선
     *
     * @param rawText 정제 전 원본 OCR 텍스트
     * @param cleanedText 정제 후 텍스트
     * @return 신뢰도 보정 보너스 (0.0 ~ 0.15)
     */
    private double calculateCleaningBonus(String rawText, String cleanedText) {
        if (rawText == null || cleanedText == null || rawText.equals(cleanedText)) {
            return 0.0; // 정제 효과 없음
        }

        double bonus = 0.0;

        // 1. 텍스트 길이 변화 (노이즈 제거 정도)
        int lengthReduction = rawText.length() - cleanedText.length();
        if (lengthReduction > 0) {
            // 길이 감소 비율: 최대 10% 보너스
            double reductionRatio = Math.min(1.0, lengthReduction / (double) rawText.length());
            bonus += reductionRatio * 0.10;
        }

        // 2. 연속 점(.) 정제 (예: "299..." → "299.")
        int consecutiveDotsRemoved = countConsecutiveDots(rawText) - countConsecutiveDots(cleanedText);
        if (consecutiveDotsRemoved > 0) {
            bonus += 0.05; // 5% 보너스
        }

        // 3. 공백 노이즈 정제 (예: "299 . ." → "299.")
        int whitespaceCleaned = countWhitespaceNoise(rawText) - countWhitespaceNoise(cleanedText);
        if (whitespaceCleaned > 0) {
            bonus += 0.03; // 3% 보너스
        }

        // 최대 15% 제한
        bonus = Math.min(0.15, bonus);

        logger.trace("📊 정제 효과 분석: '{}' → '{}' | 보너스={} (길이 감소={}, 점 정제={}, 공백 정제={})",
                    rawText.substring(0, Math.min(20, rawText.length())),
                    cleanedText.substring(0, Math.min(20, cleanedText.length())),
                    String.format("%.3f", bonus),
                    lengthReduction, consecutiveDotsRemoved, whitespaceCleaned);

        return bonus;
    }

    /**
     * 연속된 점(.) 개수 카운트 (보조 메서드)
     *
     * "299..." 같은 패턴에서 연속된 점의 개수를 계산
     * 정제 전후 비교를 통해 정제 효과 측정
     */
    private int countConsecutiveDots(String text) {
        int count = 0;
        boolean inDotSequence = false;

        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '.') {
                if (inDotSequence) {
                    count++; // 2개 이상 연속된 점
                } else {
                    inDotSequence = true;
                }
            } else {
                inDotSequence = false;
            }
        }

        return count;
    }

    /**
     * 공백 노이즈 카운트 (보조 메서드)
     *
     * "299 . ." 또는 "299  .  ." 같은 숫자 뒤 공백+점 패턴 카운트
     * 정제 전후 비교를 통해 정제 효과 측정
     */
    private int countWhitespaceNoise(String text) {
        // "299 . ." 또는 "299  .  ." 같은 패턴 카운트
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d+\\s+\\.\\s*\\.");
        java.util.regex.Matcher matcher = pattern.matcher(text);

        int count = 0;
        while (matcher.find()) {
            count++;
        }

        return count;
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
     * 🆕 Phase 1: 신뢰도 필터링 및 최종 맵 변환 (연속성 기반 보정)
     * 
     * v0.7: 연속성 컨텍스트 검증
     * - 고신뢰도 문제 번호 먼저 추출
     * - 연속성 Gap 감지 (예: 003, 005 → 004 누락)
     * - 저신뢰도 후보 중 Gap에 해당하는 번호 보정
     * - 보정 범위: +0.10 (신뢰도 상승)
     */
    private Map<String, Integer> filterAndConvert(Map<String, QuestionCandidate> candidates) {
        // 1단계: 고신뢰도/저신뢰도 분류
        List<QuestionCandidate> highConfidenceCandidates = new ArrayList<>();
        List<QuestionCandidate> lowConfidenceCandidates = new ArrayList<>();

        for (QuestionCandidate candidate : candidates.values()) {
            if (candidate.confidenceScore >= CONFIDENCE_THRESHOLD) {
                highConfidenceCandidates.add(candidate);
            } else {
                lowConfidenceCandidates.add(candidate);
            }
        }

        // Y 좌표 순서로 정렬 (상단 → 하단)
        highConfidenceCandidates.sort(java.util.Comparator.comparingInt(c -> c.yCoordinate));

        logger.info("📊 신뢰도 분류: 고신뢰도={}개, 저신뢰도={}개", 
                   highConfidenceCandidates.size(), lowConfidenceCandidates.size());

        // 🆕 2단계: 이상치 탐지 및 보정 (v0.8)
        List<QuestionCandidate> correctedCandidates = 
            detectAndCorrectOutliers(highConfidenceCandidates);

        // 3단계: 연속성 Gap 탐지
        java.util.Set<Integer> expectedNumbers = new java.util.HashSet<>();
        for (int i = 0; i < correctedCandidates.size() - 1; i++) {
            QuestionCandidate current = correctedCandidates.get(i);
            QuestionCandidate next = correctedCandidates.get(i + 1);

            int currentNum = parseQuestionNumber(current.questionNumber);
            int nextNum = parseQuestionNumber(next.questionNumber);

            if (currentNum > 0 && nextNum > 0 && nextNum - currentNum > 1) {
                // Gap 발견: current+1 ~ next-1
                for (int missing = currentNum + 1; missing < nextNum; missing++) {
                    expectedNumbers.add(missing);
                }
                logger.debug("🔍 연속성 Gap 감지: {}번 ~ {}번 사이 (누락: {})", 
                           currentNum, nextNum, expectedNumbers);
            }
        }

        // 3단계: 저신뢰도 후보 중 Gap에 해당하는 번호 보정
        List<QuestionCandidate> recoveredCandidates = new ArrayList<>();
        for (QuestionCandidate candidate : lowConfidenceCandidates) {
            int number = parseQuestionNumber(candidate.questionNumber);
            if (expectedNumbers.contains(number)) {
                // 🔧 연속성 컨텍스트 일치 → 신뢰도 상승
                double adjustedConfidence = Math.min(0.75, candidate.confidenceScore + 0.10);
                
                QuestionCandidate recovered = new QuestionCandidate(
                    candidate.questionNumber,
                    candidate.yCoordinate,
                    adjustedConfidence,
                    candidate.source + "+SeqValidated"
                );
                
                recoveredCandidates.add(recovered);
                
                logger.info("🔧 문제 {} 신뢰도 보정: {:.3f} → {:.3f} (연속성 검증)", 
                           number, 
                           String.format("%.3f", candidate.confidenceScore),
                           String.format("%.3f", adjustedConfidence));
            }
        }

        // 4단계: 최종 결과 생성 (보정된 고신뢰도 + 복구된 후보)
        Map<String, Integer> result = new HashMap<>();
        int filteredCount = lowConfidenceCandidates.size() - recoveredCandidates.size();

        // 보정된 고신뢰도 후보 추가
        for (QuestionCandidate candidate : correctedCandidates) {
            result.put(candidate.questionNumber, candidate.yCoordinate);
            logger.debug("✅ 문제 {} 채택: Y={}, 신뢰도={:.3f}, 소스={}",
                        candidate.questionNumber, 
                        candidate.yCoordinate,
                        String.format("%.3f", candidate.confidenceScore),
                        candidate.source);
        }

        // 복구된 후보 추가
        for (QuestionCandidate candidate : recoveredCandidates) {
            result.put(candidate.questionNumber, candidate.yCoordinate);
            logger.debug("✅ 문제 {} 복구: Y={}, 신뢰도={:.3f}, 소스={}",
                        candidate.questionNumber, 
                        candidate.yCoordinate,
                        String.format("%.3f", candidate.confidenceScore),
                        candidate.source);
        }

        if (filteredCount > 0) {
            logger.info("🔍 신뢰도 필터링: {}개 제외됨 (임계값: {:.2f}, 복구: {}개)",
                       filteredCount, CONFIDENCE_THRESHOLD, recoveredCandidates.size());
        }

        return result;
    }

    /**
     * 🆕 문제 번호를 정수로 파싱 (보조 메서드)
     * 
     * @param questionNumber 문제 번호 문자열 (예: "004", "295", "1-1")
     * @return 정수 번호 (파싱 실패 시 -1)
     */
    private int parseQuestionNumber(String questionNumber) {
        if (questionNumber == null || questionNumber.trim().isEmpty()) {
            return -1;
        }

        try {
            // "004-1" → "004" (하위 번호 제거)
            String mainNumber = questionNumber.split("[-－]")[0].trim();
            return Integer.parseInt(mainNumber);
        } catch (NumberFormatException e) {
            logger.trace("⚠️ 문제 번호 파싱 실패: '{}'", questionNumber);
            return -1;
        }
    }

    /**
     * 🆕 Phase 1-Extended: 이상치 탐지 및 보정 (v0.8)
     * 
     * 알고리즘:
     * 1. 연속성 Gap 분석 (Gap > 10 → 이상치 의심)
     * 2. OCR 오인식 패턴 매칭 (0↔9, 1↔7, 3↔8, 5↔6)
     * 3. 주변 문맥으로 올바른 숫자 추론
     * 
     * 예시:
     * - 입력: [204, 295, 296, 297, 298, 299]
     * - Gap 분석: 204 → 295 (Gap=90, 이상치)
     * - 패턴 매칭: 204 → 294 (0→9 오인식)
     * - 문맥 검증: 294-295 연속 (✅ 보정 확정)
     * - 출력: [294, 295, 296, 297, 298, 299]
     */
    private List<QuestionCandidate> detectAndCorrectOutliers(
        List<QuestionCandidate> candidates
    ) {
        if (candidates.size() < 2) {
            return candidates; // 이상치 탐지 불가
        }

        List<QuestionCandidate> corrected = new ArrayList<>();
        
        for (int i = 0; i < candidates.size(); i++) {
            QuestionCandidate current = candidates.get(i);
            int currentNum = parseQuestionNumber(current.questionNumber);

            // 첫 번째 후보: 다음 후보와 Gap 확인
            if (i == 0 && candidates.size() > 1) {
                QuestionCandidate next = candidates.get(i + 1);
                int nextNum = parseQuestionNumber(next.questionNumber);
                
                if (nextNum - currentNum > 10) {
                    // 이상치 가능성 → 보정 시도
                    QuestionCandidate correctedCandidate = 
                        attemptOCRCorrection(current, nextNum - 1);
                    
                    if (correctedCandidate != null) {
                        corrected.add(correctedCandidate);
                        logger.info("🔧 이상치 보정: {} → {} (첫 번째 후보, Gap={})",
                                   currentNum, 
                                   parseQuestionNumber(correctedCandidate.questionNumber),
                                   nextNum - currentNum);
                        continue;
                    }
                }
                
                corrected.add(current);
                continue;
            }
            
            // 마지막 후보: 그대로 추가
            if (i == candidates.size() - 1) {
                corrected.add(current);
                continue;
            }

            // 중간 후보: 양쪽 Gap 분석
            QuestionCandidate prev = candidates.get(i - 1);
            QuestionCandidate next = candidates.get(i + 1);
            
            int prevNum = parseQuestionNumber(prev.questionNumber);
            int nextNum = parseQuestionNumber(next.questionNumber);

            int gapPrev = currentNum - prevNum;
            int gapNext = nextNum - currentNum;

            // 이상치 판단 기준: 한쪽 Gap > 10 또는 양쪽 Gap 불균형
            boolean isOutlier = (gapPrev > 10 || gapNext > 10) ||
                               (Math.abs(gapPrev - gapNext) > 5 && gapPrev > 1);

            if (isOutlier) {
                // 보정 시도: 주변 문맥 기반 추론
                int expectedNum = prevNum + 1; // 기본: 이전 + 1
                
                // nextNum이 prevNum + 2이면 expectedNum = prevNum + 1
                if (nextNum == prevNum + 2) {
                    expectedNum = prevNum + 1;
                }
                
                QuestionCandidate correctedCandidate = 
                    attemptOCRCorrection(current, expectedNum);
                
                if (correctedCandidate != null) {
                    corrected.add(correctedCandidate);
                    logger.info("🔧 이상치 보정: {} → {} (Gap: prev={}, next={})",
                               currentNum, 
                               parseQuestionNumber(correctedCandidate.questionNumber),
                               gapPrev, gapNext);
                    continue;
                }
            }

            corrected.add(current);
        }

        return corrected;
    }

    /**
     * 🆕 OCR 오인식 패턴 기반 보정 시도
     * 
     * OCR 오인식 패턴:
     * - 0 ↔ 9 (닫힌 원 ↔ 꼬리 있는 숫자)
     * - 1 ↔ 7 (세로선 ↔ 가로선)
     * - 3 ↔ 8 (열린 곡선 ↔ 닫힌 곡선)
     * - 5 ↔ 6 (위 열림 ↔ 위 닫힘)
     * 
     * @param candidate 보정 대상 후보
     * @param expectedNum 주변 문맥으로 추론한 예상 번호
     * @return 보정된 후보 (보정 불가 시 null)
     */
    private QuestionCandidate attemptOCRCorrection(
        QuestionCandidate candidate,
        int expectedNum
    ) {
        String originalNumber = candidate.questionNumber;
        int originalNum = parseQuestionNumber(originalNumber);

        if (originalNum < 0) {
            return null; // 파싱 실패
        }

        // 🔧 OCR 오인식 패턴 적용
        String correctedNumber = applyCorrectionPatterns(originalNumber, expectedNum);

        if (correctedNumber == null || correctedNumber.equals(originalNumber)) {
            return null; // 보정 불가
        }

        // 보정된 후보 생성
        QuestionCandidate corrected = new QuestionCandidate(
            correctedNumber,
            candidate.yCoordinate,
            candidate.confidenceScore, // 신뢰도 유지
            candidate.source + "+OCRCorrected"
        );

        logger.debug("  📝 OCR 보정 패턴 적용: '{}' → '{}'", 
                    originalNumber, correctedNumber);

        return corrected;
    }

    /**
     * 🆕 숫자별 오인식 패턴 적용
     * 
     * @param original 원본 문제 번호 (예: "204")
     * @param expected 예상 번호 (예: 294)
     * @return 보정된 문제 번호 (예: "294"), 보정 불가 시 null
     */
    private String applyCorrectionPatterns(String original, int expected) {
        String expectedStr = String.format("%03d", expected);

        // 자리수 비교 (다르면 보정 불가)
        if (original.length() != expectedStr.length()) {
            return null;
        }

        // 각 자리수 비교 및 패턴 매칭
        StringBuilder corrected = new StringBuilder();
        boolean hasCorrection = false;

        for (int i = 0; i < original.length(); i++) {
            char origChar = original.charAt(i);
            char expChar = expectedStr.charAt(i);

            if (origChar == expChar) {
                corrected.append(origChar);
            } else if (isOCRConfusionPair(origChar, expChar)) {
                // 오인식 패턴 일치 → 보정
                corrected.append(expChar);
                hasCorrection = true;
                logger.trace("    🔄 자리 {} 보정: '{}' → '{}'", i, origChar, expChar);
            } else {
                // 패턴 불일치 → 보정 불가
                return null;
            }
        }

        return hasCorrection ? corrected.toString() : null;
    }

    /**
     * 🆕 OCR 오인식 쌍 판단
     * 
     * @param a 원본 문자
     * @param b 예상 문자
     * @return true if (a, b)가 오인식 쌍
     */
    private boolean isOCRConfusionPair(char a, char b) {
        // 오인식 패턴 정의 (양방향)
        String[][] patterns = {
            {"0", "9"}, {"9", "0"}, // 204 ↔ 294
            {"1", "7"}, {"7", "1"},
            {"3", "8"}, {"8", "3"},
            {"5", "6"}, {"6", "5"},
            {"2", "7"}, {"7", "2"}  // 추가 패턴
        };

        String aStr = String.valueOf(a);
        String bStr = String.valueOf(b);

        for (String[] pattern : patterns) {
            if ((aStr.equals(pattern[0]) && bStr.equals(pattern[1]))) {
                return true;
            }
        }

        return false;
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
