import { useQuery } from '@tanstack/react-query';
import { accountsApi, portfolioApi, type HoldingResponse } from '../services/api';

const DEFAULT_EXCHANGE_RATE = 1400;
// 받은 지 2분이 지나면 다시 받음(포트폴리오는 자주 바뀜)
const STALE_TIME = 2 * 60 * 1000;
const NO_HOLDINGS: HoldingResponse[] = [];

// 계좌의 보유 종목 · 포트폴리오 종합 · 잔액, 환율은 잔액 응답의 현재 환율(없으면 1400)
export const usePortfolioData = (accountId: string | null, enabled: boolean) => {
  const ready = enabled && !!accountId;

  const holdingsQuery = useQuery({
    queryKey: ['portfolio', 'holdings', accountId],
    queryFn: async () => (await portfolioApi.getAccountPortfolio(parseInt(accountId!))).data,
    enabled: ready,
    staleTime: STALE_TIME,
  });
  const holdings = holdingsQuery.data ?? NO_HOLDINGS;

  // 보유 종목의 현재가로 받는 종합 정보(현재가가 하나도 없으면 null)
  const summaryQuery = useQuery({
    queryKey: ['portfolio', 'summary', holdings],
    queryFn: async () => {
      const currentPrices: { [symbol: string]: number } = {};
      holdings.forEach((holding) => {
        if (holding.currentPrice && holding.currentPrice > 0) {
          currentPrices[holding.symbol] = holding.currentPrice;
        }
      });

      if (Object.keys(currentPrices).length === 0) {
        return null;
      }

      return (await portfolioApi.getPortfolioSummary(currentPrices)).data;
    },
    enabled: ready && holdings.length > 0,
    staleTime: STALE_TIME,
  });

  const balanceQuery = useQuery({
    queryKey: ['account', 'balance', accountId],
    queryFn: async () => (await accountsApi.getAccountBalance(parseInt(accountId!))).data,
    enabled: ready,
    staleTime: STALE_TIME,
  });
  const balance = balanceQuery.data;

  return {
    holdings,
    summary: summaryQuery.data,
    balance,
    exchangeRate: balance?.currentExchangeRate ? Number(balance.currentExchangeRate) : DEFAULT_EXCHANGE_RATE,
    loading: holdingsQuery.isLoading || summaryQuery.isLoading || balanceQuery.isLoading,
  };
};
