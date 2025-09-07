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
import java.util.function.Consumer;
import java.util.stream.IntStream;

@Service
public class PDFService {
    
    private static final Logger logger = LoggerFactory.getLogger(PDFService.class);
    
    @Autowired
    private ImageProcessingService imageProcessingService;
    
    private static final int DEFAULT_DPI = 300;
    private static final int MAX_PAGES_PER_PDF = 10000; // 안전을 위한 페이지 제한
    private static final int STREAM_BATCH_SIZE = 5; // 스트리밍 처리 시 배치 크기
    private static final int MEMORY_THRESHOLD_PAGES = 50; // 메모리 최적화 임계값
    
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
    
    /**
     * Level 2: 메모리 최적화된 스트리밍 PDF 처리 메서드들
     * 대용량 PDF (200+ 페이지)를 위한 배치 처리 및 콜백 기반 스트리밍
     */
    
    /**
     * 스트리밍 방식으로 PDF를 페이지별로 처리 (메모리 최적화)
     * 각 페이지를 개별적으로 처리하여 메모리 사용량 최소화
     * 
     * @param pdfFile PDF 파일
     * @param pageProcessor 각 페이지를 처리할 콜백 함수 (pageNumber, BufferedImage) -> void
     * @param batchSize 한번에 처리할 페이지 수 (메모리 제어)
     * @return 총 처리된 페이지 수
     */
    public int processLargePDFStream(MultipartFile pdfFile, 
                                   PageProcessor pageProcessor, 
                                   int batchSize) {
        try (InputStream inputStream = pdfFile.getInputStream()) {
            logger.info("대용량 PDF 스트리밍 처리 시작: {} ({} bytes, 배치 크기: {})", 
                pdfFile.getOriginalFilename(), pdfFile.getSize(), batchSize);
            
            return processLargePDFStream(inputStream, pageProcessor, batchSize);
            
        } catch (IOException e) {
            logger.error("PDF 스트리밍 처리 실패: {} - {}", pdfFile.getOriginalFilename(), e.getMessage(), e);
            throw new FileProcessingException("PDF 스트리밍 처리에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 스트리밍 방식으로 PDF를 페이지별로 처리 (InputStream 버전)
     */
    public int processLargePDFStream(InputStream pdfStream, 
                                   PageProcessor pageProcessor, 
                                   int batchSize) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfStream))) {
            int pageCount = document.getNumberOfPages();
            
            if (pageCount == 0) {
                throw new FileProcessingException("PDF에 페이지가 없습니다.");
            }
            
            logger.info("PDF 스트리밍 처리 시작: {}페이지 (배치 크기: {})", pageCount, batchSize);
            PDFRenderer renderer = new PDFRenderer(document);
            
            // 배치별로 페이지 처리
            for (int startPage = 0; startPage < pageCount; startPage += batchSize) {
                int endPage = Math.min(startPage + batchSize, pageCount);
                
                logger.debug("배치 처리: 페이지 {} ~ {} (총 {}페이지)", 
                    startPage + 1, endPage, pageCount);
                
                // 배치 내 페이지들을 병렬 처리
                IntStream.range(startPage, endPage)
                    .parallel()
                    .forEach(pageIndex -> {
                        try {
                            BufferedImage image = renderer.renderImageWithDPI(
                                pageIndex, DEFAULT_DPI, ImageType.RGB);
                            
                            // 메모리 최적화를 위한 이미지 크기 조정
                            if (image.getWidth() > 3000 || image.getHeight() > 3000) {
                                image = imageProcessingService.resizeImageKeepAspectRatio(
                                    image, 2048, 2048);
                            }
                            
                            // 페이지 처리 콜백 호출
                            pageProcessor.processPage(pageIndex + 1, image);
                            
                            logger.debug("페이지 {} 스트리밍 처리 완료", pageIndex + 1);
                            
                        } catch (IOException e) {
                            logger.error("페이지 {} 스트리밍 처리 실패: {}", pageIndex + 1, e.getMessage());
                            throw new RuntimeException(
                                String.format("페이지 %d 처리 중 오류: %s", pageIndex + 1, e.getMessage()), e);
                        }
                    });
                
                // 배치 처리 완료 후 메모리 정리 시점 제공
                System.gc(); // 명시적 GC 호출 (선택적)
                
                logger.debug("배치 {}/{} 완료 (페이지 {} ~ {})", 
                    (startPage / batchSize) + 1, 
                    (pageCount + batchSize - 1) / batchSize,
                    startPage + 1, endPage);
            }
            
            logger.info("PDF 스트리밍 처리 완료: 총 {}페이지", pageCount);
            return pageCount;
            
        } catch (IOException e) {
            logger.error("PDF 스트리밍 처리 실패: {}", e.getMessage(), e);
            throw new FileProcessingException("PDF 스트리밍 처리에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 기본 배치 크기로 대용량 PDF 스트리밍 처리
     */
    public int processLargePDFStream(MultipartFile pdfFile, PageProcessor pageProcessor) {
        return processLargePDFStream(pdfFile, pageProcessor, STREAM_BATCH_SIZE);
    }
    
    /**
     * 메모리 사용량에 따른 적응적 PDF 처리
     * 페이지 수가 임계값을 초과하면 자동으로 스트리밍 모드로 전환
     * 
     * @param pdfFile PDF 파일
     * @param pageProcessor 페이지 처리 콜백
     * @param memoryOptimized 강제 메모리 최적화 모드 여부
     * @return 처리 결과 (전체 로드 시 이미지 리스트, 스트리밍 시 페이지 수)
     */
    public PDFProcessingResult processAdaptivePDF(MultipartFile pdfFile, 
                                                PageProcessor pageProcessor,
                                                boolean memoryOptimized) {
        try (InputStream inputStream = pdfFile.getInputStream()) {
            
            // 1. PDF 메타데이터 확인
            PDFMetadata metadata = extractPDFMetadata(pdfFile);
            int pageCount = metadata.getPageCount();
            
            logger.info("적응적 PDF 처리 시작: {} ({}페이지, 메모리 최적화: {})", 
                pdfFile.getOriginalFilename(), pageCount, memoryOptimized);
            
            // 2. 처리 방식 결정
            boolean useStreaming = memoryOptimized || pageCount > MEMORY_THRESHOLD_PAGES;
            
            if (useStreaming) {
                logger.info("스트리밍 모드 선택 (페이지 수: {}, 임계값: {})", 
                    pageCount, MEMORY_THRESHOLD_PAGES);
                
                // 배치 크기 동적 조정
                int adaptiveBatchSize = calculateOptimalBatchSize(pageCount);
                int processedPages = processLargePDFStream(pdfFile, pageProcessor, adaptiveBatchSize);
                
                return new PDFProcessingResult(processedPages, true, null);
                
            } else {
                logger.info("전체 로드 모드 선택 (페이지 수: {}, 임계값: {})", 
                    pageCount, MEMORY_THRESHOLD_PAGES);
                
                // 기존 전체 로드 방식 사용
                List<BufferedImage> images = convertPDFToImages(pdfFile);
                
                // 페이지별 콜백 호출
                for (int i = 0; i < images.size(); i++) {
                    pageProcessor.processPage(i + 1, images.get(i));
                }
                
                return new PDFProcessingResult(images.size(), false, images);
            }
            
        } catch (IOException e) {
            logger.error("적응적 PDF 처리 실패: {} - {}", pdfFile.getOriginalFilename(), e.getMessage(), e);
            throw new FileProcessingException("적응적 PDF 처리에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 페이지 수에 따른 최적 배치 크기 계산
     * 메모리 사용량과 처리 효율성의 균형을 고려
     */
    private int calculateOptimalBatchSize(int totalPages) {
        if (totalPages <= 20) return 5;        // 소규모: 5페이지씩
        if (totalPages <= 100) return 10;      // 중규모: 10페이지씩
        if (totalPages <= 300) return 15;      // 대규모: 15페이지씩
        return 20;                              // 초대규모: 20페이지씩
    }
    
    /**
     * 비동기 대용량 PDF 스트리밍 처리
     */
    public CompletableFuture<Integer> processLargePDFStreamAsync(
            MultipartFile pdfFile, 
            PageProcessor pageProcessor,
            int batchSize) {
        return CompletableFuture.supplyAsync(() -> 
            processLargePDFStream(pdfFile, pageProcessor, batchSize));
    }
    
    /**
     * 페이지 처리 인터페이스
     * 각 페이지가 변환될 때마다 호출되는 콜백 함수
     */
    @FunctionalInterface
    public interface PageProcessor {
        void processPage(int pageNumber, BufferedImage pageImage) throws IOException;
    }
    
    /**
     * PDF 처리 결과를 담는 클래스
     */
    public static class PDFProcessingResult {
        private final int processedPages;
        private final boolean streamingMode;
        private final List<BufferedImage> images; // 전체 로드 모드일 때만 사용
        
        public PDFProcessingResult(int processedPages, boolean streamingMode, List<BufferedImage> images) {
            this.processedPages = processedPages;
            this.streamingMode = streamingMode;
            this.images = images;
        }
        
        public int getProcessedPages() { return processedPages; }
        public boolean isStreamingMode() { return streamingMode; }
        public List<BufferedImage> getImages() { return images; }
        
        @Override
        public String toString() {
            return String.format("PDFProcessingResult{pages=%d, streaming=%s, hasImages=%s}",
                processedPages, streamingMode, images != null);
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