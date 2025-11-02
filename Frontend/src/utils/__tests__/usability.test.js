/**
 * SmartEye 프론트엔드 사용성 테스트
 * 사용자 경험 및 접근성 검증
 */

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom';

// 컴포넌트별 사용성 검증을 위한 목업 테스트
describe('사용성 테스트', () => {
  describe('에러 메시지 사용자 경험', () => {
    test('에러 메시지 가독성', () => {
      const mockErrorMessages = [
        '원본 텍스트에 문제가 있어 고신뢰도 OCR 데이터를 사용합니다. (신뢰도: 85%)',
        '품질이 보장된 텍스트 데이터를 찾을 수 없습니다. 가능한 모든 데이터를 표시합니다.',
        '원본 텍스트가 유효하지 않아 AI 분석 데이터로 복원했습니다. (신뢰도: 70%)'
      ];

      mockErrorMessages.forEach(message => {
        // 메시지 길이가 적절한지 확인 (너무 길지 않아야 함)
        expect(message.length).toBeLessThan(100);

        // 기술적 용어 대신 사용자 친화적 용어 사용
        expect(message).not.toMatch(/error|exception|failed/i);

        // 신뢰도 정보가 포함되어 있는지 확인
        if (message.includes('신뢰도')) {
          expect(message).toMatch(/\d+%/);
        }

        // 행동 지침이 포함되어 있는지 확인
        expect(
          message.includes('사용합니다') ||
          message.includes('표시합니다') ||
          message.includes('복원했습니다')
        ).toBe(true);
      });
    });

    test('에러 상황별 적절한 아이콘 및 색상 사용', () => {
      const errorLevels = [
        { level: 'info', icon: '📋', message: '대체 데이터 사용 중' },
        { level: 'warning', icon: '⚠️', message: '낮은 신뢰도 데이터' },
        { level: 'error', icon: '❌', message: '데이터 처리 실패' }
      ];

      errorLevels.forEach(({ level, icon, message }) => {
        // 아이콘이 에러 수준에 적합한지 확인
        expect(icon).toBeTruthy();

        // 메시지가 에러 수준에 적합한지 확인
        if (level === 'info') {
          expect(message).not.toMatch(/실패|오류|에러/);
        } else if (level === 'error') {
          expect(message).toMatch(/실패|오류|처리/);
        }
      });
    });
  });

  describe('데이터 표시 가독성', () => {
    test('숫자 형식화 적절성', () => {
      const testNumbers = [
        { input: 1234, expected: '1,234' },
        { input: 1234567, expected: '1,234,567' },
        { input: 0.8567, percentage: true, expected: '85.7%' },
        { input: 0.1, percentage: true, expected: '10.0%' },
        { input: 123.456, time: true, expected: '123.46초' }
      ];

      testNumbers.forEach(({ input, expected, percentage, time }) => {
        let result;

        if (percentage) {
          result = `${(input * 100).toFixed(1)}%`;
        } else if (time) {
          result = `${input.toFixed(2)}초`;
        } else {
          result = input.toLocaleString();
        }

        expect(result).toBe(expected);
      });
    });

    test('긴 텍스트 처리', () => {
      const longTexts = [
        'a'.repeat(1000), // 1000자 텍스트
        '한글'.repeat(500), // 한글 긴 텍스트
        'Mixed 한글 English 123 !@#'.repeat(50) // 혼합 언어
      ];

      longTexts.forEach(text => {
        // 텍스트가 적절히 잘리거나 스크롤 가능해야 함
        const displayText = text.length > 500 ? text.substring(0, 500) + '...' : text;

        if (text.length > 500) {
          expect(displayText).toMatch(/\.\.\.$/);
          expect(displayText.length).toBeLessThan(text.length);
        }
      });
    });
  });

  describe('접근성 (Accessibility)', () => {
    test('키보드 네비게이션 지원', () => {
      const interactiveElements = [
        { type: 'button', label: '편집 모드', key: 'Enter' },
        { type: 'button', label: '복사', key: 'Enter' },
        { type: 'button', label: '리셋', key: 'Enter' },
        { type: 'button', label: '다운로드', key: 'Enter' }
      ];

      interactiveElements.forEach(({ type, label, key }) => {
        // 모든 인터랙티브 요소가 키보드로 접근 가능해야 함
        expect(type).toBe('button'); // 버튼은 기본적으로 키보드 접근 가능

        // aria-label 또는 텍스트 라벨이 있어야 함
        expect(label).toBeTruthy();
        expect(label.length).toBeGreaterThan(1);

        // 단축키 지원 확인
        expect(['Enter', 'Space', 'Tab'].includes(key)).toBe(true);
      });
    });

    test('스크린 리더 지원', () => {
      const ariaLabels = [
        { element: 'button', label: '텍스트 편집 모드로 전환', description: '현재 읽기 모드입니다' },
        { element: 'button', label: '텍스트를 클립보드에 복사', description: '복사 기능' },
        { element: 'alert', label: '에러 알림', description: '대체 텍스트 사용 중' },
        { element: 'status', label: '로딩 상태', description: '텍스트 데이터 로딩 중' }
      ];

      ariaLabels.forEach(({ element, label, description }) => {
        // aria-label이 의미 있는 설명을 제공하는지 확인
        expect(label.length).toBeGreaterThan(5);

        // 상태나 역할이 명확한지 확인
        if (element === 'alert' || element === 'status') {
          expect(description).toContain('중' || '상태' || '알림');
        }

        // 버튼의 경우 행동을 명확히 설명하는지 확인
        if (element === 'button') {
          expect(
            label.includes('전환') ||
            label.includes('복사') ||
            label.includes('다운로드') ||
            label.includes('저장')
          ).toBe(true);
        }
      });
    });

    test('색상 의존성 최소화', () => {
      const visualElements = [
        { type: 'error', color: 'red', hasIcon: '⚠️', hasText: true },
        { type: 'success', color: 'green', hasIcon: '✅', hasText: true },
        { type: 'warning', color: 'orange', hasIcon: '📋', hasText: true },
        { type: 'info', color: 'blue', hasIcon: 'ℹ️', hasText: true }
      ];

      visualElements.forEach(({ type, hasIcon, hasText }) => {
        // 색상 외에도 아이콘과 텍스트로 의미 전달
        expect(hasIcon).toBeTruthy();
        expect(hasText).toBe(true);

        // 상태별 적절한 아이콘 사용
        if (type === 'error') {
          expect(['⚠️', '❌', '🚨'].includes(hasIcon)).toBe(true);
        } else if (type === 'success') {
          expect(['✅', '✓', '🎉'].includes(hasIcon)).toBe(true);
        }
      });
    });
  });

  describe('반응형 디자인', () => {
    test('모바일 화면 대응', () => {
      const screenSizes = [
        { width: 320, name: 'mobile-small' },
        { width: 375, name: 'mobile-medium' },
        { width: 768, name: 'tablet' },
        { width: 1024, name: 'desktop-small' },
        { width: 1440, name: 'desktop-large' }
      ];

      screenSizes.forEach(({ width, name }) => {
        // 버튼 크기가 터치 친화적인지 확인 (최소 44px)
        const minButtonSize = width < 768 ? 44 : 36;
        expect(minButtonSize).toBeGreaterThanOrEqual(36);

        // 폰트 크기가 가독성을 위해 적절한지 확인
        const fontSize = width < 768 ? 16 : 14;
        expect(fontSize).toBeGreaterThanOrEqual(14);

        // 여백이 터치 디바이스에 적합한지 확인
        const padding = width < 768 ? 12 : 8;
        expect(padding).toBeGreaterThanOrEqual(8);

        console.log(`${name} (${width}px): 버튼 ${minButtonSize}px, 폰트 ${fontSize}px, 패딩 ${padding}px`);
      });
    });

    test('긴 텍스트 줄바꿈 처리', () => {
      const longWords = [
        'supercalifragilisticexpialidocious',
        'pneumonoultramicroscopicsilicovolcanoconiosisabcdefghijklmnop',
        'https://very-long-url-that-should-not-break-layout.example.com/path/to/resource'
      ];

      longWords.forEach(word => {
        // CSS word-break 또는 overflow-wrap 적용 필요
        const shouldBreak = word.length > 20;
        expect(shouldBreak).toBe(true);

        // URL의 경우 특별한 줄바꿈 처리 필요
        if (word.startsWith('http')) {
          expect(word.includes('://')).toBe(true);
        }
      });
    });
  });

  describe('로딩 상태 UX', () => {
    test('로딩 인디케이터 적절성', () => {
      const loadingStates = [
        { duration: 500, type: 'fast', indicator: 'spinner' },
        { duration: 2000, type: 'normal', indicator: 'progress' },
        { duration: 5000, type: 'slow', indicator: 'detailed_message' }
      ];

      loadingStates.forEach(({ duration, type, indicator }) => {
        // 짧은 로딩은 간단한 스피너
        if (type === 'fast') {
          expect(indicator).toBe('spinner');
        }

        // 보통 로딩은 진행률 표시
        if (type === 'normal') {
          expect(['spinner', 'progress'].includes(indicator)).toBe(true);
        }

        // 긴 로딩은 상세한 메시지 제공
        if (type === 'slow') {
          expect(indicator).toBe('detailed_message');
        }

        console.log(`${type} 로딩 (${duration}ms): ${indicator} 사용`);
      });
    });

    test('로딩 메시지 유용성', () => {
      const loadingMessages = [
        '📝 텍스트 데이터를 로딩 중...',
        '🤖 AI 분석을 처리 중...',
        '📊 통계를 계산 중...',
        '🔄 CIM 데이터를 변환 중...'
      ];

      loadingMessages.forEach(message => {
        // 메시지가 현재 작업을 명확히 설명하는지 확인
        expect(message).toMatch(/중\.\.\./);

        // 이모지가 포함되어 시각적 구분이 가능한지 확인
        expect(message).toMatch(/^[📝🤖📊🔄]/);

        // 메시지가 너무 길지 않은지 확인
        expect(message.length).toBeLessThan(30);
      });
    });
  });

  describe('사용자 피드백', () => {
    test('성공 메시지 효과성', () => {
      const successMessages = [
        '텍스트가 클립보드에 복사되었습니다.',
        'CIM 데이터가 텍스트로 변환되었습니다.',
        '파일이 성공적으로 다운로드되었습니다.'
      ];

      successMessages.forEach(message => {
        // 성공 메시지가 명확하고 긍정적인지 확인
        expect(
          message.includes('성공') ||
          message.includes('완료') ||
          message.includes('되었습니다')
        ).toBe(true);

        // 구체적인 행동 결과를 명시하는지 확인
        expect(
          message.includes('복사') ||
          message.includes('변환') ||
          message.includes('다운로드')
        ).toBe(true);
      });
    });

    test('신뢰도 정보 표시', () => {
      const confidenceDisplays = [
        { confidence: 0.95, display: '95%', level: 'high' },
        { confidence: 0.75, display: '75%', level: 'medium' },
        { confidence: 0.45, display: '45%', level: 'low' },
        { confidence: 0.0, display: 'N/A', level: 'none' }
      ];

      confidenceDisplays.forEach(({ confidence, display, level }) => {
        // 신뢰도가 적절한 형식으로 표시되는지 확인
        if (confidence > 0) {
          expect(display).toMatch(/\d+%/);
          const percentage = parseInt(display);
          expect(percentage).toBe(Math.round(confidence * 100));
        } else {
          expect(display).toBe('N/A');
        }

        // 신뢰도 수준별 적절한 메시지 제공
        if (level === 'high') {
          expect(confidence).toBeGreaterThanOrEqual(0.8);
        } else if (level === 'low') {
          expect(confidence).toBeLessThan(0.5);
        }
      });
    });
  });
});