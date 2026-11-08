import React from 'react';
import { NumberInput } from '../../common/NumberInput';
import type { BacktestOptionsState } from '../shared/backtestOptions';
import type { AmountUnitState } from '../shared/amountUnit';
import { AmountOrSharesField } from '../shared/AmountOrSharesField';
import {
  ConditionFields,
  FormField,
  PeriodFields,
  SellFields,
  StrategyOptions,
  SymbolField,
} from '../shared/StrategyFields';
import { formatNumber } from '../../../utils/format';
import type { PriceDirection } from '@services/api';
import { hasSellRates } from '../shared/sellRates';

interface ConditionalStrategyFormProps {
  symbol: string;
  setSymbol: (symbol: string) => void;
  conditionalStartDate: string;
  setConditionalStartDate: (date: string) => void;
  conditionalEndDate: string;
  setConditionalEndDate: (date: string) => void;
  amountPerPurchase: string;
  setAmountPerPurchase: (amount: string) => void;
  amountUnit: AmountUnitState;
  totalInvestment: string;
  setTotalInvestment: (amount: string) => void;
  maxPurchases: string;
  setMaxPurchases: (count: string) => void;
  direction: PriceDirection;
  setDirection: (direction: PriceDirection) => void;
  dropPercentage: string;
  setDropPercentage: (percentage: string) => void;
  takeProfit: string;
  setTakeProfit: (percentage: string) => void;
  stopLoss: string;
  setStopLoss: (percentage: string) => void;
  options: BacktestOptionsState;
  supportedSymbols: string[];
}

const AMOUNT_INPUT_CLASS =
  'w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand';

// 정한 비율만큼 떨어지거나 오른 날마다 회당 금액 · 회당 주식 수만큼 사고, 켜면 익절 · 손절로 전량 파는 전략
export const ConditionalStrategyForm: React.FC<ConditionalStrategyFormProps> = ({
  symbol,
  setSymbol,
  conditionalStartDate,
  setConditionalStartDate,
  conditionalEndDate,
  setConditionalEndDate,
  amountPerPurchase,
  setAmountPerPurchase,
  amountUnit,
  totalInvestment,
  setTotalInvestment,
  maxPurchases,
  setMaxPurchases,
  direction,
  setDirection,
  dropPercentage,
  setDropPercentage,
  takeProfit,
  setTakeProfit,
  stopLoss,
  setStopLoss,
  options,
  supportedSymbols,
}) => {
  const perPurchase = parseFloat(amountPerPurchase);
  const budget = parseFloat(totalInvestment);
  const count = parseInt(maxPurchases);
  // 회당 금액으로 센 횟수 · 투자 한도(주식 수 단위면 안 셈)
  const amountOk = amountUnit.unit === 'AMOUNT' && perPurchase > 0;

  return (
    <div className="space-y-4">
      {/* 기본 설정 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
        <SymbolField value={symbol} onChange={setSymbol} supportedSymbols={supportedSymbols} />

        <PeriodFields
          start={conditionalStartDate}
          end={conditionalEndDate}
          onStartChange={setConditionalStartDate}
          onEndChange={setConditionalEndDate}
        />

        {/* 한도: 예산 · 최대 횟수 가운데 먼저 닿는 쪽, 둘 다 비우면 제한 없음 */}
        <AmountOrSharesField
          amountLabel="회당 금액 (₩)"
          sharesLabel="회당 주식 수"
          amount={amountPerPurchase}
          onAmountChange={setAmountPerPurchase}
          amountPlaceholder="500,000"
          amountHelp="조건이 맞는 날마다 넣는 금액"
          amountUnit={amountUnit}
          symbol={symbol}
          startDate={conditionalStartDate}
          options={options}
        />

        <FormField label="예산 (₩)">
          <NumberInput
            value={totalInvestment}
            onChange={setTotalInvestment}
            placeholder="제한 없음"
            className={AMOUNT_INPUT_CLASS}
          />
          {amountOk && budget > 0 && (
            <p className="text-xs text-tx-3 mt-1">예산으로 최대 {Math.floor(budget / perPurchase)}회</p>
          )}
        </FormField>

        <FormField label="최대 횟수">
          <input
            type="number"
            aria-label="최대 횟수"
            value={maxPurchases}
            onChange={(e) => setMaxPurchases(e.target.value)}
            placeholder={budget > 0 ? '예산 한도까지' : '제한 없음'}
            step="1"
            min="1"
            className={AMOUNT_INPUT_CLASS}
          />
          {amountOk && count > 0 && (
            <p className="text-xs text-tx-2 mt-1">
              많아야 ₩{formatNumber(budget > 0 ? Math.min(perPurchase * count, budget) : perPurchase * count)} 투자
            </p>
          )}
        </FormField>

        <ConditionFields
          direction={direction}
          onDirectionChange={setDirection}
          value={dropPercentage}
          onChange={setDropPercentage}
        />

        <SellFields
          takeProfit={takeProfit}
          onTakeProfitChange={setTakeProfit}
          stopLoss={stopLoss}
          onStopLossChange={setStopLoss}
        />
      </div>

      <StrategyOptions
        options={options}
        startDate={conditionalStartDate}
        purchaseLabel="시작일 환율"
        currentLabel="현재 환율"
        reinvestLocked={hasSellRates(takeProfit, stopLoss)}
      />
    </div>
  );
};
