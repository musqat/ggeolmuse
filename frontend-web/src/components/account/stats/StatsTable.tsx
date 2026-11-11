import React, { useId } from 'react';

export interface StatsColumn<T> {
  header: string;
  cell: (row: T) => React.ReactNode;
  // 오른쪽 맞춤 숫자 칸
  numeric?: boolean;
  // 글자색(없으면 본문 색)
  tone?: (row: T) => string;
}

interface StatsTableProps<T> {
  title: string;
  columns: StatsColumn<T>[];
  rows: T[];
  rowKey: (row: T) => React.Key;
  // 줄이 없을 때 안내 한 줄
  empty?: string;
}

// 제목과 카드 안에서만 가로 스크롤하는 표
export const StatsTable = <T,>({ title, columns, rows, rowKey, empty }: StatsTableProps<T>) => {
  const titleId = useId();
  return (
    <section>
      <h4 id={titleId} className="text-sm font-medium text-tx-1 mb-2">
        {title}
      </h4>
      <div className="overflow-x-auto">
        <table aria-labelledby={titleId} className="w-full text-sm whitespace-nowrap">
          <thead>
            <tr className="border-b border-line">
              {columns.map((column) => (
                <th
                  key={column.header}
                  scope="col"
                  className={`px-3 py-2 text-xs font-medium text-tx-2 ${column.numeric ? 'text-right' : 'text-left'}`}
                >
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-line/50">
            {rows.length === 0 && empty ? (
              <tr>
                <td colSpan={columns.length} className="px-3 py-6 text-center text-tx-2">
                  {empty}
                </td>
              </tr>
            ) : (
              rows.map((row) => (
                <tr key={rowKey(row)}>
                  {columns.map((column) => (
                    <td
                      key={column.header}
                      className={`px-3 py-2 ${column.numeric ? 'text-right tabular-nums' : 'text-left'} ${
                        column.tone?.(row) ?? 'text-tx-1'
                      }`}
                    >
                      {column.cell(row)}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
};
