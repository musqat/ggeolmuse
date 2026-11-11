import type { FC } from 'react';
import type { StrategyFieldsProps } from '../types';
import { BandField } from '../../settings/BandField';
import { bandIssues } from '../../settings/checks';

// 밴드 칩 칸: 평균 기간 · 위 · 아래 비율(원금은 전략마다 원금)
export const MaBandFields: FC<StrategyFieldsProps> = ({ name, params, onChange }) => {
  const window = params.bandWindow ?? '';
  const upper = params.upperPercent ?? '';
  const lower = params.lowerPercent ?? '';
  return (
    <BandField
      label={`${name} 설정`}
      window={window}
      onWindowChange={(value) => onChange('bandWindow', value)}
      upper={upper}
      onUpperChange={(value) => onChange('upperPercent', value)}
      lower={lower}
      onLowerChange={(value) => onChange('lowerPercent', value)}
      messages={bandIssues(window, upper, lower)}
    />
  );
};
