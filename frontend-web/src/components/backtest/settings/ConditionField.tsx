import type { PriceDirection } from '@services/api';
import { DIRECTIONS, DIRECTION_ORDER } from '../shared/priceDirection';
import { RadioGroup } from './radio';
import { SettingField } from './SettingField';
import { useMessageLink } from './messageLink';
import { oneDecimal } from './inputText';
import type { FieldMessage } from './checks';
import { LEAD, PILL, UNIT, VALUE_ROW } from './styles';

const OPTIONS = DIRECTION_ORDER.map((value) => ({ value, label: DIRECTIONS[value].tab }));

export interface ConditionFieldProps {
  // 칩 설정이면 "조건부 매수 조건"
  label?: string;
  direction: PriceDirection;
  onDirectionChange: (direction: PriceDirection) => void;
  percent: string;
  onPercentChange: (percent: string) => void;
  message?: FieldMessage | null;
}

// 조건부 조건 칸: 이름 줄의 하락 · 상승 · 둘 다와 "전날 종가보다 [5] % 내리면"
export const ConditionField = ({
  label = '조건',
  direction,
  onDirectionChange,
  percent,
  onPercentChange,
  message,
}: ConditionFieldProps) => {
  const { messageId, invalid, describedBy } = useMessageLink(message);
  const tail = DIRECTIONS[direction].tail;
  return (
    <SettingField
      label={label}
      message={message}
      messageId={messageId}
      right={
        <RadioGroup
          label="방향"
          options={OPTIONS}
          value={direction}
          onChange={onDirectionChange}
          className="flex flex-wrap gap-1.5"
          optionClassName={PILL}
        />
      }
    >
      <div className={VALUE_ROW}>
        <span className={LEAD}>전날 종가보다</span>
        <input
          className="input-inline ml-1"
          inputMode="decimal"
          aria-label={`전날 종가보다 몇 ${tail}`}
          aria-invalid={invalid || undefined}
          aria-describedby={describedBy}
          value={percent}
          onChange={(e) => onPercentChange(oneDecimal(e.target.value))}
        />
        <span className={UNIT}>{tail}</span>
      </div>
    </SettingField>
  );
};
