import React, { useState, useRef, useEffect, useMemo } from 'react';
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
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const filteredStocks = useMemo(() => searchSymbols(supportedSymbols, searchTerm, 20), [supportedSymbols, searchTerm]);

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
  };

  const handleSelectStock = (stock: SymbolMatch) => {
    onChange(stock.symbol);
    setSearchTerm(stock.symbol);
    setIsOpen(false);
  };

  const handleClear = () => {
    setSearchTerm('');
    onChange('');
    setIsOpen(true);
    inputRef.current?.focus();
  };

  const handleInputFocus = () => {
    setIsOpen(true);
  };

  // value 가 바뀌면 searchTerm 도 맞춤(함수형 갱신이라 searchTerm 은 의존성에 없음, 넣으면 타이핑이 되돌아감)
  useEffect(() => {
    setSearchTerm((prev) => (prev !== value ? value : prev));
  }, [value]);

  const inputA11y = {
    'aria-label': ariaLabel,
    'aria-invalid': invalid || undefined,
    'aria-describedby': describedBy,
  };

  const dropdown = isOpen && (
    <div
      ref={dropdownRef}
      className={`absolute z-10 mt-1 bg-surface shadow-lg max-h-60 rounded-md py-1 text-base ring-1 ring-black ring-opacity-5 overflow-auto focus:outline-none sm:text-sm ${
        variant === 'inline' ? 'left-2 right-2 top-full border border-line-strong' : 'w-full'
      }`}
    >
      {filteredStocks.length > 0 ? (
        filteredStocks.map((stock) => (
          <div
            key={stock.symbol}
            onClick={() => handleSelectStock(stock)}
            data-testid="symbol-option"
            className="group cursor-pointer select-none relative py-2 pl-3 pr-9 hover:bg-brand hover:text-brand-ink"
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