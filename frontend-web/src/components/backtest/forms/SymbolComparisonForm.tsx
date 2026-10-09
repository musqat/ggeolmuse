import React from 'react';
import StockSearchInput from '../../common/StockSearchInput';
import { NumberInput } from '../../common/NumberInput';
import { FxModeToggle } from '../shared/FxModeToggle';
import { DividendFeeOptions } from '../shared/DividendFeeOptions';
import { DateField } from '../../common/DateField';
import { getTodayString } from '../../../utils/dateUtils';

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
 * 여러 종목에 동일한 투자금으로 매수했을 때의 성과를 비교
 * 최대 10개 종목
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
  const today = getTodayString();

  // 환율 데이터 부족 경고 체크 (2014년 이전)
  const showFxWarning = !!comparePurchaseDate && comparePurchaseDate < '2014-01-01';

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {/* 시작일 */}
        <div>
          <label className="block text-sm font-medium text-tx-1 mb-2">시작일</label>
          <DateField
            value={comparePurchaseDate}
            onChange={setComparePurchaseDate}
            max={compareSaleDate || today}
            testId="date-start"
          />
        </div>

        {/* 종료일 */}
        <div>
          <label className="block text-sm font-medium text-tx-1 mb-2">종료일</label>
          <DateField
            value={compareSaleDate || today}
            onChange={setCompareSaleDate}
            min={comparePurchaseDate}
            max={today}
            testId="date-end"
          />
        </div>

        {/* 투자금 */}
        <div>
          <label className="block text-sm font-medium text-tx-1 mb-2">투자금 (₩)</label>
          <NumberInput
            value={compareInvestment}
            onChange={setCompareInvestment}
            placeholder="1,000,000"
            className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
          />
        </div>

        {/* 종목 추가 */}
        <div className="col-span-full">
          <label className="block text-sm font-medium text-tx-1 mb-2">종목 추가 (최대 10개)</label>
          <div className="flex items-center gap-2 mb-3">
            <StockSearchInput
              value={compareSymbolInput}
              onChange={setCompareSymbolInput}
              supportedSymbols={supportedSymbols}
              placeholder="종목 검색"
            />
            <button
              onClick={onAddSymbol}
              className="px-3 py-2 bg-brand text-white rounded-md hover:bg-brand-dark text-sm whitespace-nowrap"
            >
              + 추가
            </button>
          </div>
          <div className="flex flex-wrap gap-2">
            {compareSymbols.map((sym) => (
              <div
                key={sym}
                className="flex items-center space-x-2 px-3 py-1 bg-brand-bg text-brand-dark rounded-md"
              >
                <span className="font-medium">{sym}</span>
                <button
                  data-testid="compare-symbol-remove"
                  onClick={() => onRemoveSymbol(sym)}
                  className="text-tx-2 hover:text-brand-dark text-lg"
                >
                  ×
                </button>
              </div>
            ))}
          </div>
        </div>

        {/* 환율 데이터 부족 경고 */}
        {showFxWarning && (
          <div className="col-span-full">
            <div className="flex items-start gap-2 p-3 bg-warning-soft/10 border border-warning-soft/40 rounded-lg">
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
          </div>
        )}

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
