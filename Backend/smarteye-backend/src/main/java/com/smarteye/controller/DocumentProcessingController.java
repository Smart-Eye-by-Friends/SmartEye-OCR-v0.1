package com.smarteye.controller;

import com.smarteye.dto.DocumentResponse;
import com.smarteye.dto.FormatTextResponse;
import com.smarteye.util.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 문서 처리 컨트롤러 - 텍스트 편집 및 문서 생성
 * Python api_server.py의 format_text_from_json, save_as_word, download 엔드포인트 변환
 */
@RestController
@RequestMapping("/api/document")
@CrossOrigin(origins = "*")
@Validated
@Tag(name = "Document Processing", description = "문서 처리 및 변환 API")
public class DocumentProcessingController {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessingController.class);
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${smarteye.static.directory:./static}")
    private String staticDirectory;
    
    /**
     * JSON 파일을 업로드받아서 포맷팅된 텍스트 생성
     * Python api_server.py의 /format-text 엔드포인트와 동일
     */
    @PostMapping("/format-text")
    public ResponseEntity<FormatTextResponse> formatText(
            @RequestParam("jsonFile") MultipartFile jsonFile) {
        
        logger.info("텍스트 포맷팅 요청 - 파일: {}", jsonFile.getOriginalFilename());
        
        try {
            // JSON 파일 검증
            if (jsonFile.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new FormatTextResponse(false, null, "JSON 파일이 비어있습니다."));
            }
            
            if (!isJsonFile(jsonFile)) {
                return ResponseEntity.badRequest()
                    .body(new FormatTextResponse(false, null, "JSON 파일만 업로드 가능합니다."));
            }
            
            // JSON 파일 읽기
            String jsonContent = new String(jsonFile.getBytes(), "UTF-8");
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(jsonContent, Map.class);
            
            // 포맷팅된 텍스트 생성
            String formattedText = JsonUtils.createFormattedText(data);
            
            logger.info("텍스트 포맷팅 완료 - 텍스트 길이: {}", formattedText.length());
            
            return ResponseEntity.ok(
                new FormatTextResponse(true, formattedText, "텍스트 포맷팅이 완료되었습니다.")
            );
            
        } catch (Exception e) {
            logger.error("텍스트 포맷팅 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(new FormatTextResponse(false, null, "텍스트 포맷팅 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    /**
     * 편집된 텍스트를 워드 문서로 저장
     * Python api_server.py의 /save-as-word 엔드포인트와 동일
     */
    @PostMapping("/save-as-word")
    public ResponseEntity<DocumentResponse> saveAsWord(
            @RequestParam("text") String text,
            @RequestParam(value = "filename", defaultValue = "smarteye_document") String filename) {
        
        logger.info("워드 문서 생성 요청 - 파일명: {}, 텍스트 길이: {}", filename, text.length());
        
        try {
            // 텍스트 검증
            if (text == null || text.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new DocumentResponse(false, "텍스트 내용이 비어있습니다.", null, null));
            }
            
            // 워드 문서 생성
            XWPFDocument document = createWordDocument(text);
            
            // 파일명 정리
            String cleanFilename = cleanFilename(filename);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String safeFilename = cleanFilename + "_" + timestamp + ".docx";
            
            // static 폴더에 저장
            ensureStaticDirectoryExists();
            Path filepath = Paths.get(staticDirectory, safeFilename);
            
            // 파일 저장
            document.write(Files.newOutputStream(filepath));
            document.close();
            
            String downloadUrl = "/static/" + safeFilename;
            
            logger.info("워드 문서 저장 완료: {}", filepath);
            
            return ResponseEntity.ok(
                new DocumentResponse(true, "워드 문서가 성공적으로 생성되었습니다.", 
                                   safeFilename, downloadUrl)
            );
            
        } catch (Exception e) {
            logger.error("워드 문서 생성 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(new DocumentResponse(false, "워드 문서 생성 중 오류가 발생했습니다: " + e.getMessage(), 
                                         null, null));
        }
    }
    
    /**
     * 생성된 파일 다운로드
     * Python api_server.py의 /download/{filename} 엔드포인트와 동일
     */
    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        
        logger.info("파일 다운로드 요청: {}", filename);
        
        try {
            // 파일 경로 검증
            Path filepath = Paths.get(staticDirectory, filename);
            
            if (!Files.exists(filepath)) {
                logger.warn("다운로드 요청된 파일이 존재하지 않음: {}", filepath);
                return ResponseEntity.notFound().build();
            }
            
            // 보안을 위한 경로 검증 (디렉토리 트래버설 공격 방지)
            if (!filepath.normalize().startsWith(Paths.get(staticDirectory).normalize())) {
                logger.warn("잘못된 파일 경로 접근 시도: {}", filename);
                return ResponseEntity.badRequest().build();
            }
            
            Resource resource = new UrlResource(filepath.toUri());
            
            if (!resource.exists() || !resource.isReadable()) {
                logger.warn("읽을 수 없는 파일: {}", filepath);
                return ResponseEntity.notFound().build();
            }
            
            // Content-Type 결정
            String contentType = determineContentType(filename);
            
            logger.info("파일 다운로드 시작: {} ({})", filename, contentType);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
                    
        } catch (Exception e) {
            logger.error("파일 다운로드 중 오류 발생: {} - {}", filename, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // === Private Helper Methods ===
    
    private boolean isJsonFile(MultipartFile file) {
        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();
        
        return (contentType != null && contentType.equals("application/json")) ||
               (originalFilename != null && originalFilename.toLowerCase().endsWith(".json"));
    }
    
    private XWPFDocument createWordDocument(String text) {
        XWPFDocument document = new XWPFDocument();
        
        try {
            // 문서 제목 추가
            XWPFParagraph titleParagraph = document.createParagraph();
            titleParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText("SmartEye OCR 분석 결과");
            titleRun.setBold(true);
            titleRun.setFontSize(18);
            
            // 생성 날짜 추가
            XWPFParagraph dateParagraph = document.createParagraph();
            dateParagraph.setAlignment(ParagraphAlignment.RIGHT);
            XWPFRun dateRun = dateParagraph.createRun();
            dateRun.setText("생성일: " + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH시 mm분")));
            dateRun.setFontSize(10);
            
            // 구분선 추가
            XWPFParagraph separatorParagraph = document.createParagraph();
            XWPFRun separatorRun = separatorParagraph.createRun();
            separatorRun.setText("=".repeat(50));
            
            // 텍스트 내용 추가 (줄바꿈 처리)
            String[] lines = text.split("\n");
            
            for (String line : lines) {
                line = line.trim();
                
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                
                // 빈 줄 처리
                if (line.isEmpty()) {
                    run.setText(" "); // 공백으로 빈 줄 표현
                    continue;
                }
                
                // 제목 형태 감지 ([제목] 형식)
                if (line.startsWith("[") && line.endsWith("]")) {
                    run.setText(line.substring(1, line.length() - 1)); // 대괄호 제거
                    run.setBold(true);
                    run.setFontSize(14);
                }
                // 문제번호 형태 감지 (숫자. 형식)
                else if (line.matches("^\\d+\\..*")) {
                    run.setText(line);
                    run.setBold(true);
                    run.setFontSize(12);
                }
                // 일반 텍스트
                else {
                    run.setText(line);
                    run.setFontSize(11);
                }
            }
            
            return document;
            
        } catch (Exception e) {
            logger.error("워드 문서 생성 중 오류: {}", e.getMessage(), e);
            try {
                document.close();
            } catch (IOException ignored) {}
            throw new RuntimeException("워드 문서 생성에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    private String cleanFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "smarteye_document";
        }
        
        // .docx 확장자 제거
        if (filename.toLowerCase().endsWith(".docx")) {
            filename = filename.substring(0, filename.length() - 5);
        }
        
        // 파일명에서 위험한 문자 제거
        return filename.replaceAll("[^a-zA-Z0-9가-힣\\s\\-_]", "")
                      .trim()
                      .replaceAll("\\s+", "_");
    }
    
    private String determineContentType(String filename) {
        if (filename == null) {
            return "application/octet-stream";
        }
        
        String lowerFilename = filename.toLowerCase();
        
        if (lowerFilename.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (lowerFilename.endsWith(".json")) {
            return "application/json";
        } else if (lowerFilename.endsWith(".png")) {
            return "image/png";
        } else if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) {
            return "image/jpeg";
        } else {
            return "application/octet-stream";
        }
    }
    
    private void ensureStaticDirectoryExists() throws IOException {
        Path staticPath = Paths.get(staticDirectory);
        if (!Files.exists(staticPath)) {
            Files.createDirectories(staticPath);
        }
    }
}