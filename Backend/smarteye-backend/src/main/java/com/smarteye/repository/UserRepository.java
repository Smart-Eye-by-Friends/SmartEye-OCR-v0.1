package com.smarteye.repository;

import com.smarteye.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    @Query("SELECT u FROM User u WHERE u.username = :username OR u.email = :email")
    Optional<User> findByUsernameOrEmail(@Param("username") String username, @Param("email") String email);
    
    List<User> findByIsActiveTrue();
    
    List<User> findByIsActiveFalse();
    
    @Query("SELECT u FROM User u WHERE u.createdAt >= :fromDate")
    List<User> findUsersCreatedAfter(@Param("fromDate") LocalDateTime fromDate);
    
    @Query("SELECT u FROM User u WHERE u.displayName LIKE %:name%")
    List<User> findByDisplayNameContaining(@Param("name") String name);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
    
    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true")
    long countActiveUsers();
    
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.analysisJobs aj WHERE u.id = :userId")
    Optional<User> findByIdWithAnalysisJobs(@Param("userId") Long userId);
}