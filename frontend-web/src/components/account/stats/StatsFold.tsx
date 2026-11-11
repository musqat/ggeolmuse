import React, { useId, useState } from 'react';
import { ChevronDown } from 'lucide-react';

// 탭 아래 자세히 펼치기(닫힌 채 시작, 제목 줄 오른쪽에 요약)
export const StatsFold: React.FC<{ summary?: React.ReactNode; children: React.ReactNode }> = ({ summary, children }) => {
  const [open, setOpen] = useState(false);
  const contentId = useId();
  return (
    <div className="bg-surface rounded-xl shadow-sm border border-line/50">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={contentId}
        onClick={() => setOpen((value) => !value)}
        className="w-full flex flex-wrap items-center justify-between gap-x-3 gap-y-1 px-4 sm:px-6 py-4 text-left"
      >
        <span className="inline-flex items-center gap-1 text-lg font-semibold text-tx-1">
          자세히 펼치기
          <ChevronDown aria-hidden className={`w-5 h-5 text-tx-2 transition-transform ${open ? 'rotate-180' : ''}`} />
        </span>
        {summary && <span className="text-sm text-tx-2">{summary}</span>}
      </button>
      {open && (
        <div id={contentId} className="px-4 sm:px-6 pb-6 space-y-6">
          {children}
        </div>
      )}
    </div>
  );
};
