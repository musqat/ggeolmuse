import { NumberInput } from '../../common/NumberInput';
import { ClearButton, SettingField } from './SettingField';
import { useMessageLink } from './messageLink';
import type { FieldMessage } from './checks';
import { UNIT, VALUE_ROW } from './styles';

export interface BudgetFieldProps {
  budget: string;
  onChange: (budget: string) => void;
  message?: FieldMessage | null;
}

// 예산 칸(비우면 제한 없음, 빈 칸은 ₩ 를 숨김)
export const BudgetField = ({ budget, onChange, message }: BudgetFieldProps) => {
  const { messageId, invalid, describedBy } = useMessageLink(message);
  return (
    <SettingField
      label="예산"
      message={message}
      messageId={messageId}
      right={budget && <ClearButton label="예산 비우기" onClick={() => onChange('')} />}
    >
      <div className={VALUE_ROW}>
        {budget && <span className={UNIT}>₩</span>}
        <NumberInput
          variant="inline"
          value={budget}
          onChange={onChange}
          placeholder="제한 없음"
          ariaLabel="예산(원)"
          invalid={invalid}
          describedBy={describedBy}
        />
      </div>
    </SettingField>
  );
};
