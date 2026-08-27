import React, { useState, useEffect, useMemo } from 'react';
import StockSearchInput from '../../common/StockSearchInput';
import { NumberInput } from '../../common/NumberInput';
import { FxModeToggle } from '../shared/FxModeToggle';
import { DividendFeeOptions } from '../shared/DividendFeeOptions';
import DatePicker from '../../common/DatePicker';
import { AlertTriangle } from 'lucide-react';

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
 * 가격이 지정된 비율만큼 하락했을 때 자동으로 매수하는 전략의 수익률을 시뮬레이션
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

  const [startDateObj, setStartDateObj] = useState<Date | null>(
    conditionalStartDate ? new Date(conditionalStartDate) : new Date('2025-01-01')
  );
  const [endDateObj, setEndDateObj] = useState<Date | null>(
    conditionalEndDate ? new Date(conditionalEndDate) : new Date()
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
          <label className="block text-sm font-medium text-tx-1 mb-2">종목</label>
          <StockSearchInput
            value={symbol}
            onChange={setSymbol}
            supportedSymbols={supportedSymbols}
            placeholder="종목 검색"
          />
        </div>

        {/* 시작일 */}
        <div className="relative">
          <label className="block text-sm font-medium text-tx-1 mb-2">시작일</label>
          <button
            type="button"
            onClick={() => {
              setShowStartDatePicker(!showStartDatePicker);
              setShowEndDatePicker(false);
            }}
            className="w-full px-3 py-1.5 text-sm border border-line-strong rounded-md text-left hover:border-brand focus:ring-2 focus:ring-brand focus:border-brand transition whitespace-nowrap"
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
          <label className="block text-sm font-medium text-tx-1 mb-2">종료일</label>
          <button
            type="button"
            onClick={() => {
              setShowEndDatePicker(!showEndDatePicker);
              setShowStartDatePicker(false);
            }}
            className="w-full px-3 py-1.5 text-sm border border-line-strong rounded-md text-left hover:border-brand focus:ring-2 focus:ring-brand focus:border-brand transition whitespace-nowrap"
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
          <label className="block text-sm font-medium text-tx-1 mb-2">투자 모드</label>
          <div className="grid grid-cols-2 gap-3">
            <button
              type="button"
              onClick={() => setInvestmentMode('TOTAL_BUDGET')}
              className={`px-4 py-3 rounded-lg border-2 transition-all ${
                investmentMode === 'TOTAL_BUDGET'
                  ? 'border-brand bg-brand-bg text-brand-dark'
                  : 'border-line-strong bg-surface text-tx-1 hover:border-line-strong'
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
                  ? 'border-brand bg-brand-bg text-brand-dark'
                  : 'border-line-strong bg-surface text-tx-1 hover:border-line-strong'
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
              <label className="block text-sm font-medium text-tx-1 mb-2">총 투자금 (₩)</label>
              <NumberInput
                value={totalInvestment}
                onChange={setTotalInvestment}
                placeholder="4,000,000"
                className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
              />
              <p className="text-xs text-tx-3 mt-1">전체 투자 예산</p>
            </div>

            <div>
              <label className="block text-sm font-medium text-tx-1 mb-2">회당 투자금 (₩)</label>
              <NumberInput
                value={amountPerPurchase}
                onChange={setAmountPerPurchase}
                placeholder="500,000"
                className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
              />
              <p className="text-xs text-tx-3 mt-1">
                최대 {Math.floor(parseFloat(totalInvestment || '0') / parseFloat(amountPerPurchase || '1'))}회 투자 가능
              </p>
            </div>
          </>
        ) : (
          <>
            <div>
              <label className="block text-sm font-medium text-tx-1 mb-2">회당 투자금 (₩)</label>
              <NumberInput
                value={amountPerPurchase}
                onChange={setAmountPerPurchase}
                placeholder="500,000"
                className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
              />
              <p className="text-xs text-tx-3 mt-1">조건 만족 시마다 투자할 금액</p>
            </div>

            <div>
              <label className="block text-sm font-medium text-tx-1 mb-2">최대 매수 횟수</label>
              <input
                type="number"
                value={maxPurchases}
                onChange={(e) => setMaxPurchases(e.target.value)}
                placeholder="20"
                step="1"
                min="1"
                className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
              />
              <p className="text-xs text-tx-2 mt-1">
                최대 ₩{(parseFloat(amountPerPurchase || '0') * parseInt(maxPurchases || '0')).toLocaleString()} 투자
              </p>
            </div>
          </>
        )}

        <div>
          <label className="block text-sm font-medium text-tx-1 mb-2">하락률 (%)</label>
          <input
            type="number"
            value={dropPercentage}
            onChange={(e) => setDropPercentage(e.target.value)}
            placeholder="5"
            step="1"
            min="0.1"
            max="100"
            className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
          />
          <p className="text-xs text-tx-2 mt-1">가격이 이만큼 하락 시 매수</p>
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
