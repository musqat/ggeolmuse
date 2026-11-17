import type { QueryClient } from '@tanstack/react-query';

// 거래 · 포트폴리오 · 계좌 · 종목 조회 키(도메인마다 맨 앞 칸이 같아 그 칸으로 무효화)
export const queryKeys = {
  trade: {
    all: ['trade'] as const,
    capacity: (...parts: readonly unknown[]) => ['trade', 'capacity', ...parts] as const,
  },
  portfolio: {
    all: ['portfolio'] as const,
    holdings: (accountId: string | null) => ['portfolio', 'holdings', accountId] as const,
  },
  account: {
    all: ['account'] as const,
    balance: (accountId: string | number | null) => ['account', 'balance', accountId] as const,
  },
  accounts: {
    all: ['accounts'] as const,
    list: ['accounts', 'list'] as const,
  },
  stock: {
    ohlc: (symbol: string, range: unknown) => ['stock', 'ohlc', symbol, range] as const,
  },
};

// 주문 뒤 다시 받을 범위(거래 · 보유 · 잔고, 진행 중인 계좌 목록 요청은 취소하지 않고 같이 씀)
export const invalidateAfterTrade = (client: QueryClient) => {
  void client.invalidateQueries({ queryKey: queryKeys.trade.all });
  void client.invalidateQueries({ queryKey: queryKeys.portfolio.all });
  void client.invalidateQueries({ queryKey: queryKeys.account.all });
  void client.invalidateQueries({ queryKey: queryKeys.accounts.all }, { cancelRefetch: false });
};

// 정산 전 매수가 정산된 뒤 거래 · 보유 · 잔고를 한 번 더 받음
export const refreshAfterSettlement = (client: QueryClient) => {
  void client.invalidateQueries({ queryKey: queryKeys.trade.all });
  void client.invalidateQueries({ queryKey: queryKeys.portfolio.all });
  void client.invalidateQueries({ queryKey: queryKeys.account.all });
};
