import React, {
  useState,
  useRef,
  useEffect,
  useCallback,
  useMemo,
  memo
} from 'react';
import PropTypes from 'prop-types';
import { Editor } from '@tinymce/tinymce-react';
import { apiService } from '../services/apiService';
import { safeGet, safeArray, normalizeAnalysisResults } from '../utils/dataUtils';
import { usePerformanceMonitor } from '../utils/performanceMonitor';
import { getTinyMCEExtensionSafeConfig } from '../utils/extensionCompatibility';

// ===========================
// 🚀 최적화된 유틸리티 함수들 (순수 함수로 분리)
// ===========================

// 에러 감지 유틸리티 함수 (순수 함수로 메모이제이션 가능)
const detectError = (text) => {
  if (!text || typeof text !== 'string') return false;

  const trimmedText = text.trim();
  if (trimmedText.length < 3) return false;

  // 강화된 에러 패턴 - 정확한 에러 감지
  const criticalErrorPatterns = [
    /^error:/i,
    /^오류:/,
    /^실패:/,
    /^exception:/i,
    /text extraction failed/i,
    /ocr 처리 실패/,
    /분석에 실패했습니다/,
    /데이터를 불러올 수 없습니다/,
    /처리 중 오류가 발생했습니다/
  ];

  const warningPatterns = [
    /^(no data|데이터 없음|결과 없음)$/i,
    /^(empty|비어있음)$/i
  ];

  const hasCriticalError = criticalErrorPatterns.some(pattern => pattern.test(trimmedText));
  const hasWarningAsFullText = warningPatterns.some(pattern => pattern.test(trimmedText));

  return hasCriticalError || hasWarningAsFullText;
};

// CIM 객체에서 텍스트 추출 헬퍼 함수 (순수 함수)
const extractTextFromCIMObject = (cimData) => {
  const texts = [];
  const textFields = ['text', 'content', 'description', 'formatted_text', 'extracted_text'];

  const traverse = (obj, path = '') => {
    if (!obj || typeof obj !== 'object') return;

    Object.entries(obj).forEach(([key, value]) => {
      if (typeof value === 'string' && value.trim().length > 2) {
        if (textFields.some(field => key.toLowerCase().includes(field)) ||
            value.length > 10) {
          texts.push(value.trim());
        }
      } else if (Array.isArray(value)) {
        value.forEach((item, index) => {
          traverse(item, `${path}.${key}[${index}]`);
        });
      } else if (typeof value === 'object') {
        traverse(value, `${path}.${key}`);
      }
    });
  };

  traverse(cimData);
  return [...new Set(texts)];
};

// 안전한 텍스트 추출 함수 (우선순위 기반 - 순수 함수)
const extractTextWithPriority = (normalizedResults) => {
  if (!normalizedResults) return { text: '', source: 'empty', confidence: 0 };

  // 우선순위 1: 신뢰도 높은 OCR 결과
  const ocrResults = normalizedResults.ocrResults || [];
  const highConfidenceOCR = ocrResults.filter(result =>
    result &&
    result.text &&
    result.text.trim() &&
    result.confidence >= 0.7 &&
    !detectError(result.text)
  );

  if (highConfidenceOCR.length > 0) {
    const ocrText = highConfidenceOCR
      .sort((a, b) => (b.confidence || 0) - (a.confidence || 0))
      .map(result => result.text.trim())
      .join('\n\n');

    const avgConfidence = highConfidenceOCR.reduce((sum, r) => sum + (r.confidence || 0), 0) / highConfidenceOCR.length;
    return { text: ocrText, source: 'high_confidence_ocr', confidence: avgConfidence };
  }

  // 우선순위 2: 모든 OCR 결과
  const validOCR = ocrResults.filter(result =>
    result &&
    result.text &&
    result.text.trim() &&
    !detectError(result.text)
  );

  if (validOCR.length > 0) {
    const ocrText = validOCR
      .map(result => result.text.trim())
      .join('\n\n');

    const avgConfidence = validOCR.reduce((sum, r) => sum + (r.confidence || 0), 0) / validOCR.length;
    return { text: ocrText, source: 'all_ocr', confidence: avgConfidence };
  }

  // 우선순위 3: AI 분석 결과
  const aiResults = normalizedResults.aiResults || [];
  const validAI = aiResults.filter(result =>
    result &&
    (result.description || result.text) &&
    !detectError(result.description || result.text)
  );

  if (validAI.length > 0) {
    const aiText = validAI
      .map(result => (result.description || result.text).trim())
      .join('\n\n');

    const avgConfidence = validAI.reduce((sum, r) => sum + (r.confidence || 0.5), 0) / validAI.length;
    return { text: aiText, source: 'ai_analysis', confidence: avgConfidence };
  }

  // 우선순위 4: CIM 구조화 데이터
  const cimData = normalizedResults.cimData;
  if (cimData) {
    try {
      if (typeof cimData === 'string' && cimData.trim() && !detectError(cimData)) {
        return { text: cimData.trim(), source: 'cim_string', confidence: 0.6 };
      } else if (typeof cimData === 'object') {
        const extractedTexts = extractTextFromCIMObject(cimData);
        if (extractedTexts.length > 0) {
          const cimText = extractedTexts.join('\n\n');
          if (!detectError(cimText)) {
            return { text: cimText, source: 'cim_object', confidence: 0.5 };
          }
        }
      }
    } catch (error) {
      console.warn('CIM 데이터 파싱 오류:', error);
    }
  }

  // 최후 수단: 에러 메시지 포함 모든 텍스트
  const allTexts = [];

  ocrResults.forEach(result => {
    if (result && result.text && result.text.trim()) {
      allTexts.push(`[OCR] ${result.text.trim()}`);
    }
  });

  aiResults.forEach(result => {
    if (result && (result.description || result.text)) {
      allTexts.push(`[AI] ${(result.description || result.text).trim()}`);
    }
  });

  if (allTexts.length > 0) {
    return {
      text: allTexts.join('\n\n'),
      source: 'fallback_all',
      confidence: 0.2
    };
  }

  return {
    text: '추출 가능한 텍스트가 없습니다.',
    source: 'empty',
    confidence: 0
  };
};

// 데이터 소스 설명 함수 (순수 함수로 최적화)
const getSourceDescription = (source) => {
  const descriptions = {
    'high_confidence_ocr': '고신뢰도 OCR',
    'all_ocr': 'OCR',
    'ai_analysis': 'AI 분석',
    'cim_string': 'CIM 문자열',
    'cim_object': 'CIM 구조화',
    'fallback_all': '전체 백업',
    'empty': '없음'
  };
  return descriptions[source] || source;
};

// ===========================
// 🎯 메모이제이션된 서브 컴포넌트들
// ===========================

// 에러 알림 컴포넌트 (React.memo로 최적화)
const ErrorNotification = memo(({ hasError, errorMessage, onDismiss }) => {
  if (!hasError) return null;

  return (
    <div className="error-notification">
      <div className="error-content">
        <span className="error-icon">⚠️</span>
        <span className="error-text">{errorMessage}</span>
        <button
          className="error-dismiss"
          onClick={onDismiss}
          title="알림 닫기"
        >
          ✕
        </button>
      </div>
    </div>
  );
});

ErrorNotification.displayName = 'ErrorNotification';
ErrorNotification.propTypes = {
  hasError: PropTypes.bool.isRequired,
  errorMessage: PropTypes.string.isRequired,
  onDismiss: PropTypes.func.isRequired
};

// 로딩 컴포넌트 (React.memo로 최적화)
const LoadingComponent = memo(() => (
  <div className="no-result">
    <div className="loading-state">
      <div className="loading-spinner"></div>
      <p>📝 텍스트 데이터를 로딩 중...</p>
    </div>
  </div>
));

LoadingComponent.displayName = 'LoadingComponent';

// 빈 결과 컴포넌트 (React.memo로 최적화)
const EmptyResult = memo(() => (
  <div className="no-result">
    <div className="no-result-icon">📝</div>
    <h3>텍스트 결과가 없습니다</h3>
    <p>먼저 이미지를 업로드하고 분석을 실행해주세요.</p>
  </div>
));

EmptyResult.displayName = 'EmptyResult';

// ===========================
// 🚀 메인 컴포넌트 (최적화된 TextEditorTab)
// ===========================

const TextEditorTab = ({
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
  // ===========================
  // 📊 상태 관리 (최소한의 상태만 유지)
  // ===========================
  const [isEditing, setIsEditing] = useState(false);
  const [editorContent, setEditorContent] = useState('');
  const [isConverting, setIsConverting] = useState(false);
  const [showCimData, setShowCimData] = useState(false);
  const [hasError, setHasError] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  // ===========================
  // 📈 성능 모니터링 (무한 렌더링 감지)
  // ===========================
  const { startMeasure, endMeasure, detectInfiniteRendering } = usePerformanceMonitor('TextEditorTab');

  // ===========================
  // 🔗 안정적인 참조 관리
  // ===========================
  const editorRef = useRef(null);
  const lastProcessedDataRef = useRef({
    formattedText: '',
    editableText: '',
    analysisResults: null
  });

  // ===========================
  // 🧠 메모이제이션된 계산 값들
  // ===========================

  // 1️⃣ 정규화된 분석 결과 (안정적인 참조)
  const normalizedResults = useMemo(() => {
    if (!analysisResults) return null;

    try {
      return normalizeAnalysisResults(analysisResults);
    } catch (error) {
      console.error('분석 결과 정규화 오류:', error);
      return null;
    }
  }, [analysisResults]);

  // 2️⃣ 데이터 가용성 검사 (메모이제이션)
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

  // 3️⃣ 최적 텍스트 추출 (메모이제이션)
  const extractedTextData = useMemo(() => {
    if (!normalizedResults) return null;

    return extractTextWithPriority(normalizedResults);
  }, [normalizedResults]);

  // ===========================
  // 🔄 안정적인 콜백 함수들 (useCallback으로 최적화)
  // ===========================

  // 1️⃣ 텍스트 변경 핸들러 (안정적인 참조)
  const handleTextChange = useCallback((newText) => {
    // 현재 텍스트와 동일하면 호출하지 않음 (무한 루프 방지)
    if (newText === editableText) return;

    // onTextChange가 함수인 경우에만 호출
    if (typeof onTextChange === 'function') {
      onTextChange(newText);
    }
  }, [editableText, onTextChange]);

  // 2️⃣ 에디터 변경 핸들러 (디바운싱 적용)
  const handleEditorChange = useCallback((content) => {
    setEditorContent(content);

    // 디바운싱을 위한 setTimeout (과도한 업데이트 방지)
    const timeoutId = setTimeout(() => {
      handleTextChange(content);
    }, 300);

    return () => clearTimeout(timeoutId);
  }, [handleTextChange]);

  // 3️⃣ 저장 핸들러
  const handleSave = useCallback(() => {
    if (typeof onSaveText === 'function') {
      onSaveText();
    }
    setIsEditing(false);
  }, [onSaveText]);

  // 4️⃣ 리셋 핸들러 (에러 복구 포함)
  const handleReset = useCallback(() => {
    try {
      const originalText = formattedText || editableText || '';
      const isOriginalValid = originalText.trim().length > 0 && !detectError(originalText);

      if (isOriginalValid) {
        // 유효한 원본으로 복원
        setEditorContent(originalText);
        setHasError(false);
        setErrorMessage('');
        handleTextChange(originalText);
      } else if (extractedTextData) {
        // 최적 대체 텍스트 사용
        setEditorContent(extractedTextData.text);
        setHasError(true);
        setErrorMessage(
          `원본 텍스트가 유효하지 않아 ${getSourceDescription(extractedTextData.source)} 데이터로 복원했습니다. ` +
          `(신뢰도: ${(extractedTextData.confidence * 100).toFixed(0)}%)`
        );
        handleTextChange(extractedTextData.text);
      }

      // 부모 컴포넌트 리셋 핸들러 호출
      if (typeof onResetText === 'function') {
        onResetText();
      }
    } catch (error) {
      console.error('텍스트 리셋 오류:', error);
      setHasError(true);
      setErrorMessage('텍스트 리셋 중 오류가 발생했습니다. 시스템 관리자에게 문의하세요.');
    }
  }, [formattedText, editableText, extractedTextData, handleTextChange, onResetText]);

  // 5️⃣ 복사 핸들러 (향상된 에러 처리)
  const handleCopy = useCallback(async () => {
    try {
      const textToCopy = editorContent || '';

      if (!textToCopy.trim()) {
        alert('복사할 텍스트가 없습니다.');
        return;
      }

      // HTML 태그 제거 후 클립보드에 복사
      const plainText = textToCopy.replace(/<[^>]*>/g, '');
      await navigator.clipboard.writeText(plainText);
      alert('텍스트가 클립보드에 복사되었습니다.');
    } catch (err) {
      console.error('클립보드 복사 실패:', err);

      // 대체 방법 시도
      if (typeof onCopyText === 'function') {
        onCopyText();
      } else {
        alert('클립보드 복사에 실패했습니다. 브라우저 설정을 확인해주세요.');
      }
    }
  }, [editorContent, onCopyText]);

  // 6️⃣ CIM → 텍스트 변환 핸들러
  const handleConvertCimToText = useCallback(async () => {
    if (!normalizedResults?.cimData) {
      alert('CIM 데이터가 없습니다. 먼저 분석을 실행해주세요.');
      return;
    }

    setIsConverting(true);
    setHasError(false);

    try {
      const convertedResponse = await apiService.convertCimToText(normalizedResults.cimData);

      // 응답 구조에 따라 텍스트 추출
      const resultText = convertedResponse.formattedText ||
                        convertedResponse.text ||
                        convertedResponse ||
                        'CIM 변환 결과를 찾을 수 없습니다.';

      if (resultText && resultText.trim()) {
        setEditorContent(resultText);
        handleTextChange(resultText);
        setHasError(false);
        alert('CIM 데이터가 텍스트로 변환되었습니다.');
      } else {
        throw new Error('변환된 텍스트가 비어있습니다.');
      }
    } catch (error) {
      console.error('CIM → 텍스트 변환 실패:', error);

      setHasError(true);
      let errorMessage = 'CIM → 텍스트 변환에 실패했습니다.';

      if (error.response?.status === 404) {
        errorMessage = 'CIM 변환 서비스를 찾을 수 없습니다. 시스템 관리자에게 문의하세요.';
      } else if (error.response?.status >= 500) {
        errorMessage = '서버에서 CIM 변환 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';
      } else if (error.response?.data?.message) {
        errorMessage = error.response.data.message;
      }

      setErrorMessage(errorMessage);
      alert(errorMessage);
    } finally {
      setIsConverting(false);
      setIsLoading(false); // 명시적으로 로딩 상태 해제
    }
  }, [normalizedResults, handleTextChange]);

  // 7️⃣ 대체 데이터 로드 핸들러
  const handleLoadFallbackData = useCallback(() => {
    if (!extractedTextData) return;

    setEditorContent(extractedTextData.text);

    if (extractedTextData.confidence > 0.3) {
      setHasError(false);
      setErrorMessage('');
    } else {
      setHasError(true);
      setErrorMessage(`낮은 신뢰도 데이터입니다 (${(extractedTextData.confidence * 100).toFixed(0)}%). 검토가 필요합니다.`);
    }

    handleTextChange(extractedTextData.text);
  }, [extractedTextData, handleTextChange]);

  // 8️⃣ 에러 알림 해제 핸들러
  const handleDismissError = useCallback(() => {
    setHasError(false);
    setErrorMessage('');
  }, []);

  // ===========================
  // 🎯 핵심 useEffect (무한 루프 완전 방지)
  // ===========================
  useEffect(() => {
    const measureId = startMeasure({
      editableText: !!editableText,
      formattedText: !!formattedText,
      hasAnalysisResults: !!analysisResults
    });

    // 🚨 무한 렌더링 감지 및 차단
    if (detectInfiniteRendering(10)) {
      console.error('TextEditorTab에서 무한 렌더링이 감지되었습니다.');
      endMeasure(measureId, { status: 'infinite_rendering_detected' });
      return;
    }

    // 🔍 실제 데이터 변경 감지 (얕은 비교)
    const currentData = {
      formattedText,
      editableText,
      analysisResults
    };

    const hasActualChange = (
      currentData.formattedText !== lastProcessedDataRef.current.formattedText ||
      currentData.editableText !== lastProcessedDataRef.current.editableText ||
      currentData.analysisResults !== lastProcessedDataRef.current.analysisResults
    );

    // 변경사항이 없으면 처리하지 않음
    if (!hasActualChange) {
      endMeasure(measureId, { status: 'no_change' });
      return;
    }

    // 참조 업데이트
    lastProcessedDataRef.current = currentData;

    // 🔄 비동기 텍스트 처리 (UI 블로킹 방지)
    setIsLoading(true);

    const processAsync = async () => {
      try {
        // 텍스트 우선순위 결정
        const currentText = editableText || formattedText || '';
        const hasCurrentTextError = detectError(currentText);
        const isCurrentTextValid = currentText.trim().length > 0 && !hasCurrentTextError;

        if (isCurrentTextValid) {
          // ✅ 현재 텍스트가 유효한 경우
          setHasError(false);
          setErrorMessage('');
          setEditorContent(currentText);
        } else if (extractedTextData) {
          // ⚠️ 현재 텍스트가 무효하면 대체 텍스트 사용
          if (extractedTextData.confidence > 0.3) {
            setHasError(true);
            setErrorMessage(
              `원본 텍스트에 문제가 있어 ${getSourceDescription(extractedTextData.source)} 데이터를 사용합니다. ` +
              `(신뢰도: ${(extractedTextData.confidence * 100).toFixed(0)}%)`
            );
            setEditorContent(extractedTextData.text);

            // 📤 대체 텍스트로 상위 컴포넌트 업데이트
            if (extractedTextData.text !== editableText && typeof onTextChange === 'function') {
              onTextChange(extractedTextData.text);
            }
          } else {
            // ❌ 신뢰할 만한 대체 텍스트도 없는 경우
            setHasError(true);
            setErrorMessage('품질이 보장된 텍스트 데이터를 찾을 수 없습니다. 가능한 모든 데이터를 표시합니다.');
            setEditorContent(extractedTextData.text);
          }
        }
      } catch (error) {
        console.error('텍스트 처리 중 오류:', error);
        setHasError(true);
        setErrorMessage('텍스트 처리 중 오류가 발생했습니다.');
      } finally {
        setIsLoading(false);
        endMeasure(measureId, { status: 'completed' });
      }
    };

    // 🕐 50ms 지연으로 UI 블로킹 방지 (응답성 개선)
    const timeoutId = setTimeout(processAsync, 50);

    return () => {
      clearTimeout(timeoutId);
      endMeasure(measureId, { status: 'cleanup' });
    };
  }, [
    editableText,
    formattedText,
    analysisResults,
    extractedTextData,
    onTextChange
    // 성능 모니터링 함수들은 의존성에서 제외 (매 렌더링마다 새 참조 생성 방지)
  ]);

  // ===========================
  // 🎨 렌더링 (조건부 렌더링으로 최적화)
  // ===========================

  // 로딩 상태 렌더링
  if (isLoading) {
    return <LoadingComponent />;
  }

  // 빈 데이터 상태 렌더링
  if (!dataAvailability.hasAnyData) {
    return <EmptyResult />;
  }

  // 메인 UI 렌더링
  return (
    <div className="text-editor-content">
      {/* 📢 에러 알림 (메모이제이션된 컴포넌트) */}
      <ErrorNotification
        hasError={hasError}
        errorMessage={errorMessage}
        onDismiss={handleDismissError}
      />

      {/* 🎛️ 에디터 헤더 */}
      <div className="editor-header">
        <h4>📝 텍스트 편집기</h4>
        <div className="editor-actions">
          <button
            className="action-btn edit-btn"
            onClick={() => setIsEditing(!isEditing)}
          >
            {isEditing ? '📖 읽기 모드' : '✏️편집 모드'}
          </button>

          <button
            className="action-btn reset-btn"
            onClick={handleReset}
            disabled={!formattedText && !dataAvailability.hasOCRData && !dataAvailability.hasAIData && !dataAvailability.hasCIMData}
            title={hasError ? '대체 데이터로 복원' : '포맷된 텍스트로 복원'}
          >
            🔄 {hasError ? '대체 데이터로 복원' : '원본으로 복원'}
          </button>

          <button
            className="action-btn copy-btn"
            onClick={handleCopy}
          >
            📋 복사
          </button>

          <button
            className="action-btn download-btn"
            onClick={onDownloadText}
            disabled={typeof onDownloadText !== 'function'}
          >
            💾 텍스트 다운로드
          </button>

          <button
            className="action-btn convert-btn"
            onClick={handleConvertCimToText}
            disabled={isConverting || !normalizedResults?.cimData}
            title="CIM 데이터를 최종 텍스트로 변환"
          >
            {isConverting ? (
              <>
                <span className="loading-spinner small"></span>
                변환 중...
              </>
            ) : (
              '🔄 CIM→텍스트'
            )}
          </button>

          <button
            className="action-btn data-btn"
            onClick={() => setShowCimData(!showCimData)}
            disabled={!normalizedResults?.cimData}
            title="CIM 원시 데이터 보기/숨기기"
          >
            {showCimData ? '🔻 데이터 숨기기' : '🔺 데이터 보기'}
          </button>

          <button
            className="action-btn word-btn"
            onClick={onSaveAsWord}
            disabled={isWordSaving || typeof onSaveAsWord !== 'function'}
          >
            {isWordSaving ? (
              <>
                <span className="loading-spinner small"></span>
                변환 중...
              </>
            ) : (
              '📄 워드 저장'
            )}
          </button>
        </div>
      </div>

      {/* 📝 에디터 컨테이너 */}
      <div className="editor-container">
        {isEditing ? (
          // ✏️ 편집 모드
          <div className="editor-wrapper">
            <Editor
              ref={editorRef}
              value={editorContent}
              onEditorChange={handleEditorChange}
              init={{
                height: 500,
                menubar: false,
                plugins: [
                  'advlist', 'autolink', 'lists', 'link', 'image', 'charmap',
                  'preview', 'anchor', 'searchreplace', 'visualblocks', 'code',
                  'fullscreen', 'insertdatetime', 'media', 'table', 'code',
                  'help', 'wordcount'
                ],
                toolbar: 'undo redo | blocks | ' +
                  'bold italic forecolor | alignleft aligncenter ' +
                  'alignright alignjustify | bullist numlist outdent indent | ' +
                  'removeformat | help',
                language: 'ko_KR',
                placeholder: '여기에 텍스트를 입력하세요...',

                // 확장프로그램 호환성 설정
                ...getTinyMCEExtensionSafeConfig()
              }}
            />
            <div className="editor-footer">
              <button
                className="save-btn"
                onClick={handleSave}
                disabled={!editorContent.trim()}
              >
                💾 저장
              </button>
              <span className="character-count">
                문자 수: {editorContent.replace(/<[^>]*>/g, '').length.toLocaleString()}
              </span>
              {hasError && (
                <span className="fallback-indicator">
                  📋 대체 텍스트 사용 중
                </span>
              )}
            </div>
          </div>
        ) : (
          // 📖 읽기 모드
          <div className="text-display">
            {editorContent || formattedText ? (
              <div
                className={`text-content ${hasError ? 'fallback-content' : ''}`}
                dangerouslySetInnerHTML={{ __html: editorContent || formattedText }}
              />
            ) : (
              <div className="empty-content">
                <p>표시할 텍스트가 없습니다.</p>
                {(dataAvailability.hasOCRData || dataAvailability.hasAIData || dataAvailability.hasCIMData) && (
                  <button
                    className="load-ocr-btn"
                    onClick={handleLoadFallbackData}
                  >
                    📋 {getSourceDescription(
                      dataAvailability.hasOCRData ? 'all_ocr' :
                      dataAvailability.hasAIData ? 'ai_analysis' : 'cim_object'
                    )} 데이터 불러오기
                  </button>
                )}
              </div>
            )}
          </div>
        )}
      </div>

      {/* 📊 CIM 원시 데이터 표시 */}
      {showCimData && normalizedResults?.cimData && (
        <div className="cim-data-section">
          <h5>📋 CIM 원시 데이터 (Circuit Integration Management)</h5>
          <div className="cim-data-container">
            <pre className="cim-data-content">
              {JSON.stringify(normalizedResults.cimData, null, 2)}
            </pre>
          </div>
        </div>
      )}
    </div>
  );
};

// ===========================
// 📋 PropTypes (React 18 호환)
// ===========================
TextEditorTab.propTypes = {
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
    cimData: PropTypes.oneOfType([
      PropTypes.object,
      PropTypes.string
    ]),
    formattedText: PropTypes.string
  })
};

// ===========================
// 🚀 React.memo로 최종 최적화 (Props 변경시에만 리렌더링)
// ===========================
export default memo(TextEditorTab);