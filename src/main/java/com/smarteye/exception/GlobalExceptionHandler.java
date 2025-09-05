package com.smarteye.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.smarteye.dto.response.AnalysisResponse;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<AnalysisResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.error("File size exceeded: {}", e.getMessage());
        
        AnalysisResponse response = AnalysisResponse.builder()
                .success(false)
                .message("파일 크기가 너무 큽니다. 최대 50MB까지 업로드 가능합니다.")
                .timestamp(LocalDateTime.now())
                .build();
                
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<AnalysisResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.error("Invalid argument: {}", e.getMessage());
        
        AnalysisResponse response = AnalysisResponse.builder()
                .success(false)
                .message("잘못된 요청입니다: " + e.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
                
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AnalysisResponse> handleGeneral(Exception e) {
        log.error("Unexpected error occurred", e);
        
        AnalysisResponse response = AnalysisResponse.builder()
                .success(false)
                .message("서버에서 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.")
                .timestamp(LocalDateTime.now())
                .build();
                
        return ResponseEntity.internalServerError().body(response);
    }
}
