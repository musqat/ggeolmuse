import React from 'react';
import StockSearchInput from '../../common/StockSearchInput';
import { NumberInput } from '../../common/NumberInput';
import { FxModeToggle } from '../shared/FxModeToggle';
import { DividendFeeOptions } from '../shared/DividendFeeOptions';

interface DCAStrategyFormProps {
  symbol: string;
  setSymbol: (symbol: string) => void;
  dcaStartDate: string;
  setDcaStartDate: (date: string) => void;
  dcaEndDate: string;
  setDcaEndDate: (date: string) => void;
  monthlyAmount: string;
  setMonthlyAmount: (amount: string) => void;
  purchaseDay: string;
  setPurchaseDay: (day: string) => void;
  investmentInterval: string;
  setInvestmentInterval: (interval: string) => void;
  dcaFxMode: 'auto' | 'manual';
  setDcaFxMode: (mode: 'auto' | 'manual') => void;
  dcaManualPurchaseFxRate: string;
  setDcaManualPurchaseFxRate: (rate: string) => void;
  dcaManualCurrentFxRate: string;
  setDcaManualCurrentFxRate: (rate: string) => void;
  dcaReinvestDividends: boolean;
  setDcaReinvestDividends: (reinvest: boolean) => void;
  dcaTradingFeeRate: string;
  setDcaTradingFeeRate: (rate: string) => void;
  dcaDividendTax: boolean;
  setDcaDividendTax: (tax: boolean) => void;
  supportedSymbols: string[];
}

/**
 * 적립식 투자 전략 입력 폼 (DCA: Dollar Cost Averaging)
 * 정해진 날짜에 정해진 금액을 주기적으로 투자하는 전략의 수익률을 시뮬레이션합니다.
 */
export const DCAStrategyForm: React.FC<DCAStrategyFormProps> = ({
  symbol,
  setSymbol,
  dcaStartDate,
  setDcaStartDate,
  dcaEndDate,
  setDcaEndDate,
  monthlyAmount,
  setMonthlyAmount,
  purchaseDay,
  setPurchaseDay,
  investmentInterval,
  setInvestmentInterval,
  dcaFxMode,
  setDcaFxMode,
  dcaManualPurchaseFxRate,
  setDcaManualPurchaseFxRate,
  dcaManualCurrentFxRate,
  setDcaManualCurrentFxRate,
  dcaReinvestDividends,
  setDcaReinvestDividends,
  dcaTradingFeeRate,
  setDcaTradingFeeRate,
  dcaDividendTax,
  setDcaDividendTax,
  supportedSymbols,
}) => {
  const today = new Date().toISOString().split('T')[0];

  return (
    <div className="space-y-4">
      {/* 기본 설정 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-6 gap-4">
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
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">시작일</label>
          <input
            type="date"
            value={dcaStartDate}
            onChange={(e) => setDcaStartDate(e.target.value)}
            max={today}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>

        {/* 종료일 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">종료일</label>
          <input
            type="date"
            value={dcaEndDate}
            onChange={(e) => setDcaEndDate(e.target.value)}
            max={today}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>

        {/* 월 투자금 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">월 투자금 (₩)</label>
          <NumberInput
            value={monthlyAmount}
            onChange={setMonthlyAmount}
            placeholder="100,000"
            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
          <p className="text-xs text-gray-400 mt-1">매월 정기 투자 금액</p>
        </div>

        {/* 매월 투자일 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">매월 투자일</label>
          <input
            type="number"
            value={purchaseDay}
            onChange={(e) => setPurchaseDay(e.target.value)}
            placeholder="15"
            min="1"
            max="28"
            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
          <p className="text-xs text-gray-500 mt-1">1~28일</p>
        </div>

        {/* 투자 주기 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">투자 주기</label>
          <select
            value={investmentInterval}
            onChange={(e) => setInvestmentInterval(e.target.value)}
            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          >
            <option value="1">매월 (1개월)</option>
            <option value="2">2개월마다</option>
            <option value="3">분기마다 (3개월)</option>
            <option value="6">반기마다 (6개월)</option>
          </select>
        </div>
      </div>

      {/* 환율 설정 */}
      <FxModeToggle
        fxMode={dcaFxMode}
        setFxMode={setDcaFxMode}
        manualPurchaseFxRate={dcaManualPurchaseFxRate}
        setManualPurchaseFxRate={setDcaManualPurchaseFxRate}
        manualCurrentFxRate={dcaManualCurrentFxRate}
        setManualCurrentFxRate={setDcaManualCurrentFxRate}
        purchaseLabel="시작일 환율"
        currentLabel="현재 환율"
      />

      {/* 배당 및 수수료 옵션 */}
      <DividendFeeOptions
        tradingFeeRate={dcaTradingFeeRate}
        setTradingFeeRate={setDcaTradingFeeRate}
        dividendTax={dcaDividendTax}
        setDividendTax={setDcaDividendTax}
        reinvestDividends={dcaReinvestDividends}
        setReinvestDividends={setDcaReinvestDividends}
      />
    </div>
  );
};
