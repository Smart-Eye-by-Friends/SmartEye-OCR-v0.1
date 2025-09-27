package com.smarteye.domain.book.repository;

import com.smarteye.domain.book.entity.Book;
import com.smarteye.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
    
    // 사용자별 책 조회
    List<Book> findByUserOrderByCreatedAtDesc(User user);
    
    Page<Book> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    
    // 사용자ID로 책 조회
    List<Book> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    Page<Book> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    // 상태별 조회
    List<Book> findByUserAndStatus(User user, Book.BookStatus status);
    
    List<Book> findByUserIdAndStatus(Long userId, Book.BookStatus status);
    
    // 제목으로 검색
    List<Book> findByUserAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(User user, String title);
    
    List<Book> findByUserIdAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(Long userId, String title);
    
    // 사용자와 ID로 책 조회 (권한 확인용)
    Optional<Book> findByIdAndUser(Long id, User user);
    
    Optional<Book> findByIdAndUserId(Long id, Long userId);
    
    // 통계 쿼리
    @Query("SELECT COUNT(b) FROM Book b WHERE b.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);
    
    @Query("SELECT COUNT(b) FROM Book b WHERE b.user.id = :userId AND b.status = :status")
    long countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") Book.BookStatus status);
    
    // 최근 생성된 책들
    @Query("SELECT b FROM Book b WHERE b.user.id = :userId AND b.createdAt >= :since ORDER BY b.createdAt DESC")
    List<Book> findRecentBooksByUserId(@Param("userId") Long userId, @Param("since") LocalDateTime since);
    
    // 완료된/진행 중인 책들
    @Query("SELECT b FROM Book b WHERE b.user.id = :userId AND b.completedAnalysisJobs = b.totalAnalysisJobs AND b.totalAnalysisJobs > 0")
    List<Book> findCompletedBooksByUserId(@Param("userId") Long userId);
    
    @Query("SELECT b FROM Book b WHERE b.user.id = :userId AND b.completedAnalysisJobs < b.totalAnalysisJobs")
    List<Book> findIncompleteBooksByUserId(@Param("userId") Long userId);
    
    // 진행률 기반 조회
    @Query("SELECT b FROM Book b WHERE b.user.id = :userId AND " +
           "CASE WHEN b.totalAnalysisJobs > 0 THEN (CAST(b.completedAnalysisJobs AS double) / b.totalAnalysisJobs * 100) >= :minProgress " +
           "ELSE 0 END = true")
    List<Book> findBooksByUserIdWithMinProgress(@Param("userId") Long userId, @Param("minProgress") double minProgress);
    
    // 분석 작업 수별 조회
    List<Book> findByUserIdAndTotalAnalysisJobsGreaterThanEqual(Long userId, Integer minJobs);
    
    List<Book> findByUserIdAndTotalAnalysisJobsBetween(Long userId, Integer minJobs, Integer maxJobs);
    
    // 페이지 수별 조회
    List<Book> findByUserIdAndTotalPagesGreaterThanEqual(Long userId, Integer minPages);
    
    List<Book> findByUserIdAndTotalPagesBetween(Long userId, Integer minPages, Integer maxPages);
    
    // 날짜 범위 조회
    @Query("SELECT b FROM Book b WHERE b.user.id = :userId AND b.createdAt BETWEEN :startDate AND :endDate ORDER BY b.createdAt DESC")
    List<Book> findBooksByUserIdAndDateRange(@Param("userId") Long userId, 
                                           @Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);
    
    // 복합 검색
    @Query("SELECT b FROM Book b WHERE b.user.id = :userId AND " +
           "(:title IS NULL OR LOWER(b.title) LIKE LOWER(CONCAT('%', :title, '%'))) AND " +
           "(:status IS NULL OR b.status = :status) AND " +
           "(:minJobs IS NULL OR b.totalAnalysisJobs >= :minJobs) AND " +
           "(:maxJobs IS NULL OR b.totalAnalysisJobs <= :maxJobs) " +
           "ORDER BY b.createdAt DESC")
    Page<Book> findBooksWithFilters(@Param("userId") Long userId,
                                   @Param("title") String title,
                                   @Param("status") Book.BookStatus status,
                                   @Param("minJobs") Integer minJobs,
                                   @Param("maxJobs") Integer maxJobs,
                                   Pageable pageable);
    
    // 사용자별 요약 통계
    @Query("SELECT new map(" +
           "COUNT(b) as totalBooks, " +
           "SUM(b.totalAnalysisJobs) as totalJobs, " +
           "SUM(b.completedAnalysisJobs) as completedJobs, " +
           "SUM(b.totalPages) as totalPages, " +
           "SUM(b.completedPages) as completedPages" +
           ") FROM Book b WHERE b.user.id = :userId")
    List<Object> getUserBookStatistics(@Param("userId") Long userId);
    
    // 아카이브되지 않은 활성 책들만
    @Query("SELECT b FROM Book b WHERE b.user.id = :userId AND b.status != 'ARCHIVED' AND b.status != 'DELETED' ORDER BY b.updatedAt DESC")
    List<Book> findActiveBooksByUserId(@Param("userId") Long userId);
    
    // 최근 업데이트된 책들
    @Query("SELECT b FROM Book b WHERE b.user.id = :userId AND b.updatedAt >= :since ORDER BY b.updatedAt DESC")
    List<Book> findRecentlyUpdatedBooksByUserId(@Param("userId") Long userId, @Param("since") LocalDateTime since);
    
    // 특정 기간 내 생성된 책의 개수
    @Query("SELECT COUNT(b) FROM Book b WHERE b.user.id = :userId AND b.createdAt >= :since")
    long countBooksCreatedSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);
    
    // 중복 제목 확인
    boolean existsByUserIdAndTitleIgnoreCase(Long userId, String title);
    
    // 특정 상태의 책이 존재하는지 확인
    boolean existsByUserIdAndStatus(Long userId, Book.BookStatus status);
}