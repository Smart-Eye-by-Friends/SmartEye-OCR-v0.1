package com.smarteye.infrastructure.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.util.concurrent.ConcurrentHashMap;
import com.smarteye.application.analysis.AnalysisJobService;
import com.smarteye.application.user.UserService;
import com.smarteye.domain.document.entity.DocumentPage;
import com.smarteye.infrastructure.external.*;
import com.smarteye.application.file.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 이미지 캐싱 서비스
 *
 * 중복 이미지 처리를 방지하여 성능을 최적화합니다.
 * - OCR, AI 서비스에서 동일한 이미지를 여러 번 크롭하는 것을 방지
 * - 메모리 기반 캐싱으로 빠른 접근 제공
 * - TTL 기반 자동 캐시 정리
 */
@Service
public class ImageCacheService {

    private static final Logger logger = LoggerFactory.getLogger(ImageCacheService.class);

    @Value("${smarteye.cache.image.ttl-minutes:10}")
    private int cacheTtlMinutes;

    @Value("${smarteye.cache.image.max-size:100}")
    private int maxCacheSize;

    // 이미지 캐시 (JobID -> 이미지)
    private final ConcurrentHashMap<String, CachedImage> imageCache = new ConcurrentHashMap<>();

    // 캐시 정리를 위한 스케줄러
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /**
     * 서비스 초기화 - 캐시 정리 스케줄러 시작
     */
    public ImageCacheService() {
        // 5분마다 만료된 캐시 정리
        scheduler.scheduleWithFixedDelay(this::evictExpiredEntries, 5, 5, TimeUnit.MINUTES);

        // 캐시 크기 초과 시 LRU 방식으로 정리
        scheduler.scheduleWithFixedDelay(this::evictOldestEntries, 3, 3, TimeUnit.MINUTES);

        logger.info("이미지 캐시 서비스 초기화 완료 - TTL: {}분, 최대 크기: {}개",
                   cacheTtlMinutes, maxCacheSize);
    }

    /**
     * 이미지를 캐시에 저장
     *
     * @param image 캐시할 이미지
     * @param jobId 작업 ID (캐시 키)
     * @return 캐시 키
     */
    public String cacheImage(BufferedImage image, String jobId) {
        if (image == null || jobId == null) {
            logger.warn("이미지 또는 JobID가 null입니다");
            return null;
        }

        try {
            CachedImage cachedImage = new CachedImage(image, System.currentTimeMillis());
            imageCache.put(jobId, cachedImage);

            logger.debug("이미지 캐시 저장 완료 - JobID: {}, 크기: {}x{}, 캐시 사용량: {}/{}",
                        jobId, image.getWidth(), image.getHeight(),
                        imageCache.size(), maxCacheSize);

            return jobId;

        } catch (Exception e) {
            logger.error("이미지 캐시 저장 실패 - JobID: {}", jobId, e);
            return null;
        }
    }

    /**
     * 캐시에서 이미지 조회
     *
     * @param jobId 작업 ID (캐시 키)
     * @return 캐시된 이미지 (없으면 null)
     */
    public BufferedImage getCachedImage(String jobId) {
        if (jobId == null) {
            return null;
        }

        try {
            CachedImage cachedImage = imageCache.get(jobId);

            if (cachedImage == null) {
                logger.debug("캐시된 이미지를 찾을 수 없음 - JobID: {}", jobId);
                return null;
            }

            // TTL 체크
            long currentTime = System.currentTimeMillis();
            long cacheAge = currentTime - cachedImage.getCachedTime();
            long ttlMs = cacheTtlMinutes * 60 * 1000;

            if (cacheAge > ttlMs) {
                logger.debug("캐시된 이미지가 만료됨 - JobID: {}, 경과시간: {}ms", jobId, cacheAge);
                imageCache.remove(jobId);
                return null;
            }

            // 접근 시간 업데이트 (LRU)
            cachedImage.updateAccessTime();

            logger.debug("캐시된 이미지 반환 - JobID: {}, 캐시 hit", jobId);
            return cachedImage.getImage();

        } catch (Exception e) {
            logger.error("이미지 캐시 조회 실패 - JobID: {}", jobId, e);
            return null;
        }
    }

    /**
     * 특정 이미지를 캐시에서 제거
     *
     * @param jobId 작업 ID
     */
    public void evictImage(String jobId) {
        if (jobId == null) {
            return;
        }

        try {
            CachedImage removed = imageCache.remove(jobId);
            if (removed != null) {
                logger.debug("이미지 캐시 제거 완료 - JobID: {}", jobId);
            }
        } catch (Exception e) {
            logger.error("이미지 캐시 제거 실패 - JobID: {}", jobId, e);
        }
    }

    /**
     * 만료된 캐시 엔트리 정리
     */
    private void evictExpiredEntries() {
        try {
            long currentTime = System.currentTimeMillis();
            long ttlMs = cacheTtlMinutes * 60 * 1000;
            final AtomicInteger removedCount = new AtomicInteger(0);

            imageCache.entrySet().removeIf(entry -> {
                long cacheAge = currentTime - entry.getValue().getCachedTime();
                if (cacheAge > ttlMs) {
                    removedCount.incrementAndGet();
                    return true;
                }
                return false;
            });

            if (removedCount.get() > 0) {
                logger.debug("만료된 캐시 엔트리 정리 완료 - 제거된 항목: {}개, 남은 항목: {}개",
                           removedCount.get(), imageCache.size());
            }

        } catch (Exception e) {
            logger.error("만료된 캐시 엔트리 정리 실패", e);
        }
    }

    /**
     * 캐시 크기 초과 시 오래된 엔트리 정리 (LRU)
     */
    private void evictOldestEntries() {
        try {
            if (imageCache.size() <= maxCacheSize) {
                return;
            }

            // 접근 시간 기준으로 정렬하여 오래된 것부터 제거
            int targetRemovalCount = imageCache.size() - maxCacheSize;

            imageCache.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(
                    e1.getValue().getLastAccessTime(),
                    e2.getValue().getLastAccessTime()))
                .limit(targetRemovalCount)
                .forEach(entry -> imageCache.remove(entry.getKey()));

            logger.info("LRU 캐시 정리 완료 - 제거된 항목: {}개, 현재 크기: {}/{}",
                       targetRemovalCount, imageCache.size(), maxCacheSize);

        } catch (Exception e) {
            logger.error("LRU 캐시 정리 실패", e);
        }
    }

    /**
     * 캐시 통계 정보 반환
     */
    public CacheStats getCacheStats() {
        return new CacheStats(
            imageCache.size(),
            maxCacheSize,
            cacheTtlMinutes
        );
    }

    /**
     * 캐시 초기화 (테스트용)
     */
    public void clearCache() {
        int size = imageCache.size();
        imageCache.clear();
        logger.info("캐시 초기화 완료 - 제거된 항목: {}개", size);
    }

    /**
     * 서비스 종료 시 스케줄러 정리
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        clearCache();
        logger.info("이미지 캐시 서비스 종료 완료");
    }

    /**
     * 캐시된 이미지 데이터 클래스
     */
    private static class CachedImage {
        private final BufferedImage image;
        private final long cachedTime;
        private volatile long lastAccessTime;

        public CachedImage(BufferedImage image, long cachedTime) {
            this.image = image;
            this.cachedTime = cachedTime;
            this.lastAccessTime = cachedTime;
        }

        public BufferedImage getImage() {
            return image;
        }

        public long getCachedTime() {
            return cachedTime;
        }

        public long getLastAccessTime() {
            return lastAccessTime;
        }

        public void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }

    /**
     * 캐시 통계 데이터 클래스
     */
    public static class CacheStats {
        private final int currentSize;
        private final int maxSize;
        private final int ttlMinutes;

        public CacheStats(int currentSize, int maxSize, int ttlMinutes) {
            this.currentSize = currentSize;
            this.maxSize = maxSize;
            this.ttlMinutes = ttlMinutes;
        }

        public int getCurrentSize() { return currentSize; }
        public int getMaxSize() { return maxSize; }
        public int getTtlMinutes() { return ttlMinutes; }
        public double getUsagePercentage() {
            return maxSize > 0 ? (double) currentSize / maxSize * 100 : 0;
        }

        @Override
        public String toString() {
            return String.format("CacheStats{size=%d/%d (%.1f%%), ttl=%d분}",
                               currentSize, maxSize, getUsagePercentage(), ttlMinutes);
        }
    }
}