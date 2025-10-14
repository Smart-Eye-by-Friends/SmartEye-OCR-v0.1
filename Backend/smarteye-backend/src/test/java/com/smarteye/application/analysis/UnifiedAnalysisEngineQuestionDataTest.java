package com.smarteye.application.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * UnifiedAnalysisEngine.QuestionData 단위 테스트
 * 
 * P1 개선 사항 검증:
 * - aiDescription 필드 추가
 * - getter/setter 정상 작동
 */
class UnifiedAnalysisEngineQuestionDataTest {

    @Test
    @DisplayName("QuestionData - aiDescription getter/setter 정상 작동")
    void testAiDescriptionGetterSetter() {
        // Given
        UnifiedAnalysisEngine.QuestionData questionData = new UnifiedAnalysisEngine.QuestionData();
        String testAiDescription = "이 그림은 간단한 뺄셈 문제들을 보여주고 있습니다.";
        
        // When
        questionData.setAiDescription(testAiDescription);
        
        // Then
        assertEquals(testAiDescription, questionData.getAiDescription(),
                    "aiDescription getter는 setter로 설정한 값을 반환해야 합니다");
    }
    
    @Test
    @DisplayName("QuestionData - aiDescription null 값 허용")
    void testAiDescriptionNullValue() {
        // Given
        UnifiedAnalysisEngine.QuestionData questionData = new UnifiedAnalysisEngine.QuestionData();
        
        // When
        questionData.setAiDescription(null);
        
        // Then
        assertNull(questionData.getAiDescription(),
                  "aiDescription은 null 값을 허용해야 합니다");
    }
    
    @Test
    @DisplayName("QuestionData - aiDescription 빈 문자열 허용")
    void testAiDescriptionEmptyString() {
        // Given
        UnifiedAnalysisEngine.QuestionData questionData = new UnifiedAnalysisEngine.QuestionData();
        
        // When
        questionData.setAiDescription("");
        
        // Then
        assertEquals("", questionData.getAiDescription(),
                    "aiDescription은 빈 문자열을 허용해야 합니다");
    }
    
    @Test
    @DisplayName("QuestionData - questionText와 aiDescription 독립성")
    void testQuestionTextAndAiDescriptionIndependence() {
        // Given
        UnifiedAnalysisEngine.QuestionData questionData = new UnifiedAnalysisEngine.QuestionData();
        String questionText = "그림을 보고 답하시오.";
        String aiDescription = "이 그림은 덧셈 문제를 보여줍니다.";
        
        // When
        questionData.setQuestionText(questionText);
        questionData.setAiDescription(aiDescription);
        
        // Then
        assertEquals(questionText, questionData.getQuestionText(),
                    "questionText는 독립적으로 설정되어야 합니다");
        assertEquals(aiDescription, questionData.getAiDescription(),
                    "aiDescription은 독립적으로 설정되어야 합니다");
        assertNotEquals(questionData.getQuestionText(), questionData.getAiDescription(),
                       "questionText와 aiDescription은 서로 다른 값을 가져야 합니다");
    }
    
    @Test
    @DisplayName("QuestionData - 긴 AI 설명 처리 (200자 이상)")
    void testLongAiDescription() {
        // Given
        UnifiedAnalysisEngine.QuestionData questionData = new UnifiedAnalysisEngine.QuestionData();
        StringBuilder longAiDescription = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            longAiDescription.append("이 그림은 간단한 뺄셈 문제들을 보여주고 있습니다. ");
        }
        String longText = longAiDescription.toString();
        
        // When
        questionData.setAiDescription(longText);
        
        // Then
        assertEquals(longText, questionData.getAiDescription(),
                    "aiDescription은 길이 제한 없이 원본 그대로 저장되어야 합니다");
        assertTrue(questionData.getAiDescription().length() > 200,
                  "200자 이상의 AI 설명도 잘라내지 않고 보존해야 합니다");
    }
    
    @Test
    @DisplayName("QuestionData - 기존 필드 호환성 (하위 호환)")
    void testBackwardCompatibility() {
        // Given
        UnifiedAnalysisEngine.QuestionData questionData = new UnifiedAnalysisEngine.QuestionData();
        
        // When - 기존 방식 (aiDescription 사용하지 않음)
        questionData.setQuestionNumber("295");
        questionData.setQuestionText("계산 하고, □ 안에 알맞은 수를 써넣으시오.");
        // aiDescription 설정 안 함
        
        // Then - aiDescription이 없어도 정상 작동
        assertEquals("295", questionData.getQuestionNumber());
        assertEquals("계산 하고, □ 안에 알맞은 수를 써넣으시오.", 
                    questionData.getQuestionText());
        assertNull(questionData.getAiDescription(),
                  "aiDescription을 설정하지 않으면 null이어야 합니다");
    }
}
