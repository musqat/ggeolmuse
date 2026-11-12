import React, { useId } from 'react';
import type { PortfolioMethod, PortfolioRebalance } from '@services/api';
import { PeriodField, type PeriodFieldProps } from '../../settings/PeriodField';
import { CadenceField, type CadenceFieldProps } from '../../settings/CadenceField';
import { FirstDateField, type FirstDateFieldProps } from '../../settings/FirstDateField';
import { SettingField } from '../../settings/SettingField';
import { RadioGroup } from '../../settings/radio';
import { PILL } from '../../settings/styles';
import { FIELD_GRID } from '../../forms/formLayout';
import { PortfolioAssetsField, type PortfolioAssetsFieldProps } from './PortfolioAssetsField';

const METHODS: { value: PortfolioMethod; label: string }[] = [
  { value: 'LUMP_SUM', label: '한 번에 사기' },
  { value: 'DCA', label: '적립식' },
];

const REBALANCES: { value: PortfolioRebalance; label: string }[] = [
  { value: 'NONE', label: '없음' },
  { value: 'YEARLY', label: '매년' },
  { value: 'QUARTERLY', label: '분기마다' },
  { value: 'MONTHLY', label: '매달' },
];

interface PortfolioFormProps {
  assets: PortfolioAssetsFieldProps;
  period: PeriodFieldProps;
  method: PortfolioMethod;
  onMethodChange: (method: PortfolioMethod) => void;
  // 적립식 줄이 있을 때만
  cadence?: CadenceFieldProps;
  first?: FirstDateFieldProps;
  rebalance: PortfolioRebalance;
  onRebalanceChange: (rebalance: PortfolioRebalance) => void;
}

// 포트폴리오: 종목 줄마다 금액(또는 총액 × 비율)을 줄 전략(비우면 방식)으로 사고팖, 현금 하나로 평가하고 고른 주기마다 처음 비중으로 리밸런싱
export const PortfolioForm: React.FC<PortfolioFormProps> = ({
  assets,
  period,
  method,
  onMethodChange,
  cadence,
  first,
  rebalance,
  onRebalanceChange,
}) => {
  const methodId = useId();
  const rebalanceId = useId();
  return (
    <div className="space-y-4">
      <PortfolioAssetsField {...assets} />
      <div className={FIELD_GRID}>
        <PeriodField {...period} />
        <SettingField label="방식" labelId={methodId}>
          <RadioGroup
            labelledBy={methodId}
            options={METHODS}
            value={method}
            onChange={onMethodChange}
            className="flex flex-wrap items-center gap-1.5"
            optionClassName={PILL}
          />
        </SettingField>
        {cadence && <CadenceField {...cadence} />}
        {first && <FirstDateField {...first} />}
        <SettingField label="리밸런싱" labelId={rebalanceId}>
          <RadioGroup
            labelledBy={rebalanceId}
            options={REBALANCES}
            value={rebalance}
            onChange={onRebalanceChange}
            className="flex flex-wrap items-center gap-1.5"
            optionClassName={PILL}
          />
        </SettingField>
      </div>
    </div>
  );
};
