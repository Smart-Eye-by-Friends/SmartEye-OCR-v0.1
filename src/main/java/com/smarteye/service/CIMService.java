package com.smarteye.service;

import com.smarteye.model.entity.AnalysisJob;
import com.smarteye.model.entity.LayoutBlock;
import com.smarteye.model.entity.TextBlock;
import com.smarteye.model.entity.CIMOutput;
import com.smarteye.repository.CIMOutputRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class CIMService {
    
    private final CIMOutputRepository cimOutputRepository;
    
    /**
     * Content Integration Module
     * LAM과 TSPM 결과를 통합하여 최종 결과 생성
     */
    public CIMOutput integrateResults(AnalysisJob layoutJob, AnalysisJob textJob) {
        log.info("CIM: LAM과 TSPM 결과 통합 시작 - LAM JobId: {}, TSPM JobId: {}", 
                layoutJob.getJobId(), textJob.getJobId());
        
        try {
            // 결과 통합 로직
            Map<String, Object> integratedData = performIntegration(layoutJob, textJob);
            
            // 통합 결과 생성
            CIMOutput cimOutput = CIMOutput.builder()
                    .analysisJob(layoutJob) // 주 분석 작업으로 레이아웃 작업 사용
                    .outputContent(integratedData.toString()) // JSON 문자열로 저장
                    .outputFormat("JSON")
                    .integrationMethod("LAM + TSPM Integration")
                    .build();
            
            // DB에 저장
            CIMOutput savedOutput = cimOutputRepository.save(cimOutput);
            log.info("CIM: 결과 통합 완료 - CIM Output ID: {}", savedOutput.getOutputId());
            
            return savedOutput;
            
        } catch (Exception e) {
            log.error("CIM: 결과 통합 실패", e);
            throw new RuntimeException("결과 통합 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    /**
     * 레거시 지원을 위한 Object 파라미터 메서드
     */
    public Object integrateResults(Object layoutResult, Object textResult) {
        log.info("CIM: 레거시 결과 통합 (Object 타입)");
        
        if (layoutResult instanceof AnalysisJob && textResult instanceof AnalysisJob) {
            return integrateResults((AnalysisJob) layoutResult, (AnalysisJob) textResult);
        }
        
        // 플레이스홀더 결과 반환
        Map<String, Object> result = new HashMap<>();
        result.put("status", "completed");
        result.put("message", "Legacy integration completed");
        result.put("layoutResult", layoutResult);
        result.put("textResult", textResult);
        result.put("timestamp", LocalDateTime.now());
        
        return result;
    }
    
    /**
     * 실제 통합 로직 수행
     */
    private Map<String, Object> performIntegration(AnalysisJob layoutJob, AnalysisJob textJob) {
        Map<String, Object> result = new HashMap<>();
        
        // 1. 기본 정보
        result.put("layoutJobId", layoutJob.getJobId());
        result.put("textJobId", textJob.getJobId());
        result.put("fileName", layoutJob.getOriginalFilename());
        result.put("integrationTime", LocalDateTime.now());
        
        // 2. 레이아웃 블록과 텍스트 블록 매핑
        List<LayoutBlock> layoutBlocks = layoutJob.getLayoutBlocks();
        List<TextBlock> textBlocks = textJob.getTextBlocks();
        
        List<Map<String, Object>> integratedBlocks = new ArrayList<>();
        
        // 각 레이아웃 블록에 대응하는 텍스트 블록 찾기
        for (LayoutBlock layoutBlock : layoutBlocks) {
            Map<String, Object> integratedBlock = new HashMap<>();
            integratedBlock.put("blockIndex", layoutBlock.getBlockIndex());
            integratedBlock.put("blockClass", layoutBlock.getClassName()); // className 필드 사용
            integratedBlock.put("coordinates", Map.of(
                "x1", layoutBlock.getX1(),
                "y1", layoutBlock.getY1(),
                "x2", layoutBlock.getX2(),
                "y2", layoutBlock.getY2(),
                "width", layoutBlock.getWidth(),
                "height", layoutBlock.getHeight()
            ));
            integratedBlock.put("confidence", layoutBlock.getConfidence());
            
            // 대응하는 텍스트 블록 찾기 (layoutBlock 참조로 매칭)
            Optional<TextBlock> matchingTextBlock = textBlocks.stream()
                    .filter(tb -> tb.getLayoutBlock() != null && 
                                 Objects.equals(tb.getLayoutBlock().getId(), layoutBlock.getId()))
                    .findFirst();
                    
            if (matchingTextBlock.isPresent()) {
                TextBlock textBlock = matchingTextBlock.get();
                integratedBlock.put("text", textBlock.getExtractedText());
                integratedBlock.put("textConfidence", textBlock.getConfidence());
                integratedBlock.put("processingMethod", textBlock.getProcessingMethod()); // language 대신 processingMethod 사용
                integratedBlock.put("hasText", true);
            } else {
                integratedBlock.put("hasText", false);
            }
            
            integratedBlocks.add(integratedBlock);
        }
        
        result.put("blocks", integratedBlocks);
        
        // 3. 통계 정보
        long blocksWithText = integratedBlocks.stream()
                .mapToLong(block -> (Boolean) block.get("hasText") ? 1 : 0)
                .sum();
                
        result.put("statistics", Map.of(
            "totalBlocks", layoutBlocks.size(),
            "blocksWithText", blocksWithText,
            "averageConfidence", layoutBlocks.stream()
                    .mapToDouble(LayoutBlock::getConfidence)
                    .average().orElse(0.0)
        ));
        
        return result;
    }
}
