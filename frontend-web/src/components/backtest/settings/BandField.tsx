import { SettingField } from './SettingField';
import { useMessageLink } from './messageLink';
import { digitsOnly, oneDecimal } from './inputText';
import { isError, type FieldMessage } from './checks';
import { LEAD, UNIT, VALUE_ROW } from './styles';

export interface BandFieldProps {
  // 칩 설정이면 "밴드 2 설정"
  label?: string;
  window: string;
  onWindowChange: (value: string) => void;
  upper: string;
  onUpperChange: (value: string) => void;
  lower: string;
  onLowerChange: (value: string) => void;
  messages: { window: FieldMessage | null; upper: FieldMessage | null; lower: FieldMessage | null };
}

// 이동평균 밴드 칸: "[200]일선 · 위 +[2.5]% · 아래 -[2.5]%"(휴대폰은 평균 기간 한 줄 · 비율 한 줄, 오류 글은 앞 칸 것 한 줄)
export const BandField = ({
  label = '밴드',
  window,
  onWindowChange,
  upper,
  onUpperChange,
  lower,
  onLowerChange,
  messages,
}: BandFieldProps) => {
  const message = messages.window ?? messages.upper ?? messages.lower;
  const { messageId, describedBy } = useMessageLink(message);
  const input = (
    name: string,
    value: string,
    onChange: (value: string) => void,
    own: FieldMessage | null,
    numeric: boolean,
  ) => (
    <input
      className="input-inline mx-1"
      inputMode={numeric ? 'numeric' : 'decimal'}
      aria-label={name}
      aria-invalid={isError(own) || undefined}
      aria-describedby={own ? describedBy : undefined}
      value={value}
      onChange={(e) => onChange(numeric ? digitsOnly(e.target.value) : oneDecimal(e.target.value))}
    />
  );

  return (
    <SettingField label={label} right={<span>거래일 수정 종가 평균</span>} message={message} messageId={messageId}>
      <div className={VALUE_ROW}>
        <span className="flex basis-full items-center sm:basis-auto">
          {input('평균 기간(일)', window, onWindowChange, messages.window, true)}
          <span className={UNIT}>일선</span>
        </span>
        <span className={`${LEAD} hidden sm:inline sm:ml-1`}>·</span>
        <span className={LEAD}>위</span>
        <span className={UNIT}>+</span>
        {input('위 비율(평균 대비 퍼센트)', upper, onUpperChange, messages.upper, false)}
        <span className={UNIT}>%</span>
        <span className={`${LEAD} ml-1`}>· 아래</span>
        <span className={UNIT}>-</span>
        {input('아래 비율(평균 대비 퍼센트)', lower, onLowerChange, messages.lower, false)}
        <span className={UNIT}>%</span>
      </div>
    </SettingField>
  );
};
