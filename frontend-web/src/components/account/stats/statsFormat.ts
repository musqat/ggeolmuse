import { formatKrw, formatNumber, formatPercent, formatSigned } from '../../../utils/format';
import type { StatsColumn } from './StatsTable';

// 값이 없는 '-' 칸 글자색
export const NO_VALUE_CLASS = 'text-tx-3';

// 원 단위 반올림(₩1,200)
export const krw = (value: number) => formatKrw(Math.round(value));

// 원 단위 반올림, 통화 기호 앞에 부호(+₩1,200 · -₩300)
export const signedKrw = (value: number) => formatSigned(Math.round(value), formatKrw);

// 천 단위 쉼표 · 소수 둘째 달러($1,234.50)
export const usd = (value: number) =>
  `$${formatNumber(value, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

// 주당 배당처럼 소수 여섯째까지인 달러($0.485 · $1.20)
export const usdPerShare = (value: number) =>
  `$${formatNumber(value, { minimumFractionDigits: 2, maximumFractionDigits: 6 })}`;

export const signedUsd = (value: number) => formatSigned(value, usd);

export const signedPercent = (value: number) => formatSigned(value, formatPercent);

// 저장 값 그대로인 수량(소수 여섯째까지, 끝자리 0 없음)
export const formatQuantity = (value: number) => formatNumber(value, { maximumFractionDigits: 6 });

// 첫 칸(날짜) 뒤에 계좌 이름 칸을 둔 표 칸(이름이 없는 계좌는 계좌 ID)
export const withAccountColumn = <T extends { accountId: number }>(
  columns: StatsColumn<T>[],
  accountNames: Record<number, string>
): StatsColumn<T>[] => [
  columns[0],
  { header: '계좌', cell: (row) => accountNames[row.accountId] ?? `계좌 ${row.accountId}` },
  ...columns.slice(1),
];
