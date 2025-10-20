package com.smarteye.application.analysis.finder;

import com.smarteye.domain.layout.LayoutClass;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.shared.constants.QuestionTypeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * QUESTION_TYPE 요소 탐색 전략 구현체.
 *
 * <p>이 클래스는 question_type 레이아웃 요소를 찾는 전략을 구현합니다.</p>
 *
 * <h2>탐색 알고리즘</h2>
 * <ol>
 *   <li>Y좌표 매칭 (±10px 허용 오차)</li>
 *   <li>QUESTION_TYPE 클래스 확인</li>
 *   <li>Layout ID로 question_type 식별자 검증</li>
 * </ol>
 *
 * <h2>ID 포맷</h2>
 * <pre>
 * type_{layoutId}_{sanitizedText}
 * 예: type_5_유형01, type_12_문제유형A
 * </pre>
 *
 * @author SmartEye Development Team
 * @version 0.7
 * @since 2025-10-18
 */
@Component
public class QuestionTypeElementFinder implements BoundaryElementFinder {

    private static final Logger logger = LoggerFactory.getLogger(QuestionTypeElementFinder.class);

    /**
     * Y좌표 허용 오차 (±10px).
     * A4 300dpi 기준 약 1mm 오차 허용.
     */
    private static final int Y_TOLERANCE = 10;

    @Override
    public Optional<LayoutInfo> find(
            String questionIdentifier,
            int questionY,
            List<LayoutInfo> layoutElements,
            Map<Integer, OCRResult> ocrMap) {

        // question_type ID가 아니면 지원하지 않음
        if (!QuestionTypeConstants.isQuestionTypeIdentifier(questionIdentifier)) {
            return Optional.empty();
        }

        // question_type ID에서 Layout ID 추출
        int expectedLayoutId;
        try {
            expectedLayoutId = QuestionTypeConstants.parseLayoutId(questionIdentifier);
        } catch (IllegalArgumentException e) {
            logger.warn("⚠️ 유효하지 않은 question_type ID: {}", questionIdentifier);
            return Optional.empty();
        }

        for (LayoutInfo layout : layoutElements) {
            // 1. Y좌표 매칭 확인
            if (Math.abs(layout.getBox()[1] - questionY) > Y_TOLERANCE) {
                continue;
            }

            // 2. QUESTION_TYPE 클래스 확인
            String className = layout.getClassName();
            if (!LayoutClass.QUESTION_TYPE.getClassName().equals(className)) {
                continue;
            }

            // 3. Layout ID로 매칭 (type_{layoutId}_{text} 형식)
            if (layout.getId() == expectedLayoutId) {
                OCRResult ocr = ocrMap.get(layout.getId());
                String ocrText = (ocr != null && ocr.getText() != null) ? 
                    ocr.getText() : "N/A";
                
                logger.debug("✅ question_type 요소 발견: ID={}, OCR='{}', X={}",
                           layout.getId(), ocrText, layout.getBox()[0]);
                return Optional.of(layout);
            }
        }

        logger.debug("⚠️ question_type 요소 '{}'를 찾지 못함 (Y={}, expectedLayoutId={})",
                   questionIdentifier, questionY, expectedLayoutId);
        return Optional.empty();
    }

    @Override
    public boolean supports(String questionIdentifier) {
        // question_type ID만 지원
        return QuestionTypeConstants.isQuestionTypeIdentifier(questionIdentifier);
    }
}
