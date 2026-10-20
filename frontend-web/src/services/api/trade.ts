import { apiClient } from './client';

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
  tradeId?: number;                                  // 매수 · 매도만
  accountId?: number;
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
  status?: 'PENDING' | 'COMPLETED' | 'CANCELLED';    // 매수 · 매도만. 정산 전 매수는 PENDING, 잔액 반영 실패면 CANCELLED
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
  status?: 'PENDING' | 'COMPLETED' | 'CANCELLED';    // 매수는 잔액 정산 전까지 PENDING
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
