import React from 'react';
import { TrendingUp, TrendingDown, DollarSign, Activity } from 'lucide-react';
import type { StrategyResponse } from '../../../services/api';
import {
  formatKrw,
  formatPercent,
  formatSigned,
  gainLossBoxClass,
  gainLossClass,
  gainLossTone,
} from '../../../utils/gainLoss';
import { StatCard } from './StatCard';

type SummaryValues = Pick<
  StrategyResponse,
  'totalAssetKrw' | 'currentValueKrw' | 'remainingCashKrw' | 'totalReturnKrw' | 'totalReturnPercent'
>;

interface SummaryCardsProps {
  result: SummaryValues;
  // 첫 칸. 단순은 초기 투자금, 적립식 · 조건부는 총 투자금
  investedLabel: string;
  invested: number | null | undefined;
  returnRateTestId?: string;
}

// 결과 위쪽 카드 넷: 투자금 · 평가 가치 · 총 수익 · 수익률
export const SummaryCards: React.FC<SummaryCardsProps> = ({ result, investedLabel, invested, returnRateTestId }) => (
  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
    <StatCard
      label={investedLabel}
      value={<>₩{invested?.toLocaleString()}</>}
      icon={DollarSign}
    />

    <StatCard
      label="평가 가치"
      value={<>₩{result.totalAssetKrw?.toLocaleString()}</>}
      sub={
        <>
          주식: ₩{result.currentValueKrw?.toLocaleString()}, 현금: ₩
          {result.remainingCashKrw?.toLocaleString()}
        </>
      }
      icon={TrendingUp}
    />

    <StatCard
      label="총 수익"
      value={formatSigned(result.totalReturnKrw, formatKrw)}
      valueClassName={gainLossClass(result.totalReturnKrw)}
      icon={gainLossTone(result.totalReturnKrw) === "loss" ? TrendingDown : TrendingUp}
      iconBoxClassName={gainLossBoxClass(result.totalReturnKrw)}
      iconClassName={gainLossClass(result.totalReturnKrw)}
    />

    <StatCard
      label="수익률"
      value={formatSigned(result.totalReturnPercent, formatPercent)}
      valueClassName={gainLossClass(result.totalReturnPercent)}
      icon={Activity}
      iconBoxClassName={gainLossBoxClass(result.totalReturnPercent)}
      iconClassName={gainLossClass(result.totalReturnPercent)}
      testId={returnRateTestId}
    />
  </div>
);
