import React from 'react';
import { ChevronDown } from 'lucide-react';
import type { SkipReason, SkippedRound, StrategyTransaction } from '../../../services/api';
import { formatKrw, formatUsd } from '../../../utils/format';
import { DIVIDEND_REINVEST } from './resultValues';

const REASON_LABEL: Record<SkipReason, string> = {
  NO_PRICE: '시세 없음',
  CALCULATION_ERROR: '계산 오류',
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

const isPurchase = (tx: StrategyTransaction) => tx.trigger !== DIVIDEND_REINVEST;

const boughtCount = (rounds: Round[]) =>
  rounds.filter((round) => round.kind === 'bought' && isPurchase(round.tx)).length;

// 연도 줄의 횟수(0 인 쪽은 뺌)
function yearCounts(rounds: Round[]): string {
  const bought = boughtCount(rounds);
  const skipped = rounds.filter((round) => round.kind === 'skipped').length;
  const dividends = rounds.length - bought - skipped;
  return [bought > 0 && `매수 ${bought}회`, dividends > 0 && `배당 재투자 ${dividends}회`, skipped > 0 && `미매수 ${skipped}회`]
    .filter(Boolean)
    .join(' · ');
}

const EMPTY = <span className="text-tx-3">-</span>;

function RoundRow({ round }: { round: Round }) {
  if (round.kind === 'skipped') {
    return (
      <tr className="border-t border-line/30">
        <td className="py-1.5 whitespace-nowrap">{round.planned}</td>
        <td className="py-1.5 whitespace-nowrap text-warning">미매수 · {REASON_LABEL[round.reason]}</td>
        <td className="py-1.5 text-right">{EMPTY}</td>
        <td className="py-1.5 text-right">{EMPTY}</td>
        <td className="py-1.5 text-right">{EMPTY}</td>
      </tr>
    );
  }
  const { tx } = round;
  const tradedOn = tx.actualDate ?? tx.date;
  return (
    <tr className="border-t border-line/30">
      <td className="py-1.5 whitespace-nowrap">
        <div>{tradedOn}</div>
        {tradedOn !== tx.date && <div className="text-xs text-tx-3">계획 {tx.date}</div>}
      </td>
      <td className="py-1.5 whitespace-nowrap">{tx.trigger ?? '매수'}</td>
      <td className="py-1.5 text-right whitespace-nowrap">{formatUsd(tx.price)}</td>
      <td className="py-1.5 text-right whitespace-nowrap">{tx.shares.toFixed(4)}주</td>
      <td className="py-1.5 text-right whitespace-nowrap">{formatKrw(tx.amount)}</td>
    </tr>
  );
}

// 매수 내역 표(연도마다 접힘, 매수일 · 가격과 미매수 회차를 함께)
export const PurchaseHistory: React.FC<PurchaseHistoryProps> = ({ transactions, skippedRounds }) => {
  if (transactions.length === 0 && skippedRounds.length === 0) return null;
  const years = roundsByYear(transactions, skippedRounds);

  return (
    <div>
      {years.map(([year, rounds]) => (
        <details key={year} className="group border-t border-line/50">
          <summary className="flex items-center justify-between py-3 cursor-pointer list-none [&::-webkit-details-marker]:hidden">
            <span className="font-medium text-tx-1">{year}년</span>
            <span className="inline-flex items-center gap-1 text-sm text-tx-2">
              <span>{yearCounts(rounds)}</span>
              <ChevronDown aria-hidden className="w-4 h-4 transition-transform group-open:rotate-180" />
            </span>
          </summary>
          <div className="overflow-x-auto pb-3">
            <table className="w-full text-sm text-tx-1">
              <thead className="text-tx-2">
                <tr>
                  <th className="py-1.5 text-left font-normal">날짜</th>
                  <th className="py-1.5 text-left font-normal">구분</th>
                  <th className="py-1.5 text-right font-normal">가격</th>
                  <th className="py-1.5 text-right font-normal">수량</th>
                  <th className="py-1.5 text-right font-normal">투자금</th>
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
