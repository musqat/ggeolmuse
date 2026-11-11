import React from 'react';
import { Spinner } from '../../common/Spinner';
import { formatPercent } from '../../../utils/format';
import { gainLossClass } from '../../../utils/gainLoss';
import { signedKrw } from './statsFormat';

export const CARD_CLASS = 'bg-surface rounded-xl shadow-sm border border-line/50 p-6';
export const STAT_CARD_CLASS = 'bg-surface rounded-xl shadow-sm border border-line/50 p-4 sm:p-5';

// 계좌 통계 탭의 카드 배치(위 큰 카드 두 값 · 휴대폰은 위아래, 아래 두 칸 카드)
export const StatsCards: React.FC<{ top: [React.ReactNode, React.ReactNode]; bottom: [React.ReactNode, React.ReactNode] }> = ({
  top,
  bottom,
}) => (
  <div className="space-y-4">
    <div className={`${STAT_CARD_CLASS} grid grid-cols-1 sm:grid-cols-2 gap-4`}>
      <div>{top[0]}</div>
      <div className="border-t border-line/50 pt-4 sm:border-t-0 sm:pt-0 sm:border-l sm:pl-6">{top[1]}</div>
    </div>
    <div className="grid grid-cols-2 gap-4">
      <div className={STAT_CARD_CLASS}>{bottom[0]}</div>
      <div className={STAT_CARD_CLASS}>{bottom[1]}</div>
    </div>
  </div>
);

interface SignedStatProps {
  label: string;
  // 금액 위 이름 줄(종목 이름)
  subject?: string;
  // 원화 금액(없으면 '-')
  amount: number | null;
  percent?: number | null;
  // 금액 아래 작은 줄(휴대폰은 줄마다, 넓은 화면은 · 로 이어 한 줄)
  details?: (string | null | undefined | false)[];
}

// 이익 · 손실 색 원화 금액과 비율, 아래 작은 줄
export const SignedStat: React.FC<SignedStatProps> = ({ label, subject, amount, percent, details = [] }) => {
  const lines = details.filter((line): line is string => !!line);
  return (
    <div data-stat={label}>
      <p className="text-sm font-medium text-tx-2">{label}</p>
      {amount != null && subject && <p className="text-base font-semibold text-tx-1 mt-1 truncate">{subject}</p>}
      <p className={`text-xl sm:text-2xl font-bold mt-1 break-words ${amount == null ? 'text-tx-1' : gainLossClass(amount)}`}>
        {amount == null ? '-' : signedKrw(amount)}
        {amount != null && percent != null && (
          <span className="block sm:inline text-sm sm:text-base font-semibold sm:ml-1">({formatPercent(percent)})</span>
        )}
      </p>
      {amount != null && lines.length > 0 && (
        <p className="text-xs text-tx-2 mt-1">
          {lines.map((line, index) => (
            <React.Fragment key={index}>
              {index > 0 && <span className="hidden sm:inline"> · </span>}
              <span className={index > 0 ? 'block sm:inline' : undefined}>{line}</span>
            </React.Fragment>
          ))}
        </p>
      )}
    </div>
  );
};

interface ValueStatProps {
  label: string;
  value: React.ReactNode;
  detail?: React.ReactNode;
  // 이름 줄 오른쪽 글자
  aside?: React.ReactNode;
}

// 색 없는 값 하나와 아래 작은 줄
export const ValueStat: React.FC<ValueStatProps> = ({ label, value, detail, aside }) => (
  <div data-stat={label}>
    <div className="flex items-center justify-between gap-2 text-sm text-tx-2">
      <span className="font-medium">{label}</span>
      {aside && <span className="truncate">{aside}</span>}
    </div>
    <p className="text-xl sm:text-2xl font-bold mt-1 break-words text-tx-1">{value}</p>
    {detail && <p className="text-xs text-tx-2 mt-1">{detail}</p>}
  </div>
);

// 카드 자리 안내 한 줄
export const StatsNotice: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <div className={`${STAT_CARD_CLASS} text-center text-tx-2 py-8`}>{children}</div>
);

export const StatsLoading: React.FC = () => (
  <div className={`${CARD_CLASS} flex justify-center py-16`}>
    <Spinner />
  </div>
);

// 불러오기 실패 안내와 다시 시도 단추
export const StatsLoadError: React.FC<{ message: string; onRetry: () => void }> = ({ message, onRetry }) => (
  <div className={`${CARD_CLASS} text-center py-12`}>
    <p className="text-tx-1 font-medium mb-4">{message}</p>
    <button
      type="button"
      onClick={onRetry}
      className="px-4 py-2 bg-elevated text-tx-1 rounded-lg hover:bg-hover transition-colors"
    >
      다시 시도
    </button>
  </div>
);
