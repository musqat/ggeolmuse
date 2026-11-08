import { useRef } from 'react';
import { X } from 'lucide-react';
import StockSearchInput from '../../common/StockSearchInput';
import { symbolName } from '../../../utils/symbolSearch';
import { SettingField } from './SettingField';
import { useMessageLink } from './messageLink';
import type { FieldMessage } from './checks';
import { VALUE_ROW } from './styles';

// 종목 비교에 담을 수 있는 종목 수
const MAX_SYMBOLS = 10;

export interface SymbolFieldProps {
  symbol: string;
  onChange: (symbol: string) => void;
  supportedSymbols: string[];
  message?: FieldMessage | null;
}

// 한 종목 칸(목록에서 고른 종목만, 오른쪽에 회사 이름)
export const SymbolField = ({ symbol, onChange, supportedSymbols, message }: SymbolFieldProps) => {
  const { messageId, invalid, describedBy } = useMessageLink(message);
  return (
    <SettingField label="종목" message={message} messageId={messageId}>
      <div className={VALUE_ROW}>
        <StockSearchInput
          variant="inline"
          value={symbol}
          onChange={onChange}
          supportedSymbols={supportedSymbols}
          placeholder="티커 · 영문 이름"
          ariaLabel="종목"
          invalid={invalid}
          describedBy={describedBy}
        />
      </div>
    </SettingField>
  );
};

export interface SymbolsFieldProps {
  symbols: string[];
  onAdd: (symbol: string) => void;
  onRemove: (symbol: string) => void;
  supportedSymbols: string[];
  message?: FieldMessage | null;
}

// 종목 비교 칸: 담은 종목 칩(회사 이름 · 빼기)과 더하기 칸, 10개까지
export const SymbolsField = ({ symbols, onAdd, onRemove, supportedSymbols, message }: SymbolsFieldProps) => {
  const { messageId, invalid, describedBy } = useMessageLink(message);
  const chipsRef = useRef<HTMLDivElement>(null);

  // 뺀 자리의 다음(없으면 앞) 종목 빼기 단추로, 남은 종목이 없으면 더하기 칸으로 포커스
  const remove = (symbol: string) => {
    const index = symbols.indexOf(symbol);
    const next = symbols[index + 1] ?? symbols[index - 1];
    onRemove(symbol);
    setTimeout(() => {
      const chips = chipsRef.current;
      const target = next ? chips?.querySelector<HTMLElement>(`[aria-label="${next} 빼기"]`) : null;
      (target ?? chips?.querySelector<HTMLElement>('input'))?.focus();
    });
  };
  return (
    <SettingField
      label="종목"
      message={message}
      messageId={messageId}
      right={<span>{`${symbols.length} / ${MAX_SYMBOLS}`}</span>}
    >
      <div ref={chipsRef} className="flex min-w-0 flex-wrap items-center gap-1.5">
        {symbols.map((symbol) => (
          <span
            key={symbol}
            role="group"
            aria-label={`${symbol} ${symbolName(symbol)}`.trim()}
            className="inline-flex items-center rounded-full bg-elevated"
          >
            <span className="py-[3px] pl-2.5 pr-1 text-[13px] text-tx-1">
              {symbol} <span className="text-xs text-tx-2">{symbolName(symbol)}</span>
            </span>
            <button
              type="button"
              aria-label={`${symbol} 빼기`}
              data-testid="compare-symbol-remove"
              onClick={() => remove(symbol)}
              className="inline-flex h-9 w-9 items-center justify-center rounded-full text-tx-3 hover:text-tx-1 sm:h-6 sm:w-6"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </span>
        ))}
        {symbols.length < MAX_SYMBOLS && (
          <StockSearchInput
            variant="inline"
            value=""
            onChange={(symbol) => symbol && onAdd(symbol)}
            supportedSymbols={supportedSymbols}
            placeholder="+ 종목"
            ariaLabel="종목 더하기"
            invalid={invalid}
            describedBy={describedBy}
            clearOnSelect
          />
        )}
      </div>
    </SettingField>
  );
};
