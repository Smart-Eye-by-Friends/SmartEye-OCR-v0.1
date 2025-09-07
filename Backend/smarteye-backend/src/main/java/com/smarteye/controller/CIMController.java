package com.smarteye.controller;

import com.smarteye.dto.CIMStatusResponse;
import com.smarteye.dto.IntegrateRequest;
import com.smarteye.dto.StructuredCIMResponse;
import com.smarteye.entity.CIMOutput;
import com.smarteye.exception.CIMGenerationException;
import com.smarteye.repository.CIMOutputRepository;
import com.smarteye.service.CIMService;
import com.smarteye.util.JsonUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cim")
@Tag(name = "CIM", description = "구조화된 학습지 분석 API")
@CrossOrigin(origins = "*")
public class CIMController {
    
    private static final Logger logger = LoggerFactory.getLogger(CIMController.class);
    
    @Autowired
    private CIMService cimService;
    
    @Autowired
    private CIMOutputRepository cimOutputRepository;
    
    @Autowired
    private JsonUtils jsonUtils;
    
    @PostMapping("/generate-structured/{jobId}")
    @Operation(summary = "구조화된 CIM 조회 또는 강제 재생성", 
               description = "기존 구조화 결과 우선 조회하고, 없거나 강제 재생성 요청 시 새로 생성")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "구조화된 CIM 조회/생성 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
        @ApiResponse(responseCode = "404", description = "분석 작업을 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ResponseEntity<StructuredCIMResponse> getOrRegenerateStructuredCIM(
            @PathVariable @Parameter(description = "분석 작업 ID") Long jobId,
            @RequestParam(defaultValue = "false") @Parameter(description = "강제 재생성 여부") boolean forceRegenerate) {
        
        long startTime = System.currentTimeMillis();
        logger.info("구조화된 CIM 조회/재생성 요청 - Job ID: {}, 강제 재생성: {}", jobId, forceRegenerate);
        
        try {
            // 기존 CIM Output 확인
            CIMOutput existingOutput = cimOutputRepository.findByAnalysisJobId(jobId).orElse(null);
            
            if (existingOutput != null && existingOutput.hasStructuredData() && !forceRegenerate) {
                logger.info("기존 구조화된 데이터 반환 - Job ID: {}", jobId);
                
                Map<String, Object> structuredResult = jsonUtils.fromJson(existingOutput.getStructuredDataJson(), Map.class);
                
                return ResponseEntity.ok(StructuredCIMResponse.builder()
                    .success(true)
                    .structuredResult(structuredResult)
                    .structuredText(existingOutput.getStructuredText())
                    .stats(extractStats(structuredResult, existingOutput))
                    .timestamp(System.currentTimeMillis())
                    .analysisType("structured")
                    .jobId(jobId)
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build());
            }
            
            // Python의 /analyze-structured와 동일한 결과 생성
            Map<String, Object> structuredResult = cimService.generateStructuredCIM(jobId);
            String structuredText = cimService.createStructuredText(structuredResult);
            
            // CIM Output 업데이트
            if (existingOutput == null) {
                logger.warn("CIM Output이 존재하지 않음 - Job ID: {}", jobId);
                throw new CIMGenerationException("분석 결과를 찾을 수 없습니다: " + jobId);
            }
            
            // 구조화된 데이터 저장
            existingOutput.setStructuredDataJson(jsonUtils.toJson(structuredResult));
            existingOutput.setStructuredText(structuredText);
            existingOutput.setTotalQuestions(extractTotalQuestions(structuredResult));
            existingOutput.setLayoutType(extractLayoutType(structuredResult));
            existingOutput.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            existingOutput.setGenerationStatus(CIMOutput.GenerationStatus.COMPLETED);
            
            cimOutputRepository.save(existingOutput);
            
            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("구조화된 CIM 생성 완료 - Job ID: {}, 처리시간: {}ms, 문제 수: {}", 
                jobId, processingTime, extractTotalQuestions(structuredResult));
            
            return ResponseEntity.ok(StructuredCIMResponse.builder()
                .success(true)
                .structuredResult(structuredResult)
                .structuredText(structuredText)
                .stats(extractStats(structuredResult, existingOutput))
                .timestamp(System.currentTimeMillis())
                .analysisType("structured")
                .jobId(jobId)
                .processingTimeMs(processingTime)
                .build());
                
        } catch (CIMGenerationException e) {
            logger.error("구조화된 CIM 생성 실패: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(StructuredCIMResponse.builder()
                .success(false)
                .errorMessage(e.getMessage())
                .jobId(jobId)
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .build());
        } catch (Exception e) {
            logger.error("구조화된 CIM 생성 중 예상치 못한 오류: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(StructuredCIMResponse.builder()
                .success(false)
                .errorMessage("구조화된 CIM 생성 실패: " + e.getMessage())
                .jobId(jobId)
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .build());
        }
    }
    
    @PostMapping("/integrate-pages")
    @Operation(summary = "다중 페이지 구조화 결과 통합", 
               description = "여러 페이지의 CIM 결과를 하나로 통합")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "다중 페이지 통합 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ResponseEntity<StructuredCIMResponse> integrateMultiPageCIM(
            @Valid @RequestBody IntegrateRequest request) {
        
        long startTime = System.currentTimeMillis();
        logger.info("다중 페이지 CIM 통합 요청 - Job IDs: {}, 방법: {}", 
            request.getJobIds(), request.getIntegrationMethod());
        
        try {
            // CIMService의 다중 페이지 통합 기능 사용
            Map<String, Object> integratedResult = cimService.integrateCIMResults(request.getJobIds());
            String structuredText = cimService.createStructuredText(integratedResult);
            
            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("다중 페이지 CIM 통합 완료 - 페이지 수: {}, 처리시간: {}ms", 
                request.getJobCount(), processingTime);
            
            return ResponseEntity.ok(StructuredCIMResponse.builder()
                .success(true)
                .structuredResult(integratedResult)
                .structuredText(structuredText)
                .stats(extractStatsFromIntegrated(integratedResult, request))
                .isMultiPage(true)
                .pageCount(request.getJobCount())
                .processingTimeMs(processingTime)
                .build());
                
        } catch (Exception e) {
            logger.error("다중 페이지 CIM 통합 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(StructuredCIMResponse.builder()
                .success(false)
                .errorMessage("다중 페이지 통합 실패: " + e.getMessage())
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .build());
        }
    }
    
    @GetMapping("/{jobId}/status")
    @Operation(summary = "CIM 생성 상태 확인", 
               description = "지정된 작업의 CIM 생성 상태 및 메타데이터 조회")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "상태 조회 성공"),
        @ApiResponse(responseCode = "404", description = "CIM 출력을 찾을 수 없음")
    })
    public ResponseEntity<CIMStatusResponse> getCIMStatus(
            @PathVariable @Parameter(description = "분석 작업 ID") Long jobId) {
        
        logger.info("CIM 상태 조회 요청 - Job ID: {}", jobId);
        
        try {
            CIMOutput cimOutput = cimOutputRepository.findByAnalysisJobId(jobId)
                .orElseThrow(() -> new CIMGenerationException("CIM 출력을 찾을 수 없습니다: " + jobId));
            
            CIMStatusResponse response = CIMStatusResponse.fromCIMOutput(cimOutput);
            logger.info("CIM 상태 조회 완료 - Job ID: {}, 상태: {}", jobId, response.getStatus());
            
            return ResponseEntity.ok(response);
            
        } catch (CIMGenerationException e) {
            logger.error("CIM 상태 조회 실패: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("CIM 상태 조회 중 예상치 못한 오류: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @DeleteMapping("/{jobId}/structured")
    @Operation(summary = "구조화된 데이터 삭제", 
               description = "지정된 작업의 구조화된 CIM 데이터 삭제")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "CIM 출력을 찾을 수 없음")
    })
    public ResponseEntity<Map<String, Object>> deleteStructuredData(
            @PathVariable @Parameter(description = "분석 작업 ID") Long jobId) {
        
        logger.info("구조화된 데이터 삭제 요청 - Job ID: {}", jobId);
        
        try {
            CIMOutput cimOutput = cimOutputRepository.findByAnalysisJobId(jobId)
                .orElseThrow(() -> new CIMGenerationException("CIM 출력을 찾을 수 없습니다: " + jobId));
            
            // 구조화된 데이터만 삭제 (기본 CIM 데이터는 유지)
            cimOutput.setStructuredDataJson(null);
            cimOutput.setStructuredText(null);
            cimOutput.setTotalQuestions(null);
            cimOutput.setLayoutType(null);
            cimOutput.setQuestionStructureJson(null);
            
            cimOutputRepository.save(cimOutput);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "구조화된 데이터가 삭제되었습니다");
            result.put("jobId", jobId);
            
            logger.info("구조화된 데이터 삭제 완료 - Job ID: {}", jobId);
            return ResponseEntity.ok(result);
            
        } catch (CIMGenerationException e) {
            logger.error("구조화된 데이터 삭제 실패: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("구조화된 데이터 삭제 중 예상치 못한 오류: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // Private helper methods
    
    private StructuredCIMResponse.CIMStats extractStats(Map<String, Object> structuredResult, CIMOutput cimOutput) {
        Map<String, Object> documentInfo = (Map<String, Object>) structuredResult.get("document_info");
        
        return StructuredCIMResponse.CIMStats.builder()
            .totalQuestions((Integer) documentInfo.get("total_questions"))
            .layoutType((String) documentInfo.get("layout_type"))
            .totalElements((Integer) documentInfo.get("total_elements"))
            .totalTextBlocks((Integer) documentInfo.get("total_text_blocks"))
            .questionsWithChoices(cimOutput.getQuestionStructures() != null ? 
                (int) cimOutput.getQuestionStructures().stream().filter(qs -> qs.hasChoices()).count() : 0)
            .questionsWithImages(cimOutput.getQuestionStructures() != null ? 
                (int) cimOutput.getQuestionStructures().stream().filter(qs -> qs.hasImages()).count() : 0)
            .questionsWithTables(cimOutput.getQuestionStructures() != null ? 
                (int) cimOutput.getQuestionStructures().stream().filter(qs -> qs.hasTables()).count() : 0)
            .aiMappingsCount(cimOutput.getAiQuestionMappings() != null ? 
                cimOutput.getAiQuestionMappings().size() : 0)
            .highConfidenceMappings(cimOutput.getAiQuestionMappings() != null ? 
                (int) cimOutput.getAiQuestionMappings().stream().filter(aim -> aim.isMediumOrHighConfidence()).count() : 0)
            .sectionsCount(documentInfo.get("sections") != null ? 
                ((Map<String, Object>) documentInfo.get("sections")).size() : 0)
            .build();
    }
    
    private StructuredCIMResponse.CIMStats extractStatsFromIntegrated(Map<String, Object> integratedResult, IntegrateRequest request) {
        Map<String, Object> documentInfo = (Map<String, Object>) integratedResult.get("document_info");
        
        return StructuredCIMResponse.CIMStats.builder()
            .totalQuestions(documentInfo != null ? (Integer) documentInfo.getOrDefault("total_questions", 0) : 0)
            .layoutType("integrated")
            .totalElements(documentInfo != null ? (Integer) documentInfo.getOrDefault("total_elements", 0) : 0)
            .totalTextBlocks(documentInfo != null ? (Integer) documentInfo.getOrDefault("total_text_blocks", 0) : 0)
            .sectionsCount(request.getJobCount())
            .build();
    }
    
    private Integer extractTotalQuestions(Map<String, Object> structuredResult) {
        Map<String, Object> documentInfo = (Map<String, Object>) structuredResult.get("document_info");
        return documentInfo != null ? (Integer) documentInfo.get("total_questions") : 0;
    }
    
    private String extractLayoutType(Map<String, Object> structuredResult) {
        Map<String, Object> documentInfo = (Map<String, Object>) structuredResult.get("document_info");
        return documentInfo != null ? (String) documentInfo.get("layout_type") : "unknown";
    }
    
}