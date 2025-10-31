import React from 'react';
import PropTypes from 'prop-types';

/**
 * SmartEye 전역 에러 바운더리 컴포넌트
 *
 * React 컴포넌트 트리에서 발생하는 JavaScript 에러를 캐치하고
 * 사용자 친화적인 에러 UI를 표시합니다.
 */
class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
      retryCount: 0
    };
  }

  static getDerivedStateFromError(error) {
    // 에러가 발생하면 다음 렌더링에서 에러 UI를 표시
    return { hasError: true };
  }

  componentDidCatch(error, errorInfo) {
    // 에러 정보를 상태에 저장
    this.setState({
      error,
      errorInfo
    });

    // 에러 로깅 (운영 환경에서는 에러 서비스로 전송)
    console.error('ErrorBoundary가 에러를 캐치했습니다:', error, errorInfo);

    // 선택적으로 에러 보고 서비스로 전송
    if (this.props.onError && typeof this.props.onError === 'function') {
      this.props.onError(error, errorInfo);
    }
  }

  handleRetry = () => {
    const { maxRetries = 3 } = this.props;

    if (this.state.retryCount < maxRetries) {
      this.setState(prevState => ({
        hasError: false,
        error: null,
        errorInfo: null,
        retryCount: prevState.retryCount + 1
      }));
    }
  };

  handleReset = () => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
      retryCount: 0
    });

    // 선택적으로 전체 앱 상태 리셋
    if (this.props.onReset && typeof this.props.onReset === 'function') {
      this.props.onReset();
    }
  };

  render() {
    const { hasError, error, errorInfo, retryCount } = this.state;
    const { children, fallback, maxRetries = 3, showDetails = false } = this.props;

    if (hasError) {
      // 커스텀 fallback UI가 제공된 경우
      if (fallback && typeof fallback === 'function') {
        return fallback(error, this.handleRetry, this.handleReset);
      }

      // 기본 에러 UI
      return (
        <div className="error-boundary">
          <div className="error-boundary-content">
            <div className="error-header">
              <span className="error-icon">⚠️</span>
              <h2>문제가 발생했습니다</h2>
            </div>

            <div className="error-message">
              <p>애플리케이션에서 예기치 않은 오류가 발생했습니다.</p>
              <p>페이지를 새로고침하거나 다시 시도해보세요.</p>
            </div>

            <div className="error-actions">
              {retryCount < maxRetries && (
                <button
                  className="retry-btn"
                  onClick={this.handleRetry}
                  type="button"
                >
                  🔄 다시 시도 ({maxRetries - retryCount}회 남음)
                </button>
              )}

              <button
                className="reset-btn"
                onClick={this.handleReset}
                type="button"
              >
                🏠 처음으로 돌아가기
              </button>

              <button
                className="reload-btn"
                onClick={() => window.location.reload()}
                type="button"
              >
                🔃 페이지 새로고침
              </button>
            </div>

            {/* 개발 환경에서만 상세 정보 표시 */}
            {(process.env.NODE_ENV === 'development' || showDetails) && error && (
              <details className="error-details">
                <summary>개발자 정보 (클릭하여 펼치기)</summary>
                <div className="error-stack">
                  <h4>에러:</h4>
                  <pre>{error.toString()}</pre>

                  {errorInfo && (
                    <>
                      <h4>컴포넌트 스택:</h4>
                      <pre>{errorInfo.componentStack}</pre>
                    </>
                  )}

                  {error.stack && (
                    <>
                      <h4>전체 스택 트레이스:</h4>
                      <pre>{error.stack}</pre>
                    </>
                  )}
                </div>
              </details>
            )}

            <div className="error-help">
              <h4>도움말:</h4>
              <ul>
                <li>브라우저를 새로고침해보세요</li>
                <li>브라우저 캐시를 지워보세요</li>
                <li>다른 브라우저에서 시도해보세요</li>
                <li>문제가 계속되면 관리자에게 문의하세요</li>
              </ul>
            </div>
          </div>
        </div>
      );
    }

    return children;
  }
}

// PropTypes 정의
ErrorBoundary.propTypes = {
  children: PropTypes.node.isRequired,
  fallback: PropTypes.func,
  onError: PropTypes.func,
  onReset: PropTypes.func,
  maxRetries: PropTypes.number,
  showDetails: PropTypes.bool
};

ErrorBoundary.defaultProps = {
  fallback: null,
  onError: null,
  onReset: null,
  maxRetries: 3,
  showDetails: false
};

// HOC(Higher-Order Component) 버전
export const withErrorBoundary = (Component, errorBoundaryProps = {}) => {
  const WrappedComponent = (props) => (
    <ErrorBoundary {...errorBoundaryProps}>
      <Component {...props} />
    </ErrorBoundary>
  );

  WrappedComponent.displayName = `withErrorBoundary(${Component.displayName || Component.name})`;

  return WrappedComponent;
};

export default ErrorBoundary;