package com.smarteye.service;

import com.smarteye.entity.User;
import com.smarteye.repository.UserRepository;
import com.smarteye.exception.DocumentAnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 사용자 관리 서비스
 */
@Service
@Transactional
public class UserService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * 사용자 생성 또는 조회 (간단한 인증 없는 버전)
     */
    public User getOrCreateUser(String username, String email) {
        logger.info("사용자 조회/생성 - username: {}, email: {}", username, email);
        
        // 이미 존재하는 사용자 확인
        Optional<User> existingUser = userRepository.findByUsername(username);
        if (existingUser.isPresent()) {
            logger.debug("기존 사용자 발견: {}", existingUser.get().getId());
            return existingUser.get();
        }
        
        // 새 사용자 생성
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setEmail(email);
        newUser.setActive(true);
        
        User savedUser = userRepository.save(newUser);
        logger.info("새 사용자 생성 완료 - ID: {}, username: {}", savedUser.getId(), savedUser.getUsername());
        
        return savedUser;
    }
    
    /**
     * 기본 사용자 생성 (익명 사용자용)
     */
    public User createAnonymousUser() {
        String anonymousUsername = "anonymous_" + System.currentTimeMillis();
        return getOrCreateUser(anonymousUsername, null);
    }
    
    /**
     * 사용자 ID로 조회
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserById(Long userId) {
        return userRepository.findById(userId);
    }
    
    /**
     * 사용자명으로 조회
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    /**
     * 이메일로 조회
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    /**
     * 모든 활성 사용자 조회
     */
    @Transactional(readOnly = true)
    public List<User> getAllActiveUsers() {
        return userRepository.findByIsActiveTrue();
    }
    
    /**
     * 사용자 정보 업데이트
     */
    public User updateUser(Long userId, String username, String email) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new DocumentAnalysisException("사용자를 찾을 수 없습니다: " + userId));
        
        if (username != null && !username.trim().isEmpty()) {
            // 중복 사용자명 확인
            Optional<User> existingUser = userRepository.findByUsername(username);
            if (existingUser.isPresent() && !existingUser.get().getId().equals(userId)) {
                throw new DocumentAnalysisException("이미 존재하는 사용자명입니다: " + username);
            }
            user.setUsername(username);
        }
        
        if (email != null && !email.trim().isEmpty()) {
            user.setEmail(email);
        }
        
        User updatedUser = userRepository.save(user);
        logger.info("사용자 정보 업데이트 완료 - ID: {}", userId);
        
        return updatedUser;
    }
    
    /**
     * 사용자 비활성화
     */
    public void deactivateUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new DocumentAnalysisException("사용자를 찾을 수 없습니다: " + userId));
        
        user.setActive(false);
        userRepository.save(user);
        
        logger.info("사용자 비활성화 완료 - ID: {}", userId);
    }
    
    /**
     * 사용자 삭제 (실제로는 비활성화)
     */
    public void deleteUser(Long userId) {
        deactivateUser(userId);
    }
}