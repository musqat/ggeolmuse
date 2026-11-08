import React, { useId } from 'react';
import { X } from 'lucide-react';
import { DateField } from '../../common/DateField';
import { formatPercent, formatSigned } from '../../../utils/format';
import { gainLossClass } from '../../../utils/gainLoss';
import { MAX_COMPARE, type CompareRow } from './purchaseDays';

interface PurchaseDayCompareProps {
  rows: CompareRow[];
  onAdd: (date: string) => void;
  onRemove: (date: string) => void;
  // 날짜 칸이 고를 수 있는 첫 · 마지막 매수일
  min: string;
  max: string;
}

const formatPoint = (value: number) => `${value.toFixed(2)}%p`;

// 매수일 비교 표: 고른 매수일과 넣은 날의 수익률 · 고른 매수일 대비 차이
export const PurchaseDayCompare: React.FC<PurchaseDayCompareProps> = ({ rows, onAdd, onRemove, min, max }) => {
  const titleId = useId();

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-2 mb-1">
        <h4 id={titleId} className="text-sm font-medium text-tx-1">
          매수일 비교
        </h4>
        <div className="w-44">
          <DateField value="" onChange={onAdd} min={min} max={max} presets={false} placeholder="날짜로 넣기" />
        </div>
      </div>
      <p className="text-xs text-tx-2 mb-2">
        곡선을 누르면 그날을 넣고 다시 누르면 뺍니다(최대 {MAX_COMPARE}개, 휴장일은 다음 거래일)
      </p>
      <div className="overflow-x-auto">
        <table aria-labelledby={titleId} className="w-full text-sm text-tx-1">
          <thead className="text-tx-2">
            <tr>
              <th className="py-1.5 text-left font-normal">매수일</th>
              <th className="py-1.5 text-right font-normal">수익률</th>
              <th className="py-1.5 text-right font-normal">고른 매수일 대비</th>
              <th className="py-1.5 w-8">
                <span className="sr-only">빼기</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.date} className="border-t border-line/30">
                <td className="py-1.5 whitespace-nowrap">
                  {row.date}
                  {row.base && <span className="ml-2 text-xs text-tx-2">고른 매수일</span>}
                </td>
                <td className={`py-1.5 text-right whitespace-nowrap ${gainLossClass(row.returnPct)}`}>
                  {row.returnPct == null ? '—' : formatSigned(row.returnPct, formatPercent)}
                </td>
                <td className={`py-1.5 text-right whitespace-nowrap ${gainLossClass(row.diff)}`}>
                  {row.diff == null ? '—' : formatSigned(row.diff, formatPoint)}
                </td>
                <td className="py-1.5 text-right">
                  {!row.base && (
                    <button
                      type="button"
                      aria-label={`${row.date} 비교에서 빼기`}
                      onClick={() => onRemove(row.date)}
                      className="p-1 text-tx-3 hover:text-tx-1"
                    >
                      <X aria-hidden className="w-4 h-4" />
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
