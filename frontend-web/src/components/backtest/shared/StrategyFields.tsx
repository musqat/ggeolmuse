import React from 'react';
import StockSearchInput from '../../common/StockSearchInput';
import { DateField } from '../../common/DateField';
import { getTodayString } from '../../../utils/dateUtils';
import { FxModeToggle } from './FxModeToggle';
import { DividendFeeOptions } from './DividendFeeOptions';
import { FxDataWarning } from './FxDataWarning';
import type { BacktestOptionsState } from './backtestOptions';
import type { PriceDirection } from '@services/api';
import { DIRECTIONS, DIRECTION_ORDER } from './priceDirection';

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

interface ConditionFieldsProps {
  direction: PriceDirection;
  onDirectionChange: (direction: PriceDirection) => void;
  value: string;
  onChange: (value: string) => void;
}

// 조건부 방향(하락 · 상승 · 둘 다)과 비율 칸
export const ConditionFields: React.FC<ConditionFieldsProps> = ({ direction, onDirectionChange, value, onChange }) => (
  <>
    <FormField label="조건">
      <div role="group" aria-label="조건 방향" className="grid grid-cols-3 gap-1 rounded-btn border border-line-strong p-1">
        {DIRECTION_ORDER.map((option) => (
          <button
            key={option}
            type="button"
            aria-pressed={direction === option}
            onClick={() => onDirectionChange(option)}
            className={`py-1.5 rounded-md text-sm font-medium ${
              direction === option ? 'bg-brand-bg text-brand-dark border border-brand-border' : 'text-tx-2 hover:text-tx-1'
            }`}
          >
            {DIRECTIONS[option].tab}
          </button>
        ))}
      </div>
    </FormField>
    <FormField label={`${DIRECTIONS[direction].rate} (%)`}>
      <input
        type="number"
        aria-label={`${DIRECTIONS[direction].rate} (%)`}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="5"
        step="1"
        min="0.1"
        max="100"
        className={INPUT_CLASS}
      />
      <p className="text-xs text-tx-2 mt-1">{DIRECTIONS[direction].help}</p>
    </FormField>
  </>
);

interface SellFieldsProps {
  takeProfit: string;
  onTakeProfitChange: (value: string) => void;
  stopLoss: string;
  onStopLossChange: (value: string) => void;
}

// 익절 · 손절 퍼센트 칸(비우면 안 함, 보유분 평균 단가 기준 전량 매도)
export const SellFields: React.FC<SellFieldsProps> = ({ takeProfit, onTakeProfitChange, stopLoss, onStopLossChange }) => (
  <>
    <FormField label="익절 (%)">
      <input
        type="number"
        aria-label="익절 (%)"
        value={takeProfit}
        onChange={(e) => onTakeProfitChange(e.target.value)}
        placeholder="비우면 안 함"
        step="1"
        min="0.1"
        className={INPUT_CLASS}
      />
      <p className="text-xs text-tx-2 mt-1">평균 단가보다 이만큼 오르면 전량 매도</p>
    </FormField>
    <FormField label="손절 (%)">
      <input
        type="number"
        aria-label="손절 (%)"
        value={stopLoss}
        onChange={(e) => onStopLossChange(e.target.value)}
        placeholder="비우면 안 함"
        step="1"
        min="0.1"
        max="99.9"
        className={INPUT_CLASS}
      />
      <p className="text-xs text-tx-2 mt-1">평균 단가보다 이만큼 내리면 전량 매도</p>
    </FormField>
  </>
);

interface StrategyOptionsProps {
  options: BacktestOptionsState;
  startDate: string;
  purchaseLabel: string;
  currentLabel: string;
  // 익절 · 손절을 켜서 배당 재투자를 못 쓰는지
  reinvestLocked?: boolean;
}

// 환율 데이터 경고 · 환율 설정 · 배당 · 수수료 옵션
export const StrategyOptions: React.FC<StrategyOptionsProps> = ({
  options,
  startDate,
  purchaseLabel,
  currentLabel,
  reinvestLocked = false,
}) => (
  <>
    <FxDataWarning startDate={startDate} />
    <FxModeToggle options={options} purchaseLabel={purchaseLabel} currentLabel={currentLabel} />
    <DividendFeeOptions options={options} reinvestLocked={reinvestLocked} />
  </>
);
