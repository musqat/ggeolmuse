import React, { useId } from 'react';
import type { PortfolioMethod } from '@services/api';
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

interface PortfolioFormProps {
  assets: PortfolioAssetsFieldProps;
  period: PeriodFieldProps;
  method: PortfolioMethod;
  onMethodChange: (method: PortfolioMethod) => void;
  // 적립식일 때만
  cadence?: CadenceFieldProps;
  first?: FirstDateFieldProps;
}

// 포트폴리오: 종목 줄마다 금액(또는 총액 × 비율)으로 한 번에 사거나 주기마다 삼, 현금 하나로 평가
export const PortfolioForm: React.FC<PortfolioFormProps> = ({ assets, period, method, onMethodChange, cadence, first }) => {
  const methodId = useId();
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
      </div>
    </div>
  );
};
