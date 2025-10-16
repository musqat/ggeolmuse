import React, { useState, useEffect } from 'react';
import StockSearchInput from '../../common/StockSearchInput';
import { NumberInput } from '../../common/NumberInput';
import { FxModeToggle } from '../shared/FxModeToggle';
import { DividendFeeOptions } from '../shared/DividendFeeOptions';
import DatePicker from '../../common/DatePicker';

interface ConditionalStrategyFormProps {
  symbol: string;
  setSymbol: (symbol: string) => void;
  conditionalStartDate: string;
  setConditionalStartDate: (date: string) => void;
  conditionalEndDate: string;
  setConditionalEndDate: (date: string) => void;
  investmentMode: 'TOTAL_BUDGET' | 'PER_PURCHASE';
  setInvestmentMode: (mode: 'TOTAL_BUDGET' | 'PER_PURCHASE') => void;
  totalInvestment: string;
  setTotalInvestment: (amount: string) => void;
  amountPerPurchase: string;
  setAmountPerPurchase: (amount: string) => void;
  maxPurchases: string;
  setMaxPurchases: (count: string) => void;
  dropPercentage: string;
  setDropPercentage: (percentage: string) => void;
  conditionalFxMode: 'auto' | 'manual';
  setConditionalFxMode: (mode: 'auto' | 'manual') => void;
  conditionalManualPurchaseFxRate: string;
  setConditionalManualPurchaseFxRate: (rate: string) => void;
  conditionalManualCurrentFxRate: string;
  setConditionalManualCurrentFxRate: (rate: string) => void;
  conditionalReinvestDividends: boolean;
  setConditionalReinvestDividends: (reinvest: boolean) => void;
  conditionalTradingFeeRate: string;
  setConditionalTradingFeeRate: (rate: string) => void;
  conditionalDividendTax: boolean;
  setConditionalDividendTax: (tax: boolean) => void;
  supportedSymbols: string[];
}

/**
 * 조건부 매수 전략 입력 폼
 * 가격이 지정된 비율만큼 하락했을 때 자동으로 매수하는 전략의 수익률을 시뮬레이션합니다.
 */
export const ConditionalStrategyForm: React.FC<ConditionalStrategyFormProps> = ({
  symbol,
  setSymbol,
  conditionalStartDate,
  setConditionalStartDate,
  conditionalEndDate,
  setConditionalEndDate,
  investmentMode,
  setInvestmentMode,
  totalInvestment,
  setTotalInvestment,
  amountPerPurchase,
  setAmountPerPurchase,
  maxPurchases,
  setMaxPurchases,
  dropPercentage,
  setDropPercentage,
  conditionalFxMode,
  setConditionalFxMode,
  conditionalManualPurchaseFxRate,
  setConditionalManualPurchaseFxRate,
  conditionalManualCurrentFxRate,
  setConditionalManualCurrentFxRate,
  conditionalReinvestDividends,
  setConditionalReinvestDividends,
  conditionalTradingFeeRate,
  setConditionalTradingFeeRate,
  conditionalDividendTax,
  setConditionalDividendTax,
  supportedSymbols,
}) => {
  const today = new Date().toISOString().split('T')[0];

  const [startDateObj, setStartDateObj] = useState<Date | null>(
    conditionalStartDate ? new Date(conditionalStartDate) : new Date('2025-01-01')
  );
  const [endDateObj, setEndDateObj] = useState<Date | null>(
    conditionalEndDate ? new Date(conditionalEndDate) : new Date()
  );
  const [showStartDatePicker, setShowStartDatePicker] = useState(false);
  const [showEndDatePicker, setShowEndDatePicker] = useState(false);

  useEffect(() => {
    if (startDateObj) {
      setConditionalStartDate(startDateObj.toISOString().split('T')[0]);
    }
  }, [startDateObj, setConditionalStartDate]);

  useEffect(() => {
    if (endDateObj) {
      setConditionalEndDate(endDateObj.toISOString().split('T')[0]);
    }
  }, [endDateObj, setConditionalEndDate]);

  return (
    <div className="space-y-4">
      {/* 기본 설정 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
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

        {/* 시작일 */}
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
            {startDateObj ? startDateObj.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }) : '날짜 선택'}
          </button>
          {showStartDatePicker && (
            <div className="absolute top-full left-0 md:left-1/2 md:-translate-x-1/2 mt-2 z-50 shadow-2xl w-[400px] max-w-[calc(100vw-2rem)]">
              <DatePicker value={startDateObj} onChange={(date) => { setStartDateObj(date); setShowStartDatePicker(false); }} />
            </div>
          )}
        </div>

        {/* 종료일 */}
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
            {endDateObj ? endDateObj.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }) : '날짜 선택'}
          </button>
          {showEndDatePicker && (
            <div className="absolute top-full left-0 md:left-1/2 md:-translate-x-1/2 mt-2 z-50 shadow-2xl w-[400px] max-w-[calc(100vw-2rem)]">
              <DatePicker value={endDateObj} onChange={(date) => { setEndDateObj(date); setShowEndDatePicker(false); }} />
            </div>
          )}
        </div>

        {/* 투자 모드 선택 */}
        <div className="col-span-2">
          <label className="block text-sm font-medium text-gray-700 mb-2">투자 모드</label>
          <div className="grid grid-cols-2 gap-3">
            <button
              type="button"
              onClick={() => setInvestmentMode('TOTAL_BUDGET')}
              className={`px-4 py-3 rounded-lg border-2 transition-all ${
                investmentMode === 'TOTAL_BUDGET'
                  ? 'border-indigo-500 bg-indigo-50 text-indigo-700'
                  : 'border-gray-300 bg-white text-gray-700 hover:border-gray-400'
              }`}
            >
              <div className="font-semibold">총 예산 분할</div>
              <div className="text-xs mt-1">총 투자금을 회당 금액으로 분할</div>
            </button>
            <button
              type="button"
              onClick={() => setInvestmentMode('PER_PURCHASE')}
              className={`px-4 py-3 rounded-lg border-2 transition-all ${
                investmentMode === 'PER_PURCHASE'
                  ? 'border-indigo-500 bg-indigo-50 text-indigo-700'
                  : 'border-gray-300 bg-white text-gray-700 hover:border-gray-400'
              }`}
            >
              <div className="font-semibold">회당 고정 금액</div>
              <div className="text-xs mt-1">조건 만족 시마다 동일 금액 투자</div>
            </button>
          </div>
        </div>

        {/* 모드별 입력 필드 */}
        {investmentMode === 'TOTAL_BUDGET' ? (
          <>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">총 투자금 (₩)</label>
              <NumberInput
                value={totalInvestment}
                onChange={setTotalInvestment}
                placeholder="4,000,000"
                className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
              />
              <p className="text-xs text-gray-400 mt-1">전체 투자 예산</p>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">회당 투자금 (₩)</label>
              <NumberInput
                value={amountPerPurchase}
                onChange={setAmountPerPurchase}
                placeholder="500,000"
                className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
              />
              <p className="text-xs text-gray-400 mt-1">
                최대 {Math.floor(parseFloat(totalInvestment || '0') / parseFloat(amountPerPurchase || '1'))}회 투자 가능
              </p>
            </div>
          </>
        ) : (
          <>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">회당 투자금 (₩)</label>
              <NumberInput
                value={amountPerPurchase}
                onChange={setAmountPerPurchase}
                placeholder="500,000"
                className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
              />
              <p className="text-xs text-gray-400 mt-1">조건 만족 시마다 투자할 금액</p>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">최대 매수 횟수</label>
              <input
                type="number"
                value={maxPurchases}
                onChange={(e) => setMaxPurchases(e.target.value)}
                placeholder="20"
                step="1"
                min="1"
                className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
              />
              <p className="text-xs text-gray-500 mt-1">
                최대 ₩{(parseFloat(amountPerPurchase || '0') * parseInt(maxPurchases || '0')).toLocaleString()} 투자
              </p>
            </div>
          </>
        )}

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">하락률 (%)</label>
          <input
            type="number"
            value={dropPercentage}
            onChange={(e) => setDropPercentage(e.target.value)}
            placeholder="5"
            step="1"
            min="0.1"
            max="100"
            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
          <p className="text-xs text-gray-500 mt-1">가격이 이만큼 하락 시 매수</p>
        </div>
      </div>

      {/* 환율 설정 */}
      <FxModeToggle
        fxMode={conditionalFxMode}
        setFxMode={setConditionalFxMode}
        manualPurchaseFxRate={conditionalManualPurchaseFxRate}
        setManualPurchaseFxRate={setConditionalManualPurchaseFxRate}
        manualCurrentFxRate={conditionalManualCurrentFxRate}
        setManualCurrentFxRate={setConditionalManualCurrentFxRate}
        purchaseLabel="시작일 환율"
        currentLabel="현재 환율"
      />

      {/* 배당 및 수수료 옵션 */}
      <DividendFeeOptions
        tradingFeeRate={conditionalTradingFeeRate}
        setTradingFeeRate={setConditionalTradingFeeRate}
        dividendTax={conditionalDividendTax}
        setDividendTax={setConditionalDividendTax}
        reinvestDividends={conditionalReinvestDividends}
        setReinvestDividends={setConditionalReinvestDividends}
      />
    </div>
  );
};
