import type { FC } from 'react';
import type { StrategyFieldsProps } from '../types';
import { dayOfMonth, resolveFirstPurchase, toCadence } from '../../shared/dcaSchedule';
import { AmountField } from '../../settings/AmountField';
import { CadenceField } from '../../settings/CadenceField';
import { FirstDateField } from '../../settings/FirstDateField';
import { chipAmountIssue } from './chipChecks';
import { firstIssue, monthsIssue } from '../../settings/checks';

// 적립식 칩 칸: 회당 금액 · 주기 · 첫 매수일(고르면 고를 때의 시작일도 남김)
export const DcaFields: FC<StrategyFieldsProps> = ({ name, params, onChange, common }) => {
  const cadence = toCadence(params.cadence);
  const picked = params.firstPurchaseDate ?? '';
  const first = resolveFirstPurchase(common.startDate, cadence, { date: picked, start: params.firstPickedStart ?? '' }) || common.startDate;
  const months = params.months ?? '';

  return (
    <>
      <AmountField
        label={`${name} 회당 금액`}
        amount={params.amountPerPurchase ?? ''}
        onAmountChange={(value) => onChange('amountPerPurchase', value)}
        message={chipAmountIssue(params.amountPerPurchase ?? '', common.investment)}
      />
      <CadenceField
        label={`${name} 주기`}
        cadence={cadence}
        onCadenceChange={(value) => onChange('cadence', value)}
        months={months}
        onMonthsChange={(value) => onChange('months', value)}
        message={monthsIssue(cadence, months)}
      />
      <FirstDateField
        value={first}
        picked={!!picked}
        min={common.startDate}
        max={common.endDate}
        onPick={(date) => {
          onChange('firstPurchaseDate', date);
          onChange('firstPickedStart', common.startDate);
        }}
        onFollow={() => onChange('firstPurchaseDate', '')}
        message={firstIssue({ first, end: common.endDate, cadence, monthDay: dayOfMonth(picked || first) })}
      />
    </>
  );
};
