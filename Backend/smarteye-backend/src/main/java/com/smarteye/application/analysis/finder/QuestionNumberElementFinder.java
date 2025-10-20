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
 * QUESTION_NUMBER 요소 탐색 전략 구현체.
 *
 * <p>이 클래스는 question_number 레이아웃 요소를 찾는 전략을 구현합니다.</p>
 *
 * <h2>탐색 알고리즘</h2>
 * <ol>
 *   <li>Y좌표 매칭 (±10px 허용 오차)</li>
 *   <li>QUESTION_NUMBER 클래스 확인</li>
 *   <li>OCR 텍스트로 문제 번호 검증 (정규식)</li>
 * </ol>
 *
 * @author SmartEye Development Team
 * @version 0.7
 * @since 2025-10-18
 */
@Component
public class QuestionNumberElementFinder implements BoundaryElementFinder {

    private static final Logger logger = LoggerFactory.getLogger(QuestionNumberElementFinder.class);

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

        // question_type ID는 지원하지 않음
        if (QuestionTypeConstants.isQuestionTypeIdentifier(questionIdentifier)) {
            return Optional.empty();
        }

        for (LayoutInfo layout : layoutElements) {
            // 1. Y좌표 매칭 확인
            if (Math.abs(layout.getBox()[1] - questionY) > Y_TOLERANCE) {
                continue;
            }

            // 2. QUESTION_NUMBER 클래스 확인
            String className = layout.getClassName();
            if (!LayoutClass.QUESTION_NUMBER.getClassName().equals(className)) {
                continue;
            }

            // 3. OCR 텍스트로 검증
            OCRResult ocr = ocrMap.get(layout.getId());
            if (ocr != null && ocr.getText() != null) {
                String text = ocr.getText().trim();
                
                // 문제 번호 패턴 매칭: "1.", "1번", "Q1", "003" 등
                // 숫자 앞뒤에 특수문자가 있을 수 있음
                if (text.matches(".*" + questionIdentifier + "[.번)]?.*")) {
                    logger.debug("✅ question_number 요소 발견: {}, OCR='{}', X={}",
                               questionIdentifier, text, layout.getBox()[0]);
                    return Optional.of(layout);
                }
            }
        }

        logger.debug("⚠️ question_number 요소 '{}'를 찾지 못함 (Y={})", questionIdentifier, questionY);
        return Optional.empty();
    }

    @Override
    public boolean supports(String questionIdentifier) {
        // question_type ID가 아닌 모든 식별자 지원
        return !QuestionTypeConstants.isQuestionTypeIdentifier(questionIdentifier);
    }
}
