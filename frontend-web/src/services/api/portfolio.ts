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
