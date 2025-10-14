package com.smarteye.application.analysis.engine.content;

import com.smarteye.application.analysis.UnifiedAnalysisEngine.AnalysisElement;
import com.smarteye.domain.layout.LayoutClass;
import com.smarteye.presentation.dto.AIDescriptionResult;
import com.smarteye.presentation.dto.OCRResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 시각 요소 콘텐츠 생성 전략 (AI 설명 우선)
 *
 * <p><b>사용 대상</b>: 시각 요소 (figure, table, chart, equation, diagram 등)</p>
 *
 * <p><b>추출 순서</b>:</p>
 * <ol>
 *   <li><b>1순위</b>: AI 설명 (Vision API 결과)</li>
 *   <li><b>2순위</b>: OCR 텍스트 (예: 표 내부 텍스트, 차트 레이블)</li>
 * </ol>
 *
 * <p><b>적용 조건</b>:</p>
 * <ul>
 *   <li>{@code layoutClass.isVisual() == true}</li>
 *   <li>카테고리: VISUAL, TABLE, FORMULA</li>
 * </ul>
 *
 * <p><b>결합 규칙</b>:</p>
 * <ul>
 *   <li>여러 AI 설명을 줄바꿈(\n)으로 연결 (가독성 향상)</li>
 *   <li>예: "그림 1 설명\n그림 2 설명\n표 1 설명"</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * List<AnalysisElement> figureElements = ...; // figure 클래스 요소들
 * String combined = visualStrategy.generateContent(figureElements);
 * // "피타고라스 정리를 보여주는 직각삼각형 도형\n좌표평면 상의 점 A, B, C"
 * }</pre>
 *
 * @see ContentGenerationStrategy
 * @see LayoutClass#isVisual()
 * @since v0.5 (CIM Phase 1 P0)
 * @version 1.0
 */
@Component
public class VisualContentStrategy implements ContentGenerationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(VisualContentStrategy.class);

    /**
     * 최소 유효 AI 설명 길이 (노이즈 필터링)
     */
    private static final int MIN_DESCRIPTION_LENGTH = 5;

    /**
     * 처리 중 메시지 필터 패턴
     */
    private static final String[] PROCESSING_PATTERNS = {
        "처리 중", "분석 중", "로딩 중", "대기 중",
        "Processing", "Analyzing", "Loading"
    };

    /**
     * AI 설명 우선, OCR 텍스트 fallback
     *
     * <p><b>추출 로직</b>:</p>
     * <ol>
     *   <li>AI 설명 확인 → 유효성 검증 → 반환</li>
     *   <li>AI 설명 없음 → OCR 텍스트 확인 → 반환</li>
     *   <li>둘 다 없음 → null 반환</li>
     * </ol>
     *
     * @param element 분석 요소
     * @return 추출된 콘텐츠 (없으면 null)
     */
    @Override
    public String extractContent(AnalysisElement element) {
        if (element == null) {
            return null;
        }

        // 1순위: AI 설명
        AIDescriptionResult aiResult = element.getAiResult();
        if (aiResult != null && aiResult.getDescription() != null) {
            String description = aiResult.getDescription().trim();
            if (isValidAIDescription(description)) {
                logger.trace("🤖 AI 설명 추출 성공: {}자", description.length());
                return description;
            }
        }

        // 2순위: OCR 텍스트 (예: 표 셀의 텍스트, 차트 레이블)
        OCRResult ocrResult = element.getOcrResult();
        if (ocrResult != null && ocrResult.getText() != null) {
            String text = ocrResult.getText().trim();
            if (!text.isEmpty()) {
                logger.trace("📝 OCR 텍스트 fallback: {}자", text.length());
                return text;
            }
        }

        logger.trace("❌ 시각 요소 콘텐츠 없음");
        return null;
    }

    /**
     * 여러 AI 설명을 줄바꿈으로 연결
     *
     * <p><b>결합 예시</b>:</p>
     * <pre>
     * 입력: ["그림 1: 삼각형", "그림 2: 원", "표 1: 데이터"]
     * 출력: "그림 1: 삼각형\n그림 2: 원\n표 1: 데이터"
     * </pre>
     *
     * @param elements 같은 레이아웃 클래스의 요소 리스트
     * @return 줄바꿈으로 연결된 AI 설명
     */
    @Override
    public String generateContent(List<AnalysisElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return "";
        }

        List<String> descriptions = elements.stream()
            .map(this::extractContent)
            .filter(text -> text != null && !text.isEmpty())
            .collect(Collectors.toList());

        // AI 설명은 줄바꿈으로 연결 (가독성 우선)
        String combined = String.join("\n", descriptions);

        if (logger.isDebugEnabled()) {
            logger.debug("🤖 시각 콘텐츠 결합 완료: {} 요소 → {}자",
                        elements.size(), combined.length());
        }

        return combined;
    }

    /**
     * 시각 요소 (figure, table, chart 등)에만 적용
     *
     * <p><b>적용 조건</b>:</p>
     * <ul>
     *   <li>카테고리가 VISUAL, TABLE, FORMULA 중 하나</li>
     *   <li>{@code layoutClass.isVisual() == true}</li>
     * </ul>
     *
     * @param layoutClass 레이아웃 클래스
     * @return true: 시각 요소, false: 텍스트 요소
     */
    @Override
    public boolean supports(LayoutClass layoutClass) {
        if (layoutClass == null) {
            return false;
        }

        // isVisual() 메서드 사용 (VISUAL, TABLE, FORMULA 카테고리)
        return layoutClass.isVisual();
    }

    /**
     * 시각 요소에서는 최고 우선순위 (AI 설명 필수)
     *
     * @return 9 (높은 우선순위)
     */
    @Override
    public int getPriority() {
        return 9; // 시각 요소에서는 AI 설명이 필수
    }

    /**
     * 유효한 AI 설명인지 검증
     *
     * <p><b>검증 규칙</b>:</p>
     * <ul>
     *   <li>최소 길이: {@value #MIN_DESCRIPTION_LENGTH}자 이상</li>
     *   <li>처리 중 메시지 제외: "처리 중", "분석 중" 등</li>
     * </ul>
     *
     * @param description AI 설명 텍스트
     * @return true: 유효한 설명, false: 무효한 설명
     */
    private boolean isValidAIDescription(String description) {
        if (description == null || description.length() < MIN_DESCRIPTION_LENGTH) {
            return false;
        }

        // 처리 중 메시지 필터링
        for (String pattern : PROCESSING_PATTERNS) {
            if (description.contains(pattern)) {
                logger.trace("⚠️ 처리 중 메시지 필터링: {}", description.substring(0, Math.min(20, description.length())));
                return false;
            }
        }

        return true;
    }
}
