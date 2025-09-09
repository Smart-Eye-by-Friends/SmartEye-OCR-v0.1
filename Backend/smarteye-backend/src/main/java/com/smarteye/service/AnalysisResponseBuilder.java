package com.smarteye.service;

import com.smarteye.dto.AIDescriptionResult;
import com.smarteye.dto.AnalysisResponse;
import com.smarteye.dto.OCRResult;
import com.smarteye.dto.common.LayoutInfo;
import com.smarteye.entity.DocumentPage;
import com.smarteye.entity.LayoutBlock;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 분석 결과를 AnalysisResponse로 변환하는 서비스
 * DocumentAnalysisController에서 분리된 응답 생성 로직
 */
@Service
public class AnalysisResponseBuilder {
    
    /**
     * 기본 분석 결과로 AnalysisResponse 생성
     */
    public AnalysisResponse buildAnalysisResponse(
            String layoutImagePath, String jsonFilePath,
            List<LayoutInfo> layoutInfo,
            List<OCRResult> ocrResults, List<AIDescriptionResult> aiResults,
            Long timestamp) {
        
        AnalysisResponse response = new AnalysisResponse(true, "분석이 성공적으로 완료되었습니다.");
        response.setLayoutImageUrl(layoutImagePath);
        response.setJsonUrl(jsonFilePath);
        response.setOcrResults(ocrResults);
        response.setAiResults(aiResults);
        response.setTimestamp(timestamp);
        
        // OCR 텍스트 통합
        String combinedOcrText = ocrResults.stream()
            .map(result -> "[" + result.getClassName() + "]\n" + result.getText() + "\n\n")
            .collect(Collectors.joining());
        response.setOcrText(combinedOcrText.trim());
        
        // AI 설명 통합
        String combinedAiText = aiResults.stream()
            .map(result -> "[" + result.getClassName() + "]\n" + result.getDescription() + "\n\n")
            .collect(Collectors.joining());
        response.setAiText(combinedAiText.trim());
        
        // 통계 생성
        Map<String, Integer> classCounts = layoutInfo.stream()
            .collect(Collectors.groupingBy(
                LayoutInfo::getClassName,
                Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
            ));
            
        AnalysisResponse.AnalysisStats stats = new AnalysisResponse.AnalysisStats(
            layoutInfo.size(),
            ocrResults.size(), 
            aiResults.size(),
            classCounts
        );
        response.setStats(stats);
        
        return response;
    }
    
    /**
     * 다중 페이지 분석 결과를 통합하여 AnalysisResponse 생성
     * PDF나 다중 이미지 분석 결과용
     */
    public AnalysisResponse buildMultiplePageResponse(
            List<DocumentPage> documentPages, String baseTimestamp,
            String firstPageLayoutImagePath) {
        
        // 모든 페이지의 레이아웃 정보 통합
        List<LayoutInfo> allLayoutInfo = documentPages.stream()
            .flatMap(page -> page.getLayoutBlocks().stream())
            .map(this::convertLayoutBlockToLayoutInfo)
            .collect(Collectors.toList());
        
        // 모든 페이지의 OCR 결과 통합
        List<OCRResult> allOcrResults = documentPages.stream()
            .flatMap(page -> page.getLayoutBlocks().stream())
            .filter(block -> block.getOcrText() != null && !block.getOcrText().trim().isEmpty())
            .map(this::convertLayoutBlockToOCRResult)
            .collect(Collectors.toList());
        
        // 모든 페이지의 AI 결과 통합
        List<AIDescriptionResult> allAiResults = documentPages.stream()
            .flatMap(page -> page.getLayoutBlocks().stream())
            .filter(block -> block.getAiDescription() != null && !block.getAiDescription().trim().isEmpty())
            .map(this::convertLayoutBlockToAIResult)
            .collect(Collectors.toList());
        
        return buildAnalysisResponse(
            firstPageLayoutImagePath,
            null, // JSON 파일은 페이지별로 분산
            allLayoutInfo,
            allOcrResults,
            allAiResults,
            Long.parseLong(baseTimestamp)
        );
    }
    
    /**
     * 다중 이미지 처리 결과에 대한 요약 메시지 생성
     */
    public String buildMultipleImageMessage(int successCount, int failureCount, 
            List<String> failedImageNames, double processingTimeSeconds) {
        
        String resultMessage = String.format(
            "여러 이미지 분석 완료 - 성공: %d개, 실패: %d개, 처리 시간: %.2f초",
            successCount, failureCount, processingTimeSeconds
        );
        
        if (failureCount > 0 && !failedImageNames.isEmpty()) {
            resultMessage += String.format(" (실패한 이미지: %s)", 
                String.join(", ", failedImageNames)
            );
        }
        
        return resultMessage;
    }
    
    /**
     * PDF 다중 페이지 처리 결과에 대한 요약 메시지 생성
     */
    public String buildPDFAnalysisMessage(int totalPages, int totalLayoutElements, 
            int totalOcrResults, int totalAiResults) {
        
        return String.format(
            "PDF 다중 페이지 분석 완료 - 총 %d 페이지, 레이아웃: %d개, OCR: %d개, AI: %d개",
            totalPages, totalLayoutElements, totalOcrResults, totalAiResults
        );
    }
    
    /**
     * 다중 페이지 결과에서 통합된 포맷 텍스트 생성
     */
    public String buildCombinedFormattedText(List<DocumentPage> documentPages, int totalPages) {
        String combinedFormattedText = documentPages.stream()
            .map(DocumentPage::getAnalysisResult)
            .filter(text -> text != null && !text.trim().isEmpty())
            .collect(Collectors.joining("\n\n--- 페이지 구분 ---\n\n"));
        
        // 포맷된 텍스트가 없으면 기본 메시지 생성
        if (combinedFormattedText.trim().isEmpty()) {
            combinedFormattedText = String.format("다중 페이지 분석 완료 (%d 페이지)", totalPages);
        }
        
        return combinedFormattedText;
    }
    
    // === Private Helper Methods ===
    
    /**
     * LayoutBlock을 LayoutInfo로 변환
     */
    private LayoutInfo convertLayoutBlockToLayoutInfo(LayoutBlock block) {
        return new LayoutInfo(
            block.getId().intValue(),
            block.getClassName(),
            block.getConfidence() != null ? block.getConfidence() : 0.0,
            new int[]{block.getX1(), block.getY1(), block.getX2(), block.getY2()},
            block.getWidth() != null ? block.getWidth() : 0,
            block.getHeight() != null ? block.getHeight() : 0,
            block.getArea() != null ? block.getArea() : 0
        );
    }
    
    /**
     * LayoutBlock을 OCRResult로 변환
     */
    private OCRResult convertLayoutBlockToOCRResult(LayoutBlock block) {
        return new OCRResult(
            block.getId().intValue(),
            block.getClassName(),
            new int[]{block.getX1(), block.getY1(), block.getX2(), block.getY2()},
            block.getOcrText()
        );
    }
    
    /**
     * LayoutBlock을 AIDescriptionResult로 변환
     */
    private AIDescriptionResult convertLayoutBlockToAIResult(LayoutBlock block) {
        return new AIDescriptionResult(
            block.getId().intValue(),
            block.getClassName(),
            new int[]{block.getX1(), block.getY1(), block.getX2(), block.getY2()},
            block.getAiDescription()
        );
    }
}