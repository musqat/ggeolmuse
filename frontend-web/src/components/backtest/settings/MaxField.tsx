import { ClearButton, SettingField } from './SettingField';
import { useMessageLink } from './messageLink';
import { digitsOnly } from './inputText';
import type { FieldMessage } from './checks';
import { UNIT, VALUE_ROW } from './styles';

export interface MaxFieldProps {
  value: string;
  onChange: (value: string) => void;
  // 비었을 때의 뜻(예산 한도까지 · 원금 한도까지 · 제한 없음)
  placeholder: string;
  message?: FieldMessage | null;
}

// 최대 횟수 칸(비우면 placeholder 의 한도, 빈 칸은 단위를 숨김)
export const MaxField = ({ value, onChange, placeholder, message }: MaxFieldProps) => {
  const { messageId, invalid, describedBy } = useMessageLink(message);
  return (
    <SettingField
      label="최대 횟수"
      message={message}
      messageId={messageId}
      right={value && <ClearButton label="최대 횟수 비우기" onClick={() => onChange('')} />}
    >
      <div className={VALUE_ROW}>
        <input
          className="input-inline"
          inputMode="numeric"
          placeholder={placeholder}
          aria-label="최대 횟수(회)"
          aria-invalid={invalid || undefined}
          aria-describedby={describedBy}
          value={value}
          onChange={(e) => onChange(digitsOnly(e.target.value))}
        />
        {value && <span className={UNIT}>회</span>}
      </div>
    </SettingField>
  );
};
