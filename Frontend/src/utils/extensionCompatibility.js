/**
 * 브라우저 확장프로그램 호환성 유틸리티
 * Chrome 확장프로그램과의 충돌 방지 및 처리
 */

/**
 * 전역 에러 핸들러 설정 - 확장프로그램 충돌 무시
 */
export const setupExtensionErrorHandler = () => {
  // unhandledrejection 이벤트 핸들러
  const handleUnhandledRejection = (event) => {
    const errorMessage = event.reason?.message || '';

    // 브라우저 확장프로그램 관련 에러 패턴들
    const extensionErrorPatterns = [
      'message channel closed before a response was received',
      'A listener indicated an asynchronous response by returning true',
      'Extension context invalidated',
      'Could not establish connection. Receiving end does not exist',
      'The message port closed before a response was received',
      'chrome-extension://'
    ];

    // 확장프로그램 관련 에러인지 확인
    const isExtensionError = extensionErrorPatterns.some(pattern =>
      errorMessage.toLowerCase().includes(pattern.toLowerCase())
    );

    if (isExtensionError) {
      console.warn('🔧 브라우저 확장프로그램 충돌 감지됨 (무시 처리):', errorMessage);
      event.preventDefault(); // 브라우저 콘솔 에러 표시 방지
      return;
    }

    // 확장프로그램 에러가 아닌 실제 애플리케이션 에러는 정상 처리
    console.error('❌ 애플리케이션 에러:', event.reason);
  };

  // error 이벤트 핸들러
  const handleError = (event) => {
    const errorMessage = event.message || event.error?.message || '';

    // 브라우저 확장프로그램 관련 에러 무시
    if (errorMessage.includes('message channel closed') ||
        errorMessage.includes('Extension context invalidated')) {
      console.warn('🔧 브라우저 확장프로그램 스크립트 에러 감지됨 (무시 처리)');
      event.preventDefault();
      return;
    }
  };

  // 이벤트 리스너 등록
  if (typeof window !== 'undefined') {
    window.addEventListener('unhandledrejection', handleUnhandledRejection);
    window.addEventListener('error', handleError);

    // 정리 함수 반환
    return () => {
      window.removeEventListener('unhandledrejection', handleUnhandledRejection);
      window.removeEventListener('error', handleError);
    };
  }

  return () => {}; // 서버 사이드에서는 빈 정리 함수 반환
};

/**
 * TinyMCE 에디터용 확장프로그램 간섭 방지 설정
 */
export const getTinyMCEExtensionSafeConfig = () => {
  return {
    // 확장프로그램 간섭 방지를 위한 보안 설정
    setup: (editor) => {
      editor.on('init', () => {
        // DOM 조작 확장프로그램 감지 및 차단
        try {
          const editorDoc = editor.getDoc();
          if (editorDoc) {
            // 확장프로그램의 DOM 조작 감지
            const observer = new MutationObserver((mutations) => {
              mutations.forEach((mutation) => {
                // 확장프로그램이 추가한 요소들 제거
                if (mutation.addedNodes) {
                  mutation.addedNodes.forEach((node) => {
                    if (node.nodeType === 1 && node.classList) {
                      // 알려진 확장프로그램 클래스들 제거
                      const extensionClasses = [
                        'grammarly-inline',
                        '__grammarly_',
                        'gr_grammar_',
                        'translate-',
                        'skiptranslate',
                        'notranslate'
                      ];

                      extensionClasses.forEach(className => {
                        if (node.classList.contains(className) ||
                            node.className.includes(className)) {
                          console.warn('🚫 확장프로그램 요소 제거:', className);
                          node.remove();
                        }
                      });
                    }
                  });
                }
              });
            });

            // 에디터 내용 감시 시작
            observer.observe(editorDoc.body, {
              childList: true,
              subtree: true,
              attributes: false
            });

            // 에디터 제거 시 observer도 정리
            editor.on('remove', () => {
              observer.disconnect();
            });
          }
        } catch (error) {
          console.warn('TinyMCE 확장프로그램 방지 설정 실패:', error.message);
        }
      });
    },

    // CSP 헤더 강화로 외부 스크립트 실행 방지
    content_security_policy: "script-src 'self' 'unsafe-inline' 'unsafe-eval';",

    // 확장프로그램 스타일시트 무시
    content_style: `
      body {
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
        font-size: 14px;
      }
      /* 확장프로그램 스타일 무력화 */
      .grammarly-inline,
      .__grammarly_*,
      .gr_grammar_*,
      [class*="translate-"],
      [class*="grammarly"] {
        display: none !important;
        visibility: hidden !important;
        opacity: 0 !important;
        pointer-events: none !important;
      }
    `,

    // 브라우저 자동완성 및 확장프로그램 힌트 비활성화
    browser_spellcheck: false,
    contextmenu: false,

    // 확장프로그램이 접근하기 어려운 속성들 설정
    skin: false,
    theme: 'silver',

    // 외부 플러그인 로드 방지
    external_plugins: {},

    // 메뉴 및 툴바에서 확장프로그램 항목 제거
    removed_menuitems: 'spellchecker',

    // 에디터 초기화 지연으로 확장프로그램 로드 회피
    init_instance_callback: (editor) => {
      // 에디터 준비 완료 후 확장프로그램 인터페이스 차단
      setTimeout(() => {
        try {
          // 확장프로그램 메시지 리스너 무력화
          if (window.chrome && window.chrome.runtime && window.chrome.runtime.onMessage) {
            // 기존 리스너들을 저장하고 필터링된 버전으로 교체
            const originalAddListener = window.chrome.runtime.onMessage.addListener;
            window.chrome.runtime.onMessage.addListener = function(listener) {
              // TinyMCE 관련 메시지는 무시하는 래퍼 함수 생성
              const wrappedListener = (message, sender, sendResponse) => {
                if (message && (message.action || '').includes('tinymce')) {
                  console.warn('🚫 TinyMCE 관련 확장프로그램 메시지 차단');
                  return;
                }
                return listener(message, sender, sendResponse);
              };
              originalAddListener.call(this, wrappedListener);
            };
          }
        } catch (error) {
          console.warn('확장프로그램 메시지 차단 설정 실패:', error.message);
        }
      }, 100);
    }
  };
};

/**
 * 확장프로그램 감지 및 사용자 알림
 */
export const detectProblematicExtensions = () => {
  const problematicExtensions = [];

  try {
    // DOM에서 확장프로그램 요소들 감지
    const extensionSelectors = [
      '[class*="grammarly"]',
      '[class*="__grammarly"]',
      '[class*="gr_grammar"]',
      '[class*="translate"]',
      '[id*="grammarly"]',
      '[data-gramm]',
      'grammarly-inline'
    ];

    extensionSelectors.forEach(selector => {
      const elements = document.querySelectorAll(selector);
      if (elements.length > 0) {
        problematicExtensions.push({
          name: selector.includes('grammarly') ? 'Grammarly' : 'Translation Extension',
          selector: selector,
          count: elements.length
        });
      }
    });

    // Chrome API 접근 가능 여부 확인
    if (window.chrome && window.chrome.runtime) {
      problematicExtensions.push({
        name: 'Chrome Extension API',
        selector: 'chrome.runtime',
        count: 1
      });
    }

    return problematicExtensions;
  } catch (error) {
    console.warn('확장프로그램 감지 실패:', error.message);
    return [];
  }
};

/**
 * 사용자에게 확장프로그램 충돌 경고 표시
 */
export const showExtensionWarning = (extensions) => {
  if (extensions.length === 0) return;

  const extensionNames = extensions.map(ext => ext.name).join(', ');

  console.warn(`
🔧 브라우저 확장프로그램 충돌 감지
감지된 확장프로그램: ${extensionNames}

텍스트 편집기에서 다음과 같은 문제가 발생할 수 있습니다:
- 무한 로딩 현상
- 콘솔 에러 메시지 ("message channel closed")
- 텍스트 편집 지연

해결 방법:
1. 문제가 지속되면 브라우저의 시크릿/프라이빗 모드 사용
2. 확장프로그램 일시적 비활성화
3. 브라우저 새로고침 후 재시도

이 경고는 기능에는 영향을 주지 않으며, 안전하게 무시할 수 있습니다.
`);

  // 사용자에게 한 번만 알림 (세션당)
  if (!sessionStorage.getItem('extensionWarningShown')) {
    sessionStorage.setItem('extensionWarningShown', 'true');

    // 3초 후에 사라지는 알림 토스트 생성
    const toast = document.createElement('div');
    toast.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      background: #ff9800;
      color: white;
      padding: 15px;
      border-radius: 5px;
      box-shadow: 0 4px 6px rgba(0,0,0,0.1);
      z-index: 10000;
      max-width: 350px;
      font-size: 14px;
      font-family: Arial, sans-serif;
    `;
    toast.innerHTML = `
      <strong>🔧 브라우저 확장프로그램 감지</strong><br>
      <small>텍스트 편집기의 일부 오류는 확장프로그램 충돌로 인해 발생할 수 있습니다. 기능에는 영향이 없습니다.</small>
    `;

    document.body.appendChild(toast);

    setTimeout(() => {
      if (document.body.contains(toast)) {
        document.body.removeChild(toast);
      }
    }, 5000);
  }
};

export default {
  setupExtensionErrorHandler,
  getTinyMCEExtensionSafeConfig,
  detectProblematicExtensions,
  showExtensionWarning
};