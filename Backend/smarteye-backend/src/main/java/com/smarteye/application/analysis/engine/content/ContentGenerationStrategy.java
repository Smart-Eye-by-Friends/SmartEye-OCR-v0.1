package com.smarteye.application.analysis.engine.content;

import com.smarteye.application.analysis.UnifiedAnalysisEngine.AnalysisElement;
import com.smarteye.domain.layout.LayoutClass;

import java.util.List;

/**
 * 콘텐츠 생성 전략 인터페이스 (Strategy Pattern)
 *
 * <p><b>목적</b>: OCR 텍스트와 AI 설명 추출 전략을 분리하여 유연성 및 확장성 확보</p>
 *
 * <p><b>핵심 설계 원칙</b>:</p>
 * <ul>
 *   <li>Open/Closed Principle: 새 전략 추가 시 기존 코드 수정 불필요</li>
 *   <li>Strategy Pattern: 알고리즘 군을 정의하고 각각을 캡슐화</li>
 *   <li>Type-Safe: {@link LayoutClass} Enum과 연동</li>
 * </ul>
 *
 * <p><b>구현 전략</b>:</p>
 * <ul>
 *   <li>{@link VisualContentStrategy} - AI 설명 우선 추출 (figure, table, chart)</li>
 *   <li>{@link TextContentStrategy} - OCR 텍스트 우선 추출 (question_text, plain_text)</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * // Spring이 자동으로 모든 전략 구현체를 주입
 * @Autowired
 * private List<ContentGenerationStrategy> strategies;
 *
 * // 레이아웃 클래스에 맞는 전략 선택
 * for (ContentGenerationStrategy strategy : strategies) {
 *     if (strategy.supports(layoutClass)) {
 *         String content = strategy.generateContent(elements);
 *     }
 * }
 * }</pre>
 *
 * @see VisualContentStrategy
 * @see TextContentStrategy
 * @see LayoutClass
 * @since v0.5 (CIM Phase 1 P0)
 * @version 1.0
 */
public interface ContentGenerationStrategy {

    /**
     * 단일 요소에서 콘텐츠 추출
     *
     * <p><b>추출 우선순위</b>:</p>
     * <ul>
     *   <li>시각 요소: AI 설명 → OCR 텍스트 (fallback)</li>
     *   <li>텍스트 요소: OCR 텍스트만 (AI 설명 제외)</li>
     * </ul>
     *
     * @param element 분석 대상 요소 (LayoutInfo + OCR + AI 정보 포함)
     * @return 추출된 텍스트 (없으면 null 반환)
     */
    String extractContent(AnalysisElement element);

    /**
     * 여러 요소로부터 결합된 콘텐츠 생성
     *
     * <p><b>결합 규칙</b>:</p>
     * <ul>
     *   <li>시각 요소: 줄바꿈(\n)으로 연결 (가독성 우선)</li>
     *   <li>텍스트 요소: 공백( )으로 연결 (자연스러운 문장)</li>
     * </ul>
     *
     * @param elements 같은 레이아웃 클래스의 요소 리스트
     * @return 결합된 텍스트 (빈 문자열 가능)
     */
    String generateContent(List<AnalysisElement> elements);

    /**
     * 해당 레이아웃 클래스에 이 전략이 적용 가능한지 확인
     *
     * <p><b>판단 기준</b>:</p>
     * <ul>
     *   <li>{@link VisualContentStrategy}: {@code layoutClass.isVisual() == true}</li>
     *   <li>{@link TextContentStrategy}: {@code layoutClass.getCategory() == TEXTUAL}</li>
     * </ul>
     *
     * @param layoutClass 레이아웃 클래스 Enum
     * @return true: 적용 가능, false: 적용 불가
     */
    boolean supports(LayoutClass layoutClass);

    /**
     * 전략 우선순위 반환 (높을수록 우선 적용)
     *
     * <p><b>우선순위 규칙</b>:</p>
     * <ul>
     *   <li>10: 최고 우선순위 (자동 선택 전략)</li>
     *   <li>9: 시각 요소 전략 (AI 설명 우선)</li>
     *   <li>8: 텍스트 요소 전략 (OCR 텍스트 우선)</li>
     * </ul>
     *
     * @return 우선순위 (1-10)
     */
    int getPriority();
}
