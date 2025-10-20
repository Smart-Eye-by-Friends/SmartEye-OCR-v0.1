package com.smarteye.application.analysis.utils;

import com.smarteye.application.analysis.dto.E2ETestResult;
import com.smarteye.application.analysis.dto.GroundTruth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 정확도 측정 유틸리티 (Phase 7 - E2E Testing)
 * 
 * <p>Ground Truth와 실제 분석 결과를 비교하여 정확도를 계산합니다.</p>
 * 
 * <h3>측정 메트릭</h3>
 * <ul>
 *   <li><strong>Precision</strong>: 올바르게 할당된 요소 / 할당된 전체 요소</li>
 *   <li><strong>Recall</strong>: 올바르게 할당된 요소 / 할당되어야 할 전체 요소</li>
 *   <li><strong>F1-Score</strong>: 2 * (Precision * Recall) / (Precision + Recall)</li>
 *   <li><strong>Overall Accuracy</strong>: 올바르게 할당된 요소 / 전체 요소</li>
 * </ul>
 * 
 * <h3>사용 예시</h3>
 * <pre>{@code
 * GroundTruth groundTruth = loadGroundTruth("sample_001.json");
 * CIMOutput actualResult = performAnalysis(image);
 * 
 * E2ETestResult.AccuracyMetricsResult metrics = 
 *     AccuracyMetrics.calculateAccuracy(groundTruth, actualResult);
 * 
 * System.out.println("Overall Accuracy: " + metrics.getOverallAccuracy());
 * System.out.println("F1-Score: " + metrics.getF1Score());
 * }</pre>
 * 
 * @version 2.0
 * @since 2025-01-20
 * @see GroundTruth
 * @see E2ETestResult
 */
public class AccuracyMetrics {
    
    private static final Logger logger = LoggerFactory.getLogger(AccuracyMetrics.class);
    
    /**
     * Ground Truth와 실제 결과를 비교하여 정확도를 계산합니다.
     * 
     * @param groundTruth 정답 데이터
     * @param actualAssignments 실제 할당 맵 (elementId → questionId)
     * @return 정확도 메트릭
     */
    public static E2ETestResult.AccuracyMetricsResult calculateAccuracy(
            GroundTruth groundTruth,
            Map<String, String> actualAssignments) {
        
        logger.info("📊 정확도 계산 시작: imageId={}", groundTruth.getImageId());
        
        // 1. Ground Truth에서 예상 할당 맵 생성
        Map<String, String> expectedAssignments = buildExpectedAssignmentMap(groundTruth);
        logger.debug("예상 할당: {} 요소", expectedAssignments.size());
        logger.debug("실제 할당: {} 요소", actualAssignments.size());
        
        // 3. 정확도 메트릭 계산
        int correctAssignments = 0;
        int incorrectAssignments = 0;
        int missingAssignments = 0;
        
        Map<String, Double> perQuestionAccuracy = new HashMap<>();
        Map<String, Double> perElementTypeAccuracy = new HashMap<>();
        
        // 각 예상 할당에 대해 검증
        for (Map.Entry<String, String> entry : expectedAssignments.entrySet()) {
            String elementId = entry.getKey();
            String expectedQuestionId = entry.getValue();
            String actualQuestionId = actualAssignments.get(elementId);
            
            if (actualQuestionId == null) {
                // 할당되지 않음 (누락)
                missingAssignments++;
                logger.debug("❌ 누락: elementId={}, expected={}", elementId, expectedQuestionId);
            } else if (actualQuestionId.equals(expectedQuestionId)) {
                // 올바른 할당
                correctAssignments++;
                logger.debug("✅ 정확: elementId={}, questionId={}", elementId, actualQuestionId);
            } else {
                // 잘못된 할당
                incorrectAssignments++;
                logger.warn("⚠️ 오할당: elementId={}, expected={}, actual={}", 
                           elementId, expectedQuestionId, actualQuestionId);
            }
        }
        
        // 4. 메트릭 계산
        int totalElements = expectedAssignments.size();
        int totalAssigned = actualAssignments.size();
        
        double precision = totalAssigned > 0 
            ? (double) correctAssignments / totalAssigned 
            : 0.0;
        
        double recall = totalElements > 0 
            ? (double) correctAssignments / totalElements 
            : 0.0;
        
        double f1Score = (precision + recall) > 0 
            ? 2 * (precision * recall) / (precision + recall) 
            : 0.0;
        
        double overallAccuracy = totalElements > 0 
            ? (double) correctAssignments / totalElements 
            : 0.0;
        
        // 5. 문제별 정확도 계산
        calculatePerQuestionAccuracy(groundTruth, actualAssignments, perQuestionAccuracy);
        
        // 6. 요소 타입별 정확도 계산
        calculatePerElementTypeAccuracy(groundTruth, actualAssignments, perElementTypeAccuracy);
        
        logger.info("📊 정확도 계산 완료: Overall={:.2f}%, F1={:.2f}%, Precision={:.2f}%, Recall={:.2f}%",
                   overallAccuracy * 100, f1Score * 100, precision * 100, recall * 100);
        logger.info("📊 할당 통계: 정확={}, 오류={}, 누락={}, 전체={}",
                   correctAssignments, incorrectAssignments, missingAssignments, totalElements);
        
        return new E2ETestResult.AccuracyMetricsResult(
                overallAccuracy,
                precision,
                recall,
                f1Score,
                correctAssignments,
                incorrectAssignments,
                missingAssignments,
                totalElements,
                perQuestionAccuracy,
                perElementTypeAccuracy
        );
    }
    
    /**
     * Ground Truth에서 예상 할당 맵 생성
     * 
     * @param groundTruth 정답 데이터
     * @return elementId → expectedQuestionId 맵
     */
    private static Map<String, String> buildExpectedAssignmentMap(GroundTruth groundTruth) {
        Map<String, String> map = new HashMap<>();
        
        for (GroundTruth.QuestionGroundTruth question : groundTruth.getQuestions()) {
            for (GroundTruth.ElementGroundTruth element : question.getElements()) {
                String elementId = element.getId();
                String expectedQuestionId = element.getExpectedQuestionId();
                
                map.put(elementId, expectedQuestionId);
            }
        }
        
        return map;
    }
    
    
    /**
     * 문제별 정확도 계산
     * 
     * @param groundTruth 정답 데이터
     * @param actualAssignments 실제 할당 맵
     * @param perQuestionAccuracy 문제별 정확도 (출력)
     */
    private static void calculatePerQuestionAccuracy(
            GroundTruth groundTruth,
            Map<String, String> actualAssignments,
            Map<String, Double> perQuestionAccuracy) {
        
        for (GroundTruth.QuestionGroundTruth question : groundTruth.getQuestions()) {
            String questionId = question.getIdentifier();
            int totalElements = question.getElements().size();
            int correctElements = 0;
            
            for (GroundTruth.ElementGroundTruth element : question.getElements()) {
                String elementId = element.getId();
                String expectedQuestionId = element.getExpectedQuestionId();
                String actualQuestionId = actualAssignments.get(elementId);
                
                if (actualQuestionId != null && actualQuestionId.equals(expectedQuestionId)) {
                    correctElements++;
                }
            }
            
            double accuracy = totalElements > 0 
                ? (double) correctElements / totalElements 
                : 0.0;
            
            perQuestionAccuracy.put(questionId, accuracy);
        }
    }
    
    /**
     * 요소 타입별 정확도 계산
     * 
     * @param groundTruth 정답 데이터
     * @param actualAssignments 실제 할당 맵
     * @param perElementTypeAccuracy 요소 타입별 정확도 (출력)
     */
    private static void calculatePerElementTypeAccuracy(
            GroundTruth groundTruth,
            Map<String, String> actualAssignments,
            Map<String, Double> perElementTypeAccuracy) {
        
        Map<String, Integer> totalByType = new HashMap<>();
        Map<String, Integer> correctByType = new HashMap<>();
        
        for (GroundTruth.QuestionGroundTruth question : groundTruth.getQuestions()) {
            for (GroundTruth.ElementGroundTruth element : question.getElements()) {
                String elementType = element.getType();
                String elementId = element.getId();
                String expectedQuestionId = element.getExpectedQuestionId();
                String actualQuestionId = actualAssignments.get(elementId);
                
                totalByType.put(elementType, totalByType.getOrDefault(elementType, 0) + 1);
                
                if (actualQuestionId != null && actualQuestionId.equals(expectedQuestionId)) {
                    correctByType.put(elementType, correctByType.getOrDefault(elementType, 0) + 1);
                }
            }
        }
        
        for (String elementType : totalByType.keySet()) {
            int total = totalByType.get(elementType);
            int correct = correctByType.getOrDefault(elementType, 0);
            double accuracy = total > 0 ? (double) correct / total : 0.0;
            
            perElementTypeAccuracy.put(elementType, accuracy);
        }
    }
}
