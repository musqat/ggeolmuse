import { useId } from 'react';
import type { ExecutionTiming } from '@services/api';
import { EXECUTION_OPTIONS } from '../shared/executionTiming';
import { RadioGroup } from './radio';
import { SettingField } from './SettingField';
import { PILL } from './styles';

export interface ExecutionTimingFieldProps {
  value: ExecutionTiming;
  onChange: (value: ExecutionTiming) => void;
}

// 체결 시점 칸: 신호 난 날 종가 · 다음 거래일 시가 칩 가운데 하나
export const ExecutionTimingField = ({ value, onChange }: ExecutionTimingFieldProps) => {
  const labelId = useId();
  return (
    <SettingField label="체결 시점" labelId={labelId}>
      <RadioGroup
        labelledBy={labelId}
        options={EXECUTION_OPTIONS}
        value={value}
        onChange={onChange}
        className="flex flex-wrap items-center gap-1.5"
        optionClassName={PILL}
      />
    </SettingField>
  );
};
