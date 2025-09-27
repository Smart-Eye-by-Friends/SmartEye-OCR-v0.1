package com.smarteye.application.analysis;

import com.smarteye.domain.analysis.entity.AnalysisJob;
import com.smarteye.domain.analysis.entity.CIMOutput;
import com.smarteye.domain.analysis.repository.AnalysisJobRepository;
import com.smarteye.domain.analysis.repository.CIMOutputRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.smarteye.application.analysis.AnalysisJobService;
import com.smarteye.application.user.UserService;
import com.smarteye.domain.document.entity.DocumentPage;
import com.smarteye.infrastructure.external.*;
import com.smarteye.application.file.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CIM 분석 시스템 모니터링 및 메트릭 수집 서비스
 *
 * 기능:
 * 1. 시스템 상태 모니터링
 * 2. 성능 메트릭 수집
 * 3. 오류 패턴 분석
 * 4. 알림 및 경고 시스템
 */
@Service
public class CIMAnalysisMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(CIMAnalysisMonitoringService.class);

    @Autowired
    private AnalysisJobRepository analysisJobRepository;

    @Autowired
    private CIMOutputRepository cimOutputRepository;

    @Autowired
    private CircuitBreaker cimDatabaseCircuitBreaker;

    @Autowired
    private com.smarteye.infrastructure.service.ConcurrencyManagerService concurrencyManagerService;

    /**
     * 시스템 상태 종합 모니터링 (5분마다 실행)
     */
    @Scheduled(fixedRate = 300000) // 5분
    public void monitorSystemHealth() {
        try {
            logger.info("📊 CIM 분석 시스템 상태 모니터링 시작");

            // 1. 전체 작업 상태 분석
            SystemHealthMetrics metrics = collectSystemHealthMetrics();

            // 2. Circuit Breaker 상태 확인
            CircuitBreakerStatus cbStatus = checkCircuitBreakerStatus();

            // 3. 동시성 관리 상태 확인
            ConcurrencyStatus concurrencyStatus = checkConcurrencyStatus();

            // 4. 성능 임계값 확인
            PerformanceAlert performanceAlert = checkPerformanceThresholds(metrics);

            // 5. 종합 상태 로깅
            logSystemHealthSummary(metrics, cbStatus, concurrencyStatus, performanceAlert);

            // 6. 필요시 경고 발송
            if (performanceAlert.hasAlerts()) {
                sendPerformanceAlerts(performanceAlert);
            }

        } catch (Exception e) {
            logger.error("❌ 시스템 상태 모니터링 중 오류 발생", e);
        }
    }

    /**
     * 실패한 작업 분석 및 복구 시도 (10분마다 실행)
     */
    @Scheduled(fixedRate = 600000) // 10분
    public void analyzeAndRecoverFailedJobs() {
        try {
            logger.info("🔧 실패 작업 분석 및 복구 시작");

            // 최근 1시간 내 실패한 작업 조회
            LocalDateTime since = LocalDateTime.now().minusHours(1);
            List<AnalysisJob> failedJobs = analysisJobRepository.findByStatusAndUpdatedAtAfter(
                AnalysisJob.JobStatus.FAILED, since
            );

            // 실패 패턴 분석
            FailureAnalysis analysis = analyzeFailurePatterns(failedJobs);
            logFailureAnalysis(analysis);

            // 복구 가능한 작업 식별 및 재시도
            List<AnalysisJob> recoverableJobs = identifyRecoverableJobs(failedJobs);
            if (!recoverableJobs.isEmpty()) {
                logger.info("🔄 복구 가능한 작업 {}개 발견, 재처리 대기열에 추가", recoverableJobs.size());
                // 실제 복구 로직은 비즈니스 요구사항에 따라 구현
            }

        } catch (Exception e) {
            logger.error("❌ 실패 작업 분석 중 오류 발생", e);
        }
    }

    /**
     * 성능 통계 수집 및 리포트 (1시간마다 실행)
     */
    @Scheduled(fixedRate = 3600000) // 1시간
    public void collectPerformanceStatistics() {
        try {
            logger.info("📈 성능 통계 수집 시작");

            // 최근 24시간 통계
            LocalDateTime since = LocalDateTime.now().minusHours(24);
            PerformanceStatistics stats = calculatePerformanceStatistics(since);

            // 성능 트렌드 분석
            PerformanceTrend trend = analyzePerformanceTrend(stats);

            // 통계 로깅
            logPerformanceStatistics(stats, trend);

            // 성능 개선 권장사항 생성
            List<String> recommendations = generatePerformanceRecommendations(stats, trend);
            if (!recommendations.isEmpty()) {
                logger.info("💡 성능 개선 권장사항:");
                recommendations.forEach(rec -> logger.info("   - {}", rec));
            }

        } catch (Exception e) {
            logger.error("❌ 성능 통계 수집 중 오류 발생", e);
        }
    }

    /**
     * 시스템 상태 메트릭 수집
     */
    private SystemHealthMetrics collectSystemHealthMetrics() {
        SystemHealthMetrics metrics = new SystemHealthMetrics();

        // 최근 1시간 작업 통계
        LocalDateTime since = LocalDateTime.now().minusHours(1);

        metrics.totalJobs = analysisJobRepository.countByCreatedAtAfter(since);
        metrics.completedJobs = analysisJobRepository.countByStatusAndCreatedAtAfter(
            AnalysisJob.JobStatus.COMPLETED, since
        );
        metrics.failedJobs = analysisJobRepository.countByStatusAndCreatedAtAfter(
            AnalysisJob.JobStatus.FAILED, since
        );
        metrics.processingJobs = analysisJobRepository.countByStatus(AnalysisJob.JobStatus.PROCESSING);

        // CIM 출력 통계
        metrics.completedCIMOutputs = cimOutputRepository.countByGenerationStatus(
            CIMOutput.GenerationStatus.COMPLETED
        );
        metrics.failedCIMOutputs = cimOutputRepository.countByGenerationStatus(
            CIMOutput.GenerationStatus.FAILED
        );

        // 성공률 계산
        metrics.successRate = metrics.totalJobs > 0 ?
            (double) metrics.completedJobs / metrics.totalJobs * 100 : 0.0;

        // 평균 처리 시간
        metrics.averageProcessingTime = cimOutputRepository.getAverageProcessingTime();

        return metrics;
    }

    /**
     * Circuit Breaker 상태 확인
     */
    private CircuitBreakerStatus checkCircuitBreakerStatus() {
        CircuitBreakerStatus status = new CircuitBreakerStatus();

        status.state = cimDatabaseCircuitBreaker.getState();
        status.isHealthy = status.state == CircuitBreaker.State.CLOSED ||
                          status.state == CircuitBreaker.State.HALF_OPEN;

        var metrics = cimDatabaseCircuitBreaker.getMetrics();
        status.failureRate = metrics.getFailureRate();
        status.slowCallRate = metrics.getSlowCallRate();
        status.totalCalls = (metrics.getNumberOfFailedCalls() + metrics.getNumberOfSuccessfulCalls());
        status.failedCalls = metrics.getNumberOfFailedCalls();

        return status;
    }

    /**
     * 동시성 관리 상태 확인
     */
    private ConcurrencyStatus checkConcurrencyStatus() {
        ConcurrencyStatus status = new ConcurrencyStatus();

        status.activeLocks = concurrencyManagerService.getActiveLockCount();
        status.queuedThreads = concurrencyManagerService.getQueuedThreadCount();
        status.isHealthy = status.activeLocks < 10 && status.queuedThreads < 20; // 임계값

        return status;
    }

    /**
     * 성능 임계값 확인
     */
    private PerformanceAlert checkPerformanceThresholds(SystemHealthMetrics metrics) {
        PerformanceAlert alert = new PerformanceAlert();

        // 성공률 임계값 (90% 미만시 경고)
        if (metrics.successRate < 90.0) {
            alert.addAlert("SUCCESS_RATE_LOW",
                String.format("성공률이 %.2f%%로 임계값(90%%) 미만입니다", metrics.successRate));
        }

        // 평균 처리 시간 임계값 (30초 초과시 경고)
        if (metrics.averageProcessingTime != null && metrics.averageProcessingTime > 30000) {
            alert.addAlert("PROCESSING_TIME_HIGH",
                String.format("평균 처리 시간이 %.2f초로 임계값(30초) 초과했습니다",
                    metrics.averageProcessingTime / 1000.0));
        }

        // 실패 작업 비율 임계값 (20% 초과시 경고)
        if (metrics.totalJobs > 0) {
            double failureRate = (double) metrics.failedJobs / metrics.totalJobs * 100;
            if (failureRate > 20.0) {
                alert.addAlert("FAILURE_RATE_HIGH",
                    String.format("실패율이 %.2f%%로 임계값(20%%) 초과했습니다", failureRate));
            }
        }

        return alert;
    }

    /**
     * 실패 패턴 분석
     */
    private FailureAnalysis analyzeFailurePatterns(List<AnalysisJob> failedJobs) {
        FailureAnalysis analysis = new FailureAnalysis();

        // 오류 메시지별 그룹화
        Map<String, Long> errorGroups = failedJobs.stream()
            .filter(job -> job.getErrorMessage() != null)
            .collect(Collectors.groupingBy(
                job -> extractErrorCategory(job.getErrorMessage()),
                Collectors.counting()
            ));

        analysis.errorPatterns = errorGroups;
        analysis.totalFailures = failedJobs.size();

        // 가장 빈번한 오류 식별
        analysis.mostFrequentError = errorGroups.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("UNKNOWN");

        return analysis;
    }

    /**
     * 복구 가능한 작업 식별
     */
    private List<AnalysisJob> identifyRecoverableJobs(List<AnalysisJob> failedJobs) {
        return failedJobs.stream()
            .filter(this::isRecoverable)
            .collect(Collectors.toList());
    }

    /**
     * 작업 복구 가능성 판단
     */
    private boolean isRecoverable(AnalysisJob job) {
        if (job.getErrorMessage() == null) return false;

        String errorMsg = job.getErrorMessage().toLowerCase();

        // 복구 가능한 오류 패턴
        return errorMsg.contains("timeout") ||
               errorMsg.contains("connection") ||
               errorMsg.contains("temporary") ||
               errorMsg.contains("retry");
    }

    /**
     * 오류 카테고리 추출
     */
    private String extractErrorCategory(String errorMessage) {
        if (errorMessage == null) return "UNKNOWN";

        String msg = errorMessage.toLowerCase();
        if (msg.contains("db") || msg.contains("database")) return "DATABASE_ERROR";
        if (msg.contains("timeout")) return "TIMEOUT_ERROR";
        if (msg.contains("memory") || msg.contains("oom")) return "MEMORY_ERROR";
        if (msg.contains("json") || msg.contains("serializ")) return "SERIALIZATION_ERROR";
        if (msg.contains("validation")) return "VALIDATION_ERROR";
        if (msg.contains("null")) return "NULL_POINTER_ERROR";

        return "OTHER_ERROR";
    }

    /**
     * 성능 통계 계산
     */
    private PerformanceStatistics calculatePerformanceStatistics(LocalDateTime since) {
        PerformanceStatistics stats = new PerformanceStatistics();

        // 24시간 통계
        stats.totalJobsLast24h = analysisJobRepository.countByCreatedAtAfter(since);
        stats.averageProcessingTimeLast24h = cimOutputRepository.getAverageProcessingTime();

        // 최고/최저 처리 시간
        List<CIMOutput> completedOutputs = cimOutputRepository.findByGenerationStatus(
            CIMOutput.GenerationStatus.COMPLETED
        );

        if (!completedOutputs.isEmpty()) {
            stats.maxProcessingTime = completedOutputs.stream()
                .filter(output -> output.getProcessingTimeMs() != null)
                .mapToLong(CIMOutput::getProcessingTimeMs)
                .max()
                .orElse(0L);

            stats.minProcessingTime = completedOutputs.stream()
                .filter(output -> output.getProcessingTimeMs() != null)
                .mapToLong(CIMOutput::getProcessingTimeMs)
                .min()
                .orElse(0L);
        }

        return stats;
    }

    /**
     * 성능 트렌드 분석
     */
    private PerformanceTrend analyzePerformanceTrend(PerformanceStatistics stats) {
        PerformanceTrend trend = new PerformanceTrend();

        // 이전 24시간과 비교 (간단한 구현)
        LocalDateTime previousPeriod = LocalDateTime.now().minusHours(48);
        LocalDateTime currentPeriodStart = LocalDateTime.now().minusHours(24);

        long previousPeriodJobs = analysisJobRepository.countByCreatedAtBetween(
            previousPeriod, currentPeriodStart
        );

        if (previousPeriodJobs > 0) {
            trend.volumeChange = ((double) stats.totalJobsLast24h - previousPeriodJobs) /
                               previousPeriodJobs * 100;
        }

        trend.isVolumeIncreasing = trend.volumeChange > 5.0;
        trend.isPerformanceDegrading = stats.averageProcessingTimeLast24h != null &&
                                     stats.averageProcessingTimeLast24h > 20000; // 20초 이상

        return trend;
    }

    /**
     * 성능 개선 권장사항 생성
     */
    private List<String> generatePerformanceRecommendations(PerformanceStatistics stats,
                                                           PerformanceTrend trend) {
        List<String> recommendations = new java.util.ArrayList<>();

        if (trend.isPerformanceDegrading) {
            recommendations.add("평균 처리 시간이 증가했습니다. 데이터베이스 인덱스 최적화를 검토하세요.");
        }

        if (trend.isVolumeIncreasing && trend.volumeChange > 20.0) {
            recommendations.add("작업 볼륨이 급증했습니다. 스케일링 계획을 검토하세요.");
        }

        if (stats.maxProcessingTime > 60000) { // 1분 초과
            recommendations.add("일부 작업의 처리 시간이 매우 길습니다. 타임아웃 설정을 검토하세요.");
        }

        return recommendations;
    }

    /**
     * 시스템 상태 종합 로깅
     */
    private void logSystemHealthSummary(SystemHealthMetrics metrics,
                                       CircuitBreakerStatus cbStatus,
                                       ConcurrencyStatus concurrencyStatus,
                                       PerformanceAlert alert) {
        logger.info("🏥 시스템 상태 종합 리포트");
        logger.info("├─ 작업 통계: 총 {} / 완료 {} / 실패 {} / 성공률 {:.2f}%",
                   metrics.totalJobs, metrics.completedJobs, metrics.failedJobs, metrics.successRate);
        logger.info("├─ Circuit Breaker: {} (실패율: {:.2f}%)",
                   cbStatus.state, cbStatus.failureRate);
        logger.info("├─ 동시성 관리: 활성 락 {} / 대기 스레드 {}",
                   concurrencyStatus.activeLocks, concurrencyStatus.queuedThreads);
        logger.info("└─ 평균 처리 시간: {:.2f}초",
                   metrics.averageProcessingTime != null ? metrics.averageProcessingTime / 1000.0 : 0.0);

        if (alert.hasAlerts()) {
            logger.warn("⚠️ 성능 경고 {}개 발생", alert.alerts.size());
        }
    }

    /**
     * 실패 분석 로깅
     */
    private void logFailureAnalysis(FailureAnalysis analysis) {
        if (analysis.totalFailures > 0) {
            logger.warn("📊 실패 분석 결과 - 총 {}개 실패", analysis.totalFailures);
            logger.warn("├─ 주요 오류: {}", analysis.mostFrequentError);
            logger.warn("└─ 오류 패턴:");
            analysis.errorPatterns.forEach((error, count) ->
                logger.warn("   - {}: {}회", error, count));
        }
    }

    /**
     * 성능 통계 로깅
     */
    private void logPerformanceStatistics(PerformanceStatistics stats, PerformanceTrend trend) {
        logger.info("📈 성능 통계 (24시간)");
        logger.info("├─ 처리 작업: {}개", stats.totalJobsLast24h);
        logger.info("├─ 평균 처리 시간: {:.2f}초",
                   stats.averageProcessingTimeLast24h != null ?
                   stats.averageProcessingTimeLast24h / 1000.0 : 0.0);
        logger.info("├─ 최대 처리 시간: {:.2f}초", stats.maxProcessingTime / 1000.0);
        logger.info("├─ 최소 처리 시간: {:.2f}초", stats.minProcessingTime / 1000.0);
        logger.info("└─ 볼륨 변화: {:.2f}%", trend.volumeChange);
    }

    /**
     * 성능 경고 발송
     */
    private void sendPerformanceAlerts(PerformanceAlert alert) {
        logger.warn("🚨 성능 경고 발송");
        alert.alerts.forEach((type, message) ->
            logger.warn("   [{}] {}", type, message));

        // 실제 환경에서는 여기서 이메일, Slack, PagerDuty 등으로 알림 발송
    }

    // 내부 클래스들
    private static class SystemHealthMetrics {
        long totalJobs;
        long completedJobs;
        long failedJobs;
        long processingJobs;
        long completedCIMOutputs;
        long failedCIMOutputs;
        double successRate;
        Double averageProcessingTime;
    }

    private static class CircuitBreakerStatus {
        CircuitBreaker.State state;
        boolean isHealthy;
        float failureRate;
        float slowCallRate;
        long totalCalls;
        long failedCalls;
    }

    private static class ConcurrencyStatus {
        int activeLocks;
        int queuedThreads;
        boolean isHealthy;
    }

    private static class PerformanceAlert {
        Map<String, String> alerts = new java.util.HashMap<>();

        void addAlert(String type, String message) {
            alerts.put(type, message);
        }

        boolean hasAlerts() {
            return !alerts.isEmpty();
        }
    }

    private static class FailureAnalysis {
        int totalFailures;
        String mostFrequentError;
        Map<String, Long> errorPatterns;
    }

    private static class PerformanceStatistics {
        long totalJobsLast24h;
        Double averageProcessingTimeLast24h;
        long maxProcessingTime;
        long minProcessingTime;
    }

    private static class PerformanceTrend {
        double volumeChange;
        boolean isVolumeIncreasing;
        boolean isPerformanceDegrading;
    }
}