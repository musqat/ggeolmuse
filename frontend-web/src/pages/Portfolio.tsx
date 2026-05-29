import React, { useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../contexts/AuthContext';
import {
  TrendingUp,
  TrendingDown,
  DollarSign,
  BarChart3,
  PieChart,
  Activity,
  Calendar,
  ArrowUpCircle,
  ArrowDownCircle,
  Wallet,
  LogIn,
  Lock
} from 'lucide-react';
import { portfolioApi, stockApi, accountsApi, type HoldingResponse, type PortfolioSummaryResponse, type BalanceResponse } from '../services/api';
import PortfolioPieChart from '../components/charts/portfolio/PortfolioPieChart';
import LoginModal from '../components/auth/LoginModal';

const Portfolio: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { isAuthenticated, login } = useAuth();
  const accountId = searchParams.get('accountId');

  const [exchangeRate, setExchangeRate] = useState<number>(1400); // 기본 환율
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);

  // React Query: 계좌별 포트폴리오 조회
  const {
    data: holdings = [],
    isLoading: isLoadingHoldings,
    error: holdingsError
  } = useQuery({
    queryKey: ['portfolio', 'holdings', accountId],
    queryFn: async () => {
      const response = await portfolioApi.getAccountPortfolio(parseInt(accountId!));
      return response.data;
    },
    enabled: isAuthenticated && !!accountId,
    staleTime: 2 * 60 * 1000, // 2분 (포트폴리오는 자주 변경됨)
  });

  // React Query: 포트폴리오 종합 정보 (holdings에서 현재가 추출 후 조회)
  const {
    data: portfolioSummary,
    isLoading: isLoadingSummary
  } = useQuery({
    queryKey: ['portfolio', 'summary', holdings],
    queryFn: async () => {
      // holdings에서 currentPrice 추출
      const currentPrices: { [symbol: string]: number } = {};
      holdings.forEach(holding => {
        if (holding.currentPrice && holding.currentPrice > 0) {
          currentPrices[holding.symbol] = holding.currentPrice;
        }
      });

      if (Object.keys(currentPrices).length === 0) {
        return null;
      }

      const response = await portfolioApi.getPortfolioSummary(currentPrices);
      return response.data;
    },
    enabled: isAuthenticated && !!accountId && holdings.length > 0,
    staleTime: 2 * 60 * 1000,
  });

  // React Query: 계좌 잔액 정보 조회
  const {
    data: balanceInfo,
    isLoading: isLoadingBalance
  } = useQuery({
    queryKey: ['account', 'balance', accountId],
    queryFn: async () => {
      const response = await accountsApi.getAccountBalance(parseInt(accountId!));
      const data = response.data;

      // 환율 정보 업데이트
      if (data.currentExchangeRate) {
        setExchangeRate(Number(data.currentExchangeRate));
      }

      return data;
    },
    enabled: isAuthenticated && !!accountId,
    staleTime: 2 * 60 * 1000,
  });

  // 통합 로딩 상태
  const loading = isLoadingHoldings || isLoadingSummary || isLoadingBalance;

  // 로그인 안 됨
  if (!isAuthenticated) {
    return (
      <>
        <div className="max-w-7xl mx-auto px-4 py-6">
          <div className="min-h-[60vh] flex items-center justify-center">
            <div className="text-center">
              <Lock className="w-16 h-16 text-brand mx-auto mb-4" />
              <h1 className="text-3xl font-bold text-tx-1 mb-4">로그인이 필요한 서비스입니다</h1>
              <p className="text-lg text-tx-2 mb-6">
                포트폴리오 기능을 이용하시려면 먼저 로그인해주세요
              </p>
              <button
                onClick={() => setIsLoginModalOpen(true)}
                className="flex items-center space-x-2 bg-brand text-white px-6 py-3 rounded-lg hover:bg-brand-dark transition-colors mx-auto"
              >
                <LogIn className="w-5 h-5" />
                <span>로그인하기</span>
              </button>
            </div>
          </div>
        </div>
        <LoginModal
          isOpen={isLoginModalOpen}
          onClose={() => setIsLoginModalOpen(false)}
          onSwitchToSignup={() => {
            setIsLoginModalOpen(false);
            // 회원가입은 Header에서 관리되므로 단순히 모달만 닫음
          }}
          onLogin={async (email: string, password: string) => {
            await login(email, password);
          }}
        />
      </>
    );
  }

  // 계좌 ID 없음
  if (!accountId) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="flex items-center justify-center h-64">
          <div className="text-center">
            <Wallet className="w-16 h-16 text-tx-3 mx-auto mb-4" />
            <h3 className="text-xl font-semibold text-tx-1 mb-2">계좌를 선택해주세요</h3>
            <p className="text-tx-2 mb-6">포트폴리오를 확인할 계좌를 선택해주세요</p>
            <button
              onClick={() => navigate('/account')}
              className="px-6 py-3 bg-brand text-white rounded-lg hover:bg-brand-dark transition-colors"
            >
              계좌 관리로 이동
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-brand"></div>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      <div className="space-y-6">
        {/* 포트폴리오 헤더 */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between">
          <div>
            <h1 className="text-3xl font-bold text-tx-1">포트폴리오</h1>
            <p className="text-tx-2 mt-1">
              {balanceInfo?.accountName || `계좌 #${accountId}`} - 투자 현황과 수익률을 한눈에 확인하세요
            </p>
            {balanceInfo && (
              <p className="text-sm text-tx-2 mt-1">
                KRW: ₩{Number(balanceInfo.balanceKrw)?.toLocaleString() ?? 0} |
                USD: ${Number(balanceInfo.balanceUsd)?.toFixed(2) ?? 0}
              </p>
            )}
          </div>
        </div>

        {/* 포트폴리오 요약 카드 */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-tx-2">총 자산</p>
                <p className="text-2xl font-bold text-tx-1">
                  ${(() => {
                    const stockValue = portfolioSummary?.totalCurrentValue || 0;
                    const usdCash = Number(balanceInfo?.balanceUsd) || 0;
                    const krwCash = (Number(balanceInfo?.balanceKrw) || 0) / exchangeRate;
                    return (stockValue + usdCash + krwCash).toFixed(2);
                  })()}
                </p>
              </div>
              <div className="bg-blue-100 p-3 rounded-lg">
                <DollarSign className="w-6 h-6 text-blue-600" />
              </div>
            </div>
          </div>

          <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-tx-2">평가손익</p>
                <p className={`text-2xl font-bold ${(portfolioSummary?.totalUnrealizedPnL ?? 0) >= 0 ? 'text-green-600':'text-red-600'}`}>
                  {(portfolioSummary?.totalUnrealizedPnL ?? 0) >= 0 ? '+' : ''}${portfolioSummary?.totalUnrealizedPnL?.toFixed(2) ?? '0.00'}
                </p>
              </div>
              <div className={`p-3 rounded-lg ${(portfolioSummary?.totalUnrealizedPnL ?? 0) >= 0 ? 'bg-green-500/100/15':'bg-red-500/100/15'}`}>
                {(portfolioSummary?.totalUnrealizedPnL ?? 0) >= 0 ? (
                  <TrendingUp className="w-6 h-6 text-green-600" />
                ) : (
                  <TrendingDown className="w-6 h-6 text-red-600" />
                )}
              </div>
            </div>
          </div>

          <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-tx-2">수익률</p>
                <p className={`text-2xl font-bold ${(portfolioSummary?.totalReturnRate ?? 0) >= 0 ? 'text-green-600':'text-red-600'}`}>
                  {(portfolioSummary?.totalReturnRate ?? 0) >= 0 ? '+' : ''}{portfolioSummary?.totalReturnRate?.toFixed(2) ?? '0.00'}%
                </p>
              </div>
              <div className={`p-3 rounded-lg ${(portfolioSummary?.totalReturnRate ?? 0) >= 0 ? 'bg-green-500/100/15':'bg-red-500/100/15'}`}>
                {(portfolioSummary?.totalReturnRate ?? 0) >= 0 ? (
                  <ArrowUpCircle className="w-6 h-6 text-green-600" />
                ) : (
                  <ArrowDownCircle className="w-6 h-6 text-red-600" />
                )}
              </div>
            </div>
          </div>
        </div>

        {/* 포트폴리오 차트 & 보유 종목 */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* 포트폴리오 차트 영역 */}
          <div className="lg:col-span-2">
            <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
              <div className="flex items-center justify-between mb-6">
                <h3 className="text-lg font-semibold text-tx-1">자산 구성</h3>
                <div className="flex items-center space-x-2">
                  <PieChart className="w-5 h-5 text-tx-3" />
                </div>
              </div>

              {/* 차트 영역 */}
              <div className="bg-surface/50 rounded-lg p-6 h-64 flex items-center justify-center">
                {(holdings.length > 0 || (balanceInfo && (Number(balanceInfo.balanceKrw) > 0 || Number(balanceInfo.balanceUsd) > 0))) ? (
                  <PortfolioPieChart
                    data={(() => {
                      const chartData = [];
                      const colors = ['#6366f1', '#3b82f6', '#10b981', '#eab308', '#ef4444', '#a855f7', '#f59e0b', '#06b6d4'];
                      let colorIndex = 0;

                      // 주식 추가 (quantity와 currentPrice 포함)
                      holdings.forEach((holding) => {
                        chartData.push({
                          symbol: holding.symbol,
                          value: holding.currentValue || holding.totalInvestedAmount,
                          color: colors[colorIndex % colors.length],
                          quantity: holding.totalQuantity,
                          currentPrice: holding.currentPrice
                        });
                        colorIndex++;
                      });

                      // USD 현금 추가 (0보다 크면)
                      if (balanceInfo && Number(balanceInfo.balanceUsd) > 0) {
                        chartData.push({
                          symbol: 'USD 현금',
                          value: Number(balanceInfo.balanceUsd),
                          color: colors[colorIndex % colors.length]
                        });
                        colorIndex++;
                      }

                      // KRW 현금 추가 (0보다 크면, USD로 환산)
                      if (balanceInfo && Number(balanceInfo.balanceKrw) > 0) {
                        chartData.push({
                          symbol: 'KRW 현금',
                          value: Number(balanceInfo.balanceKrw) / exchangeRate,
                          color: colors[colorIndex % colors.length]
                        });
                      }

                      return chartData;
                    })()}
                  />
                ) : (
                  <div className="text-center">
                    <PieChart className="w-12 h-12 text-tx-3 mx-auto mb-2" />
                    <p className="text-tx-2">포트폴리오 성과 차트</p>
                    <p className="text-sm text-tx-3">아직 자산이 없습니다</p>
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* 자산 배분 */}
          <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
            <h3 className="text-lg font-semibold text-tx-1 mb-4">자산 배분</h3>
            {(holdings.length > 0 || (balanceInfo && (Number(balanceInfo.balanceUsd) > 0 || Number(balanceInfo.balanceKrw) > 0))) && portfolioSummary ? (
              <div className="space-y-4">
                {(() => {
                  // 전체 자산 계산 (주식 + USD 현금 + KRW 현금을 USD로 환산)
                  const totalAssets =
                    (portfolioSummary.totalCurrentValue || 0) +
                    (Number(balanceInfo?.balanceUsd) || 0) +
                    ((Number(balanceInfo?.balanceKrw) || 0) / exchangeRate);

                  return (
                    <>
                      {/* 주식 자산 */}
                      {holdings.map((holding, index) => {
                        const allocation = totalAssets > 0 && holding.currentValue
                          ? ((holding.currentValue / totalAssets) * 100).toFixed(1)
                          : '0.0';
                        const colors = ['bg-brand-bg0', 'bg-blue-500', 'bg-green-500/100', 'bg-yellow-500/100', 'bg-red-500/100', 'bg-purple-500/100'];
                        const colorClass = colors[index % colors.length];

                        return (
                          <div key={holding.holdingId} className="flex items-center justify-between">
                            <div className="flex items-center space-x-3">
                              <div className={`w-3 h-3 ${colorClass} rounded-full`}></div>
                              <div>
                                <p className="font-medium text-tx-1 text-sm">{holding.symbol}</p>
                                <p className="text-xs text-tx-2">{holding.totalQuantity}주</p>
                              </div>
                            </div>
                            <div className="text-right">
                              <p className="font-medium text-tx-1 text-sm">{allocation}%</p>
                              <p className="text-xs text-tx-2">
                                ${holding.currentValue ? holding.currentValue.toFixed(2) : holding.totalInvestedAmount.toFixed(2)}
                              </p>
                            </div>
                          </div>
                        );
                      })}

                      {/* USD 현금 */}
                      {balanceInfo && Number(balanceInfo.balanceUsd) > 0 && (
                        <div className="flex items-center justify-between">
                          <div className="flex items-center space-x-3">
                            <div className="w-3 h-3 bg-surface/500 rounded-full"></div>
                            <div>
                              <p className="font-medium text-tx-1 text-sm">USD 현금</p>
                              <p className="text-xs text-tx-2">달러 잔액</p>
                            </div>
                          </div>
                          <div className="text-right">
                            <p className="font-medium text-tx-1 text-sm">
                              {totalAssets > 0
                                ? ((Number(balanceInfo.balanceUsd) / totalAssets) * 100).toFixed(1)
                                : '0.0'}%
                            </p>
                            <p className="text-xs text-tx-2">
                              ${Number(balanceInfo.balanceUsd).toFixed(2)}
                            </p>
                          </div>
                        </div>
                      )}

                      {/* KRW 현금 */}
                      {balanceInfo && Number(balanceInfo.balanceKrw) > 0 && (
                        <div className="flex items-center justify-between">
                          <div className="flex items-center space-x-3">
                            <div className="w-3 h-3 bg-gray-400 rounded-full"></div>
                            <div>
                              <p className="font-medium text-tx-1 text-sm">KRW 현금</p>
                              <p className="text-xs text-tx-2">원화 잔액</p>
                            </div>
                          </div>
                          <div className="text-right">
                            <p className="font-medium text-tx-1 text-sm">
                              {totalAssets > 0
                                ? ((Number(balanceInfo.balanceKrw) / exchangeRate / totalAssets) * 100).toFixed(1)
                                : '0.0'}%
                            </p>
                            <p className="text-xs text-tx-2">
                              ₩{Number(balanceInfo.balanceKrw).toLocaleString()}
                            </p>
                          </div>
                        </div>
                      )}
                    </>
                  );
                })()}
              </div>
            ) : (
              <div className="text-center py-8 text-tx-2">
                <PieChart className="w-12 h-12 mx-auto mb-2 opacity-50" />
                <p>보유 자산이 없습니다</p>
              </div>
            )}
          </div>
        </div>

        {/* 보유 종목 테이블 */}
        <div className="bg-surface rounded-xl shadow-sm border border-line/50 overflow-hidden">
          <div className="px-6 py-4 border-b border-line">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-semibold text-tx-1">보유 종목</h3>
              <p className="text-xs text-tx-2">* 수수료가 포함되지 않은 수익률입니다</p>
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-surface/50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-tx-2 uppercase tracking-wider">
                    종목
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-tx-2 uppercase tracking-wider">
                    보유량
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-tx-2 uppercase tracking-wider">
                    평균단가
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-tx-2 uppercase tracking-wider">
                    현재가
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-tx-2 uppercase tracking-wider">
                    평가금액
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-tx-2 uppercase tracking-wider">
                    손익
                  </th>
                </tr>
              </thead>
              <tbody className="bg-surface divide-y divide-line">
                {holdings.length > 0 ? (
                  holdings.map((holding) => (
                    <tr key={holding.holdingId} className="hover:bg-surface/50">
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div>
                          <div className="text-sm font-medium text-tx-1">{holding.symbol}</div>
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="text-sm text-tx-1">{holding.totalQuantity}주</div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="text-sm text-tx-1">${holding.avgPurchasePrice.toFixed(2)}</div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="text-sm text-tx-1">
                          {holding.currentPrice ? `$${holding.currentPrice.toFixed(2)}` : '-'}
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="text-sm text-tx-1">
                          {holding.currentValue ? `$${holding.currentValue.toFixed(2)}` : '-'}
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        {holding.unrealizedPnL !== null && holding.unrealizedPnL !== undefined &&
                         holding.returnRate !== null && holding.returnRate !== undefined ? (
                          <div className={`text-sm font-medium flex items-center ${
                            holding.unrealizedPnL >= 0 ? 'text-green-600' : 'text-red-600'
                          }`}>
                            {holding.unrealizedPnL >= 0 ? (
                              <ArrowUpCircle className="w-4 h-4 mr-1" />
                            ) : (
                              <ArrowDownCircle className="w-4 h-4 mr-1" />
                            )}
                            {holding.unrealizedPnL >= 0 ? '+' : ''}${holding.unrealizedPnL.toFixed(2)}
                            <span className="ml-1">({holding.returnRate >= 0 ? '+' : ''}{holding.returnRate.toFixed(2)}%)</span>
                          </div>
                        ) : (
                          <div className="text-sm text-tx-3">-</div>
                        )}
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={6} className="px-6 py-8 text-center text-tx-2">
                      <BarChart3 className="w-12 h-12 mx-auto mb-2 opacity-50" />
                      <p>보유 종목이 없습니다</p>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Portfolio;
