import React, { useMemo, useState } from 'react';
import { CompareStrategiesChart } from '@components/charts/backtest/CompareStrategiesChart';
import { CHART_COLORS } from '@components/charts/common/constants';
import type { ComparisonItem, ComparisonResponse } from '../../../services/api';
import { STRATEGY_NAMES } from '../shared/backtestDisplay';
import { gainLossClass, hasValue } from '../../../utils/gainLoss';
import { formatFxRate, formatKrw, formatPercent, formatSigned, formatNumber } from '../../../utils/format';
import { CurrencyToggle, type MetricCurrency } from './CurrencyToggle';
import { DetailRow, DetailSection } from './DetailSection';
import { ResultFold } from './ResultFold';
import { RiskDetail } from './RiskDetail';
import { purchaseCount } from './resultValues';
import { simpleDeposits, strategyDeposits } from './assetSeries';
import { CompareTable, type CompareColumn } from './compare/CompareTable';
import { ReturnLinesChart } from './compare/ReturnLinesChart';
import { bestKeys, byTotalReturn, metricsOf, returnPoints, type CompareRow } from './compare/compareRows';
import { DrawdownCell } from './compare/DrawdownCell';

interface StrategyComparisonResultProps {
  result: ComparisonResponse;
}

const toRow = (item: ComparisonItem, index: number): CompareRow => {
  const detail = item.additionalData ?? {};
  return {
    key: item.name,
    name: STRATEGY_NAMES[item.name] || item.name,
    color: CHART_COLORS[index % CHART_COLORS.length],
    item,
    totalReturnPercent: item.totalReturnPercent ?? null,
    valueKrw: detail.totalAssetKrw ?? item.currentValueKrw ?? null,
    metricsKrw: detail.metricsKrw,
    metricsUsd: detail.metricsUsd,
    deposits: detail.transactions
      ? strategyDeposits(detail.transactions)
      : simpleDeposits(detail.purchaseDate, detail.investmentAmount ?? item.totalInvested, detail.purchaseFxRate),
  };
};

// 전략 비교 결과: 요약 한 줄 · 전략별 표 · 수익률 추이, 주가 추이는 접어 둠
export const StrategyComparisonResult: React.FC<StrategyComparisonResultProps> = ({ result }) => {
  const [currency, setCurrency] = useState<MetricCurrency>('KRW');
  const [expanded, setExpanded] = useState<string | null>(null);
  const rows = useMemo(() => (result.items ?? []).map(toRow).sort(byTotalReturn), [result.items]);
  const hasMetrics = rows.some((row) => row.metricsKrw != null || row.metricsUsd != null);
  const otherLabel = currency === 'KRW' ? '달러' : '원화';
  const symbol = result.items?.[0]?.additionalData?.symbol ?? result.items?.[0]?.code;
  const best = result.bestPerformer;

  const columns: CompareColumn[] = [
    { label: '투자금', cell: (row) => <>₩{formatNumber(row.item.totalInvested)}</> },
    {
      label: '총 수익률',
      cell: (row) => (
        <span className={`font-semibold ${gainLossClass(row.totalReturnPercent)}`}>
          {formatSigned(row.totalReturnPercent, formatPercent)}
        </span>
      ),
      best: bestKeys(rows, (row) => row.totalReturnPercent),
    },
    {
      label: '연평균',
      cell: (row) => {
        const cagr = metricsOf(row, currency)?.cagr;
        return hasValue(cagr) ? <span className={gainLossClass(cagr)}>{formatSigned(cagr, formatPercent)}</span> : '—';
      },
      best: bestKeys(rows, (row) => metricsOf(row, currency)?.cagr),
    },
    {
      label: '최대 낙폭',
      cell: (row) => <DrawdownCell metrics={metricsOf(row, currency)} />,
      best: bestKeys(rows, (row) => metricsOf(row, currency)?.mdd),
      bestLabel: '가장 적음',
    },
    { label: '평가 가치', cell: (row) => <>₩{formatNumber(row.valueKrw)}</> },
  ];

  const detail = (row: CompareRow) => {
    const item = row.item;
    const data = item.additionalData ?? {};
    const transactions = data.transactions ?? [];
    const price = item.averagePrice ?? data.averagePrice ?? data.purchasePrice;
    const shares = item.totalShares ?? data.totalShares ?? data.shares;
    return (
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <DetailSection title="매수">
          <DetailRow label="매수 횟수" value={`${data.transactions ? purchaseCount(transactions) : 1}회`} />
          <DetailRow label={data.transactions ? '평균 매수가' : '매수가'} value={hasValue(price) ? `$${price.toFixed(2)}` : '-'} />
          <DetailRow label="보유 주식" value={hasValue(shares) ? `${shares.toFixed(2)}주` : '-'} />
          <DetailRow
            label="총 수익"
            value={formatSigned(item.totalReturnKrw, formatKrw)}
            valueClassName={`font-semibold ${gainLossClass(item.totalReturnKrw)}`}
            last
          />
        </DetailSection>
        <RiskDetail
          metrics={metricsOf(row, currency)}
          other={metricsOf(row, currency === 'KRW' ? 'USD' : 'KRW')}
          otherLabel={otherLabel}
          timeWeighted={Boolean(data.transactions)}
        />
        <DetailSection title="환율 영향">
          <DetailRow
            label={data.transactions ? '평균 환율' : '매수 환율'}
            value={(() => {
              const rate = data.transactions ? data.averageFxRate : data.purchaseFxRate;
              return hasValue(rate) ? formatFxRate(rate) : '-';
            })()}
          />
          <DetailRow label="평가일 환율" value={hasValue(data.currentFxRate) ? formatFxRate(data.currentFxRate) : '-'} />
          <DetailRow
            label="환차익률"
            value={formatSigned(item.fxReturnPercent ?? data.fxReturnPercent, formatPercent)}
            valueClassName={`font-semibold ${gainLossClass(item.fxReturnPercent ?? data.fxReturnPercent)}`}
            last
          />
        </DetailSection>
      </div>
    );
  };

  const series = rows.map((row) => ({
    key: row.key,
    name: row.name,
    color: row.color,
    points: returnPoints(row, currency),
    dots: row.item.additionalData?.transactions ? row.deposits.map((d) => d.date) : undefined,
  }));

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-tx-1">
          {symbol && <>{symbol} · </>}예산 ₩{formatNumber(result.investmentAmount)} · 전략 {rows.length}개
          {best && (
            <> · 최고 <span className="font-semibold">{STRATEGY_NAMES[best.name] || best.name}</span>{' '}
              <span className={gainLossClass(best.totalReturnPercent)}>{formatSigned(best.totalReturnPercent, formatPercent)}</span>
            </>
          )}
        </p>
        {hasMetrics && (
          <div className="flex items-center gap-2">
            <span className="text-sm text-tx-2">지표 기준</span>
            <CurrencyToggle value={currency} onChange={setCurrency} />
          </div>
        )}
      </div>

      <CompareTable
        title="전략별 결과"
        description={`줄을 누르면 그 전략의 매수 · 위험 · 환율이 펼쳐집니다 · 총 수익률 순${currency === 'USD' ? ' · 총 수익률 · 평가 가치는 원화 기준' : ''}`}
        nameLabel="전략"
        rows={rows}
        columns={columns}
        expandedKey={expanded}
        onToggle={(key) => setExpanded((prev) => (prev === key ? null : key))}
        detail={detail}
      />

      <ReturnLinesChart
        title="수익률 추이"
        subtitle="그때까지 넣은 돈 대비 몇 % 인지 · 점은 적립식 · 조건부 매수"
        series={series}
        scale="linear"
        highlightedKey={expanded}
      />

      <ResultFold title="주가 추이 비교" meta="기간 고르기 · 전략별 매수 점">
        <CompareStrategiesChart strategies={result.items || []} strategyNames={STRATEGY_NAMES} />
      </ResultFold>
    </div>
  );
};
