package com.smarteye.service;

import com.smarteye.dto.AnalysisResponse;
import com.smarteye.dto.BookDto;
import com.smarteye.dto.CreateBookRequest;
import com.smarteye.entity.AnalysisJob;
import com.smarteye.entity.DocumentPage;
import com.smarteye.entity.User;
import com.smarteye.repository.DocumentPageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 여러 이미지 동시 분석을 담당하는 비즈니스 서비스
 * DocumentAnalysisController에서 분리된 다중 이미지 분석 로직
 */
@Service
@Transactional
public class MultipleImageAnalysisService {
    
    private static final Logger logger = LoggerFactory.getLogger(MultipleImageAnalysisService.class);
    
    @Autowired
    private ImageAnalysisService imageAnalysisService;
    
    @Autowired
    private BookService bookService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private AnalysisResponseBuilder responseBuilder;
    
    @Autowired
    private DocumentPageRepository documentPageRepository;
    
    /**
     * 여러 이미지를 동시 분석하고 Book으로 그룹화하여 AnalysisResponse를 반환
     * 
     * @param images 분석할 이미지 파일 배열
     * @param modelChoice 분석 모델 선택
     * @param apiKey OpenAI API 키 (선택사항)
     * @param bookTitle 책 제목 (선택사항, 미지정 시 자동 생성)
     * @return 다중 이미지 분석 결과
     */
    public CompletableFuture<MultipleImageAnalysisResult> analyzeMultipleImages(
            MultipartFile[] images, String modelChoice, String apiKey, String bookTitle) {
        
        logger.info("여러 이미지 분석 요청 - 이미지 수: {}, 모델: {}, API키 존재: {}", 
                   images.length, modelChoice, apiKey != null && !apiKey.trim().isEmpty());
        
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                // 1. 이미지 배열 검증
                validateMultipleImages(images);
                
                // 2. 사용자 조회 또는 익명 사용자 생성
                User user = userService.getOrCreateAnonymousUser();
                
                // 3. Book 생성 (이미지 집합을 하나의 책으로 그룹화)
                BookDto bookDto = createBookFromImages(images, bookTitle, user.getId());
                
                logger.info("Book 생성 완료 - ID: {}, 제목: '{}', 이미지 수: {}", 
                           bookDto.getId(), bookDto.getTitle(), images.length);
                
                // 4. 이미지 처리 방식 결정 및 실행
                MultipleImageProcessingResult processingResult = processMultipleImages(
                    images, user, bookDto, modelChoice, apiKey
                );
                
                // 5. 성공/실패 결과 분리
                List<ImageProcessingResult> successfulResults = processingResult.getResults().stream()
                    .filter(ImageProcessingResult::isSuccess)
                    .collect(Collectors.toList());
                
                List<ImageProcessingResult> failedResults = processingResult.getResults().stream()
                    .filter(result -> !result.isSuccess())
                    .collect(Collectors.toList());
                
                if (successfulResults.isEmpty()) {
                    return new MultipleImageAnalysisResult(false, "모든 이미지 분석에 실패했습니다.", null);
                }
                
                // 6. Book 진행률 업데이트
                bookService.updateBookProgress(bookDto.getId());
                
                // 7. 완전한 DocumentPage 데이터 로드
                List<DocumentPage> completeDocumentPages = loadCompleteDocumentPages(successfulResults);
                
                logger.info("완전한 DocumentPage 데이터 로드 완료 - 성공: {}개, 실패: {}개, 총 페이지: {}", 
                           successfulResults.size(), failedResults.size(), completeDocumentPages.size());
                
                // 8. 다중 이미지 분석 응답 생성
                if (!completeDocumentPages.isEmpty()) {
                    AnalysisResponse response = buildMultipleImagesResponse(
                        completeDocumentPages, bookDto, successfulResults, failedResults, 
                        startTime, processingResult.getBaseTimestamp()
                    );
                    
                    logger.info("여러 이미지 분석 완료 - 책 ID: {}, 성공: {}개, 실패: {}개", 
                               bookDto.getId(), successfulResults.size(), failedResults.size());
                    
                    return new MultipleImageAnalysisResult(true, null, response);
                } else {
                    return new MultipleImageAnalysisResult(false, "이미지 분석 결과를 생성할 수 없습니다.", null);
                }
                
            } catch (Exception e) {
                logger.error("여러 이미지 분석 중 오류 발생: {}", e.getMessage(), e);
                return new MultipleImageAnalysisResult(false, 
                    "여러 이미지 분석 중 오류가 발생했습니다: " + e.getMessage(), null);
            }
        });
    }
    
    // === Private Helper Methods ===
    
    /**
     * 여러 이미지 파일 검증
     */
    private void validateMultipleImages(MultipartFile[] images) {
        if (images == null || images.length == 0) {
            throw new IllegalArgumentException("이미지 파일이 없습니다.");
        }
        
        // 이미지 개수 제한 (최대 20개)
        if (images.length > 20) {
            throw new IllegalArgumentException("한 번에 처리할 수 있는 이미지는 최대 20개입니다.");
        }
        
        // 총 파일 크기 계산 및 제한 (최대 200MB)
        long totalSize = 0;
        for (MultipartFile image : images) {
            totalSize += image.getSize();
        }
        
        if (totalSize > 200 * 1024 * 1024) { // 200MB
            throw new IllegalArgumentException("총 파일 크기가 너무 큽니다. (최대 200MB)");
        }
        
        // 각 이미지 개별 검증
        for (int i = 0; i < images.length; i++) {
            MultipartFile image = images[i];
            
            if (image.isEmpty()) {
                throw new IllegalArgumentException(String.format("이미지 %d번이 비어있습니다.", i + 1));
            }
            
            // 개별 파일 크기 제한 (50MB)
            if (image.getSize() > 50 * 1024 * 1024) {
                throw new IllegalArgumentException(
                    String.format("이미지 %d번 (%s)의 크기가 너무 큽니다. (최대 50MB)", 
                    i + 1, image.getOriginalFilename())
                );
            }
            
            // 이미지 형식 체크
            String contentType = image.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IllegalArgumentException(
                    String.format("이미지 %d번 (%s)은 지원하지 않는 파일 형식입니다.", 
                    i + 1, image.getOriginalFilename())
                );
            }
        }
        
        logger.info("이미지 배열 검증 완료 - 개수: {}, 총 크기: {:.2f}MB", 
                   images.length, totalSize / (1024.0 * 1024.0));
    }
    
    /**
     * 이미지들로부터 Book 생성
     */
    private BookDto createBookFromImages(MultipartFile[] images, String bookTitle, Long userId) {
        String finalBookTitle = bookTitle != null && !bookTitle.trim().isEmpty() 
            ? bookTitle.trim() 
            : generateBookTitleFromImages(images);
        
        CreateBookRequest bookRequest = new CreateBookRequest(
            finalBookTitle, 
            String.format("이미지 집합 분석 결과 (%d 장의 이미지)", images.length), 
            userId
        );
        
        return bookService.createBook(bookRequest);
    }
    
    /**
     * 이미지들로부터 책 제목 자동 생성
     */
    private String generateBookTitleFromImages(MultipartFile[] images) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        
        if (images.length == 1) {
            String filename = extractFileName(images[0].getOriginalFilename());
            return String.format("이미지 분석: %s (%s)", filename, timestamp);
        } else {
            return String.format("이미지 집합 분석 (%d장) - %s", images.length, timestamp);
        }
    }
    
    /**
     * 다중 이미지 처리 실행
     */
    private MultipleImageProcessingResult processMultipleImages(
            MultipartFile[] images, User user, BookDto bookDto, 
            String modelChoice, String apiKey) {
        
        String baseTimestamp = String.valueOf(System.currentTimeMillis() / 1000);
        
        // 이미지 처리 방식 결정 (병렬 vs 순차)
        boolean useParallelProcessing = shouldUseParallelProcessing(images.length);
        logger.info("이미지 처리 방식: {}", useParallelProcessing ? "병렬 처리" : "순차 처리");
        
        List<ImageProcessingResult> processingResults;
        if (useParallelProcessing) {
            // 병렬 처리 (작은 이미지 개수)
            processingResults = processImagesInParallel(
                images, user, bookDto, modelChoice, apiKey, baseTimestamp
            );
        } else {
            // 순차 처리 (많은 이미지 개수 또는 메모리 절약)
            processingResults = processImagesSequentially(
                images, user, bookDto, modelChoice, apiKey, baseTimestamp
            );
        }
        
        return new MultipleImageProcessingResult(processingResults, baseTimestamp);
    }
    
    /**
     * 병렬 처리 사용 여부 결정
     */
    private boolean shouldUseParallelProcessing(int imageCount) {
        // 이미지 개수가 적고 시스템 리소스가 충분한 경우 병렬 처리
        return imageCount <= 5 && Runtime.getRuntime().availableProcessors() >= 4;
    }
    
    /**
     * 이미지들을 병렬로 처리
     */
    private List<ImageProcessingResult> processImagesInParallel(
            MultipartFile[] images, User user, BookDto bookDto, 
            String modelChoice, String apiKey, String baseTimestamp) {
        
        logger.info("병렬 이미지 처리 시작 - 이미지 수: {}", images.length);
        
        return IntStream.range(0, images.length)
            .parallel()
            .mapToObj(i -> {
                try {
                    return processSingleImageForMultiple(
                        images[i], i + 1, user, bookDto, modelChoice, apiKey, 
                        baseTimestamp + "_img" + (i + 1)
                    );
                } catch (Exception e) {
                    logger.error("이미지 {}번 병렬 처리 실패: {}", i + 1, e.getMessage(), e);
                    return new ImageProcessingResult(false, images[i].getOriginalFilename(), 
                        "처리 실패: " + e.getMessage(), null, null);
                }
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 이미지들을 순차적으로 처리
     */
    private List<ImageProcessingResult> processImagesSequentially(
            MultipartFile[] images, User user, BookDto bookDto, 
            String modelChoice, String apiKey, String baseTimestamp) {
        
        logger.info("순차 이미지 처리 시작 - 이미지 수: {}", images.length);
        
        List<ImageProcessingResult> results = new ArrayList<>();
        
        for (int i = 0; i < images.length; i++) {
            try {
                logger.info("이미지 {}/{} 처리 중: {}", i + 1, images.length, images[i].getOriginalFilename());
                
                ImageProcessingResult result = processSingleImageForMultiple(
                    images[i], i + 1, user, bookDto, modelChoice, apiKey, 
                    baseTimestamp + "_img" + (i + 1)
                );
                
                results.add(result);
                
                logger.info("이미지 {}/{} 처리 완료: {} (성공: {})", 
                           i + 1, images.length, images[i].getOriginalFilename(), result.isSuccess());
                
            } catch (Exception e) {
                logger.error("이미지 {}/{} 처리 실패: {} - {}", 
                           i + 1, images.length, images[i].getOriginalFilename(), e.getMessage(), e);
                
                results.add(new ImageProcessingResult(false, images[i].getOriginalFilename(), 
                    "처리 실패: " + e.getMessage(), null, null));
            }
        }
        
        return results;
    }
    
    /**
     * 단일 이미지 처리 (다중 이미지 컨텍스트에서)
     */
    private ImageProcessingResult processSingleImageForMultiple(
            MultipartFile image, int imageNumber, User user, BookDto bookDto,
            String modelChoice, String apiKey, String imageTimestamp) throws Exception {
        
        // 1. 이미지 로드 및 검증
        BufferedImage bufferedImage = ImageIO.read(image.getInputStream());
        if (bufferedImage == null) {
            throw new RuntimeException("이미지를 로드할 수 없습니다: " + image.getOriginalFilename());
        }
        
        // 2. ImageAnalysisService를 사용한 분석 처리
        ImageAnalysisService.SingleImageProcessingResult result = 
            imageAnalysisService.processBufferedImage(
                bufferedImage, imageNumber, user.getId(),
                image.getOriginalFilename(),
                image.getSize(), image.getContentType(), 
                modelChoice, apiKey, imageTimestamp
            );
        
        if (result.isSuccess()) {
            // 3. Book에 파일 추가
            bookService.addFileToBook(bookDto.getId(), image, user.getId(), imageNumber);
            
            return new ImageProcessingResult(true, image.getOriginalFilename(), 
                "분석 완료", result.getAnalysisJob(), result.getDocumentPage());
        } else {
            return new ImageProcessingResult(false, image.getOriginalFilename(), 
                result.getMessage(), result.getAnalysisJob(), null);
        }
    }
    
    /**
     * 완전한 DocumentPage 데이터 로드
     */
    private List<DocumentPage> loadCompleteDocumentPages(List<ImageProcessingResult> successfulResults) {
        List<DocumentPage> completeDocumentPages = new ArrayList<>();
        
        for (ImageProcessingResult result : successfulResults) {
            if (result.getAnalysisJob() != null) {
                List<DocumentPage> jobPages = documentPageRepository
                    .findByJobIdWithLayoutBlocksAndText(result.getAnalysisJob().getJobId());
                completeDocumentPages.addAll(jobPages);
            }
        }
        
        return completeDocumentPages;
    }
    
    /**
     * 다중 이미지 분석 응답 생성
     */
    private AnalysisResponse buildMultipleImagesResponse(
            List<DocumentPage> completeDocumentPages, BookDto bookDto,
            List<ImageProcessingResult> successfulResults, List<ImageProcessingResult> failedResults,
            long startTime, String baseTimestamp) {
        
        // 다중 페이지 응답 생성
        String firstPageLayoutImagePath = !completeDocumentPages.isEmpty() 
            ? completeDocumentPages.get(0).getLayoutVisualizationPath() 
            : null;
            
        AnalysisResponse response = responseBuilder.buildMultiplePageResponse(
            completeDocumentPages, baseTimestamp, firstPageLayoutImagePath
        );
        
        // 처리 결과 요약 메시지 생성
        long processingTimeMs = System.currentTimeMillis() - startTime;
        List<String> failedImageNames = failedResults.stream()
            .map(ImageProcessingResult::getImageName)
            .collect(Collectors.toList());
            
        String resultMessage = responseBuilder.buildMultipleImageMessage(
            successfulResults.size(), failedResults.size(), 
            failedImageNames, processingTimeMs / 1000.0
        );
        
        response.setMessage(resultMessage);
        response.setJobId(bookDto.getId().toString());
        
        return response;
    }
    
    /**
     * 파일명에서 확장자 제거
     */
    private String extractFileName(String originalFilename) {
        if (originalFilename == null) {
            return "image";
        }
        
        return originalFilename.lastIndexOf('.') > 0 
            ? originalFilename.substring(0, originalFilename.lastIndexOf('.'))
            : originalFilename;
    }
    
    // === Result Classes ===
    
    /**
     * 다중 이미지 분석 결과
     */
    public static class MultipleImageAnalysisResult {
        private final boolean success;
        private final String errorMessage;
        private final AnalysisResponse response;
        
        public MultipleImageAnalysisResult(boolean success, String errorMessage, AnalysisResponse response) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.response = response;
        }
        
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public AnalysisResponse getResponse() { return response; }
    }
    
    /**
     * 다중 이미지 처리 결과
     */
    private static class MultipleImageProcessingResult {
        private final List<ImageProcessingResult> results;
        private final String baseTimestamp;
        
        public MultipleImageProcessingResult(List<ImageProcessingResult> results, String baseTimestamp) {
            this.results = results;
            this.baseTimestamp = baseTimestamp;
        }
        
        public List<ImageProcessingResult> getResults() { return results; }
        public String getBaseTimestamp() { return baseTimestamp; }
    }
    
    /**
     * 개별 이미지 처리 결과
     */
    public static class ImageProcessingResult {
        private final boolean success;
        private final String imageName;
        private final String message;
        private final AnalysisJob analysisJob;
        private final DocumentPage documentPage;
        
        public ImageProcessingResult(boolean success, String imageName, String message, 
                                   AnalysisJob analysisJob, DocumentPage documentPage) {
            this.success = success;
            this.imageName = imageName;
            this.message = message;
            this.analysisJob = analysisJob;
            this.documentPage = documentPage;
        }
        
        public boolean isSuccess() { return success; }
        public String getImageName() { return imageName; }
        public String getMessage() { return message; }
        public AnalysisJob getAnalysisJob() { return analysisJob; }
        public DocumentPage getDocumentPage() { return documentPage; }
    }
}