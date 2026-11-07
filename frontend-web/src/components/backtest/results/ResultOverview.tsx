import React from 'react';
import type { StrategyResponse } from '../../../services/api';
import { CurrencyToggle, type MetricCurrency } from './CurrencyToggle';
import { SummaryCards } from './SummaryCards';
import { AssetChart } from './AssetChart';
import type { Deposit } from './assetSeries';
import { pickMetrics } from './resultValues';

type OverviewValues = Pick<
  StrategyResponse,
  | 'totalAssetKrw'
  | 'currentValueKrw'
  | 'totalReturnKrw'
  | 'totalReturnPercent'
  | 'metricsKrw'
  | 'metricsUsd'
  | 'dailyValues'
>;

interface ResultOverviewProps {
  result: OverviewValues;
  investedLabel: string;
  invested: number | null | undefined;
  // 자산 추이의 누적 투자금 · 매수 점
  deposits: Deposit[];
  currency: MetricCurrency;
  onCurrencyChange: (currency: MetricCurrency) => void;
  returnRateTestId?: string;
  // 입금을 뺀 시간가중 지표(적립식 · 조건부)
  timeWeighted?: boolean;
}

// 결과 위쪽: 지표 기준 통화 · 카드 한 줄 · 자산 추이
export const ResultOverview: React.FC<ResultOverviewProps> = ({
  result,
  investedLabel,
  invested,
  deposits,
  currency,
  onCurrencyChange,
  returnRateTestId,
  timeWeighted,
}) => {
  const { metrics, other, otherLabel, hasMetrics } = pickMetrics(result, currency);

  return (
    <div className="space-y-4">
      {hasMetrics && (
        <div className="flex items-center justify-end gap-2">
          <span className="text-sm text-tx-2">지표 기준</span>
          <CurrencyToggle value={currency} onChange={onCurrencyChange} />
        </div>
      )}
      <SummaryCards
        result={result}
        investedLabel={investedLabel}
        invested={invested}
        metrics={metrics}
        other={other}
        otherLabel={otherLabel}
        noMetrics={!hasMetrics}
        totalInKrw={currency === 'USD'}
        timeWeighted={timeWeighted}
        returnRateTestId={returnRateTestId}
      />
      <AssetChart dailyValues={result.dailyValues} deposits={deposits} currency={hasMetrics ? currency : 'KRW'} />
    </div>
  );
};
