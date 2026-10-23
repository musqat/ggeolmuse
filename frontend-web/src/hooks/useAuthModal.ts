import { createContext, useContext } from 'react';

// 로그인 · 가입 창을 여는 컨텍스트와 훅(공급자는 contexts/AuthModalContext.tsx)
export interface AuthModalContextType {
  openLogin: () => void;
  openSignup: () => void;
}

export const AuthModalContext = createContext<AuthModalContextType | undefined>(undefined);

export const useAuthModal = () => {
  const context = useContext(AuthModalContext);
  if (context === undefined) {
    throw new Error('useAuthModal must be used within an AuthModalProvider');
  }
  return context;
};
