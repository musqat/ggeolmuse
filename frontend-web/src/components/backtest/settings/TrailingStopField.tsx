import { ClearButton, SettingField } from './SettingField';
import { useMessageLink } from './messageLink';
import { oneDecimal } from './inputText';
import { isError, type FieldMessage } from './checks';
import { LEAD, UNIT, VALUE_ROW } from './styles';

export interface TrailingStopFieldProps {
  stop: string;
  onStopChange: (value: string) => void;
  sell: string;
  onSellChange: (value: string) => void;
  messages: { stop: FieldMessage | null; sell: FieldMessage | null };
}

// 트레일링 스탑 칸: 값 "-[10]% 마다 · 보유의 [50]% 팔기"(둘 다 비면 안 함만, 오류 글은 앞 칸 것 한 줄)
export const TrailingStopField = ({ stop, onStopChange, sell, onSellChange, messages }: TrailingStopFieldProps) => {
  const message = messages.stop ?? messages.sell;
  const { messageId, describedBy } = useMessageLink(message);
  const input = (
    name: string,
    value: string,
    onChange: (value: string) => void,
    own: FieldMessage | null,
    placeholder: string,
  ) => (
    <input
      className="input-inline"
      inputMode="decimal"
      placeholder={placeholder}
      aria-label={name}
      aria-invalid={isError(own) || undefined}
      aria-describedby={own && own === message ? describedBy : undefined}
      value={value}
      onChange={(e) => onChange(oneDecimal(e.target.value))}
    />
  );
  const filled = Boolean(stop || sell);
  const clear = () => {
    onStopChange('');
    onSellChange('');
  };

  return (
    <SettingField
      label="고점 대비 하락 매도"
      message={message}
      messageId={messageId}
      right={filled && <ClearButton label="고점 대비 하락 매도 비우기" onClick={clear} />}
    >
      <div className={VALUE_ROW}>
        {stop && <span className={UNIT}>-</span>}
        {input('고점 대비 하락 비율(퍼센트)', stop, onStopChange, messages.stop, '안 함')}
        {stop && (
          <>
            <span className={UNIT}>%</span>
            <span className={LEAD}>마다</span>
          </>
        )}
        {filled && (
          <>
            <span className={`${LEAD} ml-1`}>· 보유의</span>
            {input('고점 대비 하락 때 팔 비율(보유 대비 퍼센트)', sell, onSellChange, messages.sell, '100')}
            <span className={UNIT}>%</span>
            <span className={LEAD}>팔기</span>
          </>
        )}
      </div>
    </SettingField>
  );
};
