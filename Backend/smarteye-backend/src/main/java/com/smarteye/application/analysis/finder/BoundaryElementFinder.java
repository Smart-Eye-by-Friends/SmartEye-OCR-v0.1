package com.smarteye.application.analysis.finder;

import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 문제 경계 요소 탐색 전략 인터페이스.
 *
 * <p>v0.7 Strategy Pattern 적용:</p>
 * <ul>
 *   <li>QUESTION_NUMBER → {@link QuestionNumberElementFinder}</li>
 *   <li>QUESTION_TYPE → {@link QuestionTypeElementFinder}</li>
 * </ul>
 *
 * <h2>사용 예제</h2>
 * <pre>{@code
 * BoundaryElementFinder finder = finderFactory.getFinder(questionIdentifier);
 * Optional<LayoutInfo> element = finder.find(questionIdentifier, y, layouts, ocrMap);
 * }</pre>
 *
 * @author SmartEye Development Team
 * @version 0.7
 * @since 2025-10-18
 */
public interface BoundaryElementFinder {

    /**
     * 문제 경계 요소를 찾습니다.
     *
     * <p>구현체는 다음 작업을 수행해야 합니다:</p>
     * <ol>
     *   <li>Y좌표 매칭 (±10px 허용 오차)</li>
     *   <li>클래스 타입 확인 (QUESTION_NUMBER 또는 QUESTION_TYPE)</li>
     *   <li>추가 검증 (OCR 텍스트 매칭, Layout ID 매칭 등)</li>
     * </ol>
     *
     * @param questionIdentifier 문제 식별자 ("003" 또는 "type_5_유형01")
     * @param questionY Y 좌표
     * @param layoutElements LAM 레이아웃 요소 목록
     * @param ocrMap OCR 결과 맵 (ID → OCRResult)
     * @return 찾은 레이아웃 요소, 없으면 Optional.empty()
     */
    Optional<LayoutInfo> find(
        String questionIdentifier,
        int questionY,
        List<LayoutInfo> layoutElements,
        Map<Integer, OCRResult> ocrMap
    );

    /**
     * 이 Finder가 주어진 문제 식별자를 지원하는지 확인합니다.
     *
     * @param questionIdentifier 문제 식별자
     * @return 지원하면 true
     */
    boolean supports(String questionIdentifier);
}
