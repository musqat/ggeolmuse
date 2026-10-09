import React from 'react';
import StockSearchInput from '../../common/StockSearchInput';
import { NumberInput } from '../../common/NumberInput';
import { FxModeToggle } from '../shared/FxModeToggle';
import { DividendFeeOptions } from '../shared/DividendFeeOptions';
import { DateField } from '../../common/DateField';
import { getTodayString } from '../../../utils/dateUtils';

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
 * 특정 날짜에 종목을 매수하여 보유한 경우의 수익률을 시뮬레이션
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
  const today = getTodayString();

  // 환율 데이터 부족 경고 체크 (2014년 이전)
  const showFxWarning = !!purchaseDate && purchaseDate < '2014-01-01';

  return (
    <div className="space-y-4">
      {/* 기본 설정 - 모바일 완전 세로 배치 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3">
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

        {/* 초기 투자금 */}
        <div>
          <label className="block text-sm font-medium text-tx-1 mb-2">초기 투자금 (₩)</label>
          <NumberInput
            value={initialInvestment}
            onChange={setInitialInvestment}
            placeholder="300,000"
            className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
          />
          <p className="text-xs text-tx-3 mt-1">최소 약 30만원 권장</p>
        </div>

        {/* 시작일 */}
        <div>
          <label className="block text-sm font-medium text-tx-1 mb-2">시작일</label>
          <DateField
            value={purchaseDate}
            onChange={setPurchaseDate}
            max={saleDate || today}
            testId="date-start"
          />
        </div>

        {/* 종료일. 비어 있으면 오늘로 보이고 오늘로 계산한다 */}
        <div>
          <label className="block text-sm font-medium text-tx-1 mb-2">종료일</label>
          <DateField
            value={saleDate || today}
            onChange={setSaleDate}
            min={purchaseDate}
            max={today}
            testId="date-end"
          />
        </div>
      </div>

      {/* 환율 데이터 부족 경고 */}
      {showFxWarning && (
        <div className="flex items-start gap-2 p-3 bg-warning/10 border border-warning/30 rounded-lg">
          <div className="flex-1">
            <p className="text-sm font-medium text-warning">
              환율 데이터 부족 가능성
            </p>
            <p className="text-xs text-tx-2 mt-1">
              2014년 이전 기간은 환율 정보가 부족할 수 있습니다.
              정확한 백테스트를 위해 <span className="font-semibold">수동 환율 입력</span>을 권장합니다.
            </p>
          </div>
        </div>
      )}

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
