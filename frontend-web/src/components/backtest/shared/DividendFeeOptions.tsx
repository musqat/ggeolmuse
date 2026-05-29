import React from 'react';
import { Percent, TrendingDown, Repeat } from 'lucide-react';

interface DividendFeeOptionsProps {
  tradingFeeRate: string;
  setTradingFeeRate: (rate: string) => void;
  dividendTax: boolean;
  setDividendTax: (tax: boolean) => void;
  reinvestDividends: boolean;
  setReinvestDividends: (reinvest: boolean) => void;
}

export const DividendFeeOptions: React.FC<DividendFeeOptionsProps> = ({
  tradingFeeRate,
  setTradingFeeRate,
  dividendTax,
  setDividendTax,
  reinvestDividends,
  setReinvestDividends,
}) => {
  return (
    <div className="border-t border-line pt-4 mt-4 space-y-3">
      {/* 거래 수수료율 */}
      <div className="flex items-center space-x-3">
        <Percent className="w-4 h-4 text-tx-2" />
        <label className="text-sm font-medium text-tx-1 flex-shrink-0">
          거래 수수료율
        </label>
        <input
          type="number"
          value={tradingFeeRate}
          onChange={(e) => setTradingFeeRate(e.target.value)}
          className="w-24 border border-line-strong rounded-md px-3 py-1.5 text-sm focus:ring-2 focus:ring-brand focus:border-brand"
          placeholder="0"
          step="0.01"
          min="0"
          max="100"
        />
        <span className="text-sm text-tx-2">%</span>
        <span className="text-xs text-tx-3 ml-2">
          (매수/매도 시 각각 적용)
        </span>
      </div>

      {/* 배당 원천징수 */}
      <label className="flex items-center space-x-3 cursor-pointer group">
        <input
          type="checkbox"
          checked={dividendTax}
          onChange={(e) => setDividendTax(e.target.checked)}
          className="w-4 h-4 text-brand border-line-strong rounded focus:ring-brand"
        />
        <TrendingDown className="w-4 h-4 text-tx-2 group-hover:text-brand" />
        <span className="text-sm text-tx-1 group-hover:text-brand">
          배당 원천징수 (15.4%)
        </span>
        <span className="text-xs text-tx-3">
          미국 주식 배당 세금 공제
        </span>
      </label>

      {/* 배당금 재투자 */}
      <label className="flex items-center space-x-3 cursor-pointer group">
        <input
          type="checkbox"
          checked={reinvestDividends}
          onChange={(e) => setReinvestDividends(e.target.checked)}
          className="w-4 h-4 text-brand border-line-strong rounded focus:ring-brand"
        />
        <Repeat className="w-4 h-4 text-tx-2 group-hover:text-brand" />
        <span className="text-sm text-tx-1 group-hover:text-brand">
          배당금 재투자
        </span>
        <span className="text-xs text-tx-3">
          받은 배당금으로 자동 매수
        </span>
      </label>
    </div>
  );
};
