package com.smarteye.application.analysis;

import com.smarteye.application.analysis.dto.BoundaryType;
import com.smarteye.application.analysis.dto.QuestionBoundary;
import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 문제 경계 탐지기 (Question Boundary Detector)
 * 
 * ⚠️ v2.0: QuestionNumberExtractor를 대체하는 단순화된 버전
 * ⚠️ ColumnDetector 기능 제거 (순수 2D 거리 방식 채택)
 * 
 * 처리 순서 (대폭 단순화):
 * 1. LAM 결과에서 "question number", "question type" 추출 (X, Y 좌표)
 * 
 * ⚠️ **LAM 클래스명 표준화 주의**:
 * - "question number", "question type"은 띄어쓰기 형식 사용 (LAM Phase 1-4 완료)
 * - 언더스코어 형식("question_number", "question_type") 사용 시 모든 데이터 누락!
 * 
 * 특징:
 * - 신뢰도 검증 없음 (LAM 완전 신뢰)
 * - OCR 텍스트 정제 없음 (그대로 사용)
 * - 컬럼 감지 없음 (순수 좌표만 추출)
 * - 한 번의 순회로 모든 정보 수집
 * 
 * @version 2.0 (순수 2D 거리 방식)
 * @since 2025-10-20
 */
@Service
public class QuestionBoundaryDetector {

    private static final Logger logger = LoggerFactory.getLogger(QuestionBoundaryDetector.class);

    /**
     * 문제 경계 추출 (핵심 메서드)
     * 
     * LAM 결과에서 "question number", "question type" 요소를 추출하여
     * QuestionBoundary 리스트로 반환
     * 
     * @param layoutElements LAM 분석 결과
     * @param ocrResults OCR 결과
     * @return 문제 경계 리스트 (Y좌표로 정렬됨)
     */
    public List<QuestionBoundary> extractBoundaries(
            List<LayoutInfo> layoutElements,
            List<OCRResult> ocrResults) {
        
        long startTime = System.currentTimeMillis();
        logger.info("🔍 문제 경계 추출 시작 (v2.0 - 순수 2D) - LAM: {}개, OCR: {}개",
                   layoutElements.size(), ocrResults.size());

        // OCR 결과를 ID로 매핑
        Map<Integer, OCRResult> ocrMap = ocrResults.stream()
            .collect(Collectors.toMap(OCRResult::getId, ocr -> ocr, (a, b) -> a));

        List<QuestionBoundary> boundaries = new ArrayList<>();

        // LAM 결과에서 question number, question type 추출
        for (LayoutInfo layout : layoutElements) {
            String className = layout.getClassName();
            
            // ⚠️ LAM 클래스명 표준화: 띄어쓰기 형식 사용
            if ("question number".equals(className) || "question type".equals(className)) {
                
                // OCR 텍스트 가져오기
                OCRResult ocr = ocrMap.get(layout.getId());
                if (ocr == null) {
                    logger.warn("⚠️ OCR 결과 없음: ID={}, className={}", layout.getId(), className);
                    continue;
                }
                
                String ocrText = ocr.getText();
                if (ocrText == null || ocrText.trim().isEmpty()) {
                    logger.warn("⚠️ OCR 텍스트 없음: ID={}, className={}", layout.getId(), className);
                    continue;
                }
                
                // 경계 타입 결정
                BoundaryType type = "question number".equals(className) 
                    ? BoundaryType.QUESTION_NUMBER 
                    : BoundaryType.QUESTION_TYPE;
                
                // 좌표 정보 추출
                int[] box = layout.getBox(); // [x1, y1, x2, y2]
                int x = box[0];
                int y = box[1];
                int width = box[2] - box[0];
                int height = box[3] - box[1];
                
                // QuestionBoundary 생성
                // ⚠️ OCR 텍스트 그대로 사용 (정제 없음)
                QuestionBoundary boundary = new QuestionBoundary(
                    ocrText.trim(),        // identifier: OCR 텍스트 그대로
                    type,                  // type
                    x,                     // x
                    y,                     // y
                    width,                 // width
                    height,                // height
                    ocrText,               // ocrText: 원본 텍스트
                    layout.getConfidence(), // lamConfidence
                    layout.getId()         // elementId
                );
                
                boundaries.add(boundary);
                
                logger.debug("✅ 경계 추출: {}", boundary);
            }
        }

        // Y좌표로 정렬 (위에서 아래로)
        boundaries.sort((a, b) -> Integer.compare(a.getY(), b.getY()));

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("✅ 문제 경계 추출 완료: {}개 경계 ({}ms)", boundaries.size(), elapsed);
        
        // 추출된 경계 요약 로그
        if (logger.isDebugEnabled()) {
            logBoundarySummary(boundaries);
        }

        return boundaries;
    }

    /**
     * 추출된 경계 요약 로그 출력
     */
    private void logBoundarySummary(List<QuestionBoundary> boundaries) {
        long questionNumbers = boundaries.stream()
            .filter(b -> b.getType() == BoundaryType.QUESTION_NUMBER)
            .count();
        long questionTypes = boundaries.stream()
            .filter(b -> b.getType() == BoundaryType.QUESTION_TYPE)
            .count();
        
        logger.debug("📊 경계 요약: QUESTION_NUMBER={}개, QUESTION_TYPE={}개", 
                    questionNumbers, questionTypes);
        
        // Y좌표 범위
        if (!boundaries.isEmpty()) {
            int minY = boundaries.stream().mapToInt(QuestionBoundary::getY).min().orElse(0);
            int maxY = boundaries.stream().mapToInt(QuestionBoundary::getY).max().orElse(0);
            logger.debug("📏 Y좌표 범위: {}px ~ {}px (높이: {}px)", minY, maxY, maxY - minY);
        }
    }

    /**
     * 특정 타입의 경계만 필터링
     * 
     * @param boundaries 전체 경계 리스트
     * @param type 필터링할 타입
     * @return 필터링된 경계 리스트
     */
    public List<QuestionBoundary> filterByType(List<QuestionBoundary> boundaries, BoundaryType type) {
        return boundaries.stream()
            .filter(b -> b.getType() == type)
            .collect(Collectors.toList());
    }

    /**
     * 특정 식별자를 가진 경계 찾기
     * 
     * @param boundaries 전체 경계 리스트
     * @param identifier 찾을 식별자
     * @return 찾은 경계 (없으면 null)
     */
    public QuestionBoundary findByIdentifier(List<QuestionBoundary> boundaries, String identifier) {
        return boundaries.stream()
            .filter(b -> b.getIdentifier().equals(identifier))
            .findFirst()
            .orElse(null);
    }

    /**
     * Y좌표 범위 내의 경계 찾기
     * 
     * @param boundaries 전체 경계 리스트
     * @param minY 최소 Y좌표
     * @param maxY 최대 Y좌표
     * @return 범위 내 경계 리스트
     */
    public List<QuestionBoundary> findInYRange(List<QuestionBoundary> boundaries, int minY, int maxY) {
        return boundaries.stream()
            .filter(b -> b.getY() >= minY && b.getY() <= maxY)
            .collect(Collectors.toList());
    }
}
