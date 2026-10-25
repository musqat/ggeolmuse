import React from 'react';

// 원 크기(sm 24px · md 32px · lg 48px)
const SIZE = { sm: 'h-6 w-6', md: 'h-8 w-8', lg: 'h-12 w-12' } as const;

// 불러오는 중 표시(돌아가는 원)
export const Spinner: React.FC<{ size?: keyof typeof SIZE; className?: string }> = ({ size = 'lg', className = '' }) => (
  <div
    role="status"
    aria-label="불러오는 중"
    className={`animate-spin rounded-full ${SIZE[size]} border-b-2 border-brand ${className}`.trim()}
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
