import React from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { usePortfolioData } from '../hooks/usePortfolioData';
import { LoginRequired } from '../components/common/LoginRequired';
import { PageSpinner } from '../components/common/Spinner';
import PortfolioPieChart from '../components/charts/portfolio/PortfolioPieChart';
import { SummaryCards } from '../components/portfolio/SummaryCards';
import { AllocationList } from '../components/portfolio/AllocationList';
import { HoldingsTable } from '../components/portfolio/HoldingsTable';
import { allocationItems, totalAssetsUsd } from '../utils/portfolioAllocation';

const Portfolio: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const accountId = searchParams.get('accountId');
  const { holdings, summary, balance, exchangeRate, loading } = usePortfolioData(accountId, isAuthenticated);

  // 로그인 안 됨
  if (!isAuthenticated) {
    return <LoginRequired description="포트폴리오 기능을 이용하시려면 먼저 로그인해주세요" />;
  }

  // 계좌 ID 없음
  if (!accountId) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="flex items-center justify-center h-64">
          <div className="text-center">
            <h3 className="text-xl font-semibold text-tx-1 mb-2">계좌를 선택해주세요</h3>
            <p className="text-tx-2 mb-6">포트폴리오를 확인할 계좌를 선택해주세요</p>
            <button
              onClick={() => navigate('/account')}
              className="px-6 py-3 bg-brand text-brand-ink rounded-lg hover:bg-brand-dark transition-colors"
            >
              계좌 관리로 이동
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (loading) {
    return <PageSpinner />;
  }

  const items = allocationItems(holdings, balance, exchangeRate);
  const totalAssets = totalAssetsUsd(summary, balance, exchangeRate);

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      <div className="space-y-6">
        {/* 포트폴리오 헤더 */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between">
          <div>
            <h1 className="text-3xl font-bold text-tx-1">포트폴리오</h1>
            <p className="text-tx-2 mt-1">
              {balance?.accountName || `계좌 #${accountId}`} - 투자 현황과 수익률을 한눈에 확인하세요
            </p>
            {balance && (
              <p className="text-sm text-tx-2 mt-1">
                KRW: ₩{Number(balance.balanceKrw).toLocaleString()} |
                USD: ${Number(balance.balanceUsd).toFixed(2)}
              </p>
            )}
          </div>
        </div>

        <SummaryCards totalAssets={totalAssets} summary={summary} />

        {/* 포트폴리오 차트 & 보유 종목 */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* 포트폴리오 차트 영역 */}
          <div className="lg:col-span-2">
            <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
              <h3 className="text-lg font-semibold text-tx-1 mb-6">자산 구성</h3>

              {/* 차트 영역 */}
              <div className="bg-surface/50 rounded-lg p-4 sm:p-6 min-h-64 flex items-center justify-center">
                {items.length > 0 ? (
                  <PortfolioPieChart data={items} />
                ) : (
                  <div className="text-center">
                    <p className="text-tx-2">포트폴리오 성과 차트</p>
                    <p className="text-sm text-tx-3">아직 자산이 없습니다</p>
                  </div>
                )}
              </div>
            </div>
          </div>

          <AllocationList
            items={items}
            holdings={holdings}
            balance={balance}
            exchangeRate={exchangeRate}
            totalAssets={totalAssets}
          />
        </div>

        <HoldingsTable holdings={holdings} />
      </div>
    </div>
  );
};

export default Portfolio;
