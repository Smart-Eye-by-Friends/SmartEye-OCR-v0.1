/**
 * SmartEye 프론트엔드 안정성 테스트
 * 예외 상황에서의 복구 능력 및 안정성 검증
 */

import {
  normalizeAnalysisResults,
  normalizeCIMResponse,
  safeGet,
  safeArray,
  isLegacyResponse
} from '../dataUtils';
import {
  detectError,
  extractFallbackText,
  generateErrorMessage,
  validateData,
  createLoadingManager
} from '../errorHandler';

describe('안정성 테스트', () => {
  describe('예외 상황 처리', () => {
    test('null/undefined 입력 처리', () => {
      // null 입력
      expect(() => normalizeAnalysisResults(null)).not.toThrow();
      expect(() => normalizeAnalysisResults(undefined)).not.toThrow();

      const nullResult = normalizeAnalysisResults(null);
      expect(nullResult).toHaveProperty('ocrResults', []);
      expect(nullResult).toHaveProperty('aiResults', []);
      expect(nullResult).toHaveProperty('stats');

      // 빈 객체 입력
      const emptyResult = normalizeAnalysisResults({});
      expect(emptyResult.success).toBe(false);
      expect(Array.isArray(emptyResult.ocrResults)).toBe(true);
    });

    test('잘못된 타입 입력 처리', () => {
      const invalidInputs = [
        'string',
        123,
        [],
        true,
        Symbol('test'),
        new Date()
      ];

      invalidInputs.forEach(input => {
        expect(() => normalizeAnalysisResults(input)).not.toThrow();
        const result = normalizeAnalysisResults(input);
        expect(result).toHaveProperty('ocrResults');
        expect(result).toHaveProperty('aiResults');
      });
    });

    test('순환 참조 객체 처리', () => {
      const circularObj = { data: {} };
      circularObj.data.self = circularObj;

      expect(() => normalizeAnalysisResults(circularObj)).not.toThrow();

      // safeGet으로 순환 참조 접근
      expect(() => safeGet(circularObj, 'data.self.data.self')).not.toThrow();
    });

    test('매우 깊은 중첩 객체 처리', () => {
      let deepObj = {};
      let current = deepObj;

      // 100레벨 깊이의 중첩 객체 생성
      for (let i = 0; i < 100; i++) {
        current.next = { level: i };
        current = current.next;
      }
      current.value = '깊은 값';

      // 깊은 경로로 안전 접근
      const path = Array.from({ length: 100 }, (_, i) => 'next').join('.') + '.value';

      expect(() => safeGet(deepObj, path)).not.toThrow();
      expect(safeGet(deepObj, path)).toBe('깊은 값');
    });
  });

  describe('메모리 안정성', () => {
    test('대용량 데이터 반복 처리', () => {
      const largeData = {
        ocrResults: Array.from({ length: 1000 }, (_, i) => ({
          text: `텍스트 ${i}`.repeat(100), // 긴 텍스트 시뮬레이션
          confidence: Math.random(),
          bbox: [i, i, 100, 100]
        })),
        aiResults: Array.from({ length: 500 }, (_, i) => ({
          description: `설명 ${i}`.repeat(50),
          confidence: Math.random()
        }))
      };

      // 100번 반복 처리
      for (let i = 0; i < 100; i++) {
        const result = normalizeAnalysisResults(largeData);
        expect(result.ocrResults).toHaveLength(1000);
        expect(result.aiResults).toHaveLength(500);

        // 중간에 가비지 컬렉션 힌트
        if (i % 20 === 0 && global.gc) {
          global.gc();
        }
      }
    });

    test('문자열 메모리 누수 방지', () => {
      const largeStrings = Array.from({ length: 1000 }, (_, i) =>
        'a'.repeat(10000) + i
      );

      largeStrings.forEach(str => {
        expect(detectError(str)).toBe(false);
        expect(extractFallbackText({ ocrResults: [{ text: str }] })).toContain('OCR 추출 텍스트');
      });

      // 문자열 참조 해제
      largeStrings.length = 0;
    });
  });

  describe('에러 복구 능력', () => {
    test('부분적 데이터 손상 복구', () => {
      const partiallyCorruptedData = {
        ocrResults: [
          { text: '정상 텍스트 1', confidence: 0.9 },
          null, // 손상된 데이터
          { text: 'Error: failed', confidence: 0.1 }, // 에러 텍스트
          undefined, // 손상된 데이터
          { text: '정상 텍스트 2', confidence: 0.8 },
          { /* text 필드 없음 */ confidence: 0.7 },
          { text: '', confidence: 0.5 } // 빈 텍스트
        ],
        aiResults: [
          { description: 'AI 분석 1' },
          null,
          { description: 'Error occurred' },
          { description: 'AI 분석 2' }
        ]
      };

      const result = normalizeAnalysisResults(partiallyCorruptedData);

      // 정상 데이터만 추출되어야 함
      const validOCR = result.ocrResults.filter(item =>
        item && item.text && item.text.trim() && !detectError(item.text)
      );
      expect(validOCR).toHaveLength(2); // '정상 텍스트 1', '정상 텍스트 2'

      const validAI = result.aiResults.filter(item =>
        item && item.description && !detectError(item.description)
      );
      expect(validAI).toHaveLength(2); // 'AI 분석 1', 'AI 분석 2'
    });

    test('전체 OCR 실패 시 AI 데이터 활용', () => {
      const ocrFailedData = {
        ocrResults: [
          { text: 'Error: OCR processing failed', confidence: 0.0 },
          { text: 'OCR 처리 실패', confidence: 0.0 }
        ],
        aiResults: [
          { description: '이미지에는 수학 문제가 있습니다', confidence: 0.8 },
          { description: '표와 그래프가 포함되어 있습니다', confidence: 0.7 }
        ]
      };

      const fallbackText = extractFallbackText(ocrFailedData);

      expect(fallbackText).toContain('AI 분석 결과');
      expect(fallbackText).toContain('수학 문제');
      expect(fallbackText).toContain('표와 그래프');
      expect(fallbackText).not.toContain('Error');
      expect(fallbackText).not.toContain('실패');
    });

    test('모든 데이터 손상 시 적절한 메시지 표시', () => {
      const completelyCorruptedData = {
        ocrResults: [
          { text: 'Error: total failure', confidence: 0.0 },
          null,
          undefined
        ],
        aiResults: [
          { description: 'Error occurred in AI processing' },
          null
        ],
        cimData: null
      };

      const fallbackText = extractFallbackText(completelyCorruptedData);

      expect(fallbackText).toContain('추출 가능한 텍스트가 없습니다');
      expect(fallbackText).toContain('다시 시도');
    });
  });

  describe('동시성 안전성', () => {
    test('동시 데이터 정규화 요청', async () => {
      const testData = {
        ocrResults: Array.from({ length: 100 }, (_, i) => ({
          text: `텍스트 ${i}`,
          confidence: Math.random()
        }))
      };

      // 동시에 10개의 정규화 요청
      const promises = Array.from({ length: 10 }, () =>
        new Promise(resolve => {
          setTimeout(() => {
            const result = normalizeAnalysisResults(testData);
            resolve(result);
          }, Math.random() * 100);
        })
      );

      const results = await Promise.all(promises);

      // 모든 결과가 일관되어야 함
      results.forEach(result => {
        expect(result.ocrResults).toHaveLength(100);
        expect(result.stats.total_elements).toBeGreaterThan(0);
      });
    });

    test('상태 변경 중 안전한 에러 처리', () => {
      let state = { isLoading: false, hasError: false, errorMessage: '' };

      const manager = createLoadingManager(newState => {
        state = { ...state, ...newState };
      });

      // 빠른 연속 상태 변경
      manager.setLoading(true, '로딩 중...');
      manager.setError('테스트 에러', '테스트');
      manager.clearError();
      manager.setLoading(false);
      manager.reset();

      expect(state.isLoading).toBe(false);
      expect(state.hasError).toBe(false);
      expect(state.errorMessage).toBe('');
    });
  });

  describe('타입 안전성', () => {
    test('타입 검증 함수', () => {
      const schema = {
        'title': { required: true, type: 'string' },
        'count': { required: true, type: 'number' },
        'items': { required: false, type: 'array' },
        'metadata': { required: false, type: 'object' }
      };

      // 유효한 데이터
      const validData = {
        title: '테스트 제목',
        count: 42,
        items: [1, 2, 3],
        metadata: { key: 'value' }
      };

      const validResult = validateData(validData, schema);
      expect(validResult.isValid).toBe(true);
      expect(validResult.errors).toHaveLength(0);

      // 무효한 데이터
      const invalidData = {
        title: 123, // 잘못된 타입
        count: 'not a number', // 잘못된 타입
        items: 'not an array' // 잘못된 타입
        // metadata 누락 (선택사항이므로 OK)
      };

      const invalidResult = validateData(invalidData, schema);
      expect(invalidResult.isValid).toBe(false);
      expect(invalidResult.errors.length).toBeGreaterThan(0);
    });

    test('safeGet 타입 안전성', () => {
      const testObj = {
        string: 'hello',
        number: 42,
        boolean: true,
        array: [1, 2, 3],
        object: { nested: 'value' },
        null: null,
        undefined: undefined
      };

      // 각 타입별 안전한 접근
      expect(typeof safeGet(testObj, 'string')).toBe('string');
      expect(typeof safeGet(testObj, 'number')).toBe('number');
      expect(typeof safeGet(testObj, 'boolean')).toBe('boolean');
      expect(Array.isArray(safeGet(testObj, 'array'))).toBe(true);
      expect(typeof safeGet(testObj, 'object')).toBe('object');
      expect(safeGet(testObj, 'null')).toBeNull();
      expect(safeGet(testObj, 'undefined')).toBeNull(); // 기본값
      expect(safeGet(testObj, 'nonexistent')).toBeNull();
    });
  });

  describe('에러 메시지 품질', () => {
    test('사용자 친화적 에러 메시지 생성', () => {
      const testCases = [
        {
          error: new Error('Network Error'),
          context: '데이터 로딩',
          expected: '네트워크 연결을 확인해주세요'
        },
        {
          error: new Error('timeout'),
          context: '분석 처리',
          expected: '서버 응답 시간이 초과'
        },
        {
          error: new Error('unauthorized'),
          context: '로그인',
          expected: '인증에 실패했습니다'
        },
        {
          error: 'file too large',
          context: '파일 업로드',
          expected: '파일 크기가 너무 큽니다'
        }
      ];

      testCases.forEach(({ error, context, expected }) => {
        const message = generateErrorMessage(context, error);
        expect(message).toContain(expected);
        expect(message).toContain(context);
      });
    });

    test('에러 메시지 길이 제한', () => {
      const veryLongError = 'a'.repeat(1000);
      const message = generateErrorMessage('테스트', veryLongError);

      // 에러 메시지가 너무 길지 않아야 함 (합리적 길이)
      expect(message.length).toBeLessThan(200);
    });
  });
});