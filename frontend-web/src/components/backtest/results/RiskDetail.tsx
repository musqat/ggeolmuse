import React from 'react';
import type { PerformanceMetrics } from '../../../services/api';
import { percentOrDash, ratioOrDash } from './resultValues';
import { DetailRow, DetailSection } from './DetailSection';

interface RiskDetailProps {
  metrics?: PerformanceMetrics | null;
  other?: PerformanceMetrics | null;
  otherLabel: string;
  timeWeighted?: boolean;
}

// 상세 칸 "위험": 변동성 · 샤프 비율과 쉬운 설명
export const RiskDetail: React.FC<RiskDetailProps> = ({ metrics, other, otherLabel, timeWeighted }) => (
  <DetailSection title="위험">
    <DetailRow label="변동성(연)" value={percentOrDash(metrics?.volatility)} />
    <p className="text-xs text-tx-2 pb-2">
      날마다 값이 출렁인 정도를 1년으로 늘린 값 · {otherLabel} 기준 {percentOrDash(other?.volatility)}
    </p>
    <DetailRow label="샤프 비율" value={ratioOrDash(metrics?.sharpe)} />
    <p className="text-xs text-tx-2 pb-2">
      출렁임 대비 수익, 1 넘으면 좋은 편(무위험 연 3%) · {otherLabel} 기준 {ratioOrDash(other?.sharpe)}
    </p>
    {timeWeighted && <p className="text-xs text-tx-2">입금을 뺀 시간가중 수익률로 셈</p>}
  </DetailSection>
);
