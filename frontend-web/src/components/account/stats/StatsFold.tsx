import React, { createContext, useContext, useId, useState } from 'react';
import { ChevronDown } from 'lucide-react';

interface FoldState {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const OuterFoldState = createContext<FoldState | null>(null);

// 감싼 펼침의 열림 상태를 바깥 상태로 둠
export const StatsFoldState: React.FC<FoldState & { children: React.ReactNode }> = ({ open, onOpenChange, children }) => (
  <OuterFoldState.Provider value={{ open, onOpenChange }}>{children}</OuterFoldState.Provider>
);

// 탭 아래 자세히 펼치기(제목 줄 오른쪽에 요약, 바깥 상태가 없으면 닫힌 채 시작)
export const StatsFold: React.FC<{ summary?: React.ReactNode; children: React.ReactNode }> = ({ summary, children }) => {
  const outer = useContext(OuterFoldState);
  const [localOpen, setLocalOpen] = useState(false);
  const open = outer ? outer.open : localOpen;
  const toggle = () => (outer ? outer.onOpenChange(!outer.open) : setLocalOpen((value) => !value));
  const contentId = useId();
  return (
    <div className="bg-surface rounded-xl shadow-sm border border-line/50">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={open ? contentId : undefined}
        onClick={toggle}
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
