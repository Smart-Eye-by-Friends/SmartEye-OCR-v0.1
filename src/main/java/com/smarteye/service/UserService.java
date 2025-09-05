package com.smarteye.service;

import com.smarteye.model.entity.User;
import com.smarteye.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {
    
    private final UserRepository userRepository;
    
    public User createUser(String username, String email) {
        // 중복 확인
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        
        if (email != null && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }
        
        User user = User.builder()
                .username(username)
                .email(email)
                .build();
        
        User savedUser = userRepository.save(user);
        log.info("Created user: {}", savedUser.getUsername());
        return savedUser;
    }
    
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    @Transactional(readOnly = true)
    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }
    
    @Transactional(readOnly = true)
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }
    
    public User updateUser(Long userId, String email) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        
        if (email != null) {
            user.setEmail(email);
        }
        
        User updatedUser = userRepository.save(user);
        log.info("Updated user: {}", updatedUser.getUsername());
        return updatedUser;
    }
    
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        
        userRepository.delete(user);
        log.info("Deleted user: {}", user.getUsername());
    }
    
    /**
     * 기본 사용자 생성 (테스트/개발용)
     */
    public User getOrCreateDefaultUser() {
        return findByUsername("default")
                .orElseGet(() -> createUser("default", "default@smarteye.com"));
    }
}
