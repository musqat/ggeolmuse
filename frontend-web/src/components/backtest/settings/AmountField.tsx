import { NumberInput } from '../../common/NumberInput';
import { formatReadableWon } from '../../../utils/format';
import type { AmountUnit, AmountUnitState } from '../shared/amountUnit';
import { RadioGroup } from './radio';
import { SettingField } from './SettingField';
import { useMessageLink } from './messageLink';
import { digitsOnly } from './inputText';
import type { FieldMessage } from './checks';
import { AFTER_VALUE, SEG_GROUP, SEG_OPTION, UNIT, VALUE_ROW } from './styles';

const UNIT_OPTIONS: { value: AmountUnit; label: string }[] = [
  { value: 'AMOUNT', label: '금액' },
  { value: 'SHARES', label: '주식 수' },
];

export interface AmountFieldProps {
  // 모드마다 무슨 돈인지(금액 · 회당 금액 · 종목마다 금액 · 전략마다 원금)
  label: string;
  // 있으면 금액 | 주식 수 고르기(주식 수 칸 이름)
  sharesLabel?: string;
  amount: string;
  onAmountChange: (amount: string) => void;
  amountUnit?: AmountUnitState;
  // 주식 수 한 회차의 추정 금액(원)
  perRound?: number;
  message?: FieldMessage | null;
}

// 금액 칸: ₩ 입력과 읽기 쉬운 금액, 주식 수 단위면 몇 주와 시작일 값 기준 추정 금액
export const AmountField = ({ label, sharesLabel, amount, onAmountChange, amountUnit, perRound, message }: AmountFieldProps) => {
  // 주식 수 단위일 때만 값이 있음
  const shareUnit = sharesLabel && amountUnit?.unit === 'SHARES' ? amountUnit : undefined;
  const name = shareUnit && sharesLabel ? sharesLabel : label;
  const { messageId, invalid, describedBy } = useMessageLink(message);

  return (
    <SettingField
      label={name}
      message={message}
      messageId={messageId}
      right={
        amountUnit &&
        sharesLabel && (
          <RadioGroup
            label="단위"
            options={UNIT_OPTIONS}
            value={amountUnit.unit}
            onChange={amountUnit.setUnit}
            className={SEG_GROUP}
            optionClassName={SEG_OPTION}
          />
        )
      }
    >
      {shareUnit ? (
        <div className={VALUE_ROW}>
          <input
            className="input-inline"
            inputMode="numeric"
            aria-label={`${name}(주)`}
            aria-invalid={invalid || undefined}
            aria-describedby={describedBy}
            value={shareUnit.shares}
            onChange={(e) => shareUnit.setShares(digitsOnly(e.target.value))}
          />
          <span className={UNIT}>주</span>
          {perRound !== undefined && <span className={AFTER_VALUE}>≈ {formatReadableWon(perRound)}(시작일 값 기준)</span>}
        </div>
      ) : (
        <div className={VALUE_ROW}>
          <span className={UNIT}>₩</span>
          <NumberInput
            variant="inline"
            value={amount}
            onChange={onAmountChange}
            placeholder=""
            ariaLabel={`${label}(원)`}
            invalid={invalid}
            describedBy={describedBy}
          />
        </div>
      )}
    </SettingField>
  );
};
