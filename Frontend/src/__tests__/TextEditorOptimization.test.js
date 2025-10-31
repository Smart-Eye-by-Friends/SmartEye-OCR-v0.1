/**
 * TextEditor 최적화 솔루션 통합 테스트
 * 무한 루프 방지, 성능 최적화, 메모리 효율성 테스트
 */

import React from 'react';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import '@testing-library/jest-dom';

// 테스트 대상 컴포넌트 및 훅
import TextEditorTabOptimized from '../components/TextEditorTabOptimized';
import { useTextEditorState } from '../hooks/useTextEditorState';
import { useStableAnalysisData } from '../hooks/useStableAnalysisData';
import { usePerformanceGuard } from '../components/PerformanceGuard';
import DataNormalizer from '../utils/DataNormalizer';

// Mock 설정
jest.mock('@tinymce/tinymce-react', () => ({
  Editor: ({ value, onEditorChange, ...props }) => (
    <textarea
      data-testid="tinymce-editor"
      value={value || ''}
      onChange={(e) => onEditorChange && onEditorChange(e.target.value)}
      {...props}
    />
  )
}));

const mockConvertCimToText = jest.fn();

jest.mock('../services/apiService', () => ({
  apiService: {
    convertCimToText: mockConvertCimToText
  }
}));

jest.mock('../utils/extensionCompatibility', () => ({
  getTinyMCEExtensionSafeConfig: () => ({ theme: 'silver' })
}));

// 테스트 데이터
const mockAnalysisResults = {
  ocrResults: [
    {
      text: '고품질 OCR 텍스트',
      confidence: 0.95,
      element_type: 'text',
      block_id: 'ocr_1'
    },
    {
      text: '중간 품질 OCR 텍스트',
      confidence: 0.6,
      element_type: 'text',
      block_id: 'ocr_2'
    }
  ],
  aiResults: [
    {
      description: 'AI 생성 설명',
      confidence: 0.8,
      type: 'description',
      block_id: 'ai_1'
    }
  ],
  stats: {
    total_elements: 3,
    average_confidence: 0.78
  },
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

const defaultProps = {
  formattedText: '포맷된 텍스트',
  editableText: '편집 가능한 텍스트',
  onTextChange: jest.fn(),
  onSaveText: jest.fn(),
  onResetText: jest.fn(),
  analysisResults: mockAnalysisResults
};

// ===========================
// 🧪 useTextEditorState 훅 테스트
// ===========================
describe('useTextEditorState Hook', () => {
  let TestComponent;

  beforeEach(() => {
    TestComponent = () => {
      const editorState = useTextEditorState({
        initialContent: '초기 텍스트',
        enableLogging: false
      });

      return (
        <div>
          <div data-testid="content">{editorState.editorContent}</div>
          <div data-testid="editing">{editorState.isEditing ? 'true' : 'false'}</div>
          <div data-testid="error">{editorState.error.hasError ? editorState.error.message : 'no-error'}</div>
          <button data-testid="set-editing" onClick={() => editorState.setEditing(true)}>편집 모드</button>
          <button data-testid="set-content" onClick={() => editorState.setContent('새 텍스트')}>텍스트 설정</button>
          <button data-testid="set-error" onClick={() => editorState.setError('테스트 에러')}>에러 설정</button>
          <button data-testid="clear-error" onClick={() => editorState.clearError()}>에러 해제</button>
        </div>
      );
    };
  });

  test('초기 상태가 올바르게 설정됨', () => {
    render(<TestComponent />);

    expect(screen.getByTestId('content')).toHaveTextContent('초기 텍스트');
    expect(screen.getByTestId('editing')).toHaveTextContent('false');
    expect(screen.getByTestId('error')).toHaveTextContent('no-error');
  });

  test('편집 모드 토글이 정상 작동', async () => {
    render(<TestComponent />);

    fireEvent.click(screen.getByTestId('set-editing'));

    await waitFor(() => {
      expect(screen.getByTestId('editing')).toHaveTextContent('true');
    });
  });

  test('텍스트 내용 변경이 정상 작동', async () => {
    render(<TestComponent />);

    fireEvent.click(screen.getByTestId('set-content'));

    await waitFor(() => {
      expect(screen.getByTestId('content')).toHaveTextContent('새 텍스트');
    });
  });

  test('에러 상태 관리가 정상 작동', async () => {
    render(<TestComponent />);

    // 에러 설정
    fireEvent.click(screen.getByTestId('set-error'));

    await waitFor(() => {
      expect(screen.getByTestId('error')).toHaveTextContent('테스트 에러');
    });

    // 에러 해제
    fireEvent.click(screen.getByTestId('clear-error'));

    await waitFor(() => {
      expect(screen.getByTestId('error')).toHaveTextContent('no-error');
    });
  });

  test('배치 업데이트가 정상 작동', async () => {
    const TestBatchComponent = () => {
      const editorState = useTextEditorState({ enableLogging: false });

      return (
        <div>
          <div data-testid="content">{editorState.editorContent}</div>
          <div data-testid="editing">{editorState.isEditing ? 'true' : 'false'}</div>
          <button
            data-testid="batch-update"
            onClick={() => editorState.batchUpdate({
              editorContent: '배치 텍스트',
              isEditing: true
            })}
          >
            배치 업데이트
          </button>
        </div>
      );
    };

    render(<TestBatchComponent />);

    fireEvent.click(screen.getByTestId('batch-update'));

    await waitFor(() => {
      expect(screen.getByTestId('content')).toHaveTextContent('배치 텍스트');
      expect(screen.getByTestId('editing')).toHaveTextContent('true');
    });
  });
});

// ===========================
// 🔄 useStableAnalysisData 훅 테스트
// ===========================
describe('useStableAnalysisData Hook', () => {
  test('분석 데이터가 올바르게 정규화됨', () => {
    let hookResult;

    const TestComponent = () => {
      hookResult = useStableAnalysisData(mockAnalysisResults, {
        enableLogging: false
      });

      return <div data-testid="test">테스트</div>;
    };

    render(<TestComponent />);

    expect(hookResult.normalizedData).toBeDefined();
    expect(hookResult.normalizedData.ocrResults).toHaveLength(2);
    expect(hookResult.normalizedData.aiResults).toHaveLength(1);
    expect(hookResult.availability.hasOCRData).toBe(true);
    expect(hookResult.availability.hasAIData).toBe(true);
  });

  test('텍스트 추출 함수들이 정상 작동', () => {
    let hookResult;

    const TestComponent = () => {
      hookResult = useStableAnalysisData(mockAnalysisResults, {
        enableLogging: false
      });

      return <div data-testid="test">테스트</div>;
    };

    render(<TestComponent />);

    const highConfidenceText = hookResult.textExtractors.getHighConfidenceText();
    expect(highConfidenceText).toBeDefined();
    expect(highConfidenceText.text).toBe('고품질 OCR 텍스트');
    expect(highConfidenceText.confidence).toBe(0.95);

    const allOCRText = hookResult.textExtractors.getAllOCRText();
    expect(allOCRText).toBeDefined();
    expect(allOCRText.text).toContain('고품질 OCR 텍스트');
    expect(allOCRText.text).toContain('중간 품질 OCR 텍스트');
  });

  test('캐싱이 정상 작동', () => {
    let hookResult1, hookResult2;

    const TestComponent = ({ data }) => {
      const result = useStableAnalysisData(data, {
        enableCaching: true,
        enableLogging: false
      });

      if (!hookResult1) hookResult1 = result;
      else hookResult2 = result;

      return <div data-testid="test">테스트</div>;
    };

    const { rerender } = render(<TestComponent data={mockAnalysisResults} />);

    // 같은 데이터로 재렌더링
    rerender(<TestComponent data={mockAnalysisResults} />);

    // 같은 데이터 해시를 가져야 함
    expect(hookResult1.dataHash).toBe(hookResult2.dataHash);
  });
});

// ===========================
// 🛡️ usePerformanceGuard 훅 테스트
// ===========================
describe('usePerformanceGuard Hook', () => {
  test('렌더링 추적이 정상 작동', () => {
    let hookResult;

    const TestComponent = () => {
      hookResult = usePerformanceGuard('TestComponent', {
        enabled: true,
        enableLogging: false
      });

      hookResult.trackRender();

      return <div data-testid="test">테스트</div>;
    };

    render(<TestComponent />);

    expect(hookResult.performanceStats.renderCount).toBeGreaterThan(0);
  });

  test('과도한 렌더링 감지', async () => {
    let hookResult;
    const mockOnWarning = jest.fn();

    const TestComponent = ({ trigger }) => {
      hookResult = usePerformanceGuard('TestComponent', {
        enabled: true,
        enableLogging: false,
        onWarning: mockOnWarning,
        thresholds: { RENDER_COUNT: 5 }
      });

      hookResult.trackRender();

      return <div data-testid="test">{trigger}</div>;
    };

    const { rerender } = render(<TestComponent trigger={1} />);

    // 여러 번 재렌더링 유발
    for (let i = 2; i <= 10; i++) {
      rerender(<TestComponent trigger={i} />);
    }

    await waitFor(() => {
      expect(mockOnWarning).toHaveBeenCalled();
    });
  });
});

// ===========================
// 🏗️ DataNormalizer 클래스 테스트
// ===========================
describe('DataNormalizer Class', () => {
  let normalizer;

  beforeEach(() => {
    normalizer = new DataNormalizer({
      enableLogging: false,
      errorStrategy: 'fallback'
    });
  });

  test('CIM 구조화 데이터 정규화', () => {
    const result = normalizer.normalize(mockAnalysisResults);

    expect(result).toBeDefined();
    expect(result.ocrResults).toBeDefined();
    expect(result.aiResults).toBeDefined();
    expect(result._meta).toBeDefined();
    expect(result._meta.schema.type).toBe('cim_structured');
  });

  test('레거시 데이터 감지 및 정규화', () => {
    const legacyData = {
      ocrResults: [{ text: '레거시 텍스트', confidence: 0.9 }],
      aiResults: [{ description: '레거시 AI', confidence: 0.8 }],
      stats: { total_elements: 2 }
    };

    const result = normalizer.normalize(legacyData);

    expect(result._meta.schema.type).toBe('legacy');
    expect(result.ocrResults).toHaveLength(1);
    expect(result.aiResults).toHaveLength(1);
  });

  test('에러 처리 전략', () => {
    const invalidData = { invalid: 'data' };

    const result = normalizer.normalize(invalidData);

    expect(result).toBeDefined();
    expect(result.ocrResults).toEqual([]);
    expect(result.aiResults).toEqual([]);
  });

  test('바운딩 박스 정규화', () => {
    const bboxData = {
      ocrResults: [
        {
          text: '테스트',
          confidence: 0.9,
          bbox: [10, 20, 100, 50]
        }
      ]
    };

    const result = normalizer.normalize(bboxData);

    expect(result.ocrResults[0].bbox).toEqual({
      x: 10, y: 20, width: 100, height: 50,
      x1: 10, y1: 20, x2: 110, y2: 70
    });
  });
});

// ===========================
// 🚀 TextEditorTabOptimized 컴포넌트 통합 테스트
// ===========================
describe('TextEditorTabOptimized Integration', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('기본 렌더링이 정상 작동', () => {
    render(<TextEditorTabOptimized {...defaultProps} />);

    expect(screen.getByText('📝 텍스트 편집기')).toBeInTheDocument();
    expect(screen.getByText('✏️ 편집 모드')).toBeInTheDocument();
  });

  test('편집 모드 전환이 정상 작동', async () => {
    render(<TextEditorTabOptimized {...defaultProps} />);

    const editButton = screen.getByText('✏️ 편집 모드');
    fireEvent.click(editButton);

    await waitFor(() => {
      expect(screen.getByTestId('tinymce-editor')).toBeInTheDocument();
      expect(screen.getByText('📖 읽기 모드')).toBeInTheDocument();
    });
  });

  test('텍스트 변경이 정상 작동', async () => {
    const mockOnTextChange = jest.fn();

    render(
      <TextEditorTabOptimized
        {...defaultProps}
        onTextChange={mockOnTextChange}
      />
    );

    // 편집 모드 활성화
    fireEvent.click(screen.getByText('✏️ 편집 모드'));

    await waitFor(() => {
      const editor = screen.getByTestId('tinymce-editor');
      fireEvent.change(editor, { target: { value: '새로운 텍스트' } });
    });

    // 디바운싱 대기
    await act(async () => {
      await new Promise(resolve => setTimeout(resolve, 600));
    });

    expect(mockOnTextChange).toHaveBeenCalledWith('새로운 텍스트');
  });

  test('리셋 기능이 정상 작동', async () => {
    const mockOnResetText = jest.fn();

    render(
      <TextEditorTabOptimized
        {...defaultProps}
        onResetText={mockOnResetText}
      />
    );

    const resetButton = screen.getByText(/원본으로 복원/);
    fireEvent.click(resetButton);

    await waitFor(() => {
      expect(mockOnResetText).toHaveBeenCalled();
    });
  });

  test('에러 상태 표시', async () => {
    const errorProps = {
      ...defaultProps,
      formattedText: 'error: 텍스트 추출 실패',
      analysisResults: {
        ...mockAnalysisResults,
        ocrResults: [
          {
            text: 'error: OCR 처리 실패',
            confidence: 0.1,
            element_type: 'error'
          }
        ]
      }
    };

    render(<TextEditorTabOptimized {...errorProps} />);

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });
  });

  test('빈 데이터 상태 처리', () => {
    const emptyProps = {
      ...defaultProps,
      formattedText: '',
      editableText: '',
      analysisResults: null
    };

    render(<TextEditorTabOptimized {...emptyProps} />);

    expect(screen.getByText('텍스트 결과가 없습니다')).toBeInTheDocument();
  });

  test('CIM 데이터 변환 기능', async () => {
    mockConvertCimToText.mockResolvedValue({
      formattedText: '변환된 CIM 텍스트'
    });

    render(<TextEditorTabOptimized {...defaultProps} />);

    const convertButton = screen.getByText('🔄 CIM→텍스트');
    fireEvent.click(convertButton);

    await waitFor(() => {
      expect(mockConvertCimToText).toHaveBeenCalledWith(
        mockAnalysisResults.cimData
      );
    });
  });

  test('성능 모니터링 통합', () => {
    const { container } = render(<TextEditorTabOptimized {...defaultProps} />);

    // 개발 모드에서 성능 통계 표시 확인
    if (process.env.NODE_ENV === 'development') {
      expect(container.textContent).toMatch(/렌더링:/);
    }
  });
});

// ===========================
// 🔥 성능 스트레스 테스트
// ===========================
describe('Performance Stress Tests', () => {
  test('대용량 데이터 처리 성능', async () => {
    const largeData = {
      ocrResults: Array.from({ length: 1000 }, (_, i) => ({
        text: `테스트 텍스트 ${i}`,
        confidence: Math.random(),
        element_type: 'text',
        block_id: `block_${i}`
      })),
      aiResults: Array.from({ length: 100 }, (_, i) => ({
        description: `AI 설명 ${i}`,
        confidence: Math.random(),
        type: 'description',
        block_id: `ai_${i}`
      })),
      stats: { total_elements: 1100 }
    };

    const startTime = performance.now();

    render(
      <TextEditorTabOptimized
        {...defaultProps}
        analysisResults={largeData}
      />
    );

    const endTime = performance.now();
    const renderTime = endTime - startTime;

    console.log(`대용량 데이터 렌더링 시간: ${renderTime.toFixed(2)}ms`);

    // 성능 기준: 500ms 이내
    expect(renderTime).toBeLessThan(500);
  });

  test('메모리 사용량 모니터링', () => {
    const initialMemory = performance.memory?.usedJSHeapSize || 0;

    // 여러 인스턴스 생성
    const components = Array.from({ length: 10 }, (_, i) => (
      <TextEditorTabOptimized
        key={i}
        {...defaultProps}
        analysisResults={{
          ...mockAnalysisResults,
          ocrResults: Array.from({ length: 100 }, (_, j) => ({
            text: `텍스트 ${i}-${j}`,
            confidence: Math.random()
          }))
        }}
      />
    ));

    const { unmount } = render(
      <div>
        {components}
      </div>
    );

    const peakMemory = performance.memory?.usedJSHeapSize || 0;

    // 정리
    unmount();

    const finalMemory = performance.memory?.usedJSHeapSize || 0;

    console.log('메모리 사용량:', {
      initial: initialMemory,
      peak: peakMemory,
      final: finalMemory,
      increase: peakMemory - initialMemory
    });

    // 메모리 증가량이 50MB 이내인지 확인
    expect(peakMemory - initialMemory).toBeLessThan(50 * 1024 * 1024);
  });
});

// ===========================
// 🔒 안정성 테스트
// ===========================
describe('Stability Tests', () => {
  test('무한 루프 방지 메커니즘', async () => {
    let renderCount = 0;
    const MAX_RENDERS = 20;

    const ProblematicComponent = () => {
      renderCount++;

      const editorState = useTextEditorState({
        enableLogging: false,
        onStateChange: () => {
          // 의도적으로 문제가 있는 코드 (무한 루프 유발)
          if (renderCount < MAX_RENDERS) {
            editorState?.setContent(`렌더링 ${renderCount}`);
          }
        }
      });

      if (renderCount > MAX_RENDERS) {
        throw new Error('무한 루프가 방지되지 않았습니다');
      }

      return (
        <div data-testid="problematic">
          렌더링 횟수: {renderCount}
        </div>
      );
    };

    expect(() => {
      render(<ProblematicComponent />);
    }).not.toThrow();

    expect(renderCount).toBeLessThanOrEqual(MAX_RENDERS);
  });

  test('잘못된 데이터 처리', () => {
    const invalidDataSets = [
      null,
      undefined,
      'invalid string',
      123,
      [],
      { corrupted: 'data' },
      { ocrResults: 'not array' },
      { ocrResults: [null, undefined, 'invalid'] }
    ];

    invalidDataSets.forEach((invalidData) => {
      expect(() => {
        render(
          <TextEditorTabOptimized
            {...defaultProps}
            analysisResults={invalidData}
          />
        );
      }).not.toThrow();
    });
  });

  test('API 실패 처리', async () => {
    mockConvertCimToText.mockRejectedValue(new Error('API 실패'));

    render(<TextEditorTabOptimized {...defaultProps} />);

    const convertButton = screen.getByText('🔄 CIM→텍스트');
    fireEvent.click(convertButton);

    await waitFor(() => {
      // 에러가 발생해도 컴포넌트가 충돌하지 않아야 함
      expect(screen.getByText('📝 텍스트 편집기')).toBeInTheDocument();
    });
  });
});

export default {
  mockAnalysisResults,
  defaultProps
};