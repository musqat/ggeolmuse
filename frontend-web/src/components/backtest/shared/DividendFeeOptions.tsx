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

/**
 * 배당 및 수수료 옵션 컴포넌트
 * 거래 수수료율, 배당 원천징수, 배당금 재투자 옵션을 설정합니다.
 */
export const DividendFeeOptions: React.FC<DividendFeeOptionsProps> = ({
  tradingFeeRate,
  setTradingFeeRate,
  dividendTax,
  setDividendTax,
  reinvestDividends,
  setReinvestDividends,
}) => {
  return (
    <div className="border-t border-gray-200 pt-4 mt-4 space-y-3">
      {/* 거래 수수료율 */}
      <div className="flex items-center space-x-3">
        <Percent className="w-4 h-4 text-gray-500" />
        <label className="text-sm font-medium text-gray-700 flex-shrink-0">
          거래 수수료율
        </label>
        <input
          type="number"
          value={tradingFeeRate}
          onChange={(e) => setTradingFeeRate(e.target.value)}
          className="w-24 border border-gray-300 rounded-md px-3 py-1.5 text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          placeholder="0"
          step="0.01"
          min="0"
          max="100"
        />
        <span className="text-sm text-gray-500">%</span>
        <span className="text-xs text-gray-400 ml-2">
          (매수/매도 시 각각 적용)
        </span>
      </div>

      {/* 배당 원천징수 */}
      <label className="flex items-center space-x-3 cursor-pointer group">
        <input
          type="checkbox"
          checked={dividendTax}
          onChange={(e) => setDividendTax(e.target.checked)}
          className="w-4 h-4 text-indigo-600 border-gray-300 rounded focus:ring-indigo-500"
        />
        <TrendingDown className="w-4 h-4 text-gray-500 group-hover:text-indigo-600" />
        <span className="text-sm text-gray-700 group-hover:text-indigo-600">
          배당 원천징수 (15.4%)
        </span>
        <span className="text-xs text-gray-400">
          미국 주식 배당 세금 공제
        </span>
      </label>

      {/* 배당금 재투자 */}
      <label className="flex items-center space-x-3 cursor-pointer group">
        <input
          type="checkbox"
          checked={reinvestDividends}
          onChange={(e) => setReinvestDividends(e.target.checked)}
          className="w-4 h-4 text-indigo-600 border-gray-300 rounded focus:ring-indigo-500"
        />
        <Repeat className="w-4 h-4 text-gray-500 group-hover:text-indigo-600" />
        <span className="text-sm text-gray-700 group-hover:text-indigo-600">
          배당금 재투자
        </span>
        <span className="text-xs text-gray-400">
          받은 배당금으로 자동 매수
        </span>
      </label>
    </div>
  );
};
