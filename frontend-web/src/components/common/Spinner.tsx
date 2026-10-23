import React from 'react';

// 불러오는 중 표시(돌아가는 원)
export const Spinner: React.FC<{ className?: string }> = ({ className = '' }) => (
  <div
    role="status"
    aria-label="불러오는 중"
    className={`animate-spin rounded-full h-12 w-12 border-b-2 border-brand ${className}`.trim()}
  />
);

// 화면 가운데 불러오는 중 표시
export const PageSpinner: React.FC = () => (
  <div className="max-w-7xl mx-auto px-4 py-6">
    <div className="flex items-center justify-center h-64">
      <Spinner />
    </div>
  </div>
);
