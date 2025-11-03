import React, { useState, useEffect, useMemo } from 'react';
import StockSearchInput from '../../common/StockSearchInput';
import { NumberInput } from '../../common/NumberInput';
import { FxModeToggle } from '../shared/FxModeToggle';
import { DividendFeeOptions } from '../shared/DividendFeeOptions';
import DatePicker from '../../common/DatePicker';
import { AlertTriangle } from 'lucide-react';

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
  const [startDateObj, setStartDateObj] = useState<Date | null>(
    startDate ? new Date(startDate) : new Date('2025-01-01')
  );
  const [endDateObj, setEndDateObj] = useState<Date | null>(
    endDate ? new Date(endDate) : new Date()
  );
  const [showStartDatePicker, setShowStartDatePicker] = useState(false);
  const [showEndDatePicker, setShowEndDatePicker] = useState(false);

  // 환율 데이터 부족 경고 체크 (2014년 이전)
  const showFxWarning = useMemo(() => {
    if (!startDateObj) return false;
    const cutoffDate = new Date('2014-01-01');
    return startDateObj < cutoffDate;
  }, [startDateObj]);

  useEffect(() => {
    if (startDateObj) {
      setStartDate(startDateObj.toISOString().split('T')[0]);
    }
  }, [startDateObj, setStartDate]);

  useEffect(() => {
    if (endDateObj) {
      setEndDate(endDateObj.toISOString().split('T')[0]);
    }
  }, [endDateObj, setEndDate]);

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

        <div className="relative">
          <label className="block text-sm font-medium text-gray-700 mb-2">시작일</label>
          <button
            type="button"
            onClick={() => {
              setShowStartDatePicker(!showStartDatePicker);
              setShowEndDatePicker(false);
            }}
            className="w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md text-left hover:border-indigo-500 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition whitespace-nowrap"
          >
            {startDateObj
              ? startDateObj.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
              : '날짜 선택'}
          </button>
          {showStartDatePicker && (
            <div className="absolute top-full left-0 md:left-1/2 md:-translate-x-1/2 mt-2 z-50 shadow-2xl w-[400px] max-w-[calc(100vw-2rem)]">
              <DatePicker
                value={startDateObj}
                onChange={(date) => {
                  setStartDateObj(date);
                  setShowStartDatePicker(false);
                }}
              />
            </div>
          )}
        </div>

        <div className="relative">
          <label className="block text-sm font-medium text-gray-700 mb-2">종료일</label>
          <button
            type="button"
            onClick={() => {
              setShowEndDatePicker(!showEndDatePicker);
              setShowStartDatePicker(false);
            }}
            className="w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md text-left hover:border-indigo-500 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition whitespace-nowrap"
          >
            {endDateObj
              ? endDateObj.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
              : '날짜 선택'}
          </button>
          {showEndDatePicker && (
            <div className="absolute top-full left-0 md:left-1/2 md:-translate-x-1/2 mt-2 z-50 shadow-2xl w-[400px] max-w-[calc(100vw-2rem)]">
              <DatePicker
                value={endDateObj}
                onChange={(date) => {
                  setEndDateObj(date);
                  setShowEndDatePicker(false);
                }}
              />
            </div>
          )}
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

      {/* 환율 데이터 부족 경고 */}
      {showFxWarning && (
        <div className="flex items-start gap-2 p-3 bg-amber-50 border border-amber-200 rounded-lg">
          <AlertTriangle className="w-5 h-5 text-amber-600 flex-shrink-0 mt-0.5" />
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
