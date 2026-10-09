import { Calendar } from 'lucide-react';
import { DateField } from '../../common/DateField';
import { PERIODS, type PeriodCode } from './chartRange';

interface ChartPeriodBarProps {
  period: PeriodCode;
  onPeriodChange: (period: PeriodCode) => void;
  customStart: string;
  customEnd: string;
  onCustomStartChange: (date: string) => void;
  onCustomEndChange: (date: string) => void;
  today: string;
}

const buttonClass = (active: boolean) =>
  `px-2 md:px-3 py-1.5 md:py-2 rounded-md text-xs md:text-sm font-medium transition-colors whitespace-nowrap ${
    active ? 'bg-brand text-white shadow-sm' : 'text-tx-2 hover:text-tx-1 hover:bg-hover'
  }`;

// 기간 버튼 한 줄과 직접설정 날짜 칸. 좁은 화면에서는 버튼 줄을 옆으로 민다
export const ChartPeriodBar = ({
  period,
  onPeriodChange,
  customStart,
  customEnd,
  onCustomStartChange,
  onCustomEndChange,
  today,
}: ChartPeriodBarProps) => (
  <div className="mt-3 md:mt-0 space-y-2 min-w-0">
    <div className="flex items-center gap-1 bg-surface rounded-lg shadow-sm border border-line p-1 overflow-x-auto">
      {PERIODS.map(({ code, label }) => (
        <button key={code} type="button" onClick={() => onPeriodChange(code)} className={buttonClass(period === code)}>
          {label}
        </button>
      ))}
      <span className="w-px self-stretch bg-line mx-0.5 shrink-0" />
      <button
        type="button"
        onClick={() => onPeriodChange('custom')}
        className={`flex items-center gap-1 ${buttonClass(period === 'custom')}`}
      >
        <Calendar className="w-3 h-3 md:w-4 md:h-4" />
        직접설정
      </button>
    </div>

    {period === 'custom' && (
      <div className="flex items-center gap-2 md:justify-end">
        <div className="w-40">
          <DateField
            value={customStart}
            onChange={onCustomStartChange}
            max={customEnd || today}
            placeholder="시작일"
            testId="chart-custom-start"
          />
        </div>
        <span className="text-tx-3 text-xs md:text-sm">~</span>
        <div className="w-40">
          <DateField
            value={customEnd}
            onChange={onCustomEndChange}
            min={customStart}
            max={today}
            placeholder="종료일"
            testId="chart-custom-end"
          />
        </div>
      </div>
    )}
  </div>
);
