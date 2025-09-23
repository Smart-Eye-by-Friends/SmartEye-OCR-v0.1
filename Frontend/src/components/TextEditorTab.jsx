import React, { useState, useRef, useEffect } from 'react';
import PropTypes from 'prop-types';
import { Editor } from '@tinymce/tinymce-react';
import { apiService } from '../services/apiService';
import { safeGet, safeArray, normalizeAnalysisResults } from '../utils/dataUtils';

// 에러 감지 유틸리티 함수 (개선된 버전)
const detectError = (text) => {
  if (!text || typeof text !== 'string') return false;

  const trimmedText = text.trim();

  // 빈 텍스트나 너무 짧은 텍스트는 에러로 간주하지 않음
  if (trimmedText.length < 3) return false;

  // 강화된 에러 패턴 - 더 정확한 에러 감지
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

  // 경고성 패턴 (전체 텍스트가 이것만으로 구성된 경우만 에러로 간주)
  const warningPatterns = [
    /^(no data|데이터 없음|결과 없음)$/i,
    /^(empty|비어있음)$/i
  ];

  // 심각한 에러 패턴이 있는지 확인
  const hasCriticalError = criticalErrorPatterns.some(pattern => pattern.test(trimmedText));

  // 경고 패턴의 경우 전체 텍스트가 해당 패턴과 정확히 일치하는 경우만
  const hasWarningAsFullText = warningPatterns.some(pattern => pattern.test(trimmedText));

  return hasCriticalError || hasWarningAsFullText;
};

// 안전한 텍스트 추출 함수 (우선순위 기반)
const extractTextWithPriority = (normalizedResults) => {
  if (!normalizedResults) return { text: '', source: 'empty', confidence: 0 };

  // 우선순위 1: 신뢰도가 높은 OCR 결과
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
      .sort((a, b) => (b.confidence || 0) - (a.confidence || 0)) // 신뢰도 순 정렬
      .map(result => result.text.trim())
      .join('\n\n');

    const avgConfidence = highConfidenceOCR.reduce((sum, r) => sum + (r.confidence || 0), 0) / highConfidenceOCR.length;
    return { text: ocrText, source: 'high_confidence_ocr', confidence: avgConfidence };
  }

  // 우선순위 2: 모든 OCR 결과 (신뢰도 무관)
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

  // 에러가 있더라도 OCR 텍스트 포함
  ocrResults.forEach(result => {
    if (result && result.text && result.text.trim()) {
      allTexts.push(`[OCR] ${result.text.trim()}`);
    }
  });

  // AI 결과도 포함
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

// CIM 객체에서 텍스트 추출 헬퍼 함수
const extractTextFromCIMObject = (cimData) => {
  const texts = [];

  // 일반적인 텍스트 필드들 확인
  const textFields = ['text', 'content', 'description', 'formatted_text', 'extracted_text'];

  const traverse = (obj, path = '') => {
    if (!obj || typeof obj !== 'object') return;

    Object.entries(obj).forEach(([key, value]) => {
      if (typeof value === 'string' && value.trim().length > 2) {
        // 의미있는 텍스트 필드인지 확인
        if (textFields.some(field => key.toLowerCase().includes(field)) ||
            value.length > 10) { // 충분히 긴 텍스트
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
  return [...new Set(texts)]; // 중복 제거
};

const TextEditorTab = ({
  formattedText,
  editableText,
  onTextChange,
  onSaveText,
  onResetText,
  onDownloadText,
  onCopyText,
  onSaveAsWord,
  isWordSaving,
  analysisResults
}) => {
  const [isEditing, setIsEditing] = useState(false);
  const [editorContent, setEditorContent] = useState('');
  const [isConverting, setIsConverting] = useState(false);
  const [showCimData, setShowCimData] = useState(false);
  const [hasError, setHasError] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [normalizedResults, setNormalizedResults] = useState(null);
  const editorRef = useRef(null);

  useEffect(() => {
    setIsLoading(true);

    // 데이터 정규화 수행
    const normalized = normalizeAnalysisResults(analysisResults);
    setNormalizedResults(normalized);

    // 스마트 텍스트 선택 로직
    const currentText = editableText || formattedText || '';
    const hasCurrentTextError = detectError(currentText);

    // 현재 텍스트가 유효한지 확인
    const isCurrentTextValid = currentText.trim().length > 0 && !hasCurrentTextError;

    if (isCurrentTextValid) {
      // 현재 텍스트가 유효하면 사용
      setHasError(false);
      setErrorMessage('');
      setEditorContent(currentText);
    } else {
      // 현재 텍스트가 무효하면 우선순위 기반 추출
      const fallbackResult = extractTextWithPriority(normalized);

      if (fallbackResult.confidence > 0.3) {
        // 신뢰할 만한 대체 텍스트가 있는 경우
        setHasError(true);
        setErrorMessage(
          `원본 텍스트에 문제가 있어 ${getSourceDescription(fallbackResult.source)} 데이터를 사용합니다. ` +
          `(신뢰도: ${(fallbackResult.confidence * 100).toFixed(0)}%)`
        );
        setEditorContent(fallbackResult.text);

        // 대체 텍스트로 상태 업데이트
        if (onTextChange && typeof onTextChange === 'function') {
          onTextChange(fallbackResult.text);
        }
      } else {
        // 신뢰할 만한 대체 텍스트도 없는 경우
        setHasError(true);
        setErrorMessage('품질이 보장된 텍스트 데이터를 찾을 수 없습니다. 가능한 모든 데이터를 표시합니다.');
        setEditorContent(fallbackResult.text);
      }
    }

    // 로딩 상태 해제
    setTimeout(() => setIsLoading(false), 200);
  }, [editableText, formattedText, analysisResults, onTextChange]);

  // 데이터 소스 설명 함수
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

  const handleEditorChange = (content) => {
    setEditorContent(content);
    onTextChange(content);
  };

  const handleSave = () => {
    onSaveText();
    setIsEditing(false);
  };

  const handleReset = () => {
    try {
      const originalText = formattedText || editableText || '';

      // 원본 텍스트가 유효한지 확인
      const isOriginalValid = originalText.trim().length > 0 && !detectError(originalText);

      if (isOriginalValid) {
        // 유효한 원본으로 복원
        setEditorContent(originalText);
        setHasError(false);
        setErrorMessage('');

        if (onTextChange && typeof onTextChange === 'function') {
          onTextChange(originalText);
        }
      } else {
        // 원본이 유효하지 않으면 최선의 대체 텍스트 사용
        const fallbackResult = extractTextWithPriority(normalizedResults);

        setEditorContent(fallbackResult.text);
        setHasError(true);
        setErrorMessage(
          `원본 텍스트가 유효하지 않아 ${getSourceDescription(fallbackResult.source)} 데이터로 복원했습니다. ` +
          `(신뢰도: ${(fallbackResult.confidence * 100).toFixed(0)}%)`
        );

        if (onTextChange && typeof onTextChange === 'function') {
          onTextChange(fallbackResult.text);
        }
      }

      if (onResetText && typeof onResetText === 'function') {
        onResetText();
      }
    } catch (error) {
      console.error('텍스트 리셋 오류:', error);
      setHasError(true);
      setErrorMessage('텍스트 리셋 중 오류가 발생했습니다. 시스템 관리자에게 문의하세요.');
    }
  };

  const handleCopy = async () => {
    try {
      const textToCopy = editorContent || '';

      if (!textToCopy.trim()) {
        alert('복사할 텍스트가 없습니다.');
        return;
      }

      // HTML 태그 제거
      const plainText = textToCopy.replace(/<[^>]*>/g, '');

      await navigator.clipboard.writeText(plainText);
      alert('텍스트가 클립보드에 복사되었습니다.');
    } catch (err) {
      console.error('클립보드 복사 실패:', err);

      // 대체 방법 시도
      if (onCopyText && typeof onCopyText === 'function') {
        onCopyText();
      } else {
        alert('클립보드 복사에 실패했습니다. 브라우저 설정을 확인해주세요.');
      }
    }
  };

  // CIM → 텍스트 변환 핸들러
  const handleConvertCimToText = async () => {
    if (!normalizedResults?.cimData) {
      alert('CIM 데이터가 없습니다. 먼저 분석을 실행해주세요.');
      return;
    }

    setIsConverting(true);
    try {
      const convertedText = await apiService.convertCimToText(normalizedResults.cimData);
      setEditorContent(convertedText.text || convertedText);
      onTextChange(convertedText.text || convertedText);
      alert('CIM 데이터가 텍스트로 변환되었습니다.');
    } catch (error) {
      console.error('CIM → 텍스트 변환 실패:', error);
      alert('CIM → 텍스트 변환에 실패했습니다.');
    } finally {
      setIsConverting(false);
    }
  };

  // 로딩 상태 처리
  if (isLoading) {
    return (
      <div className="no-result">
        <div className="loading-state">
          <div className="loading-spinner"></div>
          <p>📝 텍스트 데이터를 로딩 중...</p>
        </div>
      </div>
    );
  }

  // 텍스트 데이터가 전혀 없는 경우 - 정규화된 데이터 확인
  const hasOCRData = normalizedResults?.ocrResults?.length > 0;
  const hasAIData = normalizedResults?.aiResults?.length > 0;
  const hasCIMData = normalizedResults?.cimData != null;
  const hasFormattedText = formattedText && formattedText.trim();
  const hasEditableText = editableText && editableText.trim();

  if (!hasFormattedText && !hasEditableText && !hasOCRData && !hasAIData && !hasCIMData) {
    return (
      <div className="no-result">
        <div className="no-result-icon">📝</div>
        <h3>텍스트 결과가 없습니다</h3>
        <p>먼저 이미지를 업로드하고 분석을 실행해주세요.</p>
      </div>
    );
  }

  return (
    <div className="text-editor-content">
      {/* 오류 알림 표시 */}
      {hasError && (
        <div className="error-notification">
          <div className="error-content">
            <span className="error-icon">⚠️</span>
            <span className="error-text">{errorMessage}</span>
            <button
              className="error-dismiss"
              onClick={() => {
                setHasError(false);
                setErrorMessage('');
              }}
              title="알림 닫기"
            >
              ✕
            </button>
          </div>
        </div>
      )}

      <div className="editor-header">
        <h4>📝 텍스트 편집기</h4>
        <div className="editor-actions">
          <button
            className="action-btn edit-btn"
            onClick={() => setIsEditing(!isEditing)}
          >
            {isEditing ? '📖 읽기 모드' : '✏️ 편집 모드'}
          </button>
          
          <button
            className="action-btn reset-btn"
            onClick={handleReset}
            disabled={!formattedText && !hasOCRData && !hasAIData && !hasCIMData}
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
            disabled={isWordSaving}
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

      <div className="editor-container">
        {isEditing ? (
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
                content_style: 'body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; font-size: 14px }',
                language: 'ko_KR',
                placeholder: '여기에 텍스트를 입력하세요...'
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
          <div className="text-display">
            {editorContent || formattedText ? (
              <div
                className={`text-content ${hasError ? 'fallback-content' : ''}`}
                dangerouslySetInnerHTML={{ __html: editorContent || formattedText }}
              />
            ) : (
              <div className="empty-content">
                <p>표시할 텍스트가 없습니다.</p>
                {(hasOCRData || hasAIData || hasCIMData) && (
                  <button
                    className="load-ocr-btn"
                    onClick={() => {
                      const fallbackResult = extractTextWithPriority(normalizedResults);
                      setEditorContent(fallbackResult.text);

                      if (fallbackResult.confidence > 0.3) {
                        setHasError(false);
                        setErrorMessage('');
                      } else {
                        setHasError(true);
                        setErrorMessage(`낮은 신뢰도 데이터입니다 (${(fallbackResult.confidence * 100).toFixed(0)}%). 검토가 필요합니다.`);
                      }

                      if (onTextChange && typeof onTextChange === 'function') {
                        onTextChange(fallbackResult.text);
                      }
                    }}
                  >
                    📋 {getSourceDescription(
                      hasOCRData ? 'all_ocr' : hasAIData ? 'ai_analysis' : 'cim_object'
                    )} 데이터 불러오기
                  </button>
                )}
              </div>
            )}
          </div>
        )}
      </div>

      {/* CIM 원시 데이터 표시 */}
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

// PropTypes 정의
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

TextEditorTab.defaultProps = {
  formattedText: '',
  editableText: '',
  onTextChange: () => {},
  onSaveText: () => {},
  onResetText: () => {},
  onDownloadText: () => {},
  onCopyText: () => {},
  onSaveAsWord: () => {},
  isWordSaving: false,
  analysisResults: null
};

export default TextEditorTab;
