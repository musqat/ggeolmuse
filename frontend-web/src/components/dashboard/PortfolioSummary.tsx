import React, { useState } from 'react';
import { TrendingUp, TrendingDown, ExternalLink, CreditCard, RefreshCw } from 'lucide-react';
import type { Portfolio } from '../../types/portfolio';

interface PortfolioSummaryProps {
  portfolio: Portfolio | null;
}

const PortfolioSummary: React.FC<PortfolioSummaryProps> = ({ portfolio }) => {
  const [showDepositModal, setShowDepositModal] = useState(false);
  const [showExchangeModal, setShowExchangeModal] = useState(false);

  const formatCurrency = (amount: number, currency: 'USD' | 'KRW' = 'USD'): string => {
    if (currency === 'KRW') {
      return new Intl.NumberFormat('ko-KR', {
        style: 'currency',
        currency: 'KRW',
        maximumFractionDigits: 0
      }).format(amount);
    }
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(amount);
  };

  const formatPercent = (percent: number): string => {
    const sign = percent >= 0 ? '+' : '';
    return `${sign}${percent.toFixed(2)}%`;
  };

  const getReturnColor = (value: number): string => {
    return value >= 0 ? 'text-red-600' : 'text-blue-600';
  };

  const getReturnBgColor = (value: number): string => {
    return value >= 0 ? 'bg-red-50' : 'bg-blue-50';
  };

  const handleDeposit = () => {
    setShowDepositModal(true);
  };

  const handleExchange = () => {
    setShowExchangeModal(true);
  };

  const handleViewPortfolio = () => {
    // 포트폴리오 페이지로 이동
    window.location.href = '/portfolio';
  };

  // 로딩 상태
  if (portfolio === null) {
    return (
        <div className="bg-white rounded-xl shadow-sm overflow-hidden">
          <div className="bg-gray-50 px-4 py-3 border-b border-gray-200">
            <h3 className="text-lg font-semibold text-gray-900 flex items-center gap-2">
              💼 내 포트폴리오
            </h3>
          </div>
          <div className="p-4">
            <div className="animate-pulse space-y-4">
              <div className="h-8 bg-gray-200 rounded"></div>
              <div className="h-4 bg-gray-200 rounded w-3/4"></div>
              <div className="space-y-3">
                {[...Array(3)].map((_, i) => (
                    <div key={i} className="h-12 bg-gray-200 rounded"></div>
                ))}
              </div>
            </div>
          </div>
        </div>
    );
  }

  return (
      <div className="bg-white rounded-xl shadow-sm overflow-hidden">
        {/* 헤더 */}
        <div className="bg-gray-50 px-4 py-3 border-b border-gray-200">
          <div className="flex justify-between items-center">
            <h3 className="text-lg font-semibold text-gray-900 flex items-center gap-2">
              💼 내 포트폴리오
            </h3>
            <button
                onClick={handleViewPortfolio}
                className="text-sm text-gray-600 hover:text-gray-900 flex items-center gap-1 px-2 py-1 rounded-md hover:bg-gray-100 transition-colors"
            >
              전체보기
              <ExternalLink className="w-3 h-3" />
            </button>
          </div>
        </div>

        {/* 포트폴리오 총 가치 */}
        <div className="bg-gradient-to-r from-green-50 to-emerald-50 px-4 py-4 border-b border-gray-200">
          <div className="text-center">
            <div className="text-2xl font-bold text-emerald-700 mb-1">
              {formatCurrency(portfolio.totalValue)}
            </div>
            <div className={`flex items-center justify-center gap-1 text-sm font-medium ${getReturnColor(portfolio.totalReturnPercent)}`}>
              {portfolio.totalReturnPercent >= 0 ? (
                  <TrendingUp className="w-4 h-4" />
              ) : (
                  <TrendingDown className="w-4 h-4" />
              )}
              {formatCurrency(portfolio.totalReturn)} ({formatPercent(portfolio.totalReturnPercent)}) 오늘
            </div>
          </div>
        </div>

        {/* 보유 종목 */}
        <div className="max-h-80 overflow-y-auto">
          {portfolio.holdings.length > 0 ? (
              <div className="divide-y divide-gray-200">
                {portfolio.holdings.map((holding) => (
                    <div key={holding.symbol} className="p-4 hover:bg-gray-50 transition-colors">
                      <div className="flex justify-between items-center">
                        <div className="flex flex-col">
                          <div className="font-semibold text-gray-900 text-sm">
                            {holding.symbol}
                          </div>
                          <div className="text-xs text-gray-500 mt-1">
                            {holding.quantity.toFixed(2)}주
                          </div>
                        </div>
                        <div className="text-right">
                          <div className="font-semibold text-gray-900 text-sm">
                            {formatCurrency(holding.currentValue)}
                          </div>
                          <div className={`text-xs mt-1 font-medium ${getReturnColor(holding.totalReturnPercent)}`}>
                            {formatCurrency(holding.totalReturn)} ({formatPercent(holding.totalReturnPercent)})
                          </div>
                        </div>
                      </div>
                    </div>
                ))}
              </div>
          ) : (
              <div className="p-8 text-center text-gray-500">
                <div className="text-4xl mb-2">📊</div>
                <div className="font-medium">보유 종목이 없습니다</div>
                <div className="text-sm mt-1">첫 투자를 시작해보세요!</div>
              </div>
          )}
        </div>

        {/* 계좌 관리 섹션 */}
        <div className="border-t border-gray-200 bg-gray-50 p-4">
          <div className="mb-3">
            <div className="flex justify-between items-center text-sm mb-2">
              <span className="text-gray-600">🏦 투자계좌1</span>
              <span className="font-semibold text-emerald-600">활성</span>
            </div>
            <div className="grid grid-cols-2 gap-3 text-xs">
              <div className="flex justify-between">
                <span className="text-gray-600">KRW 잔고</span>
                <span className="font-medium">{formatCurrency(5000000, 'KRW')}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-600">USD 잔고</span>
                <span className="font-medium">{formatCurrency(7450)}</span>
              </div>
            </div>
          </div>

          {/* 액션 버튼들 */}
          <div className="flex gap-2">
            <button
                onClick={handleDeposit}
                className="flex-1 bg-indigo-600 text-white text-xs py-2 px-3 rounded-md hover:bg-indigo-700 transition-colors flex items-center justify-center gap-1"
            >
              <CreditCard className="w-3 h-3" />
              입금
            </button>
            <button
                onClick={handleExchange}
                className="flex-1 bg-gray-600 text-white text-xs py-2 px-3 rounded-md hover:bg-gray-700 transition-colors flex items-center justify-center gap-1"
            >
              <RefreshCw className="w-3 h-3" />
              환전
            </button>
          </div>
        </div>

        {/* 입금 모달 (간단한 버전) */}
        {showDepositModal && (
            <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
              <div className="bg-white rounded-lg p-6 max-w-md w-full">
                <h3 className="text-lg font-semibold mb-4">💳 KRW 입금</h3>
                <div className="space-y-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                      입금 금액 (원)
                    </label>
                    <input
                        type="number"
                        placeholder="1,000,000"
                        className="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                    />
                  </div>
                  <div className="text-xs text-gray-500">
                    • 수수료: 무료<br/>
                    • 처리 시간: 즉시 반영
                  </div>
                  <div className="flex gap-3">
                    <button
                        onClick={() => setShowDepositModal(false)}
                        className="flex-1 bg-gray-200 text-gray-800 py-2 px-4 rounded-md hover:bg-gray-300 transition-colors"
                    >
                      취소
                    </button>
                    <button
                        onClick={() => {
                          alert('입금이 완료되었습니다!');
                          setShowDepositModal(false);
                        }}
                        className="flex-1 bg-indigo-600 text-white py-2 px-4 rounded-md hover:bg-indigo-700 transition-colors"
                    >
                      입금하기
                    </button>
                  </div>
                </div>
              </div>
            </div>
        )}

        {/* 환전 모달 */}
        {showExchangeModal && (
            <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
              <div className="bg-white rounded-lg p-6 max-w-md w-full">
                <h3 className="text-lg font-semibold mb-4">💱 환전</h3>
                <div className="space-y-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                      환전 금액 (KRW)
                    </label>
                    <input
                        type="number"
                        placeholder="500,000"
                        className="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                    />
                  </div>
                  <div className="bg-gray-50 p-3 rounded-md text-sm">
                    <div className="flex justify-between mb-1">
                      <span>실시간 환율 (USD/KRW)</span>
                      <span className="font-semibold">₩1,334.50</span>
                    </div>
                    <div className="flex justify-between text-xs text-gray-600">
                      <span>환전 수수료</span>
                      <span>0.5%</span>
                    </div>
                  </div>
                  <div className="flex gap-3">
                    <button
                        onClick={() => setShowExchangeModal(false)}
                        className="flex-1 bg-gray-200 text-gray-800 py-2 px-4 rounded-md hover:bg-gray-300 transition-colors"
                    >
                      취소
                    </button>
                    <button
                        onClick={() => {
                          alert('환전이 완료되었습니다!');
                          setShowExchangeModal(false);
                        }}
                        className="flex-1 bg-indigo-600 text-white py-2 px-4 rounded-md hover:bg-indigo-700 transition-colors"
                    >
                      환전하기
                    </button>
                  </div>
                </div>
              </div>
            </div>
        )}
      </div>
  );
};

export default PortfolioSummary;