// JWT 토큰 관리 유틸리티

export const TOKEN_STORAGE_KEY = 'accessToken';

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

  // JWT 토큰에서 payload 디코딩 (간단한 구현)
  decodeToken: (token: string): any | null => {
    try {
      const payload = token.split('.')[1];
      if (!payload) return null;

      const decoded = atob(payload);
      return JSON.parse(decoded);
    } catch (error) {
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

// 자동 로그아웃 (토큰 만료 시)
export const handleTokenExpiration = (): void => {
  tokenManager.removeToken();
  // 필요시 추가 정리 작업
};