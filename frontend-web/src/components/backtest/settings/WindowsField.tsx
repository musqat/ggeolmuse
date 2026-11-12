import { SettingField } from './SettingField';
import { useMessageLink } from './messageLink';
import { digitsOnly } from './inputText';
import { isError, type FieldMessage } from './checks';
import { LEAD, UNIT, VALUE_ROW } from './styles';

export interface WindowsFieldProps {
  // 칩 설정이면 "이동평균 교차 2 기간"
  label?: string;
  short: string;
  onShortChange: (value: string) => void;
  long: string;
  onLongChange: (value: string) => void;
  messages: { short: FieldMessage | null; long: FieldMessage | null };
}

// 이동평균 기간 칸: "짧은 [20]일 선 · 긴 [60]일 선"(오류 글은 짧은 칸 것 먼저 한 줄)
export const WindowsField = ({ label = '이동평균 기간', short, onShortChange, long, onLongChange, messages }: WindowsFieldProps) => {
  const message = messages.short ?? messages.long;
  const { messageId, describedBy } = useMessageLink(message);
  const input = (name: string, value: string, onChange: (value: string) => void, own: FieldMessage | null) => (
    <input
      className="input-inline mx-1"
      inputMode="numeric"
      aria-label={`${name}(일)`}
      aria-invalid={isError(own) || undefined}
      aria-describedby={own ? describedBy : undefined}
      value={value}
      onChange={(e) => onChange(digitsOnly(e.target.value))}
    />
  );

  return (
    <SettingField label={label} right={<span>거래일 수정 종가 평균</span>} message={message} messageId={messageId}>
      <div className={VALUE_ROW}>
        <span className={LEAD}>짧은</span>
        {input('짧은 기간', short, onShortChange, messages.short)}
        <span className={UNIT}>일 선</span>
        <span className={`${LEAD} ml-2`}>· 긴</span>
        {input('긴 기간', long, onLongChange, messages.long)}
        <span className={UNIT}>일 선</span>
      </div>
    </SettingField>
  );
};
