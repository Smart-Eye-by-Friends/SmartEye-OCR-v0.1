package com.smarteye.service;

import com.smarteye.model.entity.AnalysisJob;
import com.smarteye.model.entity.LayoutBlock;
import com.smarteye.model.entity.TextBlock;
import com.smarteye.repository.AnalysisJobRepository;
import com.smarteye.repository.LayoutBlockRepository;
import com.smarteye.repository.TextBlockRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * TSPM (Text & Semantic Processing Module) - OCR 및 OpenAI Vision API 기반 텍스트 처리
 * Java 네이티브 서비스를 우선적으로 사용하고, 필요시 Python 스크립트로 fallback
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TSPMService {
    
    private final AnalysisJobRepository analysisJobRepository;
    private final LayoutBlockRepository layoutBlockRepository;
    private final TextBlockRepository textBlockRepository;
    private final JavaTSPMService javaTSPMService; // Java 네이티브 서비스 추가
    
    @Value("${smarteye.openai.api-key:}")
    private String openaiApiKey;
    
    @Value("${smarteye.tspm.use-java-native:true}")
    private boolean useJavaNative; // Java 네이티브 사용 여부 설정
    
    private static final String PYTHON_SCRIPT_DIR = "python_scripts";
    private static final String RESULTS_DIR = "analysis_results";
    
    // OCR 대상 클래스
    private static final Set<String> OCR_TARGET_CLASSES = Set.of(
        "title", "plain text", "abandon text",
        "table caption", "table footnote",
        "isolated formula", "formula caption"
    );
    
    // Vision API 대상 클래스  
    private static final Set<String> API_TARGET_CLASSES = Set.of(
        "figure", "table"
    );
    
    /**
     * 텍스트 및 시맨틱 처리 실행
     */
    public AnalysisJob processTextAndSemantic(String jobId) {
        log.info("TSPM 처리 시작 - JobId: {}", jobId);
        
        try {
            // 1. 분석 작업 조회
            AnalysisJob job = analysisJobRepository.findByJobId(jobId)
                    .orElseThrow(() -> new RuntimeException("분석 작업을 찾을 수 없습니다: " + jobId));
            
            if (!"COMPLETED".equals(job.getStatus())) {
                throw new RuntimeException("LAM 분석이 완료되지 않았습니다: " + jobId);
            }
            
            // 2. 레이아웃 블록 조회
            List<LayoutBlock> layoutBlocks = layoutBlockRepository.findByAnalysisJobOrderByBlockIndex(job);
            
            if (layoutBlocks.isEmpty()) {
                throw new RuntimeException("레이아웃 블록이 없습니다: " + jobId);
            }
            
            // 3. TSPM 처리 시작
            job.setStatus("PROCESSING_TSPM");
            job.setProgress(10);
            analysisJobRepository.save(job);
            
            // 4. 각 블록별 처리
            int processedBlocks = 0;
            for (LayoutBlock layoutBlock : layoutBlocks) {
                processLayoutBlock(job, layoutBlock);
                processedBlocks++;
                
                // 진행률 업데이트
                int progress = 10 + (processedBlocks * 80 / layoutBlocks.size());
                job.setProgress(progress);
                analysisJobRepository.save(job);
            }
            
            // 5. 완료 처리
            job.setStatus("COMPLETED_TSPM");
            job.setProgress(100);
            analysisJobRepository.save(job);
            
            log.info("TSPM 처리 완료 - JobId: {}, 처리된 블록: {}", jobId, processedBlocks);
            return job;
            
        } catch (Exception e) {
            log.error("TSPM 처리 실패 - JobId: {}", jobId, e);
            
            // 실패 처리
            AnalysisJob job = analysisJobRepository.findByJobId(jobId).orElse(null);
            if (job != null) {
                job.setStatus("FAILED_TSPM");
                job.setErrorMessage("TSPM 처리 실패: " + e.getMessage());
                analysisJobRepository.save(job);
            }
            
            throw new RuntimeException("TSPM 처리 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 개별 레이아웃 블록 처리
     */
    private void processLayoutBlock(AnalysisJob job, LayoutBlock layoutBlock) throws Exception {
        log.info("블록 처리 시작 - JobId: {}, 블록: {} ({})", 
                job.getJobId(), layoutBlock.getBlockIndex(), layoutBlock.getClassName());
        
        String className = layoutBlock.getClassName();
        String processingMethod;
        String extractedText = "";
        String processedContent = "";
        double confidence = 0.0;
        
        if (OCR_TARGET_CLASSES.contains(className)) {
            // OCR 처리
            processingMethod = "OCR";
            Map<String, Object> ocrResult = performOCR(job, layoutBlock);
            extractedText = (String) ocrResult.getOrDefault("text", "");
            confidence = ((Number) ocrResult.getOrDefault("confidence", 0.0)).doubleValue();
            processedContent = extractedText;
            
        } else if (API_TARGET_CLASSES.contains(className)) {
            // OpenAI Vision API 처리
            processingMethod = "VISION_API";
            Map<String, Object> visionResult = performVisionAPI(job, layoutBlock);
            extractedText = (String) visionResult.getOrDefault("description", "");
            confidence = ((Number) visionResult.getOrDefault("confidence", 0.0)).doubleValue();
            processedContent = (String) visionResult.getOrDefault("processed_content", extractedText);
            
        } else {
            // 기타 클래스는 건너뛰기
            log.info("처리 대상이 아닌 클래스: {}", className);
            return;
        }
        
        // 결과 저장
        TextBlock textBlock = TextBlock.builder()
                .analysisJob(job)
                .layoutBlock(layoutBlock)
                .processingMethod(processingMethod)
                .extractedText(extractedText)
                .processedContent(processedContent)
                .confidence(confidence)
                .metadata("{\"block_index\": " + layoutBlock.getBlockIndex() + "}")
                .build();
        
        textBlockRepository.save(textBlock);
        
        log.info("블록 처리 완료 - JobId: {}, 블록: {}, 방법: {}, 텍스트 길이: {}", 
                job.getJobId(), layoutBlock.getBlockIndex(), processingMethod, extractedText.length());
    }
    
    /**
     * OCR 처리 수행 - Java 네이티브 우선, Python fallback
     */
    private Map<String, Object> performOCR(AnalysisJob job, LayoutBlock layoutBlock) throws Exception {
        log.info("OCR 처리 시작 - JobId: {}, 블록: {}, 방식: {}", 
                job.getJobId(), layoutBlock.getBlockIndex(), useJavaNative ? "Java Native" : "Python");
        
        // 이미지 크롭
        String croppedImagePath = cropLayoutBlock(job, layoutBlock);
        
        if (useJavaNative) {
            try {
                // Java 네이티브 OCR 사용
                Map<String, Object> result = javaTSPMService.performOCR(croppedImagePath);
                log.info("Java 네이티브 OCR 처리 완료 - JobId: {}", job.getJobId());
                return result;
            } catch (Exception e) {
                log.warn("Java 네이티브 OCR 실패, Python으로 fallback - JobId: {}, 오류: {}", 
                        job.getJobId(), e.getMessage());
                // Python fallback 실행
                return performPythonOCR(job, layoutBlock, croppedImagePath);
            }
        } else {
            // Python OCR 직접 사용
            return performPythonOCR(job, layoutBlock, croppedImagePath);
        }
    }
    
    /**
     * Python OCR 처리 (fallback)
     */
    private Map<String, Object> performPythonOCR(AnalysisJob job, LayoutBlock layoutBlock, String croppedImagePath) throws Exception {
        // Python OCR 스크립트 생성
        createPythonOCRScript();
        
        // Python OCR 스크립트 실행
        List<String> command = Arrays.asList(
            "python3",
            PYTHON_SCRIPT_DIR + "/ocr_processing.py",
            croppedImagePath,
            job.getJobId(),
            String.valueOf(layoutBlock.getBlockIndex())
        );
        
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File("."));
        processBuilder.redirectErrorStream(true);
        
        Process process = processBuilder.start();
        
        // 출력 읽기
        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
                log.debug("OCR 출력: {}", line);
            }
        }
        
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("OCR 처리 타임아웃");
        }
        
        if (process.exitValue() != 0) {
            String errorOutput = String.join("\n", output);
            throw new RuntimeException("OCR 처리 실패: " + errorOutput);
        }
        
        // 결과 파싱
        Map<String, Object> result = new HashMap<>();
        for (String line : output) {
            if (line.startsWith("OCR_TEXT:")) {
                result.put("text", line.substring(9).trim());
            } else if (line.startsWith("OCR_CONFIDENCE:")) {
                try {
                    result.put("confidence", Double.parseDouble(line.substring(15).trim()));
                } catch (NumberFormatException e) {
                    result.put("confidence", 0.5);
                }
            }
        }
        
        if (!result.containsKey("text")) {
            result.put("text", "");
        }
        if (!result.containsKey("confidence")) {
            result.put("confidence", 0.0);
        }
        
        return result;
    }
    
    /**
     * OpenAI Vision API 처리 수행 - Java 네이티브 우선, Python fallback
     */
    private Map<String, Object> performVisionAPI(AnalysisJob job, LayoutBlock layoutBlock) throws Exception {
        log.info("Vision API 처리 시작 - JobId: {}, 블록: {}, 방식: {}", 
                job.getJobId(), layoutBlock.getBlockIndex(), useJavaNative ? "Java Native" : "Python");
        
        if (openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
            log.warn("OpenAI API 키가 설정되지 않았습니다. 기본 응답을 반환합니다.");
            Map<String, Object> result = new HashMap<>();
            result.put("description", "Vision API 키가 설정되지 않음");
            result.put("processed_content", "이미지 분석 결과");
            result.put("confidence", 0.1);
            return result;
        }
        
        // 이미지 크롭
        String croppedImagePath = cropLayoutBlock(job, layoutBlock);
        
        if (useJavaNative) {
            try {
                // Java 네이티브 Vision API 사용
                Map<String, Object> result = javaTSPMService.performVisionAnalysis(croppedImagePath);
                log.info("Java 네이티브 Vision API 처리 완료 - JobId: {}", job.getJobId());
                return result;
            } catch (Exception e) {
                log.warn("Java 네이티브 Vision API 실패, Python으로 fallback - JobId: {}, 오류: {}", 
                        job.getJobId(), e.getMessage());
                // Python fallback 실행
                return performPythonVisionAPI(job, layoutBlock, croppedImagePath);
            }
        } else {
            // Python Vision API 직접 사용
            return performPythonVisionAPI(job, layoutBlock, croppedImagePath);
        }
    }
    
    /**
     * Python Vision API 처리 (fallback)
     */
    private Map<String, Object> performPythonVisionAPI(AnalysisJob job, LayoutBlock layoutBlock, String croppedImagePath) throws Exception {
        // Python Vision API 스크립트 생성
        createPythonVisionScript();
        
        // Python Vision API 스크립트 실행
        List<String> command = Arrays.asList(
            "python3",
            PYTHON_SCRIPT_DIR + "/vision_processing.py",
            croppedImagePath,
            job.getJobId(),
            String.valueOf(layoutBlock.getBlockIndex()),
            openaiApiKey
        );
        
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File("."));
        processBuilder.redirectErrorStream(true);
        
        Process process = processBuilder.start();
        
        // 출력 읽기
        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
                log.debug("Vision API 출력: {}", line);
            }
        }
        
        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Vision API 처리 타임아웃");
        }
        
        if (process.exitValue() != 0) {
            String errorOutput = String.join("\n", output);
            throw new RuntimeException("Vision API 처리 실패: " + errorOutput);
        }
        
        // 결과 파싱
        Map<String, Object> result = new HashMap<>();
        StringBuilder description = new StringBuilder();
        
        for (String line : output) {
            if (line.startsWith("VISION_DESCRIPTION:")) {
                description.append(line.substring(19).trim()).append(" ");
            } else if (line.startsWith("VISION_CONFIDENCE:")) {
                try {
                    result.put("confidence", Double.parseDouble(line.substring(18).trim()));
                } catch (NumberFormatException e) {
                    result.put("confidence", 0.7);
                }
            }
        }
        
        String descriptionText = description.toString().trim();
        result.put("description", descriptionText);
        result.put("processed_content", descriptionText);
        
        if (!result.containsKey("confidence")) {
            result.put("confidence", 0.7);
        }
        
        return result;
    }
    
    /**
     * 레이아웃 블록 이미지 크롭 - Java 네이티브 우선, Python fallback
     */
    private String cropLayoutBlock(AnalysisJob job, LayoutBlock layoutBlock) throws Exception {
        // 원본 이미지 로드
        String originalImagePath = job.getFilePath();
        String outputPath = RESULTS_DIR + "/" + job.getJobId() + "_block_" + layoutBlock.getBlockIndex() + ".jpg";
        
        if (useJavaNative) {
            try {
                // Java 네이티브 이미지 크롭 사용
                return javaTSPMService.cropLayoutBlock(
                    originalImagePath,
                    layoutBlock.getX1(),
                    layoutBlock.getY1(),
                    layoutBlock.getX2(),
                    layoutBlock.getY2(),
                    outputPath
                );
            } catch (Exception e) {
                log.warn("Java 네이티브 이미지 크롭 실패, Python으로 fallback - JobId: {}, 오류: {}", 
                        job.getJobId(), e.getMessage());
                // Python fallback 실행
                return cropLayoutBlockPython(job, layoutBlock, originalImagePath, outputPath);
            }
        } else {
            // Python 크롭 직접 사용
            return cropLayoutBlockPython(job, layoutBlock, originalImagePath, outputPath);
        }
    }
    
    /**
     * Python 이미지 크롭 처리 (fallback)
     */
    private String cropLayoutBlockPython(AnalysisJob job, LayoutBlock layoutBlock, String originalImagePath, String outputPath) throws Exception {
        // Python 이미지 크롭 스크립트 실행
        List<String> command = Arrays.asList(
            "python3",
            PYTHON_SCRIPT_DIR + "/crop_image.py",
            originalImagePath,
            String.valueOf(layoutBlock.getX1()),
            String.valueOf(layoutBlock.getY1()),
            String.valueOf(layoutBlock.getX2()),
            String.valueOf(layoutBlock.getY2()),
            outputPath
        );
        
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File("."));
        
        Process process = processBuilder.start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        
        if (!finished || process.exitValue() != 0) {
            throw new RuntimeException("이미지 크롭 실패");
        }
        
        return outputPath;
    }
    
    /**
     * Python OCR 스크립트 생성
     */
    private void createPythonOCRScript() throws IOException {
        Path scriptDir = Paths.get(PYTHON_SCRIPT_DIR);
        Files.createDirectories(scriptDir);
        
        Path scriptFile = scriptDir.resolve("ocr_processing.py");
        
        if (!Files.exists(scriptFile)) {
            String pythonScript = """
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import sys
import cv2
import pytesseract

def perform_ocr(image_path, job_id, block_index):
    try:
        # 이미지 로드
        img = cv2.imread(image_path)
        if img is None:
            raise ValueError(f"이미지를 로드할 수 없습니다: {image_path}")
        
        # OCR 수행 (한국어 + 영어)
        config = r'--oem 3 --psm 6'
        text = pytesseract.image_to_string(img, lang='kor+eng', config=config)
        
        # 신뢰도 계산 (간단한 휴리스틱)
        confidence = 0.8 if len(text.strip()) > 0 else 0.1
        
        print(f"OCR_TEXT:{text.strip()}")
        print(f"OCR_CONFIDENCE:{confidence}")
        
        return text.strip(), confidence
        
    except Exception as e:
        print(f"OCR 처리 실패: {e}")
        print("OCR_TEXT:")
        print("OCR_CONFIDENCE:0.0")
        return "", 0.0

if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("사용법: python ocr_processing.py <image_path> <job_id> <block_index>")
        sys.exit(1)
    
    image_path = sys.argv[1]
    job_id = sys.argv[2]
    block_index = sys.argv[3]
    
    perform_ocr(image_path, job_id, block_index)
""";
            Files.write(scriptFile, pythonScript.getBytes());
            log.info("Python OCR 스크립트 생성 완료: {}", scriptFile);
        }
        
        // 이미지 크롭 스크립트도 생성
        Path cropScript = scriptDir.resolve("crop_image.py");
        if (!Files.exists(cropScript)) {
            String cropPythonScript = """
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import sys
import cv2
import os

def crop_image(image_path, x1, y1, x2, y2, output_path):
    try:
        # 이미지 로드
        img = cv2.imread(image_path)
        if img is None:
            raise ValueError(f"이미지를 로드할 수 없습니다: {image_path}")
        
        # 좌표 변환
        x1, y1, x2, y2 = int(x1), int(y1), int(x2), int(y2)
        
        # 이미지 크기 확인 및 좌표 보정
        h, w = img.shape[:2]
        x1 = max(0, min(x1, w-1))
        y1 = max(0, min(y1, h-1))
        x2 = max(x1+1, min(x2, w))
        y2 = max(y1+1, min(y2, h))
        
        # 크롭
        cropped = img[y1:y2, x1:x2]
        
        # 출력 디렉토리 생성
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        
        # 저장
        cv2.imwrite(output_path, cropped)
        print(f"이미지 크롭 완료: {output_path}")
        
    except Exception as e:
        print(f"이미지 크롭 실패: {e}")
        sys.exit(1)

if __name__ == "__main__":
    if len(sys.argv) != 7:
        print("사용법: python crop_image.py <image_path> <x1> <y1> <x2> <y2> <output_path>")
        sys.exit(1)
    
    crop_image(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5], sys.argv[6])
""";
            Files.write(cropScript, cropPythonScript.getBytes());
        }
    }
    
    /**
     * Python Vision API 스크립트 생성
     */
    private void createPythonVisionScript() throws IOException {
        Path scriptDir = Paths.get(PYTHON_SCRIPT_DIR);
        Files.createDirectories(scriptDir);
        
        Path scriptFile = scriptDir.resolve("vision_processing.py");
        
        if (!Files.exists(scriptFile)) {
            String pythonScript = """
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import sys
import base64
import requests
import json

def encode_image(image_path):
    with open(image_path, "rb") as image_file:
        return base64.b64encode(image_file.read()).decode('utf-8')

def analyze_with_vision_api(image_path, job_id, block_index, api_key):
    try:
        # 이미지 인코딩
        base64_image = encode_image(image_path)
        
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}"
        }
        
        payload = {
            "model": "gpt-4-vision-preview",
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "text",
                            "text": "이 이미지의 내용을 한국어로 자세히 설명해주세요. 특히 텍스트, 도표, 그림 등의 내용을 포함해주세요."
                        },
                        {
                            "type": "image_url",
                            "image_url": {
                                "url": f"data:image/jpeg;base64,{base64_image}"
                            }
                        }
                    ]
                }
            ],
            "max_tokens": 300
        }
        
        response = requests.post("https://api.openai.com/v1/chat/completions", headers=headers, json=payload)
        
        if response.status_code == 200:
            result = response.json()
            content = result['choices'][0]['message']['content']
            
            print(f"VISION_DESCRIPTION:{content}")
            print("VISION_CONFIDENCE:0.85")
            
            return content, 0.85
        else:
            print(f"Vision API 오류: {response.status_code} - {response.text}")
            print("VISION_DESCRIPTION:Vision API 처리 실패")
            print("VISION_CONFIDENCE:0.1")
            return "Vision API 처리 실패", 0.1
            
    except Exception as e:
        print(f"Vision API 처리 실패: {e}")
        print("VISION_DESCRIPTION:Vision API 처리 중 오류 발생")
        print("VISION_CONFIDENCE:0.1")
        return "Vision API 처리 중 오류 발생", 0.1

if __name__ == "__main__":
    if len(sys.argv) != 5:
        print("사용법: python vision_processing.py <image_path> <job_id> <block_index> <api_key>")
        sys.exit(1)
    
    image_path = sys.argv[1]
    job_id = sys.argv[2]
    block_index = sys.argv[3]
    api_key = sys.argv[4]
    
    analyze_with_vision_api(image_path, job_id, block_index, api_key)
""";
            Files.write(scriptFile, pythonScript.getBytes());
            log.info("Python Vision API 스크립트 생성 완료: {}", scriptFile);
        }
    }
    
    /**
     * 텍스트 블록 조회
     */
    public List<TextBlock> getTextBlocks(String jobId) {
        Optional<AnalysisJob> job = analysisJobRepository.findByJobId(jobId);
        if (job.isPresent()) {
            return textBlockRepository.findByAnalysisJob(job.get());
        }
        return Collections.emptyList();
    }
}
