/**
 * SmartEye 에러 처리 유틸리티 테스트
 */

import {
  safeGet,
  safeNumber,
  safeArray,
  safeString,
  detectError,
  extractFallbackText,
  sanitizeStats,
  generateErrorMessage,
  validateData
} from '../errorHandler';

describe('ErrorHandler Utils', () => {
  describe('safeGet', () => {
    const testObj = {
      user: {
        profile: {
          name: 'Test User',
          age: 25
        },
        settings: null
      },
      items: [1, 2, 3]
    };

    test('should return correct nested value', () => {
      expect(safeGet(testObj, 'user.profile.name')).toBe('Test User');
      expect(safeGet(testObj, 'user.profile.age')).toBe(25);
    });

    test('should return default value for non-existent path', () => {
      expect(safeGet(testObj, 'user.profile.email', 'default')).toBe('default');
      expect(safeGet(testObj, 'nonexistent.path')).toBe(null);
    });

    test('should handle null/undefined objects', () => {
      expect(safeGet(null, 'user.name', 'default')).toBe('default');
      expect(safeGet(undefined, 'user.name', 'default')).toBe('default');
    });
  });

  describe('safeNumber', () => {
    test('should return valid numbers unchanged', () => {
      expect(safeNumber(42)).toBe(42);
      expect(safeNumber(3.14)).toBe(3.14);
      expect(safeNumber(0)).toBe(0);
    });

    test('should parse string numbers', () => {
      expect(safeNumber('42')).toBe(42);
      expect(safeNumber('3.14')).toBe(3.14);
    });

    test('should return default for invalid values', () => {
      expect(safeNumber('invalid')).toBe(0);
      expect(safeNumber(null)).toBe(0);
      expect(safeNumber(undefined)).toBe(0);
      expect(safeNumber('invalid', 99)).toBe(99);
    });
  });

  describe('safeArray', () => {
    test('should return arrays unchanged', () => {
      const testArray = [1, 2, 3];
      expect(safeArray(testArray)).toBe(testArray);
    });

    test('should return default for non-arrays', () => {
      expect(safeArray(null)).toEqual([]);
      expect(safeArray('string')).toEqual([]);
      expect(safeArray(42)).toEqual([]);
    });
  });

  describe('detectError', () => {
    test('should detect error patterns', () => {
      expect(detectError('Error occurred')).toBe(true);
      expect(detectError('오류가 발생했습니다')).toBe(true);
      expect(detectError('Cannot process request')).toBe(true);
      expect(detectError('서버에서 문제가 발생했습니다')).toBe(true);
    });

    test('should not detect errors in normal text', () => {
      expect(detectError('정상적인 텍스트입니다')).toBe(false);
      expect(detectError('분석 완료되었습니다')).toBe(false);
      expect(detectError('')).toBe(false);
      expect(detectError(null)).toBe(false);
    });
  });

  describe('extractFallbackText', () => {
    const mockAnalysisResults = {
      ocrResults: [
        { text: '첫 번째 텍스트' },
        { text: '두 번째 텍스트' }
      ],
      cimData: {
        structured_text: 'CIM 구조화 텍스트'
      }
    };

    test('should extract OCR text', () => {
      const result = extractFallbackText(mockAnalysisResults);
      expect(result).toContain('첫 번째 텍스트');
      expect(result).toContain('두 번째 텍스트');
    });

    test('should handle empty results', () => {
      const result = extractFallbackText({});
      expect(result).toContain('추출 가능한 텍스트가 없습니다');
    });

    test('should handle null input', () => {
      const result = extractFallbackText(null);
      expect(result).toContain('분석 결과가 없습니다');
    });
  });

  describe('sanitizeStats', () => {
    test('should sanitize valid stats object', () => {
      const stats = {
        total_elements: '10',
        total_characters: 500,
        average_confidence: 0.85,
        element_counts: { paragraph: 5 }
      };

      const result = sanitizeStats(stats);
      expect(result.total_elements).toBe(10);
      expect(result.total_characters).toBe(500);
      expect(result.average_confidence).toBe(0.85);
    });

    test('should provide defaults for invalid stats', () => {
      const result = sanitizeStats(null);
      expect(result.total_elements).toBe(0);
      expect(result.total_characters).toBe(0);
      expect(result.average_confidence).toBe(0);
    });
  });

  describe('generateErrorMessage', () => {
    test('should generate contextual error messages', () => {
      const result = generateErrorMessage('텍스트 로딩', 'network error');
      expect(result).toContain('텍스트 로딩 중 문제가 발생했습니다');
      expect(result).toContain('네트워크 연결을 확인해주세요');
    });

    test('should handle generic errors', () => {
      const result = generateErrorMessage('분석', new Error('Unknown error'));
      expect(result).toContain('분석 중 문제가 발생했습니다');
    });
  });

  describe('validateData', () => {
    const schema = {
      name: { required: true, type: 'string' },
      age: { required: false, type: 'number' },
      email: {
        required: true,
        type: 'string',
        validate: (value) => value.includes('@') || '이메일 형식이 올바르지 않습니다'
      }
    };

    test('should validate correct data', () => {
      const data = {
        name: 'Test User',
        age: 25,
        email: 'test@example.com'
      };

      const result = validateData(data, schema);
      expect(result.isValid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    test('should detect validation errors', () => {
      const data = {
        age: 'invalid',
        email: 'invalid-email'
      };

      const result = validateData(data, schema);
      expect(result.isValid).toBe(false);
      expect(result.errors).toContain('name는 필수 항목입니다.');
      expect(result.errors).toContain('이메일 형식이 올바르지 않습니다');
    });
  });
});