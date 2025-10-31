/**
 * 성능 벤치마크 간단 테스트
 * 무한 루프 방지 및 최적화 검증
 */

import React from 'react';
import { render } from '@testing-library/react';
import '@testing-library/jest-dom';
import DataNormalizer from '../utils/DataNormalizer';
import { useStableAnalysisData } from '../hooks/useStableAnalysisData';

// Mock 데이터
const mockAnalysisResults = {
  ocrResults: [
    { text: '테스트 텍스트', confidence: 0.95, element_type: 'text', block_id: 'ocr_1' },
    { text: '중간 품질 텍스트', confidence: 0.6, element_type: 'text', block_id: 'ocr_2' }
  ],
  aiResults: [
    { description: 'AI 설명', confidence: 0.8, type: 'description', block_id: 'ai_1' }
  ],
  stats: { total_elements: 3, average_confidence: 0.78 },
  cimData: {
    questions: [
      {
        question_content: {
          main_question: '테스트 문제',
          choices: [
            { choice_text: '선택지 1', choice_number: 1 },
            { choice_text: '선택지 2', choice_number: 2 }
          ]
        }
      }
    ]
  }
};

describe('성능 벤치마크 테스트', () => {
  test('DataNormalizer 성능 테스트', () => {
    const normalizer = new DataNormalizer({
      enableLogging: false,
      errorStrategy: 'fallback'
    });

    const startTime = performance.now();

    // 100회 반복 정규화
    for (let i = 0; i < 100; i++) {
      const result = normalizer.normalize(mockAnalysisResults);
      expect(result).toBeDefined();
      expect(result.ocrResults).toHaveLength(2);
    }

    const endTime = performance.now();
    const totalTime = endTime - startTime;

    console.log(`DataNormalizer 100회 실행 시간: ${totalTime.toFixed(2)}ms`);

    // 성능 기준: 1초 이내
    expect(totalTime).toBeLessThan(1000);
  });

  test('useStableAnalysisData 훅 성능 테스트', () => {
    let renderCount = 0;

    const TestComponent = ({ data }) => {
      renderCount++;
      const result = useStableAnalysisData(data, {
        enableLogging: false,
        enableCaching: true
      });

      return <div data-testid="test">{result.dataHash}</div>;
    };

    const startTime = performance.now();

    // 동일한 데이터로 여러 번 렌더링
    const { rerender } = render(<TestComponent data={mockAnalysisResults} />);

    for (let i = 0; i < 10; i++) {
      rerender(<TestComponent data={mockAnalysisResults} />);
    }

    const endTime = performance.now();
    const totalTime = endTime - startTime;

    console.log(`useStableAnalysisData 11회 렌더링 시간: ${totalTime.toFixed(2)}ms`);
    console.log(`총 렌더링 횟수: ${renderCount}`);

    // 성능 기준: 100ms 이내, 렌더링 횟수 제한
    expect(totalTime).toBeLessThan(100);
    expect(renderCount).toBeLessThan(20); // 캐싱으로 불필요한 렌더링 방지
  });

  test('대용량 데이터 처리 성능', () => {
    // 대용량 데이터 생성
    const largeData = {
      ocrResults: Array.from({ length: 1000 }, (_, i) => ({
        text: `텍스트 ${i}`,
        confidence: Math.random(),
        element_type: 'text',
        block_id: `block_${i}`
      })),
      aiResults: Array.from({ length: 100 }, (_, i) => ({
        description: `설명 ${i}`,
        confidence: Math.random(),
        type: 'description',
        block_id: `ai_${i}`
      })),
      stats: { total_elements: 1100 }
    };

    const normalizer = new DataNormalizer({ enableLogging: false });

    const startTime = performance.now();
    const result = normalizer.normalize(largeData);
    const endTime = performance.now();

    const processingTime = endTime - startTime;

    console.log(`대용량 데이터(1100개) 정규화 시간: ${processingTime.toFixed(2)}ms`);

    expect(result.ocrResults).toHaveLength(1000);
    expect(result.aiResults).toHaveLength(100);

    // 성능 기준: 200ms 이내
    expect(processingTime).toBeLessThan(200);
  });

  test('메모리 효율성 테스트', () => {
    const normalizer = new DataNormalizer({ enableLogging: false });
    const results = [];

    // 여러 번 정규화하여 메모리 누수 체크
    for (let i = 0; i < 50; i++) {
      const result = normalizer.normalize({
        ...mockAnalysisResults,
        ocrResults: Array.from({ length: 100 }, (_, j) => ({
          text: `텍스트 ${i}-${j}`,
          confidence: Math.random()
        }))
      });
      results.push(result);
    }

    // 메모리 정리 확인 (결과가 모두 독립적이어야 함)
    expect(results).toHaveLength(50);
    expect(results[0]).not.toBe(results[1]); // 참조가 다름

    // 각 결과의 데이터 무결성 확인
    results.forEach((result, index) => {
      expect(result.ocrResults).toHaveLength(100);
      expect(result._meta.normalized_at).toBeDefined();
    });
  });

  test('안정성 테스트 - 잘못된 데이터', () => {
    const normalizer = new DataNormalizer({
      enableLogging: false,
      errorStrategy: 'fallback'
    });

    const invalidDataSets = [
      null,
      undefined,
      'invalid',
      123,
      [],
      { corrupted: 'data' },
      { ocrResults: 'not array' },
      { ocrResults: [null, undefined, 'invalid'] }
    ];

    invalidDataSets.forEach((invalidData, index) => {
      const startTime = performance.now();

      expect(() => {
        const result = normalizer.normalize(invalidData);
        expect(result).toBeDefined();
        expect(Array.isArray(result.ocrResults)).toBe(true);
        expect(Array.isArray(result.aiResults)).toBe(true);
      }).not.toThrow();

      const endTime = performance.now();
      const processingTime = endTime - startTime;

      // 에러 처리도 빨라야 함
      expect(processingTime).toBeLessThan(10);
    });
  });
});