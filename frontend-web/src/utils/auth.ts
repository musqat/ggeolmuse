// JWT 토큰 관리 유틸리티

export interface JwtPayload {
  exp?: number;
  sub?: string;
  email?: string;
  [claim: string]: unknown;
}

export const TOKEN_STORAGE_KEY = 'accessToken';
// 예전 버전이 refresh token 을 두던 키. 지금은 httpOnly 쿠키라 남은 값을 지울 때만 쓴다
export const REFRESH_TOKEN_STORAGE_KEY = 'refreshToken';

// 토큰 저장/조회/삭제
export const tokenManager = {
  getToken: (): string | null => {
    return localStorage.getItem(TOKEN_STORAGE_KEY);
  },

  setToken: (token: string): void => {
    localStorage.setItem(TOKEN_STORAGE_KEY, token);
  },

  removeToken: (): void => {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
  },

  // access token 과 예전 버전이 남긴 refresh token 을 지운다
  removeTokens: (): void => {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY);
  },

  // 예전 버전이 localStorage 에 둔 refresh token 만 지운다(로그인은 유지)
  clearLegacyRefreshToken: (): void => {
    localStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY);
  },

  // JWT 토큰에서 payload 디코딩 (간단한 구현)
  // JWT payload 는 발급자가 정한다. 공통으로 쓰는 것만 적고 나머지는 열어 둔다.
  decodeToken: (token: string): JwtPayload | null => {
    try {
      const payload = token.split('.')[1];
      if (!payload) return null;

      const decoded = atob(payload);
      return JSON.parse(decoded);
    } catch {
      // 토큰 형식이 아니면 payload 를 못 읽는다
      return null;
    }
  },

  // 토큰 만료 확인
  isTokenExpired: (token: string): boolean => {
    const payload = tokenManager.decodeToken(token);
    if (!payload || !payload.exp) return true;

    const now = Math.floor(Date.now() / 1000);
    return payload.exp < now;
  },

  // 현재 저장된 토큰이 유효한지 확인
  isCurrentTokenValid: (): boolean => {
    const token = tokenManager.getToken();
    if (!token) return false;

    return !tokenManager.isTokenExpired(token);
  },

  // 토큰에서 사용자 정보 추출
  getUserFromToken: (token: string): { email?: string; sub?: string } | null => {
    const payload = tokenManager.decodeToken(token);
    if (!payload) return null;

    return {
      email: payload.email,
      sub: payload.sub,
    };
  }
};

// 인증 상태 확인
export const checkAuthStatus = (): boolean => {
  return tokenManager.isCurrentTokenValid();
};
