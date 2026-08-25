import axios from 'axios';
import type { ApiResponse } from '../types/api';
import type { Stock, StockPrice } from '../types/stock';
import type { Portfolio } from '../types/portfolio';

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
                        requestUrl.includes('/auth/social') ||
                        requestUrl.includes('/trading-simulation/') ||
                        requestUrl.includes('/analysis/');

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
let failedQueue: any[] = [];

const processQueue = (error: any, token: string | null = null) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

// 응답 인터셉터 (에러 처리 + 자동 토큰 갱신)
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    const requestUrl = originalRequest?.url || '';

    if (error.response?.status === 401) {
      // 로그인/회원가입/refresh 요청의 401은 정상적인 실패
      if (requestUrl.includes('/auth/login') ||
          requestUrl.includes('/auth/register') ||
          requestUrl.includes('/auth/social') ||
          requestUrl.includes('/auth/refresh')) {
        return Promise.reject(error);
      }

      // 이미 재시도한 요청이면 로그아웃
      if (originalRequest._retry) {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.dispatchEvent(new CustomEvent('auth:logout'));
        return Promise.reject(error);
      }

      // refresh token이 없으면 로그아웃
      const refreshToken = localStorage.getItem('refreshToken');
      if (!refreshToken) {
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
          return apiClient(originalRequest);
        }).catch(err => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      // Refresh token으로 새 access token 받아오기
      try {
        const response = await authApi.refreshToken();
        const newAccessToken = response.data;

        localStorage.setItem('accessToken', newAccessToken);

        // 대기 중인 요청들 처리
        processQueue(null, newAccessToken);

        // 원래 요청 재시도
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        // Refresh 실패 시 로그아웃
        processQueue(refreshError, null);
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.dispatchEvent(new CustomEvent('auth:logout'));
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    // 거래 API의 400 에러는 비즈니스 로직 검증 실패 (예상된 에러)
    if (error.response?.status === 400 &&
        (requestUrl.includes('/trade/buy') || requestUrl.includes('/trade/sell'))) {
      const detail = error.response?.data?.detail || error.message;
      console.warn(`[거래 검증 실패] ${detail}`);
      return Promise.reject(error);
    }

    return Promise.reject(error);
  }
);

export const stockApi = {
  // 전체 종목 목록 조회
  getAllSymbols: () =>
    apiClient.get<any[]>(`/market/symbols`),

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
    apiClient.get(`/market/stocks`, {
      params: { page, size, assetType }
    }),

  // 특정 종목의 OHLC 데이터 조회
  getOHLCData: (symbol: string, startDate?: string, endDate?: string) =>
    apiClient.get(`/market/ohlc/multiple`, {
      params: { symbols: [symbol], startDate, endDate }
    }),

  // 여러 종목의 OHLC 데이터 조회
  getMultipleOHLCData: (symbols: string[], startDate: string, endDate: string) =>
    apiClient.get(`/market/ohlc/multiple`, {
      params: { symbols, startDate, endDate }
    }),

  // 배당 내역 조회
  getDividendHistory: (symbol: string, startDate?: string, endDate?: string) =>
    apiClient.get(`/market/dividend/${symbol}`, {
      params: { startDate, endDate }
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

  // 특정 종목 보유 현황 조회
  getHoldingBySymbol: (accountId: number, symbol: string) =>
    apiClient.get<HoldingResponse>(`/portfolio/account/${accountId}/symbol/${symbol}`),

  // 포트폴리오 종합 정보 조회 (현재가 필요)
  getPortfolioSummary: (currentPrices: { [symbol: string]: number }) =>
    apiClient.post<PortfolioSummaryResponse>('/portfolio/summary', currentPrices),

  // 백테스트 결과 포함 포트폴리오 종합 정보 조회
  getPortfolioSummaryWithBacktest: (currentPrices: { [symbol: string]: number }) =>
    apiClient.post<PortfolioSummaryResponse>('/portfolio/summary-with-backtest', currentPrices),

  // 포트폴리오 평가금액 히스토리 조회
  getEvaluationHistory: (accountId: number, startDate: string, endDate: string) =>
    apiClient.get<PortfolioEvaluationHistory[]>('/portfolio/evaluation-history', {
      params: { accountId, startDate, endDate }
    }),
};

// 포트폴리오 평가 히스토리
type PortfolioEvaluationHistory = {
  date: string;
  symbolEvaluations: { [symbol: string]: number };   // 종목별 평가액
  totalEvaluation: number;                           // 총 평가액
  symbolQuantities: { [symbol: string]: number };    // 종목별 수량
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
};

export const tradeApi = {
  // 매수 주문
  buy: (order: TradeOrder) =>
    apiClient.post('/trade/buy', order),

  // 매도 주문
  sell: (order: TradeOrder) =>
    apiClient.post('/trade/sell', order),

  // 매수 가능 여부 확인
  canBuy: (payload: TradingCapacityRequest) =>
    apiClient.post('/trade/can-buy', payload),

  // 매도 가능 여부 확인
  canSell: (payload: TradingCapacityRequest) =>
    apiClient.post('/trade/can-sell', payload),

  // 통합 거래 내역 조회 (매수/매도/배당)
  history: () =>
    apiClient.get('/transactions/history'),

  // 기존 페이징 API (deprecated, 통합 API 사용 권장)
  historyPaged: (page = 0, size = 20) =>
    apiClient.get('/trade-history/history', { params: { page, size } }),

  // 종목별 거래 내역 조회
  historyBySymbol: (symbol: string) =>
    apiClient.get(`/trade-history/history/${encodeURIComponent(symbol)}`),

  // 기간별 거래 내역 조회
  historyByPeriod: (startDate: string, endDate: string) =>
    apiClient.get('/trade-history/history/period', { params: { startDate, endDate } }),
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

export type BalanceResponse = AccountBalance;

// 계좌 생성 요청
type CreateAccountRequest = {
  accountName: string;                  // 계좌명
  commissionRate: number;               // 수수료율 (0 ~ 0.05 = 0% ~ 5%)
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

// 날짜 기준 환전 요청
type ExchangeByDateRequest = {
  fromCurrency: 'KRW' | 'USD';          // 출발 통화
  toCurrency: 'KRW' | 'USD';            // 도착 통화
  originalAmount: number;               // 환전 금액
  exchangeDate: string;                 // 환전 기준일 (YYYY-MM-DD)
};

export const accountsApi = {
  // 계좌 목록 조회
  getAccounts: () =>
    apiClient.get<AccountSummary[]>('/accounts'),

  // 계좌 상세 조회
  getAccountDetail: (accountId: number) =>
    apiClient.get<AccountDetail>(`/accounts/${accountId}`),

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

  // 날짜 기반 환전
  exchangeCurrencyByDate: (accountId: number, data: ExchangeByDateRequest) =>
    apiClient.post<void>(`/accounts/${accountId}/exchange/by-date`, data),

  // 현재 환율 조회
  getCurrentExchangeRate: () =>
    apiClient.get<number>('/accounts/exchange-rates/current'),

  // 날짜별 환율 조회
  getExchangeRateByDate: (date: string) =>
    apiClient.get<number>(`/accounts/exchange-rates/${date}`),

  // Bulk 환율 조회 (여러 날짜 한 번에)
  getExchangeRatesBulk: (dates: string[]) =>
    apiClient.get<Record<string, number>>('/accounts/exchange-rates/bulk', {
      params: { dates }
    }),

  // 수동 환율 검증
  validateManualExchangeRate: (rate: number) =>
    apiClient.post<number>('/accounts/exchange-rates/validate', null, { params: { rate } }),

  // 계좌 삭제
  deleteAccount: (accountId: number) =>
    apiClient.delete<void>(`/accounts/${accountId}`),
};

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

// Google 로그인 URL 응답
export type GoogleLoginUrlResponse = {
  loginUrl: string;                     // 로그인 URL
  provider: string;                     // 제공자 (google)
  redirectUri: string;                  // 리다이렉트 URI
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
    apiClient.post<string>('/auth/login', credentials),

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

  // Google 로그인 URL 조회
  getGoogleLoginUrl: () =>
    apiClient.get<GoogleLoginUrlResponse>('/auth/social/google/login-url'),

  // 현재 사용자 정보 조회 (JWT 토큰 기반)
  getCurrentUser: () =>
    apiClient.get<User>('/users/me'),

  // 토큰 갱신
  refreshToken: () => {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) {
      throw new Error('No refresh token available');
    }
    return apiClient.post<string>('/auth/refresh', { refreshToken });
  },

  // 비밀번호 변경
  changePassword: (data: ChangePasswordRequest) =>
    apiClient.put<void>('/users/me/password', data),

  // 닉네임 변경
  changeNickname: (data: ChangeNicknameRequest) =>
    apiClient.put<void>('/users/me', data),

  // 회원 탈퇴
  deleteAccount: () =>
    apiClient.delete<void>('/users/me'),
};

// ===== 백테스트 타입 =====

// 단순 시뮬레이션 요청
export type SimulationRequest = {
  symbol: string;                       // 종목 심볼
  purchaseDate: string;                 // 매수일 (YYYY-MM-DD)
  saleDate?: string;                    // 매도일 (YYYY-MM-DD, 기본값: 오늘)
  investmentAmount: number;             // 투자 금액
  findOptimalBuy?: boolean;             // 최적 매수 시점 찾기
  findOptimalSell?: boolean;            // 최적 매도 시점 찾기
};

// 단순 시뮬레이션 응답
export type SimulationResponse = {
  symbol: string;
  purchaseDate: string;                 // 매수일
  saleDate?: string;                    // 매도일
  currentValue: number;                 // 현재 가치
  investmentAmount: number;             // 투자 금액
  totalReturn: number;                  // 총 수익
  totalReturnPercent: number;           // 총 수익률
  daysHeld: number;                     // 보유 일수
  optimalBuyDate?: string;              // 최적 매수일
  optimalSellDate?: string;             // 최적 매도일
};

// DCA(정기 적립) 전략 요청
export type DcaStrategyRequest = {
  symbol: string;                       // 종목 심볼
  startDate: string;                    // 시작일
  endDate: string;                      // 종료일
  monthlyAmount: number;                // 월 투자 금액
  purchaseDay: number;                  // 매수일 (1-28)
  investmentInterval?: number;          // 투자 주기 (1, 2, 3, 6개월)
};

// 조건부 매수 전략 요청
export type ConditionalStrategyRequest = {
  symbol: string;                       // 종목 심볼
  startDate: string;                    // 시작일
  endDate: string;                      // 종료일
  totalInvestment: number;              // 총 투자 금액
  dropPercentage: number;               // 하락률 (0.05 = 5%)
  maxPurchases?: number;                // 최대 매수 횟수
};

// 종목 비교 요청
export type SymbolComparisonRequest = {
  symbols: string[];                    // 비교할 종목 목록
  startDate: string;                    // 시작일
  endDate: string;                      // 종료일
  investmentAmount: number;             // 투자 금액
  findOptimalBuy?: boolean;             // 최적 매수 시점 찾기
  findOptimalSell?: boolean;            // 최적 매도 시점 찾기
};

// 전략 파라미터
type StrategyParameter = {
  strategyType: 'SIMPLE' | 'DCA' | 'CONDITIONAL_PURCHASE';  // 전략 유형
  name?: string;                        // 전략명
  // DCA 전략 필드
  monthlyAmount?: number;               // 월 투자 금액
  purchaseDay?: number;                 // 매수일
  investmentInterval?: number;          // 투자 주기 (1, 2, 3, 6개월)
  // 조건부 매수 전략 필드
  totalInvestment?: number;             // 총 투자 금액
  dropPercentage?: number;              // 하락률
  maxPurchases?: number;                // 최대 매수 횟수
  // 단순 전략 필드
  purchaseDate?: string;                // 매수일
};

// 전략 비교 요청
export type StrategyComparisonRequest = {
  symbol: string;                       // 종목 심볼
  startDate: string;                    // 시작일
  endDate: string;                      // 종료일
  investmentAmount: number;             // 기준 투자 금액
  strategies: StrategyParameter[];      // 비교할 전략 목록
};

// 타이밍 비교 요청
export type TimingComparisonRequest = {
  symbol: string;                       // 종목 심볼
  purchaseDates: string[];              // 매수일 목록
  startDate: string;                    // 시작일
  endDate: string;                      // 종료일
  investmentAmount: number;             // 투자 금액
};

// 최적 타이밍 분석 요청
export type OptimalTimingRequest = {
  symbol: string;                       // 종목 심볼
  startDate: string;                    // 시작일
  endDate: string;                      // 종료일
  investmentAmount: number;             // 투자 금액
  targetReturnPercent: number;          // 목표 수익률
};

// 전략 응답
export type StrategyResponse = {
  symbol: string;
  strategyName: string;                 // 전략명
  totalInvestment: number;              // 총 투자 금액
  finalValue: number;                   // 최종 가치
  totalReturn: number;                  // 총 수익
  totalReturnPercent: number;           // 총 수익률
  transactions?: number;                // 거래 횟수
};

// 비교 항목
type ComparisonItem = {
  name: string;                         // 항목명
  totalInvested?: number;               // 총 투자액
  currentValueKrw?: number;             // 현재 가치 (KRW)
  totalInvestment?: number;             // 총 투자액 (레거시)
  finalValue?: number;                  // 최종 가치 (레거시)
  totalReturn?: number;                 // 총 수익
  totalReturnPercent: number;           // 총 수익률
  totalReturnKrw?: number;              // 총 수익 (KRW)
  additionalData?: any;                 // 추가 데이터 (StrategyResponse 또는 SimulationResponse)
};

// 비교 응답
export type ComparisonResponse = {
  items: ComparisonItem[];              // 비교 항목 목록
  bestPerformer: ComparisonItem;        // 최고 성과 항목
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

  // 타이밍 비교 분석
  compareTiming: (data: TimingComparisonRequest) =>
    apiClient.post<ComparisonResponse>('/analysis/compare/timing', data),

  // 최적 타이밍 분석
  analyzeOptimalTiming: (data: OptimalTimingRequest) =>
    apiClient.post<ComparisonResponse>('/analysis/timing/optimal', data),

  // 백테스트 히스토리 조회
  getHistory: (userId: string, page: number = 0, size: number = 20) =>
    apiClient.get<BacktestHistoryPage>('/backtest/history', { params: { userId, page, size } }),
};
