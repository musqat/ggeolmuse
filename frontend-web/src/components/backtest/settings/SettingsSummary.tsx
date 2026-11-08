import { dotDate } from './period';

// 요약 셋째 줄부터(전략 비교 칩마다 한 줄)
export interface SummaryLine {
  text: string;
  tone: 'normal' | 'warning' | 'error';
}

// 모드가 정하는 요약(첫 줄의 종목, 둘째 줄의 사는 방법 · 한도 · 매도 · 바꾼 설정)
export interface ModeSummary {
  subject: string;
  how: string;
  // 기본값과 다른 세부 설정
  changed?: string[];
  lines?: SummaryLine[];
}

interface SettingsSummaryProps extends ModeSummary {
  id: string;
  start: string;
  end: string;
  errorCount: number;
}

const TONE = { normal: 'text-tx-1', warning: 'text-warning', error: 'text-danger' };

// 실행 단추 옆 요약 두 줄: 종목 · 기간 / 사는 방법(고칠 칸이 있으면 그 수를 앞에)
export const SettingsSummary = ({ id, subject, how, changed = [], lines = [], start, end, errorCount }: SettingsSummaryProps) => (
  <div id={id} data-testid="backtest-summary" className="min-w-0 text-sm leading-relaxed text-tx-2 [word-break:keep-all]">
    {errorCount > 0 && <span className="text-danger">{`고칠 칸 ${errorCount}개 · `}</span>}
    <b className="font-semibold text-tx-1">{subject}</b>
    {` · ${dotDate(start)} → ${dotDate(end)}`}
    <br />
    <span className="text-tx-1">{how}</span>
    {changed.length > 0 && ` · ${changed.join(' · ')}`}
    {lines.map((line, index) => (
      <span key={index} className={`block ${TONE[line.tone]}`}>
        {line.text}
      </span>
    ))}
  </div>
);
