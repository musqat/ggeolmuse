import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { accountsApi, portfolioApi, type HoldingResponse } from '../services/api';
import { queryKeys, staleTimes } from '../services/queryKeys';
import { summarizeHoldings } from '../utils/accountSummary';

const DEFAULT_EXCHANGE_RATE = 1350;
const NO_HOLDINGS: HoldingResponse[] = [];

// 계좌의 보유 종목 · 그 보유로 낸 요약 · 잔액, 환율은 잔액 응답의 현재 환율(없으면 1350)
export const usePortfolioData = (accountId: string | null, enabled: boolean) => {
  const ready = enabled && !!accountId;

  const holdingsQuery = useQuery({
    queryKey: queryKeys.portfolio.holdings(accountId),
    queryFn: async () => (await portfolioApi.getAccountPortfolio(parseInt(accountId!))).data,
    enabled: ready,
    staleTime: staleTimes.recent,
  });
  const holdings = holdingsQuery.data ?? NO_HOLDINGS;

  // 이 계좌 보유와 서버가 붙인 현재가로 낸 요약(전체 계좌 요약은 다른 계좌가 섞임)
  const summary = useMemo(() => (holdings.length > 0 ? summarizeHoldings(holdings) : undefined), [holdings]);

  const balanceQuery = useQuery({
    queryKey: queryKeys.account.balance(accountId),
    queryFn: async () => (await accountsApi.getAccountBalance(parseInt(accountId!))).data,
    enabled: ready,
    staleTime: staleTimes.recent,
  });
  const balance = balanceQuery.data;

  return {
    holdings,
    summary,
    balance,
    exchangeRate: balance?.currentExchangeRate ? Number(balance.currentExchangeRate) : DEFAULT_EXCHANGE_RATE,
    loading: holdingsQuery.isLoading || balanceQuery.isLoading,
    // 보유나 잔액을 한 번도 못 받음(빈 보유와 구분, 다시 받기만 실패하면 받아 둔 값을 그대로 보여 줌)
    failed: (holdingsQuery.isError && !holdingsQuery.data) || (balanceQuery.isError && !balanceQuery.data),
    retry: () => {
      void holdingsQuery.refetch();
      void balanceQuery.refetch();
    },
  };
};
