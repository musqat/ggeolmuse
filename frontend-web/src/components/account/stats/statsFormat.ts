import { formatKrw, formatPercent, formatSigned, formatUsd } from '../../../utils/format';
import type { StatsColumn } from './StatsTable';

// 값이 없는 '-' 칸 글자색
export const NO_VALUE_CLASS = 'text-tx-3';

// 원 단위 반올림(₩1,200)
export const krw = (value: number) => formatKrw(Math.round(value));

// 원 단위 반올림, 통화 기호 앞에 부호(+₩1,200 · -₩300)
export const signedKrw = (value: number) => formatSigned(Math.round(value), formatKrw);

export const signedUsd = (value: number) => formatSigned(value, formatUsd);

export const signedPercent = (value: number) => formatSigned(value, formatPercent);

// 첫 칸(날짜) 뒤에 계좌 이름 칸을 둔 표 칸(이름이 없는 계좌는 계좌 ID)
export const withAccountColumn = <T extends { accountId: number }>(
  columns: StatsColumn<T>[],
  accountNames: Record<number, string>
): StatsColumn<T>[] => [
  columns[0],
  { header: '계좌', cell: (row) => accountNames[row.accountId] ?? `계좌 ${row.accountId}` },
  ...columns.slice(1),
];
