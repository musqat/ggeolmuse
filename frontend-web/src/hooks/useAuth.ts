import { createContext, useContext } from 'react';
import type { User } from '../services/api';

// 로그인 상태 컨텍스트와 훅. Provider 는 contexts/AuthContext.tsx 에 있다.
// Provider 파일이 컴포넌트만 내보내야 개발 서버가 그 파일만 갈아 끼운다(react-refresh)
export interface AuthContextType {
  isAuthenticated: boolean;
  isLoading: boolean;
  user: User | null;
  isAdmin: boolean;
  login: (email: string, password: string) =>Promise<void>;
  logout: () => void;
  signup: (email: string, password: string, nickname: string) =>Promise<void>;
  refreshUserData: () =>Promise<void>;
  forgotPassword: (email: string) =>Promise<void>;
  resetPassword: (token: string, newPassword: string) =>Promise<void>;
  resendVerificationEmail: (email: string) =>Promise<void>;
  loginWithGoogle: () =>Promise<void>;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
