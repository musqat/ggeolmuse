import React from 'react';
import type { BacktestOptionsState } from './backtestOptions';

// 수수료 · 배당세 · 배당 재투자(익절 · 손절을 켜면 재투자는 끄고 잠금)
export const DividendFeeOptions: React.FC<{ options: BacktestOptionsState; reinvestLocked?: boolean; reinvestNote?: string }> = ({
  options,
  reinvestLocked = false,
  reinvestNote,
}) => {
  const { tradingFeeRate, setTradingFeeRate, dividendTax, setDividendTax, reinvestDividends, setReinvestDividends } = options;
  return (
    <div className="border-t border-line pt-4 mt-4 space-y-3">
      {/* 거래 수수료율 */}
      <div className="flex items-center space-x-3">
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
        <span className="text-sm text-tx-1 group-hover:text-brand">          배당 원천징수 (15%)
        </span>
        <span className="text-xs text-tx-3">
          미국 주식 배당 세금 공제
        </span>
      </label>

      {/* 배당금 재투자 */}
      <label className={`flex items-center space-x-3 group ${reinvestLocked ? 'cursor-not-allowed opacity-60' : 'cursor-pointer'}`}>
        <input
          type="checkbox"
          checked={reinvestDividends && !reinvestLocked}
          disabled={reinvestLocked}
          onChange={(e) => setReinvestDividends(e.target.checked)}
          className="w-4 h-4 text-brand border-line-strong rounded focus:ring-brand"
        />
        <span className="text-sm text-tx-1 group-hover:text-brand">
          배당금 재투자
        </span>
        <span className="text-xs text-tx-3">
          {reinvestLocked ? '익절 · 손절과 함께 쓸 수 없음' : (reinvestNote ?? '받은 배당금으로 자동 매수')}
        </span>
      </label>
    </div>
  );
};
