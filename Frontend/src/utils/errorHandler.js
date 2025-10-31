/**
 * SmartEye 프론트엔드 에러 처리 유틸리티
 *
 * 백엔드 API 응답의 오류나 불완전한 데이터를 안전하게 처리하기 위한
 * 공통 함수들을 제공합니다.
 */

/**
 * 안전한 객체 속성 접근
 * @param {Object} obj - 접근할 객체
 * @param {string} path - 점으로 구분된 속성 경로 (예: 'user.profile.name')
 * @param {*} defaultValue - 기본값
 * @returns {*} 속성 값 또는 기본값
 */
export const safeGet = (obj, path, defaultValue = null) => {
  try {
    if (!obj || typeof obj !== 'object') return defaultValue;

    return path.split('.').reduce((current, key) => {
      return current && typeof current === 'object' && key in current
        ? current[key]
        : defaultValue;
    }, obj);
  } catch (error) {
    console.warn('SafeGet 오류:', error);
    return defaultValue;
  }
};

/**
 * 안전한 숫자 변환
 * @param {*} value - 변환할 값
 * @param {number} defaultValue - 기본값
 * @returns {number} 숫자 값 또는 기본값
 */
export const safeNumber = (value, defaultValue = 0) => {
  if (typeof value === 'number' && !isNaN(value)) return value;

  if (typeof value === 'string') {
    const parsed = parseFloat(value);
    return isNaN(parsed) ? defaultValue : parsed;
  }

  return defaultValue;
};

/**
 * 안전한 배열 처리
 * @param {*} value - 확인할 값
 * @param {Array} defaultValue - 기본값
 * @returns {Array} 배열 또는 기본값
 */
export const safeArray = (value, defaultValue = []) => {
  return Array.isArray(value) ? value : defaultValue;
};

/**
 * 안전한 문자열 처리
 * @param {*} value - 확인할 값
 * @param {string} defaultValue - 기본값
 * @returns {string} 문자열 또는 기본값
 */
export const safeString = (value, defaultValue = '') => {
  if (typeof value === 'string') return value;
  if (value == null) return defaultValue;
  return String(value);
};

/**
 * 에러 메시지 패턴 감지
 * @param {string} text - 확인할 텍스트
 * @returns {boolean} 에러 패턴 포함 여부
 */
export const detectError = (text) => {
  if (!text || typeof text !== 'string') return false;

  const errorPatterns = [
    /error/i,
    /오류/,
    /실패/,
    /exception/i,
    /not found/i,
    /cannot/i,
    /unable/i,
    /invalid/i,
    /처리할 수 없습니다/,
    /불러올 수 없습니다/,
    /문제가 발생했습니다/,
    /연결할 수 없습니다/,
    /timeout/i,
    /시간 초과/,
    /access denied/i,
    /접근 거부/,
    /unauthorized/i,
    /권한이 없습니다/
  ];

  return errorPatterns.some(pattern => pattern.test(text));
};

/**
 * 분석 데이터에서 안전하게 텍스트 추출
 * @param {Object} analysisResults - 분석 결과 객체
 * @returns {string} 추출된 텍스트
 */
export const extractFallbackText = (analysisResults) => {
  if (!analysisResults) return '분석 결과가 없습니다.';

  // OCR 결과에서 텍스트 추출
  const ocrResults = safeArray(analysisResults.ocrResults);
  if (ocrResults.length > 0) {
    const ocrText = ocrResults
      .filter(result => result && safeString(result.text).trim())
      .map(result => safeString(result.text).trim())
      .join('\n\n');

    if (ocrText.length > 0) {
      return `📝 OCR 추출 텍스트:\n\n${ocrText}`;
    }
  }

  // CIM 데이터에서 텍스트 추출
  const cimData = safeGet(analysisResults, 'cimData');
  if (cimData) {
    try {
      // CIM 데이터가 문자열인 경우
      if (typeof cimData === 'string' && cimData.trim()) {
        return `📋 CIM 데이터:\n\n${cimData.trim()}`;
      }

      // CIM 데이터가 객체인 경우
      if (typeof cimData === 'object') {
        // 구조화된 텍스트 찾기
        const structuredText = safeGet(cimData, 'structured_text') ||
                              safeGet(cimData, 'formattedText') ||
                              safeGet(cimData, 'text');

        if (structuredText && safeString(structuredText).trim()) {
          return `📋 구조화된 텍스트:\n\n${safeString(structuredText).trim()}`;
        }

        // JSON 형태로 표시
        const jsonText = JSON.stringify(cimData, null, 2);
        if (jsonText.length > 50) { // 의미있는 데이터인지 확인
          return `📋 CIM 원시 데이터:\n\n${jsonText}`;
        }
      }
    } catch (error) {
      console.warn('CIM 데이터 파싱 오류:', error);
    }
  }

  // AI 결과에서 텍스트 추출
  const aiResults = safeArray(analysisResults.aiResults);
  if (aiResults.length > 0) {
    const aiText = aiResults
      .filter(result => result && safeString(result.description || result.text).trim())
      .map(result => safeString(result.description || result.text).trim())
      .join('\n\n');

    if (aiText.length > 0) {
      return `🤖 AI 분석 결과:\n\n${aiText}`;
    }
  }

  return '추출 가능한 텍스트가 없습니다.\n\n분석을 다시 실행하거나 다른 이미지를 시도해보세요.';
};

/**
 * 통계 데이터 안전 처리
 * @param {Object} stats - 통계 객체
 * @returns {Object} 정제된 통계 데이터
 */
export const sanitizeStats = (stats) => {
  if (!stats || typeof stats !== 'object') {
    return {
      total_elements: 0,
      total_characters: 0,
      average_confidence: 0,
      processing_time: 0,
      element_counts: {},
      metadata: {}
    };
  }

  return {
    total_elements: safeNumber(stats.total_elements, 0),
    total_characters: safeNumber(stats.total_characters, 0),
    average_confidence: safeNumber(stats.average_confidence, 0),
    processing_time: safeNumber(stats.processing_time, 0),
    element_counts: safeGet(stats, 'element_counts', {}),
    metadata: safeGet(stats, 'metadata', {})
  };
};

/**
 * 에러 메시지 생성
 * @param {string} context - 에러 발생 컨텍스트
 * @param {Error|string} error - 에러 객체 또는 메시지
 * @returns {string} 사용자 친화적 에러 메시지
 */
export const generateErrorMessage = (context, error) => {
  const baseMessage = `${context} 중 문제가 발생했습니다.`;

  if (!error) return baseMessage;

  const errorMessage = error.message || error.toString();

  // 일반적인 에러 메시지 처리
  const errorMappings = {
    'network': '네트워크 연결을 확인해주세요.',
    'timeout': '서버 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.',
    'unauthorized': '인증에 실패했습니다. 다시 로그인해주세요.',
    'forbidden': '접근 권한이 없습니다.',
    'not found': '요청한 리소스를 찾을 수 없습니다.',
    'server error': '서버에서 오류가 발생했습니다. 관리자에게 문의해주세요.',
    'invalid format': '지원하지 않는 파일 형식입니다.',
    'file too large': '파일 크기가 너무 큽니다. 10MB 이하의 파일을 사용해주세요.'
  };

  for (const [key, message] of Object.entries(errorMappings)) {
    if (errorMessage.toLowerCase().includes(key)) {
      return `${baseMessage} ${message}`;
    }
  }

  return `${baseMessage} (${errorMessage})`;
};

/**
 * 데이터 유효성 검증
 * @param {Object} data - 검증할 데이터
 * @param {Object} schema - 검증 스키마
 * @returns {Object} { isValid: boolean, errors: string[] }
 */
export const validateData = (data, schema) => {
  const errors = [];

  if (!data || typeof data !== 'object') {
    return { isValid: false, errors: ['데이터가 올바르지 않습니다.'] };
  }

  for (const [field, rules] of Object.entries(schema)) {
    const value = safeGet(data, field);

    if (rules.required && (value == null || value === '')) {
      errors.push(`${field}는 필수 항목입니다.`);
      continue;
    }

    if (value != null && rules.type) {
      const expectedType = rules.type;
      const actualType = Array.isArray(value) ? 'array' : typeof value;

      if (actualType !== expectedType) {
        errors.push(`${field}의 타입이 올바르지 않습니다. (기대: ${expectedType}, 실제: ${actualType})`);
      }
    }

    if (rules.validate && typeof rules.validate === 'function') {
      const validationResult = rules.validate(value);
      if (validationResult !== true) {
        errors.push(validationResult || `${field} 값이 유효하지 않습니다.`);
      }
    }
  }

  return {
    isValid: errors.length === 0,
    errors
  };
};

/**
 * 로딩 상태 관리 유틸리티
 * @param {function} setState - 상태 설정 함수
 * @returns {Object} 로딩 관리 함수들
 */
export const createLoadingManager = (setState) => {
  return {
    setLoading: (isLoading, message = '') => {
      setState(prev => ({
        ...prev,
        isLoading,
        loadingMessage: message
      }));
    },

    setError: (error, context = '') => {
      const errorMessage = generateErrorMessage(context, error);
      setState(prev => ({
        ...prev,
        hasError: true,
        errorMessage,
        isLoading: false
      }));
    },

    clearError: () => {
      setState(prev => ({
        ...prev,
        hasError: false,
        errorMessage: ''
      }));
    },

    reset: () => {
      setState(prev => ({
        ...prev,
        isLoading: false,
        hasError: false,
        errorMessage: '',
        loadingMessage: ''
      }));
    }
  };
};

// 기본 내보내기
export default {
  safeGet,
  safeNumber,
  safeArray,
  safeString,
  detectError,
  extractFallbackText,
  sanitizeStats,
  generateErrorMessage,
  validateData,
  createLoadingManager
};