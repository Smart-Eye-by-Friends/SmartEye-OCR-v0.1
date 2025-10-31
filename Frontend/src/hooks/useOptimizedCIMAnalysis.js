import { useState, useCallback, useMemo } from 'react';

/**
 * React-friendly CIM 분석을 위한 최적화된 커스텀 훅
 *
 * 성능 최적화 특징:
 * 1. 불변 데이터 구조로 메모이제이션 최적화
 * 2. 안정적인 참조로 무한 루프 방지
 * 3. 선별적 리렌더링을 위한 세분화된 상태 관리
 */
export const useOptimizedCIMAnalysis = () => {
  // 분리된 상태로 세분화된 리렌더링 제어
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [analysisResult, setAnalysisResult] = useState(null);
  const [progress, setProgress] = useState(0);

  /**
   * CIM 분석 실행 함수
   * useCallback으로 참조 안정성 확보
   */
  const analyzeCIM = useCallback(async (formData) => {
    setIsLoading(true);
    setError(null);
    setProgress(0);

    try {
      // React-friendly 최적화된 엔드포인트 호출
      const response = await fetch('/api/document/v2/analyze-cim-optimized', {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const result = await response.json();

      // 응답 검증 (타입 안전성)
      if (!result || typeof result !== 'object') {
        throw new Error('Invalid response format');
      }

      setAnalysisResult(result);
      setProgress(100);
      return result;

    } catch (err) {
      setError(err.message);
      throw err;
    } finally {
      setIsLoading(false);
    }
  }, []);

  /**
   * 통계 정보 메모이제이션
   * 불변 객체 구조로 불필요한 리렌더링 방지
   */
  const stats = useMemo(() => {
    if (!analysisResult?.stats) return null;

    return {
      totalBlocks: analysisResult.stats.totalLayoutBlocks,
      textBlocks: analysisResult.stats.textBlocks,
      imageBlocks: analysisResult.stats.imageBlocks,
      confidence: Math.round(analysisResult.stats.averageConfidence * 10) / 10,
      processingTime: `${(analysisResult.stats.processingTimeMs / 1000).toFixed(1)}s`,
    };
  }, [analysisResult?.stats]);

  /**
   * 레이아웃 블록 데이터 메모이제이션
   * 좌표 계산 및 렌더링 최적화
   */
  const layoutBlocks = useMemo(() => {
    if (!analysisResult?.cimData?.layoutBlocks) return [];

    return analysisResult.cimData.layoutBlocks.map(block => ({
      id: block.id,
      type: block.type,
      x: block.bbox?.x || 0,
      y: block.bbox?.y || 0,
      width: block.bbox?.width || 0,
      height: block.bbox?.height || 0,
      confidence: Math.round(block.confidence * 100) / 100,
    }));
  }, [analysisResult?.cimData?.layoutBlocks]);

  /**
   * 텍스트 블록 데이터 메모이제이션
   * 텍스트 검색 및 필터링 최적화
   */
  const textBlocks = useMemo(() => {
    if (!analysisResult?.cimData?.textBlocks) return [];

    return analysisResult.cimData.textBlocks.map(block => ({
      id: block.id,
      text: block.text,
      type: block.type,
      x: block.bbox?.x || 0,
      y: block.bbox?.y || 0,
      width: block.bbox?.width || 0,
      height: block.bbox?.height || 0,
      confidence: Math.round(block.confidence * 100) / 100,
    }));
  }, [analysisResult?.cimData?.textBlocks]);

  /**
   * 섹션 데이터 메모이제이션
   * 구조화된 콘텐츠 네비게이션 최적화
   */
  const sections = useMemo(() => {
    if (!analysisResult?.cimData?.sections) return [];

    return analysisResult.cimData.sections
      .sort((a, b) => a.order - b.order)
      .map(section => ({
        id: section.id,
        title: section.title,
        type: section.type,
        content: section.content,
        order: section.order,
      }));
  }, [analysisResult?.cimData?.sections]);

  /**
   * 문제 구조 데이터 메모이제이션
   * 문제-답안 매칭 최적화
   */
  const problems = useMemo(() => {
    if (!analysisResult?.cimData?.problemStructure?.problems) return [];

    return analysisResult.cimData.problemStructure.problems.map(problem => ({
      number: problem.number,
      question: problem.question,
      choices: problem.choices || [],
      answer: problem.answer,
    }));
  }, [analysisResult?.cimData?.problemStructure?.problems]);

  /**
   * 결과 초기화 함수
   * 새 분석 시작 시 이전 결과 정리
   */
  const resetAnalysis = useCallback(() => {
    setAnalysisResult(null);
    setError(null);
    setProgress(0);
  }, []);

  /**
   * 메타데이터 정보
   */
  const metadata = useMemo(() => {
    if (!analysisResult?.cimData?.metadata) return null;

    return {
      version: analysisResult.cimData.metadata.version,
      model: analysisResult.cimData.metadata.model,
      createdAt: new Date(analysisResult.cimData.metadata.createdAt).toLocaleString('ko-KR'),
    };
  }, [analysisResult?.cimData?.metadata]);

  return {
    // 상태
    isLoading,
    error,
    progress,

    // 분석 결과 (메모이제이션됨)
    analysisResult,
    stats,
    layoutBlocks,
    textBlocks,
    sections,
    problems,
    metadata,

    // 액션
    analyzeCIM,
    resetAnalysis,

    // 유틸리티
    hasResult: !!analysisResult,
    isSuccess: analysisResult?.success || false,
    jobId: analysisResult?.jobId || null,
    layoutImageUrl: analysisResult?.layoutImageUrl || null,
    formattedText: analysisResult?.formattedText || '',
  };
};