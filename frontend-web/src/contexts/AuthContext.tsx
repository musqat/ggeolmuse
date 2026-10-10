import React, { useState, useEffect } from 'react';
import { authApi, type User } from '../services/api';
import { tokenManager, checkAuthStatus } from '../utils/auth';
import { getApiErrorStatus } from '../utils/apiError';
import { buildGoogleLoginUrl } from '../utils/googleLogin';
import { AuthContext, type AuthContextType } from '../hooks/useAuth';

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
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
        } catch {
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
    const response = await authApi.getCurrentUser();
    // UserController가 직접 UserResponseDto를 리턴하므로 response.data로 접근
    setUser(response.data);
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
    } catch (error: unknown) {
      // 401 에러이고 첫 번째 시도인 경우 자동 재시도
      if (getApiErrorStatus(error) === 401 && retryCount === 0) {
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
    } finally {
      setIsLoading(false);
    }
  };

  const forgotPassword = async (email: string) => {
    setIsLoading(true);
    try {
      await authApi.forgotPassword({ email });
    } finally {
      setIsLoading(false);
    }
  };

  const resetPassword = async (token: string, newPassword: string) => {
    setIsLoading(true);
    try {
      await authApi.resetPassword({ token, newPassword });
    } finally {
      setIsLoading(false);
    }
  };

  const resendVerificationEmail = async (email: string) => {
    setIsLoading(true);
    try {
      await authApi.resendVerification({ email });
    } finally {
      setIsLoading(false);
    }
  };

  // 구글 로그인(Keycloak)으로 보낸다. 로그인 · 회원가입 모달이 같이 쓴다
  const loginWithGoogle = async () => {
    window.location.href = await buildGoogleLoginUrl(window.location.origin);
  };

  const contextValue: AuthContextType = {
    isAuthenticated,
    isLoading,
    user,
    isAdmin: user?.role === 'ADMIN',
    login,
    logout,
    signup,
    refreshUserData,
    forgotPassword,
    resetPassword,
    resendVerificationEmail,
    loginWithGoogle
  };

  return <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>;
};
