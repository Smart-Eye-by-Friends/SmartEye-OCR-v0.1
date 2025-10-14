package com.smarteye.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 캐시 설정 클래스.
 *
 * <p>P2 통합 최적화: API 응답 캐싱을 통한 성능 향상</p>
 *
 * <h2>캐시 전략</h2>
 * <ul>
 *   <li><strong>cim-results</strong>: CIM 분석 결과 캐싱 (1시간 만료, 최대 1000개)</li>
 *   <li><strong>분석 결과 재사용</strong>: 동일 jobId 요청 시 DB 조회 없이 즉시 반환</li>
 *   <li><strong>메모리 관리</strong>: LRU 정책으로 오래된 항목 자동 제거</li>
 * </ul>
 *
 * <h2>캐시 만료 정책</h2>
 * <ul>
 *   <li><strong>시간 기반 만료</strong>: 작성 후 1시간(60분) 경과 시 자동 제거</li>
 *   <li><strong>크기 기반 제거</strong>: 최대 1000개 항목 유지, 초과 시 LRU 제거</li>
 * </ul>
 *
 * <h2>사용 방법</h2>
 * <pre>{@code
 * @Cacheable(value = "cim-results", key = "#jobId")
 * public EnhancedCIMData getCIMResult(Long jobId) {
 *     // DB 조회 로직
 * }
 * }</pre>
 *
 * @author SmartEye Development Team
 * @version 1.0 (P2 Integration & Optimization)
 * @since 2025-10-13
 * @see org.springframework.cache.annotation.Cacheable
 * @see org.springframework.cache.annotation.CacheEvict
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger logger = LoggerFactory.getLogger(CacheConfig.class);

    /**
     * CIM 결과 캐시 이름.
     */
    public static final String CIM_RESULTS_CACHE = "cim-results";

    /**
     * 캐시 만료 시간 (분).
     */
    private static final long CACHE_EXPIRATION_MINUTES = 60;

    /**
     * 최대 캐시 항목 수.
     */
    private static final long MAXIMUM_CACHE_SIZE = 1000;

    /**
     * Caffeine 기반 CacheManager 빈 생성.
     *
     * <p>CIM 분석 결과를 메모리에 캐싱하여 반복 요청 시 성능을 향상시킵니다.</p>
     *
     * <h3>캐시 설정 상세</h3>
     * <ul>
     *   <li><strong>expireAfterWrite</strong>: 항목 작성 후 60분 경과 시 만료</li>
     *   <li><strong>maximumSize</strong>: 최대 1000개 항목 유지</li>
     *   <li><strong>recordStats</strong>: 캐시 히트율 통계 기록 (모니터링용)</li>
     * </ul>
     *
     * <h3>메모리 사용량 추정</h3>
     * <ul>
     *   <li>항목당 평균 크기: 약 50KB (CIM 분석 결과)</li>
     *   <li>최대 메모리 사용: 약 50MB (1000개 × 50KB)</li>
     * </ul>
     *
     * @return Caffeine 기반 CacheManager
     */
    @Bean
    public CacheManager cacheManager() {
        logger.info("🔧 Caffeine 캐시 초기화 중...");

        // Caffeine 캐시 빌더
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                .expireAfterWrite(CACHE_EXPIRATION_MINUTES, TimeUnit.MINUTES)
                .maximumSize(MAXIMUM_CACHE_SIZE)
                .recordStats(); // 통계 기록 활성화

        // CaffeineCacheManager 생성
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(CIM_RESULTS_CACHE);
        cacheManager.setCaffeine(caffeine);

        logger.info("✅ Caffeine 캐시 초기화 완료");
        logger.info("  - 캐시 이름: {}", CIM_RESULTS_CACHE);
        logger.info("  - 만료 시간: {}분", CACHE_EXPIRATION_MINUTES);
        logger.info("  - 최대 크기: {}개", MAXIMUM_CACHE_SIZE);
        logger.info("  - 통계 기록: 활성화");

        return cacheManager;
    }

    /**
     * 캐시 통계 로깅 (선택사항).
     *
     * <p>주기적으로 캐시 히트율 등의 통계를 로깅하려면
     * {@code @Scheduled} 메서드를 추가하여 사용할 수 있습니다.</p>
     *
     * <pre>{@code
     * @Scheduled(fixedRate = 300000) // 5분마다
     * public void logCacheStatistics() {
     *     CacheManager cacheManager = ... ;
     *     Cache cache = cacheManager.getCache(CIM_RESULTS_CACHE);
     *     if (cache instanceof CaffeineCache) {
     *         com.github.benmanes.caffeine.cache.Cache nativeCache =
     *             ((CaffeineCache) cache).getNativeCache();
     *         CacheStats stats = nativeCache.stats();
     *         logger.info("📊 캐시 통계 - 히트율: {:.2f}%, 요청: {}회, 히트: {}회, 미스: {}회",
     *                    stats.hitRate() * 100,
     *                    stats.requestCount(),
     *                    stats.hitCount(),
     *                    stats.missCount());
     *     }
     * }
     * }</pre>
     */
    // 필요 시 주석 해제하여 사용
    // @Scheduled(fixedRate = 300000)
    // public void logCacheStatistics() { ... }
}
