import { apiClient } from './client';

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
  slippageRate?: number;                // 슬리피지율(0 ~ 0.01 = 0% ~ 1%, 비우면 0.1%)
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

  // 계좌 삭제
  deleteAccount: (accountId: number) =>
    apiClient.delete<void>(`/accounts/${accountId}`),
};
