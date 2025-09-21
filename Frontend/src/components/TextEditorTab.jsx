import React, { useState, useRef, useEffect } from 'react';
import PropTypes from 'prop-types';
import { Editor } from '@tinymce/tinymce-react';
import { apiService } from '../services/apiService';
import { safeGet, safeArray, normalizeAnalysisResults } from '../utils/dataUtils';

// 에러 감지 유틸리티 함수
const detectError = (text) => {
  if (!text || typeof text !== 'string') return false;

  const errorPatterns = [
    /error/i,
    /오류/,
    /실패/,
    /exception/i,
    /not found/i,
    /cannot/i,
    /unable/i,
    /invalid/i,
    /처리할 수 없습니다/,
    /불러올 수 없습니다/,
    /문제가 발생했습니다/
  ];

  return errorPatterns.some(pattern => pattern.test(text));
};

// 안전한 텍스트 추출 함수 (정규화된 데이터 사용)
const extractFallbackText = (normalizedResults) => {
  if (!normalizedResults) return '';

  // 정규화된 OCR 결과에서 텍스트 추출
  const ocrResults = normalizedResults.ocrResults || [];
  if (ocrResults.length > 0) {
    const ocrText = ocrResults
      .filter(result => result && result.text && result.text.trim())
      .map(result => result.text.trim())
      .join('\n\n');
    if (ocrText.trim()) return ocrText;
  }

  // AI 결과에서 텍스트 추출
  const aiResults = normalizedResults.aiResults || [];
  if (aiResults.length > 0) {
    const aiText = aiResults
      .filter(result => result && (result.description || result.text))
      .map(result => result.description || result.text)
      .join('\n\n');
    if (aiText.trim()) return aiText;
  }

  // CIM 데이터에서 텍스트 추출
  const cimData = normalizedResults.cimData;
  if (cimData) {
    try {
      if (typeof cimData === 'string') {
        return cimData.trim();
      } else if (typeof cimData === 'object') {
        // CIM 객체에서 텍스트 컨텐츠 추출 시도
        const extractedTexts = extractTextFromCIMObject(cimData);
        if (extractedTexts.length > 0) {
          return extractedTexts.join('\n\n');
        }

        // 마지막 수단: JSON 문자열화
        return JSON.stringify(cimData, null, 2);
      }
    } catch (error) {
      console.warn('CIM 데이터 파싱 오류:', error);
    }
  }

  return '추출 가능한 텍스트가 없습니다.';
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

    // 포맷된 텍스트 오류 감지
    const textToCheck = formattedText || editableText || '';
    const hasTextError = detectError(textToCheck);

    if (hasTextError) {
      setHasError(true);
      setErrorMessage('포맷팅된 텍스트를 불러올 수 없습니다. 원본 OCR 데이터를 표시합니다.');

      // 대체 텍스트 사용 - 정규화된 데이터 사용
      const fallbackText = extractFallbackText(normalized);
      setEditorContent(fallbackText);

      // onTextChange가 있다면 대체 텍스트로 업데이트
      if (onTextChange && typeof onTextChange === 'function') {
        onTextChange(fallbackText);
      }
    } else {
      setHasError(false);
      setErrorMessage('');
      setEditorContent(editableText || formattedText || '');
    }

    // 로딩 상태 해제
    setTimeout(() => setIsLoading(false), 300);
  }, [editableText, formattedText, analysisResults, onTextChange]);

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
      const resetContent = formattedText || '';

      // 리셋할 텍스트에 오류가 있는지 확인
      if (detectError(resetContent)) {
        const fallbackText = extractFallbackText(normalizedResults);
        setEditorContent(fallbackText);
        if (onTextChange && typeof onTextChange === 'function') {
          onTextChange(fallbackText);
        }
        setHasError(true);
        setErrorMessage('원본 텍스트에 오류가 있어 OCR 데이터로 복원했습니다.');
      } else {
        setEditorContent(resetContent);
        if (onTextChange && typeof onTextChange === 'function') {
          onTextChange(resetContent);
        }
        setHasError(false);
        setErrorMessage('');
      }

      if (onResetText && typeof onResetText === 'function') {
        onResetText();
      }
    } catch (error) {
      console.error('텍스트 리셋 오류:', error);
      setErrorMessage('텍스트 리셋 중 오류가 발생했습니다.');
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
                      const fallbackText = extractFallbackText(normalizedResults);
                      setEditorContent(fallbackText);
                      if (onTextChange && typeof onTextChange === 'function') {
                        onTextChange(fallbackText);
                      }
                    }}
                  >
                    📋 {hasOCRData ? 'OCR' : hasAIData ? 'AI' : 'CIM'} 데이터 불러오기
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
