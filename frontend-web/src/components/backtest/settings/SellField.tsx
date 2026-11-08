import { ClearButton, SettingField } from './SettingField';
import { useMessageLink } from './messageLink';
import { oneDecimal } from './inputText';
import type { FieldMessage } from './checks';
import { LEAD, UNIT, VALUE_ROW } from './styles';

export interface SellFieldProps {
  takeProfit: string;
  onTakeProfitChange: (value: string) => void;
  stopLoss: string;
  onStopLossChange: (value: string) => void;
  message?: FieldMessage | null;
}

// 매도 조건 칸: 익절 +[ ]% · 손절 -[ ]%(빈 칸은 부호 · 단위 없이 안 함)
export const SellField = ({ takeProfit, onTakeProfitChange, stopLoss, onStopLossChange, message }: SellFieldProps) => {
  const { messageId, invalid, describedBy } = useMessageLink(message);
  const a11y = { 'aria-invalid': invalid || undefined, 'aria-describedby': describedBy };
  const clear = () => {
    onTakeProfitChange('');
    onStopLossChange('');
  };
  return (
    <SettingField
      label="매도 조건"
      message={message}
      messageId={messageId}
      right={(takeProfit || stopLoss) && <ClearButton label="익절 · 손절 비우기" onClick={clear} />}
    >
      <div className={VALUE_ROW}>
        <span className={LEAD}>익절</span>
        {takeProfit && <span className={UNIT}>+</span>}
        <input
          className="input-inline"
          inputMode="decimal"
          placeholder="안 함"
          aria-label="익절(평균 단가 대비 퍼센트)"
          {...a11y}
          value={takeProfit}
          onChange={(e) => onTakeProfitChange(oneDecimal(e.target.value))}
        />
        {takeProfit && <span className={UNIT}>%</span>}
        <span className={`${LEAD} ml-1`}>· 손절</span>
        {stopLoss && <span className={UNIT}>-</span>}
        <input
          className="input-inline"
          inputMode="decimal"
          placeholder="안 함"
          aria-label="손절(평균 단가 대비 퍼센트)"
          {...a11y}
          value={stopLoss}
          onChange={(e) => onStopLossChange(oneDecimal(e.target.value))}
        />
        {stopLoss && <span className={UNIT}>%</span>}
      </div>
    </SettingField>
  );
};
