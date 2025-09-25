import React, { useState, useCallback, useMemo } from 'react';
import PropTypes from 'prop-types';
import { usePerformanceMonitor } from '../../utils/performanceMonitor';
import { normalizeAnalysisResults } from '../../utils/dataUtils';
import { extractTextWithPriority } from './utils/textExtraction';
import TextEditorPresentation from './TextEditorPresentation';

/**
 * TextEditor 컨테이너 컴포넌트 - 비즈니스 로직과 상태 관리
 * React 18 Concurrent Features 활용한 최적화된 아키텍처
 */
const TextEditorContainer = ({
  formattedText = '',
  editableText = '',
  onTextChange = null,
  onSaveText = null,
  onResetText = null,
  onDownloadText = null,
  onCopyText = null,
  onSaveAsWord = null,
  isWordSaving = false,
  analysisResults = null
}) => {
  // 📊 성능 모니터링
  const { startMeasure, endMeasure, detectInfiniteRendering } = usePerformanceMonitor('TextEditorContainer');

  // 🎯 통합된 상태 관리 (4개로 최적화)
  const [editorState, setEditorState] = useState({
    isEditing: false,
    content: editableText || formattedText || '',
    showCimData: false
  });

  const [loadingState, setLoadingState] = useState({
    isLoading: false,
    isConverting: false
  });

  const [errorState, setErrorState] = useState({
    hasError: false,
    message: '',
    source: ''
  });

  const [uiState, setUIState] = useState({
    isInitialized: false
  });

  // 🧠 안정적인 메모이제이션
  const normalizedResults = useMemo(() => {
    if (!analysisResults) return null;
    try {
      return normalizeAnalysisResults(analysisResults);
    } catch (error) {
      console.error('분석 결과 정규화 오류:', error);
      return null;
    }
  }, [analysisResults]);

  const extractedTextData = useMemo(() => {
    if (!normalizedResults) return null;
    return extractTextWithPriority(normalizedResults);
  }, [normalizedResults]);

  const dataAvailability = useMemo(() => {
    const hasOCRData = normalizedResults?.ocrResults?.length > 0;
    const hasAIData = normalizedResults?.aiResults?.length > 0;
    const hasCIMData = normalizedResults?.cimData != null;
    const hasFormattedText = formattedText && formattedText.trim();
    const hasEditableText = editableText && editableText.trim();

    return {
      hasOCRData,
      hasAIData,
      hasCIMData,
      hasFormattedText,
      hasEditableText,
      hasAnyData: hasFormattedText || hasEditableText || hasOCRData || hasAIData || hasCIMData
    };
  }, [normalizedResults, formattedText, editableText]);

  // 🔄 최적화된 이벤트 핸들러들
  const handleStateUpdate = useCallback((stateType, updates) => {
    const measureId = startMeasure({ stateType, updates });

    switch (stateType) {
      case 'editor':
        setEditorState(prev => ({ ...prev, ...updates }));
        break;
      case 'loading':
        setLoadingState(prev => ({ ...prev, ...updates }));
        break;
      case 'error':
        setErrorState(prev => ({ ...prev, ...updates }));
        break;
      case 'ui':
        setUIState(prev => ({ ...prev, ...updates }));
        break;
      default:
        break;
    }

    endMeasure(measureId, { success: true });
  }, [startMeasure, endMeasure]);

  const handleTextChange = useCallback((newText) => {
    // 중복 업데이트 방지
    if (newText === editableText) return;

    if (typeof onTextChange === 'function') {
      onTextChange(newText);
    }
  }, [editableText, onTextChange]);

  const handleEditorContentChange = useCallback((content) => {
    handleStateUpdate('editor', { content });

    // 디바운싱을 React 18 startTransition으로 대체
    React.startTransition(() => {
      handleTextChange(content);
    });
  }, [handleStateUpdate, handleTextChange]);

  // 🔧 CIM 변환 API 호출 수정
  const handleConvertCimToText = useCallback(async () => {
    if (!normalizedResults?.cimData) {
      handleStateUpdate('error', {
        hasError: true,
        message: 'CIM 데이터가 없습니다. 먼저 분석을 실행해주세요.',
        source: 'validation'
      });
      return;
    }

    handleStateUpdate('loading', { isConverting: true });

    try {
      // 올바른 Content-Type으로 수정
      const response = await fetch('/api/document/cim-to-text', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          cimData: normalizedResults.cimData
        })
      });

      if (!response.ok) {
        throw new Error(`API 호출 실패: ${response.status} ${response.statusText}`);
      }

      const result = await response.json();
      const convertedText = result.text || result.convertedText || String(result);

      handleStateUpdate('editor', { content: convertedText });
      handleTextChange(convertedText);

      handleStateUpdate('error', {
        hasError: false,
        message: 'CIM 데이터가 성공적으로 변환되었습니다.',
        source: 'success'
      });

    } catch (error) {
      console.error('CIM → 텍스트 변환 실패:', error);
      handleStateUpdate('error', {
        hasError: true,
        message: `CIM 변환 실패: ${error.message}`,
        source: 'api_error'
      });
    } finally {
      handleStateUpdate('loading', { isConverting: false });
    }
  }, [normalizedResults, handleStateUpdate, handleTextChange]);

  // 📝 초기화 로직 분리
  const initializeContent = useCallback(() => {
    const measureId = startMeasure({ phase: 'initialization' });

    // 무한 렌더링 감지
    if (detectInfiniteRendering(5)) {
      console.error('TextEditor 초기화에서 무한 렌더링 감지됨');
      return;
    }

    const currentText = editableText || formattedText || '';
    const hasValidText = currentText.trim().length > 0;

    if (hasValidText) {
      handleStateUpdate('editor', { content: currentText });
      handleStateUpdate('error', { hasError: false, message: '' });
    } else if (extractedTextData && extractedTextData.confidence > 0.3) {
      handleStateUpdate('editor', { content: extractedTextData.text });
      handleStateUpdate('error', {
        hasError: true,
        message: `대체 텍스트 사용 (신뢰도: ${(extractedTextData.confidence * 100).toFixed(0)}%)`,
        source: extractedTextData.source
      });
    }

    handleStateUpdate('ui', { isInitialized: true });
    endMeasure(measureId, { success: true });
  }, [
    editableText,
    formattedText,
    extractedTextData,
    handleStateUpdate,
    startMeasure,
    endMeasure,
    detectInfiniteRendering
  ]);

  // ⚡ React 18 useEffect 최적화
  React.useEffect(() => {
    if (!uiState.isInitialized) {
      initializeContent();
    }
  }, [initializeContent, uiState.isInitialized]);

  // 📊 프롭 변경 감지 (얕은 비교)
  React.useEffect(() => {
    if (uiState.isInitialized) {
      const newContent = editableText || formattedText || '';
      if (newContent !== editorState.content) {
        handleStateUpdate('editor', { content: newContent });
      }
    }
  }, [editableText, formattedText, editorState.content, uiState.isInitialized, handleStateUpdate]);

  // 🎨 프레젠테이션 컴포넌트에 전달할 props
  const presentationProps = {
    // 상태
    editorState,
    loadingState,
    errorState,

    // 데이터
    dataAvailability,
    extractedTextData,
    normalizedResults,

    // 핸들러
    onStateUpdate: handleStateUpdate,
    onContentChange: handleEditorContentChange,
    onConvertCim: handleConvertCimToText,

    // 부모 핸들러
    onSaveText,
    onResetText,
    onDownloadText,
    onCopyText,
    onSaveAsWord,
    isWordSaving
  };

  return <TextEditorPresentation {...presentationProps} />;
};

TextEditorContainer.propTypes = {
  formattedText: PropTypes.string,
  editableText: PropTypes.string,
  onTextChange: PropTypes.func,
  onSaveText: PropTypes.func,
  onResetText: PropTypes.func,
  onDownloadText: PropTypes.func,
  onCopyText: PropTypes.func,
  onSaveAsWord: PropTypes.func,
  isWordSaving: PropTypes.bool,
  analysisResults: PropTypes.shape({
    stats: PropTypes.object,
    ocrResults: PropTypes.array,
    aiResults: PropTypes.array,
    cimData: PropTypes.oneOfType([PropTypes.object, PropTypes.string]),
    formattedText: PropTypes.string
  })
};

export default React.memo(TextEditorContainer);