package com.smarteye.infrastructure.monitoring;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Caffeine 캐시 모니터링 서비스
 * 
 * 5분마다 캐시 통계를 수집하여 로그로 기록합니다.
 * 캐시 히트율, 메모리 사용량, 만료 통계 등을 추적하여
 * 캐시 성능을 실시간으로 모니터링합니다.
 * 
 * 모니터링 지표:
 * - 히트율 (Hit Rate): 캐시 히트 / 전체 요청
 * - 미스율 (Miss Rate): 캐시 미스 / 전체 요청
 * - 평균 로드 시간 (Avg Load Time): DB 조회 평균 시간
 * - 캐시 엔트리 수 (Entry Count): 현재 캐시된 항목 수
 * - 만료 횟수 (Eviction Count): TTL 또는 크기 제한으로 제거된 횟수
 * 
 * @author SmartEye Team
 * @since P3.2 Performance Optimization
 */
@Service
public class CacheMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(CacheMonitoringService.class);

    private final CacheManager cacheManager;

    public CacheMonitoringService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 5분마다 캐시 통계 로깅
     * 
     * fixedRate = 300000 (5분 = 5 * 60 * 1000ms)
     * initialDelay = 60000 (애플리케이션 시작 후 1분 후 첫 실행)
     */
    @Scheduled(fixedRate = 300000, initialDelay = 60000)
    public void logCacheStatistics() {
        logger.info("📊 ===== Caffeine 캐시 통계 모니터링 =====");

        // cim-results 캐시 통계 조회
        org.springframework.cache.Cache cache = cacheManager.getCache("cim-results");
        
        if (cache == null) {
            logger.warn("⚠️ 'cim-results' 캐시를 찾을 수 없습니다.");
            return;
        }

        if (!(cache instanceof CaffeineCache)) {
            logger.warn("⚠️ 캐시가 Caffeine 타입이 아닙니다: {}", cache.getClass().getName());
            return;
        }

        CaffeineCache caffeineCache = (CaffeineCache) cache;
        Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
        
        // Caffeine CacheStats 조회
        CacheStats stats = nativeCache.stats();

        // 1. 기본 통계
        long estimatedSize = nativeCache.estimatedSize();
        long requestCount = stats.requestCount();
        long hitCount = stats.hitCount();
        long missCount = stats.missCount();
        
        logger.info("🔍 캐시 이름: cim-results");
        logger.info("📈 현재 엔트리 수: {}개", estimatedSize);
        logger.info("📊 총 요청 수: {}회", requestCount);

        // 2. 히트/미스 통계
        if (requestCount > 0) {
            double hitRate = stats.hitRate() * 100;
            double missRate = stats.missRate() * 100;
            
            logger.info("✅ 캐시 히트: {}회 ({:.2f}%)", hitCount, hitRate);
            logger.info("❌ 캐시 미스: {}회 ({:.2f}%)", missCount, missRate);
            
            // 성능 경고 (히트율 50% 미만)
            if (hitRate < 50.0 && requestCount > 100) {
                logger.warn("⚠️ 캐시 히트율이 낮습니다: {:.2f}% (목표: 70% 이상)", hitRate);
            }
        } else {
            logger.info("ℹ️ 아직 캐시 요청이 없습니다.");
        }

        // 3. 로드 통계 (DB 조회)
        long loadSuccessCount = stats.loadSuccessCount();
        long loadFailureCount = stats.loadFailureCount();
        
        if (loadSuccessCount > 0) {
            double avgLoadPenalty = stats.averageLoadPenalty() / 1_000_000; // 나노초 -> 밀리초
            logger.info("🔄 로드 성공: {}회", loadSuccessCount);
            logger.info("⏱️ 평균 로드 시간: {:.2f}ms", avgLoadPenalty);
        }
        
        if (loadFailureCount > 0) {
            logger.warn("❌ 로드 실패: {}회", loadFailureCount);
        }

        // 4. 만료 통계
        long evictionCount = stats.evictionCount();
        long evictionWeight = stats.evictionWeight();
        
        if (evictionCount > 0) {
            logger.info("🗑️ 캐시 만료: {}회", evictionCount);
            logger.info("📏 만료 가중치: {}", evictionWeight);
        }

        // 5. 메모리 추정 (간단한 계산)
        long estimatedMemoryKB = estimatedSize * 50; // 엔트리당 평균 50KB 추정
        double estimatedMemoryMB = estimatedMemoryKB / 1024.0;
        
        logger.info("💾 예상 메모리 사용량: {:.2f}MB ({:.2f}KB/엔트리)", 
                   estimatedMemoryMB, estimatedMemoryKB / (double) Math.max(estimatedSize, 1));

        // 6. 캐시 효율성 점수 계산
        if (requestCount > 0) {
            double efficiencyScore = calculateEfficiencyScore(stats, estimatedSize);
            String efficiencyGrade = getEfficiencyGrade(efficiencyScore);
            
            logger.info("⭐ 캐시 효율성 점수: {:.1f}/100 (등급: {})", efficiencyScore, efficiencyGrade);
        }

        logger.info("📊 ========================================");
    }

    /**
     * 캐시 효율성 점수 계산 (0-100)
     * 
     * 평가 기준:
     * - 히트율 (50%): 70% 이상 -> 50점
     * - 메모리 사용률 (30%): 50% 미만 -> 30점
     * - 로드 시간 (20%): 100ms 미만 -> 20점
     */
    private double calculateEfficiencyScore(CacheStats stats, long estimatedSize) {
        double score = 0.0;

        // 1. 히트율 점수 (최대 50점)
        double hitRate = stats.hitRate();
        if (hitRate >= 0.9) {
            score += 50.0;
        } else if (hitRate >= 0.7) {
            score += 40.0 + (hitRate - 0.7) * 50;
        } else if (hitRate >= 0.5) {
            score += 25.0 + (hitRate - 0.5) * 75;
        } else {
            score += hitRate * 50;
        }

        // 2. 메모리 사용률 점수 (최대 30점)
        // 1000개 중 사용량 기준 (50% 미만이 이상적)
        double usageRate = estimatedSize / 1000.0;
        if (usageRate < 0.5) {
            score += 30.0;
        } else if (usageRate < 0.8) {
            score += 20.0;
        } else {
            score += 10.0;
        }

        // 3. 로드 시간 점수 (최대 20점)
        if (stats.loadSuccessCount() > 0) {
            double avgLoadMs = stats.averageLoadPenalty() / 1_000_000;
            if (avgLoadMs < 50) {
                score += 20.0;
            } else if (avgLoadMs < 100) {
                score += 15.0;
            } else if (avgLoadMs < 200) {
                score += 10.0;
            } else {
                score += 5.0;
            }
        } else {
            score += 20.0; // 로드 없음 = 완벽한 캐시 히트
        }

        return Math.min(score, 100.0);
    }

    /**
     * 효율성 점수에 따른 등급 반환
     */
    private String getEfficiencyGrade(double score) {
        if (score >= 90) return "S (Excellent)";
        if (score >= 80) return "A (Very Good)";
        if (score >= 70) return "B (Good)";
        if (score >= 60) return "C (Fair)";
        if (score >= 50) return "D (Poor)";
        return "F (Needs Improvement)";
    }

    /**
     * 수동으로 캐시 통계를 즉시 조회
     * (디버깅 또는 관리자 대시보드용)
     */
    public String getCacheStatisticsReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Caffeine 캐시 통계 리포트 ===\n");

        org.springframework.cache.Cache cache = cacheManager.getCache("cim-results");
        if (cache == null) {
            report.append("캐시를 찾을 수 없습니다.\n");
            return report.toString();
        }

        if (cache instanceof CaffeineCache) {
            CaffeineCache caffeineCache = (CaffeineCache) cache;
            Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
            CacheStats stats = nativeCache.stats();

            report.append(String.format("엔트리 수: %d개\n", nativeCache.estimatedSize()));
            report.append(String.format("총 요청: %d회\n", stats.requestCount()));
            report.append(String.format("히트율: %.2f%%\n", stats.hitRate() * 100));
            report.append(String.format("미스율: %.2f%%\n", stats.missRate() * 100));
            report.append(String.format("평균 로드 시간: %.2fms\n", stats.averageLoadPenalty() / 1_000_000));
            report.append(String.format("만료 횟수: %d회\n", stats.evictionCount()));
        }

        report.append("================================");
        return report.toString();
    }
}
