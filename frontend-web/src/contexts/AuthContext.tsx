import React, { createContext, useContext, useState, useEffect } from 'react';
import { authApi, type User } from '../services/api';
import { tokenManager, checkAuthStatus } from '../utils/auth';

interface AuthContextType {
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

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [isAuthenticated, setIsAuthenticated] = useState(DEV_SKIP_AUTH);
  const [isLoading, setIsLoading] = useState(!DEV_SKIP_AUTH);
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

  const forgotPassword = async (email: string) => {
    setIsLoading(true);
    try {
      await authApi.forgotPassword({ email });
    } catch (error) {
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const resetPassword = async (token: string, newPassword: string) => {
    setIsLoading(true);
    try {
      await authApi.resetPassword({ token, newPassword });
    } catch (error) {
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const resendVerificationEmail = async (email: string) => {
    setIsLoading(true);
    try {
      await authApi.resendVerification({ email });
    } catch (error) {
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const loginWithGoogle = async () => {
    try {
      // PKCE code_verifier 생성
      const generateRandomString = (length: number) => {
        const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
        let result = '';
        const randomValues = new Uint8Array(length);
        crypto.getRandomValues(randomValues);
        randomValues.forEach(v => result += chars[v % chars.length]);
        return result;
      };

      const codeVerifier = generateRandomString(64);

      // code_challenge 생성 (SHA256)
      const sha256 = async (plain: string) => {
        const encoder = new TextEncoder();
        const data = encoder.encode(plain);
        const hash = await crypto.subtle.digest('SHA-256', data);
        return btoa(String.fromCharCode(...new Uint8Array(hash)))
          .replace(/\+/g, '-')
          .replace(/\//g, '_')
          .replace(/=/g, '');
      };

      const codeChallenge = await sha256(codeVerifier);

      // CSRF 방어용 state 생성
      const state = crypto.randomUUID();

      // sessionStorage에 저장 (콜백에서 사용)
      sessionStorage.setItem('pkce_code_verifier', codeVerifier);
      sessionStorage.setItem('oauth_state', state);

      // Keycloak Google Identity Provider를 통한 직접 로그인
      const baseUrl = window.location.origin;
      const keycloakAuthUrl = `${baseUrl}/auth/realms/muscathan/protocol/openid-connect/auth`;
      const params = new URLSearchParams({
        client_id: 'ggeolmuse-frontend',
        response_type: 'code',
        scope: 'openid email profile',
        redirect_uri: `${baseUrl}/oauth/callback`,
        code_challenge: codeChallenge,
        code_challenge_method: 'S256',
        kc_idp_hint: 'google',
        state,
      });

      // Keycloak Google OAuth로 리디렉션
      window.location.href = `${keycloakAuthUrl}?${params.toString()}`;
    } catch (error) {
      throw error;
    }
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

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
