import React from 'react';
import StockSearchInput from '../../common/StockSearchInput';
import { NumberInput } from '../../common/NumberInput';
import { FxModeToggle } from '../shared/FxModeToggle';
import { DividendFeeOptions } from '../shared/DividendFeeOptions';

interface SymbolComparisonFormProps {
  compareSymbols: string[];
  setCompareSymbols: (symbols: string[]) => void;
  compareSymbolInput: string;
  setCompareSymbolInput: (input: string) => void;
  comparePurchaseDate: string;
  setComparePurchaseDate: (date: string) => void;
  compareSaleDate: string;
  setCompareSaleDate: (date: string) => void;
  compareInvestment: string;
  setCompareInvestment: (amount: string) => void;
  compareFxMode: 'auto' | 'manual';
  setCompareFxMode: (mode: 'auto' | 'manual') => void;
  compareManualPurchaseFxRate: string;
  setCompareManualPurchaseFxRate: (rate: string) => void;
  compareManualCurrentFxRate: string;
  setCompareManualCurrentFxRate: (rate: string) => void;
  compareTradingFeeRate: string;
  setCompareTradingFeeRate: (rate: string) => void;
  compareDividendTax: boolean;
  setCompareDividendTax: (tax: boolean) => void;
  compareReinvestDividends: boolean;
  setCompareReinvestDividends: (reinvest: boolean) => void;
  supportedSymbols: string[];
  onAddSymbol: () => void;
  onRemoveSymbol: (symbol: string) => void;
}

/**
 * 종목 비교 백테스트 폼
 * 여러 종목에 동일한 투자금으로 매수했을 때의 성과를 비교합니다.
 * 최대 10개 종목까지 비교 가능합니다.
 */
export const SymbolComparisonForm: React.FC<SymbolComparisonFormProps> = ({
  compareSymbols,
  compareSymbolInput,
  setCompareSymbolInput,
  comparePurchaseDate,
  setComparePurchaseDate,
  compareSaleDate,
  setCompareSaleDate,
  compareInvestment,
  setCompareInvestment,
  compareFxMode,
  setCompareFxMode,
  compareManualPurchaseFxRate,
  setCompareManualPurchaseFxRate,
  compareManualCurrentFxRate,
  setCompareManualCurrentFxRate,
  compareTradingFeeRate,
  setCompareTradingFeeRate,
  compareDividendTax,
  setCompareDividendTax,
  compareReinvestDividends,
  setCompareReinvestDividends,
  supportedSymbols,
  onAddSymbol,
  onRemoveSymbol,
}) => {
  const today = new Date().toISOString().split('T')[0];

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {/* 매수일 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">매수일</label>
          <input
            type="date"
            value={comparePurchaseDate}
            onChange={(e) => setComparePurchaseDate(e.target.value)}
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
              value={compareSaleDate}
              onChange={(e) => setCompareSaleDate(e.target.value)}
              max={today}
              className="flex-1 px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            />
            {compareSaleDate && (
              <button
                type="button"
                onClick={() => setCompareSaleDate('')}
                className="px-3 py-2 bg-gray-200 text-gray-700 rounded-md hover:bg-gray-300 text-sm"
                title="현재까지로 설정"
              >
                ✕
              </button>
            )}
          </div>
          <p className="text-xs text-gray-400 mt-1">
            {compareSaleDate ? '특정 날짜까지' : '비어있음 (현재까지)'}
          </p>
        </div>

        {/* 투자금 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">투자금 (₩)</label>
          <NumberInput
            value={compareInvestment}
            onChange={setCompareInvestment}
            placeholder="1,000,000"
            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>

        {/* 종목 추가 */}
        <div className="col-span-full">
          <label className="block text-sm font-medium text-gray-700 mb-2">종목 추가 (최대 10개)</label>
          <div className="flex items-center gap-2 mb-3">
            <StockSearchInput
              value={compareSymbolInput}
              onChange={setCompareSymbolInput}
              supportedSymbols={supportedSymbols}
              placeholder="종목 검색"
            />
            <button
              onClick={onAddSymbol}
              className="px-3 py-2 bg-indigo-600 text-white rounded-md hover:bg-indigo-700 text-sm whitespace-nowrap"
            >
              + 추가
            </button>
          </div>
          <div className="flex flex-wrap gap-2">
            {compareSymbols.map((sym) => (
              <div
                key={sym}
                className="flex items-center space-x-2 px-3 py-1 bg-indigo-100 text-indigo-700 rounded-md"
              >
                <span className="font-medium">{sym}</span>
                <button
                  onClick={() => onRemoveSymbol(sym)}
                  className="text-indigo-900 hover:text-indigo-700 text-lg"
                >
                  ×
                </button>
              </div>
            ))}
          </div>
        </div>

        {/* 환율 설정 */}
        <div className="col-span-full">
          <FxModeToggle
            fxMode={compareFxMode}
            setFxMode={setCompareFxMode}
            manualPurchaseFxRate={compareManualPurchaseFxRate}
            setManualPurchaseFxRate={setCompareManualPurchaseFxRate}
            manualCurrentFxRate={compareManualCurrentFxRate}
            setManualCurrentFxRate={setCompareManualCurrentFxRate}
            purchaseLabel="시작일 환율 (₩/USD)"
            currentLabel="현재 환율 (₩/USD)"
          />
        </div>

        {/* 배당 및 수수료 옵션 */}
        <div className="col-span-full">
          <DividendFeeOptions
            tradingFeeRate={compareTradingFeeRate}
            setTradingFeeRate={setCompareTradingFeeRate}
            dividendTax={compareDividendTax}
            setDividendTax={setCompareDividendTax}
            reinvestDividends={compareReinvestDividends}
            setReinvestDividends={setCompareReinvestDividends}
          />
        </div>
      </div>
    </div>
  );
};
