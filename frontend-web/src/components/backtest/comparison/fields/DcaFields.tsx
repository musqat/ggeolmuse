import type { FC } from 'react';
import type { StrategyFieldsProps } from '../types';
import { DcaScheduleFields, FormField } from '../../shared/StrategyFields';

export const DcaFields: FC<StrategyFieldsProps> = ({ params, onChange }) => (
  <>
    <FormField label="월 투자금 (₩)">
      <input
        type="number"
        value={params.monthlyAmount || "100000"}
        onChange={(e) => onChange("monthlyAmount", e.target.value)}
        placeholder="100000"
        step="10000"
        min="1"
        className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
      />
    </FormField>
    <DcaScheduleFields
      purchaseDay={params.purchaseDay || "15"}
      interval={params.investmentInterval || "1"}
      onPurchaseDayChange={(day) => onChange("purchaseDay", day)}
      onIntervalChange={(interval) => onChange("investmentInterval", interval)}
    />
  </>
);
