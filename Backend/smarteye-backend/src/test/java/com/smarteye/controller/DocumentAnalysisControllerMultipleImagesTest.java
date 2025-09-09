package com.smarteye.controller;

import com.smarteye.service.*;
import com.smarteye.dto.*;
import com.smarteye.entity.*;
import com.smarteye.repository.DocumentPageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 여러 이미지 업로드 기능 테스트
 */
@ExtendWith(MockitoExtension.class)
public class DocumentAnalysisControllerMultipleImagesTest {

    @InjectMocks
    private DocumentAnalysisController documentAnalysisController;

    @Mock
    private LAMServiceClient lamServiceClient;
    @Mock
    private OCRService ocrService;
    @Mock
    private AIDescriptionService aiDescriptionService;
    @Mock
    private ImageProcessingService imageProcessingService;
    @Mock
    private PDFService pdfService;
    @Mock
    private FileService fileService;
    @Mock
    private AnalysisJobService analysisJobService;
    @Mock
    private DocumentAnalysisDataService documentAnalysisDataService;
    @Mock
    private DocumentPageRepository documentPageRepository;
    @Mock
    private BookService bookService;
    @Mock
    private UserService userService;
    @Mock
    private ObjectMapper objectMapper;

    private MockMultipartFile[] testImages;
    private User testUser;
    private BookDto testBook;

    @BeforeEach
    void setUp() {
        // 테스트용 이미지 파일들 생성
        testImages = new MockMultipartFile[3];
        testImages[0] = new MockMultipartFile("images", "test1.jpg", "image/jpeg", createTestImageData());
        testImages[1] = new MockMultipartFile("images", "test2.png", "image/png", createTestImageData());
        testImages[2] = new MockMultipartFile("images", "test3.jpg", "image/jpeg", createTestImageData());

        // 테스트용 사용자 및 책 설정
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        testBook = new BookDto();
        testBook.setId(1L);
        testBook.setTitle("테스트 이미지 집합");
    }

    private byte[] createTestImageData() {
        // 간단한 테스트용 이미지 데이터 (실제로는 유효한 이미지 데이터여야 함)
        return "test image data".getBytes();
    }

    @Test
    void testAnalyzeMultipleImages_Success() throws Exception {
        // Given
        when(userService.getOrCreateAnonymousUser()).thenReturn(testUser);
        when(bookService.createBook(any(CreateBookRequest.class))).thenReturn(testBook);
        
        AnalysisJob testJob = new AnalysisJob();
        testJob.setJobId("test-job-id");
        when(analysisJobService.createAnalysisJob(anyLong(), anyString(), anyString(), anyLong(), anyString(), anyString()))
            .thenReturn(testJob);

        LayoutAnalysisResult layoutResult = new LayoutAnalysisResult();
        layoutResult.setLayoutInfo(List.of(createTestLayoutInfo()));
        when(lamServiceClient.analyzeLayout(any(BufferedImage.class), anyString()))
            .thenReturn(CompletableFuture.completedFuture(layoutResult));

        when(ocrService.performOCR(any(BufferedImage.class), anyList()))
            .thenReturn(List.of(createTestOCRResult()));

        DocumentPage testPage = new DocumentPage();
        testPage.setId(1L);
        when(documentAnalysisDataService.savePageAnalysisResult(any(), anyInt(), anyString(), anyList(), anyList(), anyList(), anyString(), anyString(), anyLong()))
            .thenReturn(testPage);

        when(documentPageRepository.findByJobIdWithLayoutBlocksAndText(anyString()))
            .thenReturn(List.of(testPage));

        // When
        CompletableFuture<ResponseEntity<AnalysisResponse>> result = 
            documentAnalysisController.analyzeMultipleImages(testImages, "SmartEyeSsen", null, "테스트 책");

        // Then
        assertNotNull(result);
        ResponseEntity<AnalysisResponse> response = result.get();
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        
        AnalysisResponse analysisResponse = response.getBody();
        assertNotNull(analysisResponse);
        assertTrue(analysisResponse.isSuccess());
        assertEquals("1", analysisResponse.getJobId());

        // 서비스 호출 검증
        verify(userService).getOrCreateAnonymousUser();
        verify(bookService).createBook(any(CreateBookRequest.class));
        verify(analysisJobService, times(3)).createAnalysisJob(anyLong(), anyString(), anyString(), anyLong(), anyString(), anyString());
        verify(lamServiceClient, times(3)).analyzeLayout(any(BufferedImage.class), anyString());
        verify(ocrService, times(3)).performOCR(any(BufferedImage.class), anyList());
    }

    @Test
    void testValidateMultipleImages_TooManyImages() {
        // Given - 21개의 이미지 (제한: 20개)
        MockMultipartFile[] tooManyImages = new MockMultipartFile[21];
        for (int i = 0; i < 21; i++) {
            tooManyImages[i] = new MockMultipartFile("images", "test" + i + ".jpg", "image/jpeg", createTestImageData());
        }

        // When & Then
        CompletableFuture<ResponseEntity<AnalysisResponse>> result = 
            documentAnalysisController.analyzeMultipleImages(tooManyImages, "SmartEyeSsen", null, null);

        assertThrows(Exception.class, () -> {
            ResponseEntity<AnalysisResponse> response = result.get();
            assertEquals(400, response.getStatusCodeValue());
        });
    }

    @Test
    void testValidateMultipleImages_EmptyArray() {
        // Given
        MockMultipartFile[] emptyImages = new MockMultipartFile[0];

        // When & Then
        CompletableFuture<ResponseEntity<AnalysisResponse>> result = 
            documentAnalysisController.analyzeMultipleImages(emptyImages, "SmartEyeSsen", null, null);

        assertThrows(Exception.class, () -> {
            result.get();
        });
    }

    @Test
    void testValidateMultipleImages_InvalidFileType() {
        // Given - 텍스트 파일
        MockMultipartFile[] invalidImages = new MockMultipartFile[1];
        invalidImages[0] = new MockMultipartFile("images", "test.txt", "text/plain", "not an image".getBytes());

        // When & Then
        CompletableFuture<ResponseEntity<AnalysisResponse>> result = 
            documentAnalysisController.analyzeMultipleImages(invalidImages, "SmartEyeSsen", null, null);

        assertThrows(Exception.class, () -> {
            result.get();
        });
    }

    @Test
    void testBookTitleGeneration() {
        // 이 테스트는 private 메서드를 테스트하기 위해 실제 API 호출을 통해 검증
        // Given
        when(userService.getOrCreateAnonymousUser()).thenReturn(testUser);
        when(bookService.createBook(any(CreateBookRequest.class))).thenReturn(testBook);

        // When
        CompletableFuture<ResponseEntity<AnalysisResponse>> result = 
            documentAnalysisController.analyzeMultipleImages(testImages, "SmartEyeSsen", null, null);

        // Then
        verify(bookService).createBook(argThat(request -> 
            request.getTitle().contains("이미지 집합 분석") && 
            request.getTitle().contains("3장")
        ));
    }

    private com.smarteye.dto.common.LayoutInfo createTestLayoutInfo() {
        return new com.smarteye.dto.common.LayoutInfo(
            1, "test_class", 0.9, new int[]{10, 20, 100, 120}, 90, 100, 9000
        );
    }

    private OCRResult createTestOCRResult() {
        return new OCRResult(1, "test_class", new int[]{10, 20, 100, 120}, "테스트 텍스트");
    }
}