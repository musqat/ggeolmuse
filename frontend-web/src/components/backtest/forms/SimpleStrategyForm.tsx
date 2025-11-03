import React, { useState, useEffect, useMemo } from 'react';
import StockSearchInput from '../../common/StockSearchInput';
import { NumberInput } from '../../common/NumberInput';
import { FxModeToggle } from '../shared/FxModeToggle';
import { DividendFeeOptions } from '../shared/DividendFeeOptions';
import DatePicker from '../../common/DatePicker';
import { AlertTriangle } from 'lucide-react';

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
  // DatePicker용 Date 객체 상태 (기본값: 시작일 2025-01-01, 종료일 오늘)
  const [purchaseDateObj, setPurchaseDateObj] = useState<Date | null>(
    purchaseDate ? new Date(purchaseDate) : new Date('2025-01-01')
  );
  const [saleDateObj, setSaleDateObj] = useState<Date | null>(
    saleDate ? new Date(saleDate) : new Date()
  );

  // 달력 표시 상태
  const [showPurchaseDatePicker, setShowPurchaseDatePicker] = useState(false);
  const [showSaleDatePicker, setShowSaleDatePicker] = useState(false);

  // 환율 데이터 부족 경고 체크 (2014년 이전)
  const showFxWarning = useMemo(() => {
    if (!purchaseDateObj) return false;
    const cutoffDate = new Date('2014-01-01');
    return purchaseDateObj < cutoffDate;
  }, [purchaseDateObj]);

  // Date 객체를 문자열로 변환하여 부모 컴포넌트에 전달
  useEffect(() => {
    if (purchaseDateObj) {
      setPurchaseDate(purchaseDateObj.toISOString().split('T')[0]);
    }
  }, [purchaseDateObj, setPurchaseDate]);

  useEffect(() => {
    if (saleDateObj) {
      setSaleDate(saleDateObj.toISOString().split('T')[0]);
    }
  }, [saleDateObj, setSaleDate]);

  return (
    <div className="space-y-4">
      {/* 기본 설정 - 모바일 완전 세로 배치 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3">
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

        {/* 시작일 */}
        <div className="relative">
          <label className="block text-sm font-medium text-gray-700 mb-2">시작일</label>
          <button
            type="button"
            onClick={() => {
              setShowPurchaseDatePicker(!showPurchaseDatePicker);
              setShowSaleDatePicker(false);
            }}
            className="w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md text-left hover:border-indigo-500 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition whitespace-nowrap"
          >
            {purchaseDateObj
              ? purchaseDateObj.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
              : '날짜 선택'}
          </button>
          {showPurchaseDatePicker && (
            <div className="absolute top-full left-0 md:left-1/2 md:-translate-x-1/2 mt-2 z-50 shadow-2xl w-[400px] max-w-[calc(100vw-2rem)]">
              <DatePicker
                value={purchaseDateObj}
                onChange={(date) => {
                  setPurchaseDateObj(date);
                  setShowPurchaseDatePicker(false);
                }}
              />
            </div>
          )}
        </div>

        {/* 종료일 */}
        <div className="relative">
          <label className="block text-sm font-medium text-gray-700 mb-2">종료일</label>
          <button
            type="button"
            onClick={() => {
              setShowSaleDatePicker(!showSaleDatePicker);
              setShowPurchaseDatePicker(false);
            }}
            className="w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md text-left hover:border-indigo-500 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition whitespace-nowrap"
          >
            {saleDateObj
              ? saleDateObj.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
              : '날짜 선택'}
          </button>
          {showSaleDatePicker && (
            <div className="absolute top-full left-0 md:left-1/2 md:-translate-x-1/2 mt-2 z-50 shadow-2xl w-[400px] max-w-[calc(100vw-2rem)]">
              <DatePicker
                value={saleDateObj}
                onChange={(date) => {
                  setSaleDateObj(date);
                  setShowSaleDatePicker(false);
                }}
              />
            </div>
          )}
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
