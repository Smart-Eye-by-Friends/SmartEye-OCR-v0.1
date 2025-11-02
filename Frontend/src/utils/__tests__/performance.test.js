/**
 * SmartEye 프론트엔드 성능 테스트
 * 데이터 처리 속도 및 메모리 사용량 검증
 */

import {
  normalizeAnalysisResults,
  normalizeCIMResponse,
  safeGet,
  safeArray
} from '../dataUtils';
import {
  detectError,
  extractFallbackText,
  sanitizeStats
} from '../errorHandler';

describe('성능 테스트', () => {
  // 대용량 데이터 생성 헬퍼
  const generateLargeDataset = (size) => {
    const ocrResults = Array.from({ length: size }, (_, i) => ({
      text: `테스트 텍스트 ${i} - OCR 결과입니다. 이것은 충분히 긴 텍스트입니다.`,
      confidence: Math.random(),
      bbox: [i * 10, i * 15, 100, 20],
      element_type: ['text', 'title', 'paragraph'][i % 3]
    }));

    const aiResults = Array.from({ length: size / 2 }, (_, i) => ({
      description: `AI 분석 결과 ${i} - 상세한 이미지 분석 내용입니다.`,
      confidence: Math.random(),
      type: ['image_description', 'table_analysis'][i % 2]
    }));

    return {
      ocrResults,
      aiResults,
      stats: {
        total_elements: size + size / 2,
        total_characters: size * 50,
        average_confidence: 0.85,
        processing_time: Math.random() * 10,
        element_counts: {
          text: Math.floor(size / 3),
          title: Math.floor(size / 3),
          paragraph: Math.floor(size / 3)
        }
      },
      cimData: {
        questions: Array.from({ length: Math.floor(size / 10) }, (_, i) => ({
          question_content: {
            main_question: `문제 ${i + 1}번`,
            choices: Array.from({ length: 4 }, (_, j) => ({
              choice_text: `선택지 ${j + 1}`,
              choice_number: j + 1
            }))
          }
        }))
      }
    };
  };

  describe('데이터 정규화 성능', () => {
    test('소규모 데이터셋 처리 시간 (100개 요소)', () => {
      const data = generateLargeDataset(100);

      const startTime = performance.now();
      const result = normalizeAnalysisResults(data);
      const endTime = performance.now();

      const processingTime = endTime - startTime;
      console.log(`소규모 데이터셋 처리 시간: ${processingTime.toFixed(2)}ms`);

      expect(processingTime).toBeLessThan(50); // 50ms 이내
      expect(result.ocrResults).toHaveLength(100);
      expect(result.aiResults).toHaveLength(50);
    });

    test('중규모 데이터셋 처리 시간 (500개 요소)', () => {
      const data = generateLargeDataset(500);

      const startTime = performance.now();
      const result = normalizeAnalysisResults(data);
      const endTime = performance.now();

      const processingTime = endTime - startTime;
      console.log(`중규모 데이터셋 처리 시간: ${processingTime.toFixed(2)}ms`);

      expect(processingTime).toBeLessThan(200); // 200ms 이내
      expect(result.ocrResults).toHaveLength(500);
    });

    test('대규모 데이터셋 처리 시간 (1000개 요소)', () => {
      const data = generateLargeDataset(1000);

      const startTime = performance.now();
      const result = normalizeAnalysisResults(data);
      const endTime = performance.now();

      const processingTime = endTime - startTime;
      console.log(`대규모 데이터셋 처리 시간: ${processingTime.toFixed(2)}ms`);

      expect(processingTime).toBeLessThan(500); // 500ms 이내
      expect(result.stats.total_elements).toBeGreaterThan(1000);
    });
  });

  describe('에러 감지 성능', () => {
    test('대량 텍스트 에러 패턴 감지', () => {
      const texts = Array.from({ length: 1000 }, (_, i) =>
        i % 100 === 0 ? 'Error occurred in processing' : `정상 텍스트 ${i}`
      );

      const startTime = performance.now();
      const results = texts.map(text => detectError(text));
      const endTime = performance.now();

      const processingTime = endTime - startTime;
      console.log(`1000개 텍스트 에러 감지 시간: ${processingTime.toFixed(2)}ms`);

      expect(processingTime).toBeLessThan(100); // 100ms 이내
      expect(results.filter(Boolean)).toHaveLength(10); // 에러 10개 감지
    });
  });

  describe('메모리 사용량 테스트', () => {
    test('대용량 데이터 처리 시 메모리 누수 방지', () => {
      const initialMemory = performance.memory?.usedJSHeapSize || 0;

      // 대량 데이터 처리
      for (let i = 0; i < 10; i++) {
        const data = generateLargeDataset(200);
        const result = normalizeAnalysisResults(data);

        // 결과 사용 시뮬레이션
        expect(result.ocrResults.length).toBeGreaterThan(0);
      }

      // 가비지 컬렉션 강제 실행 (테스트 환경에서만)
      if (global.gc) {
        global.gc();
      }

      const finalMemory = performance.memory?.usedJSHeapSize || 0;
      const memoryIncrease = finalMemory - initialMemory;

      console.log(`메모리 증가량: ${(memoryIncrease / 1024 / 1024).toFixed(2)}MB`);

      // 메모리 증가량이 합리적 범위 내에 있는지 확인 (10MB 이내)
      expect(memoryIncrease).toBeLessThan(10 * 1024 * 1024);
    });
  });

  describe('안전 함수 성능', () => {
    test('safeGet 깊은 객체 접근 성능', () => {
      const deepObject = {
        level1: {
          level2: {
            level3: {
              level4: {
                level5: {
                  value: '찾는 값'
                }
              }
            }
          }
        }
      };

      const startTime = performance.now();

      // 1000번 깊은 접근 테스트
      for (let i = 0; i < 1000; i++) {
        const result = safeGet(deepObject, 'level1.level2.level3.level4.level5.value');
        expect(result).toBe('찾는 값');
      }

      const endTime = performance.now();
      const processingTime = endTime - startTime;

      console.log(`1000회 깊은 객체 접근 시간: ${processingTime.toFixed(2)}ms`);
      expect(processingTime).toBeLessThan(50); // 50ms 이내
    });

    test('safeArray 대용량 배열 처리', () => {
      const largeArray = Array.from({ length: 10000 }, (_, i) => i);
      const invalidInputs = [null, undefined, {}, 'string', 123];

      const startTime = performance.now();

      // 유효한 배열 처리
      const validResult = safeArray(largeArray);
      expect(validResult).toHaveLength(10000);

      // 무효한 입력 처리
      invalidInputs.forEach(input => {
        const result = safeArray(input, []);
        expect(Array.isArray(result)).toBe(true);
      });

      const endTime = performance.now();
      const processingTime = endTime - startTime;

      console.log(`대용량 배열 안전 처리 시간: ${processingTime.toFixed(2)}ms`);
      expect(processingTime).toBeLessThan(20); // 20ms 이내
    });
  });

  describe('텍스트 추출 성능', () => {
    test('복잡한 CIM 데이터에서 텍스트 추출', () => {
      const complexCIMData = {
        questions: Array.from({ length: 50 }, (_, i) => ({
          question_content: {
            main_question: `문제 ${i + 1}: 이것은 매우 긴 문제입니다. ${Math.random().toString(36).repeat(10)}`,
            passage: `지문 ${i + 1}: 이것은 긴 지문입니다. ${Math.random().toString(36).repeat(20)}`,
            choices: Array.from({ length: 5 }, (_, j) => ({
              choice_text: `선택지 ${j + 1}: ${Math.random().toString(36).repeat(5)}`,
              choice_number: j + 1
            })),
            images: Array.from({ length: 2 }, (_, k) => ({
              description: `이미지 ${k + 1} 설명: ${Math.random().toString(36).repeat(15)}`
            }))
          }
        }))
      };

      const analysisResults = {
        cimData: complexCIMData,
        ocrResults: [],
        aiResults: []
      };

      const startTime = performance.now();
      const extractedText = extractFallbackText(analysisResults);
      const endTime = performance.now();

      const processingTime = endTime - startTime;
      console.log(`복잡한 CIM 데이터 텍스트 추출 시간: ${processingTime.toFixed(2)}ms`);

      expect(processingTime).toBeLessThan(100); // 100ms 이내
      expect(extractedText.length).toBeGreaterThan(1000); // 충분한 텍스트 추출
    });
  });
});