import type { PortfolioMethod, PortfolioStrategyType } from '@services/api';
import { digitsOnly, oneDecimal } from '../../settings/inputText';
import { isError, type FieldMessage, type PortfolioRowMessages } from '../../settings/checks';
import { LEAD, UNIT, VALUE_ROW } from '../../settings/styles';
import { STRATEGIES, STRATEGY_LABELS, chosenStrategy, rowStrategy, type PortfolioRow } from '../../shared/portfolioRows';

type SignalKey = 'shortWindow' | 'longWindow' | 'bandWindow' | 'upper' | 'lower';

interface RowStrategyProps {
  n: number;
  row: PortfolioRow;
  onChange: <K extends keyof PortfolioRow>(key: K, value: PortfolioRow[K]) => void;
}

// 줄 전략 고르기(방식대로인 줄은 방식이 고른 값으로 보임)
export const RowStrategySelect = ({
  n,
  row,
  onChange,
  method,
  className,
}: RowStrategyProps & { method: PortfolioMethod; className: string }) => (
  <div className={`${VALUE_ROW} ${className}`}>
    <span className={`${LEAD} mr-1`}>전략</span>
    <select
      className="input-inline cursor-pointer"
      aria-label={`${n}번 전략`}
      value={rowStrategy(row, method)}
      onChange={(e) => onChange('strategy', chosenStrategy(e.target.value as PortfolioStrategyType, method))}
    >
      {STRATEGIES.map((strategy) => (
        <option key={strategy} value={strategy}>
          {STRATEGY_LABELS[strategy]}
        </option>
      ))}
    </select>
  </div>
);

interface RowSignalFieldsProps extends RowStrategyProps {
  messages: PortfolioRowMessages;
  // 칸 아래 한 줄을 낸 칸이면 그 줄 id
  describedBy: (own: FieldMessage | null) => string | undefined;
  className: string;
}

// 이동평균 줄은 "짧은 [20]일 선 · 긴 [60]일 선", 밴드 줄은 "[200]일선 · 위 +[2.5]% · 아래 -[2.5]%"(휴대폰은 평균 기간 한 줄 · 비율 한 줄), 다른 줄은 없음
export const RowSignalFields = ({ n, row, onChange, messages, describedBy, className }: RowSignalFieldsProps) => {
  const input = (key: SignalKey, name: string, numeric: boolean) => (
    <input
      className="input-inline mx-1"
      inputMode={numeric ? 'numeric' : 'decimal'}
      aria-label={`${n}번 ${name}`}
      aria-invalid={isError(messages[key]) || undefined}
      aria-describedby={describedBy(messages[key])}
      value={row[key]}
      onChange={(e) => onChange(key, numeric ? digitsOnly(e.target.value) : oneDecimal(e.target.value))}
    />
  );

  if (row.strategy === 'MA_CROSS') {
    return (
      <div className={`${VALUE_ROW} ${className}`}>
        <span className={LEAD}>짧은</span>
        {input('shortWindow', '짧은 기간(일)', true)}
        <span className={UNIT}>일 선</span>
        <span className={`${LEAD} ml-2`}>· 긴</span>
        {input('longWindow', '긴 기간(일)', true)}
        <span className={UNIT}>일 선</span>
      </div>
    );
  }
  if (row.strategy !== 'MA_BAND') return null;
  return (
    <div className={`${VALUE_ROW} ${className}`}>
      <span className="flex basis-full items-center sm:basis-auto">
        {input('bandWindow', '평균 기간(일)', true)}
        <span className={UNIT}>일선</span>
      </span>
      <span className={`${LEAD} hidden sm:inline sm:ml-1`}>·</span>
      <span className={LEAD}>위</span>
      <span className={UNIT}>+</span>
      {input('upper', '위 비율(평균 대비 퍼센트)', false)}
      <span className={UNIT}>%</span>
      <span className={`${LEAD} ml-1`}>· 아래</span>
      <span className={UNIT}>-</span>
      {input('lower', '아래 비율(평균 대비 퍼센트)', false)}
      <span className={UNIT}>%</span>
    </div>
  );
};
