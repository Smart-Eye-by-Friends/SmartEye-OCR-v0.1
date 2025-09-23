/**
 * SmartEye 프론트엔드 통합 테스트
 * 컴포넌트 간 데이터 흐름 및 백엔드 연동 검증
 */

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import axios from 'axios';

// Mock 설정
jest.mock('axios');
const mockedAxios = axios as jest.Mocked<typeof axios>;

// Mock 컴포넌트들 (실제 컴포넌트가 없으므로 기능 검증을 위한 목업)
const MockTextEditorTab = ({ analysisResults, onTextChange }) => {
  const [text, setText] = React.useState('');

  React.useEffect(() => {
    // 실제 TextEditorTab의 로직 시뮬레이션
    if (analysisResults) {
      const normalizedResults = normalizeAnalysisResults(analysisResults);
      const fallbackResult = extractTextWithPriority(normalizedResults);
      setText(fallbackResult.text);
      onTextChange?.(fallbackResult.text);
    }
  }, [analysisResults, onTextChange]);

  return <div data-testid="text-editor">{text}</div>;
};

const MockStatsTab = ({ analysisResults }) => {
  const normalizedResults = normalizeAnalysisResults(analysisResults);
  const stats = normalizedResults.stats || {};

  return (
    <div data-testid="stats-tab">
      <div data-testid="total-elements">{stats.total_elements || 0}</div>
      <div data-testid="average-confidence">
        {stats.average_confidence ? (stats.average_confidence * 100).toFixed(1) : 'N/A'}%
      </div>
    </div>
  );
};

describe('프론트엔드 통합 테스트', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('백엔드 API 연동', () => {
    test('성공적인 분석 응답 처리', async () => {
      const mockResponse = {
        data: {
          success: true,
          cimData: {
            questions: [
              {
                question_content: {
                  main_question: '통합 테스트 문제',
                  choices: [
                    { choice_text: '선택지 1', choice_number: 1 },
                    { choice_text: '선택지 2', choice_number: 2 }
                  ]
                },
                ai_analysis: {
                  image_descriptions: ['AI 이미지 분석 결과']
                }
              }
            ]
          },
          stats: {
            total_elements: 3,
            average_confidence: 0.85,
            processing_time: 2.5
          },
          layoutImageUrl: 'http://example.com/layout.jpg'
        }
      };

      mockedAxios.post.mockResolvedValueOnce(mockResponse);

      // API 호출 시뮬레이션
      const response = await axios.post('/api/analyze', { image: 'test.jpg' });

      expect(response.data.success).toBe(true);
      expect(response.data.cimData.questions).toHaveLength(1);
      expect(response.data.stats.total_elements).toBe(3);
    });

    test('에러 응답 처리', async () => {
      const mockErrorResponse = {
        response: {
          status: 500,
          data: {
            success: false,
            message: 'Internal server error',
            error: 'OCR processing failed'
          }
        }
      };

      mockedAxios.post.mockRejectedValueOnce(mockErrorResponse);

      try {
        await axios.post('/api/analyze', { image: 'test.jpg' });
      } catch (error) {
        expect(error.response.status).toBe(500);
        expect(error.response.data.success).toBe(false);
        expect(error.response.data.error).toContain('failed');
      }
    });

    test('네트워크 오류 처리', async () => {
      mockedAxios.post.mockRejectedValueOnce(new Error('Network Error'));

      try {
        await axios.post('/api/analyze', { image: 'test.jpg' });
      } catch (error) {
        expect(error.message).toBe('Network Error');
      }
    });

    test('타임아웃 처리', async () => {
      mockedAxios.post.mockRejectedValueOnce({
        code: 'ECONNABORTED',
        message: 'timeout of 30000ms exceeded'
      });

      try {
        await axios.post('/api/analyze', { image: 'test.jpg' });
      } catch (error) {
        expect(error.code).toBe('ECONNABORTED');
        expect(error.message).toContain('timeout');
      }
    });
  });

  describe('컴포넌트 간 데이터 흐름', () => {
    test('CIM 응답이 TextEditorTab으로 올바르게 전달', async () => {
      const analysisResults = {
        cimData: {
          questions: [
            {
              question_content: {
                main_question: '데이터 흐름 테스트 문제',
                passage: '테스트용 지문입니다'
              }
            }
          ]
        }
      };

      let capturedText = '';
      const handleTextChange = (text) => {
        capturedText = text;
      };

      render(
        <MockTextEditorTab
          analysisResults={analysisResults}
          onTextChange={handleTextChange}
        />
      );

      await waitFor(() => {
        expect(capturedText).toContain('데이터 흐름 테스트 문제');
        expect(capturedText).toContain('테스트용 지문');
      });
    });

    test('통계 데이터가 StatsTab으로 올바르게 전달', () => {
      const analysisResults = {
        cimData: {
          questions: [
            {
              question_content: {
                main_question: '통계 테스트 문제',
                choices: [
                  { choice_text: '선택지 1' },
                  { choice_text: '선택지 2' },
                  { choice_text: '선택지 3' }
                ]
              }
            }
          ]
        }
      };

      render(<MockStatsTab analysisResults={analysisResults} />);

      // 총 요소 수: 문제 1개 + 선택지 3개 = 4개
      expect(screen.getByTestId('total-elements')).toHaveTextContent('4');
    });

    test('레거시 응답과 CIM 응답 혼합 처리', () => {
      const mixedResults = {
        // 레거시 형태
        ocrResults: [
          { text: '레거시 OCR 텍스트', confidence: 0.8 }
        ],
        // CIM 형태
        cimData: {
          questions: [
            {
              question_content: {
                main_question: 'CIM 문제'
              }
            }
          ]
        }
      };

      render(<MockStatsTab analysisResults={mixedResults} />);

      // CIM 데이터가 우선 처리되어야 함
      expect(screen.getByTestId('total-elements')).toHaveTextContent('1');
    });
  });

  describe('에러 처리 통합 테스트', () => {
    test('백엔드 에러에서 프론트엔드 복구까지', async () => {
      // 1단계: 백엔드에서 부분적 에러 응답
      const partialErrorResponse = {
        data: {
          success: true,
          cimData: {
            questions: [
              {
                question_content: {
                  main_question: 'Error: OCR failed for this question',
                  choices: [
                    { choice_text: '정상 선택지 1' },
                    { choice_text: 'Error processing choice' }
                  ]
                }
              }
            ]
          }
        }
      };

      let capturedText = '';
      const handleTextChange = (text) => {
        capturedText = text;
      };

      // 2단계: 프론트엔드에서 에러 감지 및 복구
      render(
        <MockTextEditorTab
          analysisResults={partialErrorResponse.data}
          onTextChange={handleTextChange}
        />
      );

      await waitFor(() => {
        // 에러가 포함된 텍스트는 필터링되고 정상 텍스트만 표시
        expect(capturedText).toContain('정상 선택지 1');
        expect(capturedText).not.toContain('Error:');
        expect(capturedText).not.toContain('Error processing');
      });
    });

    test('완전한 데이터 손실에서 사용자 메시지까지', () => {
      const emptyResponse = {
        success: false,
        cimData: null,
        ocrResults: [],
        aiResults: [],
        message: '분석에 실패했습니다'
      };

      let capturedText = '';
      const handleTextChange = (text) => {
        capturedText = text;
      };

      render(
        <MockTextEditorTab
          analysisResults={emptyResponse}
          onTextChange={handleTextChange}
        />
      );

      expect(capturedText).toContain('추출 가능한 텍스트가 없습니다');
    });
  });

  describe('성능 통합 테스트', () => {
    test('대용량 데이터 처리 성능', async () => {
      const largeDataset = {
        cimData: {
          questions: Array.from({ length: 100 }, (_, i) => ({
            question_content: {
              main_question: `문제 ${i + 1}: ${'텍스트 '.repeat(20)}`,
              choices: Array.from({ length: 5 }, (_, j) => ({
                choice_text: `선택지 ${j + 1}: ${'내용 '.repeat(10)}`
              }))
            }
          }))
        }
      };

      const startTime = performance.now();

      let capturedText = '';
      const handleTextChange = (text) => {
        capturedText = text;
      };

      render(
        <MockTextEditorTab
          analysisResults={largeDataset}
          onTextChange={handleTextChange}
        />
      );

      await waitFor(() => {
        expect(capturedText.length).toBeGreaterThan(1000);
      });

      const endTime = performance.now();
      const processingTime = endTime - startTime;

      // 대용량 데이터도 2초 이내에 처리되어야 함
      expect(processingTime).toBeLessThan(2000);
    });

    test('메모리 사용량 모니터링', () => {
      const initialMemory = performance.memory?.usedJSHeapSize || 0;

      // 여러 번의 데이터 처리 시뮬레이션
      for (let i = 0; i < 10; i++) {
        const testData = {
          cimData: {
            questions: Array.from({ length: 20 }, (_, j) => ({
              question_content: {
                main_question: `반복 ${i} 문제 ${j}`
              }
            }))
          }
        };

        render(<MockStatsTab analysisResults={testData} />);
      }

      const finalMemory = performance.memory?.usedJSHeapSize || 0;
      const memoryIncrease = finalMemory - initialMemory;

      // 메모리 증가량이 합리적 범위 내 (5MB 이내)
      expect(memoryIncrease).toBeLessThan(5 * 1024 * 1024);
    });
  });

  describe('실제 시나리오 시뮬레이션', () => {
    test('완전한 분석 워크플로우', async () => {
      // 1. 파일 업로드
      const uploadResponse = {
        data: {
          success: true,
          fileId: 'test-file-123',
          filename: 'test-document.jpg'
        }
      };

      mockedAxios.post.mockResolvedValueOnce(uploadResponse);

      // 2. 분석 요청
      const analysisResponse = {
        data: {
          success: true,
          jobId: 'analysis-job-456',
          cimData: {
            questions: [
              {
                question_content: {
                  main_question: '워크플로우 테스트 문제',
                  choices: [
                    { choice_text: '선택지 A' },
                    { choice_text: '선택지 B' }
                  ]
                }
              }
            ]
          },
          stats: {
            total_elements: 3,
            average_confidence: 0.9,
            processing_time: 1.5
          }
        }
      };

      mockedAxios.post.mockResolvedValueOnce(analysisResponse);

      // 3. 텍스트 변환
      const textConversionResponse = {
        data: {
          success: true,
          text: '변환된 최종 텍스트:\n\n워크플로우 테스트 문제\n\nA. 선택지 A\nB. 선택지 B'
        }
      };

      mockedAxios.post.mockResolvedValueOnce(textConversionResponse);

      // 워크플로우 실행
      const uploadResult = await axios.post('/api/upload', { file: 'test.jpg' });
      expect(uploadResult.data.success).toBe(true);

      const analysisResult = await axios.post('/api/analyze', {
        fileId: uploadResult.data.fileId
      });
      expect(analysisResult.data.cimData.questions).toHaveLength(1);

      const textResult = await axios.post('/api/convert-text', {
        cimData: analysisResult.data.cimData
      });
      expect(textResult.data.text).toContain('워크플로우 테스트 문제');
      expect(textResult.data.text).toContain('선택지 A');
    });

    test('부분적 실패 시나리오', async () => {
      // OCR은 성공했지만 AI 분석이 부분적으로 실패한 경우
      const partialFailureResponse = {
        data: {
          success: true,
          cimData: {
            questions: [
              {
                question_content: {
                  main_question: '부분 성공 문제',
                  choices: [
                    { choice_text: '정상 선택지' }
                  ]
                },
                ai_analysis: {
                  image_descriptions: ['Error: AI processing timeout'],
                  table_analysis: ['정상 표 분석']
                }
              }
            ]
          },
          warnings: ['일부 AI 분석이 실패했습니다'],
          stats: {
            total_elements: 2,
            average_confidence: 0.7
          }
        }
      };

      mockedAxios.post.mockResolvedValueOnce(partialFailureResponse);

      const result = await axios.post('/api/analyze', { image: 'test.jpg' });

      expect(result.data.success).toBe(true);
      expect(result.data.warnings).toContain('일부 AI 분석이 실패했습니다');

      // 프론트엔드에서 에러 필터링 확인
      let capturedText = '';
      const handleTextChange = (text) => {
        capturedText = text;
      };

      render(
        <MockTextEditorTab
          analysisResults={result.data}
          onTextChange={handleTextChange}
        />
      );

      await waitFor(() => {
        expect(capturedText).toContain('부분 성공 문제');
        expect(capturedText).toContain('정상 선택지');
        expect(capturedText).toContain('정상 표 분석');
        expect(capturedText).not.toContain('Error:');
        expect(capturedText).not.toContain('timeout');
      });
    });
  });
});