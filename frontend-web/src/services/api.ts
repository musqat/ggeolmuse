import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';
import type { ApiResponse } from '../types/api';
import type { StockPrice } from '../types/stock';
import type { OHLCData } from '../types/ohlc';

// Gateway를 통한 통합 API 연결
// 개발환경에서는 Vite 프록시 사용, 프로덕션에서는 환경변수 사용
const API_BASE = (import.meta.env.VITE_API_URL ?? '').trim() || '';

const apiClient = axios.create({
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
// 토큰 갱신을 기다리는 요청들. 갱신이 끝나면 resolve, 실패하면 reject 한다.
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

// refresh token 쿠키로 새 access token 을 받아 저장하고 돌려준다. 401 인터셉터와 첫 화면 확인(AuthContext)이 같이 쓴다.
// refresh token 은 갱신마다 바뀌고 옛것은 거절된다(회전). 탭 여러 개가 같이 갱신하면 늦은 탭이 이미 쓴 토큰을 보내게 되니
// 브라우저 잠금으로 한 번에 한 탭만 갱신하고, 기다린 탭은 그사이 다른 탭이 저장한 access token 을 그대로 쓴다
export const refreshAccessToken = async (): Promise<string> => {
  const staleToken = localStorage.getItem('accessToken');
  const refresh = async () => {
    const current = localStorage.getItem('accessToken');
    if (current && current !== staleToken) return current;
    const { data } = await authApi.refreshToken();
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

// 401 이면 refresh token 으로 갱신하고 원래 요청을 그 클라이언트로 다시 보낸다.
// 클라이언트 셋(apiClient · 관리자 · AI 채팅)이 같이 쓰고, 갱신 중 여부와 대기열은 모듈에 하나라 갱신은 한 번만 한다
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

// /market/stocks 가 주는 종목 한 건.
// /market/symbols 는 자동완성용이라 symbol 만 준다.
export interface AssetSummary {
  symbol: string;
  name?: string;
  assetType?: string;
  marketCap?: number;
  currentPrice?: number;
  date?: string;
  available?: boolean;
}

export const stockApi = {
  // 전체 종목 목록 조회
  getAllSymbols: () =>
    apiClient.get<AssetSummary[]>(`/market/symbols`),

  // 특정 종목 현재가 조회
  getCurrentPrice: (symbol: string) =>
    apiClient.get<ApiResponse<StockPrice>>(`/market/price/${symbol}`),

  // 여러 종목의 현재가를 한 번에 조회
  getCurrentPrices: (symbols: string[]) =>
    apiClient.get<{ [symbol: string]: StockPrice }>(`/market/prices`, {
      params: { symbols }
    }),

  // 모든 종목의 현재가 조회 (페이지네이션) - 시가총액 큰 순으로 고정
  getAllStocksWithPrices: (page: number = 0, size: number = 50, assetType?: string) =>
    apiClient.get<{ content: AssetSummary[]; totalPages: number; totalElements: number }>(`/market/stocks`, {
      params: { page, size, assetType }
    }),

  // 특정 종목의 OHLC 데이터 조회
  getOHLCData: (symbol: string, startDate?: string, endDate?: string) =>
    apiClient.get<OHLCData[]>(`/market/ohlc/multiple`, {
      params: { symbols: [symbol], startDate, endDate }
    }),

  // Bulk 환율 조회 (여러 날짜 한 번에) - Public API
  getExchangeRatesBulk: (dates: string[]) =>
    apiClient.post<Record<string, number>>('/market/fx/bulk', dates),
};

// 보유 종목 정보
export type HoldingResponse = {
  holdingId: string;
  accountId: string;
  symbol: string;
  totalQuantity: number;              // 총 보유 수량
  avgPurchasePrice: number;           // 평균 매수 단가
  totalInvestedAmount: number;        // 총 투자 금액
  createdAt: string;
  currentPrice?: number;              // 현재가
  currentValue?: number;              // 현재 평가액
  unrealizedPnL?: number;             // 미실현 손익
  returnRate?: number;                // 수익률
};

// 포트폴리오 요약 정보
export type PortfolioSummaryResponse = {
  totalInvestedAmount: number;        // 총 투자 금액
  totalCurrentValue: number;          // 총 현재 평가액
  totalUnrealizedPnL: number;         // 총 미실현 손익
  totalReturnRate: number;            // 총 수익률
  holdingCount: number;               // 보유 종목 수
  holdings: HoldingResponse[];
  symbolReturnRates: { [symbol: string]: number };      // 종목별 수익률
  symbolUnrealizedPnL: { [symbol: string]: number };    // 종목별 미실현 손익
  backtestAvailable?: boolean;        // 백테스트 가능 여부
  backtestResult?: string;            // 백테스트 결과
  backtestCalculatedAt?: string;      // 백테스트 계산 일시
  backtestStatus?: string;            // 백테스트 상태
};

export const portfolioApi = {
  // 전체 포트폴리오 조회
  getPortfolio: () =>
    apiClient.get<HoldingResponse[]>('/portfolio'),

  // 계좌별 포트폴리오 조회
  getAccountPortfolio: (accountId: number) =>
    apiClient.get<HoldingResponse[]>(`/portfolio/account/${accountId}`),

  // 포트폴리오 종합 정보 조회 (현재가 필요)
  getPortfolioSummary: (currentPrices: { [symbol: string]: number }) =>
    apiClient.post<PortfolioSummaryResponse>('/portfolio/summary', currentPrices),
};

// 거래 주문 정보
type TradeOrder = {
  accountId: string | number;
  symbol: string;
  quantity: number;                                  // 주문 수량
  tradeDate: string;                                 // 거래일 (YYYY-MM-DD)
  priceType: 'OPEN' | 'HIGH' | 'LOW' | 'CLOSE' | 'MANUAL';  // 가격 유형
  manualPrice?: number;                              // 수동 입력 가격
};

// 거래 가능 여부 확인 요청
type TradingCapacityRequest = {
  accountId: string | number;
  symbol: string;
  tradeDate: string;                                 // 거래일 (YYYY-MM-DD)
  totalAmount?: number;                              // 매수 시: 총 금액
  quantity?: number;                                 // 매도 시: 수량
  priceType?: 'OPEN' | 'HIGH' | 'LOW' | 'CLOSE' | 'MANUAL';  // 매수 시: 가격 유형. 비우면 CLOSE
  manualPrice?: number;                              // 매수 시: 지정가 입력 가격
};

// 매수 · 매도 가능 수량. trade-service TradingCapacityResponseDto 와 같은 칸이다
export type TradingCapacityResponse = {
  symbol: string;
  tradeDate: string;                                 // 거래일 (YYYY-MM-DD)
  currentPrice: number;
  availableBalance: number;
  maxShares: number;
  totalValue?: number | null;
  currency: string;
  currentHoldings?: number | null;                   // 매도 때만
  maxSellableShares?: number | null;                 // 매도 때만
};

// 통합 거래 내역 한 줄. 매수 · 매도 · 배당이 섞여 온다 (trade-service TransactionHistoryController)
export type TransactionHistoryItem = {
  type: 'BUY' | 'SELL' | 'DIVIDEND';
  tradeId?: number;                                  // 배당은 연결된 매수의 Trade ID
  accountId?: number;                                // 매수 · 매도만
  symbol: string;
  quantity?: number | null;
  price?: number | null;
  totalAmount: number;                               // 배당은 세후 금액
  fee?: number | null;
  grossAmount?: number;                              // 배당만: 세전 금액
  taxAmount?: number;                                // 배당만: 원천징수세
  dividendPerShare?: number;                         // 배당만
  shares?: number;                                   // 배당만: 배당 받은 주식 수
  date: string;
  executedAt: string;
  status?: 'COMPLETED' | 'CANCELLED';                // 매수 · 매도만. 잔액 반영 실패면 CANCELLED
  cancelReason?: string | null;
};

// 매수 · 매도 체결 결과. trade-service TradeResponseDto 와 같은 칸이다
export type TradeResult = {
  id: number;
  accountId: number;
  symbol: string;
  tradeType: 'BUY' | 'SELL';
  quantity: number;
  price: number;                                     // 체결가 (슬리피지 반영)
  totalAmount: number;                               // 매수는 체결 금액 + 수수료, 매도는 체결 금액 - 수수료
  fee: number;                                       // 수수료
  tradeDate: string;                                 // 거래일 (YYYY-MM-DD)
  executedAt: string;                                // 체결 일시
};

export const tradeApi = {
  // 매수 주문
  buy: (order: TradeOrder) =>
    apiClient.post<TradeResult>('/trade/buy', order),

  // 매도 주문
  sell: (order: TradeOrder) =>
    apiClient.post<TradeResult>('/trade/sell', order),

  // 매수 가능 여부 확인
  canBuy: (payload: TradingCapacityRequest) =>
    apiClient.post<TradingCapacityResponse>('/trade/can-buy', payload),

  // 매도 가능 여부 확인
  canSell: (payload: TradingCapacityRequest) =>
    apiClient.post<TradingCapacityResponse>('/trade/can-sell', payload),

  // 통합 거래 내역 조회 (매수/매도/배당)
  history: () =>
    apiClient.get<TransactionHistoryItem[]>('/transactions/history'),
};

// 계좌 요약 정보
export type AccountSummary = {
  accountId: number;
  accountName: string;                  // 계좌명
  accountNumber?: string;               // 계좌번호
  commissionRate: number;               // 수수료율
  usdBalance: number;                   // USD 잔액
  krwBalance: number;                   // KRW 잔액
  createdAt?: string;                   // 생성일
};

// 계좌 상세 정보
type AccountDetail = {
  id: number;
  accountName: string;                  // 계좌명
  accountNumber: string;                // 계좌번호
  balanceKrw: number;                   // KRW 잔액
  balanceUsd: number;                   // USD 잔액
  avgExchangeRate: number;              // 평균 환율
  totalExchangedKrw: number;            // 총 환전 KRW
  commissionRate: number;               // 수수료율
  slippageRate: number;                 // 슬리피지율
  createdAt: string;                    // 생성일
};

// 계좌 잔액 정보
// 환전 가능 환율 범위 (user-service ExchangeRateRangeDto)
export type ExchangeRateRange = {
  min: number;
  max: number;
};

export type AccountBalance = {
  accountName: string;                  // 계좌명
  accountNumber: string;                // 계좌번호
  balanceKrw: number;                   // KRW 잔액
  balanceUsd: number;                   // USD 잔액
  currentValueKrw: number;              // KRW 환산 총액
  myAvgExchangeRate: number;            // 내 평균 환율
  currentExchangeRate: number;          // 현재 환율
  commissionRate: number;               // 수수료율
  slippageRate: number;                 // 슬리피지율
};


// 계좌 생성 요청
type CreateAccountRequest = {
  accountName: string;                  // 계좌명
  commissionRate: number;               // 수수료율 (0 ~ 0.05 = 0% ~ 5%)
  slippageRate?: number;                // 슬리피지율 (0 ~ 0.01 = 0% ~ 1%). 비우면 0.1%
};

// 입금 요청
type DepositRequest = {
  krwAmount: number;                    // 입금 금액 (KRW)
};

// 환전 요청
type ExchangeRequest = {
  fromCurrency: 'KRW' | 'USD';          // 출발 통화
  toCurrency: 'KRW' | 'USD';            // 도착 통화
  originalAmount: number;               // 환전 금액
  exchangeRate: number;                 // 환율
};

export const accountsApi = {
  // 계좌 목록 조회
  getAccounts: () =>
    apiClient.get<AccountSummary[]>('/accounts'),

  // 계좌 잔액 조회
  getAccountBalance: (accountId: number) =>
    apiClient.get<AccountBalance>(`/accounts/${accountId}/balance`),

  // 계좌 생성
  createAccount: (data: CreateAccountRequest) =>
    apiClient.post<AccountDetail>('/accounts', data),

  // KRW 입금
  depositKrw: (accountId: number, data: DepositRequest) =>
    apiClient.post<void>(`/accounts/${accountId}/deposit`, data),

  // 환전
  exchangeCurrency: (accountId: number, data: ExchangeRequest) =>
    apiClient.post<void>(`/accounts/${accountId}/exchange`, data),

  // 현재 환율 조회
  getCurrentExchangeRate: () =>
    apiClient.get<number>('/accounts/exchange-rates/current'),

  // 환전에 쓸 수 있는 환율 범위(저장된 환율의 최저 −5% ~ 최고 +5%, 정수)
  getExchangeRateRange: () =>
    apiClient.get<ExchangeRateRange>('/accounts/exchange-rates/range'),

  // Bulk 환율 조회 (여러 날짜 한 번에)
  getExchangeRatesBulk: (dates: string[]) =>
    apiClient.get<Record<string, number>>('/accounts/exchange-rates/bulk', {
      params: { dates }
    }),

  // 계좌 삭제
  deleteAccount: (accountId: number) =>
    apiClient.delete<void>(`/accounts/${accountId}`),
};

// 로그인 · 토큰 갱신 · 구글 교환 응답 (user-service AccessTokenResponseDto). refresh token 은 httpOnly 쿠키로 온다
export type AccessTokenResponse = {
  accessToken: string;
  expiresIn: number;                    // access token 만료까지 남은 초
};

// 구글 로그인 콜백의 인가 코드 교환 요청
type GoogleTokenRequest = {
  code: string;
  codeVerifier: string;                 // PKCE code_verifier
  redirectUri: string;
};

// refresh token 쿠키를 주고받는 인증 요청. 운영은 같은 출처지만 로컬 compose 는 게이트웨이가 다른 포트라 명시한다
const withRefreshCookie = { withCredentials: true };

// 로그인 요청
type LoginRequest = {
  email: string;
  password: string;
};

// 회원가입 요청
type RegisterRequest = {
  email: string;
  password: string;
  nickname: string;                     // 닉네임
};

// 인증 이메일 재발송 요청
type ResendVerificationRequest = {
  email: string;
};

// 사용자 정보
export type User = {
  id: number;
  email: string;
  nickname: string;                     // 닉네임
  role: 'USER' | 'ADMIN';               // 사용자 역할
  provider: string;                     // 로그인 제공자 (LOCAL, GOOGLE)
  emailVerified: boolean;               // 이메일 인증 여부
  createdAt: string;                    // 생성일
  profileImageUrl?: string;             // 프로필 이미지 URL
  socialEmail?: string;                 // 소셜 계정 이메일
  hasPassword: boolean;                 // 비밀번호로 로그인할 수 있는지(구글로만 가입하면 false)
};

// 비밀번호 변경 요청
export type ChangePasswordRequest = {
  currentPassword: string;              // 현재 비밀번호
  newPassword: string;                  // 새 비밀번호
};

// 닉네임 변경 요청
export type ChangeNicknameRequest = {
  nickname: string;                     // 새 닉네임
};

// 비밀번호 재설정 요청
export type ForgotPasswordRequest = {
  email: string;                        // 이메일
};

// 비밀번호 재설정 (토큰 사용)
export type ResetPasswordRequest = {
  token: string;                        // 재설정 토큰
  newPassword: string;                  // 새 비밀번호
};

export const authApi = {
  // 일반 로그인
  login: (credentials: LoginRequest) =>
    apiClient.post<AccessTokenResponse>('/auth/login', credentials, withRefreshCookie),

  // 회원가입
  register: (userData: RegisterRequest) =>
    apiClient.post<void>('/auth/register', userData),

  // 인증 이메일 재발송
  resendVerification: (data: ResendVerificationRequest) =>
    apiClient.post<void>('/auth/resend-verification', data),

  // 비밀번호 재설정 요청 (이메일 발송)
  forgotPassword: (data: ForgotPasswordRequest) =>
    apiClient.post<void>('/auth/forgot-password', data),

  // 비밀번호 재설정 (토큰 사용)
  resetPassword: (data: ResetPasswordRequest) =>
    apiClient.post<void>('/auth/reset-password', data),

  // 현재 사용자 정보 조회 (JWT 토큰 기반)
  getCurrentUser: () =>
    apiClient.get<User>('/users/me'),

  // 토큰 갱신: refresh token 쿠키로 새 access token 을 받는다(쿠키도 새것으로 바뀐다)
  refreshToken: () =>
    apiClient.post<AccessTokenResponse>('/auth/refresh', null, withRefreshCookie),

  // 로그아웃: 쿠키의 Keycloak 세션을 끊고 쿠키를 지운다
  logout: () =>
    apiClient.post<void>('/auth/logout', null, withRefreshCookie),

  // 구글 로그인 콜백의 인가 코드를 user-service 가 토큰으로 바꾼다
  exchangeGoogleCode: (data: GoogleTokenRequest) =>
    apiClient.post<AccessTokenResponse>('/auth/social/google/token', data, withRefreshCookie),

  // 비밀번호 변경
  changePassword: (data: ChangePasswordRequest) =>
    apiClient.put<void>('/users/me/password', data),

  // 닉네임 변경
  changeNickname: (data: ChangeNicknameRequest) =>
    apiClient.put<void>('/users/me', data),

  // 회원 탈퇴
  // 비밀번호로 가입한 사용자는 비밀번호를 싣는다. 구글 사용자는 본문 없이 보내고 서버가 최근 로그인인지 본다
  deleteAccount: (password?: string) =>
    apiClient.delete<void>('/users/me', password ? { data: { password } } : undefined),
};

// ===== 백테스트 타입 =====

// 백테스트 요청 다섯 종류가 공통으로 보내는 것.
// 환율은 fxMode 가 manual 일 때만 붙는다.
type BacktestCommonRequest = {
  reinvestDividends?: boolean;          // 배당 재투자 여부
  tradingFeeRate?: number;              // 거래 수수료율 (0.001 = 0.1%)
  dividendTaxRate?: number;             // 배당 소득세율
  purchaseFxRate?: number;              // 수동 환율 - 매수 시점
  currentFxRate?: number;               // 수동 환율 - 현재
};

// 단순 시뮬레이션 요청
export type SimulationRequest = BacktestCommonRequest & {
  symbol: string;                       // 종목 심볼
  purchaseDate: string;                 // 매수일 (YYYY-MM-DD)
  saleDate?: string;                    // 매도일 (YYYY-MM-DD, 기본값: 오늘)
  investmentAmount: number;             // 투자 금액
};

// 단순 시뮬레이션 응답. backtest-service SimulationResponse 와 같은 칸이다.
// 백엔드가 빈 칸을 null 로 보내서 숫자 · 날짜 칸은 null 일 수 있다. 날짜는 YYYY-MM-DD 문자열이다
export type SimulationResponse = {
  symbol: string;
  purchaseDate?: string | null;
  currentDate?: string | null;
  investmentAmount?: number | null;     // 투자 금액 (KRW)
  purchasePrice?: number | null;        // 매수가 (USD)
  shares?: number | null;
  currentPrice?: number | null;         // 현재가 (USD)
  currentValue?: number | null;         // 현재 가치 (USD)
  stockReturn?: number | null;          // 주가 변동 (USD)
  stockReturnPercent?: number | null;
  purchaseFxRate?: number | null;
  currentFxRate?: number | null;
  fxReturn?: number | null;             // 환율 변동 (KRW)
  fxReturnPercent?: number | null;
  totalDividends?: number | null;       // 배당금 (USD)
  dividendYield?: number | null;
  tradingFee?: number | null;           // 매매 수수료 (USD)
  remainingCash?: number | null;        // 매수 후 잔액 (USD)
  totalReturn?: number | null;
  totalReturnPercent?: number | null;
  currentValueKrw?: number | null;
  remainingCashKrw?: number | null;
  totalAssetKrw?: number | null;        // 주식 가치 + 잔액 + 배당금 (KRW)
  totalReturnKrw?: number | null;
  performanceSummary?: string | null;
  optimalBuyDate?: string | null;
  optimalBuyPrice?: number | null;
  optimalSellDate?: string | null;
  optimalSellPrice?: number | null;
  optimalReturnPercent?: number | null;
  dividendsReinvested?: number | null;
  dividendReinvestDates?: string[] | null;
};

// DCA(정기 적립) 전략 요청
export type DcaStrategyRequest = BacktestCommonRequest & {
  symbol: string;                       // 종목 심볼
  startDate: string;                    // 시작일
  endDate: string;                      // 종료일
  monthlyAmount: number;                // 월 투자 금액
  purchaseDay: number;                  // 매수일 (1-28)
  investmentInterval?: number;          // 투자 주기 (1, 2, 3, 6개월)
};

// 조건부 매수 전략 요청
export type ConditionalStrategyRequest = BacktestCommonRequest & {
  symbol: string;                       // 종목 심볼
  startDate: string;                    // 시작일
  endDate: string;                      // 종료일
  // 투자 모드에 따라 둘 중 하나만 보낸다.
  totalInvestment?: number;             // investmentMode 가 TOTAL_BUDGET 일 때
  amountPerPurchase?: number;           // PER_PURCHASE 일 때           // 투자 모드가 회당 금액일 때
  investmentMode?: string;              // total | perPurchase              // 총 투자 금액
  dropPercentage: number;               // 하락률 (0.05 = 5%)
  maxPurchases?: number;                // 최대 매수 횟수
};

// 종목 비교 요청
export type SymbolComparisonRequest = BacktestCommonRequest & {
  symbols: string[];                    // 비교할 종목 목록
  startDate: string;                    // 시작일
  endDate: string;                      // 종료일
  investmentAmount: number;             // 투자 금액
};

// 전략 파라미터
export type StrategyParameter = {
  strategyType: 'SIMPLE' | 'DCA' | 'CONDITIONAL_PURCHASE';  // 전략 유형
  name?: string;                        // 전략명
  // DCA 전략 필드
  monthlyAmount?: number;               // 월 투자 금액
  purchaseDay?: number;                 // 매수일
  investmentInterval?: number;          // 투자 주기 (1, 2, 3, 6개월)
  totalInvestmentLimit?: number;        // 총 투자 한도
  // 조건부 매수 전략 필드
  totalInvestment?: number;             // 총 투자 금액
  dropPercentage?: number;              // 하락률
  maxPurchases?: number;                // 최대 매수 횟수
  // 단순 전략 필드
  purchaseDate?: string;                // 매수일
};

// 전략 비교 요청
export type StrategyComparisonRequest = BacktestCommonRequest & {
  symbol: string;                       // 종목 심볼
  startDate: string;                    // 시작일
  endDate: string;                      // 종료일
  investmentAmount: number;             // 기준 투자 금액
  strategies: StrategyParameter[];      // 비교할 전략 목록
};

// 전략 응답
// 적립식 · 조건부 전략의 매수 한 건. 거래가 일어난 건만 오므로 날짜 · 가격 · 수량은 늘 있다
export type StrategyTransaction = {
  date: string;                         // 계획된 매수일
  actualDate?: string;                  // 실제 거래일 (주말 · 휴장일 조정)
  price: number;                        // 주가 (USD)
  shares: number;
  amount: number;                       // 투자금 (KRW)
  fxRate: number;
  trigger?: string;                     // 적립식은 "월정액", 조건부는 "5%하락" 같은 값
  fee?: number | null;                  // 매매 수수료 (USD, 배당 재투자는 없음)
};

// 적립식 · 조건부 전략 응답. backtest-service StrategyResponse 와 같은 칸이다
export type StrategyResponse = {
  symbol: string;
  startDate?: string | null;
  endDate?: string | null;
  strategyType?: string | null;
  transactions?: StrategyTransaction[] | null;
  totalTransactions?: number | null;
  totalInvested?: number | null;        // 총 투자금 (KRW)
  totalShares?: number | null;
  averagePrice?: number | null;         // 평균 단가 (USD)
  currentPrice?: number | null;
  currentValue?: number | null;         // 현재 가치 (USD)
  currentValueKrw?: number | null;
  remainingCashKrw?: number | null;
  totalAssetKrw?: number | null;
  totalReturn?: number | null;          // 총 수익 (USD)
  totalReturnPercent?: number | null;
  totalReturnKrw?: number | null;
  averageFxRate?: number | null;
  currentFxRate?: number | null;
  fxReturn?: number | null;
  fxReturnPercent?: number | null;
  totalDividends?: number | null;
  dividendsReinvested?: number | null;
  dividendYield?: number | null;
  totalTradingFee?: number | null;      // 매매 수수료 합계 (USD)
  strategyDetails?: string | null;
  performanceSummary?: string | null;
};

// 비교 항목에 실린 원래 응답. 종목 비교는 SimulationResponse, 전략 비교는 전략마다
// SimulationResponse 나 StrategyResponse 다. 화면이 칸이 있는지로 읽어서 두 쪽 칸을 모두 선택으로 둔다
export type ComparisonDetail = Partial<SimulationResponse> & Partial<StrategyResponse>;

// 비교 항목
export type ComparisonItem = {
  name: string;                         // 항목명
  totalInvested?: number;               // 총 투자액
  currentValueKrw?: number;             // 현재 가치 (KRW)
  totalInvestment?: number;             // 총 투자액 (레거시)
  finalValue?: number;                  // 최종 가치 (레거시)
  totalReturn?: number;                 // 총 수익
  totalReturnPercent: number;           // 총 수익률
  totalReturnKrw?: number;              // 총 수익 (KRW)

  // 종목 비교에서만 채워지는 것들. 전략 비교 응답에는 없다.
  symbol?: string;
  purchaseDate?: string;
  currentDate?: string;
  purchasePrice?: number;               // 단순 매수
  averagePrice?: number;                // 적립식·조건부 평균 단가
  currentPrice?: number;
  shares?: number;
  totalShares?: number;
  investmentAmount?: number;
  purchaseFxRate?: number;
  currentFxRate?: number;
  fxReturnPercent?: number;             // 환차익 비중
  daysHeld?: number;

  // 최적 타이밍을 요청했을 때만 온다.
  optimalBuyDate?: string;
  optimalBuyPrice?: number;
  optimalSellDate?: string;
  optimalSellPrice?: number;

  additionalData?: ComparisonDetail | null;
};

// 비교 응답
// 종목 · 전략 비교 응답. backtest-service ComparisonResponse 와 같은 칸이다
export type ComparisonResponse = {
  comparisonType?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  investmentAmount?: number | null;
  items: ComparisonItem[];              // 비교 항목 목록
  bestPerformer?: ComparisonItem | null;
  worstPerformer?: ComparisonItem | null;
  averageReturn?: number | null;
  medianReturn?: number | null;
  summary?: string | null;
};

// 백테스트 히스토리
export type BacktestHistoryDto = {
  backtestId: string;
  userId: string;
  backtestType: 'COMPARISON' | 'INVESTMENT_ANALYSIS' | 'STRATEGY_SIMULATION';  // 백테스트 유형
  requestParams: string;                // 요청 파라미터 (JSON)
  fxRateMode: 'auto' | 'manual';        // 환율 모드
  createdAt: string;                    // 생성일
};

// 백테스트 히스토리 페이지
export type BacktestHistoryPage = {
  content: BacktestHistoryDto[];        // 내용
  totalElements: number;                // 전체 항목 수
  totalPages: number;                   // 전체 페이지 수
  size: number;                         // 페이지 크기
  number: number;                       // 현재 페이지 번호
};

// ===== 백테스트 API =====

export const backtestApi = {
  // 단순 시뮬레이션 실행
  runSimulation: (data: SimulationRequest) =>
    apiClient.post<SimulationResponse>('/trading-simulation/simulation', data),

  // DCA 전략 실행
  runDcaStrategy: (data: DcaStrategyRequest) =>
    apiClient.post<StrategyResponse>('/analysis/strategy/dca', data),

  // 조건부 매수 전략 실행
  runConditionalStrategy: (data: ConditionalStrategyRequest) =>
    apiClient.post<StrategyResponse>('/analysis/strategy/conditional', data),

  // 종목 비교 분석
  compareSymbols: (data: SymbolComparisonRequest) =>
    apiClient.post<ComparisonResponse>('/analysis/compare/symbols', data),

  // 전략 비교 분석
  compareStrategies: (data: StrategyComparisonRequest) =>
    apiClient.post<ComparisonResponse>('/analysis/compare/strategies', data),

  // 백테스트 히스토리 조회
  getHistory: (page: number = 0, size: number = 20) =>
    apiClient.get<BacktestHistoryPage>('/backtest/history', { params: { page, size } }),
};
