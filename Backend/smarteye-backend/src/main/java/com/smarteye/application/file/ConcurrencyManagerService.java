package com.smarteye.application.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import com.smarteye.application.analysis.AnalysisJobService;
import com.smarteye.application.user.UserService;
import com.smarteye.domain.document.DocumentPage;
import com.smarteye.infrastructure.external.*;
import com.smarteye.application.file.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 동시성 관리 서비스
 * CIM 서비스의 동시 요청 처리를 위한 락 메커니즘과 멱등성 보장
 */
@Service
public class ConcurrencyManagerService {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrencyManagerService.class);

    // AnalysisJob ID별 락 관리를 위한 ConcurrentHashMap
    private final ConcurrentHashMap<Long, ReentrantLock> analysisJobLocks = new ConcurrentHashMap<>();

    // 락 획득 타임아웃 (30초)
    private static final long LOCK_TIMEOUT_SECONDS = 30;

    /**
     * AnalysisJob ID에 대한 분산 락을 사용하여 동시성 보장
     *
     * @param analysisJobId 분석 작업 ID
     * @param operation 실행할 작업
     * @param operationName 작업 이름 (로깅용)
     * @return 작업 결과
     * @throws RuntimeException 락 획득 실패 또는 작업 실행 실패 시
     */
    public <T> T executeWithLock(Long analysisJobId, Supplier<T> operation, String operationName) {
        ReentrantLock lock = analysisJobLocks.computeIfAbsent(analysisJobId, k -> new ReentrantLock(true));

        logger.debug("📊 동시성 제어 시작 - AnalysisJob ID: {}, 작업: {}", analysisJobId, operationName);

        try {
            // 락 획득 시도 (타임아웃 적용)
            if (!lock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.error("⏰ 락 획득 타임아웃 - AnalysisJob ID: {}, 작업: {}", analysisJobId, operationName);
                throw new RuntimeException("동시성 제어 락 획득 타임아웃: " + operationName);
            }

            logger.debug("🔒 락 획득 성공 - AnalysisJob ID: {}, 작업: {}", analysisJobId, operationName);

            // 실제 작업 실행
            T result = operation.get();

            logger.debug("✅ 작업 완료 - AnalysisJob ID: {}, 작업: {}", analysisJobId, operationName);
            return result;

        } catch (InterruptedException e) {
            logger.error("🚫 락 대기 중 인터럽트 - AnalysisJob ID: {}, 작업: {}", analysisJobId, operationName, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("동시성 제어 중 인터럽트 발생: " + operationName, e);

        } catch (Exception e) {
            logger.error("❌ 작업 실행 실패 - AnalysisJob ID: {}, 작업: {}", analysisJobId, operationName, e);
            throw new RuntimeException("동시성 제어 작업 실행 실패: " + operationName, e);

        } finally {
            // 락 해제
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                logger.debug("🔓 락 해제 완료 - AnalysisJob ID: {}, 작업: {}", analysisJobId, operationName);
            }

            // 메모리 누수 방지: 락이 더 이상 사용되지 않으면 제거
            if (!lock.hasQueuedThreads() && !lock.isLocked()) {
                analysisJobLocks.remove(analysisJobId);
                logger.debug("🗑️ 미사용 락 제거 - AnalysisJob ID: {}", analysisJobId);
            }
        }
    }

    /**
     * 멱등성을 보장하는 데이터베이스 작업 실행
     *
     * @param operation 실행할 작업
     * @param fallbackOperation 중복 키 오류 발생 시 실행할 대체 작업
     * @param operationName 작업 이름 (로깅용)
     * @return 작업 결과
     */
    @Transactional(
        isolation = Isolation.READ_COMMITTED,
        propagation = Propagation.REQUIRES_NEW,
        rollbackFor = Exception.class
    )
    public <T> T executeIdempotentDbOperation(
            Supplier<T> operation,
            Supplier<T> fallbackOperation,
            String operationName) {

        try {
            logger.debug("🔄 멱등성 보장 작업 시작: {}", operationName);

            T result = operation.get();

            logger.debug("✅ 멱등성 보장 작업 완료: {}", operationName);
            return result;

        } catch (DataIntegrityViolationException e) {
            logger.warn("🔁 중복 키 감지, 대체 작업 실행: {}", operationName);

            if (fallbackOperation != null) {
                try {
                    T fallbackResult = fallbackOperation.get();
                    logger.info("✅ 대체 작업 완료: {}", operationName);
                    return fallbackResult;
                } catch (Exception fallbackException) {
                    logger.error("❌ 대체 작업 실패: {}", operationName, fallbackException);
                    throw new RuntimeException("대체 작업 실패: " + operationName, fallbackException);
                }
            } else {
                logger.error("❌ 대체 작업이 정의되지 않음: {}", operationName);
                throw new RuntimeException("중복 키 오류 발생, 대체 작업 없음: " + operationName, e);
            }

        } catch (Exception e) {
            logger.error("❌ 멱등성 보장 작업 실패: {}", operationName, e);
            throw new RuntimeException("멱등성 보장 작업 실패: " + operationName, e);
        }
    }

    /**
     * 분석 작업별 동시 실행 통계 조회
     *
     * @return 현재 진행 중인 락 수
     */
    public int getActiveLockCount() {
        return (int) analysisJobLocks.values().stream()
                .filter(ReentrantLock::isLocked)
                .count();
    }

    /**
     * 대기 중인 스레드 수 조회
     *
     * @return 전체 대기 중인 스레드 수
     */
    public int getQueuedThreadCount() {
        return analysisJobLocks.values().stream()
                .mapToInt(ReentrantLock::getQueueLength)
                .sum();
    }

    /**
     * 동시성 관리 통계 로깅
     */
    public void logConcurrencyStats() {
        int activeLocks = getActiveLockCount();
        int queuedThreads = getQueuedThreadCount();
        int totalLocks = analysisJobLocks.size();

        logger.info("📊 동시성 관리 통계 - 활성 락: {}, 대기 스레드: {}, 전체 락: {}",
                   activeLocks, queuedThreads, totalLocks);
    }

    /**
     * 시스템 종료 시 모든 락 정리
     */
    @org.springframework.context.event.EventListener(org.springframework.context.event.ContextClosedEvent.class)
    public void cleanup() {
        logger.info("🧹 동시성 관리 서비스 정리 시작 - 총 락 수: {}", analysisJobLocks.size());

        analysisJobLocks.clear();

        logger.info("✅ 동시성 관리 서비스 정리 완료");
    }
}