package com.smarteye.controller;

import com.smarteye.model.entity.AnalysisJob;
import com.smarteye.repository.AnalysisJobRepository;
import com.smarteye.repository.LayoutBlockRepository;
import com.smarteye.repository.TextBlockRepository;
import com.smarteye.repository.ProcessingLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 개발용 데이터베이스 연결 테스트 컨트롤러
 * 프로덕션 환경에서는 비활성화됩니다.
 */
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
@Profile({"dev", "test"})
public class DatabaseTestController {
    
    private final DataSource dataSource;
    private final AnalysisJobRepository analysisJobRepository;
    private final LayoutBlockRepository layoutBlockRepository;
    private final TextBlockRepository textBlockRepository;
    private final ProcessingLogRepository processingLogRepository;
    
    /**
     * 기본 데이터베이스 연결 테스트
     */
    @GetMapping("/db-connection")
    public ResponseEntity<Map<String, Object>> testConnection() {
        Map<String, Object> result = new HashMap<>();
        
        try (Connection connection = dataSource.getConnection()) {
            result.put("success", true);
            result.put("url", connection.getMetaData().getURL());
            result.put("driver", connection.getMetaData().getDriverName());
            result.put("timestamp", LocalDateTime.now());
            
            log.info("데이터베이스 연결 성공: {}", connection.getMetaData().getURL());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("timestamp", LocalDateTime.now());
            
            log.error("데이터베이스 연결 실패", e);
            
            return ResponseEntity.status(500).body(result);
        }
    }
    
    /**
     * 엔티티 매핑 및 CRUD 테스트
     */
    @GetMapping("/db-entities")
    public ResponseEntity<Map<String, Object>> testEntities() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 각 엔티티별 카운트 확인
            long analysisJobCount = analysisJobRepository.count();
            long layoutBlockCount = layoutBlockRepository.count();
            long textBlockCount = textBlockRepository.count();
            long processingLogCount = processingLogRepository.count();
            
            Map<String, Long> entityCounts = new HashMap<>();
            entityCounts.put("analysisJobs", analysisJobCount);
            entityCounts.put("layoutBlocks", layoutBlockCount);
            entityCounts.put("textBlocks", textBlockCount);
            entityCounts.put("processingLogs", processingLogCount);
            
            result.put("success", true);
            result.put("entityCounts", entityCounts);
            result.put("totalEntities", analysisJobCount + layoutBlockCount + textBlockCount + processingLogCount);
            result.put("timestamp", LocalDateTime.now());
            
            log.info("엔티티 매핑 테스트 성공: {}", entityCounts);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("timestamp", LocalDateTime.now());
            
            log.error("엔티티 매핑 테스트 실패", e);
            
            return ResponseEntity.status(500).body(result);
        }
    }
    
    /**
     * 테스트 데이터 생성
     */
    @PostMapping("/create-test-data")
    public ResponseEntity<Map<String, Object>> createTestData() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 테스트 분석 작업 생성
            AnalysisJob testJob = AnalysisJob.builder()
                    .jobId("test_job_" + System.currentTimeMillis())
                    .originalFilename("test_document.pdf")
                    .filePath("./temp/test_document.pdf")
                    .fileSize(1024L)
                    .status("CREATED")
                    .fileType("PDF")
                    .progress(0)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            AnalysisJob savedJob = analysisJobRepository.save(testJob);
            
            result.put("success", true);
            result.put("testJobId", savedJob.getId());
            result.put("message", "테스트 데이터 생성 완료");
            result.put("timestamp", LocalDateTime.now());
            
            log.info("테스트 데이터 생성 완료: Job ID = {}", savedJob.getId());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("timestamp", LocalDateTime.now());
            
            log.error("테스트 데이터 생성 실패", e);
            
            return ResponseEntity.status(500).body(result);
        }
    }
    
    /**
     * 데이터베이스 정보 조회
     */
    @GetMapping("/db-info")
    public ResponseEntity<Map<String, Object>> getDatabaseInfo() {
        Map<String, Object> result = new HashMap<>();
        
        try (Connection connection = dataSource.getConnection()) {
            var metaData = connection.getMetaData();
            
            Map<String, String> dbInfo = new HashMap<>();
            dbInfo.put("productName", metaData.getDatabaseProductName());
            dbInfo.put("productVersion", metaData.getDatabaseProductVersion());
            dbInfo.put("driverName", metaData.getDriverName());
            dbInfo.put("driverVersion", metaData.getDriverVersion());
            dbInfo.put("url", metaData.getURL());
            dbInfo.put("userName", metaData.getUserName());
            
            result.put("success", true);
            result.put("databaseInfo", dbInfo);
            result.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(500).body(result);
        }
    }
}
