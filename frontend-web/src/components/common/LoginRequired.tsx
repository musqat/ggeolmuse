import React from 'react';
import { LogIn } from 'lucide-react';
import { useAuthModal } from '../../hooks/useAuthModal';

// 로그인이 필요한 화면의 안내와 로그인 버튼
export const LoginRequired: React.FC<{ description: string }> = ({ description }) => {
  const { openLogin } = useAuthModal();

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      <div className="min-h-[60vh] flex items-center justify-center">
        <div className="text-center">
          <h1 className="text-3xl font-bold text-tx-1 mb-4">로그인이 필요한 서비스입니다</h1>
          <p className="text-lg text-tx-2 mb-6">{description}</p>
          <button
            onClick={openLogin}
            className="flex items-center space-x-2 bg-brand text-brand-ink px-6 py-3 rounded-lg hover:bg-brand-dark transition-colors mx-auto"
          >
            <LogIn className="w-5 h-5" />
            <span>로그인하기</span>
          </button>
        </div>
      </div>
    </div>
  );
};
