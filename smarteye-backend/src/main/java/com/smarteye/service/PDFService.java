package com.smarteye.service;

import com.smarteye.exception.FileProcessingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class PDFService {
    
    private static final Logger logger = LoggerFactory.getLogger(PDFService.class);
    
    @Autowired
    private ImageProcessingService imageProcessingService;
    
    private static final int DEFAULT_DPI = 300;
    private static final int MAX_PAGES_PER_PDF = 100; // 안전을 위한 페이지 제한
    
    public List<BufferedImage> convertPDFToImages(MultipartFile pdfFile) {
        try (InputStream inputStream = pdfFile.getInputStream()) {
            logger.info("PDF 이미지 변환 시작: {} ({} bytes)", pdfFile.getOriginalFilename(), pdfFile.getSize());
            
            List<BufferedImage> images = convertPDFToImages(inputStream);
            
            logger.info("PDF 이미지 변환 완료: {} -> {}페이지", pdfFile.getOriginalFilename(), images.size());
            return images;
            
        } catch (IOException e) {
            logger.error("PDF 파일 읽기 실패: {} - {}", pdfFile.getOriginalFilename(), e.getMessage(), e);
            throw new FileProcessingException("PDF 파일을 읽을 수 없습니다: " + e.getMessage(), e);
        }
    }
    
    public List<BufferedImage> convertPDFToImages(String pdfFilePath) {
        try {
            logger.info("PDF 이미지 변환 시작: {}", pdfFilePath);
            
            if (!Files.exists(Paths.get(pdfFilePath))) {
                throw new FileProcessingException("PDF 파일을 찾을 수 없습니다: " + pdfFilePath);
            }
            
            try (InputStream inputStream = Files.newInputStream(Paths.get(pdfFilePath))) {
                List<BufferedImage> images = convertPDFToImages(inputStream);
                
                logger.info("PDF 이미지 변환 완료: {} -> {}페이지", pdfFilePath, images.size());
                return images;
            }
            
        } catch (IOException e) {
            logger.error("PDF 파일 읽기 실패: {} - {}", pdfFilePath, e.getMessage(), e);
            throw new FileProcessingException("PDF 파일을 읽을 수 없습니다: " + e.getMessage(), e);
        }
    }
    
    public List<BufferedImage> convertPDFToImages(InputStream pdfStream) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfStream))) {
            int pageCount = document.getNumberOfPages();
            
            if (pageCount == 0) {
                throw new FileProcessingException("PDF에 페이지가 없습니다.");
            }
            
            if (pageCount > MAX_PAGES_PER_PDF) {
                throw new FileProcessingException(
                    String.format("PDF 페이지 수가 제한을 초과했습니다. (현재: %d, 최대: %d)", 
                                pageCount, MAX_PAGES_PER_PDF)
                );
            }
            
            logger.info("PDF 변환 시작: {}페이지", pageCount);
            
            PDFRenderer renderer = new PDFRenderer(document);
            List<BufferedImage> images = new ArrayList<>();
            
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                try {
                    logger.debug("페이지 {}번 변환 중...", pageIndex + 1);
                    
                    BufferedImage image = renderer.renderImageWithDPI(pageIndex, DEFAULT_DPI, ImageType.RGB);
                    
                    // 이미지 전처리 (필요한 경우)
                    if (image.getWidth() > 3000 || image.getHeight() > 3000) {
                        image = imageProcessingService.resizeImageKeepAspectRatio(image, 2048, 2048);
                        logger.debug("페이지 {}번 크기 조정: {}x{}", pageIndex + 1, image.getWidth(), image.getHeight());
                    }
                    
                    images.add(image);
                    
                    logger.debug("페이지 {}번 변환 완료: {}x{}", pageIndex + 1, image.getWidth(), image.getHeight());
                    
                } catch (IOException e) {
                    logger.error("페이지 {}번 변환 실패: {}", pageIndex + 1, e.getMessage());
                    throw new FileProcessingException(
                        String.format("PDF 페이지 %d 변환 중 오류가 발생했습니다: %s", pageIndex + 1, e.getMessage()), e
                    );
                }
            }
            
            logger.info("PDF 변환 완료: {}페이지", images.size());
            return images;
            
        } catch (IOException e) {
            logger.error("PDF 문서 로드 실패: {}", e.getMessage(), e);
            throw new FileProcessingException("PDF 문서를 로드할 수 없습니다: " + e.getMessage(), e);
        }
    }
    
    public CompletableFuture<List<BufferedImage>> convertPDFToImagesAsync(MultipartFile pdfFile) {
        return CompletableFuture.supplyAsync(() -> convertPDFToImages(pdfFile));
    }
    
    public CompletableFuture<List<BufferedImage>> convertPDFToImagesAsync(String pdfFilePath) {
        return CompletableFuture.supplyAsync(() -> convertPDFToImages(pdfFilePath));
    }
    
    public BufferedImage convertPDFPageToImage(MultipartFile pdfFile, int pageNumber) {
        try (InputStream inputStream = pdfFile.getInputStream()) {
            logger.info("PDF 단일 페이지 변환: {} 페이지 {}", pdfFile.getOriginalFilename(), pageNumber);
            
            BufferedImage image = convertPDFPageToImage(inputStream, pageNumber);
            
            logger.info("PDF 단일 페이지 변환 완료: {} 페이지 {} -> {}x{}", 
                       pdfFile.getOriginalFilename(), pageNumber, image.getWidth(), image.getHeight());
            return image;
            
        } catch (IOException e) {
            logger.error("PDF 파일 읽기 실패: {} - {}", pdfFile.getOriginalFilename(), e.getMessage(), e);
            throw new FileProcessingException("PDF 파일을 읽을 수 없습니다: " + e.getMessage(), e);
        }
    }
    
    public BufferedImage convertPDFPageToImage(String pdfFilePath, int pageNumber) {
        try {
            logger.info("PDF 단일 페이지 변환: {} 페이지 {}", pdfFilePath, pageNumber);
            
            try (InputStream inputStream = Files.newInputStream(Paths.get(pdfFilePath))) {
                BufferedImage image = convertPDFPageToImage(inputStream, pageNumber);
                
                logger.info("PDF 단일 페이지 변환 완료: {} 페이지 {} -> {}x{}", 
                           pdfFilePath, pageNumber, image.getWidth(), image.getHeight());
                return image;
            }
            
        } catch (IOException e) {
            logger.error("PDF 파일 읽기 실패: {} - {}", pdfFilePath, e.getMessage(), e);
            throw new FileProcessingException("PDF 파일을 읽을 수 없습니다: " + e.getMessage(), e);
        }
    }
    
    public BufferedImage convertPDFPageToImage(InputStream pdfStream, int pageNumber) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfStream))) {
            int pageCount = document.getNumberOfPages();
            
            if (pageNumber < 1 || pageNumber > pageCount) {
                throw new FileProcessingException(
                    String.format("잘못된 페이지 번호입니다. (요청: %d, 전체 페이지: %d)", pageNumber, pageCount)
                );
            }
            
            int pageIndex = pageNumber - 1; // 0-based index
            
            logger.debug("PDF 페이지 {}번 변환 시작", pageNumber);
            
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, DEFAULT_DPI, ImageType.RGB);
            
            // 이미지 전처리
            if (image.getWidth() > 3000 || image.getHeight() > 3000) {
                image = imageProcessingService.resizeImageKeepAspectRatio(image, 2048, 2048);
                logger.debug("페이지 {}번 크기 조정: {}x{}", pageNumber, image.getWidth(), image.getHeight());
            }
            
            logger.debug("PDF 페이지 {}번 변환 완료: {}x{}", pageNumber, image.getWidth(), image.getHeight());
            return image;
            
        } catch (IOException e) {
            logger.error("PDF 페이지 {}번 변환 실패: {}", pageNumber, e.getMessage(), e);
            throw new FileProcessingException(
                String.format("PDF 페이지 %d 변환 중 오류가 발생했습니다: %s", pageNumber, e.getMessage()), e
            );
        }
    }
    
    public CompletableFuture<BufferedImage> convertPDFPageToImageAsync(MultipartFile pdfFile, int pageNumber) {
        return CompletableFuture.supplyAsync(() -> convertPDFPageToImage(pdfFile, pageNumber));
    }
    
    public PDFMetadata extractPDFMetadata(MultipartFile pdfFile) {
        try (InputStream inputStream = pdfFile.getInputStream()) {
            return extractPDFMetadata(inputStream);
        } catch (IOException e) {
            logger.error("PDF 메타데이터 추출 실패: {} - {}", pdfFile.getOriginalFilename(), e.getMessage(), e);
            throw new FileProcessingException("PDF 메타데이터를 추출할 수 없습니다: " + e.getMessage(), e);
        }
    }
    
    public PDFMetadata extractPDFMetadata(String pdfFilePath) {
        try (InputStream inputStream = Files.newInputStream(Paths.get(pdfFilePath))) {
            return extractPDFMetadata(inputStream);
        } catch (IOException e) {
            logger.error("PDF 메타데이터 추출 실패: {} - {}", pdfFilePath, e.getMessage(), e);
            throw new FileProcessingException("PDF 메타데이터를 추출할 수 없습니다: " + e.getMessage(), e);
        }
    }
    
    public PDFMetadata extractPDFMetadata(InputStream pdfStream) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfStream))) {
            PDFMetadata metadata = new PDFMetadata();
            
            metadata.setPageCount(document.getNumberOfPages());
            metadata.setEncrypted(document.isEncrypted());
            
            if (document.getDocumentInformation() != null) {
                var docInfo = document.getDocumentInformation();
                metadata.setTitle(docInfo.getTitle());
                metadata.setAuthor(docInfo.getAuthor());
                metadata.setSubject(docInfo.getSubject());
                metadata.setCreator(docInfo.getCreator());
                metadata.setProducer(docInfo.getProducer());
                metadata.setCreationDate(docInfo.getCreationDate());
                metadata.setModificationDate(docInfo.getModificationDate());
            }
            
            logger.debug("PDF 메타데이터 추출 완료: {}페이지, 암호화: {}", 
                       metadata.getPageCount(), metadata.isEncrypted());
            
            return metadata;
            
        } catch (IOException e) {
            logger.error("PDF 메타데이터 추출 실패: {}", e.getMessage(), e);
            throw new FileProcessingException("PDF 메타데이터를 추출할 수 없습니다: " + e.getMessage(), e);
        }
    }
    
    public boolean isPDFValid(MultipartFile pdfFile) {
        try (InputStream inputStream = pdfFile.getInputStream()) {
            return isPDFValid(inputStream);
        } catch (IOException e) {
            logger.warn("PDF 유효성 검사 실패: {} - {}", pdfFile.getOriginalFilename(), e.getMessage());
            return false;
        }
    }
    
    public boolean isPDFValid(InputStream pdfStream) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfStream))) {
            int pageCount = document.getNumberOfPages();
            boolean isValid = pageCount > 0 && pageCount <= MAX_PAGES_PER_PDF;
            
            if (!isValid) {
                logger.warn("PDF 유효성 검사 실패: 페이지 수 {}", pageCount);
            }
            
            return isValid;
            
        } catch (IOException e) {
            logger.warn("PDF 유효성 검사 실패: {}", e.getMessage());
            return false;
        }
    }
    
    // Inner class for PDF metadata
    public static class PDFMetadata {
        private int pageCount;
        private boolean encrypted;
        private String title;
        private String author;
        private String subject;
        private String creator;
        private String producer;
        private java.util.Calendar creationDate;
        private java.util.Calendar modificationDate;
        
        // Getters and Setters
        public int getPageCount() { return pageCount; }
        public void setPageCount(int pageCount) { this.pageCount = pageCount; }
        
        public boolean isEncrypted() { return encrypted; }
        public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        
        public String getCreator() { return creator; }
        public void setCreator(String creator) { this.creator = creator; }
        
        public String getProducer() { return producer; }
        public void setProducer(String producer) { this.producer = producer; }
        
        public java.util.Calendar getCreationDate() { return creationDate; }
        public void setCreationDate(java.util.Calendar creationDate) { this.creationDate = creationDate; }
        
        public java.util.Calendar getModificationDate() { return modificationDate; }
        public void setModificationDate(java.util.Calendar modificationDate) { this.modificationDate = modificationDate; }
        
        @Override
        public String toString() {
            return String.format("PDFMetadata{pageCount=%d, encrypted=%s, title='%s', author='%s'}",
                    pageCount, encrypted, title, author);
        }
    }
}