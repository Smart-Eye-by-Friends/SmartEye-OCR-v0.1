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
    setAnalysisResults(null);
    setStructuredResult(null);

    let pollingInterval;

    try {
      // 1단계: 분석 작업 시작 및 jobId 확보
      setStatus('서버에 분석 작업을 요청합니다...');
      const initialResponse = await apiService.analyzeWorksheet({
        image,
        modelChoice: model,
        apiKey,
        endpoint: '/api/document/analyze'
      });

      if (!initialResponse.success || !initialResponse.jobId) {
        throw new Error(initialResponse.error || '작업 ID를 받아오지 못했습니다.');
      }

      const { jobId } = initialResponse;
      setAnalysisResults(initialResponse); // 초기 결과 설정 (jobId 포함)

      // 2단계: 작업 상태 폴링 시작
      await new Promise((resolve, reject) => {
        pollingInterval = setInterval(async () => {
          try {
            const statusResponse = await apiService.getJobStatus(jobId);
            
            // 실제 진행률과 상태로 UI 업데이트
            setProgress(statusResponse.progress || 0);
            setStatus(statusResponse.status || '상태 확인 중...');

            if (statusResponse.status === 'COMPLETED') {
              clearInterval(pollingInterval);
              // 최종 결과가 상태 응답에 포함되어 있다고 가정
              // 만약 그렇지 않다면, 별도의 결과 조회 API 호출 필요
              setAnalysisResults(prev => ({ ...prev, ...statusResponse.results })); 
              resolve();
            } else if (statusResponse.status === 'FAILED') {
              clearInterval(pollingInterval);
              reject(new Error(statusResponse.errorMessage || '분석 작업에 실패했습니다.'));
            }
          } catch (error) {
            clearInterval(pollingInterval);
            reject(error);
          }
        }, 2000); // 2초마다 상태 확인
      });

      // 3단계: 구조화 분석 (필요시)
      if (mode === 'structured') {
        setStatus('AI 기반 구조화 분석 중...');
        const structuredResponse = await apiService.getStructuredCIM(jobId);
        if (structuredResponse.success) {
          setStructuredResult(structuredResponse.structuredResult);
        } else {
          throw new Error(structuredResponse.errorMessage || '구조화된 분석에 실패했습니다.');
        }
      }

      setProgress(100);
      setStatus('분석 완료! 결과를 확인해보세요.');

    } catch (error) {
      console.error('분석 오류:', error);
      let errorMessage = '분석 중 오류가 발생했습니다.';
      if (error.message) {
        errorMessage = error.message;
      }
      setStatus(errorMessage);
      setProgress(100); // 오류 발생 시 프로그레스 바를 채워서 종료 표시

    } finally {
      clearInterval(pollingInterval);
      setTimeout(() => {
        setIsAnalyzing(false);
      }, 3000); // 3초 후 분석 상태 해제
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
