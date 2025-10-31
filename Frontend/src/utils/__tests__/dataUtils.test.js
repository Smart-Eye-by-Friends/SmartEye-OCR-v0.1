/**
 * SmartEye 데이터 유틸리티 단위 테스트
 * 데이터 정규화 및 변환 로직 검증
 */

import {
  normalizeAnalysisResults,
  normalizeCIMResponse,
  normalizeAnalysisResponse,
  safeGet,
  safeArray,
  isLegacyResponse
} from '../dataUtils';

describe('데이터 유틸리티 단위 테스트', () => {
  describe('safeGet 함수', () => {
    test('기본 객체 속성 접근', () => {
      const obj = { a: { b: { c: 'value' } } };

      expect(safeGet(obj, 'a.b.c')).toBe('value');
      expect(safeGet(obj, 'a.b')).toEqual({ c: 'value' });
      expect(safeGet(obj, 'a')).toEqual({ b: { c: 'value' } });
    });

    test('존재하지 않는 경로 접근', () => {
      const obj = { a: { b: 'value' } };

      expect(safeGet(obj, 'a.c.d')).toBeNull();
      expect(safeGet(obj, 'x.y.z')).toBeNull();
      expect(safeGet(obj, 'a.b.c.d.e')).toBeNull();
    });

    test('기본값 반환', () => {
      const obj = { a: 1 };

      expect(safeGet(obj, 'b', 'default')).toBe('default');
      expect(safeGet(obj, 'a.b', [])).toEqual([]);
      expect(safeGet(obj, 'missing', 42)).toBe(42);
    });

    test('null/undefined 객체 처리', () => {
      expect(safeGet(null, 'a.b')).toBeNull();
      expect(safeGet(undefined, 'a.b')).toBeNull();
      expect(safeGet({}, 'a.b', 'fallback')).toBe('fallback');
    });

    test('배열 인덱스 접근', () => {
      const obj = { items: [{ name: 'first' }, { name: 'second' }] };

      expect(safeGet(obj, 'items.0.name')).toBe('first');
      expect(safeGet(obj, 'items.1.name')).toBe('second');
      expect(safeGet(obj, 'items.2.name')).toBeNull();
    });
  });

  describe('safeArray 함수', () => {
    test('유효한 배열 반환', () => {
      expect(safeArray([1, 2, 3])).toEqual([1, 2, 3]);
      expect(safeArray([])).toEqual([]);
      expect(safeArray(['a', 'b'])).toEqual(['a', 'b']);
    });

    test('무효한 입력 처리', () => {
      expect(safeArray(null)).toEqual([]);
      expect(safeArray(undefined)).toEqual([]);
      expect(safeArray('string')).toEqual([]);
      expect(safeArray(123)).toEqual([]);
      expect(safeArray({})).toEqual([]);
    });

    test('기본값 반환', () => {
      const defaultArray = ['default'];
      expect(safeArray(null, defaultArray)).toEqual(defaultArray);
      expect(safeArray('invalid', defaultArray)).toEqual(defaultArray);
    });
  });

  describe('isLegacyResponse 함수', () => {
    test('레거시 응답 감지', () => {
      const legacyResponse = {
        ocrResults: [{ text: 'test' }],
        aiResults: [{ description: 'test' }],
        stats: {}
      };

      expect(isLegacyResponse(legacyResponse)).toBe(true);
    });

    test('CIM 응답 감지', () => {
      const cimResponse = {
        cimData: { questions: [] },
        stats: {}
      };

      expect(isLegacyResponse(cimResponse)).toBe(false);
    });

    test('무효한 응답 처리', () => {
      expect(isLegacyResponse(null)).toBe(false);
      expect(isLegacyResponse(undefined)).toBe(false);
      expect(isLegacyResponse({})).toBe(false);
      expect(isLegacyResponse('string')).toBe(false);
    });
  });

  describe('normalizeCIMResponse 함수', () => {
    test('구조화된 CIM 데이터 정규화', () => {
      const cimResponse = {
        cimData: {
          questions: [
            {
              question_content: {
                main_question: '1번 문제입니다',
                passage: '이것은 지문입니다',
                choices: [
                  { choice_text: '선택지 1', choice_number: 1 },
                  { choice_text: '선택지 2', choice_number: 2 }
                ],
                images: [
                  { description: '그래프 설명', bbox: [10, 20, 100, 50] }
                ]
              },
              ai_analysis: {
                image_descriptions: ['AI 이미지 분석'],
                table_analysis: ['표 분석 결과']
              }
            }
          ]
        },
        stats: { total_elements: 5 },
        layoutImageUrl: 'test.jpg'
      };

      const result = normalizeCIMResponse(cimResponse);

      expect(result.success).toBe(true);
      expect(result.ocrResults.length).toBeGreaterThan(0);
      expect(result.aiResults.length).toBeGreaterThan(0);
      expect(result.layoutImageUrl).toBe('test.jpg');

      // OCR 결과 검증
      const questionText = result.ocrResults.find(r => r.element_type === 'question_text');
      expect(questionText).toBeTruthy();
      expect(questionText.text).toBe('1번 문제입니다');

      const passage = result.ocrResults.find(r => r.element_type === 'passage');
      expect(passage).toBeTruthy();
      expect(passage.text).toBe('이것은 지문입니다');

      const choices = result.ocrResults.filter(r => r.element_type === 'choice');
      expect(choices).toHaveLength(2);

      // AI 결과 검증
      const aiImage = result.aiResults.find(r => r.type === 'image_description');
      expect(aiImage).toBeTruthy();
      expect(aiImage.description).toBe('AI 이미지 분석');
    });

    test('기본 CIM 레이아웃 데이터 정규화', () => {
      const basicCimResponse = {
        cimData: {
          layout_analysis: {
            elements: [
              {
                text: '제목 텍스트',
                confidence: 0.9,
                element_type: 'title',
                bbox: [0, 0, 200, 30]
              },
              {
                text: '본문 텍스트',
                confidence: 0.8,
                element_type: 'paragraph',
                bbox: [0, 40, 200, 100]
              }
            ]
          },
          ai_results: [
            {
              description: 'AI 분석 설명',
              confidence: 0.7,
              type: 'image_description'
            }
          ]
        }
      };

      const result = normalizeCIMResponse(basicCimResponse);

      expect(result.ocrResults).toHaveLength(2);
      expect(result.aiResults).toHaveLength(1);

      // OCR 결과 구조 검증
      const titleText = result.ocrResults.find(r => r.element_type === 'title');
      expect(titleText.text).toBe('제목 텍스트');
      expect(titleText.confidence).toBe(0.9);
      expect(titleText.bbox).toEqual({
        x: 0, y: 0, width: 200, height: 30,
        x1: 0, y1: 0, x2: 200, y2: 30
      });
    });

    test('무효한 CIM 데이터 처리', () => {
      const invalidInputs = [null, undefined, 'string', 123, []];

      invalidInputs.forEach(input => {
        const result = normalizeCIMResponse(input);

        expect(result.success).toBe(false);
        expect(result.ocrResults).toEqual([]);
        expect(result.aiResults).toEqual([]);
        expect(result.stats.total_elements).toBe(0);
      });
    });
  });

  describe('normalizeAnalysisResults 함수', () => {
    test('이미 정규화된 데이터 처리', () => {
      const normalizedData = {
        ocrResults: [{ text: 'test', confidence: 0.9 }],
        aiResults: [{ description: 'test ai' }],
        stats: { total_elements: 2 },
        cimData: null
      };

      const result = normalizeAnalysisResults(normalizedData);

      expect(result).toEqual(normalizedData);
      expect(result.ocrResults).toHaveLength(1);
      expect(result.aiResults).toHaveLength(1);
    });

    test('레거시 응답 자동 처리', () => {
      const legacyResponse = {
        ocrResults: [{ text: 'legacy ocr' }],
        aiResults: [{ description: 'legacy ai' }],
        stats: { total_elements: 2 }
      };

      const result = normalizeAnalysisResults(legacyResponse);

      expect(result.ocrResults).toEqual(legacyResponse.ocrResults);
      expect(result.aiResults).toEqual(legacyResponse.aiResults);
      expect(result.cimData).toBeNull();
    });

    test('CIM 응답 자동 정규화', () => {
      const cimResponse = {
        cimData: {
          questions: [
            {
              question_content: {
                main_question: 'CIM 테스트 문제'
              }
            }
          ]
        }
      };

      const result = normalizeAnalysisResults(cimResponse);

      expect(result.ocrResults.length).toBeGreaterThan(0);
      expect(result.ocrResults[0].source).toBe('structured_analysis');
      expect(result.cimData).toBeTruthy();
    });
  });

  describe('bbox 정규화 테스트', () => {
    test('배열 형태 bbox 처리', () => {
      const testCases = [
        {
          input: [10, 20, 100, 50], // [x, y, width, height]
          expected: { x: 10, y: 20, width: 100, height: 50, x1: 10, y1: 20, x2: 110, y2: 70 }
        },
        {
          input: [10, 20, 110, 70], // [x1, y1, x2, y2] (큰 값들)
          expected: { x: 10, y: 20, width: 100, height: 50, x1: 10, y1: 20, x2: 110, y2: 70 }
        }
      ];

      // bbox 정규화를 테스트하기 위해 OCR 아이템 정규화 사용
      testCases.forEach(({ input, expected }, index) => {
        const mockOCRItem = { text: `test ${index}`, bbox: input };
        const cimResponse = {
          cimData: {
            layout_analysis: { elements: [mockOCRItem] }
          }
        };

        const result = normalizeCIMResponse(cimResponse);
        const normalizedBbox = result.ocrResults[0].bbox;

        expect(normalizedBbox.x).toBe(expected.x);
        expect(normalizedBbox.y).toBe(expected.y);
        expect(normalizedBbox.width).toBe(expected.width);
        expect(normalizedBbox.height).toBe(expected.height);
      });
    });

    test('객체 형태 bbox 처리', () => {
      const testCases = [
        {
          input: { x: 10, y: 20, width: 100, height: 50 },
          expected: { x: 10, y: 20, width: 100, height: 50 }
        },
        {
          input: { left: 10, top: 20, right: 110, bottom: 70 },
          expected: { x: 10, y: 20, width: 100, height: 50 }
        },
        {
          input: { x1: 10, y1: 20, x2: 110, y2: 70 },
          expected: { x: 10, y: 20, width: 100, height: 50 }
        }
      ];

      testCases.forEach(({ input, expected }, index) => {
        const mockOCRItem = { text: `test ${index}`, bbox: input };
        const cimResponse = {
          cimData: {
            layout_analysis: { elements: [mockOCRItem] }
          }
        };

        const result = normalizeCIMResponse(cimResponse);
        const normalizedBbox = result.ocrResults[0].bbox;

        expect(normalizedBbox.x).toBe(expected.x);
        expect(normalizedBbox.y).toBe(expected.y);
        expect(normalizedBbox.width).toBe(expected.width);
        expect(normalizedBbox.height).toBe(expected.height);
      });
    });
  });

  describe('통계 생성 테스트', () => {
    test('구조화된 분석 통계', () => {
      const cimResponse = {
        cimData: {
          questions: [
            {
              question_content: {
                main_question: '문제 1',
                choices: [
                  { choice_text: '선택지 1' },
                  { choice_text: '선택지 2' }
                ],
                explanations: ['해설 1']
              }
            },
            {
              question_content: {
                main_question: '문제 2',
                passage: '지문 2'
              }
            }
          ]
        }
      };

      const result = normalizeCIMResponse(cimResponse);

      expect(result.stats.structured_stats.analysis_type).toBe('structured');
      expect(result.stats.structured_stats.total_questions).toBe(2);
      expect(result.stats.structured_stats.total_choices).toBe(2);
      expect(result.stats.structured_stats.total_passages).toBe(1);
      expect(result.stats.structured_stats.total_explanations).toBe(1);
    });

    test('기본 레이아웃 분석 통계', () => {
      const basicResponse = {
        cimData: {
          layout_analysis: {
            elements: [
              { text: '텍스트 1', element_type: 'paragraph' },
              { text: '텍스트 2', element_type: 'title' }
            ]
          }
        }
      };

      const result = normalizeCIMResponse(basicResponse);

      expect(result.stats.structured_stats.analysis_type).toBe('basic_layout');
      expect(result.stats.element_counts.paragraph).toBe(1);
      expect(result.stats.element_counts.title).toBe(1);
    });

    test('신뢰도 계산', () => {
      const responseWithConfidence = {
        cimData: {
          layout_analysis: {
            elements: [
              { text: '높은 신뢰도', confidence: 0.9 },
              { text: '중간 신뢰도', confidence: 0.7 },
              { text: '낮은 신뢰도', confidence: 0.3 }
            ]
          }
        }
      };

      const result = normalizeCIMResponse(responseWithConfidence);

      const expectedAverage = (0.9 + 0.7 + 0.3) / 3;
      expect(result.stats.average_confidence).toBeCloseTo(expectedAverage, 2);
    });
  });

  describe('에러 처리 및 복구', () => {
    test('부분적 데이터 손상 복구', () => {
      const partiallyCorrupted = {
        cimData: {
          questions: [
            {
              question_content: {
                main_question: '정상 문제',
                choices: [
                  { choice_text: '정상 선택지' },
                  null, // 손상된 데이터
                  { choice_text: '' }, // 빈 데이터
                  { choice_text: '또 다른 정상 선택지' }
                ]
              }
            },
            null, // 손상된 문제
            {
              question_content: {
                main_question: '두 번째 정상 문제'
              }
            }
          ]
        }
      };

      const result = normalizeCIMResponse(partiallyCorrupted);

      // 정상 데이터만 추출되어야 함
      const questionTexts = result.ocrResults.filter(r => r.element_type === 'question_text');
      expect(questionTexts).toHaveLength(2);

      const choices = result.ocrResults.filter(r => r.element_type === 'choice');
      expect(choices).toHaveLength(2); // null과 빈 문자열 제외
    });

    test('완전한 데이터 손상 처리', () => {
      const completelyCorrupted = {
        cimData: {
          questions: [null, undefined, {}]
        }
      };

      const result = normalizeCIMResponse(completelyCorrupted);

      expect(result.ocrResults).toHaveLength(0);
      expect(result.aiResults).toHaveLength(0);
      expect(result.success).toBe(true); // 구조는 유효하지만 데이터가 없음
    });
  });
});