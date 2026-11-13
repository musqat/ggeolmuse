import { useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { SlidersHorizontal, X } from 'lucide-react';
import { INDICATOR_GROUPS, type IndicatorKey, type IndicatorState } from './indicators';
import { LAYER } from '@/styles/layers';
import { useModalDialog } from '../../common/useModalDialog';

interface IndicatorSheetProps {
  indicators: IndicatorState;
  onToggle: (key: IndicatorKey) => void;
}

// 768px 미만의 지표 시트(버튼 줄에 켜진 지표, 누르면 아래에서 올라옴)
export const IndicatorSheet = ({ indicators, onToggle }: IndicatorSheetProps) => {
  const [open, setOpen] = useState(false);
  const active = INDICATOR_GROUPS.flatMap((group) => group.items).filter((item) => indicators[item.key]);
  const rootRef = useRef<HTMLDivElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  useModalDialog({ open, onClose: () => setOpen(false), rootRef, panelRef });

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
          <div ref={rootRef} className="fixed inset-0" style={{ zIndex: LAYER.popover }}>
            <div
              data-testid="chart-indicator-backdrop"
              aria-hidden="true"
              className="absolute inset-0 bg-black/50"
              onClick={() => setOpen(false)}
            />
            <div
              ref={panelRef}
              role="dialog"
              aria-modal="true"
              aria-label="지표 설정"
              tabIndex={-1}
              className="absolute inset-x-0 bottom-0 max-h-[70vh] overflow-y-auto rounded-t-2xl border-t border-line bg-surface px-4 pt-2 pb-6 text-tx-1 focus:outline-none"
            >
              <div className="mx-auto mb-2 h-1 w-8 rounded-full bg-line-strong" />
              <div className="flex items-center justify-between">
                <p className="text-sm font-semibold">지표</p>
                <button
                  type="button"
                  aria-label="닫기"
                  data-dialog-close
                  onClick={() => setOpen(false)}
                  className="p-1 text-tx-3 hover:text-tx-1"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
              {INDICATOR_GROUPS.map(({ title, items }) => (
                <div key={title} className="mt-3">
                  <p className="text-[11px] text-tx-3 mb-0.5">{title}</p>
                  {/* 두 열(시트 높이를 줄여 위로 차트가 보임) */}
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
