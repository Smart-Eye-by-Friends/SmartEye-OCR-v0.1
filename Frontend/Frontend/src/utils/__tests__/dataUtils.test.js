/**
 * 데이터 정규화 유틸리티 테스트
 */
import {
  normalizeAnalysisResults,
  normalizeAnalysisResponse,
  safeGet,
  safeArray,
  isLegacyResponse
} from '../dataUtils';

describe('Data Normalization Utils', () => {
  describe('safeGet', () => {
    test('should return correct nested value', () => {
      const obj = { a: { b: { c: 'value' } } };
      expect(safeGet(obj, 'a.b.c')).toBe('value');
    });

    test('should return default value for non-existent path', () => {
      const obj = { a: { b: {} } };
      expect(safeGet(obj, 'a.b.c', 'default')).toBe('default');
    });

    test('should handle null/undefined objects', () => {
      expect(safeGet(null, 'a.b.c', 'default')).toBe('default');
      expect(safeGet(undefined, 'a.b.c', 'default')).toBe('default');
    });
  });

  describe('safeArray', () => {
    test('should return arrays unchanged', () => {
      const arr = [1, 2, 3];
      expect(safeArray(arr)).toBe(arr);
    });

    test('should return default for non-arrays', () => {
      expect(safeArray(null)).toEqual([]);
      expect(safeArray('string')).toEqual([]);
      expect(safeArray(123)).toEqual([]);
    });

    test('should use custom default', () => {
      const customDefault = ['default'];
      expect(safeArray(null, customDefault)).toBe(customDefault);
    });
  });

  describe('isLegacyResponse', () => {
    test('should detect legacy response', () => {
      const legacyResponse = {
        ocrResults: [{ text: 'sample' }],
        aiResults: [{ description: 'sample' }],
        stats: { total_elements: 5 }
      };
      expect(isLegacyResponse(legacyResponse)).toBe(true);
    });

    test('should detect CIM response', () => {
      const cimResponse = {
        cimData: { ocr_results: [], ai_results: [] },
        stats: { processing_time_ms: 1000 }
      };
      expect(isLegacyResponse(cimResponse)).toBe(false);
    });

    test('should handle invalid data', () => {
      expect(isLegacyResponse(null)).toBe(false);
      expect(isLegacyResponse({})).toBe(false);
    });
  });

  describe('normalizeAnalysisResponse', () => {
    test('should normalize CIM response', () => {
      const cimResponse = {
        success: true,
        cimData: {
          ocr_results: [
            { text: 'Sample text', confidence: 0.9, className: 'paragraph' }
          ],
          ai_results: [
            { description: 'AI analysis', confidence: 0.8, className: 'figure' }
          ],
          questions: [
            { questionNumber: '1', questionContent: { mainQuestion: 'Test question' } }
          ]
        },
        stats: { processing_time_ms: 2000 },
        layoutImageUrl: '/static/layout.png',
        formattedText: 'Formatted content'
      };

      const result = normalizeAnalysisResponse(cimResponse);

      expect(result.success).toBe(true);
      expect(result.ocrResults).toHaveLength(1);
      expect(result.aiResults).toHaveLength(1);
      expect(result.stats.total_elements).toBe(2);
      expect(result.stats.processing_time).toBe(2);
      expect(result.layoutImageUrl).toBe('/static/layout.png');
      expect(result.formattedText).toBe('Formatted content');
      expect(result.cimData).toBeDefined();
    });

    test('should pass through legacy response', () => {
      const legacyResponse = {
        success: true,
        ocrResults: [{ text: 'Sample', confidence: 0.9 }],
        aiResults: [{ description: 'AI desc', confidence: 0.8 }],
        stats: { total_elements: 5, average_confidence: 0.85 },
        layoutImageUrl: '/static/legacy.png'
      };

      const result = normalizeAnalysisResponse(legacyResponse);

      expect(result.ocrResults).toHaveLength(1);
      expect(result.aiResults).toHaveLength(1);
      expect(result.stats.total_elements).toBe(5);
      expect(result.layoutImageUrl).toBe('/static/legacy.png');
      expect(result.cimData).toBeNull();
    });

    test('should handle empty data', () => {
      const result = normalizeAnalysisResponse(null);

      expect(result.success).toBe(false);
      expect(result.ocrResults).toEqual([]);
      expect(result.aiResults).toEqual([]);
      expect(result.stats.total_elements).toBe(0);
    });
  });

  describe('normalizeAnalysisResults', () => {
    test('should detect already normalized data', () => {
      const normalizedData = {
        ocrResults: [{ text: 'test' }],
        aiResults: [{ description: 'test' }],
        stats: { total_elements: 2 }
      };

      const result = normalizeAnalysisResults(normalizedData);

      expect(result.ocrResults).toEqual(normalizedData.ocrResults);
      expect(result.stats).toEqual(normalizedData.stats);
    });

    test('should normalize un-normalized data', () => {
      const unnormalizedData = {
        cimData: {
          ocr_results: [{ text: 'CIM text', confidence: 0.9 }],
          ai_results: [{ description: 'CIM AI', confidence: 0.8 }]
        },
        stats: { processing_time_ms: 1500 }
      };

      const result = normalizeAnalysisResults(unnormalizedData);

      expect(result.ocrResults).toHaveLength(1);
      expect(result.aiResults).toHaveLength(1);
      expect(result.stats.total_elements).toBe(2);
      expect(result.stats.processing_time).toBe(1.5);
    });

    test('should handle null input gracefully', () => {
      const result = normalizeAnalysisResults(null);

      expect(result.success).toBe(false);
      expect(result.ocrResults).toEqual([]);
      expect(result.aiResults).toEqual([]);
    });
  });

  describe('CIM data extraction', () => {
    test('should calculate total characters correctly', () => {
      const cimResponse = {
        cimData: {
          ocr_results: [
            { text: 'Hello' },    // 5 chars
            { text: 'World!' }    // 6 chars
          ],
          questions: [
            {
              questionContent: {
                mainQuestion: 'Test?',  // 5 chars
                passage: 'Sample'       // 6 chars
              }
            }
          ]
        }
      };

      const result = normalizeAnalysisResponse(cimResponse);
      expect(result.stats.total_characters).toBe(22); // 5+6+5+6
    });

    test('should calculate average confidence correctly', () => {
      const cimResponse = {
        cimData: {
          ocr_results: [
            { text: 'Text 1', confidence: 0.8 },
            { text: 'Text 2', confidence: 0.9 },
            { text: 'Text 3', confidence: 0.7 }
          ]
        }
      };

      const result = normalizeAnalysisResponse(cimResponse);
      expect(result.stats.average_confidence).toBeCloseTo(0.8, 1);
    });

    test('should handle missing confidence values', () => {
      const cimResponse = {
        cimData: {
          ocr_results: [
            { text: 'Text 1' },
            { text: 'Text 2', confidence: null },
            { text: 'Text 3', confidence: 0.8 }
          ]
        }
      };

      const result = normalizeAnalysisResponse(cimResponse);
      expect(result.stats.average_confidence).toBeCloseTo(0.8, 1);
    });

    test('should generate element counts correctly', () => {
      const cimResponse = {
        cimData: {
          ocr_results: [
            { text: 'Text 1', element_type: 'paragraph' },
            { text: 'Text 2', element_type: 'paragraph' },
            { text: 'Text 3', element_type: 'title' }
          ],
          ai_results: [
            { description: 'AI 1', type: 'figure' },
            { description: 'AI 2', type: 'figure' }
          ]
        }
      };

      const result = normalizeAnalysisResponse(cimResponse);
      expect(result.stats.element_counts.paragraph).toBe(2);
      expect(result.stats.element_counts.title).toBe(1);
      expect(result.stats.element_counts.figure).toBe(2);
    });
  });
});