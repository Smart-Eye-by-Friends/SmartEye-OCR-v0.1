import React from 'react';
import PropTypes from 'prop-types';
import { Editor } from '@tinymce/tinymce-react';
import { getTinyMCEExtensionSafeConfig } from '../../utils/extensionCompatibility';
import TextEditorHeader from './TextEditorHeader';
import TextDisplay from './TextDisplay';
import CIMDataViewer from './CIMDataViewer';
import ErrorNotification from './ErrorNotification';

/**
 * TextEditor 프레젠테이션 컴포넌트 - 순수한 UI 렌더링
 * React 18 최적화된 렌더링 패턴 적용
 */
const TextEditorPresentation = React.memo(({
  // 상태
  editorState,
  loadingState,
  errorState,

  // 데이터
  dataAvailability,
  extractedTextData,
  normalizedResults,

  // 핸들러
  onStateUpdate,
  onContentChange,
  onConvertCim,

  // 부모 핸들러
  onSaveText,
  onResetText,
  onDownloadText,
  onCopyText,
  onSaveAsWord,
  isWordSaving
}) => {
  // 🔄 로딩 상태 렌더링
  if (loadingState.isLoading) {
    return (
      <div className="text-editor-loading">
        <div className="loading-spinner"></div>
        <p>📝 텍스트 데이터를 로딩 중...</p>
      </div>
    );
  }

  // 📭 빈 데이터 상태 렌더링
  if (!dataAvailability.hasAnyData) {
    return (
      <div className="text-editor-empty">
        <div className="empty-icon">📝</div>
        <h3>텍스트 결과가 없습니다</h3>
        <p>먼저 이미지를 업로드하고 분석을 실행해주세요.</p>
      </div>
    );
  }

  return (
    <div className="text-editor-content">
      {/* 📢 에러 알림 */}
      <ErrorNotification
        errorState={errorState}
        onDismiss={() => onStateUpdate('error', { hasError: false, message: '' })}
      />

      {/* 🎛️ 에디터 헤더 */}
      <TextEditorHeader
        editorState={editorState}
        loadingState={loadingState}
        dataAvailability={dataAvailability}
        normalizedResults={normalizedResults}
        onStateUpdate={onStateUpdate}
        onConvertCim={onConvertCim}
        onDownloadText={onDownloadText}
        onSaveAsWord={onSaveAsWord}
        isWordSaving={isWordSaving}
      />

      {/* 📝 에디터 메인 영역 */}
      <div className="editor-container">
        {editorState.isEditing ? (
          // ✏️ 편집 모드
          <div className="editor-wrapper">
            <Editor
              value={editorState.content}
              onEditorChange={onContentChange}
              init={{
                height: 500,
                menubar: false,
                plugins: [
                  'advlist', 'autolink', 'lists', 'link', 'image', 'charmap',
                  'preview', 'anchor', 'searchreplace', 'visualblocks', 'code',
                  'fullscreen', 'insertdatetime', 'media', 'table', 'help', 'wordcount'
                ],
                toolbar: 'undo redo | blocks | ' +
                  'bold italic forecolor | alignleft aligncenter ' +
                  'alignright alignjustify | bullist numlist outdent indent | ' +
                  'removeformat | help',
                language: 'ko_KR',
                placeholder: '여기에 텍스트를 입력하세요...',
                ...getTinyMCEExtensionSafeConfig()
              }}
            />

            {/* 에디터 하단 정보 */}
            <div className="editor-footer">
              <button
                className="save-btn"
                onClick={() => {
                  if (typeof onSaveText === 'function') {
                    onSaveText();
                  }
                  onStateUpdate('editor', { isEditing: false });
                }}
                disabled={!editorState.content.trim()}
              >
                💾 저장
              </button>
              <span className="character-count">
                문자 수: {editorState.content.replace(/<[^>]*>/g, '').length.toLocaleString()}
              </span>
              {errorState.hasError && (
                <span className="fallback-indicator">
                  📋 {errorState.source && `${errorState.source} `}데이터 사용 중
                </span>
              )}
            </div>
          </div>
        ) : (
          // 📖 읽기 모드
          <TextDisplay
            content={editorState.content}
            errorState={errorState}
            dataAvailability={dataAvailability}
            extractedTextData={extractedTextData}
            onLoadFallbackData={() => {
              if (extractedTextData) {
                onStateUpdate('editor', { content: extractedTextData.text });

                if (extractedTextData.confidence > 0.3) {
                  onStateUpdate('error', { hasError: false, message: '' });
                } else {
                  onStateUpdate('error', {
                    hasError: true,
                    message: `낮은 신뢰도 데이터 (${(extractedTextData.confidence * 100).toFixed(0)}%)`,
                    source: extractedTextData.source
                  });
                }
              }
            }}
          />
        )}
      </div>

      {/* 📊 CIM 데이터 뷰어 */}
      {editorState.showCimData && (
        <CIMDataViewer
          cimData={normalizedResults?.cimData}
          onClose={() => onStateUpdate('editor', { showCimData: false })}
        />
      )}
    </div>
  );
});

TextEditorPresentation.displayName = 'TextEditorPresentation';

TextEditorPresentation.propTypes = {
  // 상태
  editorState: PropTypes.shape({
    isEditing: PropTypes.bool.isRequired,
    content: PropTypes.string.isRequired,
    showCimData: PropTypes.bool.isRequired
  }).isRequired,

  loadingState: PropTypes.shape({
    isLoading: PropTypes.bool.isRequired,
    isConverting: PropTypes.bool.isRequired
  }).isRequired,

  errorState: PropTypes.shape({
    hasError: PropTypes.bool.isRequired,
    message: PropTypes.string.isRequired,
    source: PropTypes.string
  }).isRequired,

  // 데이터
  dataAvailability: PropTypes.shape({
    hasOCRData: PropTypes.bool,
    hasAIData: PropTypes.bool,
    hasCIMData: PropTypes.bool,
    hasFormattedText: PropTypes.bool,
    hasEditableText: PropTypes.bool,
    hasAnyData: PropTypes.bool
  }).isRequired,

  extractedTextData: PropTypes.shape({
    text: PropTypes.string,
    source: PropTypes.string,
    confidence: PropTypes.number
  }),

  normalizedResults: PropTypes.object,

  // 핸들러
  onStateUpdate: PropTypes.func.isRequired,
  onContentChange: PropTypes.func.isRequired,
  onConvertCim: PropTypes.func.isRequired,

  // 부모 핸들러
  onSaveText: PropTypes.func,
  onResetText: PropTypes.func,
  onDownloadText: PropTypes.func,
  onCopyText: PropTypes.func,
  onSaveAsWord: PropTypes.func,
  isWordSaving: PropTypes.bool
};

export default TextEditorPresentation;