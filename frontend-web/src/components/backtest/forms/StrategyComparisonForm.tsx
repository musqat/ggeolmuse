import React from 'react';
import StockSearchInput from '../../common/StockSearchInput';
import { NumberInput } from '../../common/NumberInput';
import { FxModeToggle } from '../shared/FxModeToggle';
import { DividendFeeOptions } from '../shared/DividendFeeOptions';

interface StrategyComparisonFormProps {
  // Symbol selection
  symbol: string;
  setSymbol: (symbol: string) => void;
  supportedSymbols: string[];

  // Date range
  startDate: string;
  setStartDate: (date: string) => void;
  endDate: string;
  setEndDate: (date: string) => void;

  // Investment amount
  investment: string;
  setInvestment: (amount: string) => void;

  // Strategy selection
  selectedStrategies: string[];
  toggleStrategy: (strategy: 'SIMPLE' | 'DCA' | 'CONDITIONAL_PURCHASE') => void;
  strategyNames: Record<string, string>;

  // FX settings
  fxMode: 'auto' | 'manual';
  setFxMode: (mode: 'auto' | 'manual') => void;
  manualPurchaseFxRate: string;
  setManualPurchaseFxRate: (rate: string) => void;
  manualCurrentFxRate: string;
  setManualCurrentFxRate: (rate: string) => void;

  // Dividend and fee options
  tradingFeeRate: string;
  setTradingFeeRate: (rate: string) => void;
  dividendTax: boolean;
  setDividendTax: (tax: boolean) => void;
  reinvestDividends: boolean;
  setReinvestDividends: (reinvest: boolean) => void;
}

/**
 * 전략 비교 폼 컴포넌트
 * 동일한 종목에 대해 여러 투자 전략(단순 매수, 적립식, 조건부 매수)의 성과를 비교합니다.
 */
export const StrategyComparisonForm: React.FC<StrategyComparisonFormProps> = ({
  symbol,
  setSymbol,
  supportedSymbols,
  startDate,
  setStartDate,
  endDate,
  setEndDate,
  investment,
  setInvestment,
  selectedStrategies,
  toggleStrategy,
  strategyNames,
  fxMode,
  setFxMode,
  manualPurchaseFxRate,
  setManualPurchaseFxRate,
  manualCurrentFxRate,
  setManualCurrentFxRate,
  tradingFeeRate,
  setTradingFeeRate,
  dividendTax,
  setDividendTax,
  reinvestDividends,
  setReinvestDividends,
}) => {
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">종목</label>
          <StockSearchInput
            value={symbol}
            onChange={setSymbol}
            supportedSymbols={supportedSymbols}
            placeholder="종목 검색"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">시작일</label>
          <input
            type="date"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            max={new Date().toISOString().split('T')[0]}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">종료일</label>
          <input
            type="date"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            max={new Date().toISOString().split('T')[0]}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">투자금 (₩)</label>
          <NumberInput
            value={investment}
            onChange={setInvestment}
            placeholder="1,000,000"
            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-2">비교할 전략 선택 (최소 2개)</label>
        <div className="flex flex-wrap gap-2">
          {(['SIMPLE', 'DCA', 'CONDITIONAL_PURCHASE'] as const).map((strategy) => (
            <button
              key={strategy}
              onClick={() => toggleStrategy(strategy)}
              className={`px-4 py-2 rounded-md transition-colors ${
                selectedStrategies.includes(strategy)
                  ? 'bg-indigo-600 text-white'
                  : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
              }`}
            >
              {strategyNames[strategy]}
            </button>
          ))}
        </div>
      </div>

      {/* FX Mode Toggle */}
      <FxModeToggle
        fxMode={fxMode}
        setFxMode={setFxMode}
        manualPurchaseFxRate={manualPurchaseFxRate}
        setManualPurchaseFxRate={setManualPurchaseFxRate}
        manualCurrentFxRate={manualCurrentFxRate}
        setManualCurrentFxRate={setManualCurrentFxRate}
        purchaseLabel="시작일 환율 (₩/USD)"
        currentLabel="현재 환율 (₩/USD)"
      />

      {/* Dividend and Fee Options */}
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
