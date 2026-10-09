import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { SlidersHorizontal, X } from 'lucide-react';
import { INDICATOR_GROUPS, type IndicatorKey, type IndicatorState } from './indicators';

interface IndicatorSheetProps {
  indicators: IndicatorState;
  onToggle: (key: IndicatorKey) => void;
}

// 768px 미만에서 옆 패널 대신 쓴다. 버튼 줄에 켜진 지표를 보이고, 누르면 아래에서 시트가 올라온다
export const IndicatorSheet = ({ indicators, onToggle }: IndicatorSheetProps) => {
  const [open, setOpen] = useState(false);
  const active = INDICATOR_GROUPS.flatMap((group) => group.items).filter((item) => indicators[item.key]);

  // Esc 로 닫는다
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open]);

  return (
    <>
      <div className="md:hidden flex items-center justify-between gap-2 px-1 pb-1.5">
        <span className="text-[11px] text-tx-3 truncate">
          {active.map((item) => item.label).join(' · ') || '켜진 지표 없음'}
        </span>
        <button
          type="button"
          data-testid="chart-indicator-button"
          onClick={() => setOpen(true)}
          className="flex items-center gap-1 px-2.5 py-1 text-xs text-tx-1 border border-line-strong rounded-md hover:border-brand shrink-0"
        >
          <SlidersHorizontal className="w-3.5 h-3.5" />
          지표
          <span className="px-1.5 rounded-full bg-brand-bg text-brand text-[10px] font-semibold">
            {active.length}
          </span>
        </button>
      </div>

      {open &&
        createPortal(
          // 화면 구석의 AI 채팅 버튼(z-index 9000)보다 위에 띄운다
          <div
            className="fixed inset-0 md:hidden"
            style={{ zIndex: 9050 }}
            role="dialog"
            aria-modal="true"
            aria-label="지표 설정"
          >
            <div
              data-testid="chart-indicator-backdrop"
              className="absolute inset-0 bg-black/50"
              onClick={() => setOpen(false)}
            />
            <div className="absolute inset-x-0 bottom-0 max-h-[70vh] overflow-y-auto rounded-t-2xl border-t border-line bg-surface px-4 pt-2 pb-6 text-tx-1">
              <div className="mx-auto mb-2 h-1 w-8 rounded-full bg-line-strong" />
              <div className="flex items-center justify-between">
                <p className="text-sm font-semibold">지표</p>
                <button
                  type="button"
                  aria-label="닫기"
                  onClick={() => setOpen(false)}
                  className="p-1 text-tx-3 hover:text-tx-1"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
              {INDICATOR_GROUPS.map(({ title, items }) => (
                <div key={title} className="mt-3">
                  <p className="text-[11px] text-tx-3 mb-0.5">{title}</p>
                  {/* 두 열로 놓아 시트 높이를 줄이고 위로 차트가 보이게 한다 */}
                  <div className="grid grid-cols-2 gap-x-4">
                    {items.map(({ key, label, color }) => (
                      <button
                        key={key}
                        type="button"
                        role="switch"
                        aria-checked={indicators[key]}
                        onClick={() => onToggle(key)}
                        className="w-full flex items-center justify-between gap-2 py-2 border-b border-line/60 text-sm"
                      >
                        <span className="flex items-center gap-2">
                          {color && <span className="w-2 h-2 rounded-full" style={{ background: color }} />}
                          {label}
                        </span>
                        <span
                          className={`relative w-8 h-[18px] rounded-full transition-colors ${
                            indicators[key] ? 'bg-brand' : 'bg-line-strong'
                          }`}
                        >
                          <span
                            className={`absolute top-0.5 h-3.5 w-3.5 rounded-full bg-white transition-all ${
                              indicators[key] ? 'left-4' : 'left-0.5'
                            }`}
                          />
                        </span>
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>,
          document.body
        )}
    </>
  );
};
