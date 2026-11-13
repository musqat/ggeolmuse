import React, { useState } from 'react';
import { ChevronDown } from 'lucide-react';

interface ResultFoldProps {
  title: string;
  // 제목 옆에 미리 보이는 요약
  meta?: React.ReactNode;
  // 제목 줄 아래 작은 한 줄(닫혀 있어도 보임)
  note?: React.ReactNode;
  children: React.ReactNode;
}

// 결과 아래 접는 칸(닫힌 채 시작, 처음 열 때 안을 그림)
export const ResultFold: React.FC<ResultFoldProps> = ({ title, meta, note, children }) => {
  const [opened, setOpened] = useState(false);
  return (
    <details
      className="group bg-surface rounded-xl shadow-sm border border-line/50"
      onToggle={(event) => {
        if (event.currentTarget.open) setOpened(true);
      }}
    >
      <summary className="flex items-center justify-between gap-3 px-6 py-4 cursor-pointer list-none [&::-webkit-details-marker]:hidden">
        <span className="text-tx-1">
          <span className="text-lg font-semibold">{title}</span>
          {meta && <span className="ml-2 text-sm text-tx-2">· {meta}</span>}
          {note && <span className="mt-0.5 block text-xs text-tx-2">{note}</span>}
        </span>
        <ChevronDown aria-hidden className="w-5 h-5 shrink-0 text-tx-2 transition-transform group-open:rotate-180" />
      </summary>
      {opened && <div className="px-6 pb-6 space-y-6">{children}</div>}
    </details>
  );
};
