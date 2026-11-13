import React from 'react';
import { ChevronDown } from 'lucide-react';
import type { SkipReason, SkippedRound, StrategyTransaction } from '../../../services/api';
import { formatKrw, formatNumber, formatSigned, formatUsd } from '../../../utils/format';
import { gainLossClass } from '../../../utils/gainLoss';
import { DIVIDEND_REINVEST, TAX_PAYMENT, isPurchase, isSale, isYearEndHarvest } from './resultValues';

const REASON_LABEL: Record<SkipReason, string> = {
  NO_PRICE: '시세 없음',
  CALCULATION_ERROR: '계산 오류',
  OVER_BUDGET: '예산 부족',
};

interface PurchaseHistoryProps {
  transactions: StrategyTransaction[];
  skippedRounds: SkippedRound[];
}

type Round =
  | { kind: 'bought'; planned: string; tx: StrategyTransaction }
  | { kind: 'skipped'; planned: string; reason: SkipReason };

// 매수 · 미매수 회차를 계획일 순으로 합쳐 연도별로 나눔
function roundsByYear(transactions: StrategyTransaction[], skippedRounds: SkippedRound[]): [string, Round[]][] {
  const rounds: Round[] = [
    ...transactions.map((tx): Round => ({ kind: 'bought', planned: tx.date, tx })),
    ...skippedRounds.map((round): Round => ({ kind: 'skipped', planned: round.date, reason: round.reason })),
  ].sort((a, b) => a.planned.localeCompare(b.planned));
  const years = new Map<string, Round[]>();
  for (const round of rounds) {
    const year = round.planned.slice(0, 4);
    years.set(year, [...(years.get(year) ?? []), round]);
  }
  return [...years.entries()];
}

const countOf = (rounds: Round[], pick: (tx: StrategyTransaction) => boolean) =>
  rounds.filter((round) => round.kind === 'bought' && pick(round.tx)).length;

// 연도 줄의 횟수(0 인 쪽은 뺌, 연말 정리는 매도 · 재매수 짝마다 한 번)
function yearCounts(rounds: Round[]): string {
  const bought = countOf(rounds, isPurchase);
  const sold = countOf(rounds, (tx) => isSale(tx) && !isYearEndHarvest(tx));
  const harvests = countOf(rounds, (tx) => isSale(tx) && isYearEndHarvest(tx));
  const dividends = countOf(rounds, (tx) => tx.trigger === DIVIDEND_REINVEST);
  const payments = countOf(rounds, (tx) => tx.trigger === TAX_PAYMENT);
  const skipped = rounds.filter((round) => round.kind === 'skipped').length;
  return [
    bought > 0 && `매수 ${bought}회`,
    sold > 0 && `매도 ${sold}회`,
    dividends > 0 && `배당 재투자 ${dividends}회`,
    payments > 0 && `양도세 납부 ${payments}회`,
    harvests > 0 && `연말 정리 ${harvests}회`,
    skipped > 0 && `미매수 ${skipped}회`,
  ]
    .filter(Boolean)
    .join(' · ');
}

// 금액 칸(매도는 받은 돈 · 실현 손익, 판 돈으로 산 매수는 쓴 판 돈, 양도세 납부는 낸 달러)
function AmountCell({ tx }: { tx: StrategyTransaction }) {
  if (tx.trigger === TAX_PAYMENT) return <>{formatSigned(-(tx.taxPaid ?? 0), formatUsd)}</>;
  if (isSale(tx)) {
    return (
      <>
        <div>{formatUsd(tx.proceeds ?? 0)} 받음</div>
        <div className={`text-xs ${gainLossClass(tx.realizedProfit)}`}>손익 {formatSigned(tx.realizedProfit, formatUsd)}</div>
      </>
    );
  }
  if (tx.cashUsed != null) {
    return (
      <>
        {tx.amount > 0 && <div>{formatKrw(tx.amount)}</div>}
        <div className="text-xs text-tx-2">판 돈 {formatUsd(tx.cashUsed)}</div>
      </>
    );
  }
  return <>{formatKrw(tx.amount)}</>;
}

const EMPTY = <span className="text-tx-3">-</span>;

const NUMBER_CELL = 'py-1.5 pl-3 text-right whitespace-nowrap';

function RoundRow({ round }: { round: Round }) {
  if (round.kind === 'skipped') {
    return (
      <tr className="border-t border-line/30">
        <td className="py-1.5 whitespace-nowrap">{round.planned}</td>
        <td className="py-1.5 pl-3 whitespace-nowrap text-warning">미매수 · {REASON_LABEL[round.reason]}</td>
        <td className={NUMBER_CELL}>{EMPTY}</td>
        <td className={NUMBER_CELL}>{EMPTY}</td>
        <td className={NUMBER_CELL}>{EMPTY}</td>
      </tr>
    );
  }
  const { tx } = round;
  const tradedOn = tx.actualDate ?? tx.date;
  const payment = tx.trigger === TAX_PAYMENT;
  return (
    <tr className="border-t border-line/30">
      <td className="py-1.5 whitespace-nowrap">
        <div>{tradedOn}</div>
        {tradedOn !== tx.date && <div className="text-xs text-tx-3">계획 {tx.date}</div>}
      </td>
      <td className="py-1.5 pl-3 whitespace-nowrap">{tx.trigger ?? '매수'}</td>
      <td className={NUMBER_CELL}>{payment ? EMPTY : formatUsd(tx.price)}</td>
      <td className={NUMBER_CELL}>
        {payment ? EMPTY : `${formatNumber(Math.abs(tx.shares), { minimumFractionDigits: 4, maximumFractionDigits: 4 })}주`}
      </td>
      <td className={NUMBER_CELL}>
        <AmountCell tx={tx} />
      </td>
    </tr>
  );
}

// 매매 내역 표(연도마다 접힘, 매수 · 매도일 · 가격과 양도세 납부 · 미매수 회차를 함께)
export const PurchaseHistory: React.FC<PurchaseHistoryProps> = ({ transactions, skippedRounds }) => {
  if (transactions.length === 0 && skippedRounds.length === 0) return null;
  const years = roundsByYear(transactions, skippedRounds);

  return (
    <div>
      {years.map(([year, rounds]) => (
        <details key={year} className="group/year border-t border-line/50">
          <summary className="flex items-center justify-between py-3 cursor-pointer list-none [&::-webkit-details-marker]:hidden">
            <span className="font-medium text-tx-1">{year}년</span>
            <span className="inline-flex items-center gap-1 text-sm text-tx-2">
              <span>{yearCounts(rounds)}</span>
              <ChevronDown aria-hidden className="w-4 h-4 transition-transform group-open/year:rotate-180" />
            </span>
          </summary>
          <div className="overflow-x-auto pb-3">
            <table className="w-full text-sm text-tx-1">
              <thead className="text-tx-2">
                <tr>
                  <th className="py-1.5 text-left font-normal">날짜</th>
                  <th className="py-1.5 pl-3 text-left font-normal">구분</th>
                  <th className={`${NUMBER_CELL} font-normal`}>가격</th>
                  <th className={`${NUMBER_CELL} font-normal`}>수량</th>
                  <th className={`${NUMBER_CELL} font-normal`}>금액</th>
                </tr>
              </thead>
              <tbody>
                {rounds.map((round, index) => (
                  <RoundRow key={`${round.kind}-${round.planned}-${index}`} round={round} />
                ))}
              </tbody>
            </table>
          </div>
        </details>
      ))}
    </div>
  );
};
