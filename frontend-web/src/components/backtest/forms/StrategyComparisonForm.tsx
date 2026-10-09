import React from 'react';
import StockSearchInput from '../../common/StockSearchInput';
import { NumberInput } from '../../common/NumberInput';
import { FxModeToggle } from '../shared/FxModeToggle';
import { DividendFeeOptions } from '../shared/DividendFeeOptions';
import { DateField } from '../../common/DateField';
import { COMPARISON_STRATEGIES } from '../comparison/catalog';
import type { ComparisonStrategyType } from '../comparison/types';
import { getTodayString } from '../../../utils/dateUtils';

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
  toggleStrategy: (strategy: ComparisonStrategyType) => void;

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
 * 동일한 종목에 대해 여러 투자 전략(단순 매수, 적립식, 조건부 매수)의 성과를 비교
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
  const today = getTodayString();

  // 환율 데이터 부족 경고 체크 (2014년 이전)
  const showFxWarning = !!startDate && startDate < '2014-01-01';

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <div>
          <label className="block text-sm font-medium text-tx-1 mb-2">종목</label>
          <StockSearchInput
            value={symbol}
            onChange={setSymbol}
            supportedSymbols={supportedSymbols}
            placeholder="종목 검색"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-tx-1 mb-2">시작일</label>
          <DateField
            value={startDate}
            onChange={setStartDate}
            max={endDate || today}
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-tx-1 mb-2">종료일</label>
          <DateField
            value={endDate || today}
            onChange={setEndDate}
            min={startDate}
            max={today}
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-tx-1 mb-2">투자금 (₩)</label>
          <NumberInput
            value={investment}
            onChange={setInvestment}
            placeholder="1,000,000"
            className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
          />
        </div>
      </div>

      <div>
        <label className="block text-sm font-medium text-tx-1 mb-2">비교할 전략 선택 (최소 2개)</label>
        <div className="flex flex-wrap gap-2">
          {COMPARISON_STRATEGIES.map(({ type, name }) => (
            <button
              key={type}
              onClick={() => toggleStrategy(type)}
              className={`px-4 py-2 rounded-md transition-colors ${
                selectedStrategies.includes(type)
                  ? 'bg-brand text-white'
                  : 'bg-elevated/50 text-tx-2 hover:bg-hover hover:text-tx-1 border border-line'
              }`}
            >
              {name}
            </button>
          ))}
        </div>
      </div>

      {/* 환율 데이터 부족 경고 */}
      {showFxWarning && (
        <div className="flex items-start gap-2 p-3 bg-amber-50 border border-amber-200 rounded-lg">
          <div className="flex-1">
            <p className="text-sm font-medium text-amber-800">
              환율 데이터 부족 가능성
            </p>
            <p className="text-xs text-amber-700 mt-1">
              2014년 이전 기간은 환율 정보가 부족할 수 있습니다.
              정확한 백테스트를 위해 <span className="font-semibold">수동 환율 입력</span>을 권장합니다.
            </p>
          </div>
        </div>
      )}

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
