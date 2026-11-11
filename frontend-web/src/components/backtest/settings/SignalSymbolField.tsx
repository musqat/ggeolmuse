import { useId } from 'react';
import StockSearchInput from '../../common/StockSearchInput';
import { SIGNAL_INDEXES, type SignalChoice } from '../shared/signalSymbol';
import { RadioGroup } from './radio';
import { SettingField } from './SettingField';
import { useMessageLink } from './messageLink';
import type { FieldMessage } from './checks';
import { PILL, VALUE_ROW } from './styles';

const OPTIONS: { value: SignalChoice; label: string }[] = [
  { value: 'same', label: '매매 종목과 같게' },
  ...SIGNAL_INDEXES.map(({ symbol, name }) => ({ value: symbol, label: name })),
  { value: 'other', label: '다른 종목' },
];

export interface SignalSymbolFieldProps {
  choice: SignalChoice;
  onChoiceChange: (choice: SignalChoice) => void;
  // 다른 종목 칸의 종목
  other: string;
  onOtherChange: (symbol: string) => void;
  supportedSymbols: string[];
  message?: FieldMessage | null;
}

// 신호 종목 칸: 매매 종목과 같게 · S&P 500 · 나스닥 100 · 다른 종목을 하나만 고름(다른 종목은 목록에서 고르는 종목 칸)
export const SignalSymbolField = ({
  choice,
  onChoiceChange,
  other,
  onOtherChange,
  supportedSymbols,
  message,
}: SignalSymbolFieldProps) => {
  const labelId = useId();
  const { messageId, invalid, describedBy } = useMessageLink(message);
  return (
    <SettingField label="신호 종목" labelId={labelId} message={message} messageId={messageId}>
      <RadioGroup
        labelledBy={labelId}
        options={OPTIONS}
        value={choice}
        onChange={onChoiceChange}
        className="flex flex-wrap gap-1.5 py-0.5"
        optionClassName={PILL}
      />
      {choice === 'other' && (
        <div className={`${VALUE_ROW} mt-1.5`}>
          <StockSearchInput
            variant="inline"
            value={other}
            onChange={onOtherChange}
            supportedSymbols={supportedSymbols}
            placeholder="티커 · 영문 이름"
            ariaLabel="신호 종목"
            invalid={invalid}
            describedBy={describedBy}
          />
        </div>
      )}
    </SettingField>
  );
};
