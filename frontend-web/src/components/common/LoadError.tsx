import React from 'react';

interface LoadErrorProps {
  message: string;
  onRetry: () => void;
  className?: string;
}

// 조회 실패 안내와 다시 받기 버튼(빈 데이터 안내와 다른 화면)
export const LoadError: React.FC<LoadErrorProps> = ({ message, onRetry, className = '' }) => (
  <div
    role="alert"
    className={`flex items-center justify-between gap-3 rounded-lg border border-danger/25 bg-danger/10 px-4 py-3 text-sm text-danger ${className}`}
  >
    <span>{message}</span>
    <button
      type="button"
      onClick={onRetry}
      className="shrink-0 rounded-md border border-danger/40 px-3 py-1 font-medium hover:bg-danger/10 transition-colors"
    >
      다시 시도
    </button>
  </div>
);
