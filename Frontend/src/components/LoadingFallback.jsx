import React from 'react';
import PropTypes from 'prop-types';
import '../styles/LoadingFallback.css';

/**
 * 로딩 상태를 표시하는 공통 컴포넌트
 * Suspense fallback 및 일반 로딩 상태에 사용
 */
const LoadingFallback = ({
  message = '로딩 중...',
  size = 'medium',
  showSpinner = true,
  className = ''
}) => {
  const sizeClasses = {
    small: 'loading-fallback-small',
    medium: 'loading-fallback-medium',
    large: 'loading-fallback-large'
  };

  return (
    <div className={`loading-fallback ${sizeClasses[size]} ${className}`}>
      {showSpinner && (
        <div className="loading-spinner-container">
          <div className="loading-spinner"></div>
        </div>
      )}
      <div className="loading-message">
        {message}
      </div>
    </div>
  );
};

LoadingFallback.propTypes = {
  message: PropTypes.string,
  size: PropTypes.oneOf(['small', 'medium', 'large']),
  showSpinner: PropTypes.bool,
  className: PropTypes.string
};

export default LoadingFallback;