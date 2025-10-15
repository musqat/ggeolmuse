import { useState, useEffect } from 'react';
import {
  accountsApi,
  portfolioApi,
  type AccountSummary,
  type AccountBalance,
  type PortfolioSummaryResponse
} from '../services/api';

/**
 * 계좌 데이터 페칭 훅의 반환 타입
 */
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
  const [accounts, setAccounts] = useState<AccountSummary[]>([]);
  const [accountBalances, setAccountBalances] = useState<Map<number, AccountBalance>>(new Map());
  const [portfolioSummary, setPortfolioSummary] = useState<PortfolioSummaryResponse | null>(null);
  const [currentExchangeRate, setCurrentExchangeRate] = useState<number>(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  /**
   * 계좌 정보를 로드하는 함수
   */
  const loadAccounts = async () => {
    if (!isAuthenticated) return;

    try {
      setLoading(true);
      setError(null);

      // 계좌 목록 조회
      const accountsResponse = await accountsApi.getAccounts();
      const accountList = accountsResponse.data;
      setAccounts(accountList);

      // 현재 환율 조회
      const rateResponse = await accountsApi.getCurrentExchangeRate();
      setCurrentExchangeRate(rateResponse.data);

      // 각 계좌의 잔액 조회
      const balanceMap = new Map<number, AccountBalance>();
      for (const account of accountList) {
        const balanceResponse = await accountsApi.getAccountBalance(account.accountId);
        balanceMap.set(account.accountId, balanceResponse.data);
      }
      setAccountBalances(balanceMap);

      // 포트폴리오 전체 조회 (총자산 계산용)
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
          setPortfolioSummary(summaryResponse.data);
        }
      } catch (err) {
        // 포트폴리오는 선택적이므로 에러가 나도 계속 진행
      }

    } catch (err: any) {
      setError('계좌 정보를 불러오는데 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  // 인증 상태 변경 시 계좌 정보 로드
  useEffect(() => {
    if (isAuthenticated) {
      loadAccounts();
    } else {
      setLoading(false);
    }
  }, [isAuthenticated]);

  return {
    accounts,
    accountBalances,
    portfolioSummary,
    currentExchangeRate,
    loading,
    error,
    refetch: loadAccounts
  };
};
