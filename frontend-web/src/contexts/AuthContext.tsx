import React, { useState, useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { authApi, refreshAccessToken, type User } from '../services/api';
import { tokenManager, checkAuthStatus } from '../utils/auth';
import { buildGoogleLoginUrl, type GoogleLoginOptions } from '../utils/googleLogin';
import { AuthContext, type AuthContextType } from '../hooks/useAuth';

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [user, setUser] = useState<User | null>(null);
  // 사용자별 조회 결과(계좌 · 보유 등)가 다음 사용자에게 보이지 않게 로그아웃 때 비움
  const queryClient = useQueryClient();

  // 전역 로그아웃 이벤트 리스너
  useEffect(() => {
    const handleGlobalLogout = () => {
      setIsAuthenticated(false);
      setUser(null);
      tokenManager.removeTokens();
      queryClient.clear();
    };

    window.addEventListener('auth:logout', handleGlobalLogout);
    return () => window.removeEventListener('auth:logout', handleGlobalLogout);
  }, [queryClient]);

  // 초기 인증 상태 확인
  useEffect(() => {
    const initializeAuth = async () => {
      setIsLoading(true);
      tokenManager.clearLegacyRefreshToken();

      // access 가 유효하거나, 만료됐어도 refresh token 쿠키로 갱신되면 로그인 상태를 이어 간다(새로고침해도 유지).
      // access 가 아예 없으면 로그인한 적이 없거나 로그아웃한 것이라 갱신을 묻지 않는다
      if (checkAuthStatus() || (tokenManager.getToken() !== null && (await refreshStoredSession()))) {
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

  // refresh token 쿠키로 갱신해 본다. 쿠키가 없거나 세션이 끝났으면 false
  const refreshStoredSession = async () => {
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
      // refresh token 은 서버가 httpOnly 쿠키로 심었다(이전 사용자 쿠키는 덮인다)
      tokenManager.setToken(data.accessToken);

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

  // 화면은 바로 로그아웃시키고, 서버에는 쿠키의 Keycloak 세션 종료와 쿠키 지우기를 뒤에서 보낸다
  const logout = () => {
    tokenManager.removeTokens();
    setIsAuthenticated(false);
    setUser(null);
    queryClient.clear();
    authApi.logout().catch((error) => console.warn('서버 로그아웃 실패', error));
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
  const loginWithGoogle = async (options?: GoogleLoginOptions) => {
    window.location.href = await buildGoogleLoginUrl(window.location.origin, options);
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
