package com.smarteye.util;

import com.smarteye.exception.FileProcessingException;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class FileUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);
    
    private static final List<String> ALLOWED_IMAGE_EXTENSIONS = Arrays.asList(
        "jpg", "jpeg", "png", "bmp", "tiff", "tif", "webp"
    );
    
    private static final List<String> ALLOWED_DOCUMENT_EXTENSIONS = Arrays.asList(
        "pdf"
    );
    
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    
    public String saveUploadedFile(MultipartFile file, String uploadDirectory, String jobId) {
        try {
            validateFile(file);
            
            // Create directory if not exists
            Path uploadPath = Paths.get(uploadDirectory);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            
            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = FilenameUtils.getExtension(originalFilename);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String uniqueFilename = String.format("%s_%s.%s", 
                FilenameUtils.getBaseName(originalFilename), 
                timestamp, 
                extension
            );
            
            // Save file
            Path filePath = uploadPath.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            
            logger.info("File saved successfully: {} (Job: {})", filePath.toString(), jobId);
            return filePath.toString();
            
        } catch (IOException e) {
            logger.error("Failed to save uploaded file: {}", e.getMessage(), e);
            throw new FileProcessingException("파일 저장에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    public String saveTempFile(byte[] data, String tempDirectory, String filename) {
        try {
            Path tempPath = Paths.get(tempDirectory);
            if (!Files.exists(tempPath)) {
                Files.createDirectories(tempPath);
            }
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String tempFilename = String.format("temp_%s_%s", timestamp, filename);
            Path filePath = tempPath.resolve(tempFilename);
            
            Files.write(filePath, data);
            
            logger.debug("Temporary file saved: {}", filePath.toString());
            return filePath.toString();
            
        } catch (IOException e) {
            logger.error("Failed to save temporary file: {}", e.getMessage(), e);
            throw new FileProcessingException("임시 파일 저장에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    public void deleteFile(String filepath) {
        try {
            Path path = Paths.get(filepath);
            if (Files.exists(path)) {
                Files.delete(path);
                logger.debug("File deleted: {}", filepath);
            }
        } catch (IOException e) {
            logger.warn("Failed to delete file: {} - {}", filepath, e.getMessage());
        }
    }
    
    public void cleanupTempFiles(String tempDirectory, String jobId) {
        try {
            Path tempPath = Paths.get(tempDirectory);
            if (Files.exists(tempPath)) {
                Files.walk(tempPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().contains(jobId))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            logger.debug("Temporary file cleaned up: {}", path.toString());
                        } catch (IOException e) {
                            logger.warn("Failed to cleanup temp file: {} - {}", path.toString(), e.getMessage());
                        }
                    });
            }
        } catch (IOException e) {
            logger.warn("Failed to cleanup temp files for job {}: {}", jobId, e.getMessage());
        }
    }
    
    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileProcessingException("업로드된 파일이 비어있습니다.", "EMPTY_FILE");
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileProcessingException(
                String.format("파일 크기가 허용된 크기(%dMB)를 초과했습니다.", MAX_FILE_SIZE / (1024 * 1024)), 
                "FILE_SIZE_EXCEEDED"
            );
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new FileProcessingException("파일명이 유효하지 않습니다.", "INVALID_FILENAME");
        }
        
        String extension = FilenameUtils.getExtension(originalFilename).toLowerCase();
        if (!isAllowedExtension(extension)) {
            throw new FileProcessingException(
                String.format("지원되지 않는 파일 형식입니다. 허용된 형식: %s, %s", 
                    String.join(", ", ALLOWED_IMAGE_EXTENSIONS),
                    String.join(", ", ALLOWED_DOCUMENT_EXTENSIONS)
                ), 
                "UNSUPPORTED_FILE_TYPE"
            );
        }
    }
    
    public boolean isImageFile(String filename) {
        String extension = FilenameUtils.getExtension(filename).toLowerCase();
        return ALLOWED_IMAGE_EXTENSIONS.contains(extension);
    }
    
    public boolean isPdfFile(String filename) {
        String extension = FilenameUtils.getExtension(filename).toLowerCase();
        return ALLOWED_DOCUMENT_EXTENSIONS.contains(extension);
    }
    
    private boolean isAllowedExtension(String extension) {
        return ALLOWED_IMAGE_EXTENSIONS.contains(extension) || 
               ALLOWED_DOCUMENT_EXTENSIONS.contains(extension);
    }
    
    public String generateJobId() {
        return UUID.randomUUID().toString();
    }
    
    public String getFileExtension(String filename) {
        return FilenameUtils.getExtension(filename).toLowerCase();
    }
    
    public String getBaseName(String filename) {
        return FilenameUtils.getBaseName(filename);
    }
}