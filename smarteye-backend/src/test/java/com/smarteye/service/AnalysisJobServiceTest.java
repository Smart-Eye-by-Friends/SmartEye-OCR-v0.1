package com.smarteye.service;

import com.smarteye.entity.AnalysisJob;
import com.smarteye.entity.User;
import com.smarteye.repository.AnalysisJobRepository;
import com.smarteye.repository.UserRepository;
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
class AnalysisJobServiceTest {

    @Mock
    private AnalysisJobRepository analysisJobRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AnalysisJobService analysisJobService;

    private User testUser;
    private AnalysisJob testJob;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setActive(true);

        testJob = new AnalysisJob();
        testJob.setId(1L);
        testJob.setJobId("test-job-123");
        testJob.setUser(testUser);
        testJob.setOriginalFilename("test.png");
        testJob.setFilePath("/uploads/test.png");
        testJob.setFileSize(12345L);
        testJob.setFileType("image/png");
        testJob.setModelChoice("SmartEyeSsen");
        testJob.setStatus(AnalysisJob.JobStatus.PENDING);
        testJob.setProgressPercentage(0);
        testJob.setCreatedAt(LocalDateTime.now());
        testJob.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void createAnalysisJob_WhenUserExists_ShouldCreateJob() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(analysisJobRepository.save(any(AnalysisJob.class))).thenReturn(testJob);

        // When
        AnalysisJob result = analysisJobService.createAnalysisJob(
            1L, "test.png", "/uploads/test.png", 12345L, "image/png", "SmartEyeSsen"
        );

        // Then
        assertThat(result).isEqualTo(testJob);
        verify(userRepository).findById(1L);
        verify(analysisJobRepository).save(any(AnalysisJob.class));
    }

    @Test
    void createAnalysisJob_WhenUserDoesNotExist_ShouldThrowException() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> analysisJobService.createAnalysisJob(
            999L, "test.png", "/uploads/test.png", 12345L, "image/png", "SmartEyeSsen"
        )).isInstanceOf(DocumentAnalysisException.class)
          .hasMessageContaining("사용자를 찾을 수 없습니다: 999");
    }

    @Test
    void getAnalysisJobByJobId_WhenJobExists_ShouldReturnJob() {
        // Given
        when(analysisJobRepository.findByJobId("test-job-123")).thenReturn(Optional.of(testJob));

        // When
        Optional<AnalysisJob> result = analysisJobService.getAnalysisJobByJobId("test-job-123");

        // Then
        assertThat(result).isPresent().contains(testJob);
    }

    @Test
    void getAnalysisJobByJobId_WhenJobDoesNotExist_ShouldReturnEmpty() {
        // Given
        when(analysisJobRepository.findByJobId("non-existent")).thenReturn(Optional.empty());

        // When
        Optional<AnalysisJob> result = analysisJobService.getAnalysisJobByJobId("non-existent");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void updateJobStatus_WhenJobExists_ShouldUpdateStatus() {
        // Given
        when(analysisJobRepository.findByJobId("test-job-123")).thenReturn(Optional.of(testJob));
        when(analysisJobRepository.save(any(AnalysisJob.class))).thenReturn(testJob);

        // When
        AnalysisJob result = analysisJobService.updateJobStatus(
            "test-job-123", AnalysisJob.JobStatus.PROCESSING, 50, null
        );

        // Then
        assertThat(result).isEqualTo(testJob);
        assertThat(testJob.getStatus()).isEqualTo(AnalysisJob.JobStatus.PROCESSING);
        assertThat(testJob.getProgressPercentage()).isEqualTo(50);
        verify(analysisJobRepository).save(testJob);
    }

    @Test
    void updateJobStatus_WhenJobDoesNotExist_ShouldThrowException() {
        // Given
        when(analysisJobRepository.findByJobId("non-existent")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> analysisJobService.updateJobStatus(
            "non-existent", AnalysisJob.JobStatus.COMPLETED, 100, null
        )).isInstanceOf(DocumentAnalysisException.class)
          .hasMessageContaining("분석 작업을 찾을 수 없습니다: non-existent");
    }

    @Test
    void updateJobStatus_WhenCompletedStatus_ShouldSetCompletedAt() {
        // Given
        when(analysisJobRepository.findByJobId("test-job-123")).thenReturn(Optional.of(testJob));
        when(analysisJobRepository.save(any(AnalysisJob.class))).thenReturn(testJob);

        // When
        AnalysisJob result = analysisJobService.updateJobStatus(
            "test-job-123", AnalysisJob.JobStatus.COMPLETED, 100, null
        );

        // Then
        assertThat(testJob.getStatus()).isEqualTo(AnalysisJob.JobStatus.COMPLETED);
        assertThat(testJob.getCompletedAt()).isNotNull();
    }

    @Test
    void updateJobProgress_WhenJobExists_ShouldUpdateProgress() {
        // Given
        when(analysisJobRepository.findByJobId("test-job-123")).thenReturn(Optional.of(testJob));

        // When
        analysisJobService.updateJobProgress("test-job-123", 75, "Processing OCR...");

        // Then
        assertThat(testJob.getProgressPercentage()).isEqualTo(75);
        verify(analysisJobRepository).save(testJob);
    }

    @Test
    void updateJobProgress_When100Percent_ShouldSetStatusToCompleted() {
        // Given
        when(analysisJobRepository.findByJobId("test-job-123")).thenReturn(Optional.of(testJob));

        // When
        analysisJobService.updateJobProgress("test-job-123", 100, "Analysis complete");

        // Then
        assertThat(testJob.getProgressPercentage()).isEqualTo(100);
        assertThat(testJob.getStatus()).isEqualTo(AnalysisJob.JobStatus.COMPLETED);
        assertThat(testJob.getCompletedAt()).isNotNull();
    }

    @Test
    void getAnalysisJobsByStatus_ShouldReturnJobsWithSpecificStatus() {
        // Given
        List<AnalysisJob> pendingJobs = List.of(testJob);
        when(analysisJobRepository.findByStatus(AnalysisJob.JobStatus.PENDING)).thenReturn(pendingJobs);

        // When
        List<AnalysisJob> result = analysisJobService.getAnalysisJobsByStatus(AnalysisJob.JobStatus.PENDING);

        // Then
        assertThat(result).hasSize(1).contains(testJob);
    }

    @Test
    void cleanupOldJobs_ShouldDeleteOldCompletedAndFailedJobs() {
        // Given
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        List<AnalysisJob> oldJobs = List.of(testJob);
        
        when(analysisJobRepository.findByCreatedAtBeforeAndStatusIn(
            any(LocalDateTime.class), anyList())).thenReturn(oldJobs);

        // When
        int deletedCount = analysisJobService.cleanupOldJobs(30);

        // Then
        assertThat(deletedCount).isEqualTo(1);
        verify(analysisJobRepository).delete(testJob);
    }
}