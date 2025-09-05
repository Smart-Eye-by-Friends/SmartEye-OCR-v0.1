package com.smarteye.service;

import com.smarteye.dto.*;
import com.smarteye.entity.AnalysisJob;
import com.smarteye.entity.Book;
import com.smarteye.entity.User;
import com.smarteye.exception.ResourceNotFoundException;
import com.smarteye.exception.ValidationException;
import com.smarteye.repository.AnalysisJobRepository;
import com.smarteye.repository.BookRepository;
import com.smarteye.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Transactional
public class BookService {
    
    private static final Logger logger = LoggerFactory.getLogger(BookService.class);
    
    @Autowired
    private BookRepository bookRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private AnalysisJobRepository analysisJobRepository;
    
    @Autowired
    private AnalysisJobService analysisJobService;
    
    @Autowired
    private UserService userService;
    
    // === 기본 CRUD 메서드 ===
    
    /**
     * 새 책 생성
     */
    public BookDto createBook(CreateBookRequest request) {
        logger.info("새 책 생성 요청: {}", request);
        
        // 입력 검증
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new ValidationException("책 제목은 필수입니다.");
        }
        
        // 사용자 조회
        User user = null;
        if (request.getUserId() != null) {
            user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다: " + request.getUserId()));
        } else {
            // 익명 사용자 지원 (기존 시스템과의 호환성)
            user = userService.getOrCreateAnonymousUser();
        }
        
        // 중복 제목 확인
        if (bookRepository.existsByUserIdAndTitleIgnoreCase(user.getId(), request.getTitle().trim())) {
            throw new ValidationException("이미 같은 제목의 책이 존재합니다: " + request.getTitle());
        }
        
        // 새 책 생성
        Book book = new Book(request.getTitle().trim(), request.getDescription(), user);
        book = bookRepository.save(book);
        
        logger.info("새 책 생성 완료: {} (ID: {})", book.getTitle(), book.getId());
        return new BookDto(book);
    }
    
    /**
     * 책 조회 (ID)
     */
    @Transactional(readOnly = true)
    public BookDto getBookById(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("책을 찾을 수 없습니다: " + bookId));
        return new BookDto(book);
    }
    
    /**
     * 책 조회 (사용자 권한 확인)
     */
    @Transactional(readOnly = true)
    public BookDto getBookByIdAndUser(Long bookId, Long userId) {
        Book book = bookRepository.findByIdAndUserId(bookId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("책을 찾을 수 없거나 접근 권한이 없습니다: " + bookId));
        return new BookDto(book);
    }
    
    /**
     * 사용자의 모든 책 조회
     */
    @Transactional(readOnly = true)
    public List<BookDto> getUserBooks(Long userId) {
        List<Book> books = bookRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return books.stream()
                .map(book -> new BookDto(book, false)) // 분석 작업 목록은 제외
                .collect(Collectors.toList());
    }
    
    /**
     * 사용자의 책 조회 (페이지네이션)
     */
    @Transactional(readOnly = true)
    public Page<BookDto> getUserBooks(Long userId, Pageable pageable) {
        Page<Book> books = bookRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return books.map(book -> new BookDto(book, false));
    }
    
    /**
     * 책 정보 업데이트
     */
    public BookDto updateBook(Long bookId, CreateBookRequest updateRequest, Long userId) {
        Book book = bookRepository.findByIdAndUserId(bookId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("책을 찾을 수 없거나 접근 권한이 없습니다: " + bookId));
        
        // 제목 업데이트
        if (updateRequest.getTitle() != null && !updateRequest.getTitle().trim().isEmpty()) {
            String newTitle = updateRequest.getTitle().trim();
            
            // 다른 책과 제목 중복 확인
            if (!book.getTitle().equals(newTitle) && 
                bookRepository.existsByUserIdAndTitleIgnoreCase(userId, newTitle)) {
                throw new ValidationException("이미 같은 제목의 책이 존재합니다: " + newTitle);
            }
            
            book.setTitle(newTitle);
        }
        
        // 설명 업데이트
        if (updateRequest.getDescription() != null) {
            book.setDescription(updateRequest.getDescription());
        }
        
        book = bookRepository.save(book);
        
        logger.info("책 정보 업데이트 완료: {} (ID: {})", book.getTitle(), book.getId());
        return new BookDto(book);
    }
    
    /**
     * 책 삭제
     */
    public void deleteBook(Long bookId, Long userId) {
        Book book = bookRepository.findByIdAndUserId(bookId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("책을 찾을 수 없거나 접근 권한이 없습니다: " + bookId));
        
        logger.info("책 삭제 시작: {} (ID: {}, 분석 작업 수: {})", 
                   book.getTitle(), book.getId(), book.getTotalAnalysisJobs());
        
        bookRepository.delete(book);
        
        logger.info("책 삭제 완료: {} (ID: {})", book.getTitle(), book.getId());
    }
    
    // === 분석 작업 관리 메서드 ===
    
    /**
     * 책에 파일 추가 (분석 작업 생성)
     */
    public AnalysisJobDto addFileToBook(Long bookId, MultipartFile file, Long userId, Integer sequence) {
        Book book = bookRepository.findByIdAndUserId(bookId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("책을 찾을 수 없거나 접근 권한이 없습니다: " + bookId));
        
        logger.info("책에 파일 추가: {} -> {}", book.getTitle(), file.getOriginalFilename());
        
        // 분석 작업 생성
        AnalysisJob job = analysisJobService.createAnalysisJob(file, book.getUser());
        
        // 책에 분석 작업 추가
        if (sequence != null) {
            job.setSequenceInBook(sequence);
        }
        book.addAnalysisJob(job);
        
        // 저장
        analysisJobRepository.save(job);
        bookRepository.save(book);
        
        logger.info("책에 파일 추가 완료: {} -> {} (작업 ID: {})", 
                   book.getTitle(), file.getOriginalFilename(), job.getJobId());
        
        return new AnalysisJobDto(job);
    }
    
    /**
     * 책에서 분석 작업 제거
     */
    public void removeJobFromBook(Long bookId, Long jobId, Long userId) {
        Book book = bookRepository.findByIdAndUserId(bookId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("책을 찾을 수 없거나 접근 권한이 없습니다: " + bookId));
        
        AnalysisJob job = analysisJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("분석 작업을 찾을 수 없습니다: " + jobId));
        
        if (!job.getBook().getId().equals(bookId)) {
            throw new ValidationException("해당 분석 작업은 이 책에 속하지 않습니다.");
        }
        
        logger.info("책에서 분석 작업 제거: {} -> {}", book.getTitle(), job.getOriginalFilename());
        
        book.removeAnalysisJob(job);
        bookRepository.save(book);
        
        // 분석 작업 완전 삭제 또는 책에서만 제거 (비즈니스 로직에 따라 결정)
        job.setBook(null);
        job.setSequenceInBook(null);
        analysisJobRepository.save(job);
        
        logger.info("책에서 분석 작업 제거 완료");
    }
    
    /**
     * 책의 모든 파일 분석
     */
    public CompletableFuture<BookAnalysisResponse> analyzeAllFiles(Long bookId, String modelChoice, String apiKey, Long userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Book book = bookRepository.findByIdAndUserId(bookId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("책을 찾을 수 없거나 접근 권한이 없습니다: " + bookId));
                
                logger.info("책 전체 분석 시작: {} (작업 수: {})", book.getTitle(), book.getTotalAnalysisJobs());
                
                BookAnalysisResponse response = new BookAnalysisResponse(true, new BookDto(book));
                response.setModelUsed(modelChoice);
                
                // 책 상태를 처리 중으로 변경
                book.setStatus(Book.BookStatus.PROCESSING);
                bookRepository.save(book);
                
                List<AnalysisJob> jobs = book.getAnalysisJobs();
                int analyzedCount = 0;
                int failedCount = 0;
                
                // 각 분석 작업을 순차적으로 처리
                for (AnalysisJob job : jobs) {
                    try {
                        if (!job.isCompleted()) {
                            logger.info("분석 작업 처리 중: {} (순서: {})", job.getOriginalFilename(), job.getSequenceInBook());
                            
                            // 실제 분석 수행 (기존 분석 서비스 활용)
                            analysisJobService.processAnalysisJob(job, modelChoice, apiKey);
                            analyzedCount++;
                        }
                    } catch (Exception e) {
                        logger.error("분석 작업 실패: {} - {}", job.getOriginalFilename(), e.getMessage());
                        job.setStatus(AnalysisJob.JobStatus.FAILED);
                        job.setErrorMessage(e.getMessage());
                        analysisJobRepository.save(job);
                        response.addError("파일 분석 실패: " + job.getOriginalFilename() + " - " + e.getMessage());
                        failedCount++;
                    }
                }
                
                // 책 진행률 업데이트
                book.updateProgress();
                bookRepository.save(book);
                
                response.setAnalyzedJobsCount(analyzedCount);
                response.setFailedJobsCount(failedCount);
                response.markCompleted();
                response.setBook(new BookDto(book));
                
                logger.info("책 전체 분석 완료: {} (성공: {}, 실패: {})", 
                           book.getTitle(), analyzedCount, failedCount);
                
                return response;
                
            } catch (Exception e) {
                logger.error("책 전체 분석 중 오류 발생", e);
                BookAnalysisResponse errorResponse = new BookAnalysisResponse(false, null);
                errorResponse.addError("분석 중 오류 발생: " + e.getMessage());
                return errorResponse;
            }
        });
    }
    
    // === 통계 및 검색 메서드 ===
    
    /**
     * 사용자 책 통계
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUserBookStatistics(Long userId) {
        List<Object> stats = bookRepository.getUserBookStatistics(userId);
        
        Map<String, Object> result = new java.util.HashMap<>();
        if (!stats.isEmpty() && stats.get(0) instanceof java.util.Map) {
            result = (Map<String, Object>) stats.get(0);
        } else {
            // 기본값 설정
            result.put("totalBooks", 0L);
            result.put("totalJobs", 0L);
            result.put("completedJobs", 0L);
            result.put("totalPages", 0L);
            result.put("completedPages", 0L);
        }
        
        // 추가 통계
        result.put("activeBooks", bookRepository.countByUserIdAndStatus(userId, Book.BookStatus.ACTIVE));
        result.put("processingBooks", bookRepository.countByUserIdAndStatus(userId, Book.BookStatus.PROCESSING));
        
        return result;
    }
    
    /**
     * 책 검색
     */
    @Transactional(readOnly = true)
    public List<BookDto> searchBooks(Long userId, String title) {
        List<Book> books = bookRepository.findByUserIdAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(userId, title);
        return books.stream()
                .map(book -> new BookDto(book, false))
                .collect(Collectors.toList());
    }
    
    /**
     * 필터를 이용한 책 검색
     */
    @Transactional(readOnly = true)
    public Page<BookDto> searchBooksWithFilters(Long userId, String title, Book.BookStatus status, 
                                               Integer minJobs, Integer maxJobs, Pageable pageable) {
        Page<Book> books = bookRepository.findBooksWithFilters(userId, title, status, minJobs, maxJobs, pageable);
        return books.map(book -> new BookDto(book, false));
    }
    
    /**
     * 최근 생성된 책들
     */
    @Transactional(readOnly = true)
    public List<BookDto> getRecentBooks(Long userId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Book> books = bookRepository.findRecentBooksByUserId(userId, since);
        return books.stream()
                .map(book -> new BookDto(book, false))
                .collect(Collectors.toList());
    }
    
    /**
     * 완료된 책들
     */
    @Transactional(readOnly = true)
    public List<BookDto> getCompletedBooks(Long userId) {
        List<Book> books = bookRepository.findCompletedBooksByUserId(userId);
        return books.stream()
                .map(book -> new BookDto(book, false))
                .collect(Collectors.toList());
    }
    
    /**
     * 진행 중인 책들
     */
    @Transactional(readOnly = true)
    public List<BookDto> getIncompleteBooks(Long userId) {
        List<Book> books = bookRepository.findIncompleteBooksByUserId(userId);
        return books.stream()
                .map(book -> new BookDto(book, false))
                .collect(Collectors.toList());
    }
    
    // === 헬퍼 메서드 ===
    
    /**
     * 책 존재 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean bookExists(Long bookId, Long userId) {
        return bookRepository.findByIdAndUserId(bookId, userId).isPresent();
    }
    
    /**
     * 책 진행률 업데이트
     */
    public void updateBookProgress(Long bookId) {
        Optional<Book> bookOpt = bookRepository.findById(bookId);
        if (bookOpt.isPresent()) {
            Book book = bookOpt.get();
            book.updateProgress();
            bookRepository.save(book);
            
            logger.debug("책 진행률 업데이트: {} -> {}%", book.getTitle(), book.getProgressPercentage());
        }
    }
    
    /**
     * 책 상태 변경
     */
    public BookDto updateBookStatus(Long bookId, Book.BookStatus status, Long userId) {
        Book book = bookRepository.findByIdAndUserId(bookId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("책을 찾을 수 없거나 접근 권한이 없습니다: " + bookId));
        
        book.setStatus(status);
        book = bookRepository.save(book);
        
        logger.info("책 상태 변경: {} -> {}", book.getTitle(), status);
        return new BookDto(book);
    }
}