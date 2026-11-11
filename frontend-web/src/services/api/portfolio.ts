import { apiClient } from './client';

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
};

// 수익 흐름 기간(1M 은 30일 · 3M 은 90일 · 1Y 는 365일 전부터, ALL 은 첫 거래일부터)
export type ProfitFlowPeriod = '1M' | '3M' | '1Y' | 'ALL';

// 거래일 하나의 원화 수익(평가손익 + 실현 손익 + 받은 배당 − 수수료)과 그 날짜에 들고 있던 종목 매수 금액(투자 원금)
export type ProfitPoint = { date: string; profitKrw: number; investedKrw: number };

// 기간 안 수익 흐름과 최고 · 최저 · 현재 수익(점이 없으면 best · worst 는 null)
export type ProfitFlowResponse = {
  points: ProfitPoint[];
  best: ProfitPoint | null;
  worst: ProfitPoint | null;
  currentProfitKrw: number;
};

// 달 하나(YYYY-MM)의 원화 수익 변화(그 달 마지막 점 − 앞 달 마지막 점)
export type MonthlyProfit = { month: string; profitKrw: number };

// 매도 하나의 실현 손익(판 날 환율이 없으면 fxRate null · 원화 0)
export type ProfitSale = {
  tradeId: number;
  accountId: number;
  date: string;
  symbol: string;
  quantity: number;
  priceUsd: number;
  realizedUsd: number;
  fxRate: number | null;
  realizedKrw: number;
  returnPercent: number;
};

// 기간 끝에 들고 있는 종목 하나의 평가 손익(종가를 못 받으면 closeUsd · closeDate null)
export type ProfitHolding = {
  symbol: string;
  quantity: number;
  averagePriceUsd: number;
  closeUsd: number | null;
  closeDate: string | null;
  unrealizedUsd: number;
  unrealizedKrw: number;
  returnPercent: number;
};

// 수익 탭 자세히(달마다 수익 · 판 거래 · 들고 있는 종목, 맨 위 원화는 줄 값의 합)
export type ProfitDetailResponse = {
  monthly: MonthlyProfit[];
  realizedKrw: number;
  unrealizedKrw: number;
  sales: ProfitSale[];
  holdings: ProfitHolding[];
};

// 종목 하나의 기간 기여(칸마다 기간 끝 값 − 기간 시작 전 값, 원화, 수수료는 뺀 금액을 양수로)
export type SymbolContribution = {
  symbol: string;
  realizedKrw: number;
  unrealizedKrw: number;
  dividendKrw: number;
  feeKrw: number;
  totalKrw: number;
  sharePercent: number;
  held: boolean;
};

// 종목별 기여(합 큰 것부터, totalKrw 는 종목 합)
export type SymbolContributionResponse = {
  totalKrw: number;
  symbols: SymbolContribution[];
};

export const portfolioApi = {
  // 전체 포트폴리오 조회
  getPortfolio: () =>
    apiClient.get<HoldingResponse[]>('/portfolio'),

  // 계좌별 포트폴리오 조회
  getAccountPortfolio: (accountId: number) =>
    apiClient.get<HoldingResponse[]>(`/portfolio/account/${accountId}`),

  // 포트폴리오 종합 정보 조회 (현재가 필요)
  getPortfolioSummary: () =>
    apiClient.get<PortfolioSummaryResponse>('/portfolio/summary'),

  // 계좌 하나 또는 전체 계좌의 기간 수익 흐름
  getProfitFlow: (accountId: number | 'all', period: ProfitFlowPeriod) =>
    apiClient.get<ProfitFlowResponse>('/portfolio/stats/flow', { params: { accountId, period } }),

  // 계좌 하나 또는 전체 계좌의 기간 수익 탭 자세히
  getProfitDetail: (accountId: number | 'all', period: ProfitFlowPeriod) =>
    apiClient.get<ProfitDetailResponse>('/portfolio/stats/profit', { params: { accountId, period } }),

  // 계좌 하나 또는 전체 계좌의 기간 종목별 기여
  getSymbolContributions: (accountId: number | 'all', period: ProfitFlowPeriod) =>
    apiClient.get<SymbolContributionResponse>('/portfolio/stats/symbols', { params: { accountId, period } }),
};
