import React from 'react';
import type { StrategyResponse } from '../../../services/api';
import { CurrencyToggle, type MetricCurrency } from './CurrencyToggle';
import { SummaryCards } from './SummaryCards';
import { AssetChart } from './AssetChart';
import { ChartTabs } from './ChartTabs';
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
  // 총 수익률 이름 뒤 기준
  returnBasis?: string;
  // 자산 추이의 누적 투자금 · 매수 점
  deposits: Deposit[];
  currency: MetricCurrency;
  onCurrencyChange: (currency: MetricCurrency) => void;
  returnRateTestId?: string;
  // 입금을 뺀 시간가중 지표(적립식 · 조건부)
  timeWeighted?: boolean;
  // 카드 줄 끝에 더할 카드
  extraCard?: React.ReactNode;
  // 있으면 자산 추이 뒤에 탭으로 묶을 차트
  chartTabs?: { key: string; label: string; render: () => React.ReactNode }[];
}

// 결과 위쪽: 지표 기준 통화 · 카드 한 줄 · 자산 추이(차트 탭이 있으면 탭으로 묶음)
export const ResultOverview: React.FC<ResultOverviewProps> = ({
  result,
  investedLabel,
  invested,
  returnBasis,
  deposits,
  currency,
  onCurrencyChange,
  returnRateTestId,
  timeWeighted,
  extraCard,
  chartTabs,
}) => {
  const { metrics, other, otherLabel, hasMetrics } = pickMetrics(result, currency);
  const assetCurrency = hasMetrics ? currency : 'KRW';

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
        returnBasis={returnBasis}
        metrics={metrics}
        other={other}
        otherLabel={otherLabel}
        noMetrics={!hasMetrics}
        totalInKrw={currency === 'USD'}
        timeWeighted={timeWeighted}
        returnRateTestId={returnRateTestId}
        extra={extraCard}
      />
      {chartTabs?.length ? (
        <ChartTabs
          label="결과 차트"
          resetKey={result}
          tabs={[
            {
              key: 'asset',
              label: '자산 추이',
              render: () => <AssetChart dailyValues={result.dailyValues} deposits={deposits} currency={assetCurrency} plain />,
            },
            ...chartTabs,
          ]}
        />
      ) : (
        <AssetChart dailyValues={result.dailyValues} deposits={deposits} currency={assetCurrency} />
      )}
    </div>
  );
};
