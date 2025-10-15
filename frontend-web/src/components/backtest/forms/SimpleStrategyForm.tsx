import React from 'react';
import StockSearchInput from '../../common/StockSearchInput';
import { NumberInput } from '../../common/NumberInput';
import { FxModeToggle } from '../shared/FxModeToggle';
import { DividendFeeOptions } from '../shared/DividendFeeOptions';

interface SimpleStrategyFormProps {
  symbol: string;
  setSymbol: (symbol: string) => void;
  purchaseDate: string;
  setPurchaseDate: (date: string) => void;
  saleDate: string;
  setSaleDate: (date: string) => void;
  initialInvestment: string;
  setInitialInvestment: (amount: string) => void;
  fxMode: 'auto' | 'manual';
  setFxMode: (mode: 'auto' | 'manual') => void;
  manualPurchaseFxRate: string;
  setManualPurchaseFxRate: (rate: string) => void;
  manualCurrentFxRate: string;
  setManualCurrentFxRate: (rate: string) => void;
  reinvestDividends: boolean;
  setReinvestDividends: (reinvest: boolean) => void;
  tradingFeeRate: string;
  setTradingFeeRate: (rate: string) => void;
  dividendTax: boolean;
  setDividendTax: (tax: boolean) => void;
  supportedSymbols: string[];
}

/**
 * 단순 백테스트 전략 입력 폼
 * 특정 날짜에 종목을 매수하여 보유한 경우의 수익률을 시뮬레이션합니다.
 */
export const SimpleStrategyForm: React.FC<SimpleStrategyFormProps> = ({
  symbol,
  setSymbol,
  purchaseDate,
  setPurchaseDate,
  saleDate,
  setSaleDate,
  initialInvestment,
  setInitialInvestment,
  fxMode,
  setFxMode,
  manualPurchaseFxRate,
  setManualPurchaseFxRate,
  manualCurrentFxRate,
  setManualCurrentFxRate,
  reinvestDividends,
  setReinvestDividends,
  tradingFeeRate,
  setTradingFeeRate,
  dividendTax,
  setDividendTax,
  supportedSymbols,
}) => {
  const today = new Date().toISOString().split('T')[0];

  return (
    <div className="space-y-4">
      {/* 기본 설정 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* 종목 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">종목</label>
          <StockSearchInput
            value={symbol}
            onChange={setSymbol}
            supportedSymbols={supportedSymbols}
            placeholder="종목 검색"
          />
        </div>

        {/* 매수일 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">매수일</label>
          <input
            type="date"
            value={purchaseDate}
            onChange={(e) => setPurchaseDate(e.target.value)}
            max={today}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>

        {/* 매도일 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            매도일 <span className="text-xs text-gray-500">(선택)</span>
          </label>
          <div className="flex space-x-2">
            <input
              type="date"
              value={saleDate}
              onChange={(e) => setSaleDate(e.target.value)}
              max={today}
              className="flex-1 px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            />
            {saleDate && (
              <button
                type="button"
                onClick={() => setSaleDate('')}
                className="px-3 py-2 bg-gray-200 text-gray-700 rounded-md hover:bg-gray-300 text-sm"
                title="현재까지로 설정"
              >
                ✕
              </button>
            )}
          </div>
          <p className="text-xs text-gray-400 mt-1">
            {saleDate ? '특정 날짜까지' : '비어있음 (현재까지)'}
          </p>
        </div>

        {/* 초기 투자금 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">초기 투자금 (₩)</label>
          <NumberInput
            value={initialInvestment}
            onChange={setInitialInvestment}
            placeholder="300,000"
            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
          <p className="text-xs text-gray-400 mt-1">최소 약 30만원 권장</p>
        </div>
      </div>

      {/* 환율 설정 */}
      <FxModeToggle
        fxMode={fxMode}
        setFxMode={setFxMode}
        manualPurchaseFxRate={manualPurchaseFxRate}
        setManualPurchaseFxRate={setManualPurchaseFxRate}
        manualCurrentFxRate={manualCurrentFxRate}
        setManualCurrentFxRate={setManualCurrentFxRate}
        purchaseLabel="매수일 환율"
        currentLabel="매도일 환율"
      />

      {/* 배당 및 수수료 옵션 */}
      <DividendFeeOptions
        tradingFeeRate={tradingFeeRate}
        setTradingFeeRate={setTradingFeeRate}
        dividendTax={dividendTax}
        setDividendTax={setDividendTax}
        reinvestDividends={reinvestDividends}
        setReinvestDividends={setReinvestDividends}
      />
    </div>
  );
};
