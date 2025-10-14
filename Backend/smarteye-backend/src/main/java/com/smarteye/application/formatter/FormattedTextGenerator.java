package com.smarteye.application.formatter;

import com.smarteye.application.analysis.UnifiedAnalysisEngine.StructuredData;
import com.smarteye.shared.util.FormattedTextFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * FormattedText 생성 전담 클래스 (단일 책임 원칙)
 *
 * <p>이 클래스는 StructuredData를 입력받아 FormattedText를 생성합니다.
 * 다단 레이아웃 지원 및 XSS 방지 기능을 포함합니다.</p>
 *
 * <h2>핵심 기능</h2>
 * <ul>
 *   <li>StructuredData 기반 FormattedText 생성</li>
 *   <li>다단 레이아웃 자동 감지 및 처리</li>
 *   <li>XSS 방지 HTML 이스케이프</li>
 *   <li>Fallback 메커니즘 (structured_data 없을 때)</li>
 * </ul>
 *
 * @author SmartEye Development Team
 * @version 1.0
 * @since v0.5
 */
@Component
public class FormattedTextGenerator {

    private static final Logger logger = LoggerFactory.getLogger(FormattedTextGenerator.class);

    /**
     * StructuredData를 FormattedText로 변환 (Primary Path)
     *
     * @param structuredData UnifiedAnalysisEngine에서 생성된 구조화 데이터
     * @return FormattedText (다단 레이아웃 지원, HTML-safe)
     * @throws IllegalArgumentException structuredData가 null인 경우
     * @throws FormattedTextGenerationException FormattedText 생성 실패 시
     */
    public String generate(StructuredData structuredData) {
        if (structuredData == null) {
            throw new IllegalArgumentException("StructuredData는 null일 수 없습니다.");
        }

        logger.info("📝 FormattedText 생성 시작 - Primary Path (StructuredData 사용)");

        try {
            String formattedText = FormattedTextFormatter.format(structuredData);
            logger.info("✅ FormattedText 생성 성공: {}글자", formattedText.length());
            return formattedText;

        } catch (Exception e) {
            logger.error("❌ FormattedText 생성 실패: {}", e.getMessage(), e);
            throw new FormattedTextGenerationException("FormattedText 생성 실패", e);
        }
    }

    /**
     * CIM 데이터를 FormattedText로 변환 (Fallback Path)
     *
     * <p>structured_data가 없는 경우 사용됩니다.</p>
     *
     * @param cimData CIM 결과 데이터 (Map 형식)
     * @return FormattedText (기본 포맷팅)
     */
    public String generateWithFallback(Map<String, Object> cimData) {
        if (cimData == null || cimData.isEmpty()) {
            logger.warn("⚠️ CIM 데이터가 없습니다.");
            return "분석 데이터가 없습니다. 이미지를 다시 업로드해주세요.";
        }

        logger.info("🔄 FormattedText 생성 - Fallback Path (CIM 데이터 사용)");

        // structured_data 확인 시도
        Object structuredDataObj = cimData.get("structured_data");

        if (structuredDataObj instanceof StructuredData) {
            logger.info("✅ structured_data 발견 - Primary Path로 전환");
            return generate((StructuredData) structuredDataObj);
        }

        // Fallback: questions 기반 텍스트 생성
        return generateFromQuestions(cimData);
    }

    /**
     * questions 데이터에서 FormattedText 생성 (Fallback)
     *
     * @param cimData CIM 결과 데이터
     * @return FormattedText (questions 기반, XSS 방지)
     */
    private String generateFromQuestions(Map<String, Object> cimData) {
        StringBuilder formattedText = new StringBuilder();
        formattedText.append("=== 분석 결과 ===\n\n");

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questions = (List<Map<String, Object>>) cimData.get("questions");

            if (questions != null && !questions.isEmpty()) {
                for (Map<String, Object> question : questions) {
                    // 문제 번호
                    Object questionNumber = question.get("question_number");
                    if (questionNumber != null) {
                        formattedText.append(questionNumber).append(". ");
                    }

                    // 문제 텍스트 (XSS 방지)
                    String questionText = (String) question.get("question_text");
                    if (questionText != null && !questionText.trim().isEmpty()) {
                        String safeText = FormattedTextFormatter.escapeHtml(questionText);
                        formattedText.append(safeText).append("\n\n");
                    }

                    formattedText.append("---\n\n");
                }
            } else {
                formattedText.append("분석된 문제가 없습니다.\n");
            }

            logger.info("✅ Fallback FormattedText 생성 완료: {}글자", formattedText.length());
            return formattedText.toString();

        } catch (Exception e) {
            logger.error("❌ Fallback FormattedText 생성 실패: {}", e.getMessage(), e);
            return "분석 결과 추출 중 오류가 발생했습니다.";
        }
    }

    /**
     * FormattedText 생성 예외 클래스
     */
    public static class FormattedTextGenerationException extends RuntimeException {
        public FormattedTextGenerationException(String message) {
            super(message);
        }

        public FormattedTextGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
