import { INDICATOR_GROUPS, type IndicatorKey, type IndicatorState } from './indicators';

interface IndicatorPanelProps {
  indicators: IndicatorState;
  onToggle: (key: IndicatorKey) => void;
  height: number;
}

// 768px 이상에서 차트 오른쪽에 붙는 지표 체크 목록
export const IndicatorPanel = ({ indicators, onToggle, height }: IndicatorPanelProps) => (
  <div
    data-testid="chart-indicator-panel"
    className="hidden md:block w-[160px] flex-shrink-0 bg-surface border-l border-line/60 overflow-y-auto"
    style={{ height }}
  >
    <div className="p-3">
      <p className="text-[11px] font-semibold text-tx-3 uppercase tracking-wider mb-2">지표 설정</p>
      {INDICATOR_GROUPS.map(({ title, items }, index) => (
        <div key={title}>
          {index > 0 && <div className="border-t border-line/60 mb-3" />}
          <div className="mb-3">
            <p className="text-[10px] font-medium text-tx-3 mb-1 px-2">{title}</p>
            {items.map(({ key, label, color }) => (
              <label
                key={key}
                className="flex items-center gap-2 py-1 px-2 rounded cursor-pointer hover:bg-hover/50 transition-colors"
              >
                <input
                  type="checkbox"
                  checked={indicators[key]}
                  onChange={() => onToggle(key)}
                  className="w-3.5 h-3.5 accent-brand"
                />
                <span className="text-xs text-tx-2 select-none" style={color ? { color } : undefined}>
                  {label}
                </span>
              </label>
            ))}
          </div>
        </div>
      ))}
    </div>
  </div>
);
