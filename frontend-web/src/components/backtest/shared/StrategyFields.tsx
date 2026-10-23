import React from 'react';
import StockSearchInput from '../../common/StockSearchInput';
import { DateField } from '../../common/DateField';
import { getTodayString } from '../../../utils/dateUtils';
import { FxModeToggle } from './FxModeToggle';
import { DividendFeeOptions } from './DividendFeeOptions';
import { FxDataWarning } from './FxDataWarning';
import type { BacktestOptionsState } from './backtestOptions';

const INPUT_CLASS = 'w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand';

// 전략 폼의 이름표와 입력 칸
export const FormField: React.FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <div>
    <label className="block text-sm font-medium text-tx-1 mb-2">{label}</label>
    {children}
  </div>
);

interface SymbolFieldProps {
  value: string;
  onChange: (symbol: string) => void;
  supportedSymbols: string[];
}

// 종목 검색 칸
export const SymbolField: React.FC<SymbolFieldProps> = ({ value, onChange, supportedSymbols }) => (
  <FormField label="종목">
    <StockSearchInput
      value={value}
      onChange={onChange}
      supportedSymbols={supportedSymbols}
      placeholder="종목 검색"
    />
  </FormField>
);

interface PeriodFieldsProps {
  start: string;
  end: string;
  onStartChange: (date: string) => void;
  onEndChange: (date: string) => void;
}

// 시작일 · 종료일 칸(종료일이 비어 있으면 오늘로 보이고 오늘로 계산)
export const PeriodFields: React.FC<PeriodFieldsProps> = ({ start, end, onStartChange, onEndChange }) => {
  const today = getTodayString();

  return (
    <>
      <FormField label="시작일">
        <DateField
          value={start}
          onChange={onStartChange}
          max={end || today}
          testId="date-start"
        />
      </FormField>
      <FormField label="종료일">
        <DateField
          value={end || today}
          onChange={onEndChange}
          min={start}
          max={today}
          testId="date-end"
        />
      </FormField>
    </>
  );
};

interface DcaScheduleFieldsProps {
  purchaseDay: string;
  interval: string;
  onPurchaseDayChange: (day: string) => void;
  onIntervalChange: (interval: string) => void;
}

// 적립식 매월 투자일(1~28일) · 투자 주기 칸
export const DcaScheduleFields: React.FC<DcaScheduleFieldsProps> = ({
  purchaseDay,
  interval,
  onPurchaseDayChange,
  onIntervalChange,
}) => (
  <>
    <FormField label="매월 투자일">
      <input
        type="number"
        value={purchaseDay}
        onChange={(e) => onPurchaseDayChange(e.target.value)}
        placeholder="15"
        min="1"
        max="28"
        className={INPUT_CLASS}
      />
      <p className="text-xs text-tx-2 mt-1">1~28일</p>
    </FormField>
    <FormField label="투자 주기">
      <select
        value={interval}
        onChange={(e) => onIntervalChange(e.target.value)}
        className={INPUT_CLASS}
      >
        <option value="1">매월 (1개월)</option>
        <option value="2">2개월마다</option>
        <option value="3">분기마다 (3개월)</option>
        <option value="6">반기마다 (6개월)</option>
      </select>
    </FormField>
  </>
);

// 조건부 하락률 칸
export const DropPercentField: React.FC<{ value: string; onChange: (value: string) => void }> = ({ value, onChange }) => (
  <FormField label="하락률 (%)">
    <input
      type="number"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder="5"
      step="1"
      min="0.1"
      max="100"
      className={INPUT_CLASS}
    />
    <p className="text-xs text-tx-2 mt-1">가격이 이만큼 하락 시 매수</p>
  </FormField>
);

interface StrategyOptionsProps {
  options: BacktestOptionsState;
  startDate: string;
  purchaseLabel: string;
  currentLabel: string;
}

// 환율 데이터 경고 · 환율 설정 · 배당 · 수수료 옵션
export const StrategyOptions: React.FC<StrategyOptionsProps> = ({ options, startDate, purchaseLabel, currentLabel }) => (
  <>
    <FxDataWarning startDate={startDate} />
    <FxModeToggle options={options} purchaseLabel={purchaseLabel} currentLabel={currentLabel} />
    <DividendFeeOptions options={options} />
  </>
);
