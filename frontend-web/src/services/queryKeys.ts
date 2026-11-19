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
    search: (term: string, limit: number) => ['stock', 'search', term, limit] as const,
  },
};

// 조회 종류별 신선 시간(ms), 지나면 화면에 다시 들어올 때 다시 받음
export const staleTimes = {
  // 주문 가능 금액
  capacity: 30 * 1000,
  // 환율 · 계좌 잔고
  balance: 60 * 1000,
  // 시세 목록 · 거래 내역 · 보유
  recent: 2 * 60 * 1000,
  // 일봉 차트(조회 기본값)
  chart: 5 * 60 * 1000,
  // 지원 종목 · 환전 가능 범위 · 종목 검색
  reference: 10 * 60 * 1000,
  // 종목 비교
  comparison: 30 * 60 * 1000,
} as const;

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
