import type { FC } from 'react';
import type { StrategyFieldsProps } from '../types';
import { WindowsField } from '../../settings/WindowsField';
import { windowIssues } from '../../settings/checks';

// 이동평균 칩 칸: 짧은 · 긴 기간(원금은 전략마다 원금)
export const MaCrossFields: FC<StrategyFieldsProps> = ({ name, params, onChange }) => {
  const short = params.shortWindow ?? '';
  const long = params.longWindow ?? '';
  return (
    <WindowsField
      label={`${name} 기간`}
      short={short}
      onShortChange={(value) => onChange('shortWindow', value)}
      long={long}
      onLongChange={(value) => onChange('longWindow', value)}
      messages={windowIssues(short, long)}
    />
  );
};
