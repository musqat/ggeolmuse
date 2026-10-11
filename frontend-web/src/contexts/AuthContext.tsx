import React, { useState, useEffect } from 'react';
import { authApi, refreshAccessToken, type User } from '../services/api';
import { tokenManager, checkAuthStatus } from '../utils/auth';
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
      tokenManager.removeTokens();
    };

    window.addEventListener('auth:logout', handleGlobalLogout);
    return () => window.removeEventListener('auth:logout', handleGlobalLogout);
  }, []);

  // 초기 인증 상태 확인
  useEffect(() => {
    const initializeAuth = async () => {
      setIsLoading(true);

      // access 가 유효하거나, 만료됐어도 refresh token 으로 갱신되면 로그인 상태를 이어 간다(새로고침해도 유지)
      if (checkAuthStatus() || (await refreshStoredSession())) {
        try {
          // 토큰이 유효하면 사용자 정보 가져오기
          await refreshUserData();
          setIsAuthenticated(true);
        } catch {
          // 토큰이 있지만 유효하지 않은 경우 정리
          tokenManager.removeTokens();
          setIsAuthenticated(false);
          setUser(null);
        }
      } else {
        tokenManager.removeTokens();
        setIsAuthenticated(false);
        setUser(null);
      }

      setIsLoading(false);
    };

    initializeAuth();
  }, []);

  // 남은 refresh token 으로 갱신해 본다. 없거나 실패하면 false
  const refreshStoredSession = async () => {
    if (!tokenManager.getRefreshToken()) return false;
    try {
      await refreshAccessToken();
      return true;
    } catch {
      return false;
    }
  };

  const refreshUserData = async () => {
    const response = await authApi.getCurrentUser();
    // UserController가 직접 UserResponseDto를 리턴하므로 response.data로 접근
    setUser(response.data);
  };

  const login = async (email: string, password: string) => {
    setIsLoading(true);
    try {
      const { data } = await authApi.login({ email, password });
      // 이전 사용자의 refresh token 이 남지 않게 둘 다 새로 넣는다
      tokenManager.setTokens(data.accessToken, data.refreshToken);

      // 사용자 정보 가져오기
      await refreshUserData();

      setIsAuthenticated(true);
    } catch (error: unknown) {
      tokenManager.removeTokens();
      setIsAuthenticated(false);
      setUser(null);
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  // 화면은 바로 로그아웃시키고, Keycloak 세션 종료는 뒤에서 보낸다(실패해도 토큰은 이미 지웠다)
  const logout = () => {
    const refreshToken = tokenManager.getRefreshToken();
    tokenManager.removeTokens();
    setIsAuthenticated(false);
    setUser(null);
    if (refreshToken) {
      authApi.logout(refreshToken).catch((error) => console.warn('서버 로그아웃 실패', error));
    }
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
