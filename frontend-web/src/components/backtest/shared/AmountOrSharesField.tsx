import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { NumberInput } from '../../common/NumberInput';
import { formatKrw } from '../../../utils/format';
import type { BacktestOptions } from './backtestOptions';
import { estimateRoundCost, fetchStartQuote, parseShares, type AmountUnit, type AmountUnitState } from './amountUnit';

const INPUT_CLASS = 'w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand';

const UNIT_OPTIONS: { value: AmountUnit; label: string }[] = [
  { value: 'AMOUNT', label: '금액' },
  { value: 'SHARES', label: '주식 수' },
];

interface SharesEstimateProps {
  symbol: string;
  startDate: string;
  shares: string;
  options: BacktestOptions;
}

// 주식 수로 살 금액 추정(시작일 종가 · 그 날짜 환율, 수동 환율이면 매수 환율)
const SharesEstimate: React.FC<SharesEstimateProps> = ({ symbol, startDate, shares, options }) => {
  const { data: quote } = useQuery({
    queryKey: ['backtest', 'start-quote', symbol, startDate],
    enabled: Boolean(symbol && startDate),
    queryFn: () => fetchStartQuote(symbol, startDate),
  });
  const count = parseShares(shares);
  if (!quote || count === undefined) return null;

  const manualFx = parseFloat(options.manualPurchaseFxRate);
  const fxRate = options.fxMode === 'manual' && manualFx > 0 ? manualFx : quote.fxRate;
  const won = estimateRoundCost(quote, count, parseFloat(options.tradingFeeRate) || 0, fxRate);
  return <p className="text-xs text-tx-2 mt-1">≈ {formatKrw(won)}(시작일 값 기준)</p>;
};

interface AmountOrSharesFieldProps {
  amountLabel: string;
  sharesLabel: string;
  amount: string;
  onAmountChange: (amount: string) => void;
  amountPlaceholder: string;
  amountHelp: string;
  amountUnit: AmountUnitState;
  symbol: string;
  startDate: string;
  options: BacktestOptions;
}

// 금액 · 주식 수 고르기와 그 단위의 입력 칸
export const AmountOrSharesField: React.FC<AmountOrSharesFieldProps> = ({
  amountLabel,
  sharesLabel,
  amount,
  onAmountChange,
  amountPlaceholder,
  amountHelp,
  amountUnit,
  symbol,
  startDate,
  options,
}) => {
  const { unit, setUnit, shares, setShares } = amountUnit;

  return (
    <div>
      <div className="flex items-center justify-between gap-1 mb-2">
        <label className="block text-sm font-medium text-tx-1 whitespace-nowrap">{unit === 'SHARES' ? sharesLabel : amountLabel}</label>
        {/* 단위 단추가 이름 줄 높이를 늘리지 않게 위아래 음수 여백 */}
        <div role="group" aria-label="단위" className="-my-1 inline-flex rounded-btn border border-line-strong p-0.5">
          {UNIT_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              aria-pressed={unit === option.value}
              onClick={() => setUnit(option.value)}
              className={`px-1.5 py-0.5 text-xs rounded-btn whitespace-nowrap ${
                unit === option.value ? 'bg-elevated text-tx-1 font-medium' : 'text-tx-2 hover:text-tx-1'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>
      {unit === 'SHARES' ? (
        <>
          <input
            type="number"
            aria-label={sharesLabel}
            value={shares}
            onChange={(e) => setShares(e.target.value)}
            placeholder="1"
            step="1"
            min="1"
            className={INPUT_CLASS}
          />
          <SharesEstimate symbol={symbol} startDate={startDate} shares={shares} options={options} />
        </>
      ) : (
        <>
          <NumberInput value={amount} onChange={onAmountChange} placeholder={amountPlaceholder} className={INPUT_CLASS} />
          <p className="text-xs text-tx-3 mt-1">{amountHelp}</p>
        </>
      )}
    </div>
  );
};
