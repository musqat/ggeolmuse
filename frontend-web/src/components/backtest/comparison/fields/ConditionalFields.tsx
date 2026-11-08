import type { FC } from 'react';
import type { StrategyFieldsProps } from '../types';
import { toDirection } from '../../shared/priceDirection';
import { AmountField } from '../../settings/AmountField';
import { ConditionField } from '../../settings/ConditionField';
import { SellField } from '../../settings/SellField';
import { MaxField } from '../../settings/MaxField';
import { maxIssue, percentIssue, sellIssue } from '../../settings/checks';
import { chipAmountIssue } from './chipChecks';

// 조건부 매수 칩 칸: 회당 금액 · 조건 · 매도 조건 · 최대 횟수(비우면 원금 한도까지)
export const ConditionalFields: FC<StrategyFieldsProps> = ({ name, params, onChange, common }) => {
  const takeProfit = params.takeProfit ?? '';
  const stopLoss = params.stopLoss ?? '';
  const max = params.maxPurchases ?? '';

  return (
    <>
      <AmountField
        label={`${name} 회당 금액`}
        amount={params.amountPerPurchase ?? ''}
        onAmountChange={(value) => onChange('amountPerPurchase', value)}
        message={chipAmountIssue(params.amountPerPurchase ?? '', common.investment)}
      />
      <ConditionField
        label={`${name} 조건`}
        direction={toDirection(params.direction)}
        onDirectionChange={(value) => onChange('direction', value)}
        percent={params.dropPercentage ?? ''}
        onPercentChange={(value) => onChange('dropPercentage', value)}
        message={percentIssue(params.dropPercentage ?? '')}
      />
      <SellField
        takeProfit={takeProfit}
        onTakeProfitChange={(value) => onChange('takeProfit', value)}
        stopLoss={stopLoss}
        onStopLossChange={(value) => onChange('stopLoss', value)}
        message={sellIssue(takeProfit, stopLoss)}
      />
      <MaxField
        value={max}
        onChange={(value) => onChange('maxPurchases', value)}
        placeholder="원금 한도까지"
        message={maxIssue(max)}
      />
    </>
  );
};
