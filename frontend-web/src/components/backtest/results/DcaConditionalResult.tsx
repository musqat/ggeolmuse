import React, { useMemo, useState } from 'react';
import { AccumulationChart } from '@components/charts/backtest/AccumulationChart';
import { SignalChart } from '@components/charts/backtest/SignalChart';
import { BandChart } from '@components/charts/backtest/BandChart';
import type { StrategyResponse } from '../../../services/api';
import { gainLossClass } from '../../../utils/gainLoss';
import { formatBalanceAmount, formatKrw, formatPercent, formatSigned, formatNumber, formatUsd } from '../../../utils/format';
import { ResultOverview } from './ResultOverview';
import { StatCard } from './StatCard';
import { FxImpactSection } from './FxImpactSection';
import { DetailRow, DetailSection } from './DetailSection';
import { PurchaseHistory } from './PurchaseHistory';
import { DefaultFxNotice } from './DefaultFxNotice';
import type { MetricCurrency } from './CurrencyToggle';
import { ResultFold } from './ResultFold';
import { RiskDetail } from './RiskDetail';
import { detailMeta, pickMetrics, purchaseCount, sellCount } from './resultValues';
import { strategyDeposits } from './assetSeries';
import { CapitalGainsTaxFold } from './CapitalGainsTaxFold';
import { TradeStatsCard } from './TradeStatsCard';
import type { BandSetting } from '../shared/maBand';
import type { SellRules } from '../shared/backtestDisplay';
import { isSeparateSignal, signalName } from '../shared/signalSymbol';

type StrategyKind = 'dca' | 'conditional' | 'ma-cross' | 'ma-band';

interface DcaConditionalResultProps {
  kind: StrategyKind;
  result: StrategyResponse;
  symbol: string;
  // 결과에 시작일이 없을 때 차트 시작일
  startDate: string;
  // 실행한 요청의 예산(없으면 제한 없음)
  budget?: number;
  // 실행한 요청의 이동평균 짧은 · 긴 기간
  windows?: { short: number; long: number };
  // 실행한 요청의 밴드 평균 기간 · 위 · 아래 비율
  band?: BandSetting;
  // 실행한 요청에서 켠 트레일링 스탑 · 부분 익절
  sellRules?: SellRules;
}

const EMPTY_REASON: Record<StrategyKind, string> = {
  dca: '기간 안에 산 회차가 없습니다.',
  conditional: '기간 안에 조건이 맞아 산 날이 없습니다.',
  'ma-cross': '기간 안에 짧은 선이 긴 선을 아래에서 위로 넘은 날이 없습니다.',
  'ma-band': '기간 안에 밴드 위로 올라선 날이 없습니다.',
};

// 수익률 기준 투자금 이름(이동평균 · 밴드는 첫 매수에 쓴 원금)
const INVESTED_LABEL: Record<StrategyKind, string> = {
  dca: '누적 매수액',
  conditional: '누적 매수액',
  'ma-cross': '원금',
  'ma-band': '원금',
};

// 정한 회차 없이 신호로 사고파는 결과
const SIGNAL_KINDS: StrategyKind[] = ['ma-cross', 'ma-band'];

const NOTICE_CLASS = 'text-sm text-warning bg-warning-soft/10 border border-warning-soft/40 rounded-lg px-3 py-2';

const INFO_CLASS = 'text-sm text-tx-2 border border-line rounded-lg px-3 py-2';

// 매수 0회 · 예산 부족 구간 안내 한 줄(없으면 null)
const purchaseNotice = (kind: StrategyKind, bought: number, span: StrategyResponse['skippedSpan']) => {
  const shortfall = span ? `예산이 모자라 ${span.firstDate}부터 ${span.rounds}회를 사지 않았습니다.` : null;
  if (bought > 0) return shortfall;
  return `매수 0회: ${shortfall ?? EMPTY_REASON[kind]}`;
};

// 예산 가운데 사지 않고 남은 돈(예산이 없거나 다 썼으면 null)
const unusedBudget = (budget: number | undefined, invested: number | null | undefined) => {
  if (!budget) return null;
  const unused = Math.round(budget - (invested ?? 0));
  return unused > 0 ? unused : null;
};

// 적립식 · 조건부 · 이동평균 · 밴드 결과: 카드 · 매매 통계(조건부 · 이동평균 · 밴드) · 자산 추이 · 주가 추이 탭(주가 추이 앞에 이동평균 교차는 이동평균선 탭 · 밴드는 밴드 탭), 그 아래 예상 양도세 · 상세 · 매매 내역을 접어 둠
// 신호 종목을 따로 본 결과는 맨 위에 신호 · 사고판 종목 한 줄, 이동평균선 · 밴드 탭 이름에 신호 종목
export const DcaConditionalResult: React.FC<DcaConditionalResultProps> = ({
  kind,
  result,
  symbol,
  startDate,
  budget,
  windows,
  band,
  sellRules,
}) => {
  const [currency, setCurrency] = useState<MetricCurrency>('KRW');
  const investedLabel = INVESTED_LABEL[kind];
  const unused = unusedBudget(budget, result.totalInvested);
  const { metrics, other, otherLabel } = pickMetrics(result, currency);
  const transactions = useMemo(() => result.transactions ?? [], [result.transactions]);
  const skippedRounds = result.skippedRounds ?? [];
  const deposits = useMemo(() => strategyDeposits(transactions), [transactions]);
  // 투자일이 휴장일이라 다음 거래일 · 앞 거래일에 산 번수
  const later = transactions.filter((tx) => tx.actualDate && tx.actualDate > tx.date).length;
  const earlier = transactions.filter((tx) => tx.actualDate && tx.actualDate < tx.date).length;
  const sold = sellCount(transactions);
  const notice = purchaseNotice(kind, purchaseCount(transactions), result.skippedSpan);
  // 시세 없음 · 계산 오류 회차와 예산 부족 구간 회차
  const skipped = skippedRounds.length + (result.skippedSpan?.rounds ?? 0);
  // 정한 회차가 없는 이동평균 · 밴드는 미매수가 있을 때만
  const skippedText = !SIGNAL_KINDS.includes(kind) || skipped > 0 ? ` · 미매수 ${skipped}회` : '';
  const traded = result.symbol || symbol;
  // 사고판 종목과 다른 신호 종목(같으면 null)
  const chartSymbol = result.signalChart?.symbol;
  const signal = isSeparateSignal(chartSymbol, traded) ? chartSymbol : null;
  const signalTab = (label: string) => (signal ? `${label} · ${signal}` : label);

  return (
    <div className="space-y-6">
      {signal && (
        <p role="note" className={INFO_CLASS}>
          신호는 {signalName(signal)}, 사고파는 것은 {traded}
        </p>
      )}
      {notice && <p className={NOTICE_CLASS}>{notice}</p>}
      {later > 0 && (
        <p className={NOTICE_CLASS}>투자일이 휴장일이면 다음 거래일 종가로 삽니다(이번 결과 {later}번).</p>
      )}
      {earlier > 0 && (
        <p className={NOTICE_CLASS}>
          투자일이 휴장일이고 다음 거래일이 다음 달이나 기간 끝 뒤면 앞 거래일 종가로 삽니다(이번 결과 {earlier}번).
        </p>
      )}
      <DefaultFxNotice dates={result.defaultFxDates} />
      <ResultOverview
        result={result}
        investedLabel={investedLabel}
        invested={result.totalInvested}
        returnBasis={`${investedLabel} 기준`}
        deposits={deposits}
        currency={currency}
        onCurrencyChange={setCurrency}
        timeWeighted
        extraCard={
          unused !== null && (
            <StatCard
              className="sm:col-span-2 lg:col-span-1"
              label="쓰지 않은 예산"
              value={<>₩{formatNumber(unused)}</>}
              sub={<p>예산 ₩{formatNumber(budget)} 가운데 · 수익률 · 평가 가치에 넣지 않음</p>}
            />
          )
        }
        belowCards={result.tradeStats && <TradeStatsCard stats={result.tradeStats} sellRules={sellRules} />}
        chartTabs={[
          ...(kind === 'ma-cross'
            ? [
                {
                  key: 'signal',
                  label: signalTab('이동평균선'),
                  render: () => <SignalChart chart={result.signalChart} windows={windows} />,
                },
              ]
            : []),
          ...(kind === 'ma-band'
            ? [
                {
                  key: 'band',
                  label: signalTab('밴드'),
                  render: () => <BandChart chart={result.signalChart} transactions={transactions} band={band} />,
                },
              ]
            : []),
          {
            key: 'price',
            label: '주가 추이',
            render: () => (
              <AccumulationChart
                symbol={result.symbol || symbol}
                transactions={transactions}
                startDate={result.startDate || startDate}
                endDate={result.endDate ?? undefined}
              />
            ),
          },
        ]}
      />

      {result.capitalGainsTax && (
        <CapitalGainsTaxFold tax={result.capitalGainsTax} returnBasis={`${investedLabel} 기준`} />
      )}

      <ResultFold
        title="상세"
        meta={detailMeta(metrics, formatSigned(result.fxReturnPercent, formatPercent))}
      >
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6">
          <RiskDetail metrics={metrics} other={other} otherLabel={otherLabel} timeWeighted />

          <DetailSection title="투자 정보">
            <DetailRow label="종목" value={result.symbol} />
            <DetailRow
              label="기간"
              value={
                <>
                  {result.startDate?.toString().substring(0, 7)} ~ {result.endDate?.toString().substring(0, 7)}
                </>
              }
            />
            <DetailRow label="매수 횟수" value={<>{purchaseCount(transactions)}회</>} />
            {sold > 0 && <DetailRow label="매도 횟수" value={<>{sold}회</>} />}
            <DetailRow label={investedLabel} value={<>₩{formatNumber(result.totalInvested)}</>} />
            <div className="flex justify-between gap-3 py-2">
              <span className="text-tx-2">주식 평가액</span>
              <div className="text-right">
                <div className="font-medium text-tx-1">₩{formatNumber(result.currentValueKrw)}</div>
                <div className="text-xs text-tx-2">
                  {result.currentValue != null && formatBalanceAmount(result.currentValue, 'USD')}
                </div>
              </div>
            </div>
          </DetailSection>

          <DetailSection title="주식 수익">
            <DetailRow label="보유 주식수" value={<>{result.totalShares?.toFixed(2)} 주</>} />
            <DetailRow
              label="평균 매수가"
              value={result.averagePrice != null ? formatUsd(result.averagePrice) : '-'}
            />
            <DetailRow label="평가일 가격" value={<>${result.currentPrice?.toFixed(2)}</>} />
            <DetailRow label="배당금 (USD)" value={<>${result.totalDividends?.toFixed(2) || '0.00'}</>} />
            {sold > 0 && (
              <>
                <DetailRow
                  label="실현 손익"
                  value={formatSigned(result.realizedProfit, formatUsd)}
                  valueClassName={`font-medium ${gainLossClass(result.realizedProfit)}`}
                />
                <DetailRow label="남은 현금" value={formatUsd(result.cashBalance ?? 0)} />
              </>
            )}
            <DetailRow
              label="총 수익 (KRW)"
              value={formatSigned(result.totalReturnKrw, formatKrw)}
              valueClassName={`font-bold ${gainLossClass(result.totalReturnKrw)}`}
            />
            <DetailRow
              label="수익률"
              value={formatSigned(result.totalReturnPercent, formatPercent)}
              valueClassName={`font-bold ${gainLossClass(result.totalReturnPercent)}`}
              last
            />
          </DetailSection>

          <FxImpactSection result={result} startLabel="평균 환율" startRate={result.averageFxRate} />
        </div>
      </ResultFold>

      {(transactions.length > 0 || skippedRounds.length > 0) && (
        <ResultFold
          title={sold > 0 ? '매매 내역' : '매수 내역'}
          meta={`매수 ${purchaseCount(transactions)}회${sold > 0 ? ` · 매도 ${sold}회` : ''}${skippedText}`}
        >
          <PurchaseHistory transactions={transactions} skippedRounds={skippedRounds} />
        </ResultFold>
      )}
    </div>
  );
};
