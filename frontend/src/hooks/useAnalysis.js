import { useState, useCallback } from 'react';
import { apiService } from '../services/apiService';

export const useAnalysis = () => {
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [progress, setProgress] = useState(0);
  const [status, setStatus] = useState('');
  const [analysisResults, setAnalysisResults] = useState(null);
  const [structuredResult, setStructuredResult] = useState(null);

  const analyzeWorksheet = useCallback(async ({ image, model, apiKey, mode }) => {
    setIsAnalyzing(true);
    setProgress(0);
    setStatus('분석을 시작합니다...');

    let progressInterval;

    try {
      // 점진적 프로그레스 업데이트 함수
      const updateProgress = (steps, initialDelay = 500) => {
        let currentStep = 0;
        clearInterval(progressInterval);
        progressInterval = setInterval(() => {
          if (currentStep < steps.length) {
            setProgress(steps[currentStep].progress);
            setStatus(steps[currentStep].status);
            currentStep++;
          } else {
            clearInterval(progressInterval);
          }
        }, initialDelay);
      };

      // 1단계: 기본 분석 (모든 모드에서 공통)
      const initialProgress = [
        { progress: 10, status: '서버에 업로드 중...' },
        { progress: 25, status: 'OCR 분석 중...' },
        { progress: 50, status: '레이아웃 분석 중...' },
      ];
      updateProgress(initialProgress);

      const baseResponse = await apiService.analyzeWorksheet({
        image,
        modelChoice: model,
        apiKey,
        endpoint: '/api/document/analyze'
      });

      if (!baseResponse.success) {
        throw new Error(baseResponse.error || '기본 분석에 실패했습니다.');
      }

      // 기본 분석 결과 설정
      setAnalysisResults({
        layoutImageUrl: baseResponse.layout_image_url,
        jsonUrl: baseResponse.json_url,
        stats: baseResponse.stats,
        ocrResults: baseResponse.ocr_results || [],
        aiResults: baseResponse.ai_results || [],
        formattedText: baseResponse.formatted_text || ''
      });

      // 2단계: 구조화 분석 (structured 모드인 경우에만)
      if (mode === 'structured') {
        const structuredProgress = [
          { progress: 75, status: '구조화된 결과 생성 중...' },
          { progress: 90, status: 'AI 기반 구조화 분석 중...' }
        ];
        updateProgress(structuredProgress, 700);

        const jobId = baseResponse.jobId;
        if (!jobId) {
          throw new Error('분석 작업 ID를 찾을 수 없습니다.');
        }

        const structuredResponse = await apiService.getStructuredCIM(jobId);

        if (structuredResponse.success) {
          setStructuredResult(structuredResponse.structuredResult);
        } else {
          // 구조화 분석 실패 시에도 기본 결과는 유지
          throw new Error(structuredResponse.errorMessage || '구조화된 분석에 실패했습니다.');
        }
      } else {
        setStructuredResult(null);
      }

      clearInterval(progressInterval);
      setProgress(100);
      setStatus('분석 완료! 결과를 확인해보세요.');

      setTimeout(() => {
        setIsAnalyzing(false);
        setProgress(0);
        setStatus('');
      }, 2000);

    } catch (error) {
      console.error('분석 오류:', error);
      clearInterval(progressInterval);
      
      let errorMessage = '분석 중 오류가 발생했습니다.';
      if (error.response?.status === 413) {
        errorMessage = '이미지 파일이 너무 큽니다. 10MB 이하의 파일을 사용해주세요.';
      } else if (error.response?.status === 422) {
        errorMessage = '지원하지 않는 이미지 형식입니다. JPG, PNG, GIF 파일을 사용해주세요.';
      } else if (error.response?.data?.detail) {
        errorMessage = error.response.data.detail;
      } else if (error.message) {
        errorMessage = error.message;
      }

      alert(errorMessage);
      setIsAnalyzing(false);
      setProgress(0);
      setStatus('');
    }
  }, []);

  const reset = useCallback(() => {
    setAnalysisResults(null);
    setStructuredResult(null);
    setProgress(0);
    setStatus('');
    setIsAnalyzing(false);
  }, []);

  return {
    isAnalyzing,
    progress,
    status,
    analysisResults,
    structuredResult,
    analyzeWorksheet,
    reset
  };
};
