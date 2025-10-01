package com.smarteye.service;

import com.smarteye.domain.user.User;
import com.smarteye.infrastructure.persistence.UserRepository;
import com.smarteye.exception.DocumentAnalysisException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setActive(true);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void getOrCreateUser_WhenUserExists_ShouldReturnExistingUser() {
        // Given
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // When
        User result = userService.getOrCreateUser("testuser", "test@example.com");

        // Then
        assertThat(result).isEqualTo(testUser);
        verify(userRepository).findByUsername("testuser");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void getOrCreateUser_WhenUserDoesNotExist_ShouldCreateNewUser() {
        // Given
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        User result = userService.getOrCreateUser("newuser", "new@example.com");

        // Then
        assertThat(result).isEqualTo(testUser);
        verify(userRepository).findByUsername("newuser");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createAnonymousUser_ShouldCreateUserWithAnonymousName() {
        // Given
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        User result = userService.createAnonymousUser();

        // Then
        assertThat(result).isEqualTo(testUser);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void getUserById_WhenUserExists_ShouldReturnUser() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        Optional<User> result = userService.getUserById(1L);

        // Then
        assertThat(result).isPresent().contains(testUser);
    }

    @Test
    void getUserById_WhenUserDoesNotExist_ShouldReturnEmpty() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<User> result = userService.getUserById(999L);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void updateUser_WhenUserExists_ShouldUpdateAndReturnUser() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.findByUsername("newusername")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        User result = userService.updateUser(1L, "newusername", "newemail@example.com");

        // Then
        assertThat(result).isEqualTo(testUser);
        verify(userRepository).save(testUser);
    }

    @Test
    void updateUser_WhenUserDoesNotExist_ShouldThrowException() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.updateUser(999L, "newname", "newemail@example.com"))
            .isInstanceOf(DocumentAnalysisException.class)
            .hasMessageContaining("사용자를 찾을 수 없습니다: 999");
    }

    @Test
    void updateUser_WhenUsernameAlreadyExists_ShouldThrowException() {
        // Given
        User anotherUser = new User();
        anotherUser.setId(2L);
        anotherUser.setUsername("existinguser");
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.findByUsername("existinguser")).thenReturn(Optional.of(anotherUser));

        // When & Then
        assertThatThrownBy(() -> userService.updateUser(1L, "existinguser", "newemail@example.com"))
            .isInstanceOf(DocumentAnalysisException.class)
            .hasMessageContaining("이미 존재하는 사용자명입니다: existinguser");
    }

    @Test
    void deactivateUser_WhenUserExists_ShouldSetActiveToFalse() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        userService.deactivateUser(1L);

        // Then
        assertThat(testUser.isActive()).isFalse();
        verify(userRepository).save(testUser);
    }

    @Test
    void deactivateUser_WhenUserDoesNotExist_ShouldThrowException() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.deactivateUser(999L))
            .isInstanceOf(DocumentAnalysisException.class)
            .hasMessageContaining("사용자를 찾을 수 없습니다: 999");
    }

    @Test
    void getAllActiveUsers_ShouldReturnOnlyActiveUsers() {
        // Given
        List<User> activeUsers = List.of(testUser);
        when(userRepository.findByIsActiveTrue()).thenReturn(activeUsers);

        // When
        List<User> result = userService.getAllActiveUsers();

        // Then
        assertThat(result).hasSize(1).contains(testUser);
    }
}