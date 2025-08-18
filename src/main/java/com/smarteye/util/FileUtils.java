package com.smarteye.util;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class FileUtils {
    
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
        "jpg", "jpeg", "png", "pdf", "tiff", "bmp"
    );
    
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    
    public static boolean isValidFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        
        // 파일 크기 체크
        if (file.getSize() > MAX_FILE_SIZE) {
            return false;
        }
        
        // 확장자 체크
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            return false;
        }
        
        String extension = getFileExtension(filename).toLowerCase();
        return ALLOWED_EXTENSIONS.contains(extension);
    }
    
    public static String getFileExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        
        return filename.substring(lastDotIndex + 1);
    }
    
    public static String saveUploadedFile(MultipartFile file, String uploadDir) throws IOException {
        if (!isValidFile(file)) {
            throw new IllegalArgumentException("Invalid file");
        }
        
        // 업로드 디렉토리 생성
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        // 고유한 파일명 생성
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID().toString() + "." + extension;
        
        Path filePath = uploadPath.resolve(uniqueFilename);
        
        // 파일 저장
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        
        return filePath.toString();
    }
    
    public static void deleteFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // 로그만 남기고 예외는 발생시키지 않음
            System.err.println("Failed to delete file: " + filePath);
        }
    }
}
