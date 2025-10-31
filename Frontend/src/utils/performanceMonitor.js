/**
 * 성능 모니터링 유틸리티
 * React 컴포넌트 렌더링 성능과 메모리 사용량을 추적
 */

class PerformanceMonitor {
  constructor() {
    this.measurements = new Map();
    this.renderCounts = new Map();
    this.startTime = performance.now();
    this.isEnabled = process.env.NODE_ENV === 'development';
  }

  /**
   * 컴포넌트 렌더링 시작 측정
   * @param {string} componentName - 컴포넌트 이름
   * @param {Object} props - 컴포넌트 props (선택사항)
   */
  startMeasure(componentName, props = {}) {
    if (!this.isEnabled) return;

    const measureId = `${componentName}_${Date.now()}`;
    this.measurements.set(measureId, {
      componentName,
      startTime: performance.now(),
      props: this.sanitizeProps(props)
    });

    // 렌더링 횟수 추적
    const currentCount = this.renderCounts.get(componentName) || 0;
    this.renderCounts.set(componentName, currentCount + 1);

    return measureId;
  }

  /**
   * 컴포넌트 렌더링 완료 측정
   * @param {string} measureId - 측정 ID
   * @param {Object} additionalData - 추가 데이터
   */
  endMeasure(measureId, additionalData = {}) {
    if (!this.isEnabled || !measureId) return;

    const measurement = this.measurements.get(measureId);
    if (!measurement) return;

    const endTime = performance.now();
    const duration = endTime - measurement.startTime;

    const result = {
      ...measurement,
      endTime,
      duration,
      ...additionalData
    };

    // 성능 경고 (5초 이상 소요 시)
    if (duration > 5000) {
      console.warn(`🐌 느린 렌더링 감지:`, {
        component: measurement.componentName,
        duration: `${duration.toFixed(2)}ms`,
        renderCount: this.renderCounts.get(measurement.componentName)
      });
    }

    // 측정 완료된 항목 제거
    this.measurements.delete(measureId);

    return result;
  }

  /**
   * 컴포넌트 렌더링 통계 가져오기
   * @param {string} componentName - 컴포넌트 이름 (선택사항)
   * @returns {Object} 렌더링 통계
   */
  getRenderStats(componentName) {
    if (!this.isEnabled) return null;

    if (componentName) {
      return {
        component: componentName,
        renderCount: this.renderCounts.get(componentName) || 0,
        averageRenderTime: this.getAverageRenderTime(componentName)
      };
    }

    const stats = {};
    for (const [component, count] of this.renderCounts.entries()) {
      stats[component] = {
        renderCount: count,
        averageRenderTime: this.getAverageRenderTime(component)
      };
    }

    return stats;
  }

  /**
   * 무한 렌더링 감지
   * @param {string} componentName - 컴포넌트 이름
   * @param {number} threshold - 경고 임계값 (기본: 10회/초)
   * @returns {boolean} 무한 렌더링 여부
   */
  detectInfiniteRendering(componentName, threshold = 10) {
    if (!this.isEnabled) return false;

    const renderCount = this.renderCounts.get(componentName) || 0;
    const elapsedTime = (performance.now() - this.startTime) / 1000; // 초 단위

    if (elapsedTime > 0) {
      const renderRate = renderCount / elapsedTime;

      if (renderRate > threshold) {
        console.error(`🔄 무한 렌더링 감지:`, {
          component: componentName,
          renderCount,
          renderRate: `${renderRate.toFixed(2)}/sec`,
          threshold: `${threshold}/sec`
        });
        return true;
      }
    }

    return false;
  }

  /**
   * 메모리 사용량 측정
   * @returns {Object} 메모리 사용량 정보
   */
  getMemoryUsage() {
    if (!this.isEnabled || !performance.memory) return null;

    return {
      usedJSHeapSize: Math.round(performance.memory.usedJSHeapSize / 1048576), // MB
      totalJSHeapSize: Math.round(performance.memory.totalJSHeapSize / 1048576), // MB
      jsHeapSizeLimit: Math.round(performance.memory.jsHeapSizeLimit / 1048576), // MB
      timestamp: new Date().toISOString()
    };
  }

  /**
   * 성능 리포트 생성
   * @returns {Object} 종합 성능 리포트
   */
  generateReport() {
    if (!this.isEnabled) return null;

    const totalRuntime = (performance.now() - this.startTime) / 1000;

    return {
      runtime: `${totalRuntime.toFixed(2)}초`,
      renderStats: this.getRenderStats(),
      memoryUsage: this.getMemoryUsage(),
      activeMeasurements: this.measurements.size,
      timestamp: new Date().toISOString(),
      recommendations: this.generateRecommendations()
    };
  }

  /**
   * 성능 최적화 권장사항 생성
   * @returns {Array} 권장사항 목록
   */
  generateRecommendations() {
    const recommendations = [];

    // 렌더링 횟수가 많은 컴포넌트 식별
    for (const [component, count] of this.renderCounts.entries()) {
      if (count > 50) {
        recommendations.push({
          type: 'high_render_count',
          component,
          renderCount: count,
          suggestion: `${component} 컴포넌트의 렌더링 횟수가 ${count}회입니다. React.memo나 useMemo 사용을 고려하세요.`
        });
      }
    }

    // 메모리 사용량 확인
    const memory = this.getMemoryUsage();
    if (memory && memory.usedJSHeapSize > 100) {
      recommendations.push({
        type: 'high_memory_usage',
        memoryUsage: `${memory.usedJSHeapSize}MB`,
        suggestion: '메모리 사용량이 높습니다. 불필요한 상태나 참조를 정리하세요.'
      });
    }

    return recommendations;
  }

  /**
   * Props 정제 (민감한 정보 제거)
   * @param {Object} props - 원본 props
   * @returns {Object} 정제된 props
   */
  sanitizeProps(props) {
    if (!props || typeof props !== 'object') return {};

    const sanitized = {};
    for (const [key, value] of Object.entries(props)) {
      // 함수와 민감한 정보는 타입만 기록
      if (typeof value === 'function') {
        sanitized[key] = '[Function]';
      } else if (key.toLowerCase().includes('password') || key.toLowerCase().includes('key')) {
        sanitized[key] = '[Hidden]';
      } else if (typeof value === 'object' && value !== null) {
        sanitized[key] = '[Object]';
      } else {
        sanitized[key] = value;
      }
    }

    return sanitized;
  }

  /**
   * 평균 렌더링 시간 계산 (추정)
   * @param {string} componentName - 컴포넌트 이름
   * @returns {string} 평균 렌더링 시간
   */
  getAverageRenderTime(componentName) {
    // 실제 구현에서는 더 정확한 측정이 필요
    const renderCount = this.renderCounts.get(componentName) || 0;
    if (renderCount === 0) return 'N/A';

    // 단순 추정 (실제로는 각 렌더링 시간을 개별 측정해야 함)
    const estimatedTime = renderCount > 10 ? '~5ms' : '~2ms';
    return estimatedTime;
  }

  /**
   * 모니터링 초기화
   */
  reset() {
    this.measurements.clear();
    this.renderCounts.clear();
    this.startTime = performance.now();
  }

  /**
   * 성능 로그 출력
   */
  logPerformance() {
    if (!this.isEnabled) return;

    console.group('📊 SmartEye 성능 리포트');
    const report = this.generateReport();

    console.log('⏱️ 런타임:', report.runtime);
    console.log('🔄 렌더링 통계:', report.renderStats);
    console.log('💾 메모리 사용량:', report.memoryUsage);

    if (report.recommendations.length > 0) {
      console.warn('💡 최적화 권장사항:');
      report.recommendations.forEach((rec, index) => {
        console.warn(`${index + 1}. ${rec.suggestion}`);
      });
    }

    console.groupEnd();
  }
}

// 싱글톤 인스턴스 생성
const performanceMonitor = new PerformanceMonitor();

// React 컴포넌트에서 사용할 수 있는 훅
export const usePerformanceMonitor = (componentName) => {
  if (process.env.NODE_ENV !== 'development') {
    return {
      startMeasure: () => null,
      endMeasure: () => null,
      detectInfiniteRendering: () => false
    };
  }

  return {
    startMeasure: (props) => performanceMonitor.startMeasure(componentName, props),
    endMeasure: (measureId, data) => performanceMonitor.endMeasure(measureId, data),
    detectInfiniteRendering: (threshold) => performanceMonitor.detectInfiniteRendering(componentName, threshold)
  };
};

export default performanceMonitor;