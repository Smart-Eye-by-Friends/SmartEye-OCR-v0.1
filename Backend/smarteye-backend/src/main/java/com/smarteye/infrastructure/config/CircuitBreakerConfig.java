package com.smarteye.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;

/**
 * Circuit Breaker 설정
 * CIM 분석 API의 DB 저장 안정성 향상을 위한 회로 차단 패턴 구현
 */
@Configuration
public class CircuitBreakerConfig {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerConfig.class);

    /**
     * CIM DB 저장 작업용 Circuit Breaker
     */
    @Bean
    public CircuitBreaker cimDatabaseCircuitBreaker() {
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig config = io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .failureRateThreshold(70) // 실패율 70% 이상시 OPEN
                .waitDurationInOpenState(Duration.ofSeconds(30)) // OPEN 상태 지속 시간
                .slidingWindowSize(10) // 슬라이딩 윈도우 크기
                .minimumNumberOfCalls(5) // 최소 호출 수
                .permittedNumberOfCallsInHalfOpenState(3) // HALF_OPEN 상태에서 허용 호출 수
                .slowCallRateThreshold(80) // 느린 호출 비율 임계값
                .slowCallDurationThreshold(Duration.ofSeconds(10)) // 느린 호출 기준 시간
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                // DB 관련 예외만 실패로 간주 (비즈니스 로직 예외는 제외)
                .recordExceptions(
                    DataAccessException.class,
                    org.springframework.dao.DataIntegrityViolationException.class,
                    org.springframework.dao.QueryTimeoutException.class,
                    org.springframework.dao.CannotAcquireLockException.class,
                    java.sql.SQLException.class,
                    org.springframework.transaction.TransactionException.class
                )
                // 중복 키 오류는 복구 가능하므로 무시 (멱등성 처리)
                .ignoreExceptions(
                    IllegalArgumentException.class,
                    IllegalStateException.class
                )
                .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("cim-database", config);

        // 이벤트 리스너 등록
        circuitBreaker.getEventPublisher()
                .onStateTransition(event ->
                    logger.warn("🔄 CIM DB Circuit Breaker 상태 변경: {} -> {}",
                               event.getStateTransition().getFromState(),
                               event.getStateTransition().getToState()))
                .onCallNotPermitted(event ->
                    logger.error("🚫 CIM DB Circuit Breaker 호출 차단"))
                .onError(event ->
                    logger.error("❌ CIM DB Circuit Breaker 오류: {}",
                               event.getThrowable().getMessage()))
                .onSuccess(event ->
                    logger.debug("✅ CIM DB Circuit Breaker 성공: {}ms",
                               event.getElapsedDuration().toMillis()));

        return circuitBreaker;
    }

    /**
     * 외부 서비스 호출용 Circuit Breaker (LAM, OpenAI 등)
     */
    @Bean
    public CircuitBreaker externalServiceCircuitBreaker() {
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig config = io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .failureRateThreshold(60) // 외부 서비스는 더 관대한 임계값
                .waitDurationInOpenState(Duration.ofSeconds(60)) // 더 긴 대기 시간
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .permittedNumberOfCallsInHalfOpenState(5)
                .slowCallRateThreshold(90)
                .slowCallDurationThreshold(Duration.ofSeconds(30)) // 외부 서비스는 더 긴 허용 시간
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(
                    java.io.IOException.class,
                    java.net.ConnectException.class,
                    java.net.SocketTimeoutException.class,
                    org.springframework.web.client.ResourceAccessException.class,
                    org.springframework.web.client.HttpServerErrorException.class
                )
                .ignoreExceptions(
                    org.springframework.web.client.HttpClientErrorException.class, // 4xx 오류는 재시도 불필요
                    IllegalArgumentException.class
                )
                .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("external-service", config);

        // 외부 서비스용 이벤트 리스너
        circuitBreaker.getEventPublisher()
                .onStateTransition(event ->
                    logger.warn("🌐 외부 서비스 Circuit Breaker 상태 변경: {} -> {}",
                               event.getStateTransition().getFromState(),
                               event.getStateTransition().getToState()))
                .onCallNotPermitted(event ->
                    logger.error("🚫 외부 서비스 호출 차단 - fallback 로직 실행 필요"));

        return circuitBreaker;
    }

    /**
     * CIM DB 저장 재시도 설정
     */
    @Bean
    public Retry cimDatabaseRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3) // 최대 3회 재시도
                .waitDuration(Duration.ofMillis(500)) // 재시도 간격
                // .exponentialBackoffMultiplier(2.0) // 지수 백오프 - 메서드가 존재하지 않음
                .retryOnException(throwable -> {
                    // 재시도 가능한 예외인지 판단
                    return throwable instanceof DataIntegrityViolationException ||
                           throwable instanceof org.springframework.dao.QueryTimeoutException ||
                           throwable instanceof org.springframework.dao.CannotAcquireLockException ||
                           (throwable instanceof RuntimeException &&
                            throwable.getMessage() != null &&
                            throwable.getMessage().contains("timeout"));
                })
                .build();

        Retry retry = Retry.of("cim-database-retry", config);

        // 재시도 이벤트 리스너
        retry.getEventPublisher()
                .onRetry(event ->
                    logger.warn("🔁 CIM DB 저장 재시도 #{}: {}",
                               event.getNumberOfRetryAttempts(),
                               event.getLastThrowable().getMessage()))
                .onSuccess(event ->
                    logger.info("✅ CIM DB 저장 재시도 성공 (총 {} 시도)",
                               event.getNumberOfRetryAttempts()));

        return retry;
    }

    /**
     * Circuit Breaker 상태 모니터링을 위한 헬스 체크
     */
    @Bean
    public CircuitBreakerHealthIndicator circuitBreakerHealthIndicator() {
        return new CircuitBreakerHealthIndicator();
    }

    /**
     * Circuit Breaker 상태 모니터링 클래스
     */
    public static class CircuitBreakerHealthIndicator {

        public boolean isCimDatabaseHealthy(CircuitBreaker circuitBreaker) {
            CircuitBreaker.State state = circuitBreaker.getState();
            boolean isHealthy = state == CircuitBreaker.State.CLOSED ||
                               state == CircuitBreaker.State.HALF_OPEN;

            if (!isHealthy) {
                logger.warn("⚠️ CIM DB Circuit Breaker 비정상 상태: {}", state);
            }

            return isHealthy;
        }

        public String getCircuitBreakerMetrics(CircuitBreaker circuitBreaker) {
            var metrics = circuitBreaker.getMetrics();
            return String.format(
                "Circuit Breaker 메트릭 - 실패율: %.2f%%, 느린 호출율: %.2f%%, " +
                "총 호출: %d, 실패: %d, 성공: %d",
                metrics.getFailureRate(),
                metrics.getSlowCallRate(),
                (metrics.getNumberOfFailedCalls() + metrics.getNumberOfSuccessfulCalls()),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfSuccessfulCalls()
            );
        }
    }
}