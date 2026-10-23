import React, { useMemo, useState } from 'react';
import { useAuth } from '../hooks/useAuth';
import { AuthModalContext } from '../hooks/useAuthModal';
import LoginModal from '../components/auth/LoginModal';
import SignupModal from '../components/auth/SignupModal';
import SignupSuccessModal from '../components/auth/SignupSuccessModal';

// 앱에 하나만 두는 로그인 · 가입 · 가입 완료 창
export const AuthModalProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { login, signup } = useAuth();
  const [open, setOpen] = useState<'login' | 'signup' | null>(null);
  const [successEmail, setSuccessEmail] = useState('');
  const value = useMemo(() => ({ openLogin: () => setOpen('login'), openSignup: () => setOpen('signup') }), []);

  return (
    <AuthModalContext.Provider value={value}>
      {children}
      <LoginModal
        isOpen={open === 'login'}
        onClose={() => setOpen(null)}
        onSwitchToSignup={() => setOpen('signup')}
        onLogin={async (email, password) => { await login(email, password); }}
      />
      <SignupModal
        isOpen={open === 'signup'}
        onClose={() => setOpen(null)}
        onSwitchToLogin={() => setOpen('login')}
        onSignup={async (email, password, nickname) => { await signup(email, password, nickname); }}
        onSignupSuccess={setSuccessEmail}
      />
      <SignupSuccessModal isOpen={successEmail !== ''} onClose={() => setSuccessEmail('')} email={successEmail} />
    </AuthModalContext.Provider>
  );
};
