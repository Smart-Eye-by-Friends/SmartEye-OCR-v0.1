/**
 * PerformanceGuard - 무한 루프 방지 및 성능 모니터링 컴포넌트
 * React 18+ 최적화된 성능 가드 시스템
 */

import React, { useEffect, useRef, useState, useCallback, useMemo } from 'react';
import PropTypes from 'prop-types';

// 성능 임계값 설정
const PERFORMANCE_THRESHOLDS = {
  RENDER_COUNT: 50,        // 50회 이상 렌더링 시 경고
  RENDER_FREQUENCY: 100,   // 100ms 내 재렌더링 시 경고
  MEMORY_USAGE: 100 * 1024 * 1024, // 100MB 이상 메모리 사용 시 경고 (추정)
  EFFECT_COUNT: 20,        // 20회 이상 useEffect 실행 시 경고
  UPDATE_INTERVAL: 50      // 50ms 내 연속 업데이트 시 경고
};

// 성능 상태 타입
const PERFORMANCE_STATES = {
  NORMAL: 'normal',
  WARNING: 'warning',
  CRITICAL: 'critical',
  BLOCKED: 'blocked'
};

/**
 * 성능 가드 훅
 * @param {string} componentName - 컴포넌트 이름
 * @param {Object} options - 설정 옵션
 * @returns {Object} 성능 모니터링 유틸리티
 */
export const usePerformanceGuard = (componentName, options = {}) => {
  const {
    enabled = true,
    thresholds = PERFORMANCE_THRESHOLDS,
    onWarning = null,
    onCritical = null,
    onBlock = null,
    enableLogging = process.env.NODE_ENV === 'development'
  } = options;

  // 성능 추적 데이터
  const metricsRef = useRef({
    renderCount: 0,
    effectCount: 0,
    lastRenderTime: Date.now(),
    renderTimes: [],
    effectTimes: [],
    isBlocked: false,
    blockReason: null,
    warningCount: 0,
    criticalCount: 0
  });

  const [performanceState, setPerformanceState] = useState(PERFORMANCE_STATES.NORMAL);
  const [alerts, setAlerts] = useState([]);

  // 성능 메트릭 업데이트
  const updateMetrics = useCallback((type, data = {}) => {
    if (!enabled) return;

    const now = Date.now();
    const metrics = metricsRef.current;

    switch (type) {
      case 'render':
        metrics.renderCount++;
        metrics.renderTimes.push(now);

        // 최근 10개 렌더링 시간만 유지 (메모리 효율성)
        if (metrics.renderTimes.length > 10) {
          metrics.renderTimes.shift();
        }

        // 빈번한 렌더링 감지
        const recentRenders = metrics.renderTimes.filter(
          time => now - time < thresholds.RENDER_FREQUENCY
        );

        if (recentRenders.length > 5) {
          handlePerformanceIssue('frequent_rendering', {
            count: recentRenders.length,
            timespan: thresholds.RENDER_FREQUENCY
          });
        }

        // 총 렌더링 횟수 확인
        if (metrics.renderCount > thresholds.RENDER_COUNT) {
          handlePerformanceIssue('excessive_rendering', {
            count: metrics.renderCount,
            threshold: thresholds.RENDER_COUNT
          });
        }

        metrics.lastRenderTime = now;
        break;

      case 'effect':
        metrics.effectCount++;
        metrics.effectTimes.push(now);

        // 최근 이펙트 실행 기록만 유지
        if (metrics.effectTimes.length > 20) {
          metrics.effectTimes.shift();
        }

        // 과도한 이펙트 실행 감지
        if (metrics.effectCount > thresholds.EFFECT_COUNT) {
          handlePerformanceIssue('excessive_effects', {
            count: metrics.effectCount,
            threshold: thresholds.EFFECT_COUNT
          });
        }
        break;

      case 'update':
        // 연속적인 업데이트 감지
        if (data.lastUpdate && now - data.lastUpdate < thresholds.UPDATE_INTERVAL) {
          handlePerformanceIssue('rapid_updates', {
            interval: now - data.lastUpdate,
            threshold: thresholds.UPDATE_INTERVAL
          });
        }
        break;

      default:
        break;
    }
  }, [enabled, thresholds]);

  // 성능 문제 처리
  const handlePerformanceIssue = useCallback((issueType, details) => {
    const metrics = metricsRef.current;
    const now = Date.now();

    const alert = {
      id: `${issueType}_${now}`,
      type: issueType,
      timestamp: now,
      componentName,
      details,
      severity: determineSeverity(issueType, details)
    };

    setAlerts(prev => [...prev.slice(-4), alert]); // 최대 5개 알림 유지

    // 심각도에 따른 처리
    switch (alert.severity) {
      case PERFORMANCE_STATES.WARNING:
        metrics.warningCount++;
        if (enableLogging) {
          console.warn(`🚨 [${componentName}] Performance Warning:`, alert);
        }
        if (onWarning) onWarning(alert);
        setPerformanceState(PERFORMANCE_STATES.WARNING);
        break;

      case PERFORMANCE_STATES.CRITICAL:
        metrics.criticalCount++;
        if (enableLogging) {
          console.error(`🔥 [${componentName}] Performance Critical:`, alert);
        }
        if (onCritical) onCritical(alert);
        setPerformanceState(PERFORMANCE_STATES.CRITICAL);
        break;

      case PERFORMANCE_STATES.BLOCKED:
        metrics.isBlocked = true;
        metrics.blockReason = alert;
        if (enableLogging) {
          console.error(`🚫 [${componentName}] Performance Blocked:`, alert);
        }
        if (onBlock) onBlock(alert);
        setPerformanceState(PERFORMANCE_STATES.BLOCKED);
        break;

      default:
        break;
    }
  }, [componentName, enableLogging, onWarning, onCritical, onBlock]);

  // 심각도 결정
  const determineSeverity = (issueType, details) => {
    switch (issueType) {
      case 'frequent_rendering':
        return details.count > 10 ? PERFORMANCE_STATES.BLOCKED : PERFORMANCE_STATES.CRITICAL;

      case 'excessive_rendering':
        return details.count > 100 ? PERFORMANCE_STATES.BLOCKED : PERFORMANCE_STATES.WARNING;

      case 'excessive_effects':
        return details.count > 50 ? PERFORMANCE_STATES.CRITICAL : PERFORMANCE_STATES.WARNING;

      case 'rapid_updates':
        return details.interval < 10 ? PERFORMANCE_STATES.BLOCKED : PERFORMANCE_STATES.WARNING;

      default:
        return PERFORMANCE_STATES.WARNING;
    }
  };

  // 렌더링 추적
  const trackRender = useCallback(() => {
    updateMetrics('render');
  }, [updateMetrics]);

  // 이펙트 실행 추적
  const trackEffect = useCallback(() => {
    updateMetrics('effect');
  }, [updateMetrics]);

  // 업데이트 추적
  const trackUpdate = useCallback((lastUpdate) => {
    updateMetrics('update', { lastUpdate });
  }, [updateMetrics]);

  // 성능 통계 계산
  const performanceStats = useMemo(() => {
    const metrics = metricsRef.current;
    const now = Date.now();

    // 최근 렌더링 주기 계산
    const recentRenderTimes = metrics.renderTimes.filter(time => now - time < 5000); // 최근 5초
    const averageRenderInterval = recentRenderTimes.length > 1
      ? (now - recentRenderTimes[0]) / (recentRenderTimes.length - 1)
      : 0;

    return {
      renderCount: metrics.renderCount,
      effectCount: metrics.effectCount,
      warningCount: metrics.warningCount,
      criticalCount: metrics.criticalCount,
      averageRenderInterval,
      isBlocked: metrics.isBlocked,
      blockReason: metrics.blockReason,
      recentAlerts: alerts.slice(-3),
      state: performanceState
    };
  }, [alerts, performanceState]);

  // 성능 리셋
  const resetMetrics = useCallback(() => {
    metricsRef.current = {
      renderCount: 0,
      effectCount: 0,
      lastRenderTime: Date.now(),
      renderTimes: [],
      effectTimes: [],
      isBlocked: false,
      blockReason: null,
      warningCount: 0,
      criticalCount: 0
    };
    setPerformanceState(PERFORMANCE_STATES.NORMAL);
    setAlerts([]);
  }, []);

  // 블록 해제
  const unblock = useCallback(() => {
    metricsRef.current.isBlocked = false;
    metricsRef.current.blockReason = null;
    setPerformanceState(PERFORMANCE_STATES.NORMAL);
  }, []);

  // 컴포넌트 언마운트 시 정리
  useEffect(() => {
    return () => {
      if (enableLogging && metricsRef.current.renderCount > 0) {
        console.debug(`📊 [${componentName}] Performance Summary:`, {
          totalRenders: metricsRef.current.renderCount,
          totalEffects: metricsRef.current.effectCount,
          warnings: metricsRef.current.warningCount,
          criticals: metricsRef.current.criticalCount
        });
      }
    };
  }, [componentName, enableLogging]);

  return {
    // 추적 함수들
    trackRender,
    trackEffect,
    trackUpdate,

    // 상태 정보
    performanceState,
    performanceStats,
    alerts,

    // 제어 함수들
    resetMetrics,
    unblock,

    // 상태 확인
    isBlocked: metricsRef.current.isBlocked,
    hasWarnings: alerts.some(alert => alert.severity === PERFORMANCE_STATES.WARNING),
    hasCriticals: alerts.some(alert => alert.severity === PERFORMANCE_STATES.CRITICAL)
  };
};

/**
 * PerformanceGuard HOC (Higher-Order Component)
 * 컴포넌트를 성능 모니터링으로 감쌉니다
 */
export const withPerformanceGuard = (WrappedComponent, guardOptions = {}) => {
  const GuardedComponent = React.forwardRef((props, ref) => {
    const componentName = WrappedComponent.displayName || WrappedComponent.name || 'Unknown';

    const {
      trackRender,
      performanceState,
      performanceStats,
      isBlocked,
      unblock
    } = usePerformanceGuard(componentName, guardOptions);

    // 렌더링마다 추적
    trackRender();

    // 블록된 경우 대체 UI 표시
    if (isBlocked && guardOptions.showBlockedUI !== false) {
      return (
        <PerformanceBlockedUI
          componentName={componentName}
          performanceStats={performanceStats}
          onUnblock={unblock}
        />
      );
    }

    // 경고 상태에서 알림 표시
    const showWarning = performanceState !== PERFORMANCE_STATES.NORMAL &&
                       guardOptions.showWarnings !== false;

    return (
      <div className="performance-guarded-component">
        {showWarning && (
          <PerformanceWarningBanner
            performanceState={performanceState}
            componentName={componentName}
            stats={performanceStats}
          />
        )}
        <WrappedComponent ref={ref} {...props} />
      </div>
    );
  });

  GuardedComponent.displayName = `withPerformanceGuard(${WrappedComponent.displayName || WrappedComponent.name})`;

  return GuardedComponent;
};

/**
 * 성능 블록 상태 UI
 */
const PerformanceBlockedUI = ({ componentName, performanceStats, onUnblock }) => (
  <div className="performance-blocked-ui" style={{
    padding: '20px',
    backgroundColor: '#fff3cd',
    border: '1px solid #ffeaa7',
    borderRadius: '8px',
    textAlign: 'center'
  }}>
    <h3>🚫 성능 문제로 컴포넌트 차단됨</h3>
    <p>
      <strong>{componentName}</strong> 컴포넌트에서 성능 문제가 감지되어 실행이 차단되었습니다.
    </p>
    <div style={{ margin: '10px 0', fontSize: '14px', color: '#666' }}>
      <div>렌더링 횟수: {performanceStats.renderCount}</div>
      <div>경고 횟수: {performanceStats.warningCount}</div>
      <div>심각 문제: {performanceStats.criticalCount}</div>
      {performanceStats.blockReason && (
        <div>차단 사유: {performanceStats.blockReason.type}</div>
      )}
    </div>
    <button
      onClick={onUnblock}
      style={{
        padding: '8px 16px',
        backgroundColor: '#007bff',
        color: 'white',
        border: 'none',
        borderRadius: '4px',
        cursor: 'pointer'
      }}
    >
      차단 해제 (주의: 성능 문제가 지속될 수 있음)
    </button>
  </div>
);

/**
 * 성능 경고 배너
 */
const PerformanceWarningBanner = ({ performanceState, componentName, stats }) => {
  const [isVisible, setIsVisible] = useState(true);

  if (!isVisible) return null;

  const getStateColor = () => {
    switch (performanceState) {
      case PERFORMANCE_STATES.WARNING: return '#fff3cd';
      case PERFORMANCE_STATES.CRITICAL: return '#f8d7da';
      default: return '#d1ecf1';
    }
  };

  const getStateIcon = () => {
    switch (performanceState) {
      case PERFORMANCE_STATES.WARNING: return '⚠️';
      case PERFORMANCE_STATES.CRITICAL: return '🔥';
      default: return '🔍';
    }
  };

  return (
    <div
      className="performance-warning-banner"
      style={{
        padding: '8px 12px',
        backgroundColor: getStateColor(),
        border: '1px solid #dee2e6',
        borderRadius: '4px',
        marginBottom: '8px',
        fontSize: '12px',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center'
      }}
    >
      <span>
        {getStateIcon()} {componentName}: {performanceState}
        (렌더링: {stats.renderCount}, 경고: {stats.warningCount})
      </span>
      <button
        onClick={() => setIsVisible(false)}
        style={{
          background: 'none',
          border: 'none',
          cursor: 'pointer',
          padding: '0 4px'
        }}
      >
        ✕
      </button>
    </div>
  );
};

// PropTypes
PerformanceBlockedUI.propTypes = {
  componentName: PropTypes.string.isRequired,
  performanceStats: PropTypes.object.isRequired,
  onUnblock: PropTypes.func.isRequired
};

PerformanceWarningBanner.propTypes = {
  performanceState: PropTypes.string.isRequired,
  componentName: PropTypes.string.isRequired,
  stats: PropTypes.object.isRequired
};

export default {
  usePerformanceGuard,
  withPerformanceGuard,
  PerformanceBlockedUI,
  PerformanceWarningBanner,
  PERFORMANCE_STATES,
  PERFORMANCE_THRESHOLDS
};