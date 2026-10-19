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
  // 합계에서 뺀 정산 전 매수 수
  pending: number;
}

// 정산 전 매수가 있으면 이 간격으로 다시 불러옴. 주문한 지 이만큼 지나면 멈춤
const PENDING_REFETCH_MS = 2000;
const PENDING_WATCH_MS = 60_000;

const isCancelled = (item: TransactionHistoryItem) => item.status === 'CANCELLED';
const isPending = (item: TransactionHistoryItem) => item.status === 'PENDING';

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
    pending: 0,
  };
  items.forEach((item) => {
    if (isCancelled(item)) {
      summary.cancelled += 1;
      return;
    }
    if (isPending(item)) {
      summary.pending += 1;
      return;
    }
    const tally = item.type === 'BUY' ? summary.buy : item.type === 'SELL' ? summary.sell : summary.dividend;
    tally.total += item.totalAmount;
    tally.count += 1;
    summary.fee += item.fee ?? 0;
  });
  return summary;
}

// 주문 시각과 지금이 1분 넘게 벌어진 대기는 세지 않음(시간대가 다른 브라우저에서 끝없이 부르지 않게)
export function historyRefetchInterval(items?: TransactionHistoryItem[], now = Date.now()): number | false {
  const watching = items?.some(
    (item) => isPending(item) && Math.abs(now - Date.parse(item.executedAt)) < PENDING_WATCH_MS
  );
  return watching ? PENDING_REFETCH_MS : false;
}
