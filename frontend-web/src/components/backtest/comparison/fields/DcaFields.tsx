import type { FC } from 'react';
import type { StrategyFieldsProps } from '../types';
import { NumberInput } from '../../../common/NumberInput';
import { DcaCadenceFields, FormField } from '../../shared/StrategyFields';
import { toCadence } from '../../shared/dcaSchedule';
import { PrincipalNote } from './PrincipalNote';

const INPUT_CLASS = 'w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand';

// 적립식 칩 칸: 회당 금액 · 투자 주기 · 첫 매수일
export const DcaFields: FC<StrategyFieldsProps> = ({ params, onChange, common }) => (
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
    <DcaCadenceFields
      cadence={toCadence(params.cadence)}
      onCadenceChange={(cadence) => onChange('cadence', cadence)}
      months={params.months ?? ''}
      onMonthsChange={(months) => onChange('months', months)}
      firstPurchaseDate={params.firstPurchaseDate ?? ''}
      onFirstPurchaseDateChange={(date) => onChange('firstPurchaseDate', date)}
      startDate={common.startDate}
      endDate={common.endDate}
    />
  </>
);
