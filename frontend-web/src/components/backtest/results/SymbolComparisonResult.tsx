import React, { useMemo, useState } from 'react';
import { gainLossClass, hasValue } from '../../../utils/gainLoss';
import { formatFxRate, formatKrw, formatPercent, formatSigned, formatNumber } from '../../../utils/format';
import type { ComparisonItem, ComparisonResponse } from '../../../services/api';
import { CompareSymbolsChartMemoized } from './CompareSymbolsChartMemoized';
import { daysBetween, getTodayString } from '../../../utils/dateUtils';
import { cashKrw } from '../shared/backtestDisplay';
import { HolidayNotice } from './HolidayNotice';
import { CHART_COLORS } from '@components/charts/common/constants';
import { CurrencyToggle, type MetricCurrency } from './CurrencyToggle';
import { DetailRow, DetailSection } from './DetailSection';
import { ResultFold } from './ResultFold';
import { RiskDetail } from './RiskDetail';
import { simpleDeposits } from './assetSeries';
import { CompareTable, type CompareColumn } from './compare/CompareTable';
import { ReturnLinesChart } from './compare/ReturnLinesChart';
import { bestKeys, byTotalReturn, metricsOf, returnPoints, type CompareRow } from './compare/compareRows';
import { DrawdownCell } from './compare/DrawdownCell';

interface SymbolComparisonResultProps {
  result: ComparisonResponse;
  comparePurchaseDate: string;
  compareSaleDate: string;
}

const toRow = (item: ComparisonItem, index: number): CompareRow => {
  const detail = item.additionalData ?? {};
  return {
    key: item.code || item.name,
    name: item.code || item.name,
    color: CHART_COLORS[index % CHART_COLORS.length],
    item,
    totalReturnPercent: item.totalReturnPercent ?? null,
    valueKrw: detail.totalAssetKrw ?? item.currentValueKrw ?? null,
    metricsKrw: detail.metricsKrw,
    metricsUsd: detail.metricsUsd,
    deposits: simpleDeposits(detail.purchaseDate, detail.investmentAmount, detail.purchaseFxRate),
  };
};

// 종목 비교 결과: 요약 한 줄 · 종목별 표 · 수익률 추이, 주가 추이는 접어 둠
export const SymbolComparisonResult: React.FC<SymbolComparisonResultProps> = ({
  result,
  comparePurchaseDate,
  compareSaleDate,
}) => {
  const [currency, setCurrency] = useState<MetricCurrency>('KRW');
  const [expanded, setExpanded] = useState<string | null>(null);
  const rows = useMemo(() => (result.items ?? []).map(toRow).sort(byTotalReturn), [result.items]);
  const hasMetrics = rows.some((row) => row.metricsKrw != null || row.metricsUsd != null);
  const otherLabel = currency === 'KRW' ? '달러' : '원화';
  const firstDetail = result.items?.[0]?.additionalData;
  const investment = result.investmentAmount ?? firstDetail?.investmentAmount;

  const columns: CompareColumn[] = [
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
    {
      label: '평가 가치',
      cell: (row) => <>₩{formatNumber(row.valueKrw)}</>,
      best: bestKeys(rows, (row) => row.valueKrw),
    },
  ];

  const detail = (row: CompareRow) => {
    const item = row.item;
    const data = item.additionalData ?? {};
    const purchaseDate = data.purchaseDate ?? item.purchaseDate;
    const currentDate = data.currentDate ?? item.currentDate ?? getTodayString();
    const daysHeld = item.daysHeld ?? (purchaseDate ? daysBetween(purchaseDate, currentDate) : undefined);
    return (
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6">
        <DetailSection title="투자 정보">
          <DetailRow label="매수일" value={purchaseDate ?? '-'} />
          <DetailRow label="보유일" value={daysHeld != null ? `${formatNumber(daysHeld)}일` : '-'} />
          <DetailRow label="매수가 → 평가일 가격" value={`$${data.purchasePrice?.toFixed(2) ?? '-'} → $${data.currentPrice?.toFixed(2) ?? '-'}`} />
          <DetailRow label="보유 주식" value={`${data.shares?.toFixed(2) ?? '-'}주`} />
          <DetailRow
            label="주식 · 현금"
            value={`₩${formatNumber(item.currentValueKrw)} · ₩${formatNumber(cashKrw({ totalAssetKrw: row.valueKrw, currentValueKrw: item.currentValueKrw }))}`}
          />
          <DetailRow
            label="총 수익"
            value={formatSigned(item.totalReturnKrw, formatKrw)}
            valueClassName={`font-semibold ${gainLossClass(item.totalReturnKrw)}`}
            last
          />
        </DetailSection>
        <RiskDetail metrics={metricsOf(row, currency)} other={metricsOf(row, currency === 'KRW' ? 'USD' : 'KRW')} otherLabel={otherLabel} />
        <DetailSection title="환율 영향">
          <DetailRow label="매수 환율" value={hasValue(data.purchaseFxRate) ? formatFxRate(data.purchaseFxRate) : '-'} />
          <DetailRow label="평가일 환율" value={hasValue(data.currentFxRate) ? formatFxRate(data.currentFxRate) : '-'} />
          <DetailRow
            label="환차익률"
            value={formatSigned(item.fxReturnPercent ?? data.fxReturnPercent, formatPercent)}
            valueClassName={`font-semibold ${gainLossClass(item.fxReturnPercent ?? data.fxReturnPercent)}`}
            last
          />
        </DetailSection>
        <DetailSection title="최적 타이밍">
          <DetailRow label="최적 매수" value={item.optimalBuyDate ? `${item.optimalBuyDate} · $${item.optimalBuyPrice?.toFixed(2) ?? '-'}` : '-'} />
          <DetailRow label="최적 매도" value={item.optimalSellDate ? `${item.optimalSellDate} · $${item.optimalSellPrice?.toFixed(2) ?? '-'}` : '-'} />
          <DetailRow
            label="최적 수익률"
            value={hasValue(data.optimalReturnPercent) ? formatSigned(data.optimalReturnPercent, formatPercent) : '-'}
            last
          />
        </DetailSection>
      </div>
    );
  };

  const series = rows.map((row) => ({ key: row.key, name: row.name, color: row.color, points: returnPoints(row, currency) }));

  return (
    <div className="space-y-6">
      <HolidayNotice label="시작일" chosen={comparePurchaseDate} traded={firstDetail?.purchaseDate || result.items?.[0]?.purchaseDate} />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-tx-1">
          종목 {rows.length}개 · ₩{formatNumber(investment)}씩
          {result.bestPerformer && (
            <> · 최고 <span className="font-semibold">{result.bestPerformer.name}</span>{' '}
              <span className={gainLossClass(result.bestPerformer.totalReturnPercent)}>
                {formatSigned(result.bestPerformer.totalReturnPercent, formatPercent)}
              </span>
            </>
          )}
          {hasValue(result.averageReturn) && <> · 평균 {formatSigned(result.averageReturn, formatPercent)}</>}
          {hasValue(result.medianReturn) && <> · 중앙값 {formatSigned(result.medianReturn, formatPercent)}</>}
        </p>
        {hasMetrics && (
          <div className="flex items-center gap-2">
            <span className="text-sm text-tx-2">지표 기준</span>
            <CurrencyToggle value={currency} onChange={setCurrency} />
          </div>
        )}
      </div>

      <CompareTable
        title="종목별 결과"
        description={`줄을 누르면 그 종목의 상세가 펼쳐집니다 · 총 수익률 순${currency === 'USD' ? ' · 총 수익률 · 평가 가치는 원화 기준' : ''}`}
        nameLabel="종목"
        rows={rows}
        columns={columns}
        expandedKey={expanded}
        onToggle={(key) => setExpanded((prev) => (prev === key ? null : key))}
        detail={detail}
      />

      <ReturnLinesChart
        title="수익률 추이"
        subtitle="같은 날 같은 돈으로 샀을 때 날마다 몇 % 인지 · 2배 넘게 벌어지면 눈금은 배수 간격(+100% = 2배, +300% = 4배)"
        series={series}
        scale="multiple"
        highlightedKey={expanded}
      />

      <ResultFold title="주가 추이 비교" meta="기간 고르기 · 매수 · 최적 시점 점">
        <CompareSymbolsChartMemoized
          items={result.items}
          comparePurchaseDate={comparePurchaseDate}
          compareSaleDate={compareSaleDate}
        />
      </ResultFold>
    </div>
  );
};
