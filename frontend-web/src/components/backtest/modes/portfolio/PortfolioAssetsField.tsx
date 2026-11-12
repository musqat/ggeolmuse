import { useLayoutEffect, useRef } from 'react';
import type { PortfolioAllocation } from '@services/api';
import StockSearchInput from '../../../common/StockSearchInput';
import { NumberInput } from '../../../common/NumberInput';
import { formatKrw, formatNumber, formatReadableWon } from '../../../../utils/format';
import { ClearButton, SettingField } from '../../settings/SettingField';
import { RadioGroup } from '../../settings/radio';
import { useMessageLink } from '../../settings/messageLink';
import { oneDecimal } from '../../settings/inputText';
import { isError, type FieldMessage, type PortfolioRowMessages } from '../../settings/checks';
import { AFTER_VALUE, LEAD, SEG_GROUP, SEG_OPTION, UNIT, VALUE_ROW } from '../../settings/styles';
import {
  MAX_ROWS,
  amountShares,
  newRow,
  isWeightSumOk,
  rowAmounts,
  weightSum,
  type PortfolioRow,
} from '../../shared/portfolioRows';

export interface PortfolioAssetsFieldProps {
  rows: PortfolioRow[];
  onRowsChange: (rows: PortfolioRow[]) => void;
  allocation: PortfolioAllocation;
  onAllocationChange: (allocation: PortfolioAllocation) => void;
  total: string;
  onTotalChange: (total: string) => void;
  // 적립식이면 금액 · 총액이 회당
  perRound: boolean;
  supportedSymbols: string[];
  messages: PortfolioRowMessages[];
  totalMessage: FieldMessage | null;
}

const ALLOCATIONS: { value: PortfolioAllocation; label: string }[] = [
  { value: 'AMOUNT', label: '금액' },
  { value: 'WEIGHT', label: '비율' },
];

// 휴대폰 · 태블릿은 줄마다 카드(첫 줄 종목 + 지우기, 둘째 줄 금액 · 비율과 익절 · 손절), 넓은 화면은 한 줄
const ROW =
  'grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-2 gap-y-0.5 rounded-lg border border-line/70 px-2.5 py-1.5 ' +
  'lg:grid-cols-[minmax(0,1fr)_minmax(0,30rem)_2.25rem] lg:rounded-none lg:border-0 lg:border-t lg:border-line/50 lg:px-0 lg:first:border-t-0';

// 좁으면 익절 · 손절이 금액 · 비율 아래 줄로, 넓은 화면은 두 칸
const VALUES =
  'col-span-2 row-start-2 flex min-w-0 flex-wrap items-center gap-x-4 gap-y-0.5 ' +
  'lg:col-span-1 lg:col-start-2 lg:row-start-1 lg:grid lg:grid-cols-[minmax(0,16rem)_minmax(0,13rem)] lg:gap-x-3';

const percentText = (value: number) => `${formatNumber(value, { maximumFractionDigits: 1 })}%`;

const NO_MESSAGES: PortfolioRowMessages = { symbol: null, value: null, takeProfit: null, stopLoss: null };

// 포트폴리오 종목 칸: 이름 줄 오른쪽 금액 | 비율, 비율이면 총액 줄, 줄마다 [종목] [₩ 금액 · 몫 % 또는 비율 % · 어림 금액] [익절 +% · 손절 -%] [지우기], 아래 "＋ 종목 더하기"(10줄이면 "최대 10개") · 합계 줄
export const PortfolioAssetsField = ({
  rows,
  onRowsChange,
  allocation,
  onAllocationChange,
  total,
  onTotalChange,
  perRound,
  supportedSymbols,
  messages,
  totalMessage,
}: PortfolioAssetsFieldProps) => {
  const weighted = allocation === 'WEIGHT';
  const rowMessages = messages.flatMap((row) => [row.symbol, row.value, row.takeProfit, row.stopLoss]);
  const message = [weighted ? totalMessage : null, ...rowMessages].find(Boolean) ?? null;
  const { messageId, describedBy } = useMessageLink(message);
  const listRef = useRef<HTMLDivElement>(null);
  // 다시 그린 뒤 포커스할 줄(그 줄의 종목 칸)
  const focusAfter = useRef<number | null>(null);

  useLayoutEffect(() => {
    const index = focusAfter.current;
    if (index === null) return;
    focusAfter.current = null;
    listRef.current?.querySelectorAll<HTMLElement>('[data-row]')[index]?.querySelector('input')?.focus();
  });

  const change = (index: number, key: keyof PortfolioRow, value: string) =>
    onRowsChange(rows.map((row, i) => (i === index ? { ...row, [key]: value } : row)));
  const add = () => {
    focusAfter.current = rows.length;
    onRowsChange([...rows, newRow()]);
  };
  // 지운 자리의 줄(없으면 앞 줄)로
  const remove = (index: number) => {
    focusAfter.current = index < rows.length - 1 ? index : index - 1;
    onRowsChange(rows.filter((_, i) => i !== index));
  };

  // 그 칸이 칸 아래 한 줄을 낸 칸이면 그 줄과 이음
  const link = (own: FieldMessage | null) => ({
    invalid: isError(own),
    describedBy: own && own === message ? describedBy : undefined,
  });

  const amounts = rowAmounts(rows, allocation, total);
  const shares = amountShares(rows);
  const totalLabel = perRound ? '회당 총액' : '총액';
  const sumOk = isWeightSumOk(rows);
  const sumText = weighted
    ? `합계 ${formatNumber(weightSum(rows), { maximumFractionDigits: 2 })}%${sumOk ? '' : ' · 100% 로 맞추기'}`
    : `${perRound ? '회당 합계' : '합계'} ${formatKrw(amounts.reduce((a, b) => a + b, 0))}`;

  return (
    <SettingField
      label="종목"
      message={message}
      messageId={messageId}
      right={
        <RadioGroup
          label="금액 · 비율"
          options={ALLOCATIONS}
          value={allocation}
          onChange={onAllocationChange}
          className={SEG_GROUP}
          optionClassName={SEG_OPTION}
        />
      }
    >
      {weighted && (
        <div className={`${VALUE_ROW} mb-1.5`}>
          <span className={`${LEAD} mr-1`}>{totalLabel}</span>
          <span className={UNIT}>₩</span>
          <NumberInput
            variant="inline"
            value={total}
            onChange={onTotalChange}
            placeholder=""
            ariaLabel={`${totalLabel}(원)`}
            {...link(totalMessage)}
          />
        </div>
      )}
      <div ref={listRef} className="space-y-2 lg:space-y-0">
        {rows.map((row, index) => {
          const n = index + 1;
          const own = messages[index] ?? NO_MESSAGES;
          // 익절 · 손절 퍼센트 칸(비면 안 함)
          const sellInput = (key: 'takeProfit' | 'stopLoss', name: string, ownMessage: FieldMessage | null) => (
            <input
              className="input-inline"
              inputMode="decimal"
              placeholder="안 함"
              aria-label={`${n}번 ${name}(평균 단가 대비 퍼센트)`}
              aria-invalid={isError(ownMessage) || undefined}
              aria-describedby={link(ownMessage).describedBy}
              value={row[key]}
              onChange={(e) => change(index, key, oneDecimal(e.target.value))}
            />
          );
          return (
            <div key={index} data-row className={ROW}>
              <div className="relative col-start-1 row-start-1 flex min-w-0 items-center">
                <StockSearchInput
                  variant="inline"
                  value={row.symbol}
                  onChange={(symbol) => change(index, 'symbol', symbol)}
                  supportedSymbols={supportedSymbols}
                  placeholder="티커 · 영문 이름"
                  ariaLabel={`${n}번 종목`}
                  {...link(own.symbol)}
                />
              </div>
              <div className={VALUES}>
                <div className={`${VALUE_ROW} flex-1 basis-36`}>
                  {weighted ? (
                    <>
                      <input
                        className="input-inline"
                        inputMode="decimal"
                        aria-label={`${n}번 비율(퍼센트)`}
                        aria-invalid={isError(own.value) || undefined}
                        aria-describedby={link(own.value).describedBy}
                        value={row.weight}
                        onChange={(e) => change(index, 'weight', oneDecimal(e.target.value))}
                      />
                      <span className={UNIT}>%</span>
                      {amounts[index] > 0 && <span className={AFTER_VALUE}>≈ {formatReadableWon(amounts[index])}</span>}
                    </>
                  ) : (
                    <>
                      <span className={UNIT}>₩</span>
                      <NumberInput
                        variant="inline"
                        value={row.amount}
                        onChange={(amount) => change(index, 'amount', amount)}
                        placeholder=""
                        showKoreanHint={false}
                        ariaLabel={`${n}번 금액(원)`}
                        {...link(own.value)}
                      />
                      {shares[index] > 0 && <span className={AFTER_VALUE}>{percentText(shares[index])}</span>}
                    </>
                  )}
                </div>
                <div className={`${VALUE_ROW} basis-52`}>
                  <span className={LEAD}>익절</span>
                  {row.takeProfit && <span className={UNIT}>+</span>}
                  {sellInput('takeProfit', '익절', own.takeProfit)}
                  {row.takeProfit && <span className={UNIT}>%</span>}
                  <span className={`${LEAD} ml-1`}>· 손절</span>
                  {row.stopLoss && <span className={UNIT}>-</span>}
                  {sellInput('stopLoss', '손절', own.stopLoss)}
                  {row.stopLoss && <span className={UNIT}>%</span>}
                </div>
              </div>
              {rows.length > 1 && (
                <span className="col-start-2 row-start-1 lg:col-start-3">
                  <ClearButton label={`${n}번 종목 지우기`} onClick={() => remove(index)} />
                </span>
              )}
            </div>
          );
        })}
      </div>
      <div className="mt-1 flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
        {rows.length < MAX_ROWS ? (
          <button type="button" onClick={add} className="min-h-9 px-1.5 text-[13px] text-tx-2 hover:text-tx-1 sm:min-h-0">
            ＋ 종목 더하기
          </button>
        ) : (
          <span className="text-[13px] text-tx-3">최대 {MAX_ROWS}개</span>
        )}
        <span data-testid="portfolio-sum" className={`text-[13px] ${weighted && !sumOk ? 'text-warning' : 'text-tx-2'}`}>
          {sumText}
        </span>
      </div>
    </SettingField>
  );
};
