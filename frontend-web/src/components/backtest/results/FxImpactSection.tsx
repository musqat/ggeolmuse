import React from 'react';
import { Repeat } from 'lucide-react';
import type { StrategyResponse } from '../../../services/api';
import { formatFxRate, formatPercent, formatSigned, gainLossClass, hasValue } from '../../../utils/gainLoss';
import { DetailRow, DetailSection } from './DetailSection';

interface FxImpactSectionProps {
  result: Pick<StrategyResponse, 'currentFxRate' | 'fxReturn' | 'fxReturnPercent'>;
  // 첫 줄 라벨(단순은 시작일 환율, 적립식 · 조건부는 평균 환율)
  startLabel: string;
  startRate: number | null | undefined;
}

// 상세 칸 "환율 영향": 시작 환율 · 평가일 환율 · 변동 · 환차익률
export const FxImpactSection: React.FC<FxImpactSectionProps> = ({ result, startLabel, startRate }) => (
  <DetailSection title="환율 영향" icon={Repeat} borderClassName="border-brand/25">
    <DetailRow
      label={startLabel}
      value={hasValue(startRate) ? formatFxRate(startRate) : '-'}
      dividerClassName="border-line"
    />
    <DetailRow
      label="평가일 환율"
      value={hasValue(result.currentFxRate) ? formatFxRate(result.currentFxRate) : '-'}
      dividerClassName="border-line"
    />
    <DetailRow
      label="환율 변동"
      value={formatSigned(result.fxReturn, formatFxRate)}
      valueClassName={`font-medium ${gainLossClass(result.fxReturn)}`}
      dividerClassName="border-line"
    />
    <DetailRow
      label="환차익률"
      value={formatSigned(result.fxReturnPercent, formatPercent)}
      valueClassName={`font-bold ${gainLossClass(result.fxReturnPercent)}`}
      last
    />
  </DetailSection>
);
