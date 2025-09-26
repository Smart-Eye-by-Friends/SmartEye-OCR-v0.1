import { useReducer, useCallback, useMemo, useRef } from 'react';
import { apiService } from '../services/apiService';

// Action types for the reducer
const STATE_ACTIONS = {
  SET_EDITING: 'SET_EDITING',
  SET_CONTENT: 'SET_CONTENT',
  SET_WORD_SAVING: 'SET_WORD_SAVING',
  RESET: 'RESET',
};

// Reducer function to manage complex state logic
const textEditorReducer = (state, action) => {
  switch (action.type) {
    case STATE_ACTIONS.SET_EDITING:
      return { ...state, isEditing: action.payload };
    case STATE_ACTIONS.SET_CONTENT:
      return { ...state, editableText: action.payload };
    case STATE_ACTIONS.SET_WORD_SAVING:
      return { ...state, isWordSaving: action.payload };
    case STATE_ACTIONS.RESET:
      return { ...state, editableText: action.payload };
    default:
      return state;
  }
};

export const useTextEditor = (initialFormattedText = '') => {
  const [state, dispatch] = useReducer(textEditorReducer, {
    isEditing: false,
    editableText: initialFormattedText,
    isWordSaving: false,
  });

  const formattedTextRef = useRef(initialFormattedText);
  formattedTextRef.current = initialFormattedText; // Keep ref updated with the latest prop

  const setEditableText = useCallback((text) => {
    dispatch({ type: STATE_ACTIONS.SET_CONTENT, payload: text });
  }, []);

  const saveText = useCallback(() => {
    try {
      localStorage.setItem('smarteye_edited_text', state.editableText);
      localStorage.setItem('smarteye_saved_timestamp', new Date().toISOString());
      alert('텍스트가 저장되었습니다.');
    } catch (error) {
      console.error('텍스트 저장 실패:', error);
      alert('텍스트 저장에 실패했습니다.');
    }
  }, [state.editableText]);

  const resetText = useCallback(() => {
    dispatch({ type: STATE_ACTIONS.RESET, payload: formattedTextRef.current });
  }, []);

  const downloadText = useCallback(() => {
    try {
      const plainText = state.editableText.replace(/<[^>]*>/g, '');
      const blob = new Blob([plainText], { type: 'text/plain;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `smarteye_text_${new Date().toISOString().slice(0, 10)}.txt`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (error) {
      console.error('다운로드 실패:', error);
      alert('다운로드에 실패했습니다.');
    }
  }, [state.editableText]);

  const copyText = useCallback(async () => {
    try {
      const plainText = state.editableText.replace(/<[^>]*>/g, '');
      if (!plainText) {
        alert('복사할 텍스트가 없습니다.');
        return;
      }
      await navigator.clipboard.writeText(plainText);
      alert('텍스트가 클립보드에 복사되었습니다.');
    } catch (error) {
      console.error('복사 실패:', error);
      alert('복사에 실패했습니다.');
    }
  }, [state.editableText]);

  const saveAsWord = useCallback(async () => {
    if (!state.editableText.trim()) {
      alert('저장할 텍스트가 없습니다.');
      return;
    }
    dispatch({ type: STATE_ACTIONS.SET_WORD_SAVING, payload: true });
    try {
      const filename = `smarteye_document_${new Date().toISOString().slice(0, 19).replace(/:/g, '-')}`;
      await apiService.saveAsWord(state.editableText, filename);
      alert('워드 문서가 성공적으로 저장되었습니다.');
    } catch (error) {
      console.error('워드 저장 오류:', error);
      alert('워드 문서 저장 중 오류가 발생했습니다.');
    } finally {
      dispatch({ type: STATE_ACTIONS.SET_WORD_SAVING, payload: false });
    }
  }, [state.editableText]);

  const restoreFromStorage = useCallback(() => {
    try {
      const savedText = localStorage.getItem('smarteye_edited_text');
      const savedTimestamp = localStorage.getItem('smarteye_saved_timestamp');
      if (savedText && savedTimestamp) {
        const savedDate = new Date(savedTimestamp);
        if ((new Date() - savedDate) / (1000 * 60 * 60 * 24) <= 7) {
          if (window.confirm(`${savedDate.toLocaleString('ko-KR')}에 저장된 텍스트를 불러오시겠습니까?`)) {
            setEditableText(savedText);
            return true;
          }
        }
      }
    } catch (error) {
      console.error('저장된 텍스트 복원 실패:', error);
    }
    return false;
  }, [setEditableText]);

  return useMemo(() => ({
    isEditing: state.isEditing,
    editableText: state.editableText,
    isWordSaving: state.isWordSaving,
    formattedText: formattedTextRef.current,
    setEditing: (isEditing) => dispatch({ type: STATE_ACTIONS.SET_EDITING, payload: isEditing }),
    setEditableText,
    saveText,
    resetText,
    downloadText,
    copyText,
    saveAsWord,
    restoreFromStorage,
  }), [state, setEditableText, saveText, resetText, downloadText, copyText, saveAsWord, restoreFromStorage]);
};
