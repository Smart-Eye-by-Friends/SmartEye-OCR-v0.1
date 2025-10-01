package com.smarteye.presentation.controller;

import com.smarteye.presentation.dto.*;
import com.smarteye.domain.book.entity.Book;
import com.smarteye.application.book.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 책 관리 컨트롤러
 * 여러 파일을 하나의 논리적 '책' 단위로 관리하는 API
 */
@RestController
@RequestMapping("/api/books")
@Validated
@Tag(name = "Book Management", description = "책 관리 및 그룹화된 문서 분석 API")
public class BookController {
    
    private static final Logger logger = LoggerFactory.getLogger(BookController.class);
    
    @Autowired
    private BookService bookService;
    
    // === 기본 CRUD 작업 ===
    
    @Operation(
        summary = "새 책 생성",
        description = "여러 파일을 묶어서 관리할 새로운 책을 생성합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "책 생성 성공",
            content = @Content(schema = @Schema(implementation = BookDto.class))
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청 (제목 중복, 입력값 오류 등)"),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @PostMapping
    public ResponseEntity<BookDto> createBook(
            @Parameter(description = "책 생성 정보", required = true)
            @Valid @RequestBody CreateBookRequest request) {
        
        logger.info("새 책 생성 요청: {}", request);
        
        BookDto book = bookService.createBook(request);
        
        logger.info("새 책 생성 완료: {} (ID: {})", book.getTitle(), book.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(book);
    }
    
    @Operation(
        summary = "책 정보 조회",
        description = "ID를 통해 특정 책의 상세 정보를 조회합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = BookDto.class))
        ),
        @ApiResponse(responseCode = "404", description = "책을 찾을 수 없음")
    })
    @GetMapping("/{bookId}")
    public ResponseEntity<BookDto> getBook(
            @Parameter(description = "책 ID", example = "1")
            @PathVariable Long bookId,
            
            @Parameter(description = "사용자 ID (권한 확인용)", example = "1")
            @RequestParam(required = false) Long userId) {
        
        BookDto book;
        if (userId != null) {
            book = bookService.getBookByIdAndUser(bookId, userId);
        } else {
            book = bookService.getBookById(bookId);
        }
        
        return ResponseEntity.ok(book);
    }
    
    @Operation(
        summary = "책 정보 수정",
        description = "책의 제목이나 설명을 수정합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "수정 성공",
            content = @Content(schema = @Schema(implementation = BookDto.class))
        ),
        @ApiResponse(responseCode = "404", description = "책을 찾을 수 없음"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    @PutMapping("/{bookId}")
    public ResponseEntity<BookDto> updateBook(
            @Parameter(description = "책 ID", example = "1")
            @PathVariable Long bookId,
            
            @Parameter(description = "수정할 책 정보", required = true)
            @Valid @RequestBody CreateBookRequest updateRequest,
            
            @Parameter(description = "사용자 ID", example = "1")
            @RequestParam Long userId) {
        
        logger.info("책 정보 수정 요청: ID={}, 요청={}", bookId, updateRequest);
        
        BookDto updatedBook = bookService.updateBook(bookId, updateRequest, userId);
        
        logger.info("책 정보 수정 완료: {}", updatedBook.getTitle());
        return ResponseEntity.ok(updatedBook);
    }
    
    @Operation(
        summary = "책 삭제",
        description = "책과 포함된 모든 분석 작업을 삭제합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "책을 찾을 수 없음")
    })
    @DeleteMapping("/{bookId}")
    public ResponseEntity<Void> deleteBook(
            @Parameter(description = "책 ID", example = "1")
            @PathVariable Long bookId,
            
            @Parameter(description = "사용자 ID", example = "1")
            @RequestParam Long userId) {
        
        logger.info("책 삭제 요청: ID={}, 사용자={}", bookId, userId);
        
        bookService.deleteBook(bookId, userId);
        
        logger.info("책 삭제 완료: ID={}", bookId);
        return ResponseEntity.noContent().build();
    }
    
    // === 사용자별 책 조회 ===
    
    @Operation(
        summary = "사용자의 모든 책 조회",
        description = "특정 사용자가 생성한 모든 책을 조회합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = BookDto.class))
        )
    })
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BookDto>> getUserBooks(
            @Parameter(description = "사용자 ID", example = "1")
            @PathVariable Long userId) {
        
        List<BookDto> books = bookService.getUserBooks(userId);
        
        logger.info("사용자 책 조회 완료: 사용자={}, 책 수={}", userId, books.size());
        return ResponseEntity.ok(books);
    }
    
    @Operation(
        summary = "사용자의 책 조회 (페이지네이션)",
        description = "특정 사용자의 책을 페이지 단위로 조회합니다."
    )
    @GetMapping("/user/{userId}/paged")
    public ResponseEntity<Page<BookDto>> getUserBooksPageable(
            @Parameter(description = "사용자 ID", example = "1")
            @PathVariable Long userId,
            
            Pageable pageable) {
        
        Page<BookDto> books = bookService.getUserBooks(userId, pageable);
        
        logger.info("사용자 책 페이지 조회 완료: 사용자={}, 페이지={}/{}", 
                   userId, books.getNumber() + 1, books.getTotalPages());
        return ResponseEntity.ok(books);
    }
    
    // === 파일 관리 ===
    
    @Operation(
        summary = "책에 파일 추가",
        description = "기존 책에 새로운 파일을 추가합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "파일 추가 성공",
            content = @Content(schema = @Schema(implementation = AnalysisJobDto.class))
        ),
        @ApiResponse(responseCode = "404", description = "책을 찾을 수 없음"),
        @ApiResponse(responseCode = "400", description = "파일 형식 오류")
    })
    @PostMapping(value = "/{bookId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnalysisJobDto> addFileToBook(
            @Parameter(description = "책 ID", example = "1")
            @PathVariable Long bookId,
            
            @Parameter(description = "추가할 파일", required = true)
            @RequestParam("file") MultipartFile file,
            
            @Parameter(description = "사용자 ID", example = "1")
            @RequestParam Long userId,
            
            @Parameter(description = "책 내 순서 (선택사항)", example = "1")
            @RequestParam(required = false) Integer sequence) {
        
        logger.info("책에 파일 추가 요청: bookId={}, fileName={}, sequence={}", 
                   bookId, file.getOriginalFilename(), sequence);
        
        AnalysisJobDto job = bookService.addFileToBook(bookId, file, userId, sequence);
        
        logger.info("책에 파일 추가 완료: jobId={}", job.getJobId());
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }
    
    @Operation(
        summary = "책에서 파일 제거",
        description = "책에서 특정 분석 작업(파일)을 제거합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "제거 성공"),
        @ApiResponse(responseCode = "404", description = "책 또는 분석 작업을 찾을 수 없음")
    })
    @DeleteMapping("/{bookId}/files/{jobId}")
    public ResponseEntity<Void> removeFileFromBook(
            @Parameter(description = "책 ID", example = "1")
            @PathVariable Long bookId,
            
            @Parameter(description = "분석 작업 ID", example = "1")
            @PathVariable Long jobId,
            
            @Parameter(description = "사용자 ID", example = "1")
            @RequestParam Long userId) {
        
        logger.info("책에서 파일 제거 요청: bookId={}, jobId={}", bookId, jobId);
        
        bookService.removeJobFromBook(bookId, jobId, userId);
        
        logger.info("책에서 파일 제거 완료");
        return ResponseEntity.noContent().build();
    }
    
    // === 배치 분석 ===
    
    @Operation(
        summary = "책의 모든 파일 분석",
        description = "책에 포함된 모든 파일을 배치로 분석합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202",
            description = "분석 시작됨",
            content = @Content(schema = @Schema(implementation = BookAnalysisResponse.class))
        ),
        @ApiResponse(responseCode = "404", description = "책을 찾을 수 없음")
    })
    @PostMapping("/{bookId}/analyze-all")
    public CompletableFuture<ResponseEntity<BookAnalysisResponse>> analyzeAllFiles(
            @Parameter(description = "책 ID", example = "1")
            @PathVariable Long bookId,
            
            @Parameter(description = "사용자 ID", example = "1")
            @RequestParam Long userId,
            
            @Parameter(description = "분석 모델 선택", example = "SmartEyeSsen")
            @RequestParam(value = "modelChoice", defaultValue = "SmartEyeSsen") String modelChoice,
            
            @Parameter(description = "OpenAI API 키 (AI 설명 생성용, 선택사항)")
            @RequestParam(value = "apiKey", required = false) String apiKey) {
        
        logger.info("책 전체 분석 시작: bookId={}, model={}", bookId, modelChoice);
        
        return bookService.analyzeAllFiles(bookId, modelChoice, apiKey, userId)
                .thenApply(response -> {
                    if (response.isSuccess()) {
                        logger.info("책 전체 분석 완료: bookId={}", bookId);
                        return ResponseEntity.accepted().body(response);
                    } else {
                        logger.error("책 전체 분석 실패: bookId={}", bookId);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                    }
                });
    }
    
    // === 검색 및 필터링 ===
    
    @Operation(
        summary = "책 검색",
        description = "제목으로 사용자의 책을 검색합니다."
    )
    @GetMapping("/search")
    public ResponseEntity<List<BookDto>> searchBooks(
            @Parameter(description = "사용자 ID", example = "1")
            @RequestParam Long userId,
            
            @Parameter(description = "검색할 제목", example = "수학")
            @RequestParam String title) {
        
        List<BookDto> books = bookService.searchBooks(userId, title);
        
        logger.info("책 검색 완료: 사용자={}, 검색어={}, 결과 수={}", userId, title, books.size());
        return ResponseEntity.ok(books);
    }
    
    @Operation(
        summary = "고급 책 검색",
        description = "다양한 필터를 사용하여 책을 검색합니다."
    )
    @GetMapping("/search/advanced")
    public ResponseEntity<Page<BookDto>> searchBooksWithFilters(
            @Parameter(description = "사용자 ID", example = "1")
            @RequestParam Long userId,
            
            @Parameter(description = "제목 검색어")
            @RequestParam(required = false) String title,
            
            @Parameter(description = "책 상태")
            @RequestParam(required = false) Book.BookStatus status,
            
            @Parameter(description = "최소 분석 작업 수")
            @RequestParam(required = false) Integer minJobs,
            
            @Parameter(description = "최대 분석 작업 수")
            @RequestParam(required = false) Integer maxJobs,
            
            Pageable pageable) {
        
        Page<BookDto> books = bookService.searchBooksWithFilters(userId, title, status, minJobs, maxJobs, pageable);
        
        logger.info("고급 책 검색 완료: 결과 수={}", books.getTotalElements());
        return ResponseEntity.ok(books);
    }
    
    // === 통계 및 요약 ===
    
    @Operation(
        summary = "사용자 책 통계",
        description = "사용자의 전체 책 관련 통계를 조회합니다."
    )
    @GetMapping("/user/{userId}/statistics")
    public ResponseEntity<Map<String, Object>> getUserBookStatistics(
            @Parameter(description = "사용자 ID", example = "1")
            @PathVariable Long userId) {
        
        Map<String, Object> statistics = bookService.getUserBookStatistics(userId);
        
        logger.info("사용자 책 통계 조회 완료: 사용자={}", userId);
        return ResponseEntity.ok(statistics);
    }
    
    @Operation(
        summary = "최근 생성된 책 조회",
        description = "지정된 일수 내에 생성된 책들을 조회합니다."
    )
    @GetMapping("/user/{userId}/recent")
    public ResponseEntity<List<BookDto>> getRecentBooks(
            @Parameter(description = "사용자 ID", example = "1")
            @PathVariable Long userId,
            
            @Parameter(description = "조회할 일수", example = "7")
            @RequestParam(value = "days", defaultValue = "7") int days) {
        
        List<BookDto> books = bookService.getRecentBooks(userId, days);
        
        logger.info("최근 책 조회 완료: 사용자={}, 일수={}, 결과 수={}", userId, days, books.size());
        return ResponseEntity.ok(books);
    }
    
    @Operation(
        summary = "완료된 책 조회",
        description = "모든 분석이 완료된 책들을 조회합니다."
    )
    @GetMapping("/user/{userId}/completed")
    public ResponseEntity<List<BookDto>> getCompletedBooks(
            @Parameter(description = "사용자 ID", example = "1")
            @PathVariable Long userId) {
        
        List<BookDto> books = bookService.getCompletedBooks(userId);
        
        logger.info("완료된 책 조회 완료: 사용자={}, 결과 수={}", userId, books.size());
        return ResponseEntity.ok(books);
    }
    
    @Operation(
        summary = "진행 중인 책 조회",
        description = "아직 분석이 완료되지 않은 책들을 조회합니다."
    )
    @GetMapping("/user/{userId}/incomplete")
    public ResponseEntity<List<BookDto>> getIncompleteBooks(
            @Parameter(description = "사용자 ID", example = "1")
            @PathVariable Long userId) {
        
        List<BookDto> books = bookService.getIncompleteBooks(userId);
        
        logger.info("진행 중인 책 조회 완료: 사용자={}, 결과 수={}", userId, books.size());
        return ResponseEntity.ok(books);
    }
    
    // === 상태 관리 ===
    
    @Operation(
        summary = "책 상태 변경",
        description = "책의 상태를 변경합니다 (ACTIVE, PROCESSING, ARCHIVED, DELETED)."
    )
    @PutMapping("/{bookId}/status")
    public ResponseEntity<BookDto> updateBookStatus(
            @Parameter(description = "책 ID", example = "1")
            @PathVariable Long bookId,
            
            @Parameter(description = "변경할 상태", example = "ARCHIVED")
            @RequestParam Book.BookStatus status,
            
            @Parameter(description = "사용자 ID", example = "1")
            @RequestParam Long userId) {
        
        logger.info("책 상태 변경 요청: bookId={}, status={}", bookId, status);
        
        BookDto updatedBook = bookService.updateBookStatus(bookId, status, userId);
        
        logger.info("책 상태 변경 완료: {}", updatedBook.getTitle());
        return ResponseEntity.ok(updatedBook);
    }
}