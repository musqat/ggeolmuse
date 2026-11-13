import React, { useState, useRef, useEffect, useMemo, useId } from 'react';
import { Search, X } from 'lucide-react';
import { searchSymbols, symbolName, type SymbolMatch } from '../../utils/symbolSearch';

interface StockSearchInputProps {
  value: string;
  onChange: (symbol: string) => void;
  placeholder?: string;
  className?: string;
  supportedSymbols?: string[];
  // box: 돋보기 · 지우기가 있는 테두리 칸, inline: 밑줄 칸 + 회사 이름(목록은 감싼 칸 기준)
  variant?: 'box' | 'inline';
  ariaLabel?: string;
  invalid?: boolean;
  describedBy?: string;
  // 고른 뒤 입력 비우기(여러 종목 더하기 칸)
  clearOnSelect?: boolean;
}

const StockSearchInput: React.FC<StockSearchInputProps> = ({
  value,
  onChange,
  placeholder = "종목 검색...",
  className = "",
  supportedSymbols = [],
  variant = 'box',
  ariaLabel,
  invalid,
  describedBy,
  clearOnSelect = false,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  // ↑ ↓ 로 고르는 중인 항목
  const [active, setActive] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const listId = useId();

  // 고른 종목이 칸에 그대로면 목록은 모두
  const query = searchTerm === value ? '' : searchTerm;
  const filteredStocks = useMemo(() => searchSymbols(supportedSymbols, query, 20), [supportedSymbols, query]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(event.target as Node) &&
        inputRef.current &&
        !inputRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = e.target.value;
    setSearchTerm(newValue);
    setIsOpen(true);
    setActive(0);
  };

  const handleSelectStock = (stock: SymbolMatch) => {
    onChange(stock.symbol);
    setSearchTerm(clearOnSelect ? '' : stock.symbol);
    setIsOpen(false);
  };

  // ↓ 로 열고 ↑ ↓ 로 고르고 Enter, Esc 로 닫기
  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (!isOpen) {
        setIsOpen(true);
        setActive(0);
      } else {
        setActive((index) => Math.min(filteredStocks.length - 1, index + 1));
      }
    } else if (e.key === 'ArrowUp' && isOpen) {
      e.preventDefault();
      setActive((index) => Math.max(0, index - 1));
    } else if (e.key === 'Enter' && isOpen && filteredStocks[active]) {
      e.preventDefault();
      handleSelectStock(filteredStocks[active]);
    } else if (e.key === 'Escape' && isOpen) {
      setIsOpen(false);
      if (variant === 'inline') setSearchTerm(value);
    }
  };

  const handleClear = () => {
    setSearchTerm('');
    onChange('');
    setIsOpen(true);
    inputRef.current?.focus();
  };

  const handleInputFocus = () => {
    setIsOpen(true);
    setActive(0);
  };

  // value 가 바뀌면 searchTerm 도 맞춤(함수형 갱신이라 searchTerm 은 의존성에 없음, 넣으면 타이핑이 되돌아감)
  useEffect(() => {
    setSearchTerm((prev) => (prev !== value ? value : prev));
  }, [value]);

  const inputA11y = {
    role: 'combobox' as const,
    'aria-label': ariaLabel,
    'aria-invalid': invalid || undefined,
    'aria-describedby': describedBy,
    'aria-autocomplete': 'list' as const,
    'aria-expanded': isOpen,
    'aria-controls': listId,
    'aria-activedescendant': isOpen && filteredStocks[active] ? `${listId}-${active}` : undefined,
    onKeyDown: handleKeyDown,
  };

  const dropdown = isOpen && (
    <div
      ref={dropdownRef}
      id={listId}
      role="listbox"
      aria-label="종목 찾기"
      className={`absolute z-10 mt-1 bg-surface shadow-lg max-h-60 rounded-md py-1 text-base ring-1 ring-black ring-opacity-5 overflow-auto focus:outline-none sm:text-sm ${
        variant === 'inline' ? 'left-2 right-2 top-full border border-line-strong' : 'w-full'
      }`}
    >
      {filteredStocks.length > 0 ? (
        filteredStocks.map((stock, index) => (
          // eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/interactive-supports-focus -- 키보드 고르기는 입력 칸이 aria-activedescendant 로 맡음
          <div
            key={stock.symbol}
            id={`${listId}-${index}`}
            role="option"
            aria-selected={index === active}
            // 누르는 동안 입력 포커스 유지
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => handleSelectStock(stock)}
            data-testid="symbol-option"
            className="group cursor-pointer select-none relative py-2 pl-3 pr-9 hover:bg-brand hover:text-brand-ink aria-selected:bg-elevated aria-selected:shadow-[inset_3px_0_0_0_rgb(var(--brand-text))]"
          >
            <div className="flex items-center">
              <span className="font-semibold text-sm">{stock.symbol}</span>
              {stock.name && (
                <span className="ml-2 text-tx-2 text-sm truncate group-hover:text-brand-ink">{stock.name}</span>
              )}
            </div>
          </div>
        ))
      ) : (
        <div className="cursor-default select-none relative py-2 pl-3 pr-9 text-tx-1">
          검색 결과가 없습니다.
        </div>
      )}
    </div>
  );

  if (variant === 'inline') {
    const name = symbolName(value);
    return (
      <div className={`flex min-w-0 flex-1 items-center ${className}`.trim()}>
        <input
          ref={inputRef}
          type="text"
          value={searchTerm}
          onChange={handleInputChange}
          onFocus={(e) => {
            handleInputFocus();
            e.target.select();
          }}
          // 고르지 않고 나가면 고른 종목으로 되돌림
          onBlur={() => {
            setIsOpen(false);
            setSearchTerm(value);
          }}
          placeholder={placeholder}
          autoComplete="off"
          {...inputA11y}
          className="input-inline"
        />
        {name && <span className="ml-auto pl-2.5 text-[13px] text-tx-2 truncate">{name}</span>}
        {dropdown}
      </div>
    );
  }

  return (
    <div className={`relative ${className}`}>
      <div className="relative">
        <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
          <Search className="h-4 w-4 text-tx-3" />
        </div>
        <input
          ref={inputRef}
          type="text"
          value={searchTerm}
          onChange={handleInputChange}
          onFocus={handleInputFocus}
          placeholder={placeholder}
          {...inputA11y}
          className="block w-full pl-10 pr-10 py-2 border border-line-strong rounded-md leading-5 bg-surface placeholder-slate-500 focus:outline-none focus:placeholder-slate-500 focus:ring-1 focus:ring-brand focus:border-brand"
        />
        {searchTerm && (
          <div className="absolute inset-y-0 right-0 pr-3 flex items-center">
            <button
              onClick={handleClear}
              aria-label="검색어 지우기"
              className="h-4 w-4 text-tx-3 hover:text-tx-2"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        )}
      </div>

      {dropdown}
    </div>
  );
};

export default StockSearchInput;