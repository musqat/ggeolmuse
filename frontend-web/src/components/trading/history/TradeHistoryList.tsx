import { useId, useMemo, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import type { TransactionHistoryItem } from '../../../services/api';
import { parseLocalDate } from '../../../utils/dateUtils';
import TradeCancelledBadge from '../TradeCancelledBadge';
import { applyFilters, buyDates, groupByDay, summarize, type HistoryFilter } from './tradeHistory';

interface AccountOption {
  accountId: number;
  accountName: string;
}

interface TradeHistoryListProps {
  transactions: TransactionHistoryItem[];
  accounts: AccountOption[];
  onRefresh: () => void;
  refreshing: boolean;
}

const FILTERS: { value: HistoryFilter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'BUY', label: '매수' },
  { value: 'SELL', label: '매도' },
  { value: 'DIVIDEND', label: '배당' },
];

// 유형별 꼬리표 색과 금액 부호. 나간 돈은 -, 들어온 돈은 +. 바탕은 10% 라 작은 글자도 대비 4.5 를 넘는다
const TYPE_STYLE = {
  BUY: { label: '매수', chip: 'bg-buy/10 text-buy', sign: '-' },
  SELL: { label: '매도', chip: 'bg-loss/10 text-loss', sign: '+' },
  DIVIDEND: { label: '배당', chip: 'bg-gain/10 text-gain', sign: '+' },
} as const;

const usd = (value: number) =>
  `$${value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

// 소수 주식이 있어 넷째 자리까지, 끝의 0 은 뺀다
const shares = (value: number) => value.toLocaleString('en-US', { maximumFractionDigits: 4 });

// 2026. 10. 08. (목)
const dayLabel = (date: string) =>
  parseLocalDate(date).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'short',
  });

// YYYY-MM-DDTHH:mm... → 주문 MM/DD HH:mm
const orderTime = (executedAt: string | undefined) =>
  executedAt && executedAt.length >= 16
    ? `주문 ${executedAt.slice(5, 7)}/${executedAt.slice(8, 10)} ${executedAt.slice(11, 16)}`
    : '';

// 상세 줄 조각. 화면에서는 ' · ' 로 잇고 조각 안에서는 줄을 바꾸지 않는다
const detailParts = (item: TransactionHistoryItem): string[] =>
  item.type === 'DIVIDEND'
    ? [
        `${shares(item.shares ?? 0)}주 × ${usd(item.dividendPerShare ?? 0)}`,
        `세전 ${usd(item.grossAmount ?? 0)}`,
        `원천징수 ${usd(item.taxAmount ?? 0)}`,
      ]
    : [
        `${shares(item.quantity ?? 0)}주 × ${usd(item.price ?? 0)}`,
        ...(item.fee ? [`수수료 ${usd(item.fee)}`] : []),
      ];

interface HistoryRowProps {
  item: TransactionHistoryItem;
  // 배당이면 연결된 매수의 거래일
  buyDate?: string;
  accountName?: string;
}

const HistoryRow = ({ item, buyDate, accountName }: HistoryRowProps) => {
  const style = TYPE_STYLE[item.type];
  const cancelled = item.status === 'CANCELLED';

  return (
    <div
      data-testid="history-row"
      data-type={item.type}
      className={`grid grid-cols-[3rem_minmax(0,1fr)_auto] items-center gap-3 px-4 py-3 border-t border-line/60 ${
        cancelled ? 'opacity-60' : ''
      }`}
    >
      <span className={`justify-self-start rounded-md px-2 py-0.5 text-xs font-semibold ${style.chip}`}>
        {style.label}
      </span>
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span className="font-semibold text-tx-1">{item.symbol}</span>
          {buyDate && (
            <span className="rounded border border-line-strong px-1.5 text-[11px] text-tx-2">
              {buyDate.replaceAll('-', '.')} 매수분
            </span>
          )}
          {cancelled && <TradeCancelledBadge reason={item.cancelReason} />}
          {accountName && <span className="text-[11px] text-tx-3">{accountName}</span>}
        </div>
        <p className="text-xs text-tx-2">
          {detailParts(item).map((part, index) => (
            <span key={part}>
              {index > 0 && ' · '}
              <span className="whitespace-nowrap">{part}</span>
            </span>
          ))}
        </p>
      </div>
      <div className="text-right">
        <p
          className={`text-sm font-semibold whitespace-nowrap ${
            item.type === 'DIVIDEND' ? 'text-gain' : 'text-tx-1'
          } ${cancelled ? 'line-through' : ''}`}
        >
          {style.sign}
          {usd(item.totalAmount)}
        </p>
        {item.type !== 'DIVIDEND' && (
          <p className="text-[11px] text-tx-3 whitespace-nowrap">{orderTime(item.executedAt)}</p>
        )}
      </div>
    </div>
  );
};

const SummaryCell = ({
  label,
  value,
  note,
  valueClass = 'text-tx-1',
}: {
  label: string;
  value: string;
  note: string;
  valueClass?: string;
}) => (
  <div className="rounded-lg bg-elevated/60 px-3 py-2.5">
    <p className="text-xs text-tx-3">{label}</p>
    <p className={`text-lg font-semibold ${valueClass}`}>{value}</p>
    <p className="text-[11px] text-tx-2">{note}</p>
  </div>
);

// 거래내역 페이지와 거래 화면 탭이 같이 쓰는 목록. 거래일(배당은 배당일) 최신순으로 날짜마다 묶는다
export const TradeHistoryList = ({ transactions, accounts, onRefresh, refreshing }: TradeHistoryListProps) => {
  const accountSelectId = useId();
  const [filter, setFilter] = useState<HistoryFilter>('ALL');
  const [accountId, setAccountId] = useState<number | 'ALL'>('ALL');

  // 요약은 계좌만 거르고 유형은 거르지 않는다. 유형을 바꿔도 네 칸이 다 보인다
  const inAccount = useMemo(() => applyFilters(transactions, 'ALL', accountId), [transactions, accountId]);
  const summary = useMemo(() => summarize(inAccount), [inAccount]);
  const days = useMemo(() => groupByDay(applyFilters(inAccount, filter, 'ALL')), [inAccount, filter]);
  const buyDateById = useMemo(() => buyDates(transactions), [transactions]);
  const showAccounts = accounts.length > 1;
  const accountNameOf = (id?: number) =>
    showAccounts ? accounts.find((account) => account.accountId === id)?.accountName : undefined;

  return (
    <div className="space-y-4">
      <div data-testid="history-summary" className="grid grid-cols-2 md:grid-cols-4 gap-2">
        <SummaryCell label="매수" value={usd(summary.buy.total)} note={`${summary.buy.count}건`} />
        <SummaryCell label="매도" value={usd(summary.sell.total)} note={`${summary.sell.count}건`} />
        <SummaryCell
          label="배당 (세후)"
          value={`+${usd(summary.dividend.total)}`}
          note={`${summary.dividend.count}건`}
          valueClass="text-gain"
        />
        <SummaryCell
          label="수수료"
          value={usd(summary.fee)}
          note={summary.cancelled > 0 ? `취소 ${summary.cancelled}건 제외` : '취소 없음'}
        />
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <div className="flex items-center gap-1 rounded-lg bg-elevated p-1">
          {FILTERS.map(({ value, label }) => (
            <button
              key={value}
              type="button"
              onClick={() => setFilter(value)}
              className={`px-3 py-1.5 rounded-md text-sm font-medium transition-colors ${
                filter === value ? 'bg-brand text-brand-ink' : 'text-tx-1 hover:bg-hover'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
        {showAccounts && (
          <div className="flex items-center gap-2">
            <label htmlFor={accountSelectId} className="text-sm text-tx-2">
              계좌
            </label>
            <select
              id={accountSelectId}
              value={accountId}
              onChange={(e) => setAccountId(e.target.value === 'ALL' ? 'ALL' : Number(e.target.value))}
              className="border border-line-strong rounded-md px-2 py-1.5 text-sm focus:ring-2 focus:ring-brand focus:border-brand"
            >
              <option value="ALL">전체 계좌</option>
              {accounts.map((account) => (
                <option key={account.accountId} value={account.accountId}>
                  {account.accountName}
                </option>
              ))}
            </select>
          </div>
        )}
        <button
          type="button"
          onClick={onRefresh}
          disabled={refreshing}
          className="ml-auto flex items-center gap-1.5 px-3 py-1.5 text-sm text-tx-2 hover:text-tx-1 rounded-md hover:bg-hover transition-colors disabled:opacity-50"
        >
          <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} />
          새로고침
        </button>
      </div>

      {days.length === 0 ? (
        <div className="bg-surface rounded-xl border border-line/50 p-12 text-center text-tx-2">
          {transactions.length === 0 ? '거래 내역이 없습니다' : '조건에 맞는 거래가 없습니다'}
        </div>
      ) : (
        <div className="bg-surface rounded-xl border border-line/50 overflow-hidden">
          {days.map((day) => (
            <section key={day.date} className="border-t border-line/60 first:border-t-0">
              <h3 data-testid="history-day" className="px-4 py-2 text-xs font-medium text-tx-2 bg-elevated/50">
                {dayLabel(day.date)}
              </h3>
              {day.items.map((item) => (
                <HistoryRow
                  key={`${item.type}-${item.tradeId}-${item.date}-${item.executedAt}`}
                  item={item}
                  buyDate={
                    item.type === 'DIVIDEND' && item.tradeId !== undefined
                      ? buyDateById.get(item.tradeId)
                      : undefined
                  }
                  accountName={item.type === 'DIVIDEND' ? undefined : accountNameOf(item.accountId)}
                />
              ))}
            </section>
          ))}
        </div>
      )}
    </div>
  );
};
