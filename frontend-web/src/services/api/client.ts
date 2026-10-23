import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';

// Gateway를 통한 통합 API 연결
// 개발환경에서는 Vite 프록시 사용, 프로덕션에서는 환경변수 사용
const API_BASE = (import.meta.env.VITE_API_URL ?? '').trim() || '';

export const apiClient = axios.create({
  baseURL: API_BASE,
  timeout: 60000,
});

// 요청 인터셉터 (JWT 토큰 추가)
apiClient.interceptors.request.use(
  async (config) => {
    const requestUrl = config.url || '';

    // Public API 호출 시에는 토큰을 보내지 않음
    const isPublicApi = requestUrl.includes('/market/') ||
                        requestUrl.includes('/auth/login') ||
                        requestUrl.includes('/auth/register') ||
                        requestUrl.includes('/auth/social');

    if (!isPublicApi) {
      const token = localStorage.getItem('accessToken');
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    }

    return config;
  },
  (error) => Promise.reject(error)
);

// 토큰 갱신 중인지 추적
let isRefreshing = false;
// 토큰 갱신을 기다리는 요청(끝나면 resolve, 실패하면 reject)
interface PendingRequest {
  resolve: (token: string | null) => void;
  reject: (reason: unknown) => void;
}
let failedQueue: PendingRequest[] = [];

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

// 로그인 · 토큰 갱신 · 구글 교환 응답(refresh token 은 httpOnly 쿠키)
export type AccessTokenResponse = {
  accessToken: string;
  expiresIn: number;                    // access token 만료까지 남은 초
};

// refresh token 쿠키를 주고받는 인증 요청(로컬 compose 는 게이트웨이 포트가 달라 명시)
export const withRefreshCookie = { withCredentials: true };

// 토큰 갱신: refresh token 쿠키로 새 access token 을 받는다(쿠키도 새것으로 바뀐다)
export const requestNewAccessToken = () =>
  apiClient.post<AccessTokenResponse>('/auth/refresh', null, withRefreshCookie);

// refresh token 쿠키로 새 access token 받아 저장
// 한 번에 한 탭만 갱신(refresh token 이 회전해 늦은 탭은 다른 탭이 저장한 access token 을 씀)
export const refreshAccessToken = async (): Promise<string> => {
  const staleToken = localStorage.getItem('accessToken');
  const refresh = async () => {
    const current = localStorage.getItem('accessToken');
    if (current && current !== staleToken) return current;
    const { data } = await requestNewAccessToken();
    localStorage.setItem('accessToken', data.accessToken);
    return data.accessToken;
  };
  return navigator.locks ? navigator.locks.request('ggeolmuse-token-refresh', refresh) : refresh();
};

// 토큰 없이도 되는 백테스트 경로
const isOptionalAuthApi = (url: string) => url.includes('/trading-simulation/') || url.includes('/analysis/');

const sendWithoutToken = (client: AxiosInstance, request: InternalAxiosRequestConfig & { _retry?: boolean }) => {
  // 또 401 이면 갱신 없이 실패
  request._retry = true;
  delete request.headers.Authorization;
  return client(request);
};

// 401 이면 refresh token 으로 갱신하고 원래 요청을 다시 보냄(갱신 중 여부 · 대기열이 모듈에 하나라 갱신은 한 번만)
export const attachTokenRefresh = (client: AxiosInstance) => {
  client.interceptors.response.use(
    (response) => response,
    async (error) => {
      if (error.response?.status !== 401) return Promise.reject(error);

      const originalRequest = error.config;
      const requestUrl = originalRequest?.url || '';

      // 로그인/회원가입/refresh/로그아웃 요청의 401은 정상적인 실패
      if (requestUrl.includes('/auth/login') ||
          requestUrl.includes('/auth/register') ||
          requestUrl.includes('/auth/social') ||
          requestUrl.includes('/auth/refresh') ||
          requestUrl.includes('/auth/logout')) {
        return Promise.reject(error);
      }

      // 이미 재시도한 요청이면 로그아웃
      if (originalRequest._retry) {
        localStorage.removeItem('accessToken');
        window.dispatchEvent(new CustomEvent('auth:logout'));
        return Promise.reject(error);
      }

      // 토큰 갱신 중이면 대기열에 추가
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then(token => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return client(originalRequest);
        }, err => (isOptionalAuthApi(requestUrl) ? sendWithoutToken(client, originalRequest) : Promise.reject(err)));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const newAccessToken = await refreshAccessToken();

        // 대기 중인 요청들 처리
        processQueue(null, newAccessToken);

        // 원래 요청 재시도
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return client(originalRequest);
      } catch (refreshError) {
        // Refresh 실패 시 로그아웃(쿠키가 없거나 세션이 끝났다)
        processQueue(refreshError, null);
        localStorage.removeItem('accessToken');
        window.dispatchEvent(new CustomEvent('auth:logout'));
        if (isOptionalAuthApi(requestUrl)) return sendWithoutToken(client, originalRequest);
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }
  );
};

attachTokenRefresh(apiClient);

// 거래 API의 400 에러는 비즈니스 로직 검증 실패 (예상된 에러)
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const requestUrl = error.config?.url || '';
    if (error.response?.status === 400 &&
        (requestUrl.includes('/trade/buy') || requestUrl.includes('/trade/sell'))) {
      const detail = error.response?.data?.detail || error.message;
      console.warn(`[거래 검증 실패] ${detail}`);
    }
    return Promise.reject(error);
  }
);
