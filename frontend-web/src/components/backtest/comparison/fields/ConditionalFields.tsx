import type { FC } from 'react';
import type { StrategyFieldsProps } from '../types';
import { NumberInput } from '../../../common/NumberInput';
import { ConditionFields, FormField, SellFields } from '../../shared/StrategyFields';
import { toDirection } from '../../shared/priceDirection';
import { PrincipalNote } from './PrincipalNote';

const INPUT_CLASS = 'w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand';

// 조건부 매수 칩 칸: 회당 금액 · 최대 횟수 · 조건 · 익절 · 손절
export const ConditionalFields: FC<StrategyFieldsProps> = ({ params, onChange, common }) => (
  <>
    <PrincipalNote investment={common.investment} />
    <FormField label="회당 금액 (₩)">
      <NumberInput
        value={params.amountPerPurchase ?? ''}
        onChange={(value) => onChange('amountPerPurchase', value)}
        placeholder="100,000"
        className={INPUT_CLASS}
      />
    </FormField>
    <FormField label="최대 횟수">
      <input
        type="number"
        aria-label="최대 횟수"
        value={params.maxPurchases ?? ''}
        onChange={(e) => onChange('maxPurchases', e.target.value)}
        placeholder="원금 한도까지"
        step="1"
        min="1"
        className={INPUT_CLASS}
      />
    </FormField>
    <ConditionFields
      direction={toDirection(params.direction)}
      onDirectionChange={(direction) => onChange('direction', direction)}
      value={params.dropPercentage ?? ''}
      onChange={(value) => onChange('dropPercentage', value)}
    />
    <SellFields
      takeProfit={params.takeProfit ?? ''}
      onTakeProfitChange={(value) => onChange('takeProfit', value)}
      stopLoss={params.stopLoss ?? ''}
      onStopLossChange={(value) => onChange('stopLoss', value)}
    />
  </>
);
