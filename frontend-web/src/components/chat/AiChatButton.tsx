import React, { useState } from 'react';
import { useLocation } from 'react-router-dom';
import { MessageCircle } from 'lucide-react';
import AiChatModal from './AiChatModal';
import LoginModal from '../auth/LoginModal';
import { useAuth } from '../../contexts/AuthContext';
import { useAiChat } from '../../contexts/AiChatContext';

// 버튼을 숨길 경로 (admin, 인증 콜백 등) — 모달은 Context라 전역 동작
const HIDDEN_PREFIXES = ['/admin', '/auth/', '/oauth/', '/reset-password', '/unauthorized'];

const AiChatButton: React.FC = () => {
  const { login } = useAuth();
  const { openChat, closeChat } = useAiChat();
  const location = useLocation();
  const [loginOpen, setLoginOpen] = useState(false);

  const hideButton = HIDDEN_PREFIXES.some((p) => location.pathname.startsWith(p));

  return (
    <>
      {/* 플로팅 버튼 (admin/콜백 페이지선 숨김). 모달은 Context라 항상 마운트 */}
      {!hideButton && (
        <button
          onClick={() => openChat()}
          aria-label="AI 종목 분석"
          style={{ position: 'fixed', right: '24px', bottom: '24px', zIndex: 9000 }}
          className="w-14 h-14 rounded-full bg-brand text-white shadow-lg flex items-center justify-center hover:bg-brand-dark transition-all"
        >
          <MessageCircle className="w-6 h-6" />
        </button>
      )}

      <AiChatModal
        onRequireLogin={() => {
          closeChat();
          setLoginOpen(true);
        }}
      />

      <LoginModal
        isOpen={loginOpen}
        onClose={() => setLoginOpen(false)}
        onSwitchToSignup={() => setLoginOpen(false)}
        onLogin={async (email: string, password: string) => {
          await login(email, password);
        }}
      />
    </>
  );
};

export default AiChatButton;
