import { Fragment, useId } from 'react';
import { Check, ChevronRight } from 'lucide-react';
import { NumberInput } from '../../common/NumberInput';
import type { BacktestOptionsState } from '../shared/backtestOptions';
import { FIELD_GRID } from '../forms/formLayout';
import { SettingField } from './SettingField';
import { RadioGroup } from './radio';
import { useMessageLink } from './messageLink';
import { oneDecimal } from './inputText';
import { isError, type FieldMessage } from './checks';
import { detailParts } from './summary';
import { LEAD, PILL, UNIT, VALUE_ROW } from './styles';

// 세부 설정 칸 오류(수수료 · 직접 입력 환율)
export interface DetailMessages {
  fee?: FieldMessage | null;
  buyFx?: FieldMessage | null;
  endFx?: FieldMessage | null;
}

interface DetailSettingsProps {
  options: BacktestOptionsState;
  open: boolean;
  onToggle: () => void;
  // 배당 재투자를 못 쓰는지
  reinvestLocked?: boolean;
  // 배당 칸 안내(막혔으면 막힌 까닭)
  reinvestNote?: string;
  messages?: DetailMessages;
}

const PERIOD_END_OPTIONS = [
  { value: 'HOLD' as const, label: '끝까지 보유' },
  { value: 'SELL_AT_END' as const, label: '마지막 거래일에 팔기' },
];
const DIVIDEND_OPTIONS = { cash: '현금으로 받기', reinvest: '재투자' };
const FX_OPTIONS = [
  { value: 'auto' as const, label: '자동' },
  { value: 'manual' as const, label: '직접 입력' },
];
const LOCKED_NOTE = '익절 · 손절 · 고점 대비 하락 매도가 있으면 배당은 현금으로';

// 접는 세부 설정: 기간 끝 · 세금 · 배당 · 수수료 · 환율(접힌 줄에 기본값도 값으로, 바꾼 것은 진하게)
export const DetailSettings = ({ options, open, onToggle, reinvestLocked = false, reinvestNote, messages = {} }: DetailSettingsProps) => {
  const panelId = useId();
  const periodEndLabelId = useId();
  const taxLabelId = useId();
  const dividendLabelId = useId();
  const dividendNoteId = useId();
  const fxLabelId = useId();
  const fee = useMessageLink(messages.fee);
  const fxMessage = messages.buyFx ?? messages.endFx;
  const fx = useMessageLink(fxMessage);
  const hasError = [messages.fee, messages.buyFx, messages.endFx].some(isError);
  const dividendNote = reinvestLocked ? (reinvestNote ?? LOCKED_NOTE) : reinvestNote;
  const manual = options.fxMode === 'manual';

  return (
    <div>
      <button
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={onToggle}
        className="mt-4 inline-flex min-h-9 flex-wrap items-baseline gap-x-2 py-1 text-left text-sm text-tx-2 [word-break:keep-all] hover:text-tx-1 sm:min-h-0"
      >
        <span className="inline-flex items-center gap-1 self-center whitespace-nowrap">
          <ChevronRight aria-hidden className={`h-4 w-4 transition-transform motion-reduce:transition-none ${open ? 'rotate-90' : ''}`} />
          세부 설정
        </span>
        {!open && (
          <span className="text-xs">
            {hasError && <span className="text-danger">고칠 칸 있음 · </span>}
            {detailParts(options, reinvestLocked).map((part, index) => (
              <Fragment key={part.text}>
                {index > 0 && ' · '}
                {part.changed ? <b className="font-semibold text-tx-1">{part.text}</b> : <span>{part.text}</span>}
              </Fragment>
            ))}
          </span>
        )}
      </button>

      <div id={panelId} className={open ? `mt-3 ${FIELD_GRID}` : 'hidden'}>
        {open && (
          <>
            <SettingField label="기간이 끝나면" labelId={periodEndLabelId}>
              <RadioGroup
                labelledBy={periodEndLabelId}
                options={PERIOD_END_OPTIONS}
                value={options.periodEnd}
                onChange={options.setPeriodEnd}
                className="flex flex-wrap items-center gap-1.5"
                optionClassName={PILL}
              />
            </SettingField>

            <SettingField label="세금" labelId={taxLabelId}>
              <div role="group" aria-labelledby={taxLabelId} className="flex flex-wrap items-center gap-1.5">
                <button
                  type="button"
                  aria-pressed={options.dividendTax}
                  onClick={() => options.setDividendTax(!options.dividendTax)}
                  className={PILL(options.dividendTax)}
                >
                  {options.dividendTax && <Check aria-hidden className="h-3.5 w-3.5" />}
                  배당세 15%
                </button>
                <button
                  type="button"
                  aria-pressed={options.capitalGainsTax}
                  onClick={() => options.setCapitalGainsTax(!options.capitalGainsTax)}
                  className={PILL(options.capitalGainsTax)}
                >
                  {options.capitalGainsTax && <Check aria-hidden className="h-3.5 w-3.5" />}
                  양도세 22%(연 250만원 공제)
                </button>
              </div>
            </SettingField>

            <SettingField label="배당" labelId={dividendLabelId}>
              <RadioGroup
                labelledBy={dividendLabelId}
                options={[
                  { value: 'cash', label: DIVIDEND_OPTIONS.cash, describedBy: reinvestLocked ? dividendNoteId : undefined },
                  {
                    value: 'reinvest',
                    label: DIVIDEND_OPTIONS.reinvest,
                    disabled: reinvestLocked,
                    describedBy: dividendNote ? dividendNoteId : undefined,
                  },
                ]}
                value={options.reinvestDividends && !reinvestLocked ? 'reinvest' : 'cash'}
                onChange={(value) => options.setReinvestDividends(value === 'reinvest')}
                className="flex flex-wrap items-center gap-1.5"
                optionClassName={(checked) => `${PILL(checked)} aria-disabled:cursor-not-allowed aria-disabled:opacity-45`}
              />
              {dividendNote && (
                <p id={dividendNoteId} className="mt-0.5 text-xs text-tx-2">
                  {dividendNote}
                </p>
              )}
            </SettingField>

            <SettingField label="수수료" message={messages.fee} messageId={fee.messageId}>
              <div className={VALUE_ROW}>
                <input
                  className="input-inline"
                  inputMode="decimal"
                  placeholder="0"
                  aria-label="수수료(퍼센트)"
                  aria-invalid={fee.invalid || undefined}
                  aria-describedby={fee.describedBy}
                  value={options.tradingFeeRate}
                  onChange={(e) => options.setTradingFeeRate(oneDecimal(e.target.value))}
                />
                <span className={UNIT}>%</span>
              </div>
            </SettingField>

            <SettingField label="환율" labelId={fxLabelId} message={fxMessage} messageId={fx.messageId}>
              <RadioGroup
                labelledBy={fxLabelId}
                options={FX_OPTIONS}
                value={options.fxMode}
                onChange={options.setFxMode}
                className="flex flex-wrap items-center gap-1.5"
                optionClassName={PILL}
              />
              {manual && (
                <>
                  <div className={`${VALUE_ROW} mt-1.5`}>
                    <span className={LEAD}>매수</span>
                    <span className={UNIT}>₩</span>
                    <NumberInput
                      variant="inline"
                      value={options.manualPurchaseFxRate}
                      onChange={options.setManualPurchaseFxRate}
                      placeholder="1,350"
                      ariaLabel="매수 환율(원)"
                      showKoreanHint={false}
                      invalid={isError(messages.buyFx)}
                      describedBy={messages.buyFx ? fx.describedBy : undefined}
                    />
                    <span className={`${LEAD} ml-1`}>· 끝 날짜</span>
                    <span className={UNIT}>₩</span>
                    <NumberInput
                      variant="inline"
                      value={options.manualCurrentFxRate}
                      onChange={options.setManualCurrentFxRate}
                      placeholder="1,380"
                      ariaLabel="끝 날짜 환율(원)"
                      showKoreanHint={false}
                      invalid={isError(messages.endFx)}
                      describedBy={messages.endFx ? fx.describedBy : undefined}
                    />
                  </div>
                  <p className="mt-0.5 text-xs text-tx-2">비우면 그 날짜 시장 환율(흐린 숫자는 예시)</p>
                </>
              )}
            </SettingField>
          </>
        )}
      </div>
    </div>
  );
};
