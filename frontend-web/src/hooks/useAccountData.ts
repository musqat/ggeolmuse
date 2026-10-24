import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  accountsApi,
  portfolioApi,
  type AccountSummary,
  type AccountBalance,
  type PortfolioSummaryResponse
} from '../services/api';
import { accountListQuery } from './useAccounts';

interface UseAccountDataReturn {
  accounts: AccountSummary[];
  accountBalances: Map<number, AccountBalance>;
  portfolioSummary: PortfolioSummaryResponse | null;
  currentExchangeRate: number;
  // 계좌마다 원화 + 달러 × 환율(원화)
  cashKrw: number;
  // 현금 + 주식 평가액(원화)
  totalAssets: number;
  loading: boolean;
  error: string | null;
  refetch: () => Promise<void>;
}

// 계좌마다 원화 + 달러 × 환율(환율이 없으면 0)
const calculateCash = (accountBalances: Map<number, AccountBalance>, exchangeRate: number): number => {
  if (!exchangeRate) return 0;

  let cash = 0;
  accountBalances.forEach((balance) => {
    cash += balance.balanceKrw + balance.balanceUsd * exchangeRate;
  });
  return cash;
};

// 현금 + 주식 평가액(달러) × 환율(환율이 없으면 0)
const calculateTotalAssets = (
  accountBalances: Map<number, AccountBalance>,
  portfolioSummary: PortfolioSummaryResponse | null,
  exchangeRate: number
): number => {
  if (!exchangeRate) return 0;

  const stocks = portfolioSummary?.totalCurrentValue ? portfolioSummary.totalCurrentValue * exchangeRate : 0;
  return calculateCash(accountBalances, exchangeRate) + stocks;
};

// 로그인했을 때 계좌 목록 · 잔액(현재 환율 포함) · 포트폴리오 요약을 불러옴
export const useAccountData = (isAuthenticated: boolean): UseAccountDataReturn => {
  const queryClient = useQueryClient();

  // 계좌 목록 · 잔액 · 포트폴리오를 한 번에 조회
  const {
    data,
    isLoading: loading,
    error: queryError,
    refetch
  } = useQuery({
    queryKey: ['account', 'data'],
    queryFn: async () => {
      // 계좌 목록(진행 중인 같은 요청이 있으면 같이 씀)
      const accountList = await queryClient.fetchQuery({ ...accountListQuery, staleTime: 0 });

      // 계좌마다 잔액(병렬)
      const balancePromises = accountList.map(account =>
        accountsApi.getAccountBalance(account.accountId)
          .then(response => ({ accountId: account.accountId, balance: response.data }))
      );
      const balanceResults = await Promise.all(balancePromises);
      const balanceMap = new Map<number, AccountBalance>();
      balanceResults.forEach(result => {
        balanceMap.set(result.accountId, result.balance);
      });

      // 원화 환산 환율은 잔액 응답의 현재 환율
      const exchangeRate = balanceResults[0]?.balance.currentExchangeRate ?? 0;

      // 포트폴리오 전체(총자산 계산용, 실패해도 계속)
      let portfolioSummary: PortfolioSummaryResponse | null = null;
      try {
        const portfolioResponse = await portfolioApi.getPortfolio();
        const holdings = portfolioResponse.data;

        // holdings가 있으면 포트폴리오 종합 정보도 가져오기
        if (holdings && holdings.length > 0) {
          // holdings에서 currentPrice 추출
          const currentPrices: { [symbol: string]: number } = {};
          holdings.forEach(holding => {
            if (holding.currentPrice && holding.currentPrice > 0) {
              currentPrices[holding.symbol] = holding.currentPrice;
            }
          });

          // 포트폴리오 종합 정보 조회 (실제 현재가 기반)
          const summaryResponse = await portfolioApi.getPortfolioSummary(currentPrices);
          portfolioSummary = summaryResponse.data;
        }
      } catch (err) {
        // 포트폴리오는 선택적이므로 에러가 나도 계속 진행
        console.warn('포트폴리오 조회 실패:', err);
      }

      return {
        accounts: accountList,
        accountBalances: balanceMap,
        portfolioSummary,
        currentExchangeRate: exchangeRate
      };
    },
    enabled: isAuthenticated,
    staleTime: 1 * 60 * 1000,
  });

  const accountBalances = data?.accountBalances || new Map<number, AccountBalance>();
  const portfolioSummary = data?.portfolioSummary || null;
  const currentExchangeRate = data?.currentExchangeRate || 0;

  return {
    accounts: data?.accounts || [],
    accountBalances,
    portfolioSummary,
    currentExchangeRate,
    cashKrw: calculateCash(accountBalances, currentExchangeRate),
    totalAssets: calculateTotalAssets(accountBalances, portfolioSummary, currentExchangeRate),
    loading,
    error: queryError ? '계좌 정보를 불러오는데 실패했습니다.' : null,
    refetch: async () => { await refetch(); }
  };
};
