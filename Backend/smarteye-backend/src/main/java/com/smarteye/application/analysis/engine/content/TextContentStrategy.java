package com.smarteye.application.analysis.engine.content;

import com.smarteye.application.analysis.UnifiedAnalysisEngine.AnalysisElement;
import com.smarteye.domain.layout.LayoutClass;
import com.smarteye.presentation.dto.OCRResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 텍스트 요소 콘텐츠 생성 전략 (OCR 텍스트 우선)
 *
 * <p><b>사용 대상</b>: 텍스트 요소 (question_text, plain_text, passage, list 등)</p>
 *
 * <p><b>추출 순서</b>:</p>
 * <ol>
 *   <li><b>1순위</b>: OCR 텍스트만 추출</li>
 *   <li><b>AI 설명 제외</b>: question_text는 순수 OCR만 포함</li>
 * </ol>
 *
 * <p><b>적용 조건</b>:</p>
 * <ul>
 *   <li>카테고리: TEXTUAL, EDUCATIONAL, STRUCTURAL</li>
 *   <li>{@code layoutClass.isOcrTarget() == true}</li>
 * </ul>
 *
 * <p><b>결합 규칙</b>:</p>
 * <ul>
 *   <li>여러 OCR 텍스트를 공백( )으로 연결 (자연스러운 문장)</li>
 *   <li>예: "다음 중" + "옳은 것은?" = "다음 중 옳은 것은?"</li>
 * </ul>
 *
 * <p><b>텍스트 정규화</b>:</p>
 * <ul>
 *   <li>연속된 공백 제거: "다음  중" → "다음 중"</li>
 *   <li>앞뒤 공백 제거: " 텍스트 " → "텍스트"</li>
 *   <li>문제 번호 패턴 제거 지원 (옵션)</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * List<AnalysisElement> textElements = ...; // question_text 클래스 요소들
 * String combined = textStrategy.generateContent(textElements);
 * // "다음 중 옳은 것은? 보기를 참고하여 답하시오."
 * }</pre>
 *
 * @see ContentGenerationStrategy
 * @see LayoutClass#isOcrTarget()
 * @since v0.5 (CIM Phase 1 P0)
 * @version 1.0
 */
@Component
public class TextContentStrategy implements ContentGenerationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(TextContentStrategy.class);

    /**
     * 최소 유효 OCR 텍스트 길이 (노이즈 필터링)
     */
    private static final int MIN_TEXT_LENGTH = 1;

    /**
     * 처리 중 메시지 필터 패턴
     */
    private static final String[] PROCESSING_PATTERNS = {
        "처리 중", "분석 중", "로딩 중", "대기 중",
        "Processing", "Analyzing", "Loading"
    };

    /**
     * OCR 텍스트만 추출 (AI 설명 제외)
     *
     * <p><b>추출 로직</b>:</p>
     * <ol>
     *   <li>OCR 결과 확인 → 유효성 검증 → 반환</li>
     *   <li>OCR 텍스트 없음 → null 반환 (AI 설명 사용하지 않음)</li>
     * </ol>
     *
     * <p><b>중요</b>: question_text는 순수 OCR만 포함되도록 AI 설명 fallback 제거됨</p>
     *
     * @param element 분석 요소
     * @return OCR 텍스트 (없으면 null)
     */
    @Override
    public String extractContent(AnalysisElement element) {
        if (element == null) {
            return null;
        }

        // OCR 텍스트만 사용 (AI 설명 fallback 없음)
        OCRResult ocrResult = element.getOcrResult();
        if (ocrResult != null && ocrResult.getText() != null) {
            String text = normalizeText(ocrResult.getText());
            if (isValidOCRText(text)) {
                Double confidence = ocrResult.getConfidence();
                logger.trace("📝 OCR 텍스트 추출 성공: {}자 (신뢰도: {:.2f})",
                            text.length(),
                            confidence != null ? confidence : 0.0);
                return text;
            }
        }

        // AI 설명은 사용하지 않음 (question_text 순수성 유지)
        logger.trace("❌ OCR 텍스트 없음 - null 반환");
        return null;
    }

    /**
     * 여러 OCR 텍스트를 공백으로 연결
     *
     * <p><b>결합 예시</b>:</p>
     * <pre>
     * 입력: ["다음 중", "옳은 것은?", "보기를 참고하시오."]
     * 출력: "다음 중 옳은 것은? 보기를 참고하시오."
     * </pre>
     *
     * @param elements 같은 레이아웃 클래스의 요소 리스트
     * @return 공백으로 연결된 OCR 텍스트
     */
    @Override
    public String generateContent(List<AnalysisElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return "";
        }

        List<String> texts = elements.stream()
            .map(this::extractContent)
            .filter(text -> text != null && !text.isEmpty())
            .collect(Collectors.toList());

        // OCR 텍스트는 공백으로 연결 (자연스러운 문장)
        String combined = String.join(" ", texts);

        // 최종 정규화
        combined = normalizeText(combined);

        if (logger.isDebugEnabled()) {
            logger.debug("📝 텍스트 콘텐츠 결합 완료: {} 요소 → {}자",
                        elements.size(), combined.length());
        }

        return combined;
    }

    /**
     * 텍스트 요소 (question_text, plain_text 등)에만 적용
     *
     * <p><b>적용 조건</b>:</p>
     * <ul>
     *   <li>카테고리가 TEXTUAL, EDUCATIONAL, STRUCTURAL 중 하나</li>
     *   <li>{@code layoutClass.isOcrTarget() == true}</li>
     *   <li>{@code layoutClass.isVisual() == false} (시각 요소 제외)</li>
     * </ul>
     *
     * @param layoutClass 레이아웃 클래스
     * @return true: 텍스트 요소, false: 시각 요소
     */
    @Override
    public boolean supports(LayoutClass layoutClass) {
        if (layoutClass == null) {
            return false;
        }

        // 시각 요소 제외 (AI 설명 혼입 방지)
        if (layoutClass.isVisual()) {
            return false;
        }

        // OCR 대상 텍스트 요소만 지원
        return layoutClass.isOcrTarget();
    }

    /**
     * 텍스트 요소에서는 높은 우선순위 (OCR 텍스트 필수)
     *
     * @return 8 (높은 우선순위)
     */
    @Override
    public int getPriority() {
        return 8; // 텍스트 요소에서는 OCR이 필수
    }

    /**
     * 유효한 OCR 텍스트인지 검증
     *
     * <p><b>검증 규칙</b>:</p>
     * <ul>
     *   <li>최소 길이: {@value #MIN_TEXT_LENGTH}자 이상</li>
     *   <li>처리 중 메시지 제외: "처리 중", "분석 중" 등</li>
     * </ul>
     *
     * @param text OCR 텍스트
     * @return true: 유효한 텍스트, false: 무효한 텍스트
     */
    private boolean isValidOCRText(String text) {
        if (text == null || text.length() < MIN_TEXT_LENGTH) {
            return false;
        }

        // 처리 중 메시지 필터링
        for (String pattern : PROCESSING_PATTERNS) {
            if (text.contains(pattern)) {
                logger.trace("⚠️ 처리 중 메시지 필터링: {}", text.substring(0, Math.min(20, text.length())));
                return false;
            }
        }

        return true;
    }

    /**
     * 텍스트 정규화 (공백 정리, 트림)
     *
     * <p><b>정규화 규칙</b>:</p>
     * <ul>
     *   <li>앞뒤 공백 제거</li>
     *   <li>연속된 공백 → 단일 공백</li>
     *   <li>탭, 줄바꿈 → 공백</li>
     * </ul>
     *
     * @param text 원본 텍스트
     * @return 정규화된 텍스트
     */
    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }

        // 1. 앞뒤 공백 제거
        text = text.trim();

        // 2. 탭, 줄바꿈 → 공백
        text = text.replaceAll("[\\t\\n\\r]+", " ");

        // 3. 연속된 공백 → 단일 공백
        text = text.replaceAll("\\s{2,}", " ");

        return text;
    }
}
