import React from 'react';
import { ChevronDown } from 'lucide-react';
import type { CompareRow } from './compareRows';

export interface CompareColumn {
  label: string;
  cell: (row: CompareRow) => React.ReactNode;
  // 이 칸에서 가장 좋은 줄과 그 표시
  best?: Set<string>;
  bestLabel?: string;
}

interface CompareTableProps {
  title: string;
  description: string;
  nameLabel: string;
  rows: CompareRow[];
  columns: CompareColumn[];
  expandedKey: string | null;
  onToggle: (key: string) => void;
  detail: (row: CompareRow) => React.ReactNode;
}

// 비교 결과 표(줄을 누르면 그 항목 상세가 펼쳐짐, 한 번에 한 줄)
export const CompareTable: React.FC<CompareTableProps> = ({
  title,
  description,
  nameLabel,
  rows,
  columns,
  expandedKey,
  onToggle,
  detail,
}) => (
  <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6">
    <h3 className="text-lg font-semibold text-tx-1">{title}</h3>
    <p className="text-sm text-tx-2 mt-1 mb-4">{description}</p>
    <div className="overflow-x-auto">
      <table className="w-full text-sm text-tx-1">
        <thead className="text-tx-2">
          <tr className="border-b border-line">
            <th className="py-2 pr-3 text-left font-normal">{nameLabel}</th>
            {columns.map((column) => (
              <th key={column.label} className="py-2 px-3 text-right font-normal whitespace-nowrap">
                {column.label}
              </th>
            ))}
            <th className="py-2 w-8" aria-hidden />
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const open = row.key === expandedKey;
            return (
              <React.Fragment key={row.key}>
                <tr
                  className={`border-b border-line/50 cursor-pointer hover:bg-hover/40 ${open ? 'bg-hover/30' : ''}`}
                  onClick={() => onToggle(row.key)}
                >
                  <td className="py-3 pr-3">
                    <span className="inline-flex items-center gap-2 font-semibold">
                      <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ background: row.color }} aria-hidden />
                      <span>{row.name}</span>
                    </span>
                  </td>
                  {columns.map((column) => (
                    <td key={column.label} className="py-3 px-3 text-right align-top">
                      <div className="inline-flex items-center justify-end gap-1.5">
                        {column.best?.has(row.key) && (
                          <span className="text-[11px] font-semibold px-1.5 py-0.5 rounded bg-brand-bg text-brand whitespace-nowrap">
                            {column.bestLabel ?? '최고'}
                          </span>
                        )}
                        <span>{column.cell(row)}</span>
                      </div>
                    </td>
                  ))}
                  <td className="py-3 text-right">
                    <button
                      type="button"
                      aria-label={`${row.name} 상세`}
                      aria-expanded={open}
                      onClick={(event) => {
                        event.stopPropagation();
                        onToggle(row.key);
                      }}
                      className="p-1 text-tx-2"
                    >
                      <ChevronDown aria-hidden className={`w-4 h-4 transition-transform ${open ? 'rotate-180' : ''}`} />
                    </button>
                  </td>
                </tr>
                {open && (
                  <tr className="border-b border-line/50">
                    <td colSpan={columns.length + 2} className="py-4">
                      {detail(row)}
                    </td>
                  </tr>
                )}
              </React.Fragment>
            );
          })}
        </tbody>
      </table>
    </div>
  </div>
);
