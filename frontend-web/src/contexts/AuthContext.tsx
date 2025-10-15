import React, { createContext, useContext, useState, useEffect } from 'react';
import { authApi, type User } from '../services/api';
import { tokenManager, checkAuthStatus } from '../utils/auth';

interface AuthContextType {
  isAuthenticated: boolean;
  isLoading: boolean;
  user: User | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  signup: (email: string, password: string, nickname: string) => Promise<void>;
  refreshUserData: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true); // 초기 로딩 상태
  const [user, setUser] = useState<User | null>(null);

  // 전역 로그아웃 이벤트 리스너
  useEffect(() => {
    const handleGlobalLogout = () => {
      setIsAuthenticated(false);
      setUser(null);
      tokenManager.removeToken();
    };

    window.addEventListener('auth:logout', handleGlobalLogout);
    return () => window.removeEventListener('auth:logout', handleGlobalLogout);
  }, []);

  // 초기 인증 상태 확인
  useEffect(() => {
    const initializeAuth = async () => {
      setIsLoading(true);

      // 저장된 토큰이 있고 유효한지 확인
      if (checkAuthStatus()) {
        try {
          // 토큰이 유효하면 사용자 정보 가져오기
          await refreshUserData();
          setIsAuthenticated(true);
        } catch (error) {
          // 토큰이 있지만 유효하지 않은 경우 정리
          tokenManager.removeToken();
          setIsAuthenticated(false);
          setUser(null);
        }
      } else {
        setIsAuthenticated(false);
        setUser(null);
      }

      setIsLoading(false);
    };

    initializeAuth();
  }, []);

  const refreshUserData = async () => {
    try {
      const response = await authApi.getCurrentUser();
      // UserController가 직접 UserResponseDto를 리턴하므로 response.data로 접근
      setUser(response.data);
    } catch (error) {
      throw error;
    }
  };

  const login = async (email: string, password: string, retryCount = 0) => {
    setIsLoading(true);
    try {
      const response = await authApi.login({ email, password });
      const token = response.data;

      // 토큰 저장
      tokenManager.setToken(token);

      // 사용자 정보 가져오기
      await refreshUserData();

      setIsAuthenticated(true);
    } catch (error: any) {
      // 401 에러이고 첫 번째 시도인 경우 자동 재시도
      if (error?.response?.status === 401 && retryCount === 0) {
        setIsLoading(false); // 현재 로딩 상태 해제
        await new Promise(resolve => setTimeout(resolve, 500)); // 500ms 대기
        return login(email, password, 1); // 재시도 (retryCount = 1)
      }

      // 토큰 정리
      tokenManager.removeToken();
      setIsAuthenticated(false);
      setUser(null);
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const logout = () => {
    tokenManager.removeToken();
    setIsAuthenticated(false);
    setUser(null);
  };

  const signup = async (email: string, password: string, nickname: string) => {
    setIsLoading(true);
    try {
      await authApi.register({ email, password, nickname });
      // 회원가입 후 자동 로그인하지 않음 (이메일 인증 필요)
    } catch (error) {
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const contextValue: AuthContextType = {
    isAuthenticated,
    isLoading,
    user,
    login,
    logout,
    signup,
    refreshUserData
  };

  return <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};