import { useQuery } from '@tanstack/react-query';
import {
  accountsApi,
  portfolioApi,
  type AccountSummary,
  type AccountBalance,
  type PortfolioSummaryResponse
} from '../services/api';

export interface UseAccountDataReturn {
  accounts: AccountSummary[];
  accountBalances: Map<number, AccountBalance>;
  portfolioSummary: PortfolioSummaryResponse | null;
  currentExchangeRate: number;
  loading: boolean;
  error: string | null;
  refetch: () => Promise<void>;
}

/**
 * 계좌 데이터 조회 훅
 *
 * @description 계좌 목록, 잔액, 포트폴리오 요약, 환율 정보를 조회합니다
 * @param {boolean} isAuthenticated - 인증 상태
 * @returns {UseAccountDataReturn} 계좌 데이터 및 로딩/에러 상태
 *
 */
export const useAccountData = (isAuthenticated: boolean): UseAccountDataReturn => {
  // React Query: 모든 계좌 데이터를 한 번에 조회 (순차 API 호출)
  const {
    data,
    isLoading: loading,
    error: queryError,
    refetch
  } = useQuery({
    queryKey: ['account', 'data'],
    queryFn: async () => {
      // 1. 계좌 목록 조회
      const accountsResponse = await accountsApi.getAccounts();
      const accountList = accountsResponse.data;

      // 2. 현재 환율 조회
      const rateResponse = await accountsApi.getCurrentExchangeRate();
      const exchangeRate = rateResponse.data;

      // 3. 각 계좌의 잔액 조회 (병렬 처리)
      const balancePromises = accountList.map(account =>
        accountsApi.getAccountBalance(account.accountId)
          .then(response => ({ accountId: account.accountId, balance: response.data }))
      );
      const balanceResults = await Promise.all(balancePromises);
      const balanceMap = new Map<number, AccountBalance>();
      balanceResults.forEach(result => {
        balanceMap.set(result.accountId, result.balance);
      });

      // 4. 포트폴리오 전체 조회 (총자산 계산용, 선택적)
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
    staleTime: 1 * 60 * 1000, // 1분 (계좌 잔액은 자주 변경될 수 있음)
  });

  return {
    accounts: data?.accounts || [],
    accountBalances: data?.accountBalances || new Map(),
    portfolioSummary: data?.portfolioSummary || null,
    currentExchangeRate: data?.currentExchangeRate || 0,
    loading,
    error: queryError ? '계좌 정보를 불러오는데 실패했습니다.' : null,
    refetch: async () => { await refetch(); }
  };
};
