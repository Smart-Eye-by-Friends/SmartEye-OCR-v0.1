package com.smarteye.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * TSPM 모듈의 비동기 처리를 위한 ThreadPoolTaskExecutor 설정
 *
 * 성능 최적화 전략:
 * - analysisTaskExecutor: 범용 분석 작업용 (CPU 코어 기반)
 * - ocrTaskExecutor: OCR 전용 (CPU 집약적 작업 최적화)
 * - aiTaskExecutor: AI 설명 전용 (I/O 대기 최적화)
 *
 * 예상 성능 향상: 40-50% 처리 시간 단축
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();

    /**
     * 범용 분석 작업용 ThreadPoolTaskExecutor
     * AsyncAnalysisService의 @Async("analysisTaskExecutor")에서 사용
     *
     * 설정 근거:
     * - CorePoolSize: CPU 코어 수 (현재 시스템 최적화)
     * - MaxPoolSize: CPU 코어 수 * 2 (하이퍼스레딩 고려)
     * - QueueCapacity: 50 (메모리 효율적인 대기열)
     */
    @Bean("analysisTaskExecutor")
    public ThreadPoolTaskExecutor analysisTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CPU_CORES);
        executor.setMaxPoolSize(CPU_CORES * 2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("analysis-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * OCR 전용 ThreadPoolTaskExecutor
     * CPU 집약적 작업에 최적화
     *
     * 설정 근거:
     * - CorePoolSize: CPU 코어 수 / 2 (CPU 집약적 특성)
     * - MaxPoolSize: CPU 코어 수 (최대 활용)
     * - QueueCapacity: 30 (OCR 작업 특성상 제한적 대기)
     */
    @Bean("ocrTaskExecutor")
    public ThreadPoolTaskExecutor ocrTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, CPU_CORES / 2));
        executor.setMaxPoolSize(CPU_CORES);
        executor.setQueueCapacity(30);
        executor.setThreadNamePrefix("ocr-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * AI 설명 전용 ThreadPoolTaskExecutor
     * I/O 대기 시간이 긴 작업에 최적화
     *
     * 설정 근거:
     * - CorePoolSize: CPU 코어 수 (기본 처리량)
     * - MaxPoolSize: CPU 코어 수 * 3 (I/O 대기 시간 고려)
     * - QueueCapacity: 20 (AI API 호출 특성상 빠른 처리)
     */
    @Bean("aiTaskExecutor")
    public ThreadPoolTaskExecutor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CPU_CORES);
        executor.setMaxPoolSize(CPU_CORES * 3);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("ai-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}