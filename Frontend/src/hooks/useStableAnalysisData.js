/**
 * useStableAnalysisData - 분석 데이터 참조 안정화 및 최적화 훅
 * 불안정한 참조로 인한 무한 렌더링 방지 및 성능 최적화
 */

import { useMemo, useRef, useCallback } from 'react';
import { normalizeAnalysisResults } from '../utils/dataUtils';

// 안정적인 해시 생성 함수 (순환 참조 방지)
const createStableHash = (data, maxDepth = 3) => {
  const seen = new WeakSet();

  const serialize = (obj, depth = 0) => {
    if (depth > maxDepth) return '[MAX_DEPTH]';
    if (obj === null || obj === undefined) return String(obj);
    if (typeof obj !== 'object') return String(obj);
    if (seen.has(obj)) return '[CIRCULAR]';

    seen.add(obj);

    if (Array.isArray(obj)) {
      const items = obj.slice(0, 10).map(item => serialize(item, depth + 1));
      return `[${items.join(',')}]${obj.length > 10 ? '...' : ''}`;
    }

    const keys = Object.keys(obj).sort().slice(0, 20);
    const pairs = keys.map(key => `${key}:${serialize(obj[key], depth + 1)}`);
    return `{${pairs.join(',')}}${Object.keys(obj).length > 20 ? '...' : ''}`;
  };

  try {
    return serialize(data);
  } catch (error) {
    console.warn('Hash 생성 실패:', error);
    return `error_${Date.now()}_${Math.random()}`;
  }
};

// 데이터 품질 검증 함수
const validateDataQuality = (data) => {
  const issues = [];

  if (!data) {
    issues.push('데이터가 null 또는 undefined');
    return { isValid: false, issues };
  }

  // OCR 결과 검증
  if (data.ocrResults) {
    if (!Array.isArray(data.ocrResults)) {
      issues.push('ocrResults가 배열이 아님');
    } else {
      const invalidOcr = data.ocrResults.filter(item => !item || typeof item.text !== 'string');
      if (invalidOcr.length > 0) {
        issues.push(`유효하지 않은 OCR 결과 ${invalidOcr.length}개`);
      }
    }
  }

  // AI 결과 검증
  if (data.aiResults) {
    if (!Array.isArray(data.aiResults)) {
      issues.push('aiResults가 배열이 아님');
    } else {
      const invalidAi = data.aiResults.filter(item =>
        !item || (typeof item.description !== 'string' && typeof item.text !== 'string')
      );
      if (invalidAi.length > 0) {
        issues.push(`유효하지 않은 AI 결과 ${invalidAi.length}개`);
      }
    }
  }

  return {
    isValid: issues.length === 0,
    issues,
    quality: issues.length === 0 ? 'good' : issues.length <= 2 ? 'acceptable' : 'poor'
  };
};

/**
 * 분석 데이터의 안정적인 참조와 최적화된 처리를 제공하는 훅
 * @param {Object} analysisResults - 원본 분석 결과
 * @param {Object} options - 설정 옵션
 * @returns {Object} 안정화된 데이터와 유틸리티 함수들
 */
export const useStableAnalysisData = (analysisResults, options = {}) => {
  const {
    enableCaching = true,
    enableValidation = true,
    enableLogging = process.env.NODE_ENV === 'development',
    maxCacheSize = 10
  } = options;

  // 캐시 및 참조 저장소
  const cacheRef = useRef(new Map());
  const lastHashRef = useRef(null);
  const processingCountRef = useRef(0);

  // 캐시 크기 관리
  const manageCacheSize = useCallback(() => {
    if (cacheRef.current.size > maxCacheSize) {
      const entries = Array.from(cacheRef.current.entries());
      const oldestEntries = entries.slice(0, Math.floor(maxCacheSize / 2));
      oldestEntries.forEach(([key]) => cacheRef.current.delete(key));

      if (enableLogging) {
        console.debug('🧹 캐시 정리 완료:', {
          removed: oldestEntries.length,
          remaining: cacheRef.current.size
        });
      }
    }
  }, [maxCacheSize, enableLogging]);

  // 데이터 해시 생성 (안정적인 메모이제이션)
  const dataHash = useMemo(() => {
    if (!analysisResults) return 'empty';

    processingCountRef.current += 1;
    return createStableHash(analysisResults);
  }, [analysisResults]);

  // 정규화된 데이터 (캐싱과 함께)
  const normalizedData = useMemo(() => {
    // 캐시 확인
    if (enableCaching && cacheRef.current.has(dataHash)) {
      if (enableLogging) {
        console.debug('📦 정규화 캐시 히트:', dataHash.substring(0, 20));
      }
      return cacheRef.current.get(dataHash);
    }

    // 데이터 정규화 수행
    const startTime = performance.now();
    let result;

    try {
      result = normalizeAnalysisResults(analysisResults);

      // 데이터 품질 검증
      if (enableValidation) {
        const validation = validateDataQuality(result);
        if (!validation.isValid && enableLogging) {
          console.warn('📊 데이터 품질 문제:', validation.issues);
        }
        result._quality = validation;
      }

    } catch (error) {
      console.error('정규화 실패:', error);
      result = {
        ocrResults: [],
        aiResults: [],
        stats: {},
        cimData: null,
        _error: error.message
      };
    }

    const processingTime = performance.now() - startTime;

    // 결과에 메타데이터 추가
    result._meta = {
      hash: dataHash,
      processingTime,
      timestamp: Date.now(),
      processingCount: processingCountRef.current
    };

    // 캐시 저장
    if (enableCaching) {
      cacheRef.current.set(dataHash, result);
      manageCacheSize();
    }

    if (enableLogging) {
      console.debug('🔄 데이터 정규화 완료:', {
        hash: dataHash.substring(0, 20),
        processingTime: processingTime.toFixed(2) + 'ms',
        ocrCount: result.ocrResults?.length || 0,
        aiCount: result.aiResults?.length || 0,
        quality: result._quality?.quality
      });
    }

    lastHashRef.current = dataHash;
    return result;
  }, [dataHash, enableCaching, enableValidation, enableLogging, manageCacheSize, analysisResults]);

  // 데이터 가용성 체크 (안정적인 메모이제이션)
  const availability = useMemo(() => {
    if (!normalizedData) {
      return {
        hasData: false,
        hasOCRData: false,
        hasAIData: false,
        hasCIMData: false,
        hasFormattedText: false,
        isEmpty: true
      };
    }

    const hasOCRData = Array.isArray(normalizedData.ocrResults) && normalizedData.ocrResults.length > 0;
    const hasAIData = Array.isArray(normalizedData.aiResults) && normalizedData.aiResults.length > 0;
    const hasCIMData = normalizedData.cimData != null;
    const hasFormattedText = Boolean(normalizedData.formattedText?.trim());

    return {
      hasData: hasOCRData || hasAIData || hasCIMData || hasFormattedText,
      hasOCRData,
      hasAIData,
      hasCIMData,
      hasFormattedText,
      isEmpty: !hasOCRData && !hasAIData && !hasCIMData && !hasFormattedText
    };
  }, [normalizedData]);

  // 텍스트 추출 함수들 (안정적인 참조)
  const textExtractors = useMemo(() => ({
    // 고신뢰도 OCR 텍스트 추출
    getHighConfidenceText: () => {
      if (!normalizedData?.ocrResults) return null;

      const highConfidence = normalizedData.ocrResults.filter(
        item => item.confidence >= 0.8 && item.text?.trim()
      );

      if (highConfidence.length === 0) return null;

      return {
        text: highConfidence.map(item => item.text.trim()).join('\n\n'),
        confidence: highConfidence.reduce((sum, item) => sum + item.confidence, 0) / highConfidence.length,
        source: 'high_confidence_ocr',
        count: highConfidence.length
      };
    },

    // 모든 OCR 텍스트 추출
    getAllOCRText: () => {
      if (!normalizedData?.ocrResults?.length) return null;

      const validTexts = normalizedData.ocrResults.filter(item => item.text?.trim());
      if (validTexts.length === 0) return null;

      return {
        text: validTexts.map(item => item.text.trim()).join('\n\n'),
        confidence: validTexts.reduce((sum, item) => sum + (item.confidence || 0), 0) / validTexts.length,
        source: 'all_ocr',
        count: validTexts.length
      };
    },

    // AI 설명 텍스트 추출
    getAIDescriptions: () => {
      if (!normalizedData?.aiResults?.length) return null;

      const descriptions = normalizedData.aiResults
        .map(item => item.description || item.text)
        .filter(desc => desc?.trim());

      if (descriptions.length === 0) return null;

      return {
        text: descriptions.join('\n\n'),
        confidence: 0.7, // AI 설명의 기본 신뢰도
        source: 'ai_descriptions',
        count: descriptions.length
      };
    }
  }), [normalizedData]);

  // 성능 통계 (개발 모드)
  const performanceStats = useMemo(() => {
    if (!enableLogging) return null;

    return {
      processingCount: processingCountRef.current,
      cacheSize: cacheRef.current.size,
      lastProcessingTime: normalizedData?._meta?.processingTime,
      cacheHitRatio: processingCountRef.current > 0
        ? ((processingCountRef.current - cacheRef.current.size) / processingCountRef.current * 100).toFixed(1) + '%'
        : '0%',
      dataQuality: normalizedData?._quality?.quality
    };
  }, [enableLogging, normalizedData]);

  // 유틸리티 함수들
  const utils = useMemo(() => ({
    // 데이터 변경 여부 확인
    hasDataChanged: (prevHash) => prevHash !== dataHash,

    // 캐시 정리
    clearCache: () => {
      cacheRef.current.clear();
      processingCountRef.current = 0;
      if (enableLogging) {
        console.debug('🧹 분석 데이터 캐시 전체 정리 완료');
      }
    },

    // 데이터 품질 보고서 생성
    getQualityReport: () => {
      if (!normalizedData?._quality) return null;

      return {
        ...normalizedData._quality,
        processingStats: performanceStats,
        dataSize: {
          ocr: normalizedData.ocrResults?.length || 0,
          ai: normalizedData.aiResults?.length || 0,
          hasStats: Boolean(normalizedData.stats),
          hasCIM: Boolean(normalizedData.cimData)
        }
      };
    }
  }), [dataHash, normalizedData, performanceStats, enableLogging]);

  return {
    // 안정화된 데이터
    normalizedData,
    dataHash,

    // 가용성 정보
    availability,

    // 텍스트 추출 함수들
    textExtractors,

    // 유틸리티 함수들
    utils,

    // 성능 통계 (개발 모드)
    performanceStats,

    // 현재 데이터의 메타 정보
    meta: normalizedData?._meta
  };
};

export default useStableAnalysisData;