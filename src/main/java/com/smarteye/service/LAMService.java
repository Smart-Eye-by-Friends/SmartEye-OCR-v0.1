package com.smarteye.service;

import com.smarteye.client.LAMServiceClient;
import com.smarteye.dto.lam.*;
import com.smarteye.model.entity.AnalysisJob;
import com.smarteye.model.entity.LayoutBlock;
import com.smarteye.repository.AnalysisJobRepository;
import com.smarteye.repository.LayoutBlockRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.Base64;

/**
 * LAM (Layout Analysis Module) - DocLayout-YOLO 기반 레이아웃 분석
 * 2단계: Python 마이크로서비스와 통신하여 레이아웃 분석 수행
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LAMService {
    
    private final AnalysisJobRepository analysisJobRepository;
    private final LayoutBlockRepository layoutBlockRepository;
    
    @Autowired
    private LAMServiceClient lamClient;
    
    private static final String TEMP_DIR = "temp";
    private static final String PYTHON_SCRIPT_DIR = "python_scripts";
    
    /**
     * 레이아웃 분석 수행 (파일만으로)
     */
    public AnalysisJob analyzeLayout(MultipartFile file) {
        String jobId = UUID.randomUUID().toString();
        return analyzeLayout(file, jobId);
    }
    
    /**
     * 레이아웃 분석 실행
     */
    public AnalysisJob analyzeLayout(MultipartFile file, String jobId) {
        log.info("LAM 분석 시작 - JobId: {}, 파일: {}", jobId, file.getOriginalFilename());
        
        try {
            // 1. 분석 작업 생성
            AnalysisJob job = createAnalysisJob(file, jobId);
            
            // 2. 파일 저장
            String savedFilePath = saveFile(file, jobId);
            job.setFilePath(savedFilePath);
            job.setStatus("PROCESSING");
            job.setProgress(10);
            analysisJobRepository.save(job);
            
            // 3. 레이아웃 분석 실행
            executePythonLAM(job);
            
            log.info("LAM 분석 완료 - JobId: {}", jobId);
            return job;
            
        } catch (Exception e) {
            log.error("LAM 분석 실패 - JobId: {}", jobId, e);
            
            // 실패 처리
            AnalysisJob job = analysisJobRepository.findByJobId(jobId).orElse(null);
            if (job != null) {
                job.setStatus("FAILED");
                job.setErrorMessage("LAM 분석 실패: " + e.getMessage());
                analysisJobRepository.save(job);
            }
            
            throw new RuntimeException("LAM 분석 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 분석 작업 생성
     */
    private AnalysisJob createAnalysisJob(MultipartFile file, String jobId) {
        return AnalysisJob.builder()
                .jobId(jobId)
                .originalFilename(file.getOriginalFilename())
                .fileSize(file.getSize())
                .fileType(file.getContentType())
                .status("PENDING")
                .progress(0)
                .createdAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * 파일 저장
     */
    private String saveFile(MultipartFile file, String jobId) throws IOException {
        // 임시 디렉토리 생성
        Path tempDir = Paths.get(TEMP_DIR);
        Files.createDirectories(tempDir);
        
        // 파일 확장자 추출
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        
        // 파일 저장
        String filename = jobId + extension;
        Path filePath = tempDir.resolve(filename);
        Files.write(filePath, file.getBytes());
        
        log.info("파일 저장 완료: {}", filePath.toAbsolutePath());
        return filePath.toAbsolutePath().toString();
    }
    
    /**
     * Python LAM 스크립트 실행
     */
    private void executePythonLAM(AnalysisJob job) throws Exception {
        log.info("Python LAM 실행 시작 - JobId: {}", job.getJobId());
        
        // Python 스크립트 생성
        createPythonLAMScript();
        
        // 경로 설정
        Path scriptPath = Paths.get("python_scripts", "layout_analysis.py");
        String imagePath = job.getFilePath();
        String outputPath = "temp/lam_result_" + job.getJobId() + ".json";
        
        // Python 명령 실행
        // Python 실행 명령어 구성 (가상환경 사용)
        String pythonPath = "/home/jongyoung3/SmartEye_v0.1/smarteye-spring-backend-new/python_env/bin/python";
        List<String> command = Arrays.asList(
            pythonPath,
            scriptPath.toString(),
            imagePath,
            outputPath
        );
        
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File("."));
        processBuilder.redirectErrorStream(true);
        
        // 프로세스 실행
        Process process = processBuilder.start();
        
        // 출력 읽기
        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
                log.debug("Python LAM 출력: {}", line);
            }
        }
        
        // 프로세스 완료 대기
        boolean finished = process.waitFor(300, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Python LAM 실행 타임아웃");
        }
        
        if (process.exitValue() != 0) {
            String errorOutput = String.join("\\n", output);
            throw new RuntimeException("Python LAM 실행 실패: " + errorOutput);
        }
        
        // 결과 파싱 및 저장
        List<Map<String, Object>> layoutResults = parseLayoutResults(output);
        saveLayoutResults(job, layoutResults);
        
        // 작업 완료 처리
        job.setStatus("COMPLETED");
        job.setProgress(100);
        analysisJobRepository.save(job);
        
        log.info("Python LAM 실행 완료 - JobId: {}, 블록 수: {}", job.getJobId(), layoutResults.size());
    }
    
    /**
     * Python LAM 스크립트 생성
     */
    private void createPythonLAMScript() throws IOException {
        Path scriptDir = Paths.get(PYTHON_SCRIPT_DIR);
        Files.createDirectories(scriptDir);
        
        Path scriptFile = scriptDir.resolve("layout_analysis.py");
        
        if (!Files.exists(scriptFile)) {
            String pythonScript = """
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import sys
import cv2
import numpy as np
import json
import os

def mock_yolo_analysis(image_path, job_id):
    \"\"\"
    DocLayout-YOLO 모의 분석
    실제 환경에서는 실제 YOLO 모델을 로드하여 사용
    \"\"\"
    try:
        # 이미지 로드
        img = cv2.imread(image_path)
        if img is None:
            raise ValueError(f"이미지를 로드할 수 없습니다: {image_path}")
        
        height, width = img.shape[:2]
        
        # 모의 레이아웃 분석 결과 생성
        layout_blocks = [
            {
                "class_name": "title",
                "confidence": 0.95,
                "x1": int(width * 0.1),
                "y1": int(height * 0.05),
                "x2": int(width * 0.9),
                "y2": int(height * 0.15)
            },
            {
                "class_name": "plain text",
                "confidence": 0.88,
                "x1": int(width * 0.1),
                "y1": int(height * 0.2),
                "x2": int(width * 0.9),
                "y2": int(height * 0.6)
            },
            {
                "class_name": "table",
                "confidence": 0.82,
                "x1": int(width * 0.15),
                "y1": int(height * 0.65),
                "x2": int(width * 0.85),
                "y2": int(height * 0.9)
            }
        ]
        
        # 결과 출력 (Java에서 파싱)
        for i, block in enumerate(layout_blocks):
            print(f"LAYOUT_BLOCK:{i}:{block['class_name']}:{block['confidence']}:{block['x1']}:{block['y1']}:{block['x2']}:{block['y2']}")
        
        print(f"ANALYSIS_COMPLETE:{len(layout_blocks)}")
        
        return layout_blocks
        
    except Exception as e:
        print(f"레이아웃 분석 실패: {e}")
        print("ANALYSIS_FAILED")
        return []

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("사용법: python layout_analysis.py <image_path> <job_id>")
        sys.exit(1)
    
    image_path = sys.argv[1]
    job_id = sys.argv[2]
    
    mock_yolo_analysis(image_path, job_id)
""";
            Files.write(scriptFile, pythonScript.getBytes());
            log.info("Python LAM 스크립트 생성 완료: {}", scriptFile);
        }
    }
    
    /**
     * 레이아웃 분석 결과 파싱
     */
    private List<Map<String, Object>> parseLayoutResults(List<String> output) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (String line : output) {
            if (line.startsWith("LAYOUT_BLOCK:")) {
                try {
                    String[] parts = line.substring(13).split(":");
                    if (parts.length >= 7) {
                        Map<String, Object> block = new HashMap<>();
                        block.put("block_index", Integer.parseInt(parts[0]));
                        block.put("class_name", parts[1]);
                        block.put("confidence", Double.parseDouble(parts[2]));
                        block.put("x1", Integer.parseInt(parts[3]));
                        block.put("y1", Integer.parseInt(parts[4]));
                        block.put("x2", Integer.parseInt(parts[5]));
                        block.put("y2", Integer.parseInt(parts[6]));
                        
                        results.add(block);
                    }
                } catch (Exception e) {
                    log.warn("레이아웃 블록 파싱 실패: {}", line, e);
                }
            }
        }
        
        return results;
    }
    
    /**
     * 2단계: 마이크로서비스를 사용한 레이아웃 분석 (새로운 방식)
     */
    public AnalysisJob analyzeLayoutWithMicroservice(MultipartFile file) {
        String jobId = UUID.randomUUID().toString();
        return analyzeLayoutWithMicroservice(file, jobId);
    }
    
    /**
     * 2단계: 마이크로서비스를 사용한 레이아웃 분석 실행
     */
    public AnalysisJob analyzeLayoutWithMicroservice(MultipartFile file, String jobId) {
        log.info("LAM 마이크로서비스 분석 시작 - JobId: {}, 파일: {}", jobId, file.getOriginalFilename());
        
        try {
            // 1. 분석 작업 생성
            AnalysisJob job = createAnalysisJob(file, jobId);
            
            // 2. 파일 저장
            String savedFilePath = saveFile(file, jobId);
            job.setFilePath(savedFilePath);
            job.setStatus("PROCESSING");
            job.setProgress(20);
            analysisJobRepository.save(job);
            
            // 3. LAM 마이크로서비스 호출
            executeLAMMicroservice(job, file);
            
            log.info("LAM 마이크로서비스 분석 완료 - JobId: {}", jobId);
            return job;
            
        } catch (Exception e) {
            log.error("LAM 마이크로서비스 분석 실패 - JobId: {}", jobId, e);
            
            // 실패 처리
            AnalysisJob job = analysisJobRepository.findByJobId(jobId).orElse(null);
            if (job != null) {
                job.setStatus("FAILED");
                job.setErrorMessage("LAM 마이크로서비스 분석 실패: " + e.getMessage());
                analysisJobRepository.save(job);
            }
            
            throw new RuntimeException("LAM 마이크로서비스 분석 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * LAM 마이크로서비스 실행
     */
    private void executeLAMMicroservice(AnalysisJob job, MultipartFile file) throws Exception {
        log.info("LAM 마이크로서비스 호출 시작 - JobId: {}", job.getJobId());
        
        try {
            // 파일을 Base64로 인코딩
            byte[] imageBytes = file.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            
            // 이미지 정보 생성
            LAMImageInfo imageInfo = new LAMImageInfo();
            imageInfo.setFilename(file.getOriginalFilename());
            imageInfo.setSize(imageBytes.length);
            imageInfo.setMimeType(file.getContentType());
            
            // 분석 옵션 설정
            LAMAnalysisOptions options = new LAMAnalysisOptions();
            options.setConfidenceThreshold(0.5);
            options.setMaxBlocks(100);
            options.setDetectText(true);
            options.setDetectTables(true);
            options.setDetectFigures(true);
            
            // LAM 분석 요청 생성
            LAMAnalysisRequest request = new LAMAnalysisRequest();
            request.setImageData(base64Image);
            request.setImageInfo(imageInfo);
            request.setOptions(options);
            
            // 진행률 업데이트
            job.setProgress(50);
            analysisJobRepository.save(job);
            
            // LAM 서비스 호출
            LAMAnalysisResponse response = lamClient.analyzeLayout(request);
            
            // 진행률 업데이트
            job.setProgress(80);
            analysisJobRepository.save(job);
            
            // 결과를 LayoutBlock 엔티티로 변환하여 저장
            saveLayoutResultsFromMicroservice(job, response);
            
            // 완료 처리
            job.setStatus("COMPLETED");
            job.setProgress(100);
            job.setCompletedAt(LocalDateTime.now());
            analysisJobRepository.save(job);
            
            log.info("LAM 마이크로서비스 호출 완료 - JobId: {}, 블록 수: {}", 
                    job.getJobId(), response.getBlocks().size());
            
        } catch (Exception e) {
            log.error("LAM 마이크로서비스 호출 실패 - JobId: {}", job.getJobId(), e);
            throw e;
        }
    }
    
    /**
     * 마이크로서비스 결과를 데이터베이스에 저장
     */
    private void saveLayoutResultsFromMicroservice(AnalysisJob job, LAMAnalysisResponse response) {
        log.info("마이크로서비스 결과 저장 시작 - JobId: {}, 블록 수: {}", 
                job.getJobId(), response.getBlocks().size());
        
        int blockIndex = 0;
        for (LAMLayoutBlock block : response.getBlocks()) {
            try {
                LayoutBlock layoutBlock = LayoutBlock.builder()
                        .analysisJob(job)
                        .blockIndex(blockIndex++)
                        .className(block.getType())
                        .confidence(block.getConfidence())
                        .x1(block.getBbox().getX())
                        .y1(block.getBbox().getY())
                        .x2(block.getBbox().getX() + block.getBbox().getWidth())
                        .y2(block.getBbox().getY() + block.getBbox().getHeight())
                        .width(block.getBbox().getWidth())
                        .height(block.getBbox().getHeight())
                        .area((long) (block.getBbox().getWidth() * block.getBbox().getHeight()))
                        .build();
                
                layoutBlockRepository.save(layoutBlock);
                
                log.debug("레이아웃 블록 저장 완료 - JobId: {}, 블록: {}, 타입: {}", 
                         job.getJobId(), layoutBlock.getBlockIndex(), layoutBlock.getClassName());
                
            } catch (Exception e) {
                log.error("레이아웃 블록 저장 실패 - JobId: {}, 블록: {}", job.getJobId(), block, e);
            }
        }
        
        log.info("마이크로서비스 결과 저장 완료 - JobId: {}", job.getJobId());
    }
    
    /**
     * LAM 마이크로서비스 상태 확인
     */
    public LAMHealthResponse checkMicroserviceHealth() {
        try {
            return lamClient.getHealth();
        } catch (Exception e) {
            log.error("LAM 마이크로서비스 상태 확인 실패: {}", e.getMessage());
            LAMHealthResponse response = new LAMHealthResponse();
            response.setStatus("unhealthy");
            response.setMessage("LAM 서비스 연결 실패: " + e.getMessage());
            return response;
        }
    }
    
    /**
     * LAM 모델 정보 조회
     */
    public LAMModelInfo getMicroserviceModelInfo() {
        try {
            return lamClient.getModelInfo();
        } catch (Exception e) {
            log.error("LAM 모델 정보 조회 실패: {}", e.getMessage());
            throw new RuntimeException("모델 정보 조회 중 오류 발생", e);
        }
    }
    
    /**
     * 레이아웃 분석 결과 저장
     */
    private void saveLayoutResults(AnalysisJob job, List<Map<String, Object>> layoutResults) {
        log.info("레이아웃 결과 저장 시작 - JobId: {}, 블록 수: {}", job.getJobId(), layoutResults.size());
        
        for (Map<String, Object> result : layoutResults) {
            try {
                LayoutBlock layoutBlock = LayoutBlock.builder()
                        .analysisJob(job)
                        .blockIndex(((Number) result.get("block_index")).intValue())
                        .className((String) result.get("class_name"))
                        .confidence(((Number) result.get("confidence")).doubleValue())
                        .x1(((Number) result.get("x1")).intValue())
                        .y1(((Number) result.get("y1")).intValue())
                        .x2(((Number) result.get("x2")).intValue())
                        .y2(((Number) result.get("y2")).intValue())
                        .width(((Number) result.get("x2")).intValue() - ((Number) result.get("x1")).intValue())
                        .height(((Number) result.get("y2")).intValue() - ((Number) result.get("y1")).intValue())
                        .area((long) ((((Number) result.get("x2")).intValue() - ((Number) result.get("x1")).intValue()) * 
                                      (((Number) result.get("y2")).intValue() - ((Number) result.get("y1")).intValue())))
                        .build();
                
                layoutBlockRepository.save(layoutBlock);
                
                log.debug("레이아웃 블록 저장 완료 - JobId: {}, 블록: {}, 클래스: {}", 
                         job.getJobId(), layoutBlock.getBlockIndex(), layoutBlock.getClassName());
                
            } catch (Exception e) {
                log.error("레이아웃 블록 저장 실패 - JobId: {}, 결과: {}", job.getJobId(), result, e);
            }
        }
        
        log.info("레이아웃 결과 저장 완료 - JobId: {}", job.getJobId());
    }
    
    /**
     * 레이아웃 블록 조회
     */
    public List<LayoutBlock> getLayoutBlocks(String jobId) {
        Optional<AnalysisJob> job = analysisJobRepository.findByJobId(jobId);
        if (job.isPresent()) {
            return layoutBlockRepository.findByAnalysisJobOrderByBlockIndex(job.get());
        }
        return Collections.emptyList();
    }
}