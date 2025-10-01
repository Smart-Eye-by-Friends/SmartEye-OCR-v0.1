package com.smarteye.application.file;

import com.smarteye.infrastructure.config.SmartEyeProperties;
import com.smarteye.shared.exception.FileProcessingException;
import com.smarteye.shared.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smarteye.application.analysis.AnalysisJobService;
import com.smarteye.application.user.UserService;
import com.smarteye.domain.document.entity.DocumentPage;
import com.smarteye.infrastructure.external.*;
import com.smarteye.application.file.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Service
public class FileService {
    
    private static final Logger logger = LoggerFactory.getLogger(FileService.class);
    
    @Autowired
    private SmartEyeProperties properties;
    
    @Autowired
    private FileUtils fileUtils;
    
    @PostConstruct
    public void init() {
        // 필요한 디렉토리들 생성
        createDirectoryIfNotExists(properties.getUpload().getDirectory());
        createDirectoryIfNotExists(properties.getUpload().getTempDirectory());
        createDirectoryIfNotExists("static");
        createDirectoryIfNotExists("logs");
        
        logger.info("FileService 초기화 완료 - 업로드 디렉토리: {}, 임시 디렉토리: {}", 
                   properties.getUpload().getDirectory(), 
                   properties.getUpload().getTempDirectory());
    }
    
    public String saveUploadedFile(MultipartFile file, String jobId) {
        try {
            logger.info("파일 저장 시작: {} (Job: {})", file.getOriginalFilename(), jobId);
            
            String savedPath = fileUtils.saveUploadedFile(file, properties.getUpload().getDirectory(), jobId);
            
            logger.info("파일 저장 완료: {} -> {}", file.getOriginalFilename(), savedPath);
            return savedPath;
            
        } catch (Exception e) {
            logger.error("파일 저장 실패: {} (Job: {}) - {}", file.getOriginalFilename(), jobId, e.getMessage(), e);
            throw new FileProcessingException("파일 저장 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    public CompletableFuture<String> saveUploadedFileAsync(MultipartFile file, String jobId) {
        return CompletableFuture.supplyAsync(() -> saveUploadedFile(file, jobId));
    }
    
    public String saveTempFile(byte[] data, String filename, String jobId) {
        try {
            logger.debug("임시 파일 저장: {} (Job: {})", filename, jobId);
            
            String tempPath = fileUtils.saveTempFile(data, properties.getUpload().getTempDirectory(), filename);
            
            logger.debug("임시 파일 저장 완료: {}", tempPath);
            return tempPath;
            
        } catch (Exception e) {
            logger.error("임시 파일 저장 실패: {} (Job: {}) - {}", filename, jobId, e.getMessage(), e);
            throw new FileProcessingException("임시 파일 저장 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    public void deleteFile(String filepath) {
        try {
            fileUtils.deleteFile(filepath);
            logger.debug("파일 삭제 완료: {}", filepath);
        } catch (Exception e) {
            logger.warn("파일 삭제 실패: {} - {}", filepath, e.getMessage());
        }
    }
    
    public CompletableFuture<Void> deleteFileAsync(String filepath) {
        return CompletableFuture.runAsync(() -> deleteFile(filepath));
    }
    
    public void cleanupJobFiles(String jobId) {
        try {
            logger.info("작업 파일 정리 시작: {}", jobId);
            
            fileUtils.cleanupTempFiles(properties.getUpload().getTempDirectory(), jobId);
            
            // 추가적으로 업로드 디렉토리에서도 해당 jobId 관련 파일들 정리
            cleanupJobFilesInDirectory(properties.getUpload().getDirectory(), jobId);
            cleanupJobFilesInDirectory("static", jobId);
            
            logger.info("작업 파일 정리 완료: {}", jobId);
            
        } catch (Exception e) {
            logger.warn("작업 파일 정리 중 오류 발생: {} - {}", jobId, e.getMessage());
        }
    }
    
    public CompletableFuture<Void> cleanupJobFilesAsync(String jobId) {
        return CompletableFuture.runAsync(() -> cleanupJobFiles(jobId));
    }
    
    public boolean fileExists(String filepath) {
        try {
            Path path = Paths.get(filepath);
            return Files.exists(path);
        } catch (Exception e) {
            logger.warn("파일 존재 확인 실패: {} - {}", filepath, e.getMessage());
            return false;
        }
    }
    
    public long getFileSize(String filepath) {
        try {
            Path path = Paths.get(filepath);
            if (Files.exists(path)) {
                return Files.size(path);
            }
            return 0L;
        } catch (Exception e) {
            logger.warn("파일 크기 확인 실패: {} - {}", filepath, e.getMessage());
            return 0L;
        }
    }
    
    public String getFileExtension(String filename) {
        return fileUtils.getFileExtension(filename);
    }
    
    public String getBaseName(String filename) {
        return fileUtils.getBaseName(filename);
    }
    
    public boolean isImageFile(String filename) {
        return fileUtils.isImageFile(filename);
    }
    
    public boolean isPdfFile(String filename) {
        return fileUtils.isPdfFile(filename);
    }
    
    public void validateFile(MultipartFile file) {
        fileUtils.validateFile(file);
    }
    
    public String generateJobId() {
        return fileUtils.generateJobId();
    }
    
    public List<String> listFiles(String directory) {
        try {
            Path dirPath = Paths.get(directory);
            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                return List.of();
            }
            
            try (Stream<Path> paths = Files.list(dirPath)) {
                return paths
                        .filter(Files::isRegularFile)
                        .map(Path::toString)
                        .sorted()
                        .toList();
            }
            
        } catch (IOException e) {
            logger.error("디렉토리 파일 목록 조회 실패: {} - {}", directory, e.getMessage());
            throw new FileProcessingException("디렉토리 파일 목록을 조회할 수 없습니다: " + e.getMessage(), e);
        }
    }
    
    public void cleanupOldFiles(int daysOld) {
        try {
            logger.info("오래된 파일 정리 시작 ({}일 이상)", daysOld);
            
            long cutoffTime = System.currentTimeMillis() - (daysOld * 24L * 60 * 60 * 1000);
            
            cleanupOldFilesInDirectory(properties.getUpload().getDirectory(), cutoffTime);
            cleanupOldFilesInDirectory(properties.getUpload().getTempDirectory(), cutoffTime);
            cleanupOldFilesInDirectory("static", cutoffTime);
            
            logger.info("오래된 파일 정리 완료");
            
        } catch (Exception e) {
            logger.error("오래된 파일 정리 중 오류 발생: {}", e.getMessage(), e);
        }
    }
    
    public CompletableFuture<Void> cleanupOldFilesAsync(int daysOld) {
        return CompletableFuture.runAsync(() -> cleanupOldFiles(daysOld));
    }
    
    // Helper methods
    private void createDirectoryIfNotExists(String directory) {
        try {
            Path path = Paths.get(directory);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                logger.info("디렉토리 생성: {}", directory);
            }
        } catch (IOException e) {
            logger.error("디렉토리 생성 실패: {} - {}", directory, e.getMessage());
            throw new FileProcessingException("디렉토리를 생성할 수 없습니다: " + directory, e);
        }
    }
    
    private void cleanupJobFilesInDirectory(String directory, String jobId) {
        try {
            Path dirPath = Paths.get(directory);
            if (!Files.exists(dirPath)) return;
            
            try (Stream<Path> paths = Files.walk(dirPath)) {
                paths.filter(Files::isRegularFile)
                     .filter(path -> path.getFileName().toString().contains(jobId))
                     .forEach(path -> {
                         try {
                             Files.delete(path);
                             logger.debug("작업 파일 삭제: {}", path);
                         } catch (IOException e) {
                             logger.warn("작업 파일 삭제 실패: {} - {}", path, e.getMessage());
                         }
                     });
            }
        } catch (IOException e) {
            logger.warn("디렉토리 정리 중 오류: {} - {}", directory, e.getMessage());
        }
    }
    
    private void cleanupOldFilesInDirectory(String directory, long cutoffTime) {
        try {
            Path dirPath = Paths.get(directory);
            if (!Files.exists(dirPath)) return;
            
            try (Stream<Path> paths = Files.walk(dirPath)) {
                paths.filter(Files::isRegularFile)
                     .filter(path -> {
                         try {
                             return Files.getLastModifiedTime(path).toMillis() < cutoffTime;
                         } catch (IOException e) {
                             return false;
                         }
                     })
                     .forEach(path -> {
                         try {
                             Files.delete(path);
                             logger.debug("오래된 파일 삭제: {}", path);
                         } catch (IOException e) {
                             logger.warn("오래된 파일 삭제 실패: {} - {}", path, e.getMessage());
                         }
                     });
            }
        } catch (IOException e) {
            logger.warn("오래된 파일 정리 중 오류: {} - {}", directory, e.getMessage());
        }
    }
}