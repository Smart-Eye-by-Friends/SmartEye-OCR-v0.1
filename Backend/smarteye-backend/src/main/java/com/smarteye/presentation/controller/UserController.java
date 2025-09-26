package com.smarteye.presentation.controller;

import com.smarteye.domain.user.User;
import com.smarteye.domain.analysis.AnalysisJob;
import com.smarteye.application.user.UserService;
import com.smarteye.application.analysis.AnalysisJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 사용자 관리 컨트롤러
 */
@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {
    
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private AnalysisJobService analysisJobService;
    
    /**
     * 새 사용자 생성 또는 기존 사용자 조회
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerOrGetUser(
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "email", required = false) String email) {
        
        logger.info("사용자 등록/조회 요청 - username: {}, email: {}", username, email);
        
        try {
            User user;
            if (username == null || username.trim().isEmpty()) {
                // 익명 사용자 생성
                user = userService.createAnonymousUser();
            } else {
                user = userService.getOrCreateUser(username, email);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "사용자 정보 조회/생성 완료");
            response.put("user", Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail() != null ? user.getEmail() : "",
                "active", user.isActive(),
                "createdAt", user.getCreatedAt(),
                "updatedAt", user.getUpdatedAt()
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("사용자 등록/조회 중 오류 발생: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "사용자 처리 중 오류가 발생했습니다: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 사용자 정보 조회
     */
    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable Long userId) {
        logger.info("사용자 정보 조회 - ID: {}", userId);
        
        Optional<User> userOpt = userService.getUserById(userId);
        if (userOpt.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "사용자를 찾을 수 없습니다");
            
            return ResponseEntity.notFound().build();
        }
        
        User user = userOpt.get();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("user", Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "email", user.getEmail() != null ? user.getEmail() : "",
            "active", user.isActive(),
            "createdAt", user.getCreatedAt(),
            "updatedAt", user.getUpdatedAt()
        ));
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 사용자의 분석 작업 목록 조회
     */
    @GetMapping("/{userId}/jobs")
    public ResponseEntity<Map<String, Object>> getUserJobs(
            @PathVariable Long userId,
            @RequestParam(value = "status", required = false) String status,
            Pageable pageable) {
        
        logger.info("사용자 분석 작업 목록 조회 - 사용자 ID: {}, 상태: {}", userId, status);
        
        try {
            // 사용자 존재 확인
            Optional<User> userOpt = userService.getUserById(userId);
            if (userOpt.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "사용자를 찾을 수 없습니다");
                
                return ResponseEntity.notFound().build();
            }
            
            List<AnalysisJob> jobs;
            Page<AnalysisJob> jobPage = null;
            
            if (status != null && !status.trim().isEmpty()) {
                // 특정 상태의 작업만 조회
                try {
                    AnalysisJob.JobStatus jobStatus = AnalysisJob.JobStatus.valueOf(status.toUpperCase());
                    jobs = analysisJobService.getAnalysisJobsByUserAndStatus(userId, jobStatus);
                } catch (IllegalArgumentException e) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("message", "잘못된 상태 값입니다: " + status);
                    
                    return ResponseEntity.badRequest().body(errorResponse);
                }
            } else {
                // 모든 작업 조회 (페이징)
                jobPage = analysisJobService.getAnalysisJobsByUser(userId, pageable);
                jobs = jobPage.getContent();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "작업 목록 조회 완료");
            response.put("jobs", jobs);
            
            if (jobPage != null) {
                response.put("pagination", Map.of(
                    "totalElements", jobPage.getTotalElements(),
                    "totalPages", jobPage.getTotalPages(),
                    "currentPage", jobPage.getNumber(),
                    "pageSize", jobPage.getSize(),
                    "hasNext", jobPage.hasNext(),
                    "hasPrevious", jobPage.hasPrevious()
                ));
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("사용자 작업 목록 조회 중 오류 발생: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "작업 목록 조회 중 오류가 발생했습니다: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 사용자 정보 업데이트
     */
    @PutMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable Long userId,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "email", required = false) String email) {
        
        logger.info("사용자 정보 업데이트 - ID: {}", userId);
        
        try {
            User updatedUser = userService.updateUser(userId, username, email);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "사용자 정보가 업데이트되었습니다");
            response.put("user", Map.of(
                "id", updatedUser.getId(),
                "username", updatedUser.getUsername(),
                "email", updatedUser.getEmail() != null ? updatedUser.getEmail() : "",
                "active", updatedUser.isActive(),
                "createdAt", updatedUser.getCreatedAt(),
                "updatedAt", updatedUser.getUpdatedAt()
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("사용자 정보 업데이트 중 오류 발생: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 사용자 비활성화
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> deactivateUser(@PathVariable Long userId) {
        logger.info("사용자 비활성화 - ID: {}", userId);
        
        try {
            userService.deactivateUser(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "사용자가 비활성화되었습니다");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("사용자 비활성화 중 오류 발생: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}