import { useState, useCallback } from 'react';
import { apiService } from '../services/apiService';

export const useTextEditor = () => {
  const [formattedText, setFormattedText] = useState('');
  const [editableText, setEditableText] = useState('');
  const [isWordSaving, setIsWordSaving] = useState(false);

  const updateFormattedText = useCallback((text) => {
    setFormattedText(text);
    setEditableText(text);
  }, []);

  const saveText = useCallback(() => {
    // 로컬 스토리지에 저장
    try {
      localStorage.setItem('smarteye_edited_text', editableText);
      localStorage.setItem('smarteye_saved_timestamp', new Date().toISOString());
      alert('텍스트가 저장되었습니다.');
    } catch (error) {
      console.error('텍스트 저장 실패:', error);
      alert('텍스트 저장에 실패했습니다.');
    }
  }, [editableText]);

  const resetText = useCallback(() => {
    setEditableText(formattedText);
  }, [formattedText]);

  const downloadText = useCallback(() => {
    try {
      // HTML 태그 제거
      const plainText = editableText.replace(/<[^>]*>/g, '');
      
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
  }, [editableText]);

  const copyText = useCallback(async () => {
    try {
      // HTML 태그 제거
      const plainText = editableText.replace(/<[^>]*>/g, '');
      
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(plainText);
        alert('텍스트가 클립보드에 복사되었습니다.');
      } else {
        // Fallback for older browsers
        const textArea = document.createElement('textarea');
        textArea.value = plainText;
        textArea.style.position = 'fixed';
        textArea.style.left = '-999999px';
        textArea.style.top = '-999999px';
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        
        try {
          document.execCommand('copy');
          alert('텍스트가 클립보드에 복사되었습니다.');
        } catch (err) {
          console.error('클립보드 복사 실패:', err);
          alert('클립보드 복사에 실패했습니다.');
        }
        
        document.body.removeChild(textArea);
      }
    } catch (error) {
      console.error('복사 실패:', error);
      alert('복사에 실패했습니다.');
    }
  }, [editableText]);

  const saveAsWord = useCallback(async () => {
    if (!editableText.trim()) {
      alert('저장할 텍스트가 없습니다.');
      return;
    }

    setIsWordSaving(true);
    
    try {
      const filename = `smarteye_document_${new Date().toISOString().slice(0, 19).replace(/:/g, '-')}`;
      await apiService.saveAsWord(editableText, filename);
      alert('워드 문서가 성공적으로 저장되었습니다.');
    } catch (error) {
      console.error('워드 저장 오류:', error);
      
      let errorMessage = '워드 문서 저장 중 오류가 발생했습니다.';
      if (error.response?.status === 500) {
        errorMessage = '서버에서 워드 문서 생성에 실패했습니다. 잠시 후 다시 시도해주세요.';
      } else if (error.response?.data?.detail) {
        errorMessage = error.response.data.detail;
      } else if (error.message) {
        errorMessage = error.message;
      }
      
      alert(errorMessage);
    } finally {
      setIsWordSaving(false);
    }
  }, [editableText]);

  // 로컬 스토리지에서 저장된 텍스트 복원
  const restoreFromStorage = useCallback(() => {
    try {
      const savedText = localStorage.getItem('smarteye_edited_text');
      const savedTimestamp = localStorage.getItem('smarteye_saved_timestamp');
      
      if (savedText && savedTimestamp) {
        const savedDate = new Date(savedTimestamp);
        const daysDiff = (new Date() - savedDate) / (1000 * 60 * 60 * 24);
        
        // 7일 이내의 저장된 텍스트만 복원
        if (daysDiff <= 7) {
          if (confirm(`${savedDate.toLocaleString('ko-KR')}에 저장된 텍스트를 불러오시겠습니까?`)) {
            setEditableText(savedText);
            return true;
          }
        }
      }
    } catch (error) {
      console.error('저장된 텍스트 복원 실패:', error);
    }
    return false;
  }, []);

  return {
    formattedText,
    editableText,
    setEditableText,
    updateFormattedText,
    saveText,
    resetText,
    downloadText,
    copyText,
    saveAsWord,
    isWordSaving,
    restoreFromStorage
  };
};
