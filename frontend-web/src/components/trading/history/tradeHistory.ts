import type { TransactionHistoryItem } from '../../../services/api';

export type HistoryFilter = 'ALL' | 'BUY' | 'SELL' | 'DIVIDEND';

export interface HistoryDay {
  // 거래일. 배당은 배당일
  date: string;
  items: TransactionHistoryItem[];
}

interface Tally {
  total: number;
  count: number;
}

export interface HistorySummary {
  buy: Tally;
  sell: Tally;
  // 세후 금액
  dividend: Tally;
  fee: number;
  // 합계에서 뺀 취소 거래 수
  cancelled: number;
}

const isCancelled = (item: TransactionHistoryItem) => item.status === 'CANCELLED';

export function applyFilters(
  items: TransactionHistoryItem[],
  filter: HistoryFilter,
  accountId: number | 'ALL'
): TransactionHistoryItem[] {
  return items.filter(
    (item) => (filter === 'ALL' || item.type === filter) && (accountId === 'ALL' || item.accountId === accountId)
  );
}

// 날짜 최신순으로 묶는다. 같은 날 안은 주문 시각 최신순. 처리 시각이 비어 오는 배당은 뒤로 간다
export function groupByDay(items: TransactionHistoryItem[]): HistoryDay[] {
  const sorted = [...items].sort(
    (a, b) => b.date.localeCompare(a.date) || (b.executedAt ?? '').localeCompare(a.executedAt ?? '')
  );
  const days: HistoryDay[] = [];
  sorted.forEach((item) => {
    const last = days[days.length - 1];
    if (last && last.date === item.date) last.items.push(item);
    else days.push({ date: item.date, items: [item] });
  });
  return days;
}

export function summarize(items: TransactionHistoryItem[]): HistorySummary {
  const summary: HistorySummary = {
    buy: { total: 0, count: 0 },
    sell: { total: 0, count: 0 },
    dividend: { total: 0, count: 0 },
    fee: 0,
    cancelled: 0,
  };
  items.forEach((item) => {
    if (isCancelled(item)) {
      summary.cancelled += 1;
      return;
    }
    const tally = item.type === 'BUY' ? summary.buy : item.type === 'SELL' ? summary.sell : summary.dividend;
    tally.total += item.totalAmount;
    tally.count += 1;
    summary.fee += item.fee ?? 0;
  });
  return summary;
}
